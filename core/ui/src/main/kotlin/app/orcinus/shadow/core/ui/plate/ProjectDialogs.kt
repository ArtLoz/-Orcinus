package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.PresetChangesAnswer
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.ProjectPrompt
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.settings.PresetChangeRow
import app.orcinus.shadow.core.ui.settings.SavePresetDialog

/**
 * Plater::close_with_confirm()'s message box before a new project or another
 * one: Yes saves the project first, No goes on without it, Cancel stays; with
 * "Remember my choice." (show_dsa_button) Yes or No is kept for the next time.
 */
@Composable
fun ProjectSaveChangesDialog(onAnswer: (save: Boolean?, remember: Boolean) -> Unit) {
    val colors = OrcaTheme.colors
    var remember by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onAnswer(null, false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(orcaString("Save"), style = OrcaTheme.typography.head16) },
        text = {
            Column {
                Text(orcaString("The current project has unsaved changes, save it before continue?"), style = OrcaTheme.typography.body14)
                RememberChoice(remember) { remember = it }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrcaButton(orcaString("Yes"), onClick = { onAnswer(true, remember) })
                OrcaButton(orcaString("No"), onClick = { onAnswer(false, remember) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("Cancel"), onClick = { onAnswer(null, false) }, style = OrcaButtonStyle.Regular)
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * UnsavedChangesDialog for a project (no dependent presets): every preset with
 * unsaved changes and its changes; Transfer keeps them for a new project,
 * Save saves them (SavePresetDialog asks the names of the presets that cannot
 * be overwritten, one after another on a phone), Discard lets them go. A
 * project asks it with REMEMBER_CHOISE: "Remember my choice." keeps the action.
 */
@Composable
fun ProjectPresetChangesDialog(
    prompt: ProjectPrompt.PresetChanges,
    checkName: suspend (PresetKind, String) -> PresetNameOutcome,
    onAnswer: (answer: PresetChangesAnswer?, remember: Boolean) -> Unit,
) {
    val colors = OrcaTheme.colors
    // SavePresetDialog for the presets that need a name, in their order.
    val naming = prompt.presets.filterNot { it.canOverwrite }
    var saving by rememberSaveable { mutableStateOf(false) }
    var names by rememberSaveable { mutableStateOf(mapOf<PresetKind, String>()) }
    var remember by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onAnswer(null, false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(orcaText(prompt.caption), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(prompt.header.map { orcaText(it) }.joinToString(""), style = OrcaTheme.typography.body14)
                Text(
                    orcaString("You have previously modified your settings.") + orcaString(
                        if (prompt.transfer) {
                            "\nYou can discard the preset values you have modified, or choose to transfer the modified values to the new project"
                        } else {
                            "\nYou can save or discard the preset values you have modified."
                        },
                    ),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    prompt.presets.forEach { preset ->
                        item(key = preset.kind) {
                            Text(
                                "${orcaString(kindLabel(preset.kind))}: ${preset.name}",
                                style = OrcaTheme.typography.head14,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(preset.changes) { PresetChangeRow(it) }
                    }
                }
                RememberChoice(remember) { remember = it }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prompt.transfer) {
                    OrcaButton(orcaString("Transfer"), onClick = { onAnswer(PresetChangesAnswer.Transfer, remember) }, style = OrcaButtonStyle.Regular)
                }
                if (prompt.save) {
                    OrcaButton(
                        orcaString("Save"),
                        onClick = { if (naming.isEmpty()) onAnswer(PresetChangesAnswer.Save(emptyMap()), remember) else saving = true },
                        style = OrcaButtonStyle.Regular,
                    )
                }
                OrcaButton(orcaString("Discard"), onClick = { onAnswer(PresetChangesAnswer.Discard, remember) })
                OrcaButton(orcaString("Cancel"), onClick = { onAnswer(null, false) }, style = OrcaButtonStyle.Regular)
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
    val next = naming.firstOrNull { it.kind !in names }
    if (saving && next != null) {
        val copy = orcaString("Copy", context = "PresetName")
        SavePresetDialog(
            kind = next.kind,
            suggestedName = if (next.saveNameCopySuffix) "${next.saveName} - $copy" else next.saveName,
            checkName = { name -> checkName(next.kind, name) },
            onSave = { name ->
                val chosen = names + (next.kind to name)
                names = chosen
                if (naming.all { it.kind in chosen }) {
                    saving = false
                    onAnswer(PresetChangesAnswer.Save(chosen), remember)
                }
            },
            onDismiss = {
                // SavePresetDialog's Cancel: nothing is saved, the dialog stays.
                saving = false
                names = emptyMap()
            },
        )
    }
}

/** "Remember my choice." under a question (MessageDialog's DSA check box). */
@Composable
private fun RememberChoice(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 8.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = null)
        Text(orcaString("Remember my choice."), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The tab a preset of [kind] belongs to, as OrcaSlicer names it. */
private fun kindLabel(kind: PresetKind): String = when (kind) {
    PresetKind.FILAMENT -> "Filament"
    PresetKind.PRINTER -> "Printer"
    else -> "Process"
}
