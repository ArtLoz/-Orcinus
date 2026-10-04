package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.gcode.ActualSpeedPoint
import app.orcinus.shadow.render.gcode.ActualSpeedProfile
import app.orcinus.shadow.render.gcode.ToolpathsMoveType
import app.orcinus.shadow.render.gcode.ToolpathsVertex
import app.orcinus.shadow.render.gcode.ToolpathsViewType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Marker::render_position_window() folded: the fold button, the position in
 * the axes' colours, the speed and what the view type colours by.
 */
@Composable
internal fun ToolPositionWindow(
    vertex: ToolpathsVertex,
    viewType: ToolpathsViewType,
    propertiesShown: Boolean,
    onPropertiesShownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val detail = positionDetail(vertex, viewType)
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = colors.window.copy(alpha = 0.8f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OrcaIconButton(
                icon = if (propertiesShown) DesignR.drawable.orca_im_unfold else DesignR.drawable.orca_im_fold,
                contentDescription = stringResourceOf(propertiesShown),
                onClick = { onPropertiesShownChange(!propertiesShown) },
            )
            Spacer(Modifier.width(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // ORCA: the axes in their colours, with fewer decimals on a big print.
                val precision = when (maxOf(vertex.position.x, vertex.position.y, vertex.position.z).roundToInt()) {
                    in 10_000..Int.MAX_VALUE -> 1
                    in 1_000..9_999 -> 2
                    else -> 3
                }
                Text(
                    text = buildAnnotatedString {
                        listOf("X " to AxisX to vertex.position.x, "Y " to AxisY to vertex.position.y, "Z " to AxisZ to vertex.position.z)
                            .forEachIndexed { index, (axis, value) ->
                                if (index > 0) append("  ")
                                withStyle(SpanStyle(color = axis.second)) { append(axis.first) }
                                append(String.format(Locale.ROOT, "%.${precision}f", value))
                            }
                    },
                    color = colors.text,
                    style = OrcaTheme.typography.body13,
                )
                Row {
                    Text(orcaString("Speed: ") + String.format(Locale.ROOT, "%.0f ", vertex.feedrate), color = colors.text, style = OrcaTheme.typography.body13)
                    if (detail.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(detail, color = colors.text, style = OrcaTheme.typography.body13)
                    }
                }
            }
        }
    }
}

/**
 * Marker::render_position_window() unfolded: the move's properties, labels in
 * Orca's colour, and the button of the actual speed profile, which a move
 * that extrudes, travels or wipes has.
 */
@Composable
internal fun ToolPropertiesWindow(
    vertex: ToolpathsVertex,
    profileShown: Boolean,
    onProfileShownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val profileExists = vertex.extrusion || vertex.type == ToolpathsMoveType.Travel || vertex.type == ToolpathsMoveType.Wipe
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = colors.window.copy(alpha = 0.8f)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((label, value) in positionProperties(vertex)) {
                Row {
                    Text(label, color = colors.accent, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
                    Text(value, color = colors.text, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrcaButton(
                    text = if (profileShown) orcaString("Hide") else orcaString("Show"),
                    onClick = { onProfileShownChange(!profileShown) },
                    enabled = profileExists,
                )
                Spacer(Modifier.width(8.dp))
                Text(orcaString("Actual speed profile"), color = colors.text, style = OrcaTheme.typography.body13)
            }
        }
    }
}

/**
 * ActualSpeedImguiWidget::plot() and the table beside it: the actual speed
 * along the move's line with the range's levels on the left, a line at every
 * point (internal ones in Orca's colour), and the positions and speeds; a
 * finger on the plot picks the segment under it, as hovering does, and its
 * two rows light up.
 */
@Composable
internal fun ActualSpeedWindow(profile: ActualSpeedProfile, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    var hovered by remember(profile) { mutableIntStateOf(-1) }
    val points = profile.points
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = colors.window.copy(alpha = 0.8f)) {
        Column(Modifier.padding(10.dp)) {
            val frame = colors.buttonBackground
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PlotHeight)
                    .background(frame, RoundedCornerShape(3.dp))
                    .pointerInput(profile) {
                        awaitEachGesture {
                            var change = awaitFirstDown()
                            while (true) {
                                hovered = segmentAt(points, change.position.x, size.width.toFloat())
                                val event = awaitPointerEvent()
                                change = event.changes.firstOrNull { it.pressed } ?: break
                            }
                        }
                    },
            ) {
                val inner = Rect(PlotPadding.toPx(), PlotPadding.toPx(), size.width - PlotPadding.toPx(), size.height - PlotPadding.toPx())
                val offset = LevelStrip.toPx()
                val sizeY = profile.highest - profile.lowest
                val sizeX = points.last().position - points.first().position
                if (sizeX > 0f && points.size >= 2) {
                    val inverseY = if (sizeY == 0f) 0f else 1f / sizeY
                    val x0 = points.first().position
                    fun x(position: Float) = inner.left + offset + ((position - x0) / sizeX).coerceIn(0f, 1f) * (inner.width - offset)
                    fun y(speed: Float) = inner.top + (1f - ((speed - profile.lowest) * inverseY).coerceIn(0f, 1f)) * inner.height
                    // horizontal levels
                    for ((level, color) in profile.levels) {
                        val levelY = inner.top + (1f - ((level - profile.lowest) * inverseY).coerceIn(0f, 1f)) * inner.height
                        drawLine(rgb(color).copy(alpha = 0.5f), Offset(inner.left + 0.1f * offset, levelY), Offset(inner.left + 0.9f * offset, levelY), 3f)
                    }
                    // vertical positions
                    for (n in 0 until points.size - 1) {
                        val lineX = x(points[n].position)
                        drawLine(if (points[n].internal) GridSecondary else GridMain, Offset(lineX, inner.top), Offset(lineX, inner.bottom))
                    }
                    drawLine(GridMain, Offset(inner.right, inner.top), Offset(inner.right, inner.bottom))
                    // profile
                    for (n in 0 until points.size - 1) {
                        drawLine(
                            if (hovered == n) Orca else ProfileBase,
                            Offset(x(points[n].position), y(points[n].speed)),
                            Offset(x(points[n + 1].position), y(points[n + 1].speed)),
                            2f * density,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text(orcaString("Position") + " (" + orcaString("mm") + ")", color = colors.text, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
                Text(orcaString("Speed") + " (" + orcaString("mm/s") + ")", color = colors.text, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
            }
            Column(Modifier.heightIn(max = TableHeight).verticalScroll(rememberScrollState())) {
                points.forEachIndexed { index, point ->
                    val highlight = hovered >= 0 && (index == hovered || index == hovered + 1)
                    val text = if (highlight) colors.accent else colors.text
                    Row(Modifier.fillMaxWidth().background(if (point.internal) InternalRow else ExternalRow)) {
                        Text(String.format(Locale.ROOT, "%.3f", point.position), color = text, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.ROOT, "%.1f", point.speed), color = text, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** plot()'s hovered segment: the one whose x span holds the finger's, or -1. */
private fun segmentAt(points: List<ActualSpeedPoint>, x: Float, width: Float): Int {
    if (points.size < 2) return -1
    val sizeX = points.last().position - points.first().position
    if (sizeX <= 0f) return -1
    val t = (x / width).coerceIn(0f, 0.9999f)
    for (n in 0 until points.size - 1) {
        val t1 = ((points[n].position - points.first().position) / sizeX).coerceIn(0f, 1f)
        val t2 = ((points[n + 1].position - points.first().position) / sizeX).coerceIn(0f, 1f)
        if (t1 < t && t < t2) return n
    }
    return -1
}

private fun rgb(color: Int) = Color(0xFF000000.toInt() or color)

private val PlotHeight = 135.dp
private val TableHeight = 160.dp
private val PlotPadding = 4.dp
/** plot()'s offset: the strip on the left where the range's levels stand. */
private val LevelStrip = 10.dp
private val GridMain = Color(0.5f, 0.5f, 0.5f, 0.5f)
private val GridSecondary = Color(0f, 150f / 255f, 136f / 255f, 0.5f)
private val ProfileBase = Color(0.8f, 0.8f, 0.8f, 1f)
private val Orca = Color(0f, 150f / 255f, 136f / 255f, 1f)
private val InternalRow = Color(0f, 150f / 255f, 136f / 255f, 0.15f)
private val ExternalRow = Color(0.2f, 0.2f, 0.2f, 0.25f)

@Composable
private fun stringResourceOf(propertiesShown: Boolean): String = if (propertiesShown) orcaString("Hide") else orcaString("Show")

/** The detail of render_position_window(): what the view type colours the move by. */
@Composable
private fun positionDetail(vertex: ToolpathsVertex, viewType: ToolpathsViewType): String {
    val na = orcaString("N/A")
    fun format(pattern: String, value: Float) = String.format(Locale.ROOT, pattern, value)
    return when (viewType) {
        ToolpathsViewType.FeatureType -> when {
            vertex.extrusion -> vertex.role?.let { roleName(it) } ?: na
            vertex.type != ToolpathsMoveType.Noop -> moveTypeName(vertex.type)
            // Noop moves are not shown in the "Line type" view.
            else -> na
        }
        ToolpathsViewType.Height -> orcaString("Height: ") + if (vertex.extrusion) format("%.2f", vertex.height) else na
        ToolpathsViewType.Width -> orcaString("Width: ") + if (vertex.extrusion) format("%.2f", vertex.width) else na
        ToolpathsViewType.VolumetricFlowRate -> orcaString("Flow: ") + if (vertex.extrusion) format("%.2f", vertex.volumetricRate) else na
        ToolpathsViewType.FanSpeed -> orcaString("Fan: ") + format("%.0f", vertex.fanSpeed)
        ToolpathsViewType.Temperature -> orcaString("Temperature: ") + format("%.0f", vertex.temperature)
        ToolpathsViewType.LayerTimeLinear, ToolpathsViewType.LayerTimeLogarithmic -> orcaString("Layer Time: ") + format("%.1f", vertex.layerDuration)
        ToolpathsViewType.Tool -> orcaString("Tool: ") + (vertex.extruder + 1)
        ToolpathsViewType.ColorPrint -> orcaString("Color: ") + (vertex.color + 1)
        ToolpathsViewType.Acceleration -> orcaString("Acceleration: ") + format("%.0f", vertex.acceleration)
        ToolpathsViewType.Jerk -> orcaString("Jerk: ") + format("%.1f", vertex.jerk)
        ToolpathsViewType.PressureAdvance -> orcaString("PA: ") + String.format(Locale.ROOT, "%.4f", vertex.pressureAdvance)
        else -> ""
    }
}

/** The properties table of render_position_window(), in its order. */
@Composable
private fun positionProperties(vertex: ToolpathsVertex): List<Pair<String, String>> {
    val na = orcaString("N/A")
    val mm = orcaString("mm")
    val mmPerSecond = orcaString("mm/s")
    fun format(pattern: String, value: Float) = String.format(Locale.ROOT, pattern, value)
    return listOf(
        orcaString("Type") to moveTypeName(vertex.type),
        orcaString("Line Type") to if (vertex.extrusion) vertex.role?.let { roleName(it) } ?: na else na,
        orcaString("Width") to if (vertex.extrusion) format("%.3f ", vertex.width) + mm else na,
        orcaString("Height") to if (vertex.extrusion) format("%.3f ", vertex.height) + mm else na,
        orcaString("Layer") to (vertex.layer + 1).toString(),
        orcaString("Speed") to format("%.1f ", vertex.feedrate) + mmPerSecond,
        orcaString("Acceleration") to format("%.0f ", vertex.acceleration) + orcaString("mm/s²"),
        orcaString("Jerk") to format("%.1f ", vertex.jerk) + mmPerSecond,
        orcaString("Flow rate") to if (vertex.extrusion) format("%.3f ", vertex.volumetricRate) + orcaString("mm³/s") else na,
        orcaString("Fan speed") to format("%.0f %%", vertex.fanSpeed),
        orcaString("Temperature") to format("%.0f ", vertex.temperature) + orcaString("°C"),
        orcaString("Pressure Advance") to String.format(Locale.ROOT, "%.4f", vertex.pressureAdvance),
        orcaString("Time") to "${LegendFormat.dhms(vertex.estimatedTime)} (${String.format(Locale.ROOT, "%.3f", vertex.time)}s)",
    )
}

/** to_string(EMoveType) of GCodeViewer.cpp. */
@Composable
private fun moveTypeName(type: ToolpathsMoveType): String = orcaString(
    when (type) {
        ToolpathsMoveType.Noop -> "Noop"
        ToolpathsMoveType.Retract -> "Retract"
        ToolpathsMoveType.Unretract -> "Unretract"
        ToolpathsMoveType.Seam -> "Seam"
        ToolpathsMoveType.ToolChange -> "Tool Change"
        ToolpathsMoveType.ColorChange -> "Color Change"
        ToolpathsMoveType.PausePrint -> "Pause Print"
        ToolpathsMoveType.CustomGCode -> "Custom G-code"
        ToolpathsMoveType.Travel -> "Travel"
        ToolpathsMoveType.Wipe -> "Wipe"
        ToolpathsMoveType.Extrude -> "Extrude"
    },
)

/** ColorRGB::X(), Y() and Z(). */
private val AxisX = Color(255 / 255f, 60 / 255f, 91 / 255f)
private val AxisY = Color(100 / 255f, 200 / 255f, 24 / 255f)
private val AxisZ = Color(47 / 255f, 136 / 255f, 233 / 255f)
