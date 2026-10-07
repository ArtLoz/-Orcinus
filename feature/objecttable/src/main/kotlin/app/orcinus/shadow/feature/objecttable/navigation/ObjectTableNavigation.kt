package app.orcinus.shadow.feature.objecttable.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.ui.navigation.overlayPageMetadata
import app.orcinus.shadow.feature.objecttable.ObjectTableRoute
import app.orcinus.shadow.feature.objecttable.ObjectTableViewModel
import kotlinx.serialization.Serializable

/**
 * OrcaSlicer's Parameter Table (ObjectTableDialog), opened over the whole
 * window. Plater::PopupObjectTable() selects the row of the object [mesh], or
 * of its volume [volume]; none for null.
 */
@Serializable
data class ObjectTableNavKey(val mesh: String? = null, val volume: Int? = null) : NavKey {
    /** The row the table opens on. */
    val item: SettingsItem?
        get() = mesh?.let { ScenePath(it) }?.let { path -> volume?.let { SettingsItem.Volume(ObjectPartId(path, it)) } ?: SettingsItem.Object(path) }

    companion object {
        /** The table opened on [item]'s row, or on none. */
        fun of(item: SettingsItem?): ObjectTableNavKey = when (item) {
            is SettingsItem.Object -> ObjectTableNavKey(item.mesh.value)
            is SettingsItem.Volume -> ObjectTableNavKey(item.id.mesh.value, item.id.index)
            else -> ObjectTableNavKey()
        }
    }
}

/**
 * Registers the Parameter Table. [createViewModel] builds its view model from
 * the app's dependencies; [onBack] closes it.
 */
fun EntryProviderScope<NavKey>.objectTableEntry(
    createViewModel: () -> ObjectTableViewModel,
    onBack: () -> Unit,
) {
    // The table opens over the workspace, as the desktop app's modal dialog over the plater.
    entry<ObjectTableNavKey>(metadata = overlayPageMetadata) { key ->
        ObjectTableRoute(
            viewModel = viewModel { createViewModel() },
            onBack = onBack,
            openOn = key.item,
        )
    }
}
