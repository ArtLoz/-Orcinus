package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaSpinInput
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * OrcaSlicer's RammingDialog: the speeds a single-extruder multi-material
 * printer pushes the filament out with before it changes to another one, as a
 * curve through points dragged up and down, with the total time and volume
 * and the width and spacing of the ramming line. The desktop app warns what
 * ramming is every time the dialog opens, and so does this one. On a touch
 * screen a switch replaces the Ctrl key the desktop app holds for a constant
 * flow rate. OK writes the parameters the dialog built ([onApply]).
 */
@Composable
fun RammingDialog(parameters: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    val chart = remember(parameters) { RammingChart.parse(parameters) }
    // The chart changes in place; this makes what shows it read it again.
    var revision by remember { mutableIntStateOf(0) }
    var uniform by rememberSaveable { mutableStateOf(false) }
    var warned by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window),
        ) {
            FullScreenDialogTopBar(
                title = orcaString("Ramming customization"),
                okEnabled = true,
                onCancel = onDismiss,
                onOk = { onApply(chart.parameters()) },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                RammingChartView(chart, revision, uniform, onChanged = { revision++ })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ramming_constant_flow),
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    OrcaSwitch(checked = uniform, onCheckedChange = { uniform = it })
                }

                // Total ramming: the time sets the chart's width; the volume only shows.
                val time = remember(revision) { (chart.time * 1000).toInt() }
                val volume = remember(revision) { chart.volume.toInt() }
                RammingTitle(orcaString("Total ramming"))
                RammingSpin(orcaString("Time"), time, 0..5000, 250, orcaString("ms")) {
                    chart.setTime(it)
                    revision++
                }
                RammingSpin(orcaString("Volume"), volume, 0..10000, 1, orcaString("mm³"), enabled = false) {}

                val lineWidth = remember(revision) { chart.lineWidth }
                val lineSpacing = remember(revision) { chart.lineSpacing }
                RammingTitle(orcaString("Ramming line"), top = 16.dp)
                RammingSpin(orcaString("Width"), lineWidth, 10..300, 1, "%") {
                    chart.lineWidth = it
                    revision++
                }
                RammingSpin(orcaString("Spacing"), lineSpacing, 10..300, 1, "%") {
                    chart.lineSpacing = it
                    revision++
                }
            }
        }
        if (!warned) {
            AlertDialog(
                onDismissRequest = { warned = true },
                confirmButton = { OrcaButton(orcaString("OK"), onClick = { warned = true }) },
                title = { Text(orcaString("Warning"), style = OrcaTheme.typography.head16) },
                text = {
                    Text(
                        orcaString(RAMMING_WARNING),
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                containerColor = colors.window,
                titleContentColor = colors.text,
                textContentColor = colors.text,
                shape = OrcaTheme.shapes.window,
            )
        }
    }
}

@Composable
private fun RammingTitle(text: String, top: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text,
        color = OrcaTheme.colors.textLabel,
        style = OrcaTheme.typography.head14,
        modifier = Modifier.padding(top = top, bottom = 8.dp),
    )
}

/** A row of the dialog's parameters: the label, and its SpinInput. */
@Composable
private fun RammingSpin(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = OrcaTheme.colors.textLabel, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        OrcaSpinInput(
            value = value,
            onValueChange = onValueChange,
            range = range,
            step = step,
            unit = unit,
            enabled = enabled,
            decreaseDescription = "$label −",
            increaseDescription = "$label +",
            modifier = Modifier.width(180.dp),
        )
    }
}

/**
 * Chart::draw() and its mouse handling: the curve, filled from below in the
 * colours that grow from green to red with the speed, the points with their
 * speeds above them, and the axes. The chart is laid out in the desktop app's
 * own pixels (RammingChart) and scaled to the screen; a finger drags the point
 * nearest to it, and the drag ends when the finger leaves the chart, as the
 * mouse's does.
 */
@Composable
private fun RammingChartView(chart: RammingChart, revision: Int, uniform: Boolean, onChanged: () -> Unit) {
    val colors = OrcaTheme.colors
    val measurer = rememberTextMeasurer()
    val texts = ChartTexts(
        noRamming = orcaString("NO RAMMING AT ALL"),
        time = orcaString("Time") + " (" + orcaString("s") + ")",
        speed = orcaString("Volumetric speed") + " (" + orcaString("mm³/s") + ")",
    )
    // What the chart shows after its last change.
    val snapshot = remember(revision) { ChartSnapshot.of(chart) }
    val density = LocalDensity.current
    val touchRadius = with(density) { TOUCH_RADIUS.toPx() }
    val currentUniform by rememberUpdatedState(uniform)
    val currentOnChanged by rememberUpdatedState(onChanged)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(RammingChart.CHART_WIDTH.toFloat() / RammingChart.CHART_HEIGHT)
            .pointerInput(chart) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val scale = size.width.toFloat() / RammingChart.CHART_WIDTH
                    val index = nearestButton(chart, down.position, scale, touchRadius) ?: return@awaitEachGesture
                    down.consume()
                    var previous = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // mouse_left_window(): outside the chart the drag ends.
                        val x = change.position.x / scale
                        val y = change.position.y / scale
                        val half = BUTTON_SIDE / 2f
                        if (x < RammingChart.RECT_LEFT + half || x > RammingChart.RECT_RIGHT - half ||
                            y < RammingChart.RECT_TOP + half || y > RammingChart.RECT_BOTTOM - half
                        ) {
                            break
                        }
                        val deltaY = (change.position.y - previous.y) / scale
                        if (deltaY != 0f) {
                            chart.drag(index, deltaY.toDouble(), currentUniform)
                            currentOnChanged()
                        }
                        previous = change.position
                        change.consume()
                    }
                }
            },
    ) {
        val scale = size.width / RammingChart.CHART_WIDTH
        drawChart(snapshot, scale, measurer, texts, ChartColors(colors.sidebarTitleBottom, colors.border, colors.textLabel, colors.accent))
    }
}

private class ChartColors(val area: Color, val border: Color, val text: Color, val button: Color)

private class ChartTexts(val noRamming: String, val time: String, val speed: String)

/** A draggable point in chart pixels, with the speed it stands for. */
private class ChartButton(val x: Int, val y: Int, val speed: Double, val visible: Boolean)

/** The chart as it is drawn: its line, its points, and the time it shows. */
private class ChartSnapshot(val line: IntArray, val buttons: List<ChartButton>, val visibleWidth: Double, val visibleHeight: Double, val chart: RammingChart) {
    companion object {
        fun of(chart: RammingChart) = ChartSnapshot(
            line = chart.line.copyOf(),
            buttons = (0 until chart.buttonCount).map { b ->
                val (x, y) = chart.button(b)
                val (sx, sy) = chart.mathToScreen(x, y)
                // visible_area.Contains()
                ChartButton(sx, sy, y, x >= 0 && x <= chart.visibleWidth && y >= 0 && y <= chart.visibleHeight)
            },
            visibleWidth = chart.visibleWidth,
            visibleHeight = chart.visibleHeight,
            chart = chart,
        )
    }
}

private fun DrawScope.drawChart(snapshot: ChartSnapshot, scale: Float, measurer: TextMeasurer, texts: ChartTexts, colors: ChartColors) {
    val chart = snapshot.chart
    fun p(x: Number, y: Number) = Offset(x.toFloat() * scale, y.toFloat() * scale)
    val left = RammingChart.RECT_LEFT
    val top = RammingChart.RECT_TOP
    val bottom = RammingChart.RECT_BOTTOM
    val text = TextStyle(color = colors.text, fontSize = (FONT_PX * scale).toSp())

    drawRect(colors.area, p(left, top), Size(RammingChart.RECT_WIDTH * scale, RammingChart.RECT_HEIGHT * scale))
    drawRect(colors.border, p(left, top), Size(RammingChart.RECT_WIDTH * scale, RammingChart.RECT_HEIGHT * scale), style = Stroke(scale))

    if (snapshot.visibleWidth < 0.499) {
        val label = measurer.measure(texts.noRamming, text.copy(color = NO_RAMMING))
        drawText(label, topLeft = p(left + RammingChart.RECT_WIDTH / 2 - RammingChart.LEGEND_SIDE, bottom - RammingChart.RECT_HEIGHT / 2))
        return
    }

    val line = snapshot.line
    if (line.isNotEmpty()) {
        for (i in 0 until line.size - 2) {
            val color = (444 * ((bottom - line[i]) / RammingChart.RECT_HEIGHT.toDouble())).toInt()
            val column = Color(min(222, color), 222 - max(color - 222, 0), 60)
            drawLine(column, p(left + 1 + i, line[i]), p(left + 1 + i, bottom), strokeWidth = scale)
        }
        for (i in 0 until line.size - 2) {
            drawLine(colors.text, p(left + i, line[i]), p(left + i + 1, line[i + 1]), strokeWidth = scale)
        }
    }

    // draggable buttons
    for (button in snapshot.buttons) {
        drawCircle(colors.button, BUTTON_SIDE / 2f * scale, p(button.x, button.y))
        drawCircle(colors.text, BUTTON_SIDE / 2f * scale, p(button.x, button.y), style = Stroke(scale))
    }

    // x-axis
    val tick = BUTTON_SIDE / 2
    var lastMark = -10000f
    var mathX = (0 * 10).toInt() / 10f
    while (mathX < snapshot.visibleWidth) {
        val (x, _) = chart.mathToScreen(mathX.toDouble(), 0.0)
        if (x - lastMark >= RammingChart.LEGEND_SIDE) {
            drawLine(colors.text, p(x, bottom + tick + 1), p(x, bottom - tick), strokeWidth = scale)
            val label = measurer.measure(if (mathX == 0f) "0" else String.format(Locale.ROOT, "%.1f", mathX), text)
            drawText(label, topLeft = Offset(x * scale - label.size.width / 2f, (bottom + 0.8f * 10) * scale))
            lastMark = x.toFloat()
        }
        mathX += 0.1f
    }

    // y-axis
    lastMark = 10000f
    for (mathY in 0 until snapshot.visibleHeight.toInt()) {
        val (_, y) = chart.mathToScreen(0.0, mathY.toDouble())
        if (lastMark - y < RammingChart.LEGEND_SIDE) continue
        drawLine(colors.text, p(left - tick, y), p(left + tick + 1, y), strokeWidth = scale)
        val label = measurer.measure(mathY.toString(), text)
        drawText(label, topLeft = Offset((left - 10) * scale - label.size.width, y * scale - label.size.height / 2f))
        lastMark = y.toFloat()
    }

    // axis labels
    val timeLabel = measurer.measure(texts.time, text)
    drawText(
        timeLabel,
        topLeft = Offset(0.5f * (RammingChart.RECT_RIGHT + left) * scale - timeLabel.size.width / 2f, (bottom + 0.6f * RammingChart.LEGEND_SIDE) * scale),
    )
    val speedLabel = measurer.measure(texts.speed, text)
    val pivot = Offset(speedLabel.size.height / 2f, 0.5f * (bottom + top) * scale)
    rotate(-90f, pivot) {
        drawText(speedLabel, topLeft = Offset(pivot.x - speedLabel.size.width / 2f, pivot.y - speedLabel.size.height / 2f))
    }

    // the value above each button
    for (button in snapshot.buttons) {
        if (!button.visible) continue
        val sx = button.x
        val sy = button.y
        val label = measurer.measure(String.format(Locale.ROOT, "%.1f", button.speed), text)
        val padding = 4f
        val width = label.size.width / scale
        val height = label.size.height / scale
        var labelX = sx - width / 2f
        labelX = labelX.coerceIn(left + padding * 2, max(left + padding * 2, RammingChart.RECT_RIGHT - width - padding * 2))
        var labelY = sy - BUTTON_SIDE / 2f - height - padding * 2
        if (labelY - padding * 2 < top) labelY = sy + BUTTON_SIDE / 2f + padding * 2
        drawRoundRect(
            colors.area.copy(alpha = 0.8f),
            p(labelX - padding, labelY - padding),
            Size((width + 2 * padding) * scale, (height + 2 * padding) * scale),
            CornerRadius(2 * scale),
        )
        drawRoundRect(
            colors.border,
            p(labelX - padding, labelY - padding),
            Size((width + 2 * padding) * scale, (height + 2 * padding) * scale),
            CornerRadius(2 * scale),
            style = Stroke(scale),
        )
        drawText(label, topLeft = p(labelX, labelY))
    }
}

/** The button nearest to a touch, within the touch radius on screen. */
private fun nearestButton(chart: RammingChart, touch: Offset, scale: Float, radius: Float): Int? {
    var best: Int? = null
    var bestDistance = radius
    for (b in 0 until chart.buttonCount) {
        val (x, y) = chart.button(b)
        val (sx, sy) = chart.mathToScreen(x, y)
        val distance = hypot(sx * scale - touch.x, sy * scale - touch.y)
        if (distance <= bestDistance) {
            best = b
            bestDistance = distance
        }
    }
    return best
}

/** The ramming warning RammingDialog shows every time it opens. */
private const val RAMMING_WARNING = "Ramming denotes the rapid extrusion just before a tool change in a single-extruder MM printer. Its purpose is to " +
    "properly shape the end of the unloaded filament so it does not prevent insertion of the new filament and can itself " +
    "be reinserted later. This phase is important and different materials can require different extrusion speeds to get " +
    "the good shape. For this reason, the extrusion rates during ramming are adjustable.\n\nThis is an expert-level " +
    "setting, incorrect adjustment will likely lead to jams, extruder wheel grinding into filament etc."

/** Chart::side: a draggable point's size, the width of an "m" of the desktop app's font. */
private const val BUTTON_SIDE = 10

/** The chart's text in its own pixels. */
private const val FONT_PX = 12f

/** The desktop app's warning colour for a ramming too short to draw. */
private val NO_RAMMING = Color(0xFFFF6F00)

/** How far from a point a finger still takes it. */
private val TOUCH_RADIUS = 28.dp
