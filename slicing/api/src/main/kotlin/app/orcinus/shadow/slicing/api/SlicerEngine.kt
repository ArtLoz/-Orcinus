package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest

fun interface SliceProgressListener {
    fun onProgress(progress: SliceProgress)
}

/**
 * Stable port into a slicing engine. No OrcaSlicer, JNI, or Android type may
 * appear in this contract. Implementations may run the engine in another
 * process, so every call can suspend.
 */
interface SlicerEngine {
    /** Prepares the engine on first use and reports whether it can slice. */
    suspend fun status(): EngineStatus

    /**
     * Suspends until the job ends. Cancelling the calling coroutine cancels
     * the job. Progress may arrive on any thread.
     */
    suspend fun slice(
        request: SliceRequest,
        progressListener: SliceProgressListener,
    ): SliceOutcome

    /** Returns true only when the active job accepted the cancellation. */
    suspend fun cancel(jobId: SliceJobId): Boolean
}

interface ModelInspector {
    suspend fun inspect(model: ModelSource.LocalFile): ModelInspectionOutcome
}
