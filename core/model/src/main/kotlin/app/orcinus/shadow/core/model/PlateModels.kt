package app.orcinus.shadow.core.model

/** A model on the build plate. */
sealed interface PlateObject {
    /** An STL imported into app storage, as Orca read it. */
    data class ImportedModel(
        val file: ImportedModelFile,
        val inspection: ModelInspection,
    ) : PlateObject

    /** The engine's built-in 20 mm calibration cube. */
    data object CalibrationCube : PlateObject
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
    val importing: Boolean = false,
    val plateObject: PlateObject? = null,
    val slicing: PlateSlicing? = null,
    val result: PlateSliceResult? = null,
    val problem: PlateProblem? = null,
) {
    val busy: Boolean get() = importing || slicing != null
    val canSlice: Boolean get() = engine.availability == EngineAvailability.READY && plateObject != null && !busy
}
