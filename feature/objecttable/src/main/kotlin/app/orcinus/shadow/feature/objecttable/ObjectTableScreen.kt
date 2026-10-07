package app.orcinus.shadow.feature.objecttable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaPageTopBar
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.layout.OrcaPageWidth
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.plate.RenameDialog
import app.orcinus.shadow.core.ui.plate.volumeName
import app.orcinus.shadow.core.ui.settings.SettingsActions
import app.orcinus.shadow.core.ui.settings.SettingsTabDialogs
import app.orcinus.shadow.core.ui.settings.SettingsTabUi
import app.orcinus.shadow.core.ui.settings.rememberSettingsTab
import app.orcinus.shadow.core.ui.settings.settingsTabItems
import app.orcinus.shadow.domain.plate.ObjectTableColumn
import app.orcinus.shadow.domain.plate.ObjectTableOrder
import app.orcinus.shadow.domain.plate.ObjectTableRow
import app.orcinus.shadow.domain.plate.toSettingNumber
import java.util.Locale

/** What the table does to the plate and to its settings tabs (ObjectGridTable). */
internal class ObjectTableActions(
    val select: (ObjectTableRow) -> Unit,
    val change: (ObjectTableRow, ObjectTableColumn, String) -> Unit,
    val reset: (ObjectTableRow, ObjectTableColumn) -> Unit,
    val setPrintable: (ScenePath, Boolean) -> Unit,
    val setFilament: (ObjectTableRow, Int) -> Unit,
    val rename: (ObjectTableRow, String) -> Unit,
    /** The side panel's requests, which stay on the row's item. */
    val requestSettings: (SettingsItem, SettingsRequest) -> Unit,
    val answer: (PresetKind, Boolean) -> Unit,
    val dismissNotice: (PresetKind) -> Unit,
    val tooltip: suspend (PresetKind, String) -> List<OrcaText>,
    val checkPresetName: suspend (PresetKind, String) -> PresetNameOutcome,
    val compatibleChoices: suspend (PresetKind, String) -> PresetNamesOutcome,
    val bedShape: suspend () -> BedShapeOutcome,
    val gcodePlaceholders: suspend (PresetKind, String) -> GcodePlaceholdersOutcome,
    val gcodePlaceholder: suspend (String, Boolean) -> GcodePlaceholderInfo,
) {
    /** The actions of the tab of [item], whose requests go to that item; none for no item. */
    fun settingsOf(item: SettingsItem?) = SettingsActions(
        request = { _, request -> item?.let { requestSettings(it, request) } },
        answer = answer,
        dismissNotice = dismissNotice,
        tooltip = tooltip,
        checkPresetName = checkPresetName,
        compatibleChoices = compatibleChoices,
        bedShape = bedShape,
        // The tab of an object or a volume has no printable area.
        setBedShape = {},
        gcodePlaceholders = gcodePlaceholders,
        gcodePlaceholder = gcodePlaceholder,
    )
}

/**
 * OrcaSlicer's Parameter Table (ObjectTableDialog "Object/Part Settings"):
 * every object of the plate and the volumes of the objects that have several,
 * with their printable state, plate, name, filament and the settings they
 * override most often. [openOn] is the row Plater::PopupObjectTable() selects.
 */
@Composable
fun ObjectTableRoute(viewModel: ObjectTableViewModel, onBack: () -> Unit, openOn: SettingsItem? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObjectTableScreen(
        state = state,
        onBack = onBack,
        openOn = openOn,
        actions = ObjectTableActions(
            select = viewModel::select,
            change = viewModel::change,
            reset = viewModel::reset,
            setPrintable = viewModel::setPrintable,
            setFilament = viewModel::setFilament,
            rename = viewModel::rename,
            requestSettings = viewModel::requestSettings,
            answer = viewModel::answerSettingsQuestion,
            dismissNotice = viewModel::dismissSettingsNotice,
            tooltip = viewModel::settingTooltip,
            checkPresetName = viewModel::checkPresetName,
            compatibleChoices = viewModel::compatiblePresetChoices,
            bedShape = viewModel::bedShape,
            gcodePlaceholders = viewModel::gcodePlaceholders,
            gcodePlaceholder = viewModel::gcodePlaceholder,
        ),
    )
}

/**
 * The grid of ObjectTablePanel as a phone shows it: the name column stays at
 * the start while the other columns scroll sideways under their headers, and
 * a cell edits in place (a check box), in a list (the filament, the brim) or
 * in a small dialog (a number, the name). The panel beside the desktop grid
 * (ObjectTableSettings), the settings of the selected cell's row on the page
 * of its setting, docks beside the grid where the window is wide and opens as
 * a sheet (a dialog on a tablet in portrait) from the bar otherwise.
 */
@Composable
internal fun ObjectTableScreen(
    state: ObjectTableUiState,
    onBack: () -> Unit,
    openOn: SettingsItem?,
    actions: ObjectTableActions,
) {
    val objects = state.objects.associateBy(PlateObject::mesh)
    // The names the rows sort by and show: ModelObject::name and ModelVolume::name, as the object list names them.
    val names = state.rows.associate { row ->
        val plateObject = objects[row.mesh]
        val volume = row.volume
        row.item to when {
            plateObject == null -> ""
            volume == null -> plateObject.displayName()
            else -> plateObject.volumeName(volume)
        }
    }
    val nameOf = { row: ObjectTableRow -> names[row.item].orEmpty() }
    var order by rememberSaveable(stateSaver = OrderSaver) { mutableStateOf(ObjectTableOrder()) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(openOn?.let { TableSelection(it, null) }) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val rows = order.arrange(state.rows)
    val list = rememberLazyListState()
    // ObjectTableDialog::Popup(): sort_by_default(), then SetSelection() selects the row and makes it visible.
    LaunchedEffect(Unit) {
        if (opened) return@LaunchedEffect
        opened = true
        order = order.byDefault(state.rows, nameOf)
        val index = order.arrange(state.rows).indexOfFirst { it.item == openOn }
        if (index >= 0) list.scrollToItem(index)
    }
    val selectedRow = rows.firstOrNull { it.item == selection?.item }
    // OnSelectCell(): no panel for the header or the filament column.
    val panelShown = selectedRow != null && selection?.column != ObjectTableColumn.FILAMENT
    val objectTab = rememberSettingsTab(state.objectTab, actions.settingsOf(selectedRow?.item as? SettingsItem.Object), state.enabled)
    val partTab = rememberSettingsTab(state.partTab, actions.settingsOf(selectedRow?.item as? SettingsItem.Volume), state.enabled)
    val selectedTab = selectedRow?.takeIf { panelShown }?.let { if (it.volume == null) objectTab else partTab }
    val select: (ObjectTableRow, ObjectTableColumn) -> Unit = { row, column ->
        if (selection != TableSelection(row.item, column)) {
            selection = TableSelection(row.item, column)
            actions.select(row)
        }
    }
    // The panel docks beside the grid on a large window, where the settings
    // pages list their pages beside the page too; otherwise it opens over it.
    val docked = currentOrcaWindowLayout() == OrcaWindowLayout.Expanded
    Box(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window),
    ) {
        Column(Modifier.fillMaxSize()) {
            OrcaPageTopBar(
                title = orcaString("Object/Part Settings"),
                backDescription = stringResource(R.string.object_table_back),
                onBack = onBack,
            ) {
                if (!docked) {
                    OrcaIconButton(
                        icon = DesignR.drawable.orca_cog,
                        contentDescription = stringResource(R.string.object_table_row_settings),
                        onClick = { panelOpen = true },
                        enabled = panelShown,
                        tint = OrcaTheme.colors.onTabBar,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                ObjectGrid(
                    rows = rows,
                    names = names,
                    filaments = state.filaments,
                    enabled = state.enabled,
                    selection = selection,
                    list = list,
                    onSelect = select,
                    onSort = { column -> order = order.by(column, state.rows, nameOf) },
                    actions = actions,
                    modifier = Modifier.weight(1f),
                )
                if (docked) {
                    VerticalDivider(color = OrcaTheme.colors.separator)
                    RowSettingsPanel(
                        name = selectedRow?.let { names[it.item] },
                        column = selection?.column,
                        item = selectedRow?.item,
                        tab = selectedTab,
                        actions = actions,
                        modifier = Modifier
                            .width(DOCKED_PANEL_WIDTH)
                            .fillMaxHeight(),
                    )
                }
            }
        }
        val sheet = if (docked || !panelOpen) null else selectedRow?.let { row -> selectedTab?.let { tab -> row to tab } }
        // A sheet whose row went away stays closed for the next row selected.
        LaunchedEffect(sheet != null) { if (sheet == null) panelOpen = false }
        sheet?.let { (row, tab) ->
            // A dialog the width of a settings page on a tablet in portrait.
            OrcaSheet(onDismissRequest = { panelOpen = false }, dialogWidth = OrcaPageWidth.Settings, skipPartiallyExpanded = true) {
                RowSettingsPanel(
                    name = names[row.item],
                    column = selection?.column,
                    item = row.item,
                    tab = tab,
                    actions = actions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(SHEET_HEIGHT)
                        .navigationBarsPadding(),
                )
            }
        }
    }
    // The message boxes of the table's edits and of the panel, which the tab of the row's item raises.
    SettingsTabDialogs(objectTab)
    SettingsTabDialogs(partTab)
}

/** ObjectGrid: the header row, then a row for each object and volume. */
@Composable
private fun ObjectGrid(
    rows: List<ObjectTableRow>,
    names: Map<SettingsItem, String>,
    filaments: List<ObjectTableFilament>,
    enabled: Boolean,
    selection: TableSelection?,
    list: LazyListState,
    onSelect: (ObjectTableRow, ObjectTableColumn) -> Unit,
    onSort: (ObjectTableColumn) -> Unit,
    actions: ObjectTableActions,
    modifier: Modifier = Modifier,
) {
    // One sideways scroll for the header and every row, so the columns stay under their headers.
    val sideways = rememberScrollState()
    // The cell whose number or name a dialog edits.
    var editing by remember { mutableStateOf<Pair<ObjectTableRow, ObjectTableColumn>?>(null) }
    Column(modifier) {
        HeaderRow(sideways, onSort)
        HorizontalDivider(color = OrcaTheme.colors.separator)
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            items(rows, key = { "${it.mesh.value}:${it.volume ?: -1}" }) { row ->
                GridRow(
                    row = row,
                    name = names[row.item].orEmpty(),
                    filaments = filaments,
                    enabled = enabled,
                    selected = selection?.item == row.item,
                    selectedColumn = selection?.takeIf { it.item == row.item }?.column,
                    sideways = sideways,
                    onSelect = { column -> onSelect(row, column) },
                    onEdit = { column ->
                        onSelect(row, column)
                        editing = row to column
                    },
                    actions = actions,
                )
                HorizontalDivider(color = OrcaTheme.colors.separator.copy(alpha = 0.5f))
            }
        }
    }
    editing?.let { (row, column) ->
        val name = names[row.item].orEmpty()
        if (column == ObjectTableColumn.NAME) {
            RenameDialog(
                name = name,
                onDismiss = { editing = null },
                onRename = { renamed ->
                    editing = null
                    actions.rename(row, renamed)
                },
            )
        } else {
            ValueDialog(
                title = columnLabel(column),
                name = name,
                value = cellText(row, column),
                integer = column == ObjectTableColumn.WALL_LOOPS,
                onDismiss = { editing = null },
                onApply = { text ->
                    editing = null
                    actions.change(row, column, text)
                },
            )
        }
    }
}

/** The column labels (ObjectTablePanel::load_data()), the sorting ones marked "↑↓"; a tap sorts by them. */
@Composable
private fun HeaderRow(sideways: ScrollState, onSort: (ObjectTableColumn) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(OrcaTheme.colors.window),
    ) {
        HeaderCell(ObjectTableColumn.NAME, onSort)
        Row(Modifier.horizontalScroll(sideways)) {
            SCROLLED_COLUMNS.forEach { column -> HeaderCell(column, onSort) }
        }
    }
}

@Composable
private fun HeaderCell(column: ObjectTableColumn, onSort: (ObjectTableColumn) -> Unit) {
    val label = columnLabel(column)
    val sortLabel = stringResource(R.string.object_table_sort, label)
    Box(
        modifier = Modifier
            .width(column.width)
            .fillMaxHeight()
            .then(if (column.sortable) Modifier.orcaClickable(role = Role.Button, onClickLabel = sortLabel) { onSort(column) } else Modifier)
            .padding(horizontal = CELL_PADDING),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (column.sortable) "$label$SORT_MARK" else label,
            color = OrcaTheme.colors.text,
            style = OrcaTheme.typography.head13,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of the grid: the name, then the scrolled cells. */
@Composable
private fun GridRow(
    row: ObjectTableRow,
    name: String,
    filaments: List<ObjectTableFilament>,
    enabled: Boolean,
    selected: Boolean,
    selectedColumn: ObjectTableColumn?,
    sideways: ScrollState,
    onSelect: (ObjectTableColumn) -> Unit,
    onEdit: (ObjectTableColumn) -> Unit,
    actions: ObjectTableActions,
) {
    val colors = OrcaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (selected) colors.accentSelected else colors.window),
    ) {
        // A tap on the name of the selected row edits it, as a second click does on the desktop.
        Cell(
            column = ObjectTableColumn.NAME,
            highlighted = selectedColumn == ObjectTableColumn.NAME,
            onClick = { if (selected && enabled) onEdit(ObjectTableColumn.NAME) else onSelect(ObjectTableColumn.NAME) },
        ) {
            Text(
                text = name,
                color = colors.text,
                style = OrcaTheme.typography.body13,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // ObjectGridTable::GetValue(): a volume's name is indented.
                modifier = Modifier.padding(start = if (row.volume != null) VOLUME_INDENT else 0.dp),
            )
        }
        Row(Modifier.horizontalScroll(sideways)) {
            SCROLLED_COLUMNS.forEach { column ->
                GridCell(row, column, filaments, enabled, selectedColumn == column, onSelect, onEdit, actions)
            }
        }
    }
}

/** One cell, as the editor and renderer load_data() gives its column show and edit it. */
@Composable
private fun GridCell(
    row: ObjectTableRow,
    column: ObjectTableColumn,
    filaments: List<ObjectTableFilament>,
    enabled: Boolean,
    highlighted: Boolean,
    onSelect: (ObjectTableColumn) -> Unit,
    onEdit: (ObjectTableColumn) -> Unit,
    actions: ObjectTableActions,
) {
    val colors = OrcaTheme.colors
    val editable = enabled && row.editable(column)
    var choosing by remember { mutableStateOf(false) }
    val onClick: () -> Unit = {
        when {
            !editable -> onSelect(column)
            // GridCellSupportEditor: a tap checks or clears the box.
            column == ObjectTableColumn.PRINTABLE -> {
                onSelect(column)
                actions.setPrintable(row.mesh, !row.printable)
            }
            column == ObjectTableColumn.SUPPORT -> {
                onSelect(column)
                actions.change(row, column, if (row.values[column] == CHECKED) UNCHECKED else CHECKED)
            }
            // GridCellFilamentsEditor and GridCellChoiceEditor: a list of the choices.
            column == ObjectTableColumn.FILAMENT || column == ObjectTableColumn.BRIM -> {
                onSelect(column)
                choosing = true
            }
            // GridCellTextEditor
            else -> onEdit(column)
        }
    }
    Cell(column = column, highlighted = highlighted, onClick = onClick) {
        if (!row.shows(column)) return@Cell
        when (column) {
            ObjectTableColumn.PRINTABLE, ObjectTableColumn.SUPPORT -> {
                val checked = if (column == ObjectTableColumn.PRINTABLE) row.printable else row.values[column] == CHECKED
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    OrcaCheckBox(checked = checked, onCheckedChange = null, enabled = editable)
                }
            }
            ObjectTableColumn.FILAMENT -> FilamentCell(row, filaments, enabled = editable)
            else -> Text(
                text = cellText(row, column),
                color = if (editable || column == ObjectTableColumn.PLATE) colors.text else colors.textDisabled,
                style = OrcaTheme.typography.body13,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // GridCellIconRenderer: the lock of a value that is not the one the row takes resets it.
        if (row.modified(column)) {
            Box(
                Modifier
                    .size(RESET_SIZE)
                    .orcaClickable(enabled = enabled, role = Role.Button, onClickLabel = orcaString("Reset parameter")) {
                        onSelect(column)
                        actions.reset(row, column)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(DesignR.drawable.orca_lock_normal),
                    contentDescription = orcaString("Reset parameter"),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(OrcaTheme.dimensions.iconSmall),
                )
            }
        }
        if (choosing) {
            OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { choosing = false }) {
                if (column == ObjectTableColumn.FILAMENT) {
                    filaments.forEachIndexed { index, filament ->
                        OrcaMenuItem(
                            text = filament.name,
                            onClick = {
                                choosing = false
                                actions.setFilament(row, index + 1)
                            },
                            leading = { OrcaFilamentSlot(number = index + 1, color = filament.color.toColor()) },
                        )
                    }
                } else {
                    BRIM_TYPES.forEach { (key, label) ->
                        OrcaMenuCheckItem(
                            text = orcaString(label),
                            checked = row.values[column] == key,
                            onClick = {
                                choosing = false
                                actions.change(row, column, key)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * GridCellFilamentsRenderer: the colour of the row's filament with its number;
 * a modifier that takes its object's says "Default", and a volume that takes
 * no filament shows none.
 */
@Composable
private fun FilamentCell(row: ObjectTableRow, filaments: List<ObjectTableFilament>, enabled: Boolean) {
    when {
        row.volumeType != null && row.volumeType != VolumeType.PART && row.volumeType != VolumeType.MODIFIER -> Unit
        row.volumeType == VolumeType.MODIFIER && !row.ownFilament -> Text(
            orcaString("Default"),
            color = if (enabled) OrcaTheme.colors.text else OrcaTheme.colors.textDisabled,
            style = OrcaTheme.typography.body13,
        )
        else -> OrcaFilamentSlot(number = row.filament, color = filaments.getOrNull(row.filament - 1)?.color.toColor())
    }
}

/** A cell of the grid, the width of its column; the selected cell is outlined. */
@Composable
private fun Cell(column: ObjectTableColumn, highlighted: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Box(
        Modifier
            .width(column.width)
            .fillMaxHeight()
            .then(if (highlighted) Modifier.border(1.dp, OrcaTheme.colors.accent) else Modifier)
            .orcaClickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = CELL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * ObjectTableSettings beside the grid: the settings of the selected cell's
 * row, as the tab of its object or volume shows them, opened on the page of
 * the cell's setting with the setting marked; the other columns keep the page
 * the tab shows (the desktop panel lists every modified setting for them).
 */
@Composable
private fun RowSettingsPanel(
    name: String?,
    column: ObjectTableColumn?,
    item: SettingsItem?,
    tab: SettingsTabUi?,
    actions: ObjectTableActions,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    if (tab == null || item == null) {
        Box(modifier.padding(16.dp)) {
            Text(stringResource(R.string.object_table_pick_cell), color = colors.textSide, style = OrcaTheme.typography.body13)
        }
        return
    }
    val lines = rememberLazyListState()
    // ObjectTableSettings::UpdateAndShow() of the cell's row and category.
    LaunchedEffect(item, column) {
        val page = column?.page
        actions.requestSettings(item, if (page != null) SettingsRequest.SelectPage(page) else SettingsRequest.Describe)
        if (column?.isSetting == true) tab.highlighted = column.key
    }
    // Tab::activate_option(): the list scrolls to the marked setting once its page shows.
    LaunchedEffect(tab.highlighted, tab.page?.title) {
        val id = tab.highlighted ?: return@LaunchedEffect
        val index = tab.indexOf(id) ?: return@LaunchedEffect
        lines.animateScrollToItem(index)
    }
    Column(modifier) {
        Text(
            text = name.orEmpty(),
            color = colors.text,
            style = OrcaTheme.typography.head14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        LazyColumn(state = lines, modifier = Modifier.fillMaxSize()) {
            settingsTabItems(tab)
        }
    }
}

/** GridCellTextEditor: a number of the row's setting, which the tab of its item reads as its field does. */
@Composable
private fun ValueDialog(title: String, name: String, value: String, integer: Boolean, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onApply(text) }, enabled = text.isNotBlank()) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(title, style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(name, style = OrcaTheme.typography.body14, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OrcaTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onApply(text) }),
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** ObjectTablePanel::load_data()'s column labels. */
@Composable
private fun columnLabel(column: ObjectTableColumn): String = orcaString(
    when (column) {
        ObjectTableColumn.PRINTABLE -> "Printable"
        ObjectTableColumn.PLATE -> "Plate"
        ObjectTableColumn.NAME -> "Name"
        ObjectTableColumn.FILAMENT -> "Filament"
        ObjectTableColumn.LAYER_HEIGHT -> "Layer height"
        ObjectTableColumn.WALL_LOOPS -> "Wall loops"
        ObjectTableColumn.FILL_DENSITY -> "Infill density(%)"
        ObjectTableColumn.SUPPORT -> "Support"
        ObjectTableColumn.BRIM -> "Brim"
        ObjectTableColumn.OUTER_WALL_SPEED -> "Outer wall speed"
    },
)

/**
 * What a cell shows, as its renderer draws it: the plate's number or
 * "Outside", the brim's choice, and the numbers with the decimals of
 * wxGridCellFloatRenderer (two for the layer height, one for the density, none
 * for the speed).
 */
@Composable
private fun cellText(row: ObjectTableRow, column: ObjectTableColumn): String {
    val value = row.values[column].orEmpty()
    val number = value.toSettingNumber()
    return when (column) {
        ObjectTableColumn.PLATE -> row.plate?.let { (it + 1).toString() } ?: orcaString("Outside")
        ObjectTableColumn.BRIM -> BRIM_TYPES.firstOrNull { it.first == value }?.let { orcaString(it.second) } ?: value
        ObjectTableColumn.LAYER_HEIGHT -> number?.let { String.format(Locale.ROOT, "%.2f", it) } ?: value
        ObjectTableColumn.FILL_DENSITY -> number?.let { String.format(Locale.ROOT, "%.1f", it) } ?: value
        ObjectTableColumn.OUTER_WALL_SPEED -> number?.let { String.format(Locale.ROOT, "%.0f", it) } ?: value
        ObjectTableColumn.WALL_LOOPS -> number?.toLong()?.toString() ?: value
        else -> value
    }
}

/** The colour of a filament; OrcaSlicer's green for one it cannot read (ObjectTablePanel::init_filaments_and_colors()). */
private fun ColorRgba?.toColor(): Color = this?.let { Color(it.red, it.green, it.blue, it.alpha) } ?: Color.Green

/**
 * The page of the process tabs (TabPrint::build()) a column's setting is on,
 * which the panel opens; ObjectTableSettings shows the category of the column
 * ("Quality", "Strength", "Support" with the brim, "Speed").
 */
private val ObjectTableColumn.page: String?
    get() = when (this) {
        ObjectTableColumn.LAYER_HEIGHT -> "Quality"
        ObjectTableColumn.WALL_LOOPS, ObjectTableColumn.FILL_DENSITY -> "Strength"
        ObjectTableColumn.SUPPORT -> "Support"
        ObjectTableColumn.BRIM -> "Others"
        ObjectTableColumn.OUTER_WALL_SPEED -> "Speed"
        else -> null
    }

/** The width of a column on a phone; the name's is the desktop's FromDIP(140). */
private val ObjectTableColumn.width: Dp
    get() = when (this) {
        ObjectTableColumn.NAME -> 140.dp
        ObjectTableColumn.PRINTABLE, ObjectTableColumn.PLATE -> 76.dp
        ObjectTableColumn.FILAMENT -> 84.dp
        ObjectTableColumn.LAYER_HEIGHT, ObjectTableColumn.WALL_LOOPS, ObjectTableColumn.SUPPORT -> 92.dp
        ObjectTableColumn.FILL_DENSITY, ObjectTableColumn.OUTER_WALL_SPEED -> 108.dp
        ObjectTableColumn.BRIM -> 136.dp
    }

/** The columns after the name, in the desktop's order. */
private val SCROLLED_COLUMNS = ObjectTableColumn.entries - ObjectTableColumn.NAME

/** The brim's choices in the column's order (init_cols()), by the key brim_type writes for each. */
private val BRIM_TYPES = listOf(
    "auto_brim" to "Auto",
    "brim_ears" to "Mouse ear",
    "painted" to "Painted",
    "outer_only" to "Outer brim only",
    "inner_only" to "Inner brim only",
    "outer_and_inner" to "Outer and inner brim",
    "no_brim" to "No-brim",
)

/** ConfigOptionBool as the settings write it. */
private const val CHECKED = "1"
private const val UNCHECKED = "0"

/** The mark of a column that sorts (load_data()'s "↑↓"). */
private const val SORT_MARK = "↑↓"

private val HEADER_HEIGHT = 52.dp
private val ROW_HEIGHT = 48.dp
private val CELL_PADDING = 6.dp
private val VOLUME_INDENT = 16.dp
private val RESET_SIZE = 32.dp

/** The panel beside the grid, as the desktop dialog shows it. */
private val DOCKED_PANEL_WIDTH = 340.dp

/** The share of the window the panel's sheet takes. */
private const val SHEET_HEIGHT = 0.85f

/** The cell the grid has selected (ObjectGridTable's m_current_row and m_current_col); a row SetSelection() selected has no column. */
private data class TableSelection(val item: SettingsItem, val column: ObjectTableColumn?)

/** The selection across a configuration change: the row's mesh, its volume or -1, its column or none. */
private val SelectionSaver = listSaver<TableSelection?, Any>(
    save = { selection ->
        if (selection == null) emptyList() else listOf(
            selection.item.mesh.value,
            (selection.item as? SettingsItem.Volume)?.id?.index ?: -1,
            selection.column?.name.orEmpty(),
        )
    },
    restore = { saved ->
        if (saved.isEmpty()) return@listSaver null
        val mesh = ScenePath(saved[0] as String)
        val volume = saved[1] as Int
        TableSelection(
            if (volume < 0) SettingsItem.Object(mesh) else SettingsItem.Volume(ObjectPartId(mesh, volume)),
            (saved[2] as String).takeIf(String::isNotEmpty)?.let(ObjectTableColumn::valueOf),
        )
    },
)

/** The order across a configuration change: the column last sorted, then the objects' meshes. */
private val OrderSaver = listSaver<ObjectTableOrder, String>(
    save = { order -> listOf(order.sortColumn?.name.orEmpty()) + order.objects.map(ScenePath::value) },
    restore = { saved ->
        ObjectTableOrder(saved.drop(1).map(::ScenePath), saved.first().takeIf(String::isNotEmpty)?.let(ObjectTableColumn::valueOf))
    },
)
