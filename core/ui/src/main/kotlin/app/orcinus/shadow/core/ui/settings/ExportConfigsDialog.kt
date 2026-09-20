package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ConfigExportEntry
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * OrcaSlicer's ExportConfigsDialog, which its Export Preset Bundle opens: what
 * to write — a printer with everything it prints with, a filament, or the
 * presets of one kind as an archive — and which printers or filaments go into
 * it. The folder is asked for afterwards, as the desktop app asks for it when
 * the dialog is accepted.
 */
@Composable
fun ExportConfigsDialog(
    loadOptions: suspend (ConfigExportKind) -> ConfigExportOptionsOutcome,
    onExport: (ConfigExportKind, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var kind by rememberSaveable { mutableStateOf(ConfigExportKind.PRINTER_BUNDLE) }
    var outcome by remember { mutableStateOf<ConfigExportOptionsOutcome?>(null) }
    LaunchedEffect(kind) {
        outcome = null
        outcome = loadOptions(kind)
    }
    val entries = (outcome as? ConfigExportOptionsOutcome.Success)?.entries.orEmpty()
    // The check boxes start empty, as the dialog opens them.
    val chosen = remember(entries) { entries.map { false }.toMutableStateList() }

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
                title = orcaString("Export Preset Bundle"),
                // has_check_box_selected(): "Please select at least one printer or filament."
                okEnabled = chosen.any { it },
                onCancel = onDismiss,
                onOk = { onExport(kind, entries.filterIndexed { index, _ -> chosen.getOrElse(index) { false } }.map(ConfigExportEntry::name)) },
            )
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                item(key = "type") {
                    Text(
                        text = orcaString("Please select a type you want to export"),
                        color = colors.text,
                        style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(ConfigExportKind.entries.size, key = { "kind:${ConfigExportKind.entries[it]}" }) { index ->
                    val option = ConfigExportKind.entries[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == kind, role = Role.RadioButton) { kind = option }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrcaRadioButton(selected = option == kind, onClick = null)
                        Text(
                            text = orcaString(option.label),
                            color = colors.text,
                            style = OrcaTheme.typography.body13,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                item(key = "note") {
                    HorizontalDivider(color = colors.separator, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    val note = (outcome as? ConfigExportOptionsOutcome.Success)?.note.orEmpty()
                    if (note.isNotEmpty()) {
                        Text(
                            text = orcaString(note),
                            color = colors.textSide,
                            style = OrcaTheme.typography.body12,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                when (val current = outcome) {
                    null -> item(key = "loading") {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                    }
                    is ConfigExportOptionsOutcome.Failure -> item(key = "problem") {
                        Text(current.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                    }
                    is ConfigExportOptionsOutcome.Success -> if (current.entries.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.config_export_empty),
                                color = colors.textSide,
                                style = OrcaTheme.typography.body13,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                items(entries.size, key = { "entry:${entries[it].name}" }) { index ->
                    val entry = entries[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = chosen.getOrElse(index) { false }, role = Role.Checkbox) { checked ->
                                if (index in chosen.indices) chosen[index] = checked
                            }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrcaCheckBox(checked = chosen.getOrElse(index) { false }, onCheckedChange = null)
                        Text(
                            text = entry.name,
                            color = colors.text,
                            style = OrcaTheme.typography.body13,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        )
                        Text(
                            text = entry.count.toString(),
                            color = colors.textSide,
                            style = OrcaTheme.typography.body12,
                        )
                    }
                }
            }
        }
    }
}

/** The label of the radio button, as the dialog names the export. */
private val ConfigExportKind.label: String
    get() = when (this) {
        ConfigExportKind.PRINTER_BUNDLE -> "Printer config bundle(.orca_printer)"
        ConfigExportKind.FILAMENT_BUNDLE -> "Filament bundle(.orca_filament)"
        ConfigExportKind.PRINTER_PRESETS -> "Printer presets(.zip)"
        ConfigExportKind.FILAMENT_PRESETS -> "Filament presets(.zip)"
        ConfigExportKind.PROCESS_PRESETS -> "Process presets(.zip)"
    }
