package app.orcinus.shadow.core.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BedFileOutcome
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet
import kotlinx.coroutines.launch

/** What CreatePrinterPresetDialog asks of the app. */
class CustomPrinterActions(
    /** What its pages offer for what they are filled in with. */
    val options: suspend (CreatePrinterRequest) -> CreatePrinterOptionsOutcome,
    /** The first page's OK, which shows the second page once the first is right. */
    val check: (CreatePrinterRequest) -> Unit,
    /** The second page's Create. */
    val create: (CreatePrinterRequest) -> Unit,
    /** load_texture() (true) and load_model_stl(): the file kept for the printer, or why it is not. */
    val keepBedFile: suspend (ExternalDocumentReference, Boolean) -> BedFileOutcome,
    /** The Yes or No of the question OrcaSlicer asks while it checks or creates. */
    val answer: (Boolean) -> Unit,
    val question: SettingsDialog?,
    /** The dialog's message box, which OK closes. */
    val message: String? = null,
    val dismissMessage: () -> Unit = {},
    /** The page the dialog shows: 1, or 2 once the first page's OK is through. */
    val page: Int = 1,
    /** The second page's Return. */
    val returnToFirstPage: () -> Unit = {},
    /**
     * Whether the dialog is open. It stays open until the printer is created
     * (upstream ends it with EndModal only then), so the Cancel of a question
     * comes back to the pages the user has filled in.
     */
    val creating: Boolean = false,
    /** CreatePresetSuccessfulDialog, once the printer is created. */
    val created: Boolean = false,
    val dismissCreated: () -> Unit = {},
    val open: () -> Unit = {},
    val close: () -> Unit = {},
) {
    companion object {
        val NONE = CustomPrinterActions(
            options = { CreatePrinterOptionsOutcome.Failure("") },
            check = {},
            create = {},
            keepBedFile = { _, _ -> BedFileOutcome.Failure("") },
            answer = {},
            question = null,
        )
    }
}

/**
 * OrcaSlicer's CreatePrinterPresetDialog: a printer of the user's own, or a
 * nozzle for a printer that is installed. Its first page names the printer —
 * a vendor and model chosen or typed, or the installed printer — its nozzle,
 * and the plate it prints on; its second page picks the printer preset it is
 * made from, whether its presets come from the templates or from that
 * printer, and the filament and process presets it gets.
 */
@Composable
fun CreatePrinterDialog(actions: CustomPrinterActions) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    val page = actions.page
    // The first page: the Create Type, the printer, the nozzle, and the
    // printer's plate, with the values the desktop dialog's fields open with.
    var createNozzle by rememberSaveable { mutableStateOf(false) }
    var customPrinter by rememberSaveable { mutableStateOf(false) }
    var vendor by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var customVendor by rememberSaveable { mutableStateOf("") }
    var customModel by rememberSaveable { mutableStateOf("") }
    var existingPrinter by rememberSaveable { mutableStateOf("") }
    var nozzle by rememberSaveable { mutableStateOf("") }
    var customNozzle by rememberSaveable { mutableStateOf(false) }
    var customNozzleDiameter by rememberSaveable { mutableStateOf("") }
    var sizeX by rememberSaveable { mutableStateOf("200") }
    var sizeY by rememberSaveable { mutableStateOf("200") }
    var originX by rememberSaveable { mutableStateOf("0") }
    var originY by rememberSaveable { mutableStateOf("0") }
    var height by rememberSaveable { mutableStateOf("200") }
    // m_custom_texture and m_custom_model, and what stands next to their
    // Load... buttons: the file's name, "Empty", or why the file was not taken.
    var texture by rememberSaveable { mutableStateOf("") }
    var bedModel by rememberSaveable { mutableStateOf("") }
    var textureTip by rememberSaveable { mutableStateOf("") }
    var modelTip by rememberSaveable { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    // The second page.
    var presetVendor by rememberSaveable { mutableStateOf("") }
    var printerPreset by rememberSaveable { mutableStateOf("") }
    var fromTemplate by rememberSaveable { mutableStateOf(true) }
    var outcome by remember { mutableStateOf<CreatePrinterOptionsOutcome?>(null) }
    var loads by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf<PrinterChoice?>(null) }
    val nozzleUnit = orcaString("mm")
    val tooLarge = orcaString("The file exceeds %d MB, please import again.")

    val options = outcome as? CreatePrinterOptionsOutcome.Success
    // data_init(): a printer of several nozzles is not made from the templates.
    val templateAllowed = options?.templateAllowed ?: true
    val filaments = options?.filamentPresets.orEmpty()
    val processes = options?.processPresets.orEmpty()
    // The check boxes of a list update_presets_list() made, none checked.
    val chosenFilaments = remember(loads) { filaments.map { false }.toMutableStateList() }
    val chosenProcesses = remember(loads) { processes.map { false }.toMutableStateList() }
    val request = CreatePrinterRequest(
        createNozzle = createNozzle,
        customPrinter = customPrinter,
        vendor = if (customPrinter) customVendor else vendor,
        model = if (customPrinter) customModel else model,
        existingPrinter = existingPrinter,
        nozzle = nozzle,
        customNozzle = customNozzle,
        customNozzleDiameter = customNozzleDiameter,
        // ToDouble() leaves 0 for a field without a number.
        sizeX = sizeX.toDoubleOrNull() ?: 0.0,
        sizeY = sizeY.toDoubleOrNull() ?: 0.0,
        originX = originX.toDoubleOrNull() ?: 0.0,
        originY = originY.toDoubleOrNull() ?: 0.0,
        maxPrintHeight = height.toDoubleOrNull() ?: 0.0,
        customTexture = texture,
        customModel = bedModel,
        presetVendor = presetVendor,
        printerPreset = printerPreset,
        fromTemplate = fromTemplate && templateAllowed,
        filamentPresets = filaments.filterIndexed { index, _ -> chosenFilaments.getOrElse(index) { false } },
        processPresets = processes.filterIndexed { index, _ -> chosenProcesses.getOrElse(index) { false } },
    )
    LaunchedEffect(vendor, createNozzle, existingPrinter, nozzle, presetVendor, printerPreset, request.fromTemplate) {
        loading = true
        outcome = actions.options(request)
        loads++
        loading = false
    }
    // The nozzle list opens on its first nozzle, and a vendor's model list on
    // its first model (SetSelection(0)).
    LaunchedEffect(options?.nozzleDiameters) {
        if (nozzle.isEmpty()) nozzle = options?.nozzleDiameters?.firstOrNull().orEmpty()
    }
    LaunchedEffect(options?.models) {
        val models = options?.models ?: return@LaunchedEffect
        if (model !in models) model = models.firstOrNull().orEmpty()
    }
    // data_init(): the second page opens on the vendor's printer preset
    // nearest the nozzle, made from the templates when it can be.
    LaunchedEffect(page) {
        if (page == 2) {
            printerPreset = ""
            fromTemplate = true
        }
    }
    LaunchedEffect(page, options?.printerPresets, printerPreset) {
        val presets = options?.printerPresets.orEmpty()
        if (page == 2 && presets.isNotEmpty() && printerPreset !in presets) printerPreset = presets.first()
    }

    val texturePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        texture = ""
        textureTip = ""
        scope.launch {
            when (val kept = actions.keepBedFile(ExternalDocumentReference(uri.toString()), true)) {
                is BedFileOutcome.Kept -> {
                    texture = kept.path
                    textureTip = kept.path.substringAfterLast('/')
                }
                is BedFileOutcome.Failure -> fileError = kept.message
                is BedFileOutcome.TooLarge -> textureTip = tooLarge.replace("%d", kept.limitMb.toString())
            }
        }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        bedModel = ""
        modelTip = ""
        scope.launch {
            when (val kept = actions.keepBedFile(ExternalDocumentReference(uri.toString()), false)) {
                is BedFileOutcome.Kept -> {
                    bedModel = kept.path
                    modelTip = kept.path.substringAfterLast('/')
                }
                is BedFileOutcome.Failure -> fileError = kept.message
                is BedFileOutcome.TooLarge -> modelTip = tooLarge.replace("%d", kept.limitMb.toString())
            }
        }
    }

    Dialog(
        onDismissRequest = actions.close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window),
        ) {
            FullScreenDialogTopBar(
                title = orcaString("Create Printer/Nozzle"),
                okEnabled = true,
                onCancel = actions.close,
                onOk = { if (page == 1) actions.check(request) else actions.create(request) },
                okText = if (page == 1) orcaString("OK") else orcaString("Create"),
            )
            StepSwitch(page)
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                if (page == 1) {
                    item(key = "type") {
                        PrinterField(orcaString("Create Type")) {
                            RadioLine(orcaString("Create Printer"), selected = !createNozzle, onClick = { createNozzle = false })
                            RadioLine(orcaString("Create Nozzle for Existing Printer"), selected = createNozzle, onClick = { createNozzle = true })
                        }
                    }
                    item(key = "printer") {
                        PrinterField(orcaString("Printer")) {
                            if (createNozzle) {
                                OrcaComboField(
                                    text = existingPrinter.ifEmpty { orcaString("Select Printer") },
                                    onClick = { choosing = PrinterChoice.EXISTING_PRINTER },
                                )
                            } else {
                                if (customPrinter) {
                                    OrcaTextField(
                                        value = customVendor,
                                        onValueChange = { customVendor = it.withoutKeys(CANNOT_INPUT) },
                                        hint = orcaString("Input Custom Vendor"),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OrcaTextField(
                                        value = customModel,
                                        onValueChange = { customModel = it.withoutKeys(CANNOT_INPUT) },
                                        hint = orcaString("Input Custom Model"),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    OrcaComboField(
                                        text = vendor.ifEmpty { orcaString("Select Vendor") },
                                        onClick = { choosing = PrinterChoice.VENDOR },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OrcaComboField(
                                        text = model.ifEmpty { orcaString("Select Model") },
                                        onClick = { choosing = PrinterChoice.MODEL },
                                    )
                                }
                                CheckLine(orcaString("Can't find my printer model"), customPrinter) { customPrinter = it }
                            }
                        }
                    }
                    item(key = "nozzle") {
                        PrinterField(orcaString("Nozzle Diameter")) {
                            if (customNozzle) {
                                OrcaTextField(
                                    value = customNozzleDiameter,
                                    // The decimal separators are let through here.
                                    onValueChange = { customNozzleDiameter = it.withoutKeys(CANNOT_INPUT - setOf(',', '.')) },
                                    hint = orcaString("Input Custom Nozzle Diameter"),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                OrcaComboField(
                                    text = if (nozzle.isEmpty()) "" else "$nozzle $nozzleUnit",
                                    onClick = { choosing = PrinterChoice.NOZZLE },
                                )
                            }
                            CheckLine(orcaString("Can't find my nozzle diameter"), customNozzle) { customNozzle = it }
                        }
                    }
                    // m_printer_info_panel, which a nozzle for an installed printer has no use for.
                    if (!createNozzle) {
                        item(key = "plate") {
                            PrinterField(orcaString("Bed Shape")) {
                                Text(orcaString("Rectangle"), color = colors.text, style = OrcaTheme.typography.body14)
                            }
                            PointRow(orcaString("Printable Space"), nozzleUnit, sizeX, { sizeX = it }, sizeY, { sizeY = it })
                            PointRow(orcaString("Origin"), nozzleUnit, originX, { originX = it }, originY, { originY = it })
                            BedFileLine(orcaString("Hot Bed STL"), modelTip, onLoad = { modelPicker.launch(arrayOf("*/*")) })
                            BedFileLine(orcaString("Hot Bed SVG"), textureTip, onLoad = { texturePicker.launch(arrayOf("*/*")) })
                            PrinterField(orcaString("Max Print Height")) {
                                OrcaTextField(
                                    value = height,
                                    onValueChange = { if (it.all(Char::isDigit)) height = it },
                                    unit = nozzleUnit,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                } else {
                    item(key = "preset") {
                        PrinterField(orcaString("Printer Preset")) {
                            // The title upstream gives the two combo boxes.
                            Text(
                                orcaString("Create Based on Current Printer"),
                                color = colors.text,
                                style = OrcaTheme.typography.body13,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            OrcaComboField(
                                text = presetVendor.ifEmpty { orcaString("Select Vendor") },
                                onClick = { choosing = PrinterChoice.PRESET_VENDOR },
                            )
                            Spacer(Modifier.height(6.dp))
                            OrcaComboField(
                                text = printerPreset.ifEmpty { orcaString("Select Model") },
                                onClick = { choosing = PrinterChoice.PRINTER_PRESET },
                            )
                        }
                        PrinterField(orcaString("Presets")) {
                            RadioLine(
                                orcaString("Create from Template"),
                                selected = request.fromTemplate,
                                enabled = templateAllowed,
                                onClick = { fromTemplate = true },
                            )
                            RadioLine(orcaString("Create Based on Current Printer"), selected = !request.fromTemplate, onClick = { fromTemplate = false })
                        }
                        if (loading) {
                            CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                        }
                        val problem = (outcome as? CreatePrinterOptionsOutcome.Failure)?.message ?: options?.message
                        problem?.takeIf { it.isNotEmpty() }?.let { message ->
                            Text(orcaString(message), color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                        }
                        PresetSection(orcaString("Filament Preset Template"), filaments, chosenFilaments)
                        PresetSection(orcaString("Process Preset Template"), processes, chosenProcesses)
                    }
                }
            }
            if (page == 2) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Start) {
                    OrcaButton(orcaString("Return"), onClick = actions.returnToFirstPage, style = OrcaButtonStyle.Regular)
                }
            }
        }
    }

    choosing?.let { which ->
        val items = when (which) {
            PrinterChoice.VENDOR -> options?.vendors.orEmpty()
            PrinterChoice.MODEL -> options?.models.orEmpty()
            PrinterChoice.EXISTING_PRINTER -> options?.existingPrinters.orEmpty()
            // The list reads "0.4 mm", as the desktop combo box does.
            PrinterChoice.NOZZLE -> options?.nozzleDiameters.orEmpty().map { "$it $nozzleUnit" }
            PrinterChoice.PRESET_VENDOR -> options?.presetVendors.orEmpty()
            PrinterChoice.PRINTER_PRESET -> options?.printerPresets.orEmpty()
        }
        ChoiceListSheet(
            title = when (which) {
                PrinterChoice.MODEL, PrinterChoice.PRINTER_PRESET -> orcaString("Model")
                PrinterChoice.NOZZLE -> orcaString("Nozzle Diameter")
                PrinterChoice.EXISTING_PRINTER -> orcaString("Printer")
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
                        // The vendor's choice clears the installed printer (m_select_printer->SetSelection(-1)).
                        existingPrinter = ""
                    }
                    PrinterChoice.MODEL -> model = picked
                    PrinterChoice.EXISTING_PRINTER -> existingPrinter = picked
                    PrinterChoice.NOZZLE -> nozzle = picked.removeSuffix(" $nozzleUnit")
                    PrinterChoice.PRESET_VENDOR -> {
                        presetVendor = picked
                        printerPreset = ""
                    }
                    PrinterChoice.PRINTER_PRESET -> printerPreset = picked
                }
            },
        )
    }

    actions.message?.let { message ->
        CreatePrinterAlert(orcaString("Info"), orcaString(message), onDismiss = actions.dismissMessage)
    }
    // show_error(): a file of another type.
    fileError?.let { message ->
        CreatePrinterAlert(orcaString("Error"), orcaString(message), onDismiss = { fileError = null })
    }
}

/** Which of the dialog's lists is open. */
private enum class PrinterChoice { VENDOR, MODEL, EXISTING_PRINTER, NOZZLE, PRESET_VENDOR, PRINTER_PRESET }

/** cannot_input_key: what a typed vendor, model or nozzle cannot hold. */
private val CANNOT_INPUT = setOf('\t', '\n', '\r', '!', '#', '$', '%', '&', '(', ')', '*', ',', '.', '/', ';', '<', '>', '?', '@', '\\', '^', '_', '|', '~')

private fun String.withoutKeys(keys: Set<Char>): String = filterNot { it in keys }

/** create_step_switch_item(): the two steps, the first done once the second page shows. */
@Composable
private fun StepSwitch(page: Int) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(if (page == 1) DesignR.drawable.orca_step_1 else DesignR.drawable.orca_step_is_ok), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(orcaString("Create Printer"), color = colors.text, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 6.dp))
        Box(
            Modifier
                .width(40.dp)
                .height(1.dp)
                .background(colors.border),
        )
        Image(painterResource(if (page == 1) DesignR.drawable.orca_step_2_ready else DesignR.drawable.orca_step_2), contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(20.dp))
        Text(orcaString("Import Preset"), color = colors.text, style = OrcaTheme.typography.body13, modifier = Modifier.padding(start = 6.dp))
    }
}

/** A line of the dialog: its label over its fields. */
@Composable
private fun PrinterField(label: String, field: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = label,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        field()
    }
}

@Composable
private fun RadioLine(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaRadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(text, color = if (enabled) OrcaTheme.colors.text else OrcaTheme.colors.textDisabled, style = OrcaTheme.typography.body14)
    }
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = null)
        Text(text, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The x and y of the printable space, or of its origin, which take digits only. */
@Composable
private fun PointRow(label: String, unit: String, x: String, onX: (String) -> Unit, y: String, onY: (String) -> Unit) {
    PrinterField(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("X", color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(end = 6.dp))
            OrcaTextField(value = x, onValueChange = { if (it.all(Char::isDigit)) onX(it) }, unit = unit, modifier = Modifier.weight(1f))
            Text("Y", color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 6.dp))
            OrcaTextField(value = y, onValueChange = { if (it.all(Char::isDigit)) onY(it) }, unit = unit, modifier = Modifier.weight(1f))
        }
    }
}

/** "Hot Bed STL" and "Hot Bed SVG": Load..., and the file's name, or "Empty". */
@Composable
private fun BedFileLine(label: String, tip: String, onLoad: () -> Unit) {
    PrinterField(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrcaButton(orcaString("Load..."), onClick = onLoad, style = OrcaButtonStyle.Regular)
            Text(
                text = tip.ifEmpty { orcaString("Empty") },
                color = OrcaTheme.colors.text,
                style = OrcaTheme.typography.body13,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/**
 * The presets of the second page, none checked to begin with, with the
 * "Select All" and "Deselect All" of the desktop dialog under them.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PresetSection(title: String, presets: List<String>, chosen: MutableList<Boolean>) {
    Text(
        text = title,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrcaButton(
            text = orcaString("Select All"),
            onClick = { chosen.indices.forEach { chosen[it] = true } },
            style = OrcaButtonStyle.Regular,
        )
        OrcaButton(
            text = orcaString("Deselect All"),
            onClick = { chosen.indices.forEach { chosen[it] = false } },
            style = OrcaButtonStyle.Regular,
        )
    }
}

@Composable
private fun CreatePrinterAlert(title: String, message: String, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(title, style = OrcaTheme.typography.head16) },
        text = { Text(message.trim(), style = OrcaTheme.typography.body14) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * CreatePresetSuccessfulDialog: the printer or filament is made. A printer's
 * "Printer Setting" opens the printer's settings; a filament's dialog has OK
 * alone.
 */
@Composable
fun CreatePresetSuccessfulDialog(printer: Boolean, onOk: () -> Unit, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(if (printer) orcaString("Printer Setting") else orcaString("OK"), onClick = onOk) },
        dismissButton = if (printer) {
            { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) }
        } else {
            null
        },
        title = { Text(if (printer) orcaString("Printer Created Successfully") else orcaString("Filament Created Successfully"), style = OrcaTheme.typography.head16) },
        text = {
            Row(verticalAlignment = Alignment.Top) {
                Image(painterResource(DesignR.drawable.orca_create_success), contentDescription = null, modifier = Modifier.size(24.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        if (printer) orcaString("Printer Created") else orcaString("Filament Created"),
                        style = OrcaTheme.typography.body14.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        if (printer) {
                            orcaString("Please go to printer settings to edit your presets")
                        } else {
                            orcaString(
                                "Please go to filament setting to edit your presets if you need.\nPlease note that nozzle temperature, hot bed " +
                                    "temperature, and maximum volumetric speed has a significant impact on printing quality. Please set them carefully.",
                            )
                        },
                        style = OrcaTheme.typography.body13,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
