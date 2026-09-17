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
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SetupFilament
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrinterModel
import app.orcinus.shadow.core.model.SetupPrintersOutcome
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
import app.orcinus.shadow.slicing.api.PresetManager
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
class NativeSlicerEngine(context: Context) : SlicerEngine, PlateInspector, PresetManager {
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
        val plate = NativePlate(request.objects)

        progressListener.onProgress(SliceProgress(request.jobId, 0f, SliceStage.PREPARING))
        val result = coroutineScope {
            val nativeJob = async(Dispatchers.Default) {
                NativeBindings.slice(
                    jobId = request.jobId.value,
                    modelPaths = plate.modelPaths,
                    placements = plate.placements,
                    autoDrops = plate.autoDrops,
                    outputPath = request.output.value,
                    toolpathsPath = request.toolpaths?.value,
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
        plate: List<PlacedModel>,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext ModelInspectionOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(plate)
        NativeBindings.inspectModel(
            modelPath = model.nativePath(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            meshPath = mesh.value,
            plateModelPaths = objects.modelPaths,
            platePlacements = objects.placements,
            plateAutoDrops = objects.autoDrops,
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
                is Manipulation.LayOnFace -> 4L
                Manipulation.EnsureOnBed -> 5L
            },
            faceNormal = (manipulation as? Manipulation.LayOnFace)?.normal?.let { doubleArrayOf(it.x, it.y, it.z) },
        ).toOutcome(mesh)
    }

    override suspend fun placeObjects(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        manipulation: PlateManipulation,
    ): PlateInspectionOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext PlateInspectionOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(plate)
        val selected = (manipulation as? PlateManipulation.AutoOrient)?.selected.orEmpty()
        val arrange = (manipulation as? PlateManipulation.Arrange)?.settings ?: ArrangeSettings()
        val result = NativeBindings.placeObjects(
            modelPaths = objects.modelPaths,
            placements = objects.placements,
            autoDrops = objects.autoDrops,
            selected = BooleanArray(plate.size) { plate[it].mesh in selected },
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            manipulation = when (manipulation) {
                is PlateManipulation.AutoOrient -> 0L
                is PlateManipulation.Arrange -> 1L
                PlateManipulation.UpdatePrintVolume -> 2L
            },
            arrangeDistance = arrange.distance,
            arrangeEnableRotation = arrange.enableRotation,
            arrangeAllowMultiMaterials = arrange.allowMultiMaterialsOnSamePlate,
            arrangeAlignToYAxis = arrange.alignToYAxis,
        )
        if (result.status != NativeSceneStatus.SUCCESS || result.objects.size != plate.size) {
            return@withContext PlateInspectionOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not place the objects" })
        }
        PlateInspectionOutcome.Success(result.objects.mapIndexed { index, placed -> placed.toInspection(plate[index].mesh) })
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

    override suspend fun presets(): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.describePresets().toOutcome()
    }

    override suspend fun selectPreset(choice: PresetChoice): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        when (choice) {
            is PresetChoice.Printer -> NativeBindings.selectPreset(NativePresetChoice.PRINTER, choice.preset.value)
            is PresetChoice.PrinterModel -> NativeBindings.selectPreset(NativePresetChoice.PRINTER_MODEL, choice.model)
            is PresetChoice.NozzleDiameter -> NativeBindings.selectPreset(NativePresetChoice.NOZZLE_DIAMETER, choice.diameter)
            is PresetChoice.Filament -> NativeBindings.selectPreset(NativePresetChoice.FILAMENT, choice.preset.value)
            is PresetChoice.Process -> NativeBindings.selectPreset(NativePresetChoice.PROCESS, choice.preset.value)
        }.toOutcome()
    }

    override suspend fun setupPrinters(): SetupPrintersOutcome = whenReady(SetupPrintersOutcome::Failure) {
        val result = NativeBindings.describeSetupPrinters()
        if (result.status != NativeSceneStatus.SUCCESS) {
            SetupPrintersOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the printers" })
        } else {
            SetupPrintersOutcome.Success(
                result.models.map { model ->
                    SetupPrinterModel(
                        vendor = model.vendor,
                        id = model.model,
                        name = model.name,
                        nozzleDiameters = model.nozzleDiameters.toList(),
                        defaultMaterials = model.defaultMaterials.toList(),
                        cover = model.cover,
                        installedNozzles = model.installedNozzles.toList(),
                    )
                },
            )
        }
    }

    override suspend fun setupFilaments(models: List<String>): SetupFilamentsOutcome = whenReady(SetupFilamentsOutcome::Failure) {
        val result = NativeBindings.describeSetupFilaments(models.toTypedArray())
        if (result.status != NativeSceneStatus.SUCCESS) {
            SetupFilamentsOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the filaments" })
        } else {
            SetupFilamentsOutcome.Success(
                result.filaments.map { filament ->
                    SetupFilament(filament.name, filament.vendor, filament.type, filament.models.toList(), filament.selected)
                },
            )
        }
    }

    override suspend fun applySetup(models: List<String>, filaments: List<String>): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.applySetup(models.toTypedArray(), filaments.toTypedArray()).toOutcome()
    }

    override suspend fun applyDefaultSetup(): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.applyDefaultSetup().toOutcome()
    }

    /** Runs [block] on the IO dispatcher once the engine is ready, or reports why it is not. */
    private suspend fun <T> whenReady(failure: (String) -> T, block: () -> T): T = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (engineStatus.ready) block() else failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
    }

    private fun NativePresetState.toOutcome(): PresetsOutcome {
        if (status != NativeSceneStatus.SUCCESS) {
            return PresetsOutcome.Failure(message.ifBlank { "OrcaSlicer could not select the presets" })
        }
        return PresetsOutcome.Success(
            Presets(
                selection = SlicingProfileSelection(ProfileId(printer), ProfileId(filament), ProfileId(process)),
                setupRequired = setupRequired,
                printers = printers.map { it.toItem() },
                filaments = filaments.map { it.toItem() },
                processes = processes.map { it.toItem() },
                nozzleDiameters = nozzleDiameters.toList(),
                nozzleDiameter = nozzleDiameter,
            ),
        )
    }

    private fun NativePresetItem.toItem() = PresetListItem(
        name = name,
        label = label,
        group = when (group) {
            NativePresetGroup.USER -> PresetGroup.USER
            NativePresetGroup.BUNDLE -> PresetGroup.BUNDLE
            else -> PresetGroup.SYSTEM
        },
        subgroup = subgroup,
        selected = selected,
    )

    private fun NativeModelInspection.toOutcome(mesh: ScenePath): ModelInspectionOutcome {
        if (status != NativeSceneStatus.SUCCESS) {
            return ModelInspectionOutcome.Failure(message.ifBlank { "OrcaSlicer could not read the model" })
        }
        return ModelInspectionOutcome.Success(toInspection(mesh))
    }

    private fun NativeModelInspection.toInspection(mesh: ScenePath) = ModelInspection(
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
    )

    /** The objects of a plate as the bridge takes them: parallel arrays with an entry per object. */
    private class NativePlate(objects: List<PlacedModel>) {
        val modelPaths = Array(objects.size) { objects[it].model.nativePath() }
        val placements = objects.flatMap { it.placement.columns }.toDoubleArray()
        val autoDrops = BooleanArray(objects.size) { objects[it].autoDrop }
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
                toolpaths = request.toolpaths?.takeIf { result.toolpathsWritten },
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

/** The bridge takes an empty path for the built-in calibration cube. */
private fun ModelSource.nativePath(): String = when (this) {
    is ModelSource.LocalFile -> path.value
    is ModelSource.BuiltIn -> ""
}
