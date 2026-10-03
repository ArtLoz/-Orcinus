package app.orcinus.shadow.render.scene

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.ui.geometry.Rect
import app.orcinus.shadow.render.scene.gl.GlDepthTarget
import app.orcinus.shadow.render.scene.gl.GlOffscreenFrame
import app.orcinus.shadow.render.scene.gl.GlProgram
import app.orcinus.shadow.render.scene.gl.GlTexture
import app.orcinus.shadow.render.scene.gl.GlVertexArray
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import javax.microedition.khronos.egl.EGLConfig
import kotlin.math.abs
import javax.microedition.khronos.opengles.GL10

/** What one frame shows from where: computed on the main thread from the camera. */
internal class SceneFrame(
    val view: Affine3,
    val projection: FloatArray,
    val nearZ: Float,
    val farZ: Float,
    val lookingDownward: Boolean,
    /** Background RGBA. */
    val background: FloatArray,
    val dark: Boolean,
    /** Device pixels per desktop pixel, which OrcaSlicer's line widths are given in. */
    val pixelScale: Float,
    /** Index of the object the tools work on; null when none or several are selected. */
    val selectedIndex: Int?,
    /** Indexes of every selected object, which the scene draws as selected. */
    val selectedIndexes: Set<Int> = emptySet(),
    /** show_axes and show_plate_gridlines of the View menu. */
    val showAxes: Boolean = true,
    val showGridlines: Boolean = true,
    /** The active gizmo on the selected object. */
    val gizmo: GizmoFrame?,
    /**
     * The support painting tool's overhang highlight (slope.normal_z of
     * GLGizmoPainterBase::render_triangles): facets whose world normal points
     * further down are tinted; null while it is closed.
     */
    val slopeNormalZ: Float? = null,
    /**
     * The canvas's "Overhangs" (GLVolumeCollection::m_slope.isGlobalActive):
     * slope.normal_z of every model part but the wipe tower; null while hidden.
     */
    val overhangNormalZ: Float? = null,
    /** The canvas's "Outline" (show_outline): the selected volumes are drawn with their silhouettes. */
    val outline: Boolean = false,
    /** The realistic view with "Phong shading" (opengl_realistic_mode and opengl_realistic_phong): the volumes take the phong shader. */
    val phong: Boolean = false,
    /** The realistic view with "Shadows" (opengl_phong_basic_plate_shadows) on the Prepare page: the objects cast shadows on the plate. */
    val shadows: Boolean = false,
    /** The sequential printing's clearances while the plate's validation fails (GLCanvas3D::_render_sequential_clearance()). */
    val clearance: SceneClearance? = null,
    /** The realistic view with "SSAO ambient occlusion" (opengl_phong_ssao): the frame goes through the SSAO pass. */
    val ssao: Boolean = false,
    /**
     * The cut gizmo's colour clip plane (GLVolumeCollection::set_color_clip_plane):
     * -normal and offset; the objects are drawn in the colour of the upper part
     * above it and of the lower part below it. Null while no cut is open.
     */
    val colorClipPlane: FloatArray? = null,
    /** The colours above and below the colour clip plane (set_color_clip_plane_colors()). */
    val colorClipColors: List<FloatArray> = listOf(floatArrayOf(0f, 1f, 1f, 1f), floatArrayOf(1f, 0f, 1f, 1f)),
    /**
     * The clipping plane of the objects (GLGizmosManager::get_clipping_plane()):
     * -normal and offset, which hide the side the normal points to; null clips nothing.
     */
    val clippingPlane: FloatArray? = null,
    /** The variable layer height while it is on. */
    val layerEditing: SceneLayerEditing? = null,
    /** GLGizmosManager::is_running() for a tool that hides the selection's box: the measuring tool. */
    val selectionHidden: Boolean = false,
)

/**
 * GLCanvas3D::m_layers_editing while it is on: what it edits ([view]), the
 * copies of its object by their index in the scene, and where its bar stands,
 * in the view's pixels; null while the page shows none.
 */
internal data class SceneLayerEditing(val view: LayerEditingView, val indexes: Set<Int>, val bar: Rect?) {
    /** Whether the variable layer height shader draws [sceneObject]: a model part of a copy of the object (render_volumes()). */
    fun draws(sceneObject: SceneObject) = sceneObject.index in indexes && !sceneObject.modifier
}

/**
 * Where the plates stand, in their order, and which one is current
 * (PartPlateList): every plate is the printer's plate moved to its origin.
 */
internal class ScenePlates(val origins: List<Vec3>, val current: Int, val names: List<String> = emptyList()) {
    val currentOrigin: Vec3 get() = origins.getOrElse(current) { Vec3.ZERO }

    companion object {
        val SINGLE = ScenePlates(listOf(Vec3.ZERO), 0)
    }
}

/**
 * Draws the plates and their objects the way OrcaSlicer's 3D canvas does
 * (GLCanvas3D::render for the 3D view): the bed model under the current plate,
 * every plate's background, excluded area, grid and number, the current one's
 * texture, then the objects, with OrcaSlicer's own shaders and colours. Scene
 * data and frames arrive from the main thread; everything else happens on the
 * GL thread.
 */
internal class PlateRenderer(private val assets: AssetManager) : GLSurfaceView.Renderer {
    private val lock = Any()
    private var pendingBed: SceneBed? = null
    private var bedChanged = false
    private var pendingObjects: List<SceneObject> = emptyList()
    private var objectsChanged = false
    private var pendingLayer: PlateLayer? = null
    private var layerChanged = false
    private var layer: PlateLayer? = null

    @Volatile
    private var frame: SceneFrame? = null

    @Volatile
    private var plates: ScenePlates = ScenePlates.SINGLE

    private var programs: Programs? = null
    private var gpuBed: GpuBed? = null
    /** The volumes' meshes on the GPU by key, with the mesh each was made from: a mesh read again goes up again. */
    private val gpuObjects = LinkedHashMap<String, Pair<MeshData, GlVertexArray>>()
    /** The edges of the triangles of the meshes drawn as a wireframe, by mesh. */
    private val gpuWireframes = LinkedHashMap<String, GlVertexArray>()
    private var objects: List<SceneObject> = emptyList()
    private var selectionBox: Triple<Box3, Boolean, GlVertexArray>? = null
    private var grabberCone: GlVertexArray? = null
    private var grabberCube: GlVertexArray? = null
    private var grabberSphere: GlVertexArray? = null
    /** The cut gizmo's connector shapes, by mesh file. */
    private val gizmoMeshes = HashMap<String, Pair<MeshData, GlVertexArray>>()
    /** The build volume of the current plate while the objects are drawn. */
    private var printVolume: Box3? = null
    /** PartPlateList::m_idx_textures, made as the plates need them. */
    private val labelTextures = HashMap<Int, GlTexture>()
    private val lineWidthRange = FloatArray(2)

    /** opengl_fxaa_enabled: the frame goes through GLCanvas3D::_render_fxaa_pass(). */
    @Volatile
    var fxaa = false

    /** When the last frame started (System.nanoTime), which the FPS cap paces from. */
    @Volatile
    var lastFrameStart = 0L
        private set

    /** GLCanvas3D::m_render_stats: hears the frames per second each time they are measured. */
    @Volatile
    var onFps: ((Int) -> Unit)? = null

    // RenderStats: the start of the second being measured and its frames.
    private var measuringStart = 0L
    private var fpsOut = -1
    private var fpsRunning = 0

    private var viewportWidth = 0
    private var viewportHeight = 0
    /** The samples of the surface's buffer, which the frame drawn for FXAA takes too. */
    private var surfaceSamples = 0
    private var offscreen: GlOffscreenFrame? = null
    /** GLVolume::render_with_outline()'s depth texture, kept for the next frames rather than made for every volume. */
    private var outlineDepth: GlDepthTarget? = null
    /** GLCanvas3D::m_background: the quad over the whole view that the FXAA pass and the shadows draw. */
    private var screenQuad: GlVertexArray? = null
    /** SequentialPrintClearance's models: the perimeter, the fill and the height limits of the clearance they were made of. */
    private var clearanceArrays: Pair<SceneClearance, List<GlVertexArray>>? = null
    /** GLCanvas3D::m_plate_shadow_mask, with the build volume it was made for (m_plate_shadow_mask_key). */
    private var shadowMask: Pair<Box3, GlVertexArray>? = null
    /** LayersEditing::m_layers_texture and m_z_texture_id, with what it was generated from. */
    private var layerTexture: LayerHeightTexture? = null
    private var layerTextureId = 0
    private var layerTextureOf: LayerEditingView? = null
    /** LayersEditing::m_profile.background: the bar's quad, with where it stands. */
    private var layerBar: Pair<FloatArray, GlVertexArray>? = null

    fun setBed(bed: SceneBed?) = synchronized(lock) {
        pendingBed = bed
        bedChanged = true
    }

    fun setObjects(objects: List<SceneObject>) = synchronized(lock) {
        pendingObjects = objects
        objectsChanged = true
    }

    fun setLayer(layer: PlateLayer?) = synchronized(lock) {
        pendingLayer = layer
        layerChanged = true
    }

    /** Frees the layers once the GL thread has ended: the context and its objects went with it. */
    fun releaseLayers() = synchronized(lock) {
        layer?.release()
        if (pendingLayer !== layer) pendingLayer?.release()
    }

    fun setFrame(frame: SceneFrame) {
        this.frame = frame
    }

    fun setPlates(plates: ScenePlates) {
        this.plates = plates
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // A new context: names from an earlier one are gone. The layer forgets
        // its own before anything here takes their numbers.
        layer?.onContextCreated()
        programs = Programs(assets)
        gpuBed = null
        gpuObjects.clear()
        gpuWireframes.clear()
        selectionBox = null
        grabberCone = null
        grabberCube = null
        grabberSphere = null
        gizmoMeshes.clear()
        labelTextures.clear()
        offscreen = null
        outlineDepth = null
        screenQuad = null
        shadowMask = null
        clearanceArrays = null
        layerTextureId = 0
        layerTextureOf = null
        layerBar = null
        val samples = IntArray(2)
        GLES30.glGetIntegerv(GLES30.GL_SAMPLES, samples, 0)
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, samples, 1)
        surfaceSamples = minOf(samples[0], samples[1])
        synchronized(lock) {
            bedChanged = true
            objectsChanged = true
        }
        GLES30.glGetFloatv(GLES30.GL_ALIASED_LINE_WIDTH_RANGE, lineWidthRange, 0)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(unused: GL10?) {
        lastFrameStart = System.nanoTime()
        uploadChanges()
        val frame = frame
        val programs = programs
        if (frame == null || programs == null) {
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        // SSAO and FXAA read the whole frame, so it is drawn off screen first; OpenGL ES
        // cannot copy the surface's multisampled buffer, or its depth, as desktop OpenGL does.
        val target = if (fxaa || frame.ssao) offscreenFrame() else null
        target?.bind()
        frame.layerEditing?.let(::updateLayerTexture)
        renderScene(programs, frame)
        if (target != null) {
            // GLCanvas3D::render(): the SSAO pass, then the FXAA pass.
            if (frame.ssao) {
                target.resolve(depth = true)
                target.bind()
                renderSsao(programs, frame, target)
            }
            target.resolve()
            if (fxaa) renderFxaa(programs.fxaa, target) else renderFrame(programs.flatTexture, target)
        }
        // GLCanvas3D::_render_overlays(): the variable layer height's bar.
        frame.layerEditing?.let { renderLayerBar(programs.variableLayerHeight, it) }
        measureFps()
    }

    /** The frame drawn off screen at the view's size, made again when the view changes size. */
    private fun offscreenFrame(): GlOffscreenFrame? {
        val current = offscreen
        if (current != null && current.width == viewportWidth && current.height == viewportHeight) return current.takeIf { it.complete }
        current?.release()
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        val made = GlOffscreenFrame(viewportWidth, viewportHeight, surfaceSamples)
        offscreen = made
        return made.takeIf { it.complete }
    }

    /** GLCanvas3D::m_background: the quad over the whole view. */
    private fun screenQuad(): GlVertexArray = screenQuad ?: GlVertexArray(
        GlVertexArray.floatBuffer(
            floatArrayOf(
                -1f, -1f, 0f, 0f, 0f, 1f, -1f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 1f,
                -1f, -1f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, -1f, 1f, 0f, 0f, 1f,
            ),
        ),
        listOf(GlProgram.POSITION to 3, GlProgram.TEX_COORD to 2),
        GLES30.GL_TRIANGLES,
    ).also { screenQuad = it }

    /**
     * GLCanvas3D::_render_ssao_pass(): the frame's colour and depth as they
     * are, the stencil marking the plate where it is seen, and the frame
     * drawn again through the ssao shader everywhere else.
     */
    private fun renderSsao(programs: Programs, frame: SceneFrame, target: GlOffscreenFrame) {
        GLES30.glDisable(GLES30.GL_BLEND)
        // Build stencil mask for bed/plate and apply SSAO only outside this mask.
        GLES30.glEnable(GLES30.GL_STENCIL_TEST)
        GLES30.glStencilMask(0xFF)
        GLES30.glClearStencil(0)
        GLES30.glClear(GLES30.GL_STENCIL_BUFFER_BIT)
        GLES30.glStencilFunc(GLES30.GL_ALWAYS, 1, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_REPLACE)
        // Mark only visible plate pixels (do not exclude objects in front of plate).
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glColorMask(false, false, false, false)
        plateMask()?.let { mask ->
            programs.flat.use()
            programs.flat.setMatrix4("projection_matrix", frame.projection)
            programs.flat.setMatrix4("view_model_matrix", frame.view.toFloatArray())
            mask.draw()
        }
        GLES30.glColorMask(true, true, true, true)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glStencilMask(0x00)
        GLES30.glStencilFunc(GLES30.GL_NOTEQUAL, 1, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_KEEP)

        val program = programs.ssao
        program.use()
        program.setMatrix4("view_model_matrix", IDENTITY)
        program.setMatrix4("projection_matrix", IDENTITY)
        program.setInt("color_texture", 0)
        program.setInt("depth_texture", 1)
        program.setVec2("inv_tex_size", 1f / target.width, 1f / target.height)
        program.setFloat("z_near", frame.nearZ)
        program.setFloat("z_far", frame.farZ)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.texture)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.depthTexture)
        screenQuad().draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        GLES30.glDisable(GLES30.GL_STENCIL_TEST)
        GLES30.glStencilMask(0xFF)
        GLES30.glDepthMask(true)
        GLES30.glDepthFunc(GLES30.GL_LESS)
    }

    /** The frame drawn off screen, put on the view as it is when no FXAA pass draws it. */
    private fun renderFrame(program: GlProgram, target: GlOffscreenFrame) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        program.use()
        program.setMatrix4("view_model_matrix", IDENTITY)
        program.setMatrix4("projection_matrix", IDENTITY)
        program.setInt("uniform_texture", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.texture)
        screenQuad().draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * The current plate's build volume at z = 0, which the shadows and the
     * SSAO pass mark in the stencil (m_plate_shadow_mask, made again for
     * another build volume as its key tells).
     */
    private fun plateMask(): GlVertexArray? {
        val bed = gpuBed ?: return null
        val origin = plates.currentOrigin
        val plate = Box3(bed.scene.buildVolume.min + origin, bed.scene.buildVolume.max + origin)
        shadowMask?.takeIf { it.first == plate }?.let { return it.second }
        shadowMask?.second?.release()
        val x0 = plate.min.x.toFloat()
        val y0 = plate.min.y.toFloat()
        val x1 = plate.max.x.toFloat()
        val y1 = plate.max.y.toFloat()
        return GlVertexArray(
            GlVertexArray.floatBuffer(floatArrayOf(x0, y0, 0f, x1, y0, 0f, x1, y1, 0f, x0, y0, 0f, x1, y1, 0f, x0, y1, 0f)),
            listOf(GlProgram.POSITION to 3),
            GLES30.GL_TRIANGLES,
        ).also { shadowMask = plate to it }
    }

    /** GLCanvas3D::_render_fxaa_pass(): the frame drawn over the view through the fxaa shader. */
    private fun renderFxaa(program: GlProgram, target: GlOffscreenFrame) {
        val quad = screenQuad()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        program.use()
        program.setMatrix4("view_model_matrix", IDENTITY)
        program.setMatrix4("projection_matrix", IDENTITY)
        program.setInt("uniform_texture", 0)
        program.setVec2("inv_tex_size", 1f / target.width, 1f / target.height)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.texture)
        quad.draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /** RenderStats::get_fps_and_reset_if_needed() and increment_fps_counter(), in their order. */
    private fun measureFps() {
        val now = System.nanoTime()
        val elapsedMs = (now - measuringStart) / 1_000_000L
        if (elapsedMs > 1_000 || fpsOut == -1) {
            measuringStart = now
            fpsOut = if (elapsedMs > 0) (1_000.0 * fpsRunning / elapsedMs).toInt() else 0
            fpsRunning = 0
            onFps?.invoke(fpsOut)
        }
        ++fpsRunning
    }

    private fun renderScene(programs: Programs, frame: SceneFrame) {

        // GLCanvas3D::_render_background() draws one colour for the 3D view.
        GLES30.glClearColor(frame.background[0], frame.background[1], frame.background[2], frame.background[3])
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val bottom = !frame.lookingDownward
        val plates = plates
        gpuBed?.let { bed ->
            // Bed3D::render_internal(): the axes first.
            if (frame.showAxes) renderAxes(programs.flat, bed, frame)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            // Bed3D stands under the current plate (Plater::set_bed_position).
            if (!bottom) renderBedModel(programs.hotbed, bed, frame, plates.currentOrigin)
            // PartPlateList::render()
            plates.origins.forEachIndexed { index, origin ->
                renderPlate(programs, bed, frame, bottom, origin, index, selected = index == plates.current, name = plates.names.getOrNull(index))
            }
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        }
        if (frame.shadows) renderCastShadows(programs.flat, frame)
        // GLCanvas3D::_render() for the preview: the G-code after the bed.
        layer?.draw(frame.view.toFloatArray(), frame.projection)
        // GLCanvas3D::_render_objects(): "phong" in the realistic view with Phong shading, "gouraud" otherwise.
        renderObjects(if (frame.phong) programs.phong else programs.gouraud, frame)
        renderWireframes(programs.flat, frame)
        renderSelection(programs.flat, frame)
        frame.clearance?.let { renderClearance(programs.flat, it, frame) }
        frame.gizmo?.let { renderGizmo(programs, it, frame) }
    }

    private fun uploadChanges() {
        val bed: SceneBed?
        val newObjects: List<SceneObject>?
        synchronized(lock) {
            bed = if (bedChanged) pendingBed else null
            val replaceBed = bedChanged
            bedChanged = false
            newObjects = if (objectsChanged) pendingObjects else null
            objectsChanged = false
            if (layerChanged) {
                if (layer !== pendingLayer) layer?.release()
                layer = pendingLayer
                layerChanged = false
            }
            if (replaceBed) {
                gpuBed?.release()
                gpuBed = bed?.let(::GpuBed)
            }
        }
        if (newObjects != null) {
            val keys = newObjects.mapTo(HashSet()) { it.key }
            gpuObjects.keys.filter { it !in keys }.forEach { gpuObjects.remove(it)?.second?.release() }
            newObjects.forEach { sceneObject ->
                val uploaded = gpuObjects[sceneObject.key]
                if (uploaded?.first !== sceneObject.mesh) {
                    uploaded?.second?.release()
                    gpuObjects[sceneObject.key] = sceneObject.mesh to meshArray(sceneObject.mesh)
                }
            }
            val wired = newObjects.filter(SceneObject::wireframe).associateBy(SceneObject::key)
            gpuWireframes.keys.filter { it !in wired }.forEach { gpuWireframes.remove(it)?.release() }
            wired.forEach { (key, sceneObject) ->
                gpuWireframes.getOrPut(key) {
                    GlVertexArray(GlVertexArray.floatBuffer(triangleEdges(sceneObject.mesh)), listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)
                }
            }
            objects = newObjects
        }
    }

    /** Bed3D::render_model(). */
    private fun renderBedModel(program: GlProgram, bed: GpuBed, frame: SceneFrame, origin: Vec3) {
        val model = bed.model ?: return
        val world = Affine3().translated(bed.scene.modelOffset + origin)
        program.use()
        program.setFloat("emission_factor", 0f)
        program.setMatrix4("volume_world_matrix", world.toFloatArray())
        program.setMatrix4("view_model_matrix", (frame.view * world).toFloatArray())
        program.setMatrix4("projection_matrix", frame.projection)
        program.setMatrix3("view_normal_matrix", normalMatrix(frame.view, world))
        program.setInt("print_volume.type", -1)
        val color = if (frame.dark) BED_MODEL_COLOR_DARK else BED_MODEL_COLOR
        program.setVec4("uniform_color", color[0], color[1], color[2], color[3])
        model.draw()
    }

    /**
     * PartPlate::render() for the plate at [index], standing at [origin]: the
     * background of a plate other than the current one, the excluded area, the
     * grid, the current plate's texture, and the plate's number.
     */
    private fun renderPlate(programs: Programs, bed: GpuBed, frame: SceneFrame, bottom: Boolean, origin: Vec3, index: Int, selected: Boolean, name: String?) {
        val view = (frame.view * Affine3().translated(origin)).toFloatArray()
        val flat = programs.flat
        flat.use()
        flat.setMatrix4("view_model_matrix", view)
        flat.setMatrix4("projection_matrix", frame.projection)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        if (!bottom) {
            GLES30.glDepthMask(false)
            // render_background(): the current plate has none.
            if (!selected) {
                val background = if (frame.dark) UNSELECT_DARK_COLOR else UNSELECT_COLOR
                flat.setVec4("uniform_color", background[0], background[1], background[2], background[3])
                bed.plateTriangles.draw()
            }
            // render_exclude_area()
            val exclude = if (selected) EXCLUDE_AREA_COLOR else EXCLUDE_AREA_UNSELECTED_COLOR
            flat.setVec4("uniform_color", exclude[0], exclude[1], exclude[2], exclude[3])
            bed.excludeTriangles.draw()
            GLES30.glDepthMask(true)
        }

        // render_grid(), while show_plate_gridlines is on
        if (frame.showGridlines) {
            val lineColor = when {
                bottom -> LINE_BOTTOM_COLOR
                selected && frame.dark -> LINE_TOP_SELECTED_DARK_COLOR
                selected -> LINE_TOP_SELECTED_COLOR
                frame.dark -> LINE_TOP_DARK_COLOR
                else -> LINE_TOP_COLOR
            }
            flat.setVec4("uniform_color", lineColor[0], lineColor[1], lineColor[2], lineColor[3])
            GLES30.glLineWidth(lineWidth(1f * frame.pixelScale))
            bed.thinGridLines.draw()
            GLES30.glLineWidth(lineWidth(2f * frame.pixelScale))
            bed.boldGridLines.draw()
            GLES30.glLineWidth(1f)
        }
        GLES30.glDisable(GLES30.GL_BLEND)

        // render_logo() and render_logo_texture(), for the current plate
        val texture = bed.texture
        if (!bottom && selected && texture != null) {
            val printbed = programs.printbed
            printbed.use()
            printbed.setMatrix4("view_model_matrix", view)
            printbed.setMatrix4("projection_matrix", frame.projection)
            printbed.setBoolean("transparent_background", false)
            printbed.setBoolean("svg_source", false)
            printbed.setInt("in_texture", 0)
            GLES30.glDepthMask(false)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.id)
            bed.plateTriangles.draw()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDepthMask(true)
        }

        // render_only_numbers()
        if (index < MAX_PLATE_COUNT) {
            val label = labelTextures.getOrPut(index) { PlateLabels.image(index).let { GlTexture(it.width, it.height, it.rgba) } }
            val printbed = programs.printbed
            printbed.use()
            printbed.setMatrix4("view_model_matrix", view)
            printbed.setMatrix4("projection_matrix", frame.projection)
            printbed.setBoolean("transparent_background", bottom)
            printbed.setBoolean("svg_source", false)
            printbed.setInt("in_texture", 0)
            GLES30.glDepthMask(false)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, label.id)
            bed.labelQuad.draw()
            // render_plate_name_texture()
            if (name != null) {
                val (nameTexture, quad) = bed.nameLabel(name)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, nameTexture.id)
                quad.draw()
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDepthMask(true)
        }
    }

    /**
     * Bed3D::Axes::render(): a cylinder along each axis from the origin, in the
     * axis's colour, drawn flat as OrcaSlicer draws them.
     */
    private fun renderAxes(program: GlProgram, bed: GpuBed, frame: SceneFrame) {
        val origin = Vec3(0.0, 0.0, GROUND_Z.toDouble())
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        program.use()
        program.setMatrix4("projection_matrix", frame.projection)
        listOf(
            AXIS_X_COLOR to Vec3(0.0, 0.5 * Math.PI, 0.0),
            AXIS_Y_COLOR to Vec3(-0.5 * Math.PI, 0.0, 0.0),
            AXIS_Z_COLOR to Vec3.ZERO,
        ).forEach { (color, rotation) ->
            val transform = Affine3.assemble(origin, rotation, Vec3(1.0, 1.0, 1.0))
            program.setMatrix4("view_model_matrix", (frame.view * transform).toFloatArray())
            program.setVec4("uniform_color", color[0], color[1], color[2], color[3])
            bed.axis.draw()
        }
    }

    /**
     * GLCanvas3D::_render_cast_shadows_on_plate(): the stencil marks the
     * current plate's build volume, then every printable model part flattened
     * onto it along a light that turns with the view, and the marked pixels
     * are darkened over the whole view.
     */
    private fun renderCastShadows(program: GlProgram, frame: SceneFrame) {
        val volumes = objects.filter { it.printable && !it.modifier && !it.overlay && it.index != WIPE_TOWER_INDEX }
        if (volumes.isEmpty()) return

        // Fixed light direction (pointing downward at an angle), defined in eye space and turned to world space
        // with the inverse view rotation.
        val toLight = (frame.view.linearRow(0) * LIGHT_DIR_EYE.x + frame.view.linearRow(1) * LIGHT_DIR_EYE.y + frame.view.linearRow(2) * LIGHT_DIR_EYE.z).normalized()
        val ray = -toLight
        if (abs(ray.z) < 1e-6) return
        // Shadow projection matrix - flattens geometry onto Z=0 plane along light direction, with a bias against acne.
        val shadowProjection = Affine3.fromRows(
            doubleArrayOf(1.0, 0.0, -ray.x / ray.z, 0.0),
            doubleArrayOf(0.0, 1.0, -ray.y / ray.z, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.01),
        )

        // PASS 0: the stencil mask of the build plate (value = 1).
        GLES30.glEnable(GLES30.GL_STENCIL_TEST)
        GLES30.glStencilMask(0xFF)
        GLES30.glClearStencil(0)
        GLES30.glClear(GLES30.GL_STENCIL_BUFFER_BIT)
        GLES30.glStencilFunc(GLES30.GL_ALWAYS, 1, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_REPLACE)
        GLES30.glColorMask(false, false, false, false)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        program.use()
        program.setMatrix4("projection_matrix", frame.projection)
        program.setMatrix4("view_model_matrix", frame.view.toFloatArray())
        plateMask()?.draw()

        // PASS 1: the objects' shadows projected onto the plate (stencil 1 becomes 2).
        GLES30.glStencilFunc(GLES30.GL_EQUAL, 1, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_INCR)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_ALWAYS)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        for (volume in volumes) {
            val mesh = gpuObjects[volume.key]?.second ?: continue
            program.setMatrix4("view_model_matrix", (frame.view * shadowProjection * volume.world).toFloatArray())
            mesh.draw()
        }

        // PASS 2: the shadow colour where the stencil is 2.
        GLES30.glColorMask(true, true, true, true)
        GLES30.glStencilFunc(GLES30.GL_EQUAL, 2, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_KEEP)
        GLES30.glStencilMask(0x00)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        program.setMatrix4("view_model_matrix", IDENTITY)
        program.setMatrix4("projection_matrix", IDENTITY)
        program.setVec4("uniform_color", 0f, 0f, 0f, SHADOW_ALPHA)
        screenQuad().draw()

        // The state as it was.
        GLES30.glDepthMask(true)
        GLES30.glDepthFunc(GLES30.GL_LESS)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_STENCIL_TEST)
        GLES30.glStencilMask(0xFF)
    }

    /** SequentialPrintClearance::render() with its fill, as a failing validation shows it. */
    private fun renderClearance(program: GlProgram, clearance: SceneClearance, frame: SceneFrame) {
        val arrays = clearanceArrays?.takeIf { it.first === clearance }?.second ?: run {
            clearanceArrays?.second?.forEach(GlVertexArray::release)
            listOf(clearance.lines to GLES30.GL_LINES, clearance.fill to GLES30.GL_TRIANGLES, clearance.heightLimits to GLES30.GL_TRIANGLES)
                .map { (points, mode) -> GlVertexArray(GlVertexArray.floatBuffer(points), listOf(GlProgram.POSITION to 3), mode) }
                .also { clearanceArrays = clearance to it }
        }
        val (perimeter, fill, heightLimit) = arrays
        program.use()
        program.setMatrix4("view_model_matrix", frame.view.toFloatArray())
        program.setMatrix4("projection_matrix", frame.projection)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glLineWidth(lineWidth(frame.pixelScale))
        program.setVec4("uniform_color", CLEARANCE_FILL_COLOR[0], CLEARANCE_FILL_COLOR[1], CLEARANCE_FILL_COLOR[2], CLEARANCE_FILL_COLOR[3])
        perimeter.draw()
        // The fill keeps the colour set_polygons() gave its geometry.
        program.setVec4("uniform_color", 0.8f, 0.8f, 1f, 0.5f)
        fill.draw()
        program.setVec4("uniform_color", CLEARANCE_FILL_COLOR[0], CLEARANCE_FILL_COLOR[1], CLEARANCE_FILL_COLOR[2], CLEARANCE_FILL_COLOR[3])
        heightLimit.draw()
        GLES30.glLineWidth(1f)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /** GLCanvas3D::_render_objects() and GLVolumeCollection::render() for opaque volumes. */
    private fun renderObjects(program: GlProgram, frame: SceneFrame) {
        if (objects.isEmpty()) return
        val bed = gpuBed?.scene
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        program.use()
        // opengl_phong_ssao, which the phong shader leaves to the SSAO pass.
        if (frame.phong) program.setBoolean("enable_ssao", frame.ssao)
        program.setFloat("z_far", frame.farZ)
        program.setFloat("z_near", frame.nearZ)
        program.setMatrix4("projection_matrix", frame.projection)
        program.setVec2("z_range", -Float.MAX_VALUE, Float.MAX_VALUE)
        // ClippingPlane::ClipsNothing(), or the plane the cut gizmo shows its section at.
        val clippingPlane = frame.clippingPlane
        if (clippingPlane != null) {
            program.setVec4("clipping_plane", clippingPlane[0], clippingPlane[1], clippingPlane[2], clippingPlane[3])
        } else {
            program.setVec4("clipping_plane", 0f, 0f, 1f, Float.MAX_VALUE)
        }
        // GLGizmoCut3D::apply_color_clip_plane_colors() for the planar cut.
        val colorClipPlane = frame.colorClipPlane
        program.setBoolean("use_color_clip_plane", colorClipPlane != null)
        if (colorClipPlane != null) {
            program.setVec4("color_clip_plane", colorClipPlane[0], colorClipPlane[1], colorClipPlane[2], colorClipPlane[3])
            val (upper, lower) = frame.colorClipColors
            program.setVec4("uniform_color_clip_plane_1", upper[0], upper[1], upper[2], upper[3])
            program.setVec4("uniform_color_clip_plane_2", lower[0], lower[1], lower[2], lower[3])
        }
        program.setBoolean("is_outline", false)
        program.setBoolean("slope.actived", false)
        printVolume = bed?.let { scene ->
            // The current plate's rectangular build volume, grown by BuildVolume::SceneEpsilon.
            val origin = plates.currentOrigin
            Box3(scene.buildVolume.min + origin, scene.buildVolume.max + origin)
        }
        // GLCanvas3D::_render_objects() with the variable layer height on:
        // the model parts of its object are drawn by render_volumes().
        val layerEditing = frame.layerEditing
        val shown = if (layerEditing == null) objects else objects.filterNot(layerEditing::draws)
        // GLVolumeCollection::render(): the opaque volumes first, then the
        // transparent ones blended over them, with the depth buffer kept.
        for (sceneObject in shown.filterNot(SceneObject::transparent).filterNot(SceneObject::overlay)) {
            drawVolume(program, frame, sceneObject)
        }
        if (layerEditing != null) {
            programs?.let { renderLayerVolumes(it.variableLayerHeight, frame, layerEditing) }
            program.use()
        }
        // The painted triangles lie on the object's own surface, so they are
        // drawn with a bias that keeps them in front of it.
        val painted = shown.filter(SceneObject::overlay)
        if (painted.isNotEmpty()) {
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(-1f, -1f)
            for (sceneObject in painted) {
                drawVolume(program, frame, sceneObject)
            }
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        }
        val transparent = shown.filter(SceneObject::transparent)
        if (transparent.isNotEmpty()) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            for (sceneObject in transparent) {
                drawVolume(program, frame, sceneObject)
            }
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /**
     * LayersEditing::generate_layer_height_texture(), when the layers changed,
     * and the texture as render_volumes() loads it: both levels of detail.
     */
    private fun updateLayerTexture(editing: SceneLayerEditing) {
        val view = editing.view
        if (layerTextureId != 0 && layerTextureOf == view) return
        val texture = layerTexture ?: LayerHeightTexture().also { layerTexture = it }
        val generated = layerTextureOf?.let {
            it.layers == view.layers && it.layerHeight == view.layerHeight && it.minLayerHeight == view.minLayerHeight &&
                it.maxLayerHeight == view.maxLayerHeight && it.objectPrintZHeight == view.objectPrintZHeight
        } == true
        if (!generated) {
            texture.generate(view.layers, view.layerHeight, view.minLayerHeight, view.maxLayerHeight, view.objectPrintZHeight)
        }
        if (layerTextureId == 0) {
            // LayersEditing::init()
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            layerTextureId = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, layerTextureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAX_LEVEL, 1)
        } else if (generated) {
            // Only the cursor or the band moved: the texture stays.
            layerTextureOf = view
            return
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, layerTextureId)
        val data = texture.data
        data.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, texture.width, texture.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data)
        data.position(texture.secondLevel)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA, texture.width / 2, texture.height / 2, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data)
        data.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        layerTextureOf = view
    }

    /** The uniforms of the variable_layer_height shader that the object and the bar share. */
    private fun setLayerUniforms(program: GlProgram, view: LayerEditingView) {
        val texture = layerTexture ?: return
        val maxZ = view.objectMaxZ.toFloat()
        program.setFloat("z_to_texture_row", (texture.cells - 1).toFloat() / (texture.width.toFloat() * maxZ))
        program.setFloat("z_texture_row_to_normalized", 1f / texture.height.toFloat())
        // The finger off the bar puts the cursor far below the object (get_cursor_z_relative()).
        program.setFloat("z_cursor", view.cursorZ?.toFloat() ?: (maxZ * OUTSIDE_BAR))
        program.setFloat("z_cursor_band_width", view.bandWidth.toFloat())
        program.setInt("z_texture", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, layerTextureId)
    }

    /** LayersEditing::render_volumes(): the object's model parts in the colours of their layers. */
    private fun renderLayerVolumes(program: GlProgram, frame: SceneFrame, editing: SceneLayerEditing) {
        if (layerTextureId == 0) return
        program.use()
        setLayerUniforms(program, editing.view)
        program.setMatrix4("projection_matrix", frame.projection)
        for (sceneObject in objects) {
            if (!editing.draws(sceneObject) || sceneObject.overlay) continue
            val mesh = gpuObjects[sceneObject.key]?.second ?: continue
            program.setMatrix4("volume_world_matrix", sceneObject.world.toFloatArray())
            program.setFloat("object_max_z", 0f)
            program.setMatrix4("view_model_matrix", (frame.view * sceneObject.world).toFloatArray())
            program.setMatrix3("view_normal_matrix", normalMatrix(frame.view, sceneObject.world))
            val leftHanded = sceneObject.world.isLeftHanded
            if (leftHanded) GLES30.glFrontFace(GLES30.GL_CW)
            mesh.draw()
            if (leftHanded) GLES30.glFrontFace(GLES30.GL_CCW)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * LayersEditing::render_active_object_annotations(): the bar where the
     * page placed it, the layers' colours along it from the bed at its bottom
     * to the object's top at its top.
     */
    private fun renderLayerBar(program: GlProgram, editing: SceneLayerEditing) {
        val bar = editing.bar ?: return
        if (layerTextureId == 0 || viewportWidth <= 0 || viewportHeight <= 0 || editing.view.objectMaxZ <= 0.0) return
        val l = 2f * bar.left / viewportWidth - 1f
        val r = 2f * bar.right / viewportWidth - 1f
        val t = 1f - 2f * bar.top / viewportHeight
        val b = 1f - 2f * bar.bottom / viewportHeight
        val corners = floatArrayOf(l, b, r, t)
        val quad = layerBar?.takeIf { it.first.contentEquals(corners) }?.second ?: run {
            layerBar?.second?.release()
            GlVertexArray(
                GlVertexArray.floatBuffer(
                    floatArrayOf(
                        l, b, 0f, 0f, 0f, 1f, 0f, 0f, r, b, 0f, 0f, 0f, 1f, 1f, 0f, r, t, 0f, 0f, 0f, 1f, 1f, 1f,
                        r, t, 0f, 0f, 0f, 1f, 1f, 1f, l, t, 0f, 0f, 0f, 1f, 0f, 1f, l, b, 0f, 0f, 0f, 1f, 0f, 0f,
                    ),
                ),
                listOf(GlProgram.POSITION to 3, GlProgram.NORMAL to 3, GlProgram.TEX_COORD to 2),
                GLES30.GL_TRIANGLES,
            ).also { layerBar = corners to it }
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        program.use()
        setLayerUniforms(program, editing.view)
        program.setFloat("object_max_z", editing.view.objectMaxZ.toFloat())
        program.setMatrix4("view_model_matrix", IDENTITY)
        program.setMatrix4("projection_matrix", IDENTITY)
        program.setMatrix3("view_normal_matrix", IDENTITY3)
        quad.draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /**
     * GLGizmoSimplify::on_render() with "Show wireframe": the edges of the
     * decimated mesh drawn over it in white, where they are not hidden.
     */
    private fun renderWireframes(program: GlProgram, frame: SceneFrame) {
        val wired = objects.filter(SceneObject::wireframe)
        if (wired.isEmpty()) return
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        program.use()
        program.setMatrix4("projection_matrix", frame.projection)
        program.setVec4("uniform_color", 1f, 1f, 1f, 1f)
        GLES30.glLineWidth(lineWidth(1f * frame.pixelScale))
        for (sceneObject in wired) {
            val lines = gpuWireframes[sceneObject.key] ?: continue
            program.setMatrix4("view_model_matrix", (frame.view * sceneObject.world).toFloatArray())
            lines.draw()
        }
        GLES30.glLineWidth(1f)
        GLES30.glDepthFunc(GLES30.GL_LESS)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /** GLVolume::render(): one volume with the shader's uniforms for it. */
    private fun drawVolume(program: GlProgram, frame: SceneFrame, sceneObject: SceneObject) {
        val mesh = gpuObjects[sceneObject.key]?.second ?: return
        // GLVolumeCollection::render(): a volume across the current plate's
        // boundary is darkened outside it; the others are drawn as they are.
        val volume = printVolume?.takeIf { sceneObject.partlyInside }
        if (volume != null) {
            program.setInt("print_volume.type", 0)
            program.setVec4(
                "print_volume.xy_data",
                (volume.min.x - SCENE_EPSILON).toFloat(),
                (volume.min.y - SCENE_EPSILON).toFloat(),
                (volume.max.x + SCENE_EPSILON).toFloat(),
                (volume.max.y + SCENE_EPSILON).toFloat(),
            )
            program.setVec2("print_volume.z_data", 0f, volume.max.z.toFloat())
        } else {
            program.setInt("print_volume.type", -1)
        }
        val color = VolumeColors.render(
            sceneObject.color,
            selected = sceneObject.index in frame.selectedIndexes && !sceneObject.paintedByTool,
            printable = sceneObject.printable,
        )
        // GLGizmoPainterBase::render_triangles(): the slope of the object the
        // support painting tool draws; GLVolumeCollection::render(): the
        // canvas's overhangs on every other model part but the wipe tower.
        val slope = if (sceneObject.paintedByTool) {
            frame.slopeNormalZ
        } else {
            frame.overhangNormalZ?.takeIf { !sceneObject.modifier && sceneObject.index != WIPE_TOWER_INDEX }
        }
        program.setBoolean("slope.actived", slope != null)
        if (slope != null) program.setFloat("slope.normal_z", slope)
        program.setVec4("uniform_color", color.red, color.green, color.blue, color.alpha)
        program.setMatrix4("volume_world_matrix", sceneObject.world.toFloatArray())
        program.setMatrix4("view_model_matrix", (frame.view * sceneObject.world).toFloatArray())
        program.setMatrix3("view_normal_matrix", normalMatrix(frame.view, sceneObject.world))
        program.setMatrix3("slope.volume_world_normal_matrix", normalMatrix(Affine3(), sceneObject.world))
        // GLVolume::render(): a mirrored volume turns its faces.
        val leftHanded = sceneObject.world.isLeftHanded
        if (leftHanded) GLES30.glFrontFace(GLES30.GL_CW)
        // GLVolumeCollection::render(): a selected volume with "Outline" on.
        val depth = if (frame.outline && sceneObject.index in frame.selectedIndexes && !sceneObject.paintedByTool) depthTarget() else null
        if (depth != null) renderWithOutline(program, mesh, depth) else mesh.draw()
        if (leftHanded) GLES30.glFrontFace(GLES30.GL_CCW)
    }

    /**
     * GLVolume::render_with_outline(): the volume drawn into a depth texture
     * alone, then drawn as usual with that texture, where the gouraud shader
     * darkens or lightens its silhouette.
     */
    private fun renderWithOutline(program: GlProgram, mesh: GlVertexArray, depth: GlDepthTarget) {
        // The frame is drawn on the surface, or off screen for FXAA.
        val target = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, target, 0)
        depth.bind()
        mesh.draw()
        depth.unbind(target[0])
        program.setBoolean("is_outline", true)
        program.setVec2("screen_size", depth.width.toFloat(), depth.height.toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depth.texture)
        program.setInt("depth_tex", 0)
        mesh.draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        program.setBoolean("is_outline", false)
    }

    /** The depth texture of the outline at the view's size, made again when the view changes size; null where it cannot be. */
    private fun depthTarget(): GlDepthTarget? {
        val current = outlineDepth
        if (current != null && current.width == viewportWidth && current.height == viewportHeight) return current.takeIf { it.complete }
        current?.release()
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        return GlDepthTarget(viewportWidth, viewportHeight).also { outlineDepth = it }.takeIf { it.complete }
    }

    /**
     * Selection::render() in the world reference system: white brackets at the
     * corners of the bounding box, with arrows under it when auto drop is off.
     */
    private fun renderSelection(program: GlProgram, frame: SceneFrame) {
        // GLCanvas3D::_render_selection(): not while the cut gizmo or the measuring tool runs.
        if (frame.colorClipPlane != null || frame.selectionHidden) return
        // The desktop app draws one box around the whole selection; a plate of
        // a phone holds few objects, so each selected one gets its brackets,
        // around the copy and the parts that belong to it together.
        for ((_, volumes) in objects.filter { it.index in frame.selectedIndexes }.groupBy(SceneObject::index)) {
            val box = volumes.map(SceneObject::bounds).reduce(Box3::merge)
            renderSelectionOf(program, frame, box, volumes.first().autoDrop)
        }
    }

    private fun renderSelectionOf(program: GlProgram, frame: SceneFrame, box: Box3, autoDrop: Boolean) {
        val lines = selectionBox?.takeIf { it.first == box && it.second == autoDrop }?.third ?: run {
            selectionBox?.third?.release()
            GlVertexArray(GlVertexArray.floatBuffer(boundingBoxBrackets(box, autoDrop)), listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)
                .also { selectionBox = Triple(box, autoDrop, it) }
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        program.use()
        program.setMatrix4("view_model_matrix", frame.view.toFloatArray())
        program.setMatrix4("projection_matrix", frame.projection)
        program.setVec4("uniform_color", 1f, 1f, 1f, 1f)
        GLES30.glLineWidth(lineWidth(2f * frame.pixelScale))
        lines.draw()
        GLES30.glLineWidth(1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /**
     * GLGizmosManager::render_current_gizmo(): over the scene, with a cleared
     * depth buffer. Lines use the flat shader, grabbers gouraud_light as
     * GLGizmoBase::render_grabbers() does.
     */
    private fun renderGizmo(programs: Programs, gizmo: GizmoFrame, frame: SceneFrame) {
        val flat = programs.flat
        if (gizmo.sceneMeshes.isNotEmpty()) {
            // GLGizmoCut3D::render_connectors(): in the scene's depth, as render_model() draws them.
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            val light = programs.gouraudLight
            light.use()
            light.setFloat("emission_factor", gizmo.emission)
            light.setMatrix4("projection_matrix", frame.projection)
            for (placed in gizmo.sceneMeshes) {
                val array = gizmoMeshes[placed.key]?.takeIf { it.first === placed.mesh }?.second ?: meshArray(placed.mesh).also { made ->
                    gizmoMeshes.put(placed.key, placed.mesh to made)?.second?.release()
                }
                light.setFloat("emission_factor", placed.emission ?: gizmo.emission)
                light.setVec4("uniform_color", placed.color.red, placed.color.green, placed.color.blue, placed.color.alpha)
                light.setMatrix4("view_model_matrix", (frame.view * placed.world).toFloatArray())
                light.setMatrix3("view_normal_matrix", normalMatrix(frame.view, placed.world))
                // PartSelection::render(): a modifier blended.
                val blend = placed.color.alpha < 1f
                if (blend) {
                    GLES30.glEnable(GLES30.GL_BLEND)
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                }
                array.draw()
                if (blend) GLES30.glDisable(GLES30.GL_BLEND)
            }
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        }
        if (gizmo.overlay.isNotEmpty()) {
            // GLGizmoCut3D::render_clipper_cut(): from both sides, over the scene
            // without depth unless the connectors' window is open.
            if (gizmo.overlayDepth) GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            flat.use()
            flat.setMatrix4("view_model_matrix", frame.view.toFloatArray())
            flat.setMatrix4("projection_matrix", frame.projection)
            for (face in gizmo.overlay) drawFace(flat, face)
            if (gizmo.overlayDepth) GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        }
        if (gizmo.sceneFaces.isNotEmpty()) {
            // GLGizmoCut3D::render_cut_plane(): blended, in the scene's depth, from both sides.
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            flat.use()
            flat.setMatrix4("view_model_matrix", (frame.view * gizmo.sceneFacesWorld).toFloatArray())
            flat.setMatrix4("projection_matrix", frame.projection)
            for (face in gizmo.sceneFaces) drawFace(flat, face)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        }
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        if (gizmo.meshes.isNotEmpty()) {
            // GLGizmoMeasure::on_render(): gouraud_light over the cleared depth, without culling.
            val light = programs.gouraudLight
            light.use()
            light.setMatrix4("projection_matrix", frame.projection)
            for (placed in gizmo.meshes) {
                val array = gizmoMeshes[placed.key]?.takeIf { it.first === placed.mesh }?.second ?: meshArray(placed.mesh).also { made ->
                    gizmoMeshes.put(placed.key, placed.mesh to made)?.second?.release()
                }
                light.setFloat("emission_factor", placed.emission ?: gizmo.emission)
                light.setVec4("uniform_color", placed.color.red, placed.color.green, placed.color.blue, placed.color.alpha)
                light.setMatrix4("view_model_matrix", (frame.view * placed.world).toFloatArray())
                light.setMatrix3("view_normal_matrix", normalMatrix(frame.view, placed.world))
                array.draw()
            }
        }

        if (gizmo.faces.isNotEmpty()) {
            // GLGizmoFlatten::on_render(): blended faces with the instance matrix, back faces culled.
            flat.use()
            flat.setMatrix4("view_model_matrix", (frame.view * gizmo.facesWorld).toFloatArray())
            flat.setMatrix4("projection_matrix", frame.projection)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            for (face in gizmo.faces) {
                val array = GlVertexArray(GlVertexArray.floatBuffer(face.triangles), listOf(GlProgram.POSITION to 3), GLES30.GL_TRIANGLES)
                flat.setVec4("uniform_color", face.color.red, face.color.green, face.color.blue, face.color.alpha)
                array.draw()
                array.release()
            }
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        flat.use()
        flat.setMatrix4("view_model_matrix", frame.view.toFloatArray())
        flat.setMatrix4("projection_matrix", frame.projection)
        for (lines in gizmo.lines) {
            if (lines.segments.isEmpty()) continue
            val array = GlVertexArray(GlVertexArray.floatBuffer(lines.segments), listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)
            flat.setVec4("uniform_color", lines.color.red, lines.color.green, lines.color.blue, lines.color.alpha)
            GLES30.glLineWidth(lineWidth(lines.width))
            array.draw()
            array.release()
        }
        GLES30.glLineWidth(1f)

        val cone = grabberCone ?: meshArray(GrabberMeshes.cone).also { grabberCone = it }
        val cube = grabberCube ?: meshArray(GrabberMeshes.cube).also { grabberCube = it }
        val sphere = grabberSphere ?: meshArray(GrabberMeshes.sphere).also { grabberSphere = it }
        val light = programs.gouraudLight
        light.use()
        light.setFloat("emission_factor", gizmo.emission)
        light.setMatrix4("projection_matrix", frame.projection)
        for (grabber in gizmo.grabbers) {
            light.setVec4("uniform_color", grabber.color.red, grabber.color.green, grabber.color.blue, grabber.color.alpha)
            light.setMatrix4("view_model_matrix", (frame.view * grabber.world).toFloatArray())
            light.setMatrix3("view_normal_matrix", normalMatrix(frame.view, grabber.world))
            when (grabber.shape) {
                GrabberShape.CONE -> cone.draw()
                GrabberShape.CUBE -> cube.draw()
                GrabberShape.SPHERE -> sphere.draw()
            }
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /** One face of a gizmo in its colour with the flat shader in use. */
    private fun drawFace(flat: GlProgram, face: GizmoFace) {
        val array = GlVertexArray(GlVertexArray.floatBuffer(face.triangles), listOf(GlProgram.POSITION to 3), GLES30.GL_TRIANGLES)
        flat.setVec4("uniform_color", face.color.red, face.color.green, face.color.blue, face.color.alpha)
        array.draw()
        array.release()
    }

    private fun lineWidth(width: Float) = width.coerceIn(lineWidthRange[0].coerceAtLeast(1f), lineWidthRange[1].coerceAtLeast(1f))

    private class Programs(assets: AssetManager) {
        val flat = GlProgram(assets, "flat")
        val flatTexture = GlProgram(assets, "flat_texture")
        val fxaa = GlProgram(assets, "fxaa")
        val gouraud = GlProgram(assets, "gouraud")
        val gouraudLight = GlProgram(assets, "gouraud_light")
        val hotbed = GlProgram(assets, "hotbed")
        val phong = GlProgram(assets, "phong")
        val ssao = GlProgram(assets, "ssao")
        val printbed = GlProgram(assets, "printbed")
        val variableLayerHeight = GlProgram(assets, "variable_layer_height")
    }

    private class GpuBed(val scene: SceneBed) {
        val model = scene.model?.let(::meshArray)
        val texture = scene.texture?.let { GlTexture(it.width, it.height, it.rgba) }
        val plateTriangles = GlVertexArray(scene.plateTriangles, listOf(GlProgram.POSITION to 3, GlProgram.TEX_COORD to 2), GLES30.GL_TRIANGLES)
        val excludeTriangles = GlVertexArray(scene.excludeTriangles, listOf(GlProgram.POSITION to 3), GLES30.GL_TRIANGLES)
        val thinGridLines = GlVertexArray(scene.thinGridLines, listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)
        val boldGridLines = GlVertexArray(scene.boldGridLines, listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)
        val labelQuad = GlVertexArray(PlateLabels.quad(scene.buildVolume), listOf(GlProgram.POSITION to 3, GlProgram.TEX_COORD to 2), GLES30.GL_TRIANGLES)

        /** Bed3D::Axes::m_arrow: smooth_cylinder(16, stem length / 75, stem length). */
        val axis = GlVertexArray(
            GlVertexArray.floatBuffer(smoothCylinder(AXIS_RESOLUTION, (scene.axisLength / 75.0).toFloat(), scene.axisLength.toFloat())),
            listOf(GlProgram.POSITION to 3),
            GLES30.GL_TRIANGLES,
        )

        /** The names written over the plates, with the squares they are drawn on, by name. */
        private val names = HashMap<String, Pair<GlTexture, GlVertexArray>>()

        fun nameLabel(name: String): Pair<GlTexture, GlVertexArray> = names.getOrPut(name) {
            val image = PlateLabels.nameImage(name)
            GlTexture(image.width, image.height, image.rgba) to
                GlVertexArray(PlateLabels.nameQuad(scene.buildVolume, image), listOf(GlProgram.POSITION to 3, GlProgram.TEX_COORD to 2), GLES30.GL_TRIANGLES)
        }

        fun release() {
            model?.release()
            texture?.release()
            labelQuad.release()
            axis.release()
            names.values.forEach { (nameTexture, quad) ->
                nameTexture.release()
                quad.release()
            }
            names.clear()
            plateTriangles.release()
            excludeTriangles.release()
            thinGridLines.release()
            boldGridLines.release()
        }
    }

    private companion object {
        // SequentialPrintClearance::render(): FILL_COLOR, while the clearance is drawn with its fill.
        val CLEARANCE_FILL_COLOR = floatArrayOf(0.7f, 0.7f, 1f, 0.5f)

        // GLCanvas3D::_render_cast_shadows_on_plate(): the light, normalized (-0.6, 0.6, 1) in eye space, and the shadows' alpha.
        val LIGHT_DIR_EYE = Vec3(-0.4574957, 0.4574957, 0.7624929).normalized()
        const val SHADOW_ALPHA = 0.4f

        // ColorRGB::X(), Y() and Z(), the colours of Bed3D's axes.
        val AXIS_X_COLOR = floatArrayOf(255 / 255f, 60 / 255f, 91 / 255f, 1f)
        val AXIS_Y_COLOR = floatArrayOf(100 / 255f, 200 / 255f, 24 / 255f, 1f)
        val AXIS_Z_COLOR = floatArrayOf(47 / 255f, 136 / 255f, 233 / 255f, 1f)
        const val AXIS_RESOLUTION = 16

        // Transform3d::Identity(), column-major.
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val IDENTITY3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        // LayersEditing::get_cursor_z_relative() off the bar.
        const val OUTSIDE_BAR = -1000f
        // UPPER_PART_COLOR and LOWER_PART_COLOR of GLGizmoCut.cpp: ColorRGBA::CYAN() and MAGENTA().
        val UPPER_PART_COLOR = floatArrayOf(0f, 1f, 1f, 1f)
        val LOWER_PART_COLOR = floatArrayOf(1f, 0f, 1f, 1f)
        // Bed3D::DEFAULT_MODEL_COLOR and DEFAULT_MODEL_COLOR_DARK
        val BED_MODEL_COLOR = floatArrayOf(0.3255f, 0.337f, 0.337f, 1f)
        val BED_MODEL_COLOR_DARK = floatArrayOf(0.255f, 0.255f, 0.283f, 1f)
        // PartPlate::render_exclude_area() for the selected plate and the others
        val EXCLUDE_AREA_COLOR = floatArrayOf(0.9f, 0.86f, 0.82f, 0.7f)
        val EXCLUDE_AREA_UNSELECTED_COLOR = floatArrayOf(0.6f, 0.6f, 0.6f, 0.3f)
        // PartPlate::UNSELECT_COLOR and UNSELECT_DARK_COLOR
        val UNSELECT_COLOR = floatArrayOf(0.82f, 0.82f, 0.82f, 1f)
        val UNSELECT_DARK_COLOR = floatArrayOf(0.384f, 0.384f, 0.412f, 1f)
        // PartPlate::LINE_TOP_SEL_COLOR, LINE_TOP_SEL_DARK_COLOR, LINE_TOP_COLOR, LINE_TOP_DARK_COLOR, LINE_BOTTOM_COLOR
        val LINE_TOP_SELECTED_COLOR = floatArrayOf(0.5294f, 0.5451f, 0.5333f, 1f)
        val LINE_TOP_SELECTED_DARK_COLOR = floatArrayOf(0.298f, 0.298f, 0.3333f, 1f)
        val LINE_TOP_COLOR = floatArrayOf(0.89f, 0.89f, 0.89f, 1f)
        val LINE_TOP_DARK_COLOR = floatArrayOf(0.431f, 0.431f, 0.463f, 1f)
        val LINE_BOTTOM_COLOR = floatArrayOf(0.8f, 0.8f, 0.8f, 0.4f)
        // MAX_PLATE_COUNT of PartPlate.hpp: the numbers there are textures for
        const val MAX_PLATE_COUNT = 36
        // BuildVolume::SceneEpsilon
        const val SCENE_EPSILON = 1e-4

        // Selection::render_bounding_box(): the auto drop arrows, in millimetres at a scale factor of 1 as on Windows.
        const val ARROW_HEAD = 2.0
        const val ARROW_GAP = 1.0
        const val ARROW_HEIGHT = 5.0

        /**
         * Selection::render_bounding_box(): at every corner, three edges a fifth
         * of the box long. Without [autoDrop], an arrow points down from each
         * bottom corner, its wings towards the middle of the footprint.
         */
        fun boundingBoxBrackets(box: Box3, autoDrop: Boolean): FloatArray {
            val min = box.min
            val max = box.max
            val size = box.size() * 0.2
            val lines = ArrayList<Float>(48 * 3)
            for (corner in box.corners()) {
                val sx = if (corner.x == min.x) size.x else -size.x
                val sy = if (corner.y == min.y) size.y else -size.y
                val sz = if (corner.z == min.z) size.z else -size.z
                for (end in listOf(
                    corner.copy(x = corner.x + sx),
                    corner.copy(y = corner.y + sy),
                    corner.copy(z = corner.z + sz),
                )) {
                    lines += corner.x.toFloat()
                    lines += corner.y.toFloat()
                    lines += corner.z.toFloat()
                    lines += end.x.toFloat()
                    lines += end.y.toFloat()
                    lines += end.z.toFloat()
                }
            }
            if (!autoDrop) {
                for (corner in listOf(min, Vec3(max.x, min.y, min.z), Vec3(max.x, max.y, min.z), Vec3(min.x, max.y, min.z))) {
                    val tip = corner.copy(z = corner.z - ARROW_GAP)
                    val base = tip.copy(z = tip.z - ARROW_HEIGHT)
                    val dirX = if (corner.x == min.x) ARROW_HEAD else -ARROW_HEAD
                    val dirY = if (corner.y == min.y) ARROW_HEAD else -ARROW_HEAD
                    for (point in listOf(
                        base, tip,
                        tip, Vec3(tip.x + dirX, tip.y, tip.z - ARROW_HEAD),
                        tip, Vec3(tip.x, tip.y + dirY, tip.z - ARROW_HEAD),
                    )) {
                        lines += point.x.toFloat()
                        lines += point.y.toFloat()
                        lines += point.z.toFloat()
                    }
                }
            }
            return lines.toFloatArray()
        }

        fun meshArray(mesh: MeshData) = GlVertexArray(
            mesh.vertices,
            listOf(GlProgram.POSITION to 3, GlProgram.NORMAL to 3),
            GLES30.GL_TRIANGLES,
        )
    }
}

/** view_normal_matrix: the view's rotation times the inverse transpose of the model's linear part. */
internal fun normalMatrix(view: Affine3, world: Affine3): FloatArray {
    val inverse = world.inverse()
    val linear = view.linearToFloatArray()
    val result = FloatArray(9)
    // (inverse)^T at column c, row r is inverse[c, r].
    for (column in 0 until 3) {
        for (row in 0 until 3) {
            var sum = 0.0
            for (k in 0 until 3) sum += linear[k * 3 + row] * inverse[column, k]
            result[column * 3 + row] = sum.toFloat()
        }
    }
    return result
}
