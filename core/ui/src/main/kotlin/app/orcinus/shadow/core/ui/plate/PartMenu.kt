package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.reloadableVolumes
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * What the part menu offers for a volume selected alone (Selection::Volume),
 * from its row of the object list or on the canvas, with the conditions
 * MenuFactory enables its items by.
 */
data class PartMenuState(
    val enabled: Boolean,
    /** The volume's type, which "Change type" checks and which takes a filament as a part or a modifier. */
    val type: VolumeType,
    /** text_part_menu() and svg_part_menu(): the volume is embossed from a text or an SVG; null for neither. */
    val embossed: EmbossKind?,
    /** Plater::can_paste_from_clipboard(): the clipboard holds volumes to join the object. */
    val canPaste: Boolean,
    /** Plater::can_simplify(): the Simplify gizmo is not open already. */
    val canSimplify: Boolean,
    /** Plater::can_smooth_mesh() goes by the object's meshes. */
    val canSmooth: Boolean,
    /** Plater::can_mirror(): no part of a cut. */
    val canMirror: Boolean,
    /** "To parts": the volume's mesh has several shells. */
    val canSplitToParts: Boolean,
    /** ObjectList::can_paste_settings_into_list(): the process settings copied are a volume's. */
    val canPasteSettings: Boolean,
    /** append_menu_item_reload_from_disk(): Plater::can_reload_from_disk() of the volume. */
    val canReloadFromDisk: Boolean,
    /** Plater::can_replace_with_stl(): the volume selected alone, of no cut. */
    val canReplace: Boolean,
    /** append_menu_items_convert_unit(): the conversions the volume allows. */
    val conversions: List<ObjectEdit>,
    /** append_menu_item_change_filament(): the filaments of the plate. */
    val filaments: List<MenuFilament>,
)

/** The state of the part menu of the volume [at] of [plateObject], picked over its copy [instance]; null when it has no such volume. */
fun partMenuState(
    plateObject: PlateObject,
    at: Int,
    instance: PlateInstance,
    enabled: Boolean,
    clipboard: PlateClipboard? = null,
    /** The Simplify gizmo is open. */
    simplifying: Boolean = false,
    filaments: List<MenuFilament> = emptyList(),
    settingsClipboard: SettingsClipboard? = null,
): PartMenuState? {
    val part = plateObject.volumeAt(at) ?: return null
    return PartMenuState(
        enabled = enabled,
        type = part.type,
        embossed = part.emboss?.kind,
        canPaste = enabled && clipboard is PlateClipboard.Volumes,
        canSimplify = enabled && !simplifying,
        canSmooth = enabled && instance.inspection.openEdges == 0L,
        canMirror = enabled && !plateObject.isCut,
        canSplitToParts = enabled && part.splittable,
        canPasteSettings = enabled && settingsClipboard?.kind == SettingsItemKind.VOLUME,
        canReloadFromDisk = enabled && !plateObject.isCut && at in plateObject.reloadableVolumes(),
        canReplace = enabled && !plateObject.isCut,
        conversions = conversionsOf(listOf(part)),
        filaments = filaments,
    )
}

/** What the items of the part menu do, for the volume it was opened over. */
class PartMenuActions(
    /** ObjectList::rename_item(): opens the name to ask for. */
    val rename: () -> Unit,
    /** The Edit menu's Cut, Copy and Paste of the volume, over the copy it is picked on. */
    val cut: () -> Unit,
    val copy: () -> Unit,
    val paste: () -> Unit,
    /** append_menu_item_edit_text() and append_menu_item_edit_svg(): the volume's tool opens. */
    val editText: () -> Unit,
    val editSvg: () -> Unit,
    /** ObjectList::del_subobject_item() */
    val delete: () -> Unit,
    /** The commands that change the volume's mesh (fix, smooth, split, convert units). */
    val edit: (ObjectEdit) -> Unit,
    /** ObjectList::simplify(): the Simplify gizmo opens on the volume. */
    val simplify: () -> Unit,
    val center: () -> Unit,
    val drop: () -> Unit,
    val mirror: (Axis) -> Unit,
    /** ObjectList::switch_to_object_process(): the volume's own process settings are shown. */
    val editProcessSettings: () -> Unit,
    val copyProcessSettings: () -> Unit,
    val pasteProcessSettings: () -> Unit,
    /** ObjectList::set_volume_type() */
    val changeType: (VolumeType) -> Unit,
    /** Plater::reload_from_disk() of the volume. */
    val reloadFromDisk: () -> Unit,
    /** Opens the file to replace the volume's mesh with (Plater::replace_with_stl). */
    val replace: () -> Unit,
    /** ObjectList::set_extruder_for_selected_items(): the filament, 1-based; 0 for the object's. */
    val setFilament: (Int) -> Unit,
    /** append_menu_item_per_object_settings(): the Parameter Table opens on the volume's row; null for none. */
    val editInParameterTable: (() -> Unit)? = null,
)

/**
 * MenuFactory::create_bbl_part_menu() and part_menu() of a volume selected
 * alone, or text_part_menu() and svg_part_menu() of one embossed from a text
 * or an SVG, with the items the app has; the object list's row of a volume
 * and the canvas (Plater::priv::on_right_click) open it.
 */
@Composable
fun PartMenuItems(state: PartMenuState, actions: PartMenuActions, dismiss: () -> Unit) {
    fun run(action: () -> Unit): () -> Unit = {
        dismiss()
        action()
    }
    val enabled = state.enabled
    RenameItem(enabled, onClick = run(actions.rename))
    // MenuFactory::text_part_menu() and svg_part_menu() of a volume embossed
    // from a text or an SVG: its tool, and fewer edits of its mesh.
    val embossed = state.embossed
    // The Edit menu for the volume, over the copy it is picked on; the
    // object's own mesh cannot be cut out of it yet.
    ClipboardItems(enabled = enabled, canPaste = state.canPaste, cut = run(actions.cut), copy = run(actions.copy), paste = run(actions.paste))
    OrcaMenuSeparator()
    if (embossed != null) {
        OrcaMenuItem(
            text = orcaString(if (embossed == EmbossKind.TEXT) "Edit text" else "Edit SVG"),
            enabled = enabled,
            onClick = run(if (embossed == EmbossKind.TEXT) actions.editText else actions.editSvg),
        )
    }
    // ObjectList::del_subobject_item(), the object's own mesh too.
    OrcaMenuItem(text = stringResource(R.string.object_menu_delete), enabled = enabled, onClick = run(actions.delete))
    // MenuFactory::create_bbl_part_menu(), with the items the app has.
    OrcaMenuItem(text = orcaString("Fix model"), enabled = enabled, onClick = run { actions.edit(ObjectEdit.FIX) })
    OrcaMenuItem(text = orcaString("Simplify Model"), enabled = state.canSimplify, onClick = run(actions.simplify))
    // Plater::can_smooth_mesh() goes by the object's meshes.
    if (embossed == null) SmoothMeshItem(enabled = state.canSmooth, onClick = run { actions.edit(ObjectEdit.SMOOTH_MESH) })
    // append_menu_item_center(), append_menu_item_drop() and append_menu_items_mirror() of the volume;
    // text_part_menu() has no Drop, and svg_part_menu() neither Center nor Drop.
    if (embossed != EmbossKind.SVG) OrcaMenuItem(text = orcaString("Center"), enabled = enabled, onClick = run(actions.center))
    if (embossed == null) OrcaMenuItem(text = orcaString("Drop"), enabled = enabled, onClick = run(actions.drop))
    MirrorSubmenu(enabled = state.canMirror) { axis -> run { actions.mirror(axis) }() }
    // can_split(true): ObjectList::is_splittable(true) refuses a volume, which turns the submenu off.
    if (embossed == null) OrcaSubmenu(text = orcaString("Split"), enabled = false) {
        // ObjectList::is_splittable(true) refuses a volume.
        OrcaMenuItem(text = orcaString("To objects"), enabled = false, onClick = {})
        OrcaMenuItem(text = orcaString("To parts"), enabled = state.canSplitToParts, onClick = run { actions.edit(ObjectEdit.SPLIT_TO_PARTS) })
    }
    // MenuFactory::part_menu() appends the conversions of the volume.
    OrcaMenuSeparator()
    ProcessSettingsItems(
        enabled = enabled,
        canPaste = state.canPasteSettings,
        edit = run(actions.editProcessSettings),
        copy = run(actions.copyProcessSettings),
        paste = run(actions.pasteProcessSettings),
    )
    // append_menu_item_change_type(): the kinds of volume, the volume's checked;
    // a text or an SVG is no support blocker or enforcer.
    OrcaSubmenu(text = orcaString("Change type"), enabled = enabled) {
        VOLUME_TYPES.forEach { (type, label) ->
            val support = type == VolumeType.SUPPORT_BLOCKER || type == VolumeType.SUPPORT_ENFORCER
            OrcaMenuCheckItem(
                text = orcaString(label),
                checked = state.type == type,
                enabled = enabled && !(support && embossed != null),
                onClick = run { actions.changeType(type) },
            )
        }
    }
    // append_menu_item_reload_from_disk(): Plater::can_reload_from_disk() of the volume, which
    // text_part_menu() and svg_part_menu() do not offer.
    if (embossed == null) OrcaMenuItem(text = orcaString("Reload from disk"), enabled = state.canReloadFromDisk, onClick = run(actions.reloadFromDisk))
    // Plater::can_replace_with_stl(): the volume is selected alone, of no cut.
    if (embossed == null) OrcaMenuItem(text = orcaString("Replace 3D file") + DOTS, enabled = state.canReplace, onClick = run(actions.replace))
    if (embossed == null) state.conversions.forEach { conversion ->
        OrcaMenuItem(text = orcaString(conversionName(conversion)), enabled = enabled, onClick = run { actions.edit(conversion) })
    }
    // MenuFactory::part_menu(): a part of the model or a modifier takes a
    // filament; a modifier may follow its object's ("Default").
    if (state.type == VolumeType.PART || state.type == VolumeType.MODIFIER) {
        ChangeFilamentItem(
            state.filaments,
            withDefault = state.type == VolumeType.MODIFIER,
            enabled = enabled,
            onPick = { filament ->
                dismiss()
                actions.setFilament(filament)
            },
        )
    }
    // part_menu(), text_part_menu() and svg_part_menu() append it last; it
    // takes Selection::is_single_volume(), a part of the model alone, as any
    // other volume selected alone is is_single_modifier().
    actions.editInParameterTable?.let { ParameterTableItem(enabled = enabled && state.type == VolumeType.PART, onClick = run(it)) }
}

/** The types "Change type" offers, with their texts (MenuFactory::append_menu_item_change_type). */
private val VOLUME_TYPES = listOf(
    VolumeType.PART to "Part",
    VolumeType.NEGATIVE to "Negative Part",
    VolumeType.MODIFIER to "Modifier",
    VolumeType.SUPPORT_BLOCKER to "Support Blocker",
    VolumeType.SUPPORT_ENFORCER to "Support Enforcer",
)
