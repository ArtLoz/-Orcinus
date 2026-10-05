package app.orcinus.shadow.core.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Transformation::reset_rotation(): the offset, the scale without the
 * rotation (V S V^T of the linear part's singular values, the symmetric
 * square root of its square) and the mirroring.
 */
fun Transform3.withoutRotation(): Transform3 {
    val c = columns
    val m0 = arrayOf(doubleArrayOf(c[0], c[4], c[8]), doubleArrayOf(c[1], c[5], c[9]), doubleArrayOf(c[2], c[6], c[10]))
    val determinant = m0[0][0] * (m0[1][1] * m0[2][2] - m0[1][2] * m0[2][1]) -
        m0[0][1] * (m0[1][0] * m0[2][2] - m0[1][2] * m0[2][0]) +
        m0[0][2] * (m0[1][0] * m0[2][1] - m0[1][1] * m0[2][0])
    // TransformationSVD: a mirroring is taken off along x first, and put back after.
    val mirror = determinant < 0.0
    val m = Array(3) { row -> DoubleArray(3) { column -> if (mirror && column == 0) -m0[row][column] else m0[row][column] } }
    val square = Array(3) { row -> DoubleArray(3) { column -> (0 until 3).sumOf { m[it][row] * m[it][column] } } }
    val root = symmetricSquareRoot(square)
    val result = columns.toMutableList()
    for (row in 0 until 3) {
        for (column in 0 until 3) {
            result[column * 4 + row] = if (mirror && column == 0) -root[row][column] else root[row][column]
        }
    }
    return Transform3(result)
}

/**
 * Transformation::get_rotation_matrix(): the rotation of the linear part,
 * without its scale and mirroring, and without its offset.
 */
fun Transform3.rotationPart(): Transform3 {
    val linear = Transform3(columns.toMutableList().also { it[12] = 0.0; it[13] = 0.0; it[14] = 0.0 })
    val scale = linear.withoutRotation()
    return linear * scale.inverse()
}

/**
 * Transformation::get_scaling_factor(): the absolute diagonal of the
 * scaling Eigen's computeRotationScaling() splits off, V Σ V^T of the
 * linear part's singular values, the smallest negated when it mirrors.
 */
fun Transform3.scalingFactor(): Vector3 {
    val c = columns
    val m = arrayOf(doubleArrayOf(c[0], c[4], c[8]), doubleArrayOf(c[1], c[5], c[9]), doubleArrayOf(c[2], c[6], c[10]))
    val determinant = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
        m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
        m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
    val square = Array(3) { row -> DoubleArray(3) { column -> (0 until 3).sumOf { m[it][row] * m[it][column] } } }
    val scaling = symmetricSquareRoot(square, smallestSign = if (determinant < 0.0) -1.0 else 1.0)
    return Vector3(abs(scaling[0][0]), abs(scaling[1][1]), abs(scaling[2][2]))
}

/**
 * Transformation::set_scaling_factor(): the rotation of the transformation
 * with [factor] for its scale, at its offset.
 */
fun Transform3.withScalingFactor(factor: Vector3): Transform3 {
    val scale = Transform3(
        listOf(
            factor.x, 0.0, 0.0, 0.0,
            0.0, factor.y, 0.0, 0.0,
            0.0, 0.0, factor.z, 0.0,
            0.0, 0.0, 0.0, 1.0,
        ),
    )
    return translationTransform(translation) * rotationPart() * scale
}

/**
 * The symmetric square root of a symmetric positive semidefinite matrix, by
 * Jacobi's eigenvalue rotations; the root of its smallest eigenvalue taken
 * times [smallestSign].
 */
private fun symmetricSquareRoot(matrix: Array<DoubleArray>, smallestSign: Double = 1.0): Array<DoubleArray> {
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
    val smallest = roots.indices.minBy { roots[it] }
    roots[smallest] *= smallestSign
    return Array(3) { row -> DoubleArray(3) { column -> (0 until 3).sumOf { v[row][it] * roots[it] * v[column][it] } } }
}

private const val JACOBI_SWEEPS = 30
