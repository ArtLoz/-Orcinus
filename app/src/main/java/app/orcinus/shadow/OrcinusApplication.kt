package app.orcinus.shadow

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import app.orcinus.shadow.di.AppContainer

/**
 * Holds the composition root for the life of the process, so activities are
 * recreated without reconnecting to the engine. The class also starts in the
 * :slicer process, where the container stays unused.
 */
class OrcinusApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // The engine loads OrcaSlicer's profiles, which takes seconds on a
        // phone. It starts with the process, so the profiles are on their way
        // while the first screen is laid out; the UI waits for them anyway.
        if (isUiProcess()) {
            container.startEngineEarly()
        } else {
            Telemetry.startInSlicerProcess(this)
        }
    }

    /** The :slicer process runs this class too, and hosts the engine itself. */
    private fun isUiProcess(): Boolean {
        val pid = Process.myPid()
        val manager = getSystemService(ActivityManager::class.java)
        val name = manager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        return name == null || !name.endsWith(":slicer")
    }
}