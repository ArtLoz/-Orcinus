package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.MeasureHoverOutcome
import app.orcinus.shadow.core.model.MeasureOutcome
import app.orcinus.shadow.core.model.MeasureRay
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.MeasuredVolume
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.SlicingProfileSelection

/**
 * OrcaSlicer's measuring tool (GLGizmoMeasure): the engine keeps a
 * Measure::Measuring of every measured volume and the tool's two selections
 * from [beginMeasure] to [endMeasure]; every other call works on them.
 */
interface PlateMeasurer {
    /** The tool opens on the [volumes] of [plate], with the presets of [profiles]. */
    suspend fun beginMeasure(plate: List<PlacedModel>, volumes: List<MeasuredVolume>, profiles: SlicingProfileSelection): MeasureOutcome

    /** on_render(): what is under the finger along [ray]. */
    suspend fun hoverMeasure(ray: MeasureRay): MeasureHoverOutcome

    /** on_mouse() for a left press along [ray]: the feature or centre there is selected or deselected. */
    suspend fun selectMeasure(ray: MeasureRay): MeasureOutcome

    /** reset_feature1(), reset_feature2() or reset_all_feature(). */
    suspend fun resetMeasure(reset: MeasureReset): MeasureOutcome

    suspend fun endMeasure()
}
