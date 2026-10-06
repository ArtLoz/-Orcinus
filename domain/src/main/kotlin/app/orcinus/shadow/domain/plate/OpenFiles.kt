package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ArchiveEntry
import app.orcinus.shadow.core.model.ArchivePreview
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.storage.api.ArchiveExtraction
import app.orcinus.shadow.storage.api.ArchiveFiles
import app.orcinus.shadow.storage.api.DocumentExport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Plater::load_files() of file names, which the desktop app runs for files
 * dropped on its window or handed over by the system (GUI_App::MacOpenFiles()),
 * and "Import Zip Archive" (Plater::import_zip_archive()): here the files
 * another app opens with the app or shares with it, and the archive the user
 * picks.
 */
class OpenFilesUseCase(
    private val documents: DocumentExport,
    private val archives: ArchiveFiles,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    private val previewState = MutableStateFlow<ArchivePreview?>(null)

    /** The archive whose files FileArchiveDialog offers now; null while none is open. */
    val preview: StateFlow<ArchivePreview?> = previewState.asStateFlow()

    private var reply: CompletableDeferred<List<ArchiveEntry>?>? = null

    /** One batch of files at a time, as the desktop app's modal dialogs take them. */
    private val opening = Mutex()

    /**
     * The model, project and archive files among [references], by their
     * names, one after another as the desktop app sorts them; a G-code file
     * goes alone. The archives are previewed first and their files picked,
     * then the other files load as Plater::add_file() loads files picked at
     * once, without joining the recent files.
     */
    fun open(references: List<ExternalDocumentReference>) {
        if (references.isEmpty()) return
        applicationScope.launch {
            opening.withLock {
                val named = references.map { it to name(it) }
                val normal = named.filter { MODEL_FILES.matches(it.second) }.sortedBy { it.second }
                val gcode = named.filter { GCODE_FILES.matches(it.second) }
                when {
                    // Likely no supported files.
                    normal.isEmpty() && gcode.isEmpty() -> Unit
                    normal.isEmpty() -> if (gcode.size > 1) inform(ONLY_ONE_GCODE)
                    gcode.isNotEmpty() -> inform(GCODE_WITH_MODELS)
                    else -> {
                        // The lambda handle_zips(): each archive is previewed, and leaves the files.
                        val (zips, files) = normal.partition { it.second.endsWith(ZIP, ignoreCase = true) }
                        zips.forEach { (zip, zipName) -> previewArchive(zip, zipName) }
                        if (files.isNotEmpty()) {
                            awaitIdle()
                            addModelToPlate(files.map { it.first }, addsRecent = false)
                            awaitIdle()
                        }
                    }
                }
            }
        }
    }

    /** "Import Zip Archive": load_files() of the archive the user picked. */
    fun importZip(reference: ExternalDocumentReference) = open(listOf(reference))

    /** FileArchiveDialog's Open with the files picked, or Cancel with null. */
    fun answer(picked: List<ArchiveEntry>?) {
        reply?.complete(picked)
    }

    /**
     * Plater::preview_zip_archive(): the files of the archive the user picks
     * are unzipped and load as loadUnzipped() loads them; a project file is a
     * 3MF file or an AMF file of a ZIP archive (".zip.amf"). An archive that
     * cannot be read, or a file that cannot be unzipped, shows its error.
     */
    private suspend fun previewArchive(archive: ExternalDocumentReference, archiveName: String) {
        val entries = archives.entries(archive)
        if (entries == null) {
            // TRN %1% is archive path
            showError(OrcaText("Loading of a ZIP archive on path %1% has failed.", listOf(archiveName)))
            return
        }
        // FileArchiveDialog: only the files the desktop app loads from an archive are offered.
        val offered = entries.filter { ARCHIVE_FILES.matches(it.path) }
        val answer = CompletableDeferred<List<ArchiveEntry>?>()
        reply = answer
        previewState.value = ArchivePreview(archiveName, offered, if (offered.size == 1) offered else emptyList())
        val picked = try {
            answer.await()
        } finally {
            reply = null
            previewState.value = null
        }
        if (picked.isNullOrEmpty()) return
        when (val unzipped = archives.extract(archive, picked)) {
            is ArchiveExtraction.Failure -> showError(unzipped.message)
            is ArchiveExtraction.Success -> {
                val (projects, models) = unzipped.files.partition(::isProject)
                awaitIdle()
                addModelToPlate.loadUnzipped(projects, models)
                awaitIdle()
            }
        }
    }

    private fun isProject(path: ModelPath): Boolean {
        val name = path.value.substringAfterLast('/')
        return name.endsWith(".3mf", ignoreCase = true) || name.endsWith(".zip.amf", ignoreCase = true)
    }

    /** The plate takes another load only when the one before, with its questions, is over. */
    private suspend fun awaitIdle() {
        repository.state.first { !it.busy && it.projectDrop == null && it.plateQuestion == null }
    }

    private suspend fun name(reference: ExternalDocumentReference): String =
        documents.displayName(reference) ?: reference.value.substringAfterLast('/')

    /** show_info() with the caption "G-code loading". */
    private fun inform(text: OrcaText) = notice(SettingsDialog(GCODE_LOADING_ID, DialogIcon.INFO, listOf(GCODE_LOADING), listOf(text), question = false, yes = null, no = null))

    /** show_error() of the archive's file. */
    private fun showError(text: OrcaText) = notice(SettingsDialog(ARCHIVE_ERROR_ID, DialogIcon.ERROR, emptyList(), listOf(text), question = false, yes = null, no = null))

    private fun notice(dialog: SettingsDialog) = repository.update { it.copy(plateNotices = it.plateNotices + dialog) }

    private companion object {
        /** pattern_drop and pattern_gcode_drop of Plater::load_files(). */
        val MODEL_FILES = Regex(".*[.](stp|step|stl|oltp|obj|amf|3mf|svg|zip|drc)", RegexOption.IGNORE_CASE)
        val GCODE_FILES = Regex(".*[.](gcode|g)", RegexOption.IGNORE_CASE)

        /** FileArchiveDialog's pattern_drop. */
        val ARCHIVE_FILES = Regex(".*[.](stl|obj|amf|3mf|step|stp)", RegexOption.IGNORE_CASE)

        const val ZIP = ".zip"
        const val GCODE_LOADING_ID = "gcode_loading"
        const val ARCHIVE_ERROR_ID = "archive_error"
        val GCODE_LOADING = OrcaText("G-code loading")
        val ONLY_ONE_GCODE = OrcaText("Only one G-code file can be opened at the same time.")
        val GCODE_WITH_MODELS = OrcaText("G-code files cannot be loaded with models together!")
    }
}
