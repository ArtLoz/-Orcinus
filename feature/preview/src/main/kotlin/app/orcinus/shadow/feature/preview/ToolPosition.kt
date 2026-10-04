package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString
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

/** Marker::render_position_window() unfolded: the move's properties, labels in Orca's colour. */
@Composable
internal fun ToolPropertiesWindow(vertex: ToolpathsVertex, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = colors.window.copy(alpha = 0.8f)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((label, value) in positionProperties(vertex)) {
                Row {
                    Text(label, color = colors.accent, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
                    Text(value, color = colors.text, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

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
