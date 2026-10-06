package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.LoadProgress
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText

/**
 * The ProgressDialog of Plater::priv::load_files() (wxPD_APP_MODAL |
 * wxPD_CAN_ABORT): the file being read and how far the load got. Only its
 * Cancel closes it, which stays disabled once pressed (DisableAbort()) while
 * the load stops.
 */
@Composable
fun LoadProgressDialog(progress: LoadProgress, onCancel: () -> Unit) {
    val colors = OrcaTheme.colors
    var cancelled by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(orcaString("Loading") + "...", style = OrcaTheme.typography.head16) },
        text = {
            Column {
                Text(orcaText(OrcaText("Loading file: %s", listOf(progress.file))), style = OrcaTheme.typography.body14)
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    color = colors.accent,
                    trackColor = colors.accentSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            OrcaButton(
                orcaString("Cancel"),
                onClick = {
                    cancelled = true
                    onCancel()
                },
                style = OrcaButtonStyle.Regular,
                enabled = !cancelled,
            )
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
