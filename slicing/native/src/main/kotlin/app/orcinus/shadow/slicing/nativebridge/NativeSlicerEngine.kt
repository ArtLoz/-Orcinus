package app.orcinus.shadow.slicing.nativebridge

import android.content.Context
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelGeometryPreview
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.ModelInspector
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** OrcaSlicer engine running in this process through the JNI bridge. */
class NativeSlicerEngine(context: Context) : SlicerEngine, ModelInspector {
    private val applicationContext = context.applicationContext
    private val statusLock = Mutex()
    private var status: EngineStatus? = null

    override suspend fun status(): EngineStatus = statusLock.withLock {
        status ?: withContext(Dispatchers.IO) { start() }.also { status = it }
    }

    override suspend fun slice(
        request: SliceRequest,
        progressListener: SliceProgressListener,
    ): SliceOutcome {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return failure(
                request,
                SliceFailureCode.ENGINE_UNAVAILABLE,
                engineStatus.message ?: "OrcaSlicer engine is not ready",
                recoverable = false,
            )
        }
        val modelPath = when (val model = request.model) {
            is ModelSource.LocalFile -> model.path.value
            is ModelSource.BuiltIn -> ""
        }

        progressListener.onProgress(SliceProgress(request.jobId, 0f, SliceStage.PREPARING))
        val result = coroutineScope {
            val nativeJob = async(Dispatchers.Default) {
                NativeBindings.slice(
                    jobId = request.jobId.value,
                    modelPath = modelPath,
                    outputPath = request.output.value,
                    printerProfile = request.printerProfile.value,
                    filamentProfile = request.filamentProfile.value,
                    processProfile = request.processProfile.value,
                ) { percent, message ->
                    progressListener.onProgress(progress(request.jobId, percent, message))
                }
            }
            try {
                nativeJob.await()
            } catch (cancellation: CancellationException) {
                // The JNI call cannot be interrupted; Orca stops at its next
                // cancellation check and coroutineScope waits for it.
                NativeBindings.cancel(request.jobId.value)
                throw cancellation
            }
        }
        return outcome(request, result)
    }

    override suspend fun cancel(jobId: SliceJobId): Boolean = NativeBindings.cancel(jobId.value)

    override suspend fun inspect(
        model: ModelSource.LocalFile,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        val inspection = NativeBindings.inspectStl(model.path.value)
        if (inspection == null || inspection.size != STL_INSPECTION_SIZE) {
            return@withContext ModelInspectionOutcome.Failure("Native STL reader returned an invalid result")
        }
        val inspectionError = when (inspection[STL_STATUS_INDEX].toInt()) {
            STL_STATUS_SUCCESS -> null
            STL_STATUS_EMPTY -> "STL file contains no facets"
            STL_STATUS_LAYER_ANALYSIS_FAILED -> "Orca could not build STL layer contours"
            else -> "Orca TriangleMesh could not read the STL file"
        }
        if (inspectionError != null) {
            return@withContext ModelInspectionOutcome.Failure(inspectionError)
        }

        ModelInspectionOutcome.Success(
            ModelInspection(
                facetCount = inspection[STL_FACET_COUNT_INDEX],
                dimensions = ModelDimensions(
                    widthMillimeters = inspection[STL_WIDTH_INDEX] / 1_000.0,
                    depthMillimeters = inspection[STL_DEPTH_INDEX] / 1_000.0,
                    heightMillimeters = inspection[STL_HEIGHT_INDEX] / 1_000.0,
                ),
                geometryPreview = inspection.toGeometryPreview(),
            ),
        )
    }

    private fun start(): EngineStatus {
        val version = EngineVersion(NativeBindings.engineVersion())
        val directories = OrcaAssets.materialize(applicationContext)
        val error = NativeBindings.initialize(
            dataDir = directories.data.absolutePath,
            resourcesDir = directories.resources.absolutePath,
            temporaryDir = directories.temporary.absolutePath,
        )
        return EngineStatus(version = version, ready = error == null, message = error)
    }

    private fun LongArray.toGeometryPreview(): ModelGeometryPreview? {
        val planeCount = this[STL_SAMPLED_PLANE_COUNT_INDEX].toInt()
        if (planeCount <= 0) return null
        return ModelGeometryPreview(
            samplingStepMillimeters = this[STL_SAMPLING_STEP_INDEX] / 1_000.0,
            sampledPlaneCount = planeCount,
            nonEmptyPlaneCount = this[STL_NON_EMPTY_PLANE_COUNT_INDEX].toInt(),
            contourCount = this[STL_CONTOUR_COUNT_INDEX],
        )
    }

    private fun progress(jobId: SliceJobId, percent: Int, message: String): SliceProgress {
        val clamped = percent.coerceIn(0, 100)
        val stage = when {
            clamped >= 100 -> SliceStage.COMPLETED
            clamped >= GCODE_EXPORT_PERCENT -> SliceStage.GENERATING_GCODE
            else -> SliceStage.SLICING
        }
        return SliceProgress(jobId, clamped / 100f, stage, message.ifBlank { null })
    }

    private fun outcome(request: SliceRequest, result: NativeSliceResult): SliceOutcome =
        when (result.status) {
            NativeSliceResult.SUCCESS -> SliceOutcome.Success(
                jobId = request.jobId,
                gcodePath = OutputPath(request.output.value),
                statistics = SliceStatistics(
                    layerCount = result.layerCount.toInt(),
                    estimatedPrintTimeSeconds = result.estimatedPrintTimeSeconds,
                    filamentMillimeters = result.filamentMicrometers / 1_000.0,
                ),
            )

            NativeSliceResult.CANCELLED -> SliceOutcome.Cancelled(request.jobId)
            NativeSliceResult.BUSY -> failure(request, SliceFailureCode.ENGINE_BUSY, result.message, recoverable = true)
            NativeSliceResult.OUTPUT_WRITE_FAILED ->
                failure(request, SliceFailureCode.OUTPUT_WRITE_FAILED, result.message, recoverable = true)

            NativeSliceResult.PROFILE_NOT_FOUND ->
                failure(request, SliceFailureCode.PROFILE_NOT_FOUND, result.message, recoverable = true)

            NativeSliceResult.MODEL_READ_FAILED ->
                failure(request, SliceFailureCode.MODEL_READ_FAILED, result.message, recoverable = true)

            NativeSliceResult.INVALID_PRINT ->
                failure(request, SliceFailureCode.INVALID_PRINT, result.message, recoverable = true)

            NativeSliceResult.ENGINE_NOT_READY ->
                failure(request, SliceFailureCode.ENGINE_UNAVAILABLE, result.message, recoverable = false)

            else -> failure(request, SliceFailureCode.SLICING_FAILED, result.message, recoverable = false)
        }

    private fun failure(
        request: SliceRequest,
        code: SliceFailureCode,
        message: String,
        recoverable: Boolean,
    ) = SliceOutcome.Failure(
        jobId = request.jobId,
        code = code,
        message = message.ifBlank { code.name },
        recoverable = recoverable,
    )

    companion object {
        /** Profiles bundled with the app (Creality vendor bundle). */
        val k2PlusProfiles = SlicingProfileSelection(
            printer = ProfileId("Creality K2 Plus 0.4 nozzle"),
            filament = ProfileId("Generic PLA @K2 Plus-all"),
            process = ProfileId("0.20mm Standard @Creality K2 Plus 0.4 nozzle"),
        )

        /** Print::export_gcode reports 80 % when G-code generation starts. */
        private const val GCODE_EXPORT_PERCENT = 80

        private const val STL_INSPECTION_SIZE = 9
        private const val STL_STATUS_INDEX = 0
        private const val STL_FACET_COUNT_INDEX = 1
        private const val STL_WIDTH_INDEX = 2
        private const val STL_DEPTH_INDEX = 3
        private const val STL_HEIGHT_INDEX = 4
        private const val STL_SAMPLING_STEP_INDEX = 5
        private const val STL_SAMPLED_PLANE_COUNT_INDEX = 6
        private const val STL_NON_EMPTY_PLANE_COUNT_INDEX = 7
        private const val STL_CONTOUR_COUNT_INDEX = 8
        private const val STL_STATUS_SUCCESS = 0
        private const val STL_STATUS_EMPTY = 2
        private const val STL_STATUS_LAYER_ANALYSIS_FAILED = 3
    }
}
