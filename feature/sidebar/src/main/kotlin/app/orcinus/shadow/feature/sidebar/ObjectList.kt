package app.orcinus.shadow.feature.sidebar

import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.hasVariableLayerHeight
import app.orcinus.shadow.core.ui.plate.conversionsOf
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.hasConnectors
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.ui.plate.AddObjectItems
import app.orcinus.shadow.core.ui.plate.conversionName
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.ui.plate.shapeName
import app.orcinus.shadow.core.ui.plate.objectMenuState
import app.orcinus.shadow.core.ui.plate.ClipboardItems
import app.orcinus.shadow.core.ui.plate.ObjectMenuItems
import app.orcinus.shadow.core.ui.plate.SetAsIndividualItem
import app.orcinus.shadow.core.ui.plate.ObjectMenuActions
import app.orcinus.shadow.core.ui.plate.ChangeFilamentItem
import app.orcinus.shadow.core.ui.plate.MenuFilament
import app.orcinus.shadow.core.ui.plate.ProcessSettingsItems
import app.orcinus.shadow.core.ui.plate.SmoothMeshItem
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.Manipulation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.orcaString

/** What the object list asks of the app, as OrcaSlicer's object list does of the plater. */
internal class ObjectListActions(
    /**
     * The row was picked: the plate, or a copy of one of its objects
     * (GLCanvas3D's Selection). While several are being picked the copy joins
     * the selection or leaves it.
     */
    val select: (PlateInstanceId?, add: Boolean) -> Unit,
    /** The row of a copy alone, which its menu and its settings row need. */
    val selectAlone: (PlateInstanceId?) -> Unit,
    /** The settings row was picked: the item's settings are shown. */
    val selectSettings: (PlateInstanceId?) -> Unit,
    /** ObjectList::toggle_printable_state(). */
    val setPrintable: (PlateInstanceId, Boolean) -> Unit,
    /** ObjectList::toggle_auto_drop(). */
    val setAutoDrop: (PlateInstanceId, Boolean) -> Unit,
    /** Plater::increase_instances(), decrease_instances() and set_number_of_copies(). */
    val addInstance: (ScenePath) -> Unit,
    val removeInstance: (ScenePath) -> Unit,
    val setNumberOfInstances: (ScenePath, Int) -> Unit,
    /** Selection::erase() of one copy of an object. */
    val removeCopy: (PlateInstanceId) -> Unit,
    /** Plater::fill_bed_with_instances() of an object. */
    val fillBed: (ScenePath) -> Unit,
    /** ObjectList::split_instances() of those copies of an object. */
    val setAsIndividual: (ScenePath, Set<Int>) -> Unit,
    /** The clone dialog's OK for the whole object. */
    val clone: (ScenePath, count: Int, arrange: Boolean) -> Unit,
    /** Cut and Copy of copies of objects, or of volumes of an object over a copy; Paste over a copy. */
    val copyObjects: (Set<PlateInstanceId>, cut: Boolean) -> Unit,
    val copyVolumes: (PlateInstanceId, Set<Int>, cut: Boolean) -> Unit,
    val paste: (PlateInstanceId) -> Unit,
    /** The object menu's Center, Drop and Mirror of a copy. */
    val manipulate: (PlateInstanceId, Manipulation) -> Unit,
    /** ObjectList::rename_item() of an object and of a volume. */
    val rename: (ScenePath, String) -> Unit,
    val renamePart: (ObjectPartId, String) -> Unit,
    /** The commands that change the meshes of an object, or of its volume at an index. */
    val editObject: (ScenePath, ObjectEdit, Int?) -> Unit,
    /** ObjectList::load_generic_subobject(): a shape joins the object. */
    val addPart: (ScenePath, shape: String, type: VolumeType, name: String) -> Unit,
    /** ObjectList::del_subobject_item(): the part leaves the object. */
    val removePart: (ObjectPartId) -> Unit,
    /** ObjectList::part_selection_changed(): the parameter panel edits the part. */
    val selectPart: (ObjectPartId?) -> Unit,
    val selectPartSettings: (ObjectPartId) -> Unit,
    /** ObjectList::layers_editing() and add_layer_range_after_current(). */
    val addRange: (ScenePath, after: LayerRangeId?) -> Unit,
    val removeRange: (LayerRangeId) -> Unit,
    val selectRange: (LayerRangeId?) -> Unit,
    val selectRangeSettings: (LayerRangeId) -> Unit,
    /** ObjectList::edit_layer_range(): the range spans other heights. */
    val editRange: (LayerRangeId, bottom: Double, top: Double) -> Unit,
    /** ObjectList::set_extruder_for_selected_items(): the item prints with that filament. */
    val setObjectExtruder: (ScenePath, Int) -> Unit,
    val setPartExtruder: (ObjectPartId, Int) -> Unit,
    val setRangeExtruder: (LayerRangeId, Int) -> Unit,
    /** Plater::remove_selected(). */
    val delete: (ScenePath) -> Unit,
    /** ObjectList::toggle_printable_state() of an object's row: every copy of it, as one step. */
    val setObjectPrintable: (ScenePath, Boolean) -> Unit = { _, _ -> },
    /** The object menu's Flush Options. */
    val toggleFlushOption: (ScenePath, FlushOption) -> Unit = { _, _ -> },
    /** switch_to_object_process(), copy_settings_to_clipboard() and paste_settings_into_list() of an item. */
    val editProcessSettings: (SettingsItem) -> Unit = {},
    val copyProcessSettings: (SettingsItem) -> Unit = {},
    val pasteProcessSettings: (SettingsItem) -> Unit = {},
    /** Opens the document picker for the new mesh of a volume of an object (Plater::replace_with_stl). */
    val replaceVolume: (PlateInstanceId, volume: Int) -> Unit = { _, _ -> },
    /** Opens the folder picker whose files replace the volumes of an object (Plater::replace_all_with_stl). */
    val replaceAllVolumes: (PlateInstanceId) -> Unit = {},
    /** Opens where an object is exported to (Plater::export_stl), suggesting a file named after it. */
    val exportObject: (ScenePath, MeshFormat, name: String) -> Unit = { _, _, _ -> },
    /** ObjectList::simplify() of the whole object, or of one of its volumes. */
    val simplifyObject: (ScenePath) -> Unit = {},
    val simplifyVolume: (ObjectPartId) -> Unit = {},
    /** ObjectList::set_volume_type(): the volume takes another type. */
    val changeVolumeType: (ObjectPartId, VolumeType) -> Unit = { _, _ -> },
    /** A plate item: the plate becomes current and nothing stays selected (ObjectList::selection_changed). */
    val selectPlate: (Int) -> Unit = {},
    /** The settings row of a plate: the plate becomes current and the panel edits its settings. */
    val selectPlateSettings: (Int) -> Unit = {},
    /** The plate menu (MenuFactory::create_plate_menu), whose items act on the current plate. */
    val selectPlateObjects: () -> Unit = {},
    val selectAllPlates: () -> Unit = {},
    val deletePlateObjects: () -> Unit = {},
    val arrangePlate: (Int) -> Unit = {},
    val orientPlate: (Int) -> Unit = {},
    val deletePlate: (Int) -> Unit = {},
    val lockPlate: (Int) -> Unit = {},
    val renamePlate: (Int, String) -> Unit = { _, _ -> },
    val addPrimitive: (shape: String, name: String) -> Unit = { _, _ -> },
    val addHandyModel: (HandyModel) -> Unit = {},
    /** Opens the document picker of Add Models (Plater::add_file). */
    val addModels: () -> Unit = {},
    /** Opens the folder picker whose files replace the volumes of the plate's objects (Plater::replace_all_with_stl). */
    val replaceAllOnPlate: (Int) -> Unit = {},
    /** ObjectList::invalidate_cut_info_for_selection() of an object. */
    val invalidateCutInfo: (ScenePath) -> Unit = {},
    /** ObjectList::enable_layers_editing(): the variable layer height opens on the object. */
    val editLayers: (ScenePath) -> Unit = {},
    /** append_menu_item_edit_text(): the canvas's text tool opens on the volume. */
    val editText: (ObjectPartId) -> Unit = {},
    /** "Text" of "Add part", "Add negative part" or "Add modifier": the canvas places a text on the object. */
    val addText: (ScenePath, VolumeType) -> Unit = { _, _ -> },
    /** append_menu_item_edit_svg(): the canvas's SVG tool opens on the volume. */
    val editSvg: (ObjectPartId) -> Unit = {},
    /** "SVG" of the same submenus: the canvas places an SVG on the object, once a file is picked. */
    val addSvg: (ScenePath, VolumeType) -> Unit = { _, _ -> },
)

/** An item the user renames: an object or one of its volumes, with the name it has. */
internal sealed interface RenameRequest {
    val name: String

    data class Object(val mesh: ScenePath, override val name: String) : RenameRequest

    data class Volume(val id: ObjectPartId, override val name: String) : RenameRequest

    /** "Edit Plate Name" of a plate, which PlateNameEditDialog asks for. */
    data class Plate(val index: Int, override val name: String) : RenameRequest
}

/**
 * OrcaSlicer's object list (GUI_ObjectList, ObjectDataViewModel) in the
 * sidebar: every plate, the objects under it, and "Outside" with the objects
 * that stand on no plate whole, as the desktop app lists them
 * (ObjectList::reload_all_plates). An item that overrides the process preset
 * has a settings row under it, named after the pages its settings sit on
 * (ObjectDataViewModelNode::update_settings_digest).
 *
 * The desktop tree has a column per property; a row on a phone has room for
 * the name, the check box of ModelInstance::printable, and the mark of an item
 * with its own settings, and its menu is the object menu of the 3D view
 * (MenuFactory).
 */
internal fun LazyListScope.objectListItems(
    state: SidebarUiState,
    enabled: Boolean,
    /** Several objects are being picked: a tap adds the object or takes it out. */
    picking: Boolean,
    /** The colour of every filament of the plate, which the filament column shows. */
    filaments: List<Color>,
    /** Every filament of the plate with its preset, which "Change Filament" lists. */
    menuFilaments: List<MenuFilament>,
    actions: ObjectListActions,
    /** A part of that kind is being added to the object: the sidebar asks which shape. */
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    /** The heights of that range are being edited: the sidebar asks for them. */
    onEditRange: (LayerRangeId) -> Unit,
    /** The object's number of copies is being set: the sidebar asks for it. */
    onAskNumberOfInstances: (ScenePath) -> Unit,
    /** The object is being cloned: the sidebar asks how. */
    onAskClone: (ScenePath) -> Unit,
    /** An item is being renamed: the sidebar asks for the name. */
    onAskRename: (RenameRequest) -> Unit,
) {
    val plateDefinitions = state.plateSettings.tab?.definitions.orEmpty()
    val objectDefinitions = (state.objectSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val partDefinitions = (state.partSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val rangeDefinitions = (state.rangeSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()

    state.plates.forEach { plate ->
        val index = plate.index
        item(key = "objects:plate:$index") {
            // ObjectDataViewModel::AddPlate(): "Plate N", and the plate's name after it.
            val name = "${orcaString("Plate")} ${index + 1}" + if (plate.name.isEmpty()) "" else " (${plate.name})"
            ObjectListRow(
                name = name,
                icon = DesignR.drawable.orca_plate_settings,
                selected = index == state.currentPlate && state.selectedInstances.isEmpty(),
                hasSettings = plate.overrides.categories(plateDefinitions).isNotEmpty(),
                enabled = enabled,
                onClick = { actions.selectPlate(index) },
                // The menu acts on the current plate, which the plate becomes first.
                onLongClick = { actions.selectPlate(index) },
                menu = { dismiss -> PlateItemMenu(plate, state, enabled, actions, dismiss, onAskRename) },
            )
        }
        settingsRow(key = "plate:$index", settings = plate.overrides, definitions = plateDefinitions) { actions.selectPlateSettings(index) }
        objectRows(plate.objects, state, enabled, picking, filaments, menuFilaments, objectDefinitions, partDefinitions, rangeDefinitions, actions, onChooseShape, onEditRange, onAskNumberOfInstances, onAskClone, onAskRename)
    }
    // The outside plate, which the list always has.
    item(key = "objects:outside") {
        ObjectListRow(
            name = orcaString("Outside"),
            icon = DesignR.drawable.orca_plate_settings,
            selected = false,
            hasSettings = false,
            onClick = {},
        )
    }
    objectRows(state.outsideObjects, state, enabled, picking, filaments, menuFilaments, objectDefinitions, partDefinitions, rangeDefinitions, actions, onChooseShape, onEditRange, onAskNumberOfInstances, onAskClone, onAskRename)
}

/**
 * The menu of a plate item: MenuFactory::create_plate_menu() and the lock and
 * name items plate_menu() adds. Its items act on the current plate, which the
 * plate is while the menu is open; the ones that need objects need a copy on
 * the plate (PartPlate::get_objects). Arranging and orienting a locked plate
 * only tells the desktop user that it is locked, so here they are not offered.
 */
@Composable
private fun PlateItemMenu(
    plate: ObjectListPlate,
    state: SidebarUiState,
    enabled: Boolean,
    actions: ObjectListActions,
    dismiss: () -> Unit,
    onAskRename: (RenameRequest) -> Unit,
) {
    val index = plate.index
    val current = enabled && index == state.currentPlate
    @Composable
    fun item(text: String, isEnabled: Boolean, action: () -> Unit) = OrcaMenuItem(
        text = text,
        enabled = isEnabled,
        onClick = {
            dismiss()
            action()
        },
    )
    item(orcaString("Select All"), current && plate.occupied, actions.selectPlateObjects)
    item(orcaString("Select All Plates"), enabled && state.objects.isNotEmpty(), actions.selectAllPlates)
    item(orcaString("Delete All"), current && plate.occupied, actions.deletePlateObjects)
    item(orcaString("Arrange"), current && plate.occupied && !plate.locked) { actions.arrangePlate(index) }
    // Reload from disk is not in the app yet.
    item(orcaString("Reload All"), false) {}
    item(orcaString("Auto Rotate"), current && plate.occupied && !plate.locked) { actions.orientPlate(index) }
    item(orcaString("Delete Plate"), current && state.canDeletePlate) { actions.deletePlate(index) }
    OrcaMenuSeparator()
    AddObjectItems(
        enabled = enabled,
        dismiss = dismiss,
        addPrimitive = actions.addPrimitive,
        addHandyModel = actions.addHandyModel,
        addModels = actions.addModels,
    )
    item(orcaString("Replace all with 3D files") + "...", current) { actions.replaceAllOnPlate(index) }
    item(orcaString(if (plate.locked) "Unlock" else "Lock"), current) { actions.lockPlate(index) }
    item(orcaString("Edit Plate Name"), current) { onAskRename(RenameRequest.Plate(index, plate.name)) }
}

private fun LazyListScope.objectRows(
    objects: List<PlateObject>,
    state: SidebarUiState,
    enabled: Boolean,
    picking: Boolean,
    filaments: List<Color>,
    menuFilaments: List<MenuFilament>,
    definitions: Map<String, SettingDefinition>,
    partDefinitions: Map<String, SettingDefinition>,
    rangeDefinitions: Map<String, SettingDefinition>,
    actions: ObjectListActions,
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    onEditRange: (LayerRangeId) -> Unit,
    onAskNumberOfInstances: (ScenePath) -> Unit,
    onAskClone: (ScenePath) -> Unit,
    onAskRename: (RenameRequest) -> Unit,
) {
    objects.forEach { plateObject ->
        val mesh = plateObject.mesh
        val copies = plateObject.instances.size > 1
        val ids = plateObject.instances.indices.map { PlateInstanceId(mesh, it) }
        item(key = "objects:${mesh.value}") {
            ObjectListRow(
                name = plateObject.displayName(),
                // ObjectDataViewModel::AddObject(): a part of a cut has the lock.
                icon = if (plateObject.isCut) DesignR.drawable.orca_cut_ else null,
                selected = ids.any { it in state.selectedInstances },
                hasSettings = plateObject.settings.categories(definitions).isNotEmpty(),
                indent = true,
                // The eye of an object switches every copy of it, and shows
                // the middle state while they differ.
                printable = plateObject.instances.map { it.printable }.distinct().singleOrNull(),
                showPrintable = true,
                enabled = enabled,
                // The column is only there when the printer prints with
                // several filaments (ObjectList::set_filament_column_hidden).
                extruder = filaments.takeIf { it.size > 1 }?.let { colors ->
                    ExtruderColumn(plateObject.extruderNumber, colors, allowDefault = false) {
                        actions.setObjectExtruder(mesh, it)
                    }
                },
                // ObjectDataViewModel's colHeight: the object prints with a variable layer height.
                variableHeight = if (plateObject.hasVariableLayerHeight) ({ actions.editLayers(mesh) }) else null,
                onClick = { actions.select(ids.first(), picking) },
                onPrintable = { printable -> actions.setObjectPrintable(mesh, printable) },
                onLongClick = { actions.selectAlone(ids.first()) },
                menu = { dismiss ->
                    val name = plateObject.displayName()
                    // ObjectList's in-place rename, which a phone offers from the row's menu.
                    RenameItem(enabled) {
                        dismiss()
                        onAskRename(RenameRequest.Object(mesh, name))
                    }
                    // The row selects the whole object (Selection::add_object).
                    ObjectMenuItems(
                        objectMenuState(
                            plateObject,
                            plateObject.instances.first(),
                            state.plate,
                            enabled,
                            wholeObject = true,
                            clipboard = state.clipboard,
                            simplifying = state.simplifying,
                            filaments = menuFilaments,
                            flushing = state.flushing,
                            settingsClipboard = state.settingsClipboard,
                        ),
                        actions.menuOf(
                            id = ids.first(),
                            name = name,
                            copies = plateObject.instances.indices.toSet(),
                            delete = { actions.delete(mesh) },
                            invalidateCutInfo = { actions.invalidateCutInfo(mesh) },
                            // can_edit_text(): the row selects every copy, so one copy of an object of a text alone.
                            editText = ObjectPartId(mesh, 0)
                                .takeIf { plateObject.instances.size == 1 && plateObject.parts.isEmpty() && plateObject.volume.emboss?.kind == EmbossKind.TEXT }
                                ?.let { id -> { actions.editText(id) } },
                            // can_edit_svg(), as can_edit_text().
                            editSvg = ObjectPartId(mesh, 0)
                                .takeIf { plateObject.instances.size == 1 && plateObject.parts.isEmpty() && plateObject.volume.emboss?.kind == EmbossKind.SVG }
                                ?.let { id -> { actions.editSvg(id) } },
                            onChooseShape = onChooseShape,
                            onAskNumberOfInstances = onAskNumberOfInstances,
                            onAskClone = onAskClone,
                        ),
                        dismiss,
                    )
                },
            )
        }
        // ObjectList::update_info_items(): a part of a cut with connectors has
        // their item, which the desktop list selects them with.
        if (plateObject.isCut && plateObject.hasConnectors && plateObject.parts.isNotEmpty()) {
            item(key = "objects:${mesh.value}:cut-connectors") {
                ObjectListRow(
                    name = orcaString("Cut connectors"),
                    icon = DesignR.drawable.orca_cut_connectors,
                    selected = false,
                    hasSettings = false,
                    indent = true,
                    deeper = true,
                    enabled = enabled,
                    onClick = { actions.select(ids.first(), picking) },
                )
            }
        }
        // ObjectList::add_volumes_to_object_in_list(): once the object has
        // parts, every volume has a row, its own mesh first, but for the
        // connectors of a cut.
        if (plateObject.listsVolumes()) (0..plateObject.parts.size).forEach { at ->
            val part = plateObject.volumeAt(at) ?: return@forEach
            if (plateObject.isCut && part.cutInfo.connector) return@forEach
            val partId = ObjectPartId(mesh, at)
            item(key = "objects:${mesh.value}:part:$at") {
                ObjectListRow(
                    // ObjectDataViewModel names a volume by its own name; the
                    // object's own mesh is named after the object unless the file named it.
                    name = when {
                        at == 0 -> part.name.ifEmpty { plateObject.displayName() }
                        else -> part.name.ifEmpty { stringResource(partName(part.type), stringResource(shapeName(part.shape))) }
                    },
                    icon = volumeIcon(part),
                    selected = partId == state.selectedPart,
                    hasSettings = part.settings.categories(partDefinitions).isNotEmpty(),
                    indent = true,
                    deeper = true,
                    enabled = enabled,
                    extruder = filaments
                        .takeIf { it.size > 1 && (part.type == VolumeType.PART || part.type == VolumeType.MODIFIER) }
                        ?.let { colors ->
                            ExtruderColumn(part.settings.extruderNumber, colors, allowDefault = true) {
                                actions.setPartExtruder(partId, it)
                            }
                        },
                    onClick = { actions.selectPart(partId) },
                    menu = { dismiss ->
                        val volumeName = when {
                            at == 0 -> part.name.ifEmpty { plateObject.displayName() }
                            else -> part.name.ifEmpty { stringResource(partName(part.type), stringResource(shapeName(part.shape))) }
                        }
                        RenameItem(enabled) {
                            dismiss()
                            onAskRename(RenameRequest.Volume(partId, volumeName))
                        }
                        // MenuFactory::text_part_menu() and svg_part_menu() of a volume embossed
                        // from a text or an SVG: its tool, and fewer edits of its mesh.
                        val embossed = part.emboss?.kind
                        // The Edit menu for the volume, which the list picks over the first
                        // copy; the object's own mesh cannot be cut out of it yet.
                        val first = PlateInstanceId(mesh, 0)
                        ClipboardItems(
                            enabled = enabled,
                            canPaste = enabled && state.clipboard is PlateClipboard.Volumes,
                            cut = if (at > 0) ({ dismiss(); actions.copyVolumes(first, setOf(at), true) }) else null,
                            copy = { dismiss(); actions.copyVolumes(first, setOf(at), false) },
                            paste = { dismiss(); actions.paste(first) },
                        )
                        OrcaMenuSeparator()
                        if (embossed != null) {
                            OrcaMenuItem(
                                text = orcaString(if (embossed == EmbossKind.TEXT) "Edit text" else "Edit SVG"),
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    if (embossed == EmbossKind.TEXT) actions.editText(partId) else actions.editSvg(partId)
                                },
                            )
                        }
                        // ObjectList::del_subobject_item(). The object's own
                        // mesh cannot go yet: the object would be its parts alone.
                        OrcaMenuItem(
                            text = stringResource(UiR.string.object_menu_delete),
                            enabled = enabled && at > 0,
                            onClick = {
                                dismiss()
                                actions.removePart(partId)
                            },
                        )
                        // MenuFactory::create_bbl_part_menu(), with the items the app has.
                        OrcaMenuItem(
                            text = orcaString("Fix model"),
                            enabled = enabled,
                            onClick = {
                                dismiss()
                                actions.editObject(mesh, ObjectEdit.FIX, at)
                            },
                        )
                        OrcaMenuItem(
                            text = orcaString("Simplify Model"),
                            enabled = enabled && !state.simplifying,
                            onClick = {
                                dismiss()
                                actions.simplifyVolume(partId)
                            },
                        )
                        // Plater::can_smooth_mesh() goes by the object's meshes.
                        if (embossed == null) SmoothMeshItem(enabled = enabled && plateObject.instances.first().inspection.openEdges == 0L) {
                            dismiss()
                            actions.editObject(mesh, ObjectEdit.SMOOTH_MESH, at)
                        }
                        if (embossed == null) OrcaSubmenu(text = orcaString("Split"), enabled = enabled && part.splittable) {
                            // ObjectList::is_splittable(true) refuses a volume.
                            OrcaMenuItem(text = orcaString("To objects"), enabled = false, onClick = {})
                            OrcaMenuItem(
                                text = orcaString("To parts"),
                                enabled = enabled && part.splittable,
                                onClick = {
                                    dismiss()
                                    actions.editObject(mesh, ObjectEdit.SPLIT_TO_PARTS, at)
                                },
                            )
                        }
                        // MenuFactory::part_menu() appends the conversions of the volume.
                        OrcaMenuSeparator()
                        val item = SettingsItem.Volume(partId)
                        ProcessSettingsItems(
                            enabled = enabled,
                            canPaste = enabled && state.settingsClipboard?.kind == SettingsItemKind.VOLUME,
                            edit = { dismiss(); actions.editProcessSettings(item) },
                            copy = { dismiss(); actions.copyProcessSettings(item) },
                            paste = { dismiss(); actions.pasteProcessSettings(item) },
                        )
                        // append_menu_item_change_type(): the kinds of volume, the volume's checked;
                        // a text or an SVG is no support blocker or enforcer.
                        OrcaSubmenu(text = orcaString("Change type"), enabled = enabled) {
                            VOLUME_TYPES.forEach { (type, label) ->
                                val support = type == VolumeType.SUPPORT_BLOCKER || type == VolumeType.SUPPORT_ENFORCER
                                OrcaMenuCheckItem(
                                    text = orcaString(label),
                                    checked = part.type == type,
                                    enabled = enabled && !(support && embossed != null),
                                    onClick = {
                                        dismiss()
                                        actions.changeVolumeType(partId, type)
                                    },
                                )
                            }
                        }
                        // Plater::can_replace_with_stl(): the list selects the volume alone.
                        if (embossed == null) OrcaMenuItem(
                            text = orcaString("Replace 3D file") + "...",
                            enabled = enabled,
                            onClick = {
                                dismiss()
                                actions.replaceVolume(first, at)
                            },
                        )
                        if (embossed == null) conversionsOf(listOf(part)).forEach { conversion ->
                            OrcaMenuItem(
                                text = orcaString(conversionName(conversion)),
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    actions.editObject(mesh, conversion, at)
                                },
                            )
                        }
                        // MenuFactory::part_menu(): a part of the model or a modifier takes a
                        // filament; a modifier may follow its object's ("Default").
                        if (part.type == VolumeType.PART || part.type == VolumeType.MODIFIER) {
                            ChangeFilamentItem(
                                menuFilaments,
                                withDefault = part.type == VolumeType.MODIFIER,
                                enabled = enabled,
                                onPick = { filament ->
                                    dismiss()
                                    actions.setPartExtruder(partId, filament)
                                },
                            )
                        }
                    },
                )
            }
            settingsRow(key = "${mesh.value}:part:$at", settings = part.settings, definitions = partDefinitions) {
                actions.selectPartSettings(partId)
            }
        }
        if (plateObject.layerRanges.isNotEmpty()) {
            // ObjectDataViewModel::AddLayersRoot(): the ranges hang under a row
            // of their own.
            item(key = "objects:${mesh.value}:layers") {
                ObjectListRow(
                    name = orcaString("Layers"),
                    icon = DesignR.drawable.orca_height_range_modifier,
                    selected = false,
                    hasSettings = false,
                    indent = true,
                    deeper = true,
                    enabled = enabled,
                    onClick = { actions.addRange(mesh, null) },
                )
            }
            plateObject.layerRanges.forEachIndexed { at, range ->
                val rangeId = LayerRangeId(mesh, at)
                item(key = "objects:${mesh.value}:layer:$at") {
                    ObjectListRow(
                        name = stringResource(R.string.object_layer_range, range.bottom, range.top),
                        icon = DesignR.drawable.orca_height_range_layer,
                        selected = rangeId == state.selectedRange,
                        hasSettings = range.settings.categories(rangeDefinitions).isNotEmpty(),
                        indent = true,
                        deeper = true,
                        enabled = enabled,
                        extruder = filaments.takeIf { it.size > 1 }?.let { colors ->
                            ExtruderColumn(range.settings.extruderNumber, colors, allowDefault = true) {
                                actions.setRangeExtruder(rangeId, it)
                            }
                        },
                        onClick = { actions.selectRange(rangeId) },
                        menu = { dismiss ->
                            OrcaMenuItem(
                                text = stringResource(R.string.object_menu_edit_range),
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    onEditRange(rangeId)
                                },
                            )
                            // ObjectList::add_layer_range_after_current()
                            OrcaMenuItem(
                                text = stringResource(R.string.object_menu_add_range_after),
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    actions.addRange(mesh, rangeId)
                                },
                            )
                            OrcaMenuItem(
                                text = stringResource(UiR.string.object_menu_delete),
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    actions.removeRange(rangeId)
                                },
                            )
                            // The desktop list copies a range's settings with the keyboard
                            // (ObjectList::copy_to_clipboard of its settings row), which a
                            // phone offers from the row's menu.
                            OrcaMenuSeparator()
                            val item = SettingsItem.Layer(rangeId)
                            ProcessSettingsItems(
                                enabled = enabled,
                                canPaste = enabled && state.settingsClipboard?.kind == SettingsItemKind.LAYER,
                                edit = { dismiss(); actions.editProcessSettings(item) },
                                copy = { dismiss(); actions.copyProcessSettings(item) },
                                paste = { dismiss(); actions.pasteProcessSettings(item) },
                            )
                        },
                    )
                }
                settingsRow(key = "${mesh.value}:layer:$at", settings = range.settings, definitions = rangeDefinitions) {
                    actions.selectRangeSettings(rangeId)
                }
            }
        }
        // The desktop app lists the copies of an object under it
        // (ObjectDataViewModel::AddInstanceChild); one copy needs no row.
        if (copies) {
            plateObject.instances.forEachIndexed { index, instance ->
                val id = ids[index]
                item(key = "objects:${mesh.value}:$index") {
                    ObjectListRow(
                        name = stringResource(R.string.object_instance_name, index + 1),
                        icon = null,
                        selected = id in state.selectedInstances,
                        hasSettings = false,
                        indent = true,
                        deeper = true,
                        printable = instance.printable,
                        showPrintable = true,
                        enabled = enabled,
                        onClick = { actions.select(id, picking) },
                        onPrintable = { actions.setPrintable(id, it) },
                        onLongClick = { actions.selectAlone(id) },
                        // MenuFactory::instance_menu(): "Set as an individual object" alone;
                        // Delete stands in for the Delete key, which erases the copy (Selection::erase).
                        menu = { dismiss ->
                            SetAsIndividualItem(wholeObject = false, enabled = enabled) {
                                dismiss()
                                actions.setAsIndividual(mesh, setOf(index))
                            }
                            OrcaMenuSeparator()
                            OrcaMenuItem(
                                text = stringResource(UiR.string.object_menu_delete),
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    actions.removeCopy(id)
                                },
                            )
                        },
                    )
                }
            }
        }
        settingsRow(key = mesh.value, settings = plateObject.settings, definitions = definitions) {
            actions.selectSettings(ids.first())
        }
    }
}

/**
 * What the filament column of a row shows and sets: the filament the item
 * prints with, where 0 is the "default" the desktop list shows for an item that
 * follows what it belongs to.
 */
internal data class ExtruderColumn(
    val number: Int,
    val filaments: List<Color>,
    /** An object always prints with a filament of its own; a part or a range may follow it. */
    val allowDefault: Boolean,
    val onPick: (Int) -> Unit,
)

/**
 * OrcaSlicer's filament column (colFilament): the desktop cell opens a combo
 * box of the plate's filaments over the row (ObjectList::extruder_editing).
 * Here the cell is a swatch in the colour of the filament, which opens that
 * list where a finger can reach it.
 */
@Composable
private fun FilamentColumn(column: ExtruderColumn, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val colors = OrcaTheme.colors
    Box {
        if (column.number == 0) {
            // "default": the item takes the filament of what it belongs to.
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(22.dp)
                    .border(1.dp, colors.border)
                    .clickable(enabled = enabled) { open = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("—", color = colors.textSide, style = OrcaTheme.typography.body13)
            }
        } else {
            OrcaFilamentSlot(
                number = column.number,
                color = column.filaments.getOrElse(column.number - 1) { colors.accent },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable(enabled = enabled) { open = true },
            )
        }
        if (open) {
            OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { open = false }) {
                if (column.allowDefault) {
                    OrcaMenuItem(
                        text = orcaString("default"),
                        enabled = enabled,
                        onClick = {
                            open = false
                            column.onPick(0)
                        },
                    )
                }
                column.filaments.forEachIndexed { index, color ->
                    OrcaMenuItem(
                        text = (index + 1).toString(),
                        enabled = enabled,
                        leading = { OrcaFilamentSlot(number = index + 1, color = color) },
                        onClick = {
                            open = false
                            column.onPick(index + 1)
                        },
                    )
                }
            }
        }
    }
}

/** The types "Change type" offers, with their texts (MenuFactory::append_menu_item_change_type). */
private val VOLUME_TYPES = listOf(
    VolumeType.PART to "Part",
    VolumeType.NEGATIVE to "Negative Part",
    VolumeType.MODIFIER to "Modifier",
    VolumeType.SUPPORT_BLOCKER to "Support Blocker",
    VolumeType.SUPPORT_ENFORCER to "Support Enforcer",
)

/**
 * ObjectDataViewModel's bitmap of a volume: a text or an SVG by its type
 * (MenuFactory::get_text_volume_bitmaps(), get_svg_volume_bitmaps()), the
 * part icon of the app otherwise.
 */
private fun volumeIcon(part: ObjectPart): Int = when (part.emboss?.kind) {
    EmbossKind.TEXT -> when (part.type) {
        VolumeType.NEGATIVE -> DesignR.drawable.orca_add_text_negative
        VolumeType.MODIFIER -> DesignR.drawable.orca_add_text_modifier
        else -> DesignR.drawable.orca_add_text_part
    }
    EmbossKind.SVG -> when (part.type) {
        VolumeType.NEGATIVE -> DesignR.drawable.orca_svg_negative
        VolumeType.MODIFIER -> DesignR.drawable.orca_svg_modifier
        else -> DesignR.drawable.orca_svg_part
    }
    else -> DesignR.drawable.orca_split_parts
}

/** What a row of the list calls a part of an object (ObjectDataViewModel). */
private fun partName(type: VolumeType): Int = when (type) {
    VolumeType.PART -> R.string.object_part_name
    VolumeType.NEGATIVE -> R.string.object_part_negative
    VolumeType.MODIFIER -> R.string.object_part_modifier
    VolumeType.SUPPORT_BLOCKER -> R.string.object_part_support_blocker
    VolumeType.SUPPORT_ENFORCER -> R.string.object_part_support_enforcer
}

/** What the object menu does for the object of the copy [id], selected with its [copies], in the list. */
private fun ObjectListActions.menuOf(
    id: PlateInstanceId,
    name: String,
    copies: Set<Int>,
    delete: () -> Unit,
    invalidateCutInfo: () -> Unit,
    editText: (() -> Unit)?,
    editSvg: (() -> Unit)?,
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    onAskNumberOfInstances: (ScenePath) -> Unit,
    onAskClone: (ScenePath) -> Unit,
) = ObjectMenuActions(
    cut = { copyObjects(copies.mapTo(LinkedHashSet()) { PlateInstanceId(id.mesh, it) }, true) },
    copy = { copyObjects(copies.mapTo(LinkedHashSet()) { PlateInstanceId(id.mesh, it) }, false) },
    paste = { paste(id) },
    addInstance = { addInstance(id.mesh) },
    removeInstance = { removeInstance(id.mesh) },
    setNumberOfInstances = { onAskNumberOfInstances(id.mesh) },
    fillBedWithInstances = { fillBed(id.mesh) },
    setAsIndividual = { setAsIndividual(id.mesh, copies) },
    clone = { onAskClone(id.mesh) },
    center = { manipulate(id, Manipulation.Center) },
    drop = { manipulate(id, Manipulation.Drop) },
    mirror = { manipulate(id, Manipulation.Mirror(it)) },
    delete = delete,
    addPart = { onChooseShape(id.mesh, it) },
    addHeightRange = { addRange(id.mesh, null) },
    setAutoDrop = { setAutoDrop(id, it) },
    edit = { editObject(id.mesh, it, null) },
    simplify = { simplifyObject(id.mesh) },
    setPrintable = { setObjectPrintable(id.mesh, it) },
    setFilament = { setObjectExtruder(id.mesh, it) },
    toggleFlushOption = { toggleFlushOption(id.mesh, it) },
    editProcessSettings = { editProcessSettings(SettingsItem.Object(id.mesh)) },
    copyProcessSettings = { copyProcessSettings(SettingsItem.Object(id.mesh)) },
    pasteProcessSettings = { pasteProcessSettings(SettingsItem.Object(id.mesh)) },
    replace = { replaceVolume(id, 0) },
    replaceAll = { replaceAllVolumes(id) },
    export = { exportObject(id.mesh, it, name) },
    invalidateCutInfo = invalidateCutInfo,
    editText = editText,
    editSvg = editSvg,
)

/**
 * can_add_volumes_to_object(): an object with parts lists its volumes; a part
 * of a cut only while more than its own solid mesh is left besides the connectors.
 */
private fun PlateObject.listsVolumes(): Boolean {
    if (parts.isEmpty()) return false
    if (!isCut) return true
    val listed = (0..parts.size).mapNotNull { volumeAt(it) }.filterNot { it.cutInfo.connector }
    return listed.any { it.type != VolumeType.PART } || listed.size > 1
}

/** ObjectList::rename_item(), first in the menu of a row. */
@Composable
private fun RenameItem(enabled: Boolean, onClick: () -> Unit) {
    OrcaMenuItem(text = orcaString("Rename"), enabled = enabled, onClick = onClick)
    OrcaMenuSeparator()
}

/** The settings row of an item, which the desktop app names after their pages. */
private fun LazyListScope.settingsRow(
    key: String,
    settings: ModelSettings,
    definitions: Map<String, SettingDefinition>,
    onClick: () -> Unit,
) {
    val categories = settings.categories(definitions)
    if (categories.isEmpty()) return
    item(key = "objects:$key:settings") {
        val name = categories.map { orcaString(it) }.joinToString("; ")
        ObjectListRow(
            name = name,
            icon = DesignR.drawable.orca_cog,
            selected = false,
            hasSettings = false,
            indent = true,
            deeper = true,
            onClick = onClick,
        )
    }
}

/**
 * SettingsFactory::get_bundle(): what an item's overrides are named after in
 * the list. Only the settings the tabs hold fall into a category, so an item
 * that overrides nothing else — the filament it prints with, which has a column
 * of its own — has no settings row and no mark on its row.
 */
private fun ModelSettings.categories(definitions: Map<String, SettingDefinition>): List<String> =
    values.keys.mapNotNull { definitions[it]?.category?.takeIf(String::isNotEmpty) }.distinct()

/** A row of the object list: the item's name, its settings mark, and its printable check box. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectListRow(
    name: String,
    icon: Int?,
    selected: Boolean,
    hasSettings: Boolean,
    modifier: Modifier = Modifier,
    indent: Boolean = false,
    deeper: Boolean = false,
    /** Null on a row of several copies that differ; absent on a row without the check box. */
    printable: Boolean? = null,
    showPrintable: Boolean = false,
    /** The filament column (colFilament), which only the rows that take one have. */
    extruder: ExtruderColumn? = null,
    /** The variable layer height mark (colHeight), whose tap opens the bar; null for none. */
    variableHeight: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPrintable: (Boolean) -> Unit = {},
    menu: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    val colors = OrcaTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) colors.accentSelected else Color.Transparent)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onLongClick = {
                        // The menu acts on the object it was opened over.
                        onLongClick()
                        menuOpen = true
                    },
                    onClick = onClick,
                )
                .heightIn(min = 40.dp)
                .padding(start = if (deeper) 44.dp else if (indent) 24.dp else 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = colors.textSide,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(OrcaTheme.dimensions.iconSmall),
                )
            }
            Text(
                text = name,
                color = if (enabled) colors.text else colors.textDisabled,
                style = OrcaTheme.typography.body14,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            if (variableHeight != null) {
                Icon(
                    painterResource(DesignR.drawable.orca_obj_variable_layer_height),
                    contentDescription = orcaString("Variable layer height"),
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .clickable(enabled = enabled, role = Role.Button, onClick = variableHeight)
                        .padding(8.dp)
                        .size(OrcaTheme.dimensions.iconSmall),
                )
            }
            if (extruder != null) {
                FilamentColumn(extruder, enabled)
            }
            if (hasSettings) {
                // ObjectDataViewModelNode::set_action_icon(): the item has settings of its own.
                Icon(
                    painterResource(DesignR.drawable.orca_cog),
                    contentDescription = orcaString("Click the icon to reset all settings of the object"),
                    tint = colors.textSide,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(OrcaTheme.dimensions.iconSmall),
                )
            }
            if (showPrintable) {
                OrcaCheckBox(
                    checked = printable,
                    onCheckedChange = onPrintable,
                    enabled = enabled,
                )
            }
        }
        if (menuOpen) {
            OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { menuOpen = false }) {
                menu { menuOpen = false }
            }
        }
    }
}
