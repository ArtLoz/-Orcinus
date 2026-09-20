package app.orcinus.shadow.core.ui.settings

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.orcinus.shadow.core.model.GcodePlaceholder
import app.orcinus.shadow.core.model.GcodePlaceholderType
import app.orcinus.shadow.core.model.OrcaText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GcodePlaceholderTreeTest {
    // [Global] Slicing State > Read Only > zhop; Specific for layer_change_gcode
    // (expanded) > layer_num; Presets > Printer settings > nozzle_diameter[].
    private val tree = GcodePlaceholderTree(
        listOf(
            group(-1, "[Global] Slicing State"),
            group(0, "Read Only"),
            param(1, "zhop", GcodePlaceholderType.SCALAR),
            group(-1, "Specific for %1%", expanded = true),
            param(3, "layer_num", GcodePlaceholderType.SCALAR),
            group(-1, GcodePlaceholderTree.PRESETS),
            group(5, "Printer settings"),
            param(6, "nozzle_diameter", GcodePlaceholderType.VECTOR),
            param(5, "print_preset", GcodePlaceholderType.SCALAR),
        ),
    )

    @Test
    fun `the list opens with the groups collapsed but the one of the G-code's own placeholders`() {
        val rows = tree.rows(tree.initiallyExpanded, "")

        assertEquals(listOf(0, 3, 4, 5), rows.map { it.index })
        assertEquals(listOf(0, 0, 1, 0), rows.map { it.depth })
    }

    @Test
    fun `an expanded group shows its children in the order the dialog appended them`() {
        val rows = tree.rows(setOf(0, 1, 5, 6), "")

        assertEquals(listOf(0, 1, 2, 3, 5, 6, 7, 8), rows.map { it.index })
        assertEquals(2, rows.first { it.index == 2 }.depth)
    }

    @Test
    fun `a search keeps the matching placeholders and the groups that hold them, expanded and marked`() {
        val rows = tree.rows(emptySet(), "DIAM")

        assertEquals(listOf(5, 6, 7), rows.map { it.index })
        // "nozzle_diameter[]": the query is lowered, as the dialog lowers it.
        assertEquals(7..10, rows.last().highlight)
        // A group whose name matches but holds no match is left out.
        assertTrue(tree.rows(emptySet(), "read").isEmpty())
    }

    @Test
    fun `a placeholder of the presets group is described by the preset's definition`() {
        assertTrue(tree.inPresets(7))
        assertTrue(tree.inPresets(8))
        assertFalse(tree.inPresets(2))
    }

    @Test
    fun `a placeholder goes where the cursor is, and on a new line at the end`() {
        val middle = GcodePlaceholderTree.insert(TextFieldValue("G28\nG1 Z", TextRange(6)), "layer_z")
        assertEquals("G28\nG1layer_z Z", middle.text)
        assertEquals(TextRange(13), middle.selection)

        val end = GcodePlaceholderTree.insert(TextFieldValue("G28", TextRange(3)), "layer_num")
        assertEquals("G28\nlayer_num", end.text)
        assertEquals(TextRange(13), end.selection)

        // A selection is replaced by the placeholder.
        val replaced = GcodePlaceholderTree.insert(TextFieldValue("M104 S200", TextRange(6, 9)), "layer_num")
        assertEquals("M104 Slayer_num", replaced.text)
    }

    @Test
    fun `the cursor stands between the brackets of a vector, and selects the extruder of a filament vector`() {
        val vector = GcodePlaceholderTree.insert(TextFieldValue("M109 S", TextRange(6)), "nozzle_temperature[]")
        assertEquals("M109 S\nnozzle_temperature[]", vector.text)
        assertEquals(TextRange(vector.text.length - 1), vector.selection)

        val filament = GcodePlaceholderTree.insert(TextFieldValue("S", TextRange(0)), "nozzle_temperature[current_extruder]")
        assertEquals("current_extruder", filament.text.substring(filament.selection.start, filament.selection.end))
    }

    private fun group(parent: Int, msgid: String, expanded: Boolean = false) = GcodePlaceholder(
        parent = parent,
        type = GcodePlaceholderType.GROUP,
        label = listOf(OrcaText(msgid)),
        key = "",
        text = "",
        icon = "",
        expanded = expanded,
    )

    private fun param(parent: Int, key: String, type: GcodePlaceholderType) = GcodePlaceholder(
        parent = parent,
        type = type,
        label = emptyList(),
        key = key,
        text = if (type == GcodePlaceholderType.VECTOR) "$key[]" else key,
        icon = "custom-gcode_single",
        expanded = false,
    )
}
