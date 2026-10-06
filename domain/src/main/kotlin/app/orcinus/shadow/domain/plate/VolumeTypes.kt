package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.SettingsDialog
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

    /**
     * ObjectList::set_volume_type() of several volumes of one object: none
     * of them a text or an SVG for a support blocker or enforcer, and not
     * every solid part of the object, which OrcaSlicer refuses with its
     * message; they take [type] as one step of Undo ("Change part type"), the
     * volumes are sorted by type, and the list selects them in their new
     * places. The engine changes one volume at a time: those of a lower type
     * from the last on and those of a higher one from the first on, so that
     * each sorting leaves them in the order OrcaSlicer's single sort does.
     */
    fun all(ids: List<ObjectPartId>, type: VolumeType) {
        if (ids.size < 2) return ids.firstOrNull()?.let { invoke(it, type) } ?: Unit
        val mesh = ids.first().mesh
        var request: Pair<SlicingProfileSelection, List<VolumeType>>? = null
        repository.update { state ->
            request = null
            val target = state.objects.withMesh(mesh)
            val profiles = state.profiles
            if (state.busy || profiles == null || target == null || target.placing || ids.any { it.mesh != mesh }) return@update state
            val types = (0..target.parts.size).map { target.volumeAt(it)?.type ?: VolumeType.PART }
            val parts = ids.mapNotNull { target.volumeAt(it.index) }
            val support = type == VolumeType.SUPPORT_BLOCKER || type == VolumeType.SUPPORT_ENFORCER
            if (parts.size != ids.size || (support && parts.any { it.emboss != null }) || parts.none { it.type != type }) return@update state
            if (type != VolumeType.PART && parts.count { it.type == VolumeType.PART } == types.count { it == VolumeType.PART }) {
                return@update state.copy(plateNotices = state.plateNotices + LAST_SOLID_PART)
            }
            request = profiles to types
            state.copy(editing = true, problem = null)
        }
        val (profiles, before) = request ?: return
        val targets = ids.map { it.index }.distinct()
        val lower = targets.filter { before[it].ordinal < type.ordinal }.sortedDescending()
        val higher = targets.filter { before[it].ordinal > type.ordinal }.sorted()
        applicationScope.launch {
            // Where every volume is, by its place before the change; ModelObject::sort_volumes(true) is a stable sort by type.
            val types = before.toMutableList()
            var order = before.indices.toList()
            var current = mesh
            var recorded = false
            try {
                for (volume in lower + higher) {
                    val plate = repository.state.value.objects
                    val index = plate.indexOfFirst { it.mesh == current }
                    val old = plate.getOrNull(index) ?: break
                    val prefix = sceneFiles.newImportPrefix()
                    val outcome = try {
                        inspector.setVolumeType(plate.map { it.placed() }, index, order.indexOf(volume), type, profiles, prefix)
                    } catch (cancellation: CancellationException) {
                        sceneFiles.deleteImport(prefix)
                        throw cancellation
                    } catch (error: Exception) {
                        ModelLoadOutcome.Failure(error.message.orEmpty())
                    }
                    val changed = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(old)
                    if (changed == null) {
                        sceneFiles.deleteImport(prefix)
                        repository.update { state ->
                            val informed = state.copy(plateNotices = state.plateNotices + outcome.notices)
                            if (outcome is ModelLoadOutcome.Failure) informed.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message)) else informed
                        }
                        break
                    }
                    types[volume] = type
                    order = order.sortedBy { types[it].ordinal }
                    val selected = targets.map { ObjectPartId(changed.mesh, order.indexOf(it)) }
                    repository.update { state ->
                        if (state.objects.withMesh(current) == null) return@update state
                        (if (recorded) state else state.recorded()).copy(
                            objects = state.objects.map { if (it.mesh == current) changed else it },
                            plateNotices = state.plateNotices + outcome.notices,
                            selectedInstances = setOf(PlateInstanceId(changed.mesh)),
                            selectedPart = selected.first(),
                            selectedPartGroup = selected.toSet(),
                            selectedRange = null,
                            result = null,
                        )
                    }
                    recorded = true
                    current = changed.mesh
                }
            } finally {
                repository.update { it.copy(editing = false) }
            }
        }
    }
}

/** set_volume_type()'s show_error() when every solid part of an object would change. */
private val LAST_SOLID_PART = SettingsDialog(
    id = "last_solid_part",
    icon = DialogIcon.ERROR,
    title = emptyList(),
    text = listOf(OrcaText("The type of the last solid object part is not to be changed.")),
    question = false,
    yes = null,
    no = null,
)
