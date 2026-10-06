package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.SettingsDialog
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
    /**
     * The volume [volume] of the object of [copy] takes [matrix] in the
     * object's coordinates; in the assembly view ([inAssembly]) no copy drops.
     * [gizmoAction]: the window's values, whose snapshot is a GizmoAction.
     */
    operator fun invoke(
        copy: PlateInstanceId,
        volume: Int,
        matrix: Transform3,
        manipulation: VolumeManipulation,
        inAssembly: Boolean = false,
        gizmoAction: Boolean = false,
    ) {
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
                inspector.placeVolume(plate.map { it.placed() }, index, volume, matrix, manipulation, profiles, prefix, inAssembly)
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
                        done.recorded(gizmoAction = gizmoAction).copy(
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
     * do_move() of several volumes of one object selected together
     * (Selection::Volume): [changes] give each volume its matrix in the
     * object's coordinates, which the engine takes in turn; the plate takes
     * the object they leave as one step of Undo, the volumes still selected.
     */
    fun placeAll(copy: PlateInstanceId, changes: List<Pair<Int, Transform3>>, manipulation: VolumeManipulation) {
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(copy.mesh)
            if (state.busy || profiles == null || target == null || target.placing || changes.isEmpty()) return@update state
            request = state.objects to profiles
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles) = request ?: return
        applicationScope.launch {
            var objects = plate
            var current = plate.first { it.mesh == copy.mesh }
            val notices = mutableListOf<SettingsDialog>()
            for ((volume, matrix) in changes) {
                if (current.volumeAt(volume)?.placement == matrix) continue
                val index = objects.indexOfFirst { it.mesh == current.mesh }
                val prefix = sceneFiles.newImportPrefix()
                val outcome = try {
                    inspector.placeVolume(objects.map { it.placed() }, index, volume, matrix, manipulation, profiles, prefix, false)
                } catch (cancellation: CancellationException) {
                    sceneFiles.deleteImport(prefix)
                    repository.update { it.copy(editing = false) }
                    throw cancellation
                } catch (error: Exception) {
                    ModelLoadOutcome.Failure(error.message.orEmpty())
                }
                notices += outcome.notices
                val placed = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(current)
                if (placed == null) {
                    sceneFiles.deleteImport(prefix)
                    repository.update { state ->
                        val done = state.copy(editing = false, plateNotices = state.plateNotices + notices)
                        if (outcome is ModelLoadOutcome.Failure) done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message)) else done
                    }
                    return@launch
                }
                objects = objects.replaced(current.mesh, placed)
                current = placed
            }
            val placed = current
            repository.update { state ->
                val done = state.copy(editing = false, plateNotices = state.plateNotices + notices)
                if (placed.mesh == copy.mesh || state.objects.withMesh(copy.mesh) == null) return@update done
                fun ObjectPartId.moved() = if (mesh == copy.mesh) ObjectPartId(placed.mesh, index) else this
                done.recorded().copy(
                    objects = done.objects.replaced(copy.mesh, placed),
                    selectedInstances = done.selectedInstances.mapTo(LinkedHashSet()) { id ->
                        if (id.mesh == copy.mesh) PlateInstanceId(placed.mesh, id.instance) else id
                    },
                    selectedPart = done.selectedPart?.moved(),
                    selectedPartGroup = done.selectedPartGroup.mapTo(LinkedHashSet()) { it.moved() },
                    result = null,
                )
            }
        }
    }

    /**
     * The volume [volume] of the object of [copy] changed by [change] in the
     * world, about the world's origin, as a gizmo moved, turned or scaled it
     * where the copy stands: in the object, P⁻¹ · change · P · M. In the
     * assembly view the copy stands at its assemble transformation ([assemble],
     * the instance transformation of the assembly view's volumes).
     */
    fun changedInWorld(
        copy: PlateInstanceId,
        volume: Int,
        change: Transform3,
        manipulation: VolumeManipulation,
        assemble: Transform3? = null,
        gizmoAction: Boolean = false,
    ) {
        val target = repository.state.value.objects.withMesh(copy.mesh) ?: return
        val placement = assemble ?: target.instances.getOrNull(copy.instance)?.inspection?.placement ?: return
        val before = target.volumeAt(volume)?.placement ?: return
        invoke(copy, volume, placement.inverse() * change * placement * before, manipulation, inAssembly = assemble != null, gizmoAction = gizmoAction)
    }
}
