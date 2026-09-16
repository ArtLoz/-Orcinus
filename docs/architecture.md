# Architecture

Orcinus follows Android's recommended layered architecture (UI, domain,
data) with ports and adapters around the engine. Every screen is its own feature
module, shared look lives in a design system, and one-way module dependencies
keep upstream OrcaSlicer an implementation detail of the native adapter.

```text
:app ─┬─> :feature:prepare ─┐
      ├─> :feature:preview ─┤
      ├─> :feature:sidebar ─┼─> :core:designsystem, :core:ui
      ├─> :feature:about ───┤
      │                     └─> :domain ─> :slicing:api, :storage:api ─> :core:model
      ├─> :data:plate ─────────> :domain (implements PlateRepository)
      ├─> :data:notices ───────> :domain (implements NoticeCatalog)
      └─> adapters ────────────> :slicing:api, :storage:api
          :slicing:service, :slicing:native, :storage:android
```

At run time the app has two processes:

```text
UI process                                       :slicer process
MainActivity -> OrcinusApp (shell)         OrcaSlicerService (SlicerService)
  feature view models -> plate use cases
    -> SlicerEngine = RemoteSlicerEngine  == AIDL ==> NativeSlicerEngine -> JNI -> libslic3r
```

`liborcinus_engine.so` is loaded only in `:slicer`. A native crash there ends
that process; the UI receives `ENGINE_CRASHED`, and `RemoteSlicerEngine` binds
again, which starts a new `:slicer` process at once.

## Modules

### UI layer

- `:app` is the composition root and the app shell. `AppContainer` builds the
  adapters, the repositories, and the use cases once per process. `OrcinusApp`
  has two levels of Navigation 3 back stacks: the root one holds the workspace
  and the pages opened over the whole window (About and its license pages);
  the workspace draws OrcaSlicer's tab bar over its own `NavDisplay` of tabs.
  The shell starts the engine, holds the tab bar's slice action, and opens
  Preview when a slice finishes; features never reference each other.
  `OrcaSlicerService` hosts `NativeSlicerEngine` in the `:slicer` process
  (`foregroundServiceType="specialUse"`).
- `:feature:sidebar` is OrcaSlicer's sidebar (printer, material, process),
  shared by Prepare and Preview, so the shell places it through
  `OrcaSidebarLayout`. It links to About, which OrcaSlicer keeps in Help.
- `:feature:about` is the About page with the notices the GNU AGPL asks for
  (copyright, no warranty, the license text, the source code address), the
  credits, and the third-party components with their license texts.
- `:feature:prepare` and `:feature:preview` are one screen each: a `NavKey`, an
  `EntryProviderScope` extension that registers the entry, a view model scoped
  to the entry, a stateful route, a stateless screen with previews, and the
  screen's texts. Russian wording comes from OrcaSlicer's own translation
  (`localization/i18n/ru`) where it has one.
- `:core:designsystem` is the full OrcaSlicer look:
  - colours by role, light and dark, from OrcaSlicer's dark-mode table
    (`Widgets/StateColor.cpp`), widget styles, the main window, and the 3D canvas;
  - Orca's type scale (`Widgets/Label.cpp`) set in Inter, cut down to Latin and
    Cyrillic by `scripts/subset-fonts.ps1` (Orca's HarmonyOS Sans SC may not be
    modified or released under a free license);
  - control shapes and dimensions;
  - components: tab bar, page title bar with Back, list row, link, buttons in
    Orca's regular/confirm/alert styles and sizes, check box with Orca's icons, switch, segmented switch, text field
    with unit, combo box, sidebar title and section, parameter group and row,
    underline tabs, canvas, canvas toolbar and round buttons, the sidebar
    collapse button, the bottom info panel ("Sliced Info"), notifications,
    progress notification, G-code legend, layer and move sliders, each with
    light and dark previews;
  - `currentOrcaWindowLayout()` (Compact below 600 dp wide, Wide from 600 dp)
    and `OrcaSidebarLayout`: in a wide window the sidebar is docked under the
    tab bar and collapses, as on desktop; in a compact one it is a Material 3
    modal navigation drawer over the whole screen that opens with OrcaSlicer's
    collapse button or a swipe and closes with a swipe, the scrim, or
    predictive Back. Canvases always run edge to edge under the system bars;
  - Orca's SVG icons, converted at build time with Android's `Svg2Vector`; night
    variants apply the colour replacements OrcaSlicer applies in dark mode
    (`BitmapCache::load_svg`).
- `:core:ui` holds UI pieces shared by features and bound to app models: model
  names, print time, filament length, dimensions, problem titles.

### Domain layer

- `:domain` holds the use cases. Engine-level ones (`SliceModelUseCase`,
  `ImportModelUseCase`, `InspectModelUseCase`, `GetEngineStatusUseCase`,
  `CancelSliceUseCase`) talk to the ports. Plate use cases (`plate/`) run the
  workflow the screens need — start the engine, add a model or the calibration
  cube, slice the plate, cancel, dismiss a problem — and write the results to
  `PlateRepository`, a port the domain owns. Long operations run in the
  application scope, so a slice outlives the screen that started it. Notice use
  cases (`about/`) read the bundled third-party notices through the
  `NoticeCatalog` port.

### Data layer and adapters

- `:data:plate` implements `PlateRepository`, the single source of truth for the
  plate, in memory. Saving projects will replace the implementation, not its
  callers.
- `:data:notices` implements `NoticeCatalog` over the definitions the
  AboutLibraries Gradle plugin generates for `:app`: the Maven dependencies it
  collects, plus the native engine libraries, OrcaSlicer, and the font described
  in `app/notices` by `scripts/notices/update_notices.py` from their sources.
  License texts are never downloaded during the build.
- `:core:model` contains immutable, platform-neutral value types, including
  `PlateState`.
- `:slicing:api` is the engine port; `:storage:api` the ports for importing
  documents and locating G-code outputs.
- `:slicing:native` is the NDK/JNI adapter and the only place OrcaSlicer is
  attached. Gradle builds `liborcinus_engine.so` through
  `engine/CMakePresets.json` together with OrcaSlicer's `libslic3r` and packages
  the selected vendor profiles and Orca's runtime tables as assets, which
  `OrcaAssets` copies to app storage for Orca's `PresetBundle`.
- `:slicing:service` moves any `SlicerEngine` + `ModelInspector` into another
  process: `SlicerService` (AIDL server, one job at a time, specialUse foreground
  service with a progress notification and a cancel action) and
  `RemoteSlicerEngine` (client, turns the death of the engine process into
  `SliceFailureCode.ENGINE_CRASHED`).
- `:storage:android` copies Android documents into app storage under their own
  names and places G-code in `files/gcode`.

Inside `:slicing:native`, JNI only converts JVM values to adapter values.
`orca_engine_adapter.hpp` is the stable C++ boundary; OrcaSlicer and its
dependency types stay behind it.

## Dependency rules

1. Domain and core model source sets must not import Android, Compose, JNI, or
   OrcaSlicer types.
2. Feature modules call use cases; they do not call engines, repositories, or
   JNI directly, and they do not depend on other feature modules.
3. `:app` is the only composition root and contains no business rules; it wires
   features together through navigation.
4. Adapter and data modules implement interfaces owned by API or domain modules.
5. Every visual element comes from `:core:designsystem`; features do not define
   colours, type, or shapes.
6. Upstream OrcaSlicer files remain unmodified. Android differences are isolated
   in `:slicing:native`, `engine/`, or explicit build steps.
7. Cross-module data is expressed with `:core:model` types. Platform handles
   such as Android `Uri` are converted at adapter boundaries.

## Flows

Slicing the plate:

```text
PrepareScreen / tab bar -> PrepareViewModel / AppShellViewModel -> SlicePlateUseCase
    -> SliceModelUseCase -> SlicerEngine = RemoteSlicerEngine
    == AIDL ==> SlicerService (:slicer) -> NativeSlicerEngine
    -> JNI -> orca_engine_adapter -> PresetBundle, Model, Print (libslic3r)
SlicePlateUseCase -> PlateRepository -> PlateState.result
    -> the workspace opens Preview -> PreviewViewModel -> PreviewScreen
```

Adding a model:

```text
Android document picker -> PrepareViewModel -> AddModelToPlateUseCase
    -> ImportModelUseCase -> ContentResolverModelFileImporter -> app-owned STL
    -> InspectModelUseCase -> RemoteSlicerEngine == AIDL ==> NativeSlicerEngine
    -> orca_engine_adapter -> TriangleMesh
AddModelToPlateUseCase -> PlateRepository -> PlateState.plateObject
```

The upstream pin and update workflow are described in
[`docs/upstream.md`](upstream.md).
