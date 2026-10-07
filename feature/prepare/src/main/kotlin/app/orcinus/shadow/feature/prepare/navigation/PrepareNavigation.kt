package app.orcinus.shadow.feature.prepare.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsItem
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
    /** How many times the workspace switched to the page, which then shows the 3D view. */
    shown: Int = 0,
    onSliceRequested: () -> Unit,
    onOpenSidebar: () -> Unit = {},
    /** A setting a validation notification jumps to opens on its tab's page. */
    onOpenSetting: (SearchOption) -> Unit = {},
    /** The object menus' "Edit in Parameter Table": the table opens on the row of the item, or on none. */
    onOpenObjectTable: (SettingsItem?) -> Unit = {},
) {
    entry<PrepareNavKey> {
        PrepareRoute(
            viewModel = viewModel { createViewModel() },
            shown = shown,
            onSliceRequested = onSliceRequested,
            onOpenSidebar = onOpenSidebar,
            onOpenSetting = onOpenSetting,
            onOpenObjectTable = onOpenObjectTable,
        )
    }
}
