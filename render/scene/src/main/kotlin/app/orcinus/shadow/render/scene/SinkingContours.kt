package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import java.nio.FloatBuffer
import kotlin.math.hypot

/**
 * GLVolume::SinkingContours::update(): where the mesh in [vertices]
 * ([cornerCount] corners, MeshFiles.FLOATS_PER_CORNER apart) placed by
 * [world] crosses the plate (slice_mesh() at z = 0), a band HalfWidth either
 * side of the outline of its cut, expanded and shrunk with mitred corners
 * (expand() and shrink() of ClipperUtils), lifted a little to keep off the
 * plate; GL_TRIANGLES in the world, wound to face up. Empty unless the mesh is
 * sinking (GLVolume::is_sinking(): it reaches below SINKING_Z_THRESHOLD and
 * not wholly).
 */
internal fun sinkingContourBand(vertices: FloatBuffer, cornerCount: Int, world: Affine3): FloatArray {
    val stride = MeshFiles.FLOATS_PER_CORNER
    val m = world.elements()
    val points = DoubleArray(cornerCount * 3)
    var minZ = Double.MAX_VALUE
    var maxZ = -Double.MAX_VALUE
    for (corner in 0 until cornerCount) {
        val offset = corner * stride
        val x = vertices.get(offset).toDouble()
        val y = vertices.get(offset + 1).toDouble()
        val z = vertices.get(offset + 2).toDouble()
        for (axis in 0 until 3) points[corner * 3 + axis] = m[axis] * x + m[4 + axis] * y + m[8 + axis] * z + m[12 + axis]
        minZ = minOf(minZ, points[corner * 3 + 2])
        maxZ = maxOf(maxZ, points[corner * 3 + 2])
    }
    if (!(minZ < SINKING_Z_THRESHOLD && maxZ >= SINKING_Z_THRESHOLD)) return FloatArray(0)
    return band(contours(sliceAtZero(points, cornerCount)))
}

/**
 * GLVolume::is_sinking() and !is_below_printbed() as far as [box], which holds
 * the mesh, tells; sinkingContourBand() measures the mesh itself.
 */
internal fun maySink(box: Box3) = box.min.z < SINKING_Z_THRESHOLD && box.max.z >= 0.0

/** GLVolume::SinkingContours::HalfWidth, in millimetres. */
private const val HALF_WIDTH = 0.25

/** update(): "add a small positive z to avoid z-fighting". */
private const val Z_OFFSET = 0.015f

/** ClipperUtils' DefaultMiterLimit: a corner reaches at most this many half widths from its point. */
private const val MITER_LIMIT = 3.0

/** Model.hpp: SINKING_Z_THRESHOLD. */
private const val SINKING_Z_THRESHOLD = -0.001

/**
 * The segments, x1, y1, x2, y2 each, where the triangles of [points] cross
 * z = 0. A point at z = 0 counts as above, so no triangle touches the plane
 * without crossing it; where an edge crosses is found from its lower end, so
 * both triangles of an edge find the very same point.
 */
private fun sliceAtZero(points: DoubleArray, cornerCount: Int): List<DoubleArray> {
    val segments = ArrayList<DoubleArray>()
    val crossing = DoubleArray(4)
    for (triangle in 0 until cornerCount / 3) {
        var found = 0
        for (edge in 0 until 3) {
            val a = (triangle * 3 + edge) * 3
            val b = (triangle * 3 + (edge + 1) % 3) * 3
            val aBelow = points[a + 2] < 0.0
            if (aBelow == points[b + 2] < 0.0) continue
            val low = if (aBelow) a else b
            val high = if (aBelow) b else a
            val t = -points[low + 2] / (points[high + 2] - points[low + 2])
            if (found < 2) {
                crossing[found * 2] = points[low] + (points[high] - points[low]) * t
                crossing[found * 2 + 1] = points[low + 1] + (points[high + 1] - points[low + 1]) * t
            }
            found++
        }
        if (found == 2) segments += crossing.copyOf()
    }
    return segments
}

/** The segments joined end to end into outlines, closed where they come round, as slice_mesh() chains them. */
private fun contours(segments: List<DoubleArray>): List<Pair<List<DoubleArray>, Boolean>> {
    fun key(x: Double, y: Double) = x.toRawBits() * 31 + y.toRawBits()
    val atPoint = HashMap<Long, MutableList<Int>>()
    segments.forEachIndexed { index, segment ->
        atPoint.getOrPut(key(segment[0], segment[1])) { ArrayList(2) } += index
        atPoint.getOrPut(key(segment[2], segment[3])) { ArrayList(2) } += index
    }
    val used = BooleanArray(segments.size)
    val outlines = ArrayList<Pair<List<DoubleArray>, Boolean>>()
    /** Follows the unused segments on from [point] of the outline until it closes or ends; whether it closed. */
    fun follow(outline: ArrayList<DoubleArray>, start: DoubleArray): Boolean {
        var point = outline.last()
        while (true) {
            val next = atPoint[key(point[0], point[1])]?.firstOrNull { !used[it] && touches(segments[it], point) } ?: return false
            used[next] = true
            val segment = segments[next]
            val forward = segment[0] == point[0] && segment[1] == point[1]
            point = if (forward) doubleArrayOf(segment[2], segment[3]) else doubleArrayOf(segment[0], segment[1])
            if (point[0] == start[0] && point[1] == start[1]) return true
            outline += point
        }
    }
    for (first in segments.indices) {
        if (used[first]) continue
        used[first] = true
        val segment = segments[first]
        val start = doubleArrayOf(segment[0], segment[1])
        val outline = arrayListOf(start, doubleArrayOf(segment[2], segment[3]))
        if (follow(outline, start)) {
            outlines += outline to true
            continue
        }
        // An open outline: on from its other end too.
        outline.reverse()
        follow(outline, doubleArrayOf(Double.NaN, Double.NaN))
        outlines += outline to false
    }
    return outlines.map { (outline, closed) -> outline.filterIndexed { index, point -> index == 0 || !samePoint(point, outline[index - 1]) } to closed }
        .filter { (outline, _) -> outline.size >= 2 }
}

private fun samePoint(a: DoubleArray, b: DoubleArray) = a[0] == b[0] && a[1] == b[1]

private fun touches(segment: DoubleArray, point: DoubleArray) =
    (segment[0] == point[0] && segment[1] == point[1]) || (segment[2] == point[0] && segment[3] == point[1])

/** diff_ex(expand(polygons, HalfWidth), shrink(polygons, HalfWidth)), triangulated: a mitred band along every outline. */
private fun band(outlines: List<Pair<List<DoubleArray>, Boolean>>): FloatArray {
    val triangles = ArrayList<Float>()
    fun corner(x: Double, y: Double) {
        triangles += x.toFloat()
        triangles += y.toFloat()
        triangles += Z_OFFSET
    }
    fun triangle(a: DoubleArray, b: DoubleArray, c: DoubleArray) {
        // Wound counter-clockwise from above, as triangulate_expolygon_3d() faces them up.
        val area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        if (area == 0.0) return
        val (second, third) = if (area > 0.0) b to c else c to b
        corner(a[0], a[1])
        corner(second[0], second[1])
        corner(third[0], third[1])
    }
    for ((outline, closed) in outlines) {
        val count = outline.size
        if (closed && count < 3) continue
        fun normal(from: DoubleArray, to: DoubleArray): DoubleArray {
            val length = hypot(to[0] - from[0], to[1] - from[1])
            return doubleArrayOf(-(to[1] - from[1]) / length, (to[0] - from[0]) / length)
        }
        // Each point's offset either side: along the mean of its edges' normals, as far as keeps the edges HalfWidth away.
        val offsets = (0 until count).map { index ->
            val previous = if (index > 0) normal(outline[index - 1], outline[index]) else if (closed) normal(outline[count - 1], outline[0]) else null
            val next = if (index < count - 1) normal(outline[index], outline[index + 1]) else if (closed) normal(outline[count - 1], outline[0]) else null
            val first = previous ?: next!!
            val second = next ?: previous!!
            val mx = first[0] + second[0]
            val my = first[1] + second[1]
            val length = hypot(mx, my)
            if (length < 1e-9) {
                doubleArrayOf(second[0] * HALF_WIDTH, second[1] * HALF_WIDTH)
            } else {
                val cosine = (mx * second[0] + my * second[1]) / length
                val reach = minOf(HALF_WIDTH / cosine, MITER_LIMIT * HALF_WIDTH)
                doubleArrayOf(mx / length * reach, my / length * reach)
            }
        }
        val segments = if (closed) count else count - 1
        for (index in 0 until segments) {
            val next = (index + 1) % count
            val a = outline[index]
            val b = outline[next]
            val outerA = doubleArrayOf(a[0] + offsets[index][0], a[1] + offsets[index][1])
            val innerA = doubleArrayOf(a[0] - offsets[index][0], a[1] - offsets[index][1])
            val outerB = doubleArrayOf(b[0] + offsets[next][0], b[1] + offsets[next][1])
            val innerB = doubleArrayOf(b[0] - offsets[next][0], b[1] - offsets[next][1])
            triangle(outerA, innerA, outerB)
            triangle(innerA, innerB, outerB)
        }
    }
    return triangles.toFloatArray()
}
