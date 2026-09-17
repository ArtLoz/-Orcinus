# Slicing contract

The Kotlin slicing contract models each operation as a job. It contains no
Android, JNI, filesystem-handle, or OrcaSlicer types, so upstream changes stay
inside the native adapter.

## Process

The UI never loads the engine. `RemoteSlicerEngine` implements `SlicerEngine`
and `PlateInspector` by binding to `SlicerService`, which runs
`NativeSlicerEngine` in the `:slicer` process. The contract below is the same on
both sides; every call suspends because it may cross the process boundary.

## Engine status

`SlicerEngine.status()` prepares the engine on first use and returns
`EngineStatus(version, ready, message)`. The native implementation copies the
bundled Orca files into app storage, loads the system profiles through Orca's
`PresetBundle`, and reports the reason when it cannot.

## Request

`SliceRequest` contains:

- a caller-generated `SliceJobId` used by progress, results, and cancellation;
- the objects of the plate as `PlacedModel`s: a `ModelSource` (an imported
  local STL or the built-in 20 mm calibration cube), the object's mesh file,
  its placement, and its auto drop;
- an `OutputPath` for the G-code;
- optionally a `ScenePath` for the toolpaths file of the preview;
- printer, filament, and process profile names as Orca knows them.

`SliceModelUseCase` validates the objects, paths, and profile names before
invoking the engine. `SlicePlateUseCase` builds the request from the objects on
the plate and the plate's `SlicingProfileSelection`, and names the G-code after
the first object inside the plate, as `PrintBase::update_object_placeholders()`
fills `input_filename_base`.

A placement is the instance transformation, column-major 4 x 4, as inspection
reported it and the user may have changed it. The native adapter also accepts
an object without one, which it places as a new object; `orca_engine_slice`
slices a single STL that way for the comparison with desktop OrcaSlicer.

## Model import and inspection

Android document handles enter as an opaque `ExternalDocumentReference`.
`ModelFileImporter` (`:storage:android`) copies the document into app storage
and returns an `ImportedModelFile`.

`PlateInspector.inspect(model, profiles, mesh, plate)` loads a `ModelSource`
with the same code slicing uses (`load_stl` or the built-in cube) and places it
as `Plater::priv::load_model_objects()` places an object added to the plate
that holds `plate`: the mesh is centred around the origin and rests on the
plate, standing on the plate's centre when no other object meets the plate
(`PartPlate::empty`, `intersect_instance`), and otherwise in the empty cell
nearest to the centre (`GLCanvas3D::get_nearest_empty_cell`, a 10 mm grid; a
cell on an object's convex hull is covered). The cells are those of
`get_empty_cells()` as Bambu Studio has it and OrcaSlicer had it until commit
[8f777555](https://github.com/OrcaSlicer/OrcaSlicer/commit/8f777555358b8836ddf28b15400b45935e491334)
("Improve performance when bed are large"): since then OrcaSlicer's copy keeps
every cell that some object leaves free, which puts a second object onto the
one in the plate's centre. Inspection writes the mesh in object coordinates to
the given file and returns the facet count, the placed size, and the instance
transformation. A model the engine cannot load never reaches the plate.

`PlateInspector.place(model, profiles, mesh, previous, placement, autoDrop, manipulation)`
commits a manipulation from the placement `previous` to `placement` as the
desktop app does. The mesh is centred as for inspection, the instance gets
`ModelInstance::auto_drop = autoDrop`, then:

- `Move` (`GLCanvas3D::do_move`): an object above the plate drops onto it;
- `Rotate`, `Scale` (`do_rotate`, `do_scale`): the object rests on the plate
  unless it was sunk into it at `previous` and still is;
- `ResetRotation`: `Transformation::reset_rotation()`, then as `Rotate`;
- `LayOnFace(normal)`: `Selection::flattening_rotate()`, then resting on the plate;
- `EnsureOnBed`: `ModelObject::ensure_on_bed()`, as `ObjectList::toggle_auto_drop()`
  does when auto drop is turned on again.

With `autoDrop` off, as for an instance whose auto drop the user turned off,
none of these moves the object onto the plate: a lifted object stays lifted.

The result carries the settled placement, the instance bounding box size and
centre, the bounding sphere (`Selection::get_bounding_sphere`, CGAL), the
rotation in degrees (`get_rotation_by_quaternion`), the unscaled size
(`get_full_unscaled_instance_bounding_box`), and the `BuildVolumeFit` from
`Model::update_print_volume_state`.

`PlateInspector.placeObjects(plate, profiles, manipulation)` runs a job of the
desktop app over the objects of the plate and reports every object as placed,
in the plate's order:

- `AutoOrient(selected)`: `OrientJob` from the toolbar with the
  least-support-area parameters, for the objects with the selected mesh files,
  or for every object when none of them is on the plate
  (`OrientJob::prepare_selection`);
- `Arrange(settings)`: `ArrangeJob` from the arrange options (`prepare_all`)
  for every object, with the spacing, rotation, and alignment to the Y axis, the
  plate's excluded areas, and the printer's shrunk bed.

The adapter keeps the models of the plate's objects loaded, by file and its
modification time, so manipulating and slicing an object does not read its
file again; a call with the whole plate forgets the models of objects no
longer on it.

`PlateInspector.flatteningPlanes(model, profiles, mesh, placement)` returns the
faces "Lay on face" offers, computed with `GLGizmoFlatten::update_planes()`: in
object coordinates, largest first, at most 254.

Inspection and placement report `BuildVolumeFit`: `INSIDE`, `PARTLY_OUTSIDE`
(across the plate boundary or above the build height; the plate is not sliced,
as the desktop app disables its slice button), or `OUTSIDE`.

`PlateInspector.describePlate(profiles, directory)` returns the plate of the
selected printer as OrcaSlicer's GUI draws it: printable area and height, the
triangulated plate and excluded area, grid lines, the filament colour, and the
bed model mesh and texture PNG written into the directory.

Mesh files are little-endian: `OMSH`, format version, vertex count, triangle
count, float32 positions, uint32 indices.

## Execution and progress

`slice` suspends until Orca finishes. Cancelling the calling coroutine cancels
the job. Progress is Orca's own status: the percent
becomes `SliceProgress.fraction`, the text becomes `detail`, and the stage is
`SLICING` below 80 % and `GENERATING_GCODE` from 80 % (`Print::export_gcode`).
Progress may arrive on an Orca worker thread or a binder thread.

While a job runs, `SlicerService` is a started foreground service of type
`specialUse` with an ongoing notification (progress, Orca's text, "Отменить"),
so slicing continues when the user leaves the app. The service runs one job at
a time and stops itself when the job ends. A client process that dies does not
cancel the job; closing the app's task clears the view model, which cancels its
coroutine and therefore the job.

Native pipeline, mirroring the desktop app's background slicing:

1. Select profiles like the desktop start-up: mark the printer model and filament
   as installed in `AppConfig`, call `PresetBundle::load_selections`, and take
   `PresetBundle::full_config()`.
2. Load every object with Orca's `load_stl` (or build the cube) into one
   `Model`, in the plate's order. Centre each mesh as inspection did, give the
   instance its transformation and auto drop, and drop an instance above the
   plate onto it (`GLCanvas3D::do_move`) unless its auto drop is off; an object
   without a placement is placed as a new object.
3. `Model::update_print_volume_state` for the plate, as the desktop app does
   before enabling the slice button: an object over the plate boundary or above
   the build height ends the job as `InvalidPrint` with OrcaSlicer's message,
   and so does a plate with no object on it. Objects entirely off the plate are
   not printed.
4. `Print::set_plate_origin` at the origin of the first plate, as
   `PartPlate::set_print()` does (`Print` leaves it uninitialized, and G-code
   coordinates are relative to it), then `Print::apply`, `Print::validate`,
   `Print::process`. Identical objects share their slices, as OrcaSlicer's
   `Print::process` decides.
5. `Print::export_gcode` into `<output>.part`, then rename to the output path.

A `SlicingErrors` from Orca reports the messages of its individual errors, as
`BackgroundSlicingProcess` shows them (for example, an empty first layer).

## Cancellation

`cancel(jobId)` returns `true` only for the active job; it calls
`Print::cancel()`, and the job ends as `Cancelled` without an output file. The
notification action and cancelling the `slice` coroutine take the same path.

## Toolpaths

With `SliceRequest.toolpaths` set, the engine converts the G-code processor's
result of the export (`GCodeProcessorResult`) the way the desktop preview loads
it (`GLCanvas3D::load_gcode_preview`): OrcaSlicer's `libvgcode::convert()`, with
the filament colours as the tool colours and as the colour print colours. The
`libvgcode::GCodeInputData` is written next to its path and renamed when
complete; `toolpaths_file.hpp` in `:render:gcode` describes the format ("OTPF",
version, spiral vase flag, every `PathVertex` field, both palettes, and the
legend's statistics) and reads it back. The statistics are what the desktop
legend reads besides the moves: the times per mode and preparation time, the
filament per feature type, the model, support, flushed and tower filament, the
print's used filament, weight and cost, travel and seam totals, filament and
tool changes, and the count, time and distance of every move type, gathered as
`GCodeViewer::load_as_gcode()` does. A slice whose toolpaths cannot be written
still succeeds, without them.

`SlicePlateUseCase` requests a new file from `SceneFiles` for every slice and
keeps only the one of the plate's result.

## Result

Every outcome carries the job ID:

- `Success`: G-code path and statistics — distinct printed layer heights, Orca's
  normal-mode print time estimate, and `PrintStatistics::total_used_filament` —
  and the toolpaths file, when one was requested and written;
- `Failure`: a stable code and Orca's message:
  - `PROFILE_NOT_FOUND` — unknown or mutually incompatible profile names;
  - `MODEL_READ_FAILED` — the STL cannot be read;
  - `INVALID_PRINT` — `Print::validate` rejected the print;
  - `ENGINE_CRASHED` — the engine process died during the job. The client
    binds again at once instead of relying on Android's restart of a bound
    service, which waits 30 minutes after a second crash;
  - `ENGINE_BUSY`, `OUTPUT_WRITE_FAILED`, `ENGINE_UNAVAILABLE`, `SLICING_FAILED`;
- `Cancelled`.

JNI transports numbers as `long` and strings as UTF-8 bytes, so document names
outside the Basic Multilingual Plane survive the boundary. AIDL carries requests
and outcomes as flat parcelables with enum names; both processes run the same
APK.
