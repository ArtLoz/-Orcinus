package app.orcinus.shadow.feature.preferences.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.core.ui.navigation.overlayPageMetadata
import app.orcinus.shadow.feature.preferences.PreferencesRoute
import app.orcinus.shadow.feature.preferences.PreferencesViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Preferences, opened over the whole window. */
@Serializable
data object PreferencesNavKey : NavKey

/**
 * Registers the Preferences page. [createViewModel] builds its view model from
 * the app's dependencies; [onBack] closes the page, and [onOpenNetworkTest]
 * opens the Network Test its "Network test" offers.
 */
fun EntryProviderScope<NavKey>.preferencesEntry(
    createViewModel: () -> PreferencesViewModel,
    onBack: () -> Unit,
    onOpenNetworkTest: () -> Unit = {},
) {
    // The page opens over the workspace, as the desktop app opens its dialog over the plater.
    entry<PreferencesNavKey>(metadata = overlayPageMetadata) {
        PreferencesRoute(viewModel = viewModel { createViewModel() }, onBack = onBack, onOpenNetworkTest = onOpenNetworkTest)
    }
}
