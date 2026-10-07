package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CopyClearance
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Box3

/**
 * GLCanvas3D::SequentialPrintClearance: the copies' clearance [outlines],
 * their union as triangles ([fill]), and the height limits of the copies
 * printed before the last as triangles at their heights ([heightLimitFill]).
 * A failing validation draws them with the fill ([renderFill]); a drag of the
 * copies draws the outlines and the height limits alone, greyed
 * (set_sequential_print_clearance_render_fill(false)).
 */
data class PlateClearance(
    val outlines: List<List<Point2>>,
    val fill: List<Point2>,
    val heightLimitFill: List<Vector3>,
    val renderFill: Boolean = true,
)

/**
 * The extruder's clearance heights of a plate printed by object
 * (extruder_clearance_height_to_lid and extruder_clearance_height_to_rod),
 * which PartPlate::render_height_limit() draws over the current plate and
 * GLCanvas3D::update_sequential_clearance() limits the copies' heights to.
 */
data class ExtruderClearance(val heightToLid: Double, val heightToRod: Double)

/**
 * SequentialPrintClearance::set_polygons(): the outlines as line segments a
 * little over the plate (GLModel::init_from(polygons, 0.025)), the fill a
 * little less (0.0125) against z-fighting, and the height limits where they
 * are; x, y and z per point.
 */
internal class SceneClearance(clearance: PlateClearance) {
    val renderFill = clearance.renderFill
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

internal object SequentialClearances {
    /**
     * GLCanvas3D::update_sequential_clearance() while a finger drags copies of
     * a plate printed by object: every copy's outline ([copies], by its index
     * in the scene) at its offset where it stands now ([offsets]), but those
     * whose outline's box does not overlap the current plate's box ([plate]);
     * and, ordered as the desktop app orders
     * them, the outline of every copy but the last up to the height its
     * extruder clears: to the lid, or to the rod when a copy after it shares
     * rows of the plate, or for the last the printable height, where the copy
     * stands taller.
     */
    fun of(
        copies: List<CopyClearance>,
        offsets: Map<Int, Point2>,
        plate: Box3,
        clearance: ExtruderClearance,
        printableHeight: Double,
    ): PlateClearance {
        val polygons = ArrayList<List<Point2>>()
        val boxes = ArrayList<HeightInfo>()
        copies.forEachIndexed { index, copy ->
            val offset = offsets[index] ?: return@forEachIndexed
            if (copy.outline.isEmpty()) return@forEachIndexed
            val hull = copy.outline.map { Point2(it.x + offset.x, it.y + offset.y) }
            val info = HeightInfo(copy.top, hull.minOf { it.x }, hull.minOf { it.y }, hull.maxOf { it.x }, hull.maxOf { it.y }, hull)
            // skip the object for not current plate
            if (!(plate.max.x < info.minX || plate.min.x > info.maxX || plate.max.y < info.minY || plate.min.y > info.maxY)) {
                boxes += info
                polygons += hull
            }
        }

        // sort the print instance
        insertionSort(boxes) { l, r ->
            val interMin = maxOf(l.minY, r.minY)
            val interMax = minOf(l.maxY, r.maxY)
            if (interMax - interMin > 0) (l.minX < r.minX) || ((l.minX == r.minX) && (l.minY < r.minY)) else l.minY < r.minY
        }

        val heightLimits = ArrayList<Vector3>()
        for (k in boxes.indices) {
            val box = boxes[k]
            var height = if (k == boxes.size - 1) printableHeight else clearance.heightToLid
            for (i in k + 1 until boxes.size) {
                val next = boxes[i]
                // length = max_y - min_y > 0 means intersection exists
                if (minOf(box.maxY, next.maxY) - maxOf(box.minY, next.minY) > 0) {
                    height = clearance.heightToRod
                    break
                }
            }
            if (height < box.top) {
                // triangulate_expolygon_3d() of the convex outline, a fan from its first point.
                for (corner in 1 until box.hull.size - 1) {
                    for (point in listOf(box.hull[0], box.hull[corner], box.hull[corner + 1])) heightLimits += Vector3(point.x, point.y, height)
                }
            }
        }
        return PlateClearance(polygons, emptyList(), heightLimits, renderFill = false)
    }

    /** A copy's top, the box of its outline and the outline (height_info). */
    private class HeightInfo(val top: Double, val minX: Double, val minY: Double, val maxX: Double, val maxY: Double, val hull: List<Point2>)

    /**
     * std::sort() of so few copies, an insertion sort: one that goes before
     * the first goes first, any other after the last it does not go before.
     * The order the desktop app compares them by is not a strict weak order,
     * which the JVM's sort may refuse.
     */
    private fun <T> insertionSort(list: MutableList<T>, before: (T, T) -> Boolean) {
        for (i in 1 until list.size) {
            val value = list[i]
            if (before(value, list[0])) {
                for (j in i downTo 1) list[j] = list[j - 1]
                list[0] = value
            } else {
                var j = i
                while (before(value, list[j - 1])) {
                    list[j] = list[j - 1]
                    j--
                }
                list[j] = value
            }
        }
    }
}
