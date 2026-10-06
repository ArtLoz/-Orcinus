package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.ProjectContent
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.ProjectPrompt
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.BackupOrigin
import app.orcinus.shadow.storage.api.ProjectBackup
import app.orcinus.shadow.storage.api.ProjectBackupFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * OrcaSlicer's project backup (_BBS_Backup_Manager with MainFrame's backup
 * callback, "Auto backup" of the Preferences) and its restore at start
 * (EVT_RESTORE_PROJECT).
 *
 * Once the plate can load a project, a backup an earlier run left asks
 * "Previous unsaved project detected, do you want to restore it?": Yes opens
 * it under the name of the document it came from, unsaved; No deletes it.
 * From then on, every backup_interval seconds while backup_switch is on, a
 * project that changed since it was opened, saved or last backed up is written
 * as the backup (export_3mf() with SaveStrategy::Backup, without pictures). A
 * project that is saved or started afresh leaves no backup (remove_backup()).
 *
 * The desktop app backs up its engine's model object by object; the app's
 * plate lives in the app, so it writes the whole project, as the backup
 * callback does, through the engine that saves projects.
 */
class ProjectBackupUseCase(
    private val inspector: PlateInspector,
    private val files: ProjectBackupFiles,
    private val preferences: AppPreferences,
    private val repository: PlateRepository,
    private val restore: suspend (ProjectBackup) -> Unit,
    private val applicationScope: CoroutineScope,
) {
    private var answer: CompletableDeferred<Boolean>? = null

    /** What the last backup holds (m_backup_timestamp); null until one was written. */
    private var backedUp: ProjectContent? = null

    fun start() {
        applicationScope.launch {
            // EVT_RESTORE_PROJECT, once: the plate can load a project.
            repository.state.first { it.engine.availability == EngineAvailability.READY && it.profiles != null && !it.busy }
            offerRestore()
            launch { removeWhenSaved() }
            // set_backup_interval(): a change of the Preferences takes effect at once.
            preferences.values
                .map { AppConfigKeys.backupInterval(it[AppConfigKeys.BACKUP_SWITCH], it[AppConfigKeys.BACKUP_INTERVAL]) }
                .distinctUntilChanged()
                .collectLatest { seconds ->
                    if (seconds <= 0) return@collectLatest
                    while (true) {
                        delay(seconds * 1_000L)
                        backUp()
                    }
                }
        }
    }

    /** The answer to "Previous unsaved project detected, do you want to restore it?" */
    fun answerRestore(yes: Boolean) {
        answer?.complete(yes)
    }

    private suspend fun offerRestore() {
        val backup = files.read() ?: return
        val reply = CompletableDeferred<Boolean>()
        answer = reply
        repository.update { it.copy(projectPrompt = ProjectPrompt.RestoreBackup) }
        val yes = try {
            reply.await()
        } finally {
            answer = null
            repository.update { if (it.projectPrompt == ProjectPrompt.RestoreBackup) it.copy(projectPrompt = null) else it }
        }
        if (yes) {
            restore(backup)
            // Slic3r::backup_soon(): the restored project is the backup now.
            backedUp = repository.state.value.projectContent()
        } else {
            files.removeAll()
        }
    }

    /** Plater::save_project() and new_project(): what is saved, or nothing at all, needs no backup. */
    private suspend fun removeWhenSaved() {
        repository.state
            // up_to_date(): nothing on the plate, or nothing changed since it was opened, saved or started.
            .map { it.project.baseline to it.projectUpToDate }
            .distinctUntilChanged()
            .collect { (_, upToDate) ->
                if (upToDate) {
                    files.removeProject()
                    backedUp = null
                }
            }
    }

    /**
     * MainFrame's backup callback: up_to_date(false, true), nothing changed
     * since the project was opened or saved, or since its last backup, then
     * export_3mf() of the backup.
     */
    private suspend fun backUp() {
        val state = repository.state.value
        if (state.busy || state.objects.isEmpty()) return
        val content = state.projectContent()
        if (content == (backedUp ?: state.project.baseline) && !state.project.otherChangesBackup) return
        val profiles = state.profiles ?: return
        val file = files.pendingFile()
        val plates = state.partPlates().map { plate ->
            ProjectPlate(
                name = plate.name,
                locked = plate.locked,
                settings = plate.settings.withFlushVolumesOf(state.plateSettings),
                layerGcodes = plate.layerGcodes,
                // SaveStrategy::Backup renders no pictures.
                thumbnail = null,
            )
        }
        val outcome = try {
            inspector.saveProject(file, state.objects.map { it.placed() }, profiles, plates, state.project.info)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ProjectSaveOutcome.Failure(error.message.orEmpty())
        }
        if (outcome !is ProjectSaveOutcome.Success) return
        // origin.txt: the document the project was opened from or saved to.
        val origin = BackupOrigin(state.project.document, state.project.name?.takeIf { state.project.document != null })
        if (files.commit(origin)) {
            backedUp = content
            // up_to_date(true, true)
            repository.update { it.copy(project = it.project.copy(otherChangesBackup = false)) }
        }
    }
}
