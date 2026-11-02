package app.orcinus.shadow

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import app.orcinus.shadow.di.AppContainer

/**
 * Holds the composition root for the life of the process, so activities are
 * recreated without reconnecting to the engine. The class also starts in the
 * :slicer process, where the container stays unused. The engine starts with
 * the window (MainActivity), not here: Android also starts the process in the
 * background for scheduled jobs, such as Firebase's uploads, which need no engine.
 */
class OrcinusApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (isSlicerProcess()) Telemetry.startInSlicerProcess(this)
    }

    /** The :slicer process runs this class too, and hosts the engine itself. */
    private fun isSlicerProcess(): Boolean {
        val pid = Process.myPid()
        val manager = getSystemService(ActivityManager::class.java)
        val name = manager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        return name != null && name.endsWith(":slicer")
    }
}
