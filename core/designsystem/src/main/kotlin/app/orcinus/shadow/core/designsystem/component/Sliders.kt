package app.orcinus.shadow.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val TrackWidth = 4.dp
private val ThumbRadius = 9.dp

/**
 * OrcaSlicer's vertical layer slider: an accent track with the lower and upper
 * visible layer. [label] gives the text shown next to a thumb, for example the
 * layer number and its height. Layers run from 0 to [layerCount] - 1.
 */
@Composable
fun OrcaLayerSlider(
    layerCount: Int,
    lower: Int,
    upper: Int,
    onRangeChange: (lower: Int, upper: Int) -> Unit,
    label: (layer: Int) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val currentLower by rememberUpdatedState(lower)
    val currentUpper by rememberUpdatedState(upper)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .width(96.dp)
            .fillMaxHeight()
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = "${lower + 1}–${upper + 1}"
            },
    ) {
        val travel = with(density) { (maxHeight - ThumbRadius * 2).toPx() }.coerceAtLeast(1f)
        val last = (layerCount - 1).coerceAtLeast(1)
        fun yOf(layer: Int) = with(density) { ThumbRadius.toPx() } + travel * (1f - layer.toFloat() / last)
        fun layerAt(y: Float) = ((1f - (y - with(density) { ThumbRadius.toPx() }) / travel) * last).roundToInt().coerceIn(0, last)

        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .width(ThumbRadius * 2 + 8.dp)
                .fillMaxHeight()
                .pointerInput(layerCount) {
                    var draggingUpper = true
                    detectDragGestures(
                        onDragStart = { start: Offset ->
                            val layer = layerAt(start.y)
                            draggingUpper = abs(layer - currentUpper) <= abs(layer - currentLower)
                        },
                    ) { change, _ ->
                        val layer = layerAt(change.position.y)
                        if (draggingUpper) {
                            onRangeChange(currentLower, layer.coerceAtLeast(currentLower))
                        } else {
                            onRangeChange(layer.coerceAtMost(currentUpper), currentUpper)
                        }
                    }
                }
                .pointerInput(layerCount) {
                    detectTapGestures { position -> onRangeChange(currentLower, layerAt(position.y).coerceAtLeast(currentLower)) }
                },
        ) {
            val x = size.width / 2
            val track = TrackWidth.toPx()
            drawLine(colors.border, Offset(x, yOf(last)), Offset(x, yOf(0)), strokeWidth = track)
            drawLine(colors.accent, Offset(x, yOf(upper)), Offset(x, yOf(lower)), strokeWidth = track)
            for (layer in listOf(lower, upper)) {
                drawCircle(colors.window, ThumbRadius.toPx(), Offset(x, yOf(layer)))
                drawCircle(colors.accent, ThumbRadius.toPx(), Offset(x, yOf(layer)), style = Stroke(2.dp.toPx()))
            }
        }
        for (layer in listOf(upper, lower).distinct()) {
            SliderLabel(
                text = label(layer),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(0, (yOf(layer) - with(density) { 14.dp.toPx() }).roundToInt()) },
            )
        }
    }
}

/** OrcaSlicer's horizontal slider over the moves of the current layer. */
@Composable
fun OrcaMoveSlider(
    moveCount: Int,
    position: Int,
    onPositionChange: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = "${position + 1}"
            },
    ) {
        val start = with(density) { ThumbRadius.toPx() }
        val travel = with(density) { (maxWidth - ThumbRadius * 2).toPx() }.coerceAtLeast(1f)
        val last = (moveCount - 1).coerceAtLeast(1)
        fun xOf(move: Int) = start + travel * move.toFloat() / last
        fun moveAt(x: Float) = ((x - start) / travel * last).roundToInt().coerceIn(0, last)

        Canvas(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(moveCount) {
                    detectDragGestures { change, _ -> onPositionChange(moveAt(change.position.x)) }
                }
                .pointerInput(moveCount) {
                    detectTapGestures { onPositionChange(moveAt(it.x)) }
                },
        ) {
            val y = size.height / 2
            drawLine(colors.border, Offset(xOf(0), y), Offset(xOf(last), y), strokeWidth = TrackWidth.toPx())
            drawLine(colors.accent, Offset(xOf(0), y), Offset(xOf(position), y), strokeWidth = TrackWidth.toPx())
            drawCircle(colors.window, ThumbRadius.toPx(), Offset(xOf(position), y))
            drawCircle(colors.accent, ThumbRadius.toPx(), Offset(xOf(position), y), style = Stroke(2.dp.toPx()))
        }
    }
}

/** The white rounded box with the layer number and height next to a slider thumb. */
@Composable
private fun SliderLabel(text: String, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    Box(
        modifier = modifier
            .clip(OrcaTheme.shapes.control)
            .background(colors.window)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            text.lines().forEach { line ->
                Text(line, color = colors.accent, style = OrcaTheme.typography.body11)
            }
        }
    }
}
