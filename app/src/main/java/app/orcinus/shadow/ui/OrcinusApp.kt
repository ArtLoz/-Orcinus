package app.orcinus.shadow.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
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
import app.orcinus.shadow.core.model.ArchiveEntry
import app.orcinus.shadow.core.model.ArchivePreview
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChangesAnswer
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.ProjectPrompt
import app.orcinus.shadow.core.model.ReloadPrompt
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.StepMeshChoice
import app.orcinus.shadow.core.model.notificationLevel
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.plate.ArchivePreviewSheet
import app.orcinus.shadow.core.ui.plate.LoadProgressDialog
import app.orcinus.shadow.core.ui.plate.ObjColorActions
import app.orcinus.shadow.core.ui.plate.ObjColorDialog
import app.orcinus.shadow.core.ui.plate.ProfileUpdateDialog
import app.orcinus.shadow.core.ui.plate.ProjectDropSheet
import app.orcinus.shadow.core.ui.plate.ProjectPresetChangesDialog
import app.orcinus.shadow.core.ui.plate.ProjectRestoreDialog
import app.orcinus.shadow.core.ui.plate.ProjectSaveChangesDialog
import app.orcinus.shadow.core.ui.plate.SliceButton
import app.orcinus.shadow.core.ui.plate.StepMeshDialog
import app.orcinus.shadow.core.ui.settings.SettingsNoticeDialog
import app.orcinus.shadow.core.ui.settings.SettingsQuestionDialog
import app.orcinus.shadow.di.AppContainer
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.AnswerPlateQuestionUseCase
import app.orcinus.shadow.domain.plate.AutoSliceUseCase
import app.orcinus.shadow.domain.plate.DismissPlateNoticeUseCase
import app.orcinus.shadow.domain.plate.ObjColorPrompt
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.OpenFilesUseCase
import app.orcinus.shadow.domain.plate.ProfileUpdatesUseCase
import app.orcinus.shadow.domain.plate.ProjectBackupUseCase
import app.orcinus.shadow.domain.plate.ProjectLifecycleUseCase
import app.orcinus.shadow.domain.plate.ReloadFromDiskUseCase
import app.orcinus.shadow.domain.plate.SetSliceModeUseCase
import app.orcinus.shadow.domain.plate.SliceActionUseCase
import app.orcinus.shadow.domain.plate.StartEngineUseCase
import app.orcinus.shadow.domain.plate.StepMeshPrompt
import app.orcinus.shadow.feature.about.navigation.AboutNavKey
import app.orcinus.shadow.feature.about.navigation.NetworkTestNavKey
import app.orcinus.shadow.feature.about.navigation.TroubleshootNavKey
import app.orcinus.shadow.feature.about.navigation.aboutEntries
import app.orcinus.shadow.feature.device.navigation.DeviceNavKey
import app.orcinus.shadow.feature.device.navigation.deviceEntry
import app.orcinus.shadow.feature.home.navigation.HomeNavKey
import app.orcinus.shadow.feature.home.navigation.homeEntry
import app.orcinus.shadow.feature.objecttable.navigation.ObjectTableNavKey
import app.orcinus.shadow.feature.objecttable.navigation.objectTableEntry
import app.orcinus.shadow.feature.preferences.navigation.PreferencesNavKey
import app.orcinus.shadow.feature.preferences.navigation.preferencesEntry
import app.orcinus.shadow.feature.prepare.R as PrepareR
import app.orcinus.shadow.feature.prepare.navigation.PrepareNavKey
import app.orcinus.shadow.feature.prepare.navigation.prepareEntry
import app.orcinus.shadow.feature.preview.R as PreviewR
import app.orcinus.shadow.feature.preview.navigation.PreviewNavKey
import app.orcinus.shadow.feature.preview.navigation.previewEntry
import app.orcinus.shadow.feature.project.navigation.ProjectNavKey
import app.orcinus.shadow.feature.project.navigation.projectEntry
import app.orcinus.shadow.feature.settings.navigation.PresetSettingsNavKey
import app.orcinus.shadow.feature.settings.navigation.presetSettingsEntry
import app.orcinus.shadow.feature.setup.SetupStart
import app.orcinus.shadow.feature.setup.navigation.SetupNavKey
import app.orcinus.shadow.feature.setup.navigation.setupEntry
import app.orcinus.shadow.feature.sidebar.PlateSidebar
import app.orcinus.shadow.feature.sidebar.PresetWizardPage
import app.orcinus.shadow.feature.sidebar.R as SidebarR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** App-wide state behind the tab bar: engine start, the slice action, and results that open Preview. */
class AppShellViewModel(
    observePlate: ObservePlateUseCase,
    startEngine: StartEngineUseCase,
    private val sliceAction: SliceActionUseCase,
    private val setSliceMode: SetSliceModeUseCase,
    private val answerPlateQuestion: AnswerPlateQuestionUseCase,
    private val dismissPlateNotice: DismissPlateNoticeUseCase,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val projectLifecycle: ProjectLifecycleUseCase,
    private val presetNames: suspend (PresetKind, String) -> PresetNameOutcome,
    private val stepMeshPrompt: StepMeshPrompt,
    private val autoSlice: AutoSliceUseCase,
    private val projectBackup: ProjectBackupUseCase,
    /** The Preferences' "Default page" once it is read; null before. */
    val defaultPage: Flow<String?>,
    private val openFiles: OpenFilesUseCase,
    private val reloadFromDisk: ReloadFromDiskUseCase,
    /** ObjColorDialog's panel and answer. */
    val objColorPrompt: ObjColorPrompt,
    private val profileUpdates: ProfileUpdatesUseCase,
) : ViewModel() {
    val plate: StateFlow<PlateState> = observePlate()

    init {
        viewModelScope.launch { startEngine() }
    }

    /** The slice button: the plate or all plates, as its drop-down chose. */
    fun slice() = sliceAction()

    fun chooseSliceMode(mode: SliceMode) = setSliceMode(mode)

    /** OrcaSlicer's message boxes while it changes the plate: a load or the object menu. */
    fun answer(yes: Boolean, checked: Boolean) = answerPlateQuestion(yes, checked)

    /** The question's third button. */
    fun cancelQuestion() = answerPlateQuestion.cancel()

    /** OK of the message box, with its "Don't show again" when it has one. */
    fun dismissNotice(checked: Boolean) = dismissPlateNotice(checked)

    /** ProjectDropDialog's choice for the 3MF file that waits; null cancels. */
    fun openProjectAs(load: ModelLoad?) = addModelToPlate.openAs(load)

    /** The Cancel of the load's progress dialog. */
    fun cancelLoad() = addModelToPlate.cancelLoad()

    /** FileArchiveDialog over an archive whose files wait to be picked; null while none does. */
    val archivePreview: StateFlow<ArchivePreview?> = openFiles.preview

    /** The files another app handed over: Plater::load_files() of them. */
    fun openFiles(documents: List<ExternalDocumentReference>) = openFiles.open(documents)

    /** FileArchiveDialog's Open with the files picked, or Cancel with null. */
    fun answerArchive(picked: List<ArchiveEntry>?) = openFiles.answer(picked)

    /** The questions of New Project and Open Project (Plater::close_with_confirm and the presets' check). */
    fun answerSaveChanges(save: Boolean?, remember: Boolean) = projectLifecycle.answerSaveChanges(save, remember)

    /** MainFrame::preview_only_hint()'s Yes: Plater::new_project(). */
    fun newProject() = projectLifecycle.newProject()

    fun saveProjectTo(document: ExternalDocumentReference?) = projectLifecycle.saveTo(document)

    fun saveAsName(untitled: String): String = projectLifecycle.saveAsName(untitled)

    fun answerPresetChanges(answer: PresetChangesAnswer?, remember: Boolean) = projectLifecycle.answerPresetChanges(answer, remember)

    /** "Previous unsaved project detected, do you want to restore it?" */
    fun answerRestore(yes: Boolean) = projectBackup.answerRestore(yes)

    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = presetNames(kind, name)

    /** StepMeshDialog's answer, null for Cancel, and its count of triangles. */
    fun answerStepMesh(choice: StepMeshChoice?) = stepMeshPrompt.answer(choice)

    suspend fun stepTriangleCount(linear: Double, angle: Double): Long = stepMeshPrompt.triangleCount(linear, angle)

    /** "Reload from disk": the file the user picked for one the app can no longer read; null for Cancel. */
    fun reloadPick(document: ExternalDocumentReference?) = reloadFromDisk.pick(document)

    /** "Do you want to replace it ?" of "Reload from disk". */
    fun reloadReplace(yes: Boolean) = reloadFromDisk.replace(yes)

    /** MsgUpdateConfig's OK, which installs the configuration packages, or Cancel. */
    fun answerProfileUpdates(install: Boolean) = profileUpdates.answer(install)

    /** Whether the workspace shows Preview, which "Auto slice after changes" asks. */
    fun showingPreview(shown: Boolean) = autoSlice.setPreviewShown(shown)
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
    /** The files another app handed over that wait to load. */
    openedDocuments: StateFlow<List<ExternalDocumentReference>> = MutableStateFlow(emptyList()),
    onDocumentsTaken: () -> Unit = {},
) {
    val shell = viewModel {
        AppShellViewModel(
            container.observePlate,
            container.startEngine,
            container.sliceAction,
            container.setSliceMode,
            container.answerPlateQuestion,
            container.dismissPlateNotice,
            container.addModelToPlate,
            container.projectLifecycle,
            container::checkPresetName,
            container.stepMeshPrompt,
            container.autoSlice,
            container.projectBackup,
            container.defaultPage,
            container.openFiles,
            container.reloadFromDisk,
            container.objColorPrompt,
            container.profileUpdates,
        )
    }
    val backStack = rememberNavBackStack(WorkspaceNavKey)
    val plate by shell.plate.collectAsStateWithLifecycle()
    // The Setup Wizard's "Create" (user_guide_create_printer): CreatePrinterPresetDialog
    // of the sidebar opens once the wizard has closed.
    var createPrinterPending by rememberSaveable { mutableStateOf(false) }
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
                    openedDocuments = openedDocuments,
                    onDocumentsTaken = onDocumentsTaken,
                    onOpenWizard = { page ->
                        val start = when (page) {
                            PresetWizardPage.PRINTERS -> SetupStart.PRINTERS
                            PresetWizardPage.FILAMENTS -> SetupStart.FILAMENTS
                            PresetWizardPage.GUIDE -> SetupStart.GUIDE
                        }
                        backStack.add(SetupNavKey(start))
                    },
                    onOpenSettings = { kind -> backStack.add(PresetSettingsNavKey(kind)) },
                    // The search of the process panel opens the page of the
                    // setting it found (Tab::activate_option).
                    onOpenSetting = { option ->
                        backStack.add(PresetSettingsNavKey(option.kind, option.id, option.page.firstOrNull()?.msgid))
                    },
                    onOpenAbout = { backStack.add(AboutNavKey) },
                    onOpenPreferences = { backStack.add(PreferencesNavKey) },
                    onOpenTroubleshoot = { backStack.add(TroubleshootNavKey) },
                    onOpenObjectTable = { item -> backStack.add(ObjectTableNavKey.of(item)) },
                    onOpenNetworkTest = { backStack.add(NetworkTestNavKey) },
                    createPrinterPending = createPrinterPending,
                    onCreatePrinterTaken = { createPrinterPending = false },
                )
            }
            setupEntry(
                createViewModel = container::setupWizardViewModel,
                onClose = { backStack.removeAll { it is SetupNavKey } },
                onCreatePrinter = { createPrinterPending = true },
                covered = { backStack.lastOrNull() !is SetupNavKey },
                // Plater::priv::on_modify_filament(): the filament tab opens over
                // the wizard on the preset, in just-edit mode.
                onEditFilamentPreset = { filament, preset ->
                    backStack.add(PresetSettingsNavKey(PresetKind.FILAMENT, editingFilament = filament, editedPreset = preset))
                },
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
            preferencesEntry(
                createViewModel = container::preferencesViewModel,
                onBack = { backStack.removeLastOrNull() },
                onOpenNetworkTest = { backStack.add(NetworkTestNavKey) },
            )
            objectTableEntry(
                createViewModel = container::objectTableViewModel,
                onBack = { backStack.removeLastOrNull() },
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
 * top-level pages; Home, Preview and Device sit on top of Prepare in their
 * back stack, so Back returns to Prepare. Features do not know each other; the
 * workspace opens Preview when a slice finishes, and Prepare when a project is
 * created or opened, as OrcaSlicer does.
 */
@Composable
private fun Workspace(
    container: AppContainer,
    shell: AppShellViewModel,
    onSliceRequested: () -> Unit,
    openedDocuments: StateFlow<List<ExternalDocumentReference>>,
    onDocumentsTaken: () -> Unit,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenSettings: (PresetKind) -> Unit,
    onOpenSetting: (SearchOption) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenTroubleshoot: () -> Unit,
    /** Plater::PopupObjectTable(): the Parameter Table, on the row of the item or on none. */
    onOpenObjectTable: (SettingsItem?) -> Unit,
    /** Help's "Open Network Test". */
    onOpenNetworkTest: () -> Unit,
    /** The Setup Wizard's "Create" waits for the sidebar's CreatePrinterPresetDialog. */
    createPrinterPending: Boolean,
    onCreatePrinterTaken: () -> Unit,
) {
    val plate by shell.plate.collectAsStateWithLifecycle()
    // GUI_App::on_init_inner() selects the Home tab first (MainFrame::tpHome).
    val backStack = rememberNavBackStack(PrepareNavKey, HomeNavKey)
    // The progress dialog of a load, the message boxes OrcaSlicer showed while
    // it changed the plate, in their order, then the question it waits on,
    // over whichever tab is open.
    val loadProgress = plate.loadProgress
    // The notices the 3D view shows as notifications are no message boxes.
    val notice = plate.plateNotices.firstOrNull { it.notificationLevel == null }
    val question = plate.plateQuestion?.question
    val projectDrop = plate.projectDrop
    val projectPrompt = plate.projectPrompt
    val reloadPrompt = plate.reloadPrompt
    val stepMesh = plate.stepMesh
    val objColor = plate.objColor
    val profileUpdates = plate.profileUpdates?.takeIf { it.confirming }
    val archivePreview = shell.archivePreview.collectAsStateWithLifecycle().value
    // "Reload from disk"'s file dialog for a file the app can no longer read.
    var reloadPicking by remember { mutableStateOf(false) }
    val reloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        reloadPicking = false
        shell.reloadPick(uri?.let { ExternalDocumentReference(it.toString()) })
    }
    when {
        loadProgress != null -> LoadProgressDialog(loadProgress, onCancel = shell::cancelLoad)
        notice != null -> SettingsNoticeDialog(notice, onDismissChecked = shell::dismissNotice)
        question != null -> SettingsQuestionDialog(question, onAnswerChecked = shell::answer, onCancel = shell::cancelQuestion)
        stepMesh != null -> StepMeshDialog(stepMesh, countTriangles = shell::stepTriangleCount, onAnswer = shell::answerStepMesh)
        objColor != null -> ObjColorDialog(
            objColor,
            ObjColorActions(
                setClusterNumber = shell.objColorPrompt::setClusterNumber,
                setView = shell.objColorPrompt::setView,
                select = shell.objColorPrompt::select,
                append = shell.objColorPrompt::append,
                colorMatch = shell.objColorPrompt::colorMatch,
                reset = shell.objColorPrompt::reset,
                answer = shell.objColorPrompt::answer,
            ),
        )
        projectPrompt is ProjectPrompt.SaveChanges -> ProjectSaveChangesDialog(onAnswer = shell::answerSaveChanges)
        projectPrompt is ProjectPrompt.RestoreBackup -> ProjectRestoreDialog(onAnswer = shell::answerRestore)
        projectPrompt is ProjectPrompt.PresetChanges ->
            ProjectPresetChangesDialog(projectPrompt, checkName = shell::checkPresetName, onAnswer = shell::answerPresetChanges)
        profileUpdates != null -> ProfileUpdateDialog(profileUpdates.updates, onAnswer = shell::answerProfileUpdates)
        projectDrop != null -> ProjectDropSheet(projectDrop.value.substringAfterLast('/'), onChoose = shell::openProjectAs)
        archivePreview != null -> ArchivePreviewSheet(archivePreview, onAnswer = shell::answerArchive)
        // wxFileDialog's "Please select a file:", named after the file it looks for.
        reloadPrompt is ReloadPrompt.PickFile && !reloadPicking -> SettingsQuestionDialog(
            SettingsDialog(
                id = "reload_select_file",
                icon = DialogIcon.QUESTION,
                title = listOf(OrcaText("Please select a file")),
                text = listOf(OrcaText("%s", listOf(reloadPrompt.name))),
                question = true,
                yes = OrcaText("OK"),
                no = OrcaText("Cancel"),
            ),
            onAnswer = { yes ->
                if (yes) {
                    reloadPicking = true
                    reloadPicker.launch(arrayOf("*/*"))
                } else {
                    shell.reloadPick(null)
                }
            },
        )
        reloadPrompt is ReloadPrompt.Replace -> SettingsQuestionDialog(
            SettingsDialog(
                id = "reload_replace",
                icon = DialogIcon.QUESTION,
                title = listOf(OrcaText("Message")),
                text = listOf(OrcaText("Do you want to replace it"), OrcaText("%s", listOf(" ?"))),
                question = true,
                yes = null,
                no = null,
            ),
            onAnswer = shell::reloadReplace,
        )
    }
    // Save Project's file dialog, when the project to be saved has no document yet.
    val saveAsPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROJECT_MIME_TYPE)) { uri ->
        shell.saveProjectTo(uri?.let { ExternalDocumentReference(it.toString()) })
    }
    val untitled = orcaString("Untitled")
    LaunchedEffect(projectPrompt) {
        if (projectPrompt == ProjectPrompt.SaveAs) saveAsPicker.launch(shell.saveAsName(untitled))
    }
    val layout = currentOrcaWindowLayout()
    var sidebarVisible by rememberSaveable(layout) { mutableStateOf(layout == OrcaWindowLayout.Wide) }

    // A slice that finishes shows its G-code; another plate becoming current,
    // with the G-code it was sliced into before, leaves the tab as it is.
    var runningSlice by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(plate.slicing?.jobId, plate.result?.jobId) {
        plate.slicing?.jobId?.value?.let { runningSlice = it }
        val jobId = plate.result?.jobId?.value
        if (jobId != null && jobId == runningSlice) {
            runningSlice = null
            backStack.showTab(PreviewNavKey)
        }
    }

    // "Slice all" moves on to the next plate as soon as one is sliced, so its
    // G-code shows once it is done with every plate.
    var slicingAll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(plate.slicingAll) {
        if (plate.slicingAll) {
            slicingAll = true
        } else if (slicingAll) {
            slicingAll = false
            backStack.showTab(PreviewNavKey)
        }
    }

    // MainFrame's page change to the Prepare tab (tp3DEditor) posts
    // EVT_GLVIEWTOOLBAR_3D: the tab shows the 3D view again, not the assembly view.
    var prepareShown by rememberSaveable { mutableIntStateOf(0) }
    fun showTab(destination: NavKey) {
        if (destination == PrepareNavKey && backStack.lastOrNull() != PrepareNavKey) prepareShown++
        backStack.showTab(destination)
    }

    // MainFrame's Slice button: the slice starts and the Preview tab shows
    // its progress (m_tabpanel->SetSelection(tpPreview)).
    val sliceRequested = {
        onSliceRequested()
        showTab(PreviewNavKey)
    }

    // ...and then Prepare, when the Preferences' "Default page" says so.
    var startPageChosen by rememberSaveable { mutableStateOf(false) }
    val defaultPage by shell.defaultPage.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(defaultPage) {
        val page = defaultPage ?: return@LaunchedEffect
        if (startPageChosen) return@LaunchedEffect
        startPageChosen = true
        if (page == DEFAULT_PAGE_PREPARE && backStack.lastOrNull() == HomeNavKey) showTab(PrepareNavKey)
    }

    // Plater::new_project() and load_project() select the Prepare tab: the
    // plate was reset (Plater::priv::reset()) for a new or an opened project.
    // load_gcode() and an exported file (m_exported_file) select the Preview.
    var projectResets by remember { mutableIntStateOf(plate.projectResets) }
    LaunchedEffect(plate.projectResets) {
        if (plate.projectResets != projectResets) {
            projectResets = plate.projectResets
            showTab(if (plate.previewOnly != null) PreviewNavKey else PrepareNavKey)
        }
    }

    // MainFrame::preview_only_hint(): the Prepare tab of a G-code file or an
    // exported file asks first whether they close for a new project.
    var previewOnlyHint by rememberSaveable { mutableStateOf(false) }
    val previewOnly = plate.previewOnly
    if (previewOnlyHint && previewOnly != null) {
        SettingsQuestionDialog(
            SettingsDialog(
                id = "preview_only_hint",
                icon = DialogIcon.WARNING,
                title = listOf(OrcaText("Warning")),
                text = listOf(OrcaText("%s", listOf(previewOnly.fileName + " ")), OrcaText("will be closed before creating a new model. Do you want to continue?")),
                question = true,
                yes = OrcaText("Yes"),
                no = OrcaText("No"),
            ),
            onAnswer = { yes ->
                previewOnlyHint = false
                if (yes) shell.newProject()
            },
        )
    }

    // The files another app handed over load on the Prepare tab once a printer
    // is set up, as the desktop app loads them once it has started.
    val documents by openedDocuments.collectAsStateWithLifecycle()
    LaunchedEffect(documents) {
        if (documents.isEmpty()) return@LaunchedEffect
        shell.plate.first { it.profiles != null }
        val taken = documents
        onDocumentsTaken()
        showTab(PrepareNavKey)
        shell.openFiles(taken)
    }

    // Plater::priv::is_preview_shown()
    val shownTab = backStack.lastOrNull()
    LaunchedEffect(shownTab) { shell.showingPreview(shownTab == PreviewNavKey) }
    // The Prepare page shows its assembly view in the 3D view's place (Plater::is_view3D_shown()).
    var assemblyShown by rememberSaveable { mutableStateOf(false) }
    // CreatePrinterPresetDialog of the Setup Wizard's "Create" opens from the docked sidebar.
    LaunchedEffect(createPrinterPending) {
        if (createPrinterPending && layout == OrcaWindowLayout.Wide) sidebarVisible = true
    }
    // PlaterDropTarget::OnDropFiles(): documents another app drags onto the window (split
    // screen, a desktop mode) load as load_files() loads them, on the Prepare tab
    // (select_tab(tp3DEditor)); the 3D view takes a drop on itself where it lands.
    val activity = LocalActivity.current
    val fileDrop = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                val clip = dragEvent.clipData ?: return false
                val dropped = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.map { ExternalDocumentReference(it.toString()) }
                if (dropped.isEmpty()) return false
                // The documents of another app are readable while the activity holds the drop's permissions.
                activity?.requestDragAndDropPermissions(dragEvent)
                showTab(PrepareNavKey)
                shell.openFiles(dropped)
                return true
            }
        }
    }

    val destinations = listOf(HomeNavKey, PrepareNavKey, PreviewNavKey, DeviceNavKey, ProjectNavKey)
    val tabs = listOf(
        // MainFrame's Home tab has its icon alone.
        OrcaTab("", DesignR.drawable.orca_tab_home_active, description = orcaString("Home")),
        OrcaTab(stringResource(PrepareR.string.prepare_title), DesignR.drawable.orca_tab_3d_active),
        OrcaTab(stringResource(PreviewR.string.preview_title), DesignR.drawable.orca_tab_preview_active),
        // MainFrame::show_device(): the Device tab, after the preview.
        OrcaTab(orcaString("Device"), DesignR.drawable.orca_tab_monitor_active),
        // MainFrame's Project tab (ProjectPanel), after the device.
        OrcaTab(orcaString("Project"), DesignR.drawable.orca_tab_auxiliary_active),
    )

    OrcaSidebarLayout(
        modifier = Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = fileDrop),
        layout = layout,
        sidebarVisible = sidebarVisible,
        onSidebarVisibleChange = { sidebarVisible = it },
        toggleDescription = stringResource(if (sidebarVisible) SidebarR.string.collapse_sidebar else SidebarR.string.expand_sidebar),
        topBar = {
            OrcaTabBar(
                tabs = tabs,
                selectedIndex = destinations.indexOf(backStack.lastOrNull()).coerceAtLeast(0),
                onSelect = { index ->
                    val destination = destinations[index]
                    if (destination == PrepareNavKey && plate.previewOnly != null && backStack.lastOrNull() != PrepareNavKey) {
                        previewOnlyHint = true
                    } else {
                        showTab(destination)
                    }
                },
                fillWidth = layout == OrcaWindowLayout.Compact,
            ) {
                SliceButton(
                    mode = plate.sliceMode,
                    enabled = plate.sliceEnabled,
                    onSlice = {
                        sliceRequested()
                        shell.slice()
                    },
                    onModeChange = shell::chooseSliceMode,
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
                onOpenPreferences = {
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenPreferences()
                },
                onOpenTroubleshoot = {
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenTroubleshoot()
                },
                onOpenObjectTable = { item ->
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenObjectTable(item)
                },
                onShowCanvas = {
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                },
                onShowPrepare = {
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    showTab(PrepareNavKey)
                },
                prepareShown = shownTab == PrepareNavKey,
                view3DShown = shownTab == PrepareNavKey && !assemblyShown,
                onOpenNetworkTest = {
                    if (layout == OrcaWindowLayout.Compact) sidebarVisible = false
                    onOpenNetworkTest()
                },
                createPrinterPending = createPrinterPending,
                onCreatePrinterTaken = onCreatePrinterTaken,
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
                homeEntry(createViewModel = container::homeViewModel, onShowPrepare = { showTab(PrepareNavKey) })
                prepareEntry(
                    createViewModel = container::prepareViewModel,
                    shown = prepareShown,
                    onSliceRequested = sliceRequested,
                    onOpenSidebar = { sidebarVisible = true },
                    onOpenSetting = onOpenSetting,
                    onOpenObjectTable = onOpenObjectTable,
                    onAssemblyViewChange = { assemblyShown = it },
                )
                previewEntry(
                    createViewModel = container::previewViewModel,
                    onSliceRequested = onSliceRequested,
                    onOpenDevice = { showTab(DeviceNavKey) },
                    onOpenPrepare = { showTab(PrepareNavKey) },
                    onOpenSetting = onOpenSetting,
                )
                deviceEntry(createViewModel = container::deviceViewModel)
                projectEntry(createViewModel = container::projectViewModel)
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

/** The media type of a 3MF project. */
private const val PROJECT_MIME_TYPE = "model/3mf"

/** default_page: the Prepare tab, the second of DefaultPage's entries. */
private const val DEFAULT_PAGE_PREPARE = "1"
