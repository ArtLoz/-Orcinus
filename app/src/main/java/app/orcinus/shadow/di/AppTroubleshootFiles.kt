package app.orcinus.shadow.di

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.domain.about.TroubleshootFiles
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Troubleshoot Center's files: the documents it writes, and the log of
 * the session. The desktop app keeps its log in files; here OrcaSlicer logs to
 * logcat, of which an app reads only its own lines, those of the :slicer
 * process included.
 */
internal class AppTroubleshootFiles(context: Context) : TroubleshootFiles {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun writeText(text: String, document: ExternalDocumentReference): Boolean = withContext(Dispatchers.IO) {
        write(document) { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun pack(document: ExternalDocumentReference, project: ExternalDocumentReference?): Boolean = withContext(Dispatchers.IO) {
        write(document) { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(LOG_NAME))
                copyLog(zip)
                zip.closeEntry()
                if (project != null) {
                    val projectUri = Uri.parse(project.value)
                    zip.putNextEntry(ZipEntry(nameOf(projectUri)))
                    resolver.openInputStream(projectUri)?.use { it.copyTo(zip) } ?: throw FileNotFoundException(project.value)
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Writes [document]; SaveAsZip() and ExportAsJson() remove what a failed
     * write left, and so does this.
     */
    private fun write(document: ExternalDocumentReference, content: (OutputStream) -> Unit): Boolean {
        val uri = Uri.parse(document.value)
        val written = try {
            resolver.openOutputStream(uri, "wt")?.use { output ->
                content(output)
                true
            } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (!written) {
            try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (_: Exception) {
            }
        }
        return written
    }

    /** What logcat keeps of the app, as `logcat -d` prints it. */
    private fun copyLog(output: OutputStream) {
        val process = ProcessBuilder("logcat", "-d").redirectErrorStream(true).start()
        try {
            process.inputStream.use { it.copyTo(output) }
            process.waitFor(LOGCAT_SECONDS, TimeUnit.SECONDS)
        } finally {
            process.destroy()
        }
    }

    private fun nameOf(uri: Uri): String = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }?.takeIf { it.isNotBlank() } ?: PROJECT_NAME

    private companion object {
        const val LOG_NAME = "logcat.txt"
        const val PROJECT_NAME = "project.3mf"
        const val LOGCAT_SECONDS = 10L
    }
}
