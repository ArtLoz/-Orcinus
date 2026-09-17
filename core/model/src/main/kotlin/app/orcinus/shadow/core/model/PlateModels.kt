package app.orcinus.shadow.core.model

/**
 * A model on the build plate, as OrcaSlicer loaded and placed it. While
 * [placing], the object already stands at its new placement and OrcaSlicer has
 * yet to confirm it: its size and fit still describe the previous placement.
 * With [autoDrop] off (ModelInstance::auto_drop), manipulations leave the
 * object where the user put it, above the plate included.
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
    val plateObject: PlateObject,
    val gcode: OutputPath,
    val statistics: SliceStatistics,
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
    val plateObject: PlateObject? = null,
    val slicing: PlateSlicing? = null,
    val result: PlateSliceResult? = null,
    val problem: PlateProblem? = null,
) {
    val busy: Boolean get() = importing || slicing != null
    val canSlice: Boolean
        get() = engine.availability == EngineAvailability.READY && !busy &&
            plateObject != null && !plateObject.placing && plateObject.inspection.fit == BuildVolumeFit.INSIDE
}
