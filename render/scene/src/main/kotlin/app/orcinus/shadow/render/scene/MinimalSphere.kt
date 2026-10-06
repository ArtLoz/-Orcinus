package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * CGAL::Min_sphere_of_spheres_d over points, which Selection::get_bounding_sphere()
 * takes around the selection's volumes: the smallest sphere that holds every
 * point, by Welzl's randomised incremental algorithm, a sphere being fixed by
 * at most four points on its surface.
 */
internal object MinimalSphere {
    /** The centre and the radius of the smallest sphere around [points]; null for none. */
    fun of(points: List<Vec3>, random: Random = Random(0)): Pair<Vec3, Double>? {
        if (points.isEmpty()) return null
        val p = points.shuffled(random)
        var center = p[0]
        var radius2 = 0.0
        for (i in 1 until p.size) {
            if (inside(p[i], center, radius2)) continue
            center = p[i]
            radius2 = 0.0
            for (j in 0 until i) {
                if (inside(p[j], center, radius2)) continue
                center = (p[i] + p[j]) * 0.5
                radius2 = squared(p[i] - center)
                for (k in 0 until j) {
                    if (inside(p[k], center, radius2)) continue
                    val circle = circle(p[i], p[j], p[k]) ?: continue
                    center = circle
                    radius2 = squared(p[i] - center)
                    for (l in 0 until k) {
                        if (inside(p[l], center, radius2)) continue
                        val sphere = sphere(p[i], p[j], p[k], p[l]) ?: continue
                        center = sphere
                        radius2 = squared(p[i] - center)
                    }
                }
            }
        }
        return center to sqrt(radius2)
    }

    private fun squared(v: Vec3) = v.dot(v)

    private fun inside(point: Vec3, center: Vec3, radius2: Double) = squared(point - center) <= radius2 * (1.0 + 1e-10) + 1e-12

    /** The centre of the circle through three points, in their plane; null for points on a line. */
    private fun circle(a: Vec3, b: Vec3, c: Vec3): Vec3? {
        val ab = b - a
        val ac = c - a
        val normal = ab.cross(ac)
        val denominator = 2.0 * normal.dot(normal)
        if (denominator < 1e-18) return null
        return a + (ac.cross(normal) * ab.dot(ab) + normal.cross(ab) * ac.dot(ac)) / denominator
    }

    /** The centre of the sphere through four points; null for points in a plane. */
    private fun sphere(a: Vec3, b: Vec3, c: Vec3, d: Vec3): Vec3? {
        val ab = b - a
        val ac = c - a
        val ad = d - a
        val determinant = ab.dot(ac.cross(ad))
        if (abs(determinant) < 1e-12) return null
        // 2 (p - a) · x' = |p - a|², with x = a + x'.
        val offset = (ac.cross(ad) * ab.dot(ab) + ad.cross(ab) * ac.dot(ac) + ab.cross(ac) * ad.dot(ad)) / (2.0 * determinant)
        return a + offset
    }
}
