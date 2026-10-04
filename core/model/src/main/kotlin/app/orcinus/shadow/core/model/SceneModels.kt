package app.orcinus.shadow.core.model

// What the 3D view draws, as OrcaSlicer computes it. Coordinates are
// millimetres in OrcaSlicer's scene: X to the right and Y to the back of the
// plate, Z up.

/** A file the engine wrote for the 3D view. */
@JvmInline
value class ScenePath(val value: String)

data class Point2(val x: Double, val y: Double)

data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun get(axis: Int) = when (axis) {
        0 -> x
        1 -> y
        else -> z
    }
}

/** A sphere around an object, as OrcaSlicer's Selection::get_bounding_sphere(). */
data class BoundingSphere(val center: Vector3, val radius: Double)

/** How OrcaSlicer commits a change of an object's placement. */
sealed interface Manipulation {
    /** GLCanvas3D::do_move(): an object above the plate drops onto it. */
    data object Move : Manipulation

    /** GLCanvas3D::do_rotate(): an object that was not sunk into the plate rests on it. */
    data object Rotate : Manipulation

    /** GLCanvas3D::do_scale(): as a rotation. */
    data object Scale : Manipulation

    /** The rotation window's reset to no rotation, keeping position and scale. */
    data object ResetRotation : Manipulation

    /** GLGizmoFlatten: the face with [normal], in object coordinates, turns down and the object rests on the plate. */
    data class LayOnFace(val normal: Vector3) : Manipulation

    /** ObjectList::toggle_auto_drop() turning auto drop on: ModelObject::ensure_on_bed(). */
    data object EnsureOnBed : Manipulation

    /**
     * GLCanvas3D::mirror_selection(): the copy mirrored along [axis] about the
     * centre of its bounding box, then resting on the plate as after a rotation.
     */
    data class Mirror(val axis: Axis) : Manipulation

    /** Selection::center(): the copy over the centre of the plate. */
    data object Center : Manipulation

    /** Selection::drop(): the copy down or up to the plate, whether it drops by itself or not. */
    data object Drop : Manipulation
}

/** Slic3r::Axis. */
enum class Axis { X, Y, Z }

/**
 * How OrcaSlicer's jobs place several objects of the plate at once. The
 * copies on the [lockedPlates] (PartPlate::is_locked, by plate index) stay
 * where they are.
 */
sealed interface PlateManipulation {
    val lockedPlates: Set<Int> get() = emptySet()

    /**
     * The settings of every plate (PartPlate's config), whose wipe_tower_x and
     * wipe_tower_y place the wipe towers arranging keeps clear of
     * (ArrangeJob::prepare_wipe_tower()); none for no towers.
     */
    val plateSettings: List<ModelSettings> get() = emptyList()

    /**
     * OrientJob from the toolbar: the objects with the [selected] mesh files, or
     * every object when none is selected, turn to the orientation with the least
     * support area and rest on the plate.
     */
    data class AutoOrient(
        val selected: Set<ScenePath> = emptySet(),
        override val lockedPlates: Set<Int> = emptySet(),
    ) : PlateManipulation

    /**
     * ArrangeJob from the arrange options (prepare_all): every object arranged
     * on the plates with [settings], plates added for what they do not hold.
     */
    data class Arrange(
        val settings: ArrangeSettings,
        override val lockedPlates: Set<Int> = emptySet(),
        override val plateSettings: List<ModelSettings> = emptyList(),
    ) : PlateManipulation

    /**
     * Plater::on_config_change() for another printer: every object stays where
     * it is, and whether it fits is judged against that printer's build volume.
     */
    data object UpdatePrintVolume : PlateManipulation

    /**
     * ArrangeJob from a menu (prepare_partplate), as the clone dialog starts it:
     * the copies on the plate or over its edge arranged with [settings]; the
     * ones off it stay where they are.
     */
    data class ArrangePlate(
        val settings: ArrangeSettings,
        override val lockedPlates: Set<Int> = emptySet(),
        override val plateSettings: List<ModelSettings> = emptyList(),
    ) : PlateManipulation

    /**
     * FillBedJob with instances ("Fill bed with instances"): copies of the
     * object with the [mesh] file, modelled on its copy [instance] (null for the
     * whole object), added while the free area of the plate holds more; then
     * the plate arranged as [ArrangePlate] arranges it.
     */
    data class FillBed(
        val mesh: ScenePath,
        val instance: Int?,
        val settings: ArrangeSettings,
        override val lockedPlates: Set<Int> = emptySet(),
        override val plateSettings: List<ModelSettings> = emptyList(),
    ) : PlateManipulation
}

/** Where copied objects go (CopyPlacement in the engine). */
enum class CopyPlacement {
    /** Selection::copy_to_clipboard(), ObjectList::instances_to_separated_object(): where their sources stand. */
    KEEP,

    /**
     * Selection::paste_objects_from_clipboard(): the empty cell of the plate
     * nearest to each source, several sources keeping their layout.
     */
    PASTE,
}

/** OrcaSlicer's arrange options (GLCanvas3D::ArrangeSettings), with its defaults. */
data class ArrangeSettings(
    /** Spacing between objects in millimetres; 0 means auto spacing. */
    val distance: Double = 0.0,
    val enableRotation: Boolean = false,
    val allowMultiMaterialsOnSamePlate: Boolean = true,
    /** Only when rotation is not allowed. */
    val alignToYAxis: Boolean = false,
)

/**
 * A face an object can lie on, as OrcaSlicer's "Lay on face" offers it: a flat
 * region of its convex hull, shrunk and rounded for display. Object coordinates.
 */
data class FlatteningPlane(
    /** Outward normal. */
    val normal: Vector3,
    /** A convex polygon, counter-clockwise seen from outside. */
    val polygon: List<Vector3>,
)

/** ECoordinatesType: the axes a gizmo window works along. */
enum class CoordinateSystem {
    /** World coordinates */
    WORLD,

    /** Object coordinates: the copy's. */
    INSTANCE,

    /** Part coordinates: the volume's own. */
    LOCAL,
}

/**
 * One box of a volume selected alone
 * (Selection::get_bounding_box_in_reference_system()): its size along the
 * axes of the reference system, and its centre in the world.
 */
data class VolumeBox(val size: Vector3, val center: Vector3)

/**
 * A volume selected alone (Selection::Volume) as the engine measures it where
 * its copy stands: the smallest sphere around it in the world
 * (Selection::get_bounding_sphere()), which the rotation gizmo turns it about,
 * and its boxes in the world's axes, in its copy's and in its own
 * (ECoordinatesType World, Instance and Local).
 */
data class VolumeDescription(
    val sphere: BoundingSphere,
    val world: VolumeBox,
    val instance: VolumeBox,
    val local: VolumeBox,
    /**
     * Plater::show_object_info() of the volume: its mesh's volume in cubic
     * millimetres, scaled where it stands, its triangles and its open edges.
     */
    val volume: Double = 0.0,
    val facets: Long = 0,
    val openEdges: Long = 0,
) {
    /**
     * As the engine writes it: the sphere's centre and radius, then each box's
     * size and centre, then the volume, the triangles and the open edges.
     */
    fun values(): DoubleArray = (
        listOf(sphere.center.x, sphere.center.y, sphere.center.z, sphere.radius) +
            listOf(world, instance, local).flatMap { box -> listOf(box.size.x, box.size.y, box.size.z, box.center.x, box.center.y, box.center.z) } +
            listOf(volume, facets.toDouble(), openEdges.toDouble())
        ).toDoubleArray()

    companion object {
        /** The numbers of [values]. */
        const val SIZE = 25

        fun of(values: DoubleArray): VolumeDescription {
            require(values.size == SIZE) { "A volume is described by $SIZE numbers" }
            fun vector(at: Int) = Vector3(values[at], values[at + 1], values[at + 2])
            fun box(at: Int) = VolumeBox(vector(at), vector(at + 3))
            return VolumeDescription(
                BoundingSphere(vector(0), values[3]),
                box(4),
                box(10),
                box(16),
                volume = values[22],
                facets = values[23].toLong(),
                openEdges = values[24].toLong(),
            )
        }
    }
}

sealed interface VolumeDescriptionOutcome {
    data class Success(val description: VolumeDescription) : VolumeDescriptionOutcome

    data class Failure(val message: String) : VolumeDescriptionOutcome
}

sealed interface FlatteningPlanesOutcome {
    /** Largest faces first. */
    data class Success(val planes: List<FlatteningPlane>) : FlatteningPlanesOutcome

    data class Failure(val message: String) : FlatteningPlanesOutcome
}

/** A colour with components from 0 to 1, as OrcaSlicer's ColorRGBA. */
data class ColorRgba(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
)

/** A filament colour as OrcaSlicer's presets hold it ("#RRGGBB"); null when the text is none. */
fun parseFilamentColor(value: String): ColorRgba? {
    val hex = value.removePrefix("#")
    if (hex.length < 6) return null
    val rgb = hex.take(6).toLongOrNull(radix = 16) ?: return null
    return ColorRgba(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
    )
}

/** A 4 x 4 affine transformation, stored column by column as OrcaSlicer's Transform3d. */
data class Transform3(val columns: List<Double>) {
    init {
        require(columns.size == 16) { "A transformation has 16 elements" }
    }

    companion object {
        val IDENTITY = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 })
    }
}

/**
 * The plate of the selected printer as OrcaSlicer draws it (Bed3D and
 * PartPlate). Triangles list three points each and lines two points each.
 */
data class PlateGeometry(
    /** Printable area contour, counter-clockwise. */
    val printableArea: List<Point2>,
    val printableHeight: Double,
    val plateTriangles: List<Point2>,
    val excludeTriangles: List<Point2>,
    /** Grid lines and the plate contour. */
    val thinGridLines: List<Point2>,
    /** Every fifth grid line. */
    val boldGridLines: List<Point2>,
    /** Mesh of the printer's bed model; null when it has none. */
    val bedModel: ScenePath?,
    /** RGBA PNG of the bed texture; null when it has none. */
    val bedTexture: ScenePath?,
    /** BuildVolume::type() of the printable area. */
    val buildVolumeShape: BuildVolumeShape = BuildVolumeShape.RECTANGLE,
    /** BuildVolume::circle() of a circular one; null for another shape. */
    val circle: PlateCircle? = null,
)

/** BuildVolume_Type, in its order: the shape of the printable area. */
enum class BuildVolumeShape { RECTANGLE, CIRCLE, CONVEX, CUSTOM }

/** The circle of a circular bed, in millimetres on the first plate. */
data class PlateCircle(val center: Point2, val radius: Double)

data class PlateDescription(
    val geometry: PlateGeometry,
    /** Colour of the first filament, which OrcaSlicer paints objects with. */
    val filamentColor: ColorRgba,
    /** Mesh of the printer's hotend, the preview's tool marker (GCodeViewer::init()); null when it has none. */
    val hotendModel: ScenePath? = null,
)

/**
 * The wipe tower of the plate, which OrcaSlicer draws as a volume of its own
 * (GLVolumeCollection::load_wipe_tower_preview): a box striped with the colours
 * of the filaments printed on the plate, standing where wipe_tower_x and
 * wipe_tower_y of the project put it.
 */
data class WipeTower(
    /** Whether the plate prints one: the process preset asks for it and several filaments are printed. */
    val shown: Boolean = false,
    /** Its front left corner on the plate. */
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val depth: Double = 0.0,
    /** As tall as the tallest object of the plate. */
    val height: Double = 0.0,
    /** wipe_tower_rotation_angle, in degrees. */
    val rotation: Double = 0.0,
    val brimWidth: Double = 0.0,
    /** The filaments printed on the plate, 1-based: the tower is striped with their colours. */
    val filaments: List<Int> = emptyList(),
    /** enable_prime_tower of the edited process preset, which the object menu's Flush Options need. */
    val primeTower: Boolean = false,
    /** The flush options the edited process preset enables, which an object follows unless it sets them. */
    val flushInto: Set<FlushOption> = emptySet(),
)

/**
 * The object menu's Flush Options (MenuFactory::append_menu_items_flush_options,
 * FREQ_SETTINGS_BUNDLE_FFF["Flush options"]): where the filament flushed at a
 * change goes besides the wipe tower, with the option's key and its menu text.
 */
enum class FlushOption(val key: String, val label: String) {
    INFILL("flush_into_infill", "Flush into objects' infill"),
    OBJECTS("flush_into_objects", "Flush into this object"),
    SUPPORT("flush_into_support", "Flush into objects' support"),
}

/**
 * Whether the object flushes into [option]: the value it sets itself, or the
 * process preset's ([process]) when it sets none, as the desktop menu reads it.
 */
fun PlateObject.flushesInto(option: FlushOption, process: Set<FlushOption>): Boolean =
    settings.values[option.key]?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: (option in process)

/**
 * The flushing volumes of the plate (WipingDialog): how much filament goes into
 * the wipe tower when the print changes from one filament to another. The
 * volumes stand in a matrix per nozzle, one row per filament printed from.
 */
data class FlushVolumes(
    val filaments: Int = 0,
    val nozzles: Int = 1,
    /** nozzles * filaments * filaments, row by row: from the row's filament to the column's. */
    val matrix: List<Double> = emptyList(),
    /** The volumes OrcaSlicer works out from the filament colours, in the same shape. */
    val automatic: List<Double> = emptyList(),
    /** flush_multiplier, one per nozzle. */
    val multipliers: List<Double> = emptyList(),
    /** is_flush_config_modified(): the plate's volumes are not the calculated ones. */
    val modified: Boolean = false,
) {
    /** The volume flushed from filament [from] into filament [to], both 0-based, of [nozzle]. */
    fun volume(nozzle: Int, from: Int, to: Int): Double =
        matrix.getOrElse(nozzle * filaments * filaments + from * filaments + to) { 0.0 }

    /** The same cell of the calculated volumes. */
    fun calculated(nozzle: Int, from: Int, to: Int): Double =
        automatic.getOrElse(nozzle * filaments * filaments + from * filaments + to) { 0.0 }
}

sealed interface FlushVolumesOutcome {
    /** [updated]: for an update of the volumes, the project's matrix or multipliers changed. */
    data class Success(val volumes: FlushVolumes, val updated: Boolean = false) : FlushVolumesOutcome

    data class Failure(val message: String) : FlushVolumesOutcome
}

/**
 * What changed about the filaments of the plate, after which OrcaSlicer works
 * its flushing volumes out again (Sidebar::auto_calc_flushing_volumes).
 */
enum class FlushVolumesChange {
    /** Sidebar::add_custom_filament(): the filament at the index was added. */
    FILAMENT_ADDED,

    /** Sidebar::delete_filament(): the filament at the index was taken out. */
    FILAMENT_REMOVED,

    /** The colour of the filament at the index changed. */
    COLOR_CHANGED,

    /** The filament at the index prints with another preset. */
    FILAMENT_CHANGED,

    /** Another printer was selected. */
    PRINTER_CHANGED,

    /** long_retractions_when_cut of the printer or filament_long_retractions_when_cut of a filament changed. */
    LONG_RETRACTION_CHANGED,
}

/**
 * The facets painted on a volume by OrcaSlicer's painting gizmos, of every
 * kind (PaintKind): ModelVolume's mmu_segmentation_facets, supported_facets,
 * seam_facets and fuzzy_skin_facets. The engine writes them to a file, since
 * the painting of a detailed model runs to megabytes that do not fit a call
 * between the app and the engine's process; the app keeps the file as the
 * engine hands it over and sends it back for painting and for slicing.
 * Paintings the app kept before hold the facets themselves.
 */
@JvmInline
value class PaintedFacets(val value: String = "") {
    val isEmpty: Boolean get() = value.isEmpty()

    /** The file the facets are written to; null for none, or for facets kept inline. */
    val file: ScenePath? get() = value.takeIf { it.startsWith('/') }?.let(::ScenePath)
}

/** PainterGizmoType: what a painting tool paints on a volume, each kind into facets of its own. */
enum class PaintKind {
    /** GLGizmoFdmSupports: where supports are enforced or blocked. */
    SUPPORTS,

    /** GLGizmoSeam: where the seam is enforced or blocked. */
    SEAM,

    /** GLGizmoMmuSegmentation: the filaments of the plate. */
    COLOR,

    /** GLGizmoFuzzySkin: where the walls get fuzzy skin. */
    FUZZY_SKIN,
}

/** The tools the painting gizmos paint with (GLGizmoPainterBase's ToolType and CursorType). */
enum class PaintTool {
    /** A round brush that follows the finger and paints within a sphere around it. */
    BRUSH,

    /** Smart fill: the facets that lie flat enough against the touched one. */
    FILL,

    /** Bucket fill: the whole surface up to its sharp edges. */
    BUCKET,

    /** A round brush that paints what the camera sees under it, through the model. */
    CIRCLE,

    /** The gap fill, which paints no strokes: the small patches of the painting merge into the state around them. */
    GAP_FILL,

    /** Triangles: the one triangle of the painting under the finger. */
    TRIANGLE,

    /** Height range: every facet from the height the finger meets the model at up the cursor's height. */
    HEIGHT_RANGE,
}

/** EnforcerBlockerType: the states a stroke paints; a filament's number paints colour. */
object PaintState {
    /** Takes the paint off again. */
    const val NONE = 0

    /** Supports or the seam are enforced there; fuzzy skin is added. */
    const val ENFORCER = 1

    /** Supports or the seam are blocked there. */
    const val BLOCKER = 2
}

/** One touch of a finger on the model being painted. */
data class PaintStroke(
    /** The finger's ray in world coordinates, as the 3D view casts it. */
    val origin: Vector3,
    val direction: Vector3,
    /** The state to paint ([PaintState]); for colour, the filament, 1-based. */
    val state: Int,
    /** The brush's radius in millimetres. */
    val radius: Double = 2.0,
    val tool: PaintTool = PaintTool.BRUSH,
    /** The height range's height in millimetres (m_cursor_height). */
    val cursorHeight: Double = 0.2,
    /** The angle the fills keep to (m_smart_fill_angle), in degrees. */
    val angle: Double = 30.0,
    /**
     * "On highlighted overhangs only": the facets overhanging more than this
     * angle are the only ones painted, in degrees; 0 paints anywhere.
     */
    val overhangAngle: Double = 0.0,
    /** The first touch of a stroke, before which the tool keeps what its Undo returns to. */
    val startsStroke: Boolean = false,
    /** The tool's "Section view": the finger meets and paints the model on its near side alone; null clips nothing. */
    val clipping: ClippingPlane? = null,
    /** MeshRaycaster::unproject_on_mesh()'s sinking_limit: the model under the plate is passed by, but in the assembly view. */
    val sinkingLimit: Boolean = true,
)

/**
 * ClippingPlane: its unit [normal] and [offset] in world coordinates; a point
 * p with normal · p > offset is clipped (ClippingPlane::is_point_clipped()).
 */
data class ClippingPlane(val normal: Vector3, val offset: Double)

/** The normal and the offset, as the engine takes them; ClippingPlane::ClipsNothing() for none. */
fun ClippingPlane?.values(): DoubleArray =
    this?.let { doubleArrayOf(it.normal.x, it.normal.y, it.normal.z, it.offset) } ?: doubleArrayOf(0.0, 0.0, 1.0, Double.MAX_VALUE)

/** The plane of [values]; null for ClippingPlane::ClipsNothing(). */
fun clippingPlaneOf(values: DoubleArray): ClippingPlane? =
    values.takeIf { it.size == 4 && it[3] != Double.MAX_VALUE }?.let { ClippingPlane(Vector3(it[0], it[1], it[2]), it[3]) }

/**
 * The copy a painting tool paints and where it stands (GLGizmoPainterBase's
 * trafo matrices): the selected [instance] in the 3D view, or in the assembly
 * view at its assemble transformation, spread by the [explosionRatio].
 */
data class PaintPlacement(val instance: Int = 0, val assemblyView: Boolean = false, val explosionRatio: Double = 1.0)

/** What the painting tool shows after a stroke. */
data class PaintedSurface(
    /** Whether the stroke met the model at all. */
    val hit: Boolean = false,
    /** The states the model is painted with ([PaintState]; for colour, the filaments); none while nothing is. */
    val states: List<Int> = emptyList(),
    /** The mesh of the triangles painted with each of them, in the same order. */
    val meshes: List<ScenePath> = emptyList(),
    /** The volume each mesh lies on, in the same order ([PaintedMesh.volume]). */
    val volumes: List<Int> = emptyList(),
    /** The painted facets of the object's own mesh and of each part, reported when the tool closes. */
    val facets: PaintedFacets = PaintedFacets(),
    val partFacets: List<PaintedFacets> = emptyList(),
    /** Whether the tool can undo or redo a stroke (the gizmo's own undo/redo stack). */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface PaintingOutcome {
    data class Success(val surface: PaintedSurface) : PaintingOutcome

    data class Failure(val message: String) : PaintingOutcome
}

sealed interface WipeTowerOutcome {
    data class Success(val tower: WipeTower) : WipeTowerOutcome

    data class Failure(val message: String) : WipeTowerOutcome
}

sealed interface PlateDescriptionOutcome {
    data class Success(val description: PlateDescription) : PlateDescriptionOutcome

    data class Failure(val message: String) : PlateDescriptionOutcome
}

/**
 * A message of Print::validate() (StringObjectException), in the app's
 * language: the object of the plate it is about, by its index among the
 * objects validated (-1 for none), the copy of it (-1 for the object as a
 * whole), and [option], the setting to look at (opt_key; empty for none).
 */
data class PlateValidationMessage(
    val text: String,
    val objectIndex: Int = -1,
    val instanceIndex: Int = -1,
    val option: String = "",
)

/**
 * Plater::priv::update_background_process(): the current plate applied to its
 * print and validated. The [error] blocks slicing; while there is one the
 * plate shows the sequential printing's clearances
 * (GLCanvas3D::SequentialPrintClearance): the copies' [clearance] outlines,
 * their union as triangles ([clearanceFill], on the plate), and the outlines
 * at the height a copy printed before the last may reach, as triangles
 * ([heightLimitFill]). [sequence] numbers every copy of the plate in its print
 * order, object by object, -1 for one not printed, while the plate prints by
 * object or in the object list's order; empty otherwise. [printObjects] are
 * the objects the print holds, which the preview draws as its shells.
 */
data class PlateValidation(
    val error: PlateValidationMessage? = null,
    val warning: PlateValidationMessage? = null,
    val clearance: List<List<Point2>> = emptyList(),
    val clearanceFill: List<Point2> = emptyList(),
    val heightLimitFill: List<Vector3> = emptyList(),
    val sequence: List<Int> = emptyList(),
    val printObjects: List<PrintedObject> = emptyList(),
)

/**
 * An object the print holds (PrintObject), by its index among the objects
 * validated, and the height it stands at above the plate
 * (SlicingParameters::object_print_z_min), which a raft raises.
 */
data class PrintedObject(val objectIndex: Int, val printZMin: Double)

