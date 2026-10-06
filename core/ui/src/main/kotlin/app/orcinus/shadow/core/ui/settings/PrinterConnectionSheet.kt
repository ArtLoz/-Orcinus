package app.orcinus.shadow.core.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BonjourReply
import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.CrealityHost
import app.orcinus.shadow.core.model.ElegooOptions
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlashforgeDiscoveryOutcome
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.HostStorageOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObicoHost
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameCheck
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNameValidation
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostType
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterConnection
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.SentFilament
import app.orcinus.shadow.core.model.defaultSlotFor
import app.orcinus.shadow.core.model.withHostDefaults
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * OrcaSlicer's PhysicalPrinterDialog, which the Connection button of the
 * sidebar's printer opens. Its settings are the host's on the edited printer
 * preset (m_config is that preset's configuration), and OK saves the preset
 * under the name the dialog asks for (Tab::save_preset()): a system preset is
 * saved as a copy of its own, "<name> - Copy". The desktop dialog lays out the
 * whole "Print Host upload" group; a phone shows the fields the hosts the app
 * sends to take — the kind of host, its address and page, its key or login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConnectionSheet(
    load: suspend () -> PrinterConnectionOutcome,
    /** PhysicalPrinterDialog::update_preset_input(), as SavePresetDialog checks a name. */
    checkName: suspend (String) -> PresetNameOutcome,
    onSave: (ModelSettings, name: String) -> Unit,
    onDismiss: () -> Unit,
    /** Its Test button (PrintHost::test). */
    onTest: suspend (PhysicalPrinter) -> PrintHostTestOutcome = { PrintHostTestOutcome.Failure("") },
    /** Its Browse button: BonjourDialog's lookup of OctoPrint's service. */
    lookup: () -> Flow<List<BonjourReply>> = { emptyFlow() },
    /** Its Browse button for Creality's firmware: CrealityDiscoveryDialog's scan. */
    scanCreality: suspend () -> List<CrealityHost> = { emptyList() },
    /** The Refresh button of its printer (update_printers()). */
    loadPrinters: suspend (PhysicalPrinter) -> HostPrintersOutcome = { HostPrintersOutcome.Success(emptyList()) },
    /** Its Browse button for Flashforge: the printers that answer the broadcast. */
    discoverFlashforge: suspend () -> FlashforgeDiscoveryOutcome = { FlashforgeDiscoveryOutcome.Failure("") },
    /** The login of a cloud host outside the app (OAuthDialog), which gets the page to open in the browser. */
    cloudLogin: suspend (PhysicalPrinter, (String) -> Unit) -> CloudLoginOutcome = { _, _ -> CloudLoginOutcome.Failure("") },
    /** is_logged_in() and log_out() of such a host, for the Log Out button. */
    cloudLoggedIn: suspend (PhysicalPrinter) -> Boolean = { false },
    cloudLogOut: suspend (PhysicalPrinter) -> Unit = {},
    /** Why the printers of the local network cannot be reached, when the system says so. */
    notice: String? = null,
    /** The Browse button of the HTTPS CA file: the path of the copy the app keeps. */
    keepCaFile: suspend (ExternalDocumentReference) -> String? = { null },
) {
    val colors = OrcaTheme.colors
    var connection by remember { mutableStateOf<PrinterConnection?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        when (val outcome = load()) {
            is PrinterConnectionOutcome.Success -> connection = outcome.connection
            is PrinterConnectionOutcome.Failure -> problem = outcome.message
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = orcaString("Physical Printer"),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            listOfNotNull(problem, notice).forEach {
                Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val loaded = connection
            if (loaded == null) {
                if (problem == null) CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
            } else {
                ConnectionForm(
                    loaded, checkName, onSave, onTest, lookup, scanCreality, loadPrinters, discoverFlashforge,
                    CloudActions(cloudLogin, cloudLoggedIn, cloudLogOut), keepCaFile,
                )
            }
        }
    }
}

/** The dialog's name of the preset and its "Print Host upload" group, with OK. */
@Composable
private fun ConnectionForm(
    connection: PrinterConnection,
    checkName: suspend (String) -> PresetNameOutcome,
    onSave: (ModelSettings, name: String) -> Unit,
    onTest: suspend (PhysicalPrinter) -> PrintHostTestOutcome,
    lookup: () -> Flow<List<BonjourReply>>,
    scanCreality: suspend () -> List<CrealityHost>,
    loadPrinters: suspend (PhysicalPrinter) -> HostPrintersOutcome,
    discoverFlashforge: suspend () -> FlashforgeDiscoveryOutcome,
    cloud: CloudActions,
    keepCaFile: suspend (ExternalDocumentReference) -> String?,
) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The login outside the app the Test button started (OAuthDialog).
    var authorizing by remember { mutableStateOf<PhysicalPrinter?>(null) }
    // is_logged_in(), read again after a login or a log-out.
    var loginChanges by remember { mutableIntStateOf(0) }
    var confirmingLogOut by remember { mutableStateOf(false) }
    val copy = orcaString("Copy", context = "PresetName")
    var name by rememberSaveable { mutableStateOf(if (connection.saveNameCopySuffix) "${connection.saveName} - $copy" else connection.saveName) }
    // update() runs as the dialog opens: a cloud host fills in its address.
    var settings by remember { mutableStateOf(connection.settings.withHostDefaults()) }
    var validation by remember { mutableStateOf<PresetNameValidation?>(null) }
    var checkedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        delay(NAME_CHECK_DELAY_MILLIS)
        validation = when (val outcome = checkName(name)) {
            is PresetNameOutcome.Success -> outcome.validation
            is PresetNameOutcome.Failure -> PresetNameValidation(PresetNameCheck.INVALID, listOf(OrcaText(outcome.message)))
        }
        checkedName = name
    }
    val current = validation.takeIf { checkedName == name }
    // What the Test button last found out, for the printer it tested; the
    // desktop shows it in a message box.
    var tested: Pair<PhysicalPrinter, PrintHostTestOutcome>? by remember { mutableStateOf(null) }
    var testing by remember { mutableStateOf(false) }
    val printer = PhysicalPrinter(name, settings)
    val types = PrintHostType.entries
    // update_host_type(): the combo box lists every host, with its label translated.
    val typeLabels = types.map { orcaString(it.label) }
    val type = printer.hostType ?: PrintHostType.OCTOPRINT
    // The desktop combo box lists every host; there are too many for a switch.
    var choosingType by remember { mutableStateOf(false) }
    // The lookup the Browse button opened.
    var browsing by remember { mutableStateOf(false) }
    // update_printers(): what the Refresh button found, or why it found nothing.
    var hostPrinters by remember { mutableStateOf<List<String>?>(null) }
    var printersProblem by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // A cloud host whose Test asks for a login first (PrinterCloudAuthDialog), with what the test said.
    var loggingIn by remember { mutableStateOf<Pair<PhysicalPrinter, String>?>(null) }
    // 3DPrinterOS's question before its login, with what the test said.
    var proceeding by remember { mutableStateOf<Pair<PhysicalPrinter, String>?>(null) }

    // m_on_change: update() when the kind of host or of its login changes,
    // update_ports() when the printer of an Obico account does.
    fun set(key: String, value: String) {
        var next = ModelSettings(settings.values + (key to value))
        if (key == "host_type" || key == "printhost_authorization_type") next = next.withHostDefaults()
        if (key == "printhost_port" && PrintHostType.of(next.values["host_type"].orEmpty()) == PrintHostType.OBICO) {
            next = ModelSettings(next.values + ("print_host_webui" to ObicoHost.webUi(next.values["print_host"].orEmpty(), value)))
        }
        settings = next
        // What Test said was about another host; the desktop's message box is gone by now.
        if (key == "host_type" || key == "print_host") tested = null
    }

    // The Browse button's file dialog ("Open CA certificate file").
    val caFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { keepCaFile(ExternalDocumentReference(uri.toString()))?.let { path -> set("printhost_cafile", path) } }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            orcaText(OrcaText("Save %s as", listOf(PresetKind.PRINTER.tabTitle), translateArgs = true)),
            color = colors.text,
            style = OrcaTheme.typography.body14,
            modifier = Modifier.padding(top = 8.dp),
        )
        OrcaTextField(
            value = name,
            onValueChange = { name = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        current?.info?.takeIf { it.isNotEmpty() }?.let { info ->
            // m_valid_label, in the dialog's orange.
            Text(orcaText(info), color = colors.secondary, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            orcaString("Print Host upload"),
            color = colors.textLabel,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(orcaString("Host Type"), color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
            OrcaComboField(text = orcaString(type.label), onClick = { choosingType = true }, modifier = Modifier.width(200.dp))
        }
        Field(orcaString("Hostname, IP or URL"), printer.host, enabled = type.hostEditable) { set("print_host", it.trim()) }
        // update_printhost_buttons(): Browse for a host that finds itself on
        // the network, Test once there is an address.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            if (type.hasAutoDiscovery) {
                OrcaButton(
                    text = orcaString("Browse") + " ...",
                    onClick = { browsing = true },
                    style = OrcaButtonStyle.Regular,
                    icon = DesignR.drawable.orca_printer_host_browser,
                )
            }
            OrcaButton(
                // update_printhost_buttons(): a cloud host logs in with it.
                text = orcaString(if (type.isCloud) "Login/Test" else "Test"),
                onClick = {
                    tested = null
                    testing = true
                    val testedPrinter = printer
                    scope.launch {
                        val outcome = onTest(testedPrinter)
                        // A cloud host that did not answer logs in (Obico's PrinterCloudAuthDialog,
                        // SimplyPrint's OAuthDialog); 3DPrinterOS asks first.
                        if (outcome is PrintHostTestOutcome.Failure && testedPrinter.hostType == PrintHostType.OBICO) {
                            loggingIn = testedPrinter to outcome.message
                        } else if (outcome is PrintHostTestOutcome.Failure && testedPrinter.hostType == PrintHostType.PRINTER_3D_OS) {
                            proceeding = testedPrinter to outcome.message
                        } else if (outcome is PrintHostTestOutcome.Failure && testedPrinter.hostType?.logsInOutside == true) {
                            authorizing = testedPrinter
                        } else {
                            tested = testedPrinter to outcome
                        }
                        testing = false
                    }
                },
                style = OrcaButtonStyle.Regular,
                enabled = printer.host.isNotBlank() && !testing,
                icon = DesignR.drawable.orca_printer_host_test,
            )
            // update_printhost_buttons(): Log Out while the host keeps a login.
            val loggedIn by produceState(false, type, loginChanges) {
                value = type.logsInOutside && cloud.loggedIn(printer)
            }
            if (loggedIn) {
                OrcaButton(
                    text = orcaString("Log Out"),
                    onClick = { confirmingLogOut = true },
                    style = OrcaButtonStyle.Regular,
                )
            }
        }
        tested?.let { (testedPrinter, outcome) ->
            val testedType = testedPrinter.hostType ?: PrintHostType.OCTOPRINT
            Text(
                text = when (outcome) {
                    // get_test_ok_msg() and get_test_failed_msg() of the host.
                    is PrintHostTestOutcome.Success -> orcaString(testedPrinter.testOkMessage ?: testedType.testOkMessage) +
                        // 3DPrinterOS names the account of the session it had before the test.
                        if (testedType == PrintHostType.PRINTER_3D_OS && outcome.description.isNotEmpty()) {
                            orcaString(" Logined as user: ") + outcome.description
                        } else {
                            ""
                        }
                    is PrintHostTestOutcome.Failure -> {
                        val note = testedType.testFailedNote?.let { orcaString(it) }
                        val gap = if (testedType == PrintHostType.FLASHAIR) "\n" else "\n\n"
                        // The host's message as format_error() words it, in the app's language when it is Orca's.
                        val message = if (outcome.text.isEmpty()) outcome.message else orcaText(outcome.text)
                        "${orcaString(testedPrinter.testFailedMessage ?: testedType.testFailedMessage)}: $message" +
                            note?.let { gap + it }.orEmpty()
                    }
                },
                color = if (outcome is PrintHostTestOutcome.Success) colors.text else colors.error,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (type.showsWebUi) {
            Field(orcaString("Device UI"), settings.values["print_host_webui"].orEmpty()) { set("print_host_webui", it.trim()) }
        }
        // update(): a host that takes a login shows the fields for it instead of the key.
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
                    onSelect = { set("printhost_authorization_type", if (it == 1) "user" else "key") },
                )
            }
        }
        if (type.takesUserPassword && printer.usesUserPassword) {
            Field(orcaString("User"), printer.user) { set("printhost_user", it) }
            Field(orcaString("Password"), printer.password) { set("printhost_password", it) }
        } else if (type.showsApiKey) {
            Field(orcaString("API Key / Password"), printer.apiKey) { set("printhost_apikey", it) }
        }
        if (type.takesSerialNumber) {
            Field(orcaString("Serial Number"), settings.values["flashforge_serial_number"].orEmpty()) {
                set("flashforge_serial_number", it.trim())
            }
        }
        if (type.supportsMultiplePrinters) {
            Field(orcaString("Printer"), printer.port) { set("printhost_port", it) }
            // The Refresh button beside it, which lists the server's printers.
            OrcaButton(
                text = orcaString("Refresh") + " ...",
                onClick = {
                    printersProblem = null
                    refreshing = true
                    scope.launch {
                        when (val outcome = loadPrinters(printer)) {
                            is HostPrintersOutcome.Success -> hostPrinters = outcome.printers.takeIf { it.isNotEmpty() }
                            is HostPrintersOutcome.Failure -> printersProblem = outcome.message
                        }
                        refreshing = false
                    }
                },
                style = OrcaButtonStyle.Regular,
                enabled = printer.host.isNotBlank() && !refreshing,
                icon = DesignR.drawable.orca_monitor_signal_strong,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            printersProblem?.let {
                Text(
                    text = orcaString("Connection to printers connected via the print host failed.") + "\n\n" + it,
                    color = colors.error,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        // "HTTPS CA File", which Http::ca_file_supported() offers with curl's
        // OpenSSL as on Android, with its Browse button and the hint under it;
        // SimplyPrint does not use it.
        val caFileEnabled = type != PrintHostType.SIMPLYPRINT
        Field(orcaString("HTTPS CA File"), settings.values["printhost_cafile"].orEmpty(), enabled = caFileEnabled) { set("printhost_cafile", it) }
        OrcaButton(
            text = orcaString("Browse") + " ...",
            onClick = { caFilePicker.launch(arrayOf("*/*")) },
            style = OrcaButtonStyle.Regular,
            enabled = caFileEnabled,
            icon = DesignR.drawable.orca_monitor_signal_strong,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = orcaString("HTTPS CA file is optional. It is only needed if you use HTTPS with a self-signed certificate."),
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OrcaButton(
            text = orcaString("OK"),
            onClick = { onSave(settings, name) },
            enabled = current != null && current.check != PresetNameCheck.INVALID,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
    if (choosingType) {
        ChoiceListSheet(
            title = orcaString("Host Type"),
            items = typeLabels,
            onDismiss = { choosingType = false },
            onChoose = { label ->
                choosingType = false
                types.getOrNull(typeLabels.indexOf(label))?.let { set("host_type", it.key) }
            },
        )
    }
    authorizing?.let { loginPrinter ->
        val tokenAuth = loginPrinter.hostType == PrintHostType.PRINTER_3D_OS
        CloudAuthorizingSheet(
            // TokenAuthDialog is titled 3DPrinterOS; a cancelled one leaves login() nothing to read.
            title = if (tokenAuth) "3DPrinterOS" else orcaString("Login"),
            canceled = if (tokenAuth) "Could not parse server response." else "User canceled.",
            login = { cloud.login(loginPrinter) { url -> openInBrowser(context, url) } },
            onDone = { outcome ->
                authorizing = null
                loginChanges++
                tested = loginPrinter to when (outcome) {
                    CloudLoginOutcome.Success -> PrintHostTestOutcome.Success("")
                    is CloudLoginOutcome.Failure -> PrintHostTestOutcome.Failure(outcome.message)
                }
            },
        )
    }
    if (confirmingLogOut) {
        // print_host_logout: "Are you sure to log out?"
        AlertDialog(
            onDismissRequest = { confirmingLogOut = false },
            confirmButton = {
                OrcaButton(
                    text = orcaString("Yes"),
                    onClick = {
                        confirmingLogOut = false
                        scope.launch {
                            cloud.logOut(printer)
                            loginChanges++
                        }
                    },
                )
            },
            dismissButton = { OrcaButton(orcaString("No"), onClick = { confirmingLogOut = false }, style = OrcaButtonStyle.Regular) },
            text = { Text(orcaString("Are you sure to log out?"), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
    proceeding?.let { (loginPrinter, message) ->
        // "Valid session not detected. Proceed with login to 3DPrinterOS?"; No keeps what the test said.
        AlertDialog(
            onDismissRequest = {
                proceeding = null
                tested = loginPrinter to PrintHostTestOutcome.Failure(message)
            },
            confirmButton = {
                OrcaButton(
                    text = orcaString("Yes"),
                    onClick = {
                        proceeding = null
                        authorizing = loginPrinter
                    },
                )
            },
            dismissButton = {
                OrcaButton(
                    text = orcaString("No"),
                    onClick = {
                        proceeding = null
                        tested = loginPrinter to PrintHostTestOutcome.Failure(message)
                    },
                    style = OrcaButtonStyle.Regular,
                )
            },
            title = { Text(orcaString("Proceed"), style = OrcaTheme.typography.head16) },
            text = { Text(orcaString("Valid session not detected. Proceed with login to 3DPrinterOS?"), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            titleContentColor = colors.text,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
    loggingIn?.let { (loginPrinter, message) ->
        CloudLoginSheet(ObicoHost.loginUrl(loginPrinter.host)) { token ->
            loggingIn = null
            // The key field takes whatever the login gave, even nothing.
            set("printhost_apikey", token)
            tested = loginPrinter to if (token.isNotEmpty()) PrintHostTestOutcome.Success("") else PrintHostTestOutcome.Failure(message)
        }
    }
    hostPrinters?.let { printers ->
        ChoiceListSheet(
            title = orcaString("Printer"),
            items = printers,
            onDismiss = { hostPrinters = null },
            onChoose = { slug ->
                hostPrinters = null
                set("printhost_port", slug)
            },
        )
    }
    if (browsing) {
        // A Creality printer is found by its own scan, which the dialog gives an address of http://<ip>.
        if (type == PrintHostType.FLASHFORGE) {
            // A Flashforge printer answers a broadcast of its own and brings its serial number.
            FlashforgeDiscoverySheet(
                discover = discoverFlashforge,
                onChoose = { found ->
                    browsing = false
                    set("print_host", found.ipAddress)
                    set("flashforge_serial_number", found.serialNumber)
                },
                onDismiss = { browsing = false },
            )
        } else if (type == PrintHostType.CREALITY_PRINT) {
            CrealityDiscoverySheet(
                scan = scanCreality,
                onChoose = { ip ->
                    browsing = false
                    set("print_host", "http://$ip")
                },
                onDismiss = { browsing = false },
            )
        } else {
            BonjourLookupSheet(
                lookup = lookup,
                onChoose = { address ->
                    browsing = false
                    set("print_host", address)
                },
                onDismiss = { browsing = false },
            )
        }
    }
}

/** The login of a cloud host outside the app, with the dialog's words for it. */
internal class CloudActions(
    val login: suspend (PhysicalPrinter, (String) -> Unit) -> CloudLoginOutcome,
    val loggedIn: suspend (PhysicalPrinter) -> Boolean,
    val logOut: suspend (PhysicalPrinter) -> Unit,
)

/** wxLaunchDefaultBrowser(): the page in the browser; nothing without one. */
fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * OAuthDialog: "Authorizing..." with Cancel while the login page is open in
 * the browser and the app waits for it to come back; Cancel stops the wait,
 * which the desktop reports as "User canceled.".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudAuthorizingSheet(
    title: String,
    canceled: String,
    login: suspend () -> CloudLoginOutcome,
    onDone: (CloudLoginOutcome) -> Unit,
) {
    val colors = OrcaTheme.colors
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) { done(login()) }
    ModalBottomSheet(
        onDismissRequest = { done(CloudLoginOutcome.Failure(canceled)) },
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(title, color = colors.text, style = OrcaTheme.typography.head16, modifier = Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(20.dp))
                Text(orcaString("Authorizing..."), color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 8.dp))
            }
            OrcaButton(
                text = orcaString("Cancel"),
                onClick = { done(CloudLoginOutcome.Failure(canceled)) },
                style = OrcaButtonStyle.Regular,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * Plater::send_gcode_legacy(): the G-code of the plate goes to the host of the
 * printer preset, with a switch for starting the print at once
 * (PrintHostPostUploadAction::StartPrint). A preset without a host has
 * nothing to send to; its host is set up with the Connection button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToPrinterSheet(
    load: suspend () -> PrinterConnectionOutcome,
    onSend: (PhysicalPrinter, startPrint: Boolean, options: PrintOptions) -> Unit,
    onDismiss: () -> Unit,
    /** The slots of a printer's material boxes (CrealityPrint::query_boxes_info). */
    loadSlots: suspend (PhysicalPrinter) -> PrinterSlotsOutcome = { PrinterSlotsOutcome.Success(emptyList()) },
    /** The filaments of the plate, which the slots are matched to. */
    filaments: List<SentFilament> = emptyList(),
    /** Why the printers of the local network cannot be reached, when the system says so. */
    notice: String? = null,
    /** The slots of a Flashforge printer's material station (Flashforge::fetch_material_slots). */
    loadFlashforgeSlots: suspend (PhysicalPrinter) -> FlashforgeSlotsOutcome = { FlashforgeSlotsOutcome.Failure("") },
    /** The plate's type as a BedType value, which the Elegoo dialog compares its plate side with (curr_bed_type). */
    plateBedType: Int = 1,
    /** 3DPrinterOS's session check and the cloud's projects and printer types. */
    loadPrinter3dOsLists: suspend (PhysicalPrinter) -> Printer3dOsListsOutcome = { Printer3dOsListsOutcome.Failure("") },
    /** The send dialogs' last choices, which OrcaSlicer.conf keeps in its "recent" section. */
    loadRecent: suspend (List<String>) -> Map<String, String> = { emptyMap() },
    keepRecent: (Map<String, String>) -> Unit = {},
    /** The name the G-code goes by (output_filepath_for_project()), which the upload path ends in. */
    uploadName: String = "plate.gcode",
    /** get_groups() and get_storage() of the host, which the dialog offers. */
    loadGroups: suspend (PhysicalPrinter) -> List<String> = { emptyList() },
    loadStorage: suspend (PhysicalPrinter) -> HostStorageOutcome = { HostStorageOutcome.Success(emptyList(), emptyList()) },
    /** open_device_tab_post_upload, and its keeping when the dialog uploads. */
    switchToDeviceTab: Boolean = false,
    keepSwitchToDeviceTab: (Boolean) -> Unit = {},
) {
    val colors = OrcaTheme.colors
    var printer by remember { mutableStateOf<PhysicalPrinter?>(null) }
    // PrintHostSendDialog: the upload path, the group of a Repetier server and
    // the storage of the host, and the check box of the Device tab.
    var uploadPath by remember { mutableStateOf(uploadName) }
    var validSuffix by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var group by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf(HostStorageOutcome.Success(emptyList(), emptyList())) }
    var storageIndex by remember { mutableIntStateOf(0) }
    var storageFailed by remember { mutableStateOf(false) }
    var switchToDevice by remember { mutableStateOf(switchToDeviceTab) }
    // validate_path(): the question when the name does not end with the G-code's suffix, and what follows a Yes.
    var suffixQuestion by remember { mutableStateOf<(() -> Unit)?>(null) }
    // send_gcode_legacy()'s question before PrusaConnect prints, and what follows its OK.
    var readyQuestion by remember { mutableStateOf<(() -> Unit)?>(null) }
    // use_3mf: the plate goes as a .gcode.3mf.
    var use3mf by remember { mutableStateOf(false) }
    // Preset::get_printer_type(), which ElegooPrintHostSendDialog offers its options by.
    var printerType by remember { mutableStateOf("") }
    var elegoo by remember { mutableStateOf(ElegooOptions()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var startPrint by rememberSaveable { mutableStateOf(false) }
    // A Creality printer asks which slot feeds every filament before it prints.
    var mapping by remember { mutableStateOf(false) }
    // So does a Flashforge printer on its local API, with the options of its dialog.
    var flashforge by remember { mutableStateOf(false) }
    // 3DPrinterOS asks for the cloud's project and printer type.
    var cloudOptions by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        when (val outcome = load()) {
            is PrinterConnectionOutcome.Success -> {
                val connection = outcome.connection
                printer = connection.printer(connection.settings.values["print_host"].orEmpty())
                printerType = connection.printerType
                use3mf = connection.use3mf
                // ElegooPrintHostSendDialog::init(): a Centauri opens with the choices it last uploaded with.
                if (printer?.hostType == PrintHostType.ELEGOO_LINK && printerType in ElegooOptions.PRINTER_TYPES) {
                    val recent = loadRecent(ELEGOO_KEYS)
                    fun recentInt(key: String) = recent[key]?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                    // PrintHostPostUploadAction::StartPrint
                    recentInt(ElegooOptions.UPLOAD_AND_PRINT_KEY)?.let { startPrint = it == 1 }
                    elegoo = elegoo.copy(
                        timeLapse = recentInt(ElegooOptions.TIMELAPSE_KEY)?.let { it != 0 } ?: elegoo.timeLapse,
                        heatedBedLeveling = recentInt(ElegooOptions.HEATED_BED_LEVELING_KEY)?.let { it != 0 } ?: elegoo.heatedBedLeveling,
                        bedType = recentInt(ElegooOptions.BED_TYPE_KEY) ?: elegoo.bedType,
                    )
                }
            }
            is PrinterConnectionOutcome.Failure -> problem = outcome.message
        }
        // Plater::send_gcode_legacy() asks the host's groups and storages before
        // the dialog opens; a storage it cannot offer stops the upload.
        val host = printer ?: return@LaunchedEffect
        if (!host.canSend) return@LaunchedEffect
        groups = loadGroups(host)
        when (val stored = loadStorage(host)) {
            is HostStorageOutcome.Success -> storage = stored
            is HostStorageOutcome.Failure -> {
                problem = stored.message
                storageFailed = true
                return@LaunchedEffect
            }
        }
        // init(): the folder of the last upload in front of the name, the group and the storage it went to.
        val recent = loadRecent(listOf(RECENT_PATH_KEY, RECENT_GROUP_KEY, RECENT_STORAGE_KEY))
        var recentPath = recent[RECENT_PATH_KEY].orEmpty()
        if (recentPath.isNotEmpty() && !recentPath.endsWith('/')) recentPath += '/'
        // default_output_file.replace_extension(".gcode.3mf") for a printer that takes one.
        val name = if (use3mf) uploadName.lastIndexOf('.').let { dot -> if (dot > 0) uploadName.substring(0, dot) else uploadName } + ".gcode.3mf" else uploadName
        uploadPath = recentPath + name
        validSuffix = uploadPath.lastIndexOf('.').takeIf { it >= 0 }?.let { uploadPath.substring(it) }.orEmpty()
        recent[RECENT_GROUP_KEY]?.takeIf { it.isNotEmpty() && groups.isNotEmpty() }?.let { group = it }
        recent[RECENT_STORAGE_KEY]?.takeIf { it.isNotEmpty() && storage.names.size > 1 }?.let { storageIndex = storage.names.indexOf(it) }
    }
    // storage(): the path of the storage chosen, or of the one storage found.
    val storagePath = when {
        storage.names.size > 1 -> storage.paths.getOrNull(storageIndex).orEmpty()
        storage.names.size == 1 -> storage.paths.first()
        else -> ""
    }
    // filename(), group() and storage() go with the upload, and EndModal(wxID_OK) keeps them.
    fun uploading(options: PrintOptions): PrintOptions {
        val folder = uploadPath.lastIndexOf('/').let { slash -> if (slash < 0) "" else uploadPath.substring(0, slash + 1) }
        keepRecent(
            buildMap {
                put(RECENT_PATH_KEY, folder)
                if (groups.isNotEmpty()) put(RECENT_GROUP_KEY, group)
                if (storage.names.size > 1) put(RECENT_STORAGE_KEY, storage.names.getOrNull(storageIndex).orEmpty())
            },
        )
        keepSwitchToDeviceTab(switchToDevice)
        return options.copy(uploadPath = uploadPath, group = group, storage = storagePath, switchToDeviceTab = switchToDevice, use3mf = use3mf)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Send G-code to printer host"),
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
            val host = printer
            if (host == null) {
                if (problem == null) CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                return@Column
            }
            if (storageFailed) return@Column
            if (mapping) {
                SlotMapping(
                    printer = host,
                    filaments = filaments,
                    loadSlots = loadSlots,
                    onBack = { mapping = false },
                    onSend = { options -> onSend(host, startPrint && host.canStartPrint, uploading(options)) },
                )
                return@Column
            }
            if (flashforge) {
                FlashforgeSendPage(
                    printer = host,
                    filaments = filaments,
                    loadSlots = loadFlashforgeSlots,
                    onBack = { flashforge = false },
                    onSend = { options -> onSend(host, startPrint, uploading(PrintOptions(flashforge = options))) },
                    loadRecent = loadRecent,
                    keepRecent = keepRecent,
                )
                return@Column
            }
            if (cloudOptions) {
                Printer3dOsSendPage(
                    printer = host,
                    loadLists = loadPrinter3dOsLists,
                    onBack = { cloudOptions = false },
                    onSend = { choice -> onSend(host, startPrint && host.canStartPrint, uploading(PrintOptions(printer3dOs = choice))) },
                )
                return@Column
            }
            if (!host.canSend) {
                Text(
                    text = stringResource(R.string.printer_host_empty),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            Text(
                text = (host.hostType?.label?.let { orcaString(it) } ?: host.settings.values["host_type"].orEmpty()) +
                    SettingsSearch.SEPARATOR + host.host,
                color = colors.textSide,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            UploadChoices(
                path = uploadPath,
                onPath = { uploadPath = it },
                groups = groups,
                group = group,
                onGroup = { group = it },
                storageNames = storage.names,
                storageIndex = storageIndex,
                onStorage = { storageIndex = it },
                switchToDevice = switchToDevice,
                onSwitchToDevice = { switchToDevice = it },
            )
            // PrintHostSendDialog offers "Upload and Print" to a host that can start a print.
            if (host.canStartPrint) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.printer_host_start),
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    OrcaSwitch(checked = startPrint, onCheckedChange = { startPrint = it })
                }
            }
            val printNow = startPrint && host.canStartPrint
            // ElegooPrintHostSendDialog: a Centauri prints with its own options.
            val elegooOptions = host.hostType == PrintHostType.ELEGOO_LINK && printerType in ElegooOptions.PRINTER_TYPES
            if (elegooOptions && printNow) {
                ElegooPrintOptions(elegoo, plateBedType) { elegoo = it }
            }
            val proceed = {
                    // CrealityPrintHostSendDialog: a Creality printer is told
                    // which slot of its boxes feeds every filament.
                    if (host.hostType == PrintHostType.CREALITY_PRINT) {
                        mapping = true
                    } else if (host.usesFlashforgeLocalApi) {
                        // FlashforgePrintHostSendDialog, for the local API only.
                        flashforge = true
                    } else if (host.hostType == PrintHostType.PRINTER_3D_OS) {
                        // UploadOptionsDialog, which C3DPrinterOS::upload() opens.
                        cloudOptions = true
                    } else {
                        // ElegooPrintHostSendDialog::EndModal(wxID_OK) keeps its choices.
                        if (elegooOptions) {
                            keepRecent(
                                mapOf(
                                    ElegooOptions.UPLOAD_AND_PRINT_KEY to (if (printNow) "1" else "0"),
                                    ElegooOptions.TIMELAPSE_KEY to (if (elegoo.timeLapse) "1" else "0"),
                                    ElegooOptions.HEATED_BED_LEVELING_KEY to (if (elegoo.heatedBedLeveling) "1" else "0"),
                                    ElegooOptions.BED_TYPE_KEY to elegoo.bedType.toString(),
                                ),
                            )
                        }
                        val options = uploading(if (elegooOptions && printNow) PrintOptions(elegoo = elegoo) else PrintOptions())
                        if (host.hostType == PrintHostType.PRUSA_CONNECT && printNow) {
                            readyQuestion = { onSend(host, true, options) }
                        } else {
                            onSend(host, printNow, options)
                        }
                    }
            }
            OrcaButton(
                text = stringResource(R.string.printer_host_send),
                onClick = {
                    if (uploadPath.lowercase().endsWith(validSuffix.lowercase())) proceed() else suffixQuestion = proceed
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
    readyQuestion?.let { proceed ->
        AlertDialog(
            onDismissRequest = { readyQuestion = null },
            confirmButton = {
                OrcaButton(orcaString("OK"), onClick = {
                    readyQuestion = null
                    proceed()
                })
            },
            dismissButton = { OrcaButton(orcaString("Cancel"), onClick = { readyQuestion = null }, style = OrcaButtonStyle.Regular) },
            title = { Text(orcaString("Upload and Print"), style = OrcaTheme.typography.head16) },
            text = { Text(orcaString("Is the printer ready? Is the print sheet in place, empty and clean?"), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            titleContentColor = colors.text,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
    suffixQuestion?.let { proceed ->
        AlertDialog(
            onDismissRequest = { suffixQuestion = null },
            confirmButton = {
                OrcaButton(orcaString("Yes"), onClick = {
                    suffixQuestion = null
                    proceed()
                })
            },
            dismissButton = { OrcaButton(orcaString("No"), onClick = { suffixQuestion = null }, style = OrcaButtonStyle.Regular) },
            text = { Text(orcaString("Upload filename doesn't end with \"%s\". Do you wish to continue?").replace("%s", validSuffix), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/**
 * PrintHostSendDialog::init(): the upload path under "Upload to Printer Host
 * with the following filename:" with its hint, the group of a Repetier server
 * ("#" is its "Default"), the storage — a choice among several, the name of
 * the only one — and "Switch to Device tab after upload.".
 */
@Composable
private fun UploadChoices(
    path: String,
    onPath: (String) -> Unit,
    groups: List<String>,
    group: String,
    onGroup: (String) -> Unit,
    storageNames: List<String>,
    storageIndex: Int,
    onStorage: (Int) -> Unit,
    switchToDevice: Boolean,
    onSwitchToDevice: (Boolean) -> Unit,
) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(orcaString("Upload to Printer Host with the following filename:"), color = colors.text, style = OrcaTheme.typography.body14)
        OrcaTextField(
            value = path,
            onValueChange = onPath,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Text(
            orcaString("Use forward slashes ( / ) as a directory separator if needed."),
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(top = 4.dp),
        )
        val defaultGroup = orcaString("Default")
        if (groups.isNotEmpty()) {
            Text(orcaString("Group"), color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(top = 8.dp))
            OrcaComboBox(
                items = groups,
                // A read-only combo box without the group of a last upload shows none.
                selected = group,
                label = { if (it == REPETIER_DEFAULT_GROUP) defaultGroup else it },
                onSelect = onGroup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        when {
            storageNames.size > 1 -> {
                Text(orcaString("Upload to storage") + ":", color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(top = 8.dp))
                OrcaComboBox(
                    items = storageNames.indices.toList(),
                    selected = storageIndex,
                    // A remembered storage the host no longer offers selects none.
                    label = { storageNames.getOrNull(it).orEmpty() },
                    onSelect = onStorage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            storageNames.size == 1 -> Text(
                orcaString("Upload to storage") + ": " + storageNames.first(),
                color = colors.text,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 8.dp)
                .toggleable(value = switchToDevice, role = Role.Checkbox, onValueChange = onSwitchToDevice),
        ) {
            OrcaCheckBox(checked = switchToDevice, onCheckedChange = null)
            Text(orcaString("Switch to Device tab after upload."), color = colors.text, style = OrcaTheme.typography.body13, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** PrintHostSendDialog's CONFIG_KEY_*: what it keeps in OrcaSlicer.conf's "recent" section. */
private const val RECENT_PATH_KEY = "printhost_path"
private const val RECENT_GROUP_KEY = "printhost_group"
private const val RECENT_STORAGE_KEY = "printhost_storage"

/** Repetier's default model group, which the dialog calls "Default". */
private const val REPETIER_DEFAULT_GROUP = "#"

/** The keys ElegooPrintHostSendDialog::init() reads. */
private val ELEGOO_KEYS = listOf(
    ElegooOptions.UPLOAD_AND_PRINT_KEY,
    ElegooOptions.TIMELAPSE_KEY,
    ElegooOptions.HEATED_BED_LEVELING_KEY,
    ElegooOptions.BED_TYPE_KEY,
)

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

/**
 * ElegooPrintHostSendDialog's options under "Upload and Print": time-lapse,
 * heated bed leveling, the side of the build plate, and the warning when that
 * side is not the plate type the file was sliced for.
 */
@Composable
private fun ElegooPrintOptions(options: ElegooOptions, plateBedType: Int, onChange: (ElegooOptions) -> Unit) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(start = 32.dp, end = 16.dp)) {
        listOf(
            orcaString("Time-lapse") to options.timeLapse,
            orcaString("Heated Bed Leveling") to options.heatedBedLeveling,
        ).forEachIndexed { index, (label, checked) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(label, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
                OrcaSwitch(
                    checked = checked,
                    onCheckedChange = { value ->
                        onChange(if (index == 0) options.copy(timeLapse = value) else options.copy(heatedBedLeveling = value))
                    },
                )
            }
        }
        listOf(
            ElegooOptions.BED_TYPE_PTE to orcaString("Textured Build Plate (Side A)"),
            ElegooOptions.BED_TYPE_PC to orcaString("Smooth Build Plate (Side B)"),
        ).forEach { (bedType, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                OrcaRadioButton(selected = options.bedType == bedType, onClick = { onChange(options.copy(bedType = bedType)) })
                Text(label, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (options.bedType != plateBedType) {
            Text(
                text = orcaString("The selected bed type does not match the file. Please confirm before starting the print."),
                color = colors.error,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.padding(vertical = 4.dp),
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

@Composable
private fun Field(label: String, value: String, enabled: Boolean = true, onValueChange: (String) -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        OrcaTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.width(200.dp),
        )
    }
}

private const val NAME_CHECK_DELAY_MILLIS = 150L
