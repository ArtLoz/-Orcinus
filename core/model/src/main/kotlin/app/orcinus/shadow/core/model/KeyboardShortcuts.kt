package app.orcinus.shadow.core.model

/** A key OrcaSlicer's shortcuts name: wxKeyCode's letters, digits and WXK_ keys. */
enum class ShortcutKey {
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    TAB, DELETE, BACKSPACE, ESCAPE, UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN,

    /** The characters '+', '-' and '?', wherever the keyboard has them. */
    PLUS, MINUS, QUESTION_MARK,
    ;

    /** The digit of a digit key, null for any other. */
    val digit: Int? get() = if (ordinal in DIGIT_0.ordinal..DIGIT_9.ordinal) ordinal - DIGIT_0.ordinal else null

    val letter: Boolean get() = ordinal <= Z.ordinal

    companion object {
        fun ofDigit(digit: Int): ShortcutKey = entries[DIGIT_0.ordinal + digit]
    }
}

/**
 * A key going down ([down]), or up, with the modifiers held: Ctrl (wxMOD_CONTROL,
 * which CmdDown() is off a Mac), Shift and Alt; [repeat] counts the times a
 * held key repeated.
 */
data class KeyPress(
    val key: ShortcutKey,
    val down: Boolean = true,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val repeat: Int = 0,
)

/** The workspace's tab the keys go to: the 3D view's (tp3DEditor), the preview's (tpPreview), or another. */
enum class ShortcutPage { PREPARE, PREVIEW, OTHER }

/** GLGizmosManager::EType of the tool open on the Prepare page's canvas. */
enum class CanvasTool {
    MOVE, ROTATE, SCALE, LAY_ON_FACE, CUT, MESH_BOOLEAN,
    SUPPORTS, SEAM, FUZZY_SKIN, COLOR,
    TEXT, SVG, MEASURE, ASSEMBLY, BRIM_EARS, SIMPLIFY,
    ;

    /** GLGizmoPainterBase: the painting tools, which take Ctrl+A, C, V and X from the canvas. */
    val painting: Boolean get() = this == SUPPORTS || this == SEAM || this == FUZZY_SKIN || this == COLOR
}

/**
 * What decides where a key goes: the tab shown, a page over the workspace
 * ([covered], whose own keys these are), a text field taking the keys
 * ([typing], ImGui's WantCaptureKeyboard or a wxTextCtrl with the focus), and
 * the canvas holding the keyboard focus ([canvasFocused]); then what the
 * Prepare page's canvas shows: the open [tool], whether anything is
 * [selected], the variable layer height, the arrange options and the assembly view.
 */
data class ShortcutContext(
    val page: ShortcutPage,
    val covered: Boolean = false,
    val typing: Boolean = false,
    val canvasFocused: Boolean = false,
    val tool: CanvasTool? = null,
    val selected: Boolean = false,
    val layerEditing: Boolean = false,
    val arrangeOpen: Boolean = false,
    val assemblyView: Boolean = false,
)

/** What a key asks of the app: MainFrame's menus and char hook, GLCanvas3D's on_char() and on_key(), its gizmos' and the preview's sliders'. */
sealed interface ShortcutAction {
    // MainFrame's wxEVT_CHAR_HOOK
    data object NewProject : ShortcutAction
    data object OpenProject : ShortcutAction
    data object SaveProject : ShortcutAction
    data object SaveProjectAs : ShortcutAction
    data object ImportModels : ShortcutAction
    data object ExportSlicedFile : ShortcutAction
    data object SlicePlate : ShortcutAction
    data object PrintPlate : ShortcutAction
    data object Preferences : ShortcutAction

    /** The tab bar's Ctrl+Tab ("Switch table page"), and back with Shift. */
    data class SwitchTab(val forward: Boolean) : ShortcutAction

    /** Help's "Keyboard Shortcuts" (EVT_GLCANVAS_QUESTION_MARK). */
    data object ShowShortcuts : ShortcutAction

    // GLCanvas3D::on_key()
    /** EVT_GLCANVAS_TAB: Plater::priv::select_next_view_3D(). */
    data object SwitchPrepareAndPreview : ShortcutAction

    /** EVT_GLCANVAS_COLLAPSE_SIDEBAR */
    data object CollapseSidebar : ShortcutAction

    /** TranslationProcessor: the selection moves by [x] and [y] steps of 10 mm, of 1 mm when [fine], along the camera's axes with [cameraSpace]. */
    data class MoveSelection(val x: Int, val y: Int, val fine: Boolean, val cameraSpace: Boolean) : ShortcutAction

    /** Page Up and Page Down: the selection turns by 45° about Z, counterclockwise when [counterclockwise]. */
    data class RotateSelection(val counterclockwise: Boolean) : ShortcutAction

    /** The arrow or page key let go: do_move("Tool Move") or do_rotate("Tool Rotate") of what the keys did. */
    data object EndKeyManipulation : ShortcutAction

    // GLGizmosManager::on_key() and on_char()
    /** GLGizmoCut3D::shift_cut(): the plane moves by [millimeters] along its normal. */
    data class ShiftCutPlane(val millimeters: Double) : ShortcutAction

    /** on_key_down_select_tool_type() of the painting gizmos. */
    data class SelectPaintTool(val tool: PaintTool) : ShortcutAction

    /** Esc with a gizmo open: GLGizmoMeasure's and GLGizmoAssembly's own, or reset_all_states(). */
    data object EscapeTool : ShortcutAction

    /** Delete with the cut, measure or assembly gizmo open (SLAGizmoEventType::Delete). */
    data object ToolDelete : ShortcutAction

    /** handle_shortcut(): the gizmo with that key opens, or closes when it is open. */
    data class ToggleTool(val tool: CanvasTool) : ShortcutAction

    /** A key the open gizmo takes without doing anything with it, as 'A' while it runs. */
    data object Swallowed : ShortcutAction

    // GLCanvas3D::on_char()
    /** _deactivate_arrange_menu() */
    data object CloseArrangeOptions : ShortcutAction
    data object DeselectAll : ShortcutAction
    data object DeleteSelected : ShortcutAction
    data object DeleteAll : ShortcutAction
    /** EVT_GLCANVAS_SELECT_CURR_PLATE_ALL */
    data object SelectPlateObjects : ShortcutAction
    data object SelectAll : ShortcutAction
    data object Copy : ShortcutAction
    data object Paste : ShortcutAction
    data object Cut : ShortcutAction
    data object Clone : ShortcutAction
    data object Undo : ShortcutAction
    data object Redo : ShortcutAction
    data object ToggleLabels : ShortcutAction

    /** select_view(): a view of the camera, or null for "plate" with zoom_to_bed(). */
    data class SelectView(val view: CameraView?) : ShortcutAction

    data object Arrange : ShortcutAction
    data object ArrangePlate : ShortcutAction
    data object Orient : ShortcutAction
    data object OrientPlate : ShortcutAction

    /**
     * A digit, which m_timer_set_color reads with the one before it: the
     * filament of the selected items (set_extruder_for_selected_items()), or
     * the one the colour tool paints with (on_number_key_down(), 0 for none).
     */
    data class FilamentDigit(val digit: Int) : ShortcutAction

    data object IncreaseInstances : ShortcutAction
    data object DecreaseInstances : ShortcutAction

    /** _update_camera_zoom(1.0) and (-1.0). */
    data class Zoom(val zoomIn: Boolean) : ShortcutAction

    /** EVT_GLCANVAS_PRINTABLE: ObjectList::toggle_printable_state(). */
    data object TogglePrintable : ShortcutAction

    /** GUI_App::toggle_show_gcode_window() */
    data object ToggleGcodeWindow : ShortcutAction

    // The preview's sliders
    /** The layer slider's active thumb by [steps] layers. */
    data class LayerStep(val steps: Int) : ShortcutAction

    /** The moves slider by [steps] moves, on to the next or the previous layer at its ends. */
    data class MoveStep(val steps: Int) : ShortcutAction

    /** Home and End: the moves slider at its start ([end] false) or its end. */
    data class MovesTo(val end: Boolean) : ShortcutAction

    /** IMSlider::switch_one_layer_mode() */
    data object ToggleOneLayer : ShortcutAction

    /** IMSlider::show_go_to_layer() */
    data object GoToLayer : ShortcutAction
}

/** How a row of the shortcuts list names its keys. */
sealed interface ShortcutKeys {
    /** A key and the modifiers held with it, which the list writes as OrcaSlicer composes it ("Ctrl+Shift+S"). */
    data class Combo(val key: ShortcutKey, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) : ShortcutKeys

    /** Keys named in words, an msgid of OrcaSlicer's own ("Mouse wheel"), after the [prefix] of the modifiers. */
    data class Words(val msgid: String, val prefix: String = "") : ShortcutKeys
}

/** A row of KBShortcutsDialog: the keys, and the msgid of what they do. */
data class ShortcutRow(val keys: ShortcutKeys, val description: String)

/** A page of KBShortcutsDialog: the msgid of its button, and its rows. */
data class ShortcutGroup(val title: String, val rows: List<ShortcutRow>)

/** KBShortcutsDialog::fill_shortcuts(): OrcaSlicer's list of shortcuts, which "?" and Help's "Keyboard Shortcuts" show. */
object OrcaKeyboardShortcuts {
    /** shortkey_ctrl_prefix() and shortkey_alt_prefix() off a Mac, and L("Shift+"). */
    const val CTRL = "Ctrl+"
    const val ALT = "Alt+"
    const val SHIFT = "Shift+"

    /** mouse_actions: what left_mouse_drag_action and its kin name ("2", "1" and "1" by default, AppConfig::set_defaults()). */
    private const val ROTATE_VIEW = "Rotate View"
    private const val PAN_VIEW = "Pan View"

    private fun combo(key: ShortcutKey, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) = ShortcutKeys.Combo(key, ctrl, shift, alt)

    private fun row(keys: ShortcutKeys, description: String) = ShortcutRow(keys, description)

    private fun row(words: String, description: String, prefix: String = "") = ShortcutRow(ShortcutKeys.Words(words, prefix), description)

    val groups: List<ShortcutGroup> = listOf(
        ShortcutGroup(
            "Global",
            listOf(
                row(combo(ShortcutKey.N, ctrl = true), "New Project"),
                row(combo(ShortcutKey.O, ctrl = true), "Open Project"),
                row(combo(ShortcutKey.S, ctrl = true), "Save Project"),
                row(combo(ShortcutKey.S, ctrl = true, shift = true), "Save Project as"),
                row(combo(ShortcutKey.I, ctrl = true), "Import geometry data from STL/STEP/3MF/OBJ/AMF files"),
                row(combo(ShortcutKey.G, ctrl = true), "Export plate sliced file"),
                row(combo(ShortcutKey.R, ctrl = true), "Slice plate"),
                row(combo(ShortcutKey.G, ctrl = true, shift = true), "Print plate"),
                row(combo(ShortcutKey.X, ctrl = true), "Cut"),
                row(combo(ShortcutKey.C, ctrl = true), "Copy to clipboard"),
                row(combo(ShortcutKey.V, ctrl = true), "Paste from clipboard"),
                row(combo(ShortcutKey.P, ctrl = true), "Preferences"),
                // "Show/Hide 3Dconnexion devices settings dialog" (Ctrl+M) is left out: the app has no 3Dconnexion devices.
                row(combo(ShortcutKey.TAB, ctrl = true), "Switch table page"),
                row(combo(ShortcutKey.DELETE), "Delete selected"),
                row(combo(ShortcutKey.QUESTION_MARK), "Show keyboard shortcuts list"),
            ),
        ),
        ShortcutGroup(
            "Prepare",
            listOf(
                row("Left mouse button", ROTATE_VIEW),
                row("Middle mouse button", PAN_VIEW),
                row("Right mouse button", PAN_VIEW),
                row("Mouse wheel", "Zoom View"),
                row(combo(ShortcutKey.A), "Arrange all objects"),
                row(combo(ShortcutKey.A, shift = true), "Arrange objects on selected plates"),
                row(
                    combo(ShortcutKey.Q),
                    "Auto orients selected objects or all objects. If there are selected objects, it just orients the selected ones. " +
                        "Otherwise, it will orient all objects in the current project.",
                ),
                row(combo(ShortcutKey.Q, shift = true), "Auto orients all objects on the active plate."),
                row(combo(ShortcutKey.TAB, shift = true), "Collapse/Expand the sidebar"),
                row("Any arrow", "Movement in camera space", CTRL),
                row("Left mouse button", "Select multiple objects", CTRL),
                row("Left mouse button", "Select objects by rectangle", SHIFT),
                row(combo(ShortcutKey.UP), "Move selection 10 mm in positive Y direction"),
                row(combo(ShortcutKey.DOWN), "Move selection 10 mm in negative Y direction"),
                row(combo(ShortcutKey.LEFT), "Move selection 10 mm in negative X direction"),
                row(combo(ShortcutKey.RIGHT), "Move selection 10 mm in positive X direction"),
                row("Any arrow", "Movement step set to 1 mm", SHIFT),
                row(combo(ShortcutKey.ESCAPE), "Deselect all"),
                row("1-9", "Keyboard 1-9: set filament for object/part"),
                row(combo(ShortcutKey.DIGIT_0, ctrl = true), "Camera view - Default"),
                row(combo(ShortcutKey.DIGIT_1, ctrl = true), "Camera view - Top"),
                row(combo(ShortcutKey.DIGIT_2, ctrl = true), "Camera view - Bottom"),
                row(combo(ShortcutKey.DIGIT_3, ctrl = true), "Camera view - Front"),
                row(combo(ShortcutKey.DIGIT_4, ctrl = true), "Camera view - Behind"),
                row(combo(ShortcutKey.DIGIT_5, ctrl = true), "Camera Angle - Left side"),
                row(combo(ShortcutKey.DIGIT_6, ctrl = true), "Camera Angle - Right side"),
                row(combo(ShortcutKey.A, ctrl = true), "Select all objects"),
                row(combo(ShortcutKey.D, ctrl = true), "Delete all"),
                row(combo(ShortcutKey.Z, ctrl = true), "Undo"),
                row(combo(ShortcutKey.Y, ctrl = true), "Redo"),
                row(combo(ShortcutKey.M), "Gizmo move"),
                row(combo(ShortcutKey.R), "Gizmo rotate"),
                row(combo(ShortcutKey.S), "Gizmo scale"),
                row(combo(ShortcutKey.F), "Gizmo place face on bed"),
                row(combo(ShortcutKey.C), "Gizmo cut"),
                row(combo(ShortcutKey.B), "Gizmo mesh boolean"),
                row(combo(ShortcutKey.H), "Gizmo FDM paint-on fuzzy skin"),
                row(combo(ShortcutKey.L), "Gizmo SLA support points"),
                row(combo(ShortcutKey.P), "Gizmo FDM paint-on seam"),
                row(combo(ShortcutKey.T), "Gizmo text emboss/engrave"),
                row(combo(ShortcutKey.U), "Gizmo measure"),
                row(combo(ShortcutKey.Y), "Gizmo assemble"),
                row(combo(ShortcutKey.E), "Gizmo brim ears"),
                row(combo(ShortcutKey.I), "Zoom in"),
                row(combo(ShortcutKey.O), "Zoom out"),
                row(combo(ShortcutKey.V), "Toggle printable for object/part"),
                row(combo(ShortcutKey.TAB), "Switch between Prepare/Preview"),
            ),
        ),
        ShortcutGroup(
            "Toolbar",
            listOf(
                row(combo(ShortcutKey.ESCAPE), "Deselect all"),
                row(ShortcutKeys.Words(SHIFT), "Move: press to snap by 1mm"),
                row("Mouse wheel", "Support/Color Painting: adjust pen radius", CTRL),
                row("Mouse wheel", "Support/Color Painting: adjust section position", ALT),
            ),
        ),
        ShortcutGroup(
            "Objects list",
            listOf(
                row("1-9", "Set extruder number for the objects and parts"),
                row("Del", "Delete objects, parts, modifiers"),
                row(combo(ShortcutKey.ESCAPE), "Deselect all"),
                row(combo(ShortcutKey.C, ctrl = true), "Copy to clipboard"),
                row(combo(ShortcutKey.V, ctrl = true), "Paste from clipboard"),
                row(combo(ShortcutKey.X, ctrl = true), "Cut"),
                row(combo(ShortcutKey.A, ctrl = true), "Select all objects"),
                row(combo(ShortcutKey.K, ctrl = true), "Clone selected"),
                row(combo(ShortcutKey.Z, ctrl = true), "Undo"),
                row(combo(ShortcutKey.Y, ctrl = true), "Redo"),
                row("Space", "Select the object/part and press space to change the name"),
                row("Mouse click", "Select the object/part and mouse click to change the name"),
            ),
        ),
        ShortcutGroup(
            "Preview",
            listOf(
                row(combo(ShortcutKey.UP), "Vertical slider - Move active thumb Up"),
                row(combo(ShortcutKey.DOWN), "Vertical slider - Move active thumb Down"),
                row(combo(ShortcutKey.LEFT), "Horizontal slider - Move active thumb Left"),
                row(combo(ShortcutKey.RIGHT), "Horizontal slider - Move active thumb Right"),
                row(combo(ShortcutKey.L), "On/Off one layer mode of the vertical slider"),
                row(combo(ShortcutKey.C), "On/Off G-code window"),
                row(combo(ShortcutKey.TAB), "Switch between Prepare/Preview"),
                row("Any arrow", "Move slider 5x faster", SHIFT),
                row("Mouse wheel", "Move slider 5x faster", SHIFT),
                row("Any arrow", "Move slider 5x faster", CTRL),
                row("Mouse wheel", "Move slider 5x faster", CTRL),
                row(combo(ShortcutKey.HOME), "Horizontal slider - Move to start position"),
                row(combo(ShortcutKey.END), "Horizontal slider - Move to last position"),
            ),
        ),
    )

    /**
     * The keys of a row as KBShortcutsDialog composes them, before _() takes
     * the whole: the prefixes, then the key's own name, OrcaSlicer's msgid
     * for a key it names in words (L("Tab"), L("Del")).
     */
    fun label(keys: ShortcutKeys): String = when (keys) {
        is ShortcutKeys.Words -> keys.prefix + keys.msgid
        is ShortcutKeys.Combo -> buildString {
            if (keys.ctrl) append(CTRL)
            if (keys.alt) append(ALT)
            if (keys.shift) append(SHIFT)
            append(keyName(keys.key))
        }
    }

    /** What KBShortcutsDialog calls a key. */
    fun keyName(key: ShortcutKey): String = when {
        key.letter -> key.name
        key.digit != null -> key.digit.toString()
        else -> when (key) {
            ShortcutKey.TAB -> "Tab"
            ShortcutKey.DELETE -> "Del"
            ShortcutKey.BACKSPACE -> "Backspace"
            ShortcutKey.ESCAPE -> "Esc"
            ShortcutKey.UP -> "Arrow Up"
            ShortcutKey.DOWN -> "Arrow Down"
            ShortcutKey.LEFT -> "Arrow Left"
            ShortcutKey.RIGHT -> "Arrow Right"
            ShortcutKey.HOME -> "Home"
            ShortcutKey.END -> "End"
            ShortcutKey.PAGE_UP -> "Page Up"
            ShortcutKey.PAGE_DOWN -> "Page Down"
            ShortcutKey.PLUS -> "+"
            ShortcutKey.MINUS -> "-"
            else -> "?"
        }
    }
}
