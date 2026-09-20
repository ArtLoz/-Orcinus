package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * Writes a file of the app into a document the user picked, which is how the
 * G-code leaves the app (Plater::export_gcode of the desktop app).
 */
interface DocumentExport {
    /** False when the document could not be written. */
    suspend fun copyTo(path: String, document: ExternalDocumentReference): Boolean
}
