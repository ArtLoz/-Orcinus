package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Quaternion
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The text the text tool is open on, which a finger drags over its object's
 * surface (SurfaceDrag.cpp): the meshes the scene draws it with ([keys], one
 * per copy for an object's own mesh), its transformation in the object
 * ([placement]), the transformation the scene puts its mesh in after the
 * copy's ([sceneFrame]: the placement for a part, none for the object's own
 * mesh, which the engine writes in place), the fix of a text from a 3MF file
 * (EmbossShape::fix_3mf_tr), and whether it is its object's only part, which
 * moves as the object.
 */
data class TextDragView(
    val keys: Set<String>,
    val placement: Transform3,
    val sceneFrame: Transform3,
    val fix: Transform3?,
    val onlyPart: Boolean,
)

/** SurfaceDrag.hpp's UP_LIMIT: a text's up keeps between the sides and the top of a model. */
internal const val UP_LIMIT = 0.9

/**
 * SurfaceDrag: the text held at the copy [index], the screen offset of its
 * origin from the finger ([offsetX], [offsetY]), its transformation to the
 * world ([world], without the fix) and the copy's inverse ([instanceInv]),
 * its angle about its own Z when the drag began, and the fix.
 */
internal class TextDragStart(
    val index: Int,
    val offsetX: Double,
    val offsetY: Double,
    val world: Affine3,
    val instanceInv: Affine3,
    val startAngle: Double?,
    val fix: Affine3?,
)

/** Eigen's Quaternion::FromTwoVectors(): the shortest rotation that turns [a] into [b]. */
internal fun quaternionFromTwoVectors(a: Vec3, b: Vec3): Quaternion {
    val v0 = a.normalized()
    val v1 = b.normalized()
    var c = v1.dot(v0)
    // if dot == -1, vectors are nearly opposites
    if (c < -1.0 + DUMMY_PRECISION) {
        c = max(c, -1.0)
        // Any axis across both, which Eigen takes from an SVD.
        val axis = across(v0)
        val w2 = (1.0 + c) * 0.5
        val s = sqrt(1.0 - w2)
        return Quaternion(sqrt(w2), axis.x * s, axis.y * s, axis.z * s)
    }
    val axis = v0.cross(v1)
    val s = sqrt((1.0 + c) * 2.0)
    val invs = 1.0 / s
    return Quaternion(s * 0.5, axis.x * invs, axis.y * invs, axis.z * invs)
}

/** A unit vector perpendicular to [v]. */
private fun across(v: Vec3): Vec3 {
    val other = when {
        abs(v.x) <= abs(v.y) && abs(v.x) <= abs(v.z) -> Vec3.UNIT_X
        abs(v.y) <= abs(v.z) -> Vec3.UNIT_Y
        else -> Vec3.UNIT_Z
    }
    return v.cross(other).normalized()
}

/** Emboss::suggest_up(): the up of a text on a surface of [normal], the Y axis on a top within [upLimit]. */
internal fun suggestUp(normal: Vec3, upLimit: Double): Vec3 {
    // wanted up direction of result
    val wantedUpSide = if (abs(normal.z) > upLimit) Vec3.UNIT_Y else Vec3.UNIT_Z
    // create perpendicular unit vector to surface triangle normal vector
    // lay on surface of triangle and define up vector for text
    return normal.cross(wantedUpSide).cross(normal).normalized()
}

/** Emboss::calc_up(): how far the text of [tr] is turned from the suggested up; null for no turn. */
internal fun calcUp(tr: Affine3, upLimit: Double): Double? {
    // z base of transformation ( tr * UnitZ ); scaled matrix has base with different size
    val normal = column(tr, 2).normalized()
    val suggested = suggestUp(normal, upLimit)
    val up = column(tr, 1).normalized()
    // The determinant of the rows up, suggested and normal.
    val det = up.dot(suggested.cross(normal))
    val dot = suggested.dot(up)
    val res = -atan2(det, dot)
    return if (abs(res) < APPROX_EPSILON) null else res
}

/**
 * get_volume_transformation() of SurfaceDrag.cpp: the text's transformation
 * in its copy, [world] turned so that its Z axis looks along [worldDir] and
 * moved to [worldPosition], its up kept within [upLimit], [fix] applied, and
 * [currentAngle] turning it about Z again.
 */
internal fun volumeTransformation(
    world: Affine3,
    worldDir: Vec3,
    worldPosition: Vec3,
    fix: Affine3?,
    instanceInv: Affine3,
    currentAngle: Double?,
    upLimit: Double?,
): Affine3 {
    // Reset skew of the text Z axis:
    // Project the old Z axis into a new Z axis, which is perpendicular to the old XY plane.
    val oldZ = column(world, 2)
    val newZ = column(world, 0).cross(column(world, 1))
    val textZWorld = newZ * (oldZ.dot(newZ) / newZ.dot(newZ))
    val zRotation = quaternionFromTwoVectors(textZWorld, worldDir)
    var columns = List(3) { zRotation.rotate(column(world, it)) }
    // Fix direction of up vector to zero initial rotation
    if (upLimit != null) {
        val zWorld = columns[2].normalized()
        val wantedUp = suggestUp(zWorld, upLimit)
        val yRotation = quaternionFromTwoVectors(columns[1], wantedUp)
        columns = columns.map(yRotation::rotate)
    }
    // Edit position from right
    val position = instanceInv.transformPoint(worldPosition)
    val linear = columns.map(instanceInv::transformVector)
    var volumeNew = Affine3.fromRows(
        doubleArrayOf(linear[0].x, linear[1].x, linear[2].x, position.x),
        doubleArrayOf(linear[0].y, linear[1].y, linear[2].y, position.y),
        doubleArrayOf(linear[0].z, linear[1].z, linear[2].z, position.z),
    )
    // Check that transformation matrix is valid transformation
    if (volumeNew[0, 0].isNaN()) return Affine3()
    // fix baked transformation from .3mf store process
    if (fix != null) volumeNew = volumeNew * fix
    // apply move in Z direction and rotation by up vector
    if (currentAngle != null) volumeNew = volumeNew * Affine3.fromPositionOrientation(Vec3.ZERO, Quaternion.angleAxis(currentAngle, Vec3.UNIT_Z))
    return volumeNew
}

private fun column(tr: Affine3, index: Int) = Vec3(tr[0, index], tr[1, index], tr[2, index])

/** Eigen's NumTraits<double>::dummy_precision(). */
private const val DUMMY_PRECISION = 1e-12

/** libslic3r's EPSILON, which is_approx() compares by. */
private const val APPROX_EPSILON = 1e-4
