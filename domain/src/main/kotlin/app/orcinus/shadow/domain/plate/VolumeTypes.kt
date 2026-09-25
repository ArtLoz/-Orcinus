package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The part menu's "Change type" (ObjectList::set_volume_type): the volume
 * takes another type, the object's volumes are sorted by type, and the list
 * selects the volume in its new place, as one step of Undo ("Change part
 * type"). OrcaSlicer refuses to change the object's last solid part; its
 * message box says so.
 */
class ChangeVolumeTypeUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(id: ObjectPartId, type: VolumeType) {
        var profiles: SlicingProfileSelection? = null
        repository.update { state ->
            profiles = null
            val target = state.objects.withMesh(id.mesh)
            val volume = target?.volumeAt(id.index)
            if (state.busy || state.profiles == null || volume == null || volume.type == type || target.placing) return@update state
            profiles = state.profiles
            state.copy(editing = true, problem = null)
        }
        val selection = profiles ?: return
        applicationScope.launch {
            val plate = repository.state.value.objects
            val index = plate.indexOfFirst { it.mesh == id.mesh }
            val old = plate.getOrNull(index)
            val prefix = sceneFiles.newImportPrefix()
            val outcome = if (old == null) {
                ModelLoadOutcome.Failure("The object is not on the plate")
            } else {
                try {
                    inspector.setVolumeType(plate.map { it.placed() }, index, id.index, type, selection, prefix)
                } catch (cancellation: CancellationException) {
                    sceneFiles.deleteImport(prefix)
                    repository.update { it.copy(editing = false) }
                    throw cancellation
                } catch (error: Exception) {
                    ModelLoadOutcome.Failure(error.message.orEmpty())
                }
            }
            val success = outcome as? ModelLoadOutcome.Success
            val changed = old?.let { success?.objects?.singleOrNull()?.toPlateObjectOf(it) }
            if (changed == null) sceneFiles.deleteImport(prefix)
            repository.update { state ->
                val done = state.copy(editing = false, plateNotices = state.plateNotices + outcome.notices)
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    changed == null || state.objects.withMesh(id.mesh) == null -> done
                    else -> done.recorded().copy(
                        objects = state.objects.map { if (it.mesh == id.mesh) changed else it },
                        selectedInstances = setOf(PlateInstanceId(changed.mesh)),
                        selectedPart = success?.selectedVolume?.takeIf { it >= 0 }?.let { ObjectPartId(changed.mesh, it) },
                        selectedRange = null,
                        result = null,
                    )
                }
            }
        }
    }
}
