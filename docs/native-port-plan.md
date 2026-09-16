# Native OrcaSlicer engine on Android

How the pinned OrcaSlicer core is built for Android and why. Commands and the
list of Android-specific differences live in [`engine/README.md`](../engine/README.md).

## Approach

The Android build treats OrcaSlicer the way OrcaSlicer's own desktop build does:

- **Dependencies** are built from upstream recipes in
  `upstream/OrcaSlicer/deps/<Name>/<Name>.cmake`. `engine/deps/CMakeLists.txt`
  replaces only the desktop driver (`orcaslicer_add_cmake_project`) with an
  Android one, so URLs, hashes, patches, and options stay upstream's.
- **Sources** of `libslic3r` and `libslic3r_cgal` are read from
  `upstream/OrcaSlicer/src/libslic3r/CMakeLists.txt` at configure time. New or
  removed upstream files reach Android automatically; exclusions are explicit
  and fail the configuration when they no longer exist upstream.
- **Bundled libraries** come from `upstream/OrcaSlicer/deps_src` through its own
  CMake (`add_subdirectory ... EXCLUDE_FROM_ALL`).
- **Verification** uses OrcaSlicer's Catch2 suites (`tests/fff_print`,
  `tests/libslic3r`) cross-compiled and run on a device.

The first port (September 14-16) hand-picked about 100 sources, compiled them
without linking, and replaced missing pieces with compile-only headers and
rewritten copies. Linking `Print::process()` needs almost all of `libslic3r`,
including CGAL (wipe tower mesh), GMP/MPFR (CGAL CORE for mesh booleans),
OpenCASCADE (`Model.hpp` includes the STEP importer), Boost.Log, and the G-code
exporter, so that approach was replaced. Its files in `slicing/native` remain
only until the app links the new engine.

## Dependencies

| Dependency | Source | Android build |
| --- | --- | --- |
| Boost 1.84, TBB, Eigen, Cereal, Qhull, NLopt, libnoise, Draco, libjpeg-turbo, libpng, Expat, FreeType, OpenCASCADE 7.6, CGAL 5.6 | Upstream CMake recipes | Unchanged recipes |
| GMP 6.2.1, MPFR 4.2.2 | Upstream archives and patch | `engine/deps/recipes/GMP_MPFR.cmake`: upstream flags, NDK compilers, MSYS2 shell on Windows |
| OpenSSL (MD5 API) | — | LibreSSL 4.3.2 (`engine/deps/recipes/LibreSSL.cmake`) |
| zlib | NDK sysroot | System library |
| OpenCV, OpenVDB | Not built | Only `ObjColorUtils.cpp` and `SLA/Hollowing.cpp` need them |
| wxWidgets, GLEW, GLFW, OpenCSG, CURL | Not built | Desktop UI and networking |

## Status

- Level C: all dependencies, all 201 `libslic3r` sources and `libslic3r_cgal`
  compile for arm64-v8a.
- Level R: `fff_print_tests` (37 cases) and `libslic3r_tests` (114 cases, one
  upstream-broken scenario excluded) pass on a Pixel 8 Pro, as does the app
  facade's `orca_engine_adapter_tests` (bundled Creality profiles through
  `PresetBundle`, cube and STL slicing, errors, cancellation).
- The APK links `libslic3r` into `liborcinus_engine.so`, loads the bundled
  profiles at start-up, and slices through the UI on a Pixel 8 Pro: a 20 mm
  cube, an STL chosen in the system picker, and a 638,400-triangle sphere
  (600 layers in 24 s, about 394 MB peak), with cancellation.
- The engine runs in a separate `:slicer` process behind AIDL. Verified on the
  device: slicing with the app in the background (specialUse foreground
  service), cancelling from the notification, and a `SIGSEGV` in the engine
  process during a job, after which the UI stays alive and slices again.
- Level G with tolerances: `scripts/golden.ps1` slices 10 models from Orca's
  test data on the device and with the official OrcaSlicer 2.4.2 Windows build.
  Configs match, layers and path types match, extrusion per layer differs by at
  most 0.24 %. Byte-for-byte equality is not reachable because the Windows and
  Android math libraries round `atan2`, `sin`, and `cos` differently; see
  [`golden-comparison.md`](golden-comparison.md).

## Next steps

1. Consider building upstream's OpenSSL 1.1.1w instead of LibreSSL now that a
   POSIX shell is part of the build.
