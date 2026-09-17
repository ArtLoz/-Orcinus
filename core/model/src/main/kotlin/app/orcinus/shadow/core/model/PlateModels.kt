package app.orcinus.shadow.core.model

/**
 * A model on the build plate, as OrcaSlicer loaded and placed it. While
 * [placing], OrcaSlicer has yet to settle the object's placement: its size and
 * fit still describe the placement it last confirmed. With [autoDrop] off
 * (ModelInstance::auto_drop), manipulations leave the object where the user
 * put it, above the plate included.
 */
sealed interface PlateObject {
    val inspection: ModelInspection
    val placing: Boolean
    val autoDrop: Boolean

    /** An STL imported into app storage. */
    data class ImportedModel(
        val file: ImportedModelFile,
        override val inspection: ModelInspection,
        override val placing: Boolean = false,
        override val autoDrop: Boolean = true,
    ) : PlateObject

    /** The engine's built-in 20 mm calibration cube. */
    data class CalibrationCube(
        override val inspection: ModelInspection,
        override val placing: Boolean = false,
        override val autoDrop: Boolean = true,
    ) : PlateObject
}

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
)

enum class PlateProblemKind {
    ENGINE_UNAVAILABLE,
    IMPORT_FAILED,
    SLICE_FAILED,
    ENGINE_CRASHED,
    SLICE_CANCELLED,
    PLACEMENT_FAILED,
}

data class PlateProblem(
    val kind: PlateProblemKind,
    /** Technical detail from the engine or the importer, when there is one. */
    val detail: String? = null,
)

/** Everything the app knows about the plate being prepared and sliced. */
data class PlateState(
    val profiles: SlicingProfileSelection,
    val engine: EngineState = EngineState(),
    /** The plate of the selected printer; null until the engine described it. */
    val plate: PlateDescription? = null,
    val importing: Boolean = false,
    /** The objects on the plate, in the order they were added, as OrcaSlicer's object list shows them. */
    val objects: List<PlateObject> = emptyList(),
    val slicing: PlateSlicing? = null,
    val result: PlateSliceResult? = null,
    val problem: PlateProblem? = null,
) {
    val busy: Boolean get() = importing || slicing != null

    /**
     * GLCanvas3D::reload_scene() enables slicing when an object is inside the
     * build volume and none lies across its boundary; objects entirely off the
     * plate are not printed. Every placement must be settled.
     */
    val canSlice: Boolean
        get() = engine.availability == EngineAvailability.READY && !busy &&
            objects.any { it.inspection.fit == BuildVolumeFit.INSIDE } &&
            objects.none { it.placing || it.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE }
}
