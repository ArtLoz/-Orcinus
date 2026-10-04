package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ObjColorPanel
import app.orcinus.shadow.core.model.ObjColorQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObjColorsTest {
    private val question = ObjColorQuestion(clusterColors = listOf("#FF0000", "#0102FF"), recommended = 2)

    @Test
    fun `the panel opens with the colours appended and matched, the plate's own colour first`() {
        // A red filament on the plate, and a red and a blue colour in the file.
        val panel = ObjColorPanel.open(listOf("#FF0000"), question)

        // deal_default_strategy(): Append adds both colours as filaments 2 and 3,
        // then Color match takes the nearest, the first of equals: the plate's red.
        assertEquals(listOf("#FF0000", "#FF0000", "#0102FF"), panel.items)
        assertEquals(listOf(1, 3), panel.clusterMapFilaments)
        assertTrue(panel.isOk)
        assertTrue(panel.note)
        // update_new_add_final_colors(): only the blue joins the plate, as filament 3; 2 is chosen by none.
        assertEquals(listOf(null, "#0102FF"), panel.newFilaments())
    }

    @Test
    fun `Reset leaves every box without a filament, which OK waits for`() {
        val reset = ObjColorPanel.open(listOf("#FF0000"), question).reset()

        assertEquals(listOf("#FF0000"), reset.items)
        assertFalse(reset.isOk)
        val chosen = reset.select(0, 1).select(1, 1)
        assertTrue(chosen.isOk)
        assertEquals(emptyList(), chosen.newFilaments())
    }
}
