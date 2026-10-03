package app.orcinus.shadow.feature.home.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.feature.home.HomeRoute
import app.orcinus.shadow.feature.home.HomeViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Home tab (MainFrame::tpHome): the home page (WebViewPanel). */
@Serializable
data object HomeNavKey : NavKey

/**
 * Registers the Home tab; [createViewModel] builds its view model from the
 * app's dependencies, and [onShowPrepare] shows the Prepare tab, where a
 * project that could not be opened tells why.
 */
fun EntryProviderScope<NavKey>.homeEntry(createViewModel: () -> HomeViewModel, onShowPrepare: () -> Unit) {
    entry<HomeNavKey> {
        HomeRoute(viewModel = viewModel { createViewModel() }, onShowPrepare = onShowPrepare)
    }
}
