package app.orcinus.shadow.feature.setup

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaChoiceChips
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.OrcaPageTopBar
import app.orcinus.shadow.core.designsystem.layout.OrcaPageWidth
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.orcaContentWidth
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.SetupPrinterModel
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.rememberOrcaWebText
import app.orcinus.shadow.core.ui.settings.AddFilamentPresetDialog
import app.orcinus.shadow.core.ui.settings.CreateFilamentDialog
import app.orcinus.shadow.core.ui.settings.CreatePresetSuccessfulDialog
import app.orcinus.shadow.core.ui.settings.EditFilamentDialog
import app.orcinus.shadow.core.ui.settings.SettingsQuestionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OrcaSlicer's Setup Wizard with its printer and filament pages, laid out for
 * touch: printer cards by vendor, and filaments filtered with chips. [onClose]
 * runs once the wizard has done its work, after [onCreatePrinter] when its
 * "Create" asked for CreatePrinterPresetDialog.
 */
@Composable
fun SetupWizardRoute(
    viewModel: SetupWizardViewModel,
    onClose: () -> Unit,
    onCreatePrinter: () -> Unit = {},
    /** A page covers the wizard, as the filament tab EditFilamentPresetDialog opens does. */
    covered: Boolean = false,
    /** EditFilamentPresetDialog's "Edit Preset": the filament tab opens on the preset of the filament. */
    onEditFilamentPreset: (filamentId: String, preset: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) {
            if (state.creatingPrinter) onCreatePrinter()
            onClose()
        }
    }
    BackHandler(enabled = !state.finished) { viewModel.back() }
    SetupWizardScreen(state, viewModel, covered, onEditFilamentPreset)
}

@Composable
private fun SetupWizardScreen(
    state: SetupUiState,
    viewModel: SetupWizardViewModel,
    covered: Boolean,
    onEditFilamentPreset: (filamentId: String, preset: String) -> Unit,
) {
    val colors = OrcaTheme.colors
    val webText = rememberOrcaWebText()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        OrcaPageTopBar(
            title = when (state.page) {
                SetupPage.PRINTERS -> stringResource(R.string.printer_selection)
                SetupPage.FILAMENTS -> stringResource(R.string.filament_selection)
                SetupPage.STEALTH -> webText("orca3", "Stealth Mode")
            },
            backDescription = stringResource(R.string.back),
            onBack = viewModel::back,
        )
        Box(Modifier.weight(1f)) {
            when (state.page) {
                SetupPage.PRINTERS -> PrinterPageContent(state, viewModel)
                SetupPage.FILAMENTS -> FilamentPageContent(state.filaments, viewModel, covered, onEditFilamentPreset)
                SetupPage.STEALTH -> StealthPageContent(state.stealthMode, viewModel::setStealthMode)
            }
            if (state.applying) {
                Progress(stringResource(R.string.installing), scrim = true)
            }
        }
        HorizontalDivider(color = colors.separator, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val printersOnly = state.start == SetupStart.PRINTERS
            when {
                // guide/24's Create.
                state.page == SetupPage.PRINTERS && printersOnly ->
                    OrcaButton(stringResource(R.string.create_printer), onClick = viewModel::createPrinter, style = OrcaButtonStyle.Regular, enabled = !state.applying)
                state.page == SetupPage.STEALTH || (state.page == SetupPage.FILAMENTS && state.start != SetupStart.FILAMENTS) ->
                    OrcaButton(stringResource(R.string.back), onClick = viewModel::back, style = OrcaButtonStyle.Regular, enabled = !state.applying)
            }
            Spacer(Modifier.weight(1f))
            when (state.page) {
                SetupPage.PRINTERS -> if (printersOnly) {
                    // guide/24's Cancel and Confirm.
                    OrcaButton(stringResource(R.string.cancel), onClick = viewModel::back, style = OrcaButtonStyle.Regular, enabled = !state.applying)
                    OrcaButton(stringResource(R.string.confirm), onClick = viewModel::confirm, enabled = !state.loadingPrinters && !state.applying)
                } else {
                    OrcaButton(stringResource(R.string.next), onClick = viewModel::next, enabled = !state.loadingPrinters)
                }
                // The whole guide's filament page shows Next, as guide/22 does without the network plug-in.
                SetupPage.FILAMENTS -> if (state.wholeGuide) {
                    OrcaButton(stringResource(R.string.next), onClick = viewModel::next, enabled = !state.filaments.loading)
                } else {
                    OrcaButton(
                        stringResource(R.string.finish),
                        onClick = viewModel::finish,
                        enabled = !state.filaments.loading && !state.applying,
                    )
                }
                SetupPage.STEALTH -> OrcaButton(stringResource(R.string.finish), onClick = viewModel::finish, enabled = !state.applying)
            }
        }
    }

    when (state.notice) {
        SetupNotice.NO_PRINTER -> WizardDialog(
            text = stringResource(R.string.no_printer_selected),
            confirm = stringResource(R.string.ok),
            onConfirm = viewModel::dismissNotice,
            onDismiss = viewModel::dismissNotice,
        )
        SetupNotice.NO_FILAMENT -> WizardDialog(
            text = stringResource(R.string.no_filament_selected) + "\n" + stringResource(R.string.use_default_filaments),
            confirm = stringResource(R.string.yes),
            onConfirm = viewModel::useDefaultFilaments,
            dismiss = stringResource(R.string.no),
            onDismiss = viewModel::dismissNotice,
        )
        null -> Unit
    }
    state.error?.let { message ->
        WizardDialog(text = message, confirm = stringResource(R.string.ok), onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
    }
}

@Composable
private fun PrinterPageContent(state: SetupUiState, viewModel: SetupWizardViewModel) {
    if (state.loadingPrinters) {
        Progress(stringResource(R.string.loading))
        return
    }
    val colors = OrcaTheme.colors
    val vendors = remember(state.printers, state.keyword) { state.vendors }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = state.keyword,
            onValueChange = viewModel::setKeyword,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        // The desktop page's vendor sidebar: a vendor scrolls into view.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vendors, key = PrinterVendor::vendor) { vendor ->
                val selected = vendor.models.count { it.id in state.chosen }
                ToggleChip(
                    label = if (selected > 0) "${PrinterPage.vendorTitle(vendor.vendor)} · $selected" else PrinterPage.vendorTitle(vendor.vendor),
                    checked = selected > 0,
                    role = Role.Button,
                    onClick = {
                        val index = vendors.takeWhile { it != vendor }.sumOf { it.models.size + 1 }
                        scope.launch { gridState.animateScrollToItem(index) }
                    },
                )
            }
        }
        if (vendors.isEmpty()) {
            Text(
                stringResource(R.string.nothing_found),
                color = colors.textSide,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            vendors.forEach { vendor ->
                item(key = "vendor:${vendor.vendor}", span = { GridItemSpan(maxLineSpan) }) {
                    VendorHeader(vendor, state.chosen) { viewModel.toggleVendor(vendor) }
                }
                items(vendor.models.size, key = { vendor.models[it].id }) { index ->
                    val model = vendor.models[index]
                    PrinterCard(model, checked = model.id in state.chosen) { viewModel.toggleModel(model.id) }
                }
            }
        }
    }
}

/** CreateVendorBlock(): the vendor's banner with "selected / all" and its check box. */
@Composable
private fun VendorHeader(vendor: PrinterVendor, chosen: Set<String>, onToggle: () -> Unit) {
    val colors = OrcaTheme.colors
    val selected = vendor.models.count { it.id in chosen }
    val title = PrinterPage.vendorTitle(vendor.vendor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.text, style = OrcaTheme.typography.head15, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.selected_count, selected, vendor.models.size), color = colors.textSide, style = OrcaTheme.typography.body12)
        OrcaCheckBox(
            checked = when (selected) {
                0 -> false
                vendor.models.size -> true
                else -> null
            },
            onCheckedChange = { onToggle() },
        )
    }
}

/** CreatePrinterBlock(): the model's cover, name, and nozzle diameters; a tap chooses the model. */
@Composable
private fun PrinterCard(model: SetupPrinterModel, checked: Boolean, onToggle: () -> Unit) {
    val colors = OrcaTheme.colors
    val shape = OrcaTheme.shapes.compact
    Column(
        modifier = Modifier
            .clip(shape)
            .border(if (checked) 2.dp else 1.dp, if (checked) colors.accent else colors.border, shape)
            .background(if (checked) colors.accentSubtle else colors.window)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Cover(model.cover, Modifier.fillMaxSize())
            OrcaCheckBox(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(
            PrinterPage.modelTitle(model),
            color = colors.text,
            style = OrcaTheme.typography.body13,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.nozzles, model.nozzleDiameters.joinToString(" · ")),
            color = colors.textSide,
            style = OrcaTheme.typography.body11,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The covers are small PNG files; decoded ones are kept while the process lives. */
private val covers = LruCache<String, ImageBitmap>(160)

@Composable
private fun Cover(path: String, modifier: Modifier) {
    val bitmap by produceState(covers.get(path), path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }?.also { covers.put(path, it) }
        }
    }
    bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier) } ?: Box(modifier)
}

/** guide/4orca: what Stealth Mode does, and its check box. */
@Composable
private fun StealthPageContent(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = OrcaTheme.colors
    val webText = rememberOrcaWebText()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .orcaContentWidth(OrcaPageWidth.Text),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            webText(
                "orca4",
                "This disables all cloud features, including Orca Cloud profile syncing. Users who prefer to work entirely offline can enable this option.",
            ),
            color = colors.text,
            style = OrcaTheme.typography.body14,
        )
        Text(
            webText("orca12", "Note: When Stealth Mode is enabled, your user profiles will not be backed up to Orca Cloud."),
            color = colors.textSide,
            style = OrcaTheme.typography.body13,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Checkbox, onValueChange = onChange)
                .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrcaCheckBox(checked = enabled, onCheckedChange = null)
            Text(webText("orca5", "Enable Stealth Mode."), color = colors.text, style = OrcaTheme.typography.body14)
        }
    }
}

/**
 * The filament page (guide/22): the custom filaments, the filters and the
 * filaments they list. A phone has them in one list; a larger window lays the
 * filaments out in columns, and a large one puts the filters in a pane beside
 * them, as the desktop page has its filter block over a wide table.
 */
@Composable
private fun FilamentPageContent(
    page: FilamentPageState,
    viewModel: SetupWizardViewModel,
    covered: Boolean,
    onEditFilamentPreset: (filamentId: String, preset: String) -> Unit,
) {
    if (page.loading) {
        Progress(stringResource(R.string.loading))
        return
    }
    val listed = remember(page) { page.listedLines }
    when (currentOrcaWindowLayout()) {
        OrcaWindowLayout.Compact -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "custom") { CustomFilaments(viewModel) }
            item(key = "filters") { FilamentFilters(page, listed.size, viewModel, wrap = false) }
            if (listed.isEmpty()) item(key = "empty") { NothingFound() }
            items(listed, key = { it }) { index -> FilamentLine(page, index, viewModel) }
        }
        OrcaWindowLayout.Medium -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = FILAMENT_COLUMN_WIDTH),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item(key = "custom", span = { GridItemSpan(maxLineSpan) }) { CustomFilaments(viewModel) }
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { FilamentFilters(page, listed.size, viewModel, wrap = false) }
            if (listed.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { NothingFound() }
            items(listed.size, key = { listed[it] }) { FilamentLine(page, listed[it], viewModel) }
        }
        OrcaWindowLayout.Expanded -> Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(FILTER_PANE_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            ) {
                CustomFilaments(viewModel)
                FilamentFilters(page, listed.size, viewModel, wrap = true)
            }
            VerticalDivider(color = OrcaTheme.colors.separator)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = FILAMENT_COLUMN_WIDTH),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            ) {
                if (listed.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { NothingFound() }
                items(listed.size, key = { listed[it] }) { FilamentLine(page, listed[it], viewModel) }
            }
        }
    }

    CustomFilamentDialogs(viewModel = viewModel, covered = covered, onEditFilamentPreset = onEditFilamentPreset)
}

/** "Custom Filaments" with its "Create New" and the filaments the user made, each with its edit button. */
@Composable
private fun CustomFilaments(viewModel: SetupWizardViewModel) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.custom_filaments),
                color = colors.text,
                style = OrcaTheme.typography.head15,
                modifier = Modifier.weight(1f),
            )
            OrcaButton(
                text = stringResource(R.string.create_new_filament),
                onClick = viewModel::openCreateFilament,
                style = OrcaButtonStyle.Regular,
            )
        }
        for (filament in viewModel.custom) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = filament.name,
                    color = colors.text,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.weight(1f),
                )
                OrcaIconButton(
                    icon = DesignR.drawable.orca_edit,
                    contentDescription = orcaString("Edit Filament"),
                    onClick = { viewModel.editFilament(filament.id) },
                )
            }
        }
    }
}

/**
 * The filters of the page (printer, type, vendor), the search, which lines
 * show, and UpdateStats()'s count with "All" and "Clear all". [wrap] lays the
 * chips out in lines, as the pane beside the filaments has the height for them.
 */
@Composable
private fun FilamentFilters(page: FilamentPageState, visible: Int, viewModel: SetupWizardViewModel, wrap: Boolean) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(top = 4.dp)) {
        FilterChips(
            title = stringResource(R.string.printer),
            items = page.models.indices.toList(),
            label = { page.models[it].id },
            checked = { it in page.machines },
            allChecked = page.machines.size == page.models.size,
            onToggle = viewModel::toggleMachine,
            onToggleAll = viewModel::toggleAllMachines,
            wrap = wrap,
        )
        FilterChips(
            title = stringResource(R.string.filament_type),
            items = page.types,
            label = { it },
            checked = { it in page.checkedTypes },
            allChecked = page.checkedTypes.size == page.types.size,
            onToggle = viewModel::toggleType,
            onToggleAll = viewModel::toggleAllTypes,
            wrap = wrap,
        )
        FilterChips(
            title = stringResource(R.string.vendor),
            items = page.vendors,
            label = { it },
            checked = { it in page.checkedVendors },
            allChecked = page.checkedVendors.size == page.vendors.size,
            onToggle = viewModel::toggleFilamentVendor,
            onToggleAll = viewModel::toggleAllFilamentVendors,
            wrap = wrap,
        )
        SearchField(
            value = page.search,
            onValueChange = viewModel::setSearch,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        OrcaChoiceChips(
            items = listOf(stringResource(R.string.show_all), stringResource(R.string.show_checked), stringResource(R.string.show_unchecked)),
            selected = page.filter.ordinal,
            onSelect = { viewModel.setFilter(FilamentFilter.entries[it]) },
            modifier = Modifier.padding(top = 8.dp),
        )
        // UpdateStats(): checked / all [shown].
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (page.lines.size > visible) {
                    stringResource(R.string.selected_count_filtered, page.checked.size, page.lines.size, visible)
                } else {
                    stringResource(R.string.selected_count, page.checked.size, page.lines.size)
                },
                color = colors.textSide,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.weight(1f),
            )
            OrcaLink(stringResource(R.string.all), onClick = { viewModel.checkListed(true) })
            Spacer(Modifier.width(16.dp))
            OrcaLink(stringResource(R.string.clear_all), onClick = { viewModel.checkListed(false) })
        }
        if (!wrap) HorizontalDivider(color = colors.separator, thickness = 1.dp)
    }
}

/** A filament of the list: its check box, name, vendor and type. */
@Composable
private fun FilamentLine(page: FilamentPageState, index: Int, viewModel: SetupWizardViewModel) {
    val colors = OrcaTheme.colors
    val line = page.lines[index]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = index in page.checked, role = Role.Checkbox, onValueChange = { viewModel.toggleLine(index) })
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaCheckBox(checked = index in page.checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(line.name, color = colors.text, style = OrcaTheme.typography.body14, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${line.vendor} · ${line.type}", color = colors.textSide, style = OrcaTheme.typography.body11, maxLines = 1)
        }
    }
}

@Composable
private fun NothingFound() {
    Text(stringResource(R.string.nothing_found), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14, modifier = Modifier.padding(16.dp))
}

/** The least width of a column of filaments, which a name and its vendor fit. */
private val FILAMENT_COLUMN_WIDTH = 280.dp

/** The pane of the filters beside the filaments. */
private val FILTER_PANE_WIDTH = 360.dp

/** The dialogs the "Custom Filaments" of the filament page open. */
@Composable
private fun CustomFilamentDialogs(
    viewModel: SetupWizardViewModel,
    covered: Boolean,
    onEditFilamentPreset: (filamentId: String, preset: String) -> Unit,
) {
    // The dialog closes itself once the filament is made, so a question that
    // the user refuses keeps what they have filled in.
    if (viewModel.creatingFilament) {
        CreateFilamentDialog(
            loadOptions = viewModel::filamentOptions,
            onCreate = viewModel::createFilament,
            onDismiss = viewModel::closeCreateFilament,
        )
    }
    // Plater::priv::on_modify_filament(): the dialog is there again once the
    // filament tab it opened a preset on closes (ParamsDialog's EVT_MODIFY_FILAMENT).
    viewModel.editingFilament?.takeIf { !covered }?.let { filamentId ->
        EditFilamentDialog(
            filamentId = filamentId,
            loadPresets = viewModel::filamentPresets,
            onEditPreset = { preset -> onEditFilamentPreset(filamentId, preset) },
            onDeletePreset = viewModel::deleteFilamentPreset,
            onDismiss = viewModel::closeEditFilament,
            revision = viewModel.filamentPresetsRevision,
            onAddPreset = viewModel::openAddFilamentPreset,
            onDeleteFilament = viewModel::deleteFilament,
        )
        if (viewModel.addingFilamentPreset) {
            AddFilamentPresetDialog(
                filamentId = filamentId,
                loadSources = viewModel::filamentPresetSources,
                onAdd = viewModel::addFilamentPreset,
                onDismiss = viewModel::closeAddFilamentPreset,
            )
        }
    }
    viewModel.filamentQuestion?.let { question ->
        SettingsQuestionDialog(question, onAnswer = viewModel::answerFilamentQuestion)
    }
    if (viewModel.filamentCreated) {
        CreatePresetSuccessfulDialog(printer = false, onOk = viewModel::dismissFilamentCreated, onDismiss = viewModel::dismissFilamentCreated)
    }
    viewModel.filamentProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = viewModel::dismissFilamentProblem,
            confirmButton = { OrcaButton(orcaString("OK"), onClick = viewModel::dismissFilamentProblem) },
            title = { Text(orcaString("Create Filament"), style = OrcaTheme.typography.head16) },
            text = { Text(orcaString(problem), color = OrcaTheme.colors.error, style = OrcaTheme.typography.body14) },
            containerColor = OrcaTheme.colors.window,
            titleContentColor = OrcaTheme.colors.text,
            textContentColor = OrcaTheme.colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** A filter of the filament page: "All" and a chip per value, in one scrolling line or, with [wrap], in lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterChips(
    title: String,
    items: List<T>,
    label: (T) -> String,
    checked: (T) -> Boolean,
    allChecked: Boolean,
    onToggle: (T) -> Unit,
    onToggleAll: () -> Unit,
    wrap: Boolean = false,
) {
    val colors = OrcaTheme.colors
    Text(title, color = colors.textSide, style = OrcaTheme.typography.body12, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
    val chips: @Composable () -> Unit = {
        ToggleChip(stringResource(R.string.all), allChecked, onClick = onToggleAll)
        items.forEach { item -> ToggleChip(label(item), checked(item), onClick = { onToggle(item) }) }
    }
    if (wrap) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    }
}

@Composable
private fun ToggleChip(label: String, checked: Boolean, onClick: () -> Unit, role: Role = Role.Checkbox) {
    val colors = OrcaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (checked) colors.accent else Color.Transparent)
            .border(1.dp, if (checked) colors.accent else colors.border, shape)
            .then(
                if (role == Role.Checkbox) {
                    Modifier.toggleable(value = checked, role = role, onValueChange = { onClick() })
                } else {
                    Modifier.clickable(role = role, onClick = onClick)
                },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (checked) colors.onAccent else colors.text, style = OrcaTheme.typography.body13, maxLines = 1)
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.buttonBackground)
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

@Composable
private fun Progress(text: String, scrim: Boolean = false) {
    val colors = OrcaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scrim) {
                    Modifier
                        .background(colors.window.copy(alpha = 0.85f))
                        // Blocks taps on the page underneath.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.accent)
            Text(text, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun WizardDialog(
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String? = null,
) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(confirm, onClick = onConfirm) },
        dismissButton = dismiss?.let { { OrcaButton(it, onClick = onDismiss, style = OrcaButtonStyle.Regular) } },
        title = { Text(stringResource(R.string.error), style = OrcaTheme.typography.head16) },
        text = { Text(text, style = OrcaTheme.typography.body14) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
