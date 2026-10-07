package app.orcinus.shadow.di

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.os.Build
import app.orcinus.shadow.domain.about.DeviceDetails
import app.orcinus.shadow.domain.about.DeviceInformation
import app.orcinus.shadow.domain.about.DisplayDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone as TroubleshootDialog tells of a computer: its Android release,
 * system on a chip, memory, OpenGL ES renderer and screens, and what
 * installed the app.
 */
internal class AppDeviceInformation(context: Context) : DeviceInformation {
    private val context = context.applicationContext

    override suspend fun describe(): DeviceDetails = withContext(Dispatchers.IO) {
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val gl = glStrings()
        DeviceDetails(
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            processor = processor(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            memoryBytes = memory.totalMem,
            renderer = gl?.first,
            shadingLanguage = gl?.second,
            displays = displays(),
            fontScale = context.resources.configuration.fontScale,
            packageType = packageType(),
        )
    }

    /** GetCPUinfo(): the system on a chip, which Android names from 12 on; the hardware's name before. */
    private fun processor(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else Build.HARDWARE

    /** GetMONinfo(): every screen in its current mode, with the density it scales the app by. */
    private fun displays(): List<DisplayDetails> =
        context.getSystemService(DisplayManager::class.java).displays.map { display ->
            val mode = display.mode
            DisplayDetails(mode.physicalWidth, mode.physicalHeight, context.createDisplayContext(display).resources.displayMetrics.density)
        }

    /** GetPackageType(): "Local Build" for a debuggable build, otherwise the app that installed this one. */
    private fun packageType(): String {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return LOCAL_BUILD
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return installer ?: UNKNOWN
    }

    /**
     * GetGPUinfo(): the renderer and shading language of an OpenGL ES 3
     * context made for the question and let go after it; null when none
     * could be made. The display stays initialised, as the 3D view uses it.
     */
    private fun glStrings(): Pair<String, String>? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val configs = arrayOfNulls<EGLConfig>(1)
        val found = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, found, 0) || found[0] == 0) return null
        val config = configs[0] ?: return null
        val glContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (glContext == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        return try {
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, glContext)) {
                null
            } else {
                val renderer = GLES30.glGetString(GLES30.GL_RENDERER)
                val shadingLanguage = GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION)
                renderer?.let { it to shadingLanguage.orEmpty() }
            }
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, glContext)
        }
    }

    private companion object {
        // TroubleshootDialog::GetPackageType()'s name for a build of one's own.
        const val LOCAL_BUILD = "Local Build"
        const val UNKNOWN = "Unknown"
    }
}
