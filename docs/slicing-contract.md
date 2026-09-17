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
- a `ModelSource`: an imported local STL or the built-in 20 mm calibration cube;
- an `OutputPath` for the G-code;
- printer, filament, and process profile names as Orca knows them.

`SliceModelUseCase` validates paths and profile names before invoking the engine.
`SlicePlateUseCase` builds the request from the object on the plate and the
plate's `SlicingProfileSelection`, with the object's placement.

`SliceRequest.placement` is the instance transformation, column-major 4 x 4, as
inspection reported it and the user may have changed it by moving the object.
Null places the object as OrcaSlicer places a new one.

## Model import and inspection

Android document handles enter as an opaque `ExternalDocumentReference`.
`ModelFileImporter` (`:storage:android`) copies the document into app storage
and returns an `ImportedModelFile`.

`PlateInspector.inspect(model, profiles, mesh)` loads a `ModelSource` with the
same code slicing uses (`load_stl` or the built-in cube, then `place_on_bed`),
writes the mesh in object coordinates to the given file, and returns the facet
count, the placed size, and the instance transformation. A model the engine
cannot load never reaches the plate.

`PlateInspector.place(model, profiles, mesh, previous, placement, autoDrop, manipulation)`
commits a manipulation from the placement `previous` to `placement` as the
desktop app does. The mesh is centred as for inspection, the instance gets
`ModelInstance::auto_drop = autoDrop`, then:

- `Move` (`GLCanvas3D::do_move`): an object above the plate drops onto it;
- `Rotate`, `Scale` (`do_rotate`, `do_scale`): the object rests on the plate
  unless it was sunk into it at `previous` and still is;
- `ResetRotation`: `Transformation::reset_rotation()`, then as `Rotate`;
- `AutoOrient`: `OrientJob` with the least-support-area parameters;
- `LayOnFace(normal)`: `Selection::flattening_rotate()`, then resting on the plate;
- `Arrange(settings)`: `ArrangeJob` for the plate with the arrange options
  (spacing, rotation, alignment to the Y axis), the plate's excluded areas and
  the printer's shrunk bed;
- `EnsureOnBed`: `ModelObject::ensure_on_bed()`, as `ObjectList::toggle_auto_drop()`
  does when auto drop is turned on again.

With `autoDrop` off, as for an instance whose auto drop the user turned off,
none of these moves the object onto the plate: a lifted object stays lifted.

The result carries the settled placement, the instance bounding box size and
centre, the bounding sphere (`Selection::get_bounding_sphere`, CGAL), the
rotation in degrees (`get_rotation_by_quaternion`), the unscaled size
(`get_full_unscaled_instance_bounding_box`), and the `BuildVolumeFit` from
`Model::update_print_volume_state`. The adapter keeps the last model it read, so
manipulating an object does not read its file again.

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
2. Load the STL with Orca's `load_stl` (or build the cube). Without a placement,
   place instances on the bed centre and rest them on the plate; with one,
   centre the mesh as inspection did, give the instance the transformation and
   `SliceRequest.autoDrop`, and drop an instance above the plate onto it
   (`GLCanvas3D::do_move`) unless its auto drop is off.
3. `Model::update_print_volume_state` for the plate, as the desktop app does
   before enabling the slice button: an object over the plate boundary or above
   the build height ends the job as `InvalidPrint` with OrcaSlicer's message,
   and so does a plate with no object on it.
4. `Print::apply`, `Print::validate`, `Print::process`.
5. `Print::export_gcode` into `<output>.part`, then rename to the output path.

A `SlicingErrors` from Orca reports the messages of its individual errors, as
`BackgroundSlicingProcess` shows them (for example, an empty first layer).

## Cancellation

`cancel(jobId)` returns `true` only for the active job; it calls
`Print::cancel()`, and the job ends as `Cancelled` without an output file. The
notification action and cancelling the `slice` coroutine take the same path.

## Result

Every outcome carries the job ID:

- `Success`: G-code path and statistics — distinct printed layer heights, Orca's
  normal-mode print time estimate, and `PrintStatistics::total_used_filament`;
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
