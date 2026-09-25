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

/** How OrcaSlicer's jobs place several objects of the plate at once. */
sealed interface PlateManipulation {
    /**
     * OrientJob from the toolbar: the objects with the [selected] mesh files, or
     * every object when none is selected, turn to the orientation with the least
     * support area and rest on the plate.
     */
    data class AutoOrient(val selected: Set<ScenePath> = emptySet()) : PlateManipulation

    /** ArrangeJob from the arrange options (prepare_all): every object arranged on the plate with [settings]. */
    data class Arrange(val settings: ArrangeSettings) : PlateManipulation

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
    data class ArrangePlate(val settings: ArrangeSettings) : PlateManipulation

    /**
     * FillBedJob with instances ("Fill bed with instances"): copies of the
     * object with the [mesh] file, modelled on its copy [instance] (null for the
     * whole object), added while the free area of the plate holds more; then
     * the plate arranged as [ArrangePlate] arranges it.
     */
    data class FillBed(val mesh: ScenePath, val instance: Int?, val settings: ArrangeSettings) : PlateManipulation
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
)

data class PlateDescription(
    val geometry: PlateGeometry,
    /** Colour of the first filament, which OrcaSlicer paints objects with. */
    val filamentColor: ColorRgba,
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
 * The facets of a volume painted with the filaments of the plate, as
 * OrcaSlicer's colour painting gizmo leaves them
 * (ModelVolume::mmu_segmentation_facets). The app keeps them as the engine
 * hands them over and sends them back for painting and for slicing.
 */
@JvmInline
value class PaintedFacets(val value: String = "") {
    val isEmpty: Boolean get() = value.isEmpty()
}

/** The tools the colour painting gizmo paints with (GLGizmoPainterBase::ToolType). */
enum class PaintTool {
    /** A round brush that follows the finger. */
    BRUSH,

    /** Smart fill: the facets that lie flat enough against the touched one. */
    FILL,

    /** Bucket fill: the whole surface up to its sharp edges. */
    BUCKET,
}

/** One touch of a finger on the model being painted. */
data class PaintStroke(
    /** The finger's ray in world coordinates, as the 3D view casts it. */
    val origin: Vector3,
    val direction: Vector3,
    /** The filament to paint with, 1-based; 0 takes the paint off again. */
    val filament: Int,
    /** The brush's radius in millimetres. */
    val radius: Double = 2.0,
    val tool: PaintTool = PaintTool.BRUSH,
    /** The angle the fills keep to (m_smart_fill_angle), in degrees. */
    val angle: Double = 30.0,
    /** The first touch of a stroke, before which the tool keeps what its Undo returns to. */
    val startsStroke: Boolean = false,
)

/** What the painting tool shows after a stroke. */
data class PaintedSurface(
    /** Whether the stroke met the model at all. */
    val hit: Boolean = false,
    /** The filaments the model is painted with, 1-based. */
    val filaments: List<Int> = emptyList(),
    /** The mesh of the triangles painted with each of them, in the same order. */
    val meshes: List<ScenePath> = emptyList(),
    /** The painted facets, reported when the tool closes. */
    val facets: PaintedFacets = PaintedFacets(),
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
