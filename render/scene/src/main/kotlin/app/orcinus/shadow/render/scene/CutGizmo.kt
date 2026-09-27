package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GLGizmoCut3D open on a copy, as the plate view shows it: the copy of the
 * object [mesh] names at [instance] of its copies, cut by [plane] (its centre
 * and rotation in world coordinates, as ObjectCut.plane), with the outline of
 * the section in [contour]. [radius] is the radius of the bounding box of the
 * copy's solid parts (m_radius), which the plane and its grabbers are sized
 * by; the plane is drawn in the colour of a cut that cannot be performed
 * unless [canCut].
 */
data class CutView(
    val mesh: ScenePath,
    val instance: Int,
    val plane: Transform3,
    val radius: Double,
    val contour: ScenePath?,
    val canCut: Boolean,
    /** The section itself: a touch on the plane inside it does not turn the plane over. */
    val section: ScenePath? = null,
    /** The object's cut connectors on the plane. */
    val connectors: List<CutConnectorView> = emptyList(),
    /**
     * The connectors' window is open (m_connectors_editing): the plane and its
     * grabbers go, the copy is clipped on the camera's side to show the
     * section, and a touch places, picks and moves the connectors.
     */
    val editingConnectors: Boolean = false,
)

/**
 * A connector as render_connectors() draws it: where it stands on the plane,
 * its radius, depth and turn about the normal in radians, its shape at unit
 * size, and whether it is a dowel, a prism, selected or invalid.
 */
data class CutConnectorView(
    val position: Vector3,
    val radius: Double,
    val height: Double,
    val zAngle: Double,
    val mesh: ScenePath?,
    val dowel: Boolean,
    val prism: Boolean,
    val selected: Boolean,
    val invalid: Boolean,
)

/** What a finger does to the connectors while their window is open (GLGizmoCut3D::gizmo_event()). */
sealed interface CutConnectorEvent {
    /** A touch on the section places a connector there. */
    data class Add(val position: Vector3) : CutConnectorEvent

    /** A touch on a connector selects it alone; a long press adds it to the selection or takes it out ([toggle]). */
    data class Select(val index: Int, val toggle: Boolean) : CutConnectorEvent

    /** A connector dragged over the section; [finished] once the finger let go. */
    data class Move(val index: Int, val position: Vector3, val finished: Boolean) : CutConnectorEvent

    /** A touch elsewhere unselects them all. */
    data object Deselect : CutConnectorEvent
}

/** The changes the cut gizmo and its window make to its plane (GLGizmoCut3D). */
object CutPlanes {
    /** reset_cut_plane(): the plane at [center], not rotated. */
    fun at(center: Vector3): Transform3 = Transform3(Affine3().translated(Vec3(center.x, center.y, center.z)).elements().toList())

    /** m_plane_center */
    fun center(plane: Transform3): Vector3 = Vector3(plane.columns[12], plane.columns[13], plane.columns[14])

    /** m_cut_normal: the rotated Z axis. */
    fun normal(plane: Transform3): Vector3 {
        val z = affine(plane).transformVector(Vec3.UNIT_Z).normalized()
        return Vector3(z.x, z.y, z.z)
    }

    fun withCenter(plane: Transform3, center: Vector3): Transform3 =
        Transform3(plane.columns.toMutableList().also { it[12] = center.x; it[13] = center.y; it[14] = center.z })

    /** Whether [a] and [b] turn alike (m_rotation_m). */
    fun sameRotation(a: Transform3, b: Transform3): Boolean = (0 until 11).all { abs(a.columns[it] - b.columns[it]) < EPSILON }

    /** Whether [plane] is not rotated (m_rotation_m.isApprox(Transform3d::Identity())). */
    fun isUnrotated(plane: Transform3): Boolean = sameRotation(plane, Transform3.IDENTITY)

    /** flip_cut_plane(): the plane turned upside down about its own X axis. */
    fun flipped(plane: Transform3): Transform3 =
        Transform3((affine(plane) * Affine3.assemble(Vec3.ZERO, Vec3(PI, 0.0, 0.0), Vec3(1.0, 1.0, 1.0))).elements().toList())

    /**
     * set_center_pos(center, true): the plane may move its centre to [center]
     * while it still meets, within half a millimetre, the transformed bounding
     * box [min]..[max] the engine gave for [described], a plane turned alike;
     * away from it only towards the centre of the bounding box, [boundsCenter].
     */
    fun mayMoveTo(plane: Transform3, center: Vector3, described: Transform3, min: Vector3, max: Vector3, boundsCenter: Vector3): Boolean {
        if (!sameRotation(plane, described)) return true
        // The box the engine gave moves against the plane, along its normal.
        val from = center(described)
        val offset = affine(plane).withTranslation(Vec3.ZERO).inverse().transformVector(Vec3(from.x - center.x, from.y - center.y, from.z - center.z))
        val limit = 0.5
        if (max.z + offset.z > -limit && min.z + offset.z < limit) return true
        val old = center(plane)
        return distance(boundsCenter, center) < distance(boundsCenter, old)
    }

    private fun distance(a: Vector3, b: Vector3) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

    internal fun affine(plane: Transform3) = Affine3(plane.columns.toDoubleArray())

    private const val EPSILON = 1e-9
}

/** The grabbers of GLGizmoCut3D's plane (GrabberID): X and Y turn it, Z and the plane itself move it. */
internal enum class CutGrabber { X, Y, Z, PLANE }

/**
 * GLGizmoCut3D's plane and grabbers in world coordinates: [plane] places them,
 * [radius] is m_radius, and [pixel] is the size in millimetres of one desktop
 * pixel at the camera target, which the fixed grabbers are sized in.
 */
internal class CutGizmo(val plane: Affine3, radius: Double, private val pixel: Double) {
    val center: Vec3 = plane.translation()
    val rotation: Affine3 = plane.withTranslation(Vec3.ZERO)
    val normal: Vec3 = rotation.transformVector(Vec3.UNIT_Z).normalized()

    // update_bb()
    private val connectionLength = 0.5 * radius
    private val grabberRadius = connectionLength * 0.85
    private val snapCoarseIn = grabberRadius / 3.0
    private val snapCoarseOut = snapCoarseIn * 2.0
    private val snapFineIn = connectionLength * 0.85
    private val snapFineOut = connectionLength * 1.15
    private val planeRadius = CUT_PLANE_RADIUS_KOEF * radius

    // get_grabber_mean_size() with ENABLE_FIXED_GRABBER, get_half_size() and get_dragging_half_size().
    private val meanSize = 32.0 * pixel
    private fun halfSize(dragging: Boolean) = max(meanSize * 0.35, 0.05) * (if (dragging) 1.25 else 1.0)

    /** The sphere grabber at the end of the connection, which moves the plane along its normal. */
    fun sphereCenter(): Vec3 = plane.transformPoint(Vec3(0.0, 0.0, connectionLength))

    /** The cones of the X or Y grabber, both sides of the sphere. */
    fun coneCenters(grabber: CutGrabber, dragging: Boolean = false): List<Vec3> {
        val offset = 1.25 * halfSize(dragging)
        return when (grabber) {
            CutGrabber.X -> listOf(Vec3(0.0, offset, connectionLength), Vec3(0.0, -offset, connectionLength))
            CutGrabber.Y -> listOf(Vec3(offset, 0.0, connectionLength), Vec3(-offset, 0.0, connectionLength))
            else -> emptyList()
        }.map(plane::transformPoint)
    }

    /**
     * Where [ray] meets the plane's square (its_make_frustum_dowel with four
     * sectors: corners at 45 degrees); null past its edges.
     */
    fun planeHit(ray: Line3): Vec3? {
        val toPlane = plane.inverse()
        val local = Line3(toPlane.transformPoint(ray.a), toPlane.transformPoint(ray.b))
        if (abs((local.b - local.a).z) < 1e-12) return null
        val point = local.intersectPlane(0.0)
        val half = planeRadius / sqrt(2.0)
        if (!point.isFinite() || abs(point.x) > half || abs(point.y) > half) return null
        return plane.transformPoint(point)
    }

    /**
     * render_cut_plane(), render_cut_plane_grabbers() and render_clipper_cut():
     * the plane, in the colour of a cut that cannot be performed unless
     * [canCut]; the connection with the sphere and the cones of the grabbers
     * not being dragged away; while X or Y turns the plane, the snapping
     * scale about [dragStart], the rotation the drag began at, with the arc of
     * [angle]. [contour] is the outline of the section, drawn over everything.
     */
    fun frame(dragged: CutGrabber?, angle: Double, dragStart: Affine3?, canCut: Boolean, contour: FloatArray?, pixelScale: Float): GizmoFrame =
        frame(dragged, angle, dragStart, canCut, contour, null, emptyList(), emptyMap(), editing = false, lookingForward = true, hovered = null, pixelScale)

    /**
     * on_render() with the connectors: render_connectors(), and while their
     * window is open the section filled in dark grey with its outline, drawn
     * into the scene, without the plane and its grabbers. [lookingForward] is
     * is_looking_forward(), and [hovered] the connector a finger holds.
     */
    fun frame(
        dragged: CutGrabber?,
        angle: Double,
        dragStart: Affine3?,
        canCut: Boolean,
        contour: FloatArray?,
        section: FloatArray?,
        connectors: List<CutConnectorView>,
        meshes: Map<String, MeshData>,
        editing: Boolean,
        lookingForward: Boolean,
        hovered: Int?,
        pixelScale: Float,
    ): GizmoFrame {
        val sceneMeshes = connectorMeshes(connectors, meshes, editing, lookingForward, hovered)
        if (editing) {
            return GizmoFrame(
                lines = emptyList(),
                grabbers = emptyList(),
                overlay = listOfNotNull(section?.let { GizmoFace(it, SECTION_COLOR) }, contour?.let { GizmoFace(it, CONTOUR_COLOR) }),
                overlayDepth = true,
                sceneMeshes = sceneMeshes,
                emission = 0.2f,
            )
        }
        return planeFrame(dragged, angle, dragStart, canCut, contour, pixelScale, sceneMeshes)
    }

    /** m_clp_normal: the normal towards the camera's side, which the connectors' window clips. */
    fun clippingNormal(lookingForward: Boolean): Vec3 = if (lookingForward) normal else -normal

    /** render_connectors() */
    private fun connectorMeshes(
        connectors: List<CutConnectorView>,
        meshes: Map<String, MeshData>,
        editing: Boolean,
        lookingForward: Boolean,
        hovered: Int?,
    ): List<GizmoMesh> {
        val clpNormal = clippingNormal(lookingForward)
        return connectors.mapIndexedNotNull { index, connector ->
            val key = connector.mesh?.value ?: return@mapIndexedNotNull null
            val mesh = meshes[key] ?: return@mapIndexedNotNull null
            var color = when {
                connector.invalid -> CONNECTOR_ERR_COLOR
                connector.dowel -> DOWEL_COLOR
                else -> PLAG_COLOR
            }
            if (!editing) {
                color = CONNECTOR_ERR_COLOR
            } else if (hovered == index) {
                color = when {
                    connector.invalid -> HOVERED_ERR_COLOR
                    connector.dowel -> HOVERED_DOWEL_COLOR
                    else -> HOVERED_PLAG_COLOR
                }
            } else if (connector.selected) {
                color = if (connector.dowel) SELECTED_DOWEL_COLOR else SELECTED_PLAG_COLOR
            }
            var height = connector.height
            var pos = Vec3(connector.position.x, connector.position.y, connector.position.z)
            if (connector.dowel && connector.prism) {
                if (editing) {
                    height = 0.05
                    if (!lookingForward) pos += clpNormal * 0.05
                } else {
                    pos = if (lookingForward) pos - clpNormal * height else pos + clpNormal * height
                    height *= 2.0
                }
            } else if (!lookingForward) {
                pos += clpNormal * 0.05
            }
            val world = Affine3().translated(pos) * rotation *
                Affine3.assemble(Vec3.ZERO, Vec3(0.0, 0.0, -connector.zAngle), Vec3(connector.radius, connector.radius, height))
            GizmoMesh(key, mesh, world, color)
        }
    }

    private fun planeFrame(
        dragged: CutGrabber?,
        angle: Double,
        dragStart: Affine3?,
        canCut: Boolean,
        contour: FloatArray?,
        pixelScale: Float,
        sceneMeshes: List<GizmoMesh>,
    ): GizmoFrame {
        val lines = ArrayList<GizmoLines>()
        val grabbers = ArrayList<GizmoGrabber>()
        val dragging = dragged != null
        val connectionEnd = sphereCenter()
        val width = 1f * pixelScale

        // The plane itself hides the rest while it is dragged.
        val noXyDragging = dragged == CutGrabber.PLANE
        if (!noXyDragging) {
            lines += GizmoLines(segments(listOf(center, connectionEnd)), GRABBER_COLOR, width)
            val color = when (dragged) {
                CutGrabber.Y -> GizmoColors.AXES[1]
                CutGrabber.X -> GizmoColors.AXES[0]
                CutGrabber.Z -> GRABBER_COLOR
                else -> GRAY
            }
            val size = halfSize(dragging)
            grabbers += GizmoGrabber(Affine3.assemble(connectionEnd, Vec3.ZERO, Vec3(size, size, size)), color, GrabberShape.SPHERE)
        }
        val noXyGrabber = !dragging
        for (grabber in listOf(CutGrabber.X, CutGrabber.Y)) {
            if (!noXyGrabber && dragged != grabber) continue
            val axis = if (grabber == CutGrabber.X) 0 else 1
            val color = GizmoColors.AXES[axis]
            val size = halfSize(dragged == grabber)
            val coneScale = Vec3(0.75 * size, 0.75 * size, 1.8 * size)
            if (dragged == grabber) {
                lines += GizmoLines(segments(listOf(center, connectionEnd)), color, width)
                lines += rotationSnapping(grabber, dragStart ?: rotation, angle, color, pixelScale)
            }
            val offset = 1.25 * size
            val cones = if (grabber == CutGrabber.X) {
                listOf(
                    Vec3(0.0, offset, connectionLength) to Vec3(-0.5 * PI, 0.0, 0.0),
                    Vec3(0.0, -offset, connectionLength) to Vec3(0.5 * PI, 0.0, 0.0),
                )
            } else {
                listOf(
                    Vec3(offset, 0.0, connectionLength) to Vec3(0.0, 0.5 * PI, 0.0),
                    Vec3(-offset, 0.0, connectionLength) to Vec3(0.0, -0.5 * PI, 0.0),
                )
            }
            for ((position, turn) in cones) {
                grabbers += GizmoGrabber(plane * Affine3.assemble(position, turn, coneScale), color)
            }
        }

        return GizmoFrame(
            lines = lines,
            grabbers = grabbers,
            overlay = listOfNotNull(contour?.let { GizmoFace(it, CONTOUR_COLOR) }),
            sceneFaces = listOf(GizmoFace(planeTriangles(), if (canCut) CUT_PLANE_DEF_COLOR else CUT_PLANE_ERR_COLOR)),
            sceneFacesWorld = plane,
            sceneMeshes = sceneMeshes,
            emission = 0.2f,
        )
    }

    /**
     * dragging_grabber_rotation(): the rotation [grabber] turns the plane to
     * from [start], the rotation when the drag began, following [ray], with the
     * angle it turned by: snapped to the coarse regions near the centre or the
     * scale at the rim.
     */
    fun dragRotation(grabber: CutGrabber, start: Affine3, ray: Line3): Pair<Affine3, Double> {
        val position = mousePositionInLocalPlane(grabber, start, ray)
        val length = hypot(position.x, position.y)
        var theta = if (length == 0.0) 0.0 else acos((position.x / length).coerceIn(-1.0, 1.0))
        if (length != 0.0 && position.y < 0.0) theta = 2.0 * PI - theta
        if (length in snapCoarseIn..snapCoarseOut) {
            val step = 2.0 * PI / SNAP_REGIONS_COUNT
            theta = step * round(theta / step)
        } else if (length in snapFineIn..snapFineOut) {
            val step = 2.0 * PI / SCALE_STEPS_COUNT
            theta = step * round(theta / step)
        }
        if (abs(theta - 2.0 * PI) < 1e-9) theta = 0.0
        if (grabber != CutGrabber.Y) theta += 0.5 * PI
        val turn = if (grabber == CutGrabber.X) Vec3(theta, 0.0, 0.0) else Vec3(0.0, theta, 0.0)
        var angle = theta
        while (angle > 2.0 * PI) angle -= 2.0 * PI
        if (angle < 0.0) angle += 2.0 * PI
        return start * Affine3.assemble(Vec3.ZERO, turn, Vec3(1.0, 1.0, 1.0)) to angle
    }

    /** mouse_position_in_local_plane(): [ray] in the plane the grabber turns in, about the plane's centre. */
    private fun mousePositionInLocalPlane(grabber: CutGrabber, start: Affine3, ray: Line3): Vec3 {
        val toLocal = LOCAL_PLANES.getValue(grabber) * start.inverse() * Affine3().translated(-center)
        return Line3(toLocal.transformPoint(ray.a), toLocal.transformPoint(ray.b)).intersectPlane(0.0)
    }

    /**
     * render_rotation_snapping(): the circle, its scale, the snap radii and the
     * reference radius in white, with the arc of [angle] in the grabber's colour.
     */
    private fun rotationSnapping(grabber: CutGrabber, start: Affine3, angle: Double, color: ColorRgba, pixelScale: Float): List<GizmoLines> {
        val frame = Affine3().translated(center) * start * SNAPPING_FRAMES.getValue(grabber)
        val thin = 1f * pixelScale
        val circle = (0 until SCALE_STEPS_COUNT).flatMap { i ->
            listOf(onRing(i * SCALE_STEP_RAD, grabberRadius), onRing((i + 1) * SCALE_STEP_RAD, grabberRadius))
        }
        val outLong = grabberRadius * (1.0 + SCALE_LONG_TOOTH)
        val outShort = grabberRadius * (1.0 + 0.5 * SCALE_LONG_TOOTH)
        val scale = (0 until SCALE_STEPS_COUNT).flatMap { i ->
            val a = i * SCALE_STEP_RAD
            listOf(onRing(a, grabberRadius), onRing(a, if (i % SCALE_LONG_EVERY == 0) outLong else outShort))
        }
        val snapRadii = (0 until SNAP_REGIONS_COUNT).flatMap { i ->
            val a = i * 2.0 * PI / SNAP_REGIONS_COUNT
            listOf(onRing(a, grabberRadius / 3.0), onRing(a, 2.0 * grabberRadius / 3.0))
        }
        val reference = listOf(Vec3.ZERO, Vec3(connectionLength, 0.0, 0.0))
        val snapping = GizmoLines(segments((circle + scale + snapRadii + reference).map(frame::transformPoint)), WHITE, thin)
        val step = angle / ANGLE_RESOLUTION
        val arc = (0 until ANGLE_RESOLUTION).flatMap { i -> listOf(onRing(i * step, connectionLength), onRing((i + 1) * step, connectionLength)) }
        return listOf(snapping, GizmoLines(segments(arc.map(frame::transformPoint)), color, 1.5f * pixelScale))
    }

    /** its_make_frustum_dowel(1.5 radius, 0.02 of the mean grabber size, 4) in the plane's coordinates. */
    private fun planeTriangles(): FloatArray {
        val height = 0.02 * meanSize
        val middle = (0 until 4).map { j ->
            val angle = 2.0 * PI / 4 * j + 0.25 * PI
            Vec3(planeRadius * cos(angle), planeRadius * sin(angle), 0.0)
        }
        val top = Vec3(0.0, 0.0, height)
        val bottom = Vec3(0.0, 0.0, -height)
        val triangles = ArrayList<Vec3>()
        for (j in 0 until 4) {
            val next = (j + 1) % 4
            triangles += listOf(top, middle[j], middle[next])
            triangles += listOf(middle[j], bottom, middle[next])
        }
        return FloatArray(triangles.size * 3) { index ->
            val point = triangles[index / 3]
            when (index % 3) {
                0 -> point.x
                1 -> point.y
                else -> point.z
            }.toFloat()
        }
    }

    private fun onRing(angle: Double, distance: Double) = Vec3(cos(angle) * distance, sin(angle) * distance, 0.0)

    private fun segments(points: List<Vec3>): FloatArray = FloatArray(points.size * 3) { index ->
        val point = points[index / 3]
        when (index % 3) {
            0 -> point.x
            1 -> point.y
            else -> point.z
        }.toFloat()
    }

    companion object {
        // GLGizmoCut.cpp
        private val GRABBER_COLOR = ColorRgba(1f, 1f, 0f)
        private val GRAY = ColorRgba(0.5f, 0.5f, 0.5f)
        private val WHITE = ColorRgba(1f, 1f, 1f)
        private val CUT_PLANE_DEF_COLOR = ColorRgba(0.9f, 0.9f, 0.9f, 0.5f)
        private val CUT_PLANE_ERR_COLOR = ColorRgba(1f, 0.8f, 0.8f, 0.5f)
        // ObjectClipper::render_cut(): the contour in white, the filled cut in dark grey.
        private val CONTOUR_COLOR = ColorRgba(1f, 1f, 1f)
        private val SECTION_COLOR = ColorRgba(0.25f, 0.25f, 0.25f)
        private val PLAG_COLOR = ColorRgba(1f, 1f, 0f)
        private val DOWEL_COLOR = ColorRgba(0.5f, 0.5f, 0f)
        private val HOVERED_PLAG_COLOR = ColorRgba(0f, 1f, 1f)
        private val HOVERED_DOWEL_COLOR = ColorRgba(0f, 0.5f, 0.5f)
        private val SELECTED_PLAG_COLOR = ColorRgba(0.5f, 0.5f, 0.5f)
        private val SELECTED_DOWEL_COLOR = ColorRgba(0.25f, 0.25f, 0.25f)
        private val CONNECTOR_ERR_COLOR = ColorRgba(1f, 0.3f, 0.3f, 0.5f)
        private val HOVERED_ERR_COLOR = ColorRgba(1f, 0.3f, 0.3f, 1f)
        private const val CUT_PLANE_RADIUS_KOEF = 1.5
        private const val ANGLE_RESOLUTION = 64
        private const val SCALE_STEPS_COUNT = 72
        private val SCALE_STEP_RAD = 2.0 * PI / SCALE_STEPS_COUNT
        private const val SCALE_LONG_EVERY = 2
        private const val SCALE_LONG_TOOTH = 0.1
        private const val SNAP_REGIONS_COUNT = 8

        private fun rotation(x: Double, y: Double, z: Double) = Affine3.assemble(Vec3.ZERO, Vec3(x, y, z), Vec3(1.0, 1.0, 1.0))

        // mouse_position_in_local_plane(): X turns about Z then Y, Y about Y then Z.
        private val LOCAL_PLANES = mapOf(
            CutGrabber.X to rotation(0.0, 0.0, 0.5 * PI) * rotation(0.0, -0.5 * PI, 0.0),
            CutGrabber.Y to rotation(0.0, 0.5 * PI, 0.0) * rotation(0.0, 0.0, 0.5 * PI),
        )

        // render_rotation_snapping(): where the scale of X and of Y lies.
        private val SNAPPING_FRAMES = mapOf(
            CutGrabber.X to rotation(0.0, 0.5 * PI, 0.0) * rotation(0.0, 0.0, -PI),
            CutGrabber.Y to rotation(0.0, 0.0, -0.5 * PI) * rotation(0.0, -0.5 * PI, 0.0),
        )
    }
}
