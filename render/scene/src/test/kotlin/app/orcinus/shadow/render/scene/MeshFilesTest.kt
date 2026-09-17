package app.orcinus.shadow.render.scene

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
