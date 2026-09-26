package app.orcinus.shadow.core.model

/**
 * One copy of an object on the plate (ModelInstance), as OrcaSlicer placed it.
 * While [placing], OrcaSlicer has yet to settle the placement: the size and fit
 * still describe the one it last confirmed. With [autoDrop] off
 * (ModelInstance::auto_drop), manipulations leave the copy where the user put
 * it, above the plate included; [printable] is the check box of the object list.
 */
data class PlateInstance(
    val inspection: ModelInspection,
    val placing: Boolean = false,
    val autoDrop: Boolean = true,
    val printable: Boolean = true,
)

/** ModelVolumeType of Model.hpp: what a part of an object is for. */
enum class VolumeType {
    /** MODEL_PART: printed with the object. */
    PART,

    /** NEGATIVE_VOLUME: taken out of the object. */
    NEGATIVE,

    /** PARAMETER_MODIFIER: the settings of the part apply inside it alone. */
    MODIFIER,

    SUPPORT_BLOCKER,
    SUPPORT_ENFORCER,
}

/**
 * A part of an object (ModelVolume): one of the shapes OrcaSlicer generates, or
 * a volume the model file brought, standing in the object's coordinates, with
 * the settings it overrides. Every copy of the object has it.
 */
data class ObjectPart(
    /** "Cube", "Cylinder", "Sphere", "Slab", "Cone", "Disc" or "Torus"; empty for a volume of the file. */
    val shape: String,
    val type: VolumeType,
    /** The mesh file the 3D view draws it from. */
    val mesh: ScenePath,
    /** Its transformation in the object's coordinates. */
    val placement: Transform3,
    val settings: ModelSettings = ModelSettings(),
    /** The facets painted with the filaments of the plate (ModelVolume::mmu_segmentation_facets). */
    val painted: PaintedFacets = PaintedFacets(),
    /** The mesh a volume of the file is loaded from; null for a generated shape. */
    val source: ModelPath? = null,
    /** ModelVolume::name the file gave it; empty for a generated shape. */
    val name: String = "",
    /** ModelVolume::is_splittable(): its mesh has more than one shell. */
    val splittable: Boolean = false,
    /** ModelVolume::source: the units its mesh was converted from, which the object menu can restore. */
    val convertedFromInches: Boolean = false,
    val convertedFromMeters: Boolean = false,
    /**
     * ModelVolume::source.input_file: the file the volume was read from, whose
     * name "Replace all with 3D files" looks for; empty for a generated shape.
     */
    val inputFile: String = "",
)

/**
 * An object's own mesh (its first ModelVolume), which OrcaSlicer's object list
 * shows as a part of its own once the object has others
 * (ObjectList::add_volumes_to_object_in_list).
 */
data class ObjectVolume(
    /** ModelVolume::name; empty for a volume named after its object. */
    val name: String = "",
    /** ModelVolume::config: the settings its own row changes, such as its filament. */
    val settings: ModelSettings = ModelSettings(),
    /** As ObjectPart.splittable, its units and its file. */
    val splittable: Boolean = false,
    val convertedFromInches: Boolean = false,
    val convertedFromMeters: Boolean = false,
    val inputFile: String = "",
)

/**
 * A height range of an object (one entry of ModelObject::layer_config_ranges):
 * the slab between two heights of the object, printed with a layer height of
 * its own, which is what a range is for.
 */
data class LayerRange(
    val bottom: Double,
    val top: Double,
    val settings: ModelSettings = ModelSettings(),
)

/** The triangles of an object painted with one filament, as a mesh for the 3D view. */
data class PaintedMesh(val filament: Int, val mesh: ScenePath)

/**
 * A model on the build plate, as OrcaSlicer loaded it, with the parts added to
 * it (ModelObject::volumes) and the copies of it that stand on the plate
 * (ModelObject::instances). Every copy prints with the object's settings and
 * its parts, as the desktop app's copies share the ModelObject.
 */
sealed interface PlateObject {
    /** At least one; OrcaSlicer removes an object that loses its last copy. */
    val instances: List<PlateInstance>

    /** The process settings the object overrides (ModelObject::config). */
    val settings: ModelSettings

    /** The object's own mesh as a volume of it. */
    val volume: ObjectVolume

    /** The parts added to the object, after its own geometry. */
    val parts: List<ObjectPart>

    /** The height ranges of the object, from the bed up (layer_config_ranges). */
    val layerRanges: List<LayerRange>

    /** The facets of the object's own mesh painted with the filaments of the plate. */
    val painted: PaintedFacets

    /** The painted triangles as meshes, which the 3D view draws in the filament colours. */
    val paintedMeshes: List<PaintedMesh>

    /**
     * An object of a model file: [file] names its own mesh (its first volume)
     * and the object, and [frame] places that mesh in the object as the file
     * loaded it; without one the mesh is centred around the origin.
     */
    data class ImportedModel(
        val file: ImportedModelFile,
        override val instances: List<PlateInstance>,
        override val settings: ModelSettings = ModelSettings(),
        override val parts: List<ObjectPart> = emptyList(),
        override val layerRanges: List<LayerRange> = emptyList(),
        override val painted: PaintedFacets = PaintedFacets(),
        override val paintedMeshes: List<PaintedMesh> = emptyList(),
        val frame: Transform3? = null,
        override val volume: ObjectVolume = ObjectVolume(),
        /** The name of the document it came from (ModelObject::input_file), which names the G-code. */
        val inputName: String = file.displayName,
    ) : PlateObject

    /** The engine's built-in 20 mm calibration cube. */
    data class CalibrationCube(
        override val instances: List<PlateInstance>,
        override val settings: ModelSettings = ModelSettings(),
        override val parts: List<ObjectPart> = emptyList(),
        override val layerRanges: List<LayerRange> = emptyList(),
        override val painted: PaintedFacets = PaintedFacets(),
        override val paintedMeshes: List<PaintedMesh> = emptyList(),
        override val volume: ObjectVolume = ObjectVolume(),
        /** The name the user gave it; null for the app's own. */
        val name: String? = null,
    ) : PlateObject
}

/** The object named [name] (ObjectList::rename_item). */
fun PlateObject.withName(name: String): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(file = file.copy(displayName = name))
    is PlateObject.CalibrationCube -> copy(name = name)
}

/**
 * The object's volumes as the object list shows them (ModelObject::volumes):
 * its own mesh at 0, then its parts, or none while the object is its own mesh
 * alone, which the list shows no volume of.
 */
fun PlateObject.volumeAt(index: Int): ObjectPart? = when {
    parts.isEmpty() -> null
    index == 0 -> ObjectPart(
        shape = "",
        type = VolumeType.PART,
        mesh = mesh,
        placement = (this as? PlateObject.ImportedModel)?.frame ?: Transform3.IDENTITY,
        settings = volume.settings,
        painted = painted,
        name = volume.name,
        splittable = volume.splittable,
        convertedFromInches = volume.convertedFromInches,
        convertedFromMeters = volume.convertedFromMeters,
        inputFile = volume.inputFile,
    )
    else -> parts.getOrNull(index - 1)
}

/** The volume at [index] of [volumeAt] with the settings of [volume]; its own mesh keeps its geometry. */
fun PlateObject.withVolumeAt(index: Int, volume: ObjectPart): PlateObject =
    if (index == 0) withVolume(this.volume.copy(settings = volume.settings)) else withPartAt(index - 1, volume)

/** The object's own mesh as a volume replaced. */
fun PlateObject.withVolume(volume: ObjectVolume): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(volume = volume)
    is PlateObject.CalibrationCube -> copy(volume = volume)
}

/** The object with [part] added to it, as ObjectList::load_generic_subobject() adds one. */
fun PlateObject.withPart(part: ObjectPart): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(parts = parts + part)
    is PlateObject.CalibrationCube -> copy(parts = parts + part)
}

/** The parts of an object replaced, with everything else kept. */
fun PlateObject.withParts(parts: List<ObjectPart>): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(parts = parts)
    is PlateObject.CalibrationCube -> copy(parts = parts)
}

/** One part replaced, by its place in the object's list. */
fun PlateObject.withPartAt(index: Int, part: ObjectPart): PlateObject =
    withParts(parts.mapIndexed { at, existing -> if (at == index) part else existing })

/** The height ranges of an object replaced, kept in the order they print in. */
fun PlateObject.withLayerRanges(ranges: List<LayerRange>): PlateObject {
    val sorted = ranges.sortedBy(LayerRange::bottom)
    return when (this) {
        is PlateObject.ImportedModel -> copy(layerRanges = sorted)
        is PlateObject.CalibrationCube -> copy(layerRanges = sorted)
    }
}

/** One height range replaced, by its place in the object's list. */
fun PlateObject.withLayerRangeAt(index: Int, range: LayerRange): PlateObject =
    withLayerRanges(layerRanges.mapIndexed { at, existing -> if (at == index) range else existing })

/** The mesh file of the object's geometry, which its copies share and which names it. */
val PlateObject.mesh: ScenePath get() = instances.first().inspection.mesh

/** Whether any copy of the object is still being placed. */
val PlateObject.placing: Boolean get() = instances.any(PlateInstance::placing)

/**
 * The filament an object prints with: the desktop list shows filament 1 for an
 * object that was never given one (ObjectDataViewModel::GetExtruderNumber).
 */
val PlateObject.extruderNumber: Int get() = settings.extruderNumber.coerceAtLeast(1)

/** The colours an object is painted with, as the painting tool leaves them. */
fun PlateObject.withPainted(painted: PaintedFacets, meshes: List<PaintedMesh>): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(painted = painted, paintedMeshes = meshes)
    is PlateObject.CalibrationCube -> copy(painted = painted, paintedMeshes = meshes)
}

/** An object's settings replaced, with its copies left where they stand. */
fun PlateObject.withSettings(settings: ModelSettings): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(settings = settings)
    is PlateObject.CalibrationCube -> copy(settings = settings)
}

/** The copies of an object replaced, with its settings kept. */
fun PlateObject.withInstances(instances: List<PlateInstance>): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(instances = instances)
    is PlateObject.CalibrationCube -> copy(instances = instances)
}

/** One copy of the object replaced, by its place in the object's list. */
fun PlateObject.withInstance(index: Int, instance: PlateInstance): PlateObject =
    withInstances(instances.mapIndexed { at, copy -> if (at == index) instance else copy })

/**
 * A state of the plate the undo/redo stack returns to (UndoRedo::Snapshot): its
 * objects with their meshes, what was selected, and the plate's own settings,
 * which hold where the wipe tower stands.
 */
data class PlateSnapshot(
    val objects: List<PlateObject>,
    val selectedInstances: Set<PlateInstanceId>,
    val selectedPart: ObjectPartId?,
    val selectedRange: LayerRangeId?,
    val plateSettings: ModelSettings,
)

/**
 * Plater's UndoRedo::Stack: [undo] holds the states before the actions taken,
 * the latest last, and [redo] the states Undo left, the next one first.
 */
data class PlateHistory(
    val undo: List<PlateSnapshot> = emptyList(),
    val redo: List<PlateSnapshot> = emptyList(),
    /**
     * The state before the tool that is open (UndoRedo::SnapshotType::EnteringGizmo),
     * which joins [undo] when the tool leaves the plate changed.
     */
    val beforeTool: PlateSnapshot? = null,
)

/**
 * Selection::Clipboard: what Copy took, as objects of its own, whose meshes
 * outlive the objects they were copied from.
 */
sealed interface PlateClipboard {
    /** Selection::Instance mode: objects with the copies taken, where they stood. */
    data class Objects(val objects: List<PlateObject>) : PlateClipboard

    /**
     * Selection::Volume mode: the [volumes] (ModelObject::volumes) of [source],
     * a copy of the object they belong to with the copy they were taken over.
     */
    data class Volumes(val source: PlateObject, val volumes: List<Int>) : PlateClipboard
}

/** A copy of an object, as the object list and the selection name it. */
data class PlateInstanceId(val mesh: ScenePath, val instance: Int = 0)

/**
 * A volume of an object, as the object list names it (ObjectList's itVolume):
 * [index] is its place in ModelObject::volumes, 0 for the object's own mesh
 * and 1 for its first part (PlateObject.volumeAt).
 */
data class ObjectPartId(val mesh: ScenePath, val index: Int)

/** A height range of an object, as the object list names it (itLayer). */
data class LayerRangeId(val mesh: ScenePath, val index: Int)

/** An item of the object list with settings of its own (ObjectList::get_item_config). */
sealed interface SettingsItem {
    val mesh: ScenePath

    data class Object(override val mesh: ScenePath) : SettingsItem

    data class Volume(val id: ObjectPartId) : SettingsItem {
        override val mesh: ScenePath get() = id.mesh
    }

    data class Layer(val id: LayerRangeId) : SettingsItem {
        override val mesh: ScenePath get() = id.mesh
    }
}

/**
 * What "Copy Process Settings" took (ObjectList::Clipboard's config cache):
 * the settings of an item over those of its object. They paste only into an
 * item of the same kind (ObjectList::can_paste_settings_into_list).
 */
data class SettingsClipboard(val kind: SettingsItemKind, val settings: ModelSettings)

/** The kind of item of the object list settings come from (ItemType: itObject, itVolume, itLayer). */
enum class SettingsItemKind { OBJECT, VOLUME, LAYER }

val SettingsItem.kind: SettingsItemKind
    get() = when (this) {
        is SettingsItem.Object -> SettingsItemKind.OBJECT
        is SettingsItem.Volume -> SettingsItemKind.VOLUME
        is SettingsItem.Layer -> SettingsItemKind.LAYER
    }

/**
 * The switch over the settings (ParamsPanel::m_mode_region): the presets, or
 * the settings of the plate and of the object the plate has selected.
 */
enum class SettingsScope { GLOBAL, OBJECT }

enum class EngineAvailability { STARTING, READY, UNAVAILABLE }

data class EngineState(
    val availability: EngineAvailability = EngineAvailability.STARTING,
    val version: EngineVersion? = null,
)

/** A slicing job in progress. [progress] is null until the engine reports. */
data class PlateSlicing(
    val jobId: SliceJobId,
    val progress: SliceProgress? = null,
    val cancelling: Boolean = false,
)

data class PlateSliceResult(
    val jobId: SliceJobId,
    /** The objects on the plate when it was sliced. */
    val objects: List<PlateObject>,
    val gcode: OutputPath,
    val statistics: SliceStatistics,
    /** The G-code's toolpaths for the preview, when the engine wrote them. */
    val toolpaths: ScenePath? = null,
    /** The wipe tower as the slice built it, which the plate then shows. */
    val wipeTower: ScenePath? = null,
    /** The codes on the layers the plate was sliced with. */
    val layerGcodes: List<LayerGcode> = emptyList(),
    /** What the layer slider's menu offers for this print. */
    val layerGcodeRules: LayerGcodeRules = LayerGcodeRules(),
)

enum class PlateProblemKind {
    ENGINE_UNAVAILABLE,
    IMPORT_FAILED,
    SLICE_FAILED,
    ENGINE_CRASHED,
    SLICE_CANCELLED,
    PLACEMENT_FAILED,
    PRESETS_FAILED,

    /** "Export as one STL/DRC" could not write the file. */
    EXPORT_FAILED,

    /** Plater::export_stl() wrote the positive volumes alone: the notification OrcaSlicer shows. */
    EXPORT_WITHOUT_NEGATIVE_VOLUMES,
}

data class PlateProblem(
    val kind: PlateProblemKind,
    /** Technical detail from the engine or the importer, when there is one. */
    val detail: String? = null,
)

/**
 * A change of the plate that waits for the answer to [question], as the
 * desktop app waits for its message box. The change starts again as
 * [request] with [answers] and the new answer; the notices it [shown] already
 * are not shown twice.
 */
data class PendingPlateQuestion(
    val request: PlateRequest,
    val question: SettingsDialog,
    val answers: Map<String, Boolean>,
    val shown: List<SettingsDialog>,
)

/** What asked the question: the load of a model file, the object menu, or a suggestion after a load. */
sealed interface PlateRequest {
    /** The load of [source], one of the files of [batch]. */
    data class Import(val source: ModelPath, val batch: ImportBatch = ImportBatch()) : PlateRequest

    /** [edit] of the object with the [mesh] file, or of its volume at [volume] (ObjectPartId.index). */
    data class Edit(val mesh: ScenePath, val edit: ObjectEdit, val volume: Int? = null) : PlateRequest

    /**
     * The handy model Orca String Hell loaded: OrcaSlicer suggests "One Wall
     * Threshold" 0 for "Only one wall on top surfaces" to work best.
     */
    data object TopSurfaceSuggestion : PlateRequest
}

/**
 * Plater::load_files() with several files: the files still to load after the
 * one loading, the copies loaded before it, which stay selected with the new
 * ones, whether the plate is arranged afterwards, and whether the String Hell
 * suggestion follows.
 */
data class ImportBatch(
    val rest: List<ModelPath> = emptyList(),
    val loaded: Set<PlateInstanceId> = emptySet(),
    val arrange: Boolean = false,
    val suggestTopSurface: Boolean = false,
    /** How a 3MF file of the batch loads, and whether the user chose it in ProjectDropDialog. */
    val load: ModelLoad = ModelLoad.GEOMETRY,
    val chosen: Boolean = false,
)

/**
 * MenuFactory::append_submenu_add_handy_model(): the models OrcaSlicer ships
 * under resources/handy_models, with the files each loads, whether the plate
 * is arranged after them, and whether OrcaSlicer suggests settings for it.
 */
enum class HandyModel(
    /** OrcaSlicer's menu text, which its catalogue translates. */
    val label: String,
    val files: List<String>,
    val arrangeAfterImport: Boolean = false,
    val suggestsTopSurface: Boolean = false,
) {
    ORCA_CUBE("Orca Cube", listOf("OrcaCube_v2.drc", "OrcaPlug_v2.drc"), arrangeAfterImport = true),
    ORCASLICED_COMBO("OrcaSliced Combo", listOf("OrcaSliced.3mf", "OrcaCube_v2.drc", "OrcaPlug_v2.drc"), arrangeAfterImport = true),
    ORCA_TOLERANCE_TEST("Orca Tolerance Test", listOf("OrcaToleranceTest.drc")),
    BENCHY("3DBenchy", listOf("3DBenchy.drc")),
    CALI_CAT("Cali Cat", listOf("calicat.drc")),
    AUTODESK_FDM_TEST("Autodesk FDM Test", listOf("ksr_fdmtest_v4.drc")),
    VORON_CUBE("Voron Cube", listOf("Voron_Design_Cube_v7.drc")),
    STANFORD_BUNNY("Stanford Bunny", listOf("Stanford_Bunny.drc")),
    ORCA_STRING_HELL("Orca String Hell", listOf("Orca_stringhell.drc"), suggestsTopSurface = true),
    ;

}

/** The object menu's commands that change the meshes of an object (ObjectEdit in orca_engine_adapter.hpp). */
enum class ObjectEdit {
    /** Plater::priv::split_object() */
    SPLIT_TO_OBJECTS,

    /** ObjectList::split() */
    SPLIT_TO_PARTS,

    /** ObjectList::fix_through_cgal() */
    FIX,

    /** Plater::convert_unit() */
    CONVERT_FROM_INCHES,
    RESTORE_TO_INCHES,
    CONVERT_FROM_METERS,
    RESTORE_TO_METERS,

    /** ObjectList::smooth_mesh(): "Subdivision mesh" */
    SMOOTH_MESH,

    /** ObjectList::boolean(): "Mesh boolean" */
    MESH_BOOLEAN,
}

/** Everything the app knows about the plate being prepared and sliced. */
data class PlateState(
    val engine: EngineState = EngineState(),
    /** The presets OrcaSlicer's sidebar offers and the selected ones; null until the engine reported them. */
    val presets: Presets? = null,
    /** A preset choice or the Setup Wizard's result is being applied. */
    val changingPresets: Boolean = false,
    /** OrcaSlicer's settings tabs for the selected presets, by the preset they edit. */
    val settingsTabs: Map<PresetKind, SettingsTabState> = PresetKind.entries.associateWith { SettingsTabState(it) },
    /** The plate is the one the app drew last, not one this engine described. */
    val plateFromCache: Boolean = false,
    /** A preset choice that waits for what happens to the unsaved changes of the edited preset. */
    val presetChange: PendingPresetChange? = null,
    /** The plate of the selected printer; null until the engine described it. */
    val plate: PlateDescription? = null,
    val importing: Boolean = false,
    /** The object menu is changing the meshes of an object (ObjectEdit). */
    val editing: Boolean = false,
    /** A question OrcaSlicer asked while it changed the plate; the change goes on once it is answered. */
    val plateQuestion: PendingPlateQuestion? = null,
    /**
     * ProjectDropDialog: the 3MF file the user picked for a plate that has
     * objects, which waits to be opened as a project or for its geometry only.
     */
    val projectDrop: ModelPath? = null,
    /** Message boxes OrcaSlicer showed while it changed the plate, which the user dismisses in turn. */
    val plateNotices: List<SettingsDialog> = emptyList(),
    /** The objects on the plate, in the order they were added, as OrcaSlicer's object list shows them. */
    val objects: List<PlateObject> = emptyList(),
    /** The copies the user picked (GLCanvas3D's Selection); empty when none is. */
    val selectedInstances: Set<PlateInstanceId> = emptySet(),
    /**
     * The part of an object the list has selected, whose own settings the tab
     * then edits; null while the selection is the object itself.
     */
    val selectedPart: ObjectPartId? = null,
    /** The height range the list has selected, whose own settings the tab edits. */
    val selectedRange: LayerRangeId? = null,
    /** The settings of the plate itself (PartPlate::config). */
    val plateSettings: ModelSettings = ModelSettings(),
    /** The wipe tower of the plate; null until the engine described it, and not shown when the plate prints with one filament. */
    val wipeTower: WipeTower? = null,
    /**
     * The tower as the engine last described it, shown or not, for what the
     * object menu's Flush Options go by: the filaments printed on the plate and
     * the options of the process preset.
     */
    val flushing: WipeTower = WipeTower(),
    /** The arrange options (GLCanvas3D::ArrangeSettings), which every arrangement of the plate takes. */
    val arrangeSettings: ArrangeSettings = ArrangeSettings(),
    /** What Copy and Cut took (Selection::Clipboard); null while nothing was copied. */
    val clipboard: PlateClipboard? = null,
    /** What "Copy Process Settings" took (ObjectList's clipboard); null while nothing was copied. */
    val settingsClipboard: SettingsClipboard? = null,
    /** The volume the Simplify gizmo is open on (GLGizmoSimplify::m_volume); null while it is closed. */
    val simplifyTarget: ObjectPartId? = null,
    /** The codes the preview's layer slider put on the layers (Model::plates_custom_gcodes), by height. */
    val layerGcodes: List<LayerGcode> = emptyList(),
    /** The states of the plate Undo and Redo return to (Plater's UndoRedo::Stack). */
    val history: PlateHistory = PlateHistory(),
    /** Which settings the app shows: the presets, or the ones of an object (ParamsPanel's Global and Objects). */
    val settingsScope: SettingsScope = SettingsScope.GLOBAL,
    val slicing: PlateSlicing? = null,
    val result: PlateSliceResult? = null,
    val problem: PlateProblem? = null,
) {
    /** The objects the plate has selected a copy of, in its order. */
    fun selectedObjects(): List<PlateObject> {
        val meshes = selectedInstances.mapTo(mutableSetOf(), PlateInstanceId::mesh)
        return objects.filter { it.mesh in meshes }
    }

    /**
     * The one copy the plate has selected; null when none or several are, as
     * the manipulation tools work on a single copy.
     */
    val selectedInstance: PlateInstanceId? get() = selectedInstances.singleOrNull()

    /** The object of that copy; null when none or several are selected. */
    val selected: PlateObject? get() = selectedInstance?.let { id -> objects.firstOrNull { it.mesh == id.mesh } }

    /** The object the selected part belongs to, and the part itself. */
    val selectedPartOwner: PlateObject? get() = selectedPart?.let { id -> objects.firstOrNull { it.mesh == id.mesh } }

    val selectedObjectPart: ObjectPart? get() = selectedPart?.let { id -> selectedPartOwner?.volumeAt(id.index) }

    /** The object the selected height range belongs to, and the range itself. */
    val selectedRangeOwner: PlateObject? get() = selectedRange?.let { id -> objects.firstOrNull { it.mesh == id.mesh } }

    val selectedLayerRange: LayerRange? get() = selectedRange?.let { id -> selectedRangeOwner?.layerRanges?.getOrNull(id.index) }

    /** The copy the tools work on; null when none or several are selected. */
    val selectedCopy: PlateInstance?
        get() = selectedInstance?.let { id -> selected?.instances?.getOrNull(id.instance) }

    /** The selected presets the plate is prepared and sliced with; null until a printer is set up. */
    val profiles: SlicingProfileSelection?
        get() = presets?.takeUnless(Presets::setupRequired)?.selection

    val busy: Boolean get() = importing || editing || slicing != null || changingPresets

    /**
     * Plater::can_undo() and can_redo(): a state to go to, no change of the
     * plate running, and no tool open that keeps a state of its own.
     */
    val canUndo: Boolean get() = history.undo.isNotEmpty() && idle
    val canRedo: Boolean get() = history.redo.isNotEmpty() && idle

    private val idle: Boolean get() = !busy && objects.none(PlateObject::placing) && history.beforeTool == null

    /**
     * GLCanvas3D::reload_scene() enables slicing when an object is inside the
     * build volume and none lies across its boundary; objects entirely off the
     * plate are not printed. Every placement must be settled.
     */
    val canSlice: Boolean
        get() = engine.availability == EngineAvailability.READY && profiles != null && !busy &&
            // ModelInstance::is_printable(): inside the build volume and printed.
            copies().any { it.inspection.fit == BuildVolumeFit.INSIDE && it.printable } &&
            copies().none { it.placing || it.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE }

    /** Every copy on the plate, in the plate's order. */
    fun copies(): List<PlateInstance> = objects.flatMap(PlateObject::instances)
}
