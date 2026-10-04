package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/** A folder of the user's documents that the user picked, as the system's document tree. */
interface DocumentFolders {
    /** The document named [name] directly in [folder]; null when the folder has none. */
    suspend fun find(folder: ExternalDocumentReference, name: String): ExternalDocumentReference?

    /** What the folder is called, as the system names it. */
    suspend fun displayName(folder: ExternalDocumentReference): String

    /** A new document named [name] in [folder], of [mimeType]; null when it cannot be made. */
    suspend fun create(folder: ExternalDocumentReference, name: String, mimeType: String): ExternalDocumentReference? = null
}
