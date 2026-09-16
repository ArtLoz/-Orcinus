package app.orcinus.shadow.slicing.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.slicing.api.ModelInspector
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Hosts a slicing engine in its own process, so a crash in native code ends
 * only that process. The app declares a subclass with `android:process` and
 * `foregroundServiceType="specialUse"`; UI code talks to it through
 * [RemoteSlicerEngine].
 *
 * While a job runs the service is started and in the foreground with a progress
 * notification, so slicing continues when the app leaves the screen. It stops
 * itself when the job ends.
 */
abstract class SlicerService<E> : Service() where E : SlicerEngine, E : ModelInspector {
    private val engine: E by lazy { createEngine() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobLock = Any()
    private var activeJobId: SliceJobId? = null
    private lateinit var notifications: SliceNotifications

    /** Creates the engine hosted by this process; called once, on first use. */
    protected abstract fun createEngine(): E

    override fun onCreate() {
        super.onCreate()
        notifications = SliceNotifications(this, javaClass)
        // When the previous process died during a job and Android recreated the
        // bound service before the client dropped the binding, the service is
        // still marked foreground with that job's notification. A new process
        // never has a job.
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_JOB_ID)?.let { jobId ->
                scope.launch { engine.cancel(SliceJobId(jobId)) }
            }
        }
        synchronized(jobLock) {
            if (activeJobId == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val binder = object : ISlicerService.Stub() {
        override fun status(): EngineStatusParcel = runBlocking { engine.status() }.toParcel()

        override fun inspect(modelPath: String): InspectionParcel =
            runBlocking { engine.inspect(ModelSource.LocalFile(ModelPath(modelPath))) }.toParcel()

        override fun slice(request: SliceRequestParcel, callback: ISliceCallback) {
            startJob(request.toSliceRequest(), callback)
        }

        override fun cancel(jobId: String): Boolean = runBlocking { engine.cancel(SliceJobId(jobId)) }
    }

    private fun startJob(request: SliceRequest, callback: ISliceCallback) {
        val accepted = synchronized(jobLock) {
            if (activeJobId != null) {
                false
            } else {
                activeJobId = request.jobId
                enterForeground(request)
                true
            }
        }
        if (!accepted) {
            callback.deliver {
                onFinished(
                    SliceOutcome.Failure(
                        jobId = request.jobId,
                        code = SliceFailureCode.ENGINE_BUSY,
                        message = "Another slicing job is running",
                        recoverable = true,
                    ).toParcel(),
                )
            }
            return
        }

        scope.launch {
            val outcome = try {
                engine.slice(request) { progress ->
                    notifications.update(request, progress)
                    callback.deliver {
                        onProgress(progress.jobId.value, progress.fraction, progress.stage.name, progress.detail)
                    }
                }
            } catch (_: CancellationException) {
                // The service is being destroyed; the engine has already stopped the job.
                SliceOutcome.Cancelled(request.jobId)
            } catch (error: Exception) {
                Log.e(TAG, "Slicing job failed", error)
                SliceOutcome.Failure(
                    jobId = request.jobId,
                    code = SliceFailureCode.SLICING_FAILED,
                    message = error.message ?: error.javaClass.name,
                    recoverable = false,
                )
            }
            synchronized(jobLock) {
                activeJobId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifications.stopped()
                stopSelf()
            }
            callback.deliver { onFinished(outcome.toParcel()) }
        }
    }

    /** The binding app is visible when the user starts a job, so the start is allowed. */
    private fun enterForeground(request: SliceRequest) {
        try {
            startForegroundService(Intent(this, javaClass))
            val notification = notifications.started(request)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(SliceNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(SliceNotifications.NOTIFICATION_ID, notification)
            }
        } catch (error: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the job still runs while
            // the app stays visible.
            Log.w(TAG, "Slicing without a foreground service", error)
        }
    }

    /** A client that died keeps no claim on the job, which finishes regardless. */
    private inline fun ISliceCallback.deliver(call: ISliceCallback.() -> Unit) {
        try {
            call()
        } catch (error: RemoteException) {
            Log.w(TAG, "Slicing client is gone", error)
        }
    }

    internal companion object {
        const val ACTION_CANCEL = "app.orcinus.shadow.slicing.service.action.CANCEL"
        const val EXTRA_JOB_ID = "app.orcinus.shadow.slicing.service.extra.JOB_ID"
        private const val TAG = "SlicerService"
    }
}
