package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.ListClipboard
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.flushesInto
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.reloadableVolumes
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlin.math.abs

/**
 * What the object menu offers for the copy it was opened over, with the
 * conditions MenuFactory enables its items by.
 */
data class ObjectMenuState(
    val enabled: Boolean,
    /** Plater::can_increase_instances(): no copy of the object is left out of the print, and it is no part of a cut. */
    val canAddInstance: Boolean,
    /** Plater::can_decrease_instances(): the object has more than one copy, and it is no part of a cut. */
    val canRemoveInstance: Boolean,
    /** Plater::can_set_instance_to_object(): the object has more than one copy. */
    val canSetAsIndividual: Boolean = canRemoveInstance,
    /** Plater::can_mirror(): no part of a cut. */
    val canMirror: Boolean = enabled,
    /**
     * The menu is over the whole object, as the object list selects it, rather
     * than over one copy, as the canvas selects it.
     */
    val wholeObject: Boolean = false,
    /** append_menu_item_center(): the copy is not over the centre of the plate already. */
    val canCenter: Boolean,
    /** append_menu_item_drop(): the copy does not touch the plate. */
    val canDrop: Boolean,
    val autoDrop: Boolean,
    /** ObjectList::is_splittable(true): the object has parts, or its mesh several shells. */
    val canSplitToObjects: Boolean = false,
    /** ObjectList::is_splittable(false): the object is one mesh of several shells. */
    val canSplitToParts: Boolean = false,
    /** Plater::can_smooth_mesh(): the object's meshes have no open edges. */
    val canSmooth: Boolean = false,
    /** ObjectList::can_mesh_boolean(): the object has several volumes, or a mesh of several shells. */
    val canMeshBoolean: Boolean = false,
    /** Plater::can_simplify(): the Simplify gizmo is not open already. */
    val canSimplify: Boolean = false,
    /** append_menu_items_convert_unit(): the conversions the object's volumes allow. */
    val conversions: List<ObjectEdit> = emptyList(),
    /** Plater::can_paste_from_clipboard(): the clipboard holds objects, or volumes to join this one. */
    val canPaste: Boolean = false,
    /** append_menu_item_printable(): ModelInstance::printable of the copy, of the first copy for the whole object. */
    val printable: Boolean = true,
    /** append_menu_item_change_filament(): the filaments of the plate, when it has more than one. */
    val filaments: List<MenuFilament> = emptyList(),
    /** append_menu_items_flush_options(); null where the desktop menu has none. */
    val flushOptions: FlushOptionsMenu? = null,
    /** ObjectList::can_paste_settings_into_list(): the process settings copied are an object's. */
    val canPasteSettings: Boolean = false,
    /** Plater::can_reload_from_disk(): a volume came from a file, and the object is no part of a cut. */
    val canReloadFromDisk: Boolean = false,
    /** Plater::can_replace_with_stl(): the selection is one volume, the object's own mesh, of no cut. */
    val canReplace: Boolean = false,
    /** Plater::can_replace_all_with_stl(): the selection holds several volumes, of no cut. */
    val canReplaceAll: Boolean = false,
    /**
     * "Export as one STL" and "Export as one DRC": the whole object, or its
     * only copy. The desktop menu also offers them over one copy of several,
     * for which Plater::export_stl() writes an empty mesh.
     */
    val canExport: Boolean = false,
    /** append_menu_item_invalidate_cut_info(): the object is a part of a cut. */
    val cut: Boolean = false,
)

/** A filament of the plate as "Change Filament" lists it: its preset (Preset::label) and colour. */
data class MenuFilament(val name: String, val color: Color)

/**
 * The submenu Flush Options: whether its items apply (enable_prime_tower of
 * the process preset) and the options the object flushes into.
 */
data class FlushOptionsMenu(val enabled: Boolean, val checked: Set<FlushOption>)

/** The state of the menu of [plateObject] opened over its copy [instance]. */
fun objectMenuState(
    plateObject: PlateObject,
    instance: PlateInstance,
    plate: PlateDescription?,
    enabled: Boolean,
    wholeObject: Boolean = false,
    clipboard: PlateClipboard? = null,
    /** The Simplify gizmo is open. */
    simplifying: Boolean = false,
    filaments: List<MenuFilament> = emptyList(),
    /** The wipe tower as the engine last described it, with the process preset's flush options. */
    flushing: WipeTower? = null,
    settingsClipboard: SettingsClipboard? = null,
    /** The object list's clipboard: height ranges it holds go into the object. */
    listClipboard: ListClipboard? = null,
): ObjectMenuState {
    val inspection = instance.inspection
    val area = plate?.geometry?.printableArea.orEmpty()
    val minX = area.minOfOrNull { it.x } ?: 0.0
    val maxX = area.maxOfOrNull { it.x } ?: 0.0
    val minY = area.minOfOrNull { it.y } ?: 0.0
    val maxY = area.maxOfOrNull { it.y } ?: 0.0
    // PartPlate::get_center_origin()
    val centerX = (minX + maxX) / 2
    val centerY = (minY + maxY) / 2
    val minZ = inspection.boxCenter.z - inspection.dimensions.heightMillimeters / 2
    // PartPlate::contains() of the selection's box: within the plate, give or
    // take BuildVolume::BedEpsilon, at any height below 1000 mm.
    val inside = plate != null && (if (wholeObject) plateObject.instances else listOf(instance)).all { copy ->
        val box = copy.inspection
        val halfWidth = box.dimensions.widthMillimeters / 2
        val halfDepth = box.dimensions.depthMillimeters / 2
        box.boxCenter.x - halfWidth >= minX - BED_EPSILON && box.boxCenter.x + halfWidth <= maxX + BED_EPSILON &&
            box.boxCenter.y - halfDepth >= minY - BED_EPSILON && box.boxCenter.y + halfDepth <= maxY + BED_EPSILON &&
            box.boxCenter.z + box.dimensions.heightMillimeters / 2 <= MAX_PLATE_HEIGHT
    }
    return ObjectMenuState(
        enabled = enabled,
        // ObjectList::has_selected_cut_object() turns off what would break the cut.
        canAddInstance = enabled && plateObject.instances.all(PlateInstance::printable) && !plateObject.isCut,
        canRemoveInstance = enabled && plateObject.instances.size > 1 && !plateObject.isCut,
        canSetAsIndividual = enabled && plateObject.instances.size > 1,
        canMirror = enabled && !plateObject.isCut,
        wholeObject = wholeObject,
        canCenter = enabled && plate != null && (inspection.boxCenter.x != centerX || inspection.boxCenter.y != centerY),
        // SINKING_Z_THRESHOLD
        canDrop = enabled && abs(minZ) > SINKING_Z_THRESHOLD,
        autoDrop = instance.autoDrop,
        canSplitToObjects = enabled && (plateObject.parts.isNotEmpty() || plateObject.volume.splittable),
        canSplitToParts = enabled && plateObject.parts.isEmpty() && plateObject.volume.splittable,
        canSmooth = enabled && instance.inspection.openEdges == 0L,
        canMeshBoolean = enabled && (plateObject.parts.isNotEmpty() || plateObject.volume.splittable),
        canSimplify = enabled && !simplifying,
        conversions = conversionsOf((0..plateObject.parts.size).mapNotNull { plateObject.volumeAt(it) }.ifEmpty {
            listOf(plateObject.ownVolume())
        }),
        // Volumes join a single copy (Selection::is_from_single_instance);
        // the object list's height ranges come first.
        canPaste = enabled && listClipboard?.holdsRanges == true || enabled && when (clipboard) {
            is PlateClipboard.Objects -> true
            is PlateClipboard.Volumes -> !wholeObject || plateObject.instances.size == 1
            null -> false
        },
        printable = instance.printable,
        filaments = filaments.takeIf { it.size > 1 }.orEmpty(),
        // Shown once the plate prints more than one filament and the selection is on it.
        flushOptions = flushing?.takeIf { inside && it.filaments.distinct().size > 1 }?.let { tower ->
            FlushOptionsMenu(
                enabled = enabled && tower.primeTower,
                checked = FlushOption.entries.filterTo(mutableSetOf()) { plateObject.flushesInto(it, tower.flushInto) },
            )
        },
        canPasteSettings = enabled && settingsClipboard?.kind == SettingsItemKind.OBJECT,
        canReloadFromDisk = enabled && !plateObject.isCut && plateObject.reloadableVolumes().isNotEmpty(),
        canReplace = enabled && !plateObject.isCut && plateObject.parts.isEmpty() && (!wholeObject || plateObject.instances.size == 1),
        canReplaceAll = enabled && !plateObject.isCut && (plateObject.parts.isNotEmpty() || (wholeObject && plateObject.instances.size > 1)),
        canExport = enabled && (wholeObject || plateObject.instances.size == 1),
        cut = plateObject.isCut,
    )
}

/**
 * ModelObject::get_export_filename() with the format's extension, as
 * Plater::priv::get_export_file() suggests it: the object's name, its
 * extension replaced.
 */
fun exportFileName(name: String, format: MeshFormat): String =
    "${name.substringBeforeLast('.', name).ifEmpty { name }}.${format.extension}"

/** The object's own mesh as a volume, which volumeAt() lists only once the object has parts. */
internal fun PlateObject.ownVolume() = ObjectPart(
    shape = "",
    type = VolumeType.PART,
    mesh = mesh,
    placement = Transform3.IDENTITY,
    splittable = volume.splittable,
    convertedFromInches = volume.convertedFromInches,
    convertedFromMeters = volume.convertedFromMeters,
)

/**
 * append_menu_items_convert_unit(): a conversion is offered unless one of the
 * volumes already stands in the units it would give.
 */
fun conversionsOf(volumes: List<ObjectPart>): List<ObjectEdit> = listOf(
    ObjectEdit.CONVERT_FROM_INCHES to { part: ObjectPart -> part.convertedFromInches },
    ObjectEdit.RESTORE_TO_INCHES to { part: ObjectPart -> !part.convertedFromInches },
    ObjectEdit.CONVERT_FROM_METERS to { part: ObjectPart -> part.convertedFromMeters },
    ObjectEdit.RESTORE_TO_METERS to { part: ObjectPart -> !part.convertedFromMeters },
).filter { (_, respects) -> volumes.none(respects) }.map { it.first }

/** The text MenuFactory gives a conversion of units. */
fun conversionName(edit: ObjectEdit): String = when (edit) {
    ObjectEdit.CONVERT_FROM_INCHES -> "Convert from inches"
    ObjectEdit.RESTORE_TO_INCHES -> "Restore to inches"
    ObjectEdit.CONVERT_FROM_METERS -> "Convert from meters"
    else -> "Restore to meters"
}

/** What the items of the object menu do, for the copy it was opened over. */
class ObjectMenuActions(
    /** Plater::cut_selection_to_clipboard(), copy_selection_to_clipboard() and paste_from_clipboard(). */
    val cut: () -> Unit,
    val copy: () -> Unit,
    val paste: () -> Unit,
    val addInstance: () -> Unit,
    val removeInstance: () -> Unit,
    /** Opens the number of copies to ask for (Plater::set_number_of_copies). */
    val setNumberOfInstances: () -> Unit,
    val fillBedWithInstances: () -> Unit,
    /** ObjectList::split_instances() */
    val setAsIndividual: () -> Unit,
    /** Opens the clone dialog (Plater::clone_selection). */
    val clone: () -> Unit,
    val center: () -> Unit,
    val drop: () -> Unit,
    val mirror: (Axis) -> Unit,
    val delete: () -> Unit,
    /** Opens the shapes of a part of that kind. */
    val addPart: (VolumeType) -> Unit,
    val addHeightRange: () -> Unit,
    val setAutoDrop: (Boolean) -> Unit,
    /** The commands that change the object's meshes (split, fix, convert units). */
    val edit: (ObjectEdit) -> Unit,
    /** ObjectList::simplify(): the Simplify gizmo opens. */
    val simplify: () -> Unit,
    /** ObjectList::toggle_printable_state() */
    val setPrintable: (Boolean) -> Unit,
    /** ObjectList::set_extruder_for_selected_items(): the filament, 1-based. */
    val setFilament: (Int) -> Unit,
    val toggleFlushOption: (FlushOption) -> Unit,
    /** ObjectList::switch_to_object_process(): the object's own process settings are shown. */
    val editProcessSettings: () -> Unit,
    val copyProcessSettings: () -> Unit,
    val pasteProcessSettings: () -> Unit,
    /** Plater::reload_from_disk() of the object's volumes. */
    val reloadFromDisk: () -> Unit,
    /** Opens the file to replace the object's mesh with (Plater::replace_with_stl). */
    val replace: () -> Unit,
    /** Opens the folder whose files replace the object's volumes (Plater::replace_all_with_stl). */
    val replaceAll: () -> Unit,
    /** Opens where the object is exported to (Plater::export_stl). */
    val export: (MeshFormat) -> Unit,
    /** ObjectList::invalidate_cut_info_for_selection() */
    val invalidateCutInfo: () -> Unit,
    /** append_menu_item_edit_text(): the text tool opens on the object made of a text; null for none. */
    val editText: (() -> Unit)? = null,
    /** append_menu_item_edit_svg(): the SVG tool opens on the object made of an SVG; null for none. */
    val editSvg: (() -> Unit)? = null,
)

/**
 * MenuFactory::create_extra_object_menu(), the object menu of OrcaSlicer's
 * canvas and object list, with the items the app has, in its order.
 */
@Composable
fun ObjectMenuItems(state: ObjectMenuState, actions: ObjectMenuActions, dismiss: () -> Unit) {
    fun run(action: () -> Unit): () -> Unit = {
        dismiss()
        action()
    }
    // The Edit menu's Cut, Copy and Paste, which a phone offers where a finger
    // holds the object, as it offers them over text.
    ClipboardItems(enabled = state.enabled, canPaste = state.canPaste, cut = run(actions.cut), copy = run(actions.copy), paste = run(actions.paste))
    OrcaMenuSeparator()
    // append_menu_items_instance_manipulation()
    OrcaMenuItem(text = stringResource(R.string.object_menu_add_instance), enabled = state.canAddInstance, onClick = run(actions.addInstance))
    OrcaMenuItem(text = stringResource(R.string.object_menu_remove_instance), enabled = state.canRemoveInstance, onClick = run(actions.removeInstance))
    OrcaMenuItem(
        text = orcaString("Set number of instances") + DOTS,
        enabled = state.canAddInstance,
        onClick = run(actions.setNumberOfInstances),
    )
    OrcaMenuItem(
        text = orcaString("Fill bed with instances") + DOTS,
        enabled = state.canAddInstance,
        onClick = run(actions.fillBedWithInstances),
    )
    OrcaMenuSeparator()
    // append_menu_item_instance_to_object(): Plater::can_set_instance_to_object().
    SetAsIndividualItem(state.wholeObject, enabled = state.canSetAsIndividual, onClick = run(actions.setAsIndividual))
    OrcaMenuSeparator()
    OrcaMenuItem(text = orcaString("Clone"), enabled = state.enabled, onClick = run(actions.clone))
    // append_menu_item_fix_through_cgal(): FIX_THROUGH_CGAL_ALWAYS.
    OrcaMenuItem(text = orcaString("Fix model"), enabled = state.enabled, onClick = run { actions.edit(ObjectEdit.FIX) })
    OrcaMenuItem(text = orcaString("Simplify Model"), enabled = state.canSimplify, onClick = run(actions.simplify))
    SmoothMeshItem(enabled = state.canSmooth, onClick = run { actions.edit(ObjectEdit.SMOOTH_MESH) })
    // append_menu_item_merge_parts_to_single_part()
    OrcaMenuSeparator()
    OrcaMenuItem(text = orcaString("Mesh boolean"), enabled = state.canMeshBoolean, onClick = run { actions.edit(ObjectEdit.MESH_BOOLEAN) })
    OrcaMenuItem(text = orcaString("Center"), enabled = state.canCenter, onClick = run(actions.center))
    OrcaMenuItem(text = orcaString("Drop"), enabled = state.canDrop, onClick = run(actions.drop))
    OrcaSubmenu(text = orcaString("Split"), enabled = state.canSplitToObjects || state.canSplitToParts) {
        OrcaMenuItem(
            text = orcaString("To objects"),
            enabled = state.canSplitToObjects,
            onClick = run { actions.edit(ObjectEdit.SPLIT_TO_OBJECTS) },
            leading = { MenuIcon(DesignR.drawable.orca_menu_split_objects) },
        )
        OrcaMenuItem(
            text = orcaString("To parts"),
            enabled = state.canSplitToParts,
            onClick = run { actions.edit(ObjectEdit.SPLIT_TO_PARTS) },
            leading = { MenuIcon(DesignR.drawable.orca_menu_split_parts) },
        )
    }
    // append_menu_items_mirror()
    OrcaSubmenu(text = orcaString("Mirror"), enabled = state.canMirror) {
        MIRROR_ITEMS.forEach { (axis, text, icon) ->
            OrcaMenuItem(
                text = orcaString(text),
                enabled = state.canMirror,
                onClick = run { actions.mirror(axis) },
                leading = { MenuIcon(icon) },
            )
        }
    }
    OrcaMenuItem(text = stringResource(R.string.object_menu_delete), enabled = state.enabled, onClick = run(actions.delete))
    OrcaMenuSeparator()
    // append_menu_items_add_volume(): every kind of part the desktop app adds,
    // whose submenu of shapes a phone shows as a sheet once the kind is picked.
    VolumeType.entries.forEach { type ->
        OrcaMenuItem(text = stringResource(addPartName(type)), enabled = state.enabled, onClick = run { actions.addPart(type) })
    }
    OrcaMenuItem(text = stringResource(R.string.object_menu_height_range), enabled = state.enabled, onClick = run(actions.addHeightRange))
    OrcaMenuSeparator()
    // append_menu_item_printable(): the object list's check box.
    OrcaMenuCheckItem(
        text = orcaString("Printable"),
        checked = state.printable,
        enabled = state.enabled,
        onClick = run { actions.setPrintable(!state.printable) },
    )
    OrcaMenuSeparator()
    OrcaMenuCheckItem(
        text = stringResource(R.string.object_menu_auto_drop),
        checked = state.autoDrop,
        enabled = state.enabled,
        onClick = run { actions.setAutoDrop(!state.autoDrop) },
    )
    OrcaMenuSeparator()
    ProcessSettingsItems(
        enabled = state.enabled,
        canPaste = state.canPasteSettings,
        edit = run(actions.editProcessSettings),
        copy = run(actions.copyProcessSettings),
        paste = run(actions.pasteProcessSettings),
    )
    // MenuFactory::object_menu() puts Flush Options after the process settings.
    state.flushOptions?.let { flush ->
        OrcaSubmenu(text = orcaString("Flush Options"), enabled = state.enabled) {
            FlushOption.entries.forEach { option ->
                OrcaMenuCheckItem(
                    text = orcaString(option.label),
                    checked = option in flush.checked,
                    enabled = flush.enabled,
                    onClick = run { actions.toggleFlushOption(option) },
                )
            }
        }
    }
    OrcaMenuSeparator()
    // append_menu_item_reload_from_disk()
    OrcaMenuItem(text = orcaString("Reload from disk"), enabled = state.canReloadFromDisk, onClick = run(actions.reloadFromDisk))
    OrcaMenuItem(text = orcaString("Replace 3D file") + DOTS, enabled = state.canReplace, onClick = run(actions.replace))
    OrcaMenuItem(text = orcaString("Replace all with 3D files") + DOTS, enabled = state.canReplaceAll, onClick = run(actions.replaceAll))
    // append_menu_item_export_stl() and append_menu_item_export_drc()
    OrcaMenuItem(text = orcaString("Export as one STL") + DOTS, enabled = state.canExport, onClick = run { actions.export(MeshFormat.STL) })
    OrcaMenuItem(text = orcaString("Export as one DRC") + DOTS, enabled = state.canExport, onClick = run { actions.export(MeshFormat.DRC) })
    // MenuFactory::object_menu() appends the conversions it allows, then the
    // filaments to print with.
    if (state.conversions.isNotEmpty()) OrcaMenuSeparator()
    state.conversions.forEach { conversion ->
        OrcaMenuItem(text = orcaString(conversionName(conversion)), enabled = state.enabled, onClick = run { actions.edit(conversion) })
    }
    // MenuFactory::object_menu(): "Invalidate cut info" for a part of a cut.
    if (state.cut) OrcaMenuItem(text = orcaString("Invalidate cut info"), enabled = state.enabled, onClick = run(actions.invalidateCutInfo))
    actions.editText?.let { edit -> OrcaMenuItem(text = orcaString("Edit text"), enabled = state.enabled, onClick = run(edit)) }
    actions.editSvg?.let { edit -> OrcaMenuItem(text = orcaString("Edit SVG"), enabled = state.enabled, onClick = run(edit)) }
    ChangeFilamentItem(state.filaments, withDefault = false, enabled = state.enabled, onPick = { filament -> dismiss(); actions.setFilament(filament) })
}

/** append_menu_item_smooth_mesh(): the mesh subdivided, its painting lost. */
@Composable
fun SmoothMeshItem(enabled: Boolean, onClick: () -> Unit) {
    OrcaMenuItem(text = orcaString("Subdivision mesh") + orcaString("(Lost color)"), enabled = enabled, onClick = onClick)
}

/**
 * append_menu_item_per_object_process(): the item's own process settings, and
 * copying them to another item of the same kind.
 */
@Composable
fun ProcessSettingsItems(enabled: Boolean, canPaste: Boolean, edit: () -> Unit, copy: () -> Unit, paste: () -> Unit) {
    OrcaMenuItem(text = orcaString("Edit Process Settings"), enabled = enabled, onClick = edit)
    OrcaMenuItem(text = orcaString("Copy Process Settings"), enabled = enabled, onClick = copy)
    OrcaMenuItem(text = orcaString("Paste Process Settings"), enabled = canPaste, onClick = paste)
}

/**
 * append_menu_item_change_filament(): a submenu of the plate's filaments, with
 * their presets and colours; "Default" leaves a modifier to the filament of
 * its object. Nothing while the plate has a single filament.
 */
@Composable
fun ChangeFilamentItem(filaments: List<MenuFilament>, withDefault: Boolean, enabled: Boolean, onPick: (Int) -> Unit) {
    if (filaments.size <= 1) return
    OrcaSubmenu(text = orcaString("Change Filament"), enabled = enabled) {
        if (withDefault) OrcaMenuItem(text = orcaString("Default"), enabled = enabled, onClick = { onPick(0) })
        filaments.forEachIndexed { index, filament ->
            OrcaMenuItem(
                text = filament.name,
                enabled = enabled,
                onClick = { onPick(index + 1) },
                leading = { OrcaFilamentSlot(number = index + 1, color = filament.color) },
            )
        }
    }
}

/**
 * MainFrame's Edit menu: Cut, Copy and Paste, with its texts. [cut] is null
 * where nothing can be cut.
 */
@Composable
fun ClipboardItems(enabled: Boolean, canPaste: Boolean, cut: (() -> Unit)?, copy: () -> Unit, paste: () -> Unit) {
    OrcaMenuItem(text = orcaString("Cut"), enabled = enabled && cut != null, onClick = cut ?: {})
    OrcaMenuItem(text = orcaString("Copy"), enabled = enabled, onClick = copy)
    OrcaMenuItem(text = orcaString("Paste"), enabled = canPaste, onClick = paste)
}

/**
 * append_menu_item_instance_to_object(): its text follows the selection, the
 * whole object or some of its copies.
 */
@Composable
fun SetAsIndividualItem(wholeObject: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OrcaMenuItem(
        text = orcaString(if (wholeObject) "Set as individual objects" else "Set as an individual object"),
        enabled = enabled,
        onClick = onClick,
    )
}

/** The icon MenuFactory gives an item. */
@Composable
private fun MenuIcon(icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
}

/** The shapes of the submenus that add a part (MenuFactory::append_submenu_add_generic), in their order. */
val PART_SHAPES = listOf("Cube", "Cylinder", "Sphere", "Cone", "Disc", "Torus")

/** What the menu calls adding a part of that kind (MenuFactory). */
fun addPartName(type: VolumeType): Int = when (type) {
    VolumeType.PART -> R.string.object_menu_add_part
    VolumeType.NEGATIVE -> R.string.object_menu_add_negative
    VolumeType.MODIFIER -> R.string.object_menu_add_modifier
    VolumeType.SUPPORT_BLOCKER -> R.string.object_menu_add_support_blocker
    VolumeType.SUPPORT_ENFORCER -> R.string.object_menu_add_support_enforcer
}

/** What the sheet of shapes calls one of them. */
fun shapeName(shape: String): Int = when (shape) {
    "Cube" -> R.string.object_shape_cube
    "Cylinder" -> R.string.object_shape_cylinder
    "Sphere" -> R.string.object_shape_sphere
    "Cone" -> R.string.object_shape_cone
    "Disc" -> R.string.object_shape_disc
    else -> R.string.object_shape_torus
}

/**
 * The submenu of the desktop app's "Add part" items
 * (MenuFactory::append_submenu_add_generic): "Load..." (ObjectList::load_subobject),
 * then the shapes a part can have (create_mesh of GUI_ObjectList.cpp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartShapeSheet(
    type: VolumeType,
    onDismiss: () -> Unit,
    onChoose: (shape: String, name: String) -> Unit,
    onText: (() -> Unit)? = null,
    onSvg: (() -> Unit)? = null,
    onLoad: (() -> Unit)? = null,
) {
    val colors = OrcaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(addPartName(type)),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (onLoad != null) {
                Text(
                    text = orcaString("Load..."),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .orcaClickable(role = Role.Button, onClick = onLoad)
                        .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = colors.separator, thickness = 1.dp)
            }
            PART_SHAPES.forEach { shape ->
                // ObjectList::load_generic_subobject() names the volume "Generic-<shape>", translated.
                val name = orcaString("Generic") + "-" + orcaString(shape)
                Text(
                    text = stringResource(shapeName(shape)),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .orcaClickable(role = Role.Button, onClick = { onChoose(shape, name) })
                        .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            // append_menu_item_add_text() and append_menu_item_add_svg(): a text or an
            // SVG of a part, a negative part or a modifier.
            if (type == VolumeType.PART || type == VolumeType.NEGATIVE || type == VolumeType.MODIFIER) {
                listOfNotNull(onText?.let { "Text" to it }, onSvg?.let { "SVG" to it }).forEach { (label, onClick) ->
                    Text(
                        text = orcaString(label),
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .orcaClickable(role = Role.Button, onClick = onClick)
                            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Plater::set_number_of_copies(): GetNumberFromUser() asks for the number of
 * copies of the object, from 0 to 1000.
 */
@Composable
fun NumberOfInstancesDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val number = text.toIntOrNull()?.takeIf { it in 0..MAX_COPIES }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { number?.let(onConfirm) }, enabled = number != null) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Copies of the selected object"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("Number of copies:"), style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter(Char::isDigit).take(4) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { number?.let(onConfirm) }),
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * CloneDialog: the number of copies, from 1 to 1000, and whether the plate is
 * arranged after cloning, which the preference "Auto arrange plate after
 * cloning" sets at first. Fill fills the bed with instances of the object
 * instead (Plater::fill_bed_with_instances).
 */
@Composable
fun CloneDialog(autoArrange: Boolean, onDismiss: () -> Unit, onFill: () -> Unit, onClone: (count: Int, arrange: Boolean) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf("1") }
    var arrange by rememberSaveable { mutableStateOf(autoArrange) }
    val count = text.toIntOrNull()?.takeIf { it in 1..MAX_COPIES }
    AlertDialog(
        onDismissRequest = onDismiss,
        // DialogButtons {"Fill", "OK", "Cancel"}: Fill on the left, alone.
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrcaButton(orcaString("Fill"), onClick = onFill, style = OrcaButtonStyle.Regular)
                Spacer(Modifier.weight(1f))
                OrcaButton(orcaString("OK"), onClick = { count?.let { onClone(it, arrange) } }, enabled = count != null)
                OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular)
            }
        },
        title = { Text(orcaString("Clone"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("Number of copies:"), style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter(Char::isDigit).take(4) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { count?.let { onClone(it, arrange) } }),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                        .toggleable(value = arrange, role = Role.Checkbox, onValueChange = { arrange = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(orcaString("Auto arrange plate after cloning") + ":", style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
                    OrcaCheckBox(checked = arrange, onCheckedChange = null)
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * ObjectList::rename_item(): wxGetTextFromUser() asks for the new name. The
 * desktop app refuses a name with a character a file name cannot hold once OK
 * is pressed; here the field says so while it holds one.
 */
@Composable
fun RenameDialog(name: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf(name) }
    val illegal = text.any { it in ILLEGAL_CHARACTERS }
    val canRename = text.isNotEmpty() && !illegal
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onRename(text) }, enabled = canRename) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Renaming"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("Enter new name") + ":", style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canRename) onRename(text) }),
                )
                if (illegal) {
                    // Plater::show_illegal_characters_warning()
                    Text(
                        orcaString("Invalid name, the following characters are not allowed:") + " " + ILLEGAL_CHARACTERS,
                        color = colors.error,
                        style = OrcaTheme.typography.body13,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** Plater::has_illegal_filename_characters() */
private const val ILLEGAL_CHARACTERS = "<>:/\\|?*\""

/** BuildVolume::BedEpsilon */
private const val BED_EPSILON = 3e-4

/** The height PartPlate::contains() allows the selection. */
private const val MAX_PLATE_HEIGHT = 1e3

/** GetNumberFromUser(..., 0, 1000, ...) of Plater::set_number_of_copies(). */
private const val MAX_COPIES = 1000

/** SINKING_Z_THRESHOLD of libslic3r, as a distance from the plate. */
private const val SINKING_Z_THRESHOLD = 0.001

/** GUI::dots */
internal const val DOTS = "..."

/** The submenu of append_menu_items_mirror(): the axis, its text and its icon. */
private val MIRROR_ITEMS = listOf(
    Triple(Axis.X, "Along X axis", DesignR.drawable.orca_menu_mirror_x),
    Triple(Axis.Y, "Along Y axis", DesignR.drawable.orca_menu_mirror_y),
    Triple(Axis.Z, "Along Z axis", DesignR.drawable.orca_menu_mirror_z),
)
