package app.orcinus.shadow.render.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ColorRgba
import java.util.Locale
import kotlin.math.roundToInt

/**
 * GLGizmoMeasure::render_dimensioning() over the view: its lines, arcs and
 * arrowheads, and its labels, a small window each whose bottom left corner
 * stands at the label's point. A distance reads in [units], in inches when
 * [imperial], and offers "Edit to scale" ([editDescription]) where the
 * dimensioning allows it. The fingers pass through everything but that
 * button to the view.
 */
@Composable
internal fun MeasureDimensionsOverlay(
    dimensions: MeasureDimensions,
    imperial: Boolean,
    units: String,
    editDescription: String,
    dark: Boolean,
    onEditDistance: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            for (segment in dimensions.segments) {
                drawLine(segment.color.toColor(), Offset(segment.x1, segment.y1), Offset(segment.x2, segment.y2), strokeWidth = segment.width)
            }
            for (arrow in dimensions.arrows) {
                val points = arrow.points
                val path = Path().apply {
                    moveTo(points[0], points[1])
                    lineTo(points[2], points[3])
                    lineTo(points[4], points[5])
                    close()
                }
                drawPath(path, Color.White)
            }
        }
        val labels = dimensions.labels
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                labels.forEach { label ->
                    DimensionLabelWindow(label, imperial, units, editDescription, dark, onEditDistance)
                }
            },
        ) { measurables, constraints ->
            val free = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(free) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    // set_next_window_pos(x, y, ImGuiCond_Always, 0.0f, 1.0f)
                    placeable.place(labels[index].x.roundToInt(), (labels[index].y - placeable.height).roundToInt())
                }
            }
        }
    }
}

@Composable
private fun DimensionLabelWindow(
    label: DimensionLabel,
    imperial: Boolean,
    units: String,
    editDescription: String,
    dark: Boolean,
    onEditDistance: (Double) -> Unit,
) {
    val text = if (label.angle) {
        // format_double(Geometry::rad2deg(angle)) + "°"
        String.format(Locale.ROOT, "%.3f", label.value) + "°"
    } else {
        // curr_value_str + " " + units
        String.format(Locale.ROOT, "%.3f", if (imperial) label.value * MM_TO_IN else label.value) + " " + units
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = text,
            style = OrcaTheme.typography.body13.copy(color = if (dark) DARK_TEXT else LIGHT_TEXT),
            modifier = Modifier
                .background(LABEL_BACKGROUND)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (label.editable) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(EDIT_TOUCH_SIZE.dp)
                    .clickable(role = Role.Button, onClickLabel = editDescription) { onEditDistance(label.value) },
            ) {
                Image(painterResource(R.drawable.orca_edit_button), contentDescription = editDescription, modifier = Modifier.size(EDIT_ICON_SIZE.dp))
            }
        }
    }
}

private fun ColorRgba.toColor() = Color(red, green, blue, alpha)

/** The label's backdrop: ImGuiWrapper::to_ImU32({1.0f, 1.0f, 1.0f, 0.5f}). */
private val LABEL_BACKGROUND = Color(1f, 1f, 1f, 0.5f)

/** push_common_window_style()'s ImGuiCol_Text in the dark and the light mode. */
private val DARK_TEXT = Color(1f, 1f, 1f, 0.88f)
private val LIGHT_TEXT = Color(38 / 255f, 46 / 255f, 48 / 255f)

/** GizmoObjectManipulation::mm_to_in */
private const val MM_TO_IN = 0.0393700787

/** The edit button: the icon OrcaSlicer's image button draws, in a touch target a finger hits. */
private const val EDIT_ICON_SIZE = 18
private const val EDIT_TOUCH_SIZE = 36
