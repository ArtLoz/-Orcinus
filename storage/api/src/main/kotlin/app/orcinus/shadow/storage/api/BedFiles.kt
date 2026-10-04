package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * The files BedShapeDialog loads: a bed's shape, texture or model, which the
 * printer preset then names by path (bed_custom_texture, bed_custom_model).
 * The app keeps a copy of each document under its own name, which the preset
 * can name for good.
 */
interface BedFiles {
    /** The path of the copy of [document]; null when it could not be read. */
    suspend fun keep(document: ExternalDocumentReference): String?

    /** Whether the file at [path] is still there (the dialog draws a missing one red). */
    fun exists(path: String): Boolean
}
