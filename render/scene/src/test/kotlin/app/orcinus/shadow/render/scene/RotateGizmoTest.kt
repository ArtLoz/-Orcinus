package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class RotateGizmoTest {
    // The 20 mm cube at the plate centre: its sphere through the corners.
    private val center = Vec3(175.0, 175.0, 10.0)
    private val gizmo = RotateGizmo(center, 10.0 * sqrt(3.0), pixel = 0.5)

    @Test
    fun ringsLieAcrossTheirAxesAroundTheSphere() {
        assertEquals(5.0 + 10.0 * sqrt(3.0), gizmo.radius, 1e-9)
        for (axis in 0 until 3) {
            val ring = gizmo.ringMatrix(axis)
            assertVec(center, ring.transformPoint(Vec3.ZERO))
            // The ring's normal, its local Z, is the world axis.
            val normal = ring.transformVector(Vec3.UNIT_Z)
            assertEquals(1.0, kotlin.math.abs(normal.dot(listOf(Vec3.UNIT_X, Vec3.UNIT_Y, Vec3.UNIT_Z)[axis])), 1e-9)
        }
    }

    @Test
    fun theAngleIsMeasuredInTheRingPlaneAndSnapsAtTheScale() {
        // A ray straight down through the Z ring at 30 degrees, between the snap regions.
        val distance = 0.8 * gizmo.radius
        val point = center + Vec3(cos(PI / 6) * distance, sin(PI / 6) * distance, 0.0)
        assertEquals(PI / 6, gizmo.dragAngle(2, Line3(point + Vec3(0.0, 0.0, 100.0), point - Vec3(0.0, 0.0, 100.0))), 1e-9)

        // At the rim the angle snaps to the 5 degree scale; near the centre to 45 degrees.
        val rim = center + Vec3(cos(0.3) * gizmo.radius * 1.05, sin(0.3) * gizmo.radius * 1.05, 0.0)
        assertEquals(PI / 36 * 3, gizmo.dragAngle(2, Line3(rim + Vec3(0.0, 0.0, 100.0), rim - Vec3(0.0, 0.0, 100.0))), 1e-9)
        val inner = center + Vec3(cos(0.9) * gizmo.radius * 0.5, sin(0.9) * gizmo.radius * 0.5, 0.0)
        assertEquals(PI / 4, gizmo.dragAngle(2, Line3(inner + Vec3(0.0, 0.0, 100.0), inner - Vec3(0.0, 0.0, 100.0))), 1e-9)
    }

    @Test
    fun anObjectTurnsAboutTheSphereCentre() {
        val start = Affine3().translated(Vec3(175.0, 175.0, 10.0))

        val turned = RotateGizmo.rotated(start, axis = 0, angle = 0.5 * PI, pivot = Vec3(175.0, 175.0, 0.0))

        // The cube's centre, 10 mm above the pivot, swings towards -Y.
        assertVec(Vec3(175.0, 165.0, 0.0), turned.translation())
        assertVec(Vec3(0.0, 0.0, 1.0), turned.transformVector(Vec3.UNIT_Y))
    }

    @Test
    fun theDraggedRingShowsItsScaleAndArc() {
        val idle = gizmo.frame(dragged = null, angle = 0.0, pixelScale = 1f)
        val dragging = gizmo.frame(dragged = 1, angle = 0.5, pixelScale = 1f)

        assertEquals(9, idle.grabbers.size)
        assertEquals(3, dragging.grabbers.size)
        // Circle, scale with snap radii and reference, arc, grabber connection.
        assertEquals(4, dragging.lines.size)
        assertEquals(GizmoColors.AXES_HOVER[1], dragging.grabbers[0].color)
        assertEquals(GrabberShape.CUBE, dragging.grabbers[0].shape)
    }

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
        assertEquals(expected.z, actual.z, 1e-9)
    }
}
