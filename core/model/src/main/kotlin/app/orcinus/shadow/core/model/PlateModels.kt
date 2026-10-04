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
    /** ModelInstance::m_assemble_transformation: where the copy stands in the assembly view; null while it has no place there. */
    val assemble: Transform3? = null,
    /** ModelInstance::m_offset_to_assembly, which the assembly view's explosion spreads the copy by; null for none. */
    val offsetToAssembly: Vector3? = null,
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
    /** ModelVolume::cut_info: whether a cut made it a connector. */
    val cutInfo: CutInfo = CutInfo(),
    /** The text or the SVG the part was embossed from; null for a part of neither. */
    val emboss: EmbossData? = null,
    /** ModelVolume::source: where the volume was in its file. */
    val origin: VolumeOrigin = VolumeOrigin(),
)

/**
 * ModelVolume::source of a volume read from a file: the object and the volume
 * it was there, and where the centre of its mesh stood in the file
 * (mesh_offset), by which a volume loaded or replaced from a file keeps its
 * place (ObjectList::load_modifier(), Plater::priv::replace_volume_with_stl()).
 */
data class VolumeOrigin(
    val objectIndex: Int = -1,
    val volumeIndex: Int = -1,
    val meshOffset: Vector3 = Vector3(0.0, 0.0, 0.0),
) {
    /** Flattened as the engine reads it (VolumeOrigin of orca_engine_adapter.hpp). */
    fun values(): DoubleArray = doubleArrayOf(objectIndex.toDouble(), volumeIndex.toDouble(), meshOffset.x, meshOffset.y, meshOffset.z)

    companion object {
        const val SIZE = 5

        fun of(values: DoubleArray?, at: Int = 0): VolumeOrigin {
            if (values == null || values.size < at + SIZE) return VolumeOrigin()
            return VolumeOrigin(values[at].toInt(), values[at + 1].toInt(), Vector3(values[at + 2], values[at + 3], values[at + 4]))
        }
    }
}

/**
 * ModelVolume::CutInfo: what a cut made of a volume — a connector of the
 * [connectorType] with its tolerances, or not — and which part it came from.
 */
data class CutInfo(
    val fromUpper: Boolean = true,
    val connector: Boolean = false,
    val processed: Boolean = true,
    val connectorType: CutConnectorType = CutConnectorType.PLUG,
    val radiusTolerance: Double = 0.0,
    val heightTolerance: Double = 0.0,
) {
    /** Flattened as the engine reads it (VolumeCutInfo of orca_engine_adapter.hpp). */
    fun values(): DoubleArray = doubleArrayOf(
        if (fromUpper) 1.0 else 0.0,
        if (connector) 1.0 else 0.0,
        if (processed) 1.0 else 0.0,
        connectorType.ordinal.toDouble(),
        radiusTolerance,
        heightTolerance,
    )

    /** CutInfo::invalidate(): no longer a connector. */
    fun invalidated() = copy(connector = false)

    companion object {
        const val SIZE = 6

        /** The cut info of [values] from [at], as [values] flattens it. */
        fun of(values: DoubleArray?, at: Int = 0): CutInfo {
            if (values == null || values.size < at + SIZE) return CutInfo()
            return CutInfo(
                fromUpper = values[at] != 0.0,
                connector = values[at + 1] != 0.0,
                processed = values[at + 2] != 0.0,
                connectorType = CutConnectorType.entries.getOrElse(values[at + 3].toInt()) { CutConnectorType.PLUG },
                radiusTolerance = values[at + 4],
                heightTolerance = values[at + 5],
            )
        }
    }
}

/**
 * CutObjectBase (ModelObject::cut_id): the cut an object is a part of. The
 * objects of one cut share [id]; [checkSum] and [connectorsCount] follow the
 * cuts made of them since.
 */
data class CutId(val id: Long, val checkSum: Long = 1, val connectorsCount: Long = 0) {
    /** CutObjectBase::has_same_id() */
    fun hasSameId(other: CutId) = id == other.id

    /** CutObjectBase::is_equal() */
    fun isEqual(other: CutId) = this == other

    /** Flattened as the engine reads it (ObjectCutId of orca_engine_adapter.hpp). */
    fun values(): LongArray = longArrayOf(id, checkSum, connectorsCount)

    companion object {
        const val SIZE = 3

        /** The cut id of [values] from [at]; null for none (an invalid id). */
        fun of(values: LongArray?, at: Int = 0): CutId? {
            if (values == null || values.size < at + SIZE || values[at] == 0L) return null
            return CutId(values[at], values[at + 1], values[at + 2])
        }
    }
}

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
    val cutInfo: CutInfo = CutInfo(),
    /** The text or the SVG the object's own mesh was embossed from (an object made of a text). */
    val emboss: EmbossData? = null,
    val origin: VolumeOrigin = VolumeOrigin(),
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

/**
 * The triangles of an object painted in one state, as a mesh for the 3D view:
 * the filament of colour painting, or while another painting tool is open,
 * the state it paints ([PaintState]).
 */
data class PaintedMesh(
    val state: Int,
    val mesh: ScenePath,
    val kind: PaintKind = PaintKind.COLOR,
    /**
     * The volume the triangles lie on (ModelObject::volumes): 0, the object's
     * own mesh, in the object's coordinates; a part in its own, which the 3D
     * view places as it places the part.
     */
    val volume: Int = 0,
)

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

    /**
     * ModelObject::layer_height_profile: the variable layer height, z and
     * layer height pairs from the bed up; empty for none.
     */
    val layerHeightProfile: List<Double>

    /** ModelObject::brim_points: the brim ears GLGizmoBrimEars placed; empty for none. */
    val brimPoints: List<BrimPoint>

    /** The facets of the object's own mesh painted with the filaments of the plate. */
    val painted: PaintedFacets

    /** The painted triangles as meshes, which the 3D view draws in the filament colours. */
    val paintedMeshes: List<PaintedMesh>

    /** ModelObject::cut_id: the cut the object is a part of; null for none. */
    val cutId: CutId?

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
        override val layerHeightProfile: List<Double> = emptyList(),
        override val brimPoints: List<BrimPoint> = emptyList(),
        override val painted: PaintedFacets = PaintedFacets(),
        override val paintedMeshes: List<PaintedMesh> = emptyList(),
        val frame: Transform3? = null,
        override val volume: ObjectVolume = ObjectVolume(),
        /** The name of the document it came from (ModelObject::input_file), which names the G-code. */
        val inputName: String = file.displayName,
        override val cutId: CutId? = null,
    ) : PlateObject

    /** The engine's built-in 20 mm calibration cube. */
    data class CalibrationCube(
        override val instances: List<PlateInstance>,
        override val settings: ModelSettings = ModelSettings(),
        override val parts: List<ObjectPart> = emptyList(),
        override val layerRanges: List<LayerRange> = emptyList(),
        override val layerHeightProfile: List<Double> = emptyList(),
        override val brimPoints: List<BrimPoint> = emptyList(),
        override val painted: PaintedFacets = PaintedFacets(),
        override val paintedMeshes: List<PaintedMesh> = emptyList(),
        override val volume: ObjectVolume = ObjectVolume(),
        /** The name the user gave it; null for the app's own. */
        val name: String? = null,
        override val cutId: CutId? = null,
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
        cutInfo = volume.cutInfo,
        emboss = volume.emboss,
        origin = volume.origin,
    )
    else -> parts.getOrNull(index - 1)
}

/** ModelVolume::source.input_file of the volume at [index] (ModelObject::volumes); empty for none. */
fun PlateObject.volumeInputFile(index: Int): String = if (index == 0) volume.inputFile else parts.getOrNull(index - 1)?.inputFile.orEmpty()

/**
 * reloadable_volumes(): the volumes (ModelObject::volumes) that came from a
 * file with an extension, which "Reload from disk" reads again.
 */
fun PlateObject.reloadableVolumes(): List<Int> = (0..parts.size).filter { index ->
    val name = volumeInputFile(index).substringAfterLast('/')
    name.lastIndexOf('.') > 0
}

/** ModelObject::is_cut(): the object is a part of a cut. */
val PlateObject.isCut: Boolean get() = cutId != null

/** ModelObject::has_connectors(): a volume of the object is a connector of its cut. */
val PlateObject.hasConnectors: Boolean get() = volume.cutInfo.connector || parts.any { it.cutInfo.connector }

/** ModelObject::invalidate_cut(): the object is no longer a part of a cut, nor its volumes connectors. */
fun PlateObject.withoutCut(): PlateObject {
    val parts = parts.map { it.copy(cutInfo = it.cutInfo.invalidated()) }
    val volume = volume.copy(cutInfo = volume.cutInfo.invalidated())
    return when (this) {
        is PlateObject.ImportedModel -> copy(cutId = null, parts = parts, volume = volume)
        is PlateObject.CalibrationCube -> copy(cutId = null, parts = parts, volume = volume)
    }
}

/** The object as a part of the cut [cutId] (CutObjectBase::copy()). */
fun PlateObject.withCutId(cutId: CutId?): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(cutId = cutId)
    is PlateObject.CalibrationCube -> copy(cutId = cutId)
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

/** The variable layer height of the object replaced; empty for none. */
fun PlateObject.withLayerHeightProfile(profile: List<Double>): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(layerHeightProfile = profile)
    is PlateObject.CalibrationCube -> copy(layerHeightProfile = profile)
}

/** The brim ears of the object replaced (GLGizmoBrimEars::update_model_object()). */
fun PlateObject.withBrimPoints(points: List<BrimPoint>): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(brimPoints = points)
    is PlateObject.CalibrationCube -> copy(brimPoints = points)
}

/**
 * BrimPoint: a brim ear at [position] in the object's coordinates (on the
 * plate, under the object), [radius] (head_front_radius) wide.
 */
data class BrimPoint(val position: Vector3, val radius: Double) {
    companion object {
        /** The ears as the engine takes them: x, y, z and the radius of each. */
        fun List<BrimPoint>.values(): DoubleArray = flatMap { listOf(it.position.x, it.position.y, it.position.z, it.radius) }.toDoubleArray()

        fun of(values: DoubleArray): List<BrimPoint> = List(values.size / 4) { index ->
            BrimPoint(Vector3(values[index * 4], values[index * 4 + 1], values[index * 4 + 2]), values[index * 4 + 3])
        }
    }
}

/**
 * GUI_ObjectList's variable height column: a profile of more than the two
 * points of a plain one is a variable layer height.
 */
val PlateObject.hasVariableLayerHeight: Boolean get() = layerHeightProfile.size > 4

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
 * objects with their meshes, what was selected, and the plates with their own
 * settings, which hold where their wipe towers stand, and the current one
 * (PartPlateList as the stack keeps it: the codes on the layers are the
 * model's, which the stack leaves alone).
 */
data class PlateSnapshot(
    val objects: List<PlateObject>,
    val selectedInstances: Set<PlateInstanceId>,
    val selectedPart: ObjectPartId?,
    val selectedRange: LayerRangeId?,
    /** Every plate with its name, lock and settings alone. */
    val plates: List<PartPlate>,
    val currentPlate: Int,
    /** UndoRedo::SnapshotData::VARIABLE_LAYER_EDITING_ACTIVE: the variable layer height was on. */
    val layerEditing: Boolean = false,
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

/**
 * ObjectLayers' EditorType: the field of the selected height range that has
 * the focus — its bottom, its top, or none of the two (etLayerHeight).
 */
enum class LayerRangeEditor { MIN_Z, MAX_Z, LAYER_HEIGHT }

/**
 * ObjectList::Clipboard: whether Copy in the object list last took height
 * ranges ([holdsRanges]) rather than process settings, and the height ranges
 * it keeps (m_layer_config_ranges_cache), which copying single ranges adds
 * to. The settings it keeps are [SettingsClipboard].
 */
data class ListClipboard(val holdsRanges: Boolean, val ranges: List<LayerRange> = emptyList())

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

/** MainFrame's SliceSelectType: the slice button slices the current plate, or all of them. */
enum class SliceMode {
    /** eSlicePlate, "Slice plate". */
    PLATE,

    /** eSliceAll, "Slice all". */
    ALL,
}

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
    /**
     * What the plate keeps of the slice for the 3MF files of the project and
     * its sliced plates (the slice info of its print and G-code result).
     */
    val sliceInfo: ScenePath? = null,
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

    /** GLGizmoMeshBoolean's warning: the operation gave no mesh. */
    MESH_BOOLEAN_FAILED,

    /** The warnings of ArrangeJob and OrientJob on a locked plate (prepare_partplate()). */
    PLATE_LOCKED_ARRANGE,
    PLATE_LOCKED_ORIENT,

    /** ... and of every selected copy on a locked plate (prepare_all(), prepare_selection()). */
    SELECTION_LOCKED_ARRANGE,
    SELECTION_LOCKED_ORIENT,

    /** ArrangeJob::prepare_all() with no copy to arrange. */
    NO_ARRANGEABLE_OBJECTS,
}

/** The kinds OrcaSlicer shows as warnings (WarningNotificationLevel). */
val PlateProblemKind.warning: Boolean
    get() = this == PlateProblemKind.PLATE_LOCKED_ARRANGE || this == PlateProblemKind.PLATE_LOCKED_ORIENT ||
        this == PlateProblemKind.SELECTION_LOCKED_ARRANGE || this == PlateProblemKind.SELECTION_LOCKED_ORIENT ||
        this == PlateProblemKind.NO_ARRANGEABLE_OBJECTS

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
    /** The load of [files], one of the loads of [batch]. */
    data class Import(val files: ImportFiles, val batch: ImportBatch = ImportBatch()) : PlateRequest

    /**
     * [edit] of the object with the [mesh] file, or of its volume at [volume]
     * (ObjectPartId.index); [cut] is what ObjectEdit.CUT cuts with. With
     * [others], the multi-selection menu's edit of the objects with [mesh]
     * and those files, in the plate's order, as one change.
     */
    data class Edit(
        val mesh: ScenePath,
        val edit: ObjectEdit,
        val volume: Int? = null,
        val cut: ObjectCut? = null,
        val others: List<ScenePath> = emptyList(),
    ) : PlateRequest {
        /** Every object the edit changes. */
        val meshes: List<ScenePath> get() = listOf(mesh) + others
    }

    /**
     * The handy model Orca String Hell loaded: OrcaSlicer suggests "One Wall
     * Threshold" 0 for "Only one wall on top surfaces" to work best.
     */
    data object TopSurfaceSuggestion : PlateRequest

    /** Deleting the object with the [mesh] file, a part of a cut (Plater::priv::delete_object_from_model()). */
    data class DeleteCutObject(val mesh: ScenePath) : PlateRequest

    /** Cut of the Edit menu erasing [copies], the last copies of parts of a cut among them. */
    data class EraseCutObjects(val copies: Set<PlateInstanceId>) : PlateRequest

    /** Deleting a volume of the object with the [mesh] file, a part of a cut (ObjectList::del_from_cut_object()). */
    data class InvalidateCut(val mesh: ScenePath) : PlateRequest
}

/**
 * One Plater::priv::load_files() call: its [files] load together, as one
 * model whose objects join the plate at once. With [askMulti]
 * (Plater::add_file() of several model files, none a 3MF file) the user is
 * asked whether they make one object of several parts, and whether it drops
 * onto the plate.
 */
data class ImportFiles(
    val files: List<ModelPath>,
    val askMulti: Boolean = false,
    /** The documents of the files, which join the recent files once they load (add_file() with recent_models). */
    val recentModels: List<ExternalDocumentReference> = emptyList(),
) {
    constructor(file: ModelPath) : this(listOf(file))

    /** The file the load is known by: the first. */
    val first: ModelPath get() = files.first()
}

/**
 * Plater::load_files() with several files: the loads still to come after the
 * one loading, the copies loaded before it, which stay selected with the new
 * ones, whether the plate is arranged afterwards, and whether the String Hell
 * suggestion follows.
 */
data class ImportBatch(
    val rest: List<ImportFiles> = emptyList(),
    val loaded: Set<PlateInstanceId> = emptySet(),
    val arrange: Boolean = false,
    val suggestTopSurface: Boolean = false,
    /** How a 3MF file of the batch loads, and whether the user chose it in ProjectDropDialog. */
    val load: ModelLoad = ModelLoad.GEOMETRY,
    val chosen: Boolean = false,
    /** StepMeshDialog's answers for the STEP files the batch loads now, by their place among its files. */
    val stepMeshes: Map<Int, StepMeshOptions> = emptyMap(),
    /** ObjColorDialog's answers for the OBJ files with colours the batch loads now, by their place among its files. */
    val objColors: Map<Int, ObjColorChoice> = emptyMap(),
    /**
     * The document the user picked and the name it goes by: a project opened
     * from it is saved into it again, and a plate without a name takes the
     * name of the first model file it loads (Plater::add_file).
     */
    val document: ExternalDocumentReference? = null,
    val displayName: String? = null,
    /**
     * LoadStrategy::Restore: the file is the project's backup, which loads
     * under the name of [document] and stays unsaved.
     */
    val restore: Boolean = false,
)

/**
 * The project the plate is (Plater's project name and project filename): the
 * name the desktop app shows in its title, none while it is "Untitled", and
 * the document it was opened from or last saved to, which "Save Project"
 * writes again.
 */
data class PlateProject(
    val name: String? = null,
    val document: ExternalDocumentReference? = null,
    /** What the plate held when the project was last opened, saved or started. */
    val baseline: ProjectContent = ProjectContent(),
    /**
     * The presets and filament colours selected then (ProjectDirtyStateManager's
     * initial presets); null until the project was first opened, saved or started.
     */
    val presets: SlicingProfileSelection? = null,
    val filamentColors: List<String> = emptyList(),
    /** What the project opened from a file holds besides its objects (LoadedProject.info), which Save writes again. */
    val info: ScenePath? = null,
    /**
     * put_other_changes(): the project's information changed since it was
     * opened, saved or started (a file added, a cover chosen), which a save
     * clears (clear_other_changes()).
     */
    val otherChanges: Boolean = false,
)

/**
 * What a project holds of the plate: the objects as the undo stack keeps them,
 * and the plates with their settings and the codes on their layers.
 */
data class ProjectContent(
    val objects: List<PlateObject> = emptyList(),
    val plates: List<PartPlate> = listOf(PartPlate()),
)

/**
 * A question of New Project or Open Project that waits for the user, in the
 * order the desktop app asks them: whether the project is saved first
 * (Plater::close_with_confirm), where to when it has no document yet, and
 * what happens to the unsaved changes of the presets (UnsavedChangesDialog).
 */
sealed interface ProjectPrompt {
    /** "The current project has unsaved changes, save it before continue?" */
    data object SaveChanges : ProjectPrompt

    /** EVT_RESTORE_PROJECT: "Previous unsaved project detected, do you want to restore it?" */
    data object RestoreBackup : ProjectPrompt

    /** Save Project asks for a document, as its file dialog does. */
    data object SaveAs : ProjectPrompt

    /**
     * The presets with unsaved changes, under [caption] and [header]. The
     * changes can move to a new project ([transfer]), be saved ([save]),
     * discarded, or the project stays as it is.
     */
    data class PresetChanges(
        val caption: OrcaText,
        val header: List<OrcaText>,
        val presets: List<DirtyPreset>,
        val transfer: Boolean,
        val save: Boolean,
    ) : ProjectPrompt
}

/**
 * A question of "Reload from disk" (Plater::priv::reload_from_disk()) that
 * waits for the user.
 */
sealed interface ReloadPrompt {
    /** "Please select a file": the app can no longer read the file [name], and the user picks one. */
    data class PickFile(val name: String) : ReloadPrompt

    /** "Do you want to replace it ?": the file picked for [name] has another name. */
    data class Replace(val name: String) : ReloadPrompt
}

/** What the user answered to [ProjectPrompt.PresetChanges]. */
sealed interface PresetChangesAnswer {
    /** Keep: the changes stay with the presets of the new project. */
    data object Transfer : PresetChangesAnswer

    data object Discard : PresetChangesAnswer

    /** Save: the presets that cannot be overwritten under the names given, the others under their own. */
    data class Save(val names: Map<PresetKind, PresetSave>) : PresetChangesAnswer
}

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

    /** GLGizmoCut3D::perform_cut() with a plane ([ObjectCut]). */
    CUT,

    /**
     * ObjectList::del_subobject_from_object() of the volume: the object keeps
     * its other volumes, the first of them in its place.
     */
    DELETE_VOLUME,

    /** ObjectList::merge(true) ("Assemble"): the selected objects become the parts of one. */
    ASSEMBLE,
}

/**
 * The cut gizmo's plane and "After cut" of its window (GLGizmoCut3D; ObjectCut
 * in orca_engine_adapter.hpp): [plane] stands in world coordinates, its centre
 * and rotation, and its normal is the rotated Z axis, with the upper part on
 * that side.
 */
data class ObjectCut(
    /** The copy the plane cuts. */
    val instance: Int,
    val plane: Transform3,
    val keepUpper: Boolean = true,
    val keepLower: Boolean = true,
    /** "Cut to parts": both halves stay one object, as its parts. */
    val keepAsParts: Boolean = false,
    val placeOnCutUpper: Boolean = true,
    val placeOnCutLower: Boolean = false,
    val flipUpper: Boolean = false,
    val flipLower: Boolean = false,
    /** The connectors the cut makes (ModelObject::cut_connectors). */
    val connectors: List<CutConnector> = emptyList(),
    /** m_snap_space_proportion and m_snap_bulge_proportion of the snaps. */
    val snapSpace: Double = 0.3,
    val snapBulge: Double = 0.15,
    /** The name the connectors' volumes take, "<name>-<n>" (_u8L("Connector")). */
    val connectorName: String = "Connector",
    /** CutMode::cutTongueAndGroove: the plane carries [groove]; [radius] is m_radius, which sizes it. */
    val dovetail: Boolean = false,
    val groove: CutGroove = CutGroove(),
    val radius: Double = 0.0,
    /** The pieces a right click turned over (perform_by_contour()); null cuts the halves as they fall. */
    val parts: CutPartSelection? = null,
)

/**
 * GLGizmoCut3D::PartSelection as the gizmo keeps it: the object split into its
 * pieces by [plane] (which the plane may have been flipped from since), and
 * whether each piece goes to the upper part.
 */
data class CutPartSelection(val plane: Transform3, val selected: List<Boolean>)

/**
 * Cut::Groove of the dovetail cut, in millimetres and radians, with how many
 * grooves there are and the gap between them (m_groove_count, m_groove_gap).
 */
data class CutGroove(
    val depth: Double = 0.0,
    val width: Double = 0.0,
    val flapsAngle: Double = 0.0,
    val angle: Double = 0.0,
    val depthTolerance: Double = 0.1,
    val widthTolerance: Double = 0.1,
    val count: Int = 1,
    val gap: Double = 10.0,
) {
    /** Cut::calculate_groove_width(): how wide a groove stands out on a plane sized by [radius] (m_radius). */
    fun outerWidth(radius: Double): Double {
        val flapWidth = if (kotlin.math.abs(flapsAngle) < 1e-9) depth else depth / kotlin.math.sin(flapsAngle)
        val totalFlapWidth = 2.0 * flapWidth * kotlin.math.cos(flapsAngle)
        val slotNeckHalfWidth = 0.5 * width
        val slotMouthHalfWidth = 0.5 * (width + totalFlapWidth)
        val planeHalfHeight = 0.5 * (1.5 * (1.5 * radius))
        val flapTaperOffset = planeHalfHeight * kotlin.math.tan(angle)
        return 2.0 * maxOf(slotMouthHalfWidth + flapTaperOffset, slotNeckHalfWidth + flapTaperOffset)
    }

    /** Flattened as the engine reads it (CutGroove of orca_engine_adapter.hpp). */
    fun values(): DoubleArray = doubleArrayOf(depth, width, flapsAngle, angle, depthTolerance, widthTolerance, count.toDouble(), gap)

    companion object {
        fun of(values: DoubleArray): CutGroove = if (values.size < 8) {
            CutGroove()
        } else {
            CutGroove(values[0], values[1], values[2], values[3], values[4], values[5], values[6].toInt(), values[7])
        }
    }
}

/** CutConnectorType of Model.hpp. */
enum class CutConnectorType { PLUG, DOWEL, SNAP }

/** CutConnectorStyle of Model.hpp. */
enum class CutConnectorStyle { PRISM, FRUSTUM }

/** CutConnectorShape of Model.hpp. */
enum class CutConnectorShape { TRIANGLE, SQUARE, HEXAGON, CIRCLE }

/**
 * A connector of the cut (CutConnector of Model.hpp) where the gizmo placed
 * it on the plane, in world coordinates; [zAngle] is its turn about the
 * plane's normal, in radians.
 */
data class CutConnector(
    val position: Vector3,
    val radius: Double,
    val height: Double,
    val radiusTolerance: Double,
    val heightTolerance: Double,
    val zAngle: Double,
    val type: CutConnectorType,
    val style: CutConnectorStyle,
    val shape: CutConnectorShape,
)

/** The connectors flattened as the engine reads them: eight values each (CutConnectorData). */
fun List<CutConnector>.connectorValues(): DoubleArray = flatMap {
    listOf(it.position.x, it.position.y, it.position.z, it.radius, it.height, it.radiusTolerance, it.heightTolerance, it.zAngle)
}.toDoubleArray()

/** ... and their type, style and shape, three each. */
fun List<CutConnector>.connectorKinds(): IntArray = flatMap { listOf(it.type.ordinal, it.style.ordinal, it.shape.ordinal) }.toIntArray()

/** The connectors [connectorValues] and [connectorKinds] flattened. */
fun cutConnectors(values: DoubleArray, kinds: IntArray): List<CutConnector> = (0 until minOf(values.size / 8, kinds.size / 3)).map { index ->
    val value = index * 8
    val kind = index * 3
    CutConnector(
        position = Vector3(values[value], values[value + 1], values[value + 2]),
        radius = values[value + 3],
        height = values[value + 4],
        radiusTolerance = values[value + 5],
        heightTolerance = values[value + 6],
        zAngle = values[value + 7],
        type = CutConnectorType.entries[kinds[kind]],
        style = CutConnectorStyle.entries[kinds[kind + 1]],
        shape = CutConnectorShape.entries[kinds[kind + 2]],
    )
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
    /**
     * How many times PhysicalPrinterDialog saved the printer's host, which the
     * Device tab loads its page again after (Sidebar::update_all_preset_comboboxes()).
     */
    val connectionSaves: Int = 0,
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
    /** The document of that file, which a project opened from it is saved into. */
    val projectDropBatch: ImportBatch = ImportBatch(),
    /** StepMeshDialog, which a load or a replacement of a STEP file waits for. */
    val stepMesh: StepMeshQuestion? = null,
    /** ObjColorDialog, which a load of an OBJ file with colours waits for. */
    val objColor: ObjColorDialogState? = null,
    /** The project the plate is: its name and the document it is saved into. */
    val project: PlateProject = PlateProject(),
    /** A question of New Project or Open Project that waits for the user. */
    val projectPrompt: ProjectPrompt? = null,
    /** A question of "Reload from disk" that waits for the user. */
    val reloadPrompt: ReloadPrompt? = null,
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
    /**
     * The plates of the project in their order (PartPlateList). The entry of
     * the current one holds what it held when another was current: its
     * settings, layer codes and G-code are the fields of the plate below, and
     * partPlates() gives every plate as it is now.
     */
    val plates: List<PartPlate> = listOf(PartPlate()),
    /** PartPlateList::m_current_plate: the plate the view works on, which is judged, arranged and sliced. */
    val currentPlate: Int = 0,
    /** The plate the engine was told of last; the plate is busy until the engine knows the current one. */
    val enginePlate: EnginePlate = EnginePlate(),
    /** The settings of the current plate itself (PartPlate::config). */
    val plateSettings: ModelSettings = ModelSettings(),
    /** The wipe tower of the plate; null until the engine described it, and not shown when the plate prints with one filament. */
    val wipeTower: WipeTower? = null,
    /**
     * The tower as the engine last described it, shown or not, for what the
     * object menu's Flush Options go by: the filaments printed on the plate and
     * the options of the process preset.
     */
    val flushing: WipeTower = WipeTower(),
    /**
     * GLVolumeCollection::get_selection_support_normal_z(): the canvas's
     * "Overhangs" tint the faces whose world normal points further down;
     * null until the engine worked it out from the edited presets.
     */
    val overhangNormalZ: Float? = null,
    /**
     * The plate applied to its print and validated after its last change
     * (Plater::priv::update_background_process()); null while there is
     * nothing to validate or the engine has not answered.
     */
    val validation: PlateValidation? = null,
    /** The arrange options (GLCanvas3D::ArrangeSettings), which every arrangement of the plate takes. */
    val arrangeSettings: ArrangeSettings = ArrangeSettings(),
    /** What Copy and Cut took (Selection::Clipboard); null while nothing was copied. */
    val clipboard: PlateClipboard? = null,
    /** The height ranges Copy took in the object list (ObjectList's clipboard); null while it holds none. */
    val listClipboard: ListClipboard? = null,
    /**
     * GLCanvas3D::m_sidebar_field of the selected height range: the field of
     * it that has the focus, whose plane the 3D view draws solid.
     */
    val layerRangeEditor: LayerRangeEditor = LayerRangeEditor.LAYER_HEIGHT,
    /** What "Copy Process Settings" took (ObjectList's clipboard); null while nothing was copied. */
    val settingsClipboard: SettingsClipboard? = null,
    /** The volume the Simplify gizmo is open on (GLGizmoSimplify::m_volume); null while it is closed. */
    val simplifyTarget: ObjectPartId? = null,
    /**
     * The 3D view's variable layer height is on (GLCanvas3D::LayersEditing::m_enabled):
     * the selected object's layer heights are edited on the bar.
     */
    val layerEditing: Boolean = false,
    /** What the object list asks of the canvas's text or SVG tool; null for nothing. */
    val embossRequest: EmbossRequest? = null,
    /** The codes the preview's layer slider put on the layers of the current plate (Model::plates_custom_gcodes), by height. */
    val layerGcodes: List<LayerGcode> = emptyList(),
    /**
     * Model::calib_pa_pattern: the PA pattern the Calibration menu set up,
     * whose G-code the slice of every plate generates for the handles on it
     * (Plater::_calib_pa_pattern_gen_gcode); null without one. A new or
     * opened project leaves it.
     */
    val paPattern: CalibrationParams? = null,
    /** The states of the plate Undo and Redo return to (Plater's UndoRedo::Stack). */
    val history: PlateHistory = PlateHistory(),
    /**
     * How many times Plater::priv::reset() started the project afresh (New
     * Project, Open Project), which the canvases' own state follows, such as
     * the assembly view's explosion ratio.
     */
    val projectResets: Int = 0,
    /** Which settings the app shows: the presets, or the ones of an object (ParamsPanel's Global and Objects). */
    val settingsScope: SettingsScope = SettingsScope.GLOBAL,
    val slicing: PlateSlicing? = null,
    /** What the slice button slices, as its drop-down chose (MainFrame::m_slice_select). */
    val sliceMode: SliceMode = SliceMode.PLATE,
    /**
     * "Slice all" goes through the plates (Plater::priv::m_slice_all): it
     * alone selects and slices them until it is done.
     */
    val slicingAll: Boolean = false,
    /**
     * The preview's plate bar shows the statistics of all plates instead of
     * the current plate (GLCanvas3D's all plates stats item is selected).
     */
    val allPlatesStats: Boolean = false,
    /** The G-code of the current plate. */
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

    val busy: Boolean
        get() = importing || editing || slicing != null || changingPresets || enginePlate != EnginePlate(currentPlate, plates.size)

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
     * plate are not printed. Every placement must be settled, and "Slice all"
     * slices the plates itself while it runs.
     */
    val canSlice: Boolean get() = !slicingAll && plateSliceable

    /** The current plate can be sliced ([canSlice]), whoever slices it. */
    val plateSliceable: Boolean
        get() = engine.availability == EngineAvailability.READY && profiles != null && !busy &&
            // ModelInstance::is_printable(): inside the build volume and printed.
            copies().any { it.inspection.fit == BuildVolumeFit.INSIDE && it.printable } &&
            copies().none { it.placing || it.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE } &&
            // PartPlate::can_slice(): not while the print's validation fails (m_apply_invalid).
            validation?.error == null

    /** MainFrame::get_enable_slice_status() of "Slice all": always, while nothing is being sliced. */
    val canSliceAll: Boolean
        get() = engine.availability == EngineAvailability.READY && profiles != null && !busy && !slicingAll &&
            objects.none(PlateObject::placing)

    /**
     * MainFrame::get_enable_slice_status(): the slice button slices the plate
     * when it can and its G-code no longer applies, or all plates.
     */
    val sliceEnabled: Boolean
        get() = when (sliceMode) {
            SliceMode.PLATE -> canSlice && result == null
            SliceMode.ALL -> canSliceAll
        }

    /** Every copy on the plate, in the plate's order. */
    fun copies(): List<PlateInstance> = objects.flatMap(PlateObject::instances)

    /**
     * What the project holds of the plate now, settled as the undo stack keeps
     * it. How a copy fits judges it against the current plate
     * (update_print_volume_state), which selecting another plate changes and
     * the project does not keep.
     */
    fun projectContent(): ProjectContent = ProjectContent(
        objects = objects.map { plateObject ->
            plateObject.withInstances(
                plateObject.instances.map { it.copy(placing = false, inspection = it.inspection.copy(fit = BuildVolumeFit.INSIDE)) },
            )
        },
        plates = partPlates().map { PartPlate(name = it.name, locked = it.locked, settings = it.settings, layerGcodes = it.layerGcodes) },
    )

    /**
     * Plater::up_to_date(): a plate without objects, or one that has not
     * changed since the project was opened, saved or started, needs no save.
     */
    val projectUpToDate: Boolean get() = objects.isEmpty() || projectContent() == project.baseline && !project.otherChanges

    /**
     * ProjectDirtyStateManager::is_dirty(), the star of the desktop title: the
     * plate changed, or other presets or filament colours are selected.
     */
    val projectDirty: Boolean
        get() = projectContent() != project.baseline || project.otherChanges ||
            (project.presets != null && (profiles != project.presets || presets?.filamentColors.orEmpty() != project.filamentColors))
}
