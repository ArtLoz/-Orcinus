package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaLayerRangeSlider
import app.orcinus.shadow.core.designsystem.component.OrcaMovePlayer
import app.orcinus.shadow.render.gcode.ToolpathsLayer
import app.orcinus.shadow.render.gcode.ToolpathsView
import java.util.Locale

private val MovePlayerHeight = 52.dp
private val ControlsMargin = 12.dp

/**
 * The preview's floating controls over the toolpaths: the layer range on a
 * rail at the right edge and the moves of the top layer in a player above the
 * legend's sheet, which takes [bottomInset] at the bottom. They show what
 * OrcaSlicer's layer and moves sliders show.
 */
@Composable
internal fun ToolpathsControls(
    layer: ToolpathsLayer,
    view: ToolpathsView,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    var oneLayer by rememberSaveable { mutableStateOf(false) }
    // IMSlider::switch_one_layer_mode() remembers the layer it showed.
    var oneLayerValue by rememberSaveable { mutableIntStateOf(-1) }
    val last = (view.layerZs.size - 1).coerceAtLeast(0)
    val resources = LocalContext.current.resources

    Box(modifier) {
        if (view.layerZs.isNotEmpty()) {
            OrcaLayerRangeSlider(
                layerCount = view.layerZs.size,
                lower = view.lowerLayer,
                higher = view.upperLayer,
                oneLayer = oneLayer,
                onRangeChange = layer::setLayerRange,
                onOneLayerChange = { enabled ->
                    if (enabled) {
                        // The remembered layer when the whole model shows, the upper layer otherwise.
                        val value = when {
                            oneLayerValue !in 0..last -> last / 2
                            view.upperLayer == last -> oneLayerValue
                            else -> view.upperLayer
                        }
                        oneLayerValue = value
                        layer.setLayerRange(value, value)
                    } else {
                        oneLayerValue = view.upperLayer
                        layer.setLayerRange(0, last)
                    }
                    oneLayer = enabled
                },
                label = { index ->
                    resources.getString(R.string.layer_label, index + 1, String.format(Locale.ROOT, "%.2f", view.layerZs.getOrElse(index) { 0f }))
                },
                contentDescription = stringResource(R.string.layer_slider),
                stepUpDescription = stringResource(R.string.layer_up),
                stepDownDescription = stringResource(R.string.layer_down),
                oneLayerDescription = stringResource(R.string.one_layer_mode),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ControlsMargin, end = ControlsMargin - 4.dp, bottom = bottomInset + MovePlayerHeight + ControlsMargin * 2)
                    .fillMaxHeight(),
            )
        }
        if (view.moves.size > 1) {
            OrcaMovePlayer(
                moveCount = view.moves.size,
                position = view.lastVisibleMove,
                onPositionChange = layer::setLastVisibleMove,
                label = stringResource(R.string.move_label, view.lastVisibleMove + 1, view.moves.size),
                contentDescription = stringResource(R.string.move_slider),
                playDescription = stringResource(R.string.move_play),
                previousDescription = stringResource(R.string.move_previous),
                nextDescription = stringResource(R.string.move_next),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = ControlsMargin, end = ControlsMargin, bottom = bottomInset + ControlsMargin)
                    .fillMaxWidth(),
            )
        }
    }
}
