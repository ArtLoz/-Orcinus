package app.orcinus.shadow.feature.prepare.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.feature.prepare.PrepareRoute
import app.orcinus.shadow.feature.prepare.PrepareViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Prepare page: the plate and the settings used to slice it. */
@Serializable
data object PrepareNavKey : NavKey

/**
 * Registers the Prepare page. [createViewModel] builds its view model from the
 * app's dependencies; [onSliceRequested] runs before a slice starts.
 */
fun EntryProviderScope<NavKey>.prepareEntry(
    createViewModel: () -> PrepareViewModel,
    onSliceRequested: () -> Unit,
) {
    entry<PrepareNavKey> {
        PrepareRoute(viewModel = viewModel { createViewModel() }, onSliceRequested = onSliceRequested)
    }
}
