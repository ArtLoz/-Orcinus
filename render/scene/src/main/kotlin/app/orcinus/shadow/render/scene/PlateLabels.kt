package app.orcinus.shadow.render.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import app.orcinus.shadow.render.scene.gl.GlVertexArray
import app.orcinus.shadow.render.scene.math.Box3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil

/**
 * The number OrcaSlicer writes beside every plate (PartPlate::render_only_numbers):
 * "01" to "36" in bold, in its orange, on nothing, at the right of the
 * plate's front edge.
 */
internal object PlateLabels {
    // PartPlate.cpp
    private const val PARTPLATE_ICON_SIZE = 16.0
    private const val PARTPLATE_ICON_GAP_LEFT = 3.0
    private const val PARTPLATE_TEXT_OFFSET_X2 = 1.0
    private const val PARTPLATE_TEXT_OFFSET_Y = 1.0

    private const val PARTPLATE_EDIT_PLATE_NAME_ICON_SIZE = 9.0

    /** PartPlateList::generate_icon_textures(): wxGetApp().em_unit() * PARTPLATE_ICON_SIZE at 100 %. */
    private const val FONT_SIZE = 10f * 16f

    /** PartPlate::generate_plate_name_texture(): wxGetApp().em_unit() * PARTPLATE_EDIT_PLATE_NAME_ICON_SIZE. */
    private const val NAME_FONT_SIZE = 10f * 9f

    /** The foreground generate_from_text_string() is given. */
    private const val RED = 0xF2
    private const val GREEN = 0x75
    private const val BLUE = 0x4E

    /**
     * GLTexture::generate_from_text(): the text drawn white on black in a
     * power-of-two bitmap, its top left corner; the texture takes the
     * foreground colour, with the text's whiteness as its alpha.
     */
    fun image(index: Int): TextureImage = textImage(if (index < 9) "0${index + 1}" else "${index + 1}", FONT_SIZE)

    /** PartPlate::generate_plate_name_texture(): the plate's name, as the numbers are written. */
    fun nameImage(name: String): TextureImage = textImage(name, NAME_FONT_SIZE)

    private fun textImage(text: String, fontSize: Float): TextureImage {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = fontSize
            color = Color.WHITE
        }
        val metrics = paint.fontMetrics
        val textWidth = ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
        val textHeight = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        val width = nextPowerOfTwo(textWidth)
        val height = nextPowerOfTwo(textHeight)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.BLACK)
                drawText(text, 0f, -metrics.ascent, paint)
            }
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val rgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                rgba.put(RED.toByte()).put(GREEN.toByte()).put(BLUE.toByte()).put(Color.red(pixel).toByte())
            }
            rgba.flip()
            return TextureImage(width, height, rgba)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * PartPlate::calc_vertex_for_number() "in the bottom": a square of
     * PARTPLATE_ICON_SIZE scaled by the plate's depth over 200 mm, right of
     * the plate's front right corner, textured as init_model_from_poly() maps
     * its bounding box; x, y, z, u, v per vertex.
     */
    fun quad(area: Box3): FloatBuffer {
        val factor = (area.max.y - area.min.y) / 200.0
        val size = PARTPLATE_ICON_SIZE * factor
        val offsetX = PARTPLATE_TEXT_OFFSET_X2 * factor
        val offsetY = PARTPLATE_TEXT_OFFSET_Y * factor
        val x = area.max.x + PARTPLATE_ICON_GAP_LEFT * factor
        val y = area.min.y
        val x0 = x + offsetX
        val x1 = x + size - offsetX
        val y0 = y + offsetY
        val y1 = y + size - offsetY
        val corners = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y0, x1 to y1, x0 to y1)
        return GlVertexArray.floatBuffer(FloatArray(corners.size * 5) { index ->
            val (px, py) = corners[index / 5]
            when (index % 5) {
                0 -> px.toFloat()
                1 -> py.toFloat()
                2 -> GROUND_Z
                3 -> ((px - x0) / (x1 - x0)).toFloat()
                else -> (-(py - y0) / (y1 - y0)).toFloat()
            }
        })
    }

    /**
     * generate_plate_name_texture(): over the plate's back left corner, as tall
     * as PARTPLATE_EDIT_PLATE_NAME_ICON_SIZE scaled by the plate's depth over
     * 200 mm and as wide as the texture's proportions make it.
     */
    fun nameQuad(area: Box3, image: TextureImage): FloatBuffer {
        val factor = (area.max.y - area.min.y) / 200.0
        val height = PARTPLATE_EDIT_PLATE_NAME_ICON_SIZE * factor
        val width = height * image.width / image.height
        return quadOf(area.min.x, area.max.y + PARTPLATE_TEXT_OFFSET_Y * factor, width, height)
    }

    /** A textured square from ([x], [y]), [width] by [height], as init_model_from_poly() maps its bounding box. */
    private fun quadOf(x: Double, y: Double, width: Double, height: Double): FloatBuffer {
        val x1 = x + width
        val y1 = y + height
        val corners = listOf(x to y, x1 to y, x1 to y1, x to y, x1 to y1, x to y1)
        return GlVertexArray.floatBuffer(FloatArray(corners.size * 5) { index ->
            val (px, py) = corners[index / 5]
            when (index % 5) {
                0 -> px.toFloat()
                1 -> py.toFloat()
                2 -> GROUND_Z
                3 -> ((px - x) / width).toFloat()
                else -> (-(py - y) / height).toFloat()
            }
        })
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var power = 1
        while (power < value) power = power shl 1
        return power
    }
}
