package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.LayerMark
import app.orcinus.shadow.core.designsystem.component.OrcaLayerRangeSlider
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeRules
import app.orcinus.shadow.core.model.LayerGcodeType
import androidx.compose.ui.graphics.Color
import app.orcinus.shadow.core.designsystem.component.OrcaMovePlayer
import app.orcinus.shadow.render.gcode.ToolpathsLayer
import app.orcinus.shadow.render.gcode.ToolpathsView
import java.util.Locale

private val MovePlayerHeight = 52.dp
private val ControlsMargin = 12.dp

/** The room the page's sidebar button and the 3D navigator's top keep above the position's properties. */
private val DetailsTop = 64.dp

/** The desktop slider's colour of a code that is no filament change (IMSlider::draw_ticks). */
private val CodeMarkColor = Color(255, 111, 0)

/** The codes on the layers the slider shows and edits, with what the menu offers. */
internal class LayerGcodeUi(
    val codes: List<LayerGcode>,
    val rules: LayerGcodeRules,
    val filamentColors: List<Color>,
    val actions: LayerGcodeActions,
    /** IMSlider::set_menu_enable(): the slider's menu opens. */
    val menuEnabled: Boolean = true,
)

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
    layerGcodes: LayerGcodeUi? = null,
    /** The G-code window over the move slider; null while it is hidden. */
    gcodeWindow: (@Composable (Modifier) -> Unit)? = null,
    /** The tool's position window right over the move slider, and its properties above the windows; null while hidden. */
    positionWindow: (@Composable (Modifier) -> Unit)? = null,
    positionDetails: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // The layer slider stands above the windows, which stand above the move slider;
    // the position's properties stand over the canvas above them.
    var windowsHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val windowed = gcodeWindow != null || positionWindow != null
    val windowSpace = if (windowed) windowsHeight + ControlsMargin else 0.dp
    val windowsBottom = bottomInset + MovePlayerHeight + ControlsMargin * 2
    var oneLayer by rememberSaveable { mutableStateOf(false) }
    // The layer whose menu is open (and whether by the lower handle), the layer whose G-code is being edited, and the jump dialog.
    var menuLayer by rememberSaveable { mutableStateOf<Int?>(null) }
    var menuLower by rememberSaveable { mutableStateOf(false) }
    var customLayer by rememberSaveable { mutableStateOf<Int?>(null) }
    var jumping by rememberSaveable { mutableStateOf(false) }
    // IMSlider::switch_one_layer_mode() remembers the layer it showed.
    var oneLayerValue by rememberSaveable { mutableIntStateOf(-1) }
    val last = (view.layerZs.size - 1).coerceAtLeast(0)
    val resources = LocalContext.current.resources
    // IMSlider::get_tick_from_value(): a code stands on the first layer at its height or above.
    fun layerOf(code: LayerGcode) = view.layerZs.indexOfFirst { it >= code.printZ - HEIGHT_EPSILON }.takeIf { it >= 0 }
    fun zOf(layer: Int) = view.layerZs.getOrElse(layer) { 0f }.toDouble()
    val codes = layerGcodes?.codes.orEmpty()
    val marks = codes.mapNotNull { code ->
        val at = layerOf(code) ?: return@mapNotNull null
        val color = if (code.type == LayerGcodeType.TOOL_CHANGE) layerGcodes?.filamentColors?.getOrNull(code.extruder - 1) ?: CodeMarkColor else CodeMarkColor
        LayerMark(at, color)
    }
    val codeLabels = codes.mapNotNull { code -> layerOf(code)?.let { it to layerGcodeLabel(code.type) } }.toMap()
    // IMSlider::do_go_to_layer(): the handle the menu was opened by goes to the layer.
    fun jumpTo(target: Int) {
        val value = target.coerceIn(0, last)
        when {
            oneLayer -> layer.setLayerRange(value, value)
            menuLower -> layer.setLayerRange(value, maxOf(view.upperLayer, value))
            else -> layer.setLayerRange(minOf(view.lowerLayer, value), value)
        }
    }
    // IMSlider::SetSelectionSpan(): a span of several layers leaves the one-layer mode.
    LaunchedEffect(layer, view.lowerLayer, view.upperLayer) {
        if (oneLayer && view.lowerLayer < view.upperLayer) oneLayer = false
    }
    // IMSlider::draw_colored_band(): with a filament change among the codes, the
    // first filament's colour from the bottom, and each change's from its layer up.
    val bands = if (codes.any { it.type == LayerGcodeType.TOOL_CHANGE }) {
        val colors = layerGcodes?.filamentColors.orEmpty()
        listOfNotNull(colors.firstOrNull()?.let { LayerMark(0, it) }) + codes.filter { it.type == LayerGcodeType.TOOL_CHANGE }.mapNotNull { code ->
            val at = layerOf(code) ?: return@mapNotNull null
            colors.getOrNull(code.extruder - 1)?.let { LayerMark(at, it) }
        }
    } else {
        emptyList()
    }
    // IMSlider::draw_tick_on_mouse_position(): the time the print takes up to a layer.
    val elapsed = view.layerTimes.runningFold(0f, Float::plus).drop(1)

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
                    val height = resources.getString(R.string.layer_label, index + 1, String.format(Locale.ROOT, "%.2f", view.layerZs.getOrElse(index) { 0f }))
                    // The hover tooltip's time, which a phone writes beside the height.
                    val text = elapsed.getOrNull(index)?.let { "$height · ${LegendFormat.shortTime(it)}" } ?: height
                    codeLabels[index]?.let { "$text · $it" } ?: text
                },
                marks = marks,
                bands = bands,
                onLayerMenu = layerGcodes?.takeIf { it.menuEnabled }?.let { { layer, lower -> menuLayer = layer; menuLower = lower } },
                layerMenuDescription = stringResource(R.string.layer_codes),
                contentDescription = stringResource(R.string.layer_slider),
                stepUpDescription = stringResource(R.string.layer_up),
                stepDownDescription = stringResource(R.string.layer_down),
                oneLayerDescription = stringResource(R.string.one_layer_mode),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ControlsMargin, end = ControlsMargin - 4.dp, bottom = bottomInset + MovePlayerHeight + ControlsMargin * 2 + windowSpace)
                    .fillMaxHeight(),
            )
        }
        if (windowed) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = ControlsMargin, end = ControlsMargin, bottom = windowsBottom)
                    .fillMaxWidth()
                    .onSizeChanged { windowsHeight = with(density) { it.height.toDp() } },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                gcodeWindow?.invoke(Modifier.fillMaxWidth())
                // render_position_window(): at the bottom, in the middle.
                positionWindow?.invoke(Modifier)
            }
        }
        if (positionDetails != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Below the sidebar's button, scrolling when it is taller than the room left.
                    .padding(start = ControlsMargin, end = ControlsMargin, top = DetailsTop, bottom = windowsBottom + windowSpace)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                content = positionDetails,
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

    val gcodes = layerGcodes ?: return
    menuLayer?.takeIf { it in 0..last }?.let { layer ->
        val z = zOf(layer)
        LayerGcodeSheet(
            layerNumber = layer + 1,
            printZ = z,
            code = gcodes.codes.firstOrNull { layerOf(it) == layer },
            rules = gcodes.rules,
            filamentColors = gcodes.filamentColors,
            actions = gcodes.actions,
            onEditCustom = { customLayer = layer },
            onJumpToLayer = { jumping = true },
            onDismiss = { menuLayer = null },
        )
    }
    customLayer?.takeIf { it in 0..last }?.let { layer ->
        CustomGcodeDialog(
            initial = gcodes.codes.firstOrNull { layerOf(it) == layer && it.type == LayerGcodeType.CUSTOM }?.extra.orEmpty(),
            onDismiss = { customLayer = null },
            onConfirm = { text ->
                customLayer = null
                gcodes.actions.setCustom(zOf(layer), text)
            },
        )
    }
    if (jumping) {
        JumpToLayerDialog(
            layerCount = view.layerZs.size,
            onDismiss = { jumping = false },
            onJump = { layer ->
                jumping = false
                jumpTo(layer)
            },
        )
    }
}

/** How far a code's height may be from its layer's, which the G-code rounds. */
private const val HEIGHT_EPSILON = 1e-4f
