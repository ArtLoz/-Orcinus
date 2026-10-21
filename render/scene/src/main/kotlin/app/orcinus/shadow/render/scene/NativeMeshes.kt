package app.orcinus.shadow.render.scene

import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** liborcinus_scene.so, as scene_bridge.cpp exposes it: mesh_tree.hpp over direct buffers, and convex_hull.hpp. */
internal object NativeMeshes {
    init {
        System.loadLibrary("orcinus_scene")
    }

    external fun nodeCount(triangles: Int): Int

    external fun build(corners: FloatBuffer, triangles: Int, nodes: ByteBuffer, order: ByteBuffer)

    /** [ray]: origin then direction; [hit] receives the parameter and the triangle. */
    external fun firstHit(corners: FloatBuffer, triangles: Int, nodes: ByteBuffer, order: ByteBuffer, ray: DoubleArray, hit: DoubleArray): Boolean

    external fun allHits(corners: FloatBuffer, triangles: Int, nodes: ByteBuffer, order: ByteBuffer, ray: DoubleArray): DoubleArray

    /** The vertices of the convex hull of [positions] (x, y, z each), x, y, z each; empty when qhull finds no hull. */
    external fun convexHull(positions: FloatArray): FloatArray
}
