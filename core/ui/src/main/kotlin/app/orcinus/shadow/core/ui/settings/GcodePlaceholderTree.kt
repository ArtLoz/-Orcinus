package app.orcinus.shadow.core.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.orcinus.shadow.core.model.GcodePlaceholder
import app.orcinus.shadow.core.model.GcodePlaceholderType

/**
 * The list of EditGCodeDialog (ParamsModel and ParamsNode): the groups,
 * subgroups and placeholders the engine listed, what a search leaves of them,
 * and how a placeholder goes into the G-code.
 */
@Immutable
class GcodePlaceholderTree(val nodes: List<GcodePlaceholder>) {
    private val children: Map<Int, List<Int>> = nodes.indices.groupBy { nodes[it].parent }

    /** The groups the dialog opens with expanded. */
    val initiallyExpanded: Set<Int> = nodes.indices.filter { nodes[it].expanded }.toSet()

    /** A row of the list: the node, how deep it sits, and the part of its text a search matched. */
    data class Row(val index: Int, val depth: Int, val highlight: IntRange?)

    /**
     * The rows the list shows. Without a query, the children of the [expanded]
     * groups; with one, ParamsModel::RefreshSearch(): the placeholders whose
     * text holds the query and the groups that hold one of them, all expanded.
     */
    fun rows(expanded: Set<Int>, query: String): List<Row> {
        val search = query.lowercase()
        val matches = if (search.isEmpty()) null else search(search)
        val rows = mutableListOf<Row>()
        fun visit(index: Int, depth: Int) {
            if (matches != null && index !in matches) return
            rows += Row(index, depth, matches?.get(index))
            if (matches != null || index in expanded) {
                children[index].orEmpty().forEach { visit(it, depth + 1) }
            }
        }
        children[-1].orEmpty().forEach { visit(it, 0) }
        return rows
    }

    fun isGroup(index: Int): Boolean = nodes[index].type == GcodePlaceholderType.GROUP

    /**
     * Whether the node is in the "Presets" group, whose definitions win over
     * the placeholders of the same name (EditGCodeDialog::selection_changed).
     */
    fun inPresets(index: Int): Boolean {
        var top = index
        while (nodes[top].parent >= 0) top = nodes[top].parent
        return nodes[top].label.firstOrNull()?.msgid == PRESETS
    }

    /**
     * ParamsNode::RefreshSearch(): the enabled nodes, with the highlighted part
     * of a matching placeholder. A group is enabled when one of its children is.
     */
    private fun search(search: String): Map<Int, IntRange?> {
        val enabled = mutableMapOf<Int, IntRange?>()
        fun refresh(index: Int): Boolean {
            val anyChild = children[index].orEmpty().map(::refresh).any { it }
            if (anyChild) {
                enabled[index] = null
                return true
            }
            val node = nodes[index]
            val position = node.text.indexOf(search)
            if (node.type != GcodePlaceholderType.GROUP && position >= 0) {
                enabled[index] = position until position + search.length
                return true
            }
            return false
        }
        children[-1].orEmpty().forEach { refresh(it) }
        return enabled
    }

    companion object {
        /** The msgid of the group of the presets' settings. */
        const val PRESETS = "Presets"

        /**
         * EditGCodeDialog::add_selected_value_to_gcode(): the placeholder's
         * text goes where the cursor is, on a new line when the cursor is at
         * the end of the G-code. The cursor then stands between the brackets
         * of a vector, or selects the index "current_extruder" of a filament
         * vector.
         */
        fun insert(gcode: TextFieldValue, value: String): TextFieldValue {
            if (value.isEmpty()) return gcode
            val text = gcode.text
            val selection = gcode.selection
            val atEnd = selection.collapsed && selection.start == text.length
            val written = if (atEnd) "\n" + value else value
            val start = selection.min
            val result = text.substring(0, start) + written + text.substring(selection.max)
            val newPos = start + written.length
            val cursor = when {
                !value.endsWith("]") -> TextRange(newPos)
                value.length >= 2 && value[value.length - 2] == '[' -> TextRange(newPos - 1)
                else -> TextRange(newPos - CURRENT_EXTRUDER.length - 1, newPos - 1)
            }
            return TextFieldValue(result, cursor)
        }

        private const val CURRENT_EXTRUDER = "current_extruder"
    }
}
