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
 * A part added to an object (ModelVolume): one of the shapes OrcaSlicer
 * generates, standing in the object's coordinates, with the settings it
 * overrides. Every copy of the object has it.
 */
data class ObjectPart(
    /** "Cube", "Cylinder", "Sphere", "Slab", "Cone", "Disc" or "Torus". */
    val shape: String,
    val type: VolumeType,
    /** The mesh file the 3D view draws it from. */
    val mesh: ScenePath,
    /** Its transformation in the object's coordinates. */
    val placement: Transform3,
    val settings: ModelSettings = ModelSettings(),
    /** The facets painted with the filaments of the plate (ModelVolume::mmu_segmentation_facets). */
    val painted: PaintedFacets = PaintedFacets(),
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

    /** The parts added to the object, after its own geometry. */
    val parts: List<ObjectPart>

    /** The height ranges of the object, from the bed up (layer_config_ranges). */
    val layerRanges: List<LayerRange>

    /** The facets of the object's own mesh painted with the filaments of the plate. */
    val painted: PaintedFacets

    /** The painted triangles as meshes, which the 3D view draws in the filament colours. */
    val paintedMeshes: List<PaintedMesh>

    /** An STL imported into app storage. */
    data class ImportedModel(
        val file: ImportedModelFile,
        override val instances: List<PlateInstance>,
        override val settings: ModelSettings = ModelSettings(),
        override val parts: List<ObjectPart> = emptyList(),
        override val layerRanges: List<LayerRange> = emptyList(),
        override val painted: PaintedFacets = PaintedFacets(),
        override val paintedMeshes: List<PaintedMesh> = emptyList(),
    ) : PlateObject

    /** The engine's built-in 20 mm calibration cube. */
    data class CalibrationCube(
        override val instances: List<PlateInstance>,
        override val settings: ModelSettings = ModelSettings(),
        override val parts: List<ObjectPart> = emptyList(),
        override val layerRanges: List<LayerRange> = emptyList(),
        override val painted: PaintedFacets = PaintedFacets(),
        override val paintedMeshes: List<PaintedMesh> = emptyList(),
    ) : PlateObject
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

/** A copy of an object, as the object list and the selection name it. */
data class PlateInstanceId(val mesh: ScenePath, val instance: Int = 0)

/** A part of an object, as the object list names it (ObjectList's itVolume). */
data class ObjectPartId(val mesh: ScenePath, val index: Int)

/** A height range of an object, as the object list names it (itLayer). */
data class LayerRangeId(val mesh: ScenePath, val index: Int)

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
)

enum class PlateProblemKind {
    ENGINE_UNAVAILABLE,
    IMPORT_FAILED,
    SLICE_FAILED,
    ENGINE_CRASHED,
    SLICE_CANCELLED,
    PLACEMENT_FAILED,
    PRESETS_FAILED,
}

data class PlateProblem(
    val kind: PlateProblemKind,
    /** Technical detail from the engine or the importer, when there is one. */
    val detail: String? = null,
)

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

    val selectedObjectPart: ObjectPart? get() = selectedPart?.let { id -> selectedPartOwner?.parts?.getOrNull(id.index) }

    /** The object the selected height range belongs to, and the range itself. */
    val selectedRangeOwner: PlateObject? get() = selectedRange?.let { id -> objects.firstOrNull { it.mesh == id.mesh } }

    val selectedLayerRange: LayerRange? get() = selectedRange?.let { id -> selectedRangeOwner?.layerRanges?.getOrNull(id.index) }

    /** The copy the tools work on; null when none or several are selected. */
    val selectedCopy: PlateInstance?
        get() = selectedInstance?.let { id -> selected?.instances?.getOrNull(id.instance) }

    /** The selected presets the plate is prepared and sliced with; null until a printer is set up. */
    val profiles: SlicingProfileSelection?
        get() = presets?.takeUnless(Presets::setupRequired)?.selection

    val busy: Boolean get() = importing || slicing != null || changingPresets

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
