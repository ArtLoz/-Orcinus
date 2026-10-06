package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * What MenuFactory::multi_selection_menu() offers while the selection is
 * several volumes of one object (its multi_volume branch), with the
 * conditions it enables its items by.
 */
data class VolumesMenuState(
    val enabled: Boolean,
    /** append_menu_item_change_type(): a type is checked while a selected volume has it. */
    val types: Set<VolumeType>,
    /** A text or an SVG is selected, which is no support blocker or enforcer. */
    val embossed: Boolean,
    /** ObjectList::can_paste_settings_into_list(): the process settings copied are a volume's. */
    val canPasteSettings: Boolean,
    /** append_menu_item_change_filament(): "Default" while a modifier is selected. */
    val withDefaultFilament: Boolean,
    val filaments: List<MenuFilament>,
)

/** The menu's state for the volumes [volumes] (ObjectPartId.index) of [plateObject]. */
fun volumesMenuState(
    plateObject: PlateObject,
    volumes: List<Int>,
    enabled: Boolean,
    filaments: List<MenuFilament> = emptyList(),
    settingsClipboard: SettingsClipboard? = null,
): VolumesMenuState {
    val parts = volumes.mapNotNull { plateObject.volumeAt(it) }
    return VolumesMenuState(
        enabled = enabled,
        types = parts.mapTo(HashSet()) { it.type },
        embossed = parts.any { it.emboss != null },
        canPasteSettings = enabled && settingsClipboard?.kind == SettingsItemKind.VOLUME,
        withDefaultFilament = parts.any { it.type == VolumeType.MODIFIER },
        filaments = filaments,
    )
}

/** What the items of the menu do to the selected volumes. */
class VolumesMenuActions(
    /** Selection::erase() of the volumes (Plater::remove_selected()). */
    val delete: () -> Unit,
    /** ObjectList::switch_to_object_process(): the settings of the volumes are shown. */
    val editProcessSettings: () -> Unit,
    val pasteProcessSettings: () -> Unit,
    /** ObjectList::set_volume_type() of the volumes. */
    val changeType: (VolumeType) -> Unit,
    /** ObjectList::set_extruder_for_selected_items(): the filament, 1-based; 0 for "Default". */
    val setFilament: (Int) -> Unit,
)

/**
 * MenuFactory::multi_selection_menu() of several volumes of one object, in
 * its order, with the items the app has for them.
 */
@Composable
fun VolumesMenuItems(state: VolumesMenuState, actions: VolumesMenuActions, dismiss: () -> Unit) {
    fun run(action: () -> Unit): () -> Unit = {
        dismiss()
        action()
    }
    val enabled = state.enabled
    OrcaMenuItem(text = stringResource(R.string.object_menu_delete), enabled = enabled, onClick = run(actions.delete))
    // can_split(): ObjectList::is_splittable() takes a single item, so both are off.
    OrcaSubmenu(text = orcaString("Split"), enabled = false) {
        OrcaMenuItem(text = orcaString("To objects"), enabled = false, onClick = {})
        OrcaMenuItem(text = orcaString("To parts"), enabled = false, onClick = {})
    }
    // append_menu_item_per_object_process(): Edit takes is_multiple_volume(), Copy a single item.
    OrcaMenuItem(text = orcaString("Edit Process Settings"), enabled = enabled, onClick = run(actions.editProcessSettings))
    OrcaMenuItem(text = orcaString("Copy Process Settings"), enabled = false, onClick = {})
    OrcaMenuItem(text = orcaString("Paste Process Settings"), enabled = state.canPasteSettings, onClick = run(actions.pasteProcessSettings))
    OrcaMenuSeparator()
    OrcaSubmenu(text = orcaString("Change type"), enabled = enabled) {
        listOf(
            VolumeType.PART to "Part",
            VolumeType.NEGATIVE to "Negative Part",
            VolumeType.MODIFIER to "Modifier",
            VolumeType.SUPPORT_BLOCKER to "Support Blocker",
            VolumeType.SUPPORT_ENFORCER to "Support Enforcer",
        ).forEach { (type, label) ->
            val support = type == VolumeType.SUPPORT_BLOCKER || type == VolumeType.SUPPORT_ENFORCER
            OrcaMenuCheckItem(
                text = orcaString(label),
                checked = type in state.types,
                enabled = enabled && !(support && state.embossed),
                onClick = run { actions.changeType(type) },
            )
        }
    }
    // multi_selection_menu() appends "Change Filament" twice; the second call
    // takes away the first one's item, so it stands once, here at the end.
    ChangeFilamentItem(
        state.filaments,
        withDefault = state.withDefaultFilament,
        enabled = enabled,
        onPick = { filament ->
            dismiss()
            actions.setFilament(filament)
        },
    )
}
