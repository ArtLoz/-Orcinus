package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Quaternion
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrcaCameraTest {
    private val plate = Box3(Vec3(0.0, 0.0, 0.0), Vec3(350.0, 350.0, 0.0))

    @Test
    fun defaultOrientationLooksAtTheTargetFromFrontLeftAbove() {
        val camera = OrcaCamera()

        // Camera::set_default_orientation(): zenit 45 degrees, azimuth 45 degrees.
        val half = sqrt(0.5)
        assertVec(Vec3(-0.5, -0.5, half) * 1000.0, camera.position())
        assertVec(Vec3(0.5, 0.5, -half), camera.dirForward())
        assertEquals(45.0, camera.zenit, 1e-9)
    }

    @Test
    fun plateViewFramesThePlateFromTheFront() {
        val camera = OrcaCamera()
        camera.setViewport(1000, 2000)

        camera.selectPlateView()
        camera.zoomToBox(plate, 1.25)

        assertVec(Vec3(175.0, 175.0, 0.0), camera.target)
        assertVec(Vec3(1.0, 0.0, 0.0), camera.dirRight())
        assertTrue(camera.dirForward().y > 0.0 && camera.dirForward().z < 0.0)
        assertEquals(45.0, camera.zenit, 1e-6)
        // The plate's 350 mm width with the margin fills the narrower side.
        assertEquals(1000.0 / (350.0 * 1.25), camera.zoom, 1e-9)
    }

    @Test
    fun aFullTurnAroundTheTargetReturnsToTheSameView() {
        val camera = OrcaCamera()
        camera.selectPlateView()
        val before = camera.viewMatrix.elements()

        repeat(8) { camera.rotateOnSphereWithTarget(Math.PI / 4, 0.0, true, camera.target) }

        camera.viewMatrix.elements().forEachIndexed { index, value -> assertEquals(before[index], value, 1e-9) }
    }

    @Test
    fun zenitStopsAtTheTop() {
        val camera = OrcaCamera()
        camera.selectPlateView()

        camera.rotateOnSphereWithTarget(0.0, Math.toRadians(80.0), true, camera.target)

        assertEquals(90.0, camera.zenit, 1e-9)
        assertVec(Vec3(0.0, 0.0, -1.0), camera.dirForward())
    }

    @Test
    fun movingTheTargetKeepsTheOrientation() {
        val camera = OrcaCamera()
        camera.selectPlateView()
        val forward = camera.dirForward()

        camera.setTarget(Vec3(10.0, 20.0, 0.0))

        assertVec(Vec3(10.0, 20.0, 0.0), camera.target)
        assertVec(forward, camera.dirForward())
        // select_view("plate") places the camera with 0.707, so the distance is not exactly 1000.
        assertEquals(camera.distance, (camera.position() - camera.target).norm(), 1e-9)
    }

    @Test
    fun theProjectionKeepsTheNearPlaneAwayFromTheCamera() {
        val camera = OrcaCamera()
        camera.setViewport(1000, 1000)
        camera.selectPlateView()
        camera.zoomToBox(plate, 1.25)

        camera.applyProjection(plate)

        assertTrue(camera.nearZ >= 100.0)
        assertTrue(camera.farZ > camera.nearZ)
        // A point at the target projects to the centre of the viewport.
        val eye = camera.viewMatrix.transformPoint(camera.target)
        val clipX = camera.projectionMatrix[0] * eye.x + camera.projectionMatrix[8] * eye.z
        assertEquals(0.0, clipX, 1e-6)
    }

    @Test
    fun theFreeCameraRollsAboutItsTargetAndRecoveringLevelsItAgain() {
        val camera = OrcaCamera()
        camera.selectPlateView()
        val target = camera.target
        val distance = (camera.position() - target).norm()

        // A turn about the view's forward axis tilts the right vector off the plate.
        camera.rotateLocalAroundTarget(Vec3(0.3, 0.2, 0.0))
        camera.rotateLocalAroundTarget(Vec3(0.0, 0.0, 0.0))
        assertVec(target, camera.target)
        assertEquals(distance, (camera.position() - camera.target).norm(), 1e-9)
        assertTrue(kotlin.math.abs(camera.dirRight().z) > 1e-3)

        // Camera::recover_from_free_camera(): same eye and target, right vector level again.
        val eye = camera.position()
        camera.recoverFromFreeCamera()
        assertEquals(0.0, camera.dirRight().z, 1e-9)
        assertVec(eye, camera.position())
        assertVec(target, camera.target)
    }

    @Test
    fun theViewsLookAtTheTargetFromTheirSide() {
        val camera = OrcaCamera()
        camera.selectPlateView()
        val target = camera.target
        val distance = (camera.position() - target).norm()
        fun check(view: CameraView, forward: Vec3, up: Vec3) {
            camera.selectView(view)
            assertVec(forward, camera.dirForward())
            assertVec(up, camera.dirUp())
            assertVec(target, camera.target)
            assertEquals(distance, (camera.position() - target).norm(), 1e-9)
        }
        // Camera::select_view(): top looks down with Y up, bottom looks up with -Y up.
        check(CameraView.TOP, Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0))
        check(CameraView.BOTTOM, Vec3(0.0, 0.0, 1.0), Vec3(0.0, -1.0, 0.0))
        check(CameraView.FRONT, Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        check(CameraView.REAR, Vec3(0.0, -1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        check(CameraView.LEFT, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0))
        check(CameraView.RIGHT, Vec3(-1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0))
    }

    @Test
    fun zoomStaysWithinTheSceneLimits() {
        val camera = OrcaCamera()
        camera.setViewport(1000, 1000)
        camera.selectPlateView()
        camera.sceneBox = plate

        camera.setZoom(1e-6)
        assertTrue(camera.zoom > 0.0)
        assertEquals(camera.minZoom(), camera.zoom, 1e-12)

        camera.setZoom(1e6)
        assertEquals(OrcaCamera.MAX_ZOOM, camera.zoom)
    }

    @Test
    fun quaternionsRoundTripThroughRotationMatrices() {
        val rotation = (Quaternion.angleAxis(0.7, Vec3.UNIT_Z) * Quaternion.angleAxis(-0.3, Vec3.UNIT_X)).normalized()

        val restored = Quaternion.fromRotationRows(rotation.toRotationRows())

        val sign = if (restored.w * rotation.w < 0) -1.0 else 1.0
        assertEquals(rotation.w, restored.w * sign, 1e-12)
        assertEquals(rotation.x, restored.x * sign, 1e-12)
        assertEquals(rotation.y, restored.y * sign, 1e-12)
        assertEquals(rotation.z, restored.z * sign, 1e-12)
    }

    private fun assertVec(expected: Vec3, actual: Vec3, tolerance: Double = 1e-6) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual")
        assertEquals(expected.z, actual.z, tolerance, "z of $actual")
    }
}
