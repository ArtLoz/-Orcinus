package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * MsgUpdateConfig: the configuration packages the profile updater offers,
 * each vendor with its version, and their changelogs under them. OK installs
 * them ([onAnswer] true); Cancel, or Back, leaves them. The desktop app's
 * "Description:" row shows nothing for an update of the cache, whose
 * description it does not pass, and is left out.
 */
@Composable
fun ProfileUpdateDialog(updates: List<ProfileUpdate>, onAnswer: (install: Boolean) -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(orcaString("Configuration update"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("A new configuration package is available. Do you want to install it?"), style = OrcaTheme.typography.head14)
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    updates.forEach { update ->
                        Row(horizontalArrangement = Arrangement.spacedBy(15.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(update.vendor, style = OrcaTheme.typography.body13)
                            Text(update.version, style = OrcaTheme.typography.body13)
                        }
                    }
                    // The changelog of every update, one after another, below the versions.
                    val changelog = updates.joinToString("") { it.changelog + "\n" }.trimEnd()
                    if (changelog.isNotEmpty()) {
                        Text(changelog, color = colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        },
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onAnswer(true) }) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = { onAnswer(false) }, style = OrcaButtonStyle.Regular) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
