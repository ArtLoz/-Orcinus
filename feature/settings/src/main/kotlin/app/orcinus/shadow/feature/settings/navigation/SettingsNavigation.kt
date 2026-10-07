package app.orcinus.shadow.feature.settings.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.ui.navigation.overlayPageMetadata
import app.orcinus.shadow.feature.settings.PresetSettingsRoute
import app.orcinus.shadow.feature.settings.PresetSettingsViewModel
import kotlinx.serialization.Serializable

/**
 * The settings of one preset kind, opened over the whole window from the
 * sidebar. [option] is the setting the search found, which the page opens on
 * its [page] and marks (Tab::activate_option). EditFilamentPresetDialog opens
 * the filament tab on [editedPreset] of the filament [editingFilament]
 * (ParamsDialog::set_editing_filament_id()), which it edits alone.
 */
@Serializable
data class PresetSettingsNavKey(
    val kind: PresetKind,
    val option: String? = null,
    val page: String? = null,
    val editingFilament: String? = null,
    val editedPreset: String? = null,
) : NavKey

/**
 * Registers the settings page. [createViewModel] builds its view model from the
 * app's dependencies; [onBack] closes the page.
 */
fun EntryProviderScope<NavKey>.presetSettingsEntry(
    createViewModel: (PresetKind) -> PresetSettingsViewModel,
    onBack: () -> Unit,
    /** A setting the search found on another tab opens that tab's page on it. */
    onOpenTab: (SearchOption) -> Unit = {},
) {
    // The page opens over the workspace, as the desktop app opens its
    // ParamsDialog over the plater.
    entry<PresetSettingsNavKey>(metadata = overlayPageMetadata) { key ->
        PresetSettingsRoute(
            viewModel = viewModel { createViewModel(key.kind) },
            onBack = onBack,
            onOpenTab = onOpenTab,
            openOption = key.option,
            openPage = key.page,
            editedPreset = key.editedPreset.takeIf { key.editingFilament != null },
        )
    }
}
