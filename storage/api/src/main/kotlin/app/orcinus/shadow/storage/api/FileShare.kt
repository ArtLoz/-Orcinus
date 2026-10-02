package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * Android's share sheet: a file of the app is handed to the app the user
 * picks, as a document under [name] it may read. OrcaSlicer has no such
 * command; on a phone it is how a file reaches a messenger, a cloud drive or
 * another slicer, besides being saved into a document.
 */
fun interface FileShare {
    /** The document the file at [path] is offered as; null when it could not be prepared. */
    suspend fun shareable(path: String, name: String): ExternalDocumentReference?
}
