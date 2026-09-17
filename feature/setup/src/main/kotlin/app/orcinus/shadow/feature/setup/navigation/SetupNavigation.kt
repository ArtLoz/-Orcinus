package app.orcinus.shadow.feature.setup.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.feature.setup.SetupStart
import app.orcinus.shadow.feature.setup.SetupWizardRoute
import app.orcinus.shadow.feature.setup.SetupWizardViewModel
import kotlinx.serialization.Serializable

/** OrcaSlicer's Setup Wizard, opened over the whole window at [start]. */
@Serializable
data class SetupNavKey(val start: SetupStart) : NavKey

/**
 * Registers the Setup Wizard. [createViewModel] builds its view model from the
 * app's dependencies; [onClose] removes the wizard once it has done its work.
 */
fun EntryProviderScope<NavKey>.setupEntry(
    createViewModel: (SetupStart) -> SetupWizardViewModel,
    onClose: () -> Unit,
) {
    entry<SetupNavKey> { key ->
        SetupWizardRoute(viewModel = viewModel { createViewModel(key.start) }, onClose = onClose)
    }
}
