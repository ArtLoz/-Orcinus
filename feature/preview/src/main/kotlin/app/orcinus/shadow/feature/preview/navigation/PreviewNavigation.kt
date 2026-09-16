package app.orcinus.shadow.feature.preview.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.feature.preview.PreviewRoute
import app.orcinus.shadow.feature.preview.PreviewViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Preview page: the result of slicing the plate. */
@Serializable
data object PreviewNavKey : NavKey

/**
 * Registers the Preview page. [createViewModel] builds its view model from the
 * app's dependencies; [onSliceRequested] runs before a slice starts.
 */
fun EntryProviderScope<NavKey>.previewEntry(
    createViewModel: () -> PreviewViewModel,
    onSliceRequested: () -> Unit,
) {
    entry<PreviewNavKey> {
        PreviewRoute(viewModel = viewModel { createViewModel() }, onSliceRequested = onSliceRequested)
    }
}
