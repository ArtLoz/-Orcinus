package app.orcinus.shadow.feature.device.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.feature.device.DeviceRoute
import app.orcinus.shadow.feature.device.DeviceViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Device tab: the page of the printer's host. */
@Serializable
data object DeviceNavKey : NavKey

/** Registers the Device tab; [createViewModel] builds its view model from the app's dependencies. */
fun EntryProviderScope<NavKey>.deviceEntry(createViewModel: () -> DeviceViewModel) {
    entry<DeviceNavKey> {
        DeviceRoute(viewModel = viewModel { createViewModel() })
    }
}
