package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.MeshBooleanOperation
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateRequest
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * GLGizmoMeshBoolean's "Union", "Difference" and "Intersection": the volumes
 * source and tool (ModelObject::volumes) of the object of the copy are
 * joined, subtracted or intersected by the engine, and the result takes the
 * source's place ("Mesh Boolean"); a union takes the tool away, the others
 * when asked to, a step of Undo of its own ("Delete part"), but not from a
 * part of a cut, which asks to invalidate the cut info instead
 * (del_from_cut_object()). The object list then selects the new volume. An
 * empty result changes nothing and warns "Unable to perform boolean operation
 * on selected parts". Answers true once the volumes changed.
 */
class MeshBooleanUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(
        copy: PlateInstanceId,
        source: Int,
        tool: Int,
        operation: MeshBooleanOperation,
        deleteInput: Boolean,
    ): Deferred<Boolean> {
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(copy.mesh)
            if (state.busy || profiles == null || target == null || target.placing) return@update state
            request = state.objects to profiles
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles) = request ?: return CompletableDeferred(false)
        return applicationScope.async {
            val index = plate.indexOfFirst { it.mesh == copy.mesh }
            val target = plate[index]
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                inspector.meshBoolean(plate.map { it.placed() }, index, source, tool, operation, deleteInput, profiles, prefix)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                repository.update { it.copy(editing = false) }
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            val written = (outcome as? ModelLoadOutcome.Success)?.objects.orEmpty().map { it.toPlateObjectOf(target) }
            // del_subobject_from_object() refuses the last solid part with its message, before it asks about a cut.
            val refused = outcome.notices.any { it.id == LAST_SOLID_PART }
            val askCut = (operation == MeshBooleanOperation.UNION || deleteInput) && written.size == 1 && !refused
            val toolType = target.volumeAt(tool)?.type ?: VolumeType.PART
            var changed = false
            repository.update { state ->
                val done = state.copy(editing = false, plateNotices = state.plateNotices + outcome.notices)
                val at = state.objects.indexOfFirst { it.mesh == copy.mesh }
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    written.isEmpty() -> done.copy(problem = PlateProblem(PlateProblemKind.MESH_BOOLEAN_FAILED, null))
                    at < 0 -> done
                    else -> {
                        changed = true
                        val last = written.last()
                        // "Mesh Boolean", then "Delete part" when the tool went. The
                        // result takes the source's place in the object, so the tool,
                        // open until the end, keeps its picks for the object written
                        // anew (GLGizmoMeshBoolean::on_save() of "Delete part").
                        val steps = written.fold(done) { step, result ->
                            step.recorded().copy(
                                objects = step.objects.mapIndexed { position, it -> if (position == at) result else it },
                                meshBooleanTool = step.meshBooleanTool?.copy(copy = PlateInstanceId(result.mesh, copy.instance)),
                            )
                        }
                        val question = target.cutVolumeQuestion(toolType)?.takeIf { askCut }
                        steps.copy(
                            selectedInstances = setOf(PlateInstanceId(last.mesh, copy.instance)),
                            selectedPart = (outcome as ModelLoadOutcome.Success).selectedVolume?.let { ObjectPartId(last.mesh, it) },
                            selectedRange = null,
                            result = null,
                            plateQuestion = question?.copy(request = PlateRequest.InvalidateCut(mesh = last.mesh)) ?: steps.plateQuestion,
                        )
                    }
                }
            }
            if (!changed && written.isNotEmpty()) sceneFiles.deleteImport(prefix)
            if (written.isEmpty()) sceneFiles.deleteImport(prefix)
            changed
        }
    }

    private companion object {
        /** The engine's message of ObjectList::del_subobject_from_object() for the last solid part. */
        const val LAST_SOLID_PART = "delete_last_solid_part"
    }
}
