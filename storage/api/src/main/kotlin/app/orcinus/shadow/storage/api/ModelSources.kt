package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * The documents the model files in app storage were copied from. The engine
 * reads a copy, so a volume's file (ModelVolume::source.input_file) names
 * the copy; "Reload from disk" reads the document it came from again. The
 * app keeps its access to the documents of the latest copies.
 */
interface ModelSources {
    /** The document the copy at [file] was made from; null for a copy the app does not know of. */
    fun documentOf(file: String): ExternalDocumentReference?

    /** The copy at [file] was made from [document]. */
    fun record(file: String, document: ExternalDocumentReference)
}
