package app.orcinus.shadow.core.model

/**
 * The folders of a project's auxiliary files (s_default_folders of
 * Auxiliary.cpp and ProjectPanel::Reload()), as the 3MF file keeps them under
 * Auxiliaries.
 */
enum class AuxiliaryFolder(val directory: String) {
    MODEL_PICTURES("Model Pictures"),
    BILL_OF_MATERIALS("Bill of Materials"),
    ASSEMBLY_GUIDE("Assembly Guide"),
    OTHERS("Others"),
    PROFILE_PICTURES("Profile Pictures"),
}

/** A file of a folder: where it is, its name, and its size in bytes. */
data class AuxiliaryFile(val path: ScenePath, val name: String, val size: Long)

/**
 * What a project holds besides its objects, as ProjectPanel::on_reload()
 * reads it from the model: the designer (ModelDesignInfo), the model's
 * information (ModelInfo: its name, licence, description, cover and upload
 * type), the profile's (ModelProfileInfo), and the auxiliary files by folder.
 * [modelInfo] says whether the model has its information at all
 * (Model::model_info), which the editor's fields read as empty without it.
 */
data class ProjectInfo(
    val designer: String = "",
    val modelInfo: Boolean = false,
    val modelName: String = "",
    val license: String = "",
    val description: String = "",
    val coverFile: String = "",
    /** ModelInfo::origin, the upload type: "remix", "shared", or the model's own. */
    val origin: String = "",
    /** The author the copyright (ModelInfo::copyright, a JSON list) names last. */
    val copyrightAuthor: String = "",
    val profileName: String = "",
    val profileAuthor: String = "",
    val profileDescription: String = "",
    val profileCover: String = "",
    val files: Map<AuxiliaryFolder, List<AuxiliaryFile>> = emptyMap(),
) {
    /** on_reload(): the model's author is the copyright's, or else the designer. */
    val author: String get() = copyrightAuthor.ifEmpty { designer }

    fun filesIn(folder: AuxiliaryFolder): List<AuxiliaryFile> = files[folder].orEmpty()

    /** on_reload()'s has_content: anything to show, or "No model information". */
    val hasContent: Boolean
        get() = listOf(origin, license, modelName, author, coverFile, description, profileName, profileAuthor, profileDescription, profileCover)
            .any(String::isNotEmpty) || files.values.any(List<AuxiliaryFile>::isNotEmpty)

    companion object {
        /** license_list of Auxiliary.cpp: the licences DesignerPanel offers, none first. */
        val LICENSES = listOf("", "CC0", "BY", "BY-SA", "BY-ND", "BY-NC", "BY-NC-SA", "BY-NC-ND")
    }
}

/** Why AuFile::on_input_enter() keeps a file's name: Orca's message, or none for a renamed file. */
sealed interface AuxiliaryRename {
    data object Renamed : AuxiliaryRename

    data class Invalid(val message: List<OrcaText>) : AuxiliaryRename
}
