package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet

/** What CreatePrinterPresetDialog asks of the app. */
class CustomPrinterActions(
    /** What its pages offer for what has been chosen so far. */
    val options: suspend (vendor: String, nozzle: String, presetVendor: String, printerPreset: String) -> CreatePrinterOptionsOutcome,
    /** Its Create button. */
    val create: (CreatePrinterRequest) -> Unit,
    /** The Yes or No of the question OrcaSlicer asks while it creates. */
    val answer: (Boolean) -> Unit,
    val question: SettingsDialog?,
    /**
     * Whether the dialog is open. It stays open until the printer is created
     * (upstream ends it with EndModal only then), so the Cancel of a question
     * comes back to the pages the user has filled in.
     */
    val creating: Boolean = false,
    val open: () -> Unit = {},
    val close: () -> Unit = {},
) {
    companion object {
        val NONE = CustomPrinterActions(
            options = { _, _, _, _ -> CreatePrinterOptionsOutcome.Failure("") },
            create = {},
            answer = {},
            question = null,
        )
    }
}

/**
 * OrcaSlicer's CreatePrinterPresetDialog: a printer of the user's own. Its
 * first page names the printer — vendor, model, nozzle — and the plate it
 * prints on; its second page picks the vendor preset it is made from and the
 * filament and process presets that come with it.
 */
@Composable
fun CreatePrinterDialog(
    loadOptions: suspend (vendor: String, nozzle: String, presetVendor: String, printerPreset: String) -> CreatePrinterOptionsOutcome,
    onCreate: (CreatePrinterRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var page by rememberSaveable { mutableStateOf(1) }
    var vendor by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var nozzle by rememberSaveable { mutableStateOf("") }
    // The plate the dialog opens with (TextInput "200"), until a preset is chosen.
    var bedX by rememberSaveable { mutableStateOf("200") }
    var bedY by rememberSaveable { mutableStateOf("200") }
    var originX by rememberSaveable { mutableStateOf("0") }
    var originY by rememberSaveable { mutableStateOf("0") }
    var height by rememberSaveable { mutableStateOf("200") }
    var presetVendor by rememberSaveable { mutableStateOf("") }
    var printerPreset by rememberSaveable { mutableStateOf("") }
    var outcome by remember { mutableStateOf<CreatePrinterOptionsOutcome?>(null) }
    var choosing by remember { mutableStateOf<PrinterChoice?>(null) }
    val nozzleUnit = orcaString("mm")
    LaunchedEffect(vendor, nozzle, presetVendor, printerPreset) {
        outcome = loadOptions(vendor, nozzle, presetVendor, printerPreset)
    }
    val options = outcome as? CreatePrinterOptionsOutcome.Success
    // The nozzle the dialog opens with is the first of the list (SetSelection(0)).
    LaunchedEffect(options?.nozzleDiameters) {
        if (nozzle.isEmpty()) {
            nozzle = options?.nozzleDiameters?.firstOrNull().orEmpty()
        }
    }
    // The plate of the chosen printer preset, which the first page then shows.
    LaunchedEffect(printerPreset, options?.printableArea, options?.maxPrintHeight) {
        val area = options?.printableArea.orEmpty()
        if (printerPreset.isNotEmpty() && area.size >= 3) {
            bedX = area.maxOf { it.x }.toInt().toString()
            bedY = area.maxOf { it.y }.toInt().toString()
        }
        val printable = options?.maxPrintHeight ?: 0.0
        if (printerPreset.isNotEmpty() && printable > 0) height = printable.toInt().toString()
    }
    val filaments = options?.filamentPresets.orEmpty()
    val processes = options?.processPresets.orEmpty()
    val chosenFilaments = remember(filaments) { filaments.map { true }.toMutableStateList() }
    val chosenProcesses = remember(processes) { processes.map { true }.toMutableStateList() }

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
                title = orcaString("Create Printer"),
                okEnabled = if (page == 1) {
                    model.isNotEmpty() && nozzle.isNotEmpty() && bedX.toDoubleOrNull() != null && bedY.toDoubleOrNull() != null &&
                        (height.toDoubleOrNull() ?: 0.0) > 0
                } else {
                    printerPreset.isNotEmpty() && chosenFilaments.any { it } && chosenProcesses.any { it }
                },
                onCancel = onDismiss,
                onOk = {
                    if (page == 1) {
                        page = 2
                    } else {
                        val x = bedX.toDoubleOrNull() ?: 0.0
                        val y = bedY.toDoubleOrNull() ?: 0.0
                        val dx = originX.toDoubleOrNull() ?: 0.0
                        val dy = originY.toDoubleOrNull() ?: 0.0
                        onCreate(
                            CreatePrinterRequest(
                                model = model,
                                nozzle = nozzle,
                                // save_printable_area_config(): the origin moves the corners.
                                printableArea = listOf(
                                    Point2(-dx, -dy),
                                    Point2(x - dx, -dy),
                                    Point2(x - dx, y - dy),
                                    Point2(-dx, y - dy),
                                ),
                                maxPrintHeight = height.toDoubleOrNull() ?: 0.0,
                                presetVendor = presetVendor,
                                printerPreset = printerPreset,
                                filamentPresets = filaments.filterIndexed { index, _ -> chosenFilaments.getOrElse(index) { false } },
                                processPresets = processes.filterIndexed { index, _ -> chosenProcesses.getOrElse(index) { false } },
                            ),
                        )
                    }
                },
                okText = if (page == 1) orcaString("Next") else orcaString("Create"),
            )
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                if (page == 1) {
                    item(key = "printer") {
                        SectionTitle(orcaString("Create Printer"))
                        PrinterField(orcaString("Printer")) {
                            OrcaComboField(
                                text = vendor.ifEmpty { orcaString("Select Vendor") },
                                onClick = { choosing = PrinterChoice.VENDOR },
                            )
                        }
                        PrinterField(orcaString("Model")) {
                            OrcaComboField(
                                text = model.ifEmpty { orcaString("Select Model") },
                                enabled = vendor.isNotEmpty(),
                                onClick = { choosing = PrinterChoice.MODEL },
                            )
                        }
                        PrinterField(orcaString("Nozzle Diameter")) {
                            OrcaComboField(
                                text = if (nozzle.isEmpty()) "" else nozzle + " " + orcaString("mm"),
                                onClick = { choosing = PrinterChoice.NOZZLE },
                            )
                        }
                        SectionTitle(orcaString("Printable Space"))
                        SizeRow(orcaString("mm"), bedX, { bedX = it }, bedY, { bedY = it })
                        SizeRow(orcaString("Origin"), originX, { originX = it }, originY, { originY = it })
                        PrinterField(orcaString("Max Print Height")) {
                            OrcaTextField(value = height, onValueChange = { height = it }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    item(key = "preset") {
                        SectionTitle(orcaString("Import Preset"))
                        PrinterField(orcaString("Vendor")) {
                            OrcaComboField(
                                text = presetVendor.ifEmpty { orcaString("Select Vendor") },
                                onClick = { choosing = PrinterChoice.PRESET_VENDOR },
                            )
                        }
                        PrinterField(orcaString("Printer")) {
                            OrcaComboField(
                                text = printerPreset.ifEmpty { orcaString("Select Printer") },
                                enabled = presetVendor.isNotEmpty(),
                                onClick = { choosing = PrinterChoice.PRINTER_PRESET },
                            )
                        }
                        if (outcome == null) {
                            CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                        }
                        (outcome as? CreatePrinterOptionsOutcome.Failure)?.let { failure ->
                            Text(failure.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                        }
                        PresetSection(orcaString("Filament Preset Template"), filaments, chosenFilaments)
                        PresetSection(orcaString("Process Preset Template"), processes, chosenProcesses)
                    }
                }
            }
            if (page == 2) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Start) {
                    OrcaButton(orcaString("Return"), onClick = { page = 1 }, style = OrcaButtonStyle.Regular)
                }
            }
        }
    }

    choosing?.let { which ->
        val items = when (which) {
            PrinterChoice.VENDOR -> options?.vendors.orEmpty()
            PrinterChoice.MODEL -> options?.models.orEmpty()
            // The list reads "0.4 mm", as the desktop combo box does.
            PrinterChoice.NOZZLE -> options?.nozzleDiameters.orEmpty().map { it + " " + nozzleUnit }
            PrinterChoice.PRESET_VENDOR -> options?.presetVendors.orEmpty()
            PrinterChoice.PRINTER_PRESET -> options?.printerPresets.orEmpty()
        }
        ChoiceListSheet(
            title = when (which) {
                PrinterChoice.MODEL -> orcaString("Model")
                PrinterChoice.NOZZLE -> orcaString("Nozzle Diameter")
                PrinterChoice.PRINTER_PRESET -> orcaString("Printer")
                else -> orcaString("Vendor")
            },
            items = items,
            onDismiss = { choosing = null },
            onChoose = { picked ->
                choosing = null
                when (which) {
                    PrinterChoice.VENDOR -> {
                        vendor = picked
                        model = ""
                    }
                    PrinterChoice.MODEL -> model = picked
                    PrinterChoice.NOZZLE -> nozzle = picked.removeSuffix(" " + nozzleUnit)
                    PrinterChoice.PRESET_VENDOR -> {
                        presetVendor = picked
                        printerPreset = ""
                    }
                    PrinterChoice.PRINTER_PRESET -> printerPreset = picked
                }
            },
        )
    }
}

/** Which of the dialog's lists is open. */
private enum class PrinterChoice { VENDOR, MODEL, NOZZLE, PRESET_VENDOR, PRINTER_PRESET }

/** A line of the dialog: its label over the field. */
@Composable
private fun PrinterField(label: String, field: @Composable () -> Unit) {
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

/** The x and y of the plate, or of its origin. */
@Composable
private fun SizeRow(label: String, x: String, onX: (String) -> Unit, y: String, onY: (String) -> Unit) {
    PrinterField(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrcaTextField(value = x, onValueChange = onX, modifier = Modifier.weight(1f))
            Text(
                text = "×",
                color = OrcaTheme.colors.textSide,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OrcaTextField(value = y, onValueChange = onY, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The presets of the second page, which are all picked to begin with, with the
 * "Select All" and "Deselect All" of the desktop dialog over them.
 */
@Composable
private fun PresetSection(title: String, presets: List<String>, chosen: MutableList<Boolean>) {
    if (presets.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = OrcaTheme.colors.text,
            style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        OrcaButton(
            text = orcaString("Select All"),
            onClick = { chosen.indices.forEach { chosen[it] = true } },
            style = OrcaButtonStyle.Regular,
        )
        Spacer(Modifier.width(8.dp))
        OrcaButton(
            text = orcaString("Deselect All"),
            onClick = { chosen.indices.forEach { chosen[it] = false } },
            style = OrcaButtonStyle.Regular,
        )
    }
    presets.forEachIndexed { index, preset ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = chosen.getOrElse(index) { false }, role = Role.Checkbox) { checked ->
                    if (index in chosen.indices) chosen[index] = checked
                }
                .padding(horizontal = 12.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrcaCheckBox(checked = chosen.getOrElse(index) { false }, onCheckedChange = null)
            Text(
                text = preset,
                color = OrcaTheme.colors.text,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
