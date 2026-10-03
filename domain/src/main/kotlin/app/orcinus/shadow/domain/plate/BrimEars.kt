package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BrimEarHit
import app.orcinus.shadow.core.model.BrimEarsOutcome
import app.orcinus.shadow.core.model.BrimPoint
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withBrimPoints
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.BrimEarsEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the brim ears tool is open on: the copy, and the plate as the engine
 * loads it, without the objects' ears, which do not change the first layer.
 */
data class BrimEarsTarget(val copy: PlateInstanceId, val plate: List<PlacedModel>, val profiles: SlicingProfileSelection)

/**
 * OrcaSlicer's brim ears tool (GLGizmoBrimEars): while it is open the engine
 * keeps the first layer and the model parts of the selected copy; the ears
 * are the object's (ModelObject::brim_points), which every change the tool
 * makes replaces with the desktop app's snapshot.
 */
class EditBrimEarsUseCase(
    private val editor: BrimEarsEditor,
    private val repository: PlateRepository,
) {
    /** What the engine's session is open on; null while none is. */
    private var opened: BrimEarsTarget? = null

    /**
     * GLGizmoBrimEars::on_is_activable(): the copy the tool works on, the one
     * selected whole; null without one.
     */
    fun copyOf(state: PlateState): PlateInstanceId? =
        state.selectedInstances.singleOrNull()?.takeIf { copy -> state.selectedPart == null && state.objects.withMesh(copy.mesh) != null }

    /** What the tool opens on in [state] for [copy]; null while the plate can change. */
    fun targetOf(state: PlateState, copy: PlateInstanceId): BrimEarsTarget? {
        if (state.busy) return null
        val profiles = state.profiles ?: return null
        if (state.objects.withMesh(copy.mesh)?.instances?.getOrNull(copy.instance) == null) return null
        return BrimEarsTarget(copy, state.objects.map { it.placed().copy(brimPoints = emptyList()) }, profiles)
    }

    /**
     * on_set_state() turning the tool on, and data_changed(): the engine
     * slices the copy's first layer unless it is open on it already, or
     * [again] for a caller that has not seen it open. Null when nothing
     * changed.
     */
    suspend fun open(target: BrimEarsTarget, again: Boolean = false): BrimEarsOutcome? {
        if (opened == target && !again) return null
        opened = null
        val index = target.plate.indexOfFirst { it.mesh == target.copy.mesh }
        if (index < 0) return BrimEarsOutcome.Failure("The object is not on the plate")
        val outcome = editor.beginBrimEars(target.plate, index, target.copy.instance, target.profiles)
        if (outcome is BrimEarsOutcome.Success) opened = target
        return outcome
    }

    /** unproject_on_mesh2(): where a ray hits the copy, and the ear a press there places. */
    suspend fun hit(origin: Vector3, direction: Vector3): BrimEarHit? = editor.hitBrimEars(origin, direction)

    /** auto_generate(): [points] with the ears along the first layer's corners added. */
    suspend fun generate(points: List<BrimPoint>, maxAngle: Double, detectionRadius: Double, headDiameter: Double): List<BrimPoint> =
        editor.generateBrimEars(points, maxAngle, detectionRadius, headDiameter)

    /** find_single(): the ears that touch neither the first layer nor an ear that does. */
    suspend fun invalid(points: List<BrimPoint>): List<Int> = editor.checkBrimEars(points)

    /**
     * update_model_object() after the snapshot the change takes ("Add brim
     * ear", "Delete brim ear", "Move support point", "Change point head
     * diameter", "Auto generate brim ear"): the object's ears replaced.
     */
    fun commit(mesh: ScenePath, points: List<BrimPoint>) = repository.update { state ->
        val target = state.objects.withMesh(mesh)
        if (state.busy || target == null || target.brimPoints == points) return@update state
        state.recorded().copy(objects = state.objects.replaced(target.withBrimPoints(points)), result = null)
    }

    /** The tool closes, and the engine lets the copy go. */
    suspend fun end() {
        if (opened == null) return
        opened = null
        editor.endBrimEars()
    }
}

/**
 * "Set the brim type of this object to "painted"" of the brim ears tool's
 * warning: the object's brim becomes "painted", so its ears take effect. The
 * desktop app sets the object's config without a step of Undo; the settings
 * shown follow.
 */
class EnablePaintedBrimUseCase(
    private val repository: PlateRepository,
    private val settingsTabs: PresetSettingsTabs,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath) {
        var changed = false
        repository.update { state ->
            val target = state.objects.withMesh(mesh)
            if (state.busy || target == null) return@update state
            changed = true
            state.copy(
                objects = state.objects.replaced(target.withSettings(ModelSettings(target.settings.values + (BRIM_TYPE to BRIM_TYPE_PAINTED)))),
                result = null,
            )
        }
        if (changed) applicationScope.launch { settingsTabs.refresh() }
    }
}

/** The brim_type option, and btPainted as OrcaSlicer writes it into a project. */
private const val BRIM_TYPE = "brim_type"
private const val BRIM_TYPE_PAINTED = "painted"
