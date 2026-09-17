package app.orcinus.shadow.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.domain.DescribeFlatteningPlanesUseCase
import app.orcinus.shadow.domain.plate.AddCalibrationCubeToPlateUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import app.orcinus.shadow.render.scene.ObjectTransforms
import app.orcinus.shadow.render.scene.PlateGizmo
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PrepareViewModel(
    observePlate: ObservePlateUseCase,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val addCalibrationCubeToPlate: AddCalibrationCubeToPlateUseCase,
    private val placePlateObject: PlacePlateObjectUseCase,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val setPlateObjectAutoDrop: SetPlateObjectAutoDropUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val describeFlatteningPlanes: DescribeFlatteningPlanesUseCase,
    private val slicePlate: SlicePlateUseCase,
    private val cancelPlateSlicing: CancelPlateSlicingUseCase,
    private val dismissPlateProblem: DismissPlateProblemUseCase,
) : ViewModel() {
    private val plate = observePlate()
    private val view = MutableStateFlow(PrepareViewState())

    val state: StateFlow<PrepareUiState> = combine(plate, view, PlateState::toPrepareUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), plate.value.toPrepareUiState(PrepareViewState()))

    init {
        // Plater::priv::load_files() selects the objects it added to the plate.
        viewModelScope.launch {
            var known: Set<ScenePath>? = null
            plate.collect { state ->
                val added = known?.let { before -> state.objects.lastOrNull { it.inspection.mesh !in before } }
                known = state.objects.mapTo(HashSet()) { it.inspection.mesh }
                if (added != null) select(added.inspection)
            }
        }
        // GLGizmoFlatten::is_plane_update_necessary(): the faces follow the object and its scale.
        viewModelScope.launch {
            combine(plate, view) { plate, view ->
                val target = plate.objects.firstOrNull { view.gizmo == PlateGizmo.LAY_ON_FACE && it.inspection.mesh == view.selectedMesh }
                target?.let { FlatteningKey(it, plate.profiles, it.inspection.dimensions, it.inspection.unscaledDimensions) }
            }
                .distinctUntilChanged { old, new -> old?.sameFaces(new) ?: (new == null) }
                .collectLatest { key ->
                    view.update { it.copy(flatteningPlanes = emptyList()) }
                    if (key == null) return@collectLatest
                    val outcome = describeFlatteningPlanes(key.plateObject, key.profiles)
                    if (outcome is FlatteningPlanesOutcome.Success) view.update { it.copy(flatteningPlanes = outcome.planes) }
                }
        }
    }

    private class FlatteningKey(
        val plateObject: PlateObject,
        val profiles: SlicingProfileSelection,
        val dimensions: ModelDimensions,
        val unscaledDimensions: ModelDimensions,
    ) {
        fun sameFaces(other: FlatteningKey?) = other != null && other.plateObject.inspection.mesh == plateObject.inspection.mesh &&
            other.dimensions == dimensions && other.unscaledDimensions == unscaledDimensions && other.profiles == profiles
    }

    fun addModel(reference: String) = addModelToPlate(ExternalDocumentReference(reference))

    fun addCalibrationCube() = addCalibrationCubeToPlate()

    /** Clearing the selection closes the gizmo, as GLGizmosManager does when it is no longer activable. */
    fun selectObject(index: Int?) = select(index?.let { state.value.sceneObjects.getOrNull(it) }?.inspection)

    private fun select(selected: ModelInspection?) {
        view.update { view ->
            view.copy(
                selectedMesh = selected?.mesh,
                rotationStart = if (selected?.mesh != view.selectedMesh) selected?.placement else view.rotationStart,
                gizmo = if (selected == null) null else view.gizmo,
            )
        }
    }

    /** A gizmo's toolbar icon opens the gizmo, or closes it when it is open. */
    fun toggleGizmo(type: PlateGizmo) {
        val state = state.value
        if (!state.canManipulate) return
        view.update { view ->
            view.copy(
                gizmo = if (state.gizmo == type) null else type,
                rotationStart = state.selectedObject?.let { state.sceneObjects[it].inspection.placement },
                // Another toolbar item closes the arrange options.
                arrangeOptionsOpen = false,
            )
        }
    }

    /** The Arrange toolbar item opens its options window, or closes it; a gizmo closes. */
    fun toggleArrangeOptions() {
        if (!state.value.canArrange) return
        view.update { it.copy(arrangeOptionsOpen = !it.arrangeOptionsOpen, gizmo = null) }
    }

    fun setArrangeSettings(settings: ArrangeSettings) {
        view.update { it.copy(arrangeSettings = settings.copy(distance = settings.distance.coerceIn(0.0, MAX_ARRANGE_DISTANCE))) }
    }

    /** _render_arrange_menu()'s Reset: OrcaSlicer's default arrange settings. */
    fun resetArrangeSettings() {
        view.update { it.copy(arrangeSettings = ArrangeSettings()) }
    }

    /** _render_arrange_menu()'s Arrange: ArrangeJob for every object on the plate. */
    fun arrange() = placePlateObjects(PlateManipulation.Arrange(view.value.arrangeSettings))

    /** The toolbar's OrientJob: the selected object, or every object when none is selected. */
    fun autoOrient() = placePlateObjects(PlateManipulation.AutoOrient(setOfNotNull(state.value.selectedPlateObject?.inspection?.mesh)))

    /**
     * GizmoObjectManipulation::change_rotation_value(): the selected object turns
     * by [degrees] about the world [axis] through its bounding sphere's centre.
     */
    fun rotateBy(axis: Int, degrees: Double) {
        if (degrees == 0.0) return
        val target = selected() ?: return
        placePlateObject(target.mesh, ObjectTransforms.rotated(target.placement, axis, degrees, target.boundingSphere.center), Manipulation.Rotate)
    }

    /** GizmoObjectManipulation::change_absolute_rotation_value(): turns by the difference to the rotation shown. */
    fun setRotation(axis: Int, degrees: Double) {
        val target = selected() ?: return
        rotateBy(axis, degrees - target.rotationDegrees[axis])
    }

    /** GizmoObjectManipulation::reset_rotation_value(true): the rotation from when the tool opened. */
    fun resetRotation() {
        val target = selected() ?: return
        val start = view.value.rotationStart ?: return
        placePlateObject(target.mesh, ObjectTransforms.withLinearPartOf(target.placement, start), Manipulation.Rotate)
    }

    /** GizmoObjectManipulation::reset_rotation_value(false): no rotation. */
    fun resetRotationToZero() {
        val target = selected() ?: return
        placePlateObject(target.mesh, target.placement, Manipulation.ResetRotation)
    }

    fun setUniformScale(uniform: Boolean) {
        view.update { it.copy(uniformScale = uniform) }
    }

    /** GizmoObjectManipulation::change_scale_value(): [percent] of the unscaled size on [axis], or on every axis when uniform. */
    fun setScale(axis: Int, percent: Double) {
        val current = state.value.selectedScale ?: return
        if (percent <= 0.0) return
        val factors = if (view.value.uniformScale) {
            Vector3(percent / 100.0, percent / 100.0, percent / 100.0)
        } else {
            current.with(axis, percent).let { Vector3(it.x / 100.0, it.y / 100.0, it.z / 100.0) }
        }
        scaleTo(factors)
    }

    /** GizmoObjectManipulation::change_size_value(): [millimeters] on [axis], as a scale of the unscaled size. */
    fun setSize(axis: Int, millimeters: Double) {
        val target = selected() ?: return
        if (millimeters <= 0.0) return
        val unscaled = target.unscaledDimensions.vector()
        val size = target.dimensions.vector().with(axis, millimeters.coerceAtMost(MAX_NUM))
        val factors = Vector3(size.x / unscaled.x, size.y / unscaled.y, size.z / unscaled.z)
        scaleTo(if (view.value.uniformScale) factors[axis].let { Vector3(it, it, it) } else factors)
    }

    /** GizmoObjectManipulation::reset_scale_value(): the unscaled size. */
    fun resetScale() = scaleTo(Vector3(1.0, 1.0, 1.0))

    private fun scaleTo(factors: Vector3) {
        val target = selected() ?: return
        val unscaled = target.unscaledDimensions.vector()
        // limit_scaling_ratio(): no side beyond MAX_NUM.
        val limited = Vector3(
            factors.x.coerceAtMost(MAX_NUM / unscaled.x),
            factors.y.coerceAtMost(MAX_NUM / unscaled.y),
            factors.z.coerceAtMost(MAX_NUM / unscaled.z),
        )
        val placement = ObjectTransforms.scaled(target.placement, limited, unscaled, target.dimensions.vector(), target.boxCenter)
        placePlateObject(target.mesh, placement, Manipulation.Scale)
    }

    private fun ModelDimensions.vector() = Vector3(widthMillimeters, depthMillimeters, heightMillimeters)

    private fun Vector3.with(axis: Int, value: Double) = when (axis) {
        0 -> copy(x = value)
        1 -> copy(y = value)
        else -> copy(z = value)
    }

    private fun selected() = state.value.let { state -> state.selectedObject?.let(state.sceneObjects::get)?.inspection }

    fun closeGizmo() {
        view.update { it.copy(gizmo = null) }
    }

    /** GizmoObjectManipulation::change_position_value(): the object moves so its position on [axis] is [value]. */
    fun setPosition(axis: Int, value: Double) {
        val state = state.value
        val index = state.selectedObject ?: return
        val current = state.sceneObjects[index].inspection.placement.columns
        val clamped = value.coerceIn(-MAX_NUM, MAX_NUM)
        if (abs(current[12 + axis] - clamped) < POSITION_EPSILON) return
        placeObject(index, Transform3(current.toMutableList().also { it[12 + axis] = clamped }), Manipulation.Move)
    }

    fun placeObject(index: Int, placement: Transform3, manipulation: Manipulation) {
        state.value.sceneObjects.getOrNull(index)?.let { placePlateObject(it.inspection.mesh, placement, manipulation) }
    }

    /** The object menu's "Auto Drop": ObjectList::toggle_auto_drop() for the object [index]. */
    fun setAutoDrop(index: Int, enabled: Boolean) {
        state.value.sceneObjects.getOrNull(index)?.let { setPlateObjectAutoDrop(it.inspection.mesh, enabled) }
    }

    /** The object menu's "Delete": Plater::remove_selected() for the object [index]. */
    fun deleteObject(index: Int) {
        state.value.sceneObjects.getOrNull(index)?.let { deletePlateObject(it.inspection.mesh) }
    }

    fun slice() = slicePlate()

    fun cancelSlicing() = cancelPlateSlicing()

    fun dismissProblem() = dismissPlateProblem()

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // GizmoObjectManipulation.cpp: MAX_NUM, and the change it ignores (EPSILON).
        const val MAX_NUM = 9999.99
        const val POSITION_EPSILON = 1e-4

        // The spacing slider of the arrange options runs to 100 mm.
        const val MAX_ARRANGE_DISTANCE = 100.0
    }
}
