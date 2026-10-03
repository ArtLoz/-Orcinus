package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.LayerEditingOutcome
import app.orcinus.shadow.core.model.LayerHeightEdit
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withLayerHeightProfile
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.LayerHeightEditor

/**
 * What the variable layer height bar is open on: the object with the [mesh]
 * file as the engine loads it, without its profile, the presets and the
 * plate's settings it is sliced with, and the object's own [profile].
 */
data class LayerEditingTarget(
    val mesh: ScenePath,
    val placed: PlacedModel,
    val profiles: SlicingProfileSelection,
    val plateSettings: ModelSettings,
    val profile: List<Double>,
)

/**
 * OrcaSlicer's variable layer height (GLCanvas3D::LayersEditing): while it is
 * on, the engine keeps the selected object and the profile being edited. A
 * press on the bar changes the profile there; the object takes it once the
 * finger lets go (accept_changes()), and at once from Adaptive, Smooth and
 * Reset, each with the desktop app's snapshot ([commit]).
 */
class EditLayerHeightsUseCase(
    private val editor: LayerHeightEditor,
    private val repository: PlateRepository,
) {
    /** What the engine's session is open on, with the profile the object has from it; null while none is. */
    private var opened: LayerEditingTarget? = null

    /**
     * The toolbar's "Variable layer height" (Plater::priv::on_action_layersediting()):
     * the bar opens on the selected object, or closes. It opens only where
     * layers_height_allowed() lets it.
     */
    fun enable(on: Boolean) = repository.update { state ->
        when {
            state.layerEditing == on -> state
            on && state.layerEditingObject() == null -> state
            else -> state.copy(layerEditing = on)
        }
    }

    /**
     * ObjectList::enable_layers_editing(): a tap on the variable layer height
     * mark of an object's row selects the object, as the row does, and opens
     * the bar on it.
     */
    fun enableFor(mesh: ScenePath) = repository.update { state ->
        val target = state.objects.withMesh(mesh) ?: return@update state
        val selected = state.copy(selectedInstances = listOf(target).allCopies(), selectedPart = null, selectedRange = null)
        if (selected.layerEditingObject() == null) state else selected.copy(layerEditing = true)
    }

    /** What the bar edits in [state]: the selected object while the bar is on and the plate can change; null otherwise. */
    fun targetOf(state: PlateState): LayerEditingTarget? {
        if (!state.layerEditing || state.busy) return null
        val profiles = state.profiles ?: return null
        val target = state.layerEditingObject() ?: return null
        return LayerEditingTarget(target.mesh, target.placed().copy(layerHeightProfile = emptyList()), profiles, state.plateSettings, target.layerHeightProfile)
    }

    /**
     * LayersEditing::select_object() and set_config(): the engine opens on
     * [target] unless it is open on it already, as the desktop bar starts
     * anew on another object, another height or other presets, or [again]
     * for a caller that has not seen it open. Null when nothing changed.
     */
    suspend fun open(target: LayerEditingTarget, again: Boolean = false): LayerEditingOutcome? {
        if (opened == target && !again) return null
        opened = null
        val state = repository.state.value
        val index = state.objects.indexOfFirst { it.mesh == target.mesh }
        if (index < 0) return LayerEditingOutcome.Failure("The object is not on the plate")
        val outcome = editor.begin(state.objects.map { it.placed() }, index, target.profiles, target.plateSettings)
        if (outcome is LayerEditingOutcome.Success) opened = target
        return outcome
    }

    /** adjust_layer_height_profile(): a press of [action] on the bar at [z]. */
    suspend fun edit(action: LayerHeightEdit, z: Double, strength: Double, bandWidth: Double): LayerEditingOutcome =
        editor.edit(action, z, strength, bandWidth)

    /** LayersEditing::accept_changes(): the finger let go; the object takes the profile from [commit]. */
    suspend fun accept(): LayerEditingOutcome = editor.accept()

    /** LayersEditing::adaptive_layer_height_profile() at [quality]. */
    suspend fun adaptive(quality: Double): LayerEditingOutcome = editor.adaptive(quality)

    /** LayersEditing::smooth_layer_height_profile() */
    suspend fun smooth(radius: Int, keepMin: Boolean): LayerEditingOutcome = editor.smooth(radius, keepMin)

    /** LayersEditing::reset_layer_height_profile(): the object has no profile of its own any more ([commit] an empty one). */
    suspend fun reset(): LayerEditingOutcome = editor.reset()

    /** The bar closes, and the engine lets the object go. */
    suspend fun end() {
        if (opened == null) return
        opened = null
        editor.end()
    }

    /**
     * Plater::take_snapshot() of "Variable layer height - Manual edit",
     * "- Adaptive", "- Smooth all" or "- Reset", and the object with
     * [profile]; a profile the object already has changes nothing. The
     * session knows the profile first, so the bar does not open anew for it.
     */
    fun commit(mesh: ScenePath, profile: List<Double>) {
        opened = opened?.takeIf { it.mesh == mesh }?.copy(profile = profile)
        repository.update { state ->
            val target = state.objects.withMesh(mesh)
            if (target == null || target.layerHeightProfile == profile) return@update state
            state.recorded().copy(objects = state.objects.replaced(target.withLayerHeightProfile(profile)), result = null)
        }
    }
}

/**
 * Plater::priv::layers_height_allowed(): the selection is one object, whose
 * copies stand above the bed somewhere (ModelObject::max_z() over
 * SINKING_Z_THRESHOLD); null while the bar cannot edit any.
 */
fun PlateState.layerEditingObject(): PlateObject? {
    val mesh = selectedInstances.map { it.mesh }.distinct().singleOrNull() ?: return null
    val target = objects.withMesh(mesh) ?: return null
    val top = target.instances.maxOfOrNull { it.inspection.boxCenter.z + it.inspection.dimensions.heightMillimeters / 2 } ?: return null
    return target.takeIf { top > SINKING_Z_THRESHOLD }
}

/** SINKING_Z_THRESHOLD of Model.hpp */
private const val SINKING_Z_THRESHOLD = -0.001
