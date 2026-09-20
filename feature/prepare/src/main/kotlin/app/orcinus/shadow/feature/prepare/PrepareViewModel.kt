package app.orcinus.shadow.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.domain.DescribeFlatteningPlanesUseCase
import app.orcinus.shadow.domain.plate.AddCalibrationCubeToPlateUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.AddPlateInstanceUseCase
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.MoveWipeTowerUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.PaintObjectUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import app.orcinus.shadow.render.scene.ObjectTransforms
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.WIPE_TOWER_INDEX
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
    private val addPlateInstance: AddPlateInstanceUseCase,
    private val removePlateInstance: RemovePlateInstanceUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val describeFlatteningPlanes: DescribeFlatteningPlanesUseCase,
    private val selectPlateObject: SelectPlateObjectUseCase,
    private val moveTower: MoveWipeTowerUseCase,
    private val paintObject: PaintObjectUseCase,
    private val slicePlate: SlicePlateUseCase,
    private val cancelPlateSlicing: CancelPlateSlicingUseCase,
    private val dismissPlateProblem: DismissPlateProblemUseCase,
) : ViewModel() {
    private val plate = observePlate()

    /** A stroke is being painted; the next one waits for the engine to answer. */
    private var painting = false
    private val view = MutableStateFlow(PrepareViewState())

    val state: StateFlow<PrepareUiState> = combine(plate, view, PlateState::toPrepareUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), plate.value.toPrepareUiState(PrepareViewState()))

    init {
        // Plater::priv::load_files() selects the objects it added to the plate.
        viewModelScope.launch {
            var known: Set<ScenePath>? = null
            plate.collect { state ->
                val added = known?.let { before -> state.objects.lastOrNull { it.mesh !in before } }
                known = state.objects.mapTo(HashSet(), PlateObject::mesh)
                if (added != null) select(PlateInstanceId(added.mesh), added.instances.first().inspection)
            }
        }
        // GLGizmoFlatten::is_plane_update_necessary(): the faces follow the object and its scale.
        viewModelScope.launch {
            combine(plate, view) { plate, view ->
                val target = plate.selected?.takeIf { view.gizmo == PlateGizmo.LAY_ON_FACE }
                val copy = plate.selectedCopy
                val profiles = plate.profiles
                if (target == null || copy == null || profiles == null) {
                    null
                } else {
                    FlatteningKey(target, copy, profiles, copy.inspection.dimensions, copy.inspection.unscaledDimensions)
                }
            }
                .distinctUntilChanged { old, new -> old?.sameFaces(new) ?: (new == null) }
                .collectLatest { key ->
                    view.update { it.copy(flatteningPlanes = emptyList()) }
                    if (key == null) return@collectLatest
                    val outcome = describeFlatteningPlanes(key.plateObject, key.instance, key.profiles)
                    if (outcome is FlatteningPlanesOutcome.Success) view.update { it.copy(flatteningPlanes = outcome.planes) }
                }
        }
    }

    private class FlatteningKey(
        val plateObject: PlateObject,
        val instance: PlateInstance,
        val profiles: SlicingProfileSelection,
        val dimensions: ModelDimensions,
        val unscaledDimensions: ModelDimensions,
    ) {
        fun sameFaces(other: FlatteningKey?) = other != null && other.plateObject.mesh == plateObject.mesh &&
            other.dimensions == dimensions && other.unscaledDimensions == unscaledDimensions && other.profiles == profiles
    }

    fun addModel(reference: String) = addModelToPlate(ExternalDocumentReference(reference))

    fun addCalibrationCube() = addCalibrationCubeToPlate()

    /** Clearing the selection closes the gizmo, as GLGizmosManager does when it is no longer activable. */
    fun selectObject(index: Int?) {
        // The wipe tower is no object of the plate: picking it takes the
        // selection off the objects, as the desktop canvas does.
        if (index == WIPE_TOWER_INDEX) {
            view.update { it.copy(wipeTowerSelected = true) }
            select(null, null)
            return
        }
        view.update { if (it.wipeTowerSelected) it.copy(wipeTowerSelected = false) else it }
        val copy = index?.let { state.value.sceneCopies.getOrNull(it) }
        select(copy?.id, copy?.instance?.inspection)
    }

    /** GLCanvas3D::WipeTowerInfo::apply_wipe_tower(): the tower was dragged across the plate. */
    fun moveWipeTower(x: Double, y: Double) = moveTower(x, y)

    /**
     * GLGizmoMmuSegmentation: the colour painting tool opens on the selected
     * object, paints while a finger moves over it, and keeps the colours when
     * it closes. A stroke is dropped while the engine is still painting the
     * one before, so a fast finger does not pile up work.
     */
    fun togglePainting() {
        val open = view.value.painting
        if (open != null) {
            closePainting()
            return
        }
        val mesh = state.value.sceneCopies.getOrNull(state.value.selectedObject ?: -1)?.plateObject?.mesh ?: return
        view.update { it.copy(painting = PaintingMode(mesh), gizmo = null) }
        viewModelScope.launch { paintObject.begin(mesh) }
    }

    fun closePainting() {
        if (view.value.painting == null) return
        view.update { it.copy(painting = null) }
        viewModelScope.launch { paintObject.end() }
    }

    fun paintWith(filament: Int) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(filament = filament)) } ?: state }
    }

    fun setBrushRadius(radius: Double) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(radius = radius)) } ?: state }
    }

    fun setPaintTool(tool: PaintTool) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(tool = tool)) } ?: state }
    }

    fun paint(origin: Vector3, direction: Vector3) {
        val mode = view.value.painting ?: return
        if (painting) return
        painting = true
        viewModelScope.launch {
            try {
                paintObject.stroke(
                    PaintStroke(
                        origin = origin,
                        direction = direction,
                        filament = mode.filament,
                        radius = mode.radius,
                        tool = mode.tool,
                    ),
                )
            } finally {
                painting = false
            }
        }
    }

    private fun select(id: PlateInstanceId?, selected: ModelInspection?) {
        val before = plate.value.selectedInstance
        selectPlateObject(id)
        view.update { view ->
            view.copy(
                rotationStart = if (id != before) selected?.placement else view.rotationStart,
                gizmo = if (id == null) null else view.gizmo,
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
                rotationStart = state.selectedCopy?.instance?.inspection?.placement,
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
    fun autoOrient() = placePlateObjects(PlateManipulation.AutoOrient(setOfNotNull(state.value.selectedPlateObject?.mesh)))

    /** The toolbar's "Add instance": another copy of the selected one (Plater::increase_instances). */
    fun addInstance() {
        if (!state.value.canCopy) return
        selectedId()?.let(addPlateInstance::invoke)
    }

    /** The toolbar's "Remove instance" (Plater::decrease_instances). */
    fun removeInstance() {
        if (!state.value.canRemoveCopy) return
        selectedId()?.let(removePlateInstance::invoke)
    }

    /**
     * GizmoObjectManipulation::change_rotation_value(): the selected object turns
     * by [degrees] about the world [axis] through its bounding sphere's centre.
     */
    fun rotateBy(axis: Int, degrees: Double) {
        if (degrees == 0.0) return
        val target = selected() ?: return
        placePlateObject(selectedId() ?: return, ObjectTransforms.rotated(target.placement, axis, degrees, target.boundingSphere.center), Manipulation.Rotate)
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
        placePlateObject(selectedId() ?: return, ObjectTransforms.withLinearPartOf(target.placement, start), Manipulation.Rotate)
    }

    /** GizmoObjectManipulation::reset_rotation_value(false): no rotation. */
    fun resetRotationToZero() {
        val target = selected() ?: return
        placePlateObject(selectedId() ?: return, target.placement, Manipulation.ResetRotation)
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
        placePlateObject(selectedId() ?: return, placement, Manipulation.Scale)
    }

    private fun ModelDimensions.vector() = Vector3(widthMillimeters, depthMillimeters, heightMillimeters)

    private fun Vector3.with(axis: Int, value: Double) = when (axis) {
        0 -> copy(x = value)
        1 -> copy(y = value)
        else -> copy(z = value)
    }

    private fun selected() = state.value.selectedCopy?.instance?.inspection

    /** The copy the tools work on, as the plate names it. */
    private fun selectedId() = state.value.selectedCopy?.id

    fun closeGizmo() {
        view.update { it.copy(gizmo = null) }
    }

    /** GizmoObjectManipulation::change_position_value(): the object moves so its position on [axis] is [value]. */
    fun setPosition(axis: Int, value: Double) {
        val state = state.value
        val index = state.selectedObject ?: return
        val current = state.sceneCopies[index].instance.inspection.placement.columns
        val clamped = value.coerceIn(-MAX_NUM, MAX_NUM)
        if (abs(current[12 + axis] - clamped) < POSITION_EPSILON) return
        placeObject(index, Transform3(current.toMutableList().also { it[12 + axis] = clamped }), Manipulation.Move)
    }

    fun placeObject(index: Int, placement: Transform3, manipulation: Manipulation) {
        state.value.sceneCopies.getOrNull(index)?.let { placePlateObject(it.id, placement, manipulation) }
    }

    /** The object menu's "Auto Drop": ObjectList::toggle_auto_drop() for the object [index]. */
    fun setAutoDrop(index: Int, enabled: Boolean) {
        state.value.sceneCopies.getOrNull(index)?.let { setPlateObjectAutoDrop(it.id, enabled) }
    }

    /** The object menu's "Delete": Plater::remove_selected() for the object [index]. */
    fun deleteObject(index: Int) {
        state.value.sceneCopies.getOrNull(index)?.let { deletePlateObject(it.id.mesh) }
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
