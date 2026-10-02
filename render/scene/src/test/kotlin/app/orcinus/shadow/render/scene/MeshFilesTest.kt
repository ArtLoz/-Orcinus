package app.orcinus.shadow.render.scene

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeshFilesTest {
    @Test
    fun trianglesGetOneVertexPerCornerWithTheFaceNormal() {
        // Two triangles of the unit square in the XY plane, sharing two vertices.
        val file = meshFile(
            vertices = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
        )

        val mesh = MeshFiles.read(file)

        assertEquals(6, mesh.cornerCount)
        assertEquals(6 * MeshFiles.FLOATS_PER_CORNER, mesh.vertices.limit())
        // Third corner of the first triangle: (1, 1, 0) facing +Z.
        val corner = FloatArray(MeshFiles.FLOATS_PER_CORNER).also { mesh.vertices.position(2 * MeshFiles.FLOATS_PER_CORNER); mesh.vertices.get(it) }
        assertEquals(listOf(1f, 1f, 0f, 0f, 0f, 1f), corner.toList())
        assertEquals(1.0, mesh.bounds.max.x)
        assertEquals(1.0, mesh.bounds.max.y)
        assertEquals(0.0, mesh.bounds.min.z)
    }

    @Test
    fun smoothNormalsAverageTheFacesThatTurnLessThanFiveDegrees() {
        // Two triangles folded by 4 degrees along their shared edge x = 1, and a third one at right angles to them.
        val tilt = Math.toRadians(4.0)
        val vertices = floatArrayOf(
            0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f,
            (1 + cos(tilt)).toFloat(), 0f, sin(tilt).toFloat(),
            1f, 0f, -1f,
        )
        val indices = intArrayOf(0, 1, 2, 1, 4, 2, 1, 5, 4)

        val mesh = MeshFiles.fromIndexed(vertices, indices, smooth = true)

        fun normal(corner: Int) = FloatArray(3) { mesh.vertices.get(corner * MeshFiles.FLOATS_PER_CORNER + 3 + it) }.toList()
        // The far corner of the first face has that face alone: +Z.
        assertEquals(listOf(0f, 0f, 1f), normal(0))
        // On the fold the two faces are averaged, weighted by their (equal) areas; the upright one is not.
        val folded = listOf(-sin(tilt) / 2, 0.0, (1 + cos(tilt)) / 2).let { n ->
            val length = sqrt(n.sumOf { it * it })
            n.map { (it / length).toFloat() }
        }
        normal(1).zip(folded).forEach { (actual, expected) -> assertEquals(expected, actual, 1e-6f) }
        // Without smooth normals every corner keeps its face's.
        assertEquals(listOf(0f, 0f, 1f), FloatArray(3) { MeshFiles.fromIndexed(vertices, indices).vertices.get(MeshFiles.FLOATS_PER_CORNER + 3 + it) }.toList())
    }

    @Test
    fun filesThatAreNotMeshesAreRejected() {
        assertFailsWith<IOException> { MeshFiles.read(ByteBuffer.wrap("solid cube".toByteArray())) }
        val truncated = meshFile(floatArrayOf(0f, 0f, 0f), intArrayOf(0, 0, 0)).let { ByteBuffer.wrap(it.array(), 0, 20) }
        assertFailsWith<IOException> { MeshFiles.read(truncated.slice()) }
        assertFailsWith<IOException> { MeshFiles.read(meshFile(floatArrayOf(0f, 0f, 0f), intArrayOf(0, 1, 2))) }
    }

    private fun meshFile(vertices: FloatArray, indices: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(16 + vertices.size * 4 + indices.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("OMSH".toByteArray())
        buffer.putInt(1)
        buffer.putInt(vertices.size / 3)
        buffer.putInt(indices.size / 3)
        vertices.forEach(buffer::putFloat)
        indices.forEach(buffer::putInt)
        buffer.flip()
        return buffer
    }
}
