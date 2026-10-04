package app.orcinus.shadow.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.ui.settings.BedShapeFileActions
import app.orcinus.shadow.core.ui.settings.SettingsSearchSheet
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaPageTopBar
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarSection
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PendingPresetChange
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.preset.PresetListSheet
import app.orcinus.shadow.core.ui.settings.PresetChangeActions
import app.orcinus.shadow.core.ui.settings.PresetChangeDialog
import app.orcinus.shadow.core.ui.settings.SettingsActions
import app.orcinus.shadow.core.ui.settings.SettingsModeSwitch
import app.orcinus.shadow.core.ui.settings.SettingsPresetButtons
import app.orcinus.shadow.core.ui.settings.SettingsTabDialogs
import app.orcinus.shadow.core.ui.settings.rememberSettingsTab
import app.orcinus.shadow.core.ui.settings.settingsTabItems
import app.orcinus.shadow.core.ui.settings.tabTitle
import app.orcinus.shadow.domain.plate.BedShapeFilesUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.SetBedShapeUseCase
import app.orcinus.shadow.domain.plate.SelectPresetUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the settings page of one preset kind shows. */
data class PresetSettingsUiState(
    val kind: PresetKind,
    /** Null until the engine reported the presets. */
    val presets: Presets? = null,
    /** The preset can be chosen and edited: the plate is not busy. */
    val enabled: Boolean = false,
    val tab: SettingsTabState = SettingsTabState(PresetKind.PRINT),
    /** A preset choice that waits for what happens to the unsaved changes. */
    val presetChange: PendingPresetChange? = null,
)

class PresetSettingsViewModel(
    private val kind: PresetKind,
    observePlate: ObservePlateUseCase,
    private val selectPreset: SelectPresetUseCase,
    private val settingsTabs: PresetSettingsTabs,
    private val setBedShape: SetBedShapeUseCase,
    private val bedShapeFiles: BedShapeFilesUseCase? = null,
) : ViewModel() {
    val state: StateFlow<PresetSettingsUiState> = observePlate()
        .map { it.toUiState(kind) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toUiState(kind))

    init {
        // The page is the first to open this tab; the engine describes it once,
        // and the plate keeps it for the next time the page opens.
        if (state.value.tab.settings == null) {
            settingsTabs.request(kind, SettingsRequest.Describe)
        }
    }

    fun choose(choice: PresetChoice) = selectPreset(choice)

    fun resolvePresetChange(action: PresetChangeAction) = selectPreset.resolve(action)

    fun savePresetChange(name: String) = selectPreset.save(name)

    fun cancelPresetChange() = selectPreset.cancelPresetChange()

    fun requestSettings(kind: PresetKind, request: SettingsRequest) = settingsTabs.request(kind, request)

    fun answerSettingsQuestion(kind: PresetKind, yes: Boolean) = settingsTabs.answer(kind, yes)

    fun dismissSettingsNotice(kind: PresetKind) = settingsTabs.dismissNotice(kind)

    suspend fun settingTooltip(kind: PresetKind, id: String): List<OrcaText> = settingsTabs.tooltip(kind, id)

    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = settingsTabs.checkPresetName(kind, name)

    suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome = settingsTabs.compatiblePresetChoices(kind, key)

    /** Search::OptionsSearcher: every setting the preset tabs show. */
    suspend fun searchCatalog(): SearchCatalogOutcome = settingsTabs.searchCatalog()

    /** EditGCodeDialog: the G-code of a setting with the placeholders, and what a placeholder is. */
    suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome = settingsTabs.gcodePlaceholders(kind, key)

    suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo = settingsTabs.gcodePlaceholder(key, presets)

    /** BedShapeDialog: the printable area of the edited printer, and the shape it is set to. */
    suspend fun bedShape(): BedShapeOutcome = settingsTabs.bedShape()

    suspend fun setBedShape(shape: BedShape) = setBedShape.invoke(shape)

    /** BedShapeDialog's files and drawing. */
    val bedFiles: BedShapeFileActions = bedShapeFiles?.let { use ->
        BedShapeFileActions(
            loadShape = use::loadShape,
            keep = use::keep,
            preview = use::preview,
            exists = use::exists,
        )
    } ?: BedShapeFileActions.NONE

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toUiState(kind: PresetKind) = PresetSettingsUiState(
    kind = kind,
    presets = presets,
    enabled = profiles != null && !busy,
    tab = settingsTabs[kind] ?: SettingsTabState(kind),
    presetChange = presetChange,
)

/**
 * The settings of one preset kind, as OrcaSlicer shows them in the dialog its
 * sidebar opens (ParamsDialog with the filament or the printer tab): the preset
 * of the plate, the buttons that save, delete or undo it, and the pages of the
 * tab.
 */
@Composable
fun PresetSettingsRoute(
    viewModel: PresetSettingsViewModel,
    onBack: () -> Unit,
    onOpenTab: (SearchOption) -> Unit = {},
    openOption: String? = null,
    openPage: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PresetSettingsScreen(
        state = state,
        onChoose = viewModel::choose,
        onBack = onBack,
        onOpenTab = onOpenTab,
        openOption = openOption,
        openPage = openPage,
        searchCatalog = viewModel::searchCatalog,
        actions = SettingsActions(
            request = viewModel::requestSettings,
            answer = viewModel::answerSettingsQuestion,
            dismissNotice = viewModel::dismissSettingsNotice,
            tooltip = viewModel::settingTooltip,
            checkPresetName = viewModel::checkPresetName,
            compatibleChoices = viewModel::compatiblePresetChoices,
            bedShape = viewModel::bedShape,
            setBedShape = viewModel::setBedShape,
            bedShapeFiles = viewModel.bedFiles,
            gcodePlaceholders = viewModel::gcodePlaceholders,
            gcodePlaceholder = viewModel::gcodePlaceholder,
        ),
        presetChange = PresetChangeActions(
            resolve = viewModel::resolvePresetChange,
            save = viewModel::savePresetChange,
            cancel = viewModel::cancelPresetChange,
        ),
    )
}

@Composable
internal fun PresetSettingsScreen(
    state: PresetSettingsUiState,
    onChoose: (PresetChoice) -> Unit,
    onBack: () -> Unit,
    actions: SettingsActions,
    presetChange: PresetChangeActions,
    /** A setting the search found on another tab opens that tab. */
    onOpenTab: (SearchOption) -> Unit = {},
    /** The setting the search found before the page opened, and its page. */
    openOption: String? = null,
    openPage: String? = null,
    searchCatalog: suspend () -> SearchCatalogOutcome = { SearchCatalogOutcome.Failure("") },
) {
    var choosingPreset by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val presets = state.presets
    val enabled = state.enabled && presets != null
    val tab = rememberSettingsTab(state.tab, actions, enabled)
    val rows = rememberLazyListState()
    // The setting the search found on another tab, which opened this page.
    LaunchedEffect(openOption) {
        if (openOption == null) return@LaunchedEffect
        tab.highlighted = openOption
        actions.request(state.kind, SettingsRequest.SelectPage(openPage.orEmpty()))
    }
    // Tab::activate_option(): the tab scrolls to the setting the search found
    // once its page is shown, and marks it there.
    LaunchedEffect(tab.highlighted, tab.page?.title) {
        val id = tab.highlighted ?: return@LaunchedEffect
        val index = tab.indexOf(id, leading = 1) ?: return@LaunchedEffect
        rows.animateScrollToItem(index)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window),
    ) {
        OrcaPageTopBar(
            title = orcaString(state.kind.tabTitle),
            backDescription = stringResource(R.string.settings_back),
            onBack = onBack,
        ) {
            // The search of the desktop app's sidebar, which finds a setting on
            // any of the tabs (Search::SearchDialog).
            OrcaIconButton(
                icon = DesignR.drawable.orca_search,
                contentDescription = stringResource(UiR.string.settings_search),
                onClick = { searching = true },
                enabled = enabled,
                tint = OrcaTheme.colors.onTabBar,
            )
        }
        LazyColumn(
            state = rows,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            item(key = "preset") {
            OrcaSidebarSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrcaComboField(
                        text = presets?.items(state.kind)?.selectedLabel().orEmpty(),
                        enabled = enabled,
                        onClick = { choosingPreset = true },
                        modifier = Modifier.weight(1f),
                    )
                    state.tab.settings?.let { SettingsPresetButtons(state.kind, it, enabled, actions) }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = orcaString("Settings"),
                        color = OrcaTheme.colors.textLabel,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    state.tab.settings?.let { SettingsModeSwitch(state.kind, it, enabled, actions) }
                }
            }
            }

            settingsTabItems(tab)
        }
        SettingsTabDialogs(tab)
    }

    if (searching) {
        SettingsSearchSheet(
            mode = state.tab.settings?.mode ?: SettingsMode.SIMPLE,
            loadCatalog = searchCatalog,
            onChoose = { option ->
                searching = false
                // Tab::activate_option(): the setting's own tab and page open.
                if (option.kind == state.kind) {
                    tab.highlighted = option.id
                    actions.request(state.kind, SettingsRequest.SelectPage(option.page.firstOrNull()?.msgid.orEmpty()))
                } else {
                    onOpenTab(option)
                }
            },
            onDismiss = { searching = false },
        )
    }

    if (choosingPreset && presets != null) {
        PresetListSheet(
            title = orcaString(state.kind.tabTitle),
            items = presets.items(state.kind),
            onDismiss = { choosingPreset = false },
            onChoose = { item ->
                choosingPreset = false
                onChoose(state.kind.choiceOf(item))
            },
        )
    }

    PresetChangeDialog(state.presetChange, presets, presetChange, actions)
}

private fun Presets.items(kind: PresetKind): List<PresetListItem> = when (kind) {
    PresetKind.FILAMENT -> filaments
    PresetKind.PRINTER -> printers
    // The settings of an object and of the plate override the process preset.
    else -> processes
}

/** A printer of the system presets is chosen by its model, as the sidebar does. */
private fun PresetKind.choiceOf(item: PresetListItem): PresetChoice = when (this) {
    PresetKind.FILAMENT -> PresetChoice.Filament(ProfileId(item.name))
    PresetKind.PRINTER ->
        if (item.group == PresetGroup.SYSTEM) PresetChoice.PrinterModel(item.name) else PresetChoice.Printer(ProfileId(item.name))
    else -> PresetChoice.Process(ProfileId(item.name))
}

/** The label the combo box shows for its selected entry. */
private fun List<PresetListItem>.selectedLabel(): String = firstOrNull(PresetListItem::selected)?.label.orEmpty()
