package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaModeSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsPage
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a settings tab asks of the app: its requests, and what it reads while a dialog is open. */
class SettingsActions(
    val request: (PresetKind, SettingsRequest) -> Unit,
    val answer: (PresetKind, Boolean) -> Unit,
    val dismissNotice: (PresetKind) -> Unit,
    val tooltip: suspend (PresetKind, String) -> List<OrcaText>,
    val checkPresetName: suspend (PresetKind, String) -> PresetNameOutcome,
    val compatibleChoices: suspend (PresetKind, String) -> PresetNamesOutcome,
    /** BedShapeDialog: the printable area of the edited printer, and the shape it is set to. */
    val bedShape: suspend () -> BedShapeOutcome,
    val setBedShape: suspend (BedShape) -> Unit,
    /** BedShapeDialog's files and drawing. */
    val bedShapeFiles: BedShapeFileActions = BedShapeFileActions.NONE,
    /** EditGCodeDialog: the G-code of a setting of a tab with the placeholders, and what one of them is. */
    val gcodePlaceholders: suspend (PresetKind, String) -> GcodePlaceholdersOutcome,
    val gcodePlaceholder: suspend (String, Boolean) -> GcodePlaceholderInfo,
)

/**
 * One of OrcaSlicer's settings tabs while it is on screen: what the engine
 * described, and the dialogs the tab opens over it. A page of a tab has up to a
 * few hundred lines, so they are rows of the screen's list
 * ([settingsTabItems]), and only the ones in view are built.
 */
@Stable
class SettingsTabUi internal constructor(internal val scope: CoroutineScope) {
    internal var state: SettingsTabState by mutableStateOf(SettingsTabState(PresetKind.PRINT))
    internal var actions: SettingsActions by mutableStateOf(NO_ACTIONS)
    internal var enabled: Boolean by mutableStateOf(false)
    internal var tooltip: SettingTooltip? by mutableStateOf(null)
    internal var compatible: CompatibleChoice? by mutableStateOf(null)
    /** The shape of the plate is being set (TabPrinter::create_bed_shape_widget). */
    internal var bedShapeOpen: Boolean by mutableStateOf(false)
    /** The custom G-code EditGCodeDialog edits (Tab::edit_custom_gcode). */
    internal var gcodeEdited: String? by mutableStateOf(null)
    /** The ramming parameters RammingDialog opened with. */
    internal var rammingEdited: String? by mutableStateOf(null)

    /**
     * The setting the search jumped to, which the tab marks (Tab::activate_option).
     * The mark blinks and goes out by itself (Tab::Highlighter).
     */
    var highlighted: String? by mutableStateOf(null)

    /** Whether the mark of [highlighted] is showing in this blink. */
    internal var highlightShown: Boolean by mutableStateOf(false)

    /** What the tab draws, or null until the engine has described it. */
    val view: SettingsView?
        get() {
            val current = state
            if (viewOf !== current.tab || viewFor !== current.settings) {
                viewOf = current.tab
                viewFor = current.settings
                lastView = current.tab?.let { tab -> current.settings?.let { SettingsView(tab, it) } }
            }
            return lastView
        }

    /**
     * Where the line of [optionId] sits in the list, counting the rows the tab
     * itself adds; [leading] is what the screen put before them. Null while the
     * setting is not on the page the tab shows.
     */
    fun indexOf(optionId: String, leading: Int = 0, pageTabs: Boolean = true): Int? {
        val view = view ?: return null
        val page = page ?: return null
        var index = leading
        if (state.problem != null) index++
        if (pageTabs) index++
        if (view.settings.variants.size > 1) index++
        for (group in page.groups.filter(view::isVisible)) {
            if (group.title.isNotEmpty()) index++
            val lines = group.lines.filter(view::isVisible)
            lines.forEachIndexed { at, line ->
                if (line.options.any { it.id == optionId }) return index + at
            }
            index += lines.size
        }
        return null
    }

    /** The page the tab shows: the one the engine toggled, or the first visible one. */
    val page: SettingsPage?
        get() = view?.let { view -> view.visiblePages.firstOrNull { it.title == view.settings.activePage } ?: view.visiblePages.firstOrNull() }

    internal val request: (SettingsRequest) -> Unit = { actions.request(state.kind, it) }

    internal val showTooltip: (String, String) -> Unit = { id, label ->
        tooltip = SettingTooltip(label, null)
        val kind = state.kind
        scope.launch {
            val text = actions.tooltip(kind, id)
            tooltip = tooltip?.takeIf { it.label == label }?.copy(text = text)
        }
    }

    internal val chooseCompatible: (String, List<String>) -> Unit = { key, selected -> compatible = CompatibleChoice(key, selected) }

    internal val openBedShape: () -> Unit = { bedShapeOpen = true }

    internal val editCustomGcode: (String) -> Unit = { key -> gcodeEdited = key }

    internal val editRamming: (String) -> Unit = { parameters -> rammingEdited = parameters }

    private var viewOf: Any? = null
    private var viewFor: Any? = null
    private var lastView: SettingsView? = null

    private companion object {
        val NO_ACTIONS = SettingsActions(
            request = { _, _ -> },
            answer = { _, _ -> },
            dismissNotice = {},
            tooltip = { _, _ -> emptyList() },
            checkPresetName = { _, _ -> PresetNameOutcome.Failure("") },
            compatibleChoices = { _, _ -> PresetNamesOutcome.Failure("") },
            bedShape = { BedShapeOutcome.Failure("") },
            setBedShape = {},
            gcodePlaceholders = { _, _ -> GcodePlaceholdersOutcome.Failure("") },
            gcodePlaceholder = { _, _ -> GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true) },
        )
    }
}

/** Keeps the tab of [state] on screen: its dialogs stay open while the engine describes it again. */
@Composable
fun rememberSettingsTab(state: SettingsTabState, actions: SettingsActions, enabled: Boolean): SettingsTabUi {
    val scope = rememberCoroutineScope()
    val ui = remember(scope) { SettingsTabUi(scope) }
    ui.state = state
    ui.actions = actions
    ui.enabled = enabled
    // Tab::Highlighter: the marked line blinks every 300 ms, and the mark goes
    // out after the eleventh blink (blink() calls invalidate()).
    LaunchedEffect(ui.highlighted) {
        if (ui.highlighted == null) return@LaunchedEffect
        ui.highlightShown = true
        repeat(HIGHLIGHT_BLINKS) {
            delay(HIGHLIGHT_INTERVAL_MILLIS)
            ui.highlightShown = !ui.highlightShown
        }
        ui.highlighted = null
        ui.highlightShown = false
    }
    return ui
}

/** Highlighter::init(): m_timer.Start(300, false). */
private const val HIGHLIGHT_INTERVAL_MILLIS = 300L

/** Highlighter::blink(): the eleventh tick invalidates the mark. */
private const val HIGHLIGHT_BLINKS = 11

/**
 * The rows of the tab: the pages of the edited preset under underline tabs, and
 * the lines of the chosen page's option groups.
 */
fun LazyListScope.settingsTabItems(ui: SettingsTabUi, pageTabs: Boolean = true) {
    val state = ui.state
    state.problem?.let { problem ->
        item(key = "settings:problem") {
            Text(problem, color = OrcaTheme.colors.error, style = OrcaTheme.typography.body12, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
    val view = ui.view
    if (view == null) {
        if (state.changing) {
            item(key = "settings:loading") {
                Text(
                    stringResource(R.string.settings_loading),
                    color = OrcaTheme.colors.textSide,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        return
    }
    val page = ui.page ?: return
    if (pageTabs) {
        item(key = "settings:tabs") {
            // The engine toggles the fields of the page it was given; it is
            // asked for another one when that page is not among the visible ones.
            LaunchedEffect(view.settings.activePage, page.title) {
                if (view.settings.activePage != page.title) ui.request(SettingsRequest.SelectPage(page.title))
            }
            SettingsPageTabs(view, page, ui.request)
        }
    }
    // Tab::m_variant_combo, which the desktop shows only with more than one
    // extruder variant (m_variant_combo->Enable(options.size() > 1)).
    if (view.settings.variants.size > 1) {
        item(key = "settings:variant") {
            OrcaSegmentedSwitch(
                options = view.settings.variants,
                selectedIndex = view.settings.variant,
                onSelect = { ui.request(SettingsRequest.SetVariant(it)) },
                enabled = ui.enabled,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
    settingsPageItems(ui, view, page)
}

/** The dialogs of the tab: its message boxes, a tooltip, and the presets it is compatible with. */
@Composable
fun SettingsTabDialogs(ui: SettingsTabUi) {
    val state = ui.state
    ui.tooltip?.let { tooltip -> SettingTooltipDialog(tooltip.label, tooltip.text, onDismiss = { ui.tooltip = null }) }
    ui.compatible?.let { choice ->
        CompatiblePresetsDialog(
            title = if (choice.key == "compatible_printers") "Compatible printers" else "Compatible process profiles",
            selected = choice.selected,
            loadChoices = { ui.actions.compatibleChoices(state.kind, choice.key) },
            onApply = { presets ->
                ui.compatible = null
                ui.request(SettingsRequest.SetCompatible(choice.key, presets))
            },
            onDismiss = { ui.compatible = null },
        )
    }
    if (ui.bedShapeOpen) {
        BedShapeSheet(
            load = ui.actions.bedShape,
            files = ui.actions.bedShapeFiles,
            onApply = { shape ->
                ui.bedShapeOpen = false
                ui.scope.launch { ui.actions.setBedShape(shape) }
            },
            onDismiss = { ui.bedShapeOpen = false },
        )
    }
    ui.gcodeEdited?.let { key ->
        EditGcodeDialog(
            key = key,
            loadPlaceholders = { ui.actions.gcodePlaceholders(state.kind, key) },
            describePlaceholder = ui.actions.gcodePlaceholder,
            onApply = { gcode ->
                ui.gcodeEdited = null
                ui.request(SettingsRequest.EditCustomGcode(key, gcode))
            },
            onDismiss = { ui.gcodeEdited = null },
        )
    }
    ui.rammingEdited?.let { parameters ->
        RammingDialog(
            parameters = parameters,
            onApply = { written ->
                ui.rammingEdited = null
                ui.request(SettingsRequest.SetRammingParameters(written))
            },
            onDismiss = { ui.rammingEdited = null },
        )
    }
    state.question?.let { question -> SettingsQuestionDialog(question.dialog, onAnswer = { ui.actions.answer(state.kind, it) }) }
        ?: state.notices.firstOrNull()?.let { SettingsNoticeDialog(it, onDismiss = { ui.actions.dismissNotice(state.kind) }) }
}

/**
 * The preset buttons of Tab: save the edited preset, delete a user preset, and
 * undo every change back to the saved preset.
 */
@Composable
fun SettingsPresetButtons(kind: PresetKind, settings: PresetSettings, enabled: Boolean, actions: SettingsActions) {
    var saving by rememberSaveable { mutableStateOf(false) }
    OrcaIconButton(
        icon = DesignR.drawable.orca_save,
        contentDescription = orcaText(OrcaText("Save current %s", listOf(kind.tabTitle), translateArgs = true)),
        onClick = { saving = true },
        enabled = enabled,
    )
    if (settings.canDelete) {
        OrcaIconButton(
            icon = DesignR.drawable.orca_cross,
            contentDescription = orcaString("Delete this preset"),
            onClick = { actions.request(kind, SettingsRequest.Delete) },
            enabled = enabled,
        )
    }
    if (settings.dirty) {
        OrcaIconButton(
            icon = DesignR.drawable.orca_undo,
            contentDescription = orcaString("Click to reset all settings to the last saved preset."),
            onClick = { actions.request(kind, SettingsRequest.Reset(emptyList())) },
            enabled = enabled,
        )
    }
    if (saving) {
        val copy = orcaString("Copy", context = "PresetName")
        SavePresetDialog(
            kind = kind,
            suggestedName = if (settings.saveNameCopySuffix) "${settings.saveName} - $copy" else settings.saveName,
            checkName = { name -> actions.checkPresetName(kind, name) },
            onSave = { name ->
                saving = false
                actions.request(kind, SettingsRequest.Save(name))
            },
            onDismiss = { saving = false },
        )
    }
}

/** ParamsPanel's mode switch: which settings the tab shows. */
@Composable
fun SettingsModeSwitch(kind: PresetKind, settings: PresetSettings, enabled: Boolean, actions: SettingsActions) {
    OrcaModeSwitch(
        selection = settings.mode.switchPosition,
        // Every choice of the switch is sent, even one that looks like the mode
        // already shown: while a change is on its way the shown mode is the old
        // one, and a comparison with it would swallow the next choice.
        onSelect = { actions.request(kind, SettingsRequest.SetMode(settingsModeAt(it))) },
        contentDescription = orcaString(listOf("Simple settings", "Advanced settings", "Expert settings")[settings.mode.switchPosition]),
        enabled = enabled,
        developer = settings.mode == SettingsMode.DEVELOP,
    )
}

/** The tooltip dialog of a setting: its label, and its text once the engine gave it. */
internal data class SettingTooltip(val label: String, val text: List<OrcaText>?)

/** The open dialog of compatible presets: which setting, and what it holds. */
internal data class CompatibleChoice(val key: String, val selected: List<String>)
