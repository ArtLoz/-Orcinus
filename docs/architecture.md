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
      │                     ├─> :render:scene (Prepare) ─> :core:designsystem, :core:model
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
- `:render:scene` is OrcaSlicer's 3D plate view for Compose (`PlateView`), on
  OpenGL ES 3.0:
  - OrcaSlicer's own shaders (`resources/shaders/140`), which the build turns
    into GLSL ES 3.00 by replacing the version line and adding the default
    precision;
  - `OrcaCamera`, a line-by-line port of `Camera.cpp` (orbiting at a fixed
    distance, zoom by frustum size, tight near and far planes), with JVM tests;
  - the draw order, GL state, and colours of `GLCanvas3D::render`, `Bed3D`, and
    `PartPlate`: bed model, excluded area, thin and bold grid, bed texture, then
    the objects in the filament colour, shaded with flat face normals as
    `GLModel::init_from` builds them;
  - selection and moving as `GLCanvas3D::on_mouse`: a finger on an object
    (found by casting the touch ray, `Camera::mouse_ray`, at its mesh) selects
    it, brightened as `GLVolume::brighten_color` does and framed by the white
    corner brackets of `Selection::render_bounding_box`; dragging it moves it
    in the horizontal plane through the touched point, or in the screen plane
    when the camera looks along the plate; on release it drops onto the plate
    as `do_move` does, unless the object's auto drop (`ModelInstance::auto_drop`)
    is off, and the placement goes back to the app. A tap on empty space clears
    the selection. With auto drop off, arrows point down from the bottom
    corners of the brackets. Touch differences: the move starts after the touch
    slop even for a selected object, a second finger ends the move, and holding
    an object without moving it asks for its context menu, as a right click
    does;
  - gizmos (`PlateGizmo`) on the selected object, ported from `Gizmos/` with
    OrcaSlicer's grabbers (cubes and cones from `its_make_cube` and
    `its_make_cone`, axis colours, dashed connections, `gouraud_light`):
    - move (`GLGizmoMove3D`): an arrow per axis, dragged by `calc_projection`;
    - rotate (`GLGizmoRotate3D`): a ring per axis around the engine's bounding
      sphere, the angle from `mouse_position_in_local_plane` with the scale and
      coarse snaps, the object turning about the sphere's centre;
    - scale (`GLGizmoScale3D`): grabbers on the bounding box, `calc_ratio`, the
      object scaling about the box centre;
    - lay on face (`GLGizmoFlatten`): the faces the engine computed, drawn
      translucent; touching one lays the object on it.
    A finished drag reports the placement with its `Manipulation`, and the view
    rests the object on the plate by the same rule, auto drop included, until
    the engine answers. A
    finger takes a grabber within three touch slops of it on the screen;
  - gestures elsewhere: one finger orbits like OrcaSlicer's mouse, two fingers
    pan (the plate stays under the fingers) and pinch-zoom around their
    midpoint, a double tap on empty space returns to OrcaSlicer's plate view.
    A 20 dp band at the start edge is left to the drawer and system gestures.
  It draws what the engine computed and follows OrcaSlicer's canvas rules for
  moving; whether a placement is printable is decided by the engine.
- `:feature:prepare` builds the canvas toolbar with every button OrcaSlicer
  shows for a single-filament FFF printer, in its order: the main toolbar, a
  separator, the gizmos, a separator, the assembly view. The active gizmo's
  icon is in its own colours (the `_dark.svg` in dark mode); tools the app
  does not have yet stay disabled. On a phone the row scrolls instead of
  shrinking its icons, and it starts after the sidebar button. The gizmo
  windows of `GizmoObjectManipulation` are Compose panels: position; relative
  and absolute rotation with both resets; scale ratios, size, uniform scaling
  and reset. Inputs apply when done. `ObjectTransforms` computes their changes
  with the gizmos' world-coordinate math. The Arrange item opens the arrange
  options of `GLCanvas3D::_render_arrange_menu` (spacing, rotation, alignment
  to the Y axis, reset, arrange). Holding an object opens its context menu
  (`MenuFactory::create_object_menu`) with the items the app has so far: the
  "Auto Drop" check item. The page's own state (selection, open gizmo, rotation
  at opening, uniform scaling, faces for "Lay on face", arrange options) is one
  `PrepareViewState` in `PrepareViewModel`; clearing the selection closes the
  gizmo.

### Domain layer

- `:domain` holds the use cases. Engine-level ones (`SliceModelUseCase`,
  `ImportModelUseCase`, `InspectModelUseCase`, `GetEngineStatusUseCase`,
  `CancelSliceUseCase`) talk to the ports. Plate use cases (`plate/`) run the
  workflow the screens need — start the engine, add a model or the calibration
  cube, slice the plate, cancel, dismiss a problem — and write the results to
  `PlateRepository`, a port the domain owns. Long operations run in the
  application scope, so a slice outlives the screen that started it. Starting
  the engine also describes the plate of the selected printer, and adding a
  model or the calibration cube has the engine load and place it and write its
  mesh; the new object's mesh file replaces the previous one's.
  `PlacePlateObjectUseCase` commits a `Manipulation` (move, rotate, scale, reset
  rotation, auto orient, lay on face, arrange, ensure on bed) of the object
  with a given mesh file: the object stands at the new placement at once,
  G-code sliced for another placement is dropped, and the engine then settles
  it as OrcaSlicer commits that manipulation, keeping it where it is when its
  auto drop is off, and reports its size, bounding sphere, rotation, unscaled
  size, and whether it fits the build volume. `SetPlateObjectAutoDropUseCase`
  is `ObjectList::toggle_auto_drop()`: the flag lives on `PlateObject`, and
  turning it on again rests the object on the plate (`ensure_on_bed`).
  `DescribeFlatteningPlanesUseCase` asks the engine for the faces "Lay on face"
  offers. Until that answer, and while the object lies across the
  plate boundary, the plate cannot be sliced; slicing sends the placement to
  the engine. The selection is UI state of `PrepareViewModel`,
  kept by the object's mesh file, so a replaced object is never selected. Notice use
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
  `PlateState` and the scene types (`PlateDescription`, `ModelInspection` with
  its mesh file and placement).
- `:slicing:api` holds the engine ports: `SlicerEngine` and `PlateInspector`
  (describe the plate, load and place a model). `:storage:api` holds the ports
  for importing documents, locating G-code outputs, and scene files.
- `:slicing:native` is the NDK/JNI adapter and the only place OrcaSlicer is
  attached. Gradle builds `liborcinus_engine.so` through
  `engine/CMakePresets.json` together with OrcaSlicer's `libslic3r` and packages
  the selected vendor profiles, Orca's runtime tables, and the bed models and
  textures the vendors' printer models name as assets, which `OrcaAssets`
  copies to app storage for Orca's `PresetBundle`. For the 3D view the adapter
  computes the plate as OrcaSlicer's GUI does (`PartPlate` triangulation and
  grid, `Bed3D` model, `GLTexture` rasterizing the SVG texture with nanosvg)
  and writes meshes in a small binary format described in
  `orca_engine_adapter.hpp`.
- `:slicing:service` moves any `SlicerEngine` + `PlateInspector` into another
  process: `SlicerService` (AIDL server, one job at a time, specialUse foreground
  service with a progress notification and a cancel action) and
  `RemoteSlicerEngine` (client, turns the death of the engine process into
  `SliceFailureCode.ENGINE_CRASHED`).
- `:storage:android` copies Android documents into app storage under their own
  names, places G-code in `files/gcode`, and scene files in the no-backup
  `files/scene`.

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
    -> orca_engine_adapter: load_stl, place_on_bed (as slicing), mesh file
AddModelToPlateUseCase -> PlateRepository -> PlateState.plateObject
    -> PrepareScreen -> PlateView reads the mesh -> OpenGL ES
```

Describing the plate, once the engine is ready:

```text
AppShellViewModel -> StartEngineUseCase -> PlateInspector.describePlate
    == AIDL ==> orca_engine_adapter: printable area, grid, bed model mesh, texture PNG
StartEngineUseCase -> PlateRepository -> PlateState.plate -> PlateView
```

Moving an object:

```text
PlateView (touch ray or gizmo grabber, drag, drop onto the plate), or the move
window's position -> PrepareViewModel -> PlacePlateObjectUseCase
    -> PlateRepository: placement at once, placing
    -> PlaceModelUseCase == AIDL ==> orca_engine_adapter place_model: place_at,
       instance bounding box, update_print_volume_state
    -> PlateRepository: dropped placement, size, BuildVolumeFit
SlicePlateUseCase -> SliceRequest.placement, autoDrop == AIDL ==> orca_engine_adapter:
    place_at, update_print_volume_state, slice
```

The upstream pin and update workflow are described in
[`docs/upstream.md`](upstream.md).
