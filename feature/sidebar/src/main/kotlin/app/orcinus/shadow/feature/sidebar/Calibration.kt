package app.orcinus.shadow.feature.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaChoiceChips
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.component.OrcaDialogWidth
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.icon.orcaIcon
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.CalibrationMode
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.CalibrationPrinter
import app.orcinus.shadow.core.model.FlowRateCalibration
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import java.util.Locale

/**
 * MainFrame's Calibration menu, which the desktop app's top bar opens: the
 * tests in its order, and the calibration guide on the web. Every item wants
 * the 3D view shown (Plater::is_view3D_shown()) [view3D], and a test, which
 * starts a project of its own, the plate idle as well ([enabled]).
 */
@Composable
internal fun CalibrationMenuItems(
    enabled: Boolean,
    view3D: Boolean,
    dismiss: () -> Unit,
    onTemperature: () -> Unit,
    onRange: (RangeTest) -> Unit,
    onPressureAdvance: () -> Unit,
    onFlowRate: () -> Unit,
    onPrinterTest: (PrinterTest) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    @Composable
    fun item(text: String, onClick: () -> Unit) = OrcaMenuItem(
        text = orcaString(text),
        enabled = enabled && view3D,
        onClick = {
            dismiss()
            onClick()
        },
    )
    item("Temperature", onTemperature)
    item("Max flowrate") { onRange(RangeTest.MAX_FLOWRATE) }
    item("Pressure advance", onPressureAdvance)
    item("Flow ratio", onFlowRate)
    item("Retraction") { onRange(RangeTest.RETRACTION) }
    item("Cornering") { onPrinterTest(PrinterTest.CORNERING) }
    OrcaSubmenu(text = orcaString("Input Shaping"), enabled = enabled && view3D) {
        item("Input Shaping Frequency") { onPrinterTest(PrinterTest.INPUT_SHAPING_FREQUENCY) }
        item("Input Shaping Damping/zeta factor") { onPrinterTest(PrinterTest.INPUT_SHAPING_DAMPING) }
    }
    item("VFA") { onRange(RangeTest.VFA) }
    OrcaMenuItem(
        text = orcaString("Calibration Guide"),
        enabled = view3D,
        onClick = {
            dismiss()
            uriHandler.openUri(CALIBRATION_GUIDE)
        },
    )
}

/** The filament types of Temp_Calibration_Dlg with the temperatures each starts and ends at (on_filament_type_changed). */
/**
 * The calibration dialogs of calib_dlg.cpp size to their labels and fields
 * (FromDIP(120) each) with Fit(); a larger window shows them at the width of a
 * dialog of a form.
 */
private val CALIBRATION_DIALOG_WIDTH = OrcaDialogWidth.Medium

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
@Composable
internal fun TemperatureCalibrationSheet(onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    val kept = CalibrationDialogs.temperature
    var type by remember { mutableIntStateOf(kept.type) }
    var start by remember { mutableStateOf(kept.start) }
    var end by remember { mutableStateOf(kept.end) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { CalibrationDialogs.temperature = TemperatureFigures(type, start, end) } }
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

    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = CALIBRATION_DIALOG_WIDTH, skipPartiallyExpanded = true) {
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
                    // The press takes the focus from the field first, whose
                    // validate_text() rounds and limits it; its warning stops the press.
                    start = validated(start)
                    end = validated(end)
                    if (message != null) return@OrcaButton
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

/**
 * The dialog of a [RangeTest], as a sheet on a phone and a dialog on a larger
 * window: its start, end and step, and OK once they are valid.
 */
@Composable
internal fun RangeCalibrationSheet(test: RangeTest, onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    val kept = CalibrationDialogs.ranges[test]
    var start by remember { mutableStateOf(kept?.start ?: test.start) }
    var end by remember { mutableStateOf(kept?.end ?: test.end) }
    var step by remember { mutableStateOf(kept?.step ?: test.step) }
    var invalid by remember { mutableStateOf(false) }
    DisposableEffect(test) { onDispose { CalibrationDialogs.ranges[test] = RangeFigures(start, end, step) } }
    val unit = orcaString(test.unit)

    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = CALIBRATION_DIALOG_WIDTH, skipPartiallyExpanded = true) {
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

/** The tests whose dialogs read the printer (CalibrationPrinter) as they open. */
internal enum class PrinterTest { INPUT_SHAPING_FREQUENCY, INPUT_SHAPING_DAMPING, CORNERING }

/** The printer's firmware in the dialogs of Input Shaping and Cornering (GCodeFlavor). */
private const val FLAVOR_MARLIN_LEGACY = "marlin"
private const val FLAVOR_MARLIN = "marlin2"
private const val FLAVOR_KLIPPER = "klipper"
private const val FLAVOR_REPRAP = "reprapfirmware"

/** The test models of the Input Shaping dialogs, and Cornering's third. */
private val SHAPING_MODELS = listOf("Ringing Tower", "Fast Tower")
private val CORNERING_MODELS = SHAPING_MODELS + "SCV-V2"

/** The note of the Input Shaping dialogs on the firmware; [fallback] for a firmware with none. */
@Composable
private fun firmwareNote(printer: CalibrationPrinter, fallback: String): String = orcaString(
    when (printer.gcodeFlavor) {
        FLAVOR_MARLIN, FLAVOR_MARLIN_LEGACY -> "Marlin version => 2.1.2\nFixed-Time motion not yet implemented."
        FLAVOR_KLIPPER -> "Klipper version => 0.9.0"
        FLAVOR_REPRAP -> "RepRap firmware version => 3.4.0\nCheck your firmware documentation for supported shaper types."
        else -> fallback
    },
)

/**
 * Input_Shaping_Freq_Test_Dlg, as a sheet: the test model, the input shaper
 * type among those the firmware knows, the frequencies to go from and to on
 * each axis (one range for RepRap firmware) and the damping. OK wants
 * frequencies from 0 up to 500 Hz and a damping from 0 below 1.
 */
@Composable
internal fun InputShapingFrequencySheet(printer: CalibrationPrinter, onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val reprap = printer.gcodeFlavor == FLAVOR_REPRAP
    var model by remember { mutableIntStateOf(0) }
    var type by remember { mutableIntStateOf(0) }
    var startX by remember { mutableStateOf("15") }
    var endX by remember { mutableStateOf("110") }
    var startY by remember { mutableStateOf("15") }
    var endY by remember { mutableStateOf("110") }
    var damping by remember { mutableStateOf("0.150") }
    var message by remember { mutableStateOf<String?>(null) }
    val invalidFrequencies = orcaString("Please input valid values:\n(0 < FreqStart < FreqEnd < 500)")
    val invalidDamping = orcaString("Please input a valid damping factor (0 < Damping/zeta factor <= 1)")
    val hz = orcaString("Hz")
    val startEnd = orcaString("Start / End")
    TestSheet(
        title = orcaString("Input shaping Frequency test"),
        guide = INPUT_SHAPING_GUIDE,
        onDismiss = onDismiss,
        onOk = {
            val x = startX.toDoubleOrNull() to endX.toDoubleOrNull()
            // RepRap firmware takes the range of X for both axes.
            val y = if (reprap) x else startY.toDoubleOrNull() to endY.toDoubleOrNull()
            val damp = damping.toDoubleOrNull()
            val (fromX, toX) = x
            val (fromY, toY) = y
            when {
                fromX == null || toX == null || fromY == null || toY == null || damp == null ||
                    fromX < 0 || toX > 500 || (!reprap && (fromY < 0 || toY > 500)) ||
                    fromX >= toX || (!reprap && fromY >= toY) -> message = invalidFrequencies
                damp < 0 || damp >= 1 -> message = invalidDamping
                else -> onStart(
                    CalibrationParams(
                        CalibrationMode.INPUT_SHAPING_FREQ,
                        start = damp,
                        freqStartX = fromX,
                        freqEndX = toX,
                        freqStartY = fromY,
                        freqEndY = toY,
                        testModel = model,
                        shaperType = printer.shaperTypes.getOrElse(type) { printer.shaperTypes.firstOrNull().orEmpty() },
                    ),
                )
            }
        },
    ) {
        Choices(orcaString("Test model"), SHAPING_MODELS.map { orcaString(it) }, model) { model = it }
        Choices(orcaString("Input shaper type"), printer.shaperTypes, type) { type = it }
        Note(firmwareNote(printer, "Please ensure the selected type is compatible with your firmware version."))
        SectionLabel(orcaString("Frequency settings"))
        if (reprap) {
            RangeFields(orcaString("Frequency (Start / End): "), startX, endX, hz, { startX = it }, { endX = it })
            // The fields' tooltip.
            Note(orcaString("RepRap firmware uses the same frequency range for both axes."))
        } else {
            RangeFields("X $startEnd: ", startX, endX, hz, { startX = it }, { endX = it })
            RangeFields("Y $startEnd: ", startY, endY, hz, { startY = it }, { endY = it })
        }
        NumberField(orcaString("Damp: "), damping, null) { damping = it }
        Note(orcaString("Recommended: Set Damp to 0.\nThis will use the printer's default or saved value."))
    }
    message?.let { text -> CalibrationMessage(text) { message = null } }
}

/**
 * Input_Shaping_Damp_Test_Dlg, as a sheet: the test model, the input shaper
 * type, the frequency of each axis found before (one for RepRap firmware) and
 * the damping to go from and to. OK wants frequencies from 0 up to 500 Hz and
 * a damping range within 0 and 1.
 */
@Composable
internal fun InputShapingDampingSheet(printer: CalibrationPrinter, onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val reprap = printer.gcodeFlavor == FLAVOR_REPRAP
    var model by remember { mutableIntStateOf(0) }
    var type by remember { mutableIntStateOf(0) }
    var frequencyX by remember { mutableStateOf("30") }
    var frequencyY by remember { mutableStateOf("30") }
    var dampingStart by remember { mutableStateOf("0.000") }
    var dampingEnd by remember { mutableStateOf("0.400") }
    var message by remember { mutableStateOf<String?>(null) }
    val invalidFrequencies = orcaString("Please input valid values:\n(0 < Freq < 500)")
    val invalidDamping = orcaString("Please input a valid damping factor (0 <= DampingStart < DampingEnd <= 1)")
    val hz = orcaString("Hz")
    TestSheet(
        title = orcaString("Input shaping Damp test"),
        guide = INPUT_SHAPING_GUIDE,
        onDismiss = onDismiss,
        onOk = {
            val x = frequencyX.toDoubleOrNull()
            // RepRap firmware takes the frequency of X for both axes.
            val y = if (reprap) x else frequencyY.toDoubleOrNull()
            val from = dampingStart.toDoubleOrNull()
            val to = dampingEnd.toDoubleOrNull()
            when {
                x == null || y == null || from == null || to == null ||
                    x < 0 || x > 500 || (!reprap && (y < 0 || y > 500)) -> message = invalidFrequencies
                from < 0 || to > 1 || from >= to -> message = invalidDamping
                else -> onStart(
                    CalibrationParams(
                        CalibrationMode.INPUT_SHAPING_DAMP,
                        start = from,
                        end = to,
                        freqStartX = x,
                        freqStartY = y,
                        testModel = model,
                        shaperType = printer.shaperTypes.getOrElse(type) { printer.shaperTypes.firstOrNull().orEmpty() },
                    ),
                )
            }
        },
    ) {
        Choices(orcaString("Test model"), SHAPING_MODELS.map { orcaString(it) }, model) { model = it }
        Choices(orcaString("Input shaper type"), printer.shaperTypes, type) { type = it }
        Note(firmwareNote(printer, "Check firmware compatibility."))
        SectionLabel(orcaString("Frequency settings"))
        if (reprap) {
            NumberField(orcaString("Frequency: "), frequencyX, hz) { frequencyX = it }
            // The field's tooltip.
            Note(orcaString("RepRap firmware uses the same frequency for both axes."))
        } else {
            RangeFields(orcaString("Frequency") + " X / Y: ", frequencyX, frequencyY, hz, { frequencyX = it }, { frequencyY = it })
        }
        RangeFields(orcaString("Damp") + " " + orcaString("Start / End") + ": ", dampingStart, dampingEnd, null, { dampingStart = it }, { dampingEnd = it })
        Note(orcaString("Note: Use previously calculated frequencies."))
    }
    message?.let { text -> CalibrationMessage(text) { message = null } }
}

/**
 * Cornering_Test_Dlg, as a sheet: the test model and the jerk to go from and
 * to, or the junction deviation for Marlin 2 with one, with the notes on the
 * firmware. OK wants a range from 0 up to 100 mm/s (0.3 mm), and warns of
 * layer shifts above 20 mm/s (0.25 mm) before the test starts.
 */
@Composable
internal fun CorneringSheet(printer: CalibrationPrinter, onDismiss: () -> Unit, onStart: (CalibrationParams) -> Unit) {
    val junctionDeviation = printer.junctionDeviation
    var model by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf(if (junctionDeviation) "0.000" else "1.000") }
    var end by remember { mutableStateOf(if (junctionDeviation) "0.250" else "15.000") }
    var message by remember { mutableStateOf<String?>(null) }
    var warned by remember { mutableStateOf<CalibrationParams?>(null) }
    val maxEnd = if (junctionDeviation) 0.3 else 100.0
    val warningThreshold = if (junctionDeviation) 0.25 else 20.0
    val invalid = orcaText(OrcaText("Please input valid values:\n(0 <= Cornering <= %s)", listOf("%.3f".format(Locale.ROOT, maxEnd))))
    val warning = orcaText(OrcaText("NOTE: High values may cause Layer shift (>%s)", listOf("%.3f".format(Locale.ROOT, warningThreshold))))
    val unit = if (junctionDeviation) "mm" else "mm/s"
    TestSheet(
        title = orcaString("Cornering test"),
        guide = CORNERING_GUIDE,
        onDismiss = onDismiss,
        onOk = {
            val from = start.toDoubleOrNull()
            val to = end.toDoubleOrNull()
            if (from == null || to == null || from < 0 || to > maxEnd || from >= to) {
                message = invalid
            } else {
                val params = CalibrationParams(CalibrationMode.CORNERING, start = from, end = to, testModel = model)
                if (to > warningThreshold) warned = params else onStart(params)
            }
        },
    ) {
        Choices(orcaString("Test model"), CORNERING_MODELS.map { orcaString(it) }, model) { model = it }
        SectionLabel(orcaString("Cornering settings"))
        NumberField(orcaString("Start: "), start, unit) { start = it }
        NumberField(orcaString("End: "), end, unit) { end = it }
        Note(orcaString("Note: Lower values = sharper corners but slower speeds."))
        when (printer.gcodeFlavor) {
            FLAVOR_MARLIN -> Note(
                orcaString(
                    if (junctionDeviation) {
                        "Marlin 2 Junction Deviation detected:\nTo test Classic Jerk, set 'Maximum Junction Deviation' in Motion ability to 0."
                    } else {
                        "Marlin 2 Classic Jerk detected:\nTo test Junction Deviation, set 'Maximum Junction Deviation' in Motion ability to a value > 0."
                    },
                ),
            )
            FLAVOR_REPRAP -> Note(orcaString("RepRap detected: Jerk in mm/s.\nOrcaSlicer will convert the values to mm/min when necessary."))
        }
    }
    message?.let { text -> CalibrationMessage(text) { message = null } }
    // The warning informs; the test starts once it is closed.
    warned?.let { params ->
        CalibrationMessage(warning) {
            warned = null
            onStart(params)
        }
    }
}

/** A sheet of a test's dialog, a dialog on a larger window: its title, its content, and the guide and OK at the bottom. */
@Composable
private fun TestSheet(title: String, guide: String, onDismiss: () -> Unit, onOk: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = CALIBRATION_DIALOG_WIDTH, skipPartiallyExpanded = true) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = colors.text, style = OrcaTheme.typography.head16)
            content()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OrcaButton(orcaString("Wiki Guide"), onClick = { uriHandler.openUri(guide) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("OK"), onClick = onOk)
            }
        }
    }
}

/** A titled group of a dialog's radio buttons, as rows of the sheet. */
@Composable
private fun Choices(title: String, items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SectionLabel(title)
    items.forEachIndexed { index, item -> ChoiceRow(item, selected = selected == index, onSelect = { onSelect(index) }) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = OrcaTheme.colors.textLabel, style = OrcaTheme.typography.body14, modifier = Modifier.padding(top = 8.dp))
}

/** The grey note of a dialog under its fields. */
@Composable
private fun Note(text: String) {
    Text(text, color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(vertical = 4.dp))
}

/** A figure's start and end of a dialog, the label above the two fields. */
@Composable
private fun RangeFields(label: String, first: String, second: String, unit: String?, onFirst: (String) -> Unit, onSecond: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(first to onFirst, second to onSecond).forEach { (value, onChange) ->
                OrcaTextField(
                    value = value,
                    onValueChange = { text -> onChange(text.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }.take(8)) },
                    unit = unit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** MessageDialog(wxICON_WARNING | wxOK) of a test's dialog. */
@Composable
private fun CalibrationMessage(text: String, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        text = { Text(text, style = OrcaTheme.typography.body14) },
        containerColor = colors.window,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

private const val INPUT_SHAPING_GUIDE = "https://www.orcaslicer.com/wiki/input_shaping_calib"
private const val CORNERING_GUIDE = "https://www.orcaslicer.com/wiki/cornering_calib"

/** FlowRateCalibrationDialog's choices, which the dialog keeps from one showing to the next. */
internal data class FlowRateChoice(val type: Int = FLOW_YOLO, val pattern: Int = 0)

/** The test types of FlowRateCalibrationDialog, in its order. */
private val FLOW_TYPES = listOf("Pass 1 (Coarse)", "Pass 2 (Fine)", "YOLO (Recommended)", "YOLO (Perfectionist)")
private const val FLOW_YOLO = 2

/** Its top surface patterns: the icon, the label and the InfillPattern. */
private val FLOW_PATTERNS = listOf(
    Triple("param_archimedeanchords", "Archimedean Chords", "archimedeanchords"),
    Triple("param_monotonic", "Monotonic", "monotonic"),
)

/**
 * FlowRateCalibrationDialog, as a sheet: the test type (the coarse or fine
 * pass, or a YOLO test) and the top surface pattern, each a list of choices
 * as the dialog's radio group and combo box offer them; OK starts the test.
 */
@Composable
internal fun FlowRateSheet(
    choice: FlowRateChoice,
    onChoice: (FlowRateChoice) -> Unit,
    onDismiss: () -> Unit,
    onStart: (FlowRateCalibration) -> Unit,
) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = CALIBRATION_DIALOG_WIDTH, skipPartiallyExpanded = true) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(orcaString("Flow Ratio Calibration"), color = colors.text, style = OrcaTheme.typography.head16)
            Text(
                orcaString("Calibration Test Type"),
                color = colors.textLabel,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.padding(top = 8.dp),
            )
            FLOW_TYPES.forEachIndexed { index, type ->
                ChoiceRow(orcaString(type), selected = choice.type == index, onSelect = { onChoice(choice.copy(type = index)) })
            }
            Text(
                orcaString("Top Surface Pattern"),
                color = colors.textLabel,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.padding(top = 8.dp),
            )
            FLOW_PATTERNS.forEachIndexed { index, (icon, label, _) ->
                ChoiceRow(orcaString(label), selected = choice.pattern == index, icon = orcaIcon(icon), onSelect = { onChoice(choice.copy(pattern = index)) })
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OrcaButton(orcaString("Wiki Guide"), onClick = { uriHandler.openUri(FLOW_RATE_GUIDE) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("OK"), onClick = {
                    // on_start(): the YOLO tests are linear, and every other type is the second pass.
                    onStart(FlowRateCalibration(linear = choice.type >= 2, pass = choice.type % 2 + 1, topSurfacePattern = FLOW_PATTERNS[choice.pattern].third))
                })
            }
        }
    }
}

/** A choice of a sheet's list, with its icon when it has one: the whole row is the touch target. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, onSelect: () -> Unit, icon: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrcaRadioButton(selected = selected, onClick = null)
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
        }
        Text(text, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14)
    }
}

private const val FLOW_RATE_GUIDE = "https://www.orcaslicer.com/wiki/flow_ratio_calib"

/** PA_Calibration_Dlg's choices, which the dialog keeps from one showing to the next. */
internal data class PressureAdvanceChoice(val bowden: Boolean = false, val method: Int = PA_TOWER)

/** Temp_Calibration_Dlg's filament type and temperatures. */
internal data class TemperatureFigures(val type: Int = 0, val start: String = "230", val end: String = "190")

/** The start, end and step of a [RangeTest]'s dialog. */
internal data class RangeFigures(val start: String, val end: String, val step: String)

/**
 * MainFrame makes the dialogs of the temperature, volumetric speed, PA, flow
 * ratio, retraction and VFA tests once (m_temp_calib_dlg and the others), so
 * what they show stays from one opening to the next while the app runs; the
 * Input Shaping and Cornering dialogs are made again each time.
 */
internal object CalibrationDialogs {
    var temperature = TemperatureFigures()
    val ranges = mutableMapOf<RangeTest, RangeFigures>()
    var pressureAdvance by mutableStateOf(PressureAdvanceChoice())
    var accelerations = ""
    var speeds = ""
    var flowRate by mutableStateOf(FlowRateChoice())
}

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
    // on_show() sets the figures again; the lists stay as they were left.
    var accelerations by remember { mutableStateOf(CalibrationDialogs.accelerations) }
    var speeds by remember { mutableStateOf(CalibrationDialogs.speeds) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            CalibrationDialogs.accelerations = accelerations
            CalibrationDialogs.speeds = speeds
        }
    }
    val invalidMessage = orcaString("Please input valid values:\nStart PA: >= 0.0\nEnd PA: > Start PA\nPA step: >= 0.001")
    val swappedMessage = orcaString("Acceleration values must be greater than speed values.\nPlease verify the inputs.")

    fun choose(next: PressureAdvanceChoice) {
        onChoice(next)
        start = "0"
        end = endOf(next)
        step = stepOf(next)
        printNumbers = next.method != PA_TOWER
    }

    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = CALIBRATION_DIALOG_WIDTH, skipPartiallyExpanded = true) {
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
                    // ParseStringValues(): the numbers of the comma-separated list above zero.
                    val accels = accelerations.split(',').mapNotNull { it.trim().toDoubleOrNull()?.takeIf { value -> value > 0 } }
                    val speedList = speeds.split(',').mapNotNull { it.trim().toDoubleOrNull()?.takeIf { value -> value > 0 } }
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
