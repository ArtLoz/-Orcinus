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
    /** ModelObject::get_object_stl_stats().open_edges: edges of its meshes that bound one triangle only. */
    val openEdges: Long = 0,
    /**
     * ... and its volume in cubic millimetres: the model parts' meshes scaled
     * by the first copy, whatever copy this is, as Plater::show_object_info()
     * shows it.
     */
    val volume: Double = 0.0,
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
    /**
     * The copies of every object as placed, in the order the plate listed
     * them, and the number of [plates] afterwards, when the engine reports it:
     * arranging every plate adds plates for what the others do not hold.
     * After arranging, [objectOrder] gives the plate's indexes in the order
     * rebuild_plates_after_arrangement() sorts the objects in; empty when the
     * order stays.
     */
    data class Success(
        val inspections: List<List<ModelInspection>>,
        val plates: Int? = null,
        val objectOrder: List<Int> = emptyList(),
    ) : PlateInspectionOutcome

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
    /** ModelInstance::m_assemble_transformation; null while the copy has no place in the assembly view. */
    val assemble: Transform3? = null,
    /** ModelInstance::m_offset_to_assembly; null for none. */
    val offsetToAssembly: Vector3? = null,
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
    /** The model's own mesh as a volume (its first ModelVolume): its settings and units. */
    val volume: ObjectVolume = ObjectVolume(),
    /** ModelObject::name; empty keeps the name the engine gives the model. */
    val name: String = "",
    /** ModelObject::layer_height_profile; empty for none. */
    val layerHeightProfile: List<Double> = emptyList(),
    /** ModelObject::cut_id: the cut the object is a part of. */
    val cutId: CutId? = null,
    /** ModelObject::brim_points; empty for none. */
    val brimPoints: List<BrimPoint> = emptyList(),
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
    val instances: List<PlateInstance>,
    /** The facets of its own mesh painted with the filaments of the plate. */
    val painted: PaintedFacets = PaintedFacets(),
    val layerRanges: List<LayerRange> = emptyList(),
    /** ModelObject::cut_id */
    val cutId: CutId? = null,
    /** The name of the file it came from (ModelObject::input_file) when the load read several files; empty otherwise. */
    val inputFile: String = "",
    /** ModelObject::layer_height_profile; empty for none. */
    val layerHeightProfile: List<Double> = emptyList(),
    /** ModelObject::brim_points; empty for none. */
    val brimPoints: List<BrimPoint> = emptyList(),
)

/** What moved a volume: GLCanvas3D::do_move(), do_rotate() or do_scale(), which drop the copies differently. */
enum class VolumeManipulation {
    MOVE,
    ROTATE,
    SCALE,
}

/** GLGizmoMeshBoolean's operations (MeshBooleanOperation), in its order. */
enum class MeshBooleanOperation {
    /** "Union": the two volumes joined, the second gone. */
    UNION,

    /** "Difference": the second volume taken from the first. */
    DIFFERENCE,

    /** "Intersection": what the two volumes share. */
    INTERSECTION,
}

/** The file formats "Export as one STL" and "Export as one DRC" write. */
/**
 * GLGizmoSimplify::Configuration: how far a mesh is decimated, down to
 * [wantedCount] triangles with [useCount], or as far as a collapsed edge
 * keeps within [maxError] (the detail level) without it. [decimateRatio] is
 * the percentage of triangles taken out, which the window shows; a negative
 * [wantedCount] takes that percentage of the volume's triangles away.
 */
data class SimplifyConfig(
    val useCount: Boolean = false,
    val decimateRatio: Float = 50f,
    val wantedCount: Int = -1,
    val maxError: Float = 1f,
) {
    /** Configuration::fix_count_by_ratio() */
    fun withCountByRatio(triangleCount: Long): SimplifyConfig = copy(
        wantedCount = when {
            decimateRatio <= 0f -> triangleCount.toInt()
            decimateRatio >= 100f -> 0
            else -> Math.round(triangleCount * (100f - decimateRatio) / 100f)
        },
    )
}

sealed interface SimplifyOutcome {
    /** The decimated mesh has [triangles] triangles, written for the 3D view; the volume has [original]. */
    data class Success(val triangles: Long, val original: Long) : SimplifyOutcome

    data class Failure(val message: String) : SimplifyOutcome
}

enum class MeshFormat(val extension: String) {
    STL("stl"),
    DRC("drc"),
}

sealed interface MeshExportOutcome {
    /**
     * Written; [warning] is OrcaSlicer's notification when the negative
     * volumes could not be taken out, and [files] every file written when
     * each copy or object went into a file of its own.
     */
    data class Success(val warning: String? = null, val files: List<ExportedMesh> = emptyList()) : MeshExportOutcome

    data class Failure(val message: String) : MeshExportOutcome
}

/** What "Save project" came to. */
sealed interface ProjectSaveOutcome {
    data object Success : ProjectSaveOutcome

    data class Failure(val message: String) : ProjectSaveOutcome
}

sealed interface ModelLoadOutcome {
    /** The message boxes the load showed; they informed only. */
    val notices: List<SettingsDialog>

    /**
     * The objects that came of the load or the edit; none when an edit changed
     * nothing. [appended] objects join the end of the plate's list, and an
     * edited object leaves it (load_model_objects); otherwise the one edited
     * object takes the place of the one it was.
     */
    data class Success(
        val objects: List<LoadedObject>,
        override val notices: List<SettingsDialog>,
        val appended: Boolean = false,
        /** The volume of the edited object the object list selects afterwards (ModelObject::volumes). */
        val selectedVolume: Int? = null,
        /** What a 3MF file opened as a project brought besides its objects; null for any other load. */
        val project: LoadedProject? = null,
        /** The engine selected other presets: a project's, or more filaments for a 3MF file's objects. */
        val presetsChanged: Boolean = false,
        /** A calibration's load: what the plate's print is told, as the test set it; null for any other load. */
        val calibration: CalibrationParams? = null,
        /**
         * A calibration's load: the plates its objects stand on, which the PA
         * pattern's handles may fill beyond the first; 0 for any other load.
         */
        val plateCount: Int = 0,
        /**
         * load_files() of several files the user wants as separate objects
         * that keep their places: they loaded as one object, which
         * split_object() is to split into objects.
         */
        val splitToObjects: Boolean = false,
        /**
         * Plater::priv::reload_from_disk(): the files or names of the volumes
         * the file had nothing for (fail_list).
         */
        val failed: List<String> = emptyList(),
    ) : ModelLoadOutcome

    /** The load asks [question] before it adds anything; it is requested again with the answer. */
    data class Question(val question: SettingsDialog, override val notices: List<SettingsDialog>) : ModelLoadOutcome

    /**
     * A STEP file waits for StepMeshDialog, which opens with [options]; the load
     * is requested again with the user's choice.
     */
    data class StepMesh(
        val options: StepMeshOptions,
        override val notices: List<SettingsDialog>,
        /** The STEP file, by its place among the files of the load. */
        val file: Int = 0,
    ) : ModelLoadOutcome

    /**
     * An OBJ file with colours waits for ObjColorDialog, which opens on
     * [question]; the load is requested again with the user's choice.
     */
    data class ObjColors(
        val question: ObjColorQuestion,
        override val notices: List<SettingsDialog>,
        /** The OBJ file, by its place among the files of the load. */
        val file: Int = 0,
    ) : ModelLoadOutcome

    data class Failure(val message: String, override val notices: List<SettingsDialog> = emptyList()) : ModelLoadOutcome
}

/**
 * What ObjColorDialog opens on: the error it shows instead of its panel (the
 * material the MTL file lacks, or faces without a colour), or the colours the
 * file's are clustered into ("#RRGGBB") and their number, which it recommends.
 */
data class ObjColorQuestion(
    val lostMaterialName: String = "",
    val someFaceNoColor: Boolean = false,
    val clusterColors: List<String> = emptyList(),
    val recommended: Int = 0,
) {
    /** The dialog shows its error, and its OK loads the file without colours. */
    val error: Boolean get() = lostMaterialName.isNotEmpty() || someFaceNoColor
}

/**
 * ObjColorDialog's answer: its OK gives the filament (1-based, as its combo
 * boxes number them) of every colour of the last clustering it showed; its
 * Cancel gives none.
 */
data class ObjColorChoice(val clusterFilaments: List<Int> = emptyList())

/** StepMeshDialog's values: the deflections a STEP file is meshed with, and whether its compounds and compsolids split into objects. */
data class StepMeshOptions(val linearDeflection: Double, val angleDeflection: Double, val splitCompound: Boolean)

/** StepMeshDialog's OK: its values, and "Don't show again". */
data class StepMeshChoice(val options: StepMeshOptions, val dontShowAgain: Boolean = false)

/** StepMeshDialog open on the STEP file [source], with [options] to begin with. */
data class StepMeshQuestion(val source: ModelPath, val options: StepMeshOptions)

/**
 * How a 3MF file loads (LoadType of the desktop app's Plater): its objects
 * alone ("Import geometry only"), or as a project with its settings and
 * presets ("Open as project"). Files of other types load their objects alone.
 */
enum class ModelLoad { GEOMETRY, PROJECT }

/**
 * A plate of a 3MF project (PartPlate, PlateData): its name, whether it is
 * locked, its own settings (PartPlate::config) with the project's values the
 * app keeps with each plate (its wipe tower's position and the flushing
 * volumes), the codes on its layers (Model::plates_custom_gcodes) and, to be
 * saved, its picture.
 */
data class ProjectPlate(
    val name: String = "",
    val locked: Boolean = false,
    val settings: ModelSettings = ModelSettings(),
    val layerGcodes: List<LayerGcode> = emptyList(),
    val thumbnail: ThumbnailImage? = null,
    /**
     * export_3mf()'s other pictures of the plate, as large as [thumbnail]:
     * without light, from the top, and its pick picture.
     */
    val noLightThumbnail: ThumbnailImage? = null,
    val topThumbnail: ThumbnailImage? = null,
    val pickThumbnail: ThumbnailImage? = null,
    /** While the plate's slice result is valid: what it keeps of the slice, and its G-code. */
    val sliceInfo: ScenePath? = null,
    val gcode: OutputPath? = null,
)

/**
 * The plates whose G-code a project's 3MF file carries
 * (Plater::export_gcode_3mf()): none for "Save project", the current plate's
 * for "Export plate sliced file", every sliced plate's for "Export all plate
 * sliced file".
 */
/** What a project's file holds besides the plates: the G-code of none, the current or every sliced plate; GENERIC is "Export Generic 3MF". */
enum class SlicedPlates { NONE, CURRENT, ALL, GENERIC }

/** A mesh file the engine wrote, and the object it is named after (ModelObject::name). */
data class ExportedMesh(val name: String, val path: ScenePath)

/** A copy the selection holds: the object by its index on the plate, and its copy, or -1 for every copy. */
data class SelectedCopy(val objectIndex: Int, val instanceIndex: Int)

/** A 3MF file opened as a project: its plates, at least one (PartPlateList::load_from_3mf_structure). */
data class LoadedProject(
    val plates: List<ProjectPlate> = listOf(ProjectPlate()),
    /**
     * Where the engine kept what the project holds besides its objects: the
     * designer, the model's information and its auxiliary files, which its
     * next save writes again.
     */
    val info: ScenePath? = null,
)

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
    /** Where the engine writes what the plate keeps of the slice for its 3MF files; null writes none. */
    val sliceInfo: ScenePath? = null,
    val printerProfile: ProfileId,
    val filamentProfile: ProfileId,
    /** Every filament of the plate; empty prints with [filamentProfile] alone. */
    val filamentProfiles: List<ProfileId> = emptyList(),
    val processProfile: ProfileId,
    /** The settings of the plate, which the print is sliced with. */
    val plateSettings: ModelSettings = ModelSettings(),
    /** The pictures of the plate the G-code carries for the printer's screen; empty writes none. */
    val thumbnails: List<ThumbnailImage> = emptyList(),
    /** The codes the layer slider put on the layers (Model::plates_custom_gcodes). */
    val layerGcodes: List<LayerGcode> = emptyList(),
    /** The calibration the plate prints (Print::set_calib_params); null for none. */
    val calibration: CalibrationParams? = null,
    /**
     * Model::calib_pa_pattern: the PA pattern whose G-code the plate's handles
     * print in place of the layer codes; null for none.
     */
    val paPattern: CalibrationParams? = null,
)

/** CalibMode of calib.hpp: the calibration a plate prints, in its order. */
enum class CalibrationMode {
    NONE,
    PA_LINE,
    PA_PATTERN,
    PA_TOWER,
    AUTO_PA_LINE,
    FLOW_RATE,
    TEMP_TOWER,
    VOL_SPEED_TOWER,
    VFA_TOWER,
    RETRACTION_TOWER,
    INPUT_SHAPING_FREQ,
    INPUT_SHAPING_DAMP,
    CORNERING,
}

/**
 * Calib_Params of calib.hpp: the calibration and its figures, which the
 * dialogs of the Calibration menu set and G-code generation reads from the
 * print of the plate.
 */
data class CalibrationParams(
    val mode: CalibrationMode,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val step: Double = 0.0,
    val printNumbers: Boolean = false,
    val freqStartX: Double = 0.0,
    val freqEndX: Double = 0.0,
    val freqStartY: Double = 0.0,
    val freqEndY: Double = 0.0,
    val testModel: Int = 0,
    val shaperType: String = "",
    val accelerations: List<Double> = emptyList(),
    val speeds: List<Double> = emptyList(),
    val extruderId: Int = 0,
)

/**
 * What the dialogs of Input Shaping and Cornering read of the printer:
 * gcode_flavor as the configuration writes it, whether Marlin 2 runs with a
 * junction deviation (Plater::has_junction_deviation), and the input shapers
 * the firmware knows (get_shaper_type_values of calib_dlg.cpp).
 */
data class CalibrationPrinter(
    val gcodeFlavor: String,
    val junctionDeviation: Boolean,
    val shaperTypes: List<String>,
)

/** GLGizmoCut3D::bounding_box(): the solid parts of the copy the cut gizmo is open on, in the world. */
sealed interface CutObjectOutcome {
    data class Success(val min: Vector3, val max: Vector3) : CutObjectOutcome

    data class Failure(val message: String) : CutObjectOutcome
}

/** What the cut gizmo shows of a plane. */
data class CutPlaneDescription(
    /**
     * transformed_bounding_box(): the solid parts in the plane's frame, its
     * centre at the origin, which "Build Volume" gives the size of.
     */
    val min: Vector3,
    val max: Vector3,
    /** ObjectClipper::has_valid_contour(): the plane goes through the object. */
    val validContour: Boolean,
    /** The outline of the section, 0.4 mm wide, in world coordinates; null for none. */
    val contour: ScenePath?,
    /** The section itself, which tells a touch on the plane inside it from one outside it; null for none. */
    val section: ScenePath? = null,
    /**
     * check_and_update_connectors_state(): the indexes of the connectors that
     * cannot be cut with, how many lie out of the section and out of the
     * object, and whether some overlap.
     */
    val invalidConnectors: List<Int> = emptyList(),
    val outsideCutContour: Int = 0,
    val outsideBoundingBox: Int = 0,
    val overlap: Boolean = false,
    /** The shape of every connector at unit size (get_connector_mesh()), for the 3D view. */
    val connectorMeshes: List<ScenePath> = emptyList(),
    /** The dovetail cut: the plane with its grooves in the plane's frame, and has_valid_groove(). */
    val groovePlane: ScenePath? = null,
    val validGroove: Boolean = true,
    /** The parts the dovetail cut makes, in world coordinates, shown in the object's place. */
    val previewParts: List<CutPreviewPart> = emptyList(),
)

/** A part of the dovetail cut as the gizmo previews it (PartSelection::Part). */
data class CutPreviewPart(val mesh: ScenePath, val upper: Boolean, val modifier: Boolean)

sealed interface CutPlaneOutcome {
    data class Success(val plane: CutPlaneDescription) : CutPlaneOutcome

    data class Failure(val message: String) : CutPlaneOutcome
}

/** The pieces of a right click of the cut gizmo, in world coordinates, [CutPreviewPart.upper] where they go. */
sealed interface CutPartsOutcome {
    data class Success(val parts: List<CutPreviewPart>) : CutPartsOutcome

    data class Failure(val message: String) : CutPartsOutcome
}

sealed interface CalibrationPrinterOutcome {
    data class Success(val printer: CalibrationPrinter) : CalibrationPrinterOutcome

    data class Failure(val message: String) : CalibrationPrinterOutcome
}

/**
 * What FlowRateCalibrationDialog asks Plater::calib_flowrate() for: the YOLO
 * tests when [linear] (pass 1 the recommended one, pass 2 the perfectionist
 * one), the coarse first or fine second pass otherwise, and the top surface
 * pattern, an InfillPattern of the configuration.
 */
data class FlowRateCalibration(
    val linear: Boolean,
    val pass: Int,
    val topSurfacePattern: String,
)

/** CustomGCode::Type: what a code on a layer does. */
enum class LayerGcodeType {
    COLOR_CHANGE,
    PAUSE_PRINT,
    TOOL_CHANGE,
    TEMPLATE,
    CUSTOM,
}

/**
 * CustomGCode::Item: a code the print runs where the layer at [printZ] starts,
 * as the preview's layer slider puts it there. [extruder] is the filament a
 * filament change switches to and [color] its colour; [extra] is the G-code of
 * a custom one.
 */
data class LayerGcode(
    val printZ: Double,
    val type: LayerGcodeType,
    val extruder: Int = 1,
    val color: String = "",
    val extra: String = "",
)

/**
 * What the layer slider's menu offers for a sliced print (IMSlider::SetDrawMode,
 * SetModeAndOnlyExtruder): nothing but "Jump to Layer" for a print by object,
 * a filament change while the objects print with one filament and the print is
 * no spiral vase, and a template when the printer has one.
 */
data class LayerGcodeRules(
    val sequential: Boolean = false,
    val canChangeFilament: Boolean = true,
    val hasTemplate: Boolean = false,
)

/** The size of a G-code thumbnail in pixels, as the printer's "thumbnails" setting lists it. */
data class ThumbnailSize(val width: Int, val height: Int)

/**
 * What a picture of a plate shows and how: ThumbnailsParams' printable_only
 * with the camera, picking and light GLCanvas3D::render_thumbnail() is asked
 * for. The G-code's thumbnails show the printable copies; a project's
 * pictures of a plate (Plater::export_3mf()) show every copy.
 */
enum class PlatePicture(val printableOnly: Boolean) {
    /** The G-code's thumbnails: from the default isometric view, zoomed to the copies. */
    GCODE(true),

    /** A project's picture of a plate (plate_N.png), as the G-code's thumbnails are drawn. */
    PLATE(false),

    /** plate_no_light_N.png: the same view without light, each volume's alpha telling its filament. */
    NO_LIGHT(false),

    /** top_N.png: the whole plate from the top (ViewAngleType::Top_Plate). */
    TOP(false),

    /** pick_N.png: the whole plate from the top, every copy in the colour of its loaded_id. */
    PICK(false),
}

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
    /** PrintStatistics::total_cost. */
    val cost: Double = 0.0,
    /** The filaments the plate prints with (PartPlate::get_extruders) and what the print used of each. */
    val filaments: List<FilamentUsage> = emptyList(),
) {
    init {
        require(layerCount >= 0) { "Layer count must not be negative" }
        require(estimatedPrintTimeSeconds >= 0) { "Print time must not be negative" }
        require(filamentMillimeters >= 0.0) { "Filament length must not be negative" }
    }
}

/** A length of filament in metres and its weight in grams. */
data class FilamentAmount(val meters: Double, val grams: Double) {
    operator fun plus(other: FilamentAmount) = FilamentAmount(meters + other.meters, grams + other.grams)

    companion object {
        val NONE = FilamentAmount(0.0, 0.0)
    }
}

/**
 * What a print used of one filament ([filament], from 1), as
 * GCodeViewer::render_all_plates_stats() turns the volumes per extruder into
 * filament: for the objects, their support, the flushing and the wipe tower.
 */
data class FilamentUsage(
    val filament: Int,
    val model: FilamentAmount = FilamentAmount.NONE,
    val support: FilamentAmount = FilamentAmount.NONE,
    val flushed: FilamentAmount = FilamentAmount.NONE,
    val wipeTower: FilamentAmount = FilamentAmount.NONE,
)

/** The filament usages of flattened [amounts]: eight for each of [filaments], metres then grams of the model, support, flushing and wipe tower. */
fun filamentUsagesOf(filaments: IntArray, amounts: DoubleArray): List<FilamentUsage> = filaments.mapIndexed { index, filament ->
    fun amount(part: Int) = FilamentAmount(amounts.getOrElse(index * 8 + part * 2) { 0.0 }, amounts.getOrElse(index * 8 + part * 2 + 1) { 0.0 })
    FilamentUsage(filament, amount(0), amount(1), amount(2), amount(3))
}

/** The flattened amounts [filamentUsagesOf] reads. */
fun List<FilamentUsage>.amounts(): DoubleArray = flatMap { usage ->
    listOf(usage.model, usage.support, usage.flushed, usage.wipeTower).flatMap { listOf(it.meters, it.grams) }
}.toDoubleArray()

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
        /** The requested slice info, or null when none was written. */
        val sliceInfo: ScenePath? = null,
        val layerGcodeRules: LayerGcodeRules = LayerGcodeRules(),
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
