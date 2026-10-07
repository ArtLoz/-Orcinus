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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.FilamentPresetChoice
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet

/**
 * OrcaSlicer's CreateFilamentPresetDialog: a filament of the user's own, made
 * from one that is installed. The vendor, the type and the serial become its
 * name, and it is created for every printer whose preset is picked — either the
 * presets of a filament ("Create Based on Current Filament") or, for each
 * printer, a preset of that type it chooses ("Copy Current Filament Preset").
 */
@Composable
fun CreateFilamentDialog(
    loadOptions: suspend (type: String, baseFilament: String) -> CreateFilamentOptionsOutcome,
    onCreate: (CreateFilamentRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var vendor by rememberSaveable { mutableStateOf("") }
    var customVendor by rememberSaveable { mutableStateOf(false) }
    var customVendorName by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var serial by rememberSaveable { mutableStateOf("") }
    // The two radio buttons: from a filament, or from any preset of the type.
    var copyPreset by rememberSaveable { mutableStateOf(false) }
    var baseFilament by rememberSaveable { mutableStateOf("") }
    var outcome by remember { mutableStateOf<CreateFilamentOptionsOutcome?>(null) }
    var choosing by remember { mutableStateOf<FilamentChoice?>(null) }
    LaunchedEffect(type, baseFilament) {
        outcome = loadOptions(type, baseFilament)
    }
    val options = outcome as? CreateFilamentOptionsOutcome.Success
    val presets = options?.presets.orEmpty()
    val chosen = remember(presets) { presets.map { false }.toMutableStateList() }
    // create_select_filament_preset_checkbox(): every printer with its check
    // box and the preset its combo box selected, which checks the box.
    val copyPresets = options?.copyPresets.orEmpty()
    val copyPrinters = remember(copyPresets) { copyPresets.map(FilamentPresetChoice::printer).distinct() }
    val copyChecked = remember(copyPresets) { mutableStateMapOf<String, Boolean>() }
    val copyChosen = remember(copyPresets) { mutableStateMapOf<String, String>() }
    var choosingCopyFor by remember { mutableStateOf<String?>(null) }
    val requested = if (copyPreset) {
        copyPrinters.filter { copyChecked[it] == true }.mapNotNull { printer -> copyChosen[printer]?.let { FilamentPresetChoice(printer, it) } }
    } else {
        presets.filterIndexed { index, _ -> chosen.getOrElse(index) { false } }
    }
    val vendorName = if (customVendor) customVendorName else vendor

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
                title = orcaString("Create Filament"),
                okEnabled = vendorName.isNotBlank() && type.isNotEmpty() && serial.isNotBlank() && requested.isNotEmpty(),
                onCancel = onDismiss,
                onOk = {
                    onCreate(
                        CreateFilamentRequest(
                            vendor = vendorName.trim(),
                            customVendor = customVendor,
                            type = type,
                            serial = serial.trim(),
                            presets = requested,
                        ),
                    )
                },
                okText = orcaString("Create"),
            )
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                item(key = "basic") {
                    SectionTitle(orcaString("Basic Information"))
                    // The vendor from the list, or the one the user writes.
                    FieldRow(orcaString("Vendor")) {
                        if (customVendor) {
                            OrcaTextField(
                                value = customVendorName,
                                onValueChange = { customVendorName = it },
                                hint = orcaString("Input Custom Vendor"),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            OrcaComboField(
                                text = vendor.ifEmpty { orcaString("Select Vendor") },
                                onClick = { choosing = FilamentChoice.VENDOR },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = customVendor, role = Role.Checkbox) { customVendor = it }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrcaCheckBox(checked = customVendor, onCheckedChange = null)
                        Text(
                            text = orcaString("Can't find vendor I want"),
                            color = colors.text,
                            style = OrcaTheme.typography.body13,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    FieldRow(orcaString("Type")) {
                        OrcaComboField(
                            text = type.ifEmpty { orcaString("Select Type") },
                            onClick = { choosing = FilamentChoice.TYPE },
                        )
                    }
                    FieldRow(orcaString("Serial")) {
                        OrcaTextField(
                            value = serial,
                            onValueChange = { serial = it },
                            hint = orcaString("e.g. Basic, Matte, Silk, Marble"),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(key = "presets") {
                    SectionTitle(orcaString("Add Filament Preset under this filament"))
                    RadioLine(orcaString("Create Based on Current Filament"), selected = !copyPreset) { copyPreset = false }
                    if (!copyPreset) {
                        FieldRow(orcaString("Filament Preset")) {
                            OrcaComboField(
                                text = baseFilament.ifEmpty { orcaString("Select Filament Preset") },
                                enabled = type.isNotEmpty(),
                                onClick = { choosing = FilamentChoice.BASE_FILAMENT },
                            )
                        }
                    }
                    RadioLine(orcaString("Copy Current Filament Preset "), selected = copyPreset) { copyPreset = true }
                    // select_curr_radiobox(): m_filament_preset_text of either way.
                    Text(
                        text = if (copyPreset) {
                            orcaString(
                                "We would rename the presets as \"Vendor Type Serial @printer you selected\".\nTo add preset for more printers, please go to printer selection",
                            )
                        } else {
                            orcaString("We could create the filament presets for your following printer:")
                        },
                        color = colors.textSide,
                        style = OrcaTheme.typography.body12,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (outcome == null) {
                    item(key = "loading") {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                    }
                }
                (outcome as? CreateFilamentOptionsOutcome.Failure)?.let { failure ->
                    item(key = "problem") {
                        Text(failure.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                    }
                }
                if (copyPreset) {
                    items(copyPrinters.size, key = { "printer:${copyPrinters[it]}" }) { index ->
                        val printer = copyPrinters[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OrcaCheckBox(checked = copyChecked[printer] == true, onCheckedChange = { copyChecked[printer] = it })
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp),
                            ) {
                                Text(printer, color = colors.text, style = OrcaTheme.typography.body13)
                                OrcaComboField(
                                    text = copyChosen[printer] ?: orcaString("Select filament preset"),
                                    onClick = { choosingCopyFor = printer },
                                )
                            }
                        }
                    }
                }
                items(if (copyPreset) 0 else presets.size, key = { "preset:${presets[it].printer}:${presets[it].preset}" }) { index ->
                    val choice = presets[index]
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
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(choice.printer, color = colors.text, style = OrcaTheme.typography.body13)
                            Text(choice.preset, color = colors.textSide, style = OrcaTheme.typography.body12)
                        }
                    }
                }
            }
        }
    }

    choosingCopyFor?.let { printer ->
        ChoiceListSheet(
            title = printer,
            items = copyPresets.filter { it.printer == printer }.map(FilamentPresetChoice::preset),
            onDismiss = { choosingCopyFor = null },
            onChoose = { picked ->
                choosingCopyFor = null
                copyChosen[printer] = picked
                copyChecked[printer] = true
            },
        )
    }
    choosing?.let { which ->
        val items = when (which) {
            FilamentChoice.VENDOR -> options?.vendors.orEmpty()
            FilamentChoice.TYPE -> options?.types.orEmpty()
            FilamentChoice.BASE_FILAMENT -> options?.baseFilaments.orEmpty()
        }
        ChoiceListSheet(
            title = when (which) {
                FilamentChoice.VENDOR -> orcaString("Vendor")
                FilamentChoice.TYPE -> orcaString("Type")
                FilamentChoice.BASE_FILAMENT -> orcaString("Filament Preset")
            },
            items = items,
            onDismiss = { choosing = null },
            onChoose = { picked ->
                choosing = null
                when (which) {
                    FilamentChoice.VENDOR -> vendor = picked
                    FilamentChoice.TYPE -> {
                        type = picked
                        baseFilament = ""
                    }
                    FilamentChoice.BASE_FILAMENT -> baseFilament = picked
                }
            },
        )
    }
}

/** Which of the dialog's lists is open. */
private enum class FilamentChoice { VENDOR, TYPE, BASE_FILAMENT }

/** A caption of the dialog ("Basic Information"). */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.head15,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A line of the dialog: its label over the field, as a phone lays them out. */
@Composable
private fun FieldRow(label: String, field: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = label,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        field()
    }
}

/** One of the two radio buttons of the dialog. */
@Composable
private fun RadioLine(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaRadioButton(selected = selected, onClick = null)
        Text(
            text = text,
            color = OrcaTheme.colors.text,
            style = OrcaTheme.typography.body13.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
