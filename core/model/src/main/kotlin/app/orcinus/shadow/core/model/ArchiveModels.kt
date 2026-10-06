package app.orcinus.shadow.core.model

/** A file of a ZIP archive: its path in the archive and its unzipped size, which tell files of the same path apart. */
data class ArchiveEntry(val path: String, val size: Long)

/**
 * FileArchiveDialog ("Archive preview") over the archive [name]: the files of
 * the archive it offers, the model and project files, by their paths, and
 * those it starts with picked — all of them when it offers only one.
 */
data class ArchivePreview(val name: String, val entries: List<ArchiveEntry>, val picked: List<ArchiveEntry>)
