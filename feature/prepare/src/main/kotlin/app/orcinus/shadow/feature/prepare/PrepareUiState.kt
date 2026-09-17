package app.orcinus.shadow.feature.prepare

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.PlateGizmo
import kotlin.math.abs

/** An object's instance offset, which OrcaSlicer's move window shows as its position, in millimetres. */
data class ObjectPosition(val x: Double, val y: Double, val z: Double) {
    operator fun get(axis: Int) = when (axis) {
        0 -> x
        1 -> y
        else -> z
    }
}

data class PrepareUiState(
    /** The printer's plate for the 3D view; null until the engine described it. */
    val plate: PlateDescription?,
    val importing: Boolean,
    val plateObject: PlateObject?,
    /** Objects of the 3D view. */
    val sceneObjects: List<PlateObject>,
    /** Index of the selected object in [sceneObjects]. */
    val selectedObject: Int?,
    /** The open gizmo; gizmos need a selected object on a plate that can change. */
    val gizmo: PlateGizmo?,
    /** The faces the selected object can lie on while "Lay on face" is open. */
    val flatteningPlanes: List<FlatteningPlane>,
    val arrangeOptionsOpen: Boolean,
    val arrangeSettings: ArrangeSettings,
    /** Position of the selected object. */
    val selectedPosition: ObjectPosition?,
    /** Rotation of the selected object in degrees, as the rotation window shows it. */
    val selectedRotation: Vector3?,
    /** GizmoObjectManipulation::update_reset_buttons_visibility(): the rotation differs from when the tool opened. */
    val canResetRotation: Boolean,
    /** ... and from no rotation. */
    val canResetRotationToZero: Boolean,
    /** Scale ratios of the selected object in percent and its size, as the scale window shows them. */
    val selectedScale: Vector3?,
    val selectedSize: Vector3?,
    val uniformScale: Boolean,
    /** An object lies across the plate boundary or above the build height. */
    val objectClashed: Boolean,
    val slicing: PlateSlicing?,
    val problem: PlateProblem?,
    val canEditPlate: Boolean,
    val canSlice: Boolean,
) {
    /** GLGizmoBase::on_is_activable() for the manipulation gizmos: an object is selected. */
    val canManipulate: Boolean get() = selectedObject != null && canEditPlate

    /** Plater::can_arrange(), which also enables auto orient: the plate has objects. */
    val canArrange: Boolean get() = sceneObjects.isNotEmpty() && canEditPlate
}

/** What the Prepare page itself keeps: the selection and the open gizmo with its state. */
internal data class PrepareViewState(
    /** The selected object, by its mesh file; a replaced object is no longer selected. */
    val selectedMesh: ScenePath? = null,
    val gizmo: PlateGizmo? = null,
    /** The selected object's placement when the rotation tool opened: GizmoObjectManipulation::set_init_rotation(). */
    val rotationStart: Transform3? = null,
    /** GizmoObjectManipulation::m_uniform_scale, on by default. */
    val uniformScale: Boolean = true,
    /** The faces "Lay on face" offers for the selected object. */
    val flatteningPlanes: List<FlatteningPlane> = emptyList(),
    /** The arrange options window is open, as the pressed Arrange toolbar item shows it. */
    val arrangeOptionsOpen: Boolean = false,
    val arrangeSettings: ArrangeSettings = ArrangeSettings(),
)

internal fun PlateState.toPrepareUiState(view: PrepareViewState): PrepareUiState {
    val selectedMesh = view.selectedMesh
    val gizmo = view.gizmo
    val rotationStart = view.rotationStart
    val uniformScale = view.uniformScale
    val sceneObjects = listOfNotNull(plateObject)
    val selectedObject = sceneObjects.indexOfFirst { it.inspection.mesh == selectedMesh }.takeIf { it >= 0 }
    val selected = selectedObject?.let(sceneObjects::get)?.inspection
    val canEditPlate = !busy && engine.availability == EngineAvailability.READY
    return PrepareUiState(
        plate = plate,
        importing = importing,
        plateObject = plateObject,
        sceneObjects = sceneObjects,
        selectedObject = selectedObject,
        gizmo = gizmo.takeIf { selectedObject != null && canEditPlate },
        flatteningPlanes = if (gizmo == PlateGizmo.LAY_ON_FACE) view.flatteningPlanes else emptyList(),
        arrangeOptionsOpen = view.arrangeOptionsOpen && plateObject != null && canEditPlate,
        arrangeSettings = view.arrangeSettings,
        selectedPosition = selected?.placement?.columns?.let { ObjectPosition(it[12], it[13], it[14]) },
        selectedRotation = selected?.rotationDegrees,
        canResetRotation = selected != null && rotationStart != null && !selected.placement.hasLinearPartOf(rotationStart),
        canResetRotationToZero = selected != null && with(selected.rotationDegrees) { listOf(x, y, z).any { abs(it) > 0.001 } },
        selectedScale = selected?.let {
            // update_settings_value() in world coordinates: size over the unscaled size.
            with(it.dimensions) {
                Vector3(
                    widthMillimeters / it.unscaledDimensions.widthMillimeters * 100.0,
                    depthMillimeters / it.unscaledDimensions.depthMillimeters * 100.0,
                    heightMillimeters / it.unscaledDimensions.heightMillimeters * 100.0,
                )
            }
        },
        selectedSize = selected?.dimensions?.let { Vector3(it.widthMillimeters, it.depthMillimeters, it.heightMillimeters) },
        uniformScale = uniformScale,
        objectClashed = plateObject?.inspection?.fit == BuildVolumeFit.PARTLY_OUTSIDE,
        slicing = slicing,
        problem = problem,
        // Objects are loaded and placed by the engine.
        canEditPlate = canEditPlate,
        canSlice = canSlice,
    )
}

/** Whether the rotation and scale match [other]'s, within rounding. */
private fun Transform3.hasLinearPartOf(other: Transform3): Boolean =
    (0 until 11).filter { it % 4 != 3 }.all { abs(columns[it] - other.columns[it]) < 1e-6 }
