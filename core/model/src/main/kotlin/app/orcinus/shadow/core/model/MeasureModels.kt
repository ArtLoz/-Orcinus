package app.orcinus.shadow.core.model

import kotlin.math.abs

/** Measure::SurfaceFeatureType of a feature the measuring tool finds. */
enum class MeasureFeatureType { POINT, EDGE, CIRCLE, PLANE }

/**
 * The triangles of a plane the measuring tool shows, in world coordinates and
 * lifted off the mesh as init_plane_data() lifts them, nine numbers each. The
 * engine sends them once for a plane; a plane is the same by its feature, not
 * by these.
 */
class MeasurePlaneMesh(val vertices: FloatArray)

/**
 * Measure::SurfaceFeature in world coordinates: a point at [pt1]; an edge
 * from [pt1] to [pt2], [pt3] the centre of the polygon it is a side of; a
 * circle about [pt1] with the normal [pt2] and the radius [value]; a plane
 * through [pt2] with the normal [pt1], [value] its index among its volume's
 * planes, with its triangles ([planeMesh]) when the engine sent them.
 */
data class MeasureFeature(
    val type: MeasureFeatureType,
    val pt1: Vector3,
    val pt2: Vector3,
    val pt3: Vector3? = null,
    val value: Double = 0.0,
    val planeMesh: MeasurePlaneMesh? = null,
) {
    /** SurfaceFeature's operator==, which compares the geometry only. */
    fun sameAs(other: MeasureFeature?): Boolean {
        if (other == null || type != other.type) return false
        return when (type) {
            MeasureFeatureType.POINT -> pt1.isApprox(other.pt1)
            MeasureFeatureType.EDGE ->
                (pt1.isApprox(other.pt1) && pt2.isApprox(other.pt2)) || (pt1.isApprox(other.pt2) && pt2.isApprox(other.pt1))
            MeasureFeatureType.PLANE, MeasureFeatureType.CIRCLE ->
                pt1.isApprox(other.pt1) && pt2.isApprox(other.pt2) && abs(value - other.value) < MEASURE_EPSILON
        }
    }

    /** GLGizmoMeasure::is_feature_with_center() */
    val hasCenter: Boolean get() = type == MeasureFeatureType.CIRCLE || (type == MeasureFeatureType.EDGE && pt3 != null)

    /** GLGizmoMeasure::get_feature_offset(): a feature's centre, or a point's position. */
    val offset: Vector3
        get() = when (type) {
            MeasureFeatureType.CIRCLE, MeasureFeatureType.POINT -> pt1
            MeasureFeatureType.EDGE -> pt3 ?: Vector3((pt1.x + pt2.x) / 2, (pt1.y + pt2.y) / 2, (pt1.z + pt2.z) / 2)
            MeasureFeatureType.PLANE -> Vector3(0.0, 0.0, 0.0)
        }
}

/**
 * A selection of the measuring tool (GLGizmoMeasure::SelectedFeatures::Item):
 * the [feature] selected, the one it is the centre or a point of ([source]),
 * whether it is a centre, and the object and volume it is on, by their
 * indexes on the plate.
 */
data class MeasureSelection(
    val isCenter: Boolean,
    val source: MeasureFeature,
    val feature: MeasureFeature,
    val objectIndex: Int,
    val volumeIndex: Int,
)

/** Measure::DistAndPoints: a distance and the points it is between. */
data class MeasureDistance(val distance: Double, val from: Vector3, val to: Vector3)

/**
 * Measure::AngleAndEdges: the angle between two edges or planes, the point
 * its arc is drawn about, the two edges it is drawn along, the arc's radius,
 * and whether the edges lie in one plane.
 */
data class MeasureAngle(
    val angle: Double,
    val center: Vector3,
    val edge1: Pair<Vector3, Vector3>,
    val edge2: Pair<Vector3, Vector3>,
    val radius: Double,
    val coplanar: Boolean,
)

/**
 * Measure::MeasurementResult: what the two selections measure, or what a
 * circle alone measures from its centre.
 */
data class MeasureResult(
    val angle: MeasureAngle? = null,
    val distanceInfinite: MeasureDistance? = null,
    val distanceStrict: MeasureDistance? = null,
    val distanceXyz: Vector3? = null,
)

/** Measure::AssemblyAction: what the assembly tool can do with the two selections. */
data class MeasureAssembly(
    val canSetToParallel: Boolean = false,
    val canSetToCenterCoincidence: Boolean = false,
    val canSetFeature1ReverseRotation: Boolean = false,
    val canSetFeature2ReverseRotation: Boolean = false,
    val canAroundCenterOfFaces: Boolean = false,
    val hasParallelDistance: Boolean = false,
    val parallelDistance: Double = 0.0,
)

/**
 * The measuring tool after a selection: the two selections, what they
 * measure, what can assemble them and whether their distance along the axes
 * can be set (Measure::can_set_xyz_distance()), how many volumes they are on
 * and whether one object has both (is_two_volume_in_same_model_object()),
 * whether the second selection just became the first (m_show_reset_first_tip),
 * and the feature under the finger.
 */
data class Measurement(
    val first: MeasureSelection? = null,
    val second: MeasureSelection? = null,
    val result: MeasureResult = MeasureResult(),
    val assembly: MeasureAssembly = MeasureAssembly(),
    val canSetXyzDistance: Boolean = false,
    val hitVolumes: Int = 0,
    val sameObject: Boolean = false,
    val showResetFirstTip: Boolean = false,
    val hovered: MeasureFeature? = null,
)

/**
 * What is under the finger while it explores: the feature, without its
 * triangles when it is the plane told last ([unchanged]); in point selection
 * the point on it; and the selection whose sphere the finger is on (1 or 2, 0
 * for none), which hides the feature.
 */
data class MeasureHover(
    val feature: MeasureFeature? = null,
    val unchanged: Boolean = false,
    val point: Vector3? = null,
    val sphere: Int = 0,
)

/**
 * A ray of the finger into the measured volumes (world coordinates), whether
 * it selects points (the desktop app's Shift), whether only planes are
 * features (the face-to-face assembly), and how far from a centre or point the
 * ray takes its sphere, in millimetres.
 */
data class MeasureRay(
    val origin: Vector3,
    val direction: Vector3,
    val pointSelection: Boolean = false,
    val onlySelectPlane: Boolean = false,
    val sphereRadius: Double = 1.0,
)

/** reset_feature1(), reset_feature2() and reset_all_feature(). */
enum class MeasureReset { ALL, FIRST, SECOND }

/**
 * A volume the measuring tool measures (a GLVolume of the selection): the
 * copy [instanceIndex] of the object at [objectIndex], and its volume at
 * [volumeIndex], or all of the copy's volumes for null.
 */
data class MeasuredVolume(val objectIndex: Int, val instanceIndex: Int, val volumeIndex: Int? = null)

sealed interface MeasureOutcome {
    data class Success(val measurement: Measurement) : MeasureOutcome

    data class Failure(val message: String) : MeasureOutcome
}

sealed interface MeasureHoverOutcome {
    data class Success(val hover: MeasureHover) : MeasureHoverOutcome

    data class Failure(val message: String) : MeasureHoverOutcome
}

/** libslic3r's EPSILON, which SurfaceFeature compares a circle's radius and a plane's index by. */
private const val MEASURE_EPSILON = 1e-4

/** Eigen's isApprox() with the precision of a double. */
private fun Vector3.isApprox(other: Vector3): Boolean {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    val difference = dx * dx + dy * dy + dz * dz
    val smaller = minOf(x * x + y * y + z * z, other.x * other.x + other.y * other.y + other.z * other.z)
    return difference <= APPROX_PRECISION * APPROX_PRECISION * smaller
}

/** NumTraits<double>::dummy_precision() */
private const val APPROX_PRECISION = 1e-12
