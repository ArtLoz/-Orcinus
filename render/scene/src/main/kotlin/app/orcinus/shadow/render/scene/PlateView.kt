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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintState
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeBox
import app.orcinus.shadow.core.model.VolumeManipulation
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import java.io.File
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
 * The scale gizmo of a volume selected alone: the transformation of the
 * reference system (Selection::get_bounding_box_in_reference_system()'s
 * trafo — none, the copy's, or the volume's own in the world) and the
 * volume's box there.
 */
data class VolumeScaleFrame(val reference: Transform3, val box: VolumeBox)

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
 * a selection of several dragged together the placements of its copies to
 * [onPlaceObjects], and a held object its index and the finger's position in the view to
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
    onPlaceObjects: (placements: List<Pair<Int, Transform3>>) -> Unit = {},
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
    /** The canvas's "Overhangs": slope.normal_z they are tinted from; null while they are hidden. */
    overhangNormalZ: Float? = null,
    /** The canvas's "Labels": what each labelled copy's label reads, by the copy's index; none while they are hidden. */
    labels: Map<Int, PlateLabel> = emptyMap(),
    /** The realistic view with "Smooth normals" (opengl_phong_smooth_normals): the meshes are read with smooth normals. */
    smoothNormals: Boolean = false,
    /** The sequential printing's clearances while the plate's validation fails; null for none. */
    clearance: PlateClearance? = null,
    /** The height range the object list edits (Selection::render_sidebar_layers_hints()); null for none. */
    layerRangeHint: LayerRangeHint? = null,
    /** The preview's shells (GCodeViewer::load_shells()); null for none. */
    shells: PlateShells? = null,
    /** GCodeViewer's tool marker, the plate's hotend model standing at this point; null while it hides. */
    toolMarker: Vector3? = null,
    /**
     * The variable layer height while it is on: the object it edits is drawn
     * in the colours of its layers, and the bar the page placed with
     * [LayerHeightBar] shows them too.
     */
    layerEditing: LayerEditingView? = null,
    /** The text tool's text, which a finger drags over its object's surface (SurfaceDrag); null while it is closed. */
    textDrag: TextDragView? = null,
    /** The text's transformation in its object where the finger let it go. */
    onTextDragged: (Transform3) -> Unit = {},
    /**
     * The measuring tool while it is open (GLGizmoMeasure): the view shows the
     * measured volumes alone, a finger on them explores their features and
     * selects one where it lets go, and the selections are highlighted and
     * dimensioned. Null while it is closed.
     */
    measure: MeasureView? = null,
    /** What a finger does with the measuring tool open. */
    onMeasure: (MeasureTouch) -> Unit = {},
    /** The distance label's "Edit to scale", with the distance it reads in millimetres. */
    onEditMeasureDistance: (Double) -> Unit = {},
    /**
     * The brim ears tool while it is open (GLGizmoBrimEars): its ears are
     * drawn, a finger on the copy places one and a finger on an ear selects,
     * drags or removes it. Null while it is closed.
     */
    brimEars: BrimEarsView? = null,
    /** What a finger does with the brim ears tool open. */
    onBrimEars: (BrimEarsTouch) -> Unit = {},
    /**
     * The mesh boolean tool while it is open (GLGizmoMeshBoolean): its volumes
     * are framed, and a finger on a volume of its copy picks it. Null while
     * it is closed.
     */
    meshBoolean: MeshBooleanView? = null,
    /** The mesh file of the volume a finger picked with the mesh boolean tool open. */
    onMeshBooleanPick: (String) -> Unit = {},
    /**
     * The move gizmo's reference system for the move window's "Object
     * coordinates": the selected copy's placement, which turns the gizmo's box
     * and arrows to the copy's axes; null for world coordinates.
     */
    moveFrame: Transform3? = null,
    /**
     * The assembly view while it shows (AssembleView): every copy's model parts
     * where they stand in the assembly, spread by its explosion ratio, under a
     * camera of the view's own; null for the 3D view.
     */
    assembly: AssemblyView? = null,
    /** The size of the assembly view's selection, which its "Assembly Info" tells; null while nothing is selected. */
    onAssemblySelection: (Vector3?) -> Unit = {},
    /** A gizmo of the assembly view placed the copy at [index]: its new assemble transformation. */
    onPlaceInAssembly: (index: Int, assemble: Transform3, manipulation: Manipulation) -> Unit = { _, _, _ -> },
    /** The plane of the assembly view's "Section View", its normal and offset, as it changes; null while it clips nothing. */
    onAssemblySection: (normal: Vector3, offset: Double) -> Unit = { _, _ -> },
    /**
     * Selection::Volume: the mesh of the selected copy's volume the object
     * list selected alone, which is drawn selected and which the move gizmo
     * and a finger move alone; null while copies are selected.
     */
    selectedVolume: String? = null,
    /**
     * Selection::get_bounding_sphere() of the selected volume in the world,
     * which the rotation gizmo turns it about; null until the engine measured
     * it, when the gizmo waits.
     */
    selectedVolumeSphere: BoundingSphere? = null,
    /** The scale gizmo of the selected volume: its reference system and box there; null until the engine measured it. */
    selectedVolumeScale: VolumeScaleFrame? = null,
    /** The painting tool's section plane as the view placed it, its normal and offset. */
    onPaintSection: (normal: Vector3, offset: Double) -> Unit = { _, _ -> },
    /**
     * GLCanvas3D::do_move() and do_rotate() of a volume: the selected volume
     * of the copy at [index] changed by [change] in the world; the app places it.
     */
    onPlaceVolume: (index: Int, change: Transform3, manipulation: VolumeManipulation) -> Unit = { _, _, _ -> },
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
        // The assembly view's canvas shows no overhangs ("Overhangs" works on the
        // Prepare page's 3D view alone), no clearance and no labels.
        val inAssembly = assembly != null
        val shownLabels = if (inAssembly) emptyMap() else labels
        LaunchedEffect(overhangNormalZ, inAssembly) { controller.setOverhangs(overhangNormalZ.takeUnless { inAssembly }) }
        LaunchedEffect(clearance, inAssembly) { controller.setClearance(clearance.takeUnless { inAssembly }) }
        LaunchedEffect(layerRangeHint, inAssembly) { controller.setLayerRangeHint(layerRangeHint.takeUnless { inAssembly }) }
        LaunchedEffect(shownLabels.keys) { controller.setLabelled(shownLabels.keys) }
        val labelPlacements by controller.labelPlacements.collectAsState()
        val measureDimensions by controller.measureDimensions.collectAsState()
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

        val bed by produceState<SceneBed?>(null, plate, smoothNormals) {
            value = plate?.let { description ->
                withContext(Dispatchers.IO) { runCatching { SceneLoader.loadBed(description, smoothNormals) }.getOrNull() }
            }
        }
        LaunchedEffect(bed) { controller.setBed(bed) }
        LaunchedEffect(plateOrigins, currentPlate, plateNames) { controller.setPlates(plateOrigins, currentPlate, followCurrentPlate, plateNames) }
        LaunchedEffect(layer) { controller.setLayer(layer) }

        val color = plate?.filamentColor ?: DEFAULT_FILAMENT_COLOR
        val meshes = remember { MeshCache() }
        val shellMeshes = remember { MeshCache() }
        // Marker::init(): the hotend of the printer, read once for the plate.
        val hotendPath = plate?.hotendModel
        val hotend by produceState<MeshData?>(null, hotendPath) {
            value = hotendPath?.let { path -> withContext(Dispatchers.IO) { runCatching { MeshFiles.read(File(path.value)) }.getOrNull() } }
        }
        LaunchedEffect(hotend, toolMarker) { controller.setToolMarker(hotend, toolMarker?.let { Vec3(it.x, it.y, it.z) }) }
        LaunchedEffect(shells, color, filamentColors, smoothNormals) {
            val loaded = withContext(Dispatchers.IO) {
                shellMeshes.smoothNormals = smoothNormals
                val objects = shells?.objects.orEmpty().map { it.first }
                shellMeshes.retain(objects.flatMapTo(HashSet()) { listOf(it.mesh.value) + it.parts.map { part -> part.mesh.value } })
                shells?.let { runCatching { ShellLoader.load(it, filamentColors, color, shellMeshes) }.getOrNull() }.orEmpty()
            }
            controller.setShells(loaded)
        }
        // The tower stands on the current plate, where wipe_tower_x and wipe_tower_y of the plate put it.
        val towerOrigin = plateOrigins.getOrElse(currentPlate) { Point2(0.0, 0.0) }
        LaunchedEffect(wipeTower, filamentColors, builtWipeTower, towerOrigin, smoothNormals, inAssembly) {
            // GLCanvas3D::reload_scene(): the assembly view loads no wipe tower.
            val tower = withContext(Dispatchers.IO) {
                wipeTower?.takeUnless { inAssembly }?.let { SceneLoader.loadWipeTower(it, filamentColors, builtWipeTower, towerOrigin, smoothNormals) }.orEmpty()
            }
            controller.setWipeTower(tower)
        }
        val paintedByTool = painting?.takeIf { it.kind != PaintKind.COLOR }?.mesh
        val hiddenCopies = assembly?.hidden.orEmpty()
        LaunchedEffect(objects, color, filamentColors, wireframes, paintedByTool, smoothNormals, inAssembly, hiddenCopies) {
            val loaded = withContext(Dispatchers.IO) {
                meshes.smoothNormals = smoothNormals
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
                    plateObject.instances.flatMapIndexed { instanceIndex, instance ->
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
                                ?.let { part to it }
                        }
                        // The colours the object is painted with, over its surface, or
                        // the paint of the open painting tool of another kind.
                        val painted = plateObject.paintedMeshes.mapNotNull { mesh ->
                            val paint = when (mesh.kind) {
                                PaintKind.COLOR -> filamentColors.getOrNull(mesh.state - 1) ?: color
                                else -> if (mesh.state == PaintState.BLOCKER) GizmoColors.BLOCKERS else GizmoColors.ENFORCERS
                            }
                            // The paint of a part lies on the part, where it stands in the object.
                            val part = plateObject.parts.getOrNull(mesh.volume - 1)
                            runCatching { SceneLoader.loadPaintedMesh(copyIndex, mesh, instance, paint, meshes, part) }.getOrNull()
                                ?.let { if (byTool) it.paintedByTool(paint) else it }
                                ?.let { part to it }
                        }
                        if (!inAssembly) {
                            listOfNotNull(copy) + parts.map { it.second } + painted.map { it.second }
                        } else {
                            // GLCanvas3D::reload_scene() for the assembly view: the
                            // copy's model parts alone, where it stands in the assembly.
                            val placement = AssemblyPlacement(instance, (plateObject as? PlateObject.ImportedModel)?.frame ?: Transform3.IDENTITY)
                            val hidden = PlateInstanceId(plateObject.mesh, instanceIndex) in hiddenCopies
                            // GLVolume::render(): a hidden volume under paint keeps the paint's colours.
                            val faint = hidden && painted.isNotEmpty()
                            listOfNotNull(copy?.let { placement.place(it, hidden = hidden && !faint, faint = faint) }) +
                                parts.filter { (part, _) -> part.type == VolumeType.PART }.map { (part, volume) -> placement.place(volume, part.placement, hidden = hidden) } +
                                painted.map { (part, volume) -> placement.place(volume, part?.placement, faint = hidden) }
                        }
                    }
                }
            }
            controller.setObjects(loaded, inAssembly, source = objects)
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
        // ModelObjectsClipper::render_cut(): the cut the engine made of the assembly view's section.
        val sectionPath = assembly?.section
        LaunchedEffect(sectionPath) {
            val loaded = withContext(Dispatchers.IO) {
                sectionPath?.let { runCatching { MeshFiles.read(java.io.File(it.value)).cornerPositions() }.getOrNull() }
            }
            controller.setAssemblySection(loaded)
        }
        // ObjectClipper::render_cut() of the painting tool's section.
        val paintCutPath = painting?.section?.cut
        LaunchedEffect(paintCutPath) {
            val loaded = withContext(Dispatchers.IO) {
                paintCutPath?.let { runCatching { MeshFiles.read(java.io.File(it.value)).cornerPositions() }.getOrNull() }
            }
            controller.setPaintSectionCut(loaded)
        }
        // The dovetail's plane, and the pieces shown in the object's place.
        val dovetailPaths = listOfNotNull(cut?.groovePlane?.value) + cut?.previewParts?.map { it.mesh.value }.orEmpty()
        LaunchedEffect(dovetailPaths, smoothNormals) {
            val loaded = withContext(Dispatchers.IO) {
                dovetailPaths.mapNotNull { path -> runCatching { MeshFiles.read(java.io.File(path), smoothNormals) }.getOrNull()?.let { path to it } }.toMap()
            }
            controller.setCutPartMeshes(loaded)
        }
        // The shapes of the cut's connectors.
        val connectorMeshPaths = cut?.connectors?.mapNotNull { it.mesh?.value }?.distinct().orEmpty()
        LaunchedEffect(connectorMeshPaths, smoothNormals) {
            val loaded = withContext(Dispatchers.IO) {
                connectorMeshPaths.mapNotNull { path -> runCatching { MeshFiles.read(java.io.File(path), smoothNormals) }.getOrNull()?.let { path to it } }.toMap()
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

        // The copies of the object the variable layer height edits, numbered as the scene numbers the copies.
        val layerEditingIndexes = layerEditing?.let { open ->
            var index = 0
            buildSet {
                for (plateObject in objects) {
                    for (instance in plateObject.instances.indices) {
                        if (plateObject.mesh == open.mesh) add(index)
                        index++
                    }
                }
            }
        }.orEmpty()

        // The copies the measuring tool measures, numbered as the scene numbers the copies.
        val measuredIndexes = measure?.let { open ->
            var index = 0
            buildSet {
                for (plateObject in objects) {
                    for (instance in plateObject.instances.indices) {
                        if (PlateInstanceId(plateObject.mesh, instance) in open.copies) add(index)
                        index++
                    }
                }
            }
        }.orEmpty()

        // The copy the mesh boolean tool is open on, numbered as the scene numbers the copies.
        val meshBooleanIndex = meshBoolean?.let { open ->
            var index = 0
            var found: Int? = null
            for (plateObject in objects) {
                for (instance in plateObject.instances.indices) {
                    if (PlateInstanceId(plateObject.mesh, instance) == open.copy) found = index
                    index++
                }
            }
            found
        }

        // The copy the brim ears tool is open on, numbered as the scene numbers the copies.
        val brimEarsIndex = brimEars?.let { open ->
            var index = 0
            var found: Int? = null
            for (plateObject in objects) {
                for (instance in plateObject.instances.indices) {
                    if (PlateInstanceId(plateObject.mesh, instance) == open.copy) found = index
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
            controller.onPlaceObjects = onPlaceObjects
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
            controller.onTextDragged = { volume -> onTextDragged(Transform3(volume.elements().toList())) }
            controller.setTextDrag(textDrag)
            controller.onMeasure = onMeasure
            controller.setMeasure(measure, measuredIndexes)
            controller.onBrimEars = { touch ->
                if (touch is BrimEarsTouch.Delete) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onBrimEars(touch)
            }
            controller.setBrimEars(brimEars, brimEarsIndex)
            controller.onMeshBooleanPick = onMeshBooleanPick
            controller.setMeshBoolean(meshBoolean, meshBooleanIndex)
            controller.setMoveFrame(moveFrame)
            controller.onPixelSize = onPixelSize
            controller.setCut(cut, cutIndex)
            controller.setPainting(painting != null)
            controller.setVerticalOnly(painting?.verticalOnly == true)
            controller.setHorizontalOnly(painting?.horizontalOnly == true)
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
            controller.onAssemblySelection = onAssemblySelection
            controller.onPlaceInAssembly = onPlaceInAssembly
            controller.onPlaceVolume = onPlaceVolume
            controller.onAssemblySection = onAssemblySection
            controller.setAssembly(assembly)
            controller.setSelection(selectedObject)
            controller.setSelected(selectedObjects)
            controller.setGizmo(gizmo)
            controller.setFlatteningPlanes(flatteningPlanes)
            controller.setEditable(editable)
            controller.setSelectedVolume(selectedVolume)
            controller.volumeSphere = selectedVolumeSphere
            controller.volumeScale = selectedVolumeScale
            controller.onPaintSection = onPaintSection
            controller.setPaintSection(painting?.section)
            controller.settleVolume(editable, objects)
        }

        val touchSlop = LocalViewConfiguration.current.touchSlop
        val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
        val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
        val edgePx = with(LocalDensity.current) { GESTURE_EDGE.toPx() }
        // The 3D navigator, which the view draws and takes the touches of where the page placed it.
        val navigatorInput = remember(controller) { NavigatorInput(controller) }
        var viewOrigin by remember { mutableStateOf<Offset?>(null) }
        val navigatorSlot = camera?.navigatorSlot
        val navigatorSquare = navigatorSlot?.let { slot -> viewOrigin?.let { slot.bounds.translate(-it) } }
        val layerBar = camera?.layerBarSlot?.let { slot -> viewOrigin?.let { slot.translate(-it) } }
        SideEffect {
            controller.setLayerEditing(layerEditing.takeUnless { inAssembly }, layerEditingIndexes, layerBar)
            navigatorInput.square = navigatorSquare
            navigatorInput.density = density
            navigatorInput.touchSlop = touchSlop
        }
        Box(modifier.onGloballyPositioned { viewOrigin = it.boundsInRoot().topLeft }) {
            AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { this.contentDescription = contentDescription }
                    .pointerInput(surface) { detectPlateGestures(controller, navigatorInput, touchSlop, doubleTapTimeout, longPressTimeout, edgePx) },
            )
            if (navigatorSlot != null && navigatorSquare != null) NavigatorCube(controller, navigatorInput, navigatorSquare, navigatorSlot.faceLabels)
            if (shownLabels.isNotEmpty()) ObjectLabels(labelPlacements, shownLabels, Modifier.fillMaxSize())
            measureDimensions?.let { dimensions ->
                MeasureDimensionsOverlay(
                    dimensions = dimensions,
                    imperial = measure?.imperial == true,
                    units = measure?.units.orEmpty(),
                    editDescription = measure?.editToScaleDescription.orEmpty(),
                    dark = colors.isDark,
                    onEditDistance = onEditMeasureDistance,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
 * (show_axes), [gridlines] (show_plate_gridlines) and [outline]
 * (show_outline, which the preview has not).
 */
data class PlateViewOptions(
    val perspective: Boolean = true,
    val autoPerspective: Boolean = false,
    val axes: Boolean = true,
    val gridlines: Boolean = true,
    val outline: Boolean = false,
    /** The realistic view with Phong shading (opengl_realistic_mode and opengl_realistic_phong). */
    val phong: Boolean = false,
    /** The realistic view with shadows on the plate (opengl_phong_basic_plate_shadows), which the preview has not. */
    val shadows: Boolean = false,
    /** The realistic view's SSAO pass (opengl_phong_ssao). */
    val ssao: Boolean = false,
)

/**
 * The View menu's commands to the camera of the [PlateView] it is given to:
 * a view of the camera, "Default View", and the canvas's zoom button.
 */
class PlateViewCamera {
    /** The view's controller while it is shown. */
    internal var controller: PlateViewController? by mutableStateOf(null)

    /** Where the page placed the 3D navigator ([PlateNavigator]); null while it shows none. */
    internal var navigatorSlot: NavigatorSlot? by mutableStateOf(null)

    /** Where the page placed the variable layer height bar ([LayerHeightBar]), in the root's pixels; null while it shows none. */
    internal var layerBarSlot: Rect? by mutableStateOf(null)

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

    /**
     * Where a ray through [at] of the view (the copy's volume whose outline
     * on the screen centres nearest the view's centre without it, as
     * start_create_volume_without_position() looks) hits the copy [copy]
     * first, with the surface's normal there; null where it misses.
     */
    fun surfaceHit(copy: Int, at: Offset? = null): SurfaceHit? = controller?.surfaceHit(copy, at?.x, at?.y)?.let { (point, normal) ->
        SurfaceHit(Vector3(point.x, point.y, point.z), Vector3(normal.x, normal.y, normal.z))
    }

    /** CameraUtils::get_z0_position(): where a ray through [at] of the view, or through its centre, meets the bed. */
    fun bedPoint(at: Offset? = null): Point2? = controller?.bedPoint(at?.x, at?.y)

    /** Where the camera stands, where it looks and whether in perspective (Camera::get_position(), get_dir_forward()). */
    fun eye(): CameraEye? = controller?.eye()
}

/** The camera's position and forward direction, in world coordinates, and whether it looks in perspective. */
data class CameraEye(val position: Vector3, val forward: Vector3, val perspective: Boolean)

/** A point of a surface, with the surface's normal there, in world coordinates. */
data class SurfaceHit(val position: Vector3, val normal: Vector3)

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
    navigator: NavigatorInput,
    touchSlop: Float,
    doubleTapTimeoutMillis: Long,
    longPressTimeoutMillis: Long,
    edgePx: Float,
) {
    var lastTapUptime = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        if (down.position.x < edgePx) return@awaitEachGesture
        if (with(navigator) { handle(down) }) return@awaitEachGesture
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
                controller.endMove(cancelled = true)
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
            // A painting tool and the cut gizmo keep their object while the finger turns the camera around it,
            // and the variable layer height its selection (GLCanvas3D::on_mouse() for a left up).
            if (!controller.isPainting && !controller.isCutting && !controller.isEditingLayers && !controller.isMeasuring && !controller.isBrimEars) {
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
    /** The wipe tower's volumes (its stripes, or the tower the slice built), which move together. */
    private var wipeTower: List<SceneObject> = emptyList()
    /** A painting tool is open, so a finger on the object paints (GLGizmoPainterBase). */
    private var painting = false

    val isPainting: Boolean get() = painting
    private var paintingStroke = false

    /** GLGizmoMeasure open on the copies at [measuredIndexes], which the scene shows alone. */
    private var measure: MeasureView? = null
    private var measuredIndexes: Set<Int> = emptySet()
    private val measureMeshes = MeasureMeshes()
    val isMeasuring: Boolean get() = measure != null
    var onMeasure: (MeasureTouch) -> Unit = {}

    /** The ray of the finger exploring the measured volumes, and how far around a selection's centre it takes the sphere, in millimetres. */
    private var measureRay: Line3? = null
    private var measureRadius = 0.0
    private val measureDimensionsState = MutableStateFlow<MeasureDimensions?>(null)
    val measureDimensions: StateFlow<MeasureDimensions?> = measureDimensionsState.asStateFlow()

    /** GLGizmoBrimEars open on the copy at [brimEarsIndex]. */
    private var brimEars: BrimEarsView? = null
    private var brimEarsIndex: Int? = null
    val isBrimEars: Boolean get() = brimEars != null
    var onBrimEars: (BrimEarsTouch) -> Unit = {}

    /** The ray of the finger on the copy, which places an ear where it lets go. */
    private var brimRay: Line3? = null

    /** GLGizmoMeshBoolean open on the copy at [meshBooleanIndex]. */
    private var meshBoolean: MeshBooleanView? = null
    private var meshBooleanIndex: Int? = null
    var onMeshBooleanPick: (String) -> Unit = {}

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

    /** Selection::Volume: the mesh of the selected copy's volume selected alone; null while copies are. */
    private var selectedVolume: String? = null

    /** The selected volume's sphere in the world, which the rotation gizmo turns it about; null until known. */
    var volumeSphere: BoundingSphere? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The selected volume's reference system and box, which the scale gizmo stands on; null until known. */
    var volumeScale: VolumeScaleFrame? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
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

    /** The assembly view while it shows (GLCanvas3D::CanvasAssembleView). */
    private var assembly: AssemblyView? = null

    /** Whether [plateObjects] were made for the assembly view, which shows no others. */
    private var objectsInAssembly = false

    /**
     * The camera's view the 3D view and the assembly view each keep while the
     * other shows (their canvases' cameras); the assembly view has none before
     * it first shows, when it frames its volumes (first_enter_assemble and
     * Camera::requires_zoom_to_volumes).
     */
    private var plateCameraView: OrcaCamera.View? = null
    private var assemblyCameraView: OrcaCamera.View? = null
    private var zoomToVolumes = false

    /** _render_assemble_info(): the size of the assembly view's selection, told as it changes. */
    var onAssemblySelection: (Vector3?) -> Unit = {}
    var onPlaceInAssembly: (Int, Transform3, Manipulation) -> Unit = { _, _, _ -> }
    var onPlaceVolume: (Int, Transform3, VolumeManipulation) -> Unit = { _, _, _ -> }

    /**
     * The objects of the plate the scene was last made from, and the volumes
     * made of them, which a volume a gizmo left goes back to when the app
     * placed none ([settleVolume]); [volumeLeft] while a volume stands where
     * a gizmo left it for the app to place it.
     */
    private var source: List<PlateObject>? = null
    private var loaded: List<SceneObject> = emptyList()
    private var volumeLeft = false

    /**
     * ModelObjectsClipper of the assembly view: the normal of its plane, kept
     * until "Reset direction" or until the view goes; the radius of the
     * volumes' box when they last changed; the plane, normal and offset; and
     * the cut the engine made of it.
     */
    private var sectionNormal: Vec3? = null
    private var sectionRadius = 0.0
    private var sectionKeys: Set<String> = emptySet()
    private var sectionPlane: Pair<Vec3, Double>? = null
    private var sectionCut: FloatArray? = null
    var onAssemblySection: (Vector3, Double) -> Unit = { _, _ -> }
    private var assemblySelection: Vector3? = null

    var onSelectObject: (Int?) -> Unit = {}
    var onMoveWipeTower: (Double, Double) -> Unit = { _, _ -> }
    var onPaint: (Line3, starts: Boolean) -> Unit = { _, _ -> }
    var onPlaceObject: (Int, Transform3, Manipulation) -> Unit = { _, _, _ -> }
    var onPlaceObjects: (List<Pair<Int, Transform3>>) -> Unit = {}
    var onOpenObjectMenu: (Int, Float, Float) -> Unit = { _, _, _ -> }
    var onOpenPlateMenu: ((Float, Float) -> Unit)? = null
    /** A tap on another plate, with its index; null where plates are not picked. */
    var onSelectPlate: ((Int) -> Unit)? = null

    /**
     * A press that may become a manipulation of the object [index], which
     * stood at [startWorld]; of its volume [key] alone (Selection::Volume),
     * or of the copy with its parts when null.
     */
    private sealed class Drag(val index: Int, val startWorld: Affine3, val key: String? = null) {
        var moved = false
    }

    /**
     * GLCanvas3D::Mouse::Drag: the object itself, touched at [startPosition].
     * A copy of a multiple selection is [held]: the finger keeps the selection
     * for its menu, as a right click does, and moved, drags the whole
     * selection, the [others] copies from where they stood.
     */
    private class ObjectDrag(
        index: Int,
        startWorld: Affine3,
        val startPosition: Vec3,
        key: String? = null,
        val held: Boolean = false,
        val others: List<Pair<Int, Affine3>> = emptyList(),
    ) : Drag(index, startWorld, key)

    /** GLGizmoBase::use_grabbers(): the move gizmo's grabber of [axis] at [startGrabber], the box centre at [startCenter]. */
    private class MoveGrabberDrag(index: Int, startWorld: Affine3, val axis: Int, val startGrabber: Vec3, val startCenter: Vec3, key: String?) :
        Drag(index, startWorld, key)

    /**
     * The scale gizmo's grabber [id], scaling about the box [center] as the
     * grabber moves from [start], or the selected volume [key] as [volume] says.
     */
    private class ScaleGrabberDrag(
        index: Int,
        startWorld: Affine3,
        val id: Int,
        val start: Vec3,
        val bottomCenter: Vec3,
        val center: Vec3,
        key: String?,
        val volume: VolumeScaling?,
    ) : Drag(index, startWorld, key) {
        /** The scale reached, along the axes of the gizmo's frame. */
        var scale = Vec3(1.0, 1.0, 1.0)
    }

    /**
     * Selection::scale_and_translate() of a volume, which is independent:
     * along the reference system's [linear] part about the volume's [origin]
     * in the world; [frame] the reference's rotation and [box] the volume's
     * box along it, as the drag began.
     */
    private class VolumeScaling(val linear: Affine3, val origin: Vec3, val frame: Affine3, val box: Box3)

    /**
     * GLGizmoCut3D's [grabber], or the plane itself, pressed at [startPoint]
     * while the plane stood at [startPlane]; the plane it has taken, and for a
     * rotation the angle it turned by.
     */
    private class CutDrag(index: Int, val grabber: CutGrabber, val startPlane: Affine3, val startPoint: Vec3) : Drag(index, startPlane) {
        var plane: Affine3 = startPlane
        var angle = 0.0
    }

    /** An ear of the brim ears tool ([ear]), which the finger holds. */
    private class BrimEarDrag(index: Int, val ear: Int, start: Affine3) : Drag(index, start)

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

    /**
     * SurfaceDrag of the text tool's text, from [start], with the scene as it
     * stood then; the transformation in its object it has taken.
     */
    private class TextDrag(val start: TextDragStart, startWorld: Affine3, val startScene: List<SceneObject>) : Drag(start.index, startWorld) {
        var volume: Affine3? = null
    }

    /** The text tool's text, which a finger drags over its object's surface. */
    private var textDrag: TextDragView? = null
    var onTextDragged: (Affine3) -> Unit = {}

    fun setTextDrag(view: TextDragView?) {
        if (textDrag == view) return
        textDrag = view
        if (drag is TextDrag) drag = null
    }

    /** Where the camera stands and looks, for the text tool's "Set text to face camera". */
    fun eye(): CameraEye {
        val position = camera.position()
        val forward = camera.dirForward()
        return CameraEye(Vector3(position.x, position.y, position.z), Vector3(forward.x, forward.y, forward.z), !camera.orthographic)
    }

    /** The rotation gizmo's grabber of [axis], turning about the sphere [center] by [angle]. */
    private class RotateGrabberDrag(index: Int, startWorld: Affine3, val axis: Int, val center: Vec3, val sphereRadius: Double, key: String?) :
        Drag(index, startWorld, key) {
        var angle = 0.0
    }

    /**
     * Whether a finger holds an object or a grabber it can move, or paints a
     * stroke that follows it, instead of orbiting the camera.
     */
    val moving: Boolean get() = drag != null || paintingStroke || measureRay != null || brimRay != null

    /** Whether a finger holds an object it has not moved yet, which a long press turns into its context menu. */
    val holdsObject: Boolean
        get() = (drag as? ObjectDrag)?.moved == false || (drag as? CutConnectorDrag)?.moved == false || (drag as? BrimEarDrag)?.moved == false ||
            (drag as? CutDrag)?.let { it.grabber == CutGrabber.PLANE && !it.moved } == true

    /** GLGizmoCut3D::on_mouse() takes a right click on the pieces of a planar cut outside the connectors' window. */
    private val selectsCutParts: Boolean get() = cut?.let { !it.editingConnectors && !it.dovetail && !it.drawingLine } == true

    /**
     * Plater::priv::on_right_click() for the object the finger holds: asks for
     * its menu at the point ([x], [y]) and lets the object go. Returns false
     * when the finger holds no object.
     */
    fun openObjectMenu(x: Float, y: Float): Boolean {
        (drag as? BrimEarDrag)?.takeIf { !it.moved }?.let { held ->
            // GLGizmoBrimEars::gizmo_event(RightDown) over an ear: it goes.
            drag = null
            onBrimEars(BrimEarsTouch.Delete(held.ear))
            invalidate()
            return true
        }
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
        // GLCanvas3D::on_mouse(): no right click while the variable layer height is on.
        if (held.moved || isEditingLayers) return false
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
        if (isEditingLayers) return false
        val open = onOpenPlateMenu ?: return false
        if (assembly != null) {
            // The assembly view picks no plate, and has no menu over empty space while something is selected.
            if (selectedIndex != null || selectedIndexes.isNotEmpty()) return false
            open(x, y)
            return true
        }
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
        if (assembly != null) return
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

    fun setShells(shells: List<SceneObject>) {
        renderer.setShells(shells)
        invalidate()
    }

    fun setToolMarker(mesh: MeshData?, position: Vec3?) {
        renderer.setToolMarker(mesh, position)
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
    /** The volumes of the copies, made for the assembly view when [inAssembly], at an explosion ratio of 1. */
    fun setObjects(objects: List<SceneObject>, inAssembly: Boolean = false, source: List<PlateObject>? = null) {
        drag = null
        this.source = source
        loaded = objects
        volumeLeft = false
        objectsInAssembly = inAssembly
        plateObjects = if (inAssembly) objects.map { it.exploded((assembly?.explosionRatio ?: 1.0) - 1.0) } else objects
        showObjects(plateObjects + wipeTower)
        // ModelObjectsClipper::on_update(): new meshes take the radius of the volumes' box.
        if (inAssembly) {
            val keys = objects.mapTo(HashSet()) { it.key }
            if (keys != sectionKeys) {
                sectionKeys = keys
                sectionRadius = objectsBox()?.let { 0.5 * it.size().norm() } ?: 0.0
            }
        }
    }

    /** The cut of the assembly view's section the engine made, GL_TRIANGLES corners; null for none. */
    fun setAssemblySection(cut: FloatArray?) {
        sectionCut = cut
        invalidate()
    }

    /**
     * ModelObjectsClipper::set_position(): the plane at [position] of the
     * volumes' box along its normal, which [keepNormal] keeps and the camera
     * gives otherwise, through the box's centre now and as far as its radius
     * spread by the explosion ratio; told to the page while it clips.
     */
    private fun setSectionPosition(position: Double, keepNormal: Boolean) {
        val normal = sectionNormal?.takeIf { keepNormal } ?: -camera.dirForward()
        val center = objectsBox()?.center() ?: Vec3.ZERO
        val ratio = assembly?.explosionRatio ?: 1.0
        sectionNormal = normal
        val plane = normal to (normal.dot(center) + sectionRadius * ratio - position * 2.0 * sectionRadius * ratio)
        sectionPlane = plane
        if (position > 0.0) onAssemblySection(Vector3(normal.x, normal.y, normal.z), plane.second)
    }

    /**
     * Plater::priv::set_current_panel() between the 3D view and the assembly
     * view: each keeps its view of the camera while the other shows. While the
     * assembly view shows, the volumes move on with its explosion ratio
     * (GLVolume::explosion_ratio).
     */
    fun setAssembly(view: AssemblyView?) {
        val previous = assembly
        if (previous == view) return
        assembly = view
        if ((previous == null) != (view == null)) {
            drag = null
            if (view != null) {
                plateCameraView = camera.view()
                val kept = assemblyCameraView
                if (kept != null) {
                    camera.loadView(kept)
                } else {
                    camera.loadView(OrcaCamera().view())
                    zoomToVolumes = true
                }
            } else {
                assemblyCameraView = camera.view()
                plateCameraView?.let(camera::loadView)
                zoomToVolumes = false
            }
            // The clipper goes with the view (AssembleViewDataPool::update(0) releases it).
            if (view == null) {
                sectionNormal = null
                sectionPlane = null
                sectionKeys = emptySet()
            }
            showObjects(plateObjects + wipeTower)
        } else if (objectsInAssembly && previous != null && view != null && previous.explosionRatio != view.explosionRatio) {
            plateObjects = plateObjects.map { it.exploded(view.explosionRatio - previous.explosionRatio) }
            showObjects(plateObjects + wipeTower)
        }
        if (view != null && (previous?.sectionPosition != view.sectionPosition || previous.sectionResets != view.sectionResets)) {
            // "Section View" keeps the plane's normal, which the camera gives the
            // first time; "Reset direction" takes the camera's anew.
            val reset = previous != null && previous.sectionResets != view.sectionResets
            if (view.sectionPosition > 0.0 || sectionPlane != null || reset) setSectionPosition(view.sectionPosition, keepNormal = !reset)
            invalidate()
        }
    }

    /**
     * The cut gizmo on the copy at [index], or none. While it is open the scene
     * shows that copy alone (InstancesHider and toggle_model_objects_visibility).
     */
    /**
     * GLGizmoMeasure opens, changes or closes: the scene shows the measured
     * volumes alone (toggle_selected_volume_visibility()).
     */
    fun setMeasure(view: MeasureView?, indexes: Set<Int>) {
        if (measure == view && measuredIndexes == indexes) return
        val shownChanged = (measure == null) != (view == null) || measuredIndexes != indexes || measure?.volumes != view?.volumes
        measure = view
        measuredIndexes = indexes
        if (view == null) measureRay = null
        if (shownChanged) showObjects(plateObjects + wipeTower) else invalidate()
    }

    /** GLGizmoBrimEars opens, changes or closes. */
    fun setBrimEars(view: BrimEarsView?, index: Int?) {
        if (brimEars == view && brimEarsIndex == index) return
        brimEars = view
        brimEarsIndex = index
        if (view == null) {
            brimRay = null
            if (drag is BrimEarDrag) drag = null
        }
        invalidate()
    }

    /** The move gizmo's reference system: the copy's placement in object coordinates, null in the world's. */
    private var moveFrame: Affine3? = null

    fun setMoveFrame(frame: Transform3?) {
        val affine = frame?.let { Affine3(it.columns.toDoubleArray()) }
        if (affine?.elements()?.contentEquals(moveFrame?.elements()) ?: (moveFrame == null)) return
        moveFrame = affine
        invalidate()
    }

    /** GLGizmoMeshBoolean opens, takes other volumes, or closes. */
    fun setMeshBoolean(view: MeshBooleanView?, index: Int?) {
        if (meshBoolean == view && meshBooleanIndex == index) return
        meshBoolean = view
        meshBooleanIndex = index
        invalidate()
    }

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
            showObjects(plateObjects + wipeTower)
        } else if (cut?.previewParts.orEmpty().isNotEmpty() != this.shownPreview) {
            showObjects(plateObjects + wipeTower)
        } else {
            invalidate()
        }
    }

    fun setCutPartMeshes(meshes: Map<String, MeshData>) {
        cutPartMeshes = meshes
        // The object gives way to its parts once they are there (toggle_model_objects_visibility()).
        showObjects(plateObjects + wipeTower)
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

    /**
     * The painting tool's section (ObjectClipper): as the page has it, the
     * normal its plane keeps, the plane itself (m_clp), and the cut.
     */
    private var paintSection: PaintSectionView? = null
    private var paintSectionNormal: Vec3? = null
    private var paintSectionPlane: Pair<Vec3, Double>? = null
    private var paintSectionCut: FloatArray? = null
    var onPaintSection: (Vector3, Double) -> Unit = { _, _ -> }

    /**
     * ObjectClipper::set_position_by_ratio(): the slider keeps the plane's
     * normal, which the camera gives the first time, and "Reset direction"
     * takes the camera's anew; the tool closing lets the plane go (on_release()).
     */
    fun setPaintSection(section: PaintSectionView?) {
        val previous = paintSection
        if (previous == section) return
        paintSection = section
        if (section == null) {
            paintSectionNormal = null
            paintSectionPlane = null
        } else if (previous?.position != section.position || previous.resets != section.resets) {
            val reset = previous != null && previous.resets != section.resets
            if (section.position > 0.0 || paintSectionPlane != null || reset) {
                val normal = paintSectionNormal?.takeIf { !reset } ?: -camera.dirForward()
                paintSectionNormal = normal
                val center = Vec3(section.center.x, section.center.y, section.center.z)
                val plane = normal to (normal.dot(center) + section.radius - section.position * 2.0 * section.radius)
                paintSectionPlane = plane
                onPaintSection(Vector3(normal.x, normal.y, normal.z), plane.second)
            }
        }
        invalidate()
    }

    fun setPaintSectionCut(cut: FloatArray?) {
        paintSectionCut = cut
        invalidate()
    }

    /** GLGizmoPainterBase::get_clipping_plane_data(): the section's plane while it is past 0, as the shader takes it. */
    private fun paintingClippingPlane(): FloatArray? {
        if (!painting || (paintSection?.position ?: 0.0) <= 0.0) return null
        val (normal, offset) = paintSectionPlane ?: return null
        return floatArrayOf(-normal.x.toFloat(), -normal.y.toFloat(), -normal.z.toFloat(), offset.toFloat())
    }

    /** The wipe tower stands in the scene beside the objects, and is picked and moved like one. */
    fun setWipeTower(tower: List<SceneObject>) {
        val same = wipeTower.size == tower.size && wipeTower.zip(tower).all { (shown, given) ->
            shown.key == given.key && shown.color == given.color && shown.world.elements().contentEquals(given.world.elements())
        }
        if (same) return
        wipeTower = tower
        showObjects(plateObjects + tower)
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

    /** "Vertical" and "Horizontal", and the screen column and row the stroke started in. */
    private var verticalOnly = false
    private var horizontalOnly = false
    private var strokeX = 0f
    private var strokeY = 0f

    fun setVerticalOnly(vertical: Boolean) {
        verticalOnly = vertical
    }

    fun setHorizontalOnly(horizontal: Boolean) {
        horizontalOnly = horizontal
    }

    fun setSlope(normalZ: Float?) {
        if (slopeNormalZ == normalZ) return
        slopeNormalZ = normalZ
        invalidate()
    }

    fun setEditable(editable: Boolean) {
        this.editable = editable
    }

    fun setSelectedVolume(key: String?) {
        if (selectedVolume == key) return
        selectedVolume = key
        if (drag?.key != null) drag = null
        invalidate()
    }

    /**
     * Once the plate can be edited again after a volume was left for the app
     * to place, and the plate's [objects] are still those the scene was made
     * from (the engine placed nothing), the volume goes back where they have it.
     */
    fun settleVolume(editable: Boolean, objects: List<PlateObject>) {
        if (!volumeLeft || !editable) return
        volumeLeft = false
        if (objects == source) setObjects(loaded, objectsInAssembly, source)
    }

    /**
     * Selection::Volume in the 3D view: the volume the move, rotation and
     * scale gizmos and a finger change alone. Lay on face, and the assembly
     * view, still work on the copy.
     */
    private fun volumeMode(): String? = selectedVolume?.takeIf { assembly == null && gizmo != PlateGizmo.LAY_ON_FACE }

    /** What the gizmos stand around: the selected volume, or the selected copy. */
    private fun selectedTarget(): SceneObject? =
        volumeMode()?.let { key -> objects.firstOrNull { it.index == selectedIndex && it.key == key } } ?: objects.firstOrNull { it.index == selectedIndex }

    /** What [drag] moves: its volume, or the copy. */
    private fun targetOf(drag: Drag): SceneObject? =
        drag.key?.let { key -> objects.firstOrNull { it.index == drag.index && it.key == key } } ?: objects.firstOrNull { it.index == drag.index }

    /** The volumes drawn selected while one is selected alone: it, with the paint on it; null while copies are. */
    private fun selectedVolumes(): Set<String>? {
        val key = selectedVolume?.takeIf { assembly == null } ?: return null
        return plateObjects.filter { it.index == selectedIndex && (it.key == key || it.paintedOn == key) }.mapTo(HashSet()) { it.key }
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
        measure?.let { open ->
            // GLGizmoMeasure::on_mouse(): over the measured volumes, or a
            // selection's sphere, the finger is the tool's, which shows what is
            // under it and selects that where it lets go; elsewhere it turns
            // the camera, as the desktop tool leaves the mouse to the canvas.
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
            // The sphere is 7.5 pixels wide on the desktop; a fingertip takes it from further.
            val radius = maxOf(MEASURE_SPHERE_RADIUS * pixel(), grabberRadius / camera.zoom)
            val direction = ray.unitVector()
            val onSphere = measureSpheres(open.measurement).any { center ->
                val along = (center - ray.a).dot(direction)
                along >= 0.0 && (center - ray.a - direction * along).norm() <= radius
            }
            if (!onSphere && objects.none { it.index != WIPE_TOWER_INDEX && it.raycast(ray) != null }) return false
            measureRay = ray
            measureRadius = radius
            onMeasure(measureTouch(ray, select = false))
            return true
        }
        if (meshBoolean != null) {
            // GLGizmoMeshBoolean::gizmo_event(LeftDown): the copy's volume the
            // ray hits closest to the eye, modifiers too; elsewhere the finger
            // turns the camera.
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
            val hit = objects.filter { it.index == meshBooleanIndex && !it.overlay }
                .mapNotNull { volume -> volume.raycast(ray)?.let { point -> volume to (point - ray.a).norm() } }
                .minByOrNull { it.second }
                ?: return false
            onMeshBooleanPick(hit.first.key)
            return true
        }
        brimEars?.let { open ->
            // GLGizmoBrimEars::gizmo_event(LeftDown): an ear's grabber first,
            // then the copy's model parts, where an ear goes once the finger
            // lets go; elsewhere the finger turns the camera.
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
            brimEarAt(open, ray, grabberRadius / camera.zoom)?.let { ear ->
                drag = BrimEarDrag(brimEarsIndex ?: -1, ear, Affine3())
                invalidate()
                return true
            }
            if (objects.none { it.index == brimEarsIndex && !it.modifier && it.raycast(ray) != null }) return false
            brimRay = ray
            onBrimEars(BrimEarsTouch.Explore(ray.a.toVector(), (ray.b - ray.a).toVector()))
            return true
        }
        if (painting) {
            // GLGizmoPainterBase::gizmo_event(): a press on the painted object
            // starts a stroke there, and the engine finds the triangle under
            // it; off the object the finger turns the camera, as the gizmo
            // leaves the mouse to the canvas.
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
            // The raycasters of the object's model parts, which pass by what the
            // section clips and, but in the assembly view, what sinks under the plate.
            val plane = paintSectionPlane?.takeIf { (paintSection?.position ?: 0.0) > 0.0 }
            val clipped = { point: Vec3 -> plane != null && plane.first.dot(point) > plane.second }
            if (objects.none { it.index == selectedIndex && !it.modifier && !it.overlay && it.unproject(ray, assembly == null, clipped) != null }) return false
            paintingStroke = true
            strokeX = x
            strokeY = y
            onPaint(ray, true)
            return true
        }
        textDrag?.let { text ->
            pressText(text, x, y)?.let { start ->
                drag = TextDrag(start, start.world, plateObjects)
                return true
            }
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
                PlateGizmo.ROTATE -> rotationSphere(target)?.let { (center, radius) -> RotateGrabberDrag(target.index, target.world, axis, center, radius, volumeMode()) }
                PlateGizmo.SCALE -> scaleGizmo(target)?.let {
                    val key = volumeMode()
                    ScaleGrabberDrag(target.index, target.world, axis, it.grabberCenter(axis), it.grabberCenter(4), it.center, key, key?.let { volumeScaling(target) })
                }
                else -> moveGizmo(target).let { MoveGrabberDrag(target.index, target.world, axis, it.grabberCenter(axis), it.center, volumeMode()) }
            }
            invalidate()
            return true
        }
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return false
        val (volume, hit) = objects
            .mapNotNull { sceneObject -> sceneObject.raycast(ray)?.let { sceneObject to it } }
            .minByOrNull { (_, hit) -> (hit - ray.a).norm() }
            ?: return false
        val volumeKey = volumeMode()
        if (volumeKey != null && volume.index == selectedIndex && volume.key == volumeKey) {
            // Selection::add() of the selected volume keeps the selection, and the
            // finger drags the volume alone.
            drag = if (editable) ObjectDrag(volume.index, volume.world, hit, volume.key) else null
            return true
        }
        // A part of an object is picked with the object it belongs to, as the
        // desktop canvas moves the instance, not the volume (Selection::add()
        // of a model part takes the instance mode, even of the copy whose
        // volume was selected).
        val target = objects.firstOrNull { it.index == volume.index } ?: volume
        if (volumeKey == null && selectedIndexes.size > 1 && target.index in selectedIndexes) {
            val others = selectedIndexes.filter { it != target.index }.mapNotNull { index -> objects.firstOrNull { it.index == index }?.let { index to it.world } }
            drag = if (editable) ObjectDrag(target.index, target.world, hit, held = true, others = others) else null
            return true
        }
        if (volumeKey != null && target.index == selectedIndex) onSelectObject(target.index) else select(target.index)
        drag = if (editable) ObjectDrag(target.index, target.world, hit) else null
        return true
    }

    /**
     * GLCanvas3D::on_mouse() while dragging a selected object: the object
     * follows the finger in the horizontal plane through the touched point, or
     * in the plane of the screen when the camera looks along the plate.
     */
    fun moveTo(x: Float, y: Float) {
        if (brimRay != null) {
            camera.mouseRay(x.toDouble(), y.toDouble())?.let { ray ->
                brimRay = ray
                onBrimEars(BrimEarsTouch.Explore(ray.a.toVector(), (ray.b - ray.a).toVector()))
            }
            return
        }
        (drag as? BrimEarDrag)?.let { held ->
            // on_dragging(): the ear follows the point of the copy under the finger.
            camera.mouseRay(x.toDouble(), y.toDouble())?.let { ray ->
                held.moved = true
                onBrimEars(BrimEarsTouch.Drag(held.ear, ray.a.toVector(), (ray.b - ray.a).toVector()))
            }
            return
        }
        if (measureRay != null) {
            camera.mouseRay(x.toDouble(), y.toDouble())?.let { ray ->
                measureRay = ray
                onMeasure(measureTouch(ray, select = false))
            }
            return
        }
        if (paintingStroke) {
            // The brush follows the finger, as the desktop gizmo paints while
            // the left button is held; "Vertical" keeps the finger's x where
            // the stroke started (_mouse_position.x() = m_last_mouse_click.x()),
            // "Horizontal" its y.
            val column = if (verticalOnly) strokeX else x
            val row = if (horizontalOnly) strokeY else y
            camera.mouseRay(column.toDouble(), row.toDouble())?.let { onPaint(it, false) }
            return
        }
        val drag = drag ?: return
        if (drag is TextDrag) {
            dragText(drag, x, y)
            return
        }
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
        val target = targetOf(drag) ?: return
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return
        if (drag is RotateGrabberDrag) {
            // GLGizmoRotate3D::on_mouse(): the object turns about the sphere's centre by the ring's angle.
            drag.angle = RotateGizmo(drag.center, drag.sphereRadius, pixel()).dragAngle(drag.axis, ray)
            drag.moved = true
            replaceObject(target.withWorld(RotateGizmo.rotated(drag.startWorld, drag.axis, drag.angle, drag.center)), alone = drag.key != null)
            return
        }
        if (drag is ScaleGrabberDrag) {
            // GLGizmoScale3D::do_scale_along_axis() and do_scale_uniform().
            val up = drag.volume?.frame?.transformVector(Vec3.UNIT_Z) ?: Vec3.UNIT_Z
            val ratio = ScaleGizmo.ratio(drag.id, drag.start, drag.bottomCenter, ray, up)
            if (ratio <= 0.0 || !ratio.isFinite()) return
            val scale = when (drag.id) {
                0, 1 -> Vec3(ratio, 1.0, 1.0)
                2, 3 -> Vec3(1.0, ratio, 1.0)
                4, 5 -> Vec3(1.0, 1.0, ratio)
                else -> Vec3(ratio, ratio, ratio)
            }
            drag.moved = true
            drag.scale = scale
            val world = drag.volume?.let { volume ->
                Affine3().translated(volume.origin) * volume.linear * Affine3.assemble(Vec3.ZERO, Vec3.ZERO, scale) * volume.linear.inverse() *
                    Affine3().translated(-volume.origin) * drag.startWorld
            } ?: ScaleGizmo.scaled(drag.startWorld, scale, drag.center)
            replaceObject(target.withWorld(world), alone = drag.key != null)
            return
        }
        val offset = when (drag) {
            is RotateGrabberDrag, is ScaleGrabberDrag, is BrimEarDrag -> return
            // GLCanvas3D::on_mouse(): the assembly view moves nothing a finger drags.
            is ObjectDrag -> if (assembly != null) return else objectOffset(drag, ray) ?: return
            is MoveGrabberDrag -> {
                // GLGizmoMove3D::on_dragging(): the displacement along the grabber's
                // axis, the world's or the copy's (Selection::translate() in instance coordinates).
                val displacement = MoveGizmo.projection(drag.startGrabber, drag.startCenter, ray)
                if (!displacement.isFinite()) return
                (drag.startGrabber - drag.startCenter).normalized() * displacement
            }
        }
        drag.moved = true
        replaceObject(target.withWorld(drag.startWorld.withTranslation(drag.startWorld.translation() + offset)), alone = drag.key != null)
        // Selection::translate(): the other copies of the selection go the same way.
        (drag as? ObjectDrag)?.others?.forEach { (index, start) ->
            objects.firstOrNull { it.index == index }?.let { replaceObject(it.withWorld(start.withTranslation(start.translation() + offset))) }
        }
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
    /** The finger let go, or [cancelled] as another finger came. */
    fun endMove(cancelled: Boolean = false) {
        brimRay?.let { ray ->
            brimRay = null
            onBrimEars(if (cancelled) BrimEarsTouch.Leave else BrimEarsTouch.Place(ray.a.toVector(), (ray.b - ray.a).toVector()))
            return
        }
        (drag as? BrimEarDrag)?.let { held ->
            // on_stop_dragging(), or a tap on the ear, which selects it alone (on_start_dragging()).
            drag = null
            when {
                held.moved -> onBrimEars(BrimEarsTouch.Dropped(held.ear))
                !cancelled -> onBrimEars(BrimEarsTouch.Select(held.ear))
            }
            invalidate()
            return
        }
        measureRay?.let { ray ->
            measureRay = null
            onMeasure(if (cancelled) MeasureTouch.Leave else measureTouch(ray, select = true))
            return
        }
        if (paintingStroke) {
            paintingStroke = false
            return
        }
        val drag = drag ?: return
        this.drag = null
        if (drag is ObjectDrag && drag.held && !drag.moved) {
            // Let go without the menu, the copy is a click on it: it is selected alone (Selection::add()).
            selectedIndex = drag.index
            invalidate()
            onSelectObject(drag.index)
            return
        }
        if (drag is TextDrag) {
            // write transformation from UI into model
            drag.volume?.let(onTextDragged)
            return
        }
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
        val target = targetOf(drag) ?: return
        if (target.index == WIPE_TOWER_INDEX) {
            // apply_wipe_tower(): the tower keeps to the plate, so only its
            // corner on the current plate is written back.
            val corner = target.world.translation() - plates.currentOrigin
            onMoveWipeTower(corner.x, corner.y)
            return
        }
        if (drag.key != null) {
            // GLCanvas3D::do_move() of a volume: the app places it and drops the
            // copies; the volume stands where it was left until then.
            volumeLeft = true
            val manipulation = when (drag) {
                is RotateGrabberDrag -> VolumeManipulation.ROTATE
                is ScaleGrabberDrag -> VolumeManipulation.SCALE
                else -> VolumeManipulation.MOVE
            }
            onPlaceVolume(target.index, Transform3((target.world * drag.startWorld.inverse()).elements().toList()), manipulation)
            return
        }
        val manipulation = when (drag) {
            is RotateGrabberDrag -> Manipulation.Rotate
            is ScaleGrabberDrag -> Manipulation.Scale
            else -> Manipulation.Move
        }
        assembly?.let { view ->
            // do_move() and do_rotate() of the assembly view: the copy's assemble
            // transformation, the explosion's offsets turned with it and taken off,
            // with nothing dropped on the plate.
            val spread = (target.world * drag.startWorld.inverse()).transformVector(target.explosion) * (view.explosionRatio - 1.0)
            val assemble = target.world.withTranslation(target.world.translation() - spread)
            onPlaceInAssembly(target.index, Transform3(assemble.elements().toList()), manipulation)
            return
        }
        if (drag is ObjectDrag && drag.held) {
            // do_move() of the selection: each copy rests on the plate unless its
            // auto drop is off, and the app places them all as one step.
            val placed = (listOf(target) + drag.others.mapNotNull { (index, _) -> objects.firstOrNull { it.index == index } }).map { copy ->
                val shift = copy.minZ()
                if (copy.autoDrop && shift > SINKING_Z_THRESHOLD) copy.withWorld(copy.world.withTranslation(copy.world.translation() - Vec3(0.0, 0.0, shift))) else copy
            }
            placed.forEach(::replaceObject)
            onPlaceObjects(placed.map { it.index to Transform3(it.world.elements().toList()) })
            return
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

    /**
     * start_dragging() of SurfaceDrag.cpp: a press on the text of the
     * selected copy, the nearest of what the finger's ray meets, holds it;
     * a text that is its object's only part moves with the object.
     */
    private fun pressText(text: TextDragView, x: Float, y: Float): TextDragStart? {
        if (text.onlyPart || !editable) return null
        val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return null
        val hovered = objects
            .mapNotNull { sceneObject -> sceneObject.raycast(ray)?.let { sceneObject to it } }
            .minByOrNull { (_, hit) -> (hit - ray.a).norm() }
            ?.first ?: return null
        if (hovered.key !in text.keys || hovered.index != selectedIndex) return null
        val placement = Affine3(text.placement.columns.toDoubleArray())
        val fix = text.fix?.let { Affine3(it.columns.toDoubleArray()) }
        // The copy, from the scene's transformation of the text's mesh.
        val instance = hovered.world * Affine3(text.sceneFrame.columns.toDoubleArray()).inverse()
        // world_matrix_fixed() without sla shift
        val world = instance * (fix?.let { placement * it.inverse() } ?: placement)
        // screen coordinate of volume center
        val (screenX, screenY) = camera.project(world.translation()) ?: return null
        val upLimit = UP_LIMIT.takeIf { text.keepUp }
        val startAngle = upLimit?.let { calcUp(world, it) }?.let { if (world.isLeftHanded) -it else it }
        return TextDragStart(hovered.index, screenX - x, screenY - y, world, instance.inverse(), startAngle, fix, upLimit)
    }

    /**
     * dragging() of SurfaceDrag.cpp: the text stands where the finger's ray,
     * moved by the offset it was held at, meets the copy's other model parts,
     * facing out of them with its up kept, on every copy of the object.
     */
    private fun dragText(drag: TextDrag, x: Float, y: Float) {
        val text = textDrag ?: return
        val start = drag.start
        val ray = camera.mouseRay(x + start.offsetX, y + start.offsetY) ?: return
        val (position, normal) = objects
            .filter { it.index == start.index && !it.overlay && !it.modifier && it.key !in text.keys }
            .mapNotNull { it.raycastHit(ray) }
            .minByOrNull { (point, _) -> (point - ray.a).norm() }
            ?: return
        val volume = volumeTransformation(start.world, normal, position, start.fix, start.instanceInv, start.startAngle, start.upLimit)
        drag.volume = volume
        drag.moved = true
        // Update transformation for all instances: the scene's mesh of the text after the copy's.
        val placement = Affine3(text.placement.columns.toDoubleArray())
        val sceneFrame = Affine3(text.sceneFrame.columns.toDoubleArray())
        val toScene = volume * placement.inverse() * sceneFrame
        plateObjects = drag.startScene.map { sceneObject ->
            if (sceneObject.key in text.keys) sceneObject.withWorld(sceneObject.world * sceneFrame.inverse() * toScene) else sceneObject
        }
        showObjects(plateObjects + wipeTower)
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
            // The assembly view turns about the selection, or every volume, past the limits, whatever the camera.
            assembly != null -> camera.rotateOnSphereWithTarget(rotX, rotY, false, (selectionBox() ?: objectsBox())?.center() ?: Vec3.ZERO)
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
        // A constrained camera is levelled before it pans; the assembly view's is not.
        if (!freeCamera && assembly == null) camera.recoverFromFreeCamera()
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

    /** The canvas's "Overhangs" (GLCanvas3D::m_slope's global use), slope.normal_z; null while hidden. */
    private var overhangNormalZ: Float? = null

    /** GLCanvas3D::m_sequential_print_clearance, visible while it is set. */
    private var clearance: SceneClearance? = null

    /** GLCanvas3D::m_sidebar_field of a height range. */
    private var layerRangeHint: LayerRangeHint? = null

    /** GLCanvas3D::m_layers_editing while it is on. */
    private var layerEditing: SceneLayerEditing? = null
    val isEditingLayers: Boolean get() = layerEditing != null

    /**
     * The point of the copy [copy] a ray through ([x], [y]) hits first, with
     * the normal there; without a point, find_closest() of EmbossJob.cpp:
     * the copy's model part whose outline on the screen (CameraUtils::create_hull2d())
     * has its centroid nearest the view's centre, hit through that centroid.
     */
    fun surfaceHit(copy: Int, x: Float?, y: Float?): Pair<Vec3, Vec3>? {
        val volumes = objects.filter { it.index == copy && !it.overlay }
        if (x != null && y != null) {
            val ray = camera.mouseRay(x.toDouble(), y.toDouble()) ?: return null
            return volumes.mapNotNull { it.raycastHit(ray) }.minByOrNull { (point, _) -> (point - ray.a).norm() }
        }
        val centerX = camera.viewportWidth / 2.0
        val centerY = camera.viewportHeight / 2.0
        var centerSqDistance = Double.MAX_VALUE
        var closest: Pair<SceneObject, Pair<Double, Double>>? = null
        for (volume in volumes.filterNot(SceneObject::modifier)) {
            val hull = convexHull(volume.worldPoints().mapNotNull(camera::project))
            val center = polygonCentroid(hull) ?: continue
            val dx = center.first - centerX
            val dy = center.second - centerY
            val biggerX = abs(dx) > abs(dy)
            if ((biggerX && dx * dx > centerSqDistance) || (!biggerX && dy * dy > centerSqDistance)) continue
            val distance = dx * dx + dy * dy
            if (centerSqDistance < distance) continue
            centerSqDistance = distance
            closest = volume to center
        }
        val (volume, center) = closest ?: return null
        val ray = camera.mouseRay(center.first, center.second) ?: return null
        return volume.raycastHit(ray)
    }

    /** CameraUtils::get_z0_position() of ([x], [y]), or of the view's centre. */
    fun bedPoint(x: Float?, y: Float?): Point2? {
        val ray = camera.mouseRay(x?.toDouble() ?: (camera.viewportWidth / 2.0), y?.toDouble() ?: (camera.viewportHeight / 2.0)) ?: return null
        val direction = ray.b - ray.a
        if (abs(direction.z) < EPSILON) return null
        val point = ray.intersectPlane(0.0)
        return Point2(point.x, point.y)
    }

    /** The variable layer height of [view] on the copies at [indexes], with its bar at [bar] in the view's pixels. */
    fun setLayerEditing(view: LayerEditingView?, indexes: Set<Int>, bar: Rect?) {
        val editing = view?.let { SceneLayerEditing(it, indexes, bar) }
        if (editing == layerEditing) return
        layerEditing = editing
        invalidate()
    }

    fun setClearance(value: PlateClearance?) {
        clearance = value?.let(::SceneClearance)
        invalidate()
    }

    fun setLayerRangeHint(value: LayerRangeHint?) {
        if (layerRangeHint == value) return
        layerRangeHint = value
        invalidate()
    }

    fun setOverhangs(normalZ: Float?) {
        if (overhangNormalZ == normalZ) return
        overhangNormalZ = normalZ
        invalidate()
    }

    /** The copies whose labels the view shows (GLCanvas3D::Labels), and where they stand. */
    private var labelled: Set<Int> = emptySet()
    private val labelPlacementsState = MutableStateFlow<List<LabelPlacement>>(emptyList())
    val labelPlacements: StateFlow<List<LabelPlacement>> = labelPlacementsState.asStateFlow()

    fun setLabelled(indexes: Set<Int>) {
        if (labelled == indexes) return
        labelled = indexes
        invalidate()
    }

    /**
     * GLCanvas3D::Labels::render(): every labelled copy's box, merged from its
     * volumes, labelled at its centre where that lies in the view; the
     * selected ones last, the others from the farthest to the nearest. OrcaSlicer
     * divides the perspective projection by 1000, the camera's usual distance,
     * rather than by each point's own depth; the view projects the centre.
     */
    private fun placeLabels() {
        if (labelled.isEmpty()) {
            if (labelPlacementsState.value.isNotEmpty()) labelPlacementsState.value = emptyList()
            return
        }
        val view = camera.viewMatrix
        labelPlacementsState.value = objects.filter { it.index in labelled }
            .groupBy(SceneObject::index)
            .map { (index, volumes) ->
                val center = volumes.map(SceneObject::bounds).reduce(Box3::merge).center()
                Triple(index, center, view.transformPoint(center).z)
            }
            .sortedWith(compareBy({ (index, _, _) -> index in selectedIndexes || index == selectedIndex }, { (_, _, eyeZ) -> eyeZ }))
            .mapNotNull { (index, center, _) ->
                val (x, y) = camera.project(center) ?: return@mapNotNull null
                if (x < 0.0 || camera.viewportWidth < x || y < 0.0 || camera.viewportHeight < y) return@mapNotNull null
                LabelPlacement(index, x.toFloat(), y.toFloat(), selected = index in selectedIndexes || index == selectedIndex)
            }
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

    /**
     * The canvas's zoom button: the plate view, framing the selection or, with
     * none, the plate, or the assembly view's volumes (zoom_to_volumes()).
     */
    fun zoomToFit() {
        selectView(CameraView.PLATE)
        val selection = selectionBox()
        when {
            // zoom_to_selection(): DefaultCameraZoomToBoxMarginFactor.
            selection != null -> {
                camera.zoomToBox(selection, ZOOM_TO_BOX_MARGIN_FACTOR)
                invalidate()
            }
            assembly != null -> {
                objectsBox()?.let { camera.zoomToBox(it, ZOOM_TO_BOX_MARGIN_FACTOR) }
                invalidate()
            }
            else -> zoomToBed()
        }
    }

    /**
     * GLGizmosManager::get_assemble_view_clipping_plane(): the section's plane
     * with its normal turned, which hides what lies beyond it; none while the
     * section is at 0.
     */
    private fun assemblyClippingPlane(): FloatArray? {
        val position = assembly?.sectionPosition ?: return null
        val (normal, offset) = sectionPlane?.takeIf { position > 0.0 } ?: return null
        return floatArrayOf(-normal.x.toFloat(), -normal.y.toFloat(), -normal.z.toFloat(), offset.toFloat())
    }

    /** Selection::get_bounding_box(): every volume of the selected copies. */
    private fun selectionBox(): Box3? =
        objects.filter { it.index in selectedIndexes || it.index == selectedIndex }.map(SceneObject::bounds).reduceOrNull(Box3::merge)

    /** GLCanvas3D::zoom_to_bed(): the current plate's build volume at z = 0 (DefaultCameraZoomToBedMarginFactor). */
    private fun zoomToBed() {
        camera.sceneBox = sceneBox()
        currentPlateBox()?.let { camera.zoomToBox(it, ZOOM_TO_BED_MARGIN_FACTOR) }
        invalidate()
    }

    /**
     * OrcaSlicer's plate view: from the front and above, framing the current
     * plate (GLCanvas3D::zoom_to_plate); the assembly view, which has no plate,
     * frames its volumes as its zoom button does with nothing selected.
     */
    fun resetView() {
        if (assembly != null) {
            camera.selectPlateView()
            objectsBox()?.let { camera.zoomToBox(it, ZOOM_TO_BOX_MARGIN_FACTOR) }
            invalidate()
            return
        }
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
        val target = selectedTarget() ?: return null
        val ends: (Int) -> Pair<Vec3, Vec3> = when (gizmo) {
            PlateGizmo.MOVE -> moveGizmo(target).let { move -> { axis -> move.grabberCenter(axis) to move.grabberTip(axis) } }
            PlateGizmo.ROTATE -> rotateGizmo(target)?.let { rotate -> { axis: Int -> rotate.grabberEnds(axis, 0.0) } } ?: return null
            PlateGizmo.SCALE -> scaleGizmo(target)?.let { scale -> { id: Int -> scale.grabberCenter(id).let { it to it } } } ?: return null
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

    private fun Vec3.toVector() = Vector3(x, y, z)

    private fun measureTouch(ray: Line3, select: Boolean): MeasureTouch {
        val origin = Vector3(ray.a.x, ray.a.y, ray.a.z)
        val direction = (ray.b - ray.a).let { Vector3(it.x, it.y, it.z) }
        return if (select) MeasureTouch.Select(origin, direction, measureRadius) else MeasureTouch.Explore(origin, direction, measureRadius)
    }

    /**
     * GLGizmoMove3D::on_render(): the box of the selection in the current
     * reference system (Selection::get_bounding_box_in_current_reference_system()),
     * the copy's rotation in object coordinates.
     */
    private fun moveGizmo(target: SceneObject): MoveGizmo {
        val placement = moveFrame ?: return MoveGizmo(target.bounds, pixel())
        val rotation = Affine3(AssemblyTransforms.rotation(Transform3(placement.elements().toList())).columns.toDoubleArray())
        return MoveGizmo(target.mesh.bounds.transformed(rotation.inverse() * target.world), pixel(), rotation, placement)
    }

    /** The sphere the rotation gizmo turns [target] about: the copy's, or the selected volume's once the engine measured it. */
    private fun rotationSphere(target: SceneObject): Pair<Vec3, Double>? {
        if (volumeMode() == null) return target.sphereCenter() to target.sphereRadius
        val sphere = volumeSphere ?: return null
        return Vec3(sphere.center.x, sphere.center.y, sphere.center.z) to sphere.radius
    }

    private fun rotateGizmo(target: SceneObject): RotateGizmo? = rotationSphere(target)?.let { (center, radius) -> RotateGizmo(center, radius, pixel()) }

    /**
     * Around the copy's box in the world, or the selected volume's box in its
     * reference system once the engine measured it, scaled as far as a drag
     * of it has gone.
     */
    private fun scaleGizmo(target: SceneObject): ScaleGizmo? {
        if (volumeMode() == null) return ScaleGizmo(target.bounds, pixel())
        val held = drag as? ScaleGrabberDrag
        val volume = held?.volume ?: return volumeScaling(target)?.let { ScaleGizmo(it.box, pixel(), it.frame) }
        val origin = volume.frame.inverse().transformPoint(volume.origin)
        fun scaled(point: Vec3) = Vec3(
            origin.x + (point.x - origin.x) * held.scale.x,
            origin.y + (point.y - origin.y) * held.scale.y,
            origin.z + (point.z - origin.z) * held.scale.z,
        )
        return ScaleGizmo(Box3(scaled(volume.box.min), scaled(volume.box.max)), pixel(), volume.frame)
    }

    /** The selected volume's scaling as the window's coordinates have it, from the volume [target] as it stands. */
    private fun volumeScaling(target: SceneObject): VolumeScaling? {
        val scale = volumeScale ?: return null
        val reference = Affine3(scale.reference.columns.toDoubleArray())
        val frame = Affine3(AssemblyTransforms.rotation(scale.reference).columns.toDoubleArray())
        val center = frame.inverse().transformPoint(Vec3(scale.box.center.x, scale.box.center.y, scale.box.center.z))
        val half = Vec3(scale.box.size.x, scale.box.size.y, scale.box.size.z) * 0.5
        return VolumeScaling(reference.withTranslation(Vec3.ZERO), target.world.translation(), frame, Box3(center - half, center + half))
    }

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
     * A volume moved [alone] takes the paint on it along.
     */
    private fun replaceObject(sceneObject: SceneObject, alone: Boolean = false) {
        if (sceneObject.index == WIPE_TOWER_INDEX) {
            // The tower moves whole, its stripes together.
            val previous = wipeTower.firstOrNull { it.key == sceneObject.key }
            val transform = previous?.let { sceneObject.world * it.world.inverse() }
            wipeTower = wipeTower.map { stripe ->
                when {
                    stripe.key == sceneObject.key -> sceneObject
                    transform == null -> stripe
                    else -> stripe.withWorld(transform * stripe.world)
                }
            }
        } else {
            val previous = plateObjects.firstOrNull { it.index == sceneObject.index && it.key == sceneObject.key }
            val transform = previous?.let { sceneObject.world * it.world.inverse() }
            plateObjects = plateObjects.map { volume ->
                when {
                    volume.index != sceneObject.index -> volume
                    volume.key == sceneObject.key -> sceneObject
                    transform == null -> volume
                    alone && volume.paintedOn != sceneObject.key -> volume
                    else -> volume.withWorld(transform * volume.world)
                }
            }
        }
        showObjects(plateObjects + wipeTower)
    }

    /** The dovetail's parts, or the pieces of a right click, stand in the object's place. */
    private var shownPreview = false

    private fun showObjects(objects: List<SceneObject>) {
        val preview = cut?.previewParts.orEmpty().let { parts -> parts.isNotEmpty() && parts.all { it.mesh.value in cutPartMeshes } }
        shownPreview = cut?.previewParts.orEmpty().isNotEmpty()
        val measured = measure
        val shown = when {
            // toggle_selected_volume_visibility() leaves the wipe tower as it is.
            measured != null -> objects.filter { volume ->
                volume.index == WIPE_TOWER_INDEX ||
                    (volume.index in measuredIndexes && measured.volumes?.let { volumes -> volumes.any { it.value == volume.key } } != false)
            }
            // The volumes made for the other view wait for those of this one; the assembly view has no wipe tower.
            objectsInAssembly != (assembly != null) -> emptyList()
            assembly != null -> objects.filter { it.index != WIPE_TOWER_INDEX }
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
        if (bed != null && framedBed !== bed && camera.viewportWidth > 1 && assembly == null) {
            resetView()
            return
        }
        // Camera::requires_zoom_to_volumes: the assembly view first shows its volumes framed.
        if (zoomToVolumes && assembly != null && objectsInAssembly && camera.viewportWidth > 1) {
            zoomToVolumes = false
            objectsBox()?.let { camera.zoomToBox(it, ZOOM_TO_BOX_MARGIN_FACTOR) }
        }
        navigatorViewState.value = ViewNavigator.viewOf(camera.viewRotationRows())
        val box = sceneBox() ?: Box3(Vec3(-1.0, -1.0, -1.0), Vec3(1.0, 1.0, 1.0))
        camera.sceneBox = box
        camera.applyProjection(box)
        placeLabels()
        measureDimensionsState.value = measure?.let { open -> measureDimensions(open, camera::project, density, pixel()) }
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
                selectedVolumes = selectedVolumes(),
                showAxes = options.axes,
                showGridlines = options.gridlines,
                overhangNormalZ = overhangNormalZ,
                outline = options.outline,
                // _render_sequential_clearance(): not while a gizmo's grabber is dragged.
                clearance = clearance.takeIf { drag !is MoveGrabberDrag && drag !is RotateGrabberDrag && drag !is ScaleGrabberDrag },
                layerRangeHint = layerRangeHint,
                phong = options.phong,
                // _render_cast_shadows_on_plate(): the 3D view's alone.
                shadows = options.shadows && assembly == null,
                ssao = options.ssao,
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
                } ?: assemblyClippingPlane() ?: paintingClippingPlane(),
                clippedIndex = selectedIndex.takeIf { cut?.editingConnectors != true && assemblyClippingPlane() == null && paintingClippingPlane() != null },
                layerEditing = layerEditing,
                selectionHidden = measure != null || brimEars != null,
                // GLGizmoMeshBoolean::on_render(): the source in white, the tool in Orca's green.
                framedVolumes = meshBoolean?.let { open ->
                    objects.filter { it.index == meshBooleanIndex && !it.overlay }.mapNotNull { volume ->
                        when (volume.key) {
                            open.source -> FramedVolume(volume.bounds, MESH_BOOLEAN_SOURCE)
                            open.tool -> FramedVolume(volume.bounds, MESH_BOOLEAN_TOOL)
                            else -> null
                        }
                    }
                }.orEmpty(),
                assembly = assembly != null,
                section = sectionCut?.takeIf { assemblyClippingPlane() != null } ?: paintSectionCut?.takeIf { paintingClippingPlane() != null },
            ),
        )
        surface.requestRender()
        // _render_assemble_info(): the selection's size while the assembly view shows.
        val selection = assembly?.let { selectionBox()?.size()?.let { size -> Vector3(size.x, size.y, size.z) } }
        if (selection != assemblySelection) {
            assemblySelection = selection
            onAssemblySelection(selection)
        }
    }

    private fun gizmoFrame(): GizmoFrame? {
        measure?.let { open -> return measureFrame(open, pixel(), measureMeshes) }
        brimEars?.let { open -> return brimEarsFrame(open) }
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
        val target = selectedTarget() ?: return null
        return when (gizmo) {
            PlateGizmo.MOVE -> moveGizmo(target).frame((drag as? MoveGrabberDrag)?.axis, density)
            PlateGizmo.ROTATE -> {
                // The rings stay where the drag began while the object turns inside them.
                val rotating = drag as? RotateGrabberDrag
                val gizmo = rotating?.let { RotateGizmo(it.center, it.sphereRadius, pixel()) } ?: rotateGizmo(target) ?: return null
                gizmo.frame(rotating?.axis, rotating?.angle ?: 0.0, density)
            }
            PlateGizmo.SCALE -> scaleGizmo(target)?.frame((drag as? ScaleGrabberDrag)?.id, density)
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
        const val ZOOM_TO_BOX_MARGIN_FACTOR = 1.25
        const val ZOOM_TO_BED_MARGIN_FACTOR = 2.0
        // libslic3r.h and Model.hpp
        const val EPSILON = 1e-4
        // GLGizmoMeasure's m_sphere: smooth_sphere(16, 7.5f), in pixels.
        const val MEASURE_SPHERE_RADIUS = 7.5
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
    // OpenGLManager::create_wxglcanvas() asks for an 8-bit stencil, which the realistic view's shadows mark the plate with.
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig =
        STENCILS.firstNotNullOfOrNull { stencil ->
            (if (samples > 0) choose(egl, display, samples, stencil) else null) ?: choose(egl, display, samples = 0, stencil)
        } ?: error("No OpenGL ES 3.0 configuration with a depth buffer")

    private fun choose(egl: EGL10, display: EGLDisplay, samples: Int, stencil: Int): EGLConfig? {
        val attributes = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 24,
            EGL10.EGL_STENCIL_SIZE, stencil,
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
        val STENCILS = listOf(8, 0)
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
    /** "Section view" of the painted copy; null for none. */
    val section: PaintSectionView? = null,
    /** "Horizontal" (m_horizontal_only): a stroke keeps to the screen row where it met the model. */
    val horizontalOnly: Boolean = false,
)

/**
 * A painting tool's "Section view" (ObjectClipper): how far its plane has
 * gone through the copy, 0 to 1, 0 clipping nothing; how many times "Reset
 * direction" was pressed; the point the plane passes at 0.5 and how far it
 * goes either side ([center] and [radius]: the copy's offset and the radius
 * of its box); and the cut the engine made, null for none.
 */
data class PaintSectionView(val position: Double, val resets: Int, val center: Vector3, val radius: Double, val cut: ScenePath?)

/** Geometry::convex_hull() of points of the screen: Andrew's monotone chain, counterclockwise. */
internal fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val sorted = points.distinct().sortedWith(compareBy<Pair<Double, Double>>({ it.first }, { it.second }))
    if (sorted.size < 3) return sorted
    fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
    val lower = ArrayList<Pair<Double, Double>>()
    for (point in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], point) <= 0) lower.removeAt(lower.size - 1)
        lower.add(point)
    }
    val upper = ArrayList<Pair<Double, Double>>()
    for (point in sorted.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], point) <= 0) upper.removeAt(upper.size - 1)
        upper.add(point)
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** Polygon::centroid(): the centre of the area of [polygon]; null for none. */
internal fun polygonCentroid(polygon: List<Pair<Double, Double>>): Pair<Double, Double>? {
    if (polygon.isEmpty()) return null
    var area = 0.0
    var x = 0.0
    var y = 0.0
    for (index in polygon.indices) {
        val (x0, y0) = polygon[index]
        val (x1, y1) = polygon[(index + 1) % polygon.size]
        val cross = x0 * y1 - x1 * y0
        area += cross
        x += (x0 + x1) * cross
        y += (y0 + y1) * cross
    }
    if (abs(area) < 1e-12) return polygon.first()
    return x / (3.0 * area) to y / (3.0 * area)
}

