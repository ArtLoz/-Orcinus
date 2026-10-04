package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.KeyboardType
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeKind
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameCheck
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNameValidation
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import kotlinx.coroutines.delay

/** A message box OrcaSlicer showed while it applied a change: OK dismisses it. */
@Composable
fun SettingsNoticeDialog(dialog: SettingsDialog, onDismiss: () -> Unit) {
    SettingsAlert(
        dialog = dialog,
        onDismissRequest = onDismiss,
        confirm = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
    )
}

/**
 * A question OrcaSlicer asks before it applies a change. It must be answered,
 * as the desktop app's modal box must: the change waits for the answer.
 */
@Composable
fun SettingsQuestionDialog(dialog: SettingsDialog, onAnswer: (Boolean) -> Unit) =
    SettingsQuestionDialog(dialog, onAnswerChecked = { yes, _ -> onAnswer(yes) })

/**
 * A question with RichMessageDialog's check box under it when it has one
 * ([SettingsDialog.checkbox]): the answer comes with the box's state.
 */
@Composable
fun SettingsQuestionDialog(dialog: SettingsDialog, onAnswerChecked: (yes: Boolean, checked: Boolean) -> Unit) {
    var checked by rememberSaveable(dialog) { mutableStateOf(dialog.checked) }
    SettingsAlert(
        dialog = dialog,
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        confirm = { OrcaButton(dialog.yes?.let { orcaText(it) } ?: orcaString("Yes"), onClick = { onAnswerChecked(true, checked) }) },
        dismiss = {
            OrcaButton(dialog.no?.let { orcaText(it) } ?: orcaString("No"), onClick = { onAnswerChecked(false, checked) }, style = OrcaButtonStyle.Regular)
        },
        below = dialog.checkbox?.let { label ->
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { checked = it }),
                ) {
                    OrcaCheckBox(checked = checked, onCheckedChange = null)
                    Text(orcaText(label), style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    )
}

@Composable
private fun SettingsAlert(
    dialog: SettingsDialog,
    onDismissRequest: () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    /** What stands under the message, such as a check box. */
    below: (@Composable () -> Unit)? = null,
) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirm,
        dismissButton = dismiss,
        title = dialog.title.takeIf { it.isNotEmpty() }?.let { title -> { Text(orcaText(title), style = OrcaTheme.typography.head16) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(orcaText(dialog.text).trim(), style = OrcaTheme.typography.body14)
                below?.invoke()
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
        properties = properties,
    )
}

/** The tooltip of a setting's field (get_formatted_tooltip_text), shown when its label is tapped; null while it loads. */
@Composable
fun SettingTooltipDialog(label: String, tooltip: List<OrcaText>?, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(label, style = OrcaTheme.typography.head16) },
        text = {
            if (tooltip == null) {
                CircularProgressIndicator(color = colors.accent)
            } else {
                Text(orcaText(tooltip), style = OrcaTheme.typography.body14, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * SavePresetDialog: the name, checked as it is typed, with why it cannot be
 * used or what saving it overwrites. OK saves a name that is not invalid.
 */
@Composable
fun SavePresetDialog(
    kind: PresetKind,
    suggestedName: String,
    checkName: suspend (String) -> PresetNameOutcome,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    var validation by remember { mutableStateOf<PresetNameValidation?>(null) }
    var checkedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        // SavePresetDialog::Item::update() runs on every change of the text.
        delay(CHECK_DELAY_MILLIS)
        validation = when (val outcome = checkName(name)) {
            is PresetNameOutcome.Success -> outcome.validation
            is PresetNameOutcome.Failure -> PresetNameValidation(PresetNameCheck.INVALID, listOf(OrcaText(outcome.message)))
        }
        checkedName = name
    }
    val current = validation.takeIf { checkedName == name }
    val canSave = current != null && current.check != PresetNameCheck.INVALID
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onSave(name) }, enabled = canSave) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Save preset"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaText(OrcaText("Save %s as", listOf(kind.tabTitle), translateArgs = true)), style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name) }),
                )
                current?.info?.takeIf { it.isNotEmpty() }?.let { info ->
                    Text(
                        orcaText(info),
                        // SavePresetDialog's info label colour.
                        color = colors.secondary,
                        style = OrcaTheme.typography.body13,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
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

/**
 * The presets a preset is compatible with (Tab::create_compatible_widget):
 * every one of them, or the chosen ones. The choices come from the engine while
 * the dialog is open.
 */
@Composable
fun CompatiblePresetsDialog(
    title: String,
    selected: List<String>,
    loadChoices: suspend () -> PresetNamesOutcome,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var choices by remember { mutableStateOf<List<String>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf(selected.toSet()) }
    LaunchedEffect(Unit) {
        when (val outcome = loadChoices()) {
            is PresetNamesOutcome.Success -> choices = outcome.names
            is PresetNamesOutcome.Failure -> problem = outcome.message
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onApply(chosen.toList()) }) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString(title), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // "All presets are compatible": no preset is chosen.
                PresetChoiceRow(
                    label = orcaString("All"),
                    checked = chosen.isEmpty(),
                    onCheckedChange = { if (it) chosen = emptySet() },
                )
                when {
                    problem != null -> Text(problem.orEmpty(), color = colors.error, style = OrcaTheme.typography.body13)
                    choices == null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(8.dp))
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(choices.orEmpty()) { preset ->
                            PresetChoiceRow(
                                label = preset,
                                checked = preset in chosen,
                                onCheckedChange = { chosen = if (it) chosen + preset else chosen - preset },
                            )
                        }
                    }
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

@Composable
private fun PresetChoiceRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .orcaClickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * The colour of a setting (ColourPicker): the filament colours OrcaSlicer
 * offers, and any other colour as "#RRGGBB".
 */
@Composable
fun SettingColorDialog(value: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf(value) }
    val picked = parseColor(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onPick(text) }, enabled = picked != null) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Color"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                for (row in FILAMENT_COLORS.chunked(6)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (color in row) {
                            val chosen = parseColor(color) == picked
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(parseColor(color) ?: colors.window, OrcaTheme.shapes.control)
                                    .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.accent else colors.border, OrcaTheme.shapes.control)
                                    .orcaClickable { text = color },
                            )
                        }
                    }
                }
                OrcaTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (picked == null) {
                    Text("#RRGGBB", color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** Tab::title(): the name the save dialog and the settings page show. */
val PresetKind.tabTitle: String
    get() = when (this) {
        PresetKind.PRINT -> "Process"
        PresetKind.FILAMENT -> "Filament"
        PresetKind.PRINTER -> "Machine"
        // The titles of the object list, which names the settings of an object
        // and of the plate (ObjectDataViewModel).
        PresetKind.OBJECT -> "Object"
        PresetKind.PLATE -> "Plate"
        PresetKind.PART -> "Part"
        PresetKind.LAYER -> "Layer"
    }

/** The filament colours of OrcaSlicer's colour picker (PresetComboBox's custom colours). */
private val FILAMENT_COLORS = listOf(
    "#FFFFFF", "#D3D3D3", "#808080", "#404040", "#000000", "#8B4513",
    "#FF0000", "#FF6600", "#FFFF00", "#00FF00", "#008000", "#00FFFF",
    "#0000FF", "#000080", "#800080", "#FF00FF", "#FFC0CB", "#C0C0C0",
)

private const val CHECK_DELAY_MILLIS = 150L
