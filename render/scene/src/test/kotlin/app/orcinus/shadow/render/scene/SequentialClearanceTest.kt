package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CopyClearance
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SequentialClearanceTest {
    private val plate = Box3(Vec3(0.0, 0.0, 0.0), Vec3(350.0, 350.0, 0.0))
    private val heights = ExtruderClearance(heightToLid = 120.0, heightToRod = 25.0)
    private val square = listOf(Point2(-10.0, -10.0), Point2(10.0, -10.0), Point2(10.0, 10.0), Point2(-10.0, 10.0))

    @Test
    fun aDragOutlinesTheCopiesOnTheCurrentPlateWhereTheyStand() {
        val copies = List(3) { CopyClearance(square, 10.0) }
        val offsets = mapOf(0 to Point2(50.0, 50.0), 1 to Point2(150.0, 50.0), 2 to Point2(500.0, 50.0))

        val clearance = SequentialClearances.of(copies, offsets, plate, heights, 250.0)

        // The third copy stands on another plate; the outlines go without their fill.
        assertEquals(listOf(Point2(40.0, 40.0), Point2(60.0, 40.0), Point2(60.0, 60.0), Point2(40.0, 60.0)), clearance.outlines[0])
        assertEquals(2, clearance.outlines.size)
        assertEquals(Point2(140.0, 40.0), clearance.outlines[1][0])
        assertTrue(clearance.fill.isEmpty())
        assertFalse(clearance.renderFill)
        assertTrue(clearance.heightLimitFill.isEmpty())
    }

    @Test
    fun aTallCopyBeforeAnotherInItsRowsReachesTheRodAndOneAloneTheLid() {
        // The left copy goes first in its row and the copy after it shares its rows: the rod limits it.
        val row = SequentialClearances.of(
            listOf(CopyClearance(square, 100.0), CopyClearance(square, 100.0)),
            mapOf(0 to Point2(150.0, 50.0), 1 to Point2(50.0, 50.0)),
            plate,
            heights,
            250.0,
        )
        assertEquals(6, row.heightLimitFill.size)
        assertTrue(row.heightLimitFill.all { it.z == 25.0 })
        assertTrue(row.heightLimitFill.all { it.x in 40.0..60.0 && it.y in 40.0..60.0 })

        // Copies in rows of their own: the first is limited to the lid, which it is lower than.
        val apart = SequentialClearances.of(
            listOf(CopyClearance(square, 100.0), CopyClearance(square, 130.0)),
            mapOf(0 to Point2(50.0, 50.0), 1 to Point2(50.0, 200.0)),
            plate,
            heights,
            250.0,
        )
        assertTrue(apart.heightLimitFill.isEmpty())
        val taller = SequentialClearances.of(
            listOf(CopyClearance(square, 130.0), CopyClearance(square, 100.0)),
            mapOf(0 to Point2(50.0, 50.0), 1 to Point2(50.0, 200.0)),
            plate,
            heights,
            250.0,
        )
        assertEquals(6, taller.heightLimitFill.size)
        assertTrue(taller.heightLimitFill.all { it.z == 120.0 })
    }

    @Test
    fun theHeightLimitsRiseFromThePlateToTheRodAndOnToTheLid() {
        val shape = listOf(Point2(0.0, 0.0), Point2(10.0, 0.0), Point2(10.0, 10.0), Point2(0.0, 10.0))

        val (lower, upper) = heightLimitLines(shape, heights)

        // Four corners up to the rod and the contour there; the contour at the lid and the corners up to it.
        assertEquals(8 * 6, lower.size)
        assertEquals(8 * 6, upper.size)
        assertEquals(listOf(0f, 0f, 0.02f, 0f, 0f, 25f), lower.take(6))
        assertEquals(listOf(0f, 0f, 25f, 10f, 0f, 25f), lower.slice(24 until 30))
        assertEquals(listOf(0f, 0f, 120f, 10f, 0f, 120f), upper.take(6))
        assertEquals(listOf(0f, 0f, 25f, 0f, 0f, 120f), upper.slice(24 until 30))
    }
}
