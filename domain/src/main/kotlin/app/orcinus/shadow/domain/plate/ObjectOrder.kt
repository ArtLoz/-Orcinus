package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ObjectList::OnDrop(): a row of the object list dropped on another takes its
 * place, the rows between moving by one ("Object order changed"). An object
 * goes onto an object standing on the same plate; a volume onto a volume of
 * its object that may trade places with it ([canMoveVolume]). The parts move
 * in the plate's own list; the object's own mesh moves through the engine,
 * which writes the object anew. The moved row is selected.
 */
class ObjectOrderUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    fun moveObject(from: ScenePath, to: ScenePath) = repository.update { state ->
        if (state.busy || !state.canMoveObject(from, to)) return@update state
        val fromIndex = state.objects.indexOfFirst { it.mesh == from }
        val toIndex = state.objects.indexOfFirst { it.mesh == to }
        val objects = state.objects.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        state.recorded().copy(
            objects = objects,
            selectedInstances = setOf(PlateInstanceId(from)),
            selectedPart = null,
            selectedRange = null,
            result = null,
        )
    }

    /** The volume [from] (ObjectPartId.index) of the object with the [mesh] file dropped on its volume [to]. */
    fun moveVolume(mesh: ScenePath, from: Int, to: Int) {
        if (from > 0 && to > 0) {
            moveParts(mesh, from, to)
        } else {
            moveOwnVolume(mesh, from, to)
        }
    }

    private fun moveParts(mesh: ScenePath, from: Int, to: Int) = repository.update { state ->
        val target = state.objects.withMesh(mesh)
        if (state.busy || target == null || !target.canMoveVolume(from, to)) return@update state
        val parts = target.parts.toMutableList().apply { add(to - 1, removeAt(from - 1)) }
        // The volumes the painting lies on follow their parts.
        val order = (0..target.parts.size).toMutableList().apply { add(to, removeAt(from)) }
        val meshes = target.paintedMeshes.map { painted -> painted.copy(volume = order.indexOf(painted.volume).takeIf { it >= 0 } ?: painted.volume) }
        state.recorded().copy(
            objects = state.objects.replaced(target.withParts(parts).withPainted(target.painted, meshes)),
            selectedInstances = setOf(PlateInstanceId(mesh)),
            selectedPart = ObjectPartId(mesh, to),
            selectedPartGroup = emptySet(),
            selectedRange = null,
            result = null,
        )
    }

    private fun moveOwnVolume(mesh: ScenePath, from: Int, to: Int) {
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(mesh)
            if (state.busy || profiles == null || target == null || target.placing || !target.canMoveVolume(from, to)) return@update state
            request = state.objects to profiles
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles) = request ?: return
        applicationScope.launch {
            val index = plate.indexOfFirst { it.mesh == mesh }
            val target = plate[index]
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                inspector.moveVolume(plate.map { it.placed() }, index, from, to, profiles, prefix)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                repository.update { it.copy(editing = false) }
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            val written = (outcome as? ModelLoadOutcome.Success)?.objects.orEmpty().map { it.toPlateObjectOf(target) }.singleOrNull()
            var changed = false
            repository.update { state ->
                val done = state.copy(editing = false, plateNotices = state.plateNotices + outcome.notices)
                val at = state.objects.indexOfFirst { it.mesh == mesh }
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    written == null || at < 0 -> done
                    else -> {
                        changed = true
                        done.recorded().copy(
                            objects = done.objects.mapIndexed { position, it -> if (position == at) written else it },
                            selectedInstances = setOf(PlateInstanceId(written.mesh)),
                            selectedPart = (outcome as ModelLoadOutcome.Success).selectedVolume?.let { ObjectPartId(written.mesh, it) },
                            selectedRange = null,
                            result = null,
                        )
                    }
                }
            }
            if (!changed) sceneFiles.deleteImport(prefix)
        }
    }
}

/**
 * ObjectList::can_drop() of an object: the object with the [to] file stands on
 * the plate the object with the [from] file stands on, by their first copies.
 */
fun PlateState.canMoveObject(from: ScenePath, to: ScenePath): Boolean {
    if (from == to) return false
    val dragged = objects.withMesh(from)?.instances?.firstOrNull() ?: return false
    val target = objects.withMesh(to)?.instances?.firstOrNull() ?: return false
    val plate = plateOf(dragged) ?: return false
    return plateOf(target) == plate
}

/**
 * ObjectList::can_drop() of a volume of the object: the volume [from]
 * (ObjectPartId.index) may take the place of the volume [to]. Volumes trade
 * places with volumes of their kind alone; a model part moves while the
 * object has another one, onto the first volume or with a model part first;
 * negative parts, modifiers, blockers and enforcers move among their own.
 */
fun PlateObject.canMoveVolume(from: Int, to: Int): Boolean {
    if (from == to) return false
    val dragged = volumeAt(from)?.type ?: return false
    val target = volumeAt(to)?.type ?: return false
    if (dragged == target && dragged != VolumeType.PART) return true
    if (dragged != target) return false
    val solids = (0..parts.size).count { volumeAt(it)?.type == VolumeType.PART }
    if (solids < 2) return false
    return to == 0 ||
        (from == 0 && volumeAt(1)?.type == VolumeType.PART) ||
        (from != 0 && volumeAt(0)?.type == VolumeType.PART)
}
