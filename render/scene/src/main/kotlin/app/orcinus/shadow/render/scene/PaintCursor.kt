package app.orcinus.shadow.render.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3

/** The cursor a painting tool draws at the finger (GLGizmoPainterBase::render_cursor()), by its cursor type. */
enum class PaintCursorShape { CIRCLE, SPHERE, HEIGHT_RANGE }

/**
 * A painting tool's brush cursor: its [shape], m_cursor_radius and
 * m_cursor_height in millimetres, and whether the stroke paints as the right
 * mouse button would (blocking, or taking fuzzy skin off), which colours it red.
 */
data class PaintCursor(val shape: PaintCursorShape, val radius: Double, val height: Double, val rightButton: Boolean)

/**
 * render_cursor_circle(): the circle of [radius] device pixels about
 * [center], drawn as [steps] arcs of which every other one shows.
 */
internal data class PaintCursorCircle(val center: Offset, val radius: Float, val steps: Int, val rightButton: Boolean)

/** get_cursor_sphere_left_button_color() and get_cursor_sphere_right_button_color(). */
private val LEFT_BUTTON = ColorRgba(0f, 0f, 1f, 0.25f)
private val RIGHT_BUTTON = ColorRgba(1f, 0f, 0f, 0.25f)

/** The cursor's colour while a finger paints, as the mouse button held down colours it. */
internal fun PaintCursor.color(): ColorRgba = if (rightButton) RIGHT_BUTTON else LEFT_BUTTON

/**
 * render_cursor_circle(): as many steps as the camera's zoom ([zoom], desktop
 * pixels per millimetre) asks for, of which the odd ones are left out.
 */
internal fun circleCursorSteps(zoom: Double): Int = (2 * (4 + (252.0 * (zoom - 1.0) / (250.0 - 1.0)).toInt())).coerceAtLeast(2)

/** The dashed circle of a painting tool's cursor, over the 3D view while a finger paints with it. */
@Composable
internal fun PaintCursorOverlay(controller: PlateViewController) {
    val circle by controller.paintCircle.collectAsState()
    val shown = circle ?: return
    val width = with(LocalDensity.current) { CIRCLE_LINE.toPx() }
    val color = (if (shown.rightButton) RIGHT_BUTTON else LEFT_BUTTON).let { Color(it.red, it.green, it.blue, it.alpha) }
    Canvas(Modifier.fillMaxSize()) {
        val sweep = 360f / shown.steps
        val corner = Offset(shown.center.x - shown.radius, shown.center.y - shown.radius)
        val size = Size(2f * shown.radius, 2f * shown.radius)
        for (step in 0 until shown.steps step 2) {
            drawArc(color, startAngle = step * sweep, sweepAngle = sweep, useCenter = false, topLeft = corner, size = size, style = Stroke(width = width))
        }
    }
}

/** glLineWidth(1.5f) of the circle. */
private val CIRCLE_LINE = 1.5.dp

/**
 * render_cursor_sphere(): the sphere of the cursor's radius about [hit] in
 * the world, blended into the scene's depth.
 */
internal fun sphereCursorFrame(cursor: PaintCursor, hit: Vec3): GizmoFrame =
    GizmoFrame(
        lines = emptyList(),
        grabbers = emptyList(),
        sceneFaces = listOf(GizmoFace(GrabberMeshes.sphere.cornerPositions(), cursor.color())),
        sceneFacesWorld = Affine3.assemble(hit, Vec3.ZERO, Vec3(cursor.radius, cursor.radius, cursor.radius)),
    )

/**
 * render_cursor_height_range(): where every volume of the object crosses the
 * height the finger meets the model at, and the cursor's height above it,
 * both kept within [box] (bounding_box(), of the object's parts) and
 * drawn only strictly inside it (update_contours()), in white lines of
 * width 2 in the scene's depth. [volumes] are the meshes placed in the world.
 */
internal fun heightRangeCursorFrame(cursor: PaintCursor, hit: Vec3, box: Box3, volumes: List<Pair<MeshData, Affine3>>, pixelScale: Float): GizmoFrame {
    val minZ = box.min.z
    val maxZ = box.max.z
    val cursorZ = hit.z.coerceIn(minZ, maxZ)
    val zs = listOf(cursorZ, (cursorZ + cursor.height).coerceIn(minZ, maxZ))
    val segments = ArrayList<Float>()
    for (z in zs) {
        if (!(minZ < z && z < maxZ)) continue
        for ((mesh, world) in volumes) contourAt(mesh, world, z, segments)
    }
    return GizmoFrame(
        lines = emptyList(),
        grabbers = emptyList(),
        sceneLines = listOf(GizmoLines(segments.toFloatArray(), ColorRgba(1f, 1f, 1f, 1f), 2f * pixelScale)),
    )
}

/**
 * slice_mesh() of [mesh] placed by [world] at the height [z]: the segment
 * where each triangle crosses it, GL_LINES in the world. A corner at [z]
 * counts as above, so no triangle only touches it.
 */
private fun contourAt(mesh: MeshData, world: Affine3, z: Double, out: MutableList<Float>) {
    val stride = MeshFiles.FLOATS_PER_CORNER
    val vertices = mesh.vertices
    val corners = arrayOfNulls<Vec3>(3)
    for (triangle in 0 until mesh.cornerCount / 3) {
        for (corner in 0 until 3) {
            val at = (triangle * 3 + corner) * stride
            corners[corner] = world.transformPoint(Vec3(vertices.get(at).toDouble(), vertices.get(at + 1).toDouble(), vertices.get(at + 2).toDouble()))
        }
        // A triangle crossing the plane crosses it on two of its edges.
        for (edge in 0 until 3) {
            val a = corners[edge]!!
            val b = corners[(edge + 1) % 3]!!
            val aBelow = a.z < z
            if (aBelow == b.z < z) continue
            val low = if (aBelow) a else b
            val high = if (aBelow) b else a
            val t = (z - low.z) / (high.z - low.z)
            out += (low.x + (high.x - low.x) * t).toFloat()
            out += (low.y + (high.y - low.y) * t).toFloat()
            out += z.toFloat()
        }
    }
}
