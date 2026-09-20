package app.orcinus.shadow.core.ui.settings

import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSearchTest {
    @Test
    fun `a setting is found by a part of its label, best match first`() {
        val results = SettingsSearch.search("стен", CATALOG, SettingsMode.EXPERT, ::translate)

        assertTrue(results.isNotEmpty())
        assertEquals("wall_loops", results.first().option.key)
        // OptionsSearcher builds the line as "Group : Label".
        assertEquals("Стенки : Периметры", results.first().text)
        // The characters the query matched are marked in the line.
        assertTrue(results.first().matches.isNotEmpty())
    }

    @Test
    fun `a query that matches a whole word finds the setting it belongs to`() {
        val results = SettingsSearch.search("периметры", CATALOG, SettingsMode.EXPERT, ::translate)

        assertEquals(listOf("wall_loops"), results.map { it.option.key })
    }

    @Test
    fun `a setting whose label matches scores above one that only matches through its page`() {
        // "шва" is in the label of the seam setting, and its characters are
        // scattered over the line of the layer height.
        val results = SettingsSearch.search("шва", CATALOG, SettingsMode.EXPERT, ::translate)

        assertEquals("seam_position", results.first().option.key)
        results.drop(1).forEach { assertTrue(it.score < results.first().score) }
    }

    @Test
    fun `settings above the mode are left out, as the tabs leave them out`() {
        val simple = SettingsSearch.search("", CATALOG, SettingsMode.SIMPLE, ::translate)

        assertEquals(listOf("wall_loops"), simple.map { it.option.key })
        // An empty query lists every setting of the mode in the tabs' order.
        assertEquals(CATALOG.map { it.key }, SettingsSearch.search("", CATALOG, SettingsMode.EXPERT, ::translate).map { it.option.key })
    }

    @Test
    fun `a query that matches nothing finds nothing`() {
        assertTrue(SettingsSearch.search("щщщ", CATALOG, SettingsMode.EXPERT, ::translate).isEmpty())
    }

    @Test
    fun `the matcher takes the characters in their order and marks where they matched`() {
        val match = FuzzyMatch.match("wall", "Walls : Wall loops")

        assertNotNull(match)
        assertEquals(4, match.matches.size)
        // A run of characters scores above the 90 the search accepts.
        assertTrue(match.score > 90)

        assertNull(FuzzyMatch.match("zzz", "Walls : Wall loops"))
    }

    private fun translate(texts: List<OrcaText>): String = texts.joinToString("") { text ->
        TRANSLATIONS[text.msgid] ?: text.msgid
    }

    private companion object {
        val TRANSLATIONS = mapOf(
            "Strength" to "Прочность",
            "Walls" to "Стенки",
            "Wall loops" to "Периметры",
            "Quality" to "Вид",
            "Layer height" to "Высота слоя",
            "Seam" to "Шов",
            "Seam position" to "Положение шва",
        )

        fun option(key: String, page: String, group: String, label: String, mode: SettingsMode) = SearchOption(
            kind = PresetKind.PRINT,
            key = key,
            id = key,
            page = listOf(OrcaText(page)),
            group = listOf(OrcaText(group)),
            label = listOf(OrcaText(label)),
            mode = mode,
        )

        val CATALOG = listOf(
            option("wall_loops", "Strength", "Walls", "Wall loops", SettingsMode.SIMPLE),
            option("layer_height", "Quality", "Layer height", "Layer height", SettingsMode.ADVANCED),
            option("seam_position", "Quality", "Seam", "Seam position", SettingsMode.EXPERT),
        )
    }
}
