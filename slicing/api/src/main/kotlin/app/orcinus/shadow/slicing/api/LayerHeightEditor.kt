package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.LayerEditingOutcome
import app.orcinus.shadow.core.model.LayerHeightEdit
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.SlicingProfileSelection

/**
 * The variable layer height of OrcaSlicer's 3D view (GLCanvas3D::LayersEditing):
 * the engine keeps the object being edited, its slicing parameters and the
 * profile from [begin] to [end]; every other call works on that object.
 */
interface LayerHeightEditor {
    /** Opens on the object at [index] of [plate], with the presets of [profiles] and the plate's settings. */
    suspend fun begin(plate: List<PlacedModel>, index: Int, profiles: SlicingProfileSelection, plateSettings: ModelSettings): LayerEditingOutcome

    /** adjust_layer_height_profile(): a press on the bar at [z], the band [bandWidth] around it changed by [strength]. */
    suspend fun edit(action: LayerHeightEdit, z: Double, strength: Double, bandWidth: Double): LayerEditingOutcome

    /** layer_height_profile_adaptive() at [quality], 0 for the fastest and 1 for the finest. */
    suspend fun adaptive(quality: Double): LayerEditingOutcome

    /** smooth_height_profile() */
    suspend fun smooth(radius: Int, keepMin: Boolean): LayerEditingOutcome

    /** The plain profile again. */
    suspend fun reset(): LayerEditingOutcome

    /** accept_changes(): the profile edited on the bar becomes the object's. */
    suspend fun accept(): LayerEditingOutcome

    suspend fun end()
}
