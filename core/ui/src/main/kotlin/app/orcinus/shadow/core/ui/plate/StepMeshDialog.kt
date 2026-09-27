package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.StepMeshChoice
import app.orcinus.shadow.core.model.StepMeshOptions
import app.orcinus.shadow.core.model.StepMeshQuestion
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.openInBrowser
import java.util.Locale
import kotlin.math.roundToInt

/**
 * StepMeshDialog: the linear and angle deflections a STEP file is meshed with,
 * each a slider with its field (0.001–0.1 and 0.01–1), whether its compounds
 * split into objects, and the triangles the file makes with them, counted
 * again when a slider is let go or a field is left ([countTriangles] gives 0
 * for a count that was stopped, which leaves "Calculating, please wait...").
 * A field left with a value out of range says so and takes its last value
 * back. OK answers with the values, "Don't show again" turning the dialog off;
 * Cancel and closing the dialog answer null.
 */
@Composable
fun StepMeshDialog(
    question: StepMeshQuestion,
    countTriangles: suspend (linear: Double, angle: Double) -> Long,
    onAnswer: (StepMeshChoice?) -> Unit,
) {
    val colors = OrcaTheme.colors
    val context = LocalContext.current
    val initial = question.options
    // m_linear_last and m_angle_last: the values the dialog works with.
    var linearLast by remember { mutableStateOf(format(initial.linearDeflection, 3)) }
    var angleLast by remember { mutableStateOf(format(initial.angleDeflection, 2)) }
    var linearText by remember { mutableStateOf(linearLast) }
    var angleText by remember { mutableStateOf(angleLast) }
    var split by remember { mutableStateOf(initial.splitCompound) }
    var dontShowAgain by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf<String?>(null) }
    // update_mesh_number_text(): the values counted last, and their count.
    var counted by remember { mutableStateOf(linearLast.toDouble() to angleLast.toDouble()) }
    var count by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(counted) {
        count = null
        count = countTriangles(counted.first, counted.second).takeIf { it != 0L }
    }
    val linear = Deflection(
        title = "Linear Deflection",
        text = linearText,
        slider = SLIDER_LINEAR,
        decimals = 3,
        range = 0.001..0.1,
        warning = "Please input a valid value (0.001 < linear deflection < 0.1)",
        onText = { linearText = it },
        onValue = { linearText = it; linearLast = it },
        onLeft = { valid ->
            if (valid) linearLast = linearText else linearText = linearLast
            counted = linearLast.toDouble() to angleLast.toDouble()
        },
    )
    val angle = Deflection(
        title = "Angle Deflection",
        text = angleText,
        slider = SLIDER_ANGLE,
        decimals = 2,
        range = 0.01..1.0,
        warning = "Please input a valid value (0.01 < angle deflection < 1.0)",
        onText = { angleText = it },
        onValue = { angleText = it; angleLast = it },
        onLeft = { valid ->
            if (valid) angleLast = angleText else angleText = angleLast
            counted = linearLast.toDouble() to angleLast.toDouble()
        },
    )
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(orcaString("Step file import parameters"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.sidebarBackground, OrcaTheme.shapes.control)
                        .padding(10.dp),
                ) {
                    Text(
                        orcaString("Smaller linear and angular deflections result in higher-quality transformations but increase the processing time."),
                        style = OrcaTheme.typography.body14,
                    )
                    OrcaLink(orcaString("Wiki Guide"), onClick = { openInBrowser(context, WIKI_GUIDE) })
                }
                DeflectionRow(linear) { warning = it }
                DeflectionRow(angle) { warning = it }
                CheckRow(orcaString("Split compound and compsolid into multiple objects"), split) { split = it }
                Text(
                    orcaString("Number of triangular facets") + ": " + (count?.toString() ?: orcaString("Calculating, please wait...")),
                    style = OrcaTheme.typography.body14,
                )
                CheckRow(orcaString("Don't show again"), dontShowAgain) { dontShowAgain = it }
            }
        },
        confirmButton = {
            OrcaButton(
                text = orcaString("OK"),
                onClick = {
                    // The fields leave their focus before OK takes their values.
                    val linearValue = linearText.toDoubleOrNull()?.takeIf { it in linear.range }
                    val angleValue = angleText.toDoubleOrNull()?.takeIf { it in angle.range }
                    if (linearValue != null && angleValue != null) {
                        onAnswer(StepMeshChoice(StepMeshOptions(linearValue, angleValue, split), dontShowAgain))
                    }
                },
            )
        },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = { onAnswer(null) }, style = OrcaButtonStyle.Regular) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
    warning?.let { message ->
        AlertDialog(
            onDismissRequest = { warning = null },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = { warning = null }) },
            text = { Text(orcaString(message), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** A deflection of the dialog: its slider's scale, its field's decimals and range, and what changes it. */
private class Deflection(
    val title: String,
    val text: String,
    /** SLIDER_SCALE: the value of one step of the slider, which runs from 1 to 100 steps. */
    val slider: Double,
    val decimals: Int,
    val range: ClosedFloatingPointRange<Double>,
    val warning: String,
    val onText: (String) -> Unit,
    val onValue: (String) -> Unit,
    /** The field or the slider was left; valid tells whether the field's value is in range. */
    val onLeft: (valid: Boolean) -> Unit,
)

@Composable
private fun DeflectionRow(deflection: Deflection, onWarning: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // The slider follows the field while its text is a value in the slider's range.
    val steps = deflection.text.toDoubleOrNull()?.let { (it / deflection.slider).roundToInt() }?.takeIf { it in 1..100 }
    var position by remember { mutableStateOf((steps ?: 1).toFloat()) }
    if (steps != null && steps.toFloat() != position) position = steps.toFloat()
    Column {
        Text(orcaString(deflection.title) + ": ", style = OrcaTheme.typography.body14)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = position,
                onValueChange = {
                    position = it
                    deflection.onValue(format(it.roundToInt() * deflection.slider, deflection.decimals))
                },
                onValueChangeFinished = { deflection.onLeft(true) },
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = OrcaTheme.colors.accent,
                    activeTrackColor = OrcaTheme.colors.accent,
                    inactiveTrackColor = OrcaTheme.colors.separator,
                ),
                modifier = Modifier.weight(1f),
            )
            OrcaTextField(
                value = deflection.text,
                onValueChange = deflection.onText,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(80.dp)
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused) {
                            val valid = deflection.text.toDoubleOrNull()?.let { it in deflection.range } == true
                            if (!valid) onWarning(deflection.warning)
                            deflection.onLeft(valid)
                        }
                        focused = state.isFocused
                    },
            )
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = null)
        Text(text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 8.dp))
    }
}

/** wxString::Format("%.3f") and "%.2f", with a point whatever the language. */
private fun format(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)

// StepMeshDialog.cpp: SLIDER_SCALE and SLIDER_SCALE_10.
private const val SLIDER_LINEAR = 0.001
private const val SLIDER_ANGLE = 0.01
private const val WIKI_GUIDE = "https://www.orcaslicer.com/wiki/import_export#step"
