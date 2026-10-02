package app.orcinus.shadow.render.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The place of GLCanvas3D::_render_3d_navigator(): a square of the page's
 * layout, beside the canvas toolbar, where the [PlateView] that [camera]
 * drives draws ImGuizmo::ViewManipulate()'s cube. The view draws the cube and
 * takes the touches on it itself, as OrcaSlicer's canvas holds the ImGui
 * window: a finger on a face, an edge or a corner of the cube is the
 * navigator's, and one beside it goes on turning the view.
 *
 * [faceLabels] are the faces' names in ImGuizmo::FACES order: back, top,
 * right, front, bottom and left.
 */
@Composable
fun PlateNavigator(camera: PlateViewCamera, faceLabels: List<String>, modifier: Modifier = Modifier) {
    DisposableEffect(camera) { onDispose { camera.navigatorSlot = null } }
    Box(
        modifier
            .size(NAVIGATOR_SIZE)
            .onGloballyPositioned { coordinates -> camera.navigatorSlot = NavigatorSlot(coordinates.boundsInRoot(), faceLabels) },
    )
}

/** Where the page placed the navigator, in the root's pixels, and the names of its faces. */
internal data class NavigatorSlot(val bounds: Rect, val faceLabels: List<String>)

/**
 * The navigator of a view: ViewManipulate()'s state, the box the finger
 * holds, and the clicks whose turn is under way. [square] is where it stands
 * in the view, in pixels; null while the page shows none.
 */
internal class NavigatorInput(private val controller: PlateViewController) {
    val navigator = ViewNavigator()
    var pressedBox by mutableIntStateOf(-1)
    var turns by mutableIntStateOf(0)
    var square: Rect? = null
    var density = 1f
    var touchSlop = 0f

    /**
     * ImGuizmo captures the mouse over a box of the cube (SetNextFrameWantCaptureMouse()),
     * so GLCanvas3D::on_mouse() leaves it alone: when [down] presses a box, the
     * whole gesture is the navigator's and true is returned; otherwise the view
     * takes it.
     */
    suspend fun AwaitPointerEventScope.handle(down: PointerInputChange): Boolean {
        val square = square ?: return false
        if (!square.contains(down.position)) return false
        val origin = square.topLeft
        if (!navigator.press(controller.navigatorView.value, down.position.x - origin.x, down.position.y - origin.y, square.width)) return false
        down.consume()
        pressedBox = navigator.pressedBox
        var last = down.position
        var travelled = 0f
        var dragging = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            val delta = change.position - last
            last = change.position
            travelled += hypot(delta.x, delta.y)
            // A fingertip trembles: the click becomes a drag past the touch slop, not at the first motion.
            if (!dragging && travelled <= touchSlop) continue
            dragging = true
            // ImGui's MouseDelta, in desktop pixels.
            navigator.drag(controller.navigatorView.value, delta.x / density, delta.y / density)
                ?.let { controller.turnFromNavigator(it, dragging = true, clickedBox = -1) }
            pressedBox = navigator.pressedBox
        }
        val clicked = navigator.release()
        pressedBox = -1
        if (clicked >= 0) {
            controller.turnFromNavigator(controller.navigatorView.value, dragging = false, clickedBox = clicked)
            turns++
        }
        return true
    }
}

/**
 * GLCanvas3D::_render_3d_navigator(): the cube in [square] of the view, turned
 * as the camera looks at the plate, with the plate's axes from its corner;
 * the turn a click started goes on frame by frame. ImGuizmo fills every face
 * with one flat colour; on a phone's small cube the faces are shaded by where
 * they turn, edged, and their names set small to fit them, so the cube reads
 * as one at a glance.
 */
@Composable
internal fun NavigatorCube(controller: PlateViewController, input: NavigatorInput, square: Rect, faceLabels: List<String>) {
    val view by controller.navigatorView.collectAsState()
    val navigator = input.navigator
    LaunchedEffect(navigator, input.turns) {
        while (navigator.turning) {
            withFrameNanos {}
            navigator.step(controller.navigatorView.value, CAMERA_DISTANCE)?.let { controller.turnFromNavigator(it, dragging = false, clickedBox = -1) }
        }
    }

    val density = LocalDensity.current
    val colors = OrcaTheme.colors
    val dark = colors.isDark
    val labelStyle = OrcaTheme.typography.head10.copy(letterSpacing = 0.4.sp)
    val axisStyle = OrcaTheme.typography.head10
    val textMeasurer = rememberTextMeasurer()
    val faceTexts = remember(faceLabels, labelStyle) { faceLabels.map { textMeasurer.measure(it.uppercase(), labelStyle) } }
    val axisTexts = remember(axisStyle) { AXIS_LABELS.map { textMeasurer.measure(it, axisStyle) } }
    val faceColor = if (dark) FACE_DARK else FACE_LIGHT
    val edgeColor = if (dark) EDGE_DARK else EDGE_LIGHT
    val textColor = if (dark) TEXT_DARK else TEXT_LIGHT
    val highlight = colors.accent.copy(alpha = HIGHLIGHT_ALPHA)
    val pressedBox = input.pressedBox

    Canvas(
        Modifier
            .offset { IntOffset(square.left.roundToInt(), square.top.roundToInt()) }
            .size(with(density) { square.width.toDp() }),
    ) {
        val drawing = navigator.draw(view, size.width, pressedBox)
        val hairline = EDGE_WIDTH.toPx()
        for (face in drawing.faces) {
            val path = face.corners.toPath()
            // Lit from the camera's upper left, as the realistic view lights the plate (LIGHT_TOP_DIR).
            val n = face.viewNormal
            val lambert = (n.x * LIGHT.x + n.y * LIGHT.y + n.z * LIGHT.z).coerceAtLeast(0f)
            val shade = AMBIENT + (1f - AMBIENT) * lambert
            drawPath(path, Color(faceColor.red * shade, faceColor.green * shade, faceColor.blue * shade, 1f))
        }
        for (panel in drawing.panels) {
            if (panel.pressed) drawPath(panel.corners.toPath(), highlight)
        }
        for (face in drawing.faces) drawPath(face.corners.toPath(), edgeColor, style = Stroke(hairline))
        // In a frontal view a face spans half the square and the text is drawn at its own size.
        val faceWidth = size.width * 0.5f
        for (label in drawing.labels) {
            val text = faceTexts.getOrNull(label.face) ?: continue
            val halfWidth = text.size.width * 0.5f
            val halfHeight = text.size.height * 0.5f
            val scale = (faceWidth * LABEL_FILL / text.size.width).coerceAtMost(1f)
            // The text's vertices go where ViewManipulate() puts them, which an orthographic cube maps affinely.
            val origin = label.map(0f, 0f, halfWidth, halfHeight, scale)
            val unitX = label.map(1f, 0f, halfWidth, halfHeight, scale)
            val unitY = label.map(0f, 1f, halfWidth, halfHeight, scale)
            val matrix = Matrix().apply {
                values[Matrix.ScaleX] = unitX.x - origin.x
                values[Matrix.SkewY] = unitX.y - origin.y
                values[Matrix.SkewX] = unitY.x - origin.x
                values[Matrix.ScaleY] = unitY.y - origin.y
                values[Matrix.TranslateX] = origin.x
                values[Matrix.TranslateY] = origin.y
            }
            withTransform({ transform(matrix) }) { drawText(text, textColor) }
        }
        for (axis in drawing.axes) {
            val color = AXIS_COLORS[axis.axis].let { if (axis.visible) it else it.copy(alpha = it.alpha * HIDDEN_AXIS_ALPHA) }
            drawLine(color, Offset(axis.start.x, axis.start.y), Offset(axis.end.x, axis.end.y), AXIS_THICKNESS.toPx(), cap = StrokeCap.Round)
            val text = axisTexts[axis.axis]
            drawText(text, color, Offset(axis.label.x - text.size.width * 0.5f, axis.label.y - text.size.height * 0.5f))
        }
    }
}

private fun List<Vec2>.toPath() = Path().apply {
    moveTo(first().x, first().y)
    for (corner in drop(1)) lineTo(corner.x, corner.y)
    close()
}

/** The navigator's side: ImGuizmo's 128 desktop pixels would crowd a phone's canvas. */
val NAVIGATOR_SIZE = 104.dp

/** camDistance of GLCanvas3D::_render_3d_navigator(), ViewManipulate()'s length. */
private const val CAMERA_DISTANCE = 8f

/** How much of a face's width its name may take. */
private const val LABEL_FILL = 0.84f

private val AXIS_THICKNESS = 1.5.dp
private val EDGE_WIDTH = 1.dp

/** AxisLabels: the navigator's X, Y and Z are the plate's Y, Z and X. */
private val AXIS_LABELS = listOf("Y", "Z", "X")

/** DIRECTION_X, DIRECTION_Y and DIRECTION_Z: ColorRGBA::Y(), Z() and X(). */
private val AXIS_COLORS = listOf(Color(100, 200, 24), Color(47, 136, 233), Color(255, 60, 91))

/** An axis behind the cube is drawn at this part of its alpha. */
private const val HIDDEN_AXIS_ALPHA = 0.35f

/** LIGHT_TOP_DIR of the phong shader, in eye space, and the light every face gets. */
private val LIGHT = Vec4(-0.4574957f, 0.4574957f, 0.7624929f)
private const val AMBIENT = 0.62f

private val FACE_DARK = Color(0xFF5A5A62)
private val FACE_LIGHT = Color(0xFFF4F4F5)
private val EDGE_DARK = Color(0x66FFFFFF)
private val EDGE_LIGHT = Color(0x40000000)
private val TEXT_DARK = Color(0xFFEFEFF0)
private val TEXT_LIGHT = Color(0xFF323A3D)

/** The box under the finger, in the app's accent. */
private const val HIGHLIGHT_ALPHA = 0.55f
