package app.orcinus.shadow.feature.prepare

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BedTypeChoice
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutConnectorShape
import app.orcinus.shadow.core.model.CutConnectorStyle
import app.orcinus.shadow.core.model.CutConnectorType
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPlaneDescription
import app.orcinus.shadow.core.model.CutPreviewPart
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.EmbossVolume
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.LayerEditing
import app.orcinus.shadow.core.model.LayerHeightEdit
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PlateValidationMessage
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.lockedPlates
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.core.model.plateSettingsChoice
import app.orcinus.shadow.core.model.withInstance
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.core.model.withPartAt
import app.orcinus.shadow.domain.plate.SimplifyPreview
import app.orcinus.shadow.domain.plate.canAddPlate
import app.orcinus.shadow.domain.plate.canDeletePlate
import app.orcinus.shadow.domain.plate.canWorkOnPlate
import app.orcinus.shadow.domain.plate.layerEditingObject
import app.orcinus.shadow.domain.plate.presetValue
import app.orcinus.shadow.domain.plate.spiralVaseMode
import app.orcinus.shadow.render.scene.CutPlanes
import app.orcinus.shadow.render.scene.PlateClearance
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

/**
 * A message of the plate's validation as its notification shows it
 * (NotificationManager::push_validate_error_notification() and
 * Plater::priv::process_validation_warning()): the [text], and what "Jump to"
 * goes to — the [target] object or copy it is about, which it selects and
 * names, and the setting [option] it names (opt_key).
 */
data class ValidationNotice(
    val text: String,
    val target: PlateInstanceId? = null,
    val targetObject: PlateObject? = null,
    /** The message is about one copy of the object, not the object as a whole. */
    val copy: Boolean = false,
    val option: String = "",
)

private fun notice(message: PlateValidationMessage, objects: List<PlateObject>): ValidationNotice {
    val target = objects.getOrNull(message.objectIndex)
    return ValidationNotice(
        text = message.text,
        target = target?.let { PlateInstanceId(it.mesh, message.instanceIndex.coerceAtLeast(0)) },
        targetObject = target,
        copy = target != null && message.instanceIndex >= 0,
        option = message.option,
    )
}

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
    /** The cut gizmo, while it is open on a copy. */
    val cut: CutMode? = null,
    /** The variable layer height, while it is on and the plate can change. */
    val layerEditing: LayerEditingMode? = null,
    /** The text tool (GLGizmoEmboss), while it is open on a text volume. */
    val text: TextMode? = null,
    /** What the object list asks of the text or SVG tool, which the canvas places. */
    val embossRequest: EmbossRequest? = null,
    /** Plater::can_layers_editing(): the toolbar's "Variable layer height" can open on the selection. */
    val canEditLayers: Boolean = false,
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
    /** The canvas's "Overhangs": slope.normal_z, which the support settings give; null until the engine answered. */
    val overhangNormalZ: Float? = null,
    /** The copies of the current plate, by their index among [sceneCopies], which the canvas's labels name. */
    val currentPlateCopies: Set<Int> = emptySet(),
    /** The labels' "Sequence#": each copy's place in the print order, -1 for one not printed; empty while the plate prints by layer. */
    val printSequence: List<Int> = emptyList(),
    /** The plate's validation (Plater::priv::update_background_process()): its error and its warning. */
    val validationError: ValidationNotice? = null,
    val validationWarning: ValidationNotice? = null,
    /** The sequential printing's clearances while the validation fails. */
    val clearance: PlateClearance? = null,
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
    /** The plates with settings of their own (PartPlate's settings icon). */
    val customizedPlates: Set<Int> = emptySet(),
    /** What PlateSettingsDialog shows for the current plate. */
    val plateSettings: PlateSettingsChoice = PlateSettingsChoice(),
    val bedTypes: List<BedTypeChoice> = emptyList(),
    /** The current plate prints in spiral vase mode, its own or the process preset's. */
    val spiralVaseMode: Boolean = false,
    /** The printer's structure is I3 (printer_structure). */
    val printerI3: Boolean = false,
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
    /** What the slice button slices, and whether it can now (MainFrame::get_enable_slice_status). */
    val sliceMode: SliceMode = SliceMode.PLATE,
    val sliceEnabled: Boolean = false,
) {
    /** GLGizmoBase::on_is_activable() for the manipulation gizmos: an object is selected. */
    val canManipulate: Boolean get() = selectedObject != null && canEditPlate

    /**
     * GLGizmoCut3D::on_is_activable(): a copy to cut, which is not a dowel a
     * cut made an object of (a part of a cut whose one volume is that connector).
     */
    val canCut: Boolean
        get() {
            val selected = sceneCopies.getOrNull(selectedObject ?: -1)?.plateObject ?: return false
            val dowel = selected.isCut && selected.parts.isEmpty() && selected.volume.cutInfo.let { it.connector && it.connectorType == CutConnectorType.DOWEL }
            return canManipulate && !dowel
        }

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
 * One of OrcaSlicer's painting gizmos while it is open (GLGizmoPainterBase):
 * what it paints, the state the finger paints, the tool and its size.
 */
data class PaintingMode(
    /** The object being painted. */
    val mesh: ScenePath,
    val kind: PaintKind = PaintKind.COLOR,
    /** The state the finger paints ([PaintState]); for colour, the filament, 1-based; 0 takes the paint off. */
    val state: Int = 1,
    val radius: Double = 2.0,
    val tool: PaintTool = PaintTool.BRUSH,
    /** The angle the smart fill keeps to (m_smart_fill_angle), in degrees. */
    val fillAngle: Double = 30.0,
    /**
     * "Highlight overhang areas" (m_highlight_by_angle_threshold_deg): facets
     * overhanging more than this angle below the horizontal are highlighted,
     * in degrees; 0 highlights none.
     */
    val highlightAngle: Double = 0.0,
    /** "On highlighted overhangs only" (m_paint_on_overhangs_only). */
    val overhangsOnly: Boolean = false,
    /** The gap fill's area (TriangleSelectorPatch::gap_area), in square millimetres. */
    val gapArea: Double = 0.0,
    /** The seam tool's "Vertical" (m_vertical_only): a stroke keeps to a screen column. */
    val verticalOnly: Boolean = false,
    /**
     * The fuzzy skin tool's warning: fuzzy skin is disabled for the object, by
     * its settings or the process preset, so what is painted does not take effect.
     */
    val fuzzySkinDisabled: Boolean = false,
    /** Whether the model carries the tool's kind of paint at all, which "Erase all" takes off. */
    val painted: Boolean = false,
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
    /** The cut gizmo, while it is open. */
    val cut: CutMode? = null,
    /** The arrange options window is open, as the pressed Arrange toolbar item shows it. */
    val arrangeOptionsOpen: Boolean = false,
    /** The Simplify gizmo, while it is open. */
    val simplify: SimplifyMode? = null,
    /** The variable layer height window's tools, kept while it is closed. */
    val layerTools: LayerEditingTools = LayerEditingTools(),
    /** The text tool, while it is open. */
    val text: TextMode? = null,
    /** The layers of the object the variable layer height edits, as the engine described them last. */
    val layerDescription: Pair<ScenePath, LayerEditing>? = null,
    /** The height under the finger on the variable layer height bar. */
    val layerCursor: Double? = null,
)

/**
 * GLGizmoEmboss while it is open on the text [volume]: the [text] and the
 * [style] its window edits (the style's angle and distance measured from where
 * the volume stands), what the engine said of the volume ([described]: its
 * type, whether it is the object's only part, its scale), the styles the
 * window offers with the one it started from ([styles], StyleManager's
 * stored styles), whether the window's font is one the phone has not
 * ([unknownFont]: only another font can be chosen), whether "Advanced" is
 * open, and whether the engine is embossing it anew.
 */
data class TextMode(
    val volume: ObjectPartId,
    val text: String,
    val style: TextStyle,
    val described: EmbossVolume? = null,
    val styles: List<TextStyle> = emptyList(),
    val unknownFont: Boolean = false,
    val advanced: Boolean = false,
    val busy: Boolean = false,
) {
    /** Whether the text is white spaces alone, which embosses nothing (is_text_empty()). */
    val blank: Boolean get() = text.isBlank()

    /** ModelVolume::is_the_only_one_part(): the text is its object, which takes no other operation. */
    val onlyPart: Boolean get() = described?.onlyPart != false

    /** The style the window started from, which Reset loads (is_changed_from_default_style()). */
    val defaultStyle: TextStyle? get() = styles.firstOrNull()
}

/**
 * What the variable layer height bar keeps from one opening to the next
 * (members of GLCanvas3D::LayersEditing): the band a press edits
 * ([bandWidth], which the mouse wheel sets), Adaptive's quality and Smooth's
 * radius and "Keep min". A finger has no right button and no Shift, so what a
 * press does ([action]) is chosen in the window: the left button's "Add
 * detail" at first.
 */
data class LayerEditingTools(
    val action: LayerHeightEdit = LayerHeightEdit.DECREASE,
    val bandWidth: Double = DEFAULT_BAND_WIDTH,
    val adaptiveQuality: Double = DEFAULT_ADAPTIVE_QUALITY,
    val smoothRadius: Int = DEFAULT_SMOOTH_RADIUS,
    val keepMin: Boolean = false,
) {
    companion object {
        /** LayersEditing::band_width, which the wheel keeps between 1.5 and 10 mm. */
        const val DEFAULT_BAND_WIDTH = 2.0
        const val MIN_BAND_WIDTH = 1.5
        const val MAX_BAND_WIDTH = 10.0

        /** LayersEditing::m_adaptive_quality */
        const val DEFAULT_ADAPTIVE_QUALITY = 0.5

        /** HeightProfileSmoothingParams: radius 5 of 1 to 10. */
        const val DEFAULT_SMOOTH_RADIUS = 5
        const val MIN_SMOOTH_RADIUS = 1
        const val MAX_SMOOTH_RADIUS = 10
    }
}

/**
 * The variable layer height on the object with the [mesh] file: its layers as
 * the engine describes them (null until it has), the window's [tools], and
 * the height under the finger on the bar ([cursorZ], null while none is).
 */
data class LayerEditingMode(
    val mesh: ScenePath,
    val editing: LayerEditing?,
    val tools: LayerEditingTools,
    val cursorZ: Double?,
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
    val canEditPlate = !busy && !slicingAll && engine.availability == EngineAvailability.READY && profiles != null
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
        cut = view.cut?.takeIf { mode -> objects.any { it.mesh == mode.mesh } && canEditPlate },
        layerEditing = layerEditingObject()?.takeIf { layerEditing && canEditPlate }?.let { target ->
            LayerEditingMode(target.mesh, view.layerDescription?.takeIf { it.first == target.mesh }?.second, view.layerTools, view.layerCursor)
        },
        canEditLayers = canEditPlate && layerEditingObject() != null,
        text = view.text?.takeIf { mode -> objects.any { it.mesh == mode.volume.mesh } && canEditPlate },
        embossRequest = embossRequest.takeIf { canEditPlate },
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
        overhangNormalZ = overhangNormalZ,
        currentPlateCopies = copies.indices.filterTo(mutableSetOf()) { plateOf(copies[it].instance) == currentPlate },
        printSequence = validation?.sequence.orEmpty(),
        validationError = validation?.error?.let { notice(it, objects) },
        validationWarning = validation?.warning?.let { notice(it, objects) },
        clearance = validation?.takeIf { it.error != null && (it.clearance.isNotEmpty() || it.heightLimitFill.isNotEmpty()) }
            ?.let { PlateClearance(it.clearance, it.clearanceFill, it.heightLimitFill) },
        arrangeOptionsOpen = view.arrangeOptionsOpen && objects.isNotEmpty() && canEditPlate,
        arrangeSettings = arrangeSettings,
        clipboard = clipboard,
        // While the painting tool is open, Undo and Redo work on its strokes (the gizmo's stack),
        // and while the cut gizmo is, on its plane.
        canUndo = view.cut?.canUndo ?: view.painting?.canUndo ?: canUndo,
        canRedo = view.cut?.canRedo ?: view.painting?.canRedo ?: canRedo,
        plateOrigins = plateOrigins(),
        currentPlate = currentPlate,
        canAddPlate = canAddPlate && canEditPlate,
        canDeletePlate = canDeletePlate && canEditPlate,
        plateNames = plates.map(PartPlate::name),
        lockedPlates = lockedPlates(),
        workablePlates = plates.indices.filterTo(mutableSetOf(), ::canWorkOnPlate),
        customizedPlates = partPlates().indices.filterTo(mutableSetOf()) { partPlates()[it].settings.plateSettingsChoice() != PlateSettingsChoice() },
        plateSettings = plateSettings.plateSettingsChoice(),
        bedTypes = presets?.bedTypes.orEmpty(),
        spiralVaseMode = spiralVaseMode(),
        printerI3 = presetValue(PresetKind.PRINTER, "printer_structure") == "i3",
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
        sliceMode = sliceMode,
        sliceEnabled = sliceEnabled,
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

/**
 * GLGizmoCut3D while it is open on the copy at [instance] of the object with
 * the [mesh] file, cutting with a plane: the plane in world coordinates, the
 * bounding box of the copy's solid parts it started in, what the engine said of
 * the plane, "After cut", and the gizmo's own snapshots of its plane, which
 * Undo and Redo go through while it is open.
 */
data class CutMode(
    val mesh: ScenePath,
    val instance: Int,
    /** GLGizmoCut3D::bounding_box(); null until the engine reported it. */
    val boundsMin: Vector3? = null,
    val boundsMax: Vector3? = null,
    /** The plane (ObjectCut.plane); null until the bounding box is known. */
    val plane: Transform3? = null,
    /** What the engine said of [describedPlane], the plane as it stood when it was asked. */
    val described: CutPlaneDescription? = null,
    val describedPlane: Transform3? = null,
    val keepUpper: Boolean = true,
    val keepLower: Boolean = true,
    /** "Cut to parts" */
    val keepAsParts: Boolean = false,
    val placeOnCutUpper: Boolean = true,
    val placeOnCutLower: Boolean = false,
    /** "Flip" (m_rotate_upper and m_rotate_lower). */
    val flipUpper: Boolean = false,
    val flipLower: Boolean = false,
    /** ModelObject::cut_connectors: where the connectors stand on the plane. */
    val connectors: List<CutConnector> = emptyList(),
    /** m_selected: the indexes of the selected connectors. */
    val selectedConnectors: Set<Int> = emptySet(),
    /** m_connectors_editing: the connectors' window is open and a touch on the section adds one. */
    val editingConnectors: Boolean = false,
    /** The connector the window adds and sets. */
    val connectorSettings: CutConnectorSettings = CutConnectorSettings(),
    /** m_snap_space_proportion and m_snap_bulge_proportion. */
    val snapSpace: Double = SNAP_SPACE,
    val snapBulge: Double = SNAP_BULGE,
    /** The connectors as they stood when the engine described the plane. */
    val describedConnectors: List<CutConnector>? = null,
    /** m_mode: "Planar" or "Dovetail". */
    val kind: CutKind = CutKind.PLANAR,
    /** m_groove with m_groove_count and m_groove_gap, and their initial values, which the resets bring back. */
    val groove: CutGroove = CutGroove(),
    val grooveInit: CutGroove = CutGroove(),
    /**
     * The plane or the grooves are being dragged (m_dragging, m_groove_editing):
     * the dovetail cut's parts are not worked out meanwhile.
     */
    val shaping: Boolean = false,
    /** The grooves as they stood when the engine described the plane; null for the planar cut. */
    val describedGroove: CutGroove? = null,
    /**
     * m_part_selection: the pieces a long press (a right click) split the
     * object into at [partsPlane], each going to the upper part or the lower
     * one; null while the halves fall as the plane has them.
     */
    val parts: List<CutPreviewPart>? = null,
    val partsPlane: Transform3? = null,
    /** Where the pieces went when the engine described the plane. */
    val describedParts: List<Boolean>? = null,
    /** "Draw cut line" (Shift + drag): the next drag of a finger lays the plane across its line. */
    val drawingLine: Boolean = false,
    /** The copy's offset, which the cut line checks its plane against. */
    val instanceOffset: Vector3 = Vector3(0.0, 0.0, 0.0),
    /** The gizmo's snapshots (on_save()), the one shown at [snapshot]. */
    val snapshots: List<CutSnapshot> = emptyList(),
    val snapshot: Int = 0,
) {
    /** m_bb_center, where reset_cut_plane() puts the plane. */
    val boundsCenter: Vector3?
        get() {
            val min = boundsMin ?: return null
            val max = boundsMax ?: return null
            return Vector3((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
        }

    /** m_radius: BoundingBoxf3::radius(), half the diagonal of the bounding box. */
    val radius: Double
        get() {
            val min = boundsMin ?: return 0.0
            val max = boundsMax ?: return 0.0
            val x = max.x - min.x
            val y = max.y - min.y
            val z = max.z - min.z
            return 0.5 * kotlin.math.sqrt(x * x + y * y + z * z)
        }

    /** is_cut_plane_init: the plane stands where reset_cut_plane() puts it. */
    val planeAtStart: Boolean
        get() {
            val plane = plane ?: return true
            return CutPlanes.isUnrotated(plane) && CutPlanes.center(plane) == boundsCenter
        }

    /**
     * can_perform_cut() of a planar cut: something is kept, the connectors'
     * window is closed and none of them is invalid.
     */
    val canPerform: Boolean
        get() = plane != null && (keepUpper || keepLower) && !editingConnectors &&
            if (kind == CutKind.DOVETAIL) grooveValid else connectorsValid && !(parts != null && partsOneObject)

    /** The pieces as the engine takes them: where they were made and where each goes. */
    val partSelection: CutPartSelection?
        get() {
            val pieces = parts ?: return null
            val at = partsPlane ?: return null
            return CutPartSelection(at, pieces.map(CutPreviewPart::upper))
        }

    /** PartSelection::is_one_object(): every solid piece goes to the part the first one goes to. */
    val partsOneObject: Boolean
        get() {
            val pieces = parts ?: return true
            if (pieces.size < 2) return true
            return pieces.all { it.modifier || it.upper == pieces.first().upper }
        }

    /** has_valid_groove(), as the engine found it for this very plane and grooves. */
    val grooveValid: Boolean
        get() = describedPlane == plane && describedGroove == groove && described?.validGroove == true

    /** The parts the dovetail cut makes, shown in the object's place while nothing is dragged. */
    val previewParts: List<CutPreviewPart>
        get() = when {
            kind == CutKind.DOVETAIL && !shaping && describedPlane == plane && describedGroove == groove -> described?.previewParts.orEmpty()
            kind == CutKind.PLANAR -> parts.orEmpty()
            else -> emptyList()
        }

    /** m_invalid_connectors_idxs is empty, as the engine found it for these very connectors. */
    val connectorsValid: Boolean
        get() = connectors.isEmpty() || (describedConnectors == connectors && describedParts == parts?.map(CutPreviewPart::upper) && described?.invalidConnectors.isNullOrEmpty())

    /** The invalid connectors, as far as the engine described these very ones. */
    val invalidConnectors: Set<Int>
        get() = if (describedConnectors == connectors) described?.invalidConnectors.orEmpty().toSet() else emptySet()

    /** "Add connectors" (or "Edit connectors"): both parts kept, not cut to parts, and pieces going to both. */
    val canEditConnectors: Boolean
        get() = keepUpper && keepLower && !keepAsParts && kind == CutKind.PLANAR && !(parts != null && partsOneObject)

    /** render_build_size(): the size of the transformed bounding box. */
    val buildVolume: Vector3?
        get() = described?.let { Vector3(it.max.x - it.min.x, it.max.y - it.min.y, it.max.z - it.min.z) }

    val canUndo: Boolean get() = snapshot > 0
    val canRedo: Boolean get() = snapshot < snapshots.lastIndex

    /**
     * Plater::TakeSnapshot of a GizmoAction: the gizmo as it is now, the ones
     * undone before gone; nothing when it has not changed since the last one.
     */
    fun snapshotted(): CutMode =
        if (snapshots.getOrNull(snapshot) == current()) this else copy(snapshots = snapshots.take(snapshot + 1) + current(), snapshot = snapshot + 1)

    /** on_load() of the snapshot at [index]. */
    fun restored(index: Int): CutMode {
        val saved = snapshots[index]
        return copy(
            plane = saved.plane,
            keepUpper = saved.keepUpper,
            keepLower = saved.keepLower,
            flipUpper = saved.flipUpper,
            flipLower = saved.flipLower,
            connectors = saved.connectors,
            editingConnectors = saved.editingConnectors,
            kind = saved.kind,
            groove = groove.copy(
                depth = saved.groove.depth,
                width = saved.groove.width,
                flapsAngle = saved.groove.flapsAngle,
                angle = saved.groove.angle,
                depthTolerance = saved.groove.depthTolerance,
                widthTolerance = saved.groove.widthTolerance,
            ),
            selectedConnectors = emptySet(),
            connectorSettings = connectorSettings.validated(),
            // on_load(): the pieces go (reset_cut_by_contours()).
            parts = null,
            partsPlane = null,
            snapshot = index,
        )
    }

    fun current() = CutSnapshot(plane, keepUpper, keepLower, flipUpper, flipLower, connectors, editingConnectors, kind, groove)

    /**
     * init_input_window_data() and validate_connector_settings(): the window
     * shows what the selected connectors have in common, and with none
     * selected what it adds, anything left undefined taking its default.
     */
    fun withSelection(selected: Set<Int>): CutMode {
        val chosen = selected.mapNotNull(connectors::getOrNull)
        return copy(
            selectedConnectors = selected,
            connectorSettings = if (chosen.isEmpty()) connectorSettings.validated() else CutConnectorSettings.of(chosen),
        )
    }

    companion object {
        // GLGizmoCut3D::m_snap_space_proportion and m_snap_bulge_proportion.
        const val SNAP_SPACE = 0.3
        const val SNAP_BULGE = 0.15
    }
}

/**
 * The connector GLGizmoCut3D's window adds and sets: m_connector_type,
 * m_connector_style, m_connector_shape_id, the depth (m_connector_depth_ratio,
 * in millimetres) and the size across (m_connector_size) with their
 * tolerances, and the turn in radians (m_connector_angle). A value is null
 * where the selected connectors differ (UndefFloat and Undef).
 */
data class CutConnectorSettings(
    val type: CutConnectorType? = CutConnectorType.PLUG,
    val style: CutConnectorStyle? = CutConnectorStyle.PRISM,
    val shape: CutConnectorShape? = CutConnectorShape.CIRCLE,
    val depth: Double? = 3.0,
    val depthTolerance: Double? = 0.1,
    val size: Double? = 2.5,
    val sizeTolerance: Double? = 0.0,
    val angle: Double? = 0.0,
) {
    /** validate_connector_settings() */
    fun validated() = CutConnectorSettings(
        type = type ?: CutConnectorType.PLUG,
        style = style ?: CutConnectorStyle.PRISM,
        shape = shape ?: CutConnectorShape.CIRCLE,
        depth = depth ?: 3.0,
        depthTolerance = depthTolerance ?: 0.1,
        size = size ?: 2.5,
        sizeTolerance = sizeTolerance ?: 0.0,
        angle = angle ?: 0.0,
    )

    companion object {
        /** init_input_window_data(): the values [connectors] share, null where they differ. */
        fun of(connectors: List<CutConnector>): CutConnectorSettings {
            fun <T> common(value: (CutConnector) -> T): T? = connectors.map(value).distinct().singleOrNull()
            return CutConnectorSettings(
                type = common { it.type },
                style = common { it.style },
                shape = common { it.shape },
                depth = common { it.height },
                depthTolerance = common { it.heightTolerance },
                size = common { 2.0 * it.radius },
                sizeTolerance = common { 2.0 * it.radiusTolerance },
                angle = common { it.zAngle },
            )
        }
    }
}

/** What GLGizmoCut3D::on_save() keeps of the planar cut, with the object's cut connectors. */
data class CutSnapshot(
    val plane: Transform3?,
    val keepUpper: Boolean,
    val keepLower: Boolean,
    val flipUpper: Boolean,
    val flipLower: Boolean,
    val connectors: List<CutConnector> = emptyList(),
    val editingConnectors: Boolean = false,
    val kind: CutKind = CutKind.PLANAR,
    val groove: CutGroove = CutGroove(),
)

/** GLGizmoCut3D::CutMode: a plane, or a plane with dovetail grooves. */
enum class CutKind { PLANAR, DOVETAIL }
