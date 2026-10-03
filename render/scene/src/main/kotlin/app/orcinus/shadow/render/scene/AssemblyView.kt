package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OrcaSlicer's assembly view (AssembleView, a GLCanvas3D of the
 * CanvasAssembleView type) while the plate view shows it: the model parts of
 * every copy where its assemble transformation puts them, spread by the
 * [explosionRatio] (GLVolume::explosion_ratio), with no plate, wipe tower,
 * labels or clearance, and a camera of its own. [hidden] are the copies its
 * menu's "Hide" made faint (GLCanvas3D::set_selected_visible()).
 *
 * "Section View" (ModelObjectsClipper) clips the volumes at [sectionPosition],
 * 0 for none to 1, across their box along the plane's normal, which the
 * camera gave and [sectionResets] takes from it anew ("Reset direction");
 * [section] is the engine's mesh of the cut, drawn dark grey.
 */
data class AssemblyView(
    val explosionRatio: Double = 1.0,
    val hidden: Set<PlateInstanceId> = emptySet(),
    val sectionPosition: Double = 0.0,
    val sectionResets: Int = 0,
    val section: ScenePath? = null,
)

/**
 * Where the volumes of a copy stand in the assembly view: GLVolume::world_matrix()
 * with the copy's assemble transformation for its instance transformation
 * (ModelInstance::get_assemble_transformation(), the identity while the copy
 * has none). The copy's offset to the assembly and the volume's own offset in
 * the object, times the explosion ratio less one, move each volume after its
 * transformation; [ownVolume] is the transformation of the object's own mesh
 * (its first ModelVolume).
 */
internal class AssemblyPlacement(instance: PlateInstance, ownVolume: Transform3) {
    private val assemble = instance.assemble?.let(::affine) ?: Affine3()
    private val fromPlate = assemble * affine(instance.inspection.placement).inverse()
    private val offsetToAssembly = instance.offsetToAssembly?.let { Vec3(it.x, it.y, it.z) } ?: Vec3.ZERO
    private val own = affine(ownVolume)

    /**
     * The volume the 3D view drew as [sceneObject], whose transformation in the
     * object is [volume]: the object's own mesh, and the paint over it, go by
     * the object's own volume.
     */
    fun place(sceneObject: SceneObject, volume: Transform3? = null, hidden: Boolean = false, faint: Boolean = false): SceneObject {
        val transformation = volume?.let(::affine) ?: own
        val explosion = (assemble * transformation).transformVector(offsetToAssembly + transformation.translation())
        return sceneObject.inAssembly(fromPlate * sceneObject.world, explosion, hidden, faint)
    }

    /** Where the point of the copy at [point] in the 3D view stands with the object's own mesh in the assembly view. */
    fun placeOwn(point: Vec3, explosionRatio: Double): Vec3 {
        val explosion = (assemble * own).transformVector(offsetToAssembly + own.translation())
        return fromPlate.transformPoint(point) + explosion * (explosionRatio - 1.0)
    }

    private companion object {
        fun affine(transform: Transform3) = Affine3(transform.columns.toDoubleArray())
    }
}

/**
 * What GizmoObjectManipulation's window shows of a copy in the assembly view,
 * and does to it: its assemble transformation's values, and the place it
 * turns about.
 */
object AssemblyTransforms {
    /**
     * Transformation::get_rotation_by_quaternion() in degrees, as
     * update_settings_value() shows it, with delete_negative_sign()'s zero
     * for less than a thousandth: the linear part, scale and all, as an
     * Eigen::Quaterniond normalized, back to a matrix whose eulerAngles(2, 1, 0)
     * come swapped.
     */
    fun rotationDegrees(transform: Transform3): Vector3 {
        val c = transform.columns
        val m = arrayOf(doubleArrayOf(c[0], c[4], c[8]), doubleArrayOf(c[1], c[5], c[9]), doubleArrayOf(c[2], c[6], c[10]))
        val r = rotationMatrix(quaternion(m))
        // MatrixBase::eulerAngles(2, 1, 0): odd, i = 2, j = 1, k = 0, the Tait-Bryan angles.
        var z = atan2(r[1][0], r[0][0])
        val c2 = hypot(r[2][2], r[2][1])
        val y: Double
        if (z < 0.0) {
            z += Math.PI
            y = atan2(-r[2][0], -c2)
        } else {
            y = atan2(-r[2][0], c2)
        }
        val s1 = sin(z)
        val c1 = cos(z)
        val x = atan2(s1 * r[0][2] - c1 * r[1][2], c1 * r[1][1] - s1 * r[0][1])
        fun shown(radians: Double) = Math.toDegrees(radians).let { if (abs(it) < 0.001) 0.0 else it }
        return Vector3(shown(x), shown(y), shown(z))
    }

    /**
     * Transformation::reset_rotation(): the offset, the scale without the
     * rotation (V S V^T of the linear part's singular values, the symmetric
     * square root of its square) and the mirroring.
     */
    fun withoutRotation(transform: Transform3): Transform3 {
        val c = transform.columns
        val m0 = arrayOf(doubleArrayOf(c[0], c[4], c[8]), doubleArrayOf(c[1], c[5], c[9]), doubleArrayOf(c[2], c[6], c[10]))
        val determinant = m0[0][0] * (m0[1][1] * m0[2][2] - m0[1][2] * m0[2][1]) -
            m0[0][1] * (m0[1][0] * m0[2][2] - m0[1][2] * m0[2][0]) +
            m0[0][2] * (m0[1][0] * m0[2][1] - m0[1][1] * m0[2][0])
        // TransformationSVD: a mirroring is taken off along x first, and put back after.
        val mirror = determinant < 0.0
        val m = Array(3) { row -> DoubleArray(3) { column -> if (mirror && column == 0) -m0[row][column] else m0[row][column] } }
        val square = Array(3) { row -> DoubleArray(3) { column -> (0 until 3).sumOf { m[it][row] * m[it][column] } } }
        val root = symmetricSquareRoot(square)
        val columns = transform.columns.toMutableList()
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                columns[column * 4 + row] = if (mirror && column == 0) -root[row][column] else root[row][column]
            }
        }
        return Transform3(columns)
    }

    /**
     * Selection::m_cache.rotation_pivot in the assembly view, which the
     * window turns the copy about: the copy's bounding sphere centre
     * ([sphereCenter], as the 3D view places it), where the assembly and its
     * explosion ratio put it with the object's own mesh, as the view's
     * rotation gizmo turns it.
     */
    fun pivot(instance: PlateInstance, ownVolume: Transform3, sphereCenter: Vector3, explosionRatio: Double): Vector3 {
        val point = AssemblyPlacement(instance, ownVolume).placeOwn(Vec3(sphereCenter.x, sphereCenter.y, sphereCenter.z), explosionRatio)
        return Vector3(point.x, point.y, point.z)
    }

    /** Eigen::Quaterniond(const Matrix3d&) (Shoemake's algorithm), normalized: w, x, y, z. */
    private fun quaternion(m: Array<DoubleArray>): DoubleArray {
        val q = DoubleArray(4)
        var t = m[0][0] + m[1][1] + m[2][2]
        if (t > 0.0) {
            t = sqrt(t + 1.0)
            q[0] = 0.5 * t
            t = 0.5 / t
            q[1] = (m[2][1] - m[1][2]) * t
            q[2] = (m[0][2] - m[2][0]) * t
            q[3] = (m[1][0] - m[0][1]) * t
        } else {
            var i = 0
            if (m[1][1] > m[0][0]) i = 1
            if (m[2][2] > m[i][i]) i = 2
            val j = (i + 1) % 3
            val k = (j + 1) % 3
            t = sqrt(m[i][i] - m[j][j] - m[k][k] + 1.0)
            q[1 + i] = 0.5 * t
            t = 0.5 / t
            q[0] = (m[k][j] - m[j][k]) * t
            q[1 + j] = (m[j][i] + m[i][j]) * t
            q[1 + k] = (m[k][i] + m[i][k]) * t
        }
        val norm = sqrt(q.sumOf { it * it })
        return DoubleArray(4) { q[it] / norm }
    }

    /** QuaternionBase::toRotationMatrix() */
    private fun rotationMatrix(q: DoubleArray): Array<DoubleArray> {
        val (w, x, y, z) = q
        val tx = 2.0 * x
        val ty = 2.0 * y
        val tz = 2.0 * z
        val twx = tx * w
        val twy = ty * w
        val twz = tz * w
        val txx = tx * x
        val txy = ty * x
        val txz = tz * x
        val tyy = ty * y
        val tyz = tz * y
        val tzz = tz * z
        return arrayOf(
            doubleArrayOf(1.0 - (tyy + tzz), txy - twz, txz + twy),
            doubleArrayOf(txy + twz, 1.0 - (txx + tzz), tyz - twx),
            doubleArrayOf(txz - twy, tyz + twx, 1.0 - (txx + tyy)),
        )
    }

    /** The symmetric square root of a symmetric positive semidefinite matrix, by Jacobi's eigenvalue rotations. */
    private fun symmetricSquareRoot(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val a = Array(3) { matrix[it].copyOf() }
        val v = Array(3) { row -> DoubleArray(3) { column -> if (row == column) 1.0 else 0.0 } }
        repeat(JACOBI_SWEEPS) {
            val off = a[0][1] * a[0][1] + a[0][2] * a[0][2] + a[1][2] * a[1][2]
            if (off < 1e-30) return@repeat
            for ((p, q) in listOf(0 to 1, 0 to 2, 1 to 2)) {
                if (abs(a[p][q]) < 1e-300) continue
                val theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q])
                val t = (if (theta >= 0.0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1.0))
                val cosine = 1.0 / sqrt(t * t + 1.0)
                val sine = t * cosine
                for (k in 0 until 3) {
                    val akp = a[k][p]
                    val akq = a[k][q]
                    a[k][p] = cosine * akp - sine * akq
                    a[k][q] = sine * akp + cosine * akq
                }
                for (k in 0 until 3) {
                    val apk = a[p][k]
                    val aqk = a[q][k]
                    a[p][k] = cosine * apk - sine * aqk
                    a[q][k] = sine * apk + cosine * aqk
                }
                for (k in 0 until 3) {
                    val vkp = v[k][p]
                    val vkq = v[k][q]
                    v[k][p] = cosine * vkp - sine * vkq
                    v[k][q] = sine * vkp + cosine * vkq
                }
            }
        }
        val roots = DoubleArray(3) { sqrt(a[it][it].coerceAtLeast(0.0)) }
        return Array(3) { row -> DoubleArray(3) { column -> (0 until 3).sumOf { v[row][it] * roots[it] * v[column][it] } } }
    }

    private const val JACOBI_SWEEPS = 30
}
