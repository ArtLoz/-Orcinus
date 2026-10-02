package app.orcinus.shadow.render.scene

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
)

/** The three edges of every triangle of [mesh] as GL_LINES segments: x, y, z per end. */
internal fun triangleEdges(mesh: MeshData): FloatArray {
    val vertices = mesh.vertices
    val triangles = mesh.cornerCount / 3
    val edges = FloatArray(triangles * 18)
    var out = 0
    for (triangle in 0 until triangles) {
        for (edge in 0 until 3) {
            for (end in 0 until 2) {
                val corner = (triangle * 3 + (edge + end) % 3) * MeshFiles.FLOATS_PER_CORNER
                edges[out++] = vertices.get(corner)
                edges[out++] = vertices.get(corner + 1)
                edges[out++] = vertices.get(corner + 2)
            }
        }
    }
    return edges
}

/** The corners of [this] as GL_TRIANGLES points: x, y, z per corner. */
internal fun MeshData.cornerPositions(): FloatArray {
    val positions = FloatArray(cornerCount * 3)
    for (corner in 0 until cornerCount) {
        for (axis in 0 until 3) positions[corner * 3 + axis] = vertices.get(corner * MeshFiles.FLOATS_PER_CORNER + axis)
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
     * normals rather than the faces' own.
     */
    fun read(file: File, smooth: Boolean = false): MeshData = RandomAccessFile(file, "r").use { access ->
        val buffer = access.channel.map(FileChannel.MapMode.READ_ONLY, 0, access.length()).order(ByteOrder.LITTLE_ENDIAN)
        read(buffer, smooth)
    }

    fun read(buffer: ByteBuffer, smooth: Boolean = false): MeshData {
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

        val positions = data.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        data.position(HEADER_BYTES + 12 * vertexCount)
        val indices = data.slice().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        return build(positions, indices, vertexCount, triangleCount, smooth)
    }

    /** A mesh built in code, as indexed_triangle_set: x, y, z per vertex and three vertex indices per triangle. */
    fun fromIndexed(positions: FloatArray, indices: IntArray, smooth: Boolean = false): MeshData =
        build(FloatBuffer.wrap(positions), IntBuffer.wrap(indices), positions.size / 3, indices.size / 3, smooth)

    /**
     * GLModel::init_from(const indexed_triangle_set&): a vertex per corner,
     * with the face's normal, or in the realistic view with "Smooth normals"
     * ([smooth]) the corner's normal of igl::per_corner_normals() at 5 degrees.
     */
    private fun build(positions: FloatBuffer, indices: IntBuffer, vertexCount: Int, triangleCount: Int, smooth: Boolean): MeshData {
        for (index in 0 until triangleCount * 3) {
            val vertex = indices.get(index)
            if (vertex < 0 || vertex >= vertexCount) throw IOException("Mesh file is corrupt")
        }
        val smoothNormals = if (smooth) cornerNormals(positions, indices, vertexCount, triangleCount, SMOOTH_CORNER_THRESHOLD_DEGREES) else null
        val cornerCount = triangleCount * 3
        val vertices = ByteBuffer.allocateDirect(cornerCount * FLOATS_PER_CORNER * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        val corner = IntArray(3)
        for (triangle in 0 until triangleCount) {
            for (k in 0 until 3) {
                val index = indices.get(triangle * 3 + k)
                if (index < 0 || index >= vertexCount) throw IOException("Mesh file is corrupt")
                corner[k] = index * 3
            }
            val x0 = positions.get(corner[0])
            val y0 = positions.get(corner[0] + 1)
            val z0 = positions.get(corner[0] + 2)
            val x1 = positions.get(corner[1])
            val y1 = positions.get(corner[1] + 1)
            val z1 = positions.get(corner[1] + 2)
            val x2 = positions.get(corner[2])
            val y2 = positions.get(corner[2] + 1)
            val z2 = positions.get(corner[2] + 2)
            // face_normal_normalized(): (v1 - v0) x (v2 - v1), normalized.
            val ax = x1 - x0
            val ay = y1 - y0
            val az = z1 - z0
            val bx = x2 - x1
            val by = y2 - y1
            val bz = z2 - z1
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
                val x = positions.get(corner[k])
                val y = positions.get(corner[k] + 1)
                val z = positions.get(corner[k] + 2)
                if (smoothNormals != null) {
                    val normal = (triangle * 3 + k) * 3
                    vertices.put(x).put(y).put(z).put(smoothNormals[normal]).put(smoothNormals[normal + 1]).put(smoothNormals[normal + 2])
                } else {
                    vertices.put(x).put(y).put(z).put(nx).put(ny).put(nz)
                }
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (z < minZ) minZ = z
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                if (z > maxZ) maxZ = z
            }
        }
        vertices.flip()
        val bounds = if (cornerCount == 0) {
            Box3(Vec3.ZERO, Vec3.ZERO)
        } else {
            Box3(Vec3(minX.toDouble(), minY.toDouble(), minZ.toDouble()), Vec3(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()))
        }
        return MeshData(vertices, cornerCount, bounds)
    }

    /**
     * igl::per_corner_normals(V, F, corner_threshold_degrees, CN): every
     * corner's normal is the area-weighted average of the unit normals of the
     * faces around its vertex that turn from its own face by less than the
     * threshold, in double precision as GLModel hands it the mesh.
     */
    private fun cornerNormals(positions: FloatBuffer, indices: IntBuffer, vertexCount: Int, triangleCount: Int, thresholdDegrees: Double): FloatArray {
        // unit normals and face areas
        val faceNormals = DoubleArray(triangleCount * 3)
        val faceAreas = DoubleArray(triangleCount)
        for (face in 0 until triangleCount) {
            val v0 = indices.get(face * 3) * 3
            val v1 = indices.get(face * 3 + 1) * 3
            val v2 = indices.get(face * 3 + 2) * 3
            val ax = positions.get(v1).toDouble() - positions.get(v0)
            val ay = positions.get(v1 + 1).toDouble() - positions.get(v0 + 1)
            val az = positions.get(v1 + 2).toDouble() - positions.get(v0 + 2)
            val bx = positions.get(v2).toDouble() - positions.get(v0)
            val by = positions.get(v2 + 1).toDouble() - positions.get(v0 + 1)
            val bz = positions.get(v2 + 2).toDouble() - positions.get(v0 + 2)
            val nx = ay * bz - az * by
            val ny = az * bx - ax * bz
            val nz = ax * by - ay * bx
            val area = sqrt(nx * nx + ny * ny + nz * nz)
            faceAreas[face] = area
            faceNormals[face * 3] = nx / area
            faceNormals[face * 3 + 1] = ny / area
            faceNormals[face * 3 + 2] = nz / area
        }
        // igl::vertex_triangle_adjacency(): the faces around every vertex, in the faces' order.
        val starts = IntArray(vertexCount + 1)
        for (corner in 0 until triangleCount * 3) starts[indices.get(corner) + 1]++
        for (vertex in 0 until vertexCount) starts[vertex + 1] += starts[vertex]
        val filled = starts.copyOf(vertexCount)
        val faces = IntArray(triangleCount * 3)
        for (corner in 0 until triangleCount * 3) faces[filled[indices.get(corner)]++] = corner / 3

        val cosThreshold = cos(thresholdDegrees * Math.PI / 180)
        val normals = FloatArray(triangleCount * 9)
        for (face in 0 until triangleCount) {
            val fx = faceNormals[face * 3]
            val fy = faceNormals[face * 3 + 1]
            val fz = faceNormals[face * 3 + 2]
            for (corner in 0 until 3) {
                val vertex = indices.get(face * 3 + corner)
                var sx = 0.0
                var sy = 0.0
                var sz = 0.0
                for (k in starts[vertex] until starts[vertex + 1]) {
                    val other = faces[k]
                    val ox = faceNormals[other * 3]
                    val oy = faceNormals[other * 3 + 1]
                    val oz = faceNormals[other * 3 + 2]
                    // if difference in normal is slight then add to average
                    if (fx * ox + fy * oy + fz * oz > cosThreshold) {
                        sx += ox * faceAreas[other]
                        sy += oy * faceAreas[other]
                        sz += oz * faceAreas[other]
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
                normals[out] = sx.toFloat()
                normals[out + 1] = sy.toFloat()
                normals[out + 2] = sz.toFloat()
            }
        }
        return normals
    }

    /** GLModel::init_from(): the corner threshold it gives igl::per_corner_normals(). */
    private const val SMOOTH_CORNER_THRESHOLD_DEGREES = 5.0
}
