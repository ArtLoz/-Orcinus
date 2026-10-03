package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ObjectList::load_subobject(): "Load..." of the submenus that add a part, a
 * negative volume, a modifier, a support blocker or a support enforcer. Each
 * file the user picked joins the object as one volume of its kind, in their
 * order (load_modifier()): named after the file, printed with the object's
 * filament when it is a part, where it stood in its file beside the object's
 * own mesh. A STEP file asks StepMeshDialog first; its Cancel, as a file that
 * cannot be read, stops the load at that file and keeps the volumes before
 * it. One Undo step takes them all ("Load Part" or "Load Modifier"), and the
 * object list selects the first new volume.
 */
class LoadObjectVolumesUseCase(
    private val importModel: ImportModelUseCase,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val stepMeshPrompt: StepMeshPrompt,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath, type: VolumeType, documents: List<ExternalDocumentReference>) {
        if (documents.isEmpty()) return
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(mesh)
            if (state.busy || profiles == null || target == null || target.placing) return@update state
            request = state.objects to profiles
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles) = request ?: return
        applicationScope.launch {
            var objects = plate
            var current = mesh
            var selected: Int? = null
            var written: ScenePath? = null
            val notices = mutableListOf<SettingsDialog>()
            var problem: PlateProblem? = null
            try {
                for (document in documents) {
                    val index = objects.indexOfFirst { it.mesh == current }
                    val source = when (val imported = importModel(document)) {
                        is ModelImportOutcome.Success -> imported.model
                        is ModelImportOutcome.Failure -> {
                            problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, imported.message)
                            break
                        }
                    }
                    val prefix = sceneFiles.newImportPrefix()
                    val outcome = try {
                        loadVolume(objects.map { it.placed() }, index, source.path, source.displayName, type, profiles, prefix)
                    } catch (cancellation: CancellationException) {
                        sceneFiles.deleteImport(prefix)
                        throw cancellation
                    } catch (error: Exception) {
                        ModelLoadOutcome.Failure(error.message.orEmpty())
                    }
                    notices += outcome.notices
                    val loaded = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(objects[index])
                    if (loaded == null) {
                        sceneFiles.deleteImport(prefix)
                        if (outcome is ModelLoadOutcome.Failure) problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, outcome.message)
                        break
                    }
                    if (selected == null) selected = outcome.selectedVolume
                    // The object of the file before is no one's any more: its meshes go.
                    written?.let(sceneFiles::deleteImport)
                    written = prefix
                    objects = objects.map { if (it.mesh == current) loaded else it }
                    current = loaded.mesh
                }
            } finally {
                val loaded = objects.withMesh(current).takeIf { current != mesh }
                repository.update { state ->
                    val done = state.copy(editing = false, plateNotices = state.plateNotices + notices, problem = problem ?: state.problem)
                    if (loaded == null || state.objects.withMesh(mesh) == null) {
                        done
                    } else {
                        val instance = state.selectedInstances.firstOrNull { it.mesh == mesh }?.instance ?: 0
                        done.recorded().copy(
                            objects = state.objects.map { if (it.mesh == mesh) loaded else it },
                            // ObjectList::load_subobject() selects the new volumes.
                            selectedInstances = setOf(PlateInstanceId(loaded.mesh, instance)),
                            selectedPart = selected?.let { ObjectPartId(loaded.mesh, it) },
                            selectedRange = null,
                            result = null,
                        )
                    }
                }
                // An object that did not join the plate after all.
                if (loaded != null && repository.state.value.objects.withMesh(loaded.mesh) == null) written?.let(sceneFiles::deleteImport)
            }
        }
    }

    /** load_modifier() of one file: a STEP file asks StepMeshDialog, whose Cancel adds nothing. */
    private suspend fun loadVolume(
        plate: List<PlacedModel>,
        index: Int,
        source: ModelPath,
        name: String,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome {
        val outcome = inspector.loadVolume(plate, index, source, name, type, profiles, prefix)
        if (outcome !is ModelLoadOutcome.StepMesh) return outcome
        val options = stepMeshPrompt.ask(source, outcome.options) ?: return ModelLoadOutcome.Success(emptyList(), outcome.notices)
        return inspector.loadVolume(plate, index, source, name, type, profiles, prefix, options)
    }
}
