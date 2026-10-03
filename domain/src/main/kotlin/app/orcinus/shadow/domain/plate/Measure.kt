package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AssemblyAction
import app.orcinus.shadow.core.model.MeasureHoverOutcome
import app.orcinus.shadow.core.model.MeasureOutcome
import app.orcinus.shadow.core.model.MeasureRay
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.MeasureEditOutcome
import app.orcinus.shadow.core.model.MeasuredVolume
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateMeasurer
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException

/**
 * What the measuring tool is open on: the plate as the engine loads it, the
 * selected volumes on it, and the presets.
 */
data class MeasureTarget(
    val plate: List<PlacedModel>,
    val volumes: List<MeasuredVolume>,
    val profiles: SlicingProfileSelection,
)

/**
 * OrcaSlicer's measuring tool (GLGizmoMeasure): while it is open the engine
 * keeps the features of the selected volumes and the tool's two selections.
 * The tool opens anew whenever the selection or the plate changes, as
 * data_changed() registers the volumes again and resets the selections.
 */
class MeasureUseCase(
    private val measurer: PlateMeasurer,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /** What the engine's session is open on; null while none is. */
    private var opened: MeasureTarget? = null

    /**
     * GLGizmoMeasure::on_is_activable(): what the tool measures in [state],
     * the selected volumes; null while nothing is selected or the plate can
     * change.
     */
    fun targetOf(state: PlateState = repository.state.value): MeasureTarget? {
        if (state.busy) return null
        val profiles = state.profiles ?: return null
        val volumes = state.measuredVolumes()
        if (volumes.isEmpty()) return null
        return MeasureTarget(state.objects.map { it.placed() }, volumes, profiles)
    }

    /**
     * register_single_mesh_pick() and reset_all_feature(): the engine opens on
     * [target] unless it is open on it already, or [again] for a caller that
     * has not seen it open. Null when nothing changed.
     */
    suspend fun open(target: MeasureTarget, again: Boolean = false): MeasureOutcome? {
        if (opened == target && !again) return null
        opened = null
        val outcome = measurer.beginMeasure(target.plate, target.volumes, target.profiles)
        if (outcome is MeasureOutcome.Success) opened = target
        return outcome
    }

    /** on_render(): what is under the finger along [ray]. */
    suspend fun hover(ray: MeasureRay): MeasureHoverOutcome = measurer.hoverMeasure(ray)

    /** on_mouse(): a tap along [ray] selects or deselects what is there. */
    suspend fun select(ray: MeasureRay): MeasureOutcome = measurer.selectMeasure(ray)

    /** The window's reset buttons and Delete: reset_feature1(), reset_feature2() or reset_all_feature(). */
    suspend fun reset(reset: MeasureReset): MeasureOutcome = measurer.resetMeasure(reset)

    /**
     * The dimensioning's "Edit to scale" (perform_scale()): the selection
     * scaled by [ratio] after the snapshot "Scale", and the tool's selections
     * following it; the tool is not opened anew for the change it made itself
     * (m_pending_scale). Null when nothing was scaled.
     */
    suspend fun scale(ratio: Double): MeasureOutcome? =
        edit { target, prefix -> measurer.scaleMeasure(target.plate, ratio, target.profiles, prefix) }

    /**
     * The assembly tool's [action] with its [values] ("MoveInMeasure",
     * "RotateInMeasure" and the other snapshots of GLGizmoMeasure's
     * set_distance() and kin), the selections following the volumes. Null
     * when nothing changed.
     */
    suspend fun assemble(action: AssemblyAction, values: List<Double> = emptyList()): MeasureOutcome? =
        edit { target, prefix -> measurer.assembleMeasure(target.plate, action, values, target.profiles, prefix) }

    /**
     * An edit the engine makes to the volumes the tool is open on: the objects
     * that changed take their places after a snapshot, the selection follows
     * their new mesh files, and the engine's session, which follows the
     * change already, is not opened anew for it.
     */
    private suspend fun edit(call: suspend (MeasureTarget, ScenePath) -> MeasureEditOutcome): MeasureOutcome? {
        val state = repository.state.value
        val target = opened?.takeIf { it == targetOf(state) } ?: return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = try {
            call(target, prefix)
        } catch (cancellation: CancellationException) {
            sceneFiles.deleteImport(prefix)
            throw cancellation
        } catch (error: Exception) {
            MeasureEditOutcome.Failure(error.message.orEmpty())
        }
        val edited = (outcome as? MeasureEditOutcome.Success)?.edit
        val written = edited?.objects as? ModelLoadOutcome.Success
        if (edited == null || written == null || written.objects.size != edited.objectIndexes.size) {
            sceneFiles.deleteImport(prefix)
            val message = (outcome as? MeasureEditOutcome.Failure)?.message ?: (edited?.objects as? ModelLoadOutcome.Failure)?.message
            repository.update { it.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, message)) }
            return null
        }
        if (written.objects.isEmpty()) {
            sceneFiles.deleteImport(prefix)
            return MeasureOutcome.Success(edited.measurement)
        }
        val sources = edited.objectIndexes.map { state.objects[it] }
        var applied = false
        repository.update { current ->
            applied = false
            if (sources.any { current.objects.withMesh(it.mesh) == null }) return@update current
            val made = written.objects.mapIndexed { index, loaded -> loaded.toPlateObjectOf(sources[index]) }
            val meshes = sources.map { it.mesh }.zip(made.map { it.mesh }).toMap()
            val objects = sources.indices.fold(current.objects) { objects, index -> objects.replaced(sources[index].mesh, made[index]) }
            val changed = current.recorded().copy(
                objects = objects,
                selectedInstances = current.selectedInstances.mapTo(LinkedHashSet()) { id -> meshes[id.mesh]?.let { PlateInstanceId(it, id.instance) } ?: id },
                selectedPart = current.selectedPart?.let { part -> meshes[part.mesh]?.let { ObjectPartId(it, part.index) } ?: part },
                result = null,
            )
            // The engine's session already measures the changed volumes.
            opened = targetOf(changed)
            applied = true
            changed
        }
        if (!applied) {
            sceneFiles.deleteImport(prefix)
            return null
        }
        return MeasureOutcome.Success(edited.measurement)
    }

    /** The tool closes, and the engine lets the volumes go. */
    suspend fun end() {
        if (opened == null) return
        opened = null
        measurer.endMeasure()
    }
}

/**
 * Selection::get_volume_idxs() as the measuring tool takes it: the selected
 * part of each selected copy, or all of each copy's volumes, by their indexes
 * on the plate.
 */
fun PlateState.measuredVolumes(): List<MeasuredVolume> = selectedInstances.mapNotNull { copy ->
    val objectIndex = objects.indexOfFirst { it.mesh == copy.mesh }
    if (objectIndex < 0 || copy.instance !in objects[objectIndex].instances.indices) return@mapNotNull null
    MeasuredVolume(objectIndex, copy.instance, selectedPart?.takeIf { it.mesh == copy.mesh }?.index)
}.sortedWith(compareBy(MeasuredVolume::objectIndex, MeasuredVolume::instanceIndex))
