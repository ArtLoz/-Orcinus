package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * Writes a file of the app into a document the user picked, which is how the
 * G-code leaves the app (Plater::export_gcode of the desktop app).
 */
interface DocumentExport {
    /** False when the document could not be written. */
    suspend fun copyTo(path: String, document: ExternalDocumentReference): Boolean

    /**
     * gcode_add_line_number(): the file with "N<number> " before each of its
     * lines, from 1; false when the document could not be written.
     */
    suspend fun copyNumberedTo(path: String, document: ExternalDocumentReference): Boolean

    /** The name the document goes by, which the user may have chosen when it was created; null when unknown. */
    suspend fun displayName(document: ExternalDocumentReference): String? = null
}
