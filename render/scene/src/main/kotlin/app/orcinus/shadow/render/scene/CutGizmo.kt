package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.CutPreviewPart
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
    /**
     * The dovetail cut: the plane with its grooves (in the plane's frame), the
     * groove's angle, which shows the grabber moving the plane along its Y
     * axis, and the parts the cut makes, drawn in the object's place.
     */
    val dovetail: Boolean = false,
    val groovePlane: ScenePath? = null,
    val grooveAngle: Double = 0.0,
    /**
     * The pieces drawn in the object's place: the parts of the dovetail cut,
     * or the pieces a long press split the object into (m_part_selection).
     */
    val previewParts: List<CutPreviewPart> = emptyList(),
    /** "Draw cut line" (Shift + drag): the next drag of a finger draws the line the plane is laid across. */
    val drawingLine: Boolean = false,
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

/** The cut line a finger draws (GLGizmoCut3D::process_cut_line()). */
sealed interface CutLineEvent {
    /** The finger went down: the line begins. */
    data object Start : CutLineEvent

    /** The finger let go of the line from [start] to [end], looked at along [direction]. */
    data class Drawn(val start: Vector3, val end: Vector3, val direction: Vector3) : CutLineEvent
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

    /**
     * process_cut_line(): the plane a line from [start] to [end], looked at
     * along [direction], lays across the object — through the line along the
     * view, its centre the bounding box's centre [boundsCenter] brought onto
     * it; null for a line shorter than 3 mm.
     */
    fun byLine(start: Vector3, end: Vector3, direction: Vector3, boundsCenter: Vector3): Transform3? {
        val lineBeg = Vec3(start.x, start.y, start.z)
        val lineEnd = Vec3(end.x, end.y, end.z)
        val lineDir = lineEnd - lineBeg
        if (lineDir.norm() < 3.0) return null
        val dir = Vec3(direction.x, direction.y, direction.z).normalized()
        val crossDir = lineDir.cross(dir).normalized()
        if (!crossDir.isFinite()) return null
        val bbCenter = Vec3(boundsCenter.x, boundsCenter.y, boundsCenter.z)
        val newPlaneCenter = bbCenter + crossDir * crossDir.dot(lineEnd - bbCenter)
        val m = Affine3().translated(newPlaneCenter) * rotationFromTwoVectors(Vec3.UNIT_Z, crossDir)
        return Transform3(m.elements().toList())
    }

    /**
     * process_cut_line()'s check of the new [plane]: the plane's centre from
     * the copy's [instanceOffset], turned into the plane's frame and moved by
     * the centre of the transformed bounding box [min]..[max] the engine gave
     * for it, lies in that box.
     */
    fun lineCutFits(plane: Transform3, instanceOffset: Vector3, min: Vector3, max: Vector3): Boolean {
        val m = affine(plane).withTranslation(Vec3.ZERO)
        val center = center(plane)
        val tbbCenter = Vec3((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
        val transCenterPos = m.inverse().transformPoint(Vec3(center.x - instanceOffset.x, center.y - instanceOffset.y, center.z - instanceOffset.z)) + tbbCenter
        return transCenterPos.x >= min.x && transCenterPos.x <= max.x &&
            transCenterPos.y >= min.y && transCenterPos.y <= max.y &&
            transCenterPos.z >= min.z && transCenterPos.z <= max.z
    }

    /**
     * Eigen's Quaterniond::setFromTwoVectors() as a rotation: the shortest
     * turn of [from] onto [to]; opposite vectors turn half round the X axis.
     */
    private fun rotationFromTwoVectors(from: Vec3, to: Vec3): Affine3 {
        val v0 = from.normalized()
        val v1 = to.normalized()
        val c = v1.dot(v0)
        val w: Double
        val axis: Vec3
        if (c < -1.0 + 1e-12) {
            val w2 = (1.0 + maxOf(c, -1.0)) * 0.5
            w = sqrt(w2)
            axis = Vec3.UNIT_X * sqrt(1.0 - w2)
        } else {
            val s = sqrt((1.0 + c) * 2.0)
            axis = v0.cross(v1) * (1.0 / s)
            w = s * 0.5
        }
        val x = axis.x
        val y = axis.y
        val z = axis.z
        // Column-major 4 x 4 of the quaternion's rotation matrix.
        return Affine3(
            doubleArrayOf(
                1.0 - 2.0 * (y * y + z * z), 2.0 * (x * y + z * w), 2.0 * (x * z - y * w), 0.0,
                2.0 * (x * y - z * w), 1.0 - 2.0 * (x * x + z * z), 2.0 * (y * z + x * w), 0.0,
                2.0 * (x * z + y * w), 2.0 * (y * z - x * w), 1.0 - 2.0 * (x * x + y * y), 0.0,
                0.0, 0.0, 0.0, 1.0,
            ),
        )
    }

    internal fun affine(plane: Transform3) = Affine3(plane.columns.toDoubleArray())

    private const val EPSILON = 1e-9
}

/**
 * The grabbers of GLGizmoCut3D's plane (GrabberID): X and Y turn it, Z and the
 * plane itself move it; for the dovetail cut Z_ROTATION turns it about its
 * normal and X_MOVE and Y_MOVE move it along its own axes.
 */
internal enum class CutGrabber { X, Y, Z, PLANE, Z_ROTATION, X_MOVE, Y_MOVE }

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
     * sectors: corners at 45 degrees), or the rectangle of the dovetail
     * plane; null past its edges.
     */
    fun planeHit(ray: Line3, dovetail: Boolean = false): Vec3? {
        val point = planePoint(ray) ?: return null
        val local = plane.inverse().transformPoint(point)
        val halfX = if (dovetail) 0.5 * planeRadius else planeRadius / sqrt(2.0)
        val halfY = if (dovetail) 0.5 * 1.5 * planeRadius else planeRadius / sqrt(2.0)
        if (abs(local.x) > halfX || abs(local.y) > halfY) return null
        return point
    }

    /** unproject_on_cut_plane() without the contours: where [ray] meets the plane. */
    fun planePoint(ray: Line3): Vec3? {
        val toPlane = plane.inverse()
        val local = Line3(toPlane.transformPoint(ray.a), toPlane.transformPoint(ray.b))
        if (abs((local.b - local.a).z) < 1e-12) return null
        val point = local.intersectPlane(0.0)
        return if (point.isFinite()) plane.transformPoint(point) else null
    }

    /** The dovetail's grabbers (render_cut_plane_grabbers()): where a finger takes them. */
    fun dovetailGrabbers(grooveAngle: Double): List<Pair<CutGrabber, Vec3>> {
        val size = halfSize(false)
        val xyConnection = 0.75 * connectionLength
        return buildList {
            add(CutGrabber.Z_ROTATION to plane.transformPoint(Vec3(0.0, -1.75 * connectionLength, 0.0)))
            add(CutGrabber.X_MOVE to plane.transformPoint(Vec3(xyConnection, 0.0, 0.0)))
            add(CutGrabber.X_MOVE to plane.transformPoint(Vec3(size + xyConnection, 0.0, 0.0)))
            if (grooveAngle > 0.0) {
                add(CutGrabber.Y_MOVE to plane.transformPoint(Vec3(0.0, xyConnection, 0.0)))
                add(CutGrabber.Y_MOVE to plane.transformPoint(Vec3(0.0, size + xyConnection, 0.0)))
            }
        }
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
        frame(dragged, angle, dragStart, canCut, contour, null, emptyList(), emptyMap(), editing = false, lookingForward = true, hovered = null, pixelScale = pixelScale)

    /**
     * on_render() with the connectors: render_connectors(), and while their
     * window is open the section filled in dark grey with its outline, drawn
     * into the scene, without the plane and its grabbers. [lookingForward] is
     * is_looking_forward(), and [hovered] the connector a finger holds. The
     * pieces [parts] stand in the object's place (PartSelection::render()),
     * and [line] is the cut line a finger draws (render_cut_line()), which
     * hides the connectors.
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
        dovetail: CutDovetail? = null,
        parts: List<Pair<CutPreviewPart, MeshData?>> = emptyList(),
        line: Pair<Vec3, Vec3>? = null,
        pixelScale: Float,
    ): GizmoFrame {
        val shownConnectors = if (line != null) emptyList() else connectors
        val sceneMeshes = connectorMeshes(shownConnectors, meshes, editing, lookingForward, hovered) + partMeshes(parts, editing, lookingForward)
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
        return planeFrame(dragged, angle, dragStart, canCut, contour, pixelScale, sceneMeshes, dovetail, line)
    }

    /**
     * PartSelection::render(): the pieces in the colours of the upper and lower
     * parts, modifiers blended; while the connectors' window is open the solid
     * pieces on the camera's side are left out (render(&m_cut_normal)).
     */
    private fun partMeshes(parts: List<Pair<CutPreviewPart, MeshData?>>, editing: Boolean, lookingForward: Boolean): List<GizmoMesh> =
        parts.mapNotNull { (part, mesh) ->
            mesh ?: return@mapNotNull null
            if (!part.modifier && editing && ((lookingForward && part.upper) || (!lookingForward && !part.upper))) return@mapNotNull null
            val color = when {
                part.modifier -> MODIFIER_COLOR
                part.upper -> UPPER_PART_COLOR
                else -> LOWER_PART_COLOR
            }
            GizmoMesh(part.mesh.value, mesh, Affine3(), color, emission = 0f)
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
        dovetail: CutDovetail?,
        line: Pair<Vec3, Vec3>?,
    ): GizmoFrame {
        val lines = ArrayList<GizmoLines>()
        val grabbers = ArrayList<GizmoGrabber>()
        val dragging = dragged != null
        val connectionEnd = sphereCenter()
        val width = 1f * pixelScale
        // render_cut_plane(): the dovetail's plane a little more transparent.
        val planeColor = (if (canCut) CUT_PLANE_DEF_COLOR else CUT_PLANE_ERR_COLOR)
            .let { if (dovetail != null) ColorRgba(it.red, it.green, it.blue, it.alpha - 0.1f) else it }

        // The plane itself hides the rest while it is dragged, and so do the dovetail's own grabbers.
        val noXyDragging = dragged == CutGrabber.PLANE
        if (!noXyDragging && dragged != CutGrabber.Z_ROTATION && dragged != CutGrabber.X_MOVE && dragged != CutGrabber.Y_MOVE) {
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
        if (dovetail != null) dovetailGrabbers(dragged, angle, dragStart, planeColor, dovetail.grooveAngle, pixelScale, lines, grabbers)
        // render_cut_line()
        if (line != null) lines += GizmoLines(segments(listOf(line.first, line.second)), GRABBER_COLOR, width)

        return GizmoFrame(
            lines = lines,
            grabbers = grabbers,
            overlay = listOfNotNull(contour?.let { GizmoFace(it, CONTOUR_COLOR) }),
            sceneFaces = listOf(GizmoFace(dovetail?.planeTriangles ?: planeTriangles(), planeColor)),
            sceneFacesWorld = plane,
            sceneMeshes = sceneMeshes,
            emission = 0.2f,
        )
    }

    /** render_cut_plane_grabbers() of the dovetail cut: the turn about the normal and the moves along X and Y. */
    private fun dovetailGrabbers(
        dragged: CutGrabber?,
        angle: Double,
        dragStart: Affine3?,
        planeColor: ColorRgba,
        grooveAngle: Double,
        pixelScale: Float,
        lines: MutableList<GizmoLines>,
        grabbers: MutableList<GizmoGrabber>,
    ) {
        val dragging = dragged != null
        val noXyGrabber = !dragging
        val width = 1f * pixelScale
        if (noXyGrabber || dragged == CutGrabber.Z_ROTATION) {
            val size = 0.75 * halfSize(dragging)
            val color = GizmoColors.AXES[2]
            val shift = -1.75 * connectionLength
            val sphereColor = if (dragged == CutGrabber.Z_ROTATION) color else planeColor
            grabbers += GizmoGrabber(plane * Affine3.assemble(Vec3(0.0, shift, 0.0), Vec3.ZERO, Vec3(size, size, size)), sphereColor, GrabberShape.SPHERE)
            if (dragged == CutGrabber.Z_ROTATION) {
                val coneScale = Vec3(0.75 * size, 0.75 * size, 1.8 * size)
                lines += rotationSnapping(CutGrabber.Z_ROTATION, dragStart ?: rotation, angle, color, pixelScale)
                lines += GizmoLines(segments(listOf(center, plane.transformPoint(Vec3(0.0, shift, 0.0)))), GRABBER_COLOR, width)
                grabbers += GizmoGrabber(plane * Affine3.assemble(Vec3(1.25 * size, shift, 0.0), Vec3(0.0, 0.5 * PI, 0.0), coneScale), color)
                grabbers += GizmoGrabber(plane * Affine3.assemble(Vec3(-1.25 * size, shift, 0.0), Vec3(0.0, -0.5 * PI, 0.0), coneScale), color)
            }
        }
        val xyConnection = 0.75 * connectionLength
        for (grabber in listOf(CutGrabber.X_MOVE, CutGrabber.Y_MOVE)) {
            val alongX = grabber == CutGrabber.X_MOVE
            if (!alongX && grooveAngle <= 0.0) continue
            if (!(noXyGrabber || dragged == grabber)) continue
            val size = halfSize(dragging)
            val color = if (dragged == grabber) GizmoColors.AXES[if (alongX) 0 else 1] else planeColor
            val axis = if (alongX) Vec3.UNIT_X else Vec3(0.0, 1.0, 0.0)
            lines += GizmoLines(segments(listOf(center, plane.transformPoint(axis * xyConnection))), GRABBER_COLOR, width)
            grabbers += GizmoGrabber(plane * Affine3.assemble(axis * xyConnection, Vec3.ZERO, Vec3(size, size, size)), color, GrabberShape.CUBE)
            val coneScale = Vec3(0.5 * size, 0.5 * size, 1.8 * size)
            val turn = if (alongX) Vec3(0.0, 0.5 * PI, 0.0) else Vec3(-0.5 * PI, 0.0, 0.0)
            grabbers += GizmoGrabber(plane * Affine3.assemble(axis * (size + xyConnection), turn, coneScale), color)
        }
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
        val turn = when (grabber) {
            CutGrabber.X -> Vec3(theta, 0.0, 0.0)
            CutGrabber.Y -> Vec3(0.0, theta, 0.0)
            else -> Vec3(0.0, 0.0, theta)
        }
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
        private val UPPER_PART_COLOR = ColorRgba(0f, 1f, 1f)
        private val LOWER_PART_COLOR = ColorRgba(1f, 0f, 1f)
        private val MODIFIER_COLOR = ColorRgba(0.75f, 0.75f, 0.75f, 0.5f)
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
            CutGrabber.Z_ROTATION to Affine3(),
        )

        // render_rotation_snapping(): where the scale of X and of Y lies.
        private val SNAPPING_FRAMES = mapOf(
            CutGrabber.X to rotation(0.0, 0.5 * PI, 0.0) * rotation(0.0, 0.0, -PI),
            CutGrabber.Y to rotation(0.0, 0.0, -0.5 * PI) * rotation(0.0, -0.5 * PI, 0.0),
            CutGrabber.Z_ROTATION to rotation(0.0, 0.0, -0.5 * PI),
        )
    }
}

/**
 * The dovetail cut as a frame draws it: the plane with its grooves as
 * GL_TRIANGLES corners in the plane's frame, and the groove's angle.
 */
internal class CutDovetail(val planeTriangles: FloatArray?, val grooveAngle: Double)
