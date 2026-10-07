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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PendingPresetChange
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetSave
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText

/** What the unsaved-changes dialog asks of the app (UnsavedChangesDialog's buttons). */
class PresetChangeActions(
    val resolve: (PresetChangeAction) -> Unit,
    val save: (PresetSave) -> Unit,
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
        presetName = change.presetName.ifEmpty { presets?.selectedLabel(change.kind).orEmpty() },
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
 * OrcaSlicer's UnsavedChangesDialog: the preset that is left behind, or one
 * that depends on it, was modified, and the changes are saved under a name,
 * moved to the preset that is selected, or lost. Cancel selects nothing, unless
 * the dependent preset suits the new one. Every change is listed with the value
 * before and after it, in the page and group of its setting.
 */
@Composable
fun UnsavedChangesDialog(
    change: PendingPresetChange,
    presetName: String,
    checkName: suspend (String) -> PresetNameOutcome,
    onTransfer: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (PresetSave) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = OrcaTheme.colors
    val uriHandler = LocalUriHandler.current
    var saving by rememberSaveable { mutableStateOf(false) }
    var droppingVariants by rememberSaveable { mutableStateOf(false) }
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
                OrcaLink(orcaString("Help"), onClick = { runCatching { uriHandler.openUri(TRANSFER_DISCARD_HELP) } })
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // UnsavedChangesDialog::save(): only a preset that cannot be overwritten asks a name.
                OrcaButton(
                    orcaString("Save"),
                    onClick = { if (change.saveCanOverwrite) onSave(PresetSave(change.saveName)) else saving = true },
                    style = OrcaButtonStyle.Regular,
                )
                if (change.canTransfer) {
                    OrcaButton(
                        orcaString("Transfer"),
                        onClick = { if (change.transferDropsVariants) droppingVariants = true else onTransfer() },
                        style = OrcaButtonStyle.Regular,
                    )
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
    if (droppingVariants) {
        // Tab::may_discard_current_dirty_preset() with no_transfer_variant: the
        // message box only informs, and the Transfer goes on however it closes.
        val proceed = {
            droppingVariants = false
            onTransfer()
        }
        AlertDialog(
            onDismissRequest = proceed,
            title = { Text(orcaString("Use Modified Value"), style = OrcaTheme.typography.head16) },
            text = {
                Text(
                    orcaString(
                        "Switching to a printer with different extruder types or numbers will discard or reset changes to extruder or multi-nozzle-related parameters.",
                    ),
                    style = OrcaTheme.typography.body14,
                )
            },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = proceed) },
            containerColor = colors.window,
            titleContentColor = colors.text,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
    if (saving) {
        val copy = orcaString("Copy", context = "PresetName")
        SavePresetDialog(
            kind = change.kind,
            suggestedName = if (change.saveNameCopySuffix) "${change.saveName} - $copy" else change.saveName,
            checkName = checkName,
            onSave = { save ->
                saving = false
                onSave(save)
            },
            onDismiss = { saving = false },
        )
    }
}

/** The HyperLink "Help" of UnsavedChangesDialog::build() for a preset that is switched. */
private const val TRANSFER_DISCARD_HELP = "https://www.orcaslicer.com/wiki/transfer_discard_changes"

/**
 * One changed value: where its setting sits, and the value before and after.
 * A value too long for the row is cut short (DiffViewCtrl::Append()), and the
 * row opens FullCompareDialog on it, as the desktop tree's right click does.
 */
@Composable
internal fun PresetChangeRow(change: PresetChange) {
    val colors = OrcaTheme.colors
    val label = orcaText(change.label)
    val oldValue = orcaText(change.oldValue)
    val newValue = orcaText(change.newValue)
    val long = FullCompare.isLong(oldValue, newValue)
    var comparing by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (long) Modifier.orcaClickable(role = Role.Button, onClickLabel = stringResource(R.string.full_compare)) { comparing = true } else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = listOf(orcaText(change.category), orcaText(change.group)).filter(String::isNotEmpty).joinToString(" • "),
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
        )
        Text(label, color = colors.text, style = OrcaTheme.typography.body14)
        Text(
            text = FullCompare.shortValue(oldValue) + "  →  " + FullCompare.shortValue(newValue),
            color = colors.labelModified,
            style = OrcaTheme.typography.body13,
        )
        HorizontalDivider(color = colors.separator, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
    }
    if (comparing) {
        // The headers of UnsavedChangesDialog's value columns.
        FullCompareDialog(label, oldValue, newValue, orcaString("Old Value"), orcaString("New Value"), onDismiss = { comparing = false })
    }
}
