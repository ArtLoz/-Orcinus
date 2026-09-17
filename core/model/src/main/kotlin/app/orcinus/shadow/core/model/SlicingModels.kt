package app.orcinus.shadow.core.model

@JvmInline
value class EngineVersion(val value: String)

data class EngineStatus(
    val version: EngineVersion,
    val ready: Boolean,
    /** Why the engine is not ready; null when [ready]. */
    val message: String? = null,
)

@JvmInline
value class SliceJobId(val value: String) {
    init {
        require(value.isNotBlank()) { "Slice job id must not be blank" }
    }
}

@JvmInline
value class ModelPath(val value: String)

@JvmInline
value class ExternalDocumentReference(val value: String) {
    init {
        require(value.isNotBlank()) { "External document reference must not be blank" }
    }
}

data class ImportedModelFile(
    val path: ModelPath,
    val displayName: String,
)

enum class ModelImportFailureCode {
    READ_FAILED,
    EMPTY_FILE,
    FILE_TOO_LARGE,
}

sealed interface ModelImportOutcome {
    data class Success(val model: ImportedModelFile) : ModelImportOutcome

    data class Failure(
        val code: ModelImportFailureCode,
        val message: String,
    ) : ModelImportOutcome
}

data class ModelDimensions(
    val widthMillimeters: Double,
    val depthMillimeters: Double,
    val heightMillimeters: Double,
) {
    init {
        require(widthMillimeters >= 0.0)
        require(depthMillimeters >= 0.0)
        require(heightMillimeters >= 0.0)
    }
}

/** Where an object stands relative to the printer's build volume, as OrcaSlicer judges it. */
enum class BuildVolumeFit {
    /** Inside the build volume: printable. */
    INSIDE,

    /** Across the plate boundary or above the build height: OrcaSlicer does not slice the plate. */
    PARTLY_OUTSIDE,

    /** Entirely off the plate: not printed. */
    OUTSIDE,
}

/** A model as OrcaSlicer loads and places it on the plate of the selected printer. */
data class ModelInspection(
    val facetCount: Long,
    /** Size of the placed object's bounding box. */
    val dimensions: ModelDimensions,
    /** Centre of that bounding box, which scaling keeps in place. */
    val boxCenter: Vector3,
    /** The mesh in object coordinates. */
    val mesh: ScenePath,
    /** Where the object stands on the plate: its instance transformation. */
    val placement: Transform3,
    val fit: BuildVolumeFit,
    /** The smallest sphere around the placed object, which the rotation gizmo turns about. */
    val boundingSphere: BoundingSphere,
    /** The instance rotation in degrees, as OrcaSlicer's rotation window shows it. */
    val rotationDegrees: Vector3,
    /** Size of the bounding box without the instance scaling, which scale ratios refer to. */
    val unscaledDimensions: ModelDimensions,
) {
    init {
        require(facetCount > 0) { "A valid model must contain facets" }
    }
}

sealed interface ModelInspectionOutcome {
    data class Success(val inspection: ModelInspection) : ModelInspectionOutcome

    data class Failure(val message: String) : ModelInspectionOutcome
}

sealed interface PlateInspectionOutcome {
    /** Every object as placed, in the order the plate listed them. */
    data class Success(val inspections: List<ModelInspection>) : PlateInspectionOutcome

    data class Failure(val message: String) : PlateInspectionOutcome
}

enum class BuiltInModel {
    CALIBRATION_CUBE_20_MM,
}

sealed interface ModelSource {
    data class LocalFile(val path: ModelPath) : ModelSource

    data class BuiltIn(val model: BuiltInModel) : ModelSource
}

/** An object on the plate as the engine loads it. */
data class PlacedModel(
    val model: ModelSource,
    /** The mesh file inspection wrote for the object, which identifies it. */
    val mesh: ScenePath,
    /** Where the object stands on the plate: its instance transformation. */
    val placement: Transform3,
    /** ModelInstance::auto_drop: off keeps an object above the plate where the user put it. */
    val autoDrop: Boolean = true,
)

@JvmInline
value class OutputPath(val value: String)

@JvmInline
value class ProfileId(val value: String)

data class SlicingProfileSelection(
    val printer: ProfileId,
    val filament: ProfileId,
    val process: ProfileId,
)

data class SliceRequest(
    val jobId: SliceJobId,
    /** The objects on the plate; those entirely off it are not printed. */
    val objects: List<PlacedModel>,
    val output: OutputPath,
    /** Where the engine also writes the G-code's toolpaths for the preview; null writes none. */
    val toolpaths: ScenePath? = null,
    val printerProfile: ProfileId,
    val filamentProfile: ProfileId,
    val processProfile: ProfileId,
)

enum class SliceStage {
    PREPARING,
    LOADING_MODEL,
    APPLYING_PROFILES,
    SLICING,
    GENERATING_GCODE,
    WRITING_OUTPUT,
    COMPLETED,
}

data class SliceProgress(
    val jobId: SliceJobId,
    val fraction: Float,
    val stage: SliceStage,
    val detail: String? = null,
) {
    init {
        require(fraction in 0f..1f) { "Progress fraction must be between 0 and 1" }
    }
}

data class SliceStatistics(
    val layerCount: Int,
    val estimatedPrintTimeSeconds: Long,
    val filamentMillimeters: Double,
) {
    init {
        require(layerCount >= 0) { "Layer count must not be negative" }
        require(estimatedPrintTimeSeconds >= 0) { "Print time must not be negative" }
        require(filamentMillimeters >= 0.0) { "Filament length must not be negative" }
    }
}

enum class SliceFailureCode {
    INVALID_REQUEST,
    ENGINE_BUSY,
    ENGINE_UNAVAILABLE,
    MODEL_READ_FAILED,
    PROFILE_NOT_FOUND,
    /** The print failed Orca's validation, for example an object outside the bed. */
    INVALID_PRINT,
    OUTPUT_WRITE_FAILED,
    SLICING_FAILED,
    /** The engine process terminated during the job, for example a native crash. */
    ENGINE_CRASHED,
}

sealed interface SliceOutcome {
    val jobId: SliceJobId

    data class Success(
        override val jobId: SliceJobId,
        val gcodePath: OutputPath,
        val statistics: SliceStatistics,
        /** The requested toolpaths file, or null when none was written. */
        val toolpaths: ScenePath? = null,
    ) : SliceOutcome

    data class Failure(
        override val jobId: SliceJobId,
        val code: SliceFailureCode,
        val message: String,
        val recoverable: Boolean,
    ) : SliceOutcome

    data class Cancelled(
        override val jobId: SliceJobId,
    ) : SliceOutcome
}
