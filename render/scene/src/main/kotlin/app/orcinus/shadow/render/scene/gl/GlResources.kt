package app.orcinus.shadow.render.scene.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// Thin wrappers over OpenGL ES 3.0 objects. Every call runs on the GL thread
// with the context current; objects die with the context and are created
// again when it is recreated.

/** One of OrcaSlicer's shader programs, loaded from the assets converted at build time. */
internal class GlProgram(assets: AssetManager, name: String) {
    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, assets.readShader("$name.vs"), "$name.vs")
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, assets.readShader("$name.fs"), "$name.fs")
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex)
        GLES30.glAttachShader(id, fragment)
        // Fixed locations let one vertex array serve every program.
        GLES30.glBindAttribLocation(id, POSITION, "v_position")
        GLES30.glBindAttribLocation(id, NORMAL, "v_normal")
        GLES30.glBindAttribLocation(id, TEX_COORD, "v_tex_coord")
        GLES30.glLinkProgram(id)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) { "Linking shader $name failed: ${GLES30.glGetProgramInfoLog(id)}" }
    }

    fun use() = GLES30.glUseProgram(id)

    private fun location(name: String) = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun setMatrix4(name: String, columns: FloatArray) = location(name).let { if (it >= 0) GLES30.glUniformMatrix4fv(it, 1, false, columns, 0) }

    fun setMatrix3(name: String, columns: FloatArray) = location(name).let { if (it >= 0) GLES30.glUniformMatrix3fv(it, 1, false, columns, 0) }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) = location(name).let { if (it >= 0) GLES30.glUniform4f(it, x, y, z, w) }

    fun setVec2(name: String, x: Float, y: Float) = location(name).let { if (it >= 0) GLES30.glUniform2f(it, x, y) }

    fun setFloat(name: String, value: Float) = location(name).let { if (it >= 0) GLES30.glUniform1f(it, value) }

    fun setInt(name: String, value: Int) = location(name).let { if (it >= 0) GLES30.glUniform1i(it, value) }

    fun setBoolean(name: String, value: Boolean) = setInt(name, if (value) 1 else 0)

    fun release() = GLES30.glDeleteProgram(id)

    private fun compile(type: Int, source: String, name: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) { "Compiling shader $name failed: ${GLES30.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun AssetManager.readShader(file: String) = open("orca/shaders/$file").use { it.readBytes().decodeToString() }

    companion object {
        const val POSITION = 0
        const val NORMAL = 1
        const val TEX_COORD = 2
    }
}

/** A vertex array with one interleaved float buffer. */
internal class GlVertexArray(
    data: FloatBuffer,
    /** Attribute locations (GlProgram.POSITION, ...) and their component counts, in buffer order. */
    layout: List<Pair<Int, Int>>,
    private val mode: Int,
) {
    private val vertexArray = IntArray(1)
    private val buffer = IntArray(1)
    private val stride = layout.sumOf { it.second }
    val vertexCount = data.limit() / stride

    init {
        GLES30.glGenVertexArrays(1, vertexArray, 0)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glGenBuffers(1, buffer, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer[0])
        data.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.limit() * Float.SIZE_BYTES, data, GLES30.GL_STATIC_DRAW)
        var offset = 0
        for ((location, size) in layout) {
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, stride * Float.SIZE_BYTES, offset * Float.SIZE_BYTES)
            offset += size
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw() {
        if (vertexCount == 0) return
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glDrawArrays(mode, 0, vertexCount)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, buffer, 0)
        GLES30.glDeleteVertexArrays(1, vertexArray, 0)
    }

    companion object {
        fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(values)
            .also { it.flip() }
    }
}

/** An RGBA texture with mipmaps. */
internal class GlTexture(width: Int, height: Int, rgba: ByteBuffer) {
    val id: Int

    init {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        id = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        rgba.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        if (GLES30.glGetString(GLES30.GL_EXTENSIONS)?.contains(ANISOTROPIC_EXTENSION) == true) {
            val maxAnisotropy = FloatArray(1)
            GLES30.glGetFloatv(MAX_TEXTURE_MAX_ANISOTROPY, maxAnisotropy, 0)
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, TEXTURE_MAX_ANISOTROPY, maxAnisotropy[0])
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun release() = GLES30.glDeleteTextures(1, intArrayOf(id), 0)

    private companion object {
        const val ANISOTROPIC_EXTENSION = "GL_EXT_texture_filter_anisotropic"
        const val TEXTURE_MAX_ANISOTROPY = 0x84FE
        const val MAX_TEXTURE_MAX_ANISOTROPY = 0x84FF
    }
}

/**
 * A frame drawn off screen for a pass over the whole of it: with [samples], a
 * multisampled colour and depth buffer that [resolve] copies into [texture],
 * as the surface's own buffer would be resolved; without, [texture] itself
 * with a depth buffer. [complete] tells whether the driver took it.
 */
internal class GlOffscreenFrame(val width: Int, val height: Int, samples: Int) {
    val texture: Int
    private val resolveFramebuffer: Int
    private val drawFramebuffer: Int
    private val renderbuffers = IntArray(2)
    val complete: Boolean

    init {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        GLES30.glGenFramebuffers(1, ids, 0)
        resolveFramebuffer = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resolveFramebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)

        GLES30.glGenRenderbuffers(2, renderbuffers, 0)
        if (samples > 0) {
            GLES30.glGenFramebuffers(1, ids, 0)
            drawFramebuffer = ids[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, drawFramebuffer)
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, renderbuffers[0])
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, width, height)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, renderbuffers[0])
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, renderbuffers[1])
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, width, height)
        } else {
            drawFramebuffer = resolveFramebuffer
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, renderbuffers[1])
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, width, height)
        }
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, renderbuffers[1])
        complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE &&
            (drawFramebuffer == resolveFramebuffer || run {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resolveFramebuffer)
                GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
            })
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /** Draws into the frame from now on. */
    fun bind() = GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, drawFramebuffer)

    /** Resolves the samples into [texture], and draws on the surface again. */
    fun resolve() {
        if (drawFramebuffer != resolveFramebuffer) {
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, drawFramebuffer)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFramebuffer)
            GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        val framebuffers = if (drawFramebuffer == resolveFramebuffer) intArrayOf(resolveFramebuffer) else intArrayOf(resolveFramebuffer, drawFramebuffer)
        GLES30.glDeleteFramebuffers(framebuffers.size, framebuffers, 0)
        GLES30.glDeleteRenderbuffers(2, renderbuffers, 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

/**
 * GLVolume::render_with_outline()'s first pass: a framebuffer of [width] by
 * [height] with a depth texture alone (GL_DEPTH_COMPONENT32F), into which a
 * volume is drawn so its second pass can find its silhouette. OpenGL ES samples
 * a depth texture only with nearest filtering, which reads the same texels the
 * shader asks for at the pixels' centres.
 */
internal class GlDepthTarget(val width: Int, val height: Int) {
    val texture: Int
    private val framebuffer: Int
    val complete: Boolean

    init {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT32F, width, height, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_FLOAT, null)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val previous = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, previous, 0)
        GLES30.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, texture, 0)
        complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previous[0])
    }

    /** Draws into the depth texture, cleared, until [unbind] goes back to [previous]. */
    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    }

    fun unbind(previous: Int) = GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previous)

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}
