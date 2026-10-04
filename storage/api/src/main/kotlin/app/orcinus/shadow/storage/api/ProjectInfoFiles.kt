package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.AuxiliaryFolder
import app.orcinus.shadow.core.model.AuxiliaryRename
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ProjectInfo
import app.orcinus.shadow.core.model.ScenePath

/**
 * The directory that holds what a project holds besides its objects, as the
 * engine keeps it for the next save (info.json and the Auxiliaries folder,
 * Model::get_auxiliary_file_temp_path()), and the changes the Project tab's
 * editor makes to it (AuxiliaryPanel and DesignerPanel).
 */
interface ProjectInfoFiles {
    /** A new directory for the information of a project that has none yet. */
    fun newDirectory(): ScenePath

    /** ProjectPanel::on_reload() and Reload(): the information, the default folders made where they are missing. */
    fun read(directory: ScenePath): ProjectInfo

    /** DesignerPanel::on_input_enter_designer(): Model::SetDesigner(), which clears the designer's user id. */
    fun setDesigner(directory: ScenePath, designer: String)

    /** on_input_enter_model(), on_select_license() and on_input_enter_description(), into the model's information (ensure_model_info()). */
    fun setModelName(directory: ScenePath, name: String)

    fun setLicense(directory: ScenePath, license: String)

    fun setDescription(directory: ScenePath, description: String)

    /**
     * AuxiliaryPanel::on_import_file(): [document] copied into [folder], under
     * its own name, or with the time of day added when the folder has a file
     * of that name; false when it could not be copied.
     */
    fun import(directory: ScenePath, folder: AuxiliaryFolder, document: ExternalDocumentReference): Boolean

    /** AuFile::on_input_enter(): the file named [name], its extension kept, unless the name is not allowed. */
    fun rename(file: ScenePath, name: String): AuxiliaryRename

    /** AuFile::on_set_delete(): the file goes, and the cover's pictures with it when it was the cover. */
    fun delete(directory: ScenePath, file: ScenePath)

    /** AuFile::on_set_cover(): the picture is the model's cover, and its pictures for the 3MF file and the printer are made. */
    fun setCover(directory: ScenePath, file: ScenePath)
}
