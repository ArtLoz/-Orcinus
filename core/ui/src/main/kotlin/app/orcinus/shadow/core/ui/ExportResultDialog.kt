package app.orcinus.shadow.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString

/** Whether a file reached the document or folder the user picked: [title] tells which export, [success] its success. */
@Composable
fun ExportResultDialog(title: String, written: Boolean, success: String, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(title, style = OrcaTheme.typography.head16) },
        text = {
            Text(
                text = if (written) success else stringResource(R.string.gcode_save_failed),
                color = if (written) colors.text else colors.error,
                style = OrcaTheme.typography.body14,
            )
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
