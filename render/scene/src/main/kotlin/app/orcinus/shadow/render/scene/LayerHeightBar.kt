package app.orcinus.shadow.render.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ScenePath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * GLCanvas3D::LayersEditing of the object with the [mesh] file, as the 3D
 * view shows it: the [layers] its profile makes (the bottom and top of every
 * layer from the bed up), the [profile] itself (z and layer height pairs), the
 * object's height, the slicing parameters the colours scale with, where the
 * finger is on the bar ([cursorZ], null while it is not there) and the band it
 * edits ([bandWidth]).
 */
data class LayerEditingView(
    val mesh: ScenePath,
    val layers: List<Double>,
    val profile: List<Double>,
    val objectMaxZ: Double,
    val layerHeight: Double,
    val minLayerHeight: Double,
    val maxLayerHeight: Double,
    val objectPrintZHeight: Double,
    val cursorZ: Double?,
    val bandWidth: Double,
)

/**
 * The variable layer height bar (LayersEditing::render_active_object_annotations()
 * and render_profile()) where the page places it, beside the [PlateView] that
 * [camera] drives: the view draws the layers' colours there, and this draws
 * the profile over them, black at the plain layer height and blue along the
 * profile, and takes the finger. The desktop bar fills the canvas's right edge
 * and a mouse button held on it edits the band of layers under it; a finger
 * does the same, [onPress] at the height it touches, [onMove] as it slides and
 * [onRelease] once it lets go, while the layer height under it shows beside
 * the bar, as the desktop canvas's tooltip shows it.
 */
@Composable
fun LayerHeightBar(
    camera: PlateViewCamera,
    editing: LayerEditingView,
    onPress: (z: Double) -> Unit,
    onMove: (z: Double) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(camera) { onDispose { camera.layerBarSlot = null } }
    val current by rememberUpdatedState(editing)
    val press by rememberUpdatedState(onPress)
    val move by rememberUpdatedState(onMove)
    val release by rememberUpdatedState(onRelease)
    var height by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .width(THICKNESS_BAR_WIDTH.dp)
            .onGloballyPositioned { coordinates ->
                height = coordinates.size.height.toFloat()
                camera.layerBarSlot = coordinates.boundsInRoot()
            }
            .pointerInput(Unit) {
                // GLCanvas3D::_perform_layer_editing_action(): the bar's height at
                // the finger, from its bottom up to the object's top.
                fun zAt(y: Float): Double {
                    val bottom = size.height.toFloat()
                    return current.objectMaxZ * ((bottom - y - 1f) / bottom).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    press(zAt(down.position.y))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.position != change.previousPosition) move(zAt(change.position.y))
                        event.changes.forEach(PointerInputChange::consume)
                    }
                    release()
                }
            },
    ) {
        val density = LocalDensity.current.density
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val barHeight = size.height
            if (editing.objectMaxZ <= 0.0 || editing.maxLayerHeight <= 0.0 || barHeight <= 0f) return@Canvas
            // Make the vertical bar a bit wider so the layer height curve does not touch the edge of the bar region.
            val scaleX = width / (1.12 * editing.maxLayerHeight)
            val scaleY = barHeight / editing.objectMaxZ
            val stroke = Stroke(width = density)
            // Baseline
            val axis = (editing.layerHeight * scaleX).toFloat()
            drawLine(Color.Black, Offset(axis, 0f), Offset(axis, barHeight), strokeWidth = density)
            // The profile.
            val profile = editing.profile
            if (profile.size >= 4) {
                val path = Path()
                for (index in 0 until profile.size / 2) {
                    val x = (profile[2 * index + 1] * scaleX).toFloat()
                    val y = barHeight - (profile[2 * index] * scaleY).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color.Blue, style = stroke)
            }
        }
        // LayersEditing::get_tooltip(): the layer height at the finger.
        val cursor = editing.cursorZ
        val tooltip = cursor?.let { layerHeightAt(editing.profile, it) }
        if (cursor != null && tooltip != null && height > 0f) {
            val y = height * (1f - (cursor / editing.objectMaxZ).toFloat().coerceIn(0f, 1f))
            BasicText(
                text = String.format(Locale.ROOT, "%.3f", tooltip),
                style = OrcaTheme.typography.body12.copy(color = OrcaTheme.colors.text),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-(THICKNESS_BAR_WIDTH * density).roundToInt(), (y - TOOLTIP_HALF_HEIGHT * density).roundToInt()) }
                    .background(OrcaTheme.colors.window, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * LayersEditing::get_tooltip(): the layer height of [profile] at [z],
 * interpolated between its points from the top down; null outside it.
 */
internal fun layerHeightAt(profile: List<Double>, z: Double): Double? {
    if (profile.size < 4) return null
    var index = profile.size - 2
    while (index >= 2) {
        val zi = profile[index]
        val below = profile[index - 2]
        if (below <= z && z <= zi) {
            val dz = zi - below
            val height = if (dz != 0.0) profile[index - 1] + (profile[index + 1] - profile[index - 1]) * (z - below) / dz else profile[index + 1]
            return height.takeIf { it > 0.0 }
        }
        index -= 2
    }
    return null
}

/**
 * LayersEditing::m_layers_texture: generate_layer_height_texture() of
 * Slicing.cpp, a 1D texture of the layers' colours along the object's height
 * split into the rows of a 2D one, with a second level of detail after the
 * first that has no stripes between the layers.
 */
internal class LayerHeightTexture {
    val width = TEXTURE_SIZE
    val height = TEXTURE_SIZE

    /** RGBA of level 0, then of level 1 at half the width and height, as OpenGL reads it. */
    val data: ByteBuffer = ByteBuffer.allocateDirect(width * height * 5).order(ByteOrder.nativeOrder())

    /** Where level 1 starts in [data]. */
    val secondLevel = width * height * 4

    /** The cells of level 0 the layers fill. */
    var cells = 0
        private set

    /** generate_layer_height_texture() with level_of_detail_2nd_level. */
    fun generate(layers: List<Double>, layerHeight: Double, minLayerHeight: Double, maxLayerHeight: Double, objectPrintZHeight: Double) {
        val rows = height
        val cols = width
        // 2nd LOD level data start
        val data1 = secondLevel
        val ncells = min((cols - 1) * rows, ceil(16.0 * (objectPrintZHeight / minLayerHeight)).toInt())
        val ncells1 = ncells / 2
        val cols1 = cols / 2
        val zToCell = (ncells - 1) / objectPrintZHeight
        val cellToZ = objectPrintZHeight / (ncells - 1)
        val zToCell1 = (ncells1 - 1) / objectPrintZHeight
        // for color scaling
        var hscale = 2.0 * max(maxLayerHeight - layerHeight, layerHeight - minLayerHeight)
        // All layers have the same height. Provide some height scale to avoid division by zero.
        if (hscale == 0.0) hscale = layerHeight
        var layer = 0
        while (layer + 1 < layers.size) {
            val lo = layers[layer]
            var hi = layers[layer + 1]
            val mid = 0.5 * (lo + hi)
            val h = hi - lo
            hi = min(hi, objectPrintZHeight)
            val idxf = (0.5 * hscale + (h - layerHeight)) * (PALETTE.size - 1) / hscale
            val idx1 = floor(idxf).toInt().coerceIn(0, PALETTE.size - 1)
            val idx2 = min(PALETTE.size - 1, idx1 + 1)
            val t = idxf - idx1
            val color1 = PALETTE[idx1]
            val color2 = PALETTE[idx2]
            var cellFirst = ceil(lo * zToCell).toInt().coerceIn(0, ncells - 1)
            var cellLast = floor(hi * zToCell).toInt().coerceIn(0, ncells - 1)
            for (cell in cellFirst..cellLast) {
                val z = cellToZ * cell
                // Intensity profile to visualize the layers.
                val intensity = cos(Math.PI * 0.7 * (mid - z) / h)
                // Color mapping from layer height to RGB.
                val row = cell / (cols - 1)
                val col = cell - row * (cols - 1)
                put((row * cols + col) * 4, color1, color2, t, intensity, duplicate = col == 0 && row > 0)
            }
            cellFirst = ceil(lo * zToCell1).toInt().coerceIn(0, ncells1 - 1)
            cellLast = floor(hi * zToCell1).toInt().coerceIn(0, ncells1 - 1)
            for (cell in cellFirst..cellLast) {
                val row = cell / (cols1 - 1)
                val col = cell - row * (cols1 - 1)
                put(data1 + (row * cols1 + col) * 4, color1, color2, t, 1.0, duplicate = col == 0 && row > 0)
            }
            layer += 2
        }
        cells = ncells
    }

    private fun put(at: Int, color1: IntArray, color2: IntArray, t: Double, intensity: Double, duplicate: Boolean) {
        for (channel in 0 until 3) {
            val value = intensity * (color1[channel] + (color2[channel] - color1[channel]) * t)
            data.put(at + channel, floor(value + 0.5).toInt().coerceIn(0, 255).toByte())
        }
        data.put(at + 3, 255.toByte())
        // Duplicate the first value in a row as a last value of the preceding row.
        if (duplicate) {
            for (channel in 0 until 4) data.put(at - 4 + channel, data.get(at + channel))
        }
    }

    private companion object {
        const val TEXTURE_SIZE = 1024

        // https://github.com/aschn/gnuplot-colorbrewer
        val PALETTE = arrayOf(
            intArrayOf(0x01A, 0x098, 0x050),
            intArrayOf(0x066, 0x0BD, 0x063),
            intArrayOf(0x0A6, 0x0D9, 0x06A),
            intArrayOf(0x0D9, 0x0F1, 0x0EB),
            intArrayOf(0x0FE, 0x0E6, 0x0EB),
            intArrayOf(0x0FD, 0x0AE, 0x061),
            intArrayOf(0x0F4, 0x06D, 0x043),
            intArrayOf(0x0D7, 0x030, 0x027),
        )
    }
}

/** GLCanvas3D::LayersEditing::THICKNESS_BAR_WIDTH, in desktop pixels. */
internal const val THICKNESS_BAR_WIDTH = 70f

/** Half the height of the tooltip's line, which it stands centred on. */
private const val TOOLTIP_HALF_HEIGHT = 10f
