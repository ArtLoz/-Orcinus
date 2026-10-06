package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SinkingContoursTest {
    private val cube = GrabberMeshes.cube

    private fun band(center: Vec3) =
        sinkingContourBand(cube.vertices, cube.cornerCount, Affine3.assemble(center, Vec3.ZERO, Vec3(10.0, 10.0, 10.0)))

    @Test
    fun aSinkingCubeIsOutlinedByAMitredBandHalfAMillimetreWide() {
        val band = band(Vec3(20.0, 30.0, 0.0))

        // The 10 mm square, 0.25 mm out and in: 10.5² - 9.5², every triangle facing up just above the plate.
        var area = 0.0
        for (triangle in 0 until band.size / 9) {
            val at = triangle * 9
            val doubled = (band[at + 3] - band[at]) * (band[at + 7] - band[at + 1]) - (band[at + 4] - band[at + 1]) * (band[at + 6] - band[at])
            assertTrue(doubled > 0f)
            area += doubled / 2.0
        }
        assertEquals(20.0, area, 1e-3)
        assertTrue((0 until band.size / 3).all { band[it * 3 + 2] == 0.015f })
        val xs = (0 until band.size / 3).map { band[it * 3] }
        assertEquals(14.75f, xs.min(), 1e-4f)
        assertEquals(25.25f, xs.max(), 1e-4f)
    }

    @Test
    fun aCubeOnThePlateOrBelowItHasNoContour() {
        assertEquals(0, band(Vec3(0.0, 0.0, 5.0)).size)
        // Within SINKING_Z_THRESHOLD of the plate it is not sinking.
        assertEquals(0, band(Vec3(0.0, 0.0, 4.9995)).size)
        assertEquals(0, band(Vec3(0.0, 0.0, -6.0)).size)
    }
}
