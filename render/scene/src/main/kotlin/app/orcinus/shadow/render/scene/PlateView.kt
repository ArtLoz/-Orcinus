package app.orcinus.shadow.render.scene

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OrcaSlicer's 3D plate view: the printer's plate with the objects on it,
 * drawn with OrcaSlicer's shaders and camera.
 *
 * As on OrcaSlicer's canvas, touching an object selects it and dragging it
 * moves it over the plate; touching empty space clears the selection. Holding
 * an object, as a right click does, asks for its context menu. The active
 * [gizmo] shows on the selected object, and dragging its grabbers manipulates
 * the object. One finger elsewhere orbits, two fingers pan and pinch to zoom,
 * and a double tap on empty space returns to the plate view.
 *
 * [plate] is the engine's description of the plate, null until it arrives;
 * [objects] are the plate's objects, painted with the filament colour, and
 * [selectedObject] indexes them. Objects change only while [editable]; a
 * finished manipulation reports the object's new placement to [onPlaceObject],
 * and a held object its index and the finger's position in the view to
 * [onOpenObjectMenu]. A [layer], such as the G-code toolpaths of the preview,
 * is drawn after the bed; the view owns it and releases it when it is replaced.
 */
@Composable
fun PlateView(
    plate: PlateDescription?,
    objects: List<PlateObject>,
    selectedObject: Int?,
    gizmo: PlateGizmo?,
    flatteningPlanes: List<FlatteningPlane>,
    editable: Boolean,
    onSelectObject: (Int?) -> Unit,
    onPlaceObject: (index: Int, placement: Transform3, manipulation: Manipulation) -> Unit,
    onOpenObjectMenu: (index: Int, position: Offset) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    layer: PlateLayer? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val colors = OrcaTheme.colors
    val surface = remember { PlateSurfaceView(context) }
    val controller = surface.controller

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surface.onResume()
                Lifecycle.Event.ON_PAUSE -> surface.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            surface.onPause()
        }
    }

    LaunchedEffect(colors.canvas, colors.isDark, density) {
        controller.setAppearance(colors.canvas, colors.isDark, density)
    }

    val bed by produceState<SceneBed?>(null, plate) {
        value = plate?.let { description ->
            withContext(Dispatchers.IO) { runCatching { SceneLoader.loadBed(description) }.getOrNull() }
        }
    }
    LaunchedEffect(bed) { controller.setBed(bed) }
    LaunchedEffect(layer) { controller.setLayer(layer) }

    val color = plate?.filamentColor ?: DEFAULT_FILAMENT_COLOR
    val meshes = remember { MeshCache() }
    LaunchedEffect(objects, color) {
        val loaded = withContext(Dispatchers.IO) {
            meshes.retain(objects.mapTo(HashSet()) { it.inspection.mesh.value })
            objects.mapIndexedNotNull { index, plateObject ->
                runCatching { SceneLoader.loadObject(index, plateObject, color, meshes) }.getOrNull()
            }
        }
        controller.setObjects(loaded)
    }

    val haptics = LocalHapticFeedback.current
    SideEffect {
        controller.onSelectObject = onSelectObject
        controller.onPlaceObject = onPlaceObject
        controller.onOpenObjectMenu = { index, x, y ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onOpenObjectMenu(index, Offset(x, y))
        }
        controller.setSelection(selectedObject)
        controller.setGizmo(gizmo)
        controller.setFlatteningPlanes(flatteningPlanes)
        controller.setEditable(editable)
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val edgePx = with(LocalDensity.current) { GESTURE_EDGE.toPx() }
    Box(modifier) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(surface) { detectPlateGestures(controller, touchSlop, doubleTapTimeout, longPressTimeout, edgePx) },
        )
    }
}

private val DEFAULT_FILAMENT_COLOR = ColorRgba(0xF2 / 255f, 0x75 / 255f, 0x4E / 255f)

/** Gestures that start this close to the start edge are left to the app's drawer and the system. */
private val GESTURE_EDGE = 20.dp

/**
 * GLCanvas3D::on_mouse() for a touch screen. A finger pressing a gizmo grabber
 * takes it; one pressing an object selects it at once, as the left button
 * does. What the finger holds moves once it travels past the touch slop, so a
 * tap never nudges an object; held in place for a long press, an object opens
 * its context menu instead, as the right button does. A second finger ends the
 * move and pans and zooms instead.
 */
private suspend fun PointerInputScope.detectPlateGestures(
    controller: PlateViewController,
    touchSlop: Float,
    doubleTapTimeoutMillis: Long,
    longPressTimeoutMillis: Long,
    edgePx: Float,
) {
    var lastTapUptime = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        if (down.position.x < edgePx) return@awaitEachGesture
        down.consume()
        val pressedObject = controller.press(down.position.x, down.position.y, touchSlop * GRABBER_TOUCH_SLOPS)
        var positions = mapOf(down.id to down.position)
        var dragging = false
        var multiTouch = false
        var menuOpened = false
        var travelled = 0f
        while (true) {
            val event = if (controller.holdsObject && !dragging && !multiTouch) {
                val remaining = down.uptimeMillis + longPressTimeoutMillis - SystemClock.uptimeMillis()
                withTimeoutOrNull(remaining.coerceAtLeast(0L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                // GLCanvas3D::on_mouse() for a right click: the object's context menu, and the finger no longer moves it.
                menuOpened = controller.openObjectMenu(down.position.x, down.position.y)
                continue
            }
            val pressed = event.changes.filter(PointerInputChange::pressed)
            val current = pressed.associate { it.id to it.position }
            val common = current.keys.intersect(positions.keys)
            if (pressed.size >= 2 && !multiTouch) {
                multiTouch = true
                controller.endMove()
            }
            if (menuOpened) {
                event.changes.forEach(PointerInputChange::consume)
                if (pressed.isEmpty()) break
                continue
            }
            if (common.size == 1 && pressed.size == 1) {
                val id = common.first()
                val position = current.getValue(id)
                val delta = position - positions.getValue(id)
                travelled += delta.getDistance()
                if (dragging || travelled > touchSlop) {
                    when {
                        controller.moving -> controller.moveTo(position.x, position.y)
                        dragging -> controller.rotate(delta.x, delta.y)
                    }
                    dragging = true
                }
            } else if (common.size >= 2) {
                val ids = common.take(2)
                val before = ids.map(positions::getValue)
                val after = ids.map(current::getValue)
                val centroidBefore = (before[0] + before[1]) / 2f
                val centroidAfter = (after[0] + after[1]) / 2f
                val spanBefore = (before[0] - before[1]).getDistance()
                val spanAfter = (after[0] - after[1]).getDistance()
                controller.pan(centroidAfter.x - centroidBefore.x, centroidAfter.y - centroidBefore.y)
                if (spanBefore > 0f && spanAfter > 0f) controller.zoom(spanAfter / spanBefore, centroidAfter.x, centroidAfter.y)
                dragging = true
            }
            event.changes.forEach(PointerInputChange::consume)
            positions = current
            if (pressed.isEmpty()) break
        }
        controller.endMove()

        if (!dragging && !multiTouch && !pressedObject && !menuOpened) {
            controller.clearSelection()
            val now = down.uptimeMillis
            if (now - lastTapUptime <= doubleTapTimeoutMillis) {
                controller.resetView()
                lastTapUptime = 0L
            } else {
                lastTapUptime = now
            }
        }
    }
}

private fun Offset.getDistance() = hypot(x, y)

/** How far from a grabber, in touch slops, a finger still takes it: a fingertip is wider than a mouse pointer. */
private const val GRABBER_TOUCH_SLOPS = 3f

/**
 * The camera and scene on the main thread. Every change recomputes the frame
 * the way GLCanvas3D::render() prepares the camera and hands it to the renderer.
 */
internal class PlateViewController(private val surface: GLSurfaceView, private val renderer: PlateRenderer) {
    private val camera = OrcaCamera()
    private var bed: SceneBed? = null
    private var objects: List<SceneObject> = emptyList()
    private var layer: PlateLayer? = null
    private var layerBox: Box3? = null
    private var selectedIndex: Int? = null
    private var gizmo: PlateGizmo? = null
    private var flatteningPlanes: List<FlatteningPlane> = emptyList()
    private var layOnFace = LayOnFaceGizmo(emptyList())
    private var editable = false
    private var drag: Drag? = null
    private var background = floatArrayOf(0f, 0f, 0f, 1f)
    private var dark = false
    private var density = 1f
    private var framedBed: SceneBed? = null

    var onSelectObject: (Int?) -> Unit = {}
    var onPlaceObject: (Int, Transform3, Manipulation) -> Unit = { _, _, _ -> }
    var onOpenObjectMenu: (Int, Float, Float) -> Unit = { _, _, _ -> }

    /** A press that may become a manipulation of the object [index], which stood at [startWorld]. */
    private sealed class Drag(val index: Int, val startWorld: Affine3) {
        var moved = false
    }

    /** GLCanvas3D::Mouse::Drag: the object itself, touched at [startPosition]. */
    private class ObjectDrag(index: Int, startWorld: Affine3, val startPosition: Vec3) : Drag(index, startWorld)

    /** GLGizmoBase::use_grabbers(): the move gizmo's grabber of [axis] at [startGrabber], the box centre at [startCenter]. */
    private class MoveGrabberDrag(index: Int, startWorld: Affine3, val axis: Int, val startGrabber: Vec3, val startCenter: Vec3) :
        Drag(index, startWorld)

    /** The scale gizmo's grabber [id], scaling about the box [center] as the grabber moves from [start]. */
    private class ScaleGrabberDrag(index: Int, startWorld: Affine3, val id: Int, val start: Vec3, val bottomCenter: Vec3, val center: Vec3) :
        Drag(index, startWorld)

    /** The rotation gizmo's grabber of [axis], turning about the sphere [center] by [angle]. */
    private class RotateGrabberDrag(index: Int, startWorld: Affine3, val axis: Int, val center: Vec3, val sphereRadius: Double) :
        Drag(index, startWorld) {
        var angle = 0.0
    }

    /** Whether a finger holds an object or a grabber it can move. */
    val moving: Boolean get() = drag != null

    /** Whether a finger holds an object it has not moved yet, which a long press turns into its context menu. */
    val holdsObject: Boolean get() = (drag as? ObjectDrag)?.moved == false

    /**
     * Plater::priv::on_right_click() for the object the finger holds: asks for
     * its menu at the point ([x], [y]) and lets the object go. Returns false
     * when the finger holds no object.
     */
    fun openObjectMenu(x: Float, y: Float): Boolean {
        val held = drag as? ObjectDrag ?: return false
        if (held.moved) return false
        drag = null
        onOpenObjectMenu(held.index, x, y)
        return true
    }

    fun setAppearance(canvas: Color, dark: Boolean, density: Float) {
        background = floatArrayOf(canvas.red, canvas.green, canvas.blue, canvas.alpha)
        this.dark = dark
        this.density = density
        invalidate()
    }

    fun setViewport(width: Int, height: Int) {
        camera.setViewport(width, height)
        invalidate()
    }

    fun setBed(bed: SceneBed?) {
        this.bed = bed
        renderer.setBed(bed)
        invalidate()
    }

    fun setLayer(layer: PlateLayer?) {
        if (layer === this.layer) return
        this.layer?.setOnChanged(null)
        this.layer = layer
        layerBox = layer?.bounds?.let { Box3(Vec3(it.min.x, it.min.y, it.min.z), Vec3(it.max.x, it.max.y, it.max.z)) }
        layer?.setOnChanged(surface::requestRender)
        renderer.setLayer(layer)
        invalidate()
    }

    /** New objects replace what a finger was moving: the scene no longer has it where the move began. */
    fun setObjects(objects: List<SceneObject>) {
        drag = null
        showObjects(objects)
    }

    fun setFlatteningPlanes(planes: List<FlatteningPlane>) {
        if (planes == flatteningPlanes) return
        flatteningPlanes = planes
        layOnFace = LayOnFaceGizmo(planes)
        invalidate()
    }

    fun setGizmo(gizmo: PlateGizmo?) {
        if (this.gizmo == gizmo) return
        this.gizmo = gizmo
        if (drag != null && drag !is ObjectDrag) drag = null
        invalidate()
    }

    fun setSelection(index: Int?) {
        if (selectedIndex == index) return
        selectedIndex = index
        invalidate()
    }

    fun setEditable(editable: Boolean) {
        this.editable = editable
    }

    /**
     * GLCanvas3D::on_mouse() for a left press. Gizmo grabbers come first, as
     * they are drawn over the scene: within [grabberRadius] pixels of one, the
     * finger takes it. Otherwise the object under the finger, found as the
     * scene raycaster finds the hovered volume, becomes the selection, and an
     * editable scene gets ready to move it from the touched point. Returns
     * false when the finger is on neither.
     */
    fun press(x: Float, y: Float, grabberRadius: Float): Boolean {
        if (gizmo == PlateGizmo.LAY_ON_FACE && editable) {
            val target = objects.firstOrNull { it.index == selectedIndex }
            val ray = camera.mouseRay(x.toDouble(), y.toDouble())
            val face = if (target != null && ray != null) layOnFace.faceAt(target.world, ray) else null
            if (target != null && face != null) {
                // GLGizmoFlatten::on_mouse(): a press on a face lays the object on it.
                onPlaceObject(target.index, Transform3(target.world.elements().toList()), Manipulation.LayOnFace(face.second))
                return true
            }
        }
        grabberAt(x.toDouble(), y.toDouble(), grabberRadius.toDouble())?.let { (target, axis) ->
            drag = when (gizmo) {
                PlateGizmo.ROTATE -> RotateGrabberDrag(target.index, target.world, axis, target.sphereCenter(), target.sphereRadius)
                PlateGizmo.SCALE -> scaleGizmo(target).let {
                    ScaleGrabberDrag(target.index, target.world, axis, it.grabberCenter(axis), it.grabberCenter(4), it.center)
                }
                else -> moveGizmo(target).let { MoveGrabberDrag(target.index, target.world, axis, it.grabberCenter(axis), it.center) }
            }
            invalidate()
            return true
        }
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
        val (target, hit) = objects
            .mapNotNull { sceneObject -> sceneObject.raycast(ray)?.let { sceneObject to it } }
            .minByOrNull { (_, hit) -> (hit - ray.a).norm() }
            ?: return false
        select(target.index)
        drag = if (editable) ObjectDrag(target.index, target.world, hit) else null
        return true
    }

    /**
     * GLCanvas3D::on_mouse() while dragging a selected object: the object
     * follows the finger in the horizontal plane through the touched point, or
     * in the plane of the screen when the camera looks along the plate.
     */
    fun moveTo(x: Float, y: Float) {
        val drag = drag ?: return
        val target = objects.firstOrNull { it.index == drag.index } ?: return
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return
        if (drag is RotateGrabberDrag) {
            // GLGizmoRotate3D::on_mouse(): the object turns about the sphere's centre by the ring's angle.
            drag.angle = RotateGizmo(drag.center, drag.sphereRadius, pixel()).dragAngle(drag.axis, ray)
            drag.moved = true
            replaceObject(target.withWorld(RotateGizmo.rotated(drag.startWorld, drag.axis, drag.angle, drag.center)))
            return
        }
        if (drag is ScaleGrabberDrag) {
            // GLGizmoScale3D::do_scale_along_axis() and do_scale_uniform().
            val ratio = ScaleGizmo.ratio(drag.id, drag.start, drag.bottomCenter, ray)
            if (ratio <= 0.0 || !ratio.isFinite()) return
            val scale = when (drag.id) {
                0, 1 -> Vec3(ratio, 1.0, 1.0)
                2, 3 -> Vec3(1.0, ratio, 1.0)
                4, 5 -> Vec3(1.0, 1.0, ratio)
                else -> Vec3(ratio, ratio, ratio)
            }
            drag.moved = true
            replaceObject(target.withWorld(ScaleGizmo.scaled(drag.startWorld, scale, drag.center)))
            return
        }
        val offset = when (drag) {
            is RotateGrabberDrag, is ScaleGrabberDrag -> return
            is ObjectDrag -> objectOffset(drag, ray) ?: return
            is MoveGrabberDrag -> {
                // GLGizmoMove3D::on_dragging(): the displacement along the grabber's axis.
                val displacement = MoveGizmo.projection(drag.startGrabber, drag.startCenter, ray)
                if (!displacement.isFinite()) return
                when (drag.axis) {
                    0 -> Vec3(displacement, 0.0, 0.0)
                    1 -> Vec3(0.0, displacement, 0.0)
                    else -> Vec3(0.0, 0.0, displacement)
                }
            }
        }
        drag.moved = true
        replaceObject(target.withWorld(drag.startWorld.withTranslation(drag.startWorld.translation() + offset)))
    }

    private fun objectOffset(drag: ObjectDrag, ray: Line3): Vec3? {
        val start = drag.startPosition
        val direction = ray.b - ray.a
        val position = if (abs(camera.dirForward().z) < EPSILON) {
            // Side view: the point of the ray closest to the start, projected on the camera's axes.
            val intersection = ray.a + direction * ((start - ray.a).dot(direction) / direction.dot(direction))
            val offset = intersection - start
            val right = camera.dirRight()
            val up = camera.dirUp()
            start + right * offset.dot(right) + up * offset.dot(up)
        } else {
            ray.intersectPlane(start.z)
        }
        // Above the horizon the ray meets the plane behind the eye; the object stays where it was.
        if (!position.isFinite() || (position - ray.a).dot(direction) < 0.0) return null
        return position - start
    }

    /**
     * GLCanvas3D::do_move() when the finger lifts: an object above the plate
     * drops onto it unless its auto drop is off, one sunk into the plate stays,
     * and the placement goes to the app. A press that never moved changes nothing.
     */
    fun endMove() {
        val drag = drag ?: return
        this.drag = null
        if (!drag.moved) {
            if (drag !is ObjectDrag) invalidate()
            return
        }
        val target = objects.firstOrNull { it.index == drag.index } ?: return
        val manipulation = when (drag) {
            is RotateGrabberDrag -> Manipulation.Rotate
            is ScaleGrabberDrag -> Manipulation.Scale
            else -> Manipulation.Move
        }
        val shiftZ = target.minZ()
        val drops = target.autoDrop && when (manipulation) {
            Manipulation.Move -> shiftZ > SINKING_Z_THRESHOLD
            // do_rotate(): an object that was not sunk before rests on the plate.
            else -> (target.withWorld(drag.startWorld).minZ() >= SINKING_Z_THRESHOLD || shiftZ > SINKING_Z_THRESHOLD) && shiftZ != 0.0
        }
        val placed = if (drops) {
            target.withWorld(target.world.withTranslation(target.world.translation() - Vec3(0.0, 0.0, shiftZ)))
        } else {
            target
        }
        replaceObject(placed)
        onPlaceObject(placed.index, Transform3(placed.world.elements().toList()), manipulation)
    }

    /** GLCanvas3D::deselect_all() after a click on empty space. */
    fun clearSelection() = select(null)

    /** GLCanvas3D::on_mouse() rotation: desktop pixels map to device-independent pixels. */
    fun rotate(dx: Float, dy: Float) {
        val factor = Math.PI * TRACKBALL_SIZE / 180.0 / density
        // Rotate around the objects on the plate or the toolpaths, or the plate when it is empty.
        val rotationTarget = (objectsBox() ?: layerBox)?.center() ?: bed?.plateBox?.center() ?: camera.target
        camera.rotateOnSphereWithTarget(dx * factor, dy * factor, true, rotationTarget)
        invalidate()
    }

    /**
     * Moves the scene with the fingers. OrcaSlicer pans by the pointer's motion
     * on the near plane; on a touch screen the plate should stay under the
     * fingers, so the motion is taken on the plane through the target, where
     * one pixel spans 1 / zoom millimetres.
     */
    fun pan(dx: Float, dy: Float) {
        val scale = 1.0 / camera.zoom
        camera.setTarget(camera.target - camera.dirRight() * (dx * scale) + camera.dirUp() * (dy * scale))
        invalidate()
    }

    /** Zooms around a point, as GLCanvas3D::on_mouse_wheel() with "zoom to mouse". */
    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        val scale = 1.0 / camera.zoom
        val displacement = camera.dirRight() * ((focusX - camera.viewportWidth / 2.0) * scale) -
            camera.dirUp() * ((focusY - camera.viewportHeight / 2.0) * scale)
        camera.translate(displacement)
        val before = camera.zoom
        camera.setZoom(camera.zoom * factor)
        camera.translate(-displacement / (camera.zoom / before))
        invalidate()
    }

    /** OrcaSlicer's plate view: from the front and above, framing the plate. */
    fun resetView() {
        val bed = bed ?: return
        camera.selectPlateView()
        camera.sceneBox = sceneBox()
        camera.zoomToBox(bed.plateBox, ZOOM_TO_PLATE_MARGIN_FACTOR)
        framedBed = bed
        invalidate()
    }

    /** The grabber of the active gizmo nearest to the point, within [radius] pixels of it on the screen. */
    private fun grabberAt(x: Double, y: Double, radius: Double): Pair<SceneObject, Int>? {
        if (!editable) return null
        val target = objects.firstOrNull { it.index == selectedIndex } ?: return null
        val ends: (Int) -> Pair<Vec3, Vec3> = when (gizmo) {
            PlateGizmo.MOVE -> moveGizmo(target).let { move -> { axis -> move.grabberCenter(axis) to move.grabberTip(axis) } }
            PlateGizmo.ROTATE -> rotateGizmo(target).let { rotate -> { axis -> rotate.grabberEnds(axis, 0.0) } }
            PlateGizmo.SCALE -> scaleGizmo(target).let { scale -> { id -> scale.grabberCenter(id).let { it to it } } }
            PlateGizmo.LAY_ON_FACE, null -> return null
        }
        val grabbers = if (gizmo == PlateGizmo.SCALE) ScaleGizmo.VISIBLE_GRABBERS else listOf(0, 1, 2)
        return grabbers
            .mapNotNull { axis ->
                val (from, to) = ends(axis)
                val base = camera.project(from) ?: return@mapNotNull null
                val tip = camera.project(to) ?: return@mapNotNull null
                axis to distanceToSegment(x, y, base, tip)
            }
            .filter { (_, distance) -> distance <= radius }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (axis, _) -> target to axis }
    }

    /** OrcaSlicer's gizmo sizes are desktop pixels at the camera target. */
    private fun pixel() = density / camera.zoom

    private fun moveGizmo(target: SceneObject) = MoveGizmo(target.bounds, pixel())

    private fun rotateGizmo(target: SceneObject) = RotateGizmo(target.sphereCenter(), target.sphereRadius, pixel())

    private fun scaleGizmo(target: SceneObject) = ScaleGizmo(target.bounds, pixel())

    private fun select(index: Int?) {
        if (selectedIndex == index) return
        selectedIndex = index
        invalidate()
        onSelectObject(index)
    }

    private fun replaceObject(sceneObject: SceneObject) {
        showObjects(objects.map { if (it.index == sceneObject.index) sceneObject else it })
    }

    private fun showObjects(objects: List<SceneObject>) {
        this.objects = objects
        renderer.setObjects(objects)
        invalidate()
    }

    private fun invalidate() {
        val bed = bed
        if (bed != null && framedBed !== bed && camera.viewportWidth > 1) {
            resetView()
            return
        }
        val box = sceneBox() ?: Box3(Vec3(-1.0, -1.0, -1.0), Vec3(1.0, 1.0, 1.0))
        camera.sceneBox = box
        camera.applyProjection(box)
        renderer.setFrame(
            SceneFrame(
                view = camera.viewMatrix,
                projection = FloatArray(16) { camera.projectionMatrix[it].toFloat() },
                nearZ = camera.nearZ.toFloat(),
                farZ = camera.farZ.toFloat(),
                lookingDownward = camera.isLookingDownward(),
                background = background,
                dark = dark,
                pixelScale = density,
                selectedIndex = selectedIndex,
                gizmo = gizmoFrame(),
            ),
        )
        surface.requestRender()
    }

    private fun gizmoFrame(): GizmoFrame? {
        val target = objects.firstOrNull { it.index == selectedIndex } ?: return null
        return when (gizmo) {
            PlateGizmo.MOVE -> moveGizmo(target).frame((drag as? MoveGrabberDrag)?.axis, density)
            PlateGizmo.ROTATE -> {
                // The rings stay where the drag began while the object turns inside them.
                val rotating = drag as? RotateGrabberDrag
                val gizmo = rotating?.let { RotateGizmo(it.center, it.sphereRadius, pixel()) } ?: rotateGizmo(target)
                gizmo.frame(rotating?.axis, rotating?.angle ?: 0.0, density)
            }
            PlateGizmo.SCALE -> scaleGizmo(target).frame((drag as? ScaleGrabberDrag)?.id, density)
            PlateGizmo.LAY_ON_FACE -> layOnFace.frame(target.world, pressed = null)
            null -> null
        }
    }

    private fun objectsBox(): Box3? = objects.map(SceneObject::bounds).reduceOrNull(Box3::merge)

    /**
     * GLCanvas3D::_max_bounding_box(): the objects, the bed and its model, and
     * with a gizmo, room for it around the selection.
     */
    private fun sceneBox(): Box3? {
        val gizmoBox = objects.firstOrNull { it.index == selectedIndex && gizmo != null }?.bounds?.let { selection ->
            val extend = Vec3(1.0, 1.0, 1.0) * selection.maxSize()
            Box3(selection.center() - extend, selection.center() + extend)
        }
        return listOfNotNull(bed?.extendedBox, objectsBox(), layerBox, gizmoBox).reduceOrNull(Box3::merge)
    }

    private companion object {
        // GLCanvas3D.cpp
        const val TRACKBALL_SIZE = 0.8
        const val ZOOM_TO_PLATE_MARGIN_FACTOR = 1.25
        // libslic3r.h and Model.hpp
        const val EPSILON = 1e-4
        const val SINKING_Z_THRESHOLD = -0.001

        fun distanceToSegment(x: Double, y: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dx = b.first - a.first
            val dy = b.second - a.second
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((x - a.first) * dx + (y - a.second) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            return hypot(x - (a.first + t * dx), y - (a.second + t * dy))
        }
    }
}

/** A GLSurfaceView with OpenGL ES 3.0, a depth buffer, and 4x multisampling where available, drawn on demand. */
@SuppressLint("ViewConstructor")
internal class PlateSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = PlateRenderer(context.assets)
    val controller = PlateViewController(this, renderer)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MultisampleConfigChooser())
        holder.setFormat(PixelFormat.RGBA_8888)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        controller.setViewport(width, height)
    }

    override fun onDetachedFromWindow() {
        // Waits for the GL thread to end, which destroys the context.
        super.onDetachedFromWindow()
        renderer.releaseLayers()
    }
}

/** RGBA 8888 with a 24-bit depth buffer, preferring 4 samples like OrcaSlicer's multisampled canvas. */
private class MultisampleConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig =
        choose(egl, display, samples = 4) ?: choose(egl, display, samples = 0)
            ?: error("No OpenGL ES 3.0 configuration with a depth buffer")

    private fun choose(egl: EGL10, display: EGLDisplay, samples: Int): EGLConfig? {
        val attributes = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 24,
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0,
            EGL10.EGL_SAMPLES, samples,
            EGL10.EGL_NONE,
        )
        val count = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)
        return if (egl.eglChooseConfig(display, attributes, configs, 1, count) && count[0] > 0) configs[0] else null
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT = 0x40
    }
}
