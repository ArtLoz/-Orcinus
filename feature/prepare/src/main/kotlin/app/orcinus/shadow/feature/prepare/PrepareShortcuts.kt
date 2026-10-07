package app.orcinus.shadow.feature.prepare

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.CanvasTool
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.shortcuts.CanvasShortcutState
import app.orcinus.shadow.core.ui.shortcuts.ReportCanvasShortcutState
import app.orcinus.shadow.core.ui.shortcuts.ShortcutHandler
import app.orcinus.shadow.domain.shortcuts.FilamentDigits
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.PlateViewCamera
import app.orcinus.shadow.render.scene.ToolWheel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the Prepare page's canvas does with OrcaSlicer's keys: GLCanvas3D's
 * on_char() and on_key() and its gizmos', each through the action the
 * toolbar, the menus or the tool's window run for it, under the same
 * conditions (the tools' is_activable(), Plater's can_*()).
 */
internal class PrepareShortcutActions(
    val selectObject: (Int?) -> Unit,
    val deleteObject: (index: Int) -> Unit,
    val objectMenu: PrepareObjectMenuActions,
    val paste: () -> Unit,
    val plateMenu: PlateMenuActions,
    val plate: PlateActions,
    val arrange: ArrangeActions,
    val autoOrient: () -> Unit,
    val addInstance: () -> Unit,
    val removeInstance: () -> Unit,
    val undo: () -> Unit,
    val redo: () -> Unit,
    val toggleGizmo: (PlateGizmo) -> Unit,
    val closeGizmo: () -> Unit,
    val togglePainting: (PaintKind) -> Unit,
    val painting: PaintingActions,
    val cut: CutActions,
    val text: TextActions,
    val svg: SvgActions,
    val measure: MeasureActions,
    val assembly: AssemblyActions,
    val assemblyView: AssemblyViewActions,
    val brimEars: BrimEarsActions,
    val meshBoolean: MeshBooleanActions,
    val simplify: SimplifyActions,
    val setCanvas: (key: String, value: String) -> Unit,
    /** Plater::clone_selection(): the Clone dialog over the copy at an index. */
    val askClone: (index: Int) -> Unit,
)

/**
 * The Prepare page's keys while it is shown: it tells the keyboard what its
 * canvas shows (the open tool, the selection), and does what the keys ask.
 */
@Composable
internal fun PrepareShortcuts(state: PrepareUiState, canvas: CanvasPreferences, camera: PlateViewCamera, actions: PrepareShortcutActions) {
    ReportCanvasShortcutState(
        CanvasShortcutState(
            tool = state.openTool(),
            selected = state.selectedObject != null || state.selectedObjects.isNotEmpty(),
            layerEditing = state.layerEditing != null,
            arrangeOpen = state.arrangeOptionsOpen,
            assemblyView = state.assemblyView != null,
        ),
    )
    val digits = remember { FilamentDigits() }
    val scope = rememberCoroutineScope()
    val defaultText = orcaString("Embossed text")
    ShortcutHandler { action -> perform(action, state, canvas, camera, actions, defaultText) { digit -> filamentDigit(digit, digits, state, actions, scope) } }
}

/** GLGizmosManager::get_current_type() of the page's open tool. */
private fun PrepareUiState.openTool(): CanvasTool? = when {
    simplify != null -> CanvasTool.SIMPLIFY
    cut != null -> CanvasTool.CUT
    painting != null -> when (painting.kind) {
        PaintKind.SUPPORTS -> CanvasTool.SUPPORTS
        PaintKind.SEAM -> CanvasTool.SEAM
        PaintKind.FUZZY_SKIN -> CanvasTool.FUZZY_SKIN
        else -> CanvasTool.COLOR
    }
    text != null -> CanvasTool.TEXT
    svg != null -> CanvasTool.SVG
    measure != null -> if (measure.assembly != null) CanvasTool.ASSEMBLY else CanvasTool.MEASURE
    meshBoolean != null -> CanvasTool.MESH_BOOLEAN
    brimEars != null -> CanvasTool.BRIM_EARS
    else -> when (gizmo) {
        PlateGizmo.MOVE -> CanvasTool.MOVE
        PlateGizmo.ROTATE -> CanvasTool.ROTATE
        PlateGizmo.SCALE -> CanvasTool.SCALE
        PlateGizmo.LAY_ON_FACE -> CanvasTool.LAY_ON_FACE
        null -> null
    }
}

private fun perform(
    action: ShortcutAction,
    state: PrepareUiState,
    canvas: CanvasPreferences,
    camera: PlateViewCamera,
    actions: PrepareShortcutActions,
    defaultText: String,
    filamentDigit: (Int) -> Unit,
): Boolean {
    val selected = state.selectedObject ?: state.selectedObjects.minOrNull()
    val editable = state.canEditPlate
    when (action) {
        ShortcutAction.Undo -> if (state.canUndo) actions.undo()
        ShortcutAction.Redo -> if (state.canRedo) actions.redo()
        is ShortcutAction.SelectView -> action.view?.let(camera::selectView) ?: camera.defaultView()
        is ShortcutAction.Zoom -> camera.zoomStep(action.zoomIn)
        is ShortcutAction.MoveSelection -> return camera.moveSelection(action.x, action.y, action.fine, action.cameraSpace)
        is ShortcutAction.RotateSelection -> return camera.rotateSelection(action.counterclockwise)
        ShortcutAction.EndKeyManipulation -> camera.endKeyManipulation()
        ShortcutAction.CloseArrangeOptions -> if (state.arrangeOptionsOpen) actions.arrange.toggle()
        ShortcutAction.EscapeTool -> escapeTool(state, actions)
        // GLCanvas3D::deselect_all()
        ShortcutAction.DeselectAll -> if (selected != null) actions.selectObject(null)
        ShortcutAction.ToolDelete -> when {
            // delete_selected_connectors()
            state.cut != null -> if (state.cut.selectedConnectors.isNotEmpty()) actions.cut.connectors.deleteSelected()
            // GLGizmoMeasure: reset_all_feature()
            state.measure != null -> actions.measure.reset(MeasureReset.ALL)
        }
        is ShortcutAction.ShiftCutPlane -> actions.cut.shift(action.millimeters)
        is ShortcutAction.SelectPaintTool -> actions.painting.setTool(action.tool)
        is ShortcutAction.FilamentDigit -> filamentDigit(action.digit)
        is ShortcutAction.ToggleTool -> toggleTool(action.tool, state, camera, actions, defaultText)
        ShortcutAction.ToggleLabels -> actions.setCanvas(AppConfigKeys.SHOW_LABELS, (!canvas.labels).toString())
        ShortcutAction.ToggleGcodeWindow -> actions.setCanvas(AppConfigKeys.SHOW_GCODE_WINDOW, (!canvas.gcodeWindow).toString())
        // Plater::remove_selected()
        ShortcutAction.DeleteSelected -> if (editable && selected != null) deleteSelected(state, selected, actions)
        // MainFrame::can_delete_all()
        ShortcutAction.DeleteAll -> if (editable && state.sceneCopies.isNotEmpty()) actions.plateMenu.deleteAll()
        ShortcutAction.SelectPlateObjects -> actions.plateMenu.selectPlateObjects()
        ShortcutAction.SelectAll -> actions.plateMenu.selectAllPlates()
        ShortcutAction.Copy -> if (selected != null) copy(state, selected, actions, cut = false)
        ShortcutAction.Cut -> if (editable && selected != null) copy(state, selected, actions, cut = true)
        ShortcutAction.Paste -> when {
            !editable -> Unit
            selected != null -> actions.objectMenu.selection?.paste?.invoke()
            state.canPasteOnPlate -> actions.paste()
        }
        ShortcutAction.Clone -> if (editable && selected != null && state.canCopy) actions.askClone(selected)
        ShortcutAction.Arrange -> if (state.canArrange) actions.arrange.arrange()
        ShortcutAction.ArrangePlate -> if (state.canArrange) actions.plate.arrange(state.currentPlate)
        ShortcutAction.Orient -> if (state.canArrange) actions.autoOrient()
        ShortcutAction.OrientPlate -> if (state.canArrange) actions.plate.orient(state.currentPlate)
        ShortcutAction.IncreaseInstances -> if (state.canCopy) actions.addInstance()
        ShortcutAction.DecreaseInstances -> if (state.canRemoveCopy) actions.removeInstance()
        ShortcutAction.TogglePrintable -> if (editable && selected != null) togglePrintable(state, selected, actions)
        else -> return false
    }
    return true
}

/** Esc with a gizmo open: GLGizmoMeasure's and GLGizmoAssembly's own Escape, GLGizmoSimplify::on_esc_key_down(), else reset_all_states(). */
private fun escapeTool(state: PrepareUiState, actions: PrepareShortcutActions) {
    when {
        state.simplify != null -> actions.simplify.cancel()
        state.measure != null -> actions.measure.escape()
        state.cut != null -> actions.cut.cancel()
        state.painting != null -> actions.painting.close()
        state.text != null -> actions.text.close()
        state.svg != null -> actions.svg.close()
        state.meshBoolean != null -> actions.meshBoolean.close()
        state.brimEars != null -> actions.brimEars.close()
        state.gizmo != null -> actions.closeGizmo()
    }
}

/** handle_shortcut(): the gizmo opens, or closes, as its toolbar button does while it is enabled. */
private fun toggleTool(tool: CanvasTool, state: PrepareUiState, camera: PlateViewCamera, actions: PrepareShortcutActions, defaultText: String) {
    fun gizmo(gizmo: PlateGizmo) {
        if (state.canOpen(gizmo)) actions.toggleGizmo(gizmo)
    }
    fun paint(kind: PaintKind) {
        if (state.canPaintFacets || state.painting?.kind == kind) actions.togglePainting(kind)
    }
    when (tool) {
        CanvasTool.MOVE -> gizmo(PlateGizmo.MOVE)
        CanvasTool.ROTATE -> gizmo(PlateGizmo.ROTATE)
        CanvasTool.SCALE -> gizmo(PlateGizmo.SCALE)
        CanvasTool.LAY_ON_FACE -> gizmo(PlateGizmo.LAY_ON_FACE)
        CanvasTool.CUT -> if (state.cutSelectable && (state.canCut || state.cut != null)) actions.cut.toggle()
        CanvasTool.MESH_BOOLEAN -> if (state.canMeshBoolean || state.meshBoolean != null) actions.meshBoolean.toggle()
        CanvasTool.SUPPORTS -> paint(PaintKind.SUPPORTS)
        CanvasTool.SEAM -> paint(PaintKind.SEAM)
        CanvasTool.FUZZY_SKIN -> paint(PaintKind.FUZZY_SKIN)
        // GLGizmoMmuSegmentation::on_is_selectable(): more than one filament.
        CanvasTool.COLOR -> if (state.filamentColors.size > 1 && (state.canPaint || state.painting?.kind == PaintKind.COLOR)) actions.togglePainting(PaintKind.COLOR)
        // GLGizmoEmboss::on_shortcut_key(): on the selected copy, where its volume nearest the view's centre is hit.
        CanvasTool.TEXT -> if (state.canEditPlate) actions.text.toggle(state.selectedObject?.let { camera.surfaceHit(it) }, camera.bedPoint(), defaultText)
        CanvasTool.MEASURE -> if (state.canMeasure || (state.measure != null && state.measure.assembly == null)) actions.measure.toggle()
        CanvasTool.ASSEMBLY -> if (state.canAssemble || state.measure?.assembly != null) actions.assembly.toggle()
        CanvasTool.BRIM_EARS -> if (state.canEditBrimEars || state.brimEars != null) actions.brimEars.toggle()
        CanvasTool.SVG, CanvasTool.SIMPLIFY -> Unit
    }
}

/** Plater::remove_selected(): the volumes selected, the copies of a selection of several, or the copy. */
private fun deleteSelected(state: PrepareUiState, selected: Int, actions: PrepareShortcutActions) {
    val volume = state.selectedVolume?.id
    when {
        state.volumeGroup.size > 1 -> actions.objectMenu.volumes?.invoke(selected)?.delete?.invoke()
        volume != null -> actions.objectMenu.part?.delete?.invoke(volume)
        state.selectedObjects.size > 1 -> actions.objectMenu.selection?.delete?.invoke()
        state.sceneCopies.getOrNull(selected) != null -> actions.deleteObject(selected)
    }
}

/** Plater::copy_selection_to_clipboard() and cut_selection_to_clipboard(): the volume selected alone, or the selected copies. */
private fun copy(state: PrepareUiState, selected: Int, actions: PrepareShortcutActions, cut: Boolean) {
    val volume = state.selectedVolume?.id
    val part = actions.objectMenu.part
    when {
        volume != null && part != null -> part.copy(selected, volume.index, cut)
        cut -> actions.objectMenu.selection?.cut?.invoke()
        else -> actions.objectMenu.selection?.copy?.invoke()
    }
}

/** ObjectList::toggle_printable_state(): the first selected copy's printable state, turned, for every selected copy. */
private fun togglePrintable(state: PrepareUiState, selected: Int, actions: PrepareShortcutActions) {
    val printable = !(state.sceneCopies.getOrNull(selected)?.instance?.printable ?: return)
    if (state.selectedObjects.size > 1) actions.objectMenu.selection?.setPrintable?.invoke(printable) else actions.objectMenu.setPrintable(selected, printable)
}

/**
 * A digit (m_timer_set_color): the filament the colour tool paints with
 * (on_number_key_down(), 0 for none), or that of the selected items
 * (set_extruder_for_selected_items()), a filament the plate has.
 */
private fun filamentDigit(digit: Int, digits: FilamentDigits, state: PrepareUiState, actions: PrepareShortcutActions, scope: CoroutineScope) {
    fun apply(filament: Int) {
        if (filament > state.filamentColors.size) return
        if (state.painting?.kind == PaintKind.COLOR) {
            actions.painting.setState(filament)
        } else if (filament >= 1 && state.canEditPlate) {
            actions.objectMenu.selection?.setFilament?.invoke(filament)
        }
    }
    val now = SystemClock.uptimeMillis()
    val filament = digits.press(digit, now)
    if (filament != null) {
        apply(filament)
        return
    }
    scope.launch {
        delay(FilamentDigits.WAIT_MILLIS)
        digits.expire(SystemClock.uptimeMillis())?.let(::apply)
    }
}

/**
 * GLGizmosManager::on_mouse_wheel() of the painting and brim ears tools, and
 * the assembly view's: Ctrl turns the painting tool's brush, its height range,
 * its fill's angle or its gap area by a step; Alt moves the section by a
 * hundredth, and in the assembly view Ctrl spreads it by a hundredth. True
 * when the tool took the turn, which then does not zoom.
 */
internal fun toolWheel(wheel: ToolWheel, state: PrepareUiState, actions: PrepareShortcutActions): Boolean {
    val sign = if (wheel.up) 1.0 else -1.0
    state.painting?.let { mode ->
        if (wheel.ctrl) {
            when (mode.tool) {
                PaintTool.HEIGHT_RANGE -> actions.painting.setCursorHeight((mode.cursorHeight + sign * CURSOR_HEIGHT_STEP).coerceIn(CURSOR_HEIGHT_MIN, CURSOR_HEIGHT_MAX))
                PaintTool.BRUSH, PaintTool.CIRCLE -> actions.painting.setRadius((mode.radius + sign * CURSOR_RADIUS_STEP).coerceIn(mode.radiusMin, CURSOR_RADIUS_MAX))
                PaintTool.FILL, PaintTool.BUCKET -> actions.painting.setFillAngle((mode.fillAngle + sign * SMART_FILL_ANGLE_STEP).coerceIn(0.0, SMART_FILL_ANGLE_MAX))
                PaintTool.GAP_FILL -> actions.painting.setGapArea((mode.gapArea + sign * GAP_AREA_STEP).coerceIn(0.0, GAP_AREA_MAX))
                PaintTool.TRIANGLE -> return false
            }
            return true
        }
        if (wheel.alt) {
            actions.painting.setSection((mode.sectionPosition + sign * SECTION_STEP).coerceIn(0.0, 1.0))
            return true
        }
        return false
    }
    state.brimEars?.let { mode ->
        if (!wheel.alt) return false
        actions.brimEars.setSection((mode.sectionPosition + sign * SECTION_STEP).coerceIn(0.0, 1.0))
        return true
    }
    state.assemblyView?.let { mode ->
        // GLCanvas3D::on_mouse_wheel() of the assembly view: Alt the clipping plane, Ctrl the explosion ratio.
        when {
            wheel.alt -> actions.assemblyView.setSectionPosition((mode.sectionPosition + sign * SECTION_STEP).coerceIn(0.0, 1.0))
            wheel.ctrl -> actions.assemblyView.setExplosionRatio(mode.explosionRatio + sign * SECTION_STEP)
            else -> return false
        }
        return true
    }
    return false
}

// GLGizmoPainterBase's CursorRadiusMax and CursorRadiusStep, CursorHeightMin, -Max and -Step,
// SmartFillAngleMax and -Step, TriangleSelectorPatch's GapAreaMax and GapAreaStep, and the
// clipper's step of a hundredth.
private const val CURSOR_RADIUS_MAX = 8.0
private const val CURSOR_RADIUS_STEP = 0.2
private const val CURSOR_HEIGHT_MIN = 0.1
private const val CURSOR_HEIGHT_MAX = 8.0
private const val CURSOR_HEIGHT_STEP = 0.2
private const val SMART_FILL_ANGLE_MAX = 90.0
private const val SMART_FILL_ANGLE_STEP = 1.0
private const val GAP_AREA_MAX = 5.0
private const val GAP_AREA_STEP = 0.2
private const val SECTION_STEP = 0.01
