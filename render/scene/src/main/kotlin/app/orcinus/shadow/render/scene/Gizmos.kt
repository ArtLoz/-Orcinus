package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** OrcaSlicer's gizmos that the plate view shows on the selected object (GLGizmosManager::EType). */
enum class PlateGizmo {
    MOVE,
    ROTATE,
    SCALE,
    LAY_ON_FACE,
}

/**
 * A line drawn with glLineStipple(1, 0x0FFF), as OrcaSlicer's gizmos draw
 * their connections: 12 pixels drawn and 4 skipped, [pixel] millimetres each,
 * as GL_LINES segments.
 */
internal fun dashedSegments(from: Vec3, to: Vec3, pixel: Double): FloatArray {
    val dash = 12.0 * pixel
    val gap = 4.0 * pixel
    val length = (to - from).norm()
    if (length == 0.0 || dash <= 0.0) return FloatArray(0)
    val direction = (to - from) / length
    val points = ArrayList<Float>()
    var start = 0.0
    while (start < length) {
        val end = minOf(start + dash, length)
        for (point in listOf(from + direction * start, from + direction * end)) {
            points += point.x.toFloat()
            points += point.y.toFloat()
            points += point.z.toFloat()
        }
        start = end + gap
    }
    return points.toFloatArray()
}

/** A gizmo as one frame draws it, in world coordinates. */
internal class GizmoFrame(
    val lines: List<GizmoLines>,
    val grabbers: List<GizmoGrabber>,
    /** Translucent faces drawn in object coordinates with [facesWorld]. */
    val faces: List<GizmoFace> = emptyList(),
    val facesWorld: Affine3 = Affine3(),
    /** Drawn first, over the scene without its depth unless [overlayDepth]: the cut gizmo's section and its outline. */
    val overlay: List<GizmoFace> = emptyList(),
    val overlayDepth: Boolean = false,
    /** Meshes drawn into the scene with its depth before the rest: the cut gizmo's connectors. */
    val sceneMeshes: List<GizmoMesh> = emptyList(),
    /**
     * Translucent faces drawn into the scene, hidden where the objects stand
     * in front of them, from both sides, in [sceneFacesWorld]: the cut plane.
     */
    val sceneFaces: List<GizmoFace> = emptyList(),
    val sceneFacesWorld: Affine3 = Affine3(),
    /** gouraud_light's emission_factor for the grabbers. */
    val emission: Float = 0.1f,
)

/** GL_TRIANGLES of one colour, x, y, z per corner. */
internal class GizmoFace(val triangles: FloatArray, val color: ColorRgba)

/** GL_LINES segments of one colour; [width] is in device pixels. */
internal class GizmoLines(val segments: FloatArray, val color: ColorRgba, val width: Float)

/** A grabber mesh placed in the world. */
internal class GizmoGrabber(val world: Affine3, val color: ColorRgba, val shape: GrabberShape = GrabberShape.CONE)

internal enum class GrabberShape { CONE, CUBE, SPHERE }

/** A mesh of a gizmo placed in the world; [key] names it for the GPU. [emission] null takes the frame's. */
internal class GizmoMesh(val key: String, val mesh: MeshData, val world: Affine3, val color: ColorRgba, val emission: Float? = null)

internal object GizmoColors {
    // ColorRGBA::X(), Y(), Z() in libslic3r/Color.hpp: GLGizmoBase::AXES_COLOR.
    val AXES = listOf(
        ColorRgba(255 / 255f, 60 / 255f, 91 / 255f),
        ColorRgba(100 / 255f, 200 / 255f, 24 / 255f),
        ColorRgba(47 / 255f, 136 / 255f, 233 / 255f),
    )

    // GLGizmoBase::AXES_HOVER_COLOR
    val AXES_HOVER = listOf(
        AXES[0].scaled(1.2f, 1.4f, 1.4f),
        AXES[1].scaled(1.2f, 1.2f, 1.2f),
        AXES[2].scaled(1.2f, 1.2f, 1.2f),
    )

    // GLGizmoBase::DEFAULT_DRAG_COLOR
    val DRAG = ColorRgba(1f, 1f, 1f)

    // The grey of GLGizmoRotate's grabber connection while nothing is dragged.
    val ROTATE_CONNECTION = ColorRgba(0.6f, 0.6f, 0.6f)

    // GLGizmoBase::FLATTEN_COLOR and FLATTEN_HOVER_COLOR
    val FLATTEN = ColorRgba(0.96f, 0.93f, 0.93f, 0.5f)
    val FLATTEN_HOVER = ColorRgba(1f, 1f, 1f, 0.75f)

    // GLGizmoBase::GRABBER_UNIFORM_COL and GRABBER_UNIFORM_HOVER_COL
    val UNIFORM = ColorRgba(0f, 1f, 1f)
    val UNIFORM_HOVER = ColorRgba(0f, 0.7f, 0.7f)

    // GLVolume::NEUTRAL_COLOR: the unpainted facets of the object a painting
    // gizmo of supports, the seam or fuzzy skin draws.
    val NEUTRAL = ColorRgba(0.8f, 0.8f, 0.8f)

    // TriangleSelectorGUI::enforcers_color and blockers_color, which the
    // painting gizmos of supports, the seam and fuzzy skin paint with.
    val ENFORCERS = ColorRgba(0.5f, 1f, 0.5f)
    val BLOCKERS = ColorRgba(1f, 0.5f, 0.5f)

    private fun ColorRgba.scaled(r: Float, g: Float, b: Float) = ColorRgba(red * r, green * g, blue * b, alpha)
}

/** GLGizmoBase::Grabber meshes: its_make_cone(1, 1, PI / 18), apex up the Z axis, and a unit cube about the origin. */
internal object GrabberMeshes {
    val cube: MeshData by lazy {
        // its_make_cube(1, 1, 1) translated by -0.5.
        val corners = floatArrayOf(1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f)
        val indices = intArrayOf(0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7, 0, 4, 7, 0, 7, 1, 1, 7, 6, 1, 6, 2, 2, 6, 5, 2, 5, 3, 4, 0, 3, 4, 3, 5)
        MeshFiles.fromIndexed(FloatArray(corners.size) { corners[it] - 0.5f }, indices)
    }

    /** its_make_sphere(1, PI / 12): GLGizmoCut3D's sphere grabber. */
    val sphere: MeshData by lazy {
        val fa = PI / 12.0
        val sectorCount = kotlin.math.ceil(2.0 * PI / fa).toInt()
        val stackCount = kotlin.math.ceil(PI / fa).toInt()
        val sectorStep = 2.0 * PI / sectorCount
        val stackStep = PI / stackCount
        val positions = ArrayList<Float>()
        for (i in 0..stackCount) {
            // from pi/2 to -pi/2
            val stackAngle = 0.5 * PI - stackStep * i
            val xy = cos(stackAngle)
            val z = sin(stackAngle)
            if (i == 0 || i == stackCount) {
                positions += listOf(xy.toFloat(), 0f, z.toFloat())
            } else {
                for (j in 0 until sectorCount) {
                    val sectorAngle = sectorStep * j
                    positions += listOf((xy * cos(sectorAngle)).toFloat(), (xy * sin(sectorAngle)).toFloat(), z.toFloat())
                }
            }
        }
        val indices = ArrayList<Int>()
        for (i in 0 until stackCount) {
            // Beginning of current stack, and of the next one.
            var k1 = if (i == 0) 0 else 1 + (i - 1) * sectorCount
            val k1First = k1
            var k2 = if (i == 0) 1 else k1 + sectorCount
            val k2First = k2
            for (j in 0 until sectorCount) {
                // 2 triangles per sector excluding first and last stacks
                var k1Next = k1
                var k2Next = k2
                if (i != 0) {
                    k1Next = if (j + 1 == sectorCount) k1First else k1 + 1
                    indices += listOf(k1, k2, k1Next)
                }
                if (i + 1 != stackCount) {
                    k2Next = if (j + 1 == sectorCount) k2First else k2 + 1
                    indices += listOf(k1Next, k2, k2Next)
                }
                k1 = k1Next
                k2 = k2Next
            }
        }
        MeshFiles.fromIndexed(positions.toFloatArray(), indices.toIntArray())
    }

    val cone: MeshData by lazy {
        val fa = PI / 18.0
        val positions = mutableListOf(0f, 0f, 0f, 0f, 0f, 1f)
        val indices = mutableListOf<Int>()
        var i = 0
        var angle = 0.0
        while (angle < 2 * PI) {
            positions += cos(angle).toFloat()
            positions += sin(angle).toFloat()
            positions += 0f
            if (angle > 0.0) {
                indices += listOf(0, i + 2, i + 1)
                indices += listOf(1, i + 1, i + 2)
            }
            ++i
            angle += fa
        }
        indices += listOf(0, 2, i + 1)
        indices += listOf(1, i + 1, 2)
        MeshFiles.fromIndexed(positions.toFloatArray(), indices.toIntArray())
    }
}

/**
 * GLGizmoMove3D: an arrow per axis beyond the selection's bounding box, joined
 * to its centre by a dashed line. [pixel] is the size in millimetres of one
 * desktop pixel at the camera target, which OrcaSlicer's gizmo sizes are in.
 */
internal class MoveGizmo(box: Box3, private val pixel: Double) {
    val center: Vec3 = box.center()
    private val halfSize = box.size() * 0.5

    /** Where the grabber of [axis] stands. */
    fun grabberCenter(axis: Int): Vec3 {
        val space = SPACE_SIZE * pixel
        return when (axis) {
            0 -> center + Vec3(halfSize.x + space, 0.0, 0.0)
            1 -> center + Vec3(0.0, halfSize.y + space, 0.0)
            else -> center + Vec3(0.0, 0.0, halfSize.z + space)
        }
    }

    /** GLGizmoBase::Grabber::render() with the PosZ extension: a cone pointing along the axis. */
    fun grabberWorld(axis: Int): Affine3 {
        val extension = 0.75 * FIXED_GRABBER_SIZE * pixel
        return Affine3.assemble(grabberCenter(axis), GRABBER_ANGLES[axis], Vec3(0.75 * extension, 0.75 * extension, 2.0 * extension))
    }

    /** The grabber's tip, for touching it. */
    fun grabberTip(axis: Int): Vec3 = grabberWorld(axis).transformPoint(Vec3.UNIT_Z)

    /** GLGizmoMove3D::on_render(); [dragged] is the axis being dragged. */
    fun frame(dragged: Int?, pixelScale: Float): GizmoFrame = GizmoFrame(
        lines = (0 until 3).map { axis ->
            // glLineWidth(hover ? 2 : 1.5) with a dashed line.
            GizmoLines(
                segments = dashedSegments(center, grabberCenter(axis), pixel),
                color = GizmoColors.AXES[axis],
                width = (if (dragged != null) 2f else 1.5f) * pixelScale,
            )
        },
        grabbers = (0 until 3).map { axis ->
            GizmoGrabber(grabberWorld(axis), if (axis == dragged) GizmoColors.AXES_HOVER[axis] else GizmoColors.AXES[axis])
        },
    )

    companion object {
        /**
         * GLGizmoMove3D::calc_projection(): how far from [start], the grabber's
         * position when the drag began, the point of [ray] nearest to it lies
         * along the direction from [startCenter], the box centre then.
         */
        fun projection(start: Vec3, startCenter: Vec3, ray: Line3): Double {
            val startingVec = start - startCenter
            if (startingVec.norm() == 0.0) return 0.0
            val direction = ray.unitVector()
            val intersection = ray.a + direction * (start - ray.a).dot(direction)
            return (intersection - start).dot(startingVec.normalized())
        }

        // GLGizmoBase::Grabber::FixedGrabberSize, and the space GLGizmoMove3D leaves beyond the box.
        const val FIXED_GRABBER_SIZE = 16.0
        const val SPACE_SIZE = 20.0

        // GLGizmoMove3D::on_init(): the X arrow turns about Y, the Y arrow about X.
        private val GRABBER_ANGLES = listOf(Vec3(0.0, 0.5 * PI, 0.0), Vec3(-0.5 * PI, 0.0, 0.0), Vec3.ZERO)
    }
}

/**
 * GLGizmoRotate3D: a ring per axis around the selection's bounding sphere,
 * each with a grabber. [pixel] is the size in millimetres of one desktop pixel
 * at the camera target.
 */
internal class RotateGizmo(val center: Vec3, sphereRadius: Double, private val pixel: Double) {
    // GLGizmoRotate::init_data_from_selection(): Offset is in millimetres.
    val radius = OFFSET + sphereRadius

    /** GLGizmoRotate::local_transform() in world coordinates: the ring of [axis] lies in its XY plane. */
    fun ringMatrix(axis: Int): Affine3 = Affine3().translated(center) * LOCAL_ROTATIONS[axis]

    /** The grabber of [axis] turned by [angle]: GLGizmoRotate::on_render(). */
    fun grabberBase(axis: Int, angle: Double): Affine3 {
        val distance = radius * (1.0 + GRABBER_OFFSET)
        return ringMatrix(axis) * Affine3.assemble(Vec3(cos(angle) * distance, sin(angle) * distance, 0.0), Vec3(0.0, 0.0, angle), Vec3(1.0, 1.0, 1.0))
    }

    /** The grabber's ends along its cones, for touching it. */
    fun grabberEnds(axis: Int, angle: Double): Pair<Vec3, Vec3> {
        val base = grabberBase(axis, angle)
        val reach = 3.5 * extension()
        return base.transformPoint(Vec3(0.0, reach, 0.0)) to base.transformPoint(Vec3(0.0, -reach, 0.0))
    }

    /**
     * GLGizmoRotate3D::on_render(): every ring, or only the one being dragged
     * with its scale, snap radii, reference radius, and the arc of [angle].
     */
    fun frame(dragged: Int?, angle: Double, pixelScale: Float): GizmoFrame {
        val lines = ArrayList<GizmoLines>()
        val grabbers = ArrayList<GizmoGrabber>()
        val width = (if (dragged != null) 2f else 1.5f) * pixelScale
        for (axis in 0 until 3) {
            if (dragged != null && dragged != axis) continue
            val ring = ringMatrix(axis)
            val axisAngle = if (dragged == axis) angle else 0.0
            val color = if (dragged != null) GizmoColors.DRAG else GizmoColors.AXES[axis]
            lines += GizmoLines(ring.segments(circle()), color, width)
            if (dragged != null) {
                lines += GizmoLines(ring.segments(scale() + snapRadii() + referenceRadius()), color, width)
                if (axisAngle > 0.0) lines += GizmoLines(ring.segments(angleArc(axisAngle)), GizmoColors.AXES[axis], width)
            }
            val grabberDistance = radius * (1.0 + GRABBER_OFFSET)
            val grabberCenter = Vec3(cos(axisAngle) * grabberDistance, sin(axisAngle) * grabberDistance, 0.0)
            lines += GizmoLines(
                ring.segments(listOf(Vec3.ZERO, grabberCenter)),
                if (dragged != null) GizmoColors.DRAG else GizmoColors.ROTATE_CONNECTION,
                width,
            )
            grabbers += grabber(axis, axisAngle, if (dragged == axis) GizmoColors.AXES_HOVER[axis] else GizmoColors.AXES[axis])
        }
        return GizmoFrame(lines, grabbers)
    }

    /**
     * GLGizmoRotate::on_dragging(): the angle of the point of [ray] in the
     * ring's plane, measured from the ring's X axis and snapped to the coarse
     * regions near the centre or the scale at the rim.
     */
    fun dragAngle(axis: Int, ray: Line3): Double {
        val position = mousePositionInLocalPlane(axis, ray)
        val length = hypot(position.x, position.y)
        if (length == 0.0) return 0.0
        var theta = acos((position.x / length).coerceIn(-1.0, 1.0))
        if (position.y / length < 0.0) theta = 2.0 * PI - theta
        val coarseIn = radius / 3.0
        val fineOut = radius + radius * SCALE_LONG_TOOTH
        if (length in coarseIn..2.0 * coarseIn) {
            val step = 2.0 * PI / SNAP_REGIONS_COUNT
            theta = step * Math.round(theta / step)
        } else if (length in radius..fineOut) {
            val step = 2.0 * PI / SCALE_STEPS_COUNT
            theta = step * Math.round(theta / step)
        }
        return if (theta == 2.0 * PI) 0.0 else theta
    }

    /** GLGizmoRotate::mouse_position_in_local_plane() in world coordinates. */
    private fun mousePositionInLocalPlane(axis: Int, ray: Line3): Vec3 {
        val toLocal = PLANE_ROTATIONS[axis] * Affine3().translated(-center)
        val a = toLocal.transformPoint(ray.a)
        val b = toLocal.transformPoint(ray.b)
        val vector = b - a
        if (abs(vector.z) < EPSILON) {
            // The ray is parallel to the plane of the ring.
            if (abs(vector.y) > 1.0 - EPSILON) return Vec3.UNIT_X
            val world = if (a.x >= 0.0) ray.a - center else ray.b - center
            return (PLANE_ROTATIONS[axis]).transformPoint(world)
        }
        return Line3(a, b).intersectPlane(0.0)
    }

    private fun extension() = 0.75 * MoveGizmo.FIXED_GRABBER_SIZE * pixel

    /** GLGizmoBase::Grabber::render() with the PosY and NegY extensions: a cube with a cone to each side. */
    private fun grabber(axis: Int, angle: Double, color: ColorRgba): List<GizmoGrabber> {
        val base = grabberBase(axis, angle)
        val size = MoveGizmo.FIXED_GRABBER_SIZE * pixel
        val extension = extension()
        val coneScale = Vec3(0.75 * extension, 0.75 * extension, 3.0 * extension)
        return listOf(
            GizmoGrabber(base * Affine3.assemble(Vec3.ZERO, Vec3.ZERO, Vec3(size, size, size)), color, GrabberShape.CUBE),
            GizmoGrabber(base * Affine3.assemble(Vec3(0.0, 2.0 * extension, 0.0), Vec3(-0.5 * PI, 0.0, 0.0), coneScale), color),
            GizmoGrabber(base * Affine3.assemble(Vec3(0.0, -2.0 * extension, 0.0), Vec3(0.5 * PI, 0.0, 0.0), coneScale), color),
        )
    }

    /** render_circle(): a loop of ScaleStepsCount points. */
    private fun circle(): List<Vec3> = (0 until SCALE_STEPS_COUNT).flatMap { i ->
        listOf(onRing(i * SCALE_STEP_RAD, radius), onRing((i + 1) * SCALE_STEP_RAD, radius))
    }

    /** render_scale(): a long tooth every ScaleLongEvery steps. */
    private fun scale(): List<Vec3> {
        val outLong = radius + radius * SCALE_LONG_TOOTH
        val outShort = radius * (1.0 + 0.5 * SCALE_LONG_TOOTH)
        return (0 until SCALE_STEPS_COUNT).flatMap { i ->
            val angle = i * SCALE_STEP_RAD
            listOf(onRing(angle, radius), onRing(angle, if (i % SCALE_LONG_EVERY == 0) outLong else outShort))
        }
    }

    /** render_snap_radii() */
    private fun snapRadii(): List<Vec3> = (0 until SNAP_REGIONS_COUNT).flatMap { i ->
        val angle = i * 2.0 * PI / SNAP_REGIONS_COUNT
        listOf(onRing(angle, radius / 3.0), onRing(angle, 2.0 * radius / 3.0))
    }

    /** render_reference_radius() */
    private fun referenceRadius(): List<Vec3> = listOf(Vec3.ZERO, Vec3(radius * (1.0 + GRABBER_OFFSET), 0.0, 0.0))

    /** render_angle_arc() */
    private fun angleArc(angle: Double): List<Vec3> {
        val step = angle / ANGLE_RESOLUTION
        val arcRadius = radius * (1.0 + GRABBER_OFFSET)
        return (0 until ANGLE_RESOLUTION).flatMap { i -> listOf(onRing(i * step, arcRadius), onRing((i + 1) * step, arcRadius)) }
    }

    private fun onRing(angle: Double, distance: Double) = Vec3(cos(angle) * distance, sin(angle) * distance, 0.0)

    private fun Affine3.segments(points: List<Vec3>): FloatArray {
        val values = FloatArray(points.size * 3)
        points.forEachIndexed { index, point ->
            val world = transformPoint(point)
            values[index * 3] = world.x.toFloat()
            values[index * 3 + 1] = world.y.toFloat()
            values[index * 3 + 2] = world.z.toFloat()
        }
        return values
    }

    companion object {
        // GLGizmoRotate.cpp
        const val OFFSET = 5.0
        const val ANGLE_RESOLUTION = 64
        const val SCALE_STEPS_COUNT = 72
        val SCALE_STEP_RAD = 2.0 * PI / SCALE_STEPS_COUNT
        const val SCALE_LONG_EVERY = 2
        const val SCALE_LONG_TOOTH = 0.1
        const val SNAP_REGIONS_COUNT = 8
        const val GRABBER_OFFSET = 0.15
        private const val EPSILON = 1e-4

        private fun rotation(x: Double, y: Double, z: Double) = Affine3.assemble(Vec3.ZERO, Vec3(x, y, z), Vec3(1.0, 1.0, 1.0))

        // local_transform(): X turns about Y then Z, Y about Z then Y.
        private val LOCAL_ROTATIONS = listOf(
            rotation(0.0, 0.5 * PI, 0.0) * rotation(0.0, 0.0, -0.5 * PI),
            rotation(0.0, 0.0, -0.5 * PI) * rotation(0.0, -0.5 * PI, 0.0),
            Affine3(),
        )

        // mouse_position_in_local_plane(): the inverse turns into the ring's plane.
        private val PLANE_ROTATIONS = listOf(
            rotation(0.0, 0.0, 0.5 * PI) * rotation(0.0, -0.5 * PI, 0.0),
            rotation(0.0, 0.5 * PI, 0.0) * rotation(0.0, 0.0, 0.5 * PI),
            Affine3(),
        )

        /** Selection::rotate() in world coordinates about [pivot]: the rotation of [angle] radians about [axis] applied to [start]. */
        fun rotated(start: Affine3, axis: Int, angle: Double, pivot: Vec3): Affine3 {
            val rotation = when (axis) {
                0 -> rotation(angle, 0.0, 0.0)
                1 -> rotation(0.0, angle, 0.0)
                else -> rotation(0.0, 0.0, angle)
            }
            return Affine3().translated(pivot) * rotation * Affine3().translated(-pivot) * start
        }
    }
}

/**
 * GLGizmoScale3D in world coordinates: grabbers at the sides of the bottom of
 * the selection's bounding box for X and Y, on its top for Z, and at the bottom
 * corners for a uniform scale, joined by dashed lines. [pixel] is the size in
 * millimetres of one desktop pixel at the camera target.
 */
internal class ScaleGizmo(private val box: Box3, private val pixel: Double) {
    val center: Vec3 = box.center()
    private val half = box.size() * 0.5

    /** GLGizmoScale3D::update_grabbers_data(): grabber 4, the bottom centre, is never shown. */
    fun grabberCenter(id: Int): Vec3 = center + when (id) {
        0 -> Vec3(-half.x, 0.0, -half.z)
        1 -> Vec3(half.x, 0.0, -half.z)
        2 -> Vec3(0.0, -half.y, -half.z)
        3 -> Vec3(0.0, half.y, -half.z)
        4 -> Vec3(0.0, 0.0, -half.z)
        5 -> Vec3(0.0, 0.0, half.z)
        6 -> Vec3(-half.x, -half.y, -half.z)
        7 -> Vec3(half.x, -half.y, -half.z)
        8 -> Vec3(half.x, half.y, -half.z)
        else -> Vec3(-half.x, half.y, -half.z)
    }

    fun frame(dragged: Int?, pixelScale: Float): GizmoFrame {
        val width = (if (dragged != null) 2f else 1.5f) * pixelScale
        val size = MoveGizmo.FIXED_GRABBER_SIZE * pixel
        // render_grabbers_connection(): the bottom rectangle in the Y and X colours.
        val lines = listOf(6 to 7, 7 to 8, 8 to 9, 9 to 6).map { (from, to) ->
            GizmoLines(
                dashedSegments(grabberCenter(from), grabberCenter(to), pixel),
                if (from == 6 || from == 7) GizmoColors.AXES[1] else GizmoColors.AXES[0],
                width,
            )
        }
        val grabbers = VISIBLE_GRABBERS.map { id ->
            val hover = id == dragged
            val color = when {
                id >= 6 -> if (hover) GizmoColors.UNIFORM_HOVER else GizmoColors.UNIFORM
                hover -> GizmoColors.AXES_HOVER[id / 2]
                else -> GizmoColors.AXES[id / 2]
            }
            GizmoGrabber(Affine3.assemble(grabberCenter(id), Vec3.ZERO, Vec3(size, size, size)), color, GrabberShape.CUBE)
        }
        return GizmoFrame(lines, grabbers)
    }

    companion object {
        /** Grabbers drawn and touchable: all but the bottom centre. */
        val VISIBLE_GRABBERS = listOf(0, 1, 2, 3, 5, 6, 7, 8, 9)

        /**
         * GLGizmoScale3D::calc_ratio(): how far the drag has moved the grabber,
         * as a ratio of its distance from the bottom centre when the drag
         * began. The point is taken where [ray] meets the plane through the
         * grabber across the Z axis, or for the top grabber the plane along
         * Z facing the view. A ray nearly along that plane keeps the ratio at 1.
         */
        fun ratio(id: Int, dragStart: Vec3, bottomCenter: Vec3, ray: Line3): Double {
            val startingVec = dragStart - bottomCenter
            val length = startingVec.norm()
            if (length == 0.0) return 0.0
            val direction = ray.unitVector()
            var normal = Vec3.UNIT_Z
            if (id == 5) {
                val planeVec = direction.cross(Vec3.UNIT_Z)
                normal = planeVec.cross(Vec3.UNIT_Z)
            }
            normal = normal.normalized()
            val angle = Math.toDegrees(acos(normal.dot(direction).coerceIn(-1.0, 1.0)))
            if (abs(angle) < 95.0 && abs(angle) > 85.0) return 1.0
            // GetIntersectionOfRayAndPlane()
            val t = (normal.dot(dragStart) - normal.dot(ray.a)) / normal.dot(direction)
            val intersection = ray.a + direction * t
            val projection = (intersection - dragStart).dot(startingVec.normalized())
            return (length + projection) / length
        }

        /** Selection::scale() in world coordinates: [scale] per axis about [center] applied to [start]. */
        fun scaled(start: Affine3, scale: Vec3, center: Vec3): Affine3 =
            Affine3().translated(center) * Affine3.assemble(Vec3.ZERO, Vec3.ZERO, scale) * Affine3().translated(-center) * start
    }
}

/**
 * GLGizmoFlatten: the faces the selected object can lie on, which the engine
 * computed, drawn over it; touching one lays the object on it.
 */
internal class LayOnFaceGizmo(planes: List<FlatteningPlane>) {
    /** Each face as a triangle fan of its convex polygon, in object coordinates. */
    private val faces: List<Pair<Vector3, FloatArray>> = planes.map { plane ->
        val points = plane.polygon
        val triangles = FloatArray(maxOf(points.size - 2, 0) * 9)
        for (i in 1 until points.size - 1) {
            listOf(points[0], points[i], points[i + 1]).forEachIndexed { corner, point ->
                val index = (i - 1) * 9 + corner * 3
                triangles[index] = point.x.toFloat()
                triangles[index + 1] = point.y.toFloat()
                triangles[index + 2] = point.z.toFloat()
            }
        }
        plane.normal to triangles
    }

    /** GLGizmoFlatten::on_render(): [pressed] in the hover colour. */
    fun frame(world: Affine3, pressed: Int?): GizmoFrame = GizmoFrame(
        lines = emptyList(),
        grabbers = emptyList(),
        faces = faces.mapIndexed { index, (_, triangles) ->
            GizmoFace(triangles, if (index == pressed) GizmoColors.FLATTEN_HOVER else GizmoColors.FLATTEN)
        },
        facesWorld = world,
    )

    /** The face under [ray] nearest to the eye with the object at [world], and its normal. */
    fun faceAt(world: Affine3, ray: Line3): Pair<Int, Vector3>? {
        val toObject = world.inverse()
        val origin = toObject.transformPoint(ray.a)
        val direction = toObject.transformPoint(ray.b) - origin
        var nearest: Pair<Int, Double>? = null
        faces.forEachIndexed { index, (_, triangles) ->
            for (corner in triangles.indices step 9) {
                val t = rayTriangle(origin, direction, triangles.point(corner), triangles.point(corner + 3), triangles.point(corner + 6)) ?: continue
                if (nearest == null || t < nearest!!.second) nearest = index to t
            }
        }
        return nearest?.let { (index, _) -> index to faces[index].first }
    }

    private fun FloatArray.point(index: Int) = Vec3(this[index].toDouble(), this[index + 1].toDouble(), this[index + 2].toDouble())
}
