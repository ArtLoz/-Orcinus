package app.orcinus.shadow.render.scene.math

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Double-precision geometry with Eigen's conventions, so OrcaSlicer's camera
// code ports line by line: matrices are column-major, quaternions are Hamilton
// quaternions, and transformations compose by post-multiplication.

internal data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Double) = Vec3(x * scale, y * scale, z * scale)

    operator fun div(scale: Double) = Vec3(x / scale, y / scale, z / scale)

    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    fun norm() = sqrt(dot(this))

    fun normalized(): Vec3 {
        val length = norm()
        return if (length > 0.0) this / length else this
    }

    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    /** Eigen's isApprox(Zero()), which only a zero vector satisfies. */
    fun isZero() = x == 0.0 && y == 0.0 && z == 0.0

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UNIT_X = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y = Vec3(0.0, 1.0, 0.0)
        val UNIT_Z = Vec3(0.0, 0.0, 1.0)
    }
}

internal data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(other: Quaternion) = Quaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y + y * other.w + z * other.x - x * other.z,
        w * other.z + z * other.w + x * other.y - y * other.x,
    )

    fun normalized(): Quaternion {
        val length = sqrt(w * w + x * x + y * y + z * z)
        return Quaternion(w / length, x / length, y / length, z / length)
    }

    /** The inverse of a unit quaternion. */
    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun rotate(vector: Vec3): Vec3 {
        val axis = Vec3(x, y, z)
        val t = axis.cross(vector) * 2.0
        return vector + t * w + axis.cross(t)
    }

    /** Rotation matrix, rows then columns. */
    fun toRotationRows(): Array<Vec3> {
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z
        return arrayOf(
            Vec3(1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy)),
            Vec3(2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx)),
            Vec3(2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)),
        )
    }

    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        /** Eigen::AngleAxisd converted to a quaternion; [axis] must be a unit vector. */
        fun angleAxis(angle: Double, axis: Vec3): Quaternion {
            val half = 0.5 * angle
            val s = sin(half)
            return Quaternion(cos(half), axis.x * s, axis.y * s, axis.z * s)
        }

        /** Eigen's conversion from a rotation matrix given by its rows. */
        fun fromRotationRows(rows: Array<Vec3>): Quaternion {
            val m = arrayOf(
                doubleArrayOf(rows[0].x, rows[0].y, rows[0].z),
                doubleArrayOf(rows[1].x, rows[1].y, rows[1].z),
                doubleArrayOf(rows[2].x, rows[2].y, rows[2].z),
            )
            val trace = m[0][0] + m[1][1] + m[2][2]
            if (trace > 0.0) {
                var t = sqrt(trace + 1.0)
                val w = 0.5 * t
                t = 0.5 / t
                return Quaternion(w, (m[2][1] - m[1][2]) * t, (m[0][2] - m[2][0]) * t, (m[1][0] - m[0][1]) * t)
            }
            var i = 0
            if (m[1][1] > m[0][0]) i = 1
            if (m[2][2] > m[i][i]) i = 2
            val j = (i + 1) % 3
            val k = (j + 1) % 3
            var t = sqrt(m[i][i] - m[j][j] - m[k][k] + 1.0)
            val q = DoubleArray(3)
            q[i] = 0.5 * t
            t = 0.5 / t
            val w = (m[k][j] - m[j][k]) * t
            q[j] = (m[j][i] + m[i][j]) * t
            q[k] = (m[k][i] + m[i][k]) * t
            return Quaternion(w, q[0], q[1], q[2])
        }
    }
}

/** A 4 x 4 affine transformation, column-major as Eigen::Transform3d. */
internal class Affine3(private val m: DoubleArray = identityElements()) {
    init {
        require(m.size == 16)
    }

    operator fun get(row: Int, column: Int) = m[column * 4 + row]

    fun translation() = Vec3(m[12], m[13], m[14])

    /** A row of the linear part. */
    fun linearRow(row: Int) = Vec3(this[row, 0], this[row, 1], this[row, 2])

    /** GLVolume::is_left_handed(): a mirroring transformation turns the faces inside out. */
    val isLeftHanded: Boolean
        get() = this[0, 0] * (this[1, 1] * this[2, 2] - this[1, 2] * this[2, 1]) -
            this[0, 1] * (this[1, 0] * this[2, 2] - this[1, 2] * this[2, 0]) +
            this[0, 2] * (this[1, 0] * this[2, 1] - this[1, 1] * this[2, 0]) < 0.0

    fun transformPoint(point: Vec3) = Vec3(
        this[0, 0] * point.x + this[0, 1] * point.y + this[0, 2] * point.z + m[12],
        this[1, 0] * point.x + this[1, 1] * point.y + this[1, 2] * point.z + m[13],
        this[2, 0] * point.x + this[2, 1] * point.y + this[2, 2] * point.z + m[14],
    )

    fun transformVector(vector: Vec3) = Vec3(
        this[0, 0] * vector.x + this[0, 1] * vector.y + this[0, 2] * vector.z,
        this[1, 0] * vector.x + this[1, 1] * vector.y + this[1, 2] * vector.z,
        this[2, 0] * vector.x + this[2, 1] * vector.y + this[2, 2] * vector.z,
    )

    /** Eigen's Transform::translate(): the translation is applied first, in local coordinates. */
    fun translated(offset: Vec3): Affine3 {
        val shift = transformVector(offset)
        return Affine3(m.copyOf().also {
            it[12] += shift.x
            it[13] += shift.y
            it[14] += shift.z
        })
    }

    operator fun times(other: Affine3): Affine3 {
        val result = DoubleArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0.0
                for (k in 0 until 4) sum += this[row, k] * other[k, column]
                result[column * 4 + row] = sum
            }
        }
        return Affine3(result)
    }

    /** Inverse of a rigid or scaled affine transformation. */
    fun inverse(): Affine3 {
        val a = this[0, 0]
        val b = this[0, 1]
        val c = this[0, 2]
        val d = this[1, 0]
        val e = this[1, 1]
        val f = this[1, 2]
        val g = this[2, 0]
        val h = this[2, 1]
        val i = this[2, 2]
        val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val inverse = arrayOf(
            doubleArrayOf((e * i - f * h) / determinant, (c * h - b * i) / determinant, (b * f - c * e) / determinant),
            doubleArrayOf((f * g - d * i) / determinant, (a * i - c * g) / determinant, (c * d - a * f) / determinant),
            doubleArrayOf((d * h - e * g) / determinant, (b * g - a * h) / determinant, (a * e - b * d) / determinant),
        )
        val t = translation()
        val result = identityElements()
        for (row in 0 until 3) {
            for (column in 0 until 3) result[column * 4 + row] = inverse[row][column]
            result[12 + row] = -(inverse[row][0] * t.x + inverse[row][1] * t.y + inverse[row][2] * t.z)
        }
        return Affine3(result)
    }

    fun withTranslation(translation: Vec3) = Affine3(m.copyOf().also {
        it[12] = translation.x
        it[13] = translation.y
        it[14] = translation.z
    })

    fun toFloatArray() = FloatArray(16) { m[it].toFloat() }

    /** The upper-left 3 x 3 block, column-major. */
    fun linearToFloatArray() = FloatArray(9) { index -> this[index % 3, index / 3].toFloat() }

    fun elements() = m.copyOf()

    companion object {
        fun identityElements() = DoubleArray(16) { if (it % 5 == 0) 1.0 else 0.0 }

        /** Eigen's Transform::fromPositionOrientationScale() with unit scale. */
        fun fromPositionOrientation(position: Vec3, orientation: Quaternion): Affine3 {
            val rows = orientation.toRotationRows()
            val result = identityElements()
            for (row in 0 until 3) {
                result[0 * 4 + row] = rows[row].x
                result[1 * 4 + row] = rows[row].y
                result[2 * 4 + row] = rows[row].z
            }
            result[12] = position.x
            result[13] = position.y
            result[14] = position.z
            return Affine3(result)
        }

        /**
         * Geometry::assemble_transform(): translation, then rotation about Z,
         * Y, and X by the angles in [rotation], then [scale].
         */
        fun assemble(translation: Vec3, rotation: Vec3, scale: Vec3): Affine3 {
            val orientation = Quaternion.angleAxis(rotation.z, Vec3.UNIT_Z) *
                Quaternion.angleAxis(rotation.y, Vec3.UNIT_Y) *
                Quaternion.angleAxis(rotation.x, Vec3.UNIT_X)
            val rows = orientation.toRotationRows()
            return fromRows(
                doubleArrayOf(rows[0].x * scale.x, rows[0].y * scale.y, rows[0].z * scale.z, translation.x),
                doubleArrayOf(rows[1].x * scale.x, rows[1].y * scale.y, rows[1].z * scale.z, translation.y),
                doubleArrayOf(rows[2].x * scale.x, rows[2].y * scale.y, rows[2].z * scale.z, translation.z),
            )
        }

        fun fromRows(row0: DoubleArray, row1: DoubleArray, row2: DoubleArray): Affine3 {
            val result = identityElements()
            for ((row, values) in listOf(row0, row1, row2).withIndex()) {
                for (column in 0 until 4) result[column * 4 + row] = values[column]
            }
            return Affine3(result)
        }
    }
}

internal data class Box3(val min: Vec3, val max: Vec3) {
    fun center() = (min + max) * 0.5

    fun size() = max - min

    fun maxSize() = size().let { max(it.x, max(it.y, it.z)) }

    fun corners() = listOf(
        min,
        Vec3(max.x, min.y, min.z),
        Vec3(max.x, max.y, min.z),
        Vec3(min.x, max.y, min.z),
        Vec3(min.x, min.y, max.z),
        Vec3(max.x, min.y, max.z),
        max,
        Vec3(min.x, max.y, max.z),
    )

    fun merge(other: Box3) = Box3(
        Vec3(min(min.x, other.min.x), min(min.y, other.min.y), min(min.z, other.min.z)),
        Vec3(max(max.x, other.max.x), max(max.y, other.max.y), max(max.z, other.max.z)),
    )

    fun transformed(transform: Affine3) = of(corners().map(transform::transformPoint))

    companion object {
        fun of(points: Collection<Vec3>): Box3 {
            require(points.isNotEmpty())
            var low = points.first()
            var high = low
            for (point in points) {
                low = Vec3(min(low.x, point.x), min(low.y, point.y), min(low.z, point.z))
                high = Vec3(max(high.x, point.x), max(high.y, point.y), max(high.z, point.z))
            }
            return Box3(low, high)
        }
    }
}

/** A 4 x 4 matrix, column-major, for projections, which are not affine. */
internal object Matrix4 {
    fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val result = DoubleArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0.0
                for (k in 0 until 4) sum += a[k * 4 + row] * b[column * 4 + k]
                result[column * 4 + row] = sum
            }
        }
        return result
    }

    /** The inverse by cofactors; null for a singular matrix. */
    fun inverse(m: DoubleArray): DoubleArray? {
        val inv = DoubleArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        val determinant = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (determinant == 0.0) return null
        return DoubleArray(16) { inv[it] / determinant }
    }
}

/** A line through two points, as Slic3r::Linef3. */
internal data class Line3(val a: Vec3, val b: Vec3) {
    fun unitVector() = (b - a).normalized()

    /** Linef3::intersect_plane(): the point of the line at height [z]. */
    fun intersectPlane(z: Double): Vec3 {
        val direction = b - a
        return a + direction * ((z - a.z) / direction.z)
    }
}
