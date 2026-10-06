package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinimalSphereTest {
    @Test
    fun theCornersOfACubeLieOnItsSphere() {
        val corners = listOf(0.0, 20.0).flatMap { x -> listOf(0.0, 20.0).flatMap { y -> listOf(0.0, 20.0).map { z -> Vec3(x + 100.0, y, z) } } }

        val (center, radius) = MinimalSphere.of(corners)!!

        assertEquals(110.0, center.x, 1e-9)
        assertEquals(10.0, center.y, 1e-9)
        assertEquals(10.0, center.z, 1e-9)
        assertEquals(10.0 * sqrt(3.0), radius, 1e-9)
    }

    @Test
    fun twoCubesApartAreHeldByTheSphereOfTheirFarCorners() {
        val cube = listOf(0.0, 20.0).flatMap { x -> listOf(0.0, 20.0).flatMap { y -> listOf(0.0, 20.0).map { z -> Vec3(x, y, z) } } }
        val points = cube + cube.map { it + Vec3(60.0, 0.0, 0.0) }

        val (center, radius) = MinimalSphere.of(points)!!

        // The diagonal from (0, 0, 0) to (80, 20, 20) is its diameter.
        assertEquals(40.0, center.x, 1e-9)
        assertEquals(10.0, center.y, 1e-9)
        assertEquals(0.5 * sqrt(80.0 * 80.0 + 20.0 * 20.0 + 20.0 * 20.0), radius, 1e-9)
    }

    @Test
    fun aCloudOfPointsLiesInsideItsSphereWithSomeOnItsSurface() {
        val random = Random(7)
        val points = List(2000) { Vec3(random.nextDouble(-50.0, 50.0), random.nextDouble(-30.0, 30.0), random.nextDouble(0.0, 40.0)) }

        val (center, radius) = MinimalSphere.of(points)!!

        val distances = points.map { (it - center).norm() }
        assertTrue(distances.all { it <= radius + 1e-6 })
        // At least two points touch the surface of the smallest sphere.
        assertTrue(distances.count { it > radius - 1e-6 } >= 2)
    }
}
