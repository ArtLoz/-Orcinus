package app.orcinus.shadow.slicing.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.slicing.api.ModelInspector
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [SlicerEngine] and [ModelInspector] backed by a [SlicerService] in another
 * process. The first call binds to the service. When the engine process dies,
 * running jobs end with [SliceFailureCode.ENGINE_CRASHED] and a new process
 * starts at once.
 */
class RemoteSlicerEngine(
    context: Context,
    private val serviceClass: Class<out SlicerService<*>>,
) : SlicerEngine, ModelInspector {
    private val applicationContext = context.applicationContext
    private val lock = Any()

    /** Completes when connected; null while unbound. Guarded by [lock]. */
    private var connected: CompletableDeferred<ISlicerService>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = ISlicerService.Stub.asInterface(binder)
            synchronized(lock) {
                val current = connected ?: return
                if (!current.complete(service)) connected = CompletableDeferred(service)
            }
        }

        // The engine process died. Android would restart a bound service by
        // itself, but backs off for 30 minutes after a second crash, so the
        // binding is replaced with a new one, which starts a process at once.
        // The new SlicerService clears the foreground state left by the dead
        // one; the engine itself loads only on the next call.
        override fun onServiceDisconnected(name: ComponentName) = reconnect()

        override fun onBindingDied(name: ComponentName) = reconnect()

        private fun reconnect() {
            synchronized(lock) {
                val current = connected ?: return
                connected = null
                applicationContext.unbindService(this)
                // Calls still waiting for the first connection keep waiting.
                bindLocked(current.takeUnless { it.isCompleted } ?: CompletableDeferred())
            }
        }
    }

    override suspend fun status(): EngineStatus = withContext(Dispatchers.IO) {
        try {
            service().status().toEngineStatus()
        } catch (error: RemoteException) {
            throw IllegalStateException(PROCESS_DIED, error)
        }
    }

    override suspend fun inspect(model: ModelSource.LocalFile): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        try {
            service().inspect(model.path.value).toInspectionOutcome()
        } catch (_: RemoteException) {
            ModelInspectionOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun slice(
        request: SliceRequest,
        progressListener: SliceProgressListener,
    ): SliceOutcome {
        val service = service()
        val outcome = CompletableDeferred<SliceOutcome>()
        val crashed = SliceOutcome.Failure(
            jobId = request.jobId,
            code = SliceFailureCode.ENGINE_CRASHED,
            message = PROCESS_DIED,
            recoverable = true,
        )
        val callback = object : ISliceCallback.Stub() {
            override fun onProgress(jobId: String, fraction: Float, stage: String, detail: String?) {
                progressListener.onProgress(SliceProgress(SliceJobId(jobId), fraction, SliceStage.valueOf(stage), detail))
            }

            override fun onFinished(result: SliceOutcomeParcel) {
                outcome.complete(result.toSliceOutcome())
            }
        }
        val binder = service.asBinder()
        val deathRecipient = IBinder.DeathRecipient { outcome.complete(crashed) }
        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (_: RemoteException) {
            return crashed
        }
        return try {
            withContext(Dispatchers.IO) { service.slice(request.toParcel(), callback) }
            outcome.await()
        } catch (_: RemoteException) {
            crashed
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    service.cancel(request.jobId.value)
                } catch (_: RemoteException) {
                    // The engine process is gone, and the job with it.
                }
            }
            throw cancellation
        } finally {
            binder.unlinkToDeath(deathRecipient, 0)
        }
    }

    override suspend fun cancel(jobId: SliceJobId): Boolean {
        // Cancelling never starts the engine process. A completed connection
        // always holds a service: failed binds reset it to null.
        val current = synchronized(lock) { connected?.takeIf { it.isCompleted } } ?: return false
        return withContext(Dispatchers.IO) {
            try {
                current.await().cancel(jobId.value)
            } catch (_: RemoteException) {
                false
            }
        }
    }

    private suspend fun service(): ISlicerService {
        val deferred = synchronized(lock) {
            connected ?: CompletableDeferred<ISlicerService>().also(::bindLocked)
        }
        return deferred.await()
    }

    private fun bindLocked(deferred: CompletableDeferred<ISlicerService>) {
        connected = deferred
        val intent = Intent(applicationContext, serviceClass)
        if (!applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            applicationContext.unbindService(connection)
            connected = null
            deferred.completeExceptionally(IllegalStateException("Cannot bind ${serviceClass.name}"))
        }
    }

    private companion object {
        const val PROCESS_DIED = "The slicing engine process terminated unexpectedly"
    }
}
