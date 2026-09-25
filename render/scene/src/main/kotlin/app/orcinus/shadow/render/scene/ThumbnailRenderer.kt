package app.orcinus.shadow.render.scene

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.render.scene.gl.GlProgram
import app.orcinus.shadow.render.scene.gl.GlVertexArray
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Vec3
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The thumbnails OrcaSlicer writes into G-code for the printer's screen
 * (GLCanvas3D::render_thumbnail with the orthographic camera the plater asks
 * for when it exports G-code): the printable model parts that stand on the
 * plate, seen from the default isometric view zoomed to them, drawn with
 * OrcaSlicer's thumbnail shader on a transparent background into a
 * multisampled framebuffer, and read back as RGBA, the bottom row first.
 *
 * The desktop app renders them with the context of its 3D view; slicing does
 * not need the plate on screen, so this renderer makes an offscreen context of
 * its own on a thread of its own for every set of thumbnails.
 */
class ThumbnailRenderer(context: Context) {
    private val assets = context.applicationContext.assets
    private val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "orca-thumbnails") }.asCoroutineDispatcher()

    /**
     * Renders [objects] standing on [plate] in the colours of their filaments
     * ([filamentColors], "#RRGGBB" by filament) at every one of [sizes], and
     * writes each picture to the file [fileFor] names.
     */
    suspend fun render(
        objects: List<PlateObject>,
        plate: PlateDescription,
        filamentColors: List<String>,
        sizes: List<ThumbnailSize>,
        fileFor: (ThumbnailSize) -> ScenePath,
    ): List<ThumbnailImage> = withContext(dispatcher) {
        val volumes = visibleVolumes(objects, plate, filamentColors)
        OffscreenContext().use {
            val program = GlProgram(assets, THUMBNAIL_SHADER)
            val arrays = volumes.associate { volume ->
                volume.key to GlVertexArray(volume.mesh.vertices, listOf(GlProgram.POSITION to 3, GlProgram.NORMAL to 3), GLES30.GL_TRIANGLES)
            }
            try {
                sizes.mapNotNull { size ->
                    val pixels = renderFramebuffer(size, program, volumes, arrays, buildVolume(plate)) ?: return@mapNotNull null
                    val path = fileFor(size)
                    File(path.value).outputStream().channel.use { channel -> channel.write(pixels) }
                    ThumbnailImage(size, path)
                }
            } finally {
                arrays.values.forEach(GlVertexArray::release)
                program.release()
            }
        }
    }

    /**
     * GLCanvas3D::render_thumbnail_internal() with use_plate_box: the model
     * parts of the printable copies whose bounding box lies inside the plate's
     * build volume and above the plate, without modifiers and the wipe tower
     * (ThumbnailsParams parts_only), each in the colour of its filament.
     */
    private fun visibleVolumes(objects: List<PlateObject>, plate: PlateDescription, filamentColors: List<String>): List<SceneObject> {
        val colors = filamentColors.map { parseFilamentColor(it) }
        val default = plate.filamentColor
        fun colorOf(extruder: Int): ColorRgba = colors.getOrNull(extruder - 1) ?: default
        val plateBox = buildVolume(plate).let { Box3(it.min.copy(z = -1e10), it.max) }
        val meshes = MeshCache()
        return objects.flatMap { plateObject ->
            plateObject.instances.filter { it.printable }.flatMap { instance ->
                val copy = SceneLoader.loadObject(0, plateObject, instance, colorOf(plateObject.extruderNumber), meshes)
                val parts = plateObject.parts
                    .filter { it.type == VolumeType.PART }
                    .map { part ->
                        val extruder = part.settings.extruderNumber.takeIf { it > 0 } ?: plateObject.extruderNumber
                        SceneLoader.loadPart(0, part, instance, colorOf(extruder), meshes)
                    }
                // GLVolume::simple_render() draws a painted volume in the colour
                // of every filament it is painted with.
                val painted = plateObject.paintedMeshes.map { mesh ->
                    SceneLoader.loadPaintedMesh(0, mesh, instance, colorOf(mesh.filament), meshes)
                }
                val contained = { volume: SceneObject ->
                    val hull = hullBox(volume)
                    plateBox.contains(hull) && hull.max.z > 0.0
                }
                (listOf(copy).filter(contained) + painted.takeIf { contained(copy) }.orEmpty() + parts.filter(contained))
            }
        }
    }

    /** GLCanvas3D::render_thumbnail_framebuffer(): the picture of one size, or null when the framebuffer cannot be made. */
    private fun renderFramebuffer(
        size: ThumbnailSize,
        program: GlProgram,
        volumes: List<SceneObject>,
        arrays: Map<String, GlVertexArray>,
        plateBuildVolume: Box3,
    ): ByteBuffer? {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return null
        val maxSamples = IntArray(1).also { GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, it, 0) }[0]
        val numSamples = maxSamples / 2
        val multisample = numSamples > 0

        val renderFbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, renderFbo)
        var renderTex = 0
        var renderTexBuffer = 0
        if (multisample) {
            renderTexBuffer = IntArray(1).also { GLES30.glGenRenderbuffers(1, it, 0) }[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, renderTexBuffer)
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, numSamples, GLES30.GL_RGBA8, w, h)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, renderTexBuffer)
        } else {
            renderTex = colorTexture(w, h)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, renderTex, 0)
        }
        val renderDepth = IntArray(1).also { GLES30.glGenRenderbuffers(1, it, 0) }[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, renderDepth)
        if (multisample) {
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, numSamples, GLES30.GL_DEPTH_COMPONENT24, w, h)
        } else {
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)
        }
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, renderDepth)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_COLOR_ATTACHMENT0), 0)

        var pixels: ByteBuffer? = null
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            renderInternal(w, h, program, volumes, arrays, plateBuildVolume)
            val read = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            if (multisample) {
                val resolveFbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resolveFbo)
                val resolveTex = colorTexture(w, h)
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, resolveTex, 0)
                GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_COLOR_ATTACHMENT0), 0)
                if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                    GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, renderFbo)
                    GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFbo)
                    GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR)
                    GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, resolveFbo)
                    GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, read)
                    pixels = read
                }
                GLES30.glDeleteTextures(1, intArrayOf(resolveTex), 0)
                GLES30.glDeleteFramebuffers(1, intArrayOf(resolveFbo), 0)
            } else {
                GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, read)
                pixels = read
            }
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteRenderbuffers(1, intArrayOf(renderDepth), 0)
        if (renderTexBuffer != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(renderTexBuffer), 0)
        if (renderTex != 0) GLES30.glDeleteTextures(1, intArrayOf(renderTex), 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(renderFbo), 0)
        return pixels?.also { it.rewind() }
    }

    /**
     * GLCanvas3D::render_thumbnail_internal() without picking: the camera
     * zoomed to the visible volumes, the projection tightened around the plate,
     * and every volume drawn with the thumbnail shader.
     */
    private fun renderInternal(w: Int, h: Int, program: GlProgram, volumes: List<SceneObject>, arrays: Map<String, GlVertexArray>, plateBuildVolume: Box3) {
        var volumesBox = volumes.map(SceneObject::bounds).reduceOrNull(Box3::merge) ?: Box3(Vec3.ZERO, Vec3.ZERO)
        volumesBox = Box3(volumesBox.min.copy(z = -SCENE_EPSILON), volumesBox.max)
        val width = volumesBox.max.x - volumesBox.min.x
        val depth = volumesBox.max.y - volumesBox.min.y
        val height = volumesBox.max.z - volumesBox.min.z
        volumesBox = Box3(
            Vec3(volumesBox.min.x - width * 0.01f, volumesBox.min.y - depth * 0.01f, volumesBox.min.z - height * 0.02f),
            Vec3(volumesBox.max.x + width * 0.01f, volumesBox.max.y + depth * 0.01f, volumesBox.max.z + height * 0.02f),
        )

        // A new camera looks from the default isometric direction (select_view("iso")).
        val camera = OrcaCamera()
        camera.orthographic = true
        camera.sceneBox = plateBuildVolume
        camera.setViewport(w, h)
        GLES30.glViewport(0, 0, w, h)
        camera.zoomToBox(volumesBox)
        val view = camera.viewMatrix
        camera.applyProjection(plateBuildVolume)
        val projection = FloatArray(16) { camera.projectionMatrix[it].toFloat() }

        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        // The 3D view's state the desktop app renders with: blending for its
        // colours, back faces culled.
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        program.use()
        program.setFloat("emission_factor", 0.1f)
        program.setBoolean("ban_light", false)
        program.setMatrix4("projection_matrix", projection)
        for (volume in volumes) {
            val array = arrays[volume.key] ?: continue
            val color = VolumeColors.adjustForRendering(volume.color)
            program.setVec4("uniform_color", color.red, color.green, color.blue, color.alpha)
            program.setMatrix4("volume_world_matrix", volume.world.toFloatArray())
            program.setMatrix4("view_model_matrix", (view * volume.world).toFloatArray())
            program.setMatrix3("view_normal_matrix", normalMatrix(view, volume.world))
            // GLVolume::is_left_handed(): a mirrored volume turns its faces.
            val leftHanded = volume.world.isLeftHanded
            if (leftHanded) GLES30.glFrontFace(GLES30.GL_CW)
            // The painted triangles lie on the volume's own surface, drawn over it.
            if (volume.overlay) {
                GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
                GLES30.glPolygonOffset(-1f, -1f)
            }
            array.draw()
            if (volume.overlay) GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            if (leftHanded) GLES30.glFrontFace(GLES30.GL_CCW)
        }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    }

    private fun colorTexture(w: Int, h: Int): Int {
        val texture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        return texture
    }

    /** An EGL context of its own, current on the calling thread until closed. */
    private class OffscreenContext : AutoCloseable {
        private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: EGLContext
        private val surface: EGLSurface

        init {
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL cannot be initialized" }
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No EGL configuration for OpenGL ES 3" }
            val config = configs[0]
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "The OpenGL ES 3 context cannot be created" }
            surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "The offscreen surface cannot be created" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "The offscreen context cannot be made current" }
        }

        // The display is the process's one, which the 3D view uses too, so it
        // stays initialized.
        override fun close() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }

    private companion object {
        const val THUMBNAIL_SHADER = "thumbnail"

        // BuildVolume::SceneEpsilon
        const val SCENE_EPSILON = 1e-4

        /** PartPlate::get_build_volume(), grown by BuildVolume::SceneEpsilon as render_thumbnail_internal() grows it. */
        fun buildVolume(plate: PlateDescription): Box3 {
            val geometry = plate.geometry
            val area = Box3.of(geometry.printableArea.map { Vec3(it.x, it.y, 0.0) })
            val epsilon = Vec3(SCENE_EPSILON, SCENE_EPSILON, SCENE_EPSILON)
            return Box3(area.min - epsilon, Vec3(area.max.x, area.max.y, geometry.printableHeight) + epsilon)
        }

        /** GLVolume::transformed_convex_hull_bounding_box(): the bounds of the volume's vertices in the world. */
        fun hullBox(volume: SceneObject): Box3 {
            val vertices = volume.mesh.vertices.duplicate()
            var min = Vec3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
            var max = Vec3(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            for (corner in 0 until volume.mesh.cornerCount) {
                val offset = corner * MeshFiles.FLOATS_PER_CORNER
                val point = volume.world.transformPoint(
                    Vec3(vertices.get(offset).toDouble(), vertices.get(offset + 1).toDouble(), vertices.get(offset + 2).toDouble()),
                )
                min = Vec3(minOf(min.x, point.x), minOf(min.y, point.y), minOf(min.z, point.z))
                max = Vec3(maxOf(max.x, point.x), maxOf(max.y, point.y), maxOf(max.z, point.z))
            }
            return Box3(min, max)
        }

        fun Box3.contains(other: Box3) =
            other.min.x >= min.x && other.min.y >= min.y && other.min.z >= min.z &&
                other.max.x <= max.x && other.max.y <= max.y && other.max.z <= max.z
    }
}
