package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ImGuizmo::ViewManipulate() as OrcaSlicer's fork has it, the 3D navigator
 * (GLCanvas3D::_render_3d_navigator()): a cube seen as the camera sees the
 * plate, with 27 boxes — its faces, edges and corners — each of which turns the
 * camera to look from its side, and which a finger drags to turn the camera.
 *
 * The matrices are ImGuizmo's matrix_t: 16 floats, m[i][j] = m16[i * 4 + j],
 * with vectors multiplied from the left, which is the column-major layout of
 * the view matrix OrcaSlicer hands it. The navigator's space has Y up; see
 * [navigatorView].
 */
internal class ViewNavigator {
    private var dragging = false
    private var clicking = false
    private var interpolationUp = Vec4.ZERO
    private var interpolationDir = Vec4.ZERO
    private var interpolationFrames = 0

    /** The box the finger pressed (overBox), highlighted while it stays down. */
    var pressedBox = -1
        private set

    /** What the navigator draws for [view] in a square of [size] pixels at the origin, with the box [highlighted] lit. */
    fun draw(view: FloatArray, size: Float, highlighted: Int): NavigatorDrawing {
        val (cubeView, res) = cube(view)
        val panels = ArrayList<NavigatorPanel>()
        val labels = ArrayList<NavigatorLabel>()
        for (iFace in 0 until 6) {
            val face = face(iFace, cubeView) ?: continue
            for (iPanel in 0 until 9) {
                val p = Vec2(PANEL_POSITION[iPanel].x * 2f, PANEL_POSITION[iPanel].y * 2f)
                val s = Vec2(PANEL_SIZE[iPanel].x * 2f, PANEL_SIZE[iPanel].y * 2f)
                val corners = listOf(
                    face.dx * p.x + face.dy * p.y,
                    face.dx * p.x + face.dy * (p.y + s.y),
                    face.dx * (p.x + s.x) + face.dy * (p.y + s.y),
                    face.dx * (p.x + s.x) + face.dy * p.y,
                ).map { worldToPos((it + face.origin) * (0.5f * face.invert), res, size) }
                panels += NavigatorPanel(corners, highlighted >= 0 && face.box(iPanel) == highlighted)
            }
            labels += label(iFace, face, res, size)
        }
        // draw axis
        val origin = Vec4(-0.5f, -0.5f, -0.5f)
        val axes = (0 until 3).map { i ->
            val dirAxis = DIRECTION_UNARY[i]
            var visible = false
            val mid = origin + dirAxis * 0.5f
            val eye = Vec4(0f, 0f, 0.5f).normalized()
            for (j in 1..2) {
                val f = (mid + DIRECTION_UNARY[(i + j) % 3] * 0.5f).transformVector(cubeView).normalized()
                if (f.dot(eye) > 0f) {
                    visible = true
                    break
                }
            }
            NavigatorAxis(
                axis = i,
                start = worldToPos(origin, res, size),
                end = worldToPos(origin + dirAxis, res, size),
                label = worldToPos(origin + dirAxis * 1.2f, res, size),
                visible = visible,
            )
        }
        return NavigatorDrawing(panels, labels, axes)
    }

    /**
     * The finger went down at ([x], [y]) in the navigator: the box under it, of
     * a face turned towards the camera, is clicked unless the finger moves.
     * False when it is on no box.
     */
    fun press(view: FloatArray, x: Float, y: Float, size: Float): Boolean {
        val (cubeView, res) = cube(view)
        val (rayOrigin, rayVector) = cameraRay(res, x, y, size)
        for (iFace in 0 until 6) {
            val face = face(iFace, cubeView) ?: continue
            val n = face.normal
            val facePlan = buildPlan(n * 0.5f, n)
            val len = intersectRayPlane(rayOrigin, rayVector, facePlan)
            val posOnPlan = rayOrigin + rayVector * len - n * 0.5f
            val localx = DIRECTION_UNARY[face.perpX].dot(posOnPlan) * face.invert + 0.5f
            val localy = DIRECTION_UNARY[face.perpY].dot(posOnPlan) * face.invert + 0.5f
            for (iPanel in 0 until 9) {
                val from = PANEL_POSITION[iPanel]
                val to = Vec2(from.x + PANEL_SIZE[iPanel].x, from.y + PANEL_SIZE[iPanel].y)
                if (localx > from.x && localx < to.x && localy > from.y && localy < to.y) {
                    pressedBox = face.box(iPanel)
                    clicking = true
                    dragging = true
                    interpolationFrames = 0
                    return true
                }
            }
        }
        return false
    }

    /**
     * The finger moved by ([dx], [dy]) desktop pixels: a click becomes a drag,
     * which turns [view] (ImGuizmo's pitch and yaw about the navigator's up);
     * null when it does not change.
     */
    fun drag(view: FloatArray, dx: Float, dy: Float): FloatArray? {
        if (clicking) {
            clicking = false
            pressedBox = -1
        }
        if (!dragging || (dx == 0f && dy == 0f)) return null
        val deltaX = dy * 0.01f
        val deltaY = dx * 0.01f
        val up = Vec4(view[4], view[5], view[6])
        val dir = Vec4(view[8], view[9], view[10])
        // Calculate the rotation along x-axis
        var rotX = acos(up.dot(REFERENCE_UP).coerceIn(-1f, 1f))
        if (up.z < 0) rotX *= -1
        val referenceRight = Vec4(1f, 0f, 0f)
        val referenceForward = Vec4(0f, 0f, 1f)
        var f2 = referenceForward.transformVector(rotationAxis(referenceRight, rotX))
        // Then calculate the rotation along y-axis
        var rotY = acos(dir.dot(f2).coerceIn(-1f, 1f))
        if (dir.x < 0) rotY *= -1
        // Apply deltas
        rotX += deltaX
        rotY += deltaY
        // Clamp
        rotX = rotX.coerceIn(-0.5f * PI_F, 0.5f * PI_F)
        // Calculate new rotation matrix
        val rx = rotationAxis(referenceRight, rotX)
        f2 = REFERENCE_UP.transformVector(rx)
        val ry = rotationAxis(f2, rotY)
        return multiply(rx, ry)
    }

    /** The finger went up: a click turns the camera over the next frames; the box clicked, or -1. */
    fun release(): Int {
        var clicked = -1
        if (clicking) {
            // apply new view direction
            val cx = pressedBox / 9
            val cy = (pressedBox - cx * 9) / 3
            val cz = pressedBox % 3
            interpolationDir = Vec4(1f - cx, 1f - cy, 1f - cz).normalized()
            interpolationUp = if (abs(interpolationDir.dot(REFERENCE_UP)) > 1f - 0.01f) {
                if (pressedBox == 10) Vec4(1f, 0f, 0f) else Vec4(-1f, 0f, 0f)
            } else {
                REFERENCE_UP
            }
            interpolationFrames = INTERPOLATION_FRAMES
            clicked = pressedBox
        }
        clicking = false
        dragging = false
        pressedBox = -1
        return clicked
    }

    /** Whether a click still turns the camera. */
    val turning: Boolean get() = interpolationFrames > 0

    /** A frame of the turn a click started: [view] a step closer to the box's side. */
    fun step(view: FloatArray, length: Float): FloatArray? {
        if (interpolationFrames <= 0) return null
        interpolationFrames--
        val viewInverse = inverse(view)
        val position = Vec4(viewInverse[12], viewInverse[13], viewInverse[14])
        val viewDir = Vec4(viewInverse[8], viewInverse[9], viewInverse[10])
        val camTarget = position - viewDir * length
        val newDir = viewDir.lerp(interpolationDir, 0.2f).normalized()
        val newEye = camTarget + newDir * length
        return lookAt(newEye, camTarget, interpolationUp)
    }

    /** The cube's view (its camera looks at it from where the view looks) and its view-projection. */
    private fun cube(view: FloatArray): Pair<FloatArray, FloatArray> {
        val viewInverse = inverse(view)
        val dir = Vec4(viewInverse[8], viewInverse[9], viewInverse[10])
        val up = Vec4(viewInverse[4], viewInverse[5], viewInverse[6])
        val eye = dir * CUBE_DISTANCE
        val cubeView = lookAt(eye, Vec4.ZERO, up)
        val cubeProjection = orthographic(-1f, 1f, -1f, 1f, 0.01f, 1000f)
        return cubeView to multiply(cubeView, cubeProjection)
    }

    /** A face turned towards the cube's camera, with its axes; null for one facing away. */
    private fun face(iFace: Int, cubeView: FloatArray): Face? {
        val normalIndex = iFace % 3
        val perpX = (normalIndex + 1) % 3
        val perpY = (normalIndex + 2) % 3
        val invert = if (iFace > 2) -1f else 1f
        val n = DIRECTION_UNARY[normalIndex] * invert
        val viewSpaceNormal = n.transformVector(cubeView).normalized()
        val viewSpacePoint = Vec4.ZERO.transformPoint(cubeView)
        // back face culling
        if (buildPlan(viewSpacePoint, viewSpaceNormal).w > 0f) return null
        return Face(normalIndex, perpX, perpY, invert)
    }

    /**
     * The face's label (FaceLabels), whose text maps onto the face as
     * ViewManipulate() maps its vertices: by where three corners of the text
     * go on the screen.
     */
    private fun label(iFace: Int, face: Face, res: FloatArray, size: Float): NavigatorLabel {
        var tdx = DIRECTION_UNARY[face.perpX]
        var tdy = DIRECTION_UNARY[face.perpY]
        var invertX = 1f
        var invertY = 1f
        when (iFace) {
            0 -> { // Back
                tdx = DIRECTION_UNARY[2]
                tdy = DIRECTION_UNARY[1]
                invertX = -1f
                invertY = -1f
            }
            3 -> { // Front
                tdx = DIRECTION_UNARY[2]
                tdy = DIRECTION_UNARY[1]
                invertX = -1f
            }
            1 -> invertY = -1f // Top
            4 -> { // Bottom
                invertX = -1f
                invertY = -1f
            }
            2 -> invertY = -1f // Right
            5 -> Unit // Left
        }
        val scaleFactor = 2f / size
        // v->pos for a vertex at (x, y) of the text, whose origin is its middle.
        return NavigatorLabel(iFace) { x, y, halfWidth, halfHeight ->
            val ppx = ((x - halfWidth) * scaleFactor * invertX + 0.5f) * 2f
            val ppy = ((y - halfHeight) * scaleFactor * invertY + 0.5f) * 2f
            val pt = tdx * ppx + tdy * ppy
            worldToPos((pt + face.origin) * (0.5f * face.invert), res, size)
        }
    }

    private inner class Face(val normalIndex: Int, val perpX: Int, val perpY: Int, val invert: Float) {
        val normal = DIRECTION_UNARY[normalIndex] * invert
        val dx = DIRECTION_UNARY[perpX]
        val dy = DIRECTION_UNARY[perpY]
        val origin = DIRECTION_UNARY[normalIndex] - dx - dy
        private val indexVectorX = DIRECTION_UNARY[perpX] * invert
        private val indexVectorY = DIRECTION_UNARY[perpY] * invert
        private val boxOrigin = DIRECTION_UNARY[normalIndex] * -invert - indexVectorX - indexVectorY

        /** boxCoordInt of a panel: the box of the 27 it belongs to. */
        fun box(iPanel: Int): Int {
            val boxCoord = boxOrigin + indexVectorX * (iPanel % 3).toFloat() + indexVectorY * (iPanel / 3).toFloat() + Vec4(1f, 1f, 1f)
            return (boxCoord.x * 9f + boxCoord.y * 3f + boxCoord.z).toInt()
        }
    }

    companion object {
        const val CUBE_DISTANCE = 3f
        const val INTERPOLATION_FRAMES = 40
        private const val PI_F = Math.PI.toFloat()

        /** The boxes at the middles of the faces: back, top, right, left, bottom, front. */
        val FACE_BOXES = setOf(4, 10, 12, 14, 16, 22)

        /**
         * coord_mapping_transform, Geometry::rotation_transform(Vec3d(0.5 * PI, 0, 0.5 * PI)),
         * by rows: the navigator's X, Y and Z are the plate's Y, Z and X, so its Y is up.
         */
        private val COORD_MAPPING = arrayOf(doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0))

        /** cameraView of GLCanvas3D::_render_3d_navigator(): the camera's view rotation [rows] times coord_mapping_transform. */
        fun viewOf(rows: Array<Vec3>): FloatArray {
            val view = FloatArray(16)
            for (r in 0 until 3) {
                val row = doubleArrayOf(rows[r].x, rows[r].y, rows[r].z)
                for (c in 0 until 3) view[c * 4 + r] = (0 until 3).sumOf { k -> row[k] * COORD_MAPPING[k][c] }.toFloat()
            }
            view[15] = 1f
            return view
        }

        /** The rotation, by rows, ImGuizmo turned [view] to: "Rotate back" (m * coord_mapping_transform.inverse()). */
        fun rotationOf(view: FloatArray): Array<Vec3> = Array(3) { r ->
            // The inverse of a rotation is its transpose.
            val row = DoubleArray(3) { c -> (0 until 3).sumOf { k -> view[k * 4 + r].toDouble() * COORD_MAPPING[c][k] } }
            Vec3(row[0], row[1], row[2])
        }

        private val REFERENCE_UP = Vec4(0f, 1f, 0f)
        private val DIRECTION_UNARY = listOf(Vec4(1f, 0f, 0f), Vec4(0f, 1f, 0f), Vec4(0f, 0f, 1f))
        private val PANEL_POSITION = listOf(
            Vec2(0.75f, 0.75f), Vec2(0.25f, 0.75f), Vec2(0f, 0.75f),
            Vec2(0.75f, 0.25f), Vec2(0.25f, 0.25f), Vec2(0f, 0.25f),
            Vec2(0.75f, 0f), Vec2(0.25f, 0f), Vec2(0f, 0f),
        )
        private val PANEL_SIZE = listOf(
            Vec2(0.25f, 0.25f), Vec2(0.5f, 0.25f), Vec2(0.25f, 0.25f),
            Vec2(0.25f, 0.5f), Vec2(0.5f, 0.5f), Vec2(0.25f, 0.5f),
            Vec2(0.25f, 0.25f), Vec2(0.5f, 0.25f), Vec2(0.25f, 0.25f),
        )

        /** ImGuizmo::LookAt() */
        fun lookAt(eye: Vec4, at: Vec4, up: Vec4): FloatArray {
            val z = (eye - at).normalized()
            var y = up.normalized()
            val x = y.cross(z).normalized()
            y = z.cross(x).normalized()
            return floatArrayOf(
                x.x, y.x, z.x, 0f,
                x.y, y.y, z.y, 0f,
                x.z, y.z, z.z, 0f,
                -x.dot(eye), -y.dot(eye), -z.dot(eye), 1f,
            )
        }

        /** ImGuizmo::OrthoGraphic() */
        private fun orthographic(l: Float, r: Float, b: Float, t: Float, zn: Float, zf: Float) = floatArrayOf(
            2 / (r - l), 0f, 0f, 0f,
            0f, 2 / (t - b), 0f, 0f,
            0f, 0f, 1f / (zf - zn), 0f,
            (l + r) / (l - r), (t + b) / (b - t), zn / (zn - zf), 1f,
        )

        /** matrix_t::RotationAxis() */
        private fun rotationAxis(axis: Vec4, angle: Float): FloatArray {
            val length2 = axis.dot(axis)
            if (length2 < 1.1920929e-7f) return floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            val n = axis * (1f / sqrt(length2))
            val s = sin(angle)
            val c = cos(angle)
            val k = 1f - c
            val xx = n.x * n.x * k + c
            val yy = n.y * n.y * k + c
            val zz = n.z * n.z * k + c
            val xy = n.x * n.y * k
            val yz = n.y * n.z * k
            val zx = n.z * n.x * k
            val xs = n.x * s
            val ys = n.y * s
            val zs = n.z * s
            return floatArrayOf(
                xx, xy + zs, zx - ys, 0f,
                xy - zs, yy, yz + xs, 0f,
                zx + ys, yz - xs, zz, 0f,
                0f, 0f, 0f, 1f,
            )
        }

        /** FPU_MatrixF_x_MatrixF(): a then b. */
        fun multiply(a: FloatArray, b: FloatArray) = FloatArray(16) { index ->
            val i = index / 4
            val j = index % 4
            a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j] + a[i * 4 + 3] * b[12 + j]
        }

        /** matrix_t::Inverse() of a general matrix, by cofactors. */
        fun inverse(source: FloatArray): FloatArray {
            val src = FloatArray(16)
            for (i in 0 until 4) {
                src[i] = source[i * 4]
                src[i + 4] = source[i * 4 + 1]
                src[i + 8] = source[i * 4 + 2]
                src[i + 12] = source[i * 4 + 3]
            }
            val m = FloatArray(16)
            var tmp = floatArrayOf(
                src[10] * src[15], src[11] * src[14], src[9] * src[15], src[11] * src[13],
                src[9] * src[14], src[10] * src[13], src[8] * src[15], src[11] * src[12],
                src[8] * src[14], src[10] * src[12], src[8] * src[13], src[9] * src[12],
            )
            m[0] = (tmp[0] * src[5] + tmp[3] * src[6] + tmp[4] * src[7]) - (tmp[1] * src[5] + tmp[2] * src[6] + tmp[5] * src[7])
            m[1] = (tmp[1] * src[4] + tmp[6] * src[6] + tmp[9] * src[7]) - (tmp[0] * src[4] + tmp[7] * src[6] + tmp[8] * src[7])
            m[2] = (tmp[2] * src[4] + tmp[7] * src[5] + tmp[10] * src[7]) - (tmp[3] * src[4] + tmp[6] * src[5] + tmp[11] * src[7])
            m[3] = (tmp[5] * src[4] + tmp[8] * src[5] + tmp[11] * src[6]) - (tmp[4] * src[4] + tmp[9] * src[5] + tmp[10] * src[6])
            m[4] = (tmp[1] * src[1] + tmp[2] * src[2] + tmp[5] * src[3]) - (tmp[0] * src[1] + tmp[3] * src[2] + tmp[4] * src[3])
            m[5] = (tmp[0] * src[0] + tmp[7] * src[2] + tmp[8] * src[3]) - (tmp[1] * src[0] + tmp[6] * src[2] + tmp[9] * src[3])
            m[6] = (tmp[3] * src[0] + tmp[6] * src[1] + tmp[11] * src[3]) - (tmp[2] * src[0] + tmp[7] * src[1] + tmp[10] * src[3])
            m[7] = (tmp[4] * src[0] + tmp[9] * src[1] + tmp[10] * src[2]) - (tmp[5] * src[0] + tmp[8] * src[1] + tmp[11] * src[2])
            tmp = floatArrayOf(
                src[2] * src[7], src[3] * src[6], src[1] * src[7], src[3] * src[5],
                src[1] * src[6], src[2] * src[5], src[0] * src[7], src[3] * src[4],
                src[0] * src[6], src[2] * src[4], src[0] * src[5], src[1] * src[4],
            )
            m[8] = (tmp[0] * src[13] + tmp[3] * src[14] + tmp[4] * src[15]) - (tmp[1] * src[13] + tmp[2] * src[14] + tmp[5] * src[15])
            m[9] = (tmp[1] * src[12] + tmp[6] * src[14] + tmp[9] * src[15]) - (tmp[0] * src[12] + tmp[7] * src[14] + tmp[8] * src[15])
            m[10] = (tmp[2] * src[12] + tmp[7] * src[13] + tmp[10] * src[15]) - (tmp[3] * src[12] + tmp[6] * src[13] + tmp[11] * src[15])
            m[11] = (tmp[5] * src[12] + tmp[8] * src[13] + tmp[11] * src[14]) - (tmp[4] * src[12] + tmp[9] * src[13] + tmp[10] * src[14])
            m[12] = (tmp[2] * src[10] + tmp[5] * src[11] + tmp[1] * src[9]) - (tmp[4] * src[11] + tmp[0] * src[9] + tmp[3] * src[10])
            m[13] = (tmp[8] * src[11] + tmp[0] * src[8] + tmp[7] * src[10]) - (tmp[6] * src[10] + tmp[9] * src[11] + tmp[1] * src[8])
            m[14] = (tmp[6] * src[9] + tmp[11] * src[11] + tmp[3] * src[8]) - (tmp[10] * src[11] + tmp[2] * src[8] + tmp[7] * src[9])
            m[15] = (tmp[10] * src[10] + tmp[4] * src[8] + tmp[9] * src[9]) - (tmp[8] * src[9] + tmp[11] * src[10] + tmp[5] * src[8])
            val det = src[0] * m[0] + src[1] * m[1] + src[2] * m[2] + src[3] * m[3]
            val invdet = 1 / det
            for (j in 0 until 16) m[j] *= invdet
            return m
        }

        /** worldToPos(): a point of the navigator's space on the screen, within [size] pixels. */
        private fun worldToPos(worldPos: Vec4, mat: FloatArray, size: Float): Vec2 {
            val trans = worldPos.transformPoint(mat)
            val x = trans.x * (0.5f / trans.w) + 0.5f
            val y = 1f - (trans.y * (0.5f / trans.w) + 0.5f)
            return Vec2(x * size, y * size)
        }

        /** ComputeCameraRay() under ([x], [y]) of the square. */
        private fun cameraRay(viewProjection: FloatArray, x: Float, y: Float, size: Float): Pair<Vec4, Vec4> {
            val inverse = inverse(viewProjection)
            val mox = (x / size) * 2f - 1f
            val moy = (1f - (y / size)) * 2f - 1f
            val zNear = 0f
            val zFar = 1f - 1.1920929e-7f
            val origin = Vec4(mox, moy, zNear, 1f).transform(inverse).let { it * (1f / it.w) }
            val end = Vec4(mox, moy, zFar, 1f).transform(inverse).let { it * (1f / it.w) }
            return origin to (end - origin).normalized()
        }

        private fun buildPlan(point: Vec4, normal: Vec4): Vec4 {
            val n = normal.normalized()
            return Vec4(n.x, n.y, n.z, n.dot(point))
        }

        private fun intersectRayPlane(origin: Vec4, vector: Vec4, plan: Vec4): Float {
            val numer = plan.dot(origin) - plan.w
            val denom = plan.dot(vector)
            if (abs(denom) < 1.1920929e-7f) return -1f
            return -(numer / denom)
        }
    }
}

/** ImGuizmo's vec_t, of which the dot products take three components. */
internal data class Vec4(val x: Float, val y: Float, val z: Float, val w: Float = 0f) {
    operator fun plus(v: Vec4) = Vec4(x + v.x, y + v.y, z + v.z, w + v.w)
    operator fun minus(v: Vec4) = Vec4(x - v.x, y - v.y, z - v.z, w - v.w)
    operator fun times(f: Float) = Vec4(x * f, y * f, z * f, w * f)
    fun dot(v: Vec4) = x * v.x + y * v.y + z * v.z
    fun cross(v: Vec4) = Vec4(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    fun normalized(): Vec4 {
        val length = sqrt(dot(this))
        return if (length > 0f) Vec4(x / length, y / length, z / length, w) else this
    }
    fun lerp(v: Vec4, t: Float) = Vec4(x + (v.x - x) * t, y + (v.y - y) * t, z + (v.z - z) * t, w + (v.w - w) * t)

    /** vec_t::TransformVector() */
    fun transformVector(m: FloatArray) = Vec4(
        x * m[0] + y * m[4] + z * m[8],
        x * m[1] + y * m[5] + z * m[9],
        x * m[2] + y * m[6] + z * m[10],
        x * m[3] + y * m[7] + z * m[11],
    )

    /** vec_t::TransformPoint() */
    fun transformPoint(m: FloatArray) = Vec4(
        x * m[0] + y * m[4] + z * m[8] + m[12],
        x * m[1] + y * m[5] + z * m[9] + m[13],
        x * m[2] + y * m[6] + z * m[10] + m[14],
        x * m[3] + y * m[7] + z * m[11] + m[15],
    )

    /** vec_t::Transform(): with its w. */
    fun transform(m: FloatArray) = Vec4(
        x * m[0] + y * m[4] + z * m[8] + w * m[12],
        x * m[1] + y * m[5] + z * m[9] + w * m[13],
        x * m[2] + y * m[6] + z * m[10] + w * m[14],
        x * m[3] + y * m[7] + z * m[11] + w * m[15],
    )

    companion object {
        val ZERO = Vec4(0f, 0f, 0f)
    }
}

internal data class Vec2(val x: Float, val y: Float)

/** A panel of a face on the screen, and whether the finger presses its box. */
internal class NavigatorPanel(val corners: List<Vec2>, val pressed: Boolean)

/** A face's label: where a point of its text, laid out from its top left, goes on the screen. */
internal class NavigatorLabel(val face: Int, val map: (x: Float, y: Float, halfWidth: Float, halfHeight: Float) -> Vec2)

/** An axis from the cube's corner, with where its label goes, dimmed when hidden behind the cube. */
internal class NavigatorAxis(val axis: Int, val start: Vec2, val end: Vec2, val label: Vec2, val visible: Boolean)

internal class NavigatorDrawing(val panels: List<NavigatorPanel>, val labels: List<NavigatorLabel>, val axes: List<NavigatorAxis>)
