package app.orcinus.shadow.storage.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.DocumentAccess
import app.orcinus.shadow.storage.api.DocumentDescription
import java.io.InputStream

/**
 * The documents of the system's document picker: the app keeps its access to
 * one by the permission Android persists for it, reading and, where granted,
 * writing; the name and the time of its last change come from its provider.
 */
class AppDocumentAccess(context: Context) : DocumentAccess {
    private val applicationContext = context.applicationContext

    override fun keep(document: ExternalDocumentReference) {
        val uri = Uri.parse(document.value)
        if (uri.scheme != "content") return
        val resolver = applicationContext.contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (writeDenied: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (denied: SecurityException) {
                // A document shared to the app, which the app can read for now only.
            }
        }
    }

    override fun release(document: ExternalDocumentReference) {
        val uri = Uri.parse(document.value)
        val resolver = applicationContext.contentResolver
        val kept = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (kept.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (kept.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try {
            resolver.releasePersistableUriPermission(uri, flags)
        } catch (error: SecurityException) {
            // Released already.
        }
    }

    override fun describe(document: ExternalDocumentReference): DocumentDescription? {
        val uri = Uri.parse(document.value)
        return try {
            applicationContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
                val modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    .takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
                DocumentDescription(name ?: uri.lastPathSegment.orEmpty(), modified)
            }
        } catch (error: RuntimeException) {
            // A provider tells a document that is gone, or that the app may no longer read, by throwing.
            null
        }
    }

    override fun open(document: ExternalDocumentReference): InputStream? = try {
        applicationContext.contentResolver.openInputStream(Uri.parse(document.value))
    } catch (error: Exception) {
        null
    }
}
