package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * The documents the Import Configs and Export Preset Bundle of OrcaSlicer's
 * File menu work with: the engine reads and writes plain files, so a document
 * the user picked is copied in, and the exported files are copied out.
 */
interface ConfigFiles {
    /** A copy of the picked document the engine can read; null when it cannot be read. */
    suspend fun copyIn(reference: ExternalDocumentReference): String?

    /** A directory of the app the engine writes the exported presets into. */
    fun exportDirectory(): String

    /** Copies [files] into the folder the user picked; returns how many arrived. */
    suspend fun copyOut(files: List<String>, folder: ExternalDocumentReference): Int
}
