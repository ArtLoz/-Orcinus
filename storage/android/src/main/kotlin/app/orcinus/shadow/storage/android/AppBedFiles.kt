package app.orcinus.shadow.storage.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.BedFiles
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies in files/bed, each in a folder of its own, so that copies of the same name stay apart. */
class AppBedFiles(context: Context) : BedFiles {
    private val applicationContext = context.applicationContext

    override suspend fun keep(document: ExternalDocumentReference): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(document.value)
        val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: return@withContext null
        val folder = File(File(applicationContext.filesDir, "bed"), UUID.randomUUID().toString())
        if (!folder.mkdirs()) return@withContext null
        val file = File(folder, name.replace('/', '_'))
        val copied = runCatching {
            applicationContext.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("unreadable")
        }.isSuccess
        if (!copied) folder.deleteRecursively()
        file.absolutePath.takeIf { copied }
    }

    override fun exists(path: String): Boolean = File(path).exists()

    private fun displayName(uri: Uri): String? =
        applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
}
