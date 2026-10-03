package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.util.Locale

/** What the brim ears tool's window and the 3D view do with it (GLGizmoBrimEars). */
internal class BrimEarsActions(
    /** The toolbar's "Brim Ears". */
    val toggle: () -> Unit,
    /** "Done". */
    val close: () -> Unit,
    /** A finger on the 3D view. */
    val touch: (BrimEarsTouch) -> Unit,
    val setDiameter: (Double) -> Unit,
    val setMaxAngle: (Double) -> Unit,
    val setDetectionRadius: (Double) -> Unit,
    val generate: () -> Unit,
    val removeSelected: () -> Unit,
    val removeAll: () -> Unit,
    /** The warning's "Set the brim type of this object to "painted"". */
    val setPainted: () -> Unit,
) {
    companion object {
        val NONE = BrimEarsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * GLGizmoBrimEars::on_render_input_window(): "Head diameter", "Max angle"
 * and "Detection radius", "Create" with "Auto-generate", "Remove" with
 * "Selected" and "All", "Done", and the warnings that the object's brim is
 * not painted (with the link that makes it so) and of the ears that touch
 * nothing. A finger places an ear where it lets go on the copy, selects an
 * ear with a tap, drags it, and removes it with a long press (the right
 * click); a slider applies once the finger lets it go.
 */
@Composable
internal fun BrimEarsPanel(mode: BrimEarsMode, ears: Int, actions: BrimEarsActions) {
    val colors = OrcaTheme.colors
    val setup = mode.setup
    PaintingPanelFrame(orcaString("Brim Ears"), orcaString("Done"), actions.close) {
        CommittedSlider(
            label = orcaString("Head diameter"),
            value = mode.headDiameter?.toFloat(),
            range = HEAD_DIAMETER_MIN..HEAD_DIAMETER_MAX,
            text = { String.format(Locale.ROOT, "%.1f", it) },
            revert = null,
            hasRevert = false,
            enabled = setup != null,
            onCommit = { value -> value?.let { actions.setDiameter(it.toDouble()) } },
        )
        CommittedSlider(
            label = orcaString("Max angle"),
            value = mode.maxAngle.toFloat(),
            range = 0f..180f,
            text = { String.format(Locale.ROOT, "%.1f", it) },
            revert = null,
            hasRevert = false,
            enabled = setup != null,
            onCommit = { value -> value?.let { actions.setMaxAngle(it.toDouble()) } },
        )
        CommittedSlider(
            label = orcaString("Detection radius"),
            value = mode.detectionRadius.toFloat(),
            // bbl_slider_float_style(..., 0, static_cast<int>(m_detection_radius_max))
            range = 0f..(setup?.detectionRadiusMax?.toInt()?.toFloat() ?: 100f).coerceAtLeast(1f),
            text = { String.format(Locale.ROOT, "%.1f", it) },
            revert = null,
            hasRevert = false,
            enabled = setup != null,
            onCommit = { value -> value?.let { actions.setDetectionRadius(it.toDouble()) } },
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

/** bbl_slider_float_style("##head_diameter", ..., 5, 20): the head diameter's range. */
private const val HEAD_DIAMETER_MIN = 5f
private const val HEAD_DIAMETER_MAX = 20f

/** ImGuiWrapper::COL_ORCA, the link's colour. */
private val COL_ORCA_LINK = androidx.compose.ui.graphics.Color(0f, 150f / 255f, 136f / 255f)
