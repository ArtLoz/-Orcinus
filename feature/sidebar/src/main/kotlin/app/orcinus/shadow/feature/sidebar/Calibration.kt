package app.orcinus.shadow.feature.sidebar

import androidx.compose.foundation.layout.Arrangement
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaChoiceChips
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.CalibrationMode
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * MainFrame's Calibration menu, which the desktop app's top bar opens: the
 * tests in its order, and the calibration guide on the web. The tests the app
 * has not ported yet stand disabled.
 */
@Composable
internal fun CalibrationMenuItems(
    enabled: Boolean,
    dismiss: () -> Unit,
    onTemperature: () -> Unit,
    onRange: (RangeTest) -> Unit,
    onPressureAdvance: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    @Composable
    fun item(text: String, onClick: (() -> Unit)?) = OrcaMenuItem(
        text = orcaString(text),
        enabled = enabled && onClick != null,
        onClick = {
            dismiss()
            onClick?.invoke()
        },
    )
    item("Temperature", onTemperature)
    item("Max flowrate") { onRange(RangeTest.MAX_FLOWRATE) }
    item("Pressure advance", onPressureAdvance)
    item("Flow ratio", null)
    item("Retraction") { onRange(RangeTest.RETRACTION) }
    item("Cornering", null)
    OrcaSubmenu(text = orcaString("Input Shaping"), enabled = false) {
        item("Input Shaping Frequency", null)
        item("Input Shaping Damping/zeta factor", null)
    }
    item("VFA") { onRange(RangeTest.VFA) }
    OrcaMenuItem(
        text = orcaString("Calibration Guide"),
        onClick = {
            dismiss()
            uriHandler.openUri(CALIBRATION_GUIDE)
        },
    )
}

/** The filament types of Temp_Calibration_Dlg with the temperatures each starts and ends at (on_filament_type_changed). */
private val FILAMENT_TYPES = listOf(
    Triple("PLA", 230, 190),
    Triple("ABS/ASA", 270, 230),
    Triple("PETG", 250, 230),
    Triple("PCTG", 280, 240),
    Triple("TPU", 240, 210),
    Triple("PA-CF", 320, 280),
    Triple("PET-CF", 320, 280),
    Triple("Custom", 230, 190),
)

/**
 * Temp_Calibration_Dlg, as a sheet: the filament type, whose choice fills in
 * the temperatures to start and end at, and the step of 5 °C the tower has. A
 * temperature leaving its field is kept between 155 and 500 °C, with the
 * dialog's message, and rounded down to 5 °C; OK wants the start at most
 * 500 °C, the end at least 155 °C, and the start 5 °C above the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemperatureCalibrationSheet(onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    var type by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf("230") }
    var end by remember { mutableStateOf("190") }
    var message by remember { mutableStateOf<String?>(null) }
    val degrees = orcaString("℃")
    val rangeMessage = "Supported range: 170$degrees - 500$degrees"
    val invalidMessage = orcaString("Please input valid values:\nStart temp: <= 500\nEnd temp: >= 155\nStart temp >= End temp + 5")

    // validate_text() of a field that loses focus.
    fun validated(text: String): String {
        var value = text.toLongOrNull()?.takeIf { it >= 0 } ?: return text
        if (value > 500 || value < 155) {
            message = rangeMessage
            value = if (value > 500) 500 else 155
        }
        return ((value / 5) * 5).toString()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(orcaString("Temperature calibration"), color = colors.text, style = OrcaTheme.typography.head16)
            Text(orcaString("Filament type"), color = colors.textLabel, style = OrcaTheme.typography.body14)
            OrcaChoiceChips(
                items = FILAMENT_TYPES.map { (name, _, _) -> orcaString(name) },
                selected = type,
                onSelect = { index ->
                    type = index
                    start = FILAMENT_TYPES[index].second.toString()
                    end = FILAMENT_TYPES[index].third.toString()
                },
            )
            Text(orcaString("Settings"), color = colors.textLabel, style = OrcaTheme.typography.body14)
            TemperatureField(orcaString("Start temp: "), start, degrees, onChange = { start = it }, onLeave = { start = validated(start) })
            TemperatureField(orcaString("End temp: "), end, degrees, onChange = { end = it }, onLeave = { end = validated(end) })
            TemperatureField(orcaString("Temp step: "), "5", degrees, enabled = false)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OrcaButton(orcaString("Wiki Guide"), onClick = { uriHandler.openUri(TEMPERATURE_GUIDE) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("OK"), onClick = {
                    val first = start.toLongOrNull()?.takeIf { it >= 0 }
                    val last = end.toLongOrNull()?.takeIf { it >= 0 }
                    if (first == null || last == null || first > 500 || last < 155 || last > first - 5) {
                        message = invalidMessage
                    } else {
                        onStart(CalibrationParams(CalibrationMode.TEMP_TOWER, start = first.toDouble(), end = last.toDouble()))
                    }
                })
            }
        }
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = { message = null }) },
            text = { Text(text, style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/**
 * A test whose dialog asks for a start, an end and a step
 * (MaxVolumetricSpeed_Test_Dlg, VFA_Test_Dlg and Retraction_Test_Dlg): its
 * texts, the figures the dialog starts with, and what its OK accepts.
 */
internal enum class RangeTest(
    val mode: CalibrationMode,
    val title: String,
    val startLabel: String,
    val endLabel: String,
    val unit: String,
    val start: String,
    val end: String,
    val step: String,
    val guide: String,
    val invalidMessage: String,
    val accepts: (start: Double, end: Double, step: Double) -> Boolean,
) {
    MAX_FLOWRATE(
        CalibrationMode.VOL_SPEED_TOWER, "Max volumetric speed test", "Start volumetric speed: ", "End volumetric speed: ", "mm³/s",
        "5", "20", "0.5", "https://www.orcaslicer.com/wiki/volumetric_speed_calib",
        "Please input valid values:\nstart > 0\nstep >= 0\nend > start + step",
        { start, end, step -> start > 0 && step > 0 && end >= start + step },
    ),
    VFA(
        CalibrationMode.VFA_TOWER, "VFA test", "Start speed: ", "End speed: ", "mm/s",
        "40", "200", "10", "https://www.orcaslicer.com/wiki/vfa_calib",
        "Please input valid values:\nstart > 10\nstep >= 0\nend > start + step",
        { start, end, step -> start > 10 && step > 0 && end >= start + step },
    ),
    RETRACTION(
        CalibrationMode.RETRACTION_TOWER, "Retraction", "Start retraction length: ", "End retraction length: ", "mm",
        "0", "2", "0.1", "https://www.orcaslicer.com/wiki/retraction_calib",
        "Please input valid values:\nstart > 0\nstep >= 0\nend > start + step",
        { start, end, step -> start >= 0 && step > 0 && end >= start + step },
    ),
}

/** The dialog of a [RangeTest], as a sheet: its start, end and step, and OK once they are valid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangeCalibrationSheet(test: RangeTest, onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    var start by remember { mutableStateOf(test.start) }
    var end by remember { mutableStateOf(test.end) }
    var step by remember { mutableStateOf(test.step) }
    var invalid by remember { mutableStateOf(false) }
    val unit = orcaString(test.unit)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(orcaString(test.title), color = colors.text, style = OrcaTheme.typography.head16)
            Text(orcaString("Settings"), color = colors.textLabel, style = OrcaTheme.typography.body14)
            NumberField(orcaString(test.startLabel), start, unit) { start = it }
            NumberField(orcaString(test.endLabel), end, unit) { end = it }
            NumberField(orcaString("Step") + ": ", step, unit) { step = it }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OrcaButton(orcaString("Wiki Guide"), onClick = { uriHandler.openUri(test.guide) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("OK"), onClick = {
                    val first = start.toDoubleOrNull()
                    val last = end.toDoubleOrNull()
                    val by = step.toDoubleOrNull()
                    if (first == null || last == null || by == null || !test.accepts(first, last, by)) {
                        invalid = true
                    } else {
                        onStart(CalibrationParams(test.mode, start = first, end = last, step = by))
                    }
                })
            }
        }
    }
    if (invalid) {
        AlertDialog(
            onDismissRequest = { invalid = false },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = { invalid = false }) },
            text = { Text(orcaString(test.invalidMessage), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** A figure of a test's dialog with its label beside it; a decimal comma counts as the point the dialog reads. */
@Composable
private fun NumberField(label: String, value: String, unit: String?, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        OrcaTextField(
            value = value,
            onValueChange = { text -> onChange(text.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }.take(8)) },
            unit = unit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(140.dp),
        )
    }
}

/** PA_Calibration_Dlg's choices, which the dialog keeps from one showing to the next. */
internal data class PressureAdvanceChoice(val bowden: Boolean = false, val method: Int = PA_TOWER)

/** The methods of PA_Calibration_Dlg, in its order. */
private val PA_METHODS = listOf("PA Tower", "PA Line", "PA Pattern")
private const val PA_TOWER = 0
private const val PA_LINE = 1
private const val PA_PATTERN = 2

/**
 * PA_Calibration_Dlg, as a sheet: the extruder type and the method, whose
 * choice sets the figures again (reset_params), the PA to start and end at
 * and its step, whether the lines print their numbers, and for the pattern
 * its accelerations and speeds. OK wants a start of 0 or more, a step of at
 * least 0.001 and an end past the start by a step, and accelerations above
 * the speeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PressureAdvanceSheet(
    choice: PressureAdvanceChoice,
    onChoice: (PressureAdvanceChoice) -> Unit,
    onDismiss: () -> Unit,
    onStart: (CalibrationParams) -> Unit,
) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    // reset_params(): the figures of the method and extruder type.
    fun endOf(choice: PressureAdvanceChoice) = when {
        choice.bowden -> "1"
        choice.method == PA_PATTERN -> "0.08"
        else -> "0.1"
    }
    fun stepOf(choice: PressureAdvanceChoice) = when {
        choice.bowden && choice.method == PA_PATTERN -> "0.05"
        choice.bowden -> "0.02"
        choice.method == PA_PATTERN -> "0.005"
        else -> "0.002"
    }
    var start by remember { mutableStateOf("0") }
    var end by remember { mutableStateOf(endOf(choice)) }
    var step by remember { mutableStateOf(stepOf(choice)) }
    var printNumbers by remember { mutableStateOf(choice.method != PA_TOWER) }
    var accelerations by remember { mutableStateOf("") }
    var speeds by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val invalidMessage = orcaString("Please input valid values:\nStart PA: >= 0.0\nEnd PA: > Start PA\nPA step: >= 0.001")
    val swappedMessage = orcaString("Acceleration values must be greater than speed values.\nPlease verify the inputs.")

    fun choose(next: PressureAdvanceChoice) {
        onChoice(next)
        start = "0"
        end = endOf(next)
        step = stepOf(next)
        printNumbers = next.method != PA_TOWER
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(orcaString("PA Calibration"), color = colors.text, style = OrcaTheme.typography.head16)
            Text(orcaString("Extruder type"), color = colors.textLabel, style = OrcaTheme.typography.body14)
            OrcaChoiceChips(
                items = listOf(orcaString("DDE"), orcaString("Bowden")),
                selected = if (choice.bowden) 1 else 0,
                onSelect = { choose(choice.copy(bowden = it == 1)) },
            )
            Text(orcaString("Method"), color = colors.textLabel, style = OrcaTheme.typography.body14)
            OrcaChoiceChips(
                items = PA_METHODS.map { orcaString(it) },
                selected = choice.method,
                onSelect = { choose(choice.copy(method = it)) },
            )
            Text(orcaString("Settings"), color = colors.textLabel, style = OrcaTheme.typography.body14)
            NumberField(orcaString("Start PA: "), start, null) { start = it }
            NumberField(orcaString("End PA: "), end, null) { end = it }
            NumberField(orcaString("PA step: "), step, null) { step = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(orcaString("Print numbers"), color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
                // Only the lines choose; the pattern always prints its numbers and the tower none.
                OrcaCheckBox(checked = printNumbers, onCheckedChange = { printNumbers = it }, enabled = choice.method == PA_LINE)
            }
            ListField(orcaString("Accelerations: "), accelerations, orcaString("Comma-separated list of printing accelerations"), choice.method == PA_PATTERN) { accelerations = it }
            ListField(orcaString("Speeds: "), speeds, orcaString("Comma-separated list of printing speeds"), choice.method == PA_PATTERN) { speeds = it }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OrcaButton(orcaString("Wiki Guide"), onClick = { uriHandler.openUri(PRESSURE_ADVANCE_GUIDE) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("OK"), onClick = {
                    val first = start.toDoubleOrNull()
                    val last = end.toDoubleOrNull()
                    val by = step.toDoubleOrNull()
                    // ParseStringValues(): the numbers of the comma-separated list.
                    val accels = accelerations.split(',').mapNotNull { it.trim().toDoubleOrNull() }
                    val speedList = speeds.split(',').mapNotNull { it.trim().toDoubleOrNull() }
                    when {
                        first == null || last == null || by == null || first < 0 || by < 10 * CALIB_EPSILON || last < first + by -> message = invalidMessage
                        accels.isNotEmpty() && speedList.isNotEmpty() && accels.min() <= speedList.max() -> message = swappedMessage
                        else -> onStart(
                            CalibrationParams(
                                mode = when (choice.method) {
                                    PA_LINE -> CalibrationMode.PA_LINE
                                    PA_PATTERN -> CalibrationMode.PA_PATTERN
                                    else -> CalibrationMode.PA_TOWER
                                },
                                start = first,
                                end = last,
                                step = by,
                                printNumbers = printNumbers,
                                accelerations = accels,
                                speeds = speedList,
                            ),
                        )
                    }
                })
            }
        }
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = { message = null }) },
            text = { Text(text, style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** A comma-separated list of the PA dialog, its tooltip under it. */
@Composable
private fun ListField(label: String, value: String, hint: String, enabled: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (enabled) OrcaTheme.colors.text else OrcaTheme.colors.textDisabled, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
            OrcaTextField(
                value = value,
                // The dialog's validator takes digits and commas alone.
                onValueChange = { text -> onChange(text.filter { it.isDigit() || it == ',' }) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
            )
        }
        Text(hint, color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body13)
    }
}

/** EPSILON of libslic3r, which the PA dialog's step is checked against. */
private const val CALIB_EPSILON = 1e-4
private const val PRESSURE_ADVANCE_GUIDE = "https://www.orcaslicer.com/wiki/pressure_advance_calib"

/** A temperature of the dialog, its label beside it, which [onLeave] checks once the field loses focus. */
@Composable
private fun TemperatureField(
    label: String,
    value: String,
    unit: String,
    onChange: (String) -> Unit = {},
    onLeave: () -> Unit = {},
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        OrcaTextField(
            value = value,
            onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
            unit = unit,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(140.dp)
                .onFocusChanged { focus ->
                    if (focused && !focus.isFocused) onLeave()
                    focused = focus.isFocused
                },
        )
    }
}

private const val CALIBRATION_GUIDE = "https://www.orcaslicer.com/wiki/calibration_guide"
private const val TEMPERATURE_GUIDE = "https://www.orcaslicer.com/wiki/temp_calib"
