package app.orcinus.shadow.storage.android

import android.content.Context
import android.provider.OpenableColumns
import android.net.Uri
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.DocumentExport
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies a file of the app into the document the user picked. */
class AppDocumentExport(context: Context) : DocumentExport {
    private val applicationContext = context.applicationContext

    override suspend fun copyTo(path: String, document: ExternalDocumentReference): Boolean = withContext(Dispatchers.IO) {
        val source = File(path)
        if (!source.isFile) return@withContext false
        try {
            applicationContext.contentResolver.openOutputStream(Uri.parse(document.value), "wt")?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: return@withContext false
        } catch (error: IOException) {
            return@withContext false
        } catch (error: SecurityException) {
            return@withContext false
        }
        true
    }

    override suspend fun copyNumberedTo(path: String, document: ExternalDocumentReference): Boolean = withContext(Dispatchers.IO) {
        val source = File(path)
        if (!source.isFile) return@withContext false
        try {
            applicationContext.contentResolver.openOutputStream(Uri.parse(document.value), "wt")?.buffered()?.use { output ->
                source.inputStream().buffered().use { input ->
                    // std::getline() lines: split at '\n', each written back with one, the last too.
                    var number = 1L
                    var lineStart = true
                    while (true) {
                        val byte = input.read()
                        if (byte < 0) break
                        if (lineStart) {
                            output.write("N${number++} ".toByteArray())
                            lineStart = false
                        }
                        output.write(byte)
                        if (byte == '\n'.code) lineStart = true
                    }
                    if (!lineStart) output.write('\n'.code)
                }
            } ?: return@withContext false
        } catch (error: IOException) {
            return@withContext false
        } catch (error: SecurityException) {
            return@withContext false
        }
        true
    }

    override suspend fun displayName(document: ExternalDocumentReference): String? = withContext(Dispatchers.IO) {
        try {
            applicationContext.contentResolver.query(Uri.parse(document.value), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (error: SecurityException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }
}
