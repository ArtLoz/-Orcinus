package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaGizmoPanel
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.orcaString
import java.util.Locale

/** What the Simplify gizmo's window does. */
internal class SimplifyActions(
    val setUseCount: (Boolean) -> Unit,
    val setReduction: (Int) -> Unit,
    val setDecimateRatio: (Float) -> Unit,
    val setWireframe: (Boolean) -> Unit,
    val apply: () -> Unit,
    val cancel: () -> Unit,
) {
    companion object {
        val NONE = SimplifyActions({}, {}, {}, {}, {}, {})
    }
}

/** The captions of the detail levels, from the finest. */
private val REDUCE_CAPTIONS = listOf("Extra high", "High", "Medium", "Low", "Extra low")

/**
 * GLGizmoSimplify::on_render_input_window(): the volume's name and triangles,
 * the detail level or the decimate ratio to simplify by, each with its radio
 * button, "Show wireframe", the progress while the engine works the mesh out,
 * and Apply and Cancel. The desktop window's progress bar counts percent; the
 * engine reports none here, so the bar only runs.
 */
@Composable
internal fun SimplifyPanel(plateObject: PlateObject?, mode: SimplifyMode, actions: SimplifyActions) {
    val colors = OrcaTheme.colors
    val config = mode.config
    OrcaGizmoPanel(Modifier.width(PanelWidth)) {
        Text(orcaString("Simplify"), color = colors.onCanvasPanel, style = OrcaTheme.typography.head14)
        SimplifyInfoRow(orcaString("Mesh name") + ":", plateObject?.let { volumeName(it, mode.volume.index) }.orEmpty())
        SimplifyInfoRow(orcaString("Triangles") + ":", mode.preview?.original?.toString() ?: "…")
        Separator()
        // The detail level: the largest error a collapsed edge may have. The
        // whole row picks it, and the slider has the window's width to itself.
        ChoiceRow(selected = !config.useCount, onSelect = { actions.setUseCount(false) }) {
            Text(
                orcaString("Detail level"),
                color = if (config.useCount) colors.textSide else colors.onCanvasPanel,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.weight(1f),
            )
            Text(
                orcaString(REDUCE_CAPTIONS[mode.reduction]),
                color = if (config.useCount) colors.textSide else colors.accent,
                style = OrcaTheme.typography.body13,
            )
        }
        Slider(
            value = mode.reduction.toFloat(),
            onValueChange = { actions.setReduction(Math.round(it)) },
            valueRange = 0f..(REDUCE_CAPTIONS.size - 1).toFloat(),
            steps = REDUCE_CAPTIONS.size - 2,
            enabled = !config.useCount,
            colors = sliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        // The decimate ratio: the share of the triangles taken away.
        ChoiceRow(selected = config.useCount, onSelect = { actions.setUseCount(true) }) {
            Text(
                orcaString("Decimate ratio"),
                color = if (config.useCount) colors.onCanvasPanel else colors.textSide,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.weight(1f),
            )
            PositionField(
                value = config.decimateRatio.toDouble(),
                onValue = { actions.setDecimateRatio(it.toFloat()) },
                modifier = Modifier.width(RatioFieldWidth),
            )
            Text("%", color = colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(start = 4.dp))
        }
        Slider(
            value = config.decimateRatio.coerceIn(0f, 100f),
            onValueChange = actions.setDecimateRatio,
            valueRange = 0f..100f,
            enabled = config.useCount,
            colors = sliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            String.format(Locale.ROOT, orcaString("%d triangles"), config.wantedCount.coerceAtLeast(0)),
            color = if (config.useCount) colors.onCanvasPanel else colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.align(Alignment.End),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = mode.wireframe, role = Role.Checkbox, onValueChange = actions.setWireframe),
        ) {
            OrcaCheckBox(checked = mode.wireframe, onCheckedChange = null)
            Text(orcaString("Show wireframe"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13)
        }
        Separator()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                if (mode.running) {
                    LinearProgressIndicator(color = colors.accent, trackColor = colors.border, modifier = Modifier.fillMaxWidth())
                }
            }
            OrcaButton(text = orcaString("Apply"), onClick = actions.apply, enabled = mode.canApply, size = OrcaButtonSize.Compact)
            OrcaButton(text = orcaString("Cancel"), onClick = actions.cancel, style = OrcaButtonStyle.Regular, size = OrcaButtonSize.Compact)
        }
    }
}

/** ModelVolume::name as the window shows it: the object's name for its own mesh when the file named none. */
@Composable
private fun volumeName(plateObject: PlateObject, index: Int): String {
    val part = plateObject.volumeAt(index)
    return when {
        part == null -> plateObject.volume.name.ifEmpty { plateObject.displayName() }
        index == 0 -> part.name.ifEmpty { plateObject.displayName() }
        else -> part.name.ifEmpty { part.shape }
    }
}

/** A radio button with what it picks: the whole row is the touch target. */
@Composable
private fun ChoiceRow(selected: Boolean, onSelect: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        OrcaRadioButton(selected = selected, onClick = null)
        content()
    }
}

@Composable
private fun SimplifyInfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(label, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body13, modifier = Modifier.width(LabelWidth))
        Text(
            value,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body13,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ValueWidth),
        )
    }
}

@Composable
private fun Separator() {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(OrcaTheme.colors.canvasPanelSeparator),
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = OrcaTheme.colors.accent,
    activeTrackColor = OrcaTheme.colors.accent,
    inactiveTrackColor = OrcaTheme.colors.border,
)

private val LabelWidth = 104.dp
private val ValueWidth = 190.dp
private val RatioFieldWidth = 72.dp

/** Wide enough for the sliders to be dragged with a thumb. */
private val PanelWidth = 320.dp
