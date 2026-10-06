package app.orcinus.shadow.core.ui.plate

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * show_error() with a monospaced font, as Plater shows a PlaceholderParserError:
 * "Failed processing of the filename_format template." and the parser's message.
 */
@Composable
fun NameErrorDialog(message: String, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    val first = message.substringBefore('\n')
    val rest = message.substringAfter('\n', "")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString("Error"), style = OrcaTheme.typography.head16) },
        text = {
            Text(
                text = orcaString(first) + if (rest.isEmpty()) "" else "\n" + rest,
                color = colors.text,
                style = OrcaTheme.typography.body13.copy(fontFamily = FontFamily.Monospace),
            )
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
