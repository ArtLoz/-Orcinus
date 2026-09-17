package app.orcinus.shadow.render.scene

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import app.orcinus.shadow.render.scene.gl.GlProgram
import app.orcinus.shadow.render.scene.gl.GlTexture
import app.orcinus.shadow.render.scene.gl.GlVertexArray
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import javax.microedition.khronos.egl.EGLConfig
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
    /** Index of the selected object. */
    val selectedIndex: Int?,
    /** The active gizmo on the selected object. */
    val gizmo: GizmoFrame?,
)

/**
 * Draws the plate and its objects the way OrcaSlicer's 3D canvas does
 * (GLCanvas3D::render for the 3D view): the bed model, the plate's excluded
 * area, grid, and texture, then the objects, with OrcaSlicer's own shaders and
 * colours. Scene data and frames arrive from the main thread; everything else
 * happens on the GL thread.
 */
internal class PlateRenderer(private val assets: AssetManager) : GLSurfaceView.Renderer {
    private val lock = Any()
    private var pendingBed: SceneBed? = null
    private var bedChanged = false
    private var pendingObjects: List<SceneObject> = emptyList()
    private var objectsChanged = false

    @Volatile
    private var frame: SceneFrame? = null

    private var programs: Programs? = null
    private var gpuBed: GpuBed? = null
    private val gpuObjects = LinkedHashMap<String, GlVertexArray>()
    private var objects: List<SceneObject> = emptyList()
    private var selectionBox: Triple<Box3, Boolean, GlVertexArray>? = null
    private var grabberCone: GlVertexArray? = null
    private var grabberCube: GlVertexArray? = null
    private val lineWidthRange = FloatArray(2)

    fun setBed(bed: SceneBed?) = synchronized(lock) {
        pendingBed = bed
        bedChanged = true
    }

    fun setObjects(objects: List<SceneObject>) = synchronized(lock) {
        pendingObjects = objects
        objectsChanged = true
    }

    fun setFrame(frame: SceneFrame) {
        this.frame = frame
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // A new context: names from an earlier one are gone.
        programs = Programs(assets)
        gpuBed = null
        gpuObjects.clear()
        selectionBox = null
        grabberCone = null
        grabberCube = null
        synchronized(lock) {
            bedChanged = true
            objectsChanged = true
        }
        GLES30.glGetFloatv(GLES30.GL_ALIASED_LINE_WIDTH_RANGE, lineWidthRange, 0)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        uploadChanges()
        val frame = frame
        val programs = programs
        if (frame == null || programs == null) {
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }

        // GLCanvas3D::_render_background() draws one colour for the 3D view.
        GLES30.glClearColor(frame.background[0], frame.background[1], frame.background[2], frame.background[3])
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val bottom = !frame.lookingDownward
        gpuBed?.let { bed ->
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            if (!bottom) renderBedModel(programs.hotbed, bed, frame)
            renderPlate(programs, bed, frame, bottom)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        }
        renderObjects(programs.gouraud, frame)
        renderSelection(programs.flat, frame)
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
            if (replaceBed) {
                gpuBed?.release()
                gpuBed = bed?.let(::GpuBed)
            }
        }
        if (newObjects != null) {
            val keys = newObjects.mapTo(HashSet()) { it.key }
            gpuObjects.keys.filter { it !in keys }.forEach { gpuObjects.remove(it)?.release() }
            newObjects.forEach { sceneObject ->
                gpuObjects.getOrPut(sceneObject.key) { meshArray(sceneObject.mesh) }
            }
            objects = newObjects
        }
    }

    /** Bed3D::render_model(). */
    private fun renderBedModel(program: GlProgram, bed: GpuBed, frame: SceneFrame) {
        val model = bed.model ?: return
        val world = Affine3().translated(bed.scene.modelOffset)
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

    /** PartPlate::render() for the selected plate: excluded area, grid, texture. */
    private fun renderPlate(programs: Programs, bed: GpuBed, frame: SceneFrame, bottom: Boolean) {
        val flat = programs.flat
        flat.use()
        flat.setMatrix4("view_model_matrix", frame.view.toFloatArray())
        flat.setMatrix4("projection_matrix", frame.projection)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        if (!bottom) {
            GLES30.glDepthMask(false)
            flat.setVec4("uniform_color", EXCLUDE_AREA_COLOR[0], EXCLUDE_AREA_COLOR[1], EXCLUDE_AREA_COLOR[2], EXCLUDE_AREA_COLOR[3])
            bed.excludeTriangles.draw()
            GLES30.glDepthMask(true)
        }

        // render_grid()
        val lineColor = when {
            bottom -> LINE_BOTTOM_COLOR
            frame.dark -> LINE_TOP_SELECTED_DARK_COLOR
            else -> LINE_TOP_SELECTED_COLOR
        }
        flat.setVec4("uniform_color", lineColor[0], lineColor[1], lineColor[2], lineColor[3])
        GLES30.glLineWidth(lineWidth(1f * frame.pixelScale))
        bed.thinGridLines.draw()
        GLES30.glLineWidth(lineWidth(2f * frame.pixelScale))
        bed.boldGridLines.draw()
        GLES30.glLineWidth(1f)
        GLES30.glDisable(GLES30.GL_BLEND)

        // render_logo() and render_logo_texture()
        val texture = bed.texture
        if (!bottom && texture != null) {
            val printbed = programs.printbed
            printbed.use()
            printbed.setMatrix4("view_model_matrix", frame.view.toFloatArray())
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
    }

    /** GLCanvas3D::_render_objects() and GLVolumeCollection::render() for opaque volumes. */
    private fun renderObjects(program: GlProgram, frame: SceneFrame) {
        if (objects.isEmpty()) return
        val bed = gpuBed?.scene
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        program.use()
        program.setFloat("z_far", frame.farZ)
        program.setFloat("z_near", frame.nearZ)
        program.setMatrix4("projection_matrix", frame.projection)
        program.setVec2("z_range", -Float.MAX_VALUE, Float.MAX_VALUE)
        // ClippingPlane::ClipsNothing()
        program.setVec4("clipping_plane", 0f, 0f, 1f, Float.MAX_VALUE)
        program.setBoolean("use_color_clip_plane", false)
        program.setBoolean("is_outline", false)
        program.setBoolean("slope.actived", false)
        if (bed != null) {
            // A rectangular build volume, grown by BuildVolume::SceneEpsilon.
            val volume = bed.buildVolume
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
        for (sceneObject in objects) {
            val mesh = gpuObjects[sceneObject.key] ?: continue
            val color = VolumeColors.render(sceneObject.color, selected = sceneObject.index == frame.selectedIndex)
            program.setVec4("uniform_color", color.red, color.green, color.blue, color.alpha)
            program.setMatrix4("volume_world_matrix", sceneObject.world.toFloatArray())
            program.setMatrix4("view_model_matrix", (frame.view * sceneObject.world).toFloatArray())
            program.setMatrix3("view_normal_matrix", normalMatrix(frame.view, sceneObject.world))
            program.setMatrix3("slope.volume_world_normal_matrix", normalMatrix(Affine3(), sceneObject.world))
            mesh.draw()
        }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    /**
     * Selection::render() in the world reference system: white brackets at the
     * corners of the bounding box, with arrows under it when auto drop is off.
     */
    private fun renderSelection(program: GlProgram, frame: SceneFrame) {
        val selected = objects.firstOrNull { it.index == frame.selectedIndex } ?: return
        val box = selected.bounds
        val autoDrop = selected.autoDrop
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
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val flat = programs.flat
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
        val light = programs.gouraudLight
        light.use()
        light.setFloat("emission_factor", 0.1f)
        light.setMatrix4("projection_matrix", frame.projection)
        for (grabber in gizmo.grabbers) {
            light.setVec4("uniform_color", grabber.color.red, grabber.color.green, grabber.color.blue, grabber.color.alpha)
            light.setMatrix4("view_model_matrix", (frame.view * grabber.world).toFloatArray())
            light.setMatrix3("view_normal_matrix", normalMatrix(frame.view, grabber.world))
            when (grabber.shape) {
                GrabberShape.CONE -> cone.draw()
                GrabberShape.CUBE -> cube.draw()
            }
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    private fun lineWidth(width: Float) = width.coerceIn(lineWidthRange[0].coerceAtLeast(1f), lineWidthRange[1].coerceAtLeast(1f))

    private class Programs(assets: AssetManager) {
        val flat = GlProgram(assets, "flat")
        val gouraud = GlProgram(assets, "gouraud")
        val gouraudLight = GlProgram(assets, "gouraud_light")
        val hotbed = GlProgram(assets, "hotbed")
        val printbed = GlProgram(assets, "printbed")
    }

    private class GpuBed(val scene: SceneBed) {
        val model = scene.model?.let(::meshArray)
        val texture = scene.texture?.let { GlTexture(it.width, it.height, it.rgba) }
        val plateTriangles = GlVertexArray(scene.plateTriangles, listOf(GlProgram.POSITION to 3, GlProgram.TEX_COORD to 2), GLES30.GL_TRIANGLES)
        val excludeTriangles = GlVertexArray(scene.excludeTriangles, listOf(GlProgram.POSITION to 3), GLES30.GL_TRIANGLES)
        val thinGridLines = GlVertexArray(scene.thinGridLines, listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)
        val boldGridLines = GlVertexArray(scene.boldGridLines, listOf(GlProgram.POSITION to 3), GLES30.GL_LINES)

        fun release() {
            model?.release()
            texture?.release()
            plateTriangles.release()
            excludeTriangles.release()
            thinGridLines.release()
            boldGridLines.release()
        }
    }

    private companion object {
        // Bed3D::DEFAULT_MODEL_COLOR and DEFAULT_MODEL_COLOR_DARK
        val BED_MODEL_COLOR = floatArrayOf(0.3255f, 0.337f, 0.337f, 1f)
        val BED_MODEL_COLOR_DARK = floatArrayOf(0.255f, 0.255f, 0.283f, 1f)
        // PartPlate::render_exclude_area() for the selected plate
        val EXCLUDE_AREA_COLOR = floatArrayOf(0.9f, 0.86f, 0.82f, 0.7f)
        // PartPlate::LINE_TOP_SEL_COLOR, LINE_TOP_SEL_DARK_COLOR, LINE_BOTTOM_COLOR
        val LINE_TOP_SELECTED_COLOR = floatArrayOf(0.5294f, 0.5451f, 0.5333f, 1f)
        val LINE_TOP_SELECTED_DARK_COLOR = floatArrayOf(0.298f, 0.298f, 0.3333f, 1f)
        val LINE_BOTTOM_COLOR = floatArrayOf(0.8f, 0.8f, 0.8f, 0.4f)
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

        /** view_normal_matrix: the view's rotation times the inverse transpose of the model's linear part. */
        fun normalMatrix(view: Affine3, world: Affine3): FloatArray {
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
    }
}
