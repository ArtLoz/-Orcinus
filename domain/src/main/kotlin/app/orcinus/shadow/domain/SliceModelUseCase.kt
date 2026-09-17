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
        val files = request.objects.mapNotNull { (it.model as? ModelSource.LocalFile)?.path?.value }
        return when {
            request.objects.isEmpty() -> "There is nothing to slice"
            files.any { it.isBlank() } -> "Model path is empty"
            request.output.value.isBlank() -> "Output path is empty"
            request.output.value in files -> "Input and output paths must differ"
            request.printerProfile.value.isBlank() -> "Printer profile is empty"
            request.filamentProfile.value.isBlank() -> "Filament profile is empty"
            request.processProfile.value.isBlank() -> "Process profile is empty"
            else -> null
        }
    }
}
