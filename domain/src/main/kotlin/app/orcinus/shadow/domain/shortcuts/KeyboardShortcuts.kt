package app.orcinus.shadow.domain.shortcuts

import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.core.model.CanvasTool
import app.orcinus.shadow.core.model.KeyPress
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.model.ShortcutContext
import app.orcinus.shadow.core.model.ShortcutKey
import app.orcinus.shadow.core.model.ShortcutPage

/**
 * Where OrcaSlicer sends a key: MainFrame's wxEVT_CHAR_HOOK, which every page
 * of the main window has, even with a text field taking the keys; then, on the
 * Prepare and Preview tabs, GLGizmosManager::on_key() and on_char() of the
 * open gizmo, GLCanvas3D::on_key() and on_char(), and the preview's
 * Preview::update_layers_slider_from_canvas(). A modal dialog takes the keys
 * itself, and a page over the workspace keeps its own.
 *
 * The canvas takes the navigation keys (Tab, the arrows, Home, End, Page Up and
 * Page Down) while it holds the keyboard focus, as on the desktop; its other
 * keys wherever the focus is but in a text field, so they also stand for the
 * object list's, which are the same.
 */
class ResolveShortcutUseCase {
    operator fun invoke(press: KeyPress, context: ShortcutContext): ShortcutAction? {
        if (context.covered) return null
        if (!press.down) return keyUp(press, context)
        charHook(press)?.let { return it }
        if (context.typing) return null
        // Help's "Keyboard Shortcuts", whose accelerator is "?".
        if (press.key == ShortcutKey.QUESTION_MARK && !press.ctrl && !press.alt) return ShortcutAction.ShowShortcuts
        if (context.page == ShortcutPage.OTHER) return null
        val prepare = context.page == ShortcutPage.PREPARE
        // GLCanvas3D::on_key(): the open gizmo's keys first (GLGizmosManager::on_key()).
        if (prepare && !context.assemblyView) gizmoKey(press, context)?.let { return it }
        if (press.key in NAVIGATION_KEYS) return if (context.canvasFocused) navigation(press, context) else null
        // GLCanvas3D::on_char(): Esc first closes the arrange options (_deactivate_arrange_menu()).
        val plain = !press.ctrl && !press.alt
        if (prepare && press.key == ShortcutKey.ESCAPE && plain && context.arrangeOpen && !context.assemblyView) return ShortcutAction.CloseArrangeOptions
        if (prepare) gizmoChar(press, context)?.let { return it }
        return canvasChar(press, context)
    }

    /**
     * MainFrame's wxEVT_CHAR_HOOK; the menu's accelerators of the same keys do
     * the same. Ctrl+Tab is the tab bar's own ("Switch table page").
     */
    private fun charHook(press: KeyPress): ShortcutAction? {
        if (!press.ctrl || press.alt || press.repeat > 0) return null
        return when (press.key) {
            ShortcutKey.R -> ShortcutAction.SlicePlate
            ShortcutKey.G -> if (press.shift) ShortcutAction.PrintPlate else ShortcutAction.ExportSlicedFile
            ShortcutKey.N -> ShortcutAction.NewProject
            ShortcutKey.O -> ShortcutAction.OpenProject
            ShortcutKey.S -> if (press.shift) ShortcutAction.SaveProjectAs else ShortcutAction.SaveProject
            ShortcutKey.P -> ShortcutAction.Preferences
            ShortcutKey.I -> ShortcutAction.ImportModels
            ShortcutKey.TAB -> ShortcutAction.SwitchTab(forward = !press.shift)
            else -> null
        }
    }

    /** GLCanvas3D::on_key() for a key let go: TranslationProcessor's up action and do_rotate(). */
    private fun keyUp(press: KeyPress, context: ShortcutContext): ShortcutAction? {
        if (context.page != ShortcutPage.PREPARE || context.assemblyView || context.typing) return null
        return if (press.key in ARROWS || press.key == ShortcutKey.PAGE_UP || press.key == ShortcutKey.PAGE_DOWN) ShortcutAction.EndKeyManipulation else null
    }

    /** GLGizmosManager::on_key() for a key going down. */
    private fun gizmoKey(press: KeyPress, context: ShortcutContext): ShortcutAction? {
        val plain = !press.ctrl && !press.alt
        return when (context.tool) {
            // GLGizmoCut3D::shift_cut(), by a millimetre.
            CanvasTool.CUT -> when (press.key) {
                ShortcutKey.UP -> ShortcutAction.ShiftCutPlane(1.0).takeIf { context.canvasFocused }
                ShortcutKey.DOWN -> ShortcutAction.ShiftCutPlane(-1.0).takeIf { context.canvasFocused }
                else -> null
            }
            CanvasTool.COLOR -> when {
                !plain -> null
                // The digits pick the filament (on_number_key_down()), 1 then 0-6 making 10-16.
                press.key.digit != null -> ShortcutAction.FilamentDigit(press.key.digit!!)
                else -> paintTool(press.key, COLOR_TOOLS)?.let(ShortcutAction::SelectPaintTool)
            }
            CanvasTool.SUPPORTS -> if (plain) paintTool(press.key, SUPPORT_TOOLS)?.let(ShortcutAction::SelectPaintTool) else null
            CanvasTool.SEAM -> if (plain) paintTool(press.key, SEAM_TOOLS)?.let(ShortcutAction::SelectPaintTool) else null
            // GLGizmoSimplify::on_esc_key_down()
            CanvasTool.SIMPLIFY -> if (press.key == ShortcutKey.ESCAPE && plain) ShortcutAction.EscapeTool else null
            else -> null
        }
    }

    private fun paintTool(key: ShortcutKey, tools: Map<ShortcutKey, PaintTool>): PaintTool? = tools[key]

    /**
     * GLCanvas3D::on_key() for Tab and the keys that move: Tab switches between
     * Prepare and Preview, Shift+Tab collapses the sidebar; on the 3D view the
     * arrows move the selection and Page Up and Down turn it, on the preview
     * they move the sliders' thumbs, five times as far with Ctrl or Shift.
     */
    private fun navigation(press: KeyPress, context: ShortcutContext): ShortcutAction? {
        val prepare = context.page == ShortcutPage.PREPARE
        if (press.key == ShortcutKey.TAB) {
            return when {
                press.ctrl || press.alt -> null
                // The assembly view's canvas posts no EVT_GLCANVAS_TAB.
                !press.shift -> ShortcutAction.SwitchPrepareAndPreview.takeIf { !context.assemblyView && press.repeat == 0 }
                else -> ShortcutAction.CollapseSidebar.takeIf { press.repeat == 0 }
            }
        }
        if (press.alt) return null
        if (prepare) {
            // m_gizmos.is_enabled() && !m_selection.is_empty() && m_canvas_type != CanvasAssembleView
            if (!context.selected || context.assemblyView) return null
            val fine = press.shift
            return when (press.key) {
                ShortcutKey.LEFT -> ShortcutAction.MoveSelection(-1, 0, fine, press.ctrl)
                ShortcutKey.RIGHT -> ShortcutAction.MoveSelection(1, 0, fine, press.ctrl)
                ShortcutKey.UP -> ShortcutAction.MoveSelection(0, 1, fine, press.ctrl)
                ShortcutKey.DOWN -> ShortcutAction.MoveSelection(0, -1, fine, press.ctrl)
                ShortcutKey.PAGE_UP -> ShortcutAction.RotateSelection(counterclockwise = true)
                ShortcutKey.PAGE_DOWN -> ShortcutAction.RotateSelection(counterclockwise = false)
                else -> null
            }
        }
        val increment = if (press.ctrl || press.shift) FAST_STEPS else 1
        return when (press.key) {
            ShortcutKey.UP -> ShortcutAction.LayerStep(increment)
            ShortcutKey.DOWN -> ShortcutAction.LayerStep(-increment)
            ShortcutKey.LEFT -> ShortcutAction.MoveStep(-increment)
            ShortcutKey.RIGHT -> ShortcutAction.MoveStep(increment)
            ShortcutKey.HOME -> ShortcutAction.MovesTo(end = false)
            ShortcutKey.END -> ShortcutAction.MovesTo(end = true)
            else -> null
        }
    }

    /**
     * GLGizmosManager::on_char(): Esc for the open gizmo, 'A' and Delete as it
     * takes them, then handle_shortcut(): the letter of a gizmo opens it, the
     * text tool's even with nothing selected, which it then adds.
     */
    private fun gizmoChar(press: KeyPress, context: ShortcutContext): ShortcutAction? {
        // HasModifiers(): Ctrl or Alt; Shift is no modifier here.
        if (press.ctrl || press.alt) return null
        val tool = context.tool
        if (tool != null && !context.assemblyView) {
            when (press.key) {
                ShortcutKey.ESCAPE -> return ShortcutAction.EscapeTool
                ShortcutKey.A -> return ShortcutAction.Swallowed
                ShortcutKey.DELETE, ShortcutKey.BACKSPACE ->
                    if (tool == CanvasTool.CUT || tool == CanvasTool.MEASURE || tool == CanvasTool.ASSEMBLY) return ShortcutAction.ToolDelete
                else -> Unit
            }
        }
        if (context.assemblyView || press.repeat > 0) return null
        val gizmo = GIZMO_KEYS[press.key] ?: return null
        return ShortcutAction.ToggleTool(gizmo).takeIf { gizmo == CanvasTool.TEXT || context.selected }
    }

    /** GLCanvas3D::on_char() */
    private fun canvasChar(press: KeyPress, context: ShortcutContext): ShortcutAction? {
        val prepare = context.page == ShortcutPage.PREPARE
        // The 3D view's own events, which neither the preview's canvas nor the assembly view's handles.
        val view3D = prepare && !context.assemblyView
        val painting = context.tool?.painting == true
        if (press.ctrl && !press.alt) {
            return when (press.key) {
                ShortcutKey.A -> when {
                    !view3D || painting || context.layerEditing -> null
                    press.shift -> ShortcutAction.SelectAll
                    else -> ShortcutAction.SelectPlateObjects
                }
                ShortcutKey.C -> ShortcutAction.Copy.takeIf { view3D && !painting }
                ShortcutKey.V -> ShortcutAction.Paste.takeIf { view3D && !painting }
                ShortcutKey.X -> ShortcutAction.Cut.takeIf { view3D && !painting }
                // Undo and Redo of the 3D view and the assembly view alone.
                ShortcutKey.Y -> ShortcutAction.Redo.takeIf { prepare }
                ShortcutKey.Z -> ShortcutAction.Undo.takeIf { prepare }
                ShortcutKey.E -> ShortcutAction.ToggleLabels.takeIf { view3D }
                ShortcutKey.D -> ShortcutAction.DeleteAll.takeIf { view3D && press.repeat == 0 }
                ShortcutKey.K -> ShortcutAction.Clone.takeIf { view3D && press.repeat == 0 }
                else -> press.key.digit?.let { digit -> VIEWS[digit]?.let { ShortcutAction.SelectView(it.view) } }
            }
        }
        if (press.alt) return null
        val preview = context.page == ShortcutPage.PREVIEW
        return when (press.key) {
            // Delete everywhere but on a Mac, where Backspace is the key that deletes.
            ShortcutKey.DELETE, ShortcutKey.BACKSPACE -> ShortcutAction.DeleteSelected.takeIf { view3D }
            ShortcutKey.ESCAPE -> ShortcutAction.DeselectAll
            ShortcutKey.PLUS -> ShortcutAction.IncreaseInstances.takeIf { view3D }
            ShortcutKey.MINUS -> ShortcutAction.DecreaseInstances.takeIf { view3D }
            ShortcutKey.A -> (if (press.shift) ShortcutAction.ArrangePlate else ShortcutAction.Arrange).takeIf { view3D && press.repeat == 0 }
            ShortcutKey.Q -> (if (press.shift) ShortcutAction.OrientPlate else ShortcutAction.Orient).takeIf { view3D && press.repeat == 0 }
            ShortcutKey.C -> ShortcutAction.ToggleGcodeWindow.takeIf { press.repeat == 0 }
            ShortcutKey.I -> ShortcutAction.Zoom(zoomIn = true)
            ShortcutKey.O -> ShortcutAction.Zoom(zoomIn = false)
            ShortcutKey.V -> ShortcutAction.TogglePrintable.takeIf { view3D && press.repeat == 0 }
            // Preview::update_layers_slider_from_canvas(), which takes no modifiers.
            ShortcutKey.L -> ShortcutAction.ToggleOneLayer.takeIf { preview && !press.shift && press.repeat == 0 }
            // GLCanvas3D::on_key(): Shift+G (Ctrl+G is the char hook's export) asks for a layer to go to.
            ShortcutKey.G -> ShortcutAction.GoToLayer.takeIf { preview && press.shift && press.repeat == 0 }
            else -> press.key.digit?.let { digit -> ShortcutAction.FilamentDigit(digit).takeIf { view3D && press.repeat == 0 } }
        }
    }

    private class View(val view: CameraView?)

    private companion object {
        val ARROWS = setOf(ShortcutKey.UP, ShortcutKey.DOWN, ShortcutKey.LEFT, ShortcutKey.RIGHT)
        val NAVIGATION_KEYS = ARROWS + setOf(ShortcutKey.TAB, ShortcutKey.HOME, ShortcutKey.END, ShortcutKey.PAGE_UP, ShortcutKey.PAGE_DOWN)

        /** Ctrl or Shift with the keys that move the sliders: "Move slider 5x faster". */
        const val FAST_STEPS = 5

        /** Ctrl+0 to Ctrl+6: select_view() of "plate" (with zoom_to_bed()), "top", "bottom", "front", "rear", "left" and "right". */
        val VIEWS = mapOf(
            0 to View(null),
            1 to View(CameraView.TOP),
            2 to View(CameraView.BOTTOM),
            3 to View(CameraView.FRONT),
            4 to View(CameraView.REAR),
            5 to View(CameraView.LEFT),
            6 to View(CameraView.RIGHT),
        )

        /** GLGizmoBase::m_shortcut_key of the gizmos an FFF printer has, in GLGizmosManager's order. */
        val GIZMO_KEYS = mapOf(
            ShortcutKey.M to CanvasTool.MOVE,
            ShortcutKey.R to CanvasTool.ROTATE,
            ShortcutKey.S to CanvasTool.SCALE,
            ShortcutKey.F to CanvasTool.LAY_ON_FACE,
            ShortcutKey.C to CanvasTool.CUT,
            ShortcutKey.B to CanvasTool.MESH_BOOLEAN,
            ShortcutKey.L to CanvasTool.SUPPORTS,
            ShortcutKey.P to CanvasTool.SEAM,
            ShortcutKey.H to CanvasTool.FUZZY_SKIN,
            ShortcutKey.N to CanvasTool.COLOR,
            ShortcutKey.T to CanvasTool.TEXT,
            ShortcutKey.U to CanvasTool.MEASURE,
            ShortcutKey.Y to CanvasTool.ASSEMBLY,
            ShortcutKey.E to CanvasTool.BRIM_EARS,
        )

        /** GLGizmoMmuSegmentation::on_key_down_select_tool_type(): Fill, Triangle, Sphere, Circle, Height Range and Gap Fill. */
        val COLOR_TOOLS = mapOf(
            ShortcutKey.F to PaintTool.BUCKET,
            ShortcutKey.T to PaintTool.TRIANGLE,
            ShortcutKey.S to PaintTool.BRUSH,
            ShortcutKey.C to PaintTool.CIRCLE,
            ShortcutKey.H to PaintTool.HEIGHT_RANGE,
            ShortcutKey.G to PaintTool.GAP_FILL,
        )

        /** GLGizmoFdmSupports::on_key_down_select_tool_type(): Fill, Sphere, Circle and Gap Fill. */
        val SUPPORT_TOOLS = mapOf(
            ShortcutKey.F to PaintTool.FILL,
            ShortcutKey.S to PaintTool.BRUSH,
            ShortcutKey.C to PaintTool.CIRCLE,
            ShortcutKey.G to PaintTool.GAP_FILL,
        )

        /** GLGizmoSeam::on_key_down_select_tool_type(): Sphere and Circle. */
        val SEAM_TOOLS = mapOf(
            ShortcutKey.S to PaintTool.BRUSH,
            ShortcutKey.C to PaintTool.CIRCLE,
        )
    }
}

/**
 * m_timer_set_color of GLCanvas3D (and of GLGizmosManager for the colour
 * tool): '1' waits [WAIT_MILLIS] for a second digit, so 1 then 0 to 6 makes 10
 * to 16; once the wait is over the '1' stands alone. Any other digit, or one
 * after the wait, stands for itself. The times are the caller's clock.
 */
class FilamentDigits {
    private var waitingSince: Long? = null

    /** The filament [digit] at [now] makes, or null while '1' waits for a second digit. */
    fun press(digit: Int, now: Long): Int? {
        val waiting = waitingSince?.takeIf { now - it < WAIT_MILLIS }
        waitingSince = null
        return when {
            waiting == null && digit == 1 -> {
                waitingSince = now
                null
            }
            waiting != null && digit < 7 -> digit + 10
            else -> digit
        }
    }

    /** on_set_color_timer(): the '1' that waited at [now] stands alone, null when none waits or it is too early. */
    fun expire(now: Long): Int? {
        val since = waitingSince ?: return null
        if (now - since < WAIT_MILLIS) return null
        waitingSince = null
        return 1
    }

    companion object {
        /** m_timer_set_color.StartOnce(500) */
        const val WAIT_MILLIS = 500L
    }
}
