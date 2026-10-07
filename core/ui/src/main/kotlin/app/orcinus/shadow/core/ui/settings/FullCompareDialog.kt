package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * The values of DiffViewCtrl's rows (UnsavedChangesDialog.cpp): one too long
 * for its row is cut short there, and the row opens FullCompareDialog on it.
 */
internal object FullCompare {
    /** get_short_string()'s max_len. */
    private const val MAX_LENGTH = 30

    /**
     * DiffViewCtrl::get_short_string(): a value of several lines, or of 30
     * characters or more, is cut at its first line or its 30th character, and
     * "..." follows; one that starts with "#" (a colour) stays whole.
     */
    fun shortValue(value: String): String {
        if (value.isEmpty() || value.startsWith("#") || ('\n' !in value && value.length < MAX_LENGTH)) return value
        val newline = value.indexOf('\n')
        return value.take(if (newline in 0 until MAX_LENGTH) newline else MAX_LENGTH) + "..."
    }

    /** DiffViewCtrl::Append()'s is_long: the row cut either value short. */
    fun isLong(oldValue: String, newValue: String): Boolean = shortValue(oldValue) != oldValue || shortValue(newValue) != newValue

    /**
     * FullCompareDialog's add_value(): the lines of [value] that [other] has not,
     * or its words when it is one line, each where it first stands in [value].
     */
    fun marks(value: String, other: String): List<IntRange> = (tokens(value) - tokens(other)).mapNotNull { token ->
        value.indexOf(token).takeIf { it >= 0 }?.let { it until it + token.length }
    }

    /** FullCompareDialog's get_set_from_val(). */
    private fun tokens(value: String): Set<String> =
        (if ('\n' in value) value else value.replace(' ', '\n')).split('\n').filter(String::isNotEmpty).toSet()
}

/**
 * FullCompareDialog: the setting whose value a row cut short, with the value
 * before and after it whole under the headers of their columns. What one has
 * that the other has not is bold, and the new value is in the colour of a
 * modified one. The desktop dialog sets the two side by side in read-only
 * fields; a phone sets one over the other, and either can be copied.
 */
@Composable
internal fun FullCompareDialog(
    optionName: String,
    oldValue: String,
    newValue: String,
    oldHeader: String,
    newHeader: String,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(optionName, style = OrcaTheme.typography.head16) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FullValue(oldHeader, oldValue, FullCompare.marks(oldValue, newValue), colors.text)
                FullValue(newHeader, newValue, FullCompare.marks(newValue, oldValue), colors.labelModified)
            }
        },
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

@Composable
private fun ColumnScope.FullValue(header: String, value: String, marks: List<IntRange>, color: Color) {
    Text(header, style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold))
    SelectionContainer(Modifier.weight(1f, fill = false)) {
        Text(
            text = buildAnnotatedString {
                append(value)
                marks.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.first, it.last + 1) }
            },
            color = color,
            style = OrcaTheme.typography.body13,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .border(1.dp, OrcaTheme.colors.border, OrcaTheme.shapes.control)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        )
    }
}
