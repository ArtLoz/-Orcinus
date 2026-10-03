package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeManipulation
import app.orcinus.shadow.core.model.inverse
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.times
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * GLCanvas3D::do_move(), do_rotate() and do_scale() of a volume
 * (Selection::Volume): the volume (ModelObject::volumes) of the object of the
 * copy takes a new transformation in the object, which the move, rotation or
 * scale gizmo or its window gave it, and the engine drops the object's copies
 * onto the plate as the manipulation lets them, a step of Undo. The object
 * list keeps the volume selected. A transformation the volume has already
 * changes nothing.
 */
class PlaceObjectVolumeUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /** The volume [volume] of the object of [copy] takes [matrix] in the object's coordinates. */
    operator fun invoke(copy: PlateInstanceId, volume: Int, matrix: Transform3, manipulation: VolumeManipulation) {
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(copy.mesh)
            val before = target?.volumeAt(volume)?.placement
            if (state.busy || profiles == null || target == null || target.placing || before == null || before == matrix) return@update state
            request = state.objects to profiles
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles) = request ?: return
        applicationScope.launch {
            val index = plate.indexOfFirst { it.mesh == copy.mesh }
            val target = plate[index]
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                inspector.placeVolume(plate.map { it.placed() }, index, volume, matrix, manipulation, profiles, prefix)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                repository.update { it.copy(editing = false) }
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            val placed = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(target)
            var applied = false
            repository.update { state ->
                applied = false
                val done = state.copy(editing = false, plateNotices = state.plateNotices + outcome.notices)
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    placed == null || state.objects.withMesh(copy.mesh) == null -> done
                    else -> {
                        applied = true
                        done.recorded().copy(
                            objects = done.objects.replaced(copy.mesh, placed),
                            selectedInstances = done.selectedInstances.mapTo(LinkedHashSet()) { id ->
                                if (id.mesh == copy.mesh) PlateInstanceId(placed.mesh, id.instance) else id
                            },
                            selectedPart = done.selectedPart?.let { part -> if (part.mesh == copy.mesh) ObjectPartId(placed.mesh, part.index) else part },
                            result = null,
                        )
                    }
                }
            }
            if (!applied) sceneFiles.deleteImport(prefix)
        }
    }

    /**
     * The volume [volume] of the object of [copy] changed by [change] in the
     * world, about the world's origin, as a gizmo moved, turned or scaled it
     * where the copy stands: in the object, P⁻¹ · change · P · M.
     */
    fun changedInWorld(copy: PlateInstanceId, volume: Int, change: Transform3, manipulation: VolumeManipulation) {
        val target = repository.state.value.objects.withMesh(copy.mesh) ?: return
        val placement = target.instances.getOrNull(copy.instance)?.inspection?.placement ?: return
        val before = target.volumeAt(volume)?.placement ?: return
        invoke(copy, volume, placement.inverse() * change * placement * before, manipulation)
    }
}
