package app.orcinus.shadow.render.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintVolumeTest {
    @Test
    fun `the shadow mask is the plate's rectangle, its circle as a fan of 64 triangles, or none`() {
        val rectangle = PrintVolume(0, listOf(-0.0001f, -0.0001f, 350.0001f, 350.0001f), listOf(0f, 300f))
        assertEquals(2 * 9, rectangle.maskTriangles()?.size)
        assertEquals(350f, rectangle.maskTriangles()!![3], 0.001f)

        // _render_cast_shadows_on_plate(): the centre and two points of the rim per triangle.
        val circle = PrintVolume(1, listOf(0f, 0f, 100.0001f, 0f), listOf(0f, 200f))
        val fan = circle.maskTriangles()!!
        assertEquals(64 * 9, fan.size)
        assertEquals(100f, fan[3], 0.001f)
        assertEquals(0f, fan[4], 0.001f)

        assertNull(PrintVolume(3, listOf(0f, 0f, 0f, 0f), listOf(0f, 0f)).maskTriangles())
    }
}
