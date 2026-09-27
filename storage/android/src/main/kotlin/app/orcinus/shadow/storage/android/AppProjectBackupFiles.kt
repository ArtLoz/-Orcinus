package app.orcinus.shadow.storage.android

import android.content.Context
import android.util.Log
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.storage.api.BackupOrigin
import app.orcinus.shadow.storage.api.ProjectBackup
import app.orcinus.shadow.storage.api.ProjectBackupFiles
import java.io.File
import java.io.IOException

/**
 * The backup folder in the app's own storage, which the device's backup does
 * not copy: ".3mf" and "origin.txt" as OrcaSlicer names them, the origin's
 * document and name each on a line of its own. The engine, which runs as the
 * same user, writes the project into the pending file.
 */
class AppProjectBackupFiles(context: Context) : ProjectBackupFiles {
    private val folder = File(context.applicationContext.noBackupFilesDir, "backup")
    private val project = File(folder, ".3mf")
    private val pending = File(folder, ".3mf.part")
    private val origin = File(folder, "origin.txt")

    override fun pendingFile(): ScenePath {
        folder.mkdirs()
        pending.delete()
        return ScenePath(pending.absolutePath)
    }

    override fun commit(origin: BackupOrigin): Boolean = try {
        this.origin.writeText(origin.document?.value.orEmpty() + "\n" + origin.name.orEmpty() + "\n")
        pending.renameTo(project) || throw IOException("Cannot rename $pending")
    } catch (error: IOException) {
        Log.w(TAG, "Cannot keep the project backup", error)
        false
    }

    override fun read(): ProjectBackup? {
        if (!project.isFile) return null
        val lines = try {
            if (origin.isFile) origin.readLines() else emptyList()
        } catch (error: IOException) {
            Log.w(TAG, "Unreadable backup origin", error)
            emptyList()
        }
        return ProjectBackup(
            file = ModelPath(project.absolutePath),
            origin = BackupOrigin(
                document = lines.getOrNull(0)?.takeIf(String::isNotEmpty)?.let(::ExternalDocumentReference),
                name = lines.getOrNull(1)?.takeIf(String::isNotEmpty),
            ),
        )
    }

    override fun removeProject() {
        project.delete()
        pending.delete()
    }

    override fun removeAll() {
        folder.deleteRecursively()
    }

    private companion object {
        const val TAG = "AppProjectBackupFiles"
    }
}
