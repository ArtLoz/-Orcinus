package app.orcinus.shadow.storage.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.DocumentFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The folders the system's document picker grants, read through DocumentsContract. */
class AppDocumentFolders(context: Context) : DocumentFolders {
    private val applicationContext = context.applicationContext

    override suspend fun find(folder: ExternalDocumentReference, name: String): ExternalDocumentReference? = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder.value)
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            applicationContext.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                val id = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val displayName = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(displayName) == name) {
                        return@withContext ExternalDocumentReference(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(id)).toString())
                    }
                }
            }
            null
        } catch (error: IllegalArgumentException) {
            null
        } catch (error: SecurityException) {
            null
        }
    }

    override suspend fun displayName(folder: ExternalDocumentReference): String = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder.value)
        try {
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            applicationContext.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (error: IllegalArgumentException) {
            null
        } catch (error: SecurityException) {
            null
        } ?: tree.lastPathSegment.orEmpty()
    }
}
