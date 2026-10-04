package app.orcinus.shadow.feature.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.AuxiliaryFile
import app.orcinus.shadow.core.model.AuxiliaryFolder
import app.orcinus.shadow.core.model.AuxiliaryRename
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.ProjectInfo
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.ProjectInfoUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Project tab shows: the project page (the web page of
 * ProjectPanel), or its editor (AuxiliaryPanel) on the tab [editorTab], with
 * the file being renamed and why its name was refused.
 */
data class ProjectUiState(
    val info: ProjectInfo = ProjectInfo(),
    val editing: Boolean = false,
    val editorTab: Int = 0,
    val renaming: AuxiliaryFile? = null,
    val renameError: List<OrcaText>? = null,
)

/**
 * ProjectPanel: the project's information, read again whenever another
 * project is on the plate or the tab is shown (ProjectPanel::Show() calls
 * update_model_data()), and the editor's changes, which the page shows once
 * the editor returns (EVT_AUXILIARY_DONE).
 */
class ProjectViewModel(
    private val projectInfo: ProjectInfoUseCase,
    observePlate: ObservePlateUseCase,
    /** The document the file at the path is offered as, under the name, for another app to open. */
    private val shareable: suspend (path: String, name: String) -> ExternalDocumentReference?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProjectUiState())
    val state: StateFlow<ProjectUiState> = mutableState.asStateFlow()

    private val opened = Channel<ExternalDocumentReference>(Channel.BUFFERED)

    /** The documents to open in another app (wxLaunchDefaultApplication()). */
    val documents: Flow<ExternalDocumentReference> = opened.receiveAsFlow()

    init {
        viewModelScope.launch {
            observePlate().map { it.project.info to it.projectResets }.distinctUntilChanged().collect { reload() }
        }
    }

    /** ProjectPanel::Show(): the page shows the information as it is now, the editor closed. */
    fun shown() {
        mutableState.update { it.copy(editing = false, renaming = null, renameError = null) }
        reload()
    }

    /** "Edit Project Info" (edit_project_info): the editor on "Basic Info" (AuxiliaryPanel::Reload()). */
    fun edit() = mutableState.update { it.copy(editing = true, editorTab = 0) }

    /** The editor's "Return" (EVT_AUXILIARY_DONE). */
    fun done() {
        mutableState.update { it.copy(editing = false, renaming = null, renameError = null) }
        reload()
    }

    fun selectTab(index: Int) = mutableState.update { it.copy(editorTab = index) }

    fun setDesigner(designer: String) = change { projectInfo.setDesigner(designer) }

    fun setModelName(name: String) = change { projectInfo.setModelName(name) }

    fun setLicense(license: String) = change { projectInfo.setLicense(license) }

    fun setDescription(description: String) = change { projectInfo.setDescription(description) }

    fun import(folder: AuxiliaryFolder, documents: List<ExternalDocumentReference>) {
        if (documents.isNotEmpty()) change { projectInfo.import(folder, documents) }
    }

    fun delete(file: AuxiliaryFile) = change { projectInfo.delete(file) }

    fun setCover(file: AuxiliaryFile) = change { projectInfo.setCover(file) }

    /** AuFile::enter_rename_mode() */
    fun startRename(file: AuxiliaryFile) = mutableState.update { it.copy(renaming = file, renameError = null) }

    /** AuFile::exit_rename_mode() */
    fun cancelRename() = mutableState.update { it.copy(renaming = null, renameError = null) }

    /** AuFile::on_input_enter(): a name it refuses keeps the rename open, with why. */
    fun rename(name: String) {
        val file = state.value.renaming ?: return
        viewModelScope.launch {
            when (val renamed = projectInfo.rename(file, name)) {
                AuxiliaryRename.Renamed -> {
                    val info = projectInfo.read()
                    mutableState.update { it.copy(info = info, renaming = null, renameError = null) }
                }
                is AuxiliaryRename.Invalid -> mutableState.update { it.copy(renameError = renamed.message) }
            }
        }
    }

    /** open_3mf_accessory and AuFile::on_dclick(): the file in the app the system opens it with. */
    fun open(file: AuxiliaryFile) {
        viewModelScope.launch { shareable(file.path.value, file.name)?.let { opened.send(it) } }
    }

    private fun change(action: suspend () -> ProjectInfo) {
        viewModelScope.launch {
            val info = action()
            mutableState.update { it.copy(info = info) }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val info = projectInfo.read()
            mutableState.update { it.copy(info = info) }
        }
    }
}
