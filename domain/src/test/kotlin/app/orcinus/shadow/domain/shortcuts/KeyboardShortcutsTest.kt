package app.orcinus.shadow.domain.shortcuts

import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.core.model.CanvasTool
import app.orcinus.shadow.core.model.KeyPress
import app.orcinus.shadow.core.model.OrcaKeyboardShortcuts
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.model.ShortcutContext
import app.orcinus.shadow.core.model.ShortcutKey
import app.orcinus.shadow.core.model.ShortcutKeys
import app.orcinus.shadow.core.model.ShortcutPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a key goes: MainFrame's char hook, the gizmos' and the canvas's
 * on_key() and on_char(), and the preview's sliders.
 */
class KeyboardShortcutsTest {
    private val resolve = ResolveShortcutUseCase()
    private val prepare = ShortcutContext(ShortcutPage.PREPARE, canvasFocused = true, selected = true)
    private val preview = ShortcutContext(ShortcutPage.PREVIEW, canvasFocused = true)

    private fun key(key: ShortcutKey, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, down: Boolean = true) =
        KeyPress(key, down = down, ctrl = ctrl, shift = shift, alt = alt)

    @Test
    fun charHookWorksOnEveryTabAndWhileTyping() {
        val home = ShortcutContext(ShortcutPage.OTHER)
        assertEquals(ShortcutAction.NewProject, resolve(key(ShortcutKey.N, ctrl = true), home))
        assertEquals(ShortcutAction.SaveProjectAs, resolve(key(ShortcutKey.S, ctrl = true, shift = true), prepare.copy(typing = true)))
        assertEquals(ShortcutAction.SaveProject, resolve(key(ShortcutKey.S, ctrl = true), preview))
        assertEquals(ShortcutAction.PrintPlate, resolve(key(ShortcutKey.G, ctrl = true, shift = true), prepare))
        assertEquals(ShortcutAction.ExportSlicedFile, resolve(key(ShortcutKey.G, ctrl = true), prepare))
        assertEquals(ShortcutAction.SlicePlate, resolve(key(ShortcutKey.R, ctrl = true), home))
        assertEquals(ShortcutAction.SwitchTab(forward = false), resolve(key(ShortcutKey.TAB, ctrl = true, shift = true), home))
        // A page over the workspace keeps its keys.
        assertNull(resolve(key(ShortcutKey.N, ctrl = true), home.copy(covered = true)))
    }

    @Test
    fun aTextFieldKeepsTheCanvasKeys() {
        val typing = prepare.copy(typing = true)
        assertNull(resolve(key(ShortcutKey.M), typing))
        assertNull(resolve(key(ShortcutKey.DELETE), typing))
        assertNull(resolve(key(ShortcutKey.C, ctrl = true), typing))
        assertNull(resolve(key(ShortcutKey.QUESTION_MARK, shift = true), typing))
    }

    @Test
    fun gizmoLettersNeedASelectionButTheText() {
        assertEquals(ShortcutAction.ToggleTool(CanvasTool.MOVE), resolve(key(ShortcutKey.M), prepare))
        assertEquals(ShortcutAction.ToggleTool(CanvasTool.SUPPORTS), resolve(key(ShortcutKey.L), prepare))
        // Shift is no modifier to handle_shortcut().
        assertEquals(ShortcutAction.ToggleTool(CanvasTool.SCALE), resolve(key(ShortcutKey.S, shift = true), prepare))
        val nothing = prepare.copy(selected = false)
        assertNull(resolve(key(ShortcutKey.M), nothing))
        assertEquals(ShortcutAction.ToggleTool(CanvasTool.TEXT), resolve(key(ShortcutKey.T), nothing))
        // Without a selection C falls through to the canvas: the G-code window.
        assertEquals(ShortcutAction.ToggleGcodeWindow, resolve(key(ShortcutKey.C), nothing))
        // The preview's canvas has no gizmos.
        assertNull(resolve(key(ShortcutKey.M), preview))
    }

    @Test
    fun theOpenGizmoTakesItsKeysFirst() {
        val supports = prepare.copy(tool = CanvasTool.SUPPORTS)
        assertEquals(ShortcutAction.SelectPaintTool(PaintTool.BRUSH), resolve(key(ShortcutKey.S), supports))
        assertEquals(ShortcutAction.SelectPaintTool(PaintTool.FILL), resolve(key(ShortcutKey.F), supports))
        // A painting gizmo takes Ctrl+A, C, V and X from the canvas.
        assertNull(resolve(key(ShortcutKey.C, ctrl = true), supports))
        assertNull(resolve(key(ShortcutKey.A, ctrl = true), supports))
        val color = prepare.copy(tool = CanvasTool.COLOR)
        assertEquals(ShortcutAction.SelectPaintTool(PaintTool.HEIGHT_RANGE), resolve(key(ShortcutKey.H), color))
        assertEquals(ShortcutAction.FilamentDigit(3), resolve(key(ShortcutKey.DIGIT_3), color))
        // The fuzzy skin tool has no keys of its own: S opens the scale gizmo.
        assertEquals(ShortcutAction.ToggleTool(CanvasTool.SCALE), resolve(key(ShortcutKey.S), prepare.copy(tool = CanvasTool.FUZZY_SKIN)))
        val cut = prepare.copy(tool = CanvasTool.CUT)
        assertEquals(ShortcutAction.ShiftCutPlane(1.0), resolve(key(ShortcutKey.UP), cut))
        assertEquals(ShortcutAction.ToolDelete, resolve(key(ShortcutKey.DELETE), cut))
        assertEquals(ShortcutAction.EscapeTool, resolve(key(ShortcutKey.ESCAPE), cut))
        // 'A' while a gizmo runs arranges nothing.
        assertEquals(ShortcutAction.Swallowed, resolve(key(ShortcutKey.A), cut))
        assertEquals(ShortcutAction.EscapeTool, resolve(key(ShortcutKey.ESCAPE), prepare.copy(tool = CanvasTool.SIMPLIFY)))
    }

    @Test
    fun canvasCharacters() {
        assertEquals(ShortcutAction.Arrange, resolve(key(ShortcutKey.A), prepare))
        assertEquals(ShortcutAction.ArrangePlate, resolve(key(ShortcutKey.A, shift = true), prepare))
        assertEquals(ShortcutAction.OrientPlate, resolve(key(ShortcutKey.Q, shift = true), prepare))
        assertEquals(ShortcutAction.SelectPlateObjects, resolve(key(ShortcutKey.A, ctrl = true), prepare))
        assertEquals(ShortcutAction.SelectAll, resolve(key(ShortcutKey.A, ctrl = true, shift = true), prepare))
        assertNull(resolve(key(ShortcutKey.A, ctrl = true), prepare.copy(layerEditing = true)))
        assertEquals(ShortcutAction.SelectView(null), resolve(key(ShortcutKey.DIGIT_0, ctrl = true), prepare))
        assertEquals(ShortcutAction.SelectView(CameraView.REAR), resolve(key(ShortcutKey.DIGIT_4, ctrl = true), preview))
        assertEquals(ShortcutAction.FilamentDigit(7), resolve(key(ShortcutKey.DIGIT_7), prepare))
        assertEquals(ShortcutAction.DeleteSelected, resolve(key(ShortcutKey.BACKSPACE), prepare))
        assertEquals(ShortcutAction.IncreaseInstances, resolve(key(ShortcutKey.PLUS, shift = true), prepare))
        assertEquals(ShortcutAction.Zoom(zoomIn = false), resolve(key(ShortcutKey.O), preview))
        assertEquals(ShortcutAction.ShowShortcuts, resolve(key(ShortcutKey.QUESTION_MARK, shift = true), ShortcutContext(ShortcutPage.OTHER)))
        assertEquals(ShortcutAction.CloseArrangeOptions, resolve(key(ShortcutKey.ESCAPE), prepare.copy(arrangeOpen = true)))
        assertEquals(ShortcutAction.DeselectAll, resolve(key(ShortcutKey.ESCAPE), prepare))
        // Undo and Redo of the 3D view and the assembly view, not the preview's.
        assertEquals(ShortcutAction.Undo, resolve(key(ShortcutKey.Z, ctrl = true), prepare.copy(assemblyView = true)))
        assertNull(resolve(key(ShortcutKey.Z, ctrl = true), preview))
        assertNull(resolve(key(ShortcutKey.DELETE), prepare.copy(assemblyView = true)))
    }

    @Test
    fun theNavigationKeysWantTheCanvasFocused() {
        assertEquals(ShortcutAction.SwitchPrepareAndPreview, resolve(key(ShortcutKey.TAB), prepare))
        assertEquals(ShortcutAction.CollapseSidebar, resolve(key(ShortcutKey.TAB, shift = true), preview))
        assertNull(resolve(key(ShortcutKey.TAB), prepare.copy(canvasFocused = false)))
        assertEquals(ShortcutAction.MoveSelection(0, 1, fine = false, cameraSpace = false), resolve(key(ShortcutKey.UP), prepare))
        assertEquals(ShortcutAction.MoveSelection(-1, 0, fine = true, cameraSpace = true), resolve(key(ShortcutKey.LEFT, ctrl = true, shift = true), prepare))
        assertEquals(ShortcutAction.RotateSelection(counterclockwise = true), resolve(key(ShortcutKey.PAGE_UP), prepare))
        assertNull(resolve(key(ShortcutKey.UP), prepare.copy(selected = false)))
        assertEquals(ShortcutAction.EndKeyManipulation, resolve(key(ShortcutKey.UP, down = false), prepare))
        assertEquals(ShortcutAction.LayerStep(5), resolve(key(ShortcutKey.UP, shift = true), preview))
        assertEquals(ShortcutAction.MoveStep(-1), resolve(key(ShortcutKey.LEFT), preview))
        assertEquals(ShortcutAction.MovesTo(end = true), resolve(key(ShortcutKey.END), preview))
        assertEquals(ShortcutAction.ToggleOneLayer, resolve(key(ShortcutKey.L), preview))
        assertEquals(ShortcutAction.GoToLayer, resolve(key(ShortcutKey.G, shift = true), preview))
    }

    @Test
    fun oneThenADigitMakesTenToSixteen() {
        val digits = FilamentDigits()
        assertNull(digits.press(1, now = 0))
        assertEquals(12, digits.press(2, now = 300))
        assertNull(digits.press(1, now = 1000))
        assertNull(digits.expire(now = 1200))
        assertEquals(1, digits.expire(now = 1500))
        assertNull(digits.press(1, now = 2000))
        // 7, 8 and 9 stand for themselves, the '1' lost.
        assertEquals(8, digits.press(8, now = 2100))
        assertEquals(3, digits.press(3, now = 2200))
        assertNull(digits.press(1, now = 3000))
        assertEquals(4, digits.press(4, now = 3600))
    }

    @Test
    fun theListWritesKeysAsOrcaSlicerComposesThem() {
        assertEquals("Ctrl+Shift+S", OrcaKeyboardShortcuts.label(ShortcutKeys.Combo(ShortcutKey.S, ctrl = true, shift = true)))
        assertEquals("Del", OrcaKeyboardShortcuts.label(ShortcutKeys.Combo(ShortcutKey.DELETE)))
        assertEquals("Ctrl+Any arrow", OrcaKeyboardShortcuts.label(ShortcutKeys.Words("Any arrow", OrcaKeyboardShortcuts.CTRL)))
        assertEquals(listOf("Global", "Prepare", "Toolbar", "Objects list", "Preview"), OrcaKeyboardShortcuts.groups.map { it.title })
    }
}
