package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Vec3
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** mesh_tree.hpp, as scene_bridge.cpp exposes it; the buffers are direct. */
internal object NativeMeshTree {
    init {
        System.loadLibrary("orcinus_scene")
    }

    external fun nodeCount(triangles: Int): Int

    external fun build(corners: FloatBuffer, triangles: Int, nodes: ByteBuffer, order: ByteBuffer)

    /** [ray]: origin then direction; [hit] receives the parameter and the triangle. */
    external fun firstHit(corners: FloatBuffer, triangles: Int, nodes: ByteBuffer, order: ByteBuffer, ray: DoubleArray, hit: DoubleArray): Boolean

    external fun allHits(corners: FloatBuffer, triangles: Int, nodes: ByteBuffer, order: ByteBuffer, ray: DoubleArray): DoubleArray
}

/**
 * The triangles of a large mesh in a tree of boxes, as OrcaSlicer's
 * MeshRaycaster keeps them in an AABB tree, so a ray tries the few triangles
 * near it instead of every one: a tap on a model of millions of triangles
 * takes microseconds rather than seconds.
 */
internal class MeshTree private constructor(
    private val mesh: MeshData,
    private val nodes: ByteBuffer,
    private val order: ByteBuffer,
) {
    /** The nearest triangle the ray from [origin] along [direction] enters ahead of it: the parameter and the triangle. */
    fun firstHit(origin: Vec3, direction: Vec3): Pair<Double, Int>? {
        val hit = DoubleArray(2)
        if (!NativeMeshTree.firstHit(mesh.vertices, mesh.cornerCount / 3, nodes, order, ray(origin, direction), hit)) return null
        return hit[0] to hit[1].toInt()
    }

    /** The parameters of every triangle the ray enters ahead of [origin], in no order. */
    fun allHits(origin: Vec3, direction: Vec3): DoubleArray =
        NativeMeshTree.allHits(mesh.vertices, mesh.cornerCount / 3, nodes, order, ray(origin, direction))

    private fun ray(origin: Vec3, direction: Vec3) = doubleArrayOf(origin.x, origin.y, origin.z, direction.x, direction.y, direction.z)

    companion object {
        /** Below this many triangles trying every one is as quick. */
        const val MIN_TRIANGLES = 4096

        fun of(mesh: MeshData): MeshTree {
            val triangles = mesh.cornerCount / 3
            val nodes = nativeBuffer(NativeMeshTree.nodeCount(triangles) * NODE_BYTES)
            val order = nativeBuffer(triangles * Int.SIZE_BYTES)
            NativeMeshTree.build(mesh.vertices, triangles, nodes, order)
            return MeshTree(mesh, nodes, order)
        }

        /** TreeNode: two corners of floats and two indices. */
        private const val NODE_BYTES = 32
    }
}
