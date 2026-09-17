package app.orcinus.shadow.feature.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetListItem

private sealed interface SheetRow {
    val key: String

    data class Group(val group: PresetGroup) : SheetRow {
        override val key get() = "group:$group"
    }

    data class Subgroup(val group: PresetGroup, val name: String) : SheetRow {
        override val key get() = "subgroup:$group:$name"
    }

    data class Entry(val item: PresetListItem) : SheetRow {
        override val key get() = "entry:${item.group}:${item.name}"
    }
}

/**
 * The drop-down list of an OrcaSlicer preset combo box as a bottom sheet: its
 * sections ("User presets", "System presets"), the submenus of their entries,
 * and the selected entry marked. A long list can be searched. [action] is the
 * list's last entry, such as "Select/Remove printers (system presets)".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetListSheet(
    title: String,
    items: List<PresetListItem>,
    onDismiss: () -> Unit,
    onChoose: (PresetListItem) -> Unit,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = OrcaTheme.colors
    var search by rememberSaveable { mutableStateOf("") }
    val rows = remember(items, search) { sheetRows(items, search) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (rows.indexOfFirst { it is SheetRow.Entry && it.item.selected } - 1).coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = items.size > SEARCH_THRESHOLD),
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                title,
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { heading() },
            )
            if (items.size > SEARCH_THRESHOLD) {
                SearchField(search, onValueChange = { search = it }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                items(rows, key = SheetRow::key) { row ->
                    when (row) {
                        is SheetRow.Group -> Text(
                            text = stringResource(
                                when (row.group) {
                                    PresetGroup.USER -> R.string.user_presets
                                    PresetGroup.BUNDLE -> R.string.bundle_presets
                                    PresetGroup.SYSTEM -> R.string.system_presets
                                },
                            ),
                            color = colors.accent,
                            style = OrcaTheme.typography.head13,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                .semantics { heading() },
                        )
                        is SheetRow.Subgroup -> Text(
                            text = row.name.ifEmpty { stringResource(R.string.unspecified) },
                            color = colors.textSide,
                            style = OrcaTheme.typography.head12,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.sidebarBackground)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        is SheetRow.Entry -> PresetRow(row.item, onClick = { onChoose(row.item) })
                    }
                }
            }
            action?.let {
                HorizontalDivider(color = colors.separator, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onAction)
                        .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(DesignR.drawable.orca_edit), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.icon))
                    Text(it, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetRow(item: PresetListItem, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (item.selected) colors.accentSelected else colors.window)
            .selectable(selected = item.selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            color = colors.text,
            style = if (item.selected) OrcaTheme.typography.head14 else OrcaTheme.typography.body14,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.selected) {
            Icon(painterResource(DesignR.drawable.orca_checked), contentDescription = null, tint = colors.accent, modifier = Modifier.size(OrcaTheme.dimensions.icon))
        }
    }
}

/**
 * The sections in the combo box's order; within one, the entries of a submenu
 * together, submenus in the order their first entries come.
 */
private fun sheetRows(items: List<PresetListItem>, search: String): List<SheetRow> {
    val query = search.trim()
    val shown = items.filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
    return shown.groupBy(PresetListItem::group).flatMap { (group, entries) ->
        listOf<SheetRow>(SheetRow.Group(group)) + entries.groupBy(PresetListItem::subgroup).flatMap { (subgroup, members) ->
            val header = if (subgroup.isEmpty() && group != PresetGroup.BUNDLE) emptyList() else listOf(SheetRow.Subgroup(group, subgroup))
            header + members.map(SheetRow::Entry)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.buttonBackground, RoundedCornerShape(20.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(DesignR.drawable.orca_search), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.search), color = colors.textSide, style = OrcaTheme.typography.body14)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = OrcaTheme.typography.body14.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            OrcaIconButton(icon = DesignR.drawable.orca_cross, contentDescription = stringResource(R.string.clear_search), onClick = { onValueChange("") })
        }
    }
}

/** Lists longer than this can be searched and open at full height. */
private const val SEARCH_THRESHOLD = 12
