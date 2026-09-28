package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewNavigatorTest {
    private val size = 128f

    @Test
    fun aTapOnTheTopFaceTurnsTheCameraToTheTopView() = assertTapTurnsTo(box = 10, view = CameraView.TOP)

    @Test
    fun aTapOnTheFrontFaceTurnsTheCameraToTheFrontView() = assertTapTurnsTo(box = 22, view = CameraView.FRONT)

    @Test
    fun theNavigatorsViewMapsBackToTheCamerasRotation() {
        val camera = OrcaCamera()
        val rows = camera.viewRotationRows()

        val back = ViewNavigator.rotationOf(ViewNavigator.viewOf(rows))

        rows.indices.forEach { assertVec(rows[it], back[it], 1e-6) }
    }

    /** GLCanvas3D::_render_3d_navigator() for a tap on the middle of [box]'s face, from the default view. */
    private fun assertTapTurnsTo(box: Int, view: CameraView) {
        val camera = OrcaCamera()
        val navigator = ViewNavigator()
        val start = ViewNavigator.viewOf(camera.viewRotationRows())
        val panel = navigator.draw(start, size, box).panels.first { it.pressed }
        val x = panel.corners.map { it.x }.average().toFloat()
        val y = panel.corners.map { it.y }.average().toFloat()

        navigator.press(start, x, y, size)
        assertEquals(box, navigator.release())
        while (navigator.turning) {
            val next = navigator.step(ViewNavigator.viewOf(camera.viewRotationRows()), 8f) ?: break
            camera.setRotation(ViewNavigator.rotationOf(next))
        }

        val expected = OrcaCamera().apply { selectView(view) }
        assertVec(expected.dirForward(), camera.dirForward(), 1e-3)
        assertVec(expected.dirUp(), camera.dirUp(), 1e-3)
        assertVec(expected.target, camera.target)
    }

    private fun assertVec(expected: Vec3, actual: Vec3, tolerance: Double = 1e-6) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual")
        assertEquals(expected.z, actual.z, tolerance, "z of $actual")
    }
}
