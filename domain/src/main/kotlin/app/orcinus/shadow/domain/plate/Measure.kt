package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.MeasureHoverOutcome
import app.orcinus.shadow.core.model.MeasureOutcome
import app.orcinus.shadow.core.model.MeasureRay
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.MeasuredVolume
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateMeasurer

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
