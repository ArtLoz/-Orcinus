package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaDialogWidth
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BonjourReply
import app.orcinus.shadow.core.model.CrealityHost
import app.orcinus.shadow.core.model.FlashforgeDiscoveredPrinter
import app.orcinus.shadow.core.model.FlashforgeDiscoveryOutcome
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * BonjourDialog ("Network lookup"), which the Browse button opens for OctoPrint
 * and the hosts built on it: the OctoPrint services of the local network as
 * they answer, each with its address, host name, service name and OctoPrint
 * version, the desktop list's columns. A tap on one takes its address, as the
 * dialog's OK does.
 */
@Composable
internal fun BonjourLookupSheet(lookup: () -> Flow<List<BonjourReply>>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    var replies by remember { mutableStateOf<List<BonjourReply>>(emptyList()) }
    var finished by remember { mutableStateOf(false) }
    // The dialog's timer: one to three dots, a second apart, while it looks.
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(lookup) {
        lookup().collect { replies = it }
        finished = true
    }
    LaunchedEffect(finished) {
        while (!finished) {
            delay(DOTS_MILLIS)
            dots = dots % 3 + 1
        }
    }
    val searching = orcaString("Searching for devices")
    DiscoverySheet(
        title = orcaString("Network lookup"),
        status = if (finished) "$searching: ${orcaString("Finished")}." else searching + ".".repeat(dots),
        busy = !finished,
        onDismiss = onDismiss,
    ) {
        items(replies, key = { Triple(it.ip, it.fullAddress, it.serviceName) }) { reply ->
            val details = listOfNotNull(
                "${orcaString("Hostname")}: ${reply.hostname}",
                "${orcaString("Service name")}: ${reply.serviceName}",
                reply.txtData["version"]?.let { "${orcaString("OctoPrint version")}: $it" },
            )
            DiscoveredRow(reply.fullAddress, details.joinToString(DETAILS_SEPARATOR)) { onChoose(reply.fullAddress) }
        }
    }
}

/**
 * CrealityDiscoveryDialog ("Detect Creality K-series printer"), which the
 * Browse button opens for Creality's firmware: the scan runs at once and again
 * with Scan, and lists every printer by its model, host name and IP. A tap on
 * one takes it, as Use Selected does.
 */
@Composable
internal fun CrealityDiscoverySheet(scan: suspend () -> List<CrealityHost>, onChoose: (ip: String) -> Unit, onDismiss: () -> Unit) {
    var hosts by remember { mutableStateOf<List<CrealityHost>>(emptyList()) }
    // Every press of Scan runs run_discovery() again.
    var round by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    LaunchedEffect(round) {
        scanning = true
        hosts = emptyList()
        hosts = scan()
        scanning = false
    }
    val unknownModel = stringResource(R.string.creality_discovery_unknown_model)
    DiscoverySheet(
        title = stringResource(R.string.creality_discovery_title),
        status = when {
            scanning -> stringResource(R.string.creality_discovery_scanning)
            hosts.isEmpty() -> stringResource(R.string.creality_discovery_none)
            else -> stringResource(R.string.creality_discovery_found, hosts.size)
        },
        busy = scanning,
        onDismiss = onDismiss,
        footer = {
            OrcaButton(
                text = stringResource(R.string.creality_discovery_scan),
                onClick = { round++ },
                style = OrcaButtonStyle.Regular,
                enabled = !scanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        },
    ) {
        items(hosts, key = { it.ip }) { host ->
            val model = when {
                host.modelName.isNotEmpty() -> host.modelName
                host.cfsCapable -> unknownModel
                else -> "Creality"
            }
            val details = "${orcaString("Hostname")}: ${host.hostname}$DETAILS_SEPARATOR${orcaString("IP")}: ${host.ip}"
            DiscoveredRow(model, details) { onChoose(host.ip) }
        }
    }
}

/**
 * The Browse button of a Flashforge printer: the printers that answered the
 * broadcast (Flashforge::discover_printers()), in the list the desktop's
 * "Discovered Printers" choice shows — name, address and serial number. A tap
 * takes the address and the serial number; nothing found is the error the
 * desktop shows.
 */
@Composable
internal fun FlashforgeDiscoverySheet(
    discover: suspend () -> FlashforgeDiscoveryOutcome,
    onChoose: (FlashforgeDiscoveredPrinter) -> Unit,
    onDismiss: () -> Unit,
) {
    var outcome by remember { mutableStateOf<FlashforgeDiscoveryOutcome?>(null) }
    LaunchedEffect(Unit) { outcome = discover() }
    val found = (outcome as? FlashforgeDiscoveryOutcome.Success)?.printers.orEmpty()
    DiscoverySheet(
        title = orcaString("Discovered Printers"),
        status = when (val answer = outcome) {
            null -> orcaString("Searching for devices") + "..."
            is FlashforgeDiscoveryOutcome.Failure -> answer.message.ifEmpty { orcaString("No Flashforge printers were discovered on the local network.") }
            is FlashforgeDiscoveryOutcome.Success -> orcaString("Select a Flashforge printer")
        },
        busy = outcome == null,
        onDismiss = onDismiss,
    ) {
        items(found, key = { it.ipAddress }) { printer ->
            // "%1% (%2%) [%3%]" of the desktop's list.
            DiscoveredRow(printer.name, "(${printer.ipAddress}) [${printer.serialNumber}]") { onChoose(printer) }
        }
    }
}

/**
 * The sheet of the lookups; a larger window shows it as a dialog. The desktop
 * dialogs are tables of a column per detail (BonjourDialog's list 80 em wide,
 * CrealityDiscoveryDialog's 50 em); a row here holds the details on a line
 * under the name, which a dialog of a list's width has room for.
 */
@Composable
private fun DiscoverySheet(
    title: String,
    status: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
    rows: LazyListScope.() -> Unit,
) {
    val colors = OrcaTheme.colors
    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = OrcaDialogWidth.Medium) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = title,
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { heading() },
            )
            Text(
                text = status,
                color = colors.textSide,
                style = OrcaTheme.typography.body13,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (busy) {
                LinearProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), content = rows)
            footer()
        }
    }
}

/** A host of the list: what it is, and what the desktop list's other columns say of it. */
@Composable
private fun DiscoveredRow(title: String, details: String, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .orcaClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, color = colors.text, style = OrcaTheme.typography.body14)
        Text(details, color = colors.textSide, style = OrcaTheme.typography.body12)
    }
    HorizontalDivider(color = colors.separator, thickness = 1.dp)
}

private const val DOTS_MILLIS = 1_000L

/** Between the columns of the desktop list, which a row shows on one line. */
private const val DETAILS_SEPARATOR = " · "
