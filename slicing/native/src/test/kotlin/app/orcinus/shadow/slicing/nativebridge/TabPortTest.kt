package app.orcinus.shadow.slicing.nativebridge

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine ports OrcaSlicer's settings tabs by hand, keeping the statements
 * of their build functions. This test reads the pinned Tab.cpp and the port and
 * compares what they lay out, so an OrcaSlicer update that adds a page, a group
 * or a setting fails here instead of silently hiding it from the app.
 */
class TabPortTest {
    private val upstream = File(System.getProperty("orcinus.upstreamTab")!!).readText()
    private val adapter = File(System.getProperty("orcinus.adapterSources")!!)

    @Test
    fun `the process tab is laid out as TabPrint does`() {
        assertLayoutMatches("void TabPrint::build()", "tab_print.cpp", "void TabPrint::build()")
    }

    @Test
    fun `the filament tab is laid out as TabFilament does`() {
        assertLayoutMatches("void TabFilament::build()", "tab_filament.cpp", "void TabFilament::build()")
        assertLayoutMatches(
            "void TabFilament::add_filament_overrides_page()",
            "tab_filament.cpp",
            "void TabFilament::add_filament_overrides_page()",
        )
    }

    @Test
    fun `the printer tab is laid out as TabPrinter does`() {
        assertLayoutMatches("void TabPrinter::build_fff()", "tab_printer.cpp", "void TabPrinter::build_fff()")
        assertLayoutMatches("PageShp TabPrinter::build_kinematics_page()", "tab_printer.cpp", "PageShp TabPrinter::build_kinematics_page()")
        assertLayoutMatches(
            "void TabPrinter::build_unregular_pages(bool from_initial_build/* = false*/)",
            "tab_printer.cpp",
            "void TabPrinter::build_unregular_pages(const bool from_initial_build)",
        )
    }

    private fun assertLayoutMatches(upstreamSignature: String, portFile: String, portSignature: String) {
        val expected = layoutOf(functionBody(upstream, upstreamSignature))
        val actual = layoutOf(functionBody(adapter.resolve(portFile).readText(), portSignature))
        assertTrue(expected.isNotEmpty(), "$upstreamSignature lays out nothing")
        assertEquals(expected, actual, "The port of $upstreamSignature differs from OrcaSlicer's")
    }

    /** The body of the function whose definition starts with [signature], without its braces. */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        check(start >= 0) { "$signature is not in the source" }
        val open = source.indexOf('{', start + signature.length)
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open + 1, index)
            }
            index++
        }
        error("The body of $signature does not end")
    }

    /**
     * What the statements of a build function lay out, in order: the pages,
     * the option groups, and the settings their lines show. Comments and the
     * code the desktop app needs for its controls are left out, since only the
     * layout is ported.
     */
    private fun layoutOf(body: String): List<String> {
        val code = withoutComments(body)
        val laidOut = LAYOUT_CALLS.findAll(code).map { match ->
            val call = match.groupValues[1]
            val argument = match.groupValues[2]
            match.range.first to when (call) {
                "add_options_page" -> "page $argument"
                "new_optgroup" -> "group $argument"
                else -> "option $argument"
            }
        }
        // The groups whose custom G-codes open EditGCodeDialog.
        val editable = EDIT_CUSTOM_GCODE.findAll(code).map { it.range.first to "edit custom G-code" }
        // The settings a group appends in a loop over a list of their keys.
        val listed = LITERAL_LISTS.findAll(code).flatMap { list ->
            LITERAL.findAll(list.groupValues[1])
                .map { it.groupValues[1] }
                .filter(SETTING_KEY::matches)
                .map { list.range.first to "option $it" }
        }
        return (laidOut + editable + listed).sortedBy { it.first }.map { it.second }.toList()
    }

    private fun withoutComments(body: String): String {
        val code = StringBuilder()
        var index = 0
        while (index < body.length) {
            when {
                body.startsWith("//", index) -> index = body.indexOf('\n', index).let { if (it < 0) body.length else it }
                body.startsWith("/*", index) -> index = body.indexOf("*/", index).let { if (it < 0) body.length else it + 2 }
                body[index] == '"' -> {
                    var end = index + 1
                    while (end < body.length && body[end] != '"') {
                        if (body[end] == '\\') end++
                        end++
                    }
                    code.append(body, index, minOf(end + 1, body.length))
                    index = end + 1
                }
                else -> {
                    code.append(body[index])
                    index++
                }
            }
        }
        return code.toString()
    }

    private companion object {
        /**
         * The calls that lay a tab out; OrcaSlicer marks its texts for
         * translation with L(), which the port drops since the engine passes
         * them untranslated.
         */
        val LAYOUT_CALLS = Regex(
            """(add_options_page|new_optgroup|append_single_option_line|get_option|create_line_with_widget)\(""" +
                """(?:optgroup(?:\.get\(\))?,\s*)?(?:L\(|L)?"([^"]*)"""",
        )

        /**
         * A group given the edit button of its custom G-codes: upstream assigns
         * the function that opens the dialog, the port a flag.
         */
        val EDIT_CUSTOM_GCODE = Regex("""optgroup->edit_custom_gcode\s*=""")

        /** A list of settings keys, which a loop appends to a group. */
        val LITERAL_LISTS = Regex("""\{\s*("(?:[^"\\]|\\.)*"(?:\s*,\s*"(?:[^"\\]|\\.)*")*)\s*,?\s*}""")
        val LITERAL = Regex(""""((?:[^"\\]|\\.)*)"""")

        /** A settings key, which the labels and the wiki paths of a line never look like. */
        val SETTING_KEY = Regex("""[a-z][a-z0-9_]*""")
    }
}
