package app.orcinus.shadow.slicing.nativebridge

import android.content.Context
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.slicing.api.PlateInspector
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
class NativeSlicerEngine(context: Context) : SlicerEngine, PlateInspector {
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
        val modelPath = request.model.nativePath()

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
                    placement = request.placement?.columns?.toDoubleArray(),
                    autoDrop = request.autoDrop,
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

    override suspend fun describePlate(
        profiles: SlicingProfileSelection,
        directory: ScenePath,
    ): PlateDescriptionOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext PlateDescriptionOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val plate = NativeBindings.describePlate(
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            outputDirectory = directory.value,
        )
        if (plate.status != NativeSceneStatus.SUCCESS) {
            return@withContext PlateDescriptionOutcome.Failure(plate.message.ifBlank { "OrcaSlicer could not describe the plate" })
        }
        PlateDescriptionOutcome.Success(
            PlateDescription(
                geometry = PlateGeometry(
                    printableArea = plate.printableArea.toPoints(),
                    printableHeight = plate.printableHeight,
                    plateTriangles = plate.plateTriangles.toPoints(),
                    excludeTriangles = plate.excludeTriangles.toPoints(),
                    thinGridLines = plate.thinGridLines.toPoints(),
                    boldGridLines = plate.boldGridLines.toPoints(),
                    bedModel = plate.bedModelMesh.ifBlank { null }?.let(::ScenePath),
                    bedTexture = plate.bedTexture.ifBlank { null }?.let(::ScenePath),
                ),
                filamentColor = decodeColor(plate.filamentColour),
            ),
        )
    }

    override suspend fun inspect(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext ModelInspectionOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        NativeBindings.inspectModel(
            modelPath = model.nativePath(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            meshPath = mesh.value,
        ).toOutcome(mesh)
    }

    override suspend fun place(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        previous: Transform3,
        placement: Transform3,
        autoDrop: Boolean,
        manipulation: Manipulation,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext ModelInspectionOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val arrange = (manipulation as? Manipulation.Arrange)?.settings ?: ArrangeSettings()
        NativeBindings.placeModel(
            modelPath = model.nativePath(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            previousPlacement = previous.columns.toDoubleArray(),
            placement = placement.columns.toDoubleArray(),
            autoDrop = autoDrop,
            manipulation = when (manipulation) {
                Manipulation.Move -> 0L
                Manipulation.Rotate -> 1L
                Manipulation.Scale -> 2L
                Manipulation.ResetRotation -> 3L
                Manipulation.AutoOrient -> 4L
                is Manipulation.LayOnFace -> 5L
                is Manipulation.Arrange -> 6L
                Manipulation.EnsureOnBed -> 7L
            },
            faceNormal = (manipulation as? Manipulation.LayOnFace)?.normal?.let { doubleArrayOf(it.x, it.y, it.z) },
            arrangeDistance = arrange.distance,
            arrangeEnableRotation = arrange.enableRotation,
            arrangeAllowMultiMaterials = arrange.allowMultiMaterialsOnSamePlate,
            arrangeAlignToYAxis = arrange.alignToYAxis,
        ).toOutcome(mesh)
    }

    override suspend fun flatteningPlanes(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        placement: Transform3,
    ): FlatteningPlanesOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext FlatteningPlanesOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val result = NativeBindings.describeFlatteningPlanes(
            modelPath = model.nativePath(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            placement = placement.columns.toDoubleArray(),
        )
        if (result.status != NativeSceneStatus.SUCCESS) {
            return@withContext FlatteningPlanesOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not find the faces" })
        }
        var offset = 0
        FlatteningPlanesOutcome.Success(
            result.vertexCounts.indices.map { plane ->
                val count = result.vertexCounts[plane]
                val polygon = (0 until count).map { point ->
                    val index = (offset + point) * 3
                    Vector3(result.vertices[index].toDouble(), result.vertices[index + 1].toDouble(), result.vertices[index + 2].toDouble())
                }
                offset += count
                FlatteningPlane(Vector3(result.normals[plane * 3], result.normals[plane * 3 + 1], result.normals[plane * 3 + 2]), polygon)
            },
        )
    }

    private fun NativeModelInspection.toOutcome(mesh: ScenePath): ModelInspectionOutcome {
        if (status != NativeSceneStatus.SUCCESS) {
            return ModelInspectionOutcome.Failure(message.ifBlank { "OrcaSlicer could not read the model" })
        }
        return ModelInspectionOutcome.Success(
            ModelInspection(
                facetCount = facetCount,
                dimensions = ModelDimensions(sizeX, sizeY, sizeZ),
                boxCenter = boxCenter.toVector(),
                mesh = mesh,
                placement = Transform3(instanceMatrix.toList()),
                fit = when (volumeState) {
                    NativeVolumeState.INSIDE -> BuildVolumeFit.INSIDE
                    NativeVolumeState.PARTLY_OUTSIDE -> BuildVolumeFit.PARTLY_OUTSIDE
                    else -> BuildVolumeFit.OUTSIDE
                },
                boundingSphere = BoundingSphere(sphereCenter.toVector(), sphereRadius),
                rotationDegrees = rotationDegrees.toVector(),
                unscaledDimensions = ModelDimensions(unscaledSize[0], unscaledSize[1], unscaledSize[2]),
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

    /** The bridge takes an empty path for the built-in calibration cube. */
    private fun ModelSource.nativePath(): String = when (this) {
        is ModelSource.LocalFile -> path.value
        is ModelSource.BuiltIn -> ""
    }

    private fun DoubleArray.toVector() = Vector3(this[0], this[1], this[2])

    private fun DoubleArray.toPoints() = (indices step 2).map { Point2(this[it], this[it + 1]) }

    private fun FloatArray.toPoints() = (indices step 2).map { Point2(this[it].toDouble(), this[it + 1].toDouble()) }

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

        /**
         * decode_color() in OrcaSlicer's libslic3r/Color.cpp: "#RRGGBB" or
         * "#RRGGBBAA"; anything else is the default filament colour, #F2754E.
         */
        internal fun decodeColor(hex: String): ColorRgba {
            val digits = hex.removePrefix("#")
            if (!hex.startsWith("#") || (digits.length != 6 && digits.length != 8) || digits.any { Character.digit(it, 16) < 0 }) {
                return decodeColor(DEFAULT_FILAMENT_COLOUR)
            }
            fun channel(index: Int) = digits.substring(index * 2, index * 2 + 2).toInt(16) / 255f
            return ColorRgba(channel(0), channel(1), channel(2), if (digits.length == 8) channel(3) else 1f)
        }

        private const val DEFAULT_FILAMENT_COLOUR = "#F2754E"
    }
}
