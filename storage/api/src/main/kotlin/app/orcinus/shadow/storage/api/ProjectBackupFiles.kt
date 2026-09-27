package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ScenePath

/**
 * The folder of OrcaSlicer's project backup (Model::get_backup_path()): the
 * project written as ".3mf" while it has unsaved changes, with where it came
 * from ("origin.txt"), so a project the system or a crash ended with the app
 * can be restored when the app starts again.
 */
interface ProjectBackupFiles {
    /** Where the project is written before [commit] makes it the backup, so a write cut short leaves the backup before. */
    fun pendingFile(): ScenePath

    /** The written project becomes the backup, with the project's [origin]; false when it could not. */
    fun commit(origin: BackupOrigin): Boolean

    /** has_restore_data(): the backup an earlier run left, or null for none. */
    fun read(): ProjectBackup?

    /** remove_backup(model, false): the project goes, the folder stays. */
    fun removeProject()

    /** remove_backup(model, true): the whole folder goes. */
    fun removeAll()
}

/**
 * origin.txt: the document the project was opened from or saved to and the
 * name it goes by; none for an untitled project.
 */
data class BackupOrigin(val document: ExternalDocumentReference?, val name: String?)

/** A backup of a project: its file and its origin. */
data class ProjectBackup(val file: ModelPath, val origin: BackupOrigin)
