package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class LayerRangeHintTest {
    @Test
    fun `the range's planes run 10 mm around the copies, the focused one solid and the far one first`() {
        val box = Box3(Vec3(10.0, 20.0, 0.0), Vec3(30.0, 40.0, 20.0))
        val hint = LayerRangeHint(2.0, 5.0, LayerRangeEditor.MIN_Z, setOf(0))
        val fromAbove = layerRangePlanes(hint, box, lookingDownward = true)
        // The bottom plane first, from x 0 to 40 and y 10 to 50, solid green.
        assertEquals(listOf(0f, 10f, 2f, 40f, 10f, 2f, 40f, 50f, 2f), fromAbove[0].triangles.take(9))
        assertEquals(1f, fromAbove[0].color.alpha)
        assertEquals(174f / 255f, fromAbove[0].color.green)
        assertEquals(5f, fromAbove[1].triangles[2])
        assertEquals(0.5f, fromAbove[1].color.alpha)
        // From below the top plane comes first; with no field focused both are see-through.
        val fromBelow = layerRangePlanes(hint.copy(field = LayerRangeEditor.LAYER_HEIGHT), box, lookingDownward = false)
        assertEquals(listOf(5f, 2f), fromBelow.map { it.triangles[2] })
        assertEquals(listOf(0.5f, 0.5f), fromBelow.map { it.color.alpha })
    }
}
