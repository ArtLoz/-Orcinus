package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.slicing.api.SlicerEngine

class CancelSliceUseCase(
    private val engine: SlicerEngine,
) {
    suspend operator fun invoke(jobId: SliceJobId): Boolean = engine.cancel(jobId)
}
