package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.FontFace
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextStylesTest {
    private val serif = TextFontFamily(
        "Noto Serif",
        listOf(
            FontFace("/fonts/NotoSerif-Regular.ttf", 0, "Noto Serif", "Regular", 400, italic = false),
            FontFace("/fonts/NotoSerif-Bold.ttf", 0, "Noto Serif", "Bold", 700, italic = false),
            FontFace("/fonts/NotoSerif-Italic.ttf", 0, "Noto Serif", "Italic", 400, italic = true),
            FontFace("/fonts/NotoSerif-SemiBold.ttf", 0, "Noto Serif", "SemiBold", 600, italic = false),
        ),
    )
    private val mono = TextFontFamily("Droid Sans Mono", listOf(FontFace("/fonts/DroidSansMono.ttf", 0, "Droid Sans Mono", "Regular", 400, italic = false)))
    private val families = listOf(mono, serif)

    @Test
    fun makeUniqueNameNumbersARepeatedNameAsOrcaSlicerDoes() {
        val styles = listOf(TextStyle("NORMAL", ""), TextStyle("NORMAL (2)", ""))
        assertEquals("SMALL", makeUniqueName(styles, "SMALL"))
        assertEquals("Text style", makeUniqueName(styles, ""))
        assertEquals("NORMAL (3)", makeUniqueName(styles, "NORMAL"))
        // find_last_of(" (") stops at the bracket and keeps the space before it.
        assertEquals("NORMAL  (2)", makeUniqueName(styles, "NORMAL (2)"))
    }

    @Test
    fun boldTakesTheFamilysBoldFaceOrElseBoldness() {
        val regular = TextStyle("NORMAL", "/fonts/NotoSerif-Regular.ttf")
        val bold = regular.toggledBold(families)
        // WxFontUtils::set_bold() asks for bold first, not the nearest heavier face.
        assertEquals("/fonts/NotoSerif-Bold.ttf", bold.fontPath)
        assertEquals("bold", bold.weight)
        assertNull(bold.boldness)
        val unbold = bold.toggledBold(families)
        assertEquals("/fonts/NotoSerif-Regular.ttf", unbold.fontPath)
        // update_property() leaves the weight of a normal face as it was.
        assertEquals("bold", unbold.weight)
        val mono = TextStyle("MODERN", "/fonts/DroidSansMono.ttf").toggledBold(families)
        assertEquals("/fonts/DroidSansMono.ttf", mono.fontPath)
        assertEquals(20.0, mono.boldness)
        assertNull(mono.toggledBold(families).boldness)
    }

    @Test
    fun italicTakesTheFamilysItalicFaceOrElseSkew() {
        val italic = TextStyle("NORMAL", "/fonts/NotoSerif-Regular.ttf").toggledItalic(families)
        assertEquals("/fonts/NotoSerif-Italic.ttf", italic.fontPath)
        assertEquals("italic", italic.style)
        assertEquals("/fonts/NotoSerif-Regular.ttf", italic.toggledItalic(families).fontPath)
        val skewed = TextStyle("MODERN", "/fonts/DroidSansMono.ttf").toggledItalic(families)
        assertEquals(0.2, skewed.skew)
        assertNull(skewed.toggledItalic(families).skew)
    }

    @Test
    fun aFontKeepsTheStylesSkewAndBoldness() {
        val style = TextStyle("NORMAL", "/fonts/DroidSansMono.ttf", skew = 0.2, boldness = 20.0)
        val serif = style.withFamily(serif)!!
        assertEquals("/fonts/NotoSerif-Regular.ttf", serif.fontPath)
        assertEquals("Noto Serif", serif.faceName)
        assertEquals(0.2, serif.skew)
        assertEquals(20.0, serif.boldness)
    }

    @Test
    fun aStyleIsModifiedByAnythingButNearlyEqualSizes() {
        val stored = TextStyle("NORMAL", "/fonts/NotoSerif-Regular.ttf", sizeInMm = 9.0)
        assertTrue(stored.sameStyleAs(stored.copy(sizeInMm = 9.00001)))
        assertTrue(stored.sameStyleAs(stored.copy(faceName = "Noto Serif")))
        assertFalse(stored.sameStyleAs(stored.copy(angle = 0.5)))
        assertFalse(stored.sameStyleAs(stored.copy(name = "SMALL")))
    }
}
