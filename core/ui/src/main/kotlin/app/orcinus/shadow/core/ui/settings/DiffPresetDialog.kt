package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetKindComparison
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.PresetTransfer
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.preset.PresetListSheet
import app.orcinus.shadow.core.designsystem.R as DesignR

/** What DiffPresetDialog asks of the app. */
class PresetComparisonActions(
    /** DiffPresetDialog::update_tree(): the presets of either side, and what they differ in. */
    val compare: suspend (left: ComparedPresets, right: ComparedPresets, showAll: Boolean) -> PresetComparisonOutcome,
    /** Its Transfer button: the chosen values move into the right presets. */
    val transfer: (List<PresetTransfer>) -> Unit,
) {
    companion object {
        val NONE = PresetComparisonActions(
            compare = { _, _, _ -> PresetComparisonOutcome.Failure("") },
            transfer = {},
        )
    }
}

/**
 * OrcaSlicer's DiffPresetDialog, which the compare button of the process panel
 * opens: the printer, the filament and the process, each with a preset on the
 * left and one on the right, and the settings they differ in under the page and
 * the group each setting sits in. With "Transfer values from left to right" the
 * settings are picked and their values move into the right presets.
 *
 * The desktop dialog is a tree with a column per value; on a phone the two
 * values of a setting sit under its name, and the tree's rows are a list's.
 */
@Composable
fun DiffPresetDialog(actions: PresetComparisonActions, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    var left by rememberSaveable(stateSaver = COMPARED_PRESETS_SAVER) { mutableStateOf(ComparedPresets()) }
    var right by rememberSaveable(stateSaver = COMPARED_PRESETS_SAVER) { mutableStateOf(ComparedPresets()) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var useForTransfer by rememberSaveable { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PresetComparisonOutcome?>(null) }
    // Which preset list is open: the kind of the combo box, and whether it is the left one.
    var choosing by remember { mutableStateOf<Pair<PresetKind, Boolean>?>(null) }
    LaunchedEffect(left, right, showAll) {
        outcome = actions.compare(left, right, showAll)
    }
    val compared = (outcome as? PresetComparisonOutcome.Success)?.kinds.orEmpty()
    val rows = diffRows(compared)
    // m_tree->Clear(): every row is picked again whenever the tree is built.
    val picked = remember(rows) { PickedOptions() }
    val different = compared.any { it.changes.isNotEmpty() }
    // enable_transfer(): the values only move into a preset the app has not
    // modified, or into the very preset it edits.
    val canTransfer = compared.all { !it.editedDirty || it.edited == it.right }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window),
        ) {
            FullScreenDialogTopBar(
                title = orcaString("Compare presets"),
                okEnabled = canTransfer && rows.any { it is DiffRow.Option && picked.isPicked(it.kind, it.change.id) },
                onCancel = onDismiss,
                onOk = {
                    actions.transfer(
                        TRANSFER_ORDER.mapNotNull { kind -> compared.firstOrNull { it.kind == kind }?.transfer(picked) },
                    )
                    onDismiss()
                },
                okText = orcaString("Transfer"),
                // m_buttons: the Transfer button shows with the check box.
                showOk = useForTransfer && different,
            )
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                item(key = "top") {
                    InfoLine(orcaString("Select presets to compare"))
                }
                when (val current = outcome) {
                    null -> item(key = "loading") {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                    }
                    is PresetComparisonOutcome.Failure -> item(key = "problem") {
                        Text(current.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                    }
                    is PresetComparisonOutcome.Success -> Unit
                }
                items(compared.size, key = { "presets:${compared[it].kind}" }) { index ->
                    val kind = compared[index]
                    ComparedPresetsRow(
                        compared = kind,
                        onChoose = { isLeft -> choosing = kind.kind to isLeft },
                        // The equal button: the right box selects the left preset.
                        onCopy = { right = right.with(kind.kind, kind.left) },
                    )
                }
                if (compared.isNotEmpty()) {
                    item(key = "show-all") {
                        CheckLine(
                            text = orcaString("Show all presets (including incompatible)"),
                            checked = showAll,
                            onCheckedChange = { showAll = it },
                        )
                    }
                }
                // m_edit_sizer shows while the tree has something to transfer.
                if (different) {
                    item(key = "transfer") {
                        CheckLine(
                            text = orcaString("Transfer values from left to right"),
                            checked = useForTransfer,
                            onCheckedChange = { useForTransfer = it },
                        )
                    }
                }
                // update_bottom_info(): why nothing is compared, while no tree shows.
                val bottom = compared.lastOrNull { it.problem.isNotEmpty() }?.problem
                if (bottom != null && !different) {
                    item(key = "bottom") { InfoLine(orcaString(bottom)) }
                }
                if (different && useForTransfer && !canTransfer) {
                    item(key = "blocked") {
                        InfoLine(orcaString("You can only transfer to current active profile because it has been modified."))
                    }
                }
                items(rows.size, key = { rows[it].key }) { index ->
                    when (val row = rows[index]) {
                        is DiffRow.Option -> ComparedValueRow(
                            change = row.change,
                            checked = picked.isPicked(row.kind, row.change.id),
                            onCheckedChange = { checked: Boolean -> picked.pick(row.kind, listOf(row.change.id), checked) }.takeIf { useForTransfer },
                        )
                        is DiffRow.Branch -> TreeRow(
                            text = row.text,
                            level = row.level,
                            icon = row.icon,
                            checked = row.ids.any { picked.isPicked(row.kind, it) },
                            onCheckedChange = { checked: Boolean -> picked.pick(row.kind, row.ids, checked) }.takeIf { useForTransfer },
                        )
                    }
                }
            }
        }
    }

    choosing?.let { (kind, isLeft) ->
        val row = compared.firstOrNull { it.kind == kind } ?: return@let
        PresetListSheet(
            title = orcaString(kind.title()),
            items = if (isLeft) row.leftPresets else row.rightPresets,
            onDismiss = { choosing = null },
            onChoose = { item ->
                choosing = null
                if (isLeft) left = left.with(kind, item.name) else right = right.with(kind, item.name)
            },
        )
    }
}

/** A row of the dialog's tree, as the list shows them one after another. */
private sealed interface DiffRow {
    val key: String

    /** A row that holds others: the two presets, a page of the tab, or an option group. */
    data class Branch(
        override val key: String,
        val kind: PresetKind,
        val text: String,
        val level: Int,
        val ids: List<String>,
        val icon: Int? = null,
    ) : DiffRow

    /** One setting the presets differ in. */
    data class Option(override val key: String, val kind: PresetKind, val change: PresetChange) : DiffRow
}

/**
 * DiffModel: the presets of a kind, then the page, the option group and the
 * setting of every value they differ in, each row under the one it belongs to.
 */
@Composable
private fun diffRows(compared: List<PresetKindComparison>): List<DiffRow> {
    val rows = mutableListOf<DiffRow>()
    for (kind in compared) {
        if (kind.changes.isEmpty()) continue
        val places = kind.changes.map { change -> Triple(orcaText(change.category), orcaText(change.group), change) }
        rows += DiffRow.Branch(
            key = "tree:${kind.kind}",
            kind = kind.kind,
            text = "\"${kind.left}\" vs \"${kind.right}\"",
            level = 0,
            ids = kind.changes.map(PresetChange::id),
            icon = kind.kind.icon(),
        )
        for ((category, inCategory) in places.groupBy { it.first }) {
            rows += DiffRow.Branch(
                key = "tree:${kind.kind}:$category",
                kind = kind.kind,
                text = category,
                level = 1,
                ids = inCategory.map { it.third.id },
            )
            for ((group, inGroup) in inCategory.groupBy { it.second }) {
                rows += DiffRow.Branch(
                    key = "tree:${kind.kind}:$category:$group",
                    kind = kind.kind,
                    text = group,
                    level = 2,
                    ids = inGroup.map { it.third.id },
                )
                for ((_, _, change) in inGroup) {
                    rows += DiffRow.Option("tree:${kind.kind}:${change.id}", kind.kind, change)
                }
            }
        }
    }
    return rows
}

/** The tab of a preset kind, as its combo boxes are named after it. */
private fun PresetKind.title(): String = when (this) {
    PresetKind.PRINTER -> "Printer"
    PresetKind.FILAMENT -> "Filament"
    else -> "Process"
}

/** The icon of a preset kind (DiffModel::get_icon_name). */
private fun PresetKind.icon(): Int = when (this) {
    PresetKind.PRINTER -> DesignR.drawable.orca_printer
    PresetKind.FILAMENT -> DesignR.drawable.orca_filament
    else -> DesignR.drawable.orca_process
}

/** The presets of one kind the dialog selects, with the equal button between them. */
@Composable
private fun ComparedPresetsRow(compared: PresetKindComparison, onChoose: (Boolean) -> Unit, onCopy: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = orcaString(compared.kind.title()),
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrcaComboField(
                text = compared.leftPresets.labelOf(compared.left),
                onClick = { onChoose(true) },
                modifier = Modifier.weight(1f),
            )
            OrcaIconButton(
                icon = when {
                    compared.problem.isNotEmpty() -> DesignR.drawable.orca_question
                    compared.changes.isEmpty() -> DesignR.drawable.orca_equal
                    else -> DesignR.drawable.orca_not_equal
                },
                contentDescription = stringResource(R.string.preset_compare_copy),
                onClick = onCopy,
            )
            OrcaComboField(
                text = compared.rightPresets.labelOf(compared.right),
                onClick = { onChoose(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A row of the tree that holds others: the presets, a page, or an option group. */
@Composable
private fun TreeRow(text: String, level: Int, icon: Int?, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (12 + level * 12).dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onCheckedChange != null) {
            OrcaCheckBox(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(OrcaTheme.dimensions.iconSmall),
            )
        }
        Text(
            text = text,
            color = colors.text,
            style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * One setting the presets differ in: its name, and the value each preset holds.
 * A value too long for its column is cut short (DiffViewCtrl::Append()), and
 * the row opens FullCompareDialog on it (DiffViewCtrl::context_menu()).
 */
@Composable
private fun ComparedValueRow(change: PresetChange, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val colors = OrcaTheme.colors
    val label = orcaText(change.label)
    val left = orcaText(change.oldValue)
    val right = orcaText(change.newValue)
    val long = FullCompare.isLong(left, right)
    var comparing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (long) Modifier.orcaClickable(role = Role.Button, onClickLabel = stringResource(R.string.full_compare)) { comparing = true } else Modifier)
            .padding(start = 36.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onCheckedChange != null) {
            OrcaCheckBox(checked = checked, onCheckedChange = onCheckedChange)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        ) {
            Text(label, color = colors.text, style = OrcaTheme.typography.body13)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The "Left Preset Value" and "Right Preset Value" columns.
                Text(
                    text = FullCompare.shortValue(left),
                    color = colors.textLabel,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = FullCompare.shortValue(right),
                    color = colors.labelModified,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    HorizontalDivider(color = colors.separator, thickness = 1.dp)
    if (comparing) {
        FullCompareDialog(label, left, right, orcaString("Left Preset Value"), orcaString("Right Preset Value"), onDismiss = { comparing = false })
    }
}

/** The lines the dialog says what it can and cannot compare in (m_top_info_line). */
@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * A check box with its label, as the dialog puts them under the presets. The
 * whole line answers to a touch, as the label of a wxCheckBox does to a click.
 */
@Composable
private fun CheckLine(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = null)
        Text(
            text = text,
            color = OrcaTheme.colors.text,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * The toggle column of the tree: every row is picked while the tree stands, and
 * a row that holds others picks or drops all of them (update_children), while
 * it is on itself as long as one of them is (update_parents).
 */
private class PickedOptions {
    private val dropped = mutableStateMapOf<String, Boolean>()

    fun isPicked(kind: PresetKind, id: String): Boolean = dropped["$kind\u0000$id"] != true

    fun pick(kind: PresetKind, ids: List<String>, picked: Boolean) {
        for (id in ids) {
            dropped["$kind\u0000$id"] = !picked
        }
    }
}

/** DiffViewCtrl::options(): the settings of this kind the tree has picked. */
private fun PresetKindComparison.transfer(picked: PickedOptions): PresetTransfer =
    PresetTransfer(kind, left, right, changes.filter { picked.isPicked(kind, it.id) }.map(PresetChange::id))

/** The preset the combo box of [kind] selects on this side. */
private fun ComparedPresets.with(kind: PresetKind, name: String): ComparedPresets = when (kind) {
    PresetKind.PRINTER -> copy(printer = name)
    PresetKind.FILAMENT -> copy(filament = name)
    else -> copy(print = name)
}

/** The text a preset combo box shows for a preset (PresetComboBox). */
private fun List<PresetListItem>.labelOf(name: String): String = firstOrNull { it.name == name }?.label ?: name

/** PresetBundle::types_list(): the order the transfer moves the values in. */
private val TRANSFER_ORDER = listOf(PresetKind.PRINTER, PresetKind.PRINT, PresetKind.FILAMENT)

private val COMPARED_PRESETS_SAVER = listSaver<ComparedPresets, String>(
    save = { listOf(it.printer, it.print, it.filament) },
    restore = { ComparedPresets(it[0], it[1], it[2]) },
)
