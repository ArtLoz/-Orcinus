package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.LayerHeightEdit
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.scene.LayerEditingView

/** What the variable layer height's window and bar do. */
internal class LayerEditingActions(
    /** The toolbar's "Variable layer height": the bar opens on the selected object, or closes. */
    val toggle: () -> Unit,
    /** Done */
    val close: () -> Unit,
    /** A finger on the bar at a height, sliding along it, and letting go. */
    val press: (Double) -> Unit,
    val move: (Double) -> Unit,
    val release: () -> Unit,
    val setAction: (LayerHeightEdit) -> Unit,
    val setBandWidth: (Double) -> Unit,
    val setAdaptiveQuality: (Double) -> Unit,
    val adaptive: () -> Unit,
    val setSmoothRadius: (Int) -> Unit,
    val setKeepMin: (Boolean) -> Unit,
    val smooth: () -> Unit,
    val reset: () -> Unit,
) {
    companion object {
        val NONE = LayerEditingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** What the 3D view and the bar show of [LayerEditingMode]: nothing until the engine described the object. */
internal fun LayerEditingMode.view(): LayerEditingView? = editing?.let {
    LayerEditingView(
        mesh = mesh,
        layers = it.layers,
        profile = it.profile,
        objectMaxZ = it.objectMaxZ,
        layerHeight = it.layerHeight,
        minLayerHeight = it.minLayerHeight,
        maxLayerHeight = it.maxLayerHeight,
        objectPrintZHeight = it.objectPrintZHeight,
        cursorZ = cursorZ,
        bandWidth = tools.bandWidth,
    )
}

/**
 * LayersEditing::render_variable_layer_height_dialog(): Adaptive with its
 * quality, Smooth with its radius and "Keep min", Reset while the profile is
 * not the plain one, and Done. The desktop window lists what the mouse does
 * on the bar — the left button adds detail, the right one removes it, and
 * with Shift they reset to the base height and smooth, while the wheel sizes
 * the band they edit; a finger has one press, so the window chooses what it
 * does and sizes the band with a slider.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayerEditingPanel(mode: LayerEditingMode, actions: LayerEditingActions) {
    val colors = OrcaTheme.colors
    val tools = mode.tools
    val ready = mode.editing != null
    PaintingPanelFrame(orcaString("Variable layer height"), orcaString("Done"), actions.close) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            listOf(
                LayerHeightEdit.DECREASE to "Add detail",
                LayerHeightEdit.INCREASE to "Remove detail",
                LayerHeightEdit.REDUCE to "Reset to base",
                LayerHeightEdit.SMOOTH to "Smoothing",
            ).forEach { (action, label) ->
                OrcaButton(
                    text = orcaString(label),
                    style = if (action == tools.action) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
                    size = OrcaButtonSize.Compact,
                    onClick = { actions.setAction(action) },
                )
            }
        }
        PaintingSlider(
            label = orcaString("Increase/decrease edit area"),
            value = tools.bandWidth.toFloat(),
            range = LayerEditingTools.MIN_BAND_WIDTH.toFloat()..LayerEditingTools.MAX_BAND_WIDTH.toFloat(),
            text = String.format(textLocale(), "%.1f", tools.bandWidth) + " " + orcaString("mm"),
            onChange = { actions.setBandWidth(it.toDouble()) },
        )
        LayerToolRow(
            button = orcaString("Adaptive"),
            onButton = actions.adaptive,
            enabled = ready,
            label = orcaString("Quality / Speed"),
            value = tools.adaptiveQuality.toFloat(),
            range = 0f..1f,
            steps = 0,
            text = String.format(textLocale(), "%.2f", tools.adaptiveQuality),
            onChange = { actions.setAdaptiveQuality(it.toDouble()) },
        )
        LayerToolRow(
            button = orcaString("Smooth"),
            onButton = actions.smooth,
            enabled = ready,
            label = orcaString("Radius"),
            value = tools.smoothRadius.toFloat(),
            range = LayerEditingTools.MIN_SMOOTH_RADIUS.toFloat()..LayerEditingTools.MAX_SMOOTH_RADIUS.toFloat(),
            steps = LayerEditingTools.MAX_SMOOTH_RADIUS - LayerEditingTools.MIN_SMOOTH_RADIUS - 1,
            text = tools.smoothRadius.toString(),
            onChange = { actions.setSmoothRadius(Math.round(it)) },
        )
        PaintingCheck(orcaString("Keep min"), tools.keepMin, actions.setKeepMin)
        OrcaButton(
            text = orcaString("Reset"),
            size = OrcaButtonSize.Compact,
            style = OrcaButtonStyle.Regular,
            // imgui.disabled_begin(check_object_layers_fixed())
            enabled = mode.editing?.fixed == false,
            onClick = actions.reset,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!ready) {
            Text(orcaString("Loading..."), color = colors.textSide, style = OrcaTheme.typography.body12)
        }
    }
}

/** A row of the window: its button, then the slider it works with, as the desktop window lines them up. */
@Composable
private fun LayerToolRow(
    button: String,
    onButton: () -> Unit,
    enabled: Boolean,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    text: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        OrcaButton(
            text = button,
            size = OrcaButtonSize.Compact,
            enabled = enabled,
            onClick = onButton,
            modifier = Modifier.width(ButtonWidth),
        )
        Text(
            text = label,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The buttons of the rows share a width, so their sliders line up, as the window's alignment does. */
private val ButtonWidth = 104.dp
