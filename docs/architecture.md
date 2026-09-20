# Architecture

Orcinus follows Android's recommended layered architecture (UI, domain,
data) with ports and adapters around the engine. Every screen is its own feature
module, shared look lives in a design system, and one-way module dependencies
keep upstream OrcaSlicer an implementation detail of the native adapter.

```text
:app ─┬─> :feature:prepare ─┐
      ├─> :feature:preview ─┤
      ├─> :feature:sidebar ─┼─> :core:designsystem, :core:ui
      ├─> :feature:settings ┤
      ├─> :feature:setup ───┤
      ├─> :feature:about ───┤
      │                     ├─> :render:scene (Prepare) ─> :core:designsystem, :core:model
      │                     ├─> :render:gcode (Preview) ─> :render:scene
      │                     └─> :domain ─> :slicing:api, :storage:api ─> :core:model
      ├─> :data:plate ─────────> :domain (implements PlateRepository)
      ├─> :data:notices ───────> :domain (implements NoticeCatalog)
      └─> adapters ────────────> :slicing:api, :storage:api
          :slicing:service, :slicing:native, :storage:android
```

The app starts around one long step: the engine loads OrcaSlicer's profiles
(`PresetBundle::load_presets`), which takes seconds on a phone. Three things
keep the first screen quick: the engine starts with the process, not with the
first composition (`OrcinusApplication`); the vendor bundles are installed from
the resources only when they are missing or older, as the desktop updater's
version rule says, instead of copying the filament library's 482 files at every
start; and the 3D view draws the plate of the last run from `PlateCache` while
the profiles load, replacing it with the engine's answer.

At run time the app has two processes:

```text
UI process                                       :slicer process
MainActivity -> OrcinusApp (shell)         OrcaSlicerService (SlicerService)
  feature view models -> plate use cases
    -> SlicerEngine = RemoteSlicerEngine  == AIDL ==> NativeSlicerEngine -> JNI -> libslic3r
```

`liborcinus_engine.so` is loaded only in `:slicer`. A native crash there ends
that process; the UI receives `ENGINE_CRASHED`, and `RemoteSlicerEngine` binds
again, which starts a new `:slicer` process at once. The UI process loads only
`liborcinus_toolpaths.so`, OrcaSlicer's G-code viewer, which reads the toolpaths
file the engine writes.

## Modules

### UI layer

- `:app` is the composition root and the app shell. `AppContainer` builds the
  adapters, the repositories, and the use cases once per process. `OrcinusApp`
  has two levels of Navigation 3 back stacks: the root one holds the workspace
  and the pages opened over the whole window (the Setup Wizard, About and its
  license pages); the workspace draws OrcaSlicer's tab bar over its own
  `NavDisplay` of tabs. The shell starts the engine, opens the Setup Wizard
  while no printer is set up (`GUI_App::config_wizard_startup()`), holds the
  tab bar's slice action, and opens Preview when a slice finishes; features
  never reference each other. `OrcaSlicerService` hosts `NativeSlicerEngine` in
  the `:slicer` process (`foregroundServiceType="specialUse"`).
- `:feature:sidebar` is OrcaSlicer's sidebar (printer with its nozzle diameter,
  material, process), shared by Prepare and Preview, so the shell places it
  through `OrcaSidebarLayout`. Each field opens the list of its OrcaSlicer
  combo box (`PlaterPresetComboBox`, the process tab's `TabPresetComboBox`) as
  a bottom sheet: the sections, the vendor submenus, the selected entry, and
  the list's last entry, "Select/Remove printers" or "Add/Remove filaments",
  which opens the Setup Wizard. It links to About, which OrcaSlicer keeps in Help.
  Under the process preset the sidebar shows OrcaSlicer's process tab inline,
  as its ParamsPanel does: the Simple/Advanced/Expert switch, the save, delete,
  and undo-all buttons of the preset row, and the pages of the tab. Under them
  is OrcaSlicer's object list (`GUI_ObjectList`, `ObjectDataViewModel`): the
  plate, the objects on it, and the ones that stand off it under "Outside", with
  the check box of `ModelInstance::printable`, the mark of an item that
  overrides the process preset and, under it, the settings row named after the
  pages those settings sit on. A row picks what the 3D view has selected, so the
  view and the settings always show the same objects, and its long press opens
  the object menu of the 3D view (`MenuFactory`). The button in the section
  title switches the list to picking several objects, which the desktop app does
  with a Ctrl-click: their settings are then edited together, and the 3D view
  draws every one of them as selected. Then comes the panel's Global/Objects
  switch: Objects shows what the plate or the selected objects override the
  process preset with. The desktop tree also has a column per property and rows
  for parts, instances and height ranges, which are not ported yet. The printer
  and the material rows have OrcaSlicer's edit button, which opens their tab as
  a page of its own (`:feature:settings`), the way the desktop sidebar opens its
  ParamsDialog.
- `:feature:settings` is the tab of one preset kind over the whole window: the
  preset of the plate with the same save, delete and undo-all buttons, the mode
  switch, and the tab's pages. It shares every settings composable with the
  sidebar through `:core:ui`. The page covers the workspace but leaves it
  composed (`overlayPageMetadata` and the shell's `PageOverlaySceneStrategy`),
  as OrcaSlicer opens its ParamsDialog over the plater: the 3D view keeps its
  OpenGL surface and scene, so coming back draws the plate at once.
- The settings composables of `:core:ui` (`settings/`) draw any of the tabs from
  what the engine describes: the pages under underline tabs, and each option
  group with its lines. A page has up to a few hundred lines, so they are rows
  of the screen's own list (`settingsTabItems` on a `LazyColumn`) and only the
  ones in view are built. A line is shown when its mode fits the chosen mode and
  the engine has not hidden it; a field is enabled as the engine toggled it.
  Labels take `Tab::decorate`'s colours (modified, system, or the user's value),
  a label shows the option's tooltip, and a modified value has an undo button.
  Fields follow `OptionsGroup::build_field`: check box, combo box, open combo
  box, number, text on one line or several, colour, and the x and y of a point;
  a filament override has the check box of `Tab::create_near_label_widget`
  before its label, and the presets a preset is compatible with open a list of
  the engine's choices. Text fields hand their text to the engine when editing
  ends. The engine's notices and questions (`MessageDialog`s of the tab and
  `ConfigManipulation`) are Compose dialogs, saving opens `SavePresetDialog`
  with the engine's name check, and a preset chosen while the edited one has
  unsaved changes opens `UnsavedChangesDialog` with those changes and its Save,
  Transfer and Discard actions.
- `:feature:setup` is OrcaSlicer's Setup Wizard (`GuideFrame`) with its printer
  and filament pages, which the desktop app runs as web pages
  (`resources/web/guide/21` and `22`). `SetupPages` ports their scripts (the
  vendor order, search, model titles, a vendor's check box; filament lines
  merged by vendor, type, and name, the printer, type, and vendor filters, the
  filter bar, the default filaments, the notices), with JVM tests. The pages
  are laid out for touch: printer cards with covers by vendor under a search
  field and a row of vendor chips, and filament lines under filter chips.
  Finish installs the choice; closing the wizard on first run installs
  OrcaSlicer's default printer.
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
    underline tabs, the settings mode switch, canvas, canvas toolbar and round buttons, the sidebar
    collapse button, the bottom info panel ("Sliced Info"), notifications,
    progress notification, the preview's bottom sheet parts (handle, summary
    row, choice chips, legend sections and items with share bars and eye
    toggles, colour scale, values), the layer range slider and the move slider,
    each with light and dark previews;
  - `currentOrcaWindowLayout()` (Compact below 600 dp wide, Wide from 600 dp)
    and `OrcaSidebarLayout`: in a wide window the sidebar is docked under the
    tab bar and collapses, as on desktop; in a compact one it is a Material 3
    modal navigation drawer over the whole screen that opens with OrcaSlicer's
    collapse button or a swipe and closes with a swipe, the scrim, or
    predictive Back. Canvases always run edge to edge under the system bars;
  - taps without Material's ripple (`orcaClickable`, `orcaSelectable`), which
    OrcaSlicer's controls never show: they answer with their own colours;
  - Orca's SVG icons, converted at build time with Android's `Svg2Vector`; night
    variants apply the colour replacements OrcaSlicer applies in dark mode
    (`BitmapCache::load_svg`). `orcaIcon(name)` finds the drawable of an icon
    that OrcaSlicer's data names, such as an option group's icon.
- `:core:ui` holds UI pieces shared by features and bound to app models: model
  names, print time, filament length, dimensions, problem titles. It also
  translates the texts that come from OrcaSlicer (option labels, tooltips,
  group titles, the engine's messages, `OrcaText`) with OrcaSlicer's own
  catalogue, `localization/i18n/<language>/OrcaSlicer_<language>.po`, which the
  build packages as an asset: `OrcaCatalog` reads it as `msgfmt` compiles it
  (fuzzy and empty entries are left out), selects plural forms by the
  catalogue's `Plural-Forms`, and fills printf and `boost::format` arguments.
  The catalogue's language is a string resource beside the app's own texts,
  which are Russian for now; a translation of the app sets its language there
  and adds it to `orcaCatalogueLanguages` in `core/ui/build.gradle.kts`, the
  catalogues the build packages.
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
  moving; whether a placement is printable is decided by the engine. A
  `PlateLayer` given to the view is drawn after the bed in its GL context, as
  the preview draws G-code; the view owns the layer and releases it when it is
  replaced or the view goes away.
- `:render:gcode` draws the sliced toolpaths with OrcaSlicer's `libvgcode` in
  its OpenGL ES variant (`liborcinus_toolpaths.so`, built from the submodule with
  a small build-time patch, see `engine/README.md`). `ToolpathsLayer` reads the
  toolpaths file off the main thread and draws it as a `PlateLayer`. What the
  viewer shows (view type, visible layers, visible moves of the top layer,
  feature types and options) is requested on the layer and applied on the GL
  thread before drawing, since libvgcode uploads textures when it changes;
  `ToolpathsLayer.view` reports the result for the legend and sliders, and the
  file's statistics give the legend its figures.
- `:feature:preview` shows OrcaSlicer's preview content with touch controls
  designed for a phone rather than copied from the desktop's ImGui windows: the
  legend of `GCodeViewer::render_legend` (view types, feature types with time,
  share and filament, move options, estimation) is a Material bottom sheet whose
  collapsed part sums the print up (time, filament, layers) and whose view types
  are chips; a colour range is a gradient scale. The layer range is a vertical
  slider drawn straight over the canvas in the style of Material 3 sliders
  (a thick track with bar handles and value pills, one-layer step buttons and the
  one layer mode), and the moves of the top layer are a floating panel with the
  same slider between buttons that play or step through them. Number
  formats follow `short_time()` and the legend's helpers (`LegendFormat`, with
  tests).
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
  to the Y axis, reset, arrange); arranging places every object on the plate,
  and auto orient the selected object or, with none selected, every object.
  Holding an object opens its context menu (`MenuFactory::create_extra_object_menu`)
  with the items the app has so far, in its order: "Delete" and the "Auto Drop"
  check item. The object info notification describes the selected object, as
  `Plater::show_object_info()` does. The page's own state (selection, open
  gizmo, rotation at opening, uniform scaling, faces for "Lay on face", arrange
  options) is one `PrepareViewState` in `PrepareViewModel`; clearing the
  selection closes the gizmo, and an object added to the plate becomes the
  selection, as `Plater::priv::load_files()` selects what it loaded.

### Domain layer

- `:domain` holds the use cases. Engine-level ones (`SliceModelUseCase`,
  `ImportModelUseCase`, `InspectModelUseCase`, `GetEngineStatusUseCase`,
  `CancelSliceUseCase`) talk to the ports. Plate use cases (`plate/`) run the
  workflow the screens need — start the engine, add a model or the calibration
  cube, delete an object, slice the plate, cancel, dismiss a problem — and
  write the results to `PlateRepository`, a port the domain owns. Long
  operations run in the application scope, so a slice outlives the screen that
  started it. The plate holds a list of objects, told apart by their mesh
  files. Starting the engine loads the presets the engine's app configuration
  remembers (`Presets`: the selection, the combo boxes' lists, and whether the
  Setup Wizard is required) and describes the plate of the selected printer.
  `SelectPresetUseCase` selects a preset as the sidebar does, and
  `ApplySetupUseCase` applies the Setup Wizard's result; `PlatePresets` brings
  the plate to the presets the engine reports: G-code sliced with other presets
  is dropped, another printer or filament describes the plate again, and
  another printer judges whether the objects fit its build volume
  (`PlateManipulation.UpdatePrintVolume`, `Plater::on_config_change()`). Until a
  printer is set up, `PlateState.profiles` is null and nothing is placed or
  sliced. Adding a model or the calibration cube has the engine load it, place it
  among the objects already on the plate as `Plater::priv::load_model_objects()`
  does, and write its mesh; the object joins the end of the list.
  `PlacePlateObjectUseCase` commits a `Manipulation` (move, rotate, scale, reset
  rotation, lay on face, ensure on bed) of the object with a given mesh file:
  the object stands at the new placement at once, G-code sliced for another
  placement is dropped, and the engine then settles it as OrcaSlicer commits
  that manipulation, keeping it where it is when its auto drop is off, and
  reports its size, bounding sphere, rotation, unscaled size, and whether it
  fits the build volume. `PlacePlateObjectsUseCase` runs a `PlateManipulation`,
  OrcaSlicer's orient or arrange job, over the plate from settled placements;
  its answer applies to every object that still stands where the job found
  it, so an object the user moved or deleted meanwhile keeps the user's change.
  `DeletePlateObjectUseCase` is `Plater::remove_selected()` for one object and
  deletes its mesh file. `PresetSettingsTabs` is the settings tabs: for each
  kind it asks the engine for the tab's settings once, runs the tab's requests
  (describe, page, change, reset, override, compatible presets, mode, save,
  delete) one after another in the application scope, and keeps the answer in
  `PlateState.settingsTabs`. Every request carries the page the app shows, whose
  fields the tab toggles, as `Tab::activate_selected_page()` does. A request
  that meets a question stops there; the answer runs the request again with
  every answer so far, as the desktop app would continue after its modal dialog.
  Another value of the same preset drops the G-code sliced before it, a new
  preset name or label reloads the sidebar's presets, and `PlatePresets` has the
  open tabs described again whenever the presets change. The tabs of an object
  and of the plate are the same machine: the engine keeps no plate, so their
  requests carry what the object and the plate override and the answer goes
  back to `PlateObject.settings` and `PlateState.plateSettings`, which travel
  with the object into slicing. `SelectPlateObjectUseCase` is the canvas's
  selection, which the object list and the settings of an object follow;
  `SetSettingsScopeUseCase` is the Global/Objects switch;
  `SetPlateObjectPrintableUseCase` is `ObjectList::toggle_printable_state()`,
  whose copy stays on the plate, is drawn in OrcaSlicer's unprintable colour
  and is left out of the print (`ModelInstance::is_printable()`).
  `AddObjectPartUseCase` is `ObjectList::load_generic_subobject()`: a shape
  joins the object as a part, a negative volume, a modifier or a support
  blocker or enforcer, the engine places it and writes its mesh, and the 3D
  view draws it in OrcaSlicer's colours for that type;
  `RemoveObjectPartUseCase` is `del_subobject_item()`, and
  `SelectObjectPartUseCase` is `part_selection_changed()`, which selects the
  part and the object it belongs to, so the parameter panel edits the part.
  The settings search is split in two: the engine lists every setting of the
  preset tabs with where it sits (`search_catalog()`, Search::OptionsSearcher's
  append_options), and `:core:ui` matches the query against the translated
  texts, since the app holds OrcaSlicer's catalogue — `FuzzyMatch` ports
  `fts_fuzzy_match.h` and `SettingsSearch` ports `OptionsSearcher::search`.
  `ObservePhysicalPrintersUseCase`, `SavePhysicalPrinterUseCase` and
  `DeletePhysicalPrinterUseCase` are `PhysicalPrinterDialog`, and
  `SendGcodeUseCase` is `PrintHost::upload`: the sliced G-code goes to the
  printer through `GcodeSender`, which `:network:printhost` implements with the
  platform's HTTP, since the desktop app's upload lives in its GUI layer.
  `ExportGcodeUseCase` is the File menu's Export G-code: the sliced file is
  copied into a document of the user's own (`DocumentExport`), since the app
  keeps its G-code in its own directory.
  `ImportConfigUseCase` and `ExportConfigUseCase` are the File menu's Import
  Configs and Export Preset Bundle: `ConfigFiles` copies the picked document
  into the app and the exported files into the folder the user picked, since
  the engine reads and writes plain files.
  `SetBedShapeUseCase` is `TabPrinter::create_bed_shape_widget()`: the shape
  `BedShapeDialog` was closed with reaches the printer preset, and the plate is
  described again, so the 3D view and the fit of the objects follow the new
  build volume.
  `AddLayerRangeUseCase` is `layers_editing()` and
  `add_layer_range_after_current()`: a height range of the object
  (`ModelObject::layer_config_ranges`), which prints with a layer height of its
  own; `EditLayerRangeUseCase`, `RemoveLayerRangeUseCase` and
  `SelectLayerRangeUseCase` are the rest of the desktop list's range editing.
  `AddPlateInstanceUseCase` and `RemovePlateInstanceUseCase` are
  `Plater::increase_instances()` and `decrease_instances()`: an object keeps
  its copies (`PlateObject.instances`), which share its settings and its mesh
  and stand where each of them was placed; a new copy is offset by 5% of the
  largest side of the bed and the engine places it, and the object goes with
  its last copy. `SelectPresetUseCase`
  is `Tab::select_preset()`: a preset chosen while the edited one of that kind
  has unsaved changes selects nothing and keeps the choice with its changes in
  `PlateState.presetChange`; the answer saves them under a name, moves them to
  the preset that is selected (`Tab::cache_config_diff`), or discards them. `SetPlateObjectAutoDropUseCase` is
  `ObjectList::toggle_auto_drop()`: the flag lives on `PlateObject`, and turning
  it on again rests the object on the plate (`ensure_on_bed`).
  `DescribeFlatteningPlanesUseCase` asks the engine for the faces "Lay on face"
  offers. Until every placement is settled, and while an object lies across the
  plate boundary or none is on the plate, the plate cannot be sliced, as
  `GLCanvas3D::reload_scene()` decides; slicing sends every object with its
  placement to the engine, which prints the objects on the plate. The selection
  is UI state of `PrepareViewModel`, kept by the object's mesh file, so a
  deleted object is never selected. Notice use cases (`about/`) read the
  bundled third-party notices through the `NoticeCatalog` port.

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
  `PlateState`, the scene types (`PlateDescription`, `ModelInspection` with
  its mesh file and placement), and the settings types (`SettingsTab`,
  `PresetSettings`, `SettingsDialog`, `OrcaText`).
- `:slicing:api` holds the engine ports: `SlicerEngine`, `PlateInspector`
  (describe the plate, load and place a model, place the plate's objects), and
  `PresetManager` (the sidebar's presets, a preset choice with what happens to
  unsaved changes, the Setup Wizard's printers and filaments, and its result),
  and `PresetSettingsEditor` (the settings tabs: the settings they define, the
  edited preset with its pages and values, changes, resets, filament overrides,
  compatible presets, mode, tooltips, saving and deleting). `:storage:api` holds the ports
  for importing documents, locating G-code outputs, scene files, and the plate
  of the last run (`PlateCache`), which the 3D view draws while the engine loads
  the profiles.
- `:slicing:native` is the NDK/JNI adapter and the only place OrcaSlicer is
  attached. Gradle builds `liborcinus_engine.so` through
  `engine/CMakePresets.json` together with OrcaSlicer's `libslic3r` and packages
  Orca's resources as one asset, `orca/resources.zip`: every vendor's profiles
  with the printer covers, bed models, and textures, and Orca's runtime tables.
  `OrcaAssets` extracts it into app storage when a new APK is installed. The
  data directory is the engine's, as the desktop app keeps it: `OrcaSlicer.conf`
  (`AppConfig`: installed printers and filaments, the selection per printer)
  and `system/`, where the engine installs the bundles of the enabled vendors
  from the resources at start-up (`PresetUpdater::check_installed_vendor_profiles`).
  The adapter ports the Setup Wizard's C++ side (`setup_catalog.cpp`,
  `GuideFrame::LoadProfileData`, `SaveProfile`, `apply_config`) and the preset
  selection of the sidebar and tabs (`Tab::select_preset`, `update_selections`,
  `get_similar_printer_preset`). The process tab is ported the same way, with
  upstream names and order: `print_settings.cpp` (`Tab::on_value_change`,
  `load_current_preset`, `on_roll_back_value`, `back_to_initial_value`,
  `save_preset`, `delete_preset`, `TabPrint::update` and `toggle_options`),
  `config_manipulation.cpp` (`ConfigManipulation::update_print_fff_config` and
  `toggle_print_fff_options`), and `settings_fields.cpp` (a field's text to a
  value as `Field::get_value` checks and corrects it). `tab_print_model.cpp`
  is the same tab for an object, for one of its parts and for the plate
  (`TabPrintModel`, `TabPrintObject`, `TabPrintPart`, `TabPrintLayer`,
  `TabPrintPlate`): the
  process preset with the overrides applied, where a change is written into
  them instead of the preset. A part's overrides sit on the settings of its
  object, which the request carries as `parent` where the desktop app reaches
  them through `m_parent_tab`. They
  reach the print through `ModelObject::config` and, for the plate, over the
  full configuration, as `BackgroundSlicingProcess::apply` lays a `PartPlate`'s
  config over it. The desktop app's modal
  dialogs become data (`settings_dialogs.cpp`): a notice is collected and
  returned with the result; a question the request has no answer for stops the
  request, the adapter restores the edited preset and the tab's state, and
  returns the question for the app to ask. Texts cross the boundary untranslated
  (`UiText`: msgid, plural, context, arguments), so the app translates them
  with OrcaSlicer's catalogue. The tab's layout is not hand-copied: the Gradle
  task `generateOrcaSettingsLayout` reads `TabPrint::build()` from `Tab.cpp`
  (pages, option groups with icons, lines, separators, full-width and code
  fields) and generates `OrcaSettingsLayouts`; an unknown statement fails the
  build, so an OrcaSlicer update that changes the tab is noticed. For the 3D view the adapter
  computes the plate as OrcaSlicer's GUI does (`PartPlate` triangulation and
  grid, `Bed3D` model, `GLTexture` rasterizing the SVG texture with nanosvg)
  and writes meshes in a small binary format described in
  `orca_engine_adapter.hpp`.
- `:slicing:service` moves any `SlicerEngine` + `PlateInspector` +
  `PresetManager` into another process: `SlicerService` (AIDL server, one job at
  a time, specialUse foreground service with a progress notification and a
  cancel action) and `RemoteSlicerEngine` (client, turns the death of the engine
  process into `SliceFailureCode.ENGINE_CRASHED`). The edited preset lives in
  the `:slicer` process, so if that process dies, unsaved edits are lost as
  when the desktop app is closed. The Setup Wizard's data comes
  in two calls, printers and then the filaments for the chosen ones, to stay
  under the binder transaction limit.
- `:storage:android` copies Android documents into app storage under their own
  names, places G-code in `files/gcode`, and scene files (plate, object meshes,
  toolpaths) in the no-backup `files/scene`.

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
   in `:slicing:native`, `engine/`, or explicit build steps, such as the
   build-time patch of `libvgcode` in `engine/patches`.
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

Showing the sliced toolpaths:

```text
SlicePlateUseCase -> SliceRequest.toolpaths (SceneFiles.newToolpaths)
    == AIDL ==> orca_engine_adapter: export_gcode with a GCodeProcessorResult
    -> libvgcode::convert (LibVGCodeWrapper.cpp) -> toolpaths file
PlateState.result.toolpaths -> PreviewScreen -> ToolpathsLayer.load (read off the main thread)
    -> PlateView layer -> libvgcode Viewer (liborcinus_toolpaths.so) -> OpenGL ES
```

Adding a model:

```text
Android document picker -> PrepareViewModel -> AddModelToPlateUseCase
    -> ImportModelUseCase -> ContentResolverModelFileImporter -> app-owned STL
    -> InspectModelUseCase (with the objects on the plate) -> RemoteSlicerEngine
    == AIDL ==> NativeSlicerEngine -> orca_engine_adapter: load_stl, the plate's
       objects, place_new_object (centre or nearest empty cell), mesh file
AddModelToPlateUseCase -> PlateRepository -> PlateState.objects
    -> PrepareScreen -> PlateView reads the meshes -> OpenGL ES
```

Starting: the presets, the Setup Wizard, and the plate:

```text
AppShellViewModel -> StartEngineUseCase -> PresetManager.presets
    == AIDL ==> orca_engine_adapter: OrcaSlicer.conf, vendor bundles installed, load_presets
PlatePresets -> PlateRepository -> PlateState.presets
    setup required -> OrcinusApp opens SetupNavKey(FIRST_RUN)
        -> SetupWizardViewModel -> GetSetupPrintersUseCase, GetSetupFilamentsUseCase
           == AIDL ==> setup catalogue (every vendor bundle in resources/profiles)
        -> ApplySetupUseCase == AIDL ==> apply_setup: apply_vendor_config, save
    -> PlatePresets -> PlateInspector.describePlate
       == AIDL ==> orca_engine_adapter: printable area, grid, bed model mesh, texture PNG
    -> PlateState.plate -> PlateView
```

Choosing a preset in the sidebar:

```text
PresetListSheet / nozzle combo -> SidebarViewModel -> SelectPresetUseCase
    -> PlateRepository: changingPresets
    -> PresetManager.selectPreset == AIDL ==> select_preset: Tab::select_preset,
       update_selections, export_selections, AppConfig::save
    -> PlatePresets: PlateState.presets; another printer or filament: describePlate;
       another printer: PlacePlateObjectsUseCase(UpdatePrintVolume)
```

Changing a setting of a preset:

```text
SettingOptionRow (text committed, choice, switch, override check box, undo)
    -> SidebarViewModel / PresetSettingsViewModel -> PresetSettingsTabs
    -> PlateRepository: settingsTabs[kind].changing
    -> PresetSettingsEditor.changeSetting == AIDL ==> change_setting:
       the tab of the kind is built, loads the selection, activates the page,
       Field::get_value, change_opt_value, Tab::on_value_change,
       ConfigManipulation::update_print_fff_config / toggle_print_fff_options,
       describe: pages, values, modified/system flags, toggles, notices
    question -> PlateState.settingsTabs[kind].question -> SettingsQuestionDialog
       -> PresetSettingsTabs.answer -> the same request with the answers
    -> PlateRepository: settings, notices; another value drops the G-code;
       another label -> PresetManager.presets -> PlateState.presets
SlicePlateUseCase == AIDL ==> slice with the edited preset (PresetBundle::full_config)
```

Changing a setting of an object or of the plate:

```text
Global/Objects switch -> SetSettingsScopeUseCase -> PlateState.settingsScope
object list row (the plate or an object) -> SelectPlateObjectUseCase
    -> PlateState.selectedObject -> the 3D view's selection and the settings
SettingOptionRow -> SidebarViewModel -> PresetSettingsTabs(OBJECT or PLATE)
    -> PresetSettingsEditor.changeSetting with what the object and the plate
       override == AIDL ==> change_setting: TabPrintObject / TabPrintPlate,
       set_model_config (the process preset with the overrides applied),
       Tab::on_value_change writes the value into the overrides
    -> PlateState: PlateObject.settings / PlateState.plateSettings, and the
       G-code sliced before it is dropped
SlicePlateUseCase == AIDL ==> slice: ModelObject::config per object, the plate's
    settings over the full configuration
```

Choosing a preset whose edited one has unsaved changes:

```text
PresetListSheet -> SelectPresetUseCase -> PresetManager.selectPreset(ASK)
    == AIDL ==> select_preset: the preset collection is dirty, so nothing is
       selected; the tab describes its changes (UnsavedChangesDialog::update_tree)
    -> PlateState.presetChange -> UnsavedChangesDialog
       Transfer -> selectPreset(TRANSFER): Tab::cache_config_diff, select,
                   Tab::apply_config_from_cache, load_current_preset
       Discard  -> selectPreset(DISCARD)
       Save     -> PresetSettingsTabs.save (Tab::save_preset) -> selectPreset(DISCARD)
```

Moving an object:

```text
PlateView (touch ray or gizmo grabber, drag, drop onto the plate), or the move
window's position -> PrepareViewModel -> PlacePlateObjectUseCase
    -> PlateRepository: placement at once, placing
    -> PlaceModelUseCase == AIDL ==> orca_engine_adapter place_model: place_at,
       instance bounding box, update_print_volume_state
    -> PlateRepository: dropped placement, size, BuildVolumeFit
SlicePlateUseCase -> SliceRequest.objects (placement, autoDrop) == AIDL ==> orca_engine_adapter:
    place_at, update_print_volume_state, slice
```

Arranging or orienting the plate:

```text
Arrange options or the orient item -> PrepareViewModel -> PlacePlateObjectsUseCase
    -> PlateRepository: the objects the job places are placing
    -> PlaceModelsUseCase == AIDL ==> orca_engine_adapter place_objects:
       the plate's objects, ArrangeJob or OrientJob, update_print_volume_state
    -> PlateRepository: every object that stayed where the job found it
```

The upstream pin and update workflow are described in
[`docs/upstream.md`](upstream.md).
