package app.orcinus.shadow.storage.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.ExternalDocumentReference
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A copy of [document] under its own name in files/[folder], each in a folder
 * of its own, so that copies of the same name stay apart; null when it could
 * not be read.
 */
internal suspend fun Context.keepDocumentCopy(document: ExternalDocumentReference, folder: String): String? = withContext(Dispatchers.IO) {
    val uri = Uri.parse(document.value)
    val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: return@withContext null
    val target = File(File(filesDir, folder), UUID.randomUUID().toString())
    if (!target.mkdirs()) return@withContext null
    val file = File(target, name.replace('/', '_'))
    val copied = runCatching {
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("unreadable")
    }.isSuccess
    if (!copied) target.deleteRecursively()
    file.absolutePath.takeIf { copied }
}

private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
    }
