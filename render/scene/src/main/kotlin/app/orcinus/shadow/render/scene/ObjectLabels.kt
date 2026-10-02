package app.orcinus.shadow.render.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.roundToInt

/**
 * What the label of a copy reads (GLCanvas3D::Labels): the object's name, with
 * the copy's number when the object has several, and while the plate prints
 * by object, the copy's place in the print order ("Sequence#: 2").
 */
data class PlateLabel(val name: String, val printOrder: String? = null)

/** Where a copy's label stands: the projection of the centre of the copy's box, in pixels from the view's top left. */
internal class LabelPlacement(val index: Int, val x: Float, val y: Float, val selected: Boolean)

/**
 * GLCanvas3D::Labels::render(): a small window centred on every labelled copy,
 * the selected ones on top and the others from the farthest to the nearest,
 * which lets the fingers through to the view.
 */
@Composable
internal fun ObjectLabels(placements: List<LabelPlacement>, labels: Map<Int, PlateLabel>, modifier: Modifier = Modifier) {
    val shown = placements.filter { it.index in labels }
    Layout(
        modifier = modifier,
        content = {
            shown.forEach { placement -> ObjectLabel(labels.getValue(placement.index), placement.selected) }
        },
    ) { measurables, constraints ->
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(free) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                // set_next_window_pos(x, y, ImGuiCond_Always, 0.5f, 0.5f): centred on the point.
                val placement = shown[index]
                placeable.place((placement.x - placeable.width / 2f).roundToInt(), (placement.y - placeable.height / 2f).roundToInt())
            }
        }
    }
}

@Composable
private fun ObjectLabel(label: PlateLabel, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .background(WINDOW_BACKGROUND)
            .border(if (selected) 3.dp else 1.5.dp, if (selected) SELECTED_BORDER else BORDER)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val style = OrcaTheme.typography.body13.copy(color = TEXT, textAlign = TextAlign.Center)
        BasicText(label.name, style = style)
        label.printOrder?.let { order ->
            // ImGui::Separator()
            Box(
                Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SEPARATOR),
            )
            BasicText(order, style = style)
        }
    }
}

/** ImGuiWrapper::COL_WINDOW_BACKGROUND, the windows' background. */
private val WINDOW_BACKGROUND = Color(0.1f, 0.1f, 0.1f, 0.8f)

/** ImGui's text colour. */
private val TEXT = Color.White

/** The border of a label: COL_ORANGE_DARK for a selected copy, light grey otherwise. */
private val SELECTED_BORDER = Color(0.757f, 0.404f, 0.216f)
private val BORDER = Color(0.75f, 0.75f, 0.75f)

/** ImGuiCol_Separator: COL_BLUE_LIGHT. */
private val SEPARATOR = Color(0.122f, 0.557f, 0.918f)
