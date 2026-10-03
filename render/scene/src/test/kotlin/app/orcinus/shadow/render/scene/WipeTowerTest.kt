package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.WipeTower
import kotlin.test.Test
import kotlin.test.assertEquals

class WipeTowerTest {
    private val red = ColorRgba(1f, 0f, 0f)
    private val green = ColorRgba(0f, 1f, 0f)
    private val blue = ColorRgba(0f, 0f, 1f)

    @Test
    fun beforeSlicingTheTowerIsAStripePerFilamentFrontToBackInTheirColours() {
        val tower = WipeTower(shown = true, x = 10.0, y = 20.0, width = 30.0, depth = 12.0, height = 5.0, filaments = listOf(1, 3))

        val stripes = SceneLoader.loadWipeTower(tower, listOf(red, green, blue))

        assertEquals(2, stripes.size)
        assertEquals(listOf(red, blue), stripes.map { it.color.copy(alpha = 1f) })
        // Each as deep as the tower over the filaments, the second behind the first.
        assertEquals(listOf(0.0 to 6.0, 6.0 to 12.0), stripes.map { it.mesh.bounds.min.y to it.mesh.bounds.max.y })
        assertEquals(listOf(30.0, 30.0), stripes.map { it.mesh.bounds.size().x })
        assertEquals(listOf(20.0, 26.0), stripes.map { it.bounds.min.y })
    }
}
