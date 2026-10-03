package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SceneSelectionTest {
    private val plate = Box3(Vec3(0.0, 0.0, 0.0), Vec3(350.0, 350.0, 0.0))

    @Test
    fun theRayUnderTheViewportCentrePassesThroughTheTarget() {
        val camera = OrcaCamera()
        camera.setViewport(1000, 2000)
        camera.selectPlateView()
        camera.zoomToBox(plate, 1.25)
        camera.applyProjection(plate)

        val ray = assertNotNull(camera.mouseRay(500.0, 1000.0))

        // The target lies on the ray, which runs along the view direction.
        val direction = (ray.b - ray.a).normalized()
        val toTarget = camera.target - ray.a
        assertEquals(0.0, (toTarget - direction * toTarget.dot(direction)).norm(), 1e-6)
        assertEquals(1.0, direction.dot(camera.dirForward()), 1e-9)
        // A point to the right on the screen is to the right on the plate.
        val right = assertNotNull(camera.mouseRay(900.0, 1000.0)).intersectPlane(0.0)
        assertEquals(400.0 / camera.zoom, (right - camera.target).dot(camera.dirRight()), 1e-6)
    }

    @Test
    fun aTouchHitsTheTopOfAMovedCube() {
        val cube = cubeAt(Vec3(100.0, 120.0, 10.0))

        val hit = assertNotNull(cube.raycast(Line3(Vec3(105.0, 115.0, 100.0), Vec3(105.0, 115.0, -100.0))))

        assertEquals(105.0, hit.x, 1e-9)
        assertEquals(115.0, hit.y, 1e-9)
        assertEquals(20.0, hit.z, 1e-9)
        assertNull(cube.raycast(Line3(Vec3(175.0, 175.0, 100.0), Vec3(175.0, 175.0, -100.0))))
    }

    @Test
    fun aPaintingTouchPassesByWhatTheSectionClipsAndWhatSinks() {
        val cube = cubeAt(Vec3(100.0, 120.0, 10.0))
        val upperHalf = { point: Vec3 -> point.z > 10.0 }
        val fromAbove = Line3(Vec3(105.0, 115.0, 100.0), Vec3(105.0, 115.0, -100.0))

        // The top is clipped: the bottom is left, met from inside the cube.
        assertNull(cube.unproject(fromAbove, sinkingLimit = true, clipped = upperHalf))
        assertEquals(20.0, assertNotNull(cube.unproject(fromAbove, sinkingLimit = true) { false }).z, 1e-9)
        // Along X under the plane both sides are there, and the near one is met.
        val alongX = Line3(Vec3(200.0, 115.0, 5.0), Vec3(0.0, 115.0, 5.0))
        assertEquals(110.0, assertNotNull(cube.unproject(alongX, sinkingLimit = true, clipped = upperHalf)).x, 1e-9)

        // A cube sunk half into the plate: from below, its bottom is passed by but in the assembly view.
        val sunk = cubeAt(Vec3(100.0, 120.0, 0.0))
        val fromBelow = Line3(Vec3(105.0, 115.0, -100.0), Vec3(105.0, 115.0, 100.0))
        assertNull(sunk.unproject(fromBelow, sinkingLimit = true) { false })
        assertEquals(-10.0, assertNotNull(sunk.unproject(fromBelow, sinkingLimit = false) { false }).z, 1e-9)
    }

    @Test
    fun theLowestPointFollowsThePlacement() {
        assertEquals(0.0, cubeAt(Vec3(100.0, 120.0, 10.0)).minZ(), 1e-9)
        assertEquals(15.0, cubeAt(Vec3(0.0, 0.0, 25.0)).minZ(), 1e-9)
    }

    @Test
    fun aSelectedVolumeIsBrightenedInHsl() {
        // GLVolume::brighten_color(): lightness 0.5 becomes 0.75 at the same hue.
        assertColor(ColorRgba(1f, 0.5f, 0.5f), VolumeColors.render(ColorRgba(1f, 0f, 0f), selected = true))
        assertColor(ColorRgba(0.75f, 0.75f, 0.75f), VolumeColors.render(ColorRgba(0.5f, 0.5f, 0.5f), selected = true))
        // Black is lifted to dark grey first, selected or not.
        assertColor(ColorRgba(0.2f, 0.2f, 0.2f), VolumeColors.render(ColorRgba(0f, 0f, 0f), selected = false))
        assertColor(ColorRgba(0.45f, 0.45f, 0.45f), VolumeColors.render(ColorRgba(0f, 0f, 0f), selected = true))
    }

    private fun assertColor(expected: ColorRgba, actual: ColorRgba) {
        assertEquals(expected.red, actual.red, 1e-5f)
        assertEquals(expected.green, actual.green, 1e-5f)
        assertEquals(expected.blue, actual.blue, 1e-5f)
        assertEquals(expected.alpha, actual.alpha, 1e-5f)
    }

    /** The 20 mm calibration cube in object coordinates, centred on the origin, placed at [offset]. */
    private fun cubeAt(offset: Vec3): SceneObject {
        val vertices = floatArrayOf(
            -10f, -10f, -10f, 10f, -10f, -10f, 10f, 10f, -10f, -10f, 10f, -10f,
            -10f, -10f, 10f, 10f, -10f, 10f, 10f, 10f, 10f, -10f, 10f, 10f,
        )
        val indices = intArrayOf(
            0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7,
            0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5,
            2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7,
        )
        val file = ByteBuffer.allocate(16 + vertices.size * 4 + indices.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        file.put("OMSH".toByteArray())
        file.putInt(1)
        file.putInt(vertices.size / 3)
        file.putInt(indices.size / 3)
        vertices.forEach(file::putFloat)
        indices.forEach(file::putInt)
        file.flip()
        return SceneObject(0, "cube.mesh", MeshFiles.read(file), Affine3().translated(offset), ColorRgba(1f, 0f, 0f), Vec3.ZERO, 10.0 * kotlin.math.sqrt(3.0))
    }
}
