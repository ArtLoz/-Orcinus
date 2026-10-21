package app.orcinus.shadow.render.scene

import android.os.SharedMemory
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Triangles ready for the GPU the way OrcaSlicer's GLModel::init_from() lays
 * them out: every corner has its own vertex with the face normal, so faces are
 * shaded flat. [vertices] holds x, y, z, nx, ny, nz per corner.
 */
internal class MeshData(
    val vertices: FloatBuffer,
    val cornerCount: Int,
    /** Bounds of the mesh in its own coordinates. */
    val bounds: Box3,
    /**
     * GLVolume::convex_hull() of a large mesh: the vertices of its convex hull,
     * x, y, z each, which reach as far as the mesh in every direction; null
     * for a small mesh, whose corners serve as well.
     */
    val hull: FloatArray? = null,
) {
    /**
     * The tree a large mesh's rays find its triangles with, built the first
     * time it is asked for; null for a small mesh, whose triangles are tried
     * one by one.
     */
    val tree: MeshTree? by lazy { if (cornerCount / 3 >= MeshTree.MIN_TRIANGLES) MeshTree.of(this) else null }
}

/**
 * Every corner's position of the [cornerCount] corners in [vertices], read
 * [CORNERS_PER_READ] at a time: Android reads each number of a direct buffer
 * with a native call, and a run of them with one.
 */
internal inline fun forEachCorner(vertices: FloatBuffer, cornerCount: Int, action: (corner: Int, x: Float, y: Float, z: Float) -> Unit) {
    val source = vertices.duplicate()
    val stride = MeshFiles.FLOATS_PER_CORNER
    val chunk = FloatArray(minOf(cornerCount, CORNERS_PER_READ) * stride)
    var first = 0
    while (first < cornerCount) {
        val count = minOf(CORNERS_PER_READ, cornerCount - first)
        source.position(first * stride)
        source.get(chunk, 0, count * stride)
        for (local in 0 until count) {
            val at = local * stride
            action(first + local, chunk[at], chunk[at + 1], chunk[at + 2])
        }
        first += count
    }
}

internal inline fun MeshData.forEachCorner(action: (corner: Int, x: Float, y: Float, z: Float) -> Unit) = forEachCorner(vertices, cornerCount, action)

internal const val CORNERS_PER_READ = 1 shl 14

/**
 * The points of the mesh as far out as it reaches: the vertices of its convex
 * hull, as OrcaSlicer finds a volume's lowest point, boxes, bounding sphere and
 * outline on the screen from GLVolume::convex_hull(), or every corner of a
 * mesh without one.
 */
internal inline fun MeshData.forEachOuterPoint(action: (x: Float, y: Float, z: Float) -> Unit) {
    val hull = hull
    if (hull == null) {
        forEachCorner { _, x, y, z -> action(x, y, z) }
    } else {
        for (point in 0 until hull.size / 3) action(hull[point * 3], hull[point * 3 + 1], hull[point * 3 + 2])
    }
}

/** The three edges of every triangle of [mesh] as GL_LINES segments: x, y, z per end. */
internal fun triangleEdges(mesh: MeshData): FloatBuffer {
    val edges = nativeBuffer(mesh.cornerCount / 3 * 18 * Float.SIZE_BYTES).asFloatBuffer()
    val triangle = FloatArray(9)
    val segments = FloatArray(18)
    mesh.forEachCorner { corner, x, y, z ->
        val at = corner % 3 * 3
        triangle[at] = x
        triangle[at + 1] = y
        triangle[at + 2] = z
        if (corner % 3 == 2) {
            var out = 0
            for (edge in 0 until 3) {
                for (end in 0 until 2) {
                    val from = (edge + end) % 3 * 3
                    for (axis in 0 until 3) segments[out++] = triangle[from + axis]
                }
            }
            edges.put(segments)
        }
    }
    edges.flip()
    return edges
}

/**
 * A direct buffer of [bytes] in the platform's byte order, outside the Java
 * heap when it is large. Android backs ByteBuffer.allocateDirect() with an
 * array in the Java heap, which an app fills only up to its limit (256 MB on a
 * Pixel 8 Pro), less than the corners of a model of a few million triangles
 * take; shared memory is bound by the device's memory alone, and is freed once
 * the buffer is collected.
 */
internal fun nativeBuffer(bytes: Int): ByteBuffer {
    val buffer = if (bytes < SHARED_MEMORY_MIN_BYTES) ByteBuffer.allocateDirect(bytes) else SharedMemory.create(null, bytes).use(SharedMemory::mapReadWrite)
    return buffer.order(ByteOrder.nativeOrder())
}

/** Smaller buffers stay in the heap: shared memory takes a file descriptor and whole pages. */
private const val SHARED_MEMORY_MIN_BYTES = 1 shl 20

/** The corners of [this] as GL_TRIANGLES points: x, y, z per corner. */
internal fun MeshData.cornerPositions(): FloatArray {
    val positions = FloatArray(cornerCount * 3)
    forEachCorner { corner, x, y, z ->
        positions[corner * 3] = x
        positions[corner * 3 + 1] = y
        positions[corner * 3 + 2] = z
    }
    return positions
}

internal object MeshFiles {
    private val MAGIC = byteArrayOf('O'.code.toByte(), 'M'.code.toByte(), 'S'.code.toByte(), 'H'.code.toByte())
    private const val VERSION = 1
    private const val HEADER_BYTES = 16
    const val FLOATS_PER_CORNER = 6

    /**
     * Reads a mesh file written by the engine (see mesh_file_magic in
     * orca_engine_adapter.hpp); with [smooth], with the realistic view's smooth
     * normals rather than the faces' own; with [hull], with the convex hull of
     * a large mesh, which takes qhull a second or two for millions of vertices.
     */
    fun read(file: File, smooth: Boolean = false, hull: Boolean = false): MeshData = RandomAccessFile(file, "r").use { access ->
        val buffer = access.channel.map(FileChannel.MapMode.READ_ONLY, 0, access.length()).order(ByteOrder.LITTLE_ENDIAN)
        read(buffer, smooth, hull)
    }

    fun read(buffer: ByteBuffer, smooth: Boolean = false, hull: Boolean = false): MeshData {
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (data.remaining() < HEADER_BYTES) throw IOException("Mesh file is truncated")
        val magic = ByteArray(4).also { data.get(it) }
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a mesh file")
        val version = data.int
        if (version != VERSION) throw IOException("Unsupported mesh file version $version")
        val vertexCount = data.int
        val triangleCount = data.int
        if (vertexCount < 0 || triangleCount < 0) throw IOException("Mesh file is corrupt")
        val expected = HEADER_BYTES + 12L * vertexCount + 12L * triangleCount
        if (data.limit() < expected) throw IOException("Mesh file is truncated")

        // The positions are read whole, which the triangles pick from in any order.
        val positions = FloatArray(vertexCount * 3).also { data.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
        data.position(HEADER_BYTES + 12 * vertexCount)
        val indices = data.slice().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        // ModelVolume::calculate_convex_hull(): its_convex_hull() of the vertices.
        val outline = if (hull && triangleCount >= MeshTree.MIN_TRIANGLES) NativeMeshes.convexHull(positions).takeIf { it.isNotEmpty() } else null
        return build(positions, indices, triangleCount, smooth, outline)
    }

    /** A mesh built in code, as indexed_triangle_set: x, y, z per vertex and three vertex indices per triangle. */
    fun fromIndexed(positions: FloatArray, indices: IntArray, smooth: Boolean = false): MeshData =
        build(positions, IntBuffer.wrap(indices), indices.size / 3, smooth)

    /**
     * GLModel::Geometry of the EVertexLayout::P3N3 layout, as smooth_sphere()
     * and its kind make it: x, y, z and the normal of every vertex, and three
     * vertex indices per triangle.
     */
    fun fromVertexNormals(positions: FloatArray, normals: FloatArray, indices: IntArray): MeshData {
        val vertices = nativeBuffer(indices.size * FLOATS_PER_CORNER * Float.SIZE_BYTES).asFloatBuffer()
        for (index in indices) {
            if (index < 0 || index * 3 + 2 >= positions.size || index * 3 + 2 >= normals.size) throw IOException("Mesh is corrupt")
            vertices.put(positions, index * 3, 3).put(normals, index * 3, 3)
        }
        vertices.flip()
        val corners = List(positions.size / 3) { Vec3(positions[it * 3].toDouble(), positions[it * 3 + 1].toDouble(), positions[it * 3 + 2].toDouble()) }
        val bounds = if (corners.isEmpty()) Box3(Vec3.ZERO, Vec3.ZERO) else Box3.of(corners)
        return MeshData(vertices, indices.size, bounds)
    }

    /**
     * GLModel::init_from(const indexed_triangle_set&): a vertex per corner,
     * with the face's normal, or in the realistic view with "Smooth normals"
     * ([smooth]) the corner's normal of igl::per_corner_normals() at 5 degrees.
     * The triangles are read and the corners written [TRIANGLES_PER_READ] at a
     * time: Android reads and writes each number of a direct buffer with a
     * native call, and a run of them with one.
     */
    private fun build(positions: FloatArray, indices: IntBuffer, triangleCount: Int, smooth: Boolean, hull: FloatArray? = null): MeshData {
        val vertexCount = positions.size / 3
        val run = IntArray(minOf(triangleCount, TRIANGLES_PER_READ) * 3)
        forEachTriangleRun(indices, triangleCount, run) { _, count ->
            for (k in 0 until count * 3) {
                if (run[k] < 0 || run[k] >= vertexCount) throw IOException("Mesh file is corrupt")
            }
        }
        val smoothNormals = if (smooth) cornerNormals(positions, indices, vertexCount, triangleCount, SMOOTH_CORNER_THRESHOLD_DEGREES) else null
        val cornerCount = triangleCount * 3
        val vertices = nativeBuffer(cornerCount * FLOATS_PER_CORNER * Float.SIZE_BYTES).asFloatBuffer()
        val corners = FloatArray(run.size * FLOATS_PER_CORNER)
        val normals = FloatArray(if (smoothNormals != null) run.size * 3 else 0)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        forEachTriangleRun(indices, triangleCount, run) { first, count ->
            smoothNormals?.duplicate()?.apply { position(first * 9) }?.get(normals, 0, count * 9)
            var out = 0
            for (local in 0 until count) {
                val c0 = run[local * 3] * 3
                val c1 = run[local * 3 + 1] * 3
                val c2 = run[local * 3 + 2] * 3
                // face_normal_normalized(): (v1 - v0) x (v2 - v1), normalized.
                val ax = positions[c1] - positions[c0]
                val ay = positions[c1 + 1] - positions[c0 + 1]
                val az = positions[c1 + 2] - positions[c0 + 2]
                val bx = positions[c2] - positions[c1]
                val by = positions[c2 + 1] - positions[c1 + 1]
                val bz = positions[c2 + 2] - positions[c1 + 2]
                var nx = ay * bz - az * by
                var ny = az * bx - ax * bz
                var nz = ax * by - ay * bx
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                if (length > 0f) {
                    nx /= length
                    ny /= length
                    nz /= length
                }
                for (k in 0 until 3) {
                    val at = run[local * 3 + k] * 3
                    val x = positions[at]
                    val y = positions[at + 1]
                    val z = positions[at + 2]
                    corners[out++] = x
                    corners[out++] = y
                    corners[out++] = z
                    if (smoothNormals != null) {
                        val normal = (local * 3 + k) * 3
                        corners[out++] = normals[normal]
                        corners[out++] = normals[normal + 1]
                        corners[out++] = normals[normal + 2]
                    } else {
                        corners[out++] = nx
                        corners[out++] = ny
                        corners[out++] = nz
                    }
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (z < minZ) minZ = z
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    if (z > maxZ) maxZ = z
                }
            }
            vertices.put(corners, 0, out)
        }
        vertices.flip()
        val bounds = if (cornerCount == 0) {
            Box3(Vec3.ZERO, Vec3.ZERO)
        } else {
            Box3(Vec3(minX.toDouble(), minY.toDouble(), minZ.toDouble()), Vec3(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()))
        }
        return MeshData(vertices, cornerCount, bounds, hull)
    }

    /** The vertex indices of [triangleCount] triangles, [run] (three a triangle) at a time from the first. */
    private inline fun forEachTriangleRun(indices: IntBuffer, triangleCount: Int, run: IntArray, action: (first: Int, count: Int) -> Unit) {
        val source = indices.duplicate()
        var first = 0
        while (first < triangleCount) {
            val count = minOf(run.size / 3, triangleCount - first)
            source.position(first * 3)
            source.get(run, 0, count * 3)
            action(first, count)
            first += count
        }
    }

    /**
     * igl::per_corner_normals(V, F, corner_threshold_degrees, CN): every
     * corner's normal is the area-weighted average of the unit normals of the
     * faces around its vertex that turn from its own face by less than the
     * threshold, in double precision as GLModel hands it the mesh. The arrays
     * of a large mesh would not fit in the Java heap, so they are native buffers.
     */
    private fun cornerNormals(positions: FloatArray, indices: IntBuffer, vertexCount: Int, triangleCount: Int, thresholdDegrees: Double): FloatBuffer {
        // unit normals and face areas
        val faceNormals = nativeBuffer(triangleCount * 3 * Double.SIZE_BYTES).asDoubleBuffer()
        val faceAreas = nativeBuffer(triangleCount * Double.SIZE_BYTES).asDoubleBuffer()
        for (face in 0 until triangleCount) {
            val v0 = indices.get(face * 3) * 3
            val v1 = indices.get(face * 3 + 1) * 3
            val v2 = indices.get(face * 3 + 2) * 3
            val ax = positions[v1].toDouble() - positions[v0]
            val ay = positions[v1 + 1].toDouble() - positions[v0 + 1]
            val az = positions[v1 + 2].toDouble() - positions[v0 + 2]
            val bx = positions[v2].toDouble() - positions[v0]
            val by = positions[v2 + 1].toDouble() - positions[v0 + 1]
            val bz = positions[v2 + 2].toDouble() - positions[v0 + 2]
            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx
            val area = sqrt(nx * nx + ny * ny + nz * nz)
            faceAreas.put(face, area)
            faceNormals.put(face * 3, nx / area)
            faceNormals.put(face * 3 + 1, ny / area)
            faceNormals.put(face * 3 + 2, nz / area)
        }
        // igl::vertex_triangle_adjacency(): the faces around every vertex, in the faces' order.
        val starts = nativeBuffer((vertexCount + 1) * Int.SIZE_BYTES).asIntBuffer()
        for (corner in 0 until triangleCount * 3) {
            val next = indices.get(corner) + 1
            starts.put(next, starts.get(next) + 1)
        }
        for (vertex in 0 until vertexCount) starts.put(vertex + 1, starts.get(vertex + 1) + starts.get(vertex))
        val filled = nativeBuffer(vertexCount * Int.SIZE_BYTES).asIntBuffer()
        for (vertex in 0 until vertexCount) filled.put(vertex, starts.get(vertex))
        val faces = nativeBuffer(triangleCount * 3 * Int.SIZE_BYTES).asIntBuffer()
        for (corner in 0 until triangleCount * 3) {
            val vertex = indices.get(corner)
            val slot = filled.get(vertex)
            faces.put(slot, corner / 3)
            filled.put(vertex, slot + 1)
        }

        val cosThreshold = cos(thresholdDegrees * Math.PI / 180)
        val normals = nativeBuffer(triangleCount * 9 * Float.SIZE_BYTES).asFloatBuffer()
        for (face in 0 until triangleCount) {
            val fx = faceNormals.get(face * 3)
            val fy = faceNormals.get(face * 3 + 1)
            val fz = faceNormals.get(face * 3 + 2)
            for (corner in 0 until 3) {
                val vertex = indices.get(face * 3 + corner)
                var sx = 0.0
                var sy = 0.0
                var sz = 0.0
                for (k in starts.get(vertex) until starts.get(vertex + 1)) {
                    val other = faces.get(k)
                    val ox = faceNormals.get(other * 3)
                    val oy = faceNormals.get(other * 3 + 1)
                    val oz = faceNormals.get(other * 3 + 2)
                    // if difference in normal is slight then add to average
                    if (fx * ox + fy * oy + fz * oz > cosThreshold) {
                        sx += ox * faceAreas.get(other)
                        sy += oy * faceAreas.get(other)
                        sz += oz * faceAreas.get(other)
                    }
                }
                // Eigen's normalize() leaves a zero vector as it is.
                val length = sqrt(sx * sx + sy * sy + sz * sz)
                if (length > 0.0) {
                    sx /= length
                    sy /= length
                    sz /= length
                }
                val out = (face * 3 + corner) * 3
                normals.put(out, sx.toFloat())
                normals.put(out + 1, sy.toFloat())
                normals.put(out + 2, sz.toFloat())
            }
        }
        return normals
    }

    /** GLModel::init_from(): the corner threshold it gives igl::per_corner_normals(). */
    private const val SMOOTH_CORNER_THRESHOLD_DEGREES = 5.0

    private const val TRIANGLES_PER_READ = 1 shl 14
}
