package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ArchiveEntry
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OrcaText

/** What unzipping files of an archive gave: the files in app storage, or OrcaSlicer's message why not. */
sealed interface ArchiveExtraction {
    data class Success(val files: List<ModelPath>) : ArchiveExtraction

    data class Failure(val message: OrcaText) : ArchiveExtraction
}

/** The ZIP archives Plater::preview_zip_archive() opens. */
interface ArchiveFiles {
    /**
     * The files of the archive in the document, as FileArchiveDialog lists
     * them: every file with an extension but the hidden ones of macOS, by their
     * paths. Null when the archive cannot be opened.
     */
    suspend fun entries(archive: ExternalDocumentReference): List<ArchiveEntry>?

    /**
     * Unzips [entries] of the archive into a folder of app storage under their
     * own names, a name taken twice getting "(1)", "(2)" and so on; the files
     * unzipped before go.
     */
    suspend fun extract(archive: ExternalDocumentReference, entries: List<ArchiveEntry>): ArchiveExtraction
}
