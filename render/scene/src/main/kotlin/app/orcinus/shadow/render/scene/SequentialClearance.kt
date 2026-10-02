package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.Vector3

/**
 * GLCanvas3D::SequentialPrintClearance while the plate's validation fails:
 * the copies' clearance [outlines], their union as triangles ([fill]), and
 * the height limits of the copies printed before the last as triangles at
 * their heights ([heightLimitFill]).
 */
data class PlateClearance(
    val outlines: List<List<Point2>>,
    val fill: List<Point2>,
    val heightLimitFill: List<Vector3>,
)

/**
 * SequentialPrintClearance::set_polygons(): the outlines as line segments a
 * little over the plate (GLModel::init_from(polygons, 0.025)), the fill a
 * little less (0.0125) against z-fighting, and the height limits where they
 * are; x, y and z per point.
 */
internal class SceneClearance(clearance: PlateClearance) {
    val lines: FloatArray = clearance.outlines.flatMap { outline ->
        outline.indices.flatMap { i ->
            val a = outline[i]
            val b = outline[(i + 1) % outline.size]
            listOf(a.x.toFloat(), a.y.toFloat(), PERIMETER_Z, b.x.toFloat(), b.y.toFloat(), PERIMETER_Z)
        }
    }.toFloatArray()
    val fill: FloatArray = clearance.fill.flatMap { listOf(it.x.toFloat(), it.y.toFloat(), FILL_Z) }.toFloatArray()
    val heightLimits: FloatArray = clearance.heightLimitFill.flatMap { listOf(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }.toFloatArray()

    private companion object {
        const val PERIMETER_Z = 0.025f
        const val FILL_Z = 0.0125f
    }
}
