package app.orcinus.shadow.feature.sidebar

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
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
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
    /** Plater::increase_instances() and decrease_instances(). */
    val copy: (PlateInstanceId) -> Unit,
    val removeCopy: (PlateInstanceId) -> Unit,
    /** ObjectList::load_generic_subobject(): a shape joins the object. */
    val addPart: (ScenePath, shape: String, type: VolumeType) -> Unit,
    /** ObjectList::del_subobject_item(): the part leaves the object. */
    val removePart: (ScenePath, index: Int) -> Unit,
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
)

/**
 * OrcaSlicer's object list (GUI_ObjectList, ObjectDataViewModel) in the
 * sidebar: the plate, the objects on it, and the ones that stand off it under
 * "Outside", as the desktop app moves them to its outside plate. An item that
 * overrides the process preset has a settings row under it, named after the
 * pages its settings sit on (ObjectDataViewModelNode::update_settings_digest).
 *
 * The desktop tree has a column per property; a row on a phone has room for
 * the name, the check box of ModelInstance::printable, and the mark of an item
 * with its own settings, and its menu is the object menu of the 3D view
 * (MenuFactory). Parts, instances and height ranges are not ported yet.
 */
internal fun LazyListScope.objectListItems(
    state: SidebarUiState,
    enabled: Boolean,
    /** Several objects are being picked: a tap adds the object or takes it out. */
    picking: Boolean,
    /** The colour of every filament of the plate, which the filament column shows. */
    filaments: List<Color>,
    actions: ObjectListActions,
    /** A part of that kind is being added to the object: the sidebar asks which shape. */
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    /** The heights of that range are being edited: the sidebar asks for them. */
    onEditRange: (LayerRangeId) -> Unit,
) {
    val onPlate = state.objects.filter { object_ -> object_.instances.any { it.inspection.fit != BuildVolumeFit.OUTSIDE } }
    val outside = state.objects.filter { object_ -> object_.instances.all { it.inspection.fit == BuildVolumeFit.OUTSIDE } }
    val plateDefinitions = state.plateSettings.tab?.definitions.orEmpty()
    val objectDefinitions = (state.objectSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val partDefinitions = (state.partSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val rangeDefinitions = (state.rangeSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()

    item(key = "objects:plate") {
        ObjectListRow(
            name = "${orcaString("Plate")} 1",
            icon = DesignR.drawable.orca_plate_settings,
            selected = state.selectedInstances.isEmpty(),
            hasSettings = state.plateOverrides.categories(plateDefinitions).isNotEmpty(),
            onClick = { actions.select(null, false) },
        )
    }
    settingsRow(key = "plate", settings = state.plateOverrides, definitions = plateDefinitions) { actions.selectSettings(null) }

    objectRows(onPlate, state, enabled, picking, filaments, objectDefinitions, partDefinitions, rangeDefinitions, actions, onChooseShape, onEditRange)
    if (outside.isNotEmpty()) {
        item(key = "objects:outside") {
            ObjectListRow(
                name = orcaString("Outside"),
                icon = DesignR.drawable.orca_plate_settings,
                selected = false,
                hasSettings = false,
                onClick = {},
            )
        }
        objectRows(outside, state, enabled, picking, filaments, objectDefinitions, partDefinitions, rangeDefinitions, actions, onChooseShape, onEditRange)
    }
}

private fun LazyListScope.objectRows(
    objects: List<PlateObject>,
    state: SidebarUiState,
    enabled: Boolean,
    picking: Boolean,
    filaments: List<Color>,
    definitions: Map<String, SettingDefinition>,
    partDefinitions: Map<String, SettingDefinition>,
    rangeDefinitions: Map<String, SettingDefinition>,
    actions: ObjectListActions,
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    onEditRange: (LayerRangeId) -> Unit,
) {
    objects.forEach { plateObject ->
        val mesh = plateObject.mesh
        val copies = plateObject.instances.size > 1
        val ids = plateObject.instances.indices.map { PlateInstanceId(mesh, it) }
        item(key = "objects:${mesh.value}") {
            ObjectListRow(
                name = plateObject.displayName(),
                icon = null,
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
                onClick = { actions.select(ids.first(), picking) },
                onPrintable = { printable -> ids.forEach { actions.setPrintable(it, printable) } },
                onLongClick = { actions.selectAlone(ids.first()) },
                menu = { dismiss ->
                    ObjectMenu(plateObject, plateObject.instances.first(), ids.first(), enabled, dismiss, actions, onChooseShape)
                },
            )
        }
        plateObject.parts.forEachIndexed { at, part ->
            val partId = ObjectPartId(mesh, at)
            item(key = "objects:${mesh.value}:part:$at") {
                ObjectListRow(
                    name = stringResource(partName(part.type), stringResource(shapeName(part.shape))),
                    icon = DesignR.drawable.orca_split_parts,
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
                        // ObjectList::del_subobject_item()
                        OrcaMenuItem(
                            text = stringResource(UiR.string.object_menu_delete),
                            enabled = enabled,
                            onClick = {
                                dismiss()
                                actions.removePart(mesh, at)
                            },
                        )
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
                        menu = { dismiss -> ObjectMenu(plateObject, instance, id, enabled, dismiss, actions, onChooseShape) },
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

/** The shapes OrcaSlicer generates (create_mesh of GUI_ObjectList.cpp), in its order. */
internal val PART_SHAPES = listOf("Cube", "Cylinder", "Sphere", "Slab", "Cone", "Disc", "Torus")

/** What the menu calls adding a part of that kind (MenuFactory). */
internal fun addPartName(type: VolumeType): Int = when (type) {
    VolumeType.PART -> R.string.object_menu_add_part
    VolumeType.NEGATIVE -> R.string.object_menu_add_negative
    VolumeType.MODIFIER -> R.string.object_menu_add_modifier
    VolumeType.SUPPORT_BLOCKER -> R.string.object_menu_add_support_blocker
    VolumeType.SUPPORT_ENFORCER -> R.string.object_menu_add_support_enforcer
}

/** What the sheet of shapes calls one of them. */
internal fun shapeName(shape: String): Int = when (shape) {
    "Cube" -> R.string.object_shape_cube
    "Cylinder" -> R.string.object_shape_cylinder
    "Sphere" -> R.string.object_shape_sphere
    "Slab" -> R.string.object_shape_slab
    "Cone" -> R.string.object_shape_cone
    "Disc" -> R.string.object_shape_disc
    else -> R.string.object_shape_torus
}

/** What a row of the list calls a part of an object (ObjectDataViewModel). */
private fun partName(type: VolumeType): Int = when (type) {
    VolumeType.PART -> R.string.object_part_name
    VolumeType.NEGATIVE -> R.string.object_part_negative
    VolumeType.MODIFIER -> R.string.object_part_modifier
    VolumeType.SUPPORT_BLOCKER -> R.string.object_part_support_blocker
    VolumeType.SUPPORT_ENFORCER -> R.string.object_part_support_enforcer
}

/** MenuFactory::create_object_menu(), with the items the app has. */
@Composable
private fun ObjectMenu(
    plateObject: PlateObject,
    instance: PlateInstance,
    id: PlateInstanceId,
    enabled: Boolean,
    dismiss: () -> Unit,
    actions: ObjectListActions,
    onChooseShape: (ScenePath, VolumeType) -> Unit,
) {
    OrcaMenuItem(
        text = stringResource(UiR.string.object_menu_delete),
        enabled = enabled,
        onClick = {
            dismiss()
            actions.delete(id.mesh)
        },
    )
    OrcaMenuSeparator()
    // append_menu_item_instance(): another copy of the object, and one fewer.
    OrcaMenuItem(
        text = stringResource(R.string.object_menu_add_instance),
        enabled = enabled,
        onClick = {
            dismiss()
            actions.copy(id)
        },
    )
    OrcaMenuItem(
        text = stringResource(R.string.object_menu_remove_instance),
        enabled = enabled && plateObject.instances.size > 1,
        onClick = {
            dismiss()
            actions.removeCopy(id)
        },
    )
    OrcaMenuSeparator()
    // append_menu_item_layers_editing()
    OrcaMenuItem(
        text = stringResource(R.string.object_menu_height_range),
        enabled = enabled,
        onClick = {
            dismiss()
            actions.addRange(id.mesh, null)
        },
    )
    OrcaMenuSeparator()
    // append_menu_items_add_volume(): every kind of part the desktop app adds,
    // whose submenu of shapes a phone shows as a sheet once the kind is picked.
    VolumeType.entries.forEach { type ->
        OrcaMenuItem(
            text = stringResource(addPartName(type)),
            enabled = enabled,
            onClick = {
                dismiss()
                onChooseShape(id.mesh, type)
            },
        )
    }
    OrcaMenuSeparator()
    OrcaMenuCheckItem(
        text = stringResource(UiR.string.object_menu_auto_drop),
        checked = instance.autoDrop,
        enabled = enabled,
        onClick = {
            dismiss()
            actions.setAutoDrop(id, !instance.autoDrop)
        },
    )
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
