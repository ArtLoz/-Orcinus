# Slicing contract

The Kotlin slicing contract models each operation as a job. It contains no
Android, JNI, filesystem-handle, or OrcaSlicer types, so upstream changes stay
inside the native adapter.

## Process

The UI never loads the engine. `RemoteSlicerEngine` implements `SlicerEngine`,
`PlateInspector`, `PresetManager`, and `PresetSettingsEditor` by binding to
`SlicerService`, which runs
`NativeSlicerEngine` in the `:slicer` process. The contract below is the same on
both sides; every call suspends because it may cross the process boundary.

## Engine status

`SlicerEngine.status()` prepares the engine on first use and returns
`EngineStatus(version, ready, message)`. The native implementation extracts the
bundled Orca resources into app storage when a new APK is installed, then starts
as the desktop app does: it reads `OrcaSlicer.conf` from the data directory when
there is one, installs vendor bundles from `resources/profiles` into
`data/system` (`PresetUpdater::check_installed_vendor_profiles` with updates
enabled: the filament library and Orca's Custom printers always, the bundle of
an enabled vendor when it is missing or older, and it removes the bundle of a
vendor no longer enabled), and loads the installed presets through
`PresetBundle::load_presets`. It is not ready when the resources hold no vendor
bundle or loading fails, and reports the reason.

## Presets and the Setup Wizard

The engine keeps the installed printers and filaments and the selection in its
`AppConfig`, saved to `OrcaSlicer.conf` after every change, so they outlive the
process. `AppConfig::save()` refuses threads other than the one libslic3r
considers the main thread; the adapter records the binder thread of the call
(`save_main_thread_id`) under its engine lock before saving.

`PresetManager.presets()` returns `Presets`:

- the selection (printer, first filament, process);
- `setupRequired`: `GUI_App::config_wizard_startup()` would run the Setup
  Wizard, because the app had no configuration yet or only default printers are
  installed;
- the lists of the sidebar's combo boxes in their order
  (`PlaterPresetComboBox::update` for printers and filaments,
  `TabPresetComboBox::update` of the process tab, with the default preferences
  that hide unsupported presets and do not group user filaments): user presets
  sorted by alias, bundle presets by bundle, system presets; system printers
  once per printer model, system filaments in OrcaSlicer's order with their
  vendor as submenu;
- the nozzle diameters of the selected printer model and the selected one
  (`Sidebar::update_presets`, `get_diameter_string`).

`PresetManager.selectPreset(choice)` selects as the sidebar does and remembers
the selection (`export_selections`):

- `Printer`: `Tab::select_preset` of the printer tab, `update_compatible`, and,
  with "Remember printer configuration" on as by default, the process and
  filaments the printer used last (`update_selections`);
- `PrinterModel`: a system preset of the model, with the selected printer's
  nozzle where it has one (`get_similar_printer_preset`), made visible;
- `NozzleDiameter`: `Sidebar::priv::switch_diameter`;
- `Filament`: `set_filament_preset` and the filament tab's `select_preset`;
- `Process`: the process tab's `select_preset`, which changes the filament when
  it is not compatible with the process.

The Setup Wizard's data mirrors what `GuideFrame::LoadProfileData` gives its web
pages. `setupPrinters()` lists every printer model of every vendor bundle (the
installed bundles first, then the others in the resources): vendor, model id,
name, nozzle diameters, default materials, cover file, and the nozzles already
installed. `setupFilaments(models)` lists the instantiable filament presets
whose `compatible_printers` name a printer of a chosen model with one of its
nozzles, or no known printer (then for every printer), with vendor and type
from the preset or the presets it inherits (`GetFilamentInfo`), and marks
those installed or among the chosen models' default materials. The first bundle
that defines a name wins; model ids are unique across the bundles.

`applySetup(models, filaments)` is the wizard's Finish (`SaveProfile`,
`apply_config`): the vendors section holds exactly the chosen models, each with
all its nozzle diameters, and the filaments section exactly the chosen
filaments; the preferred printer is the first model the wizard installs anew or
with another nozzle, Custom printers first; `PresetBundle::apply_vendor_config`
installs missing bundles, replaces `@System` filaments with a vendor's own,
loads the presets, selects, and the configuration is saved.
`applyDefaultSetup()` is `GuideFrame::run()` for a closed wizard while only
default printers are installed: OrcaSlicer's default printer and filament.
`ORCA_DEFAULT_PRINTER_MODEL` names the preset "MyKlipper 0.4 nozzle" rather
than its printer model, "Generic Klipper Printer", so the desktop app installs
no printer there; the adapter installs the model of that preset.

## The filaments of the plate

`PresetBundle::filament_presets` is the list the sidebar shows, one entry per
filament the plate prints with. `Presets` carries it whole (`selection.filaments`)
with the colour of each (`filament_colors`, "#RRGGBB" of `project_config`'s
`filament_colour`). `addFilament()` is `Sidebar::add_custom_filament`: the new
slot starts with the preset of the first one and takes the next colour of
OrcaSlicer's palette of sixteen; `removeFilament(index)` is
`Sidebar::delete_filament`, which keeps at least one; `selectFilament(index,
name)` sets that slot alone (`set_filament_preset`), and the filament tab keeps
editing the first; `setFilamentColor(index, color)` writes `filament_colour`.

A slice request carries every filament (`filamentProfiles`); the adapter sets
`filament_settings_id` to their number and each slot before slicing, so the
G-code changes tools. Which filament prints an item is the `extruder` key of its
settings (`ObjectList::set_extruder_for_selected_items`), on an object, on a
part of the model or a modifier, or on a height range; 0 means the item follows
what it belongs to. The key is not one of the settings tabs' options
(`SettingsFactory::get_bundle` drops it), so it never appears among an item's
own settings.

`describeWipeTower(plate, profiles, plateSettings)` answers what the desktop
canvas would draw (GLCanvas3D::reload_scene): whether the plate prints a wipe
tower at all — the process preset asks for one and more than one filament is
printed on the plate (PartPlate::get_extruders) — where it stands, how wide,
deep and tall it is (PartPlate::estimate_wipe_tower_size, up to the tallest
object), its rotation and brim, and the filaments printed on the plate. The
position comes from wipe_tower_x and wipe_tower_y of the plate's settings;
without them the engine answers with the position a new tower takes
(PartPlateList::set_default_wipe_tower_pos_for_plate). The app writes the
position back into the plate's settings, so the slice request carries it like
any other plate setting.

`describeFlushVolumes(plate, profiles, plateSettings)` answers what the desktop
dialog shows (WipingDialog): how much filament goes into the wipe tower for
every pair of filaments, as a matrix per nozzle, one row per filament printed
from. flush_volumes_matrix and flush_multiplier among the plate's settings are
the project's own values; the answer also carries the volumes OrcaSlicer works
out from the filament colours (CalcFlushingVolumes, over libslic3r's
FlushVolCalculator), so the app can offer them back, and whether the two differ
(is_flush_config_modified). The app writes what the user submits into the
plate's settings, which the slice request carries.

## Painting a model

`beginPainting(object, part, kind, profiles, facets, meshPrefix)` opens one of
OrcaSlicer's painting gizmos on one volume (GLGizmoPainterBase): colour
(GLGizmoMmuSegmentation), supports (GLGizmoFdmSupports), the seam or fuzzy
skin. The engine builds a TriangleSelector over the mesh, reads the facets of
that kind it is already painted with, and keeps it until `endPainting()`, as
the desktop gizmo keeps its selectors. `paint(stroke, meshPrefix)` is one touch
of a finger: the ray is cast into the mesh (AABBMesh::query_ray_hit) and the
triangles under the cursor take the stroke's state (EnforcerBlockerType: the
filament for colour, enforcer or blocker for supports and the seam, FUZZY_SKIN
for fuzzy skin), with the sphere or circle brush (SinglePointCursor, and
DoublePointCursor from where the stroke last met the model, as the gizmo joins
its mouse positions), the triangle under the finger alone (the Triangles tool,
CursorType::POINTER), the smart fill or the bucket fill; "on overhangs only"
limits it to overhanging facets. `clearPainting(meshPrefix)` is "Erase all".
Every answer carries the triangles painted in each state, written as a mesh per
state under a new name each time for the 3D view, and says whether the stroke
met the model at all. `fuzzySkinDisabled(object, profiles)` answers the fuzzy
skin tool's warning: whether fuzzy skin is "Disabled" for the object, by its
own settings or else by the process preset.

The painted facets of a volume, of every kind, travel as a file: the engine
writes them (TriangleSelector::TriangleSplittingData as hexadecimal text, each
kind behind its name) next to the meshes, since the painting of a detailed
model runs to megabytes that do not fit a call between the app and the engine's
process. `endPainting()` reports the file, or the one the session opened with
while nothing changed; the app keeps it with the object and sends it back with
the plate: a slice request carries it per object and per part, and the engine
applies each kind to its ModelVolume facets (mmu_segmentation_facets,
supported_facets, seam_facets, fuzzy_skin_facets), so the print changes
filament, supports, seam or skin over the painted surface.

## Cutting a model

`beginCut(object, instance, profiles)` opens OrcaSlicer's cut gizmo
(GLGizmoCut3D) on one copy of an object: the engine keeps the object, as the
gizmo's clippers keep its meshes, until `endCut()`, and reports the bounding
box of the copy's solid parts, whose centre the plane starts at.
`describeCutPlane(plane, meshPrefix)` answers what the gizmo shows of a plane,
given in world coordinates as a transformation (its centre and rotation; its
normal is the rotated Z axis, with the upper part on that side): the bounding
box of the solid parts in the plane's frame ("Build Volume"), whether the
plane goes through the object (ObjectClipper::has_valid_contour), and the
outline of the section, 0.4 mm wide, with the section itself, as meshes named
after the prefix (MeshClipper's contour and filled cut). The request carries
the gizmo's connectors (CutConnector: where each stands on the plane, its
size, depth, tolerances, turn, type, style and shape), and the answer says
which of them cannot be cut with — out of the section, out of the object or
overlapping (check_and_update_connectors_state()) — with the shape of each at
unit size for the 3D view (get_connector_mesh()). For the dovetail cut it
carries the grooves (Cut::Groove with their count and gap) instead, and the
answer brings the plane with its grooves (its_make_groove_plane()), whether
they meet the object (has_valid_groove()), and, once nothing is dragged, the
parts the cut makes (perform_with_groove() kept as parts), which the 3D view
shows in the object's place. A long press on the object, the desktop's right
click, asks `selectCutPart(parts, origin, direction, meshPrefix)`: the engine
cuts the object as parts at the plane and splits them into their pieces
(PartSelection, kept until another plane or `endCut`), each going to the
upper or the lower part, and turns over the piece the finger's ray meets
first; `describeCutPlane` then takes the pieces as they go and leaves out of
the section, and out of the connectors' valid places, the contours between
pieces going to the same part. The cut itself is
`edit(..., ObjectEdit.CUT, cut = ObjectCut(...))`: the connectors join the
object as negative volumes (apply_connectors_in_model() and
apply_cut_connectors(), named after the translated name the app gives), then
Cut::perform_with_plane() with the attributes of perform_cut(), plugs and
snaps becoming parts of the lower half and holes in the upper one, dowels
objects of their own, or Cut::perform_with_groove() for the dovetail cut, or
Cut::perform_by_contour() of the object split at the pieces' plane for a cut
by pieces (`ObjectCut.parts`). The objects the engine writes carry the cut
they are parts of (ModelObject::cut_id as `CutId`) and what the cut made of
each volume (ModelVolume::cut_info as `CutInfo`), and every object the app
hands over carries them back, so a later cut, a copy, Undo and a 3MF project
(Metadata/cut_information.xml of bbs_3mf) keep them; the parts it keeps are loaded at the end of
the plate as load_model_objects() loads them, with the question whether to
repair the edges the cut left open.

A request that names presets other than the selected ones is still served as
before: the adapter selects them on a copy of the configuration, and shows the
remembered selection again at the next preset call.

## Settings tabs

`PresetSettingsEditor` edits a preset as OrcaSlicer's settings tab does. The
process, filament, and machine tabs (`TabPrint`, `TabFilament`, `TabPrinter`)
are served, and the settings an object or the plate overrides the process
preset with (`TabPrintObject`, `TabPrintPlate`); the tabs of a part and of a
height range (`TabPrintPart`, `TabPrintLayer`) are not.

Every request builds the tab of its kind, loads the selection
(`Tab::load_current_preset`), activates the page the app shows, runs the action,
and describes the tab again. The page matters: the desktop app toggles the
fields of the page it shows (`Tab::activate_selected_page`), and only its
message boxes appear.

- `settingsTab(kind)` gives, for every option the tab can show, what its field
  needs to draw it: type, label, side text, mode, GUI type, enum values and
  labels. The rest of the definition stays in the engine, which checks the
  values, tells the field what to show and writes the tooltip; a tab crosses two
  processes, so what the app never reads is not sent. The pages themselves come with the edited preset, because the
  tab lays them out for it (the extruder pages of a printer, the filament
  overrides).
- `settings(kind, page)` describes the edited preset: its name and combo box
  label (with OrcaSlicer's `"* "` suffix when modified), whether it is dirty,
  default, system, has a parent, can be deleted, the settings mode, the name
  `SavePresetDialog` would offer, the pages with their groups and lines, the
  page whose fields the tab toggled, and each setting's value as the field shows
  it (`get_config_value`), whether it differs from the saved preset and from the
  system parent (the label colours of `Tab::decorate`), whether
  `ConfigManipulation` and the tab's `toggle_options` enable and show it, the
  choices of combo boxes whose entries depend on the configuration, and, for a
  filament override, whether it is set at all.
- `changeSetting(kind, page, id, text)` is the field's text committed: converted
  and checked as `Field::get_value` does (invalid numbers, ranges, percentages,
  rotation templates, reserved keywords in a custom G-code), applied with
  `change_opt_value`, then `Tab::on_value_change` and the tab's `update` run,
  which may correct other values. An id names one value of a vector setting
  (`"retraction_length#0"`), as the tab's lines do.
- `resetSettings(kind, page, ids)` is the undo button of those settings
  (`Tab::on_roll_back_value`), or of every modified setting when `ids` is empty.
- `setSettingOverride(kind, page, id, enabled)` is the check box before a
  filament override (`Tab::create_near_label_widget`): on, the filament takes
  over the value it overrides; off, it leaves it to the printer or the process.
- `setCompatiblePresets(kind, page, key, presets)` is the list behind the "Set"
  button of `compatible_printers` and `compatible_prints`; an empty list means
  every preset. `compatiblePresetChoices(kind, key)` lists what that dialog
  offers.
- `setSettingsMode(kind, mode)` sets simple, advanced, or expert mode, saved in
  the app configuration (`user_mode`).

The settings of an object, of one of its parts, of one of its height ranges and
of the plate (`PresetKind.OBJECT`, `PART`, `LAYER` and `PLATE`) are the process settings it overrides,
so the same requests serve them, with the overrides the app keeps. The engine holds no plate of its own: `settings`,
`changeSetting`, `resetSettings` and `setSettingsMode` carry a
`ModelSettingsRequest` — what every selected object overrides
(`TabPrintModel::set_model_config`) and what the plate overrides, which an
object's settings follow (`curr_bed_type`, `print_sequence`, `spiral_mode`), and
what the object a part or a height range belongs to overrides (`parent`,
`TabPrintPart` and `TabPrintLayer`) —
and the result gives the overrides back in `PresetSettings.modelSettings`, one
per object in the order the request carried them. The app keeps them with the
objects (`PlateObject.settings`), with their parts (`ObjectPart.settings`) and
with the plate (`PlateState.plateSettings`), and sends them with the next
request and with slicing.

Several selected objects are merged as the desktop app merges them: a setting
they agree on shows its value, and one they disagree on is a null key
(`TabPrintModel::m_null_keys`), which the result marks with `mixed` and shows no
value for. A change is written into every selected object.

An object's tab is the process tab with its "Frequent" page first and only the
settings of `PrintObjectConfig` and `PrintRegionConfig` left on the pages; the
plate's tab is the "Plate Settings" page of `plate_keys`. A part's tab
(`TabPrintPart`) keeps only the settings of `PrintRegionConfig`, and the value
an override goes back to is the one of the object the part belongs to, not of
the process preset: the desktop app reaches it through the model tab
(`m_parent_tab`), the app sends it as `parent`. A height range's tab
(`TabPrintLayer`) is the same with its own layer height added, which a range
always carries: a range that arrives without one is given the layer height of
its object (`notify_changed`), and a range whose height is the object's is not
marked as changed. A changed value
becomes an override of the object, `resetSettings` with an id removes that one
(the desktop app's arrow back to the value of the process preset), and with no
id removes every one of them (`TabPrintModel::reset_model_config`). The
bed type of the plate is named by its key, where the desktop app's combo box
lists the types without the default one and shifts them by one.

`printerConnection` and `savePrinterConnection` are OrcaSlicer's
`PhysicalPrinterDialog`: the settings of the printer's host live on the edited
printer preset (`host_type`, `print_host`, `print_host_webui`,
`printhost_apikey` and the rest), which the dialog edits and saves under a
name of its own — a system preset as a copy — and selects. The same answer
brings the page the Device tab loads (`PrintHost::get_print_host_webui()`, or
OrcaSlicer's `web/orca/missing_connection.html` while the preset has no host),
the API key the page's requests carry, and whether the printer is BambuLab's,
whose Device tab is BambuLab's own monitor. Sending the G-code is not part of
the contract: it goes over the network from the app, to the preset's host
(`Plater::send_gcode_legacy()`).

`importPresets` and `exportPresets` are the Import Configs and Export Preset
Bundle of the desktop app's File menu: the user presets of OrcaSlicer's
configuration files are installed (a preset of the same name is replaced), and
the user presets of the selection are written as one .json each. The engine
works with plain files, so the app copies a picked document in and the exported
files out.

`comparePresets` is `DiffPresetDialog`: the settings two presets of one kind
differ in, each with the value both of them hold, described like the unsaved
changes of a tab. A system printer may be named by its printer model, as the
app's preset lists name it.

`searchCatalog` lists every setting the process, filament and printer tabs show
(Search::OptionsSearcher), each with the page and group it sits in and the mode
it belongs to. The texts are untranslated like every text that crosses the
boundary, so the app matches a query against the translated ones.

`bedShape` and `setBedShape` are OrcaSlicer's `BedShapeDialog`: the printable
area of the edited printer as its dialog shows it (a rectangle by its bounding
box and the place of the G-code origin, a circle by its diameter, everything
else as its points), and the shape the dialog was closed with, which is written
into `printable_area`, `bed_custom_texture` and `bed_custom_model` of the edited
printer preset (`TabPrinter::create_bed_shape_widget`). The points are built as
`BedShapePanel::update_shape()` builds them, and a custom shape is the
horizontal projection of a model file (`load_stl`).

Not ported: the custom layer sequences of `PlateSettingsDialog` — choosing
"Customize" writes the filaments in their order.
- `settingTooltip(kind, id)` is the field's tooltip
  (`get_formatted_tooltip_text`): the option's tooltip, its name, the parent
  preset's value, and the range of a bounded number.
- `checkPresetName(kind, name)` is `SavePresetDialog::Item::update`: valid, a
  warning (such as replacing an existing user preset), or invalid, with the
  message. `savePreset(kind, name)` is `Tab::save_preset`; `deletePreset(kind)`
  is `Tab::delete_preset`, which asks first and then selects the parent or the
  next visible preset. Both save the configuration.

`selectPreset(choice, action)` is `Tab::select_preset`. With `ASK` and an edited
preset of that kind that is dirty, nothing is selected: the result carries the
unsaved changes as `UnsavedChangesDialog` lists them (the page, the group, the
label, and the value before and after), the name its Save button would offer,
and whether they can be moved (a printer never moves them, and neither does a
filament of another type). The caller asks the user and selects again with
`TRANSFER` (`Tab::cache_config_diff`, then `Tab::apply_config_from_cache` once
the preset is selected) or `DISCARD`; saving them first is `savePreset`.

The desktop app's modal dialogs are data. A notice (`MessageDialog` with OK) is
returned with the result, in order. A question (Yes/No) that the call has no
answer for ends the call: the adapter restores the edited preset and the tab's
state and returns only the question, with its id. The caller asks the user and
calls again with the answers so far, keyed by question id; the port then runs
the same code with those answers, as the desktop app continues after its
dialog. `PresetSettingsOutcome.Question.loadsSelection` marks a question raised
while the selection was loaded, which the caller answers with
`settings(kind, page, answers)`.

Not ported yet: the bed shape dialog (`BedShapeDialog`) and the ramming
parameters dialog (`RammingDialog`). Their lines show the value of the setting
in a field, in the text the desktop app shows for it.

Texts are not translated in the engine. Labels, tooltips, and dialog texts
cross the boundary as `OrcaText`: OrcaSlicer's msgid with its context, plural
form and count, and arguments that may themselves be msgids; the app
translates them with OrcaSlicer's catalogue, as `_L` and `wxString::Format`
would.

The edited preset lives in the engine process. A slice whose request names the
selected presets slices their edited values (`PresetBundle::full_config()`),
as the desktop app slices unsaved changes.

## Request

`SliceRequest` contains:

- a caller-generated `SliceJobId` used by progress, results, and cancellation;
- the objects of the plate as `PlacedModel`s: a `ModelSource` (an imported
  local STL or the built-in 20 mm calibration cube), the object's mesh file,
  the settings it overrides (`ModelObject::config`), the parts added to it
  (`ModelObject::volumes`: one of OrcaSlicer's shapes, its type, and its
  transformation in the object), and its copies
  (`ModelObject::instances`), each with its placement, its auto drop and
  `ModelInstance::printable`, which the object list's check box switches: a
  copy that is not printable stays on the plate and is left out of the print,
  so a plate with nothing printable has nothing to slice;
- the settings of the plate, which are laid over the presets as
  `BackgroundSlicingProcess::apply` lays a `PartPlate`'s over them;
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

`addPart(object, shape, type, profiles, mesh)` is
`ObjectList::load_generic_subobject()`: one of OrcaSlicer's shapes ("Cube",
"Cylinder", "Sphere", "Slab", "Cone", "Disc", "Torus") joins the object as a
part, a negative volume, a modifier, or a support blocker or enforcer. The
engine sizes it as the desktop app does (5% of the largest side of the bed, a
slab from the object's bounding box), places it at the object's right front
corner, writes its mesh for the 3D view and reports where it stands in the
object. Parts reach slicing with their type and their own settings
(`ModelVolume::config`).

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
  plate's excluded areas, and the printer's shrunk bed;
- `UpdatePrintVolume`: `Plater::on_config_change()` for another printer: the
  objects stay, and their fit is judged against its build volume.

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

1. Take `PresetBundle::full_config()` of the selected presets. Presets other
   than the selected ones are selected like the desktop start-up, on a copy of
   the `AppConfig`: the printer model and filament are marked as installed, and
   `PresetBundle::load_selections` selects them.
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
