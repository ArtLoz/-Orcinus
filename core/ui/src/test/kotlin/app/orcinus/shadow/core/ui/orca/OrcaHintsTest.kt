package app.orcinus.shadow.core.ui.orca

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OrcaHintsTest {
    @Test
    fun `the daily tips are the hints of OrcaSlicer's hints ini in its order, each text a msgid of the catalogue`() {
        val hints = OrcaHints.parse(File("../../upstream/OrcaSlicer/resources/data/hints.ini").readText())

        assertEquals(37, hints.size)
        val first = hints.first()
        assertEquals("Precise wall\nDid you know that turning on precise wall can improve precision and layer consistency?", first.text)
        assertEquals("https://www.orcaslicer.com/wiki/quality_settings_precision#precise-wall", first.documentationLink)
        // unescape_string_cstyle() makes \" a quote.
        assertEquals(true, hints.any { "\"Place on face\"" in it.text })
    }

    @Test
    fun `a hint's main text ends before its hypertext`() {
        val hints = OrcaHints.parse("[hint:Link]\ntext = Head\\nBody <a>open</a> more\n[other]\ntext = skipped\n[hint:Broken]\ntext = Head\\n<a>one</a><a>two</a>\n")

        assertEquals(1, hints.size)
        assertEquals("Head\nBody ", OrcaHints.mainText(hints.single().text))
    }
}
