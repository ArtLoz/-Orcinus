package app.orcinus.shadow.render.scene

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintState
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** The wipe tower of the plate, drawn when the plate prints with several filaments. */
    wipeTower: WipeTower? = null,
    /** The colour of every filament of the plate, which the tower takes its own from. */
    filamentColors: List<ColorRgba> = emptyList(),
    /** The tower the last slice built, which replaces the estimated box. */
    builtWipeTower: ScenePath? = null,
    /** GLCanvas3D::WipeTowerInfo::apply_wipe_tower(): the tower was dragged to that corner. */
    onMoveWipeTower: (x: Double, y: Double) -> Unit = { _, _ -> },
    /** The painting tool open on an object: a finger on it paints instead of moving it. */
    painting: PaintingView? = null,
    /** A stroke of the finger, as a ray in world coordinates. */
    /** [starts] is true for the first touch of a stroke. */
    onPaint: (origin: Vector3, direction: Vector3, starts: Boolean) -> Unit = { _, _, _ -> },
    /** The cut gizmo open on a copy: the view shows that copy alone, cut by the plane with its grabbers. */
    cut: CutView? = null,
    /** The plane a grabber moved or turned it to; [finished] once the finger let go. */
    onCutPlane: (plane: Transform3, finished: Boolean) -> Unit = { _, _ -> },
    /** A tap on the plane outside the section: flip_cut_plane(). */
    onFlipCutPlane: () -> Unit = {},
    /** What a finger does to the connectors while their window is open. */
    onCutConnector: (CutConnectorEvent) -> Unit = {},
    /** A long press with the cut gizmo open (a right click): the piece under the finger goes to the other part. */
    onCutPart: (origin: Vector3, direction: Vector3) -> Unit = { _, _ -> },
    /** The cut line a finger draws while "Draw cut line" is on. */
    onCutLine: (CutLineEvent) -> Unit = {},
    /** GLGizmoBase::INV_ZOOM as it changes: millimetres per desktop pixel at the camera's target. */
    onPixelSize: (Double) -> Unit = {},
    selectedObject: Int?,
    /** Every selected object, which the scene draws as selected; the tools work on a single one. */
    selectedObjects: Set<Int> = setOfNotNull(selectedObject),
    gizmo: PlateGizmo?,
    flatteningPlanes: List<FlatteningPlane>,
    /** The meshes drawn with the edges of their triangles over them. */
    wireframes: Set<ScenePath> = emptySet(),
    editable: Boolean,
    onSelectObject: (Int?) -> Unit,
    onPlaceObject: (index: Int, placement: Transform3, manipulation: Manipulation) -> Unit,
    onOpenObjectMenu: (index: Int, position: Offset) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    layer: PlateLayer? = null,
    /** A finger held on empty space: the canvas's menu there (MenuFactory::default_menu), at that position; null for none. */
    onOpenPlateMenu: ((position: Offset) -> Unit)? = null,
    /** Where every plate stands, in their order (PartPlateList); the objects stand among them. */
    plateOrigins: List<Point2> = listOf(Point2(0.0, 0.0)),
    /** The plate the view works on, which the bed model stands under and the objects are judged by. */
    currentPlate: Int = 0,
    /** A tap on another plate, which selects it (Plater::select_plate_by_hover_id); null where plates are not picked. */
    onSelectPlate: ((Int) -> Unit)? = null,
    /** Another current plate turns the view to it, as the preview's plate bar does (Plater::select_sliced_plate). */
    followCurrentPlate: Boolean = false,
    /** What the view writes over every plate (PartPlate::generate_plate_name_texture). */
    plateNames: List<String> = emptyList(),
    /** The Preferences' orbit speed multiplier (camera_orbit_mult). */
    orbitSpeed: Double = 1.0,
    /** The Preferences' "Use free camera" (use_free_camera): the finger turns the view freely, not about the vertical. */
    freeCamera: Boolean = false,
    /** The Preferences' "Zoom to mouse position" (zoom_to_mouse): a pinch zooms towards the fingers, not the view's centre. */
    zoomToFingers: Boolean = false,
    /** The Preferences' multisampling (opengl_antialiasing_samples); unsupported counts fall back to none. */
    antialiasingSamples: Int = 4,
    /** The Preferences' graphics: FXAA, the FPS cap and the FPS overlay. */
    graphics: PlateGraphics = PlateGraphics(),
    /** The View menu's projection, axes and grid. */
    options: PlateViewOptions = PlateViewOptions(),
    /** Camera::set_type() by Auto Perspective, which OrcaSlicer.conf keeps (use_perspective_camera). */
    onPerspectiveChange: (Boolean) -> Unit = {},
    /** The View menu's commands to the camera: its views and the zoom button. */
    camera: PlateViewCamera? = null,
) {
    // OpenGLManager::create_wxglcanvas(): the samples are chosen with the
    // surface, so another count builds the view anew.
    key(antialiasingSamples) {
        val context = LocalContext.current
        val density = LocalDensity.current.density
        val colors = OrcaTheme.colors
        val surface = remember { PlateSurfaceView(context, antialiasingSamples) }
        val controller = surface.controller
        LaunchedEffect(orbitSpeed) { controller.orbitSpeed = orbitSpeed }
        LaunchedEffect(graphics.fxaa) { controller.setFxaa(graphics.fxaa) }
        LaunchedEffect(options) { controller.setOptions(options) }
        SideEffect { controller.onPerspectiveChange = onPerspectiveChange }
        DisposableEffect(camera, controller) {
            camera?.controller = controller
            onDispose { if (camera?.controller === controller) camera.controller = null }
        }
        LaunchedEffect(graphics.fpsCap) { surface.fpsCap = graphics.fpsCap }
        val fps by controller.fps.collectAsState()
        LaunchedEffect(freeCamera, zoomToFingers) {
            controller.freeCamera = freeCamera
            controller.zoomToFingers = zoomToFingers
        }

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
        LaunchedEffect(plateOrigins, currentPlate, plateNames) { controller.setPlates(plateOrigins, currentPlate, followCurrentPlate, plateNames) }
        LaunchedEffect(layer) { controller.setLayer(layer) }

        val color = plate?.filamentColor ?: DEFAULT_FILAMENT_COLOR
        val meshes = remember { MeshCache() }
        // The tower stands on the current plate, where wipe_tower_x and wipe_tower_y of the plate put it.
        val towerOrigin = plateOrigins.getOrElse(currentPlate) { Point2(0.0, 0.0) }
        LaunchedEffect(wipeTower, filamentColors, builtWipeTower, towerOrigin) {
            val tower = withContext(Dispatchers.IO) { wipeTower?.let { SceneLoader.loadWipeTower(it, filamentColors, builtWipeTower, towerOrigin) } }
            controller.setWipeTower(tower)
        }
        val paintedByTool = painting?.takeIf { it.kind != PaintKind.COLOR }?.mesh
        LaunchedEffect(objects, color, filamentColors, wireframes, paintedByTool) {
            val loaded = withContext(Dispatchers.IO) {
                meshes.retain(
                    objects.flatMapTo(HashSet()) { plateObject ->
                        listOf(plateObject.mesh.value) +
                            plateObject.parts.map { part -> part.mesh.value } +
                            plateObject.paintedMeshes.map { painted -> painted.mesh.value }
                    },
                )
                // The scene draws every copy of every object, numbered in the
                // plate's order, as the app's selection counts them.
                var index = 0
                objects.flatMap { plateObject ->
                    plateObject.instances.flatMap { instance ->
                        // The copy itself is picked and moved; its parts carry its
                        // own index, so they are picked and moved with it.
                        val copyIndex = index++
                        // GLVolumeCollection::update_colors_by_extruder(): every
                        // volume is drawn in the colour of the filament it prints
                        // with; a part without one of its own takes its object's.
                        val objectColor = filamentColors.getOrNull(plateObject.extruderNumber - 1) ?: color
                        // GLGizmoPainterBase: a painting tool of another kind than
                        // colour draws the model parts of its object itself, the
                        // facets it has not painted in the neutral colour.
                        val byTool = plateObject.mesh == paintedByTool
                        val copy = runCatching { SceneLoader.loadObject(copyIndex, plateObject, instance, objectColor, meshes) }.getOrNull()
                            ?.let { it.withWireframe(instance.inspection.mesh in wireframes) }
                            ?.let { if (byTool) it.paintedByTool(GizmoColors.NEUTRAL) else it }
                        val parts = plateObject.parts.mapNotNull { part ->
                            val extruder = part.settings.extruderNumber.takeIf { it > 0 } ?: plateObject.extruderNumber
                            val partColor = filamentColors.getOrNull(extruder - 1) ?: color
                            runCatching { SceneLoader.loadPart(copyIndex, part, instance, partColor, meshes) }.getOrNull()
                                ?.let { it.withWireframe(part.mesh in wireframes) }
                                ?.let { if (byTool && part.type == VolumeType.PART) it.paintedByTool(GizmoColors.NEUTRAL) else it }
                        }
                        // The colours the object is painted with, over its surface, or
                        // the paint of the open painting tool of another kind.
                        val painted = plateObject.paintedMeshes.mapNotNull { mesh ->
                            val paint = when (mesh.kind) {
                                PaintKind.COLOR -> filamentColors.getOrNull(mesh.state - 1) ?: color
                                else -> if (mesh.state == PaintState.BLOCKER) GizmoColors.BLOCKERS else GizmoColors.ENFORCERS
                            }
                            runCatching { SceneLoader.loadPaintedMesh(copyIndex, mesh, instance, paint, meshes) }.getOrNull()
                                ?.let { if (byTool) it.paintedByTool(paint) else it }
                        }
                        listOfNotNull(copy) + parts + painted
                    }
                }
            }
            controller.setObjects(loaded)
        }

        // GLGizmoCut3D: the outline and the section of the plane, which the
        // engine writes for every position of it.
        LaunchedEffect(cut?.contour, cut?.section) {
            val contour = cut?.contour
            val section = cut?.section
            val loaded = withContext(Dispatchers.IO) {
                listOf(contour, section).map { path -> path?.let { runCatching { MeshFiles.read(java.io.File(it.value)).cornerPositions() }.getOrNull() } }
            }
            controller.setCutMeshes(loaded[0], loaded[1])
        }
        // The dovetail's plane, and the pieces shown in the object's place.
        val dovetailPaths = listOfNotNull(cut?.groovePlane?.value) + cut?.previewParts?.map { it.mesh.value }.orEmpty()
        LaunchedEffect(dovetailPaths) {
            val loaded = withContext(Dispatchers.IO) {
                dovetailPaths.mapNotNull { path -> runCatching { MeshFiles.read(java.io.File(path)) }.getOrNull()?.let { path to it } }.toMap()
            }
            controller.setCutPartMeshes(loaded)
        }
        // The shapes of the cut's connectors.
        val connectorMeshPaths = cut?.connectors?.mapNotNull { it.mesh?.value }?.distinct().orEmpty()
        LaunchedEffect(connectorMeshPaths) {
            val loaded = withContext(Dispatchers.IO) {
                connectorMeshPaths.mapNotNull { path -> runCatching { MeshFiles.read(java.io.File(path)) }.getOrNull()?.let { path to it } }.toMap()
            }
            controller.setCutConnectorMeshes(loaded)
        }
        // The copy the cut gizmo is open on, numbered as the scene numbers the copies.
        val cutIndex = cut?.let { open ->
            var index = 0
            var found: Int? = null
            for (plateObject in objects) {
                for (instance in plateObject.instances.indices) {
                    if (plateObject.mesh == open.mesh && instance == open.instance) found = index
                    index++
                }
            }
            found
        }

        val haptics = LocalHapticFeedback.current
        SideEffect {
            controller.onSelectObject = onSelectObject
            controller.onSelectPlate = onSelectPlate
            controller.onPlaceObject = onPlaceObject
            controller.onMoveWipeTower = onMoveWipeTower
            controller.onPaint = { ray, starts ->
                val direction = ray.b - ray.a
                onPaint(Vector3(ray.a.x, ray.a.y, ray.a.z), Vector3(direction.x, direction.y, direction.z), starts)
            }
            controller.onCutPlane = { plane, finished -> onCutPlane(Transform3(plane.elements().toList()), finished) }
            controller.onFlipCutPlane = onFlipCutPlane
            controller.onCutConnector = onCutConnector
            controller.onCutPart = { ray ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val direction = ray.b - ray.a
                onCutPart(Vector3(ray.a.x, ray.a.y, ray.a.z), Vector3(direction.x, direction.y, direction.z))
            }
            controller.onCutLine = onCutLine
            controller.onPixelSize = onPixelSize
            controller.setCut(cut, cutIndex)
            controller.setPainting(painting != null)
            controller.setVerticalOnly(painting?.verticalOnly == true)
            // GLGizmoFdmSupports::on_opening() turns the slope on; the painting's
            // highlight angle sets it (-cos of m_highlight_by_angle_threshold_deg).
            controller.setSlope(painting?.takeIf { it.kind == PaintKind.SUPPORTS }?.let { -cos(Math.toRadians(it.overhangAngle)).toFloat() })
            controller.onOpenObjectMenu = { index, x, y ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenObjectMenu(index, Offset(x, y))
            }
            controller.onOpenPlateMenu = onOpenPlateMenu?.let { open ->
                { x, y ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    open(Offset(x, y))
                }
            }
            controller.setSelection(selectedObject)
            controller.setSelected(selectedObjects)
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
            // GLCanvas3D::_render_fps_overlay(): nothing until the first second is measured.
            if (graphics.fpsOverlay && fps >= 0) {
                BasicText(
                    text = "FPS: $fps",
                    style = OrcaTheme.typography.body12.copy(color = Color.White),
                    modifier = Modifier
                        .align(graphics.fpsAlignment)
                        .padding(graphics.fpsPadding)
                        .background(Color.Black.copy(alpha = FPS_BACKGROUND_ALPHA), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

private val DEFAULT_FILAMENT_COLOR = ColorRgba(0xF2 / 255f, 0x75 / 255f, 0x4E / 255f)

/**
 * The View menu's settings of the view (OrcaSlicer.conf): [perspective]
 * (use_perspective_camera), [autoPerspective] (auto_perspective), [axes]
 * (show_axes) and [gridlines] (show_plate_gridlines).
 */
data class PlateViewOptions(
    val perspective: Boolean = true,
    val autoPerspective: Boolean = false,
    val axes: Boolean = true,
    val gridlines: Boolean = true,
)

/**
 * The View menu's commands to the camera of the [PlateView] it is given to:
 * a view of the camera, "Default View", and the canvas's zoom button.
 */
class PlateViewCamera {
    /** The view's controller while it is shown, which the 3D navigator follows. */
    internal var controller: PlateViewController? by mutableStateOf(null)

    /** GLCanvas3D::select_view() */
    fun selectView(view: CameraView) {
        controller?.selectView(view)
    }

    /** The View menu's "Default View": the plate view framing the plate. */
    fun defaultView() {
        controller?.defaultView()
    }

    /** The zoom button of the canvas toolbar: the plate view framing the selection, or the plate. */
    fun zoomToFit() {
        controller?.zoomToFit()
    }
}

/** A [PlateViewCamera] for as long as the page is shown. */
@Composable
fun rememberPlateViewCamera(): PlateViewCamera = remember { PlateViewCamera() }

/** Camera::select_view()'s auto_type(): the side views are orthographic, the others in perspective. */
private val CameraView.prefersPerspective: Boolean
    get() = this == CameraView.ISO || this == CameraView.TOP_FRONT || this == CameraView.PLATE

/** ImGui::SetNextWindowBgAlpha(0.35f) of the FPS overlay. */
private const val FPS_BACKGROUND_ALPHA = 0.35f

/**
 * The Preferences' graphics (the Graphics tab): [fxaa] (opengl_fxaa_enabled),
 * [fpsCap] (opengl_fps_cap, 0 for none) and [fpsOverlay]
 * (opengl_show_fps_overlay), which the page places at [fpsAlignment] with
 * [fpsPadding], clear of its own controls.
 */
data class PlateGraphics(
    val fxaa: Boolean = false,
    val fpsCap: Int = 0,
    val fpsOverlay: Boolean = false,
    val fpsAlignment: Alignment = Alignment.TopEnd,
    val fpsPadding: PaddingValues = PaddingValues(10.dp),
)

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
        var longPressed = false
        var travelled = 0f
        while (true) {
            // A finger held still on an object, or on empty space, asks for a menu.
            val holding = (controller.holdsObject || !pressedObject) && !dragging && !multiTouch && !longPressed
            val event = if (holding) {
                val remaining = down.uptimeMillis + longPressTimeoutMillis - SystemClock.uptimeMillis()
                withTimeoutOrNull(remaining.coerceAtLeast(0L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                longPressed = true
                // GLCanvas3D::on_mouse() for a right click: the object's context
                // menu, and the finger no longer moves it; over empty space the
                // canvas's own menu.
                menuOpened = if (pressedObject) {
                    controller.openObjectMenu(down.position.x, down.position.y)
                } else {
                    controller.openPlateMenu(down.position.x, down.position.y)
                }
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
            // The connectors' window: a touch on the section places a connector, elsewhere unselects them.
            if (controller.isCutting) controller.tapCut(down.position.x, down.position.y)
            // A painting tool and the cut gizmo keep their object while the finger turns the camera around it.
            if (!controller.isPainting && !controller.isCutting) {
                controller.clearSelection()
                // A tap on another plate selects it.
                controller.selectPlateAt(down.position.x, down.position.y)
            }
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
    /** The objects of the plate alone; the scene also draws the wipe tower. */
    private var plateObjects: List<SceneObject> = emptyList()
    private var wipeTower: SceneObject? = null
    /** A painting tool is open, so a finger on the object paints (GLGizmoPainterBase). */
    private var painting = false

    val isPainting: Boolean get() = painting
    private var paintingStroke = false

    /** GLGizmoCut3D open on the copy at [cutIndex], which the scene shows alone. */
    private var cut: CutView? = null
    private var cutIndex: Int? = null
    private var cutContour: FloatArray? = null
    private var cutSection: FloatArray? = null
    val isCutting: Boolean get() = cut != null
    var onCutPlane: (Affine3, Boolean) -> Unit = { _, _ -> }
    var onFlipCutPlane: () -> Unit = {}
    var onCutConnector: (CutConnectorEvent) -> Unit = {}
    var onCutPart: (Line3) -> Unit = {}
    var onCutLine: (CutLineEvent) -> Unit = {}
    var onPixelSize: (Double) -> Unit = {}
    private var reportedPixel = 0.0
    private var cutConnectorMeshes: Map<String, MeshData> = emptyMap()
    private var cutPartMeshes: Map<String, MeshData> = emptyMap()
    private var layer: PlateLayer? = null
    private var layerBox: Box3? = null
    private var selectedIndex: Int? = null
    private var selectedIndexes: Set<Int> = emptySet()
    private var gizmo: PlateGizmo? = null
    private var flatteningPlanes: List<FlatteningPlane> = emptyList()
    private var layOnFace = LayOnFaceGizmo(emptyList())
    private var editable = false
    private var drag: Drag? = null
    private var background = floatArrayOf(0f, 0f, 0f, 1f)
    private var dark = false
    private var density = 1f
    private var framedBed: SceneBed? = null
    private var plates = ScenePlates.SINGLE

    var onSelectObject: (Int?) -> Unit = {}
    var onMoveWipeTower: (Double, Double) -> Unit = { _, _ -> }
    var onPaint: (Line3, starts: Boolean) -> Unit = { _, _ -> }
    var onPlaceObject: (Int, Transform3, Manipulation) -> Unit = { _, _, _ -> }
    var onOpenObjectMenu: (Int, Float, Float) -> Unit = { _, _, _ -> }
    var onOpenPlateMenu: ((Float, Float) -> Unit)? = null
    /** A tap on another plate, with its index; null where plates are not picked. */
    var onSelectPlate: ((Int) -> Unit)? = null

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

    /**
     * GLGizmoCut3D's [grabber], or the plane itself, pressed at [startPoint]
     * while the plane stood at [startPlane]; the plane it has taken, and for a
     * rotation the angle it turned by.
     */
    private class CutDrag(index: Int, val grabber: CutGrabber, val startPlane: Affine3, val startPoint: Vec3) : Drag(index, startPlane) {
        var plane: Affine3 = startPlane
        var angle = 0.0
    }

    /** A connector of the cut, held at [index], where the finger dragged it last. */
    private class CutConnectorDrag(index: Int, val connector: Int, start: Affine3) : Drag(index, start) {
        var position: Vec3? = null
    }

    /**
     * The cut line (m_line_beg and m_line_end), from where the finger went down
     * to where it is, a millimetre past the near plane, and the direction the
     * finger's ray looks along there.
     */
    private class CutLineDrag(index: Int, start: Affine3, val begin: Vec3) : Drag(index, start) {
        var end: Vec3 = begin
        var direction: Vec3 = Vec3.UNIT_Z
    }

    /** The rotation gizmo's grabber of [axis], turning about the sphere [center] by [angle]. */
    private class RotateGrabberDrag(index: Int, startWorld: Affine3, val axis: Int, val center: Vec3, val sphereRadius: Double) :
        Drag(index, startWorld) {
        var angle = 0.0
    }

    /**
     * Whether a finger holds an object or a grabber it can move, or paints a
     * stroke that follows it, instead of orbiting the camera.
     */
    val moving: Boolean get() = drag != null || paintingStroke

    /** Whether a finger holds an object it has not moved yet, which a long press turns into its context menu. */
    val holdsObject: Boolean
        get() = (drag as? ObjectDrag)?.moved == false || (drag as? CutConnectorDrag)?.moved == false ||
            (drag as? CutDrag)?.let { it.grabber == CutGrabber.PLANE && !it.moved } == true

    /** GLGizmoCut3D::on_mouse() takes a right click on the pieces of a planar cut outside the connectors' window. */
    private val selectsCutParts: Boolean get() = cut?.let { !it.editingConnectors && !it.dovetail && !it.drawingLine } == true

    /**
     * Plater::priv::on_right_click() for the object the finger holds: asks for
     * its menu at the point ([x], [y]) and lets the object go. Returns false
     * when the finger holds no object.
     */
    fun openObjectMenu(x: Float, y: Float): Boolean {
        (drag as? CutDrag)?.takeIf { it.grabber == CutGrabber.PLANE && !it.moved }?.let {
            // A right click on the plane: the piece under it (m_part_selection.toggle_selection()).
            drag = null
            invalidate()
            return selectCutPart(x, y)
        }
        (drag as? CutConnectorDrag)?.takeIf { !it.moved }?.let { held ->
            // A connector held still joins the selection or leaves it, as a click with Shift or Alt does.
            drag = null
            onCutConnector(CutConnectorEvent.Select(held.connector, toggle = true))
            return true
        }
        val held = drag as? ObjectDrag ?: return false
        if (held.moved) return false
        drag = null
        onOpenObjectMenu(held.index, x, y)
        return true
    }

    /**
     * Plater::priv::on_right_click() over empty space: the canvas's menu at the
     * point ([x], [y]). Returns false when the view has none.
     */
    fun openPlateMenu(x: Float, y: Float): Boolean {
        if (cut != null) return selectCutPart(x, y)
        val open = onOpenPlateMenu ?: return false
        // A right click on a plate selects it first.
        selectPlateAt(x, y)
        open(x, y)
        return true
    }

    /** GLGizmoCut3D::on_mouse() for a right click: the pieces, the one under the point ([x], [y]) turned over. */
    private fun selectCutPart(x: Float, y: Float): Boolean {
        if (!selectsCutParts) return false
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
        onCutPart(ray)
        return true
    }

    /**
     * GLCanvas3D::on_mouse() for a click on a plate (m_hover_plate_idxs): the
     * plate under the point ([x], [y]) becomes the current one.
     */
    fun selectPlateAt(x: Float, y: Float) {
        val select = onSelectPlate ?: return
        val bed = bed ?: return
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return
        val direction = ray.b - ray.a
        if (abs(direction.z) < 1e-12) return
        val t = -ray.a.z / direction.z
        if (t < 0.0) return
        val point = ray.a + direction * t
        val area = bed.buildVolume
        val index = plates.origins.indexOfFirst { origin ->
            point.x >= area.min.x + origin.x && point.x <= area.max.x + origin.x &&
                point.y >= area.min.y + origin.y && point.y <= area.max.y + origin.y
        }
        if (index >= 0 && index != plates.current) select(index)
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

    /**
     * The plates at [origins], with the one at [current] current. After a
     * plate joins them, the view frames them all
     * (Plater::priv::on_action_add_plate: REQUIRES_ZOOM_TO_ALL_PLATE); when
     * the view [follows] the current plate, another one turns it to that plate.
     */
    fun setPlates(origins: List<Point2>, current: Int, follows: Boolean, names: List<String>) {
        val added = origins.size > plates.origins.size
        val moved = current != plates.current
        plates = ScenePlates(origins.map { Vec3(it.x, it.y, 0.0) }, current, names)
        renderer.setPlates(plates)
        val bed = bed
        if (bed != null && framedBed === bed && camera.viewportWidth > 1) {
            if (added) {
                camera.sceneBox = sceneBox()
                allPlatesBox()?.let { camera.zoomToBox(it, ZOOM_TO_PLATE_MARGIN_FACTOR) }
            } else if (follows && moved) {
                currentPlateBox()?.let { camera.selectPlateView(it.center()) }
            }
        }
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
        plateObjects = objects
        showObjects(plateObjects + listOfNotNull(wipeTower))
    }

    /**
     * The cut gizmo on the copy at [index], or none. While it is open the scene
     * shows that copy alone (InstancesHider and toggle_model_objects_visibility).
     */
    fun setCut(cut: CutView?, index: Int?) {
        if (this.cut == cut && cutIndex == index) return
        val opened = (this.cut == null) != (cut == null) || cutIndex != index
        this.cut = cut
        cutIndex = index
        if (cut == null) {
            cutContour = null
            cutSection = null
        }
        if (opened) {
            if (drag is CutDrag || cut != null) drag = null
            showObjects(plateObjects + listOfNotNull(wipeTower))
        } else if (cut?.previewParts.orEmpty().isNotEmpty() != this.shownPreview) {
            showObjects(plateObjects + listOfNotNull(wipeTower))
        } else {
            invalidate()
        }
    }

    fun setCutPartMeshes(meshes: Map<String, MeshData>) {
        cutPartMeshes = meshes
        // The object gives way to its parts once they are there (toggle_model_objects_visibility()).
        showObjects(plateObjects + listOfNotNull(wipeTower))
    }

    fun setCutConnectorMeshes(meshes: Map<String, MeshData>) {
        cutConnectorMeshes = meshes
        invalidate()
    }

    /** The outline and the section of the cut plane, as GL_TRIANGLES corners. */
    fun setCutMeshes(contour: FloatArray?, section: FloatArray?) {
        cutContour = contour
        cutSection = section
        invalidate()
    }

    fun setPainting(value: Boolean) {
        if (painting == value) return
        painting = value
        paintingStroke = false
        drag = null
    }

    /** The wipe tower stands in the scene beside the objects, and is picked and moved like one. */
    fun setWipeTower(tower: SceneObject?) {
        if (wipeTower?.key == tower?.key && wipeTower?.world == tower?.world && wipeTower?.color == tower?.color) return
        wipeTower = tower
        showObjects(plateObjects + listOfNotNull(tower))
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

    fun setSelected(indexes: Set<Int>) {
        if (selectedIndexes == indexes) return
        selectedIndexes = indexes
        invalidate()
    }

    fun setSelection(index: Int?) {
        if (selectedIndex == index) return
        selectedIndex = index
        invalidate()
    }

    /** The support painting tool's overhang highlight, slope.normal_z; null while it is closed. */
    private var slopeNormalZ: Float? = null

    /** The seam tool's "Vertical", and the screen column the stroke started in. */
    private var verticalOnly = false
    private var strokeX = 0f

    fun setVerticalOnly(vertical: Boolean) {
        verticalOnly = vertical
    }

    fun setSlope(normalZ: Float?) {
        if (slopeNormalZ == normalZ) return
        slopeNormalZ = normalZ
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
        if (cut != null) return pressCut(x.toDouble(), y.toDouble(), grabberRadius.toDouble())
        if (painting) {
            // GLGizmoPainterBase::gizmo_event(): a press on the painted object
            // starts a stroke there, and the engine finds the triangle under
            // it; off the object the finger turns the camera, as the gizmo
            // leaves the mouse to the canvas.
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
            if (objects.none { it.index == selectedIndex && it.raycast(ray) != null }) return false
            paintingStroke = true
            strokeX = x
            onPaint(ray, true)
            return true
        }
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
        val (volume, hit) = objects
            .mapNotNull { sceneObject -> sceneObject.raycast(ray)?.let { sceneObject to it } }
            .minByOrNull { (_, hit) -> (hit - ray.a).norm() }
            ?: return false
        // A part of an object is picked with the object it belongs to, as the
        // desktop canvas moves the instance, not the volume.
        val target = objects.firstOrNull { it.index == volume.index } ?: volume
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
        if (paintingStroke) {
            // The brush follows the finger, as the desktop gizmo paints while
            // the left button is held; "Vertical" keeps the finger's x where
            // the stroke started (_mouse_position.x() = m_last_mouse_click.x()).
            val column = if (verticalOnly) strokeX else x
            camera.mouseRay(column.toDouble(), y.toDouble())?.let { onPaint(it, false) }
            return
        }
        val drag = drag ?: return
        if (drag is CutDrag) {
            camera.mouseRay(x.toDouble(), y.toDouble())?.let { dragCut(drag, it) }
            return
        }
        if (drag is CutLineDrag) {
            // process_cut_line() while the finger moves: the line follows it.
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return
            drag.direction = ray.unitVector()
            drag.end = ray.a + drag.direction
            drag.moved = true
            invalidate()
            return
        }
        if (drag is CutConnectorDrag) {
            // dragging_connector(): the connector follows the finger over the section.
            val point = camera.mouseRay(x.toDouble(), y.toDouble())?.let(::cutPlaneHit) ?: return
            if (!insideSection(point)) return
            drag.moved = true
            drag.position = point
            onCutConnector(CutConnectorEvent.Move(drag.connector, Vector3(point.x, point.y, point.z), finished = false))
            return
        }
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
        if (paintingStroke) {
            paintingStroke = false
            return
        }
        val drag = drag ?: return
        this.drag = null
        if (drag is CutConnectorDrag) {
            val position = drag.position
            if (drag.moved && position != null) {
                onCutConnector(CutConnectorEvent.Move(drag.connector, Vector3(position.x, position.y, position.z), finished = true))
            } else {
                onCutConnector(CutConnectorEvent.Select(drag.connector, toggle = false))
            }
            invalidate()
            return
        }
        if (drag is CutLineDrag) {
            // process_cut_line() for the left button let go: the plane is laid across the line.
            onCutLine(
                CutLineEvent.Drawn(
                    Vector3(drag.begin.x, drag.begin.y, drag.begin.z),
                    Vector3(drag.end.x, drag.end.y, drag.end.z),
                    Vector3(drag.direction.x, drag.direction.y, drag.direction.z),
                ),
            )
            invalidate()
            return
        }
        if (drag is CutDrag) {
            // on_stop_dragging(): the plane is where the drag left it; a click on
            // the plane outside the section turns it over (on_mouse()).
            when {
                drag.moved -> onCutPlane(drag.plane, true)
                drag.grabber == CutGrabber.PLANE && !insideSection(drag.startPoint) -> onFlipCutPlane()
            }
            invalidate()
            return
        }
        if (!drag.moved) {
            if (drag !is ObjectDrag) invalidate()
            return
        }
        val target = objects.firstOrNull { it.index == drag.index } ?: return
        if (target.index == WIPE_TOWER_INDEX) {
            // apply_wipe_tower(): the tower keeps to the plate, so only its
            // corner on the current plate is written back.
            val corner = target.world.translation() - plates.currentOrigin
            onMoveWipeTower(corner.x, corner.y)
            return
        }
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

    /** camera_orbit_mult of the Preferences. */
    var orbitSpeed = 1.0

    /** GLCanvas3D::m_render_stats: the frames per second, -1 before the first measure. */
    private val fpsState = MutableStateFlow(-1)
    val fps: StateFlow<Int> = fpsState.asStateFlow()

    init {
        renderer.onFps = { value -> fpsState.value = value }
    }

    /** opengl_fxaa_enabled, which takes effect at once. */
    fun setFxaa(enabled: Boolean) {
        if (renderer.fxaa == enabled) return
        renderer.fxaa = enabled
        invalidate()
    }

    /** use_free_camera and zoom_to_mouse of the Preferences. */
    var freeCamera = false
    var zoomToFingers = false

    /** GLCanvas3D::on_mouse() rotation: desktop pixels map to device-independent pixels. */
    fun rotate(dx: Float, dy: Float) {
        val factor = Math.PI * TRACKBALL_SIZE / 180.0 / density * orbitSpeed
        val rotX = dx * factor
        val rotY = dy * factor
        when {
            // The painting gizmos turn about the painted object, past the limits, whatever the camera.
            painting -> {
                val box = objects.firstOrNull { it.index == selectedIndex }?.bounds ?: objectsBox()
                camera.rotateOnSphereWithTarget(rotX, rotY, false, box?.center() ?: Vec3.ZERO)
            }
            // Virtual track ball (similar to the 3DConnexion mouse).
            freeCamera -> camera.rotateLocalAroundTarget(Vec3(rotY, rotX, 0.0))
            else -> {
                // The constrained camera keeps its right vector parallel to the plate.
                camera.recoverFromFreeCamera()
                // Rotate around the objects on the plate or the toolpaths, or the plate when it is empty.
                val rotationTarget = (objectsBox() ?: layerBox)?.center() ?: currentPlateBox()?.center() ?: camera.target
                camera.rotateOnSphereWithTarget(rotX, rotY, true, rotationTarget)
            }
        }
        invalidate()
    }

    /**
     * Moves the scene with the fingers. OrcaSlicer pans by the pointer's motion
     * on the near plane; on a touch screen the plate should stay under the
     * fingers, so the motion is taken on the plane through the target, where
     * one pixel spans 1 / zoom millimetres.
     */
    fun pan(dx: Float, dy: Float) {
        // A constrained camera is levelled before it pans.
        if (!freeCamera) camera.recoverFromFreeCamera()
        val scale = 1.0 / camera.zoom
        camera.setTarget(camera.target - camera.dirRight() * (dx * scale) + camera.dirUp() * (dy * scale))
        invalidate()
    }

    /**
     * GLCanvas3D::on_mouse_wheel(): zooms around the view's centre, or with
     * "Zoom to mouse position" around the point between the fingers.
     */
    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        if (!zoomToFingers) {
            camera.setZoom(camera.zoom * factor)
            invalidate()
            return
        }
        val scale = 1.0 / camera.zoom
        val displacement = camera.dirRight() * ((focusX - camera.viewportWidth / 2.0) * scale) -
            camera.dirUp() * ((focusY - camera.viewportHeight / 2.0) * scale)
        camera.translate(displacement)
        val before = camera.zoom
        camera.setZoom(camera.zoom * factor)
        camera.translate(-displacement / (camera.zoom / before))
        invalidate()
    }

    /** The View menu's projection, axes and grid, and Camera::m_prevent_auto_type. */
    private var options = PlateViewOptions()
    private var preventAutoType = false
    var onPerspectiveChange: (Boolean) -> Unit = {}

    fun setOptions(value: PlateViewOptions) {
        options = value
        // Plater::priv::apply_free_camera_correction(): the type of OrcaSlicer.conf.
        setType(value.perspective, fromConfig = true)
        invalidate()
    }

    /** Camera::set_type(): a type of its own keeps Auto Perspective from switching to perspective. */
    private fun setType(perspective: Boolean, fromConfig: Boolean = false) {
        if (camera.orthographic != perspective) return
        camera.orthographic = !perspective
        preventAutoType = true
        // m_update_config_on_type_change_enabled: OrcaSlicer.conf follows every change.
        if (!fromConfig) onPerspectiveChange(perspective)
    }

    /** Camera::auto_type(), with Auto Perspective on. */
    private fun autoType(perspective: Boolean) {
        if (!options.autoPerspective) return
        if (perspective) {
            if (!preventAutoType) {
                setType(true)
                preventAutoType = false
            }
        } else {
            setType(false)
            preventAutoType = false
        }
    }

    /** The camera's view rotation in the 3D navigator's space, as ImGuizmo::ViewManipulate() takes it. */
    private val navigatorViewState = MutableStateFlow(ViewNavigator.viewOf(camera.viewRotationRows()))
    val navigatorView: StateFlow<FloatArray> = navigatorViewState.asStateFlow()

    /**
     * GLCanvas3D::_render_3d_navigator() once the navigator changed the view:
     * the camera turns to [view]; dragging the cube turns it to perspective,
     * a click on the middle of a face ([clickedBox]) to orthographic and on
     * another box back to perspective (Camera::auto_type()).
     */
    fun turnFromNavigator(view: FloatArray, dragging: Boolean, clickedBox: Int) {
        camera.setRotation(ViewNavigator.rotationOf(view))
        when {
            dragging -> autoType(true)
            clickedBox >= 0 -> autoType(clickedBox !in ViewNavigator.FACE_BOXES)
        }
        invalidate()
    }

    /** GLCanvas3D::select_view() */
    fun selectView(view: CameraView) {
        camera.selectView(view)
        autoType(view.prefersPerspective)
        invalidate()
    }

    /** "Default View": the plate view, framing the plate (zoom_to_bed()). */
    fun defaultView() {
        selectView(CameraView.PLATE)
        zoomToBed()
    }

    /** The canvas's zoom button: the plate view, framing the selection or, with none, the plate. */
    fun zoomToFit() {
        selectView(CameraView.PLATE)
        val selection = objects.filter { it.index in selectedIndexes || it.index == selectedIndex }.map(SceneObject::bounds).reduceOrNull(Box3::merge)
        if (selection == null) {
            zoomToBed()
        } else {
            // zoom_to_selection(): DefaultCameraZoomToBoxMarginFactor.
            camera.zoomToBox(selection, ZOOM_TO_PLATE_MARGIN_FACTOR)
            invalidate()
        }
    }

    /** GLCanvas3D::zoom_to_bed(): the current plate's build volume at z = 0 (DefaultCameraZoomToBedMarginFactor). */
    private fun zoomToBed() {
        camera.sceneBox = sceneBox()
        currentPlateBox()?.let { camera.zoomToBox(it, ZOOM_TO_BED_MARGIN_FACTOR) }
        invalidate()
    }

    /** OrcaSlicer's plate view: from the front and above, framing the current plate (GLCanvas3D::zoom_to_plate). */
    fun resetView() {
        val bed = bed ?: return
        camera.selectPlateView()
        camera.sceneBox = sceneBox()
        currentPlateBox()?.let { camera.zoomToBox(it, ZOOM_TO_PLATE_MARGIN_FACTOR) }
        framedBed = bed
        invalidate()
    }

    /** The current plate at z = 0, which the view frames. */
    private fun currentPlateBox(): Box3? {
        val box = bed?.plateBox ?: return null
        val origin = plates.currentOrigin
        return Box3(box.min + origin, box.max + origin)
    }

    /** Every plate at z = 0 (PartPlateList::get_bounding_box()). */
    private fun allPlatesBox(): Box3? {
        val box = bed?.plateBox ?: return null
        return plates.origins.map { Box3(box.min + it, box.max + it) }.reduceOrNull(Box3::merge)
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

    /** GLGizmoCut3D of the open cut, with its plane where the finger has it while it drags. */
    private fun cutGizmo(): CutGizmo? {
        val open = cut ?: return null
        val plane = (drag as? CutDrag)?.plane ?: CutPlanes.affine(open.plane)
        return CutGizmo(plane, open.radius, pixel())
    }

    /**
     * GLGizmoCut3D::on_mouse() for a left press: its grabbers first, nearest to
     * the finger within [radius] pixels, then the plane itself. Returns false
     * when the finger is on neither, so it turns the camera.
     */
    private fun pressCut(x: Double, y: Double, radius: Double): Boolean {
        val gizmo = cutGizmo() ?: return false
        val index = cutIndex ?: -1
        if (cut?.editingConnectors == true) {
            // The connectors' raycasters: the one nearest to the finger, within
            // its own radius on the screen or the finger's reach.
            val held = cut?.connectors.orEmpty().mapIndexedNotNull { connector, view ->
                val center = Vec3(view.position.x, view.position.y, view.position.z)
                val projected = camera.project(center) ?: return@mapIndexedNotNull null
                val rim = camera.project(center + gizmo.rotation.transformVector(Vec3.UNIT_X) * view.radius)
                val reach = rim?.let { hypot(it.first - projected.first, it.second - projected.second) } ?: 0.0
                val distance = hypot(projected.first - x, projected.second - y)
                if (distance <= maxOf(radius, reach)) connector to distance else null
            }.minByOrNull { it.second }?.first ?: return false
            drag = CutConnectorDrag(index, held, gizmo.plane)
            invalidate()
            return true
        }
        val open = cut ?: return false
        if (open.drawingLine) {
            // process_cut_line() for a press with Shift: the line starts under the finger.
            val ray = camera.mouseRay(x, y) ?: return false
            val direction = ray.unitVector()
            drag = CutLineDrag(index, gizmo.plane, ray.a + direction).also { it.direction = direction }
            onCutLine(CutLineEvent.Start)
            invalidate()
            return true
        }
        val dovetailGrabbers = if (open.dovetail) gizmo.dovetailGrabbers(open.grooveAngle).map { (grabber, point) -> grabber to listOf(point) } else emptyList()
        val grabber = (listOf(
            CutGrabber.Z to listOf(gizmo.sphereCenter()),
            CutGrabber.X to gizmo.coneCenters(CutGrabber.X),
            CutGrabber.Y to gizmo.coneCenters(CutGrabber.Y),
        ) + dovetailGrabbers).flatMap { (grabber, points) -> points.mapNotNull { point -> camera.project(point)?.let { grabber to (point to it) } } }
            .map { (grabber, projected) -> Triple(grabber, projected.first, hypot(projected.second.first - x, projected.second.second - y)) }
            .filter { it.third <= radius }
            .minByOrNull { it.third }
        if (grabber != null) {
            // on_mouse(): a move along the plane starts from the point of the plane under the finger.
            val start = if (grabber.first == CutGrabber.X_MOVE || grabber.first == CutGrabber.Y_MOVE) {
                camera.mouseRay(x, y)?.let(gizmo::planePoint) ?: grabber.second
            } else {
                grabber.second
            }
            drag = CutDrag(index, grabber.first, gizmo.plane, start)
            invalidate()
            return true
        }
        val ray = camera.mouseRay(x, y) ?: return false
        val hit = gizmo.planeHit(ray, open.dovetail) ?: return false
        drag = CutDrag(index, CutGrabber.PLANE, gizmo.plane, hit)
        invalidate()
        return true
    }

    /**
     * on_dragging(): the sphere and the plane move the plane along its normal
     * (dragging_grabber_move()), X and Y turn it (dragging_grabber_rotation()).
     */
    private fun dragCut(drag: CutDrag, ray: Line3) {
        val open = cut ?: return
        val gizmo = CutGizmo(drag.startPlane, open.radius, pixel())
        drag.plane = when (drag.grabber) {
            CutGrabber.Z, CutGrabber.PLANE -> {
                // The point of the ray nearest to where the drag began, taken along the normal.
                val direction = ray.unitVector()
                val intersection = ray.a + direction * (drag.startPoint - ray.a).dot(direction)
                val projection = (intersection - drag.startPoint).dot(gizmo.normal)
                if (!projection.isFinite()) return
                drag.startPlane.withTranslation(gizmo.center + gizmo.normal * projection)
            }
            CutGrabber.X, CutGrabber.Y, CutGrabber.Z_ROTATION -> {
                val (rotation, angle) = gizmo.dragRotation(drag.grabber, gizmo.rotation, ray)
                drag.angle = angle
                rotation.withTranslation(gizmo.center)
            }
            CutGrabber.X_MOVE, CutGrabber.Y_MOVE -> {
                // dragging_grabber_move() along the plane's own X or Y axis.
                val axis = gizmo.rotation.transformVector(if (drag.grabber == CutGrabber.X_MOVE) Vec3.UNIT_X else Vec3(0.0, 1.0, 0.0)).normalized()
                val direction = ray.unitVector()
                val intersection = ray.a + direction * (drag.startPoint - ray.a).dot(direction)
                val projection = (intersection - drag.startPoint).dot(axis)
                if (!projection.isFinite()) return
                drag.startPlane.withTranslation(gizmo.center + axis * projection)
            }
        }
        drag.moved = true
        onCutPlane(drag.plane, false)
        invalidate()
    }

    /**
     * gizmo_event() for a click that did not move: in the connectors' window a
     * click on the section adds a connector there, elsewhere it unselects them.
     */
    fun tapCut(x: Float, y: Float) {
        if (cut?.editingConnectors != true) return
        val point = camera.mouseRay(x.toDouble(), y.toDouble())?.let(::cutPlaneHit)
        if (point != null && insideSection(point)) {
            onCutConnector(CutConnectorEvent.Add(Vector3(point.x, point.y, point.z)))
        } else {
            onCutConnector(CutConnectorEvent.Deselect)
        }
    }

    /** unproject_on_cut_plane(): where [ray] meets the cut plane. */
    private fun cutPlaneHit(ray: Line3): Vec3? {
        val gizmo = cutGizmo() ?: return null
        val direction = ray.b - ray.a
        val den = gizmo.normal.dot(direction)
        if (den == 0.0) return null
        val t = (gizmo.normal.dot(gizmo.center) - gizmo.normal.dot(ray.a)) / den
        return (ray.a + direction * t).takeIf(Vec3::isFinite)
    }

    /** is_looking_forward(): the camera looks against the normal of the cut plane. */
    private fun lookingForward(gizmo: CutGizmo) = camera.dirForward().dot(gizmo.normal) < 0.05

    /** unproject_on_cut_plane() with the contours respected: whether [point] of the plane lies in the section. */
    private fun insideSection(point: Vec3): Boolean {
        val triangles = cutSection ?: return false
        for (start in 0 until triangles.size / 9) {
            val base = start * 9
            val a = Vec3(triangles[base].toDouble(), triangles[base + 1].toDouble(), triangles[base + 2].toDouble())
            val b = Vec3(triangles[base + 3].toDouble(), triangles[base + 4].toDouble(), triangles[base + 5].toDouble())
            val c = Vec3(triangles[base + 6].toDouble(), triangles[base + 7].toDouble(), triangles[base + 8].toDouble())
            val normal = (b - a).cross(c - a)
            if (normal.norm() == 0.0) continue
            val inside = (b - a).cross(point - a).dot(normal) >= 0.0 &&
                (c - b).cross(point - b).dot(normal) >= 0.0 &&
                (a - c).cross(point - c).dot(normal) >= 0.0
            if (inside) return true
        }
        return false
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

    /**
     * The moved volume replaces the one it was made from; the other volumes of
     * the same copy — the parts of the object — follow it through the same
     * transformation, as the desktop app moves a ModelObject with its volumes.
     */
    private fun replaceObject(sceneObject: SceneObject) {
        if (sceneObject.index == WIPE_TOWER_INDEX) {
            wipeTower = sceneObject
        } else {
            val previous = plateObjects.firstOrNull { it.index == sceneObject.index && it.key == sceneObject.key }
            val transform = previous?.let { sceneObject.world * it.world.inverse() }
            plateObjects = plateObjects.map { volume ->
                when {
                    volume.index != sceneObject.index -> volume
                    volume.key == sceneObject.key -> sceneObject
                    transform == null -> volume
                    else -> volume.withWorld(transform * volume.world)
                }
            }
        }
        showObjects(plateObjects + listOfNotNull(wipeTower))
    }

    /** The dovetail's parts, or the pieces of a right click, stand in the object's place. */
    private var shownPreview = false

    private fun showObjects(objects: List<SceneObject>) {
        val preview = cut?.previewParts.orEmpty().let { parts -> parts.isNotEmpty() && parts.all { it.mesh.value in cutPartMeshes } }
        shownPreview = cut?.previewParts.orEmpty().isNotEmpty()
        val shown = when {
            cut == null -> objects
            preview -> emptyList()
            else -> objects.filter { it.index == cutIndex }
        }
        this.objects = shown
        renderer.setObjects(shown)
        invalidate()
    }

    private fun invalidate() {
        // GLGizmoBase::INV_ZOOM, for what the gizmos size by it.
        if (camera.zoom > 0.0 && pixel() != reportedPixel) {
            reportedPixel = pixel()
            onPixelSize(reportedPixel)
        }
        val bed = bed
        if (bed != null && framedBed !== bed && camera.viewportWidth > 1) {
            resetView()
            return
        }
        navigatorViewState.value = ViewNavigator.viewOf(camera.viewRotationRows())
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
                selectedIndexes = selectedIndexes,
                showAxes = options.axes,
                showGridlines = options.gridlines,
                gizmo = gizmoFrame(),
                slopeNormalZ = slopeNormalZ,
                // apply_color_clip_plane_colors(): the dovetail cut shows no parts' colours on the object.
                colorClipColors = if (cut?.dovetail == true) listOf(CUT_PLANE_DEF_COLOR, CUT_PLANE_DEF_COLOR) else listOf(UPPER_PART, LOWER_PART),
                colorClipPlane = cutGizmo()?.let { gizmo ->
                    // update_clipper(): set_color_clip_plane(normal, normal . centre).
                    floatArrayOf(-gizmo.normal.x.toFloat(), -gizmo.normal.y.toFloat(), -gizmo.normal.z.toFloat(), gizmo.normal.dot(gizmo.center).toFloat())
                },
                clippingPlane = cutGizmo()?.takeIf { cut?.editingConnectors == true }?.let { gizmo ->
                    // set_behavior(true, ...): the object is clipped on the camera's side.
                    val normal = gizmo.clippingNormal(lookingForward(gizmo))
                    floatArrayOf(-normal.x.toFloat(), -normal.y.toFloat(), -normal.z.toFloat(), normal.dot(gizmo.center).toFloat())
                },
            ),
        )
        surface.requestRender()
    }

    private fun gizmoFrame(): GizmoFrame? {
        cutGizmo()?.let { gizmo ->
            val open = cut ?: return null
            val dragging = drag as? CutDrag
            return gizmo.frame(
                dragged = dragging?.grabber,
                angle = dragging?.angle ?: 0.0,
                dragStart = dragging?.startPlane?.withTranslation(Vec3.ZERO),
                canCut = open.canCut,
                contour = cutContour,
                section = cutSection,
                connectors = open.connectors,
                meshes = cutConnectorMeshes,
                editing = open.editingConnectors,
                lookingForward = lookingForward(gizmo),
                hovered = (drag as? CutConnectorDrag)?.connector,
                dovetail = open.takeIf { it.dovetail }?.let {
                    CutDovetail(
                        planeTriangles = it.groovePlane?.value?.let(cutPartMeshes::get)?.cornerPositions(),
                        grooveAngle = it.grooveAngle,
                    )
                },
                parts = open.previewParts.map { part -> part to cutPartMeshes[part.mesh.value] },
                line = (drag as? CutLineDrag)?.takeIf { it.moved }?.let { it.begin to it.end },
                pixelScale = density,
            )
        }
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
        val gizmoBox = objects.firstOrNull { it.index == selectedIndex && (gizmo != null || cut != null) }?.bounds?.let { selection ->
            val extend = Vec3(1.0, 1.0, 1.0) * selection.maxSize()
            Box3(selection.center() - extend, selection.center() + extend)
        }
        val plateBoxes = bed?.extendedBox?.let { box -> plates.origins.map { Box3(box.min + it, box.max + it) } }.orEmpty()
        return (plateBoxes + listOfNotNull(objectsBox(), layerBox, gizmoBox)).reduceOrNull(Box3::merge)
    }

    private companion object {
        // GLCanvas3D.cpp
        const val TRACKBALL_SIZE = 0.8
        const val ZOOM_TO_PLATE_MARGIN_FACTOR = 1.25
        const val ZOOM_TO_BED_MARGIN_FACTOR = 2.0
        // libslic3r.h and Model.hpp
        const val EPSILON = 1e-4
        const val SINKING_Z_THRESHOLD = -0.001
        // GLGizmoCut.cpp: UPPER_PART_COLOR, LOWER_PART_COLOR and CUT_PLANE_DEF_COLOR.
        val UPPER_PART = floatArrayOf(0f, 1f, 1f, 1f)
        val LOWER_PART = floatArrayOf(1f, 0f, 1f, 1f)
        val CUT_PLANE_DEF_COLOR = floatArrayOf(0.9f, 0.9f, 0.9f, 0.5f)

        fun distanceToSegment(x: Double, y: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dx = b.first - a.first
            val dy = b.second - a.second
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (((x - a.first) * dx + (y - a.second) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            return hypot(x - (a.first + t * dx), y - (a.second + t * dy))
        }
    }
}

/** A GLSurfaceView with OpenGL ES 3.0, a depth buffer, and [samples] of multisampling where available, drawn on demand. */
@SuppressLint("ViewConstructor")
internal class PlateSurfaceView(context: Context, samples: Int) : GLSurfaceView(context) {
    private val renderer = PlateRenderer(context.assets)
    val controller = PlateViewController(this, renderer)

    /** opengl_fps_cap: frames at most this often; 0 for no limit. */
    @Volatile
    var fpsCap = 0
    private val frameScheduled = AtomicBoolean(false)

    /**
     * GLCanvas3D::on_idle() with an FPS cap: a frame asked for sooner than the
     * cap allows after the last one started waits for the rest of the interval.
     */
    override fun requestRender() {
        val cap = fpsCap
        if (cap <= 0) return super.requestRender()
        val minFrameTime = 1_000_000_000L / cap
        val elapsed = System.nanoTime() - renderer.lastFrameStart
        if (elapsed >= minFrameTime) return super.requestRender()
        if (!frameScheduled.compareAndSet(false, true)) return
        val waitMs = maxOf(1L, ceil((minFrameTime - elapsed) / 1_000_000.0).toLong())
        postDelayed({
            frameScheduled.set(false)
            renderNow()
        }, waitMs)
    }

    private fun renderNow() = super.requestRender()

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MultisampleConfigChooser(samples))
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

/**
 * RGBA 8888 with a 24-bit depth buffer and [samples] of multisampling, as
 * OrcaSlicer's canvas asks for them; without multisampling where the device
 * cannot (OpenGLManager::can_multisample()).
 */
private class MultisampleConfigChooser(private val samples: Int) : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig =
        (if (samples > 0) choose(egl, display, samples) else null) ?: choose(egl, display, samples = 0)
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

/**
 * A painting tool open on the object [mesh] names (GLGizmoPainterBase): what
 * it paints, and the angle from which the support tool highlights overhangs
 * (m_highlight_by_angle_threshold_deg).
 */
data class PaintingView(
    val mesh: ScenePath,
    val kind: PaintKind,
    val overhangAngle: Double = 0.0,
    /** "Vertical" (m_vertical_only): a stroke keeps to the screen column where it met the model. */
    val verticalOnly: Boolean = false,
)
