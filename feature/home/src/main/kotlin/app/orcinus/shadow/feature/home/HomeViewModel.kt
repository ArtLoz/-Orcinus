package app.orcinus.shadow.feature.home

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.ProjectLifecycleUseCase
import app.orcinus.shadow.domain.plate.RecentProject
import app.orcinus.shadow.domain.plate.RecentProjectsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file of the home page's list (MainFrame::get_recent_projects()). */
data class RecentFile(
    val document: ExternalDocumentReference,
    val name: String,
    /** When it last changed, in milliseconds since the epoch; null where its provider does not tell. */
    val modified: Long?,
    /** "File is missing": it is gone, or the app may no longer read it. */
    val missing: Boolean,
    /** The picture its 3MF file carries (GetThumbnailUrl()); null for none. */
    val thumbnail: ImageBitmap?,
)

data class HomeUiState(
    val files: List<RecentFile> = emptyList(),
    /** A file loads: OrcaSlicer's progress dialog stands over the page meanwhile. */
    val loading: Boolean = false,
    /** A message box of the page's actions, which OK dismisses. */
    val message: SettingsDialog? = null,
)

/**
 * The home page (WebViewPanel and the commands of homepage/js/home.js that
 * GUI_App::handle_script_message() runs): New Project, Open Project and the
 * recent files, each of which opens, or leaves the list with "Remove" or
 * "Clear all".
 */
class HomeViewModel(
    private val recentProjects: RecentProjectsUseCase,
    private val projectLifecycle: ProjectLifecycleUseCase,
    private val addModelToPlate: AddModelToPlateUseCase,
    observePlate: ObservePlateUseCase,
) : ViewModel() {
    private val plate: StateFlow<PlateState> = observePlate()
    private val files = MutableStateFlow<List<RecentFile>>(emptyList())
    private val message = MutableStateFlow<SettingsDialog?>(null)
    private val refreshes = MutableStateFlow(0)
    private val prepare = Channel<Unit>(Channel.CONFLATED)

    /** Pictures read before, with the time of the change they show. */
    private val thumbnails = HashMap<ExternalDocumentReference, Pair<Long?, ImageBitmap?>>()

    val state: StateFlow<HomeUiState> = combine(files, plate.map { it.importing }.distinctUntilChanged(), message) { files, loading, message ->
        HomeUiState(files, loading, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    /** Prepare shows instead: a project that could not be opened tells why there. */
    val showPrepare: Flow<Unit> = prepare.receiveAsFlow()

    init {
        // MainFrame(): the recent projects OrcaSlicer.conf keeps, once the engine reads them.
        viewModelScope.launch { recentProjects.load() }
        viewModelScope.launch {
            combine(recentProjects.recent, refreshes) { recent, _ -> recent }.collectLatest { recent -> describe(recent) }
        }
    }

    /** SendRecentList(): the files as they are now, which may have changed or gone since. */
    fun refresh() {
        refreshes.value++
    }

    /** "New Project" (homepage_newproject): GUI_App::request_open_project("<new>"). */
    fun newProject() {
        if (refusedWhileSlicing()) return
        projectLifecycle.newProject()
    }

    /** "Open Project" (homepage_openproject) asks for its file unless the plate is slicing. */
    fun mayOpenProject(): Boolean = !refusedWhileSlicing()

    /** Plater::load_project() of the file Open Project's file dialog gave. */
    fun openProject(document: ExternalDocumentReference) {
        addModelToPlate.openProject(document)
        followLoad()
    }

    /**
     * A recent file (homepage_open_recentfile): request_open_project() and
     * MainFrame::open_recent_project(), which opens a file that is there and
     * says "The project is no longer available." of one that is not. Its
     * message box has OK only, while the file would leave the list on Yes,
     * so the file stays on the list.
     */
    fun openRecent(file: RecentFile) {
        if (refusedWhileSlicing()) return
        viewModelScope.launch {
            if (recentProjects.exists(file.document)) {
                addModelToPlate.openRecent(file.document)
                followLoad()
            } else {
                message.value = PROJECT_MISSING
            }
        }
    }

    /** The context menu's "Remove" (homepage_delete_recentfile). */
    fun remove(file: RecentFile) = recentProjects.remove(file.document)

    /** "Clear all" (homepage_delete_all_recentfile). */
    fun clearAll() = recentProjects.remove(null)

    fun dismissMessage() {
        message.value = null
    }

    /** GUI_App::request_open_project(): no project is created or opened while the plate slices. */
    private fun refusedWhileSlicing(): Boolean {
        if (plate.value.slicing == null) return false
        message.value = NOT_WHILE_SLICING
        return true
    }

    /**
     * Plater::load_project() selects the Prepare tab once it loaded, even
     * when the load failed; a project that opened shows Prepare anyway, as
     * the plate is reset.
     */
    private fun followLoad() {
        val started = plate.value
        if (!started.importing) return
        viewModelScope.launch {
            val ended = plate.first { !it.importing }
            if (ended.problem != null && ended.problem != started.problem) prepare.send(Unit)
        }
    }

    /** The files with their names and times at once, then with their pictures one by one. */
    private suspend fun describe(recent: List<ExternalDocumentReference>) {
        val described = recent.map { recentProjects.describe(it) }
        var shown = described.map { it.toFile(thumbnails[it.document]?.takeIf { (modified, _) -> modified == it.modified }?.second) }
        files.value = shown
        described.forEachIndexed { index, project ->
            val cached = thumbnails[project.document]
            if (project.missing || (cached != null && cached.first == project.modified)) return@forEachIndexed
            val picture = recentProjects.thumbnail(project.document)?.let { bytes ->
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            }
            thumbnails[project.document] = project.modified to picture
            shown = shown.toMutableList().also { it[index] = it[index].copy(thumbnail = picture) }
            files.value = shown
        }
    }

    private fun RecentProject.toFile(thumbnail: ImageBitmap?) = RecentFile(document, name, modified, missing, thumbnail.takeUnless { missing })

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** GUI_App::request_open_project()'s show_info(). */
        val NOT_WHILE_SLICING = SettingsDialog(
            id = "open_project_while_slicing",
            icon = DialogIcon.INFO,
            title = listOf(OrcaText("Open Project")),
            text = listOf(OrcaText("new or open project file is not allowed during the slicing process!")),
            question = false,
            yes = null,
            no = null,
        )

        /** MainFrame::open_recent_project()'s message box of a file that is gone. */
        val PROJECT_MISSING = SettingsDialog(
            id = "recent_project_missing",
            icon = DialogIcon.ERROR,
            title = listOf(OrcaText("Error")),
            text = listOf(OrcaText("The project is no longer available.")),
            question = false,
            yes = null,
            no = null,
        )
    }
}
