# Orcinus

Orcinus is a 3D printing slicer for Android. It slices models on the phone or
tablet with the [OrcaSlicer](https://github.com/OrcaSlicer/OrcaSlicer) engine
and its printer and material profiles, and its interface follows OrcaSlicer's
desktop layout.

> Orcinus is an independent project. It is not affiliated with or endorsed by
> the developers of OrcaSlicer, Bambu Studio, or PrusaSlicer.

## Current state

- **OrcaSlicer engine on Android.** `engine/` builds OrcaSlicer's dependencies
  from its own recipes and the whole `libslic3r` from its own source list for
  `arm64-v8a`. OrcaSlicer's `fff_print_tests` (37 cases, including
  `Print::process()` and `export_gcode()`) and `libslic3r_tests` (114 cases)
  pass on a Pixel 8 Pro. See [`engine/README.md`](engine/README.md).
- **Android app.** Kotlin/Compose modules with ports and adapters. The app
  loads the bundled Creality profiles through Orca's `PresetBundle` and slices
  an imported STL or a 20 mm cube with `Print::process()` and `export_gcode()`,
  with progress and cancellation. The engine runs in a separate `:slicer`
  process as a foreground service, so slicing continues in the background and
  a native crash does not close the UI.
- **Matches desktop.** G-code from the phone matches the official desktop
  OrcaSlicer 2.4.2 within documented tolerances
  ([`docs/golden-comparison.md`](docs/golden-comparison.md)).

Status, stages, and exact commands are in [`docs/work-plan.md`](docs/work-plan.md)
(Russian); the module layout is in [`docs/architecture.md`](docs/architecture.md).

## Building

Toolchain:

- Android Gradle Plugin 9.2.1, Gradle 9.4.1, Kotlin 2.3.21
- compileSdk/targetSdk 37, minSdk 29, `arm64-v8a`
- NDK 28.2.13676358, CMake 3.25+ and Ninja on `PATH`
- JDK 17 or newer (Android Studio's bundled JDK is preferred)
- Python 3 for the helper scripts

Clone with the OrcaSlicer submodule and create `local.properties` with the SDK
path (`sdk.dir=...`):

```sh
git clone --recurse-submodules https://github.com/ArtLoz/-Orcinus.git
```

Build the engine dependencies once (about an hour), then the app:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/engine.ps1 -Stage deps
./gradlew.bat :app:assembleDebug
```

Gradle builds `liborcinus_engine.so` from `engine/` with the same toolchain and
flags as OrcaSlicer's `libslic3r`. Engine tests on a connected device:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/engine.ps1 -Stage all -Target libslic3r_tests,fff_print_tests
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/engine-test.ps1 -Suite fff_print_tests
```

A release build needs the address of the public source code, which the About
page shows, and a signing key; see [`docs/publishing.md`](docs/publishing.md).

## License

Orcinus is free software, distributed under the
[GNU Affero General Public License v3.0](LICENSE), the license of OrcaSlicer,
whose engine, profiles, and icons it includes.

The app bundles third-party libraries under their own licenses. The app's
About page lists every component with its license text; the notices of the
native engine libraries and the font are generated into `app/notices` by
[`scripts/notices/update_notices.py`](scripts/notices/update_notices.py), and
those of the Android libraries are collected from Maven at build time.

The user interface font is [Inter](https://rsms.me/inter/) (SIL Open Font
License 1.1). OrcaSlicer's own font, HarmonyOS Sans, is not used: its license
forbids modification and does not allow a free software release.

Contributions are welcome; see [`CONTRIBUTING.md`](CONTRIBUTING.md).
