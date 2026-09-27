package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.FlashforgeMaterialSlot
import app.orcinus.shadow.core.model.FlashforgeOptions
import app.orcinus.shadow.core.model.FlashforgeSend
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.SentFilament
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * FlashforgePrintHostSendDialog, for a Flashforge printer on its local API:
 * the printer's material station is read first (Plater::send_gcode_legacy()
 * gives up with "Unable to log in" when it cannot be), then the dialog's three
 * switches — level the bed, record a time-lapse, print from the station (IFS),
 * on when the printer has one — and, with the station, the slot every filament
 * of the slice is fed from, the loaded slot of its kind nearest in colour
 * first. The desktop's slot picker offers only loaded slots of the filament's
 * kind; so does the list here. The choices are checked as the dialog checks
 * them before it closes.
 */
@Composable
internal fun FlashforgeSendPage(
    printer: PhysicalPrinter,
    filaments: List<SentFilament>,
    loadSlots: suspend (PhysicalPrinter) -> FlashforgeSlotsOutcome,
    onBack: () -> Unit,
    onSend: (FlashforgeOptions) -> Unit,
) {
    val colors = OrcaTheme.colors
    // FilamentInfo of the slice: the filaments it prints with.
    val used = remember(filaments) { filaments.filter { it.used } }
    var outcome by remember(printer) { mutableStateOf<FlashforgeSlotsOutcome?>(null) }
    var leveling by remember { mutableStateOf(false) }
    var timeLapse by remember { mutableStateOf(false) }
    var useStation by remember { mutableStateOf(false) }
    var assigned by remember { mutableStateOf<List<Int?>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(printer) {
        val answer = loadSlots(printer)
        outcome = answer
        if (answer is FlashforgeSlotsOutcome.Success) {
            // init(): IFS is on when the printer supports it.
            useStation = answer.supportsMaterialStation
            assigned = FlashforgeSend.autoAssign(used, answer.slots)
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        when (val answer = outcome) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(20.dp))
                Text(
                    text = orcaString("Loading IFS slots from printer..."),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            is FlashforgeSlotsOutcome.Failure -> {
                Text(
                    text = answer.message.ifEmpty { orcaString("Unable to log in to the Flashforge printer.") },
                    color = colors.error,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OrcaButton(
                    text = orcaString("Cancel"),
                    style = OrcaButtonStyle.Regular,
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }
            is FlashforgeSlotsOutcome.Success -> {
                val slots = answer.slots
                Option(orcaString("Leveling before print"), leveling) { leveling = it }
                Option(orcaString("Time-lapse"), timeLapse) { timeLapse = it }
                Option(orcaString("Enable IFS"), useStation, enabled = answer.supportsMaterialStation) { useStation = it }
                Text(
                    text = if (answer.supportsMaterialStation) {
                        orcaString("Detected %d IFS slots on printer.").replace("%d", slots.size.toString())
                    } else {
                        orcaString("This printer does not report a material station.")
                    },
                    color = colors.textSide,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (useStation && answer.supportsMaterialStation) {
                    if (used.isEmpty()) {
                        Text(
                            text = orcaString("Slice the plate first to get project material information."),
                            color = colors.textSide,
                            style = OrcaTheme.typography.body13,
                        )
                    }
                    used.forEachIndexed { index, filament ->
                        MappingRow(filament, slots, assigned.getOrNull(index)) { slotId ->
                            assigned = List(used.size) { at -> if (at == index) slotId else assigned.getOrNull(at) }
                            problem = null
                        }
                    }
                    if (used.isNotEmpty()) {
                        Text(
                            text = orcaString("Only materials of the same type can be selected."),
                            color = colors.textSide,
                            style = OrcaTheme.typography.body12,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                problem?.let {
                    Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(vertical = 4.dp))
                }
                val invalidMessage = FlashforgeSend.validate(useStation, used, assigned, slots)?.let { orcaString(it) }
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
                        text = orcaString("Upload"),
                        onClick = {
                            // validate_before_close()
                            if (invalidMessage != null) {
                                problem = invalidMessage
                            } else {
                                onSend(
                                    FlashforgeOptions(
                                        levelingBeforePrint = leveling,
                                        timeLapseVideo = timeLapse,
                                        useMaterialStation = useStation,
                                        mappings = FlashforgeSend.mappings(used, assigned, slots),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Option(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = if (enabled) OrcaTheme.colors.text else OrcaTheme.colors.textDisabled,
            style = OrcaTheme.typography.body14,
            modifier = Modifier.weight(1f),
        )
        OrcaSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * FlashforgeMaterialMapWidget: a filament of the slice, its colour and kind,
 * and the slot of the station it is fed from, chosen among the loaded slots
 * of its kind (FlashforgeSlotDialog).
 */
@Composable
private fun MappingRow(filament: SentFilament, slots: List<FlashforgeMaterialSlot>, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = OrcaTheme.colors
    val choices = slots.filter { FlashforgeSend.slotMatches(it, filament.type) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Swatch(filament.color)
        Text(
            text = "${filament.tool + 1} (${filament.type.ifEmpty { "?" }})",
            color = colors.text,
            style = OrcaTheme.typography.body13,
            modifier = Modifier
                .padding(start = 8.dp)
                .width(88.dp),
        )
        Text("→", color = colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(end = 8.dp))
        if (choices.isEmpty()) {
            Text("—", color = colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
        } else {
            val index = choices.indexOfFirst { it.slotId == selected }
            OrcaComboBox(
                items = choices.indices.toList(),
                selected = index.coerceAtLeast(0),
                label = { if (index < 0 && it == 0) "—" else slotLabel(choices[it]) },
                onSelect = { onSelect(choices[it].slotId) },
                leading = {
                    if (index >= 0) {
                        Swatch(choices[index].materialColor)
                        Spacer(Modifier.width(8.dp))
                    }
                },
                itemLeading = { Swatch(choices[it].materialColor) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The desktop's slot card: its number and the material loaded in it. */
private fun slotLabel(slot: FlashforgeMaterialSlot): String = "${slot.slotId} - ${slot.materialName.ifEmpty { "?" }}"

/** A colour square, as the desktop dialog draws a filament or a slot. */
@Composable
private fun Swatch(color: String) {
    Box(
        Modifier
            .size(16.dp)
            .background(Color(0xFF000000L or FlashforgeSend.rgb(color).toLong()))
            .border(1.dp, OrcaTheme.colors.border),
    )
}
