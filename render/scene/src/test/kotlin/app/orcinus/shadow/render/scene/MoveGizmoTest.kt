package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveGizmoTest {
    // The 20 mm cube on the K2 Plus plate, one desktop pixel = 0.5 mm.
    private val cube = Box3(Vec3(165.0, 165.0, 0.0), Vec3(185.0, 185.0, 20.0))
    private val gizmo = MoveGizmo(cube, pixel = 0.5)

    @Test
    fun grabbersStandTwentyPixelsBeyondTheBoxOnEachAxis() {
        assertVec(Vec3(185.0 + 10.0, 175.0, 10.0), gizmo.grabberCenter(0))
        assertVec(Vec3(175.0, 185.0 + 10.0, 10.0), gizmo.grabberCenter(1))
        assertVec(Vec3(175.0, 175.0, 20.0 + 10.0), gizmo.grabberCenter(2))
    }

    @Test
    fun conesPointAlongTheirAxes() {
        // Extension 0.75 * 16 px, the cone twice as long: 12 mm at half a millimetre per pixel.
        assertVec(gizmo.grabberCenter(0) + Vec3(12.0, 0.0, 0.0), gizmo.grabberTip(0))
        assertVec(gizmo.grabberCenter(1) + Vec3(0.0, 12.0, 0.0), gizmo.grabberTip(1))
        assertVec(gizmo.grabberCenter(2) + Vec3(0.0, 0.0, 12.0), gizmo.grabberTip(2))
    }

    @Test
    fun assembleRotatesAboutZThenYThenX() {
        // Geometry::assemble_transform(): Rz * Ry * Rx; a quarter turn about Y then Z sends X up... to Y.
        val transform = Affine3.assemble(Vec3(1.0, 2.0, 3.0), Vec3(0.0, 0.5 * PI, 0.5 * PI), Vec3(2.0, 2.0, 2.0))

        assertVec(Vec3(1.0, 2.0, 1.0), transform.transformPoint(Vec3.UNIT_X))
        assertVec(Vec3(-1.0, 2.0, 3.0), transform.transformPoint(Vec3.UNIT_Y))
    }

    @Test
    fun draggingFollowsTheAxisNotTheFinger() {
        val start = gizmo.grabberCenter(0)
        // A ray from above passing 30 mm further along X and 40 mm off the axis in Y.
        val ray = Line3(Vec3(start.x + 30.0, start.y + 40.0, 500.0), Vec3(start.x + 30.0, start.y + 40.0, -500.0))

        assertEquals(30.0, MoveGizmo.projection(start, gizmo.center, ray), 1e-9)
    }

    @Test
    fun theLinesAreDashedTwelvePixelsOnFourOff() {
        val frame = gizmo.frame(dragged = null, pixelScale = 2f)
        val xLine = frame.lines[0]

        // 20 px from the box centre to its side and 20 px beyond: 20 mm, dashes of 6 mm with 2 mm gaps.
        assertEquals(3, xLine.segments.size / 6)
        assertEquals(181.0f, xLine.segments[3], 1e-4f)
        assertEquals(3f, xLine.width)
        assertEquals(GizmoColors.AXES[0], frame.grabbers[0].color)
        assertEquals(GizmoColors.AXES_HOVER[2], gizmo.frame(dragged = 2, pixelScale = 2f).grabbers[2].color)
    }

    @Test
    fun theGrabberConeIsOrcaSlicersCone() {
        // its_make_cone(1, 1, PI / 18): the angle summed in steps of PI / 18 stays below 2 PI
        // after 36 steps, so the loop makes 37 rim vertices and 74 triangles, as in OrcaSlicer.
        assertEquals(74 * 3, GrabberMeshes.cone.cornerCount)
        assertEquals(1.0, GrabberMeshes.cone.bounds.max.z, 1e-9)
    }

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
        assertEquals(expected.z, actual.z, 1e-9)
    }
}
