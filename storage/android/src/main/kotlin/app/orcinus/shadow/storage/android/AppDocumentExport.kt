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
