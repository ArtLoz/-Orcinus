package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Matrix4
import app.orcinus.shadow.render.scene.math.Quaternion
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * OrcaSlicer's scene camera (src/slic3r/GUI/Camera.cpp), ported line by line.
 * The camera orbits a target at a fixed distance and zooms by scaling the
 * view frustum rather than by moving, so framing and gestures behave as on
 * desktop. The 3D view uses the perspective projection, OrcaSlicer's default;
 * the G-code thumbnails the orthographic one.
 */
internal class OrcaCamera {
    /** Camera::EType::Ortho; the perspective projection otherwise. */
    var orthographic = false

    /** Degrees above the plate: 90 looks straight down. */
    var zenit = 45.0
        private set
    var zoom = 1.0
        private set
    var distance = DEFAULT_DISTANCE
        private set
    var target = Vec3.ZERO
        private set
    var viewMatrix = Affine3()
        private set
    private var viewRotation = Quaternion.IDENTITY

    var viewportWidth = 1
        private set
    var viewportHeight = 1
        private set

    /** Everything the view may show; limits how far out the camera zooms. */
    var sceneBox: Box3? = null

    var nearZ = 0.0
        private set
    var farZ = 0.0
        private set

    /** Column-major projection from the last applyProjection(). */
    var projectionMatrix = DoubleArray(16)
        private set

    init {
        setDefaultOrientation()
    }

    fun setViewport(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
    }

    fun dirRight() = viewMatrix.linearRow(0)

    fun dirUp() = viewMatrix.linearRow(1)

    fun dirForward() = -viewMatrix.linearRow(2)

    fun position() = viewMatrix.inverse().translation()

    fun isLookingDownward() = dirForward().dot(Vec3.UNIT_Z) < 0.0

    fun translate(displacement: Vec3) {
        if (!displacement.isZero()) {
            viewMatrix = viewMatrix.translated(-displacement)
            updateTarget()
        }
    }

    fun setTarget(newTarget: Vec3) {
        updateTarget()
        val displacement = newTarget - target
        if (!displacement.isZero()) {
            target = newTarget
            viewMatrix = viewMatrix.translated(-displacement)
        }
    }

    fun setZoom(value: Double) {
        var clamped = value
        // Don't allow to zoom too far outside the scene.
        val zoomMin = minZoom()
        if (zoomMin > 0.0) clamped = max(clamped, zoomMin)
        // Don't allow to zoom too close to the scene.
        zoom = min(clamped, MAX_ZOOM)
    }

    /**
     * What Camera::load_camera_view() takes from another camera, as each canvas
     * keeps the shared camera's view while another canvas shows: its target,
     * zoom, scene box, view and zenit. The type and the distance stay the
     * camera's own.
     */
    class View(
        val target: Vec3,
        val zoom: Double,
        val sceneBox: Box3?,
        val viewMatrix: Affine3,
        val viewRotation: Quaternion,
        val zenit: Double,
    )

    fun view() = View(target, zoom, sceneBox, viewMatrix, viewRotation, zenit)

    /** The camera's view as numbers, which a page keeps through the activity's recreation (a turn, a resized window). */
    fun saveState(): DoubleArray =
        doubleArrayOf(target.x, target.y, target.z, zoom, distance, zenit, viewRotation.w, viewRotation.x, viewRotation.y, viewRotation.z) +
            viewMatrix.elements()

    /** The view [saveState] gave; false for numbers it did not give. */
    fun restoreState(state: DoubleArray): Boolean {
        if (state.size != SAVED_STATE_SIZE) return false
        target = Vec3(state[0], state[1], state[2])
        zoom = state[3]
        distance = state[4]
        zenit = state[5]
        viewRotation = Quaternion(state[6], state[7], state[8], state[9])
        viewMatrix = Affine3(state.copyOfRange(10, SAVED_STATE_SIZE))
        return true
    }

    /** Camera::load_camera_view() */
    fun loadView(view: View) {
        target = view.target
        zoom = view.zoom
        sceneBox = view.sceneBox
        viewMatrix = view.viewMatrix
        viewRotation = view.viewRotation
        zenit = view.zenit
    }

    fun selectPlateView() = selectView(CameraView.PLATE)

    /** Camera::select_view(): the camera looks at its target from [view], as far away as it is. */
    fun selectView(view: CameraView) {
        when (view) {
            CameraView.ISO -> setDefaultOrientation()
            CameraView.LEFT -> lookAt(target - Vec3.UNIT_X * distance, target, Vec3.UNIT_Z)
            CameraView.RIGHT -> lookAt(target + Vec3.UNIT_X * distance, target, Vec3.UNIT_Z)
            CameraView.TOP -> lookAt(target + Vec3.UNIT_Z * distance, target, Vec3.UNIT_Y)
            CameraView.BOTTOM -> lookAt(target - Vec3.UNIT_Z * distance, target, -Vec3.UNIT_Y)
            CameraView.FRONT -> lookAt(target - Vec3.UNIT_Y * distance, target, Vec3.UNIT_Z)
            CameraView.REAR -> lookAt(target + Vec3.UNIT_Y * distance, target, Vec3.UNIT_Z)
            CameraView.TOP_FRONT, CameraView.PLATE ->
                lookAt(target - Vec3.UNIT_Y * (0.707 * distance) + Vec3.UNIT_Z * (0.707 * distance), target, Vec3.UNIT_Y + Vec3.UNIT_Z)
        }
    }

    /** PartPlateList::select_plate_view(): the plate view of [center], from as far as the camera is. */
    fun selectPlateView(center: Vec3) {
        lookAt(Vec3(center.x, center.y, distance), center, Vec3.UNIT_Y)
        selectPlateView()
    }

    fun zoomToBox(box: Box3, marginFactor: Double = DEFAULT_ZOOM_TO_BOX_MARGIN_FACTOR) {
        val factor = calcZoomToBoundingBoxFactor(box, marginFactor)
        if (factor > 0.0) {
            zoom = factor
            setTarget(box.center())
        }
    }

    fun rotateOnSphereWithTarget(deltaAzimuthRad: Double, deltaZenitRad: Double, applyLimits: Boolean, rotationTarget: Vec3) {
        var deltaZenit = deltaZenitRad
        zenit += Math.toDegrees(deltaZenit)
        if (applyLimits) {
            if (zenit > 90.0) {
                deltaZenit -= Math.toRadians(zenit - 90.0)
                zenit = 90.0
            } else if (zenit < -90.0) {
                deltaZenit -= Math.toRadians(zenit + 90.0)
                zenit = -90.0
            }
        }

        val translation = viewMatrix.translation() + viewRotation.rotate(rotationTarget)
        val rotZ = Quaternion.angleAxis(deltaAzimuthRad, Vec3.UNIT_Z)
        viewRotation = (viewRotation * (rotZ * Quaternion.angleAxis(deltaZenit, rotZ.conjugate().rotate(dirRight())))).normalized()
        viewMatrix = Affine3.fromPositionOrientation(viewRotation.rotate(-rotationTarget) + translation, viewRotation)
    }

    /** Camera::rotate_on_sphere(): about the camera's own target. */
    fun rotateOnSphere(deltaAzimuthRad: Double, deltaZenitRad: Double, applyLimits: Boolean) =
        rotateOnSphereWithTarget(deltaAzimuthRad, deltaZenitRad, applyLimits, target)

    /** Camera::rotate_local_around_target(): the free camera's virtual track ball. */
    fun rotateLocalAroundTarget(rotationRad: Vec3) {
        val angle = rotationRad.norm()
        if (abs(angle) > EPSILON) {
            val translation = viewMatrix.translation() + viewRotation.rotate(target)
            val axis = viewRotation.conjugate().rotate(rotationRad.normalized())
            viewRotation = (viewRotation * Quaternion.angleAxis(angle, axis)).normalized()
            viewMatrix = Affine3.fromPositionOrientation(viewRotation.rotate(-target) + translation, viewRotation)
            updateZenit()
        }
    }

    /** Camera::get_view_rotation(), as the rows of its matrix. */
    fun viewRotationRows(): Array<Vec3> = viewRotation.toRotationRows()

    /** Camera::set_rotation(): the camera turned to the rotation of the rows [rows] about its target. */
    fun setRotation(rows: Array<Vec3>) {
        val translation = viewMatrix.translation() + viewRotation.rotate(target)
        viewRotation = Quaternion.fromRotationRows(rows).normalized()
        viewMatrix = Affine3.fromPositionOrientation(viewRotation.rotate(-target) + translation, viewRotation)
        updateZenit()
    }

    /** Camera::recover_from_free_camera(): the right vector back parallel to the plate. */
    fun recoverFromFreeCamera() {
        if (abs(dirRight().z) > EPSILON) lookAt(position(), target, Vec3.UNIT_Z)
    }

    /** Computes the projection matrix with the frustum's z range tightened around [box]. */
    fun applyProjection(box: Box3) {
        val (near, far) = calcTightFrustumZsAround(box)
        nearZ = near
        farZ = far

        val inverseZoom = 1.0 / zoom
        var w = 0.5 * viewportWidth * inverseZoom
        var h = 0.5 * viewportHeight * inverseZoom
        if (orthographic) {
            projectionMatrix = ortho(-w, w, -h, h, nearZ, farZ)
            return
        }
        // Scale the near plane to keep width and height constant on the plane at z = distance.
        val scale = nearZ / distance
        w *= scale
        h *= scale
        projectionMatrix = perspective(-w, w, -h, h, nearZ, farZ)
    }

    fun minZoom() = sceneBox?.let { 0.2 * calcZoomToBoundingBoxFactor(it) } ?: -1.0

    /**
     * GLCanvas3D::mouse_ray(): the line through the near and the far plane
     * under a point of the viewport, in pixels from the top left.
     */
    fun mouseRay(x: Double, y: Double): Line3? {
        val inverse = Matrix4.inverse(Matrix4.multiply(projectionMatrix, viewMatrix.elements())) ?: return null
        fun unproject(depth: Double): Vec3? {
            // igl::unproject() with the window y flipped, as _mouse_to_3d() does.
            val ndcX = 2.0 * x / viewportWidth - 1.0
            val ndcY = 2.0 * (viewportHeight - y) / viewportHeight - 1.0
            val ndcZ = 2.0 * depth - 1.0
            val w = inverse[3] * ndcX + inverse[7] * ndcY + inverse[11] * ndcZ + inverse[15]
            if (w == 0.0) return null
            return Vec3(
                (inverse[0] * ndcX + inverse[4] * ndcY + inverse[8] * ndcZ + inverse[12]) / w,
                (inverse[1] * ndcX + inverse[5] * ndcY + inverse[9] * ndcZ + inverse[13]) / w,
                (inverse[2] * ndcX + inverse[6] * ndcY + inverse[10] * ndcZ + inverse[14]) / w,
            )
        }
        return Line3(unproject(0.0) ?: return null, unproject(1.0) ?: return null)
    }

    /** The point of the viewport, in pixels from the top left, that [point] projects to; null behind the eye. */
    fun project(point: Vec3): Pair<Double, Double>? {
        val m = Matrix4.multiply(projectionMatrix, viewMatrix.elements())
        val w = m[3] * point.x + m[7] * point.y + m[11] * point.z + m[15]
        if (w <= 0.0) return null
        val ndcX = (m[0] * point.x + m[4] * point.y + m[8] * point.z + m[12]) / w
        val ndcY = (m[1] * point.x + m[5] * point.y + m[9] * point.z + m[13]) / w
        return Pair((ndcX + 1.0) * 0.5 * viewportWidth, viewportHeight - (ndcY + 1.0) * 0.5 * viewportHeight)
    }

    /** Camera::look_at() */
    internal fun lookAt(position: Vec3, lookTarget: Vec3, up: Vec3) {
        val unitZ = (position - lookTarget).normalized()
        val unitX = up.cross(unitZ).normalized()
        val unitY = unitZ.cross(unitX).normalized()

        target = lookTarget
        distance = (position - lookTarget).norm()
        val newPosition = target + unitZ * distance

        viewMatrix = Affine3.fromRows(
            doubleArrayOf(unitX.x, unitX.y, unitX.z, -unitX.dot(newPosition)),
            doubleArrayOf(unitY.x, unitY.y, unitY.z, -unitY.dot(newPosition)),
            doubleArrayOf(unitZ.x, unitZ.y, unitZ.z, -unitZ.dot(newPosition)),
        )
        viewRotation = Quaternion.fromRotationRows(arrayOf(viewMatrix.linearRow(0), viewMatrix.linearRow(1), viewMatrix.linearRow(2))).normalized()
        updateZenit()
    }

    private fun setDefaultOrientation() {
        zenit = 45.0
        val thetaRad = Math.toRadians(-zenit)
        val phiRad = Math.toRadians(45.0)
        val sinTheta = sin(thetaRad)
        val cameraPosition = target + Vec3(sinTheta * sin(phiRad), sinTheta * cos(phiRad), cos(thetaRad)) * distance
        viewRotation = (Quaternion.angleAxis(thetaRad, Vec3.UNIT_X) * Quaternion.angleAxis(phiRad, Vec3.UNIT_Z)).normalized()
        viewMatrix = Affine3.fromPositionOrientation(viewRotation.rotate(-cameraPosition), viewRotation)
    }

    private fun setDistance(value: Double) {
        if (value < EPSILON || value > 1.0e6) return
        if (distance != value) {
            viewMatrix = viewMatrix.translated(dirForward() * (value - distance))
            distance = value
            updateTarget()
        }
    }

    private fun updateZenit() {
        zenit = Math.toDegrees(0.5 * Math.PI - acos((-dirForward().dot(Vec3.UNIT_Z)).coerceIn(-1.0, 1.0)))
    }

    private fun updateTarget() {
        val newTarget = position() + dirForward() * distance
        if (!(newTarget - target).isZero()) target = newTarget
    }

    private fun calcTightFrustumZsAround(box: Box3): Pair<Double, Double> {
        // box in eye space
        val eyeBox = box.transformed(viewMatrix)
        var near = -eyeBox.max.z
        var far = -eyeBox.min.z

        // apply margin
        near -= FRUSTUM_Z_MARGIN
        far += FRUSTUM_Z_MARGIN

        // ensure min size
        if (far - near < FRUSTUM_MIN_Z_RANGE) {
            val midZ = 0.5 * (near + far)
            val halfSize = 0.5 * FRUSTUM_MIN_Z_RANGE
            near = midZ - halfSize
            far = midZ + halfSize
        }

        if (near < FRUSTUM_MIN_NEAR_Z) {
            val delta = FRUSTUM_MIN_NEAR_Z - near
            setDistance(distance + delta)
            near += delta
            far += delta
        }
        return near to far
    }

    private fun calcZoomToBoundingBoxFactor(box: Box3, marginFactor: Double = DEFAULT_ZOOM_TO_BOX_MARGIN_FACTOR): Double {
        if (box.maxSize() == 0.0) return -1.0

        // project the box vertices on a plane perpendicular to the camera forward axis
        // then calculates the vertices coordinate on this plane along the camera xy axes
        val right = dirRight()
        val up = dirUp()
        val forward = dirForward()
        val center = box.center()

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (vertex in box.corners()) {
            val offset = vertex - center
            val onPlane = offset - forward * offset.dot(forward)
            val x = onPlane.dot(right)
            val y = onPlane.dot(up)
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        val dx = (maxX - minX) * marginFactor
        val dy = (maxY - minY) * marginFactor
        if (dx <= 0.0 || dy <= 0.0) return -1.0
        return min(viewportWidth / dx, viewportHeight / dy)
    }

    companion object {
        const val DEFAULT_DISTANCE = 1000.0
        const val DEFAULT_ZOOM_TO_BOX_MARGIN_FACTOR = 1.025
        const val MAX_ZOOM = 250.0
        private const val FRUSTUM_MIN_Z_RANGE = 50.0
        private const val FRUSTUM_MIN_NEAR_Z = 100.0
        private const val FRUSTUM_Z_MARGIN = 10.0
        private const val EPSILON = 1e-4
        private const val SAVED_STATE_SIZE = 26

        /** Camera::apply_projection() for the orthographic type, column-major. */
        private fun ortho(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double): DoubleArray {
            val inverseDx = 1.0 / (right - left)
            val inverseDy = 1.0 / (top - bottom)
            val inverseDz = 1.0 / (far - near)
            return doubleArrayOf(
                2.0 * inverseDx, 0.0, 0.0, 0.0,
                0.0, 2.0 * inverseDy, 0.0, 0.0,
                0.0, 0.0, -2.0 * inverseDz, 0.0,
                -(left + right) * inverseDx, -(bottom + top) * inverseDy, -(near + far) * inverseDz, 1.0,
            )
        }

        /** Camera::apply_projection() for the perspective type, column-major. */
        private fun perspective(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double): DoubleArray {
            val inverseDx = 1.0 / (right - left)
            val inverseDy = 1.0 / (top - bottom)
            val inverseDz = 1.0 / (far - near)
            return doubleArrayOf(
                2.0 * near * inverseDx, 0.0, 0.0, 0.0,
                0.0, 2.0 * near * inverseDy, 0.0, 0.0,
                (left + right) * inverseDx, (bottom + top) * inverseDy, -(near + far) * inverseDz, -1.0,
                0.0, 0.0, -2.0 * near * far * inverseDz, 0.0,
            )
        }
    }
}
