package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.plateCenter
import app.orcinus.shadow.core.model.selectedCopies
import app.orcinus.shadow.core.model.selectedObjectMeshes
import app.orcinus.shadow.core.model.selectionBox
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString

/** What MenuFactory::multi_selection_menu() offers for several objects selected whole. */
data class SelectionMenuState(
    val enabled: Boolean,
    /** append_menu_item_center(): the selection's box is not centred on the plate. */
    val canCenter: Boolean,
    /** append_menu_item_drop(): the selection's box does not stand on the plate. */
    val canDrop: Boolean,
    val canPaste: Boolean,
    /** append_menu_item_set_printable(): every selected object prints. */
    val printable: Boolean,
    /** Selection::get_auto_drop(): every copy of the selection drops automatically. */
    val autoDrop: Boolean,
    val canPasteSettings: Boolean,
    val filaments: List<MenuFilament>,
)

/** The selection holds several objects whole, which the multi-selection menu is for. */
fun PlateState.selectsSeveralObjects(): Boolean = selectedObjectMeshes().size > 1

fun selectionMenuState(
    state: PlateState,
    enabled: Boolean,
    clipboard: PlateClipboard?,
    settingsClipboard: SettingsClipboard?,
    filaments: List<MenuFilament>,
): SelectionMenuState {
    val box = state.selectionBox()
    val center = state.plateCenter()
    val copies = state.selectedCopies().mapNotNull { id -> state.objects.firstOrNull { it.mesh == id.mesh }?.instances?.getOrNull(id.instance) }
    return SelectionMenuState(
        enabled = enabled,
        canCenter = enabled && box != null && center != null && (box.centerX != center.x || box.centerY != center.y),
        // SINKING_Z_THRESHOLD
        canDrop = enabled && box != null && kotlin.math.abs(box.minZ) > SINKING_Z_THRESHOLD,
        canPaste = enabled && clipboard != null,
        printable = copies.all { it.printable },
        autoDrop = copies.all { it.autoDrop },
        // ObjectList::can_paste_settings_into_list(): settings of objects for the objects.
        canPasteSettings = enabled && settingsClipboard?.kind == SettingsItemKind.OBJECT,
        filaments = filaments.takeIf { it.size > 1 }.orEmpty(),
    )
}

/** What the items of the multi-selection menu do to the selected objects. */
class SelectionMenuActions(
    val cut: () -> Unit,
    val copy: () -> Unit,
    val paste: () -> Unit,
    val center: () -> Unit,
    val drop: () -> Unit,
    val delete: () -> Unit,
    val setPrintable: (Boolean) -> Unit,
    val setAutoDrop: (Boolean) -> Unit,
    /** ObjectList::switch_to_object_process(): the settings of the selected objects are shown. */
    val editProcessSettings: () -> Unit,
    val pasteProcessSettings: () -> Unit,
    /** The filament of every selected object, 1-based. */
    val setFilament: (Int) -> Unit,
    /** Opens where the selection is exported to: one file, or a file each with [multi]. */
    val export: (format: MeshFormat, multi: Boolean) -> Unit,
)

/**
 * MenuFactory::multi_selection_menu() for objects, in its order, after the
 * Edit menu's Cut, Copy and Paste as the object menu offers them. "Assemble",
 * "Fix model", the conversions of units and "Replace all with 3D files" are
 * not here yet.
 */
@Composable
fun SelectionMenuItems(state: SelectionMenuState, actions: SelectionMenuActions, dismiss: () -> Unit) {
    fun run(action: () -> Unit): () -> Unit = {
        dismiss()
        action()
    }
    ClipboardItems(enabled = state.enabled, canPaste = state.canPaste, cut = run(actions.cut), copy = run(actions.copy), paste = run(actions.paste))
    OrcaMenuSeparator()
    OrcaMenuItem(text = orcaString("Center"), enabled = state.canCenter, onClick = run(actions.center))
    OrcaMenuItem(text = orcaString("Drop"), enabled = state.canDrop, onClick = run(actions.drop))
    OrcaMenuItem(text = stringResource(R.string.object_menu_delete), enabled = state.enabled, onClick = run(actions.delete))
    OrcaMenuSeparator()
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
    // append_menu_item_per_object_process(): Copy takes a single item, so it waits here.
    OrcaMenuItem(text = orcaString("Edit Process Settings"), enabled = state.enabled, onClick = run(actions.editProcessSettings))
    OrcaMenuItem(text = orcaString("Copy Process Settings"), enabled = false, onClick = {})
    OrcaMenuItem(text = orcaString("Paste Process Settings"), enabled = state.canPasteSettings, onClick = run(actions.pasteProcessSettings))
    OrcaMenuSeparator()
    ChangeFilamentItem(state.filaments, withDefault = false, enabled = state.enabled, onPick = { filament -> dismiss(); actions.setFilament(filament) })
    OrcaMenuSeparator()
    // append_menu_item_export_stl(menu, true) and append_menu_item_export_drc(menu, true)
    OrcaMenuItem(text = orcaString("Export as one STL") + DOTS, enabled = state.enabled, onClick = run { actions.export(MeshFormat.STL, false) })
    OrcaMenuItem(text = orcaString("Export as STLs") + DOTS, enabled = state.enabled, onClick = run { actions.export(MeshFormat.STL, true) })
    OrcaMenuItem(text = orcaString("Export as one DRC") + DOTS, enabled = state.enabled, onClick = run { actions.export(MeshFormat.DRC, false) })
    OrcaMenuItem(text = orcaString("Export as DRCs") + DOTS, enabled = state.enabled, onClick = run { actions.export(MeshFormat.DRC, true) })
}

private const val SINKING_Z_THRESHOLD = 0.001
