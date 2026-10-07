package app.orcinus.shadow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.model.ShortcutPage
import app.orcinus.shadow.core.ui.shortcuts.KeyboardShortcutsDialog
import app.orcinus.shadow.core.ui.shortcuts.LocalKeyboardShortcuts
import app.orcinus.shadow.core.ui.shortcuts.ShortcutHandler

/**
 * What MainFrame and the plater do with the shortcuts of the window itself:
 * the tab bar's Ctrl+Tab, the canvases' Tab and Shift+Tab, the char hook's
 * Ctrl+R and Ctrl+P, and "?" with Help's "Keyboard Shortcuts", which opens
 * KBShortcutsDialog. [page] is the tab shown, which the keys go by.
 */
@Composable
internal fun WorkspaceShortcuts(
    page: ShortcutPage,
    /** The notebook's page after or before the one shown ("Switch table page"), as the tab bar selects it. */
    onSwitchTab: (forward: Boolean) -> Unit,
    /** Plater::priv::select_next_view_3D(): Prepare and Preview, one for the other. */
    onSwitchPrepareAndPreview: () -> Unit,
    /** Plater::collapse_sidebar(!is_sidebar_collapsed()) */
    onToggleSidebar: () -> Unit,
    /** m_slice_enable, and EVT_GLTOOLBAR_SLICE_PLATE with the Preview tab selected. */
    sliceEnabled: Boolean,
    onSlicePlate: () -> Unit,
    onOpenPreferences: () -> Unit,
) {
    val shortcuts = LocalKeyboardShortcuts.current
    SideEffect { shortcuts?.page = page }
    var shown by rememberSaveable { mutableStateOf(false) }
    ShortcutHandler { action ->
        when (action) {
            is ShortcutAction.SwitchTab -> onSwitchTab(action.forward)
            ShortcutAction.SwitchPrepareAndPreview -> if (page == ShortcutPage.OTHER) return@ShortcutHandler false else onSwitchPrepareAndPreview()
            ShortcutAction.CollapseSidebar -> onToggleSidebar()
            // MainFrame's char hook takes Ctrl+R whether the slice is enabled or not.
            ShortcutAction.SlicePlate -> if (sliceEnabled) onSlicePlate()
            ShortcutAction.Preferences -> onOpenPreferences()
            ShortcutAction.ShowShortcuts -> shown = true
            else -> return@ShortcutHandler false
        }
        true
    }
    if (shown) KeyboardShortcutsDialog(onDismiss = { shown = false })
}
