package app.orcinus.shadow.feature.preview

import androidx.compose.runtime.Composable
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.ui.shortcuts.ShortcutHandler
import app.orcinus.shadow.render.scene.PlateViewCamera

/**
 * What the preview's canvas does with OrcaSlicer's keys (GLCanvas3D::on_char()
 * of the preview's canvas): the camera's views and zoom, the G-code window,
 * Esc, which selects nothing there, and the char hook's Ctrl+Shift+G, the
 * print button's ([printPlate], null while it is disabled). The sliders take
 * theirs in [ToolpathsControls].
 */
@Composable
internal fun PreviewShortcuts(
    camera: PlateViewCamera,
    canvas: CanvasPreferences,
    onSetCanvas: (key: String, value: String) -> Unit,
    printPlate: (() -> Unit)?,
) {
    ShortcutHandler { action ->
        when (action) {
            is ShortcutAction.SelectView -> action.view?.let(camera::selectView) ?: camera.defaultView()
            is ShortcutAction.Zoom -> camera.zoomStep(action.zoomIn)
            ShortcutAction.ToggleGcodeWindow -> onSetCanvas(AppConfigKeys.SHOW_GCODE_WINDOW, (!canvas.gcodeWindow).toString())
            // deselect_all() of the preview's canvas: nothing is selected there, and the key goes no further.
            ShortcutAction.DeselectAll -> Unit
            // MainFrame's char hook: EVT_GLTOOLBAR_PRINT_PLATE or EVT_GLTOOLBAR_SEND_GCODE while printing is enabled.
            ShortcutAction.PrintPlate -> printPlate?.invoke()
            else -> return@ShortcutHandler false
        }
        true
    }
}
