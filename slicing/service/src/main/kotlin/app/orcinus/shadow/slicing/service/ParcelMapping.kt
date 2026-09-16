package app.orcinus.shadow.slicing.service

import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelGeometryPreview
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStatistics

// Both processes run the same APK, so enum names are a safe wire format.

internal fun EngineStatus.toParcel() = EngineStatusParcel().also {
    it.version = version.value
    it.ready = ready
    it.message = message
}

internal fun EngineStatusParcel.toEngineStatus() = EngineStatus(
    version = EngineVersion(version),
    ready = ready,
    message = message,
)

internal fun SliceRequest.toParcel() = SliceRequestParcel().also {
    it.jobId = jobId.value
    when (val source = model) {
        is ModelSource.LocalFile -> it.modelPath = source.path.value
        is ModelSource.BuiltIn -> it.builtInModel = source.model.name
    }
    it.outputPath = output.value
    it.printerProfile = printerProfile.value
    it.filamentProfile = filamentProfile.value
    it.processProfile = processProfile.value
}

internal fun SliceRequestParcel.toSliceRequest() = SliceRequest(
    jobId = SliceJobId(jobId),
    model = builtInModel?.let { ModelSource.BuiltIn(BuiltInModel.valueOf(it)) }
        ?: ModelSource.LocalFile(ModelPath(checkNotNull(modelPath) { "Slice request has no model" })),
    output = OutputPath(outputPath),
    printerProfile = ProfileId(printerProfile),
    filamentProfile = ProfileId(filamentProfile),
    processProfile = ProfileId(processProfile),
)

internal fun SliceOutcome.toParcel() = SliceOutcomeParcel().also {
    it.jobId = jobId.value
    when (this) {
        is SliceOutcome.Success -> {
            it.kind = SliceOutcomeParcel.SUCCESS
            it.gcodePath = gcodePath.value
            it.layerCount = statistics.layerCount
            it.estimatedPrintTimeSeconds = statistics.estimatedPrintTimeSeconds
            it.filamentMillimeters = statistics.filamentMillimeters
        }

        is SliceOutcome.Failure -> {
            it.kind = SliceOutcomeParcel.FAILURE
            it.failureCode = code.name
            it.message = message
            it.recoverable = recoverable
        }

        is SliceOutcome.Cancelled -> it.kind = SliceOutcomeParcel.CANCELLED
    }
}

internal fun SliceOutcomeParcel.toSliceOutcome(): SliceOutcome {
    val id = SliceJobId(jobId)
    return when (kind) {
        SliceOutcomeParcel.SUCCESS -> SliceOutcome.Success(
            jobId = id,
            gcodePath = OutputPath(checkNotNull(gcodePath)),
            statistics = SliceStatistics(layerCount, estimatedPrintTimeSeconds, filamentMillimeters),
        )

        SliceOutcomeParcel.FAILURE -> SliceOutcome.Failure(
            jobId = id,
            code = SliceFailureCode.valueOf(checkNotNull(failureCode)),
            message = message.orEmpty(),
            recoverable = recoverable,
        )

        SliceOutcomeParcel.CANCELLED -> SliceOutcome.Cancelled(id)
        else -> error("Unknown slice outcome kind: $kind")
    }
}

internal fun ModelInspectionOutcome.toParcel() = InspectionParcel().also {
    when (this) {
        is ModelInspectionOutcome.Failure -> it.error = message
        is ModelInspectionOutcome.Success -> {
            it.facetCount = inspection.facetCount
            it.widthMillimeters = inspection.dimensions.widthMillimeters
            it.depthMillimeters = inspection.dimensions.depthMillimeters
            it.heightMillimeters = inspection.dimensions.heightMillimeters
            inspection.geometryPreview?.let { preview ->
                it.hasGeometryPreview = true
                it.samplingStepMillimeters = preview.samplingStepMillimeters
                it.sampledPlaneCount = preview.sampledPlaneCount
                it.nonEmptyPlaneCount = preview.nonEmptyPlaneCount
                it.contourCount = preview.contourCount
            }
        }
    }
}

internal fun InspectionParcel.toInspectionOutcome(): ModelInspectionOutcome {
    error?.let { return ModelInspectionOutcome.Failure(it) }
    return ModelInspectionOutcome.Success(
        ModelInspection(
            facetCount = facetCount,
            dimensions = ModelDimensions(widthMillimeters, depthMillimeters, heightMillimeters),
            geometryPreview = if (hasGeometryPreview) {
                ModelGeometryPreview(
                    samplingStepMillimeters = samplingStepMillimeters,
                    sampledPlaneCount = sampledPlaneCount,
                    nonEmptyPlaneCount = nonEmptyPlaneCount,
                    contourCount = contourCount,
                )
            } else {
                null
            },
        ),
    )
}
