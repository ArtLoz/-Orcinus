package app.orcinus.shadow.storage.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.ConfigFiles
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Configuration documents of the user, which the engine reads and writes as
 * plain files: a picked document is copied into the app, and the files an
 * export wrote are copied into the folder the user picked. A folder that
 * already holds a file of the same name keeps it, and the new one arrives
 * beside it, as the document provider names it.
 */
class AppConfigFiles(context: Context) : ConfigFiles {
    private val applicationContext = context.applicationContext

    private val directory: File
        get() = File(applicationContext.noBackupFilesDir, "configs")

    override suspend fun copyIn(reference: ExternalDocumentReference): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(reference.value)
        val destination = File(directory, documentName(uri).toSafeFileName())
        try {
            directory.mkdirs()
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use(input::copyTo)
            } ?: return@withContext null
        } catch (error: IOException) {
            return@withContext null
        } catch (error: SecurityException) {
            return@withContext null
        }
        destination.absolutePath
    }

    override fun exportDirectory(): String = directory.also { it.mkdirs() }.absolutePath

    override suspend fun copyOut(files: List<String>, folder: ExternalDocumentReference): Int = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder.value)
        val parent = try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (error: IllegalArgumentException) {
            return@withContext 0
        }
        var written = 0
        for (path in files) {
            val source = File(path)
            if (!source.exists()) continue
            try {
                val document = DocumentsContract.createDocument(applicationContext.contentResolver, parent, mimeTypeOf(source.name), source.name)
                    ?: continue
                applicationContext.contentResolver.openOutputStream(document)?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } ?: continue
                written++
            } catch (error: IOException) {
                continue
            } catch (error: SecurityException) {
                continue
            }
        }
        written
    }

    /**
     * The type a file is created with: the one its extension stands for, else a
     * plain stream, for which the provider keeps the name whole rather than
     * appending its type's extension, so that the files ExportConfigsDialog
     * names (".orca_printer", ".orca_filament", ".zip") import again.
     */
    private fun mimeTypeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: DEFAULT_MIME_TYPE

    /** The document's own name, which the preset keeps. */
    private fun documentName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return try {
            applicationContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            } ?: DEFAULT_NAME
        } catch (error: SecurityException) {
            DEFAULT_NAME
        }
    }

    /** Keeps the document's name, without what a file name cannot hold. */
    private fun String.toSafeFileName(): String =
        map { if (it.isLetterOrDigit() || it in ALLOWED_NAME_CHARS) it else '_' }.joinToString("").ifBlank { DEFAULT_NAME }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val DEFAULT_NAME = "config.json"
        val ALLOWED_NAME_CHARS = setOf('.', '-', '_', ' ', '@', '(', ')')
    }
}
