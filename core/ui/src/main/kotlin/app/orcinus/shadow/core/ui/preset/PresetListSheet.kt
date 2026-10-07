package app.orcinus.shadow.core.ui.preset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.orcinus.shadow.core.designsystem.component.OrcaPickerAnchor
import app.orcinus.shadow.core.designsystem.component.OrcaPickerSheet
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.component.orcaSelectable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.parseColor

/** An entry under the list: what it can do besides choosing a preset. */
@Composable
private fun SheetAction(text: String, icon: Int, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    HorizontalDivider(color = colors.separator, thickness = 1.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .orcaClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.icon))
        Text(text, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 12.dp))
    }
}

private sealed interface SheetRow {
    val key: String

    data class Group(val group: PresetGroup) : SheetRow {
        override val key get() = "group:$group"
    }

    data class Subgroup(val group: PresetGroup, val name: String, val msgid: Boolean) : SheetRow {
        override val key get() = "subgroup:$group:$name"
    }

    data class Entry(val item: PresetListItem) : SheetRow {
        override val key get() = "entry:${item.group}:${item.name}"
    }
}

/**
 * The drop-down list of an OrcaSlicer preset combo box: its sections ("User
 * presets", "System presets"), the submenus of their entries, and the selected
 * entry marked. A long list can be searched. [action] is the list's last
 * entry, such as "Select/Remove printers (system presets)". A phone shows it as
 * a bottom sheet; a larger window drops it down from the combo box of
 * [anchor], as the desktop's PresetComboBox does.
 */
@Composable
fun PresetListSheet(
    title: String,
    items: List<PresetListItem>,
    onDismiss: () -> Unit,
    onChoose: (PresetListItem) -> Unit,
    action: String? = null,
    onAction: () -> Unit = {},
    /** A second entry under it, as the printer list has two. */
    secondAction: String? = null,
    onSecondAction: () -> Unit = {},
    /** The combo box the list belongs to. */
    anchor: OrcaPickerAnchor? = null,
) {
    val colors = OrcaTheme.colors
    var search by rememberSaveable { mutableStateOf("") }
    val rows = remember(items, search) { sheetRows(items, search) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (rows.indexOfFirst { it is SheetRow.Entry && it.item.selected } - 1).coerceAtLeast(0))
    OrcaPickerSheet(
        anchor = anchor,
        onDismissRequest = onDismiss,
        title = title,
        skipPartiallyExpanded = items.size > SEARCH_THRESHOLD,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (items.size > SEARCH_THRESHOLD) {
                SearchField(search, onValueChange = { search = it }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                items(rows, key = SheetRow::key) { row ->
                    when (row) {
                        is SheetRow.Group -> Text(
                            text = stringResource(
                                when (row.group) {
                                    PresetGroup.PROJECT -> R.string.project_presets
                                    PresetGroup.USER -> R.string.user_presets
                                    PresetGroup.BUNDLE -> R.string.bundle_presets
                                    PresetGroup.SYSTEM -> R.string.system_presets
                                    PresetGroup.UNSUPPORTED -> R.string.unsupported_presets
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
                            text = if (row.msgid) orcaString(row.name) else row.name.ifEmpty { stringResource(R.string.unspecified) },
                            color = colors.textSide,
                            style = OrcaTheme.typography.head12,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.sidebarBackground)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        // An unsupported preset is listed but cannot be chosen (DD_ITEM_STYLE_DISABLED).
                        is SheetRow.Entry -> PresetRow(row.item, enabled = row.item.group != PresetGroup.UNSUPPORTED, onClick = { onChoose(row.item) })
                    }
                }
            }
            action?.let { SheetAction(it, DesignR.drawable.orca_edit, onAction) }
            secondAction?.let { SheetAction(it, DesignR.drawable.orca_add, onSecondAction) }
        }
    }
}

@Composable
private fun PresetRow(item: PresetListItem, enabled: Boolean, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (item.selected) colors.accentSelected else colors.window)
            .orcaSelectable(selected = item.selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parseColor(item.color)?.let { FilamentColorSquare(it) }
        Text(
            item.label,
            color = if (enabled) colors.text else colors.textDisabled,
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
 * PresetComboBox::get_bmp(preset): the square of a filament's default colour
 * before its name, a very light one with a grey border.
 */
@Composable
private fun FilamentColorSquare(color: Color) {
    val light = color.red > LIGHT_CHANNEL && color.green > LIGHT_CHANNEL && color.blue > LIGHT_CHANNEL
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(16.dp)
            .background(color)
            .then(if (light) Modifier.border(1.dp, Color(0xFF808080)) else Modifier),
    )
}

/** A channel above 224 of 255 (clr.Red() > 224 in get_bmp()). */
private const val LIGHT_CHANNEL = 224f / 255f

/**
 * The sections in the combo box's order; within one, the entries of a submenu
 * together, submenus in the order their first entries come.
 */
private fun sheetRows(items: List<PresetListItem>, search: String): List<SheetRow> {
    val query = search.trim()
    val shown = items.filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
    return shown.groupBy(PresetListItem::group).flatMap { (group, entries) ->
        listOf<SheetRow>(SheetRow.Group(group)) + entries.groupBy(PresetListItem::subgroup).flatMap { (subgroup, members) ->
            val msgid = members.first().subgroupMsgid
            val header = if (subgroup.isEmpty() && group != PresetGroup.BUNDLE) emptyList() else listOf(SheetRow.Subgroup(group, subgroup, msgid))
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
