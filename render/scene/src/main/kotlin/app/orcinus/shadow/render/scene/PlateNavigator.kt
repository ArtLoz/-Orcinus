package app.orcinus.shadow.render.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.hypot

/**
 * GLCanvas3D::_render_3d_navigator(): ImGuizmo::ViewManipulate()'s cube,
 * turned as the camera of the view [camera] drives looks at the plate, with
 * the plate's axes from its corner. A tap on a face, an edge or a corner turns
 * the camera over the next frames to look from that side; dragging the cube
 * turns the camera. A finger that misses the cube goes on to the view under it.
 *
 * [faceLabels] are the faces' names in ImGuizmo::FACES order: back, top,
 * right, front, bottom and left.
 */
@Composable
fun PlateNavigator(camera: PlateViewCamera, faceLabels: List<String>, modifier: Modifier = Modifier) {
    val controller = camera.controller
    val sizeModifier = modifier.size(NAVIGATOR_SIZE)
    if (controller == null) {
        Canvas(sizeModifier) {}
        return
    }
    val view by controller.navigatorView.collectAsState()
    val navigator = remember(controller) { ViewNavigator() }
    // The box the finger holds, highlighted, and the turns a click started.
    var pressedBox by remember(controller) { mutableIntStateOf(-1) }
    var turns by remember(controller) { mutableIntStateOf(0) }
    LaunchedEffect(navigator, turns) {
        while (navigator.turning) {
            withFrameNanos {}
            navigator.step(controller.navigatorView.value, CAMERA_DISTANCE)?.let { controller.turnFromNavigator(it, dragging = false, clickedBox = -1) }
        }
    }

    val density = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val dark = OrcaTheme.colors.isDark
    // ImGuiWrapper's font, 18 desktop pixels.
    val textStyle = OrcaTheme.typography.body14.copy(fontSize = with(LocalDensity.current) { LABEL_FONT_SIZE.toSp() })
    val textMeasurer = rememberTextMeasurer()
    val faceTexts = remember(faceLabels, textStyle) { faceLabels.map { textMeasurer.measure(it, textStyle) } }
    val axisTexts = remember(textStyle) { AXIS_LABELS.map { textMeasurer.measure(it, textStyle) } }
    val faceColor = if (dark) FACE_DARK else FACE_LIGHT
    val textColor = if (dark) TEXT_DARK else TEXT_LIGHT

    val input = PointerInputEventHandler {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val side = size.width.toFloat()
            if (!navigator.press(controller.navigatorView.value, down.position.x, down.position.y, side)) return@awaitEachGesture
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
        }
    }

    Canvas(sizeModifier.then(SharedPointerInputElement(listOf(controller, navigator, density, touchSlop), input))) {
        val drawing = navigator.draw(view, size.width, pressedBox)
        for (panel in drawing.panels) {
            val path = Path().apply {
                moveTo(panel.corners[0].x, panel.corners[0].y)
                for (corner in panel.corners.drop(1)) lineTo(corner.x, corner.y)
                close()
            }
            drawPath(path, faceColor)
            if (panel.pressed) drawPath(path, HIGHLIGHT)
        }
        for (label in drawing.labels) {
            val text = faceTexts.getOrNull(label.face) ?: continue
            val halfWidth = text.size.width * 0.5f
            val halfHeight = text.size.height * 0.5f
            // The text's vertices go where ViewManipulate() puts them, which an orthographic cube maps affinely.
            val origin = label.map(0f, 0f, halfWidth, halfHeight)
            val unitX = label.map(1f, 0f, halfWidth, halfHeight)
            val unitY = label.map(0f, 1f, halfWidth, halfHeight)
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
            drawLine(color, Offset(axis.start.x, axis.start.y), Offset(axis.end.x, axis.end.y), AXIS_THICKNESS.toPx())
            val text = axisTexts[axis.axis]
            drawText(text, color, Offset(axis.label.x - text.size.width * 0.5f, axis.label.y - text.size.height * 0.5f))
        }
    }
}

/** The navigator's side, 128 desktop pixels. */
val NAVIGATOR_SIZE = 128.dp

/** camDistance of GLCanvas3D::_render_3d_navigator(), ViewManipulate()'s length. */
private const val CAMERA_DISTANCE = 8f

private val LABEL_FONT_SIZE = 18.dp

/** Style::TranslationLineThickness */
private val AXIS_THICKNESS = 3.dp

/** AxisLabels: the navigator's X, Y and Z are the plate's Y, Z and X. */
private val AXIS_LABELS = listOf("Y", "Z", "X")

/** DIRECTION_X, DIRECTION_Y and DIRECTION_Z: ColorRGBA::Y(), Z() and X(). */
private val AXIS_COLORS = listOf(Color(100, 200, 24), Color(47, 136, 233), Color(255, 60, 91))

/** An axis behind the cube is drawn at this part of its alpha. */
private const val HIDDEN_AXIS_ALPHA = 0.35f

private val FACE_DARK = Color(0.23f, 0.23f, 0.23f)
private val FACE_LIGHT = Color(0.77f, 0.77f, 0.77f)
private val TEXT_DARK = Color(224, 224, 224)
private val TEXT_LIGHT = Color(0.2f, 0.2f, 0.2f)

/** IM_COL32(0xF0, 0xA0, 0x60, 0x80) over the box under the pointer. */
private val HIGHLIGHT = Color(0xF0, 0xA0, 0x60, 0x80)

/**
 * pointerInput whose pointers also reach the siblings under it
 * (PointerInputModifierNode.sharePointerInputWithSiblings()), so a finger
 * that the navigator leaves unconsumed turns the view under it.
 */
private class SharedPointerInputElement(private val key: Any, private val handler: PointerInputEventHandler) :
    ModifierNodeElement<SharedPointerInputNode>() {
    override fun create() = SharedPointerInputNode(handler)

    override fun update(node: SharedPointerInputNode) = Unit

    override fun equals(other: Any?) = other is SharedPointerInputElement && other.key == key

    override fun hashCode() = key.hashCode()
}

private class SharedPointerInputNode(handler: PointerInputEventHandler) : DelegatingNode(), PointerInputModifierNode {
    private val input = delegate(SuspendingPointerInputModifierNode(handler))

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) =
        input.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = input.onCancelPointerInput()

    override fun sharePointerInputWithSiblings() = true
}
