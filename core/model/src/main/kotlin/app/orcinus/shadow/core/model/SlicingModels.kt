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
    /** The copies of every object as placed, in the order the plate listed them. */
    data class Success(val inspections: List<List<ModelInspection>>) : PlateInspectionOutcome

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
/** Where one copy of an object stands (ModelInstance). */
data class PlacedInstance(
    /** The instance transformation, as inspection reported it. */
    val placement: Transform3,
    /** ModelInstance::auto_drop: off keeps a copy above the plate where the user put it. */
    val autoDrop: Boolean = true,
    /** ModelInstance::printable: a copy that is not printable is not sliced. */
    val printable: Boolean = true,
)

data class PlacedModel(
    val model: ModelSource,
    /** The mesh file inspection wrote for the object, which identifies it. */
    val mesh: ScenePath,
    /** The copies of the object on the plate (ModelObject::instances). */
    val instances: List<PlacedInstance>,
    /** The settings of the object (ModelObject::config), which its copies share. */
    val settings: ModelSettings = ModelSettings(),
    /** The parts added to the object (ModelObject::volumes). */
    val parts: List<ObjectPart> = emptyList(),
    /** The height ranges of the object (ModelObject::layer_config_ranges). */
    val layerRanges: List<LayerRange> = emptyList(),
    /** The facets of the object's own mesh painted with the filaments of the plate. */
    val painted: PaintedFacets = PaintedFacets(),
    /** Where the model's own mesh stands in the object; null centres it around the origin. */
    val frame: Transform3? = null,
    /** The settings of the model's own mesh (its first ModelVolume). */
    val volumeSettings: ModelSettings = ModelSettings(),
)

/**
 * An object of a model file, as Plater::priv::load_files() loaded it and
 * placed it on the plate: its own mesh (its first volume), with the
 * transformation it has in the object, its other volumes, and every copy.
 */
data class LoadedObject(
    /** ModelObject::name: the name the file gave it, or the file's name. */
    val name: String,
    /** The mesh file of its own mesh in its own coordinates, which it is loaded from. */
    val source: ModelPath,
    val frame: Transform3,
    val parts: List<ObjectPart>,
    val settings: ModelSettings,
    /** The name and settings the file gave its own mesh. */
    val volume: ObjectVolume,
    /** Every copy as it stands; the mesh is the object's own mesh for the 3D view. */
    val instances: List<ModelInspection>,
)

sealed interface ModelLoadOutcome {
    /** The message boxes the load showed; they informed only. */
    val notices: List<SettingsDialog>

    data class Success(val objects: List<LoadedObject>, override val notices: List<SettingsDialog>) : ModelLoadOutcome

    /** The load asks [question] before it adds anything; it is requested again with the answer. */
    data class Question(val question: SettingsDialog, override val notices: List<SettingsDialog>) : ModelLoadOutcome

    data class Failure(val message: String, override val notices: List<SettingsDialog> = emptyList()) : ModelLoadOutcome
}

@JvmInline
value class OutputPath(val value: String)

@JvmInline
value class ProfileId(val value: String)

data class SlicingProfileSelection(
    val printer: ProfileId,
    /** The first filament, which an object prints with unless it says otherwise. */
    val filament: ProfileId,
    val process: ProfileId,
    /**
     * PresetBundle::filament_presets: every filament of the plate, in the order
     * the sidebar lists them. Empty means the one [filament] names.
     */
    val filaments: List<ProfileId> = emptyList(),
) {
    /** The filaments of the plate, always at least one. */
    val allFilaments: List<ProfileId> get() = filaments.ifEmpty { listOf(filament) }
}

data class SliceRequest(
    val jobId: SliceJobId,
    /** The objects on the plate; those entirely off it are not printed. */
    val objects: List<PlacedModel>,
    val output: OutputPath,
    /** Where the engine also writes the G-code's toolpaths for the preview; null writes none. */
    val toolpaths: ScenePath? = null,
    /** Where the engine writes the wipe tower the slice builds, for the 3D view; null writes none. */
    val wipeTower: ScenePath? = null,
    val printerProfile: ProfileId,
    val filamentProfile: ProfileId,
    /** Every filament of the plate; empty prints with [filamentProfile] alone. */
    val filamentProfiles: List<ProfileId> = emptyList(),
    val processProfile: ProfileId,
    /** The settings of the plate, which the print is sliced with. */
    val plateSettings: ModelSettings = ModelSettings(),
    /** The pictures of the plate the G-code carries for the printer's screen; empty writes none. */
    val thumbnails: List<ThumbnailImage> = emptyList(),
)

/** The size of a G-code thumbnail in pixels, as the printer's "thumbnails" setting lists it. */
data class ThumbnailSize(val width: Int, val height: Int)

/**
 * A picture of the plate for the G-code, rendered as OrcaSlicer renders it
 * while it exports G-code (GLCanvas3D::render_thumbnail): RGBA pixels, the
 * bottom row first, as glReadPixels() reads them.
 */
data class ThumbnailImage(val size: ThumbnailSize, val path: ScenePath)

sealed interface ThumbnailSizesOutcome {
    /** The thumbnails of the printer's G-code, in order; none for a printer whose G-code holds none. */
    data class Success(val sizes: List<ThumbnailSize>) : ThumbnailSizesOutcome

    data class Failure(val message: String) : ThumbnailSizesOutcome
}

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
        /** The requested wipe tower mesh, or null when the plate prints none. */
        val wipeTower: ScenePath? = null,
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
