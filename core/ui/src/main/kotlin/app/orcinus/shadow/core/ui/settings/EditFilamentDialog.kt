package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.designsystem.R as DesignR

/**
 * OrcaSlicer's EditFilamentPresetDialog: what a filament of the user's own is
 * made of — its vendor, type and serial — and the presets under it, by the
 * printer each of them is for. A preset can be opened on the filament tab or
 * deleted; the desktop dialog shows them as a tree, a phone as a list.
 */
@Composable
fun EditFilamentDialog(
    filamentId: String,
    loadPresets: suspend (String) -> FilamentPresetsOutcome,
    onEditPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var outcome by remember(filamentId) { mutableStateOf<FilamentPresetsOutcome?>(null) }
    // The list is asked for again once a preset was deleted.
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(filamentId, revision) {
        outcome = loadPresets(filamentId)
    }
    val filament = (outcome as? FilamentPresetsOutcome.Success)?.filament

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window),
        ) {
            FullScreenDialogTopBar(
                title = orcaString("Edit Filament"),
                okEnabled = true,
                onCancel = onDismiss,
                onOk = onDismiss,
                showOk = false,
            )
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                item(key = "basic") {
                    SectionTitle(orcaString("Basic Information"))
                    InfoRow(orcaString("Vendor"), filament?.vendor.orEmpty())
                    InfoRow(orcaString("Type"), filament?.type.orEmpty())
                    InfoRow(orcaString("Serial"), filament?.serial.orEmpty())
                    SectionTitle(orcaString("Filament presets under this filament"))
                }
                when (val current = outcome) {
                    null -> item(key = "loading") {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                    }
                    is FilamentPresetsOutcome.Failure -> item(key = "problem") {
                        Text(current.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                    }
                    is FilamentPresetsOutcome.Success -> Unit
                }
                val presets = filament?.presets.orEmpty()
                items(presets.size, key = { "preset:${presets[it].printer}:${presets[it].preset}" }) { index ->
                    val choice = presets[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(choice.printer, color = colors.text, style = OrcaTheme.typography.body13)
                            Text(choice.preset, color = colors.textSide, style = OrcaTheme.typography.body12)
                        }
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_edit,
                            contentDescription = orcaString("Edit preset"),
                            onClick = { onEditPreset(choice.preset) },
                        )
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_delete,
                            contentDescription = orcaString("Delete preset"),
                            onClick = {
                                onDeletePreset(choice.preset)
                                revision++
                            },
                        )
                    }
                    HorizontalDivider(color = colors.separator, thickness = 1.dp)
                }
                if (filament != null && presets.size == 1) {
                    item(key = "note") {
                        Text(
                            text = orcaString(
                                "Note: If the only preset under this filament is deleted, the filament will be deleted after exiting the dialog.",
                            ),
                            color = colors.textSide,
                            style = OrcaTheme.typography.body12,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (filament != null && presets.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.filament_no_presets),
                            color = colors.textSide,
                            style = OrcaTheme.typography.body13,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A line of the filament's basic information: its label and the value. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body13)
    }
}
