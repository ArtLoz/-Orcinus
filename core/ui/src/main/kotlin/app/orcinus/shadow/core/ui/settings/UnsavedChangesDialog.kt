package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PendingPresetChange
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText

/** What the unsaved-changes dialog asks of the app (UnsavedChangesDialog's buttons). */
class PresetChangeActions(
    val resolve: (PresetChangeAction) -> Unit,
    val save: (String) -> Unit,
    val cancel: () -> Unit,
)

/**
 * The dialog of the preset choice that waits, if one does: the app keeps it in
 * the plate, so whichever screen changes a preset shows it.
 */
@Composable
fun PresetChangeDialog(change: PendingPresetChange?, presets: Presets?, actions: PresetChangeActions, settings: SettingsActions) {
    if (change == null) return
    UnsavedChangesDialog(
        change = change,
        presetName = presets?.selectedLabel(change.kind).orEmpty(),
        checkName = { name -> settings.checkPresetName(change.kind, name) },
        onTransfer = { actions.resolve(PresetChangeAction.TRANSFER) },
        onDiscard = { actions.resolve(PresetChangeAction.DISCARD) },
        onSave = actions.save,
        onCancel = actions.cancel,
    )
}

/** The preset of a kind the app has selected, as its combo box shows it. */
fun Presets.selectedLabel(kind: PresetKind): String = when (kind) {
    PresetKind.FILAMENT -> filaments
    PresetKind.PRINTER -> printers
    // The settings of an object and of the plate override the process preset.
    else -> processes
}.firstOrNull { it.selected }?.label.orEmpty()

/**
 * OrcaSlicer's UnsavedChangesDialog: the preset that is left behind was
 * modified, and the changes are saved under a name, moved to the preset that is
 * selected, or lost. Cancel selects nothing. Every change is listed with the
 * value before and after it, in the page and group of its setting.
 */
@Composable
fun UnsavedChangesDialog(
    change: PendingPresetChange,
    presetName: String,
    checkName: suspend (String) -> PresetNameOutcome,
    onTransfer: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var saving by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(orcaString("Transfer or discard changes"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    orcaText(OrcaText("You have changed some settings of preset \"%1%\".", listOf(presetName))),
                    style = OrcaTheme.typography.body14,
                )
                Text(
                    orcaString(
                        if (change.canTransfer) {
                            "\nYou can save or discard the preset values you have modified, or choose to transfer the values you have modified to the new preset."
                        } else {
                            "\nYou can save or discard the preset values you have modified."
                        },
                    ),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(change.changes) { PresetChangeRow(it) }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrcaButton(orcaString("Save"), onClick = { saving = true }, style = OrcaButtonStyle.Regular)
                if (change.canTransfer) {
                    OrcaButton(orcaString("Transfer"), onClick = onTransfer, style = OrcaButtonStyle.Regular)
                }
                OrcaButton(orcaString("Discard"), onClick = onDiscard)
                OrcaButton(orcaString("Cancel"), onClick = onCancel, style = OrcaButtonStyle.Regular)
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
    if (saving) {
        val copy = orcaString("Copy", context = "PresetName")
        SavePresetDialog(
            kind = change.kind,
            suggestedName = if (change.saveNameCopySuffix) "${change.saveName} - $copy" else change.saveName,
            checkName = checkName,
            onSave = { name ->
                saving = false
                onSave(name)
            },
            onDismiss = { saving = false },
        )
    }
}

/** One changed value: where its setting sits, and the value before and after. */
@Composable
internal fun PresetChangeRow(change: PresetChange) {
    val colors = OrcaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = listOf(orcaText(change.category), orcaText(change.group)).filter(String::isNotEmpty).joinToString(" • "),
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
        )
        Text(orcaText(change.label), color = colors.text, style = OrcaTheme.typography.body14)
        Text(
            text = orcaText(change.oldValue) + "  →  " + orcaText(change.newValue),
            color = colors.labelModified,
            style = OrcaTheme.typography.body13,
        )
        HorizontalDivider(color = colors.separator, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
    }
}
