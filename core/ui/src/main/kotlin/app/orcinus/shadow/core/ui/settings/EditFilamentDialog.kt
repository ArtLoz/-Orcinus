package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaFullScreenDialog
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.orcaPickerAnchor
import app.orcinus.shadow.core.designsystem.component.rememberOrcaPickerAnchor
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet

/**
 * OrcaSlicer's EditFilamentPresetDialog: what a filament of the user's own is
 * made of — its vendor, type and serial — and the presets under it, by the
 * printer each of them is for. A preset can be opened on the filament tab or
 * deleted, "+ Add Preset" makes one for another printer, and "Delete" deletes
 * the whole filament; the desktop dialog shows the presets as a tree, a phone
 * as a list. The presets are asked for again whenever [revision] changes.
 */
@Composable
fun EditFilamentDialog(
    filamentId: String,
    loadPresets: suspend (String) -> FilamentPresetsOutcome,
    onEditPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit,
    revision: Int = 0,
    onAddPreset: () -> Unit = {},
    onDeleteFilament: () -> Unit = {},
) {
    val colors = OrcaTheme.colors
    var outcome by remember(filamentId) { mutableStateOf<FilamentPresetsOutcome?>(null) }
    LaunchedEffect(filamentId, revision) {
        outcome = loadPresets(filamentId)
    }
    val filament = (outcome as? FilamentPresetsOutcome.Success)?.filament

    // EditFilamentPresetDialog::EditFilamentPresetDialog(): SetMinSize(FromDIP(600), -1), and
    // its preset tree up to 400 high (update_preset_tree()).
    OrcaFullScreenDialog(onDismissRequest = onDismiss, width = 640.dp, height = 640.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
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
                    .fillMaxWidth(),
            ) {
                item(key = "basic") {
                    SectionTitle(orcaString("Basic Information"))
                    InfoRow(orcaString("Vendor"), filament?.vendor.orEmpty())
                    InfoRow(orcaString("Type"), filament?.type.orEmpty())
                    InfoRow(orcaString("Serial"), filament?.serial.orEmpty())
                    SectionTitle(orcaString("Filament presets under this filament"))
                    // EditFilamentPresetDialog::create_add_filament_btn()
                    OrcaButton(
                        text = orcaString("+ Add Preset"),
                        onClick = onAddPreset,
                        style = OrcaButtonStyle.Regular,
                        enabled = filament != null,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
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
                            contentDescription = orcaString("Edit Preset"),
                            onClick = { onEditPreset(choice.preset) },
                        )
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_delete,
                            contentDescription = orcaString("Delete Preset"),
                            onClick = { onDeletePreset(choice.preset) },
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
            // EditFilamentPresetDialog::create_dialog_buttons(): "Delete" on the left, "OK".
            HorizontalDivider(color = colors.separator, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrcaButton(orcaString("Delete"), onClick = onDeleteFilament, style = OrcaButtonStyle.Alert, enabled = filament != null)
                Spacer(Modifier.weight(1f))
                OrcaButton(orcaString("OK"), onClick = onDismiss)
            }
        }
    }
}

/**
 * CreatePresetForPrinterDialog: a preset of the filament for another printer,
 * copied from a filament preset of the same type that printer can print with.
 * Choosing a printer offers its presets, the first one chosen.
 */
@Composable
fun AddFilamentPresetDialog(
    filamentId: String,
    loadSources: suspend (String) -> FilamentPresetsOutcome,
    onAdd: (printer: String, preset: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var outcome by remember(filamentId) { mutableStateOf<FilamentPresetsOutcome?>(null) }
    LaunchedEffect(filamentId) {
        outcome = loadSources(filamentId)
    }
    val sources = (outcome as? FilamentPresetsOutcome.Success)?.filament?.presets.orEmpty()
    val printers = remember(sources) { sources.map { it.printer }.distinct() }
    var printer by rememberSaveable { mutableStateOf("") }
    var preset by rememberSaveable { mutableStateOf("") }
    val presets = remember(sources, printer) { sources.filter { it.printer == printer }.map { it.preset } }
    var choosingPrinter by remember { mutableStateOf(false) }
    var choosingPreset by remember { mutableStateOf(false) }
    // The combo boxes the lists drop down from on a large window.
    val printerAnchor = rememberOrcaPickerAnchor()
    val presetAnchor = rememberOrcaPickerAnchor()

    // CreatePresetForPrinterDialog: as large as its two combo boxes under their labels.
    OrcaFullScreenDialog(onDismissRequest = onDismiss, width = 480.dp, height = 320.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            FullScreenDialogTopBar(
                title = orcaString("Add Preset"),
                okEnabled = printer.isNotEmpty() && preset.isNotEmpty(),
                onCancel = onDismiss,
                onOk = { onAdd(printer, preset) },
            )
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(orcaString("Add preset for new printer"))
                when (val current = outcome) {
                    null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                    is FilamentPresetsOutcome.Failure ->
                        Text(current.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                    is FilamentPresetsOutcome.Success -> Unit
                }
                Text(orcaString("Printer"), color = colors.textSide, style = OrcaTheme.typography.body12, modifier = Modifier.padding(start = 4.dp))
                OrcaComboField(
                    text = printer,
                    enabled = printers.isNotEmpty(),
                    onClick = { choosingPrinter = true },
                    modifier = Modifier.orcaPickerAnchor(printerAnchor),
                )
                Text(
                    orcaString("Copy preset from filament"),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                OrcaComboField(
                    text = preset,
                    enabled = presets.isNotEmpty(),
                    onClick = { choosingPreset = true },
                    modifier = Modifier.orcaPickerAnchor(presetAnchor),
                )
            }
        }
        // The lists open in the dialog's window, so on a large window they drop
        // down from their combo boxes inside it.
        if (choosingPrinter) {
            ChoiceListSheet(
                title = orcaString("Printer"),
                items = printers,
                anchor = printerAnchor,
                onDismiss = { choosingPrinter = false },
                onChoose = { picked ->
                    choosingPrinter = false
                    printer = picked
                    // The filament combo box lists the printer's presets with the first one selected.
                    preset = sources.firstOrNull { it.printer == picked }?.preset.orEmpty()
                },
            )
        }
        if (choosingPreset) {
            ChoiceListSheet(
                title = orcaString("Copy preset from filament"),
                items = presets,
                anchor = presetAnchor,
                onDismiss = { choosingPreset = false },
                onChoose = { picked ->
                    choosingPreset = false
                    preset = picked
                },
            )
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
