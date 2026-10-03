package app.orcinus.shadow.core.model

/** [other] first, then this transformation (Eigen's this * other), both column-major. */
operator fun Transform3.times(other: Transform3): Transform3 {
    val a = columns
    val b = other.columns
    return Transform3(
        List(16) { index ->
            val column = index / 4
            val row = index % 4
            (0 until 4).sumOf { k -> a[k * 4 + row] * b[column * 4 + k] }
        },
    )
}

/** The inverse of an affine transformation: its linear part inverted, and the offset taken back. */
fun Transform3.inverse(): Transform3 {
    val c = columns
    // The linear part, row by row.
    val m00 = c[0]
    val m01 = c[4]
    val m02 = c[8]
    val m10 = c[1]
    val m11 = c[5]
    val m12 = c[9]
    val m20 = c[2]
    val m21 = c[6]
    val m22 = c[10]
    val determinant = m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20)
    val i00 = (m11 * m22 - m12 * m21) / determinant
    val i01 = (m02 * m21 - m01 * m22) / determinant
    val i02 = (m01 * m12 - m02 * m11) / determinant
    val i10 = (m12 * m20 - m10 * m22) / determinant
    val i11 = (m00 * m22 - m02 * m20) / determinant
    val i12 = (m02 * m10 - m00 * m12) / determinant
    val i20 = (m10 * m21 - m11 * m20) / determinant
    val i21 = (m01 * m20 - m00 * m21) / determinant
    val i22 = (m00 * m11 - m01 * m10) / determinant
    val tx = c[12]
    val ty = c[13]
    val tz = c[14]
    return Transform3(
        listOf(
            i00, i10, i20, 0.0,
            i01, i11, i21, 0.0,
            i02, i12, i22, 0.0,
            -(i00 * tx + i01 * ty + i02 * tz), -(i10 * tx + i11 * ty + i12 * tz), -(i20 * tx + i21 * ty + i22 * tz), 1.0,
        ),
    )
}

/** The offset of the transformation. */
val Transform3.translation: Vector3 get() = Vector3(columns[12], columns[13], columns[14])

/** Geometry::translation_transform() */
fun translationTransform(offset: Vector3): Transform3 =
    Transform3(Transform3.IDENTITY.columns.toMutableList().also { it[12] = offset.x; it[13] = offset.y; it[14] = offset.z })

/** The linear part of the transformation applied to [vector]. */
fun Transform3.transformVector(vector: Vector3): Vector3 = Vector3(
    columns[0] * vector.x + columns[4] * vector.y + columns[8] * vector.z,
    columns[1] * vector.x + columns[5] * vector.y + columns[9] * vector.z,
    columns[2] * vector.x + columns[6] * vector.y + columns[10] * vector.z,
)
