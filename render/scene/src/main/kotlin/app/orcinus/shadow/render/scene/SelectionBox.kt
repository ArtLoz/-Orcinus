package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.rotationPart
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.abs

/**
 * A box Selection::render_bounding_box() frames: [box] in the coordinates
 * [transform] turns into the world's, with arrows under it while the copy's
 * auto drop is off, white, or yellow ([yellow]) in the assembly view and for
 * the same volume in the other copies (render_synchronized_volumes()).
 */
internal class SelectionBracket(val box: Box3, val transform: Affine3, val autoDrop: Boolean, val yellow: Boolean)

/**
 * The extents of meshes along the axes of a transformation, from every point
 * of the mesh once for each linear part (TriangleMesh::transformed_bounding_box()
 * of the convex hull, whose points reach as far): a move only shifts them.
 */
internal class MeshExtents {
    private val boxes = object : LinkedHashMap<Pair<MeshData, List<Double>>, Box3>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<MeshData, List<Double>>, Box3>?) = size > MAX_BOXES
    }

    /** The box of [mesh]'s points through [transform]. */
    fun of(mesh: MeshData, transform: Affine3): Box3 {
        val linear = (0 until 3).flatMap { row -> (0 until 3).map { column -> transform[row, column] } }
        val box = boxes.getOrPut(mesh to linear) {
            if (mesh.cornerCount == 0) return@getOrPut Box3(Vec3.ZERO, Vec3.ZERO)
            val linearPart = transform.withTranslation(Vec3.ZERO)
            val vertices = mesh.vertices
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var minZ = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            var maxZ = -Double.MAX_VALUE
            for (corner in 0 until mesh.cornerCount) {
                val offset = corner * MeshFiles.FLOATS_PER_CORNER
                val point = linearPart.transformPoint(Vec3(vertices.get(offset).toDouble(), vertices.get(offset + 1).toDouble(), vertices.get(offset + 2).toDouble()))
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                minZ = minOf(minZ, point.z)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
                maxZ = maxOf(maxZ, point.z)
            }
            Box3(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
        }
        val shift = transform.translation()
        return Box3(box.min + shift, box.max + shift)
    }

    /** Forgets the meshes no longer in the scene. */
    fun retain(meshes: Collection<MeshData>) {
        val kept = meshes.toCollection(java.util.Collections.newSetFromMap(java.util.IdentityHashMap()))
        boxes.keys.retainAll { it.first in kept }
    }

    private companion object {
        const val MAX_BOXES = 256
    }
}

internal object SelectionBoxes {
    /**
     * Selection::get_bounding_box_in_reference_system(): the box of the
     * [volumes]' points along the axes of [reference] without its scale (the
     * world's for none), and the rotation that turns it into the world. A
     * volume alone in its own coordinates ([centre], its origin in the world)
     * is boxed about that origin, as far as it reaches either way, for a
     * volume not centred on it, as a text aligned to the right.
     */
    fun inReference(volumes: List<SceneObject>, reference: Affine3?, extents: MeshExtents, centre: Vec3? = null): Pair<Box3, Affine3> {
        val basis = reference?.let(::rotationOf) ?: Affine3()
        val toBasis = basis.inverse()
        val box = volumes.map { extents.of(it.mesh, toBasis * it.world) }.reduce(Box3::merge)
        if (centre == null) return box to basis
        val axes = listOf(basis.transformVector(Vec3.UNIT_X), basis.transformVector(Vec3.UNIT_Y), basis.transformVector(Vec3.UNIT_Z))
        val c = axes.map { it.dot(centre) }
        val min = listOf(box.min.x, box.min.y, box.min.z)
        val max = listOf(box.max.x, box.max.y, box.max.z)
        val half = (0 until 3).map { maxOf(abs(c[it] - min[it]), abs(c[it] - max[it])) }
        return Box3(Vec3(c[0] - half[0], c[1] - half[1], c[2] - half[2]), Vec3(c[0] + half[0], c[1] + half[1], c[2] + half[2])) to basis
    }

    /** Transformation::reset_scaling_factor() without the offset: the rotation of [transform]. */
    fun rotationOf(transform: Affine3): Affine3 =
        Affine3(Transform3(transform.elements().toList()).rotationPart().columns.toDoubleArray())
}
