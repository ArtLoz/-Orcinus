package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.DirtyPreset
import app.orcinus.shadow.core.model.DirtyPresetsOutcome
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateHistory
import app.orcinus.shadow.core.model.PlateProject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChangesAnswer
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProjectContent
import app.orcinus.shadow.core.model.ProjectPrompt
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.storage.api.SceneFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plater::save_project(): the plates written as an OrcaSlicer project, into
 * the document the user picked ("Save Project as") or, for "Save Project",
 * into the one the project was opened from or last saved to. The project then
 * goes by the name of that document (set_project_filename) and is no longer
 * dirty; a document that cannot be written gets OrcaSlicer's message box.
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
        val target = document ?: repository.state.value.project.document ?: return
        applicationScope.launch { save(target) }
    }

    /** Writes the project into [document]; false when it could not, after the message box says so. */
    suspend fun save(document: ExternalDocumentReference): Boolean {
        val state = repository.state.value
        val profiles = state.profiles ?: return false
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-project.3mf")
        try {
            // Plater::export_3mf(): every plate with its picture, every part
            // of the objects on it whether it prints or not (THUMBNAIL_SIZE_3MF).
            val origins = state.plateOrigins()
            val plates = state.partPlates().mapIndexed { index, plate ->
                ProjectPlate(
                    name = plate.name,
                    locked = plate.locked,
                    // The flushing volumes are the project's, which the current plate holds.
                    settings = plate.settings.withFlushVolumesOf(state.plateSettings),
                    layerGcodes = plate.layerGcodes,
                    thumbnail = picture(state, origins[index], ScenePath("${prefix.value}-plate-${index + 1}.rgba")),
                )
            }
            val outcome = try {
                inspector.saveProject(file, state.objects.map { it.placed() }, profiles, plates, state.project.info)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                ProjectSaveOutcome.Failure(error.message.orEmpty())
            }
            val saved = outcome is ProjectSaveOutcome.Success && documents.copyTo(file.value, document)
            val savedName = if (saved) documents.displayName(document)?.let(::projectNameOf) else null
            repository.update { current ->
                if (saved) {
                    // Plater::reset_project_dirty_after_save(): what was saved is the project now.
                    current.copy(
                        project = state.projectBaseline().copy(name = savedName ?: current.project.name, document = document),
                    )
                } else {
                    current.copy(plateNotices = current.plateNotices + SAVE_FAILED)
                }
            }
            return saved
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }

    /** The picture of the plate at [origin] in [state], written to [file]; null when it cannot be rendered. */
    private suspend fun picture(state: PlateState, origin: Point2, file: ScenePath): ThumbnailImage? {
        val plate = state.plate ?: return null
        return try {
            thumbnails.render(state.objects, plate, origin, state.presets?.filamentColors.orEmpty(), listOf(PICTURE_SIZE), printableOnly = false) { file }
                .firstOrNull()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
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

/** What a project that is to be left behind asks first, before another one takes the plate. */
fun interface ProjectCloseConfirmation {
    /** True once the user agreed to go on: to a new project when [newProject], to a project that loads otherwise. */
    suspend fun confirm(newProject: Boolean): Boolean
}

/**
 * The desktop app's New Project and what Open Project asks before it loads
 * (Plater::new_project and load_project): a project with unsaved changes is
 * saved when the user wants it (close_with_confirm), and the presets with
 * unsaved changes are kept for the new project, saved or discarded
 * (check_and_keep_current_preset_changes, check_and_save_current_preset_changes).
 * Each question waits on the plate for its answer, as the desktop app's modal
 * boxes wait.
 */
class ProjectLifecycleUseCase(
    private val repository: PlateRepository,
    private val saveProject: SaveProjectUseCase,
    private val presetManager: PresetManager,
    private val settingsEditor: PresetSettingsEditor,
    private val platePresets: PresetsApplier,
    private val applicationScope: CoroutineScope,
) : ProjectCloseConfirmation {
    private var saveChanges: CompletableDeferred<Boolean?>? = null
    private var saveAs: CompletableDeferred<ExternalDocumentReference?>? = null
    private var presetChanges: CompletableDeferred<PresetChangesAnswer?>? = null

    /** "The current project has unsaved changes, save it before continue?": Yes, No, or null for Cancel. */
    fun answerSaveChanges(save: Boolean?) {
        saveChanges?.complete(save)
    }

    /** The document Save Project writes; null cancels. */
    fun saveTo(document: ExternalDocumentReference?) {
        saveAs?.complete(document)
    }

    /** UnsavedChangesDialog's answer; null for Cancel. */
    fun answerPresetChanges(answer: PresetChangesAnswer?) {
        presetChanges?.complete(answer)
    }

    /**
     * Plater::new_project(): the plate starts afresh as "Untitled" once the
     * user agreed, with Undo cleared and the presets the project before
     * brought gone; the presets themselves stay selected.
     */
    fun newProject() {
        applicationScope.launch { startNewProject() }
    }

    /**
     * Plater::new_project() with the project's [name], or "Untitled" for none;
     * true once the new project stands, false when the user stayed.
     */
    suspend fun startNewProject(name: String? = null): Boolean {
        val state = repository.state.value
        if (state.busy || state.projectPrompt != null || state.profiles == null) return false
        if (!confirm(newProject = true)) return false
        val before = repository.state.value.profiles
        val presets = presetManager.resetProjectPresets()
        repository.update { current ->
            // PartPlateList::reinit(): the plate loses its own settings; the
            // flushing volumes are the project config's, which stays.
            val kept = ModelSettings(current.plateSettings.values.filterKeys { it in PROJECT_CONFIG_KEYS })
            val selected = (presets as? PresetsOutcome.Success)?.presets ?: current.presets
            current.copy(
                presets = selected,
                objects = emptyList(),
                selectedInstances = emptySet(),
                selectedPart = null,
                selectedRange = null,
                simplifyTarget = null,
                // PartPlateList::reinit(): one plate again.
                plates = listOf(PartPlate(settings = kept)),
                currentPlate = 0,
                plateSettings = kept,
                layerGcodes = emptyList(),
                paPattern = null,
                // "New Project" is a ProjectSeparator, which clears Undo.
                history = PlateHistory(),
                result = null,
                problem = null,
                project = PlateProject(
                    name = name,
                    baseline = ProjectContent(plates = listOf(PartPlate(settings = kept))),
                    presets = selected?.takeUnless { it.setupRequired }?.selection,
                    filamentColors = selected?.filamentColors.orEmpty(),
                ),
            )
        }
        platePresets.apply(before, presets)
        return true
    }

    override suspend fun confirm(newProject: Boolean): Boolean {
        val state = repository.state.value
        // Plater::close_with_confirm()
        var saved = false
        if (!state.projectUpToDate) {
            when (ask(ProjectPrompt.SaveChanges) { saveChanges = it }) {
                null -> return false
                true -> {
                    val document = state.project.document ?: ask(ProjectPrompt.SaveAs) { saveAs = it } ?: return false
                    if (!saveProject.save(document)) return false
                    saved = true
                }
                false -> Unit
            }
        }
        // Its second check: the presets.
        return if (newProject) keepPresetChanges(saved) else saved || savePresetChanges()
    }

    /**
     * GUI_App::check_and_keep_current_preset_changes() for a new project: the
     * changes move to it (Transfer), are discarded, or are saved as presets
     * when the project was not just saved.
     */
    private suspend fun keepPresetChanges(saved: Boolean): Boolean {
        val dirty = dirtyPresets() ?: return true
        val header = listOf(
            OrcaText("Some presets are modified."),
            OrcaText("\n"),
            OrcaText(
                if (saved) {
                    "You can keep the modified presets to the new project or discard them"
                } else {
                    "You can keep the modified presets to the new project, discard or save changes as new presets."
                },
            ),
        )
        val prompt = ProjectPrompt.PresetChanges(OrcaText("Creating a new project"), header, dirty, transfer = true, save = !saved)
        return when (val answer = ask(prompt) { presetChanges = it }) {
            null -> false
            PresetChangesAnswer.Transfer -> true
            PresetChangesAnswer.Discard -> {
                resetModifications()
                true
            }
            is PresetChangesAnswer.Save -> {
                savePresets(dirty, answer)
                // The presets saved, whatever is left is discarded.
                resetModifications()
                true
            }
        }
    }

    /**
     * GUI_App::check_and_save_current_preset_changes() before a project
     * loads: the changes are saved as presets, or left to the project's own.
     */
    private suspend fun savePresetChanges(): Boolean {
        val dirty = dirtyPresets() ?: return true
        val prompt = ProjectPrompt.PresetChanges(OrcaText("Load project"), listOf(OrcaText("Some presets are modified.")), dirty, transfer = false, save = true)
        return when (val answer = ask(prompt) { presetChanges = it }) {
            null -> false
            PresetChangesAnswer.Transfer, PresetChangesAnswer.Discard -> true
            is PresetChangesAnswer.Save -> {
                savePresets(dirty, answer)
                true
            }
        }
    }

    private suspend fun dirtyPresets() = (presetManager.dirtyPresets() as? DirtyPresetsOutcome.Success)?.presets?.takeIf { it.isNotEmpty() }

    /** UnsavedChangesDialog::save() and PresetBundle::save_changes_for_preset() for every modified preset. */
    private suspend fun savePresets(dirty: List<DirtyPreset>, answer: PresetChangesAnswer.Save) {
        val before = repository.state.value.profiles
        for (preset in dirty) {
            val name = if (preset.canOverwrite) preset.name else answer.names[preset.kind] ?: continue
            settingsEditor.savePreset(preset.kind, name)
        }
        platePresets.apply(before, presetManager.presets())
    }

    /** reset_modifications(): every tab's preset loses its unsaved changes. */
    private suspend fun resetModifications() {
        val before = repository.state.value.profiles
        platePresets.apply(before, presetManager.discardPresetChanges())
    }

    /** Shows [prompt] and waits for the answer that completes the deferred [keep] holds. */
    private suspend fun <T> ask(prompt: ProjectPrompt, keep: (CompletableDeferred<T?>?) -> Unit): T? {
        val answer = CompletableDeferred<T?>()
        keep(answer)
        repository.update { it.copy(projectPrompt = prompt) }
        return try {
            answer.await()
        } finally {
            keep(null)
            repository.update { if (it.projectPrompt == prompt) it.copy(projectPrompt = null) else it }
        }
    }

    private companion object {
        /** The project config's own values the plate keeps, which a new project keeps. */
        val PROJECT_CONFIG_KEYS = setOf("flush_volumes_matrix", "flush_multiplier")
    }
}

/** Plater::priv::set_project_filename(): a project goes by its file's name without the extension. */
internal fun projectNameOf(displayName: String): String = displayName.substringBeforeLast('.').ifEmpty { displayName }

/** The project with what [this] plate holds now and the presets it has, as the project's saved state. */
internal fun PlateState.projectBaseline(): PlateProject = project.copy(
    baseline = projectContent(),
    presets = profiles,
    filamentColors = presets?.filamentColors.orEmpty(),
)
