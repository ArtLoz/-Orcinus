package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.Printer3dOsChoice
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.preset.ChoiceListSheet

/**
 * 3DPrinterOS's UploadOptionsDialog: the session is checked and the cloud's
 * projects and printer types are read (C3DPrinterOS::upload() gives up with
 * the check's message when it fails), then the file goes up alone or into a
 * project — one of the cloud's, or a new one of the name typed, as the
 * desktop's editable combo box allows — for the printer type chosen, which OK
 * waits for. The combo boxes open searchable lists, since the cloud's lists
 * can be long.
 */
@Composable
internal fun Printer3dOsSendPage(
    printer: PhysicalPrinter,
    loadLists: suspend (PhysicalPrinter) -> Printer3dOsListsOutcome,
    onBack: () -> Unit,
    onSend: (Printer3dOsChoice) -> Unit,
) {
    val colors = OrcaTheme.colors
    var outcome by remember(printer) { mutableStateOf<Printer3dOsListsOutcome?>(null) }
    var toProject by remember { mutableStateOf(false) }
    var project by remember { mutableStateOf("") }
    var printerType by remember { mutableStateOf<Int?>(null) }
    var choosingProject by remember { mutableStateOf(false) }
    var choosingType by remember { mutableStateOf(false) }
    LaunchedEffect(printer) {
        val answer = loadLists(printer)
        outcome = answer
        if (answer is Printer3dOsListsOutcome.Success) printerType = answer.initialPrinterType(printer.printerModel)
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = orcaString("3DPrinterOS Cloud upload options"),
            color = colors.textLabel,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        when (val answer = outcome) {
            null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
            is Printer3dOsListsOutcome.Failure -> {
                Text(answer.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(vertical = 8.dp))
                OrcaButton(
                    text = orcaString("Cancel"),
                    style = OrcaButtonStyle.Regular,
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }
            is Printer3dOsListsOutcome.Success -> {
                OrcaSegmentedSwitch(
                    options = listOf(orcaString("Single file"), orcaString("Project File")),
                    selectedIndex = if (toProject) 1 else 0,
                    onSelect = { toProject = it == 1 },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (toProject) {
                    Label(orcaString("Project:"))
                    OrcaTextField(
                        value = project,
                        onValueChange = { project = it },
                        trailing = if (answer.projects.isEmpty()) {
                            null
                        } else {
                            {
                                Icon(
                                    painter = painterResource(DesignR.drawable.orca_drop_down),
                                    contentDescription = orcaString("Project:"),
                                    tint = colors.textSide,
                                    modifier = Modifier
                                        .clickable(role = Role.DropdownList) { choosingProject = true }
                                        .padding(horizontal = 8.dp)
                                        .size(OrcaTheme.dimensions.iconSmall),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Label(orcaString("Printer type:"))
                OrcaComboField(
                    text = printerType?.let { answer.printerTypes[it].description }.orEmpty(),
                    onClick = { choosingType = true },
                    enabled = answer.printerTypes.isNotEmpty(),
                )
                if (answer.asksForPrinterType) {
                    Text(
                        text = orcaString("Printer type not found, please select manually."),
                        color = colors.error,
                        style = OrcaTheme.typography.body13,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
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
                    // ValidateOkButton(): OK waits for a printer type.
                    val chosenType = printerType
                    OrcaButton(
                        text = orcaString("OK"),
                        enabled = chosenType != null,
                        onClick = { chosenType?.let { onSend(answer.choice(if (toProject) project else "", answer.printerTypes[it])) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (choosingProject) {
                    ChoiceListSheet(
                        title = orcaString("Project:"),
                        items = answer.projects.map { it.name },
                        onDismiss = { choosingProject = false },
                        onChoose = {
                            choosingProject = false
                            project = it
                        },
                    )
                }
                if (choosingType) {
                    val descriptions = answer.printerTypes.map { it.description }
                    ChoiceListSheet(
                        title = orcaString("Printer type:"),
                        items = descriptions,
                        onDismiss = { choosingType = false },
                        onChoose = {
                            choosingType = false
                            printerType = descriptions.indexOf(it).takeIf { index -> index >= 0 }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}
