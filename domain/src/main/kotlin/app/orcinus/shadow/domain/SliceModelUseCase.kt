package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine

fun interface SliceProgressObserver {
    fun onProgress(progress: SliceProgress)
}

class SliceModelUseCase(
    private val engine: SlicerEngine,
) {
    suspend operator fun invoke(
        request: SliceRequest,
        progressObserver: SliceProgressObserver,
    ): SliceOutcome {
        val validationMessage = validate(request)
        if (validationMessage != null) {
            return SliceOutcome.Failure(
                jobId = request.jobId,
                code = SliceFailureCode.INVALID_REQUEST,
                message = validationMessage,
                recoverable = true,
            )
        }

        return engine.slice(
            request = request,
            progressListener = SliceProgressListener(progressObserver::onProgress),
        )
    }

    private fun validate(request: SliceRequest): String? {
        val model = request.model
        return when {
            model is ModelSource.LocalFile && model.path.value.isBlank() -> "Model path is empty"
            request.output.value.isBlank() -> "Output path is empty"
            model is ModelSource.LocalFile && model.path.value == request.output.value -> {
                "Input and output paths must differ"
            }
            request.printerProfile.value.isBlank() -> "Printer profile is empty"
            request.filamentProfile.value.isBlank() -> "Filament profile is empty"
            request.processProfile.value.isBlank() -> "Process profile is empty"
            else -> null
        }
    }
}
