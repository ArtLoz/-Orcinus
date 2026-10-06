package app.orcinus.shadow.feature.prepare

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.AssemblyMode
import app.orcinus.shadow.core.model.BedTypeChoice
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BrimEarsSetup
import app.orcinus.shadow.core.model.BrimPoint
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ClippingPlane
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.CoordinateSystem
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
import app.orcinus.shadow.core.model.ListClipboard
import app.orcinus.shadow.core.model.MeasureHover
import app.orcinus.shadow.core.model.Measurement
import app.orcinus.shadow.core.model.MeshBooleanOperation
import app.orcinus.shadow.core.model.MeshBooleanPicks
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateNotice
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PlateValidationMessage
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.model.ProfileUpdatesNotice
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SliceNotice
import app.orcinus.shadow.core.model.SvgPreview
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeDescription
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.inverse
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.lockedPlates
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.meshErrors
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.core.model.plateSettingsChoice
import app.orcinus.shadow.core.model.scalingFactor
import app.orcinus.shadow.core.model.times
import app.orcinus.shadow.core.model.transformVector
import app.orcinus.shadow.core.model.translation
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.model.volumeMeshErrors
import app.orcinus.shadow.core.model.withInstance
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.core.model.withPartAt
import app.orcinus.shadow.core.ui.plate.SelectionMenuState
import app.orcinus.shadow.core.ui.plate.selectionMenuState
import app.orcinus.shadow.core.ui.plate.selectsSeveralObjects
import app.orcinus.shadow.domain.plate.SimplifyPreview
import app.orcinus.shadow.domain.plate.canAddPlate
import app.orcinus.shadow.domain.plate.canDeletePlate
import app.orcinus.shadow.domain.plate.hasAssembleView
import app.orcinus.shadow.domain.plate.layerEditingObject
import app.orcinus.shadow.domain.plate.measuredVolumes
import app.orcinus.shadow.domain.plate.presetValue
import app.orcinus.shadow.domain.plate.sameStyleAs
import app.orcinus.shadow.domain.plate.spiralVaseMode
import app.orcinus.shadow.render.scene.AssemblyTransforms
import app.orcinus.shadow.render.scene.CutPlanes
import app.orcinus.shadow.render.scene.LayerRangeHint
import app.orcinus.shadow.render.scene.PaintSectionView
import app.orcinus.shadow.render.scene.PlateClearance
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.VolumeScaleFrame
import app.orcinus.shadow.render.scene.WIPE_TOWER_INDEX
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

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

/** A notification of the sliced plate with the object, still on the plate, its "Jump to" selects. */
data class SliceNoticeView(val notice: SliceNotice, val jump: ValidationNotice?)

/** The object of the plate [notice] names among the [sliced] ones, as it stands now among [objects]. */
private fun sliceNotice(notice: SliceNotice, sliced: List<PlateObject>, objects: List<PlateObject>): SliceNoticeView {
    val mesh = sliced.getOrNull(notice.objectIndex)?.mesh
    val target = mesh?.let { objects.firstOrNull { plateObject -> plateObject.mesh == it } }
    return SliceNoticeView(
        notice = notice,
        jump = target?.let {
            ValidationNotice(
                text = "",
                target = PlateInstanceId(it.mesh, notice.instanceIndex.coerceAtLeast(0)),
                targetObject = it,
                copy = notice.instanceIndex >= 0,
            )
        },
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
    /** MenuFactory::multi_selection_menu() while the selection holds several objects whole; null otherwise. */
    val selectionMenu: SelectionMenuState? = null,
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
    /** The SVG tool (GLGizmoSVG), while it is open on an SVG volume. */
    val svg: SvgMode? = null,
    /** What the object list asks of the text or SVG tool, which the canvas places. */
    val embossRequest: EmbossRequest? = null,
    /** The measuring tool (GLGizmoMeasure), while it is open. */
    val measure: MeasureMode? = null,
    /** The copies the measuring tool measures: the selected ones. */
    val measuredCopies: Set<PlateInstanceId> = emptySet(),
    /** The meshes of the volumes it measures when the selection is a part; null for all of the copies' volumes. */
    val measuredVolumes: Set<ScenePath>? = null,
    /** GLGizmoMeasure::on_is_activable(): something is selected to measure. */
    val canMeasure: Boolean = false,
    /** GLGizmoAssembly::on_is_activable(): at least two volumes are selected. */
    val canAssemble: Boolean = false,
    /** The brim ears tool (GLGizmoBrimEars), while it is open. */
    val brimEars: BrimEarsMode? = null,
    /** GLGizmoBrimEars::on_is_activable(): a single copy is selected whole. */
    val canEditBrimEars: Boolean = false,
    /** The mesh boolean tool (GLGizmoMeshBoolean), while it is open. */
    val meshBoolean: MeshBooleanMode? = null,
    /** GLGizmoMeshBoolean::on_is_activable(): a single copy is selected whole, of an object of several volumes. */
    val canMeshBoolean: Boolean = false,
    /** The assembly view (AssembleView), while it shows in the 3D view's place. */
    val assemblyView: AssemblyViewMode? = null,
    /** Plater::priv::has_assemble_view(): a copy has a place in the assembly view, which the toolbar's "Assembly View" opens. */
    val canOpenAssemblyView: Boolean = false,
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
    /** The displayed type of every filament of the plate, which the assembly view's filament buttons show. */
    val filamentDisplayTypes: List<String> = emptyList(),
    /** The tower as the engine last described it, which the object menu's Flush Options go by. */
    val flushing: WipeTower = WipeTower(),
    /** What "Copy Process Settings" took. */
    val settingsClipboard: SettingsClipboard? = null,
    /** The height ranges the object list copied, which Paste puts into the selected object. */
    val listClipboard: ListClipboard? = null,
    /** The height range the object list edits, which the 3D view draws (Selection::render_sidebar_layers_hints()). */
    val layerRangeHint: LayerRangeHint? = null,
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
    /** The notifications of the current plate's G-code, with the objects their "Jump to" selects. */
    val sliceNotices: List<SliceNoticeView> = emptyList(),
    /** The process names post-processing scripts, which the G-code was sliced without. */
    val postProcessSkipped: Boolean = false,
    /** GLCanvas3D::reload_scene()'s warnings of the plate's filaments. */
    val plateNotices: List<PlateNotice> = emptyList(),
    /** EWarning::PrimeTowerOutside: the tower of the current plate's slice reaches beyond the plate. */
    val primeTowerOutside: Boolean = false,
    /** PlateState.seqPrintInfo: the advice to arrange a plate printed by object. */
    val seqPrintInfo: Boolean = false,
    /** PlateState.exportFinished: the file the last export wrote, while its notification shows. */
    val exportFinished: String? = null,
    /** PlateState.simplifySuggestions: the objects advised to be simplified. */
    val simplifySuggestions: List<PlateObject> = emptyList(),
    /** PlateState.profileUpdates: "Configuration can update now." shows while it is notified. */
    val profileUpdates: ProfileUpdatesNotice? = null,
    /** PlateState.profileUpdatesInstalled: the packages a forced update installed. */
    val profileUpdatesInstalled: List<ProfileUpdate> = emptyList(),
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
    /** The plates with settings of their own (PartPlate's settings icon). */
    val customizedPlates: Set<Int> = emptySet(),
    /** What PlateSettingsDialog shows for the current plate. */
    val plateSettings: PlateSettingsChoice = PlateSettingsChoice(),
    /** PlateSettingsDialog opens with the filament sequences alone (PlateState.layerSequencePrompt). */
    val layerSequencePrompt: Boolean = false,
    /** GLGizmoCut3D::on_is_selectable(): the toolbar has Cut unless the app is in simple mode. */
    val cutSelectable: Boolean = true,
    val bedTypes: List<BedTypeChoice> = emptyList(),
    /** PlateSettingsDialog chooses a plate's own type: a Bambu Lab printer. */
    val plateBedTypeSelectable: Boolean = false,
    /** The current plate prints in spiral vase mode, its own or the process preset's. */
    val spiralVaseMode: Boolean = false,
    /** The printer's structure is I3 (printer_structure). */
    val printerI3: Boolean = false,
    /** What the info notification shows of the selection (Plater::show_object_info()); null for nothing. */
    val objectInfo: ObjectInfo? = null,
    /**
     * Several copies selected together in the 3D view, which the gizmos and
     * their windows work on as one ("Group Operations"); null otherwise.
     */
    val group: GroupSelection? = null,
    /** Position of the selected object. */
    val selectedPosition: ObjectPosition?,
    /** The move window shows "Object coordinates" for the selected copy (ECoordinatesType::Instance). */
    val moveObjectCoordinates: Boolean = false,
    /** The move window offers "Object coordinates": one copy is selected, not the wipe tower. */
    val canMoveObjectCoordinates: Boolean = false,
    /** The move gizmo's reference system in object coordinates: the copy's placement (or its place in the assembly). */
    val moveFrame: Transform3? = null,
    /**
     * Selection::Volume: the volume of the one selected copy the object list
     * selected alone, which the move gizmo and its window move ("Volume
     * Operations"); null while copies are selected.
     */
    val selectedVolume: SelectedVolume? = null,
    /** The volumes drawn selected besides: the connectors of the copy while the list selected them. */
    val highlightedVolumes: Set<String> = emptySet(),
    /** UpdatedItemsInfo: the objects the last load brought as parts of a cut object, and the loads that told of them. */
    val cutPartsLoaded: Int = 0,
    val cutPartsLoads: Int = 0,
    /** The scale window's coordinates for the selected volume; null while copies are selected (world coordinates). */
    val scaleCoordinates: CoordinateSystem? = null,
    /** The scale gizmo of the selected volume, once the engine measured it. */
    val volumeScale: VolumeScaleFrame? = null,
    /**
     * A tool of the canvas is open (PrepareViewState.gizmoOpen), which stays
     * so while the engine rewrites what the tool works on.
     */
    val toolOpen: Boolean = false,
    /** The "Section view" of the painting tool on the painted copy, or of the brim ears tool on its copy. */
    /**
     * Selection::get_bounding_sphere() of the selected volume, which its
     * rotation turns it about: the engine's, or in the assembly view where the
     * volume stands there with the explosion (GLVolume::world_matrix()).
     */
    val volumeSphere: BoundingSphere? = null,
    val paintSection: PaintSectionView? = null,
    /**
     * GLGizmoMmuSegmentation::update_used_filaments(): the filaments, 0-based,
     * the painted object uses, as its model parts' own and as paint.
     */
    val paintedFilaments: List<Int> = emptyList(),
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
    /**
     * ObjectFilamentResults::partly_outside_objects of check_outside_state():
     * the objects with a printable copy across the plate's boundary or above
     * its height, which construct_error_string() names.
     */
    val clashedObjects: List<PlateObject> = emptyList(),
    val slicing: PlateSlicing?,
    /** PlateState.slicesCompleted, which "Slice ok." follows. */
    val slicesCompleted: Int = 0,
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
     * EWarning::SomethingNotShown of toggle_model_objects_visibility(): a tool
     * that shows its object alone (the InstancesHider of the painting tools,
     * the cut, the brim ears and the mesh boolean) is open while the project
     * has another object or copy.
     */
    val somethingNotShown: Boolean
        get() = (painting != null || cut != null || brimEars != null || meshBoolean != null) &&
            (sceneObjects.size > 1 || (sceneObjects.firstOrNull()?.instances?.size ?: 0) > 1)

    /**
     * GLGizmoMove3D, GLGizmoRotate3D and GLGizmoScale3D::on_is_activable():
     * anything selected, a group of copies too; GLGizmoFlatten's is a single
     * full instance.
     */
    fun canOpen(gizmo: PlateGizmo): Boolean = if (gizmo == PlateGizmo.LAY_ON_FACE) canManipulate else canEditPlate && (selectedObject != null || group != null)

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
    val canPasteOnPlate: Boolean
        get() = canEditPlate && (clipboard is PlateClipboard.Objects || listClipboard?.holdsRanges == true && selectedPlateObject != null)

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
    /** "Vertical" (m_vertical_only): a stroke keeps to a screen column; "Horizontal" (m_horizontal_only), to a row. */
    val verticalOnly: Boolean = false,
    val horizontalOnly: Boolean = false,
    /** The colour tool's "Edge detection" (m_detect_geometry_edge): its fill keeps to the smart fill angle. */
    val edgeDetection: Boolean = true,
    /** The height range's height in millimetres (m_cursor_height). */
    val cursorHeight: Double = 0.2,
    /**
     * "Remap filaments" (m_extruder_remap): the filament, 0-based, each one
     * becomes; none or the filament itself for one left as it is.
     */
    val remap: List<Int> = emptyList(),
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
    /**
     * "Section view" (ObjectClipper): how far its plane has gone through the
     * copy, 0 to 1, 0 clipping nothing; how many times "Reset direction" was
     * pressed; the plane the 3D view placed; and the cut the engine made.
     */
    val sectionPosition: Double = 0.0,
    val sectionResets: Int = 0,
    val sectionPlane: ClippingPlane? = null,
    val section: ScenePath? = null,
) {
    /** ToolType::BRUSH: the strokes "Vertical" and "Horizontal" keep to a column or a row. */
    val brushing: Boolean get() = tool == PaintTool.BRUSH || tool == PaintTool.CIRCLE || tool == PaintTool.TRIANGLE || tool == PaintTool.HEIGHT_RANGE
}

/**
 * The assembly view while it shows (AssembleView): its explosion ratio
 * (GLCanvas3D::m_explosion_ratio, 1 to 3), the copies its menu's "Hide" made
 * faint (GLCanvas3D::set_selected_visible()), and "Section View"
 * (ModelObjectsClipper): its position from 0 to 1, how many times "Reset
 * direction" was pressed, and the cut the engine made.
 */
data class AssemblyViewMode(
    val explosionRatio: Double = 1.0,
    val hidden: Set<PlateInstanceId> = emptySet(),
    val sectionPosition: Double = 0.0,
    val sectionResets: Int = 0,
    val section: ScenePath? = null,
    /** "Selection Mode": "Part" (true) or "Object". */
    val partSelection: Boolean = false,
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
    /** The SVG tool, while it is open. */
    val svg: SvgMode? = null,
    /** The layers of the object the variable layer height edits, as the engine described them last. */
    val layerDescription: Pair<ScenePath, LayerEditing>? = null,
    /** The height under the finger on the variable layer height bar. */
    val layerCursor: Double? = null,
    /** The measuring tool, while it is open. */
    val measure: MeasureMode? = null,
    /** The brim ears tool, while it is open. */
    val brimEars: BrimEarsMode? = null,
    /** The mesh boolean tool, while it is open. */
    val meshBoolean: MeshBooleanMode? = null,
    /**
     * The copy the move window shows in "Object coordinates"; another copy
     * selected shows world coordinates again (GLGizmoMove3D::change_cs_by_selection()).
     */
    val moveObjectCoordinatesCopy: PlateInstanceId? = null,
    /**
     * The volume the move window shows in "World coordinates"; another volume
     * selected shows object coordinates again (change_cs_by_selection()).
     */
    val moveWorldVolume: VolumeIndex? = null,
    /**
     * The selected volume as the engine measured it last, which the rotation
     * gizmo turns about; kept while the same volume stays selected, as a turn
     * about its sphere's centre leaves the sphere where it was.
     */
    val volumeDescription: DescribedVolume? = null,
    /**
     * GizmoObjectManipulation::m_init_rotation_scale_tran of the selected
     * volume: its transformation when the rotation tool opened or it was
     * selected, which "Reset current rotation" goes back to.
     */
    val volumeRotationStart: Transform3? = null,
    /**
     * The scale window's coordinates picked for a volume; another volume
     * selected scales in its own coordinates again (change_cs_by_selection()).
     */
    val volumeScaleCoordinates: VolumeCoordinates? = null,
    /**
     * Its "Delete input" of the difference and of the intersection, which the
     * desktop tool keeps from one opening to the next.
     */
    val meshBooleanDeleteDifference: Boolean = false,
    val meshBooleanDeleteIntersection: Boolean = false,
    /** The assembly view shows in the 3D view's place. */
    val assemblyView: Boolean = false,
    /** GLCanvas3D::m_explosion_ratio of the assembly view, which it keeps until the project starts afresh (reset_explosion_ratio()). */
    val explosionRatio: Double = 1.0,
    /** The copies the assembly view's "Hide" made faint, which it keeps while it shows them. */
    val assemblyHidden: Set<PlateInstanceId> = emptySet(),
    /** The assembly view's "Section View" (m_clp_ratio), the times "Reset direction" was pressed, and the cut the engine made. */
    val sectionPosition: Double = 0.0,
    val sectionResets: Int = 0,
    val assemblySection: ScenePath? = null,
    /**
     * The assembly view's "Selection Mode": "Part" locks its selection to
     * volumes (Selection::lock_volume_selection_mode()), so a tap picks the
     * volume itself; it keeps while the app runs, as the assembly canvas's
     * selection keeps it.
     */
    val assemblyPartSelection: Boolean = false,
    /** Selection::get_bounding_sphere() of a group of copies, as the 3D view found it for the rotation window. */
    val groupSphere: BoundingSphere? = null,
) {
    /**
     * GLGizmosManager::get_current_type() != Undefined: a gizmo is open — the
     * manipulations, the painting tools, the cut, simplify, the text and SVG
     * tools, measure and assembly, the brim ears and the mesh boolean.
     */
    val gizmoOpen: Boolean
        get() = gizmo != null || painting != null || cut != null || simplify != null || text != null || svg != null ||
            measure != null || brimEars != null || meshBoolean != null

    /** GLGizmoMeshBoolean::on_save() while the tool is open. */
    fun meshBooleanPicks(): MeshBooleanPicks? = meshBoolean?.let {
        MeshBooleanPicks(it.copy, it.operation, it.selectingTool, it.source, it.tool, meshBooleanDeleteDifference, meshBooleanDeleteIntersection)
    }
}

/**
 * GLGizmoMeshBoolean while it is open on [copy]: its operation, whether a
 * finger picks the tool rather than the source (MeshBooleanSelectingState),
 * the volumes picked (VolumeInfo::volume_idx, ModelObject::volumes), and
 * whether the operation deletes its input.
 */
data class MeshBooleanMode(
    val copy: PlateInstanceId,
    val operation: MeshBooleanOperation = MeshBooleanOperation.UNION,
    val selectingTool: Boolean = false,
    val source: Int? = null,
    val tool: Int? = null,
    val deleteInput: Boolean = true,
)

/**
 * Selection of several copies in the 3D view (GizmoObjectManipulation's
 * "Group Operations"): the copies, the box of them all in the world, whether
 * a part of a cut among several objects keeps the scale uniform
 * (enable_ununiversal_scale(false)), and the smallest sphere around them,
 * which they turn about, once the 3D view found it.
 */
data class GroupSelection(
    val copies: List<PlateInstanceId>,
    val center: Vector3,
    val size: Vector3,
    val uniformOnly: Boolean,
    val sphere: BoundingSphere? = null,
)

/** ObjectClipper's m_active_inst_bb_radius: the radius of the copy's box. */
private fun clipperRadius(copy: SceneCopy): Double {
    val size = copy.instance.inspection.dimensions
    return 0.5 * sqrt(size.widthMillimeters.pow(2) + size.depthMillimeters.pow(2) + size.heightMillimeters.pow(2))
}

/**
 * GLGizmoBrimEars while it is open on [copy]: what the engine opened it with
 * ([setup]), the ears while one is dragged ([draft]; the object's otherwise),
 * the selected ones, the ear the finger holds (m_hover_id), the head
 * diameter of a new ear (m_new_point_head_diameter; null until the engine
 * told the default), the window's "Max angle" and "Detection radius", the
 * ears that touch nothing (find_single()), the point of the copy under the
 * finger (render_hover_point), in the object's coordinates, and "Section
 * view" (ObjectClipper with a level plane): how far its plane has come down
 * through the copy, 0 to 1, 0 clipping nothing, the plane the 3D view placed,
 * and the cut the engine made.
 */
data class BrimEarsMode(
    val copy: PlateInstanceId,
    val setup: BrimEarsSetup? = null,
    val draft: List<BrimPoint>? = null,
    val selected: Set<Int> = emptySet(),
    val held: Int? = null,
    val headDiameter: Double? = null,
    val maxAngle: Double = 125.0,
    val detectionRadius: Double = 1.0,
    val invalid: Set<Int> = emptySet(),
    val hover: Vector3? = null,
    val sectionPosition: Double = 0.0,
    val sectionPlane: ClippingPlane? = null,
    val section: ScenePath? = null,
) {
    /** clp_dist != 0. ? clp : nullptr: the plane a press passes by while the section clips. */
    val clipping: ClippingPlane? get() = sectionPlane.takeIf { sectionPosition > 0.0 }
}

/**
 * GLGizmoMeasure while it is open: the selections and what they measure as
 * the engine told them last, what the finger is on while it explores, and
 * whether a finger selects points (m_mode, which the desktop app's Shift
 * switches).
 */
data class MeasureMode(
    val measurement: Measurement = Measurement(),
    val hover: MeasureHover? = null,
    val pointSelection: Boolean = false,
    /** m_editing_distance: "Edit to scale" asks for the distance the label read, in millimetres. */
    val editingDistance: Double? = null,
    /** The assembly tool's mode (GLGizmoAssembly); null for the measuring tool. */
    val assembly: AssemblyMode? = null,
    /** m_flip_volume_2: "Flip by Face 2", which turns the second volume over each time it changes. */
    val flipVolume2: Boolean = false,
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
    /** StyleManager's styles, which the style list offers. */
    val styles: List<TextStyle> = emptyList(),
    /** The stored style [style] comes from (StyleManager's style_index); null for a temporary style. */
    val styleIndex: Int? = null,
    val unknownFont: Boolean = false,
    val advanced: Boolean = false,
    val busy: Boolean = false,
    /** A message box of the window. */
    val notice: TextNotice? = null,
    /** draw_delete_style_button()'s question about the style of this name. */
    val deleting: String? = null,
    /** GLGizmoEmboss::m_keep_up: the lock beside Rotation. */
    val keepUp: Boolean = true,
) {
    /** Whether the text is white spaces alone, which embosses nothing (is_text_empty()). */
    val blank: Boolean get() = text.isBlank()

    /** ModelVolume::is_the_only_one_part(): the text is its object, which takes no other operation. */
    val onlyPart: Boolean get() = described?.onlyPart != false

    /** StyleManager::get_stored_style() */
    val storedStyle: TextStyle? get() = styleIndex?.let(styles::getOrNull)

    /** draw_style_list()'s is_modified: the window changed the stored style it shows. */
    val styleModified: Boolean get() = storedStyle?.let { !it.sameStyleAs(style) } == true

    /** Reset loads the first style, while the window's is another (is_changed_from_default_style()). */
    val resettable: Boolean get() = styles.firstOrNull()?.let { !it.sameStyleAs(style) } == true
}

/**
 * GLGizmoSVG while it is open on the SVG [volume]: what the engine tells of it
 * ([described]: the file's name, its size, depth, use of the surface, angle,
 * distance and type), its picture and warnings ([preview]; [previewVersion]
 * counts the pictures written into the same file), the locks of its up and
 * of its ratio, and whether the engine works on it.
 */
data class SvgMode(
    val volume: ObjectPartId,
    val described: EmbossVolume? = null,
    val preview: SvgPreview? = null,
    val previewVersion: Int = 0,
    val keepUp: Boolean = true,
    val keepRatio: Boolean = true,
    val busy: Boolean = true,
) {
    /** ModelVolume::is_the_only_one_part(): the SVG is its object, which takes no other operation. */
    val onlyPart: Boolean get() = described?.onlyPart != false
    val depth: Double get() = described?.style?.depth ?: 0.0
    val useSurface: Boolean get() = described?.style?.useSurface == true

    /** m_angle and m_distance: calc_angle() and calc_distance() of where it stands. */
    val angle: Double? get() = described?.style?.angle
    val distance: Double? get() = described?.style?.distance

    /** draw_size()'s can_reset: the world scales its width or height. */
    val scaled: Boolean get() = described?.let { it.scaleWidth != 1.0 || it.scaleHeight != 1.0 } == true
}

/** The message boxes of the text tool's style list. */
sealed interface TextNotice {
    /** "Not valid style.": the style's font does not load, and it left the list. */
    data class InvalidStyle(val name: String) : TextNotice

    /** "Can't remove the last existing style." */
    data object LastStyle : TextNotice
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

/** Selection::get_selected_single_volume(): the object's index on the plate and the volume's in it (ModelObject::volumes). */
data class VolumeIndex(val objectIndex: Int, val volume: Int)

/**
 * A volume selected alone: [id] in the object list, [mesh] the 3D view draws
 * it from, [index] as the desktop selection counts it, and [matrix] its
 * transformation in the object.
 */
data class SelectedVolume(
    val id: ObjectPartId,
    val mesh: ScenePath,
    val index: VolumeIndex,
    val matrix: Transform3,
    /** Its sphere and boxes where it stands, once the engine measured them; null until then. */
    val description: VolumeDescription? = null,
)

/** The coordinates a window works in for the volume [index]. */
data class VolumeCoordinates(val index: VolumeIndex, val coordinates: CoordinateSystem)

/** A volume selected alone, by [index], as the engine measured it last. */
data class DescribedVolume(val index: VolumeIndex, val description: VolumeDescription)

/** What Plater::show_object_info() shows of the selection. */
sealed interface ObjectInfo {
    /** "Number of currently selected objects": the objects of the selected copies. */
    data class Count(val objects: Int) : ObjectInfo

    /**
     * One copy of [plateObject], or its volume [part] selected alone: the size
     * in the world, the volume in cubic millimetres, the triangles, the open
     * edges and the errors the repair of its file fixed
     * (ObjectList::get_mesh_errors_info()).
     */
    data class Single(
        val plateObject: PlateObject,
        val part: Int?,
        val size: Vector3,
        val volume: Double,
        val facets: Long,
        val openEdges: Long,
        val repairedErrors: Int = 0,
    ) : ObjectInfo
}

/**
 * Plater::show_object_info(), which the assembly view hides: several volumes
 * selected count the objects (a copy of an object of several volumes counts
 * as several, as there); one copy, or a model part selected alone, is
 * described; all copies of one object, a modifier alone or the wipe tower
 * alone show nothing.
 */
internal fun PlateState.objectInfo(view: PrepareViewState, volume: SelectedVolume?): ObjectInfo? {
    if (view.assemblyView) return null
    if (volume != null) {
        // Selection::is_single_volume(): a model part.
        val owner = objects.getOrNull(volume.index.objectIndex) ?: return null
        if (owner.volumeAt(volume.index.volume)?.type != VolumeType.PART) return null
        val described = volume.description ?: return null
        return ObjectInfo.Single(
            owner,
            volume.index.volume,
            described.world.size,
            described.volume,
            described.facets,
            described.openEdges,
            owner.volumeMeshErrors(volume.index.volume).repairedCount,
        )
    }
    val owners = selectedObjects()
    // Selection::get_volume_idxs(): every volume of every selected copy, and the tower.
    val tower = wipeTower != null && view.wipeTowerSelected
    val volumes = selectedInstances.sumOf { id -> owners.firstOrNull { it.mesh == id.mesh }?.let { it.parts.size + 1 } ?: 0 } + (if (tower) 1 else 0)
    val owner = owners.singleOrNull()?.takeIf { !tower }
    val fullObject = owner != null && selectedInstances.size == owner.instances.size
    if (volumes > 1 && !fullObject) return ObjectInfo.Count(owners.size + (if (tower) 1 else 0))
    if (owner == null || (fullObject && owner.instances.size > 1)) return null
    val inspection = selectedCopy?.inspection ?: return null
    val size = inspection.dimensions.let { Vector3(it.widthMillimeters, it.depthMillimeters, it.heightMillimeters) }
    return ObjectInfo.Single(owner, null, size, inspection.volume, inspection.facetCount, inspection.openEdges, owner.meshErrors.repairedCount)
}

/**
 * Selection::Volume: the volume of the one selected copy the object list, or
 * the assembly view's "Part" selection, selected alone.
 */
internal fun PlateState.selectedVolume(view: PrepareViewState): SelectedVolume? {
    val part = selectedPart ?: return null
    if (view.wipeTowerSelected || selectedInstances.singleOrNull()?.mesh != part.mesh) return null
    val objectIndex = objects.indexOfFirst { it.mesh == part.mesh }
    val volume = objects.getOrNull(objectIndex)?.volumeAt(part.index) ?: return null
    val index = VolumeIndex(objectIndex, part.index)
    return SelectedVolume(part, volume.mesh, index, volume.placement, view.volumeDescription?.takeIf { it.index == index }?.description)
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
    // GizmoObjectManipulation in the assembly view: the copy's assemble transformation.
    val assembled = selectedObject?.let(copies::get)?.instance?.takeIf { view.assemblyView }?.let { it.assemble ?: Transform3.IDENTITY }
    val assembledRotation = assembled?.let(AssemblyTransforms::rotationDegrees)
    val volume = selectedVolume(view)
    // update_settings_value() of a volume: the rotation of its own transformation.
    val volumeRotation = volume?.matrix?.let(AssemblyTransforms::rotationDegrees)
    // GLGizmoScale3D::change_cs_by_selection(): a volume scales in its own coordinates until others are picked.
    val scaleCoordinates = volume?.let { selected -> view.volumeScaleCoordinates?.takeIf { it.index == selected.index }?.coordinates ?: CoordinateSystem.LOCAL }
    val volumeBox = volume?.description?.let { described ->
        when (scaleCoordinates) {
            CoordinateSystem.WORLD -> described.world
            CoordinateSystem.INSTANCE -> described.instance
            else -> described.local
        }
    }
    // GLGizmoMove3D::change_cs_by_selection(): a volume shows object coordinates, a copy world coordinates, until the other is picked.
    val moveObjectCoordinates = if (volume != null) {
        view.moveWorldVolume != volume.index
    } else {
        view.moveObjectCoordinatesCopy != null && view.moveObjectCoordinatesCopy == selectedInstance && selectedInstances.size == 1 && !view.wipeTowerSelected
    }
    // The plate changes once OrcaSlicer has the presets it places objects with.
    val canEditPlate = !busy && !slicingAll && engine.availability == EngineAvailability.READY && profiles != null
    // "Group Operations": several copies, and nothing of them alone, in the 3D view.
    val group = if (selectedInstances.size > 1 && selectedPart == null && !view.wipeTowerSelected && !view.assemblyView) {
        val chosen = copies.filter { it.id in selectedInstances }
        val boxes = chosen.map { it.instance.inspection }
        fun low(axis: (ModelInspection) -> Pair<Double, Double>) = boxes.minOf { axis(it).first - axis(it).second / 2 }
        fun high(axis: (ModelInspection) -> Pair<Double, Double>) = boxes.maxOf { axis(it).first + axis(it).second / 2 }
        val x = { it: ModelInspection -> it.boxCenter.x to it.dimensions.widthMillimeters }
        val y = { it: ModelInspection -> it.boxCenter.y to it.dimensions.depthMillimeters }
        val z = { it: ModelInspection -> it.boxCenter.z to it.dimensions.heightMillimeters }
        GroupSelection(
            copies = chosen.map { it.id },
            center = Vector3((low(x) + high(x)) / 2, (low(y) + high(y)) / 2, (low(z) + high(z)) / 2),
            size = Vector3(high(x) - low(x), high(y) - low(y), high(z) - low(z)),
            // GUI_ObjectList's disable_ununiform_scale: several objects, a part of a cut among them.
            uniformOnly = chosen.map { it.id.mesh }.distinct().size > 1 && chosen.any { it.plateObject.cutId != null },
            sphere = view.groupSphere,
        )
    } else {
        null
    }
    return PrepareUiState(
        plate = plate,
        importing = importing,
        // GLGizmoSimplify::init_model(): the decimated mesh is drawn in the volume's place.
        sceneObjects = view.simplify?.let { mode -> objects.withSimplified(mode, selectedInstance) } ?: objects,
        sceneCopies = copies,
        selectedObject = selectedObject,
        selectedObjects = selectedIndexes,
        selectionMenu = takeIf { !view.assemblyView && it.selectsSeveralObjects() }
            ?.let { selectionMenuState(it, canEditPlate, clipboard, settingsClipboard, emptyList()) },
        // GLGizmosManager::refresh_on_off_state(): a gizmo the selection can't take closes.
        gizmo = gizmo.takeIf { canEditPlate && (selectedObject != null || group != null && it != PlateGizmo.LAY_ON_FACE) },
        flatteningPlanes = if (gizmo == PlateGizmo.LAY_ON_FACE) view.flatteningPlanes else emptyList(),
        painting = view.painting?.takeIf { mode -> objects.any { it.mesh == mode.mesh } && canEditPlate },
        cut = view.cut?.takeIf { mode -> objects.any { it.mesh == mode.mesh } && canEditPlate },
        // The assembly view's canvas edits no layers; the 3D view's keeps them for when it shows again.
        layerEditing = layerEditingObject()?.takeIf { layerEditing && canEditPlate && !view.assemblyView }?.let { target ->
            LayerEditingMode(target.mesh, view.layerDescription?.takeIf { it.first == target.mesh }?.second, view.layerTools, view.layerCursor)
        },
        canEditLayers = canEditPlate && layerEditingObject() != null,
        text = view.text?.takeIf { mode -> objects.any { it.mesh == mode.volume.mesh } && canEditPlate },
        svg = view.svg?.takeIf { mode -> objects.any { it.mesh == mode.volume.mesh } && canEditPlate },
        embossRequest = embossRequest.takeIf { canEditPlate },
        measure = view.measure?.takeIf { canEditPlate },
        brimEars = view.brimEars?.takeIf { mode -> objects.any { it.mesh == mode.copy.mesh } && canEditPlate },
        canEditBrimEars = canEditPlate && selectedInstances.size == 1 && selectedPart == null,
        meshBoolean = view.meshBoolean?.takeIf { mode -> objects.any { it.mesh == mode.copy.mesh } && canEditPlate }?.let { mode ->
            mode.copy(
                deleteInput = when (mode.operation) {
                    MeshBooleanOperation.UNION -> true
                    MeshBooleanOperation.DIFFERENCE -> view.meshBooleanDeleteDifference
                    MeshBooleanOperation.INTERSECTION -> view.meshBooleanDeleteIntersection
                },
            )
        },
        canMeshBoolean = canEditPlate && !view.assemblyView && selectedPart == null &&
            selectedInstances.singleOrNull()?.let { copy -> objects.firstOrNull { it.mesh == copy.mesh }?.parts?.isNotEmpty() } == true,
        assemblyView = AssemblyViewMode(
            view.explosionRatio,
            view.assemblyHidden,
            view.sectionPosition,
            view.sectionResets,
            view.assemblySection,
            view.assemblyPartSelection,
        )
            .takeIf { view.assemblyView },
        canOpenAssemblyView = hasAssembleView(),
        measuredCopies = selectedInstances,
        measuredVolumes = selectedPart?.let { part -> objects.firstOrNull { it.mesh == part.mesh }?.volumeAt(part.index)?.mesh }?.let(::setOf),
        // In the assembly view the tools need an explosion ratio of 1 ("Please confirm
        // explosion ratio = 1"), and a copy there has its model parts alone.
        canMeasure = canEditPlate && measuredVolumes().isNotEmpty() && (!view.assemblyView || abs(view.explosionRatio - 1.0) < EXPLOSION_RATIO_EPSILON),
        canAssemble = canEditPlate && (!view.assemblyView || abs(view.explosionRatio - 1.0) < EXPLOSION_RATIO_EPSILON) && measuredVolumes().sumOf { volume ->
            if (volume.volumeIndex != null) {
                1
            } else {
                objects.getOrNull(volume.objectIndex)?.let { 1 + if (view.assemblyView) it.parts.count { part -> part.type == VolumeType.PART } else it.parts.size } ?: 0
            }
        } >= 2,
        wipeTower = wipeTower,
        builtWipeTower = result?.wipeTower,
        filamentColors = presets?.filamentColors.orEmpty().mapNotNull(::parseFilamentColor),
        filamentNames = profiles?.allFilaments.orEmpty().map { id ->
            presets?.filaments?.firstOrNull { it.name == id.value }?.label ?: id.value
        },
        filamentDisplayTypes = presets?.filamentDisplayTypes.orEmpty(),
        flushing = flushing,
        settingsClipboard = settingsClipboard,
        listClipboard = listClipboard,
        layerRangeHint = selectedLayerRange?.takeIf { !view.assemblyView }?.let { range ->
            val owner = selectedRange?.mesh
            LayerRangeHint(range.bottom, range.top, layerRangeEditor, copies.indices.filterTo(mutableSetOf()) { copies[it].id.mesh == owner })
        },
        simplify = view.simplify?.takeIf { mode -> objects.any { it.mesh == mode.volume.mesh } },
        wireframes = view.simplify?.takeIf { it.wireframe }?.preview?.let { setOf(it.mesh) }.orEmpty(),
        overhangNormalZ = overhangNormalZ,
        currentPlateCopies = copies.indices.filterTo(mutableSetOf()) { plateOf(copies[it].instance) == currentPlate },
        printSequence = validation?.sequence.orEmpty(),
        validationError = validation?.error?.let { notice(it, objects) },
        validationWarning = validation?.warning?.let { notice(it, objects) },
        sliceNotices = result?.let { sliced -> sliced.notices.map { sliceNotice(it, sliced.objects, objects) } }.orEmpty(),
        postProcessSkipped = result?.postProcessSkipped == true,
        plateNotices = validation?.notices.orEmpty(),
        primeTowerOutside = result?.primeTowerOutside == true,
        seqPrintInfo = seqPrintInfo,
        exportFinished = exportFinished,
        simplifySuggestions = simplifySuggestions.mapNotNull { mesh -> objects.firstOrNull { it.mesh == mesh } },
        profileUpdates = profileUpdates,
        profileUpdatesInstalled = profileUpdatesInstalled,
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
        customizedPlates = partPlates().indices.filterTo(mutableSetOf()) { partPlates()[it].settings.plateSettingsChoice() != PlateSettingsChoice() },
        plateSettings = plateSettings.plateSettingsChoice(),
        layerSequencePrompt = layerSequencePrompt,
        // wxGetApp().get_mode(), which the tabs describe their settings in.
        cutSelectable = settingsTabs[PresetKind.PRINT]?.settings?.mode != SettingsMode.SIMPLE,
        bedTypes = presets?.bedTypes.orEmpty(),
        plateBedTypeSelectable = presets?.plateBedTypeSelectable == true,
        spiralVaseMode = spiralVaseMode(),
        printerI3 = presetValue(PresetKind.PRINTER, "printer_structure") == "i3",
        group = group,
        selectedPosition = if (group != null) {
            // update_settings_value() of a group: "Translate" from where it stands.
            ObjectPosition(0.0, 0.0, 0.0)
        } else if (volume != null && selected != null) {
            // update_settings_value() of a volume: its offset in the object, or in the world.
            (if (moveObjectCoordinates) volume.matrix else selected.placement * volume.matrix).translation.let { ObjectPosition(it.x, it.y, it.z) }
        } else {
            (if (view.assemblyView) assembled else selected?.placement)?.columns?.let { ObjectPosition(it[12], it[13], it[14]) }
        },
        moveObjectCoordinates = moveObjectCoordinates,
        canMoveObjectCoordinates = selectedObject != null && selectedInstances.size == 1 && !view.wipeTowerSelected,
        moveFrame = (if (view.assemblyView) assembled else selected?.placement)?.takeIf { moveObjectCoordinates },
        selectedVolume = volume,
        cutPartsLoaded = cutPartsLoaded,
        cutPartsLoads = cutPartsLoads,
        highlightedVolumes = selectedConnectors?.takeIf { connectorsSelected }
            ?.let { copy -> objects.firstOrNull { it.mesh == copy.mesh } }
            ?.parts?.filter { it.cutInfo.connector }?.mapTo(HashSet()) { it.mesh.value }.orEmpty(),
        objectInfo = objectInfo(view, volume),
        selectedRotation = if (group != null) Vector3(0.0, 0.0, 0.0) else volumeRotation ?: if (view.assemblyView) assembledRotation else selected?.rotationDegrees,
        canResetRotation = when {
            group != null -> false
            // update_reset_buttons_visibility() of a volume: its own rotation against the one it started from.
            volume != null -> view.volumeRotationStart?.let { start -> !volume.matrix.hasLinearPartOf(start) } == true
            view.assemblyView -> assembled != null && rotationStart != null && !assembled.hasLinearPartOf(rotationStart)
            else -> selected != null && rotationStart != null && !selected.placement.hasLinearPartOf(rotationStart)
        },
        canResetRotationToZero = group == null && (volumeRotation ?: if (view.assemblyView) assembledRotation else selected?.rotationDegrees)
            ?.let { rotation -> listOf(rotation.x, rotation.y, rotation.z).any { abs(it) > 0.001 } } == true,
        selectedScale = if (group != null) {
            Vector3(100.0, 100.0, 100.0)
        } else if (volume != null) {
            // update_settings_value() of a volume: its own scaling factor in its own coordinates, 100 % in the others.
            if (scaleCoordinates == CoordinateSystem.LOCAL) {
                AssemblyTransforms.scalingFactor(volume.matrix).let { Vector3(it.x * 100.0, it.y * 100.0, it.z * 100.0) }
            } else {
                Vector3(100.0, 100.0, 100.0)
            }
        } else if (moveObjectCoordinates) {
            // update_settings_value() of a copy in object coordinates: its scaling factor.
            selected?.placement?.scalingFactor()?.let { Vector3(it.x * 100.0, it.y * 100.0, it.z * 100.0) }
        } else selected?.let {
            // update_settings_value() in world coordinates: size over the unscaled size.
            with(it.dimensions) {
                Vector3(
                    widthMillimeters / it.unscaledDimensions.widthMillimeters * 100.0,
                    depthMillimeters / it.unscaledDimensions.depthMillimeters * 100.0,
                    heightMillimeters / it.unscaledDimensions.heightMillimeters * 100.0,
                )
            }
        },
        selectedSize = when {
            group != null -> group.size
            volume != null -> volumeBox?.size
            // The bounding box in the copy's own axes: its unscaled size times its scaling factor.
            moveObjectCoordinates -> selected?.let { copy ->
                val factor = copy.placement.scalingFactor()
                with(copy.localDimensions) { Vector3(widthMillimeters * factor.x, depthMillimeters * factor.y, heightMillimeters * factor.z) }
            }
            else -> selected?.dimensions?.let { Vector3(it.widthMillimeters, it.depthMillimeters, it.heightMillimeters) }
        },
        scaleCoordinates = scaleCoordinates,
        paintedFilaments = view.painting?.takeIf { it.kind == PaintKind.COLOR }?.let { mode ->
            val target = objects.firstOrNull { it.mesh == mode.mesh } ?: return@let emptyList()
            val count = presets?.filamentColors.orEmpty().size
            // ModelVolume::extruder_id() of the model parts, then the painted states.
            fun used(own: Int) = (own.takeIf { it > 0 } ?: target.settings.extruderNumber).let { if (it > 0) it - 1 else 0 }
            val bases = listOf(used(target.volume.settings.extruderNumber)) +
                target.parts.filter { it.type == VolumeType.PART }.map { used(it.settings.extruderNumber) }
            val painted = target.paintedMeshes.filter { it.kind == PaintKind.COLOR }.map { it.state - 1 }
            (bases + painted).filter { it in 0 until count }.toSortedSet().toList()
        }.orEmpty(),
        toolOpen = view.gizmoOpen,
        volumeSphere = volume?.description?.sphere?.let { sphere ->
            val copy = selectedObject?.let(copies::get)
            if (!view.assemblyView || copy == null) return@let sphere
            val assemble = copy.instance.assemble ?: Transform3.IDENTITY
            val matrix = volume.matrix
            // AssemblyPlacement: the volume's explosion along its assemble offset and its own offset.
            val toAssembly = copy.instance.offsetToAssembly ?: Vector3(0.0, 0.0, 0.0)
            val own = matrix.translation
            val explosion = (assemble * matrix).transformVector(Vector3(toAssembly.x + own.x, toAssembly.y + own.y, toAssembly.z + own.z))
            val fromPlate = assemble * copy.instance.inspection.placement.inverse()
            val moved = fromPlate.transformVector(sphere.center).let { v ->
                val t = fromPlate.translation
                Vector3(v.x + t.x, v.y + t.y, v.z + t.z)
            }
            val ratio = view.explosionRatio - 1.0
            BoundingSphere(Vector3(moved.x + explosion.x * ratio, moved.y + explosion.y * ratio, moved.z + explosion.z * ratio), sphere.radius)
        },
        paintSection = view.painting?.let { mode ->
            val copy = selectedObject?.let(copies::get) ?: return@let null
            // ObjectClipper::set_position_by_ratio(): about the copy's offset, or in the
            // assembly view its assemble offset spread by the explosion, as far as the
            // radius of its box (m_active_inst_bb_radius) either side.
            val center = if (view.assemblyView) {
                val assemble = (copy.instance.assemble ?: Transform3.IDENTITY).translation
                val spread = copy.instance.offsetToAssembly ?: Vector3(0.0, 0.0, 0.0)
                val ratio = view.explosionRatio - 1.0
                Vector3(assemble.x + spread.x * ratio, assemble.y + spread.y * ratio, assemble.z + spread.z * ratio)
            } else {
                copy.instance.inspection.placement.translation
            }
            PaintSectionView(mode.sectionPosition, mode.sectionResets, center, clipperRadius(copy), mode.section.takeIf { mode.sectionPosition > 0.0 })
        } ?: view.brimEars?.let { mode ->
            // set_position_by_ratio(ratio, false, true): a level plane about the copy's offset.
            val copy = copies.firstOrNull { it.id == mode.copy } ?: return@let null
            val center = copy.instance.inspection.placement.translation
            PaintSectionView(mode.sectionPosition, 0, center, clipperRadius(copy), mode.section.takeIf { mode.sectionPosition > 0.0 }, level = true)
        },
        volumeScale = if (volume != null && selected != null && volumeBox != null) {
            val reference = when (scaleCoordinates) {
                CoordinateSystem.WORLD -> Transform3.IDENTITY
                CoordinateSystem.INSTANCE -> selected.placement
                else -> selected.placement * volume.matrix
            }
            VolumeScaleFrame(reference, volumeBox)
        } else {
            null
        },
        uniformScale = uniformScale,
        objectClashed = copies.any { it.instance.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE },
        clashedObjects = copies.filter { it.instance.printable && it.instance.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE }
            .map(SceneCopy::plateObject).distinct(),
        slicing = slicing,
        slicesCompleted = slicesCompleted,
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

/** GLGizmoMeasure::on_is_activable() in the assembly view: abs(explosion ratio - 1) < 1e-2. */
private const val EXPLOSION_RATIO_EPSILON = 1e-2

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
