package app.orcinus.shadow.core.ui.orca

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OrcaWebTextsTest {
    private val texts = OrcaWebTexts.parse(File("../../upstream/OrcaSlicer/resources/web/data/text.js").readText())

    @Test
    fun `the home page's texts come from OrcaSlicer's text js in the page's language`() {
        val russian = webLanguageCode(ORCA_LANGUAGES.first { it.catalog == "ru" })
        assertEquals("ru_RU", russian)
        assertEquals("Новый проект", texts.text(russian, "t31", "new project"))
        assertEquals("Недавно открытые", texts.text(russian, "t35", "recent open"))
        // English has no region of its own, and Catalan none Prusa Research keeps: both read LangText.en.
        assertEquals("Clear all", texts.text(webLanguageCode(ORCA_LANGUAGES.first { it.catalog == "en" }), "t12", "Clear all"))
        assertEquals("en_US", webLanguageCode(ORCA_LANGUAGES.first { it.catalog == "ca" }))
        // Ukrainian has a code, but no texts in the file: English ones.
        assertEquals("Remove", texts.text(webLanguageCode(ORCA_LANGUAGES.first { it.catalog == "uk" }), "t88", "clear"))
        // A text no language has is the page's own.
        assertEquals("own", texts.text(russian, "missing", "own"))
    }
}
