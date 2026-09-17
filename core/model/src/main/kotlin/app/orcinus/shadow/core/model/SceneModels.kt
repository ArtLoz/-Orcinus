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

    /** OrientJob: the orientation with the least support area, resting on the plate. */
    data object AutoOrient : Manipulation

    /** GLGizmoFlatten: the face with [normal], in object coordinates, turns down and the object rests on the plate. */
    data class LayOnFace(val normal: Vector3) : Manipulation

    /** ArrangeJob: every object of the plate arranged on it with [settings]. */
    data class Arrange(val settings: ArrangeSettings) : Manipulation

    /** ObjectList::toggle_auto_drop() turning auto drop on: ModelObject::ensure_on_bed(). */
    data object EnsureOnBed : Manipulation
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

/** A 4 x 4 affine transformation, stored column by column as OrcaSlicer's Transform3d. */
data class Transform3(val columns: List<Double>) {
    init {
        require(columns.size == 16) { "A transformation has 16 elements" }
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

sealed interface PlateDescriptionOutcome {
    data class Success(val description: PlateDescription) : PlateDescriptionOutcome

    data class Failure(val message: String) : PlateDescriptionOutcome
}
