package app.orcinus.shadow.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small line glyphs for the preview's touch controls, which OrcaSlicer's
 * desktop GUI has no icons for. Drawn on a 24 unit grid; tint them.
 */
internal object OrcaGlyphs {
    val Play: ImageVector = glyph("play") {
        moveTo(8f, 5.5f)
        lineTo(18.5f, 12f)
        lineTo(8f, 18.5f)
        close()
    }

    val Pause: ImageVector = glyph("pause") {
        moveTo(8f, 5.5f)
        lineTo(8f, 18.5f)
        moveTo(16f, 5.5f)
        lineTo(16f, 18.5f)
    }

    val ChevronUp: ImageVector = glyph("chevron_up") {
        moveTo(6.5f, 14.5f)
        lineTo(12f, 9f)
        lineTo(17.5f, 14.5f)
    }

    val ChevronDown: ImageVector = glyph("chevron_down") {
        moveTo(6.5f, 9.5f)
        lineTo(12f, 15f)
        lineTo(17.5f, 9.5f)
    }

    val Plus: ImageVector = glyph("plus") {
        moveTo(12f, 6.5f)
        lineTo(12f, 17.5f)
        moveTo(6.5f, 12f)
        lineTo(17.5f, 12f)
    }

    val Minus: ImageVector = glyph("minus") {
        moveTo(6.5f, 12f)
        lineTo(17.5f, 12f)
    }

    /** One layer: a single slab. */
    val SingleLayer: ImageVector = glyph("single_layer") {
        moveTo(4.5f, 12f)
        lineTo(12f, 8.5f)
        lineTo(19.5f, 12f)
        lineTo(12f, 15.5f)
        close()
    }

    /** Every layer: stacked slabs. */
    val Layers: ImageVector = glyph("layers") {
        moveTo(4.5f, 9f)
        lineTo(12f, 5.5f)
        lineTo(19.5f, 9f)
        lineTo(12f, 12.5f)
        close()
        moveTo(4.5f, 13f)
        lineTo(12f, 16.5f)
        lineTo(19.5f, 13f)
        moveTo(4.5f, 17f)
        lineTo(12f, 20.5f)
        lineTo(19.5f, 17f)
    }

    private fun glyph(name: String, commands: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = commands,
            )
            .build()
}
