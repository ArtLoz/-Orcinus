package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.scene.BrimEarsTouch

/** What the brim ears tool's window and the 3D view do with it (GLGizmoBrimEars). */
internal class BrimEarsActions(
    /** The toolbar's "Brim Ears". */
    val toggle: () -> Unit,
    /** "Done". */
    val close: () -> Unit,
    /** A finger on the 3D view. */
    val touch: (BrimEarsTouch) -> Unit,
    val setDiameter: (Double) -> Unit,
    /** The head diameter typed in its box, which new ears take. */
    val typeDiameter: (Double) -> Unit,
    val setMaxAngle: (Double) -> Unit,
    val setDetectionRadius: (Double) -> Unit,
    val generate: () -> Unit,
    val removeSelected: () -> Unit,
    val removeAll: () -> Unit,
    /** The warning's "Set the brim type of this object to "painted"". */
    val setPainted: () -> Unit,
    /** "Section view". */
    val setSection: (Double) -> Unit,
) {
    companion object {
        val NONE = BrimEarsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * GLGizmoBrimEars::on_render_input_window(): "Head diameter", "Max angle"
 * and "Detection radius", "Create" with "Auto-generate", "Remove" with
 * "Selected" and "All", "Section view", "Done", and the warnings that the object's brim is
 * not painted (with the link that makes it so) and of the ears that touch
 * nothing. A finger places an ear where it lets go on the copy, selects an
 * ear with a tap, drags it, and removes it with a long press (the right
 * click); a slider applies once the finger lets it go, and the box beside
 * it (BBLDragFloat) takes a typed value.
 */
@Composable
internal fun BrimEarsPanel(mode: BrimEarsMode, ears: Int, actions: BrimEarsActions) {
    val colors = OrcaTheme.colors
    val setup = mode.setup
    PaintingPanelFrame(orcaString("Brim Ears"), orcaString("Done"), actions.close) {
        // The slider's change applies to the selected ears once let go (apply_radius_change()); the
        // box sets the diameter alone (BBLDragFloat("##head_diameter_input", ..., 0.0f, 0.0f)).
        SliderBox(
            label = orcaString("Head diameter"),
            value = mode.headDiameter?.toFloat(),
            range = HEAD_DIAMETER_MIN..HEAD_DIAMETER_MAX,
            enabled = setup != null,
            onSlide = { actions.setDiameter(it.toDouble()) },
            onType = { if (it > 0.0) actions.typeDiameter(it) },
        )
        // BBLDragFloat("##max_angle_input", ..., 0.0f, 180.0f)
        SliderBox(
            label = orcaString("Max angle"),
            value = mode.maxAngle.toFloat(),
            range = 0f..180f,
            enabled = setup != null,
            onSlide = { actions.setMaxAngle(it.toDouble()) },
            onType = { actions.setMaxAngle(it.coerceIn(0.0, 180.0)) },
        )
        // bbl_slider_float_style(..., 0, static_cast<int>(m_detection_radius_max)), and the box up to m_detection_radius_max.
        val detectionMax = setup?.detectionRadiusMax ?: 100.0
        SliderBox(
            label = orcaString("Detection radius"),
            value = mode.detectionRadius.toFloat(),
            range = 0f..detectionMax.toInt().toFloat().coerceAtLeast(1f),
            enabled = setup != null,
            onSlide = { actions.setDetectionRadius(it.toDouble()) },
            onType = { actions.setDetectionRadius(it.coerceIn(0.0, detectionMax)) },
        )
        TextRow(orcaString("Create")) {
            OrcaButton(orcaString("Auto-generate"), size = OrcaButtonSize.Compact, onClick = actions.generate, enabled = setup != null)
        }
        TextRow(orcaString("Remove")) {
            OrcaButton(
                text = orcaString("Selected"),
                size = OrcaButtonSize.Compact,
                style = OrcaButtonStyle.Regular,
                onClick = actions.removeSelected,
                enabled = mode.selected.any { it < ears },
            )
            OrcaButton(
                text = orcaString("All"),
                size = OrcaButtonSize.Compact,
                style = OrcaButtonStyle.Regular,
                onClick = actions.removeAll,
                enabled = ears > 0,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // set_position_by_ratio() as the slider moves, or of the value typed in the box.
        SliderBox(
            label = orcaString("Section view"),
            value = mode.sectionPosition.toFloat(),
            range = 0f..1f,
            format = "%.2f",
            live = true,
            onSlide = { actions.setSection(it.toDouble()) },
            onType = actions.setSection,
        )
        if (setup != null && !setup.painted) {
            Text(
                text = orcaString("Warning: The brim type is not set to \"painted\", the brim ears will not take effect!"),
                color = colors.warning,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = orcaString("Set the brim type of this object to \"painted\""),
                    color = COL_ORCA_LINK,
                    style = OrcaTheme.typography.body12.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                    modifier = Modifier
                        .padding(start = 16.dp, top = 4.dp)
                        .clickable(onClick = actions.setPainted),
                )
            }
        }
        val invalid = mode.invalid.count { it < ears }
        if (invalid > 0) {
            Text(
                text = orcaString("Warning") + ": " + invalid + " " + orcaString("invalid brim ears"),
                color = colors.warning,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * bbl_slider_float_style() with its label before it and the BBLDragFloat box
 * after it: the slider's value goes to [onSlide] as it moves ([live]) or once
 * let go, a value typed in the box to [onType].
 */
@Composable
private fun SliderBox(
    label: String,
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    onSlide: (Float) -> Unit,
    onType: (Double) -> Unit,
    enabled: Boolean = true,
    format: String = "%.1f",
    live: Boolean = false,
) {
    var held by remember { mutableStateOf<Float?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDimmed,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.width(SliderLabelWidth),
        )
        Slider(
            value = (held ?: value ?: range.start).coerceIn(range),
            onValueChange = { if (live) onSlide(it) else held = it },
            onValueChangeFinished = {
                held?.let(onSlide)
                held = null
            },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        PositionField(
            value = (held ?: value ?: range.start).toDouble(),
            onValue = { if (enabled) onType(it) },
            format = format,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(PositionFieldWidth),
        )
    }
}

private val SliderLabelWidth = 96.dp

/** bbl_slider_float_style("##head_diameter", ..., 5, 20): the head diameter's range. */
private const val HEAD_DIAMETER_MIN = 5f
private const val HEAD_DIAMETER_MAX = 20f

/** ImGuiWrapper::COL_ORCA, the link's colour. */
private val COL_ORCA_LINK = androidx.compose.ui.graphics.Color(0f, 150f / 255f, 136f / 255f)
