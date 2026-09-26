package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateProject
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.storage.api.SceneFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plater::save_project(): the plate written as an OrcaSlicer project, into
 * the document the user picked ("Save Project as") or, for "Save Project",
 * into the one the project was opened from or last saved to. The project then
 * goes by the name of that document (set_project_filename); a document that
 * cannot be written gets OrcaSlicer's message box.
 */
class SaveProjectUseCase(
    private val inspector: PlateInspector,
    private val thumbnails: PlateThumbnailRenderer,
    private val sceneFiles: SceneFiles,
    private val documents: DocumentExport,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /** "Save Project" has no document to write again and asks for one, as "Save Project as" does. */
    val needsDocument: Boolean get() = repository.state.value.project.document == null

    operator fun invoke(document: ExternalDocumentReference? = null) {
        val state = repository.state.value
        val target = document ?: state.project.document ?: return
        val profiles = state.profiles ?: return
        applicationScope.launch {
            val prefix = sceneFiles.newImportPrefix()
            val file = ScenePath("${prefix.value}-project.3mf")
            try {
                // Plater::export_3mf(): the plate's picture, every part of the
                // objects on it whether it prints or not (THUMBNAIL_SIZE_3MF).
                val picture = state.plate?.let { plate ->
                    try {
                        thumbnails.render(state.objects, plate, state.presets?.filamentColors.orEmpty(), listOf(PICTURE_SIZE), printableOnly = false) {
                            ScenePath("${prefix.value}-plate.rgba")
                        }.firstOrNull()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        null
                    }
                }
                val outcome = try {
                    inspector.saveProject(file, state.objects.map { it.placed() }, profiles, state.plateSettings, state.layerGcodes, picture)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    ProjectSaveOutcome.Failure(error.message.orEmpty())
                }
                val saved = outcome is ProjectSaveOutcome.Success && documents.copyTo(file.value, target)
                val savedName = if (saved) documents.displayName(target)?.let(::projectNameOf) else null
                repository.update { current ->
                    if (saved) {
                        current.copy(project = PlateProject(savedName ?: current.project.name, target))
                    } else {
                        current.copy(plateNotices = current.plateNotices + SAVE_FAILED)
                    }
                }
            } finally {
                sceneFiles.deleteImport(prefix)
            }
        }
    }

    private companion object {
        val PICTURE_SIZE = ThumbnailSize(512, 512)

        /** Plater::save_project()'s message box when export_3mf() fails. */
        val SAVE_FAILED = SettingsDialog(
            id = "save_project_failed",
            icon = DialogIcon.WARNING,
            title = listOf(OrcaText("Save project")),
            text = listOf(
                OrcaText("Failed to save the project.\nPlease check whether the folder exists online or if other programs open the project file."),
            ),
            question = false,
            yes = null,
            no = null,
        )
    }
}

/** Plater::priv::set_project_filename(): a project goes by its file's name without the extension. */
internal fun projectNameOf(displayName: String): String = displayName.substringBeforeLast('.').ifEmpty { displayName }
