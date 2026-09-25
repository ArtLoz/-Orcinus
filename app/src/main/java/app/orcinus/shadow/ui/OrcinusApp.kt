package app.orcinus.shadow.ui

import app.orcinus.shadow.domain.plate.DismissPlateNoticeUseCase
import app.orcinus.shadow.domain.plate.AnswerPlateQuestionUseCase
import app.orcinus.shadow.core.ui.settings.SettingsQuestionDialog
import app.orcinus.shadow.core.ui.settings.SettingsNoticeDialog
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.orcinus.shadow.R
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaTab
import app.orcinus.shadow.core.designsystem.component.OrcaTabBar
import app.orcinus.shadow.core.designsystem.layout.OrcaSidebarLayout
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.di.AppContainer
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import app.orcinus.shadow.domain.plate.StartEngineUseCase
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.feature.about.navigation.AboutNavKey
import app.orcinus.shadow.feature.about.navigation.aboutEntries
import app.orcinus.shadow.feature.prepare.R as PrepareR
import app.orcinus.shadow.feature.prepare.navigation.PrepareNavKey
import app.orcinus.shadow.feature.prepare.navigation.prepareEntry
import app.orcinus.shadow.feature.preview.R as PreviewR
import app.orcinus.shadow.feature.preview.navigation.PreviewNavKey
import app.orcinus.shadow.feature.preview.navigation.previewEntry
import app.orcinus.shadow.feature.setup.SetupStart
import app.orcinus.shadow.feature.settings.navigation.PresetSettingsNavKey
import app.orcinus.shadow.feature.settings.navigation.presetSettingsEntry
import app.orcinus.shadow.feature.setup.navigation.SetupNavKey
import app.orcinus.shadow.feature.setup.navigation.setupEntry
import app.orcinus.shadow.feature.sidebar.PlateSidebar
import app.orcinus.shadow.feature.sidebar.PresetWizardPage
import app.orcinus.shadow.feature.sidebar.R as SidebarR
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** App-wide state behind the tab bar: engine start, the slice action, and results that open Preview. */
class AppShellViewModel(
    observePlate: ObservePlateUseCase,
    startEngine: StartEngineUseCase,
    private val slicePlate: SlicePlateUseCase,
    private val answerPlateQuestion: AnswerPlateQuestionUseCase,
    private val dismissPlateNotice: DismissPlateNoticeUseCase,
) : ViewModel() {
    val plate: StateFlow<PlateState> = observePlate()

    init {
        viewModelScope.launch { startEngine() }
    }

    fun slice() = slicePlate()

    /** OrcaSlicer's message boxes while it changes the plate: a load or the object menu. */
    fun answer(yes: Boolean) = answerPlateQuestion(yes)

    fun dismissNotice() = dismissPlateNotice()
}

/** The workspace: OrcaSlicer's tabs and sidebar. Pages such as About open over it. */
@Serializable
data object WorkspaceNavKey : NavKey

/**
 * The app: the workspace at the root of the back stack, and pages opened over
 * the whole window on top of it.
*/
@Composable
fun OrcinusApp(
    container: AppContainer,
    onSliceRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shell = viewModel {
        AppShellViewModel(container.observePlate, container.startEngine, container.slicePlate, container.answerPlateQuestion, container.dismissPlateNotice)
    }
    val backStack = rememberNavBackStack(WorkspaceNavKey)
    val plate by shell.plate.collectAsStateWithLifecycle()
    // GUI_App::config_wizard_startup(): the Setup Wizard opens while no printer is set up.
    LaunchedEffect(plate.presets?.setupRequired) {
        if (plate.presets?.setupRequired == true && backStack.none { it is SetupNavKey }) {
            backStack.add(SetupNavKey(SetupStart.FIRST_RUN))
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        // A page can open over the workspace instead of replacing it.
        sceneStrategies = remember { listOf(PageOverlaySceneStrategy<NavKey>()) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<WorkspaceNavKey> {
                Workspace(
                    container = container,
                    shell = shell,
                    onSliceRequested = onSliceRequested,
                    onOpenWizard = { page ->
                        backStack.add(SetupNavKey(if (page == PresetWizardPage.PRINTERS) SetupStart.PRINTERS else SetupStart.FILAMENTS))
                    },
                    onOpenSettings = { kind -> backStack.add(PresetSettingsNavKey(kind)) },
                    // The search of the process panel opens the page of the
                    // setting it found (Tab::activate_option).
                    onOpenSetting = { option ->
                        backStack.add(PresetSettingsNavKey(option.kind, option.id, option.page.firstOrNull()?.msgid))
                    },
                    onOpenAbout = { backStack.add(AboutNavKey) },
                )
            }
            setupEntry(
                createViewModel = container::setupWizardViewModel,
                onClose = { backStack.removeAll { it is SetupNavKey } },
            )
            presetSettingsEntry(
                createViewModel = container::presetSettingsViewModel,
                onBack = { backStack.removeLastOrNull() },
                // A setting the search found on another tab opens that tab in
                // its place, as the desktop app's search jumps between tabs.
                onOpenTab = { option ->
                    backStack.removeLastOrNull()
                    backStack.add(PresetSettingsNavKey(option.kind, option.id, option.page.firstOrNull()?.msgid))
                },
            )
            aboutEntries(
                appInfo = container.appInfo,
                viewModels = container,
                logo = { AppLogo() },
                onNavigate = { backStack.add(it) },
                onBack = { backStack.removeLastOrNull() },
            )
        },
    )
}

/**
 * OrcaSlicer's tab bar over the feature destinations, and its sidebar, which
 * Prepare and Preview share. On a phone the sidebar is a modal drawer over the
 * whole screen; in a wide window it is docked under the tab bar. The tabs are
 * top-level pages; Preview sits on top of Prepare in their back stack, so Back
 * returns to Prepare. Features do not know each other; the workspace opens
 * Preview when a slice finishes, as OrcaSlicer does.
 */
@Composable
private fun Workspace(
    container: AppContainer,
    shell: AppShellViewModel,
    onSliceRequested: () -> Unit,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenSettings: (PresetKind) -> Unit,
    onOpenSetting: (SearchOption) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val plate by shell.plate.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(PrepareNavKey)
    // The message boxes OrcaSlicer showed while it changed the plate, in their
    // order, then the question it waits on, over whichever tab is open.
    val notice = plate.plateNotices.firstOrNull()
    val question = plate.plateQuestion?.question
    when {
        notice != null -> SettingsNoticeDialog(notice, onDismiss = shell::dismissNotice)
        question != null -> SettingsQuestionDialog(question, onAnswer = shell::answer)
    }
    val layout = currentOrcaWindowLayout()
    var sidebarVisible by rememberSaveable(layout) { mutableStateOf(layout == OrcaWindowLayout.Wide) }

    var shownResult by rememberSaveable { mutableStateOf(plate.result?.jobId?.value) }
    LaunchedEffect(plate.result?.jobId) {
        val jobId = plate.result?.jobId?.value
        if (jobId != null && jobId != shownResult) {
            shownResult = jobId
            backStack.showTab(PreviewNavKey)
        }
    }

    val destinations = listOf(PrepareNavKey, PreviewNavKey)
    val tabs = listOf(
        OrcaTab(stringResource(PrepareR.string.prepare_title), DesignR.drawable.orca_tab_3d_active),
        OrcaTab(stringResource(PreviewR.string.preview_title), DesignR.drawable.orca_tab_preview_active),
    )

    OrcaSidebarLayout(
        layout = layout,
        sidebarVisible = sidebarVisible,
        onSidebarVisibleChange = { sidebarVisible = it },
        toggleDescription = stringResource(if (sidebarVisible) SidebarR.string.collapse_sidebar else SidebarR.string.expand_sidebar),
        topBar = {
            OrcaTabBar(
                tabs = tabs,
                selectedIndex = destinations.indexOf(backStack.lastOrNull()).coerceAtLeast(0),
                onSelect = { backStack.showTab(destinations[it]) },
                fillWidth = layout == OrcaWindowLayout.Compact,
            ) {
                OrcaButton(
                    text = stringResource(PrepareR.string.slice_plate),
                    onClick = {
                        onSliceRequested()
                        shell.slice()
                    },
                    enabled = plate.canSlice,
                )
            }
        },
        sidebar = {
            PlateSidebar(
                createViewModel = container::sidebarViewModel,
                onOpenWizard = { page ->
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenWizard(page)
                },
                onOpenSettings = { kind ->
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenSettings(kind)
                },
                onOpenSetting = { option ->
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenSetting(option)
                },
                onOpenAbout = {
                    // The drawer of a phone is closed when the page comes back.
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenAbout()
                },
                onShowCanvas = {
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                },
            )
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            // Tabs switch in place, as on desktop.
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            entryProvider = entryProvider {
                prepareEntry(
                    createViewModel = container::prepareViewModel,
                    onSliceRequested = onSliceRequested,
                    onOpenSidebar = { sidebarVisible = true },
                )
                previewEntry(createViewModel = container::previewViewModel, onSliceRequested = onSliceRequested)
            },
        )
    }
}

/**
 * The launcher icon, drawn from its adaptive layers. A launcher shows the middle
 * 72 of the 108 dp layers, so the foreground is drawn larger and clipped.
 */
@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colorResource(R.color.launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(132.dp))
    }
}

private fun NavBackStack<NavKey>.showTab(destination: NavKey) {
    when (destination) {
        PrepareNavKey -> while (size > 1) removeAt(lastIndex)
        else -> if (lastOrNull() != destination) {
            while (size > 1) removeAt(lastIndex)
            add(destination)
        }
    }
}
