package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostType
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.defaultSlotFor
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlinx.coroutines.launch

/**
 * OrcaSlicer's PhysicalPrinterDialog: the printers the app can send G-code to.
 * The desktop dialog edits one printer at a time with the whole host page of
 * the printer tab; on a phone the list and the fields that matter for sending —
 * the kind of host, its address and its key — are on one sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalPrintersSheet(
    load: suspend () -> PhysicalPrintersOutcome,
    onSave: suspend (PhysicalPrinter, renamedFrom: String?) -> PhysicalPrintersOutcome,
    onDelete: suspend (String) -> PhysicalPrintersOutcome,
    onDismiss: () -> Unit,
    /** PhysicalPrinterDialog's Test button (PrintHost::test). */
    onTest: suspend (PhysicalPrinter) -> PrintHostTestOutcome = { PrintHostTestOutcome.Failure("") },
    /** The printer presets a printer can be bound to (PhysicalPrinter::preset_names). */
    loadPresets: suspend () -> List<String> = { emptyList() },
) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    var printers by remember { mutableStateOf<List<PhysicalPrinter>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<PhysicalPrinter?>(null) }
    var renamedFrom by remember { mutableStateOf<String?>(null) }

    fun apply(outcome: PhysicalPrintersOutcome) {
        when (outcome) {
            is PhysicalPrintersOutcome.Success -> {
                printers = outcome.printers
                problem = null
            }
            is PhysicalPrintersOutcome.Failure -> problem = outcome.message
        }
    }

    LaunchedEffect(Unit) { apply(load()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Physical Printer"),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            problem?.let {
                Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val current = editing
            if (current == null) {
                if (printers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.printer_host_empty),
                        color = colors.textSide,
                        style = OrcaTheme.typography.body13,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(printers, key = PhysicalPrinter::name) { printer ->
                            PrinterRow(printer) {
                                renamedFrom = printer.name
                                editing = printer
                            }
                        }
                    }
                }
                OrcaButton(
                    text = stringResource(R.string.printer_host_add),
                    onClick = {
                        renamedFrom = null
                        editing = PhysicalPrinter(name = "", settings = ModelSettings(mapOf("host_type" to PrintHostType.OCTOPRINT.key)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            } else {
                PrinterForm(
                    printer = current,
                    onChange = { editing = it },
                    onCancel = {
                        editing = null
                        renamedFrom = null
                    },
                    onSave = { printer ->
                        val from = renamedFrom?.takeIf { it.isNotBlank() && it != printer.name }
                        editing = null
                        renamedFrom = null
                        // The sheet's own scope: the form is gone by now.
                        scope.launch { apply(onSave(printer, from)) }
                    },
                    onDelete = { name ->
                        editing = null
                        renamedFrom = null
                        scope.launch { apply(onDelete(name)) }
                    },
                    deletable = renamedFrom != null,
                    onTest = onTest,
                    loadPresets = loadPresets,
                )
            }
        }
    }
}

/**
 * The printers a sliced plate can be sent to: the list, a switch for starting
 * the print at once (PrintHostPostUploadAction::StartPrint), and the same
 * editor the printer dialog has for a printer that is not set up yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToPrinterSheet(
    load: suspend () -> PhysicalPrintersOutcome,
    onSave: suspend (PhysicalPrinter, renamedFrom: String?) -> PhysicalPrintersOutcome,
    onDelete: suspend (String) -> PhysicalPrintersOutcome,
    onSend: (PhysicalPrinter, startPrint: Boolean, options: PrintOptions) -> Unit,
    onDismiss: () -> Unit,
    /** The slots of a printer's material boxes (CrealityPrint::query_boxes_info). */
    loadSlots: suspend (PhysicalPrinter) -> PrinterSlotsOutcome = { PrinterSlotsOutcome.Success(emptyList()) },
    /** The filaments of the plate, which the slots are matched to. */
    filaments: List<SentFilament> = emptyList(),
    /** Why the printers of the local network cannot be reached, when the system says so. */
    notice: String? = null,
    /** PhysicalPrinterDialog's Test button (PrintHost::test). */
    onTest: suspend (PhysicalPrinter) -> PrintHostTestOutcome = { PrintHostTestOutcome.Failure("") },
    /** The printer presets a printer can be bound to (PhysicalPrinter::preset_names). */
    loadPresets: suspend () -> List<String> = { emptyList() },
) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    var printers by remember { mutableStateOf<List<PhysicalPrinter>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<PhysicalPrinter?>(null) }
    var renamedFrom by remember { mutableStateOf<String?>(null) }
    var startPrint by rememberSaveable { mutableStateOf(false) }
    // A Creality printer asks which slot feeds every filament before it prints.
    var mapping by remember { mutableStateOf<PhysicalPrinter?>(null) }

    fun apply(outcome: PhysicalPrintersOutcome) {
        when (outcome) {
            is PhysicalPrintersOutcome.Success -> {
                printers = outcome.printers
                problem = null
            }
            is PhysicalPrintersOutcome.Failure -> problem = outcome.message
        }
    }

    LaunchedEffect(Unit) { apply(load()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.printer_host_title),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            problem?.let {
                Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 16.dp))
            }
            notice?.let {
                Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 16.dp))
            }
            mapping?.let { printer ->
                SlotMapping(
                    printer = printer,
                    filaments = filaments,
                    loadSlots = loadSlots,
                    onBack = { mapping = null },
                    onSend = { options -> onSend(printer, startPrint, options) },
                )
                return@Column
            }
            val current = editing
            if (current != null) {
                PrinterForm(
                    printer = current,
                    onChange = { editing = it },
                    onCancel = {
                        editing = null
                        renamedFrom = null
                    },
                    onSave = { printer ->
                        val from = renamedFrom?.takeIf { it.isNotBlank() && it != printer.name }
                        editing = null
                        renamedFrom = null
                        // The sheet's own scope: the form is gone by now.
                        scope.launch { apply(onSave(printer, from)) }
                    },
                    onDelete = { name ->
                        editing = null
                        renamedFrom = null
                        scope.launch { apply(onDelete(name)) }
                    },
                    deletable = renamedFrom != null,
                    onTest = onTest,
                    loadPresets = loadPresets,
                )
                return@Column
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.printer_host_start),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier.weight(1f),
                )
                OrcaSwitch(checked = startPrint, onCheckedChange = { startPrint = it })
            }
            if (printers.isEmpty()) {
                Text(
                    text = stringResource(R.string.printer_host_empty),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(printers, key = PhysicalPrinter::name) { printer ->
                        SendRow(
                            printer = printer,
                            onSend = {
                                // CrealityPrintHostSendDialog: a Creality printer is
                                // told which slot of its boxes feeds every filament.
                                if (printer.hostType == PrintHostType.CREALITY_PRINT) {
                                    mapping = printer
                                } else {
                                    onSend(printer, startPrint, PrintOptions())
                                }
                            },
                            onEdit = {
                                renamedFrom = printer.name
                                editing = printer
                            },
                        )
                    }
                }
            }
            OrcaButton(
                text = stringResource(R.string.printer_host_add),
                onClick = {
                    renamedFrom = null
                    editing = PhysicalPrinter(name = "", settings = ModelSettings(mapOf("host_type" to PrintHostType.OCTOPRINT.key)))
                },
                style = OrcaButtonStyle.Regular,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

/** A filament of the plate as the send dialog lists it: its colour and its type. */
data class SentFilament(val color: String, val type: String)

/**
 * CrealityPrintHostSendDialog: the printer's material boxes are read, and every
 * filament of the plate is fed from the slot the desktop dialog would choose
 * (the same type and colour in a CFS box first), which the user may change,
 * with the switch that has the printer calibrate first (enableSelfTest). A
 * filament fed from the spool holder prints alone, so the other rows lock.
 * When the printer does not report its boxes, the print is sent without a
 * mapping, as the desktop dialog does. OrcaSlicer's Russian catalogue has no
 * words for the dialog's three labels, so they are the app's own: shown in
 * English among the Russian sheet, the calibration switch went unnoticed.
 */
@Composable
private fun SlotMapping(
    printer: PhysicalPrinter,
    filaments: List<SentFilament>,
    loadSlots: suspend (PhysicalPrinter) -> PrinterSlotsOutcome,
    onBack: () -> Unit,
    onSend: (PrintOptions) -> Unit,
) {
    val colors = OrcaTheme.colors
    var outcome by remember(printer) { mutableStateOf<PrinterSlotsOutcome?>(null) }
    var selfTest by rememberSaveable { mutableStateOf(false) }
    var chosen by remember(printer) { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(printer) {
        val answer = loadSlots(printer)
        outcome = answer
        if (answer is PrinterSlotsOutcome.Success) {
            chosen = filaments.mapIndexed { index, filament -> defaultSlotFor(index, filament.color, filament.type, answer.slots) }
        }
    }
    val slots = (outcome as? PrinterSlotsOutcome.Success)?.slots.orEmpty()
    // The row that feeds from the spool holder locks the others.
    val spoolHolderRow = chosen.indexOfFirst { slots.getOrNull(it)?.isSpoolHolder == true }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.printer_host_printer, printer.name),
            color = colors.text,
            style = OrcaTheme.typography.body14,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.printer_host_self_test),
                color = colors.text,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.weight(1f),
            )
            OrcaSwitch(checked = selfTest, onCheckedChange = { selfTest = it })
        }
        when (val answer = outcome) {
            null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
            is PrinterSlotsOutcome.Failure -> Text(answer.message, color = colors.error, style = OrcaTheme.typography.body13)
            is PrinterSlotsOutcome.Success -> if (slots.isNotEmpty() && filaments.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.printer_host_filament_mapping),
                    color = colors.textLabel,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                filaments.forEachIndexed { index, filament ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Swatch(filament.color)
                        Text(
                            text = "${index + 1} (${filament.type.ifEmpty { "?" }})",
                            color = colors.text,
                            style = OrcaTheme.typography.body13,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .width(88.dp),
                        )
                        Text("→", color = colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(end = 8.dp))
                        val selected = chosen.getOrElse(index) { 0 }.coerceIn(0, slots.lastIndex)
                        OrcaComboBox(
                            items = slots.indices.toList(),
                            selected = selected,
                            label = { slots[it].label },
                            onSelect = { pick -> chosen = chosen.mapIndexed { at, value -> if (at == index) pick else value } },
                            enabled = spoolHolderRow < 0 || spoolHolderRow == index,
                            leading = { Swatch(slots[selected].color); Spacer(Modifier.width(8.dp)) },
                            itemLeading = { Swatch(slots[it].color) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            OrcaButton(
                text = orcaString("Cancel"),
                style = OrcaButtonStyle.Regular,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            OrcaButton(
                text = stringResource(R.string.printer_host_send),
                enabled = outcome != null,
                onClick = {
                    val picked = if (spoolHolderRow >= 0) {
                        // The spool holder prints the one filament alone.
                        listOf(slots[chosen[spoolHolderRow]])
                    } else {
                        chosen.mapNotNull(slots::getOrNull)
                    }
                    onSend(PrintOptions(selfTest = selfTest, slots = picked))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A colour square, as the desktop dialog draws a filament or a slot. */
@Composable
private fun Swatch(color: String) {
    val parsed = color.removePrefix("#").take(6).toLongOrNull(16)?.let { Color(0xFF000000L or it) } ?: OrcaTheme.colors.border
    Box(
        Modifier
            .size(16.dp)
            .background(parsed)
            .border(1.dp, OrcaTheme.colors.border),
    )
}

/** One printer the G-code can go to, with the button that sends it. */
@Composable
private fun SendRow(printer: PhysicalPrinter, onSend: () -> Unit, onEdit: () -> Unit) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .orcaClickable(role = Role.Button, onClick = onEdit),
        ) {
            Text(printer.name, color = colors.text, style = OrcaTheme.typography.body14)
            Text(
                text = (printer.hostType?.label ?: printer.settings.values["host_type"].orEmpty()) + SettingsSearch.SEPARATOR + printer.host,
                color = colors.textSide,
                style = OrcaTheme.typography.body12,
            )
            // PhysicalPrinter::preset_names: the presets it prints with.
            if (printer.presetNames.isNotEmpty()) {
                Text(
                    text = printer.presetNames.joinToString(SettingsSearch.SEPARATOR),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body11,
                )
            }
        }
        OrcaButton(text = stringResource(R.string.printer_host_send), onClick = onSend, enabled = printer.canSend)
    }
    HorizontalDivider(color = colors.separator, thickness = 1.dp)
}

/** One printer of the list: its name and where it answers. */
@Composable
private fun PrinterRow(printer: PhysicalPrinter, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .orcaClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(printer.name, color = colors.text, style = OrcaTheme.typography.body14)
        Text(
            text = (printer.hostType?.label ?: printer.settings.values["host_type"].orEmpty()) + SettingsSearch.SEPARATOR + printer.host,
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
        )
        // PhysicalPrinter::preset_names: the printer presets it prints with,
        // which the desktop combo box lists the printer under.
        if (printer.presetNames.isNotEmpty()) {
            Text(
                text = printer.presetNames.joinToString(SettingsSearch.SEPARATOR),
                color = colors.textSide,
                style = OrcaTheme.typography.body11,
            )
        }
    }
    HorizontalDivider(color = colors.separator, thickness = 1.dp)
}

/** The fields of a printer the app can send to: its host, address and key. */
@Composable
private fun PrinterForm(
    printer: PhysicalPrinter,
    onChange: (PhysicalPrinter) -> Unit,
    onCancel: () -> Unit,
    onSave: (PhysicalPrinter) -> Unit,
    onDelete: (String) -> Unit,
    deletable: Boolean,
    onTest: suspend (PhysicalPrinter) -> PrintHostTestOutcome,
    loadPresets: suspend () -> List<String>,
) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    // What the Test button last found out; the desktop shows it in a message box.
    var tested: PrintHostTestOutcome? by remember { mutableStateOf(null) }
    var testing by remember { mutableStateOf(false) }
    // The presets the printer can be bound to, read when the list is opened.
    var presets by remember { mutableStateOf<List<String>>(emptyList()) }
    var choosingPreset by remember { mutableStateOf(false) }
    val types = PrintHostType.entries
    val type = printer.hostType ?: PrintHostType.OCTOPRINT
    // The desktop combo box lists every host; there are too many for a switch.
    var choosingType by remember { mutableStateOf(false) }

    fun withSetting(key: String, value: String) =
        printer.copy(settings = ModelSettings(printer.settings.values + (key to value)))

    Column(Modifier.padding(horizontal = 16.dp)) {
        Field(stringResource(R.string.printer_host_name), printer.name) { onChange(printer.copy(name = it)) }
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(orcaString("Host Type"), color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
            OrcaComboField(text = type.label, onClick = { choosingType = true }, modifier = Modifier.width(200.dp))
        }
        Field(orcaString("Hostname, IP or URL"), printer.host) { onChange(withSetting("print_host", it)) }
        // PhysicalPrinterDialog: a host that takes a login shows the fields for
        // it instead of the key (m_optgroup->show_field).
        if (type.takesUserPassword) {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = orcaString("Authorization Type"),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier.weight(1f),
                )
                OrcaSegmentedSwitch(
                    options = listOf(orcaString("API key"), orcaString("HTTP digest")),
                    selectedIndex = if (printer.usesUserPassword) 1 else 0,
                    onSelect = { onChange(withSetting("printhost_authorization_type", if (it == 1) "user" else "key")) },
                )
            }
        }
        if (type.takesUserPassword && printer.usesUserPassword) {
            Field(orcaString("User"), printer.user) { onChange(withSetting("printhost_user", it)) }
            Field(orcaString("Password"), printer.password) { onChange(withSetting("printhost_password", it)) }
        } else if (type.takesPassword) {
            Field(orcaString("Password"), printer.password) { onChange(withSetting("printhost_password", it)) }
        } else {
            Field(orcaString("API Key / Password"), printer.apiKey) { onChange(withSetting("printhost_apikey", it)) }
        }
        if (type.takesPort) {
            Field(orcaString("Printer"), printer.port) { onChange(withSetting("printhost_port", it)) }
        }
        // PhysicalPrinter::preset_names: the printer preset it prints with,
        // which the desktop combo box lists the printer under.
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.printer_host_preset),
                color = colors.text,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.weight(1f),
            )
            OrcaComboField(
                text = printer.presetNames.firstOrNull().orEmpty(),
                onClick = {
                    scope.launch {
                        presets = loadPresets()
                        choosingPreset = presets.isNotEmpty()
                    }
                },
                modifier = Modifier.width(200.dp),
            )
        }
        // PhysicalPrinterDialog's Test button, which asks the host what it is.
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OrcaButton(
                text = orcaString("Test"),
                onClick = {
                    tested = null
                    testing = true
                    scope.launch {
                        tested = onTest(printer)
                        testing = false
                    }
                },
                style = OrcaButtonStyle.Regular,
                enabled = printer.host.isNotBlank() && !testing,
            )
            tested?.let { outcome ->
                Text(
                    text = when (outcome) {
                        is PrintHostTestOutcome.Success -> stringResource(R.string.printer_host_test_ok)
                        is PrintHostTestOutcome.Failure -> outcome.message
                    },
                    color = if (outcome is PrintHostTestOutcome.Success) colors.text else colors.error,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Row(Modifier.padding(vertical = 12.dp)) {
            OrcaButton(
                text = orcaString("OK"),
                onClick = { onSave(printer) },
                enabled = printer.name.isNotBlank() && printer.host.isNotBlank(),
            )
            OrcaButton(
                text = orcaString("Cancel"),
                onClick = onCancel,
                style = OrcaButtonStyle.Regular,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (deletable) {
                OrcaButton(
                    text = orcaString("Delete"),
                    onClick = { onDelete(printer.name) },
                    style = OrcaButtonStyle.Regular,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    if (choosingType) {
        ChoiceListSheet(
            title = orcaString("Host Type"),
            items = types.map { it.label },
            onDismiss = { choosingType = false },
            onChoose = { label ->
                choosingType = false
                types.firstOrNull { it.label == label }?.let { onChange(withSetting("host_type", it.key)) }
            },
        )
    }
    if (choosingPreset) {
        ChoiceListSheet(
            title = stringResource(R.string.printer_host_preset),
            items = presets,
            onDismiss = { choosingPreset = false },
            onChoose = { preset ->
                choosingPreset = false
                onChange(printer.copy(presetNames = listOf(preset)))
            },
        )
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        OrcaTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.width(200.dp),
        )
    }
}
