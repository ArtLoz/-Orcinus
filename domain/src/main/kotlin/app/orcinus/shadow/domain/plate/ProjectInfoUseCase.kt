package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AuxiliaryFile
import app.orcinus.shadow.core.model.AuxiliaryFolder
import app.orcinus.shadow.core.model.AuxiliaryRename
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ProjectInfo
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.storage.api.ProjectInfoFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ProjectPanel and its editor (AuxiliaryPanel, DesignerPanel): what the
 * project holds besides its objects, read and changed in the directory the
 * engine writes into the next save (PlateProject.info). A project without one
 * gets it with its first change, as the desktop model always has its
 * Model::get_auxiliary_file_temp_path(). A file added and a cover chosen mark
 * the project changed (put_other_changes() and set_plater_dirty()); the
 * texts do not, as on the desktop.
 */
class ProjectInfoUseCase(
    private val files: ProjectInfoFiles,
    private val repository: PlateRepository,
) {
    /** The information of the plate's project; none for a project without its directory. */
    suspend fun read(): ProjectInfo = withContext(Dispatchers.IO) {
        repository.state.value.project.info?.let(files::read) ?: ProjectInfo()
    }

    suspend fun setDesigner(designer: String) = change { files.setDesigner(it, designer) }

    suspend fun setModelName(name: String) = change { files.setModelName(it, name) }

    suspend fun setLicense(license: String) = change { files.setLicense(it, license) }

    suspend fun setDescription(description: String) = change { files.setDescription(it, description) }

    /** AuxiliaryPanel::on_import_file(): the files the user chose join [folder]. */
    suspend fun import(folder: AuxiliaryFolder, documents: List<ExternalDocumentReference>): ProjectInfo {
        var imported = false
        val info = change { directory -> documents.forEach { if (files.import(directory, folder, it)) imported = true } }
        if (imported) markChanged()
        return info
    }

    /** AuFile::on_input_enter(): the file's new name, or why it keeps its old one. */
    suspend fun rename(file: AuxiliaryFile, name: String): AuxiliaryRename = withContext(Dispatchers.IO) { files.rename(file.path, name) }

    suspend fun delete(file: AuxiliaryFile) = change { files.delete(it, file.path) }

    /** AuFile::on_set_cover() */
    suspend fun setCover(file: AuxiliaryFile): ProjectInfo {
        val info = change { files.setCover(it, file.path) }
        markChanged()
        return info
    }

    private suspend fun change(action: (ScenePath) -> Unit): ProjectInfo = withContext(Dispatchers.IO) {
        val directory = directory()
        action(directory)
        files.read(directory)
    }

    private fun directory(): ScenePath {
        repository.state.value.project.info?.let { return it }
        val created = files.newDirectory()
        var directory = created
        repository.update { state ->
            val kept = state.project.info
            if (kept != null) {
                directory = kept
                state
            } else {
                state.copy(project = state.project.copy(info = created))
            }
        }
        return directory
    }

    private fun markChanged() = repository.update { it.withOtherChanges() }
}
