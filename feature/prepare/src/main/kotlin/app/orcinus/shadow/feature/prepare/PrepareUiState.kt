package app.orcinus.shadow.feature.prepare

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.lockedPlates
import app.orcinus.shadow.domain.plate.canWorkOnPlate
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.domain.plate.canAddPlate
import app.orcinus.shadow.domain.plate.canDeletePlate
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.withInstance
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.core.model.withPartAt
import app.orcinus.shadow.domain.plate.SimplifyPreview
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.WIPE_TOWER_INDEX
import kotlin.math.abs

/** An object's instance offset, which OrcaSlicer's move window shows as its position, in millimetres. */
data class ObjectPosition(val x: Double, val y: Double, val z: Double) {
    operator fun get(axis: Int) = when (axis) {
        0 -> x
        1 -> y
        else -> z
    }
}

/** One copy of an object on the plate, as the 3D view draws and numbers them. */
data class SceneCopy(val id: PlateInstanceId, val plateObject: PlateObject, val instance: PlateInstance)

data class PrepareUiState(
    /** The printer's plate for the 3D view; null until the engine described it. */
    val plate: PlateDescription?,
    val importing: Boolean,
    /** The objects on the plate, as the 3D view shows them. */
    val sceneObjects: List<PlateObject>,
    /** Every copy on the plate, in the order the 3D view draws them. */
    val sceneCopies: List<SceneCopy> = emptyList(),
    /** Index of the object the tools work on; null when none or several are selected. */
    val selectedObject: Int?,
    /** Every selected object, which the 3D view draws as selected. */
    val selectedObjects: Set<Int> = emptySet(),
    /** The open gizmo; gizmos need a selected object on a plate that can change. */
    val gizmo: PlateGizmo?,
    /** The faces the selected object can lie on while "Lay on face" is open. */
    val flatteningPlanes: List<FlatteningPlane>,
    /** The colour painting tool, while it is open on an object. */
    val painting: PaintingMode? = null,
    /** The wipe tower of the plate; null when the plate prints with one filament. */
    val wipeTower: WipeTower? = null,
    /** The tower the last slice built, which the plate shows once it is sliced. */
    val builtWipeTower: ScenePath? = null,
    /** The colour of every filament of the plate, which the tower takes its own from. */
    val filamentColors: List<ColorRgba> = emptyList(),
    /** What the sidebar calls every filament of the plate: its preset, as its combo box shows it. */
    val filamentNames: List<String> = emptyList(),
    /** The tower as the engine last described it, which the object menu's Flush Options go by. */
    val flushing: WipeTower = WipeTower(),
    /** What "Copy Process Settings" took. */
    val settingsClipboard: SettingsClipboard? = null,
    /** The Simplify gizmo, while it is open on a volume. */
    val simplify: SimplifyMode? = null,
    /** The meshes the 3D view draws with their triangle edges over them (the gizmo's "Show wireframe"). */
    val wireframes: Set<ScenePath> = emptySet(),
    val arrangeOptionsOpen: Boolean,
    val arrangeSettings: ArrangeSettings,
    /** What Copy and Cut took, which Paste puts on the plate. */
    val clipboard: PlateClipboard? = null,
    /** Plater::can_undo() and can_redo(). */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Where every plate stands (PartPlateList), and the one the view works on. */
    val plateOrigins: List<Point2> = listOf(Point2(0.0, 0.0)),
    val currentPlate: Int = 0,
    /** Plater::can_add_plate() and can_delete_plate(). */
    val canAddPlate: Boolean = false,
    val canDeletePlate: Boolean = false,
    /** The plates' names, empty for one the user did not name, and the locked plates. */
    val plateNames: List<String> = listOf(""),
    val lockedPlates: Set<Int> = emptySet(),
    /** The plates their orient and arrange work on: not locked, with objects. */
    val workablePlates: Set<Int> = emptySet(),
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
    /** GLCanvas3D::EWarning::ObjectClashed: an object lies across the plate boundary or above the build height. */
    val objectClashed: Boolean,
    val slicing: PlateSlicing?,
    val problem: PlateProblem?,
    val canEditPlate: Boolean,
    val canSlice: Boolean,
) {
    /** GLGizmoBase::on_is_activable() for the manipulation gizmos: an object is selected. */
    val canManipulate: Boolean get() = selectedObject != null && canEditPlate

    /** The colour painting tool needs a selected object and more than one filament. */
    val canPaint: Boolean get() = canManipulate && filamentColors.size > 1

    /**
     * Plater::can_arrange(), which also enables auto orient: the plate has
     * objects. The jobs start from settled placements.
     */
    val canArrange: Boolean get() = sceneObjects.isNotEmpty() && canEditPlate && sceneObjects.none(PlateObject::placing)

    /**
     * Plater::can_paste_from_clipboard() with nothing held: objects the
     * clipboard holds, which go on the plate; volumes need an object to join.
     */
    val canPasteOnPlate: Boolean get() = canEditPlate && clipboard is PlateClipboard.Objects

    /** Whether another copy can be made of the selected one (Plater::can_increase_instances). */
    val canCopy: Boolean get() = selectedCopy != null && canEditPlate && selectedCopy?.plateObject?.instances.orEmpty().all { it.printable }

    /** ... and whether the selected copy can go (Plater::can_decrease_instances). */
    val canRemoveCopy: Boolean get() = (selectedCopy?.plateObject?.instances?.size ?: 0) > 1 && canEditPlate

    /**
     * Plater::can_split_to_objects() of the toolbar: ObjectList::is_splittable(true)
     * of the selected object, which has parts or a mesh of several shells.
     */
    val canSplitToObjects: Boolean get() = canEditPlate && selectedPlateObject?.let { it.parts.isNotEmpty() || it.volume.splittable } == true

    /** Plater::can_split_to_volumes(): the selected object is one mesh of several shells. */
    val canSplitToParts: Boolean get() = canEditPlate && selectedPlateObject?.let { it.parts.isEmpty() && it.volume.splittable } == true

    /** The object the info notification describes: Plater::show_object_info() for a single selected object. */
    val selectedPlateObject: PlateObject? get() = selectedObject?.let(sceneCopies::getOrNull)?.plateObject

    /** The copy the tools work on. */
    val selectedCopy: SceneCopy? get() = selectedObject?.let(sceneCopies::getOrNull)
}

/**
 * What the Prepare page itself keeps: the open gizmo with its state. The
 * selected object is the plate's, since the settings of an object follow it.
 */
/**
 * OrcaSlicer's colour painting gizmo while it is open: which filament the
 * finger paints with and how wide the brush is (GLGizmoPainterBase).
 */
data class PaintingMode(
    /** The object being painted. */
    val mesh: ScenePath,
    /** The filament the brush paints with, 1-based; 0 takes the paint off. */
    val filament: Int = 1,
    val radius: Double = 2.0,
    val tool: PaintTool = PaintTool.BRUSH,
    /** Whether the tool can undo or redo a stroke, which the Undo and Redo buttons do while it is open. */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

internal data class PrepareViewState(
    val gizmo: PlateGizmo? = null,
    /** The selected object's placement when the rotation tool opened: GizmoObjectManipulation::set_init_rotation(). */
    val rotationStart: Transform3? = null,
    /** GizmoObjectManipulation::m_uniform_scale, on by default. */
    val uniformScale: Boolean = true,
    /** The faces "Lay on face" offers for the selected object. */
    val flatteningPlanes: List<FlatteningPlane> = emptyList(),
    /** The wipe tower is the picked volume, so the 3D view draws it selected. */
    val wipeTowerSelected: Boolean = false,
    /** The colour painting tool, while it is open. */
    val painting: PaintingMode? = null,
    /** The arrange options window is open, as the pressed Arrange toolbar item shows it. */
    val arrangeOptionsOpen: Boolean = false,
    /** The Simplify gizmo, while it is open. */
    val simplify: SimplifyMode? = null,
)

/**
 * GLGizmoSimplify while it is open on a [volume]: its configuration, the
 * detail level its slider shows, and the decimated mesh it draws in the
 * volume's place once the engine made it.
 */
data class SimplifyMode(
    val volume: ObjectPartId,
    val config: SimplifyConfig,
    /** The detail level of the slider: Extra high, High, Medium, Low, Extra low. */
    val reduction: Int,
    /** "Show wireframe" */
    val wireframe: Boolean,
    /** The mesh the gizmo shows, with the triangles of the volume; null until the engine made one. */
    val preview: SimplifyPreview? = null,
    /** The engine is decimating the mesh (the worker thread is running). */
    val running: Boolean = false,
) {
    /** The Apply button: a mesh is shown and nothing is being worked out. */
    val canApply: Boolean get() = preview != null && !running
}

internal fun PlateState.toPrepareUiState(view: PrepareViewState): PrepareUiState {
    val gizmo = view.gizmo
    val rotationStart = view.rotationStart
    val uniformScale = view.uniformScale
    // The copies in the order the 3D view draws and picks them.
    val copies = objects.flatMap { plateObject ->
        plateObject.instances.mapIndexed { index, instance -> SceneCopy(PlateInstanceId(plateObject.mesh, index), plateObject, instance) }
    }
    val selectedObject = copies.indexOfFirst { it.id == selectedInstance }.takeIf { it >= 0 }
    val selectedIndexes = copies.indices.filterTo(mutableSetOf()) { copies[it].id in selectedInstances }
    // The tower is drawn selected while it is the picked volume.
    if (wipeTower != null && view.wipeTowerSelected) selectedIndexes += WIPE_TOWER_INDEX
    val selected = selectedObject?.let(copies::get)?.instance?.inspection
    // The plate changes once OrcaSlicer has the presets it places objects with.
    val canEditPlate = !busy && engine.availability == EngineAvailability.READY && profiles != null
    return PrepareUiState(
        plate = plate,
        importing = importing,
        // GLGizmoSimplify::init_model(): the decimated mesh is drawn in the volume's place.
        sceneObjects = view.simplify?.let { mode -> objects.withSimplified(mode, selectedInstance) } ?: objects,
        sceneCopies = copies,
        selectedObject = selectedObject,
        selectedObjects = selectedIndexes,
        gizmo = gizmo.takeIf { selectedObject != null && canEditPlate },
        flatteningPlanes = if (gizmo == PlateGizmo.LAY_ON_FACE) view.flatteningPlanes else emptyList(),
        painting = view.painting?.takeIf { mode -> objects.any { it.mesh == mode.mesh } && canEditPlate },
        wipeTower = wipeTower,
        builtWipeTower = result?.wipeTower,
        filamentColors = presets?.filamentColors.orEmpty().mapNotNull(::parseFilamentColor),
        filamentNames = profiles?.allFilaments.orEmpty().map { id ->
            presets?.filaments?.firstOrNull { it.name == id.value }?.label ?: id.value
        },
        flushing = flushing,
        settingsClipboard = settingsClipboard,
        simplify = view.simplify?.takeIf { mode -> objects.any { it.mesh == mode.volume.mesh } },
        wireframes = view.simplify?.takeIf { it.wireframe }?.preview?.let { setOf(it.mesh) }.orEmpty(),
        arrangeOptionsOpen = view.arrangeOptionsOpen && objects.isNotEmpty() && canEditPlate,
        arrangeSettings = arrangeSettings,
        clipboard = clipboard,
        // While the painting tool is open, Undo and Redo work on its strokes (the gizmo's stack).
        canUndo = view.painting?.canUndo ?: canUndo,
        canRedo = view.painting?.canRedo ?: canRedo,
        plateOrigins = plateOrigins(),
        currentPlate = currentPlate,
        canAddPlate = canAddPlate && canEditPlate,
        canDeletePlate = canDeletePlate && canEditPlate,
        plateNames = plates.map(PartPlate::name),
        lockedPlates = lockedPlates(),
        workablePlates = plates.indices.filterTo(mutableSetOf(), ::canWorkOnPlate),
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
        objectClashed = copies.any { it.instance.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE },
        slicing = slicing,
        problem = problem,
        // Objects are loaded and placed by the engine.
        canEditPlate = canEditPlate,
        canSlice = canSlice,
    )
}

/**
 * The objects with the gizmo's decimated mesh in place of its volume: the
 * object's own mesh of the copy it works on, or the part in every copy, and
 * without the painting drawn over the old mesh.
 */
private fun List<PlateObject>.withSimplified(mode: SimplifyMode, selected: PlateInstanceId?): List<PlateObject> {
    val preview = mode.preview?.mesh ?: return this
    return map { plateObject ->
        if (plateObject.mesh != mode.volume.mesh) return@map plateObject
        val shown = if (mode.volume.index == 0) {
            val copy = selected?.takeIf { it.mesh == plateObject.mesh }?.instance ?: 0
            plateObject.withInstance(copy, plateObject.instances[copy].let { it.copy(inspection = it.inspection.copy(mesh = preview)) })
        } else {
            plateObject.withPartAt(mode.volume.index - 1, plateObject.parts[mode.volume.index - 1].copy(mesh = preview))
        }
        shown.withPainted(PaintedFacets(), emptyList())
    }
}

/** Whether the rotation and scale match [other]'s, within rounding. */
private fun Transform3.hasLinearPartOf(other: Transform3): Boolean =
    (0 until 11).filter { it % 4 != 3 }.all { abs(columns[it] - other.columns[it]) < 1e-6 }
