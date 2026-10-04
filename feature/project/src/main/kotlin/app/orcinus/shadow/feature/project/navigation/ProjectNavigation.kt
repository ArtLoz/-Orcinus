package app.orcinus.shadow.feature.project.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.feature.project.ProjectRoute
import app.orcinus.shadow.feature.project.ProjectViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Project tab (MainFrame::tpProject): ProjectPanel. */
@Serializable
data object ProjectNavKey : NavKey

/** Registers the Project tab; [createViewModel] builds its view model from the app's dependencies. */
fun EntryProviderScope<NavKey>.projectEntry(createViewModel: () -> ProjectViewModel) {
    entry<ProjectNavKey> {
        ProjectRoute(viewModel = viewModel { createViewModel() })
    }
}
