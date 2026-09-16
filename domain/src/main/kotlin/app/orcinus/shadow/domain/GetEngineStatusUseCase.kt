package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlin.coroutines.cancellation.CancellationException

class GetEngineStatusUseCase(
    private val engine: SlicerEngine,
) {
    suspend operator fun invoke(): EngineStatus = try {
        engine.status()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        EngineStatus(
            version = EngineVersion("unavailable"),
            ready = false,
            message = error.message,
        )
    }
}
