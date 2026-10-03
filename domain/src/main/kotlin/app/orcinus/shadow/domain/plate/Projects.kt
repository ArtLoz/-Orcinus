package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.PlatePicture
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.SlicedPlates
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.allSliceResultsReady
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
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.storage.api.FileShare
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
    /** set_project_filename() adds the project to the recent files. */
    private val recentProjects: RecentProjects? = null,
    private val files: FileShare,
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
        if (state.profiles == null) return false
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-project.3mf")
        try {
            val saved = write(state, file, prefix) && documents.copyTo(file.value, document)
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
            if (saved) recentProjects?.add(listOf(document))
            return saved
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }

    /**
     * Android's share sheet for the project: the plates written as a project
     * as "Save Project as" writes them, offered as [name].3mf; the project
     * keeps its name, its document and its unsaved changes. Null when it
     * could not be written, after the message box says so.
     */
    suspend fun share(name: String): ExternalDocumentReference? {
        val state = repository.state.value
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-project.3mf")
        try {
            val shared = if (write(state, file, prefix)) files.shareable(file.value, "$name.3mf") else null
            if (shared == null) repository.update { it.copy(plateNotices = it.plateNotices + SAVE_FAILED) }
            return shared
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }

    /**
     * Plater::export_gcode_3mf(): the plates written as a project with the
     * G-code and slice info of the current plate, or of every sliced plate
     * when [all], into [document]. The project keeps its name, its document
     * and its unsaved changes (Silence). False when there is no G-code to
     * export, or after the message box says it could not be written.
     */
    suspend fun exportSliced(document: ExternalDocumentReference, all: Boolean): Boolean {
        val state = repository.state.value
        // MainFrame::can_export_gcode() and can_export_all_gcode()
        val ready = if (all) state.allSliceResultsReady() else state.result != null
        if (state.objects.isEmpty() || !ready) return false
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-sliced.gcode.3mf")
        try {
            val exported = write(state, file, prefix, if (all) SlicedPlates.ALL else SlicedPlates.CURRENT) && documents.copyTo(file.value, document)
            if (!exported) repository.update { it.copy(plateNotices = it.plateNotices + SAVE_FAILED) }
            return exported
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }

    /**
     * The name a sliced plate's file is offered under: the G-code's, as
     * output_filepath_for_project() names it, with ".gcode.3mf".
     */
    fun slicedName(): String? = repository.state.value.result?.gcode?.value?.let { java.io.File(it).name.removeSuffix(".gcode") + ".gcode.3mf" }

    /**
     * Plater::export_3mf() of [state] into [file], with the G-code of the
     * [sliced] plates; false when the engine could not write it.
     */
    private suspend fun write(state: PlateState, file: ScenePath, prefix: ScenePath, sliced: SlicedPlates = SlicedPlates.NONE): Boolean {
        val profiles = state.profiles ?: return false
        // Plater::export_3mf(): every plate with its pictures, every part of
        // the objects on it whether it prints or not (THUMBNAIL_SIZE_3MF), and
        // its slice result while it is valid.
        val origins = state.plateOrigins()
        val plates = state.partPlates().mapIndexed { index, plate ->
            suspend fun pictureOf(kind: PlatePicture, name: String) = picture(state, origins[index], kind, ScenePath("${prefix.value}-plate-${index + 1}-$name.rgba"))
            ProjectPlate(
                name = plate.name,
                locked = plate.locked,
                // The flushing volumes are the project's, which the current plate holds.
                settings = plate.settings.withFlushVolumesOf(state.plateSettings),
                layerGcodes = plate.layerGcodes,
                thumbnail = pictureOf(PlatePicture.PLATE, "picture"),
                noLightThumbnail = pictureOf(PlatePicture.NO_LIGHT, "no-light"),
                topThumbnail = pictureOf(PlatePicture.TOP, "top"),
                pickThumbnail = pictureOf(PlatePicture.PICK, "pick"),
                sliceInfo = plate.result?.sliceInfo,
                gcode = plate.result?.gcode,
            )
        }
        val outcome = try {
            inspector.saveProject(file, state.objects.map { it.placed() }, profiles, plates, state.project.info, state.currentPlate, sliced)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ProjectSaveOutcome.Failure(error.message.orEmpty())
        }
        return outcome is ProjectSaveOutcome.Success
    }

    /** The [kind] of picture of the plate at [origin] in [state], written to [file]; null when it cannot be rendered. */
    private suspend fun picture(state: PlateState, origin: Point2, kind: PlatePicture, file: ScenePath): ThumbnailImage? {
        val plate = state.plate ?: return null
        return try {
            thumbnails.render(state.objects, plate, origin, state.presets?.filamentColors.orEmpty(), listOf(PICTURE_SIZE), kind) { file }
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
    private val preferences: AppPreferences,
) : ProjectCloseConfirmation {
    private var saveChanges: CompletableDeferred<Reply<Boolean>?>? = null
    private var saveAs: CompletableDeferred<ExternalDocumentReference?>? = null
    private var presetChanges: CompletableDeferred<Reply<PresetChangesAnswer>?>? = null

    /** An answer, and whether "Remember my choice." was ticked. */
    private data class Reply<T>(val answer: T, val remember: Boolean)

    /**
     * "The current project has unsaved changes, save it before continue?": Yes,
     * No, or null for Cancel; [remember] keeps Yes or No for the next time
     * (save_project_choise).
     */
    fun answerSaveChanges(save: Boolean?, remember: Boolean = false) {
        saveChanges?.complete(save?.let { Reply(it, remember) })
    }

    /** The document Save Project writes; null cancels. */
    fun saveTo(document: ExternalDocumentReference?) {
        saveAs?.complete(document)
    }

    /** UnsavedChangesDialog's answer, null for Cancel; [remember] keeps the action (save_preset_choise). */
    fun answerPresetChanges(answer: PresetChangesAnswer?, remember: Boolean = false) {
        presetChanges?.complete(answer?.let { Reply(it, remember) })
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
            val kept = current.projectConfigSettings()
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
                // Plater::priv::reset()
                projectResets = current.projectResets + 1,
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
            // The choice "Remember my choice." kept answers without asking.
            val remembered = preferences[AppConfigKeys.SAVE_PROJECT_CHOISE]
            val reply = if (remembered.isEmpty()) ask<Reply<Boolean>>(ProjectPrompt.SaveChanges) { saveChanges = it } ?: return false else Reply(remembered == "yes", false)
            if (reply.remember) preferences.set(AppConfigKeys.SAVE_PROJECT_CHOISE, if (reply.answer) "yes" else "no")
            if (reply.answer) {
                val document = state.project.document ?: ask(ProjectPrompt.SaveAs) { saveAs = it }
                saved = document != null && saveProject.save(document)
                // A save that did not happen stays here; with a remembered Yes the project goes on unsaved, as with No.
                if (!saved && remembered.isEmpty()) return false
            }
        }
        // Its second check: the presets.
        return if (newProject) keepPresetChanges(saved) else saved || savePresetChanges()
    }

    /**
     * UnsavedChangesDialog::ShowModal(): the remembered action (save_preset_choise)
     * answers without the dialog when the dialog offers it — the desktop app's
     * check tests the action's bit against the buttons, and Save always passes.
     */
    private fun rememberedPresetAction(buttons: Int): Int? {
        val action = preferences[AppConfigKeys.SAVE_PRESET_CHOISE].toIntOrNull() ?: return null
        return action.takeIf { action in 0..30 && ((1 shl action) and (buttons or DONT_SAVE)) != 0 }
    }

    /** UnsavedChangesDialog with "Remember my choice.": the action is kept once the dialog was answered. */
    private suspend fun askPresetChanges(prompt: ProjectPrompt.PresetChanges): PresetChangesAnswer? {
        val reply = ask<Reply<PresetChangesAnswer>>(prompt) { presetChanges = it } ?: return null
        if (reply.remember) preferences.set(AppConfigKeys.SAVE_PRESET_CHOISE, actionOf(reply.answer).toString())
        return reply.answer
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
        // A remembered action answers alone. The desktop app collects the names
        // of the presets to save only when Save is clicked, so a remembered Save
        // saves nothing and, as Discard, loses the changes.
        when (rememberedPresetAction(KEEP or if (saved) 0 else SAVE)) {
            ACTION_TRANSFER -> return true
            ACTION_DISCARD, ACTION_SAVE -> {
                resetModifications()
                return true
            }
        }
        return when (val answer = askPresetChanges(prompt)) {
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
        // A remembered action answers alone and, as a remembered Save names no
        // preset, saves nothing: the project brings its own.
        if (rememberedPresetAction(SAVE) != null) return true
        return when (val answer = askPresetChanges(prompt)) {
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
        // UnsavedChangesDialog's ActionButtons and Action, as save_preset_choise keeps an action.
        const val KEEP = 2
        const val SAVE = 4
        const val DONT_SAVE = 8
        const val ACTION_TRANSFER = 1
        const val ACTION_DISCARD = 2
        const val ACTION_SAVE = 3

        fun actionOf(answer: PresetChangesAnswer): Int = when (answer) {
            PresetChangesAnswer.Transfer -> ACTION_TRANSFER
            PresetChangesAnswer.Discard -> ACTION_DISCARD
            is PresetChangesAnswer.Save -> ACTION_SAVE
        }
    }
}

/** Plater::priv::set_project_filename(): a project goes by its file's name without the extension. */
/**
 * PartPlateList::reinit() of Plater::priv::reset(): the plate loses its own
 * settings; the flushing volumes are the project config's, which stays.
 */
internal fun PlateState.projectConfigSettings(): ModelSettings = ModelSettings(plateSettings.values.filterKeys { it in PROJECT_CONFIG_KEYS })

/** The project config's own values the plate keeps, which a reset plate keeps. */
private val PROJECT_CONFIG_KEYS = setOf("flush_volumes_matrix", "flush_multiplier")

internal fun projectNameOf(displayName: String): String = displayName.substringBeforeLast('.').ifEmpty { displayName }

/** The project with what [this] plate holds now and the presets it has, as the project's saved state. */
internal fun PlateState.projectBaseline(): PlateProject = project.copy(
    baseline = projectContent(),
    presets = profiles,
    filamentColors = presets?.filamentColors.orEmpty(),
)
