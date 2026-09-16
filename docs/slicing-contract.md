# Slicing contract

The Kotlin slicing contract models each operation as a job. It contains no
Android, JNI, filesystem-handle, or OrcaSlicer types, so upstream changes stay
inside the native adapter.

## Process

The UI never loads the engine. `RemoteSlicerEngine` implements `SlicerEngine`
and `ModelInspector` by binding to `SlicerService`, which runs
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
`SliceImportedModelUseCase` and `SliceCalibrationCubeUseCase` build requests from
the injected `SlicingProfileSelection`.

## Model import and inspection

Android document handles enter as an opaque `ExternalDocumentReference`.
`ModelFileImporter` (`:storage:android`) copies the document into app storage
and returns an `ImportedModelFile`. `ModelInspector` reads it with Orca's
`TriangleMesh` and returns facet count, dimensions, and a contour preview.

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
2. Load the STL with Orca's `load_stl` (or build the cube), place instances on
   the bed centre, and rest them on the plate.
3. `Print::apply`, `Print::validate`, `Print::process`.
4. `Print::export_gcode` into `<output>.part`, then rename to the output path.

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
