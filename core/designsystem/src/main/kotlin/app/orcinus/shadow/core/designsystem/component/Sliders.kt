package app.orcinus.shadow.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.icon.OrcaGlyphs
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val TrackThickness = 16.dp
private val TrackTouchWidth = 48.dp
private val TrackInset = 10.dp
private val HandleLength = 34.dp
private val HandleDraggedLength = 42.dp
private val HandleThickness = 4.dp
private val HandleGap = 6.dp
private val InnerCorner = 2.dp
private val ControlButtonSize = 32.dp
private val ControlGap = 8.dp
private val PillHeight = 26.dp
private val PanelShape = RoundedCornerShape(22.dp)

/** Time between moves while the move player plays. */
private const val PLAYBACK_STEP_MILLIS = 40L

/** A translucent floating surface over the 3D canvas. */
@Composable
private fun canvasPanelColor() = OrcaTheme.colors.window.copy(alpha = 0.92f)

private enum class RangeThumb { Lower, Higher }

/** A code on a layer (IMSlider's tick), drawn across the track in its colour. */
data class LayerMark(val layer: Int, val color: Color)

/**
 * The layer range of the G-code preview as a vertical slider drawn straight
 * over the canvas, in the style of Material 3 sliders: a thick rounded track
 * whose selected part is filled with the accent and cut by the bar handles of
 * the lower and higher visible layer, each labelled in a pill beside it. Small
 * buttons above and below step the last moved handle by one layer, and the one
 * below them switches to a single layer. A finger anywhere on the track takes
 * the nearer handle; a finger on a pill moves its handle by the distance it
 * travels, so the label stays readable. Layers run from 0 to [layerCount] - 1.
 */
@Composable
fun OrcaLayerRangeSlider(
    layerCount: Int,
    lower: Int,
    higher: Int,
    oneLayer: Boolean,
    onRangeChange: (lower: Int, higher: Int) -> Unit,
    onOneLayerChange: (Boolean) -> Unit,
    label: (layer: Int) -> String,
    contentDescription: String,
    stepUpDescription: String,
    stepDownDescription: String,
    oneLayerDescription: String,
    modifier: Modifier = Modifier,
    /** The codes on the layers, marked on the track. */
    marks: List<LayerMark> = emptyList(),
    /**
     * Opens the menu of the layer the last moved handle is on, which the
     * desktop slider opens with a right click on the handle; null shows no button.
     */
    onLayerMenu: ((layer: Int) -> Unit)? = null,
    layerMenuDescription: String = "",
) {
    val colors = OrcaTheme.colors
    val last = (layerCount - 1).coerceAtLeast(0)
    val menuSpace = if (onLayerMenu != null) ControlGap + ControlButtonSize else 0.dp
    val currentLower by rememberUpdatedState(lower)
    val currentHigher by rememberUpdatedState(higher)
    val currentOneLayer by rememberUpdatedState(oneLayer)
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    var active by remember { mutableStateOf(RangeThumb.Higher) }
    var dragging by remember { mutableStateOf<RangeThumb?>(null) }

    fun change(thumb: RangeThumb, value: Int) {
        val layer = value.coerceIn(0, last)
        when {
            currentOneLayer -> currentOnRangeChange(layer, layer)
            // A handle pushed past the other takes it along.
            thumb == RangeThumb.Higher -> currentOnRangeChange(minOf(currentLower, layer), layer)
            else -> currentOnRangeChange(layer, maxOf(currentHigher, layer))
        }
    }

    val stepped = if (oneLayer) RangeThumb.Higher else active
    val steppedValue = if (stepped == RangeThumb.Higher) higher else lower
    val higherLength by animateDpAsState(if (dragging == RangeThumb.Higher) HandleDraggedLength else HandleLength, label = "higher handle")
    val lowerLength by animateDpAsState(if (dragging == RangeThumb.Lower) HandleDraggedLength else HandleLength, label = "lower handle")

    Row(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            stateDescription = if (oneLayer) "${higher + 1}" else "${lower + 1}–${higher + 1}"
        },
    ) {
        // The pills float beside the track at the heights of their handles.
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            val density = LocalDensity.current
            val trackTop = with(density) { (ControlButtonSize + ControlGap + TrackInset).toPx() }
            val trackBottom = with(density) { (maxHeight - ControlGap * 3 - ControlButtonSize * 2 - menuSpace - TrackInset).toPx() }
            val travel = (trackBottom - trackTop).coerceAtLeast(1f)
            fun yOf(value: Int) = trackTop + travel * (1f - if (last > 0) value.toFloat() / last else 0f)
            val pillHeight = with(density) { PillHeight.toPx() }
            val gap = with(density) { 2.dp.toPx() }
            val thumbs = if (oneLayer) listOf(RangeThumb.Higher) else listOf(RangeThumb.Higher, RangeThumb.Lower)
            for (thumb in thumbs) {
                val value = if (thumb == RangeThumb.Higher) higher else lower
                val center = when {
                    oneLayer -> yOf(value)
                    // Close handles keep their pills apart.
                    thumb == RangeThumb.Higher -> minOf(yOf(value), yOf(lower) - pillHeight - gap)
                    else -> maxOf(yOf(value), yOf(higher) + pillHeight + gap)
                }
                val emphasized = thumb == stepped || dragging == thumb
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (center - pillHeight / 2).roundToInt()) }
                        .padding(end = 2.dp)
                        .height(PillHeight)
                        .shadow(if (emphasized) 3.dp else 1.dp, CircleShape)
                        .clip(CircleShape)
                        .background(if (dragging == thumb) colors.accent else canvasPanelColor())
                        .pointerInput(thumb, layerCount) {
                            // A pill moves its handle by the distance the finger travels.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                active = thumb
                                dragging = thumb
                                val start = if (thumb == RangeThumb.Higher) currentHigher else currentLower
                                var travelled = 0f
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        travelled += change.position.y - change.previousPosition.y
                                        change(thumb, (start - travelled * last / travel).roundToInt())
                                        change.consume()
                                    }
                                } while (event.changes.any(PointerInputChange::pressed))
                                dragging = null
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(value),
                        color = when {
                            dragging == thumb -> colors.onAccent
                            emphasized -> colors.text
                            else -> colors.textSide
                        },
                        style = OrcaTheme.typography.body12.copy(fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .width(TrackTouchWidth)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ControlButton(OrcaGlyphs.ChevronUp, stepUpDescription, enabled = steppedValue < last) { change(stepped, steppedValue + 1) }
            Box(
                Modifier
                    .padding(vertical = ControlGap)
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(layerCount) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val top = TrackInset.toPx()
                            val height = (size.height - 2 * top).coerceAtLeast(1f)
                            fun valueAt(y: Float) = ((1f - ((y - top) / height).coerceIn(0f, 1f)) * last).roundToInt()
                            fun yAt(value: Int) = top + height * (1f - if (last > 0) value.toFloat() / last else 0f)
                            val thumb = when {
                                currentOneLayer -> RangeThumb.Higher
                                abs(down.position.y - yAt(currentHigher)) <= abs(down.position.y - yAt(currentLower)) -> RangeThumb.Higher
                                else -> RangeThumb.Lower
                            }
                            active = thumb
                            dragging = thumb
                            change(thumb, valueAt(down.position.y))
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    change(thumb, valueAt(change.position.y))
                                    change.consume()
                                }
                            } while (event.changes.any(PointerInputChange::pressed))
                            dragging = null
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val top = TrackInset.toPx()
                    val height = (size.height - 2 * top).coerceAtLeast(1f)
                    fun yAt(value: Int) = top + height * (1f - if (last > 0) value.toFloat() / last else 0f)
                    val x = size.width / 2
                    val thickness = TrackThickness.toPx()
                    val handleGap = HandleGap.toPx()
                    val outer = thickness / 2
                    val inner = InnerCorner.toPx()
                    val inactive = colors.textSide.copy(alpha = 0.35f)

                    // A piece of the track from [from] to [to], fully rounded where the track ends. As in
                    // Material 3 sliders, a piece no longer than its rounded end is left out.
                    fun segment(from: Float, to: Float, color: Color, roundTop: Boolean, roundBottom: Boolean) {
                        val topRadius = if (roundTop) outer else inner
                        val bottomRadius = if (roundBottom) outer else inner
                        if (to - from <= maxOf(topRadius, bottomRadius)) return
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = x - thickness / 2,
                                    top = from,
                                    right = x + thickness / 2,
                                    bottom = to,
                                    topLeftCornerRadius = CornerRadius(topRadius),
                                    topRightCornerRadius = CornerRadius(topRadius),
                                    bottomRightCornerRadius = CornerRadius(bottomRadius),
                                    bottomLeftCornerRadius = CornerRadius(bottomRadius),
                                ),
                            )
                        }
                        drawPath(path, color)
                    }

                    fun handle(y: Float, length: Float, color: Color) {
                        val barThickness = HandleThickness.toPx()
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x - length / 2, y - barThickness / 2),
                            size = Size(length, barThickness),
                            cornerRadius = CornerRadius(barThickness / 2),
                        )
                    }

                    val trackStart = top - outer
                    val trackEnd = top + height + outer
                    val higherY = yAt(higher)
                    if (oneLayer) {
                        segment(trackStart, higherY - handleGap, inactive, roundTop = true, roundBottom = false)
                        segment(higherY + handleGap, trackEnd, inactive, roundTop = false, roundBottom = true)
                        handle(higherY, higherLength.toPx(), colors.accent)
                    } else {
                        val lowerY = yAt(lower)
                        segment(trackStart, higherY - handleGap, inactive, roundTop = true, roundBottom = false)
                        segment(higherY + handleGap, lowerY - handleGap, colors.accent, roundTop = false, roundBottom = false)
                        segment(lowerY + handleGap, trackEnd, inactive, roundTop = false, roundBottom = true)
                        val secondary = colors.accent.copy(alpha = 0.6f)
                        handle(lowerY, lowerLength.toPx(), if (stepped == RangeThumb.Lower) colors.accent else secondary)
                        handle(higherY, higherLength.toPx(), if (stepped == RangeThumb.Higher) colors.accent else secondary)
                    }
                    // IMSlider::draw_ticks(): a line across the track at every code.
                    for (mark in marks) {
                        val y = yAt(mark.layer.coerceIn(0, last))
                        drawLine(mark.color, Offset(x - thickness, y), Offset(x + thickness, y), strokeWidth = 2.dp.toPx())
                    }
                }
            }
            ControlButton(OrcaGlyphs.ChevronDown, stepDownDescription, enabled = steppedValue > 0) { change(stepped, steppedValue - 1) }
            Box(Modifier.height(ControlGap))
            ControlButton(
                icon = if (oneLayer) OrcaGlyphs.SingleLayer else OrcaGlyphs.Layers,
                description = oneLayerDescription,
                enabled = true,
                selected = oneLayer,
            ) { onOneLayerChange(!oneLayer) }
            if (onLayerMenu != null) {
                Box(Modifier.height(ControlGap))
                ControlButton(OrcaGlyphs.Plus, layerMenuDescription, enabled = layerCount > 0) { onLayerMenu(steppedValue) }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = OrcaTheme.colors
    Box(
        modifier = Modifier
            .size(ControlButtonSize)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) colors.accent else canvasPanelColor())
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                selected -> colors.onAccent
                enabled -> colors.text
                else -> colors.textDisabled
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The moves of the top visible layer in a floating panel: a button that plays
 * them one after another, buttons that step by one move, and the slider of the
 * layer slider's style between them, a thick rounded track filled with the
 * accent up to the bar handle, followed by the move number. A finger anywhere
 * on the track moves the handle to it. [position] runs from 0 to [moveCount] - 1.
 */
@Composable
fun OrcaMovePlayer(
    moveCount: Int,
    position: Int,
    onPositionChange: (Int) -> Unit,
    label: String,
    contentDescription: String,
    playDescription: String,
    previousDescription: String,
    nextDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val last = (moveCount - 1).coerceAtLeast(0)
    val currentPosition by rememberUpdatedState(position)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    var playing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val handleLength by animateDpAsState(if (dragging) HandleDraggedLength else HandleLength, label = "move handle")

    LaunchedEffect(playing, moveCount) {
        if (!playing) return@LaunchedEffect
        var next = if (currentPosition >= last) 0 else currentPosition + 1
        while (next <= last) {
            currentOnPositionChange(next)
            delay(PLAYBACK_STEP_MILLIS)
            next++
        }
        playing = false
    }

    Row(
        modifier = modifier
            .height(52.dp)
            .shadow(6.dp, PanelShape)
            .clip(PanelShape)
            .background(canvasPanelColor())
            .padding(horizontal = 6.dp)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerButton(if (playing) OrcaGlyphs.Pause else OrcaGlyphs.Play, playDescription, highlighted = true) { playing = !playing }
        PlayerButton(OrcaGlyphs.Minus, previousDescription) {
            playing = false
            currentOnPositionChange((currentPosition - 1).coerceAtLeast(0))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(moveCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        playing = false
                        dragging = true
                        val start = TrackInset.toPx()
                        val span = (size.width - 2 * start).coerceAtLeast(1f)
                        fun valueAt(x: Float) = (((x - start) / span).coerceIn(0f, 1f) * last).roundToInt()
                        currentOnPositionChange(valueAt(down.position.x))
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                currentOnPositionChange(valueAt(change.position.x))
                                change.consume()
                            }
                        } while (event.changes.any(PointerInputChange::pressed))
                        dragging = false
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val start = TrackInset.toPx()
                val span = (size.width - 2 * start).coerceAtLeast(1f)
                val y = size.height / 2
                val thickness = TrackThickness.toPx()
                val outer = thickness / 2
                val inner = InnerCorner.toPx()
                val handleGap = HandleGap.toPx()
                val x = start + span * if (last > 0) position.toFloat() / last else 1f

                // A piece of the track from [from] to [to], fully rounded where the track ends. As in
                // Material 3 sliders, a piece no longer than its rounded end is left out.
                fun segment(from: Float, to: Float, color: Color, roundStart: Boolean, roundEnd: Boolean) {
                    val startRadius = if (roundStart) outer else inner
                    val endRadius = if (roundEnd) outer else inner
                    if (to - from <= maxOf(startRadius, endRadius)) return
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = from,
                                top = y - thickness / 2,
                                right = to,
                                bottom = y + thickness / 2,
                                topLeftCornerRadius = CornerRadius(startRadius),
                                topRightCornerRadius = CornerRadius(endRadius),
                                bottomRightCornerRadius = CornerRadius(endRadius),
                                bottomLeftCornerRadius = CornerRadius(startRadius),
                            ),
                        )
                    }
                    drawPath(path, color)
                }

                segment(start - outer, x - handleGap, colors.accent, roundStart = true, roundEnd = false)
                segment(x + handleGap, start + span + outer, colors.textSide.copy(alpha = 0.35f), roundStart = false, roundEnd = true)
                val barThickness = HandleThickness.toPx()
                val length = handleLength.toPx()
                drawRoundRect(
                    color = colors.accent,
                    topLeft = Offset(x - barThickness / 2, y - length / 2),
                    size = Size(barThickness, length),
                    cornerRadius = CornerRadius(barThickness / 2),
                )
            }
        }
        PlayerButton(OrcaGlyphs.Plus, nextDescription) {
            playing = false
            currentOnPositionChange((currentPosition + 1).coerceAtMost(last))
        }
        Text(
            text = label,
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp, end = 8.dp),
        )
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, description: String, highlighted: Boolean = false, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (highlighted) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = if (highlighted) colors.onAccent else colors.text, modifier = Modifier.size(18.dp))
        }
    }
}
