package app.orcinus.shadow.slicing.nativebridge

import android.content.Context
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportEntry
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilament
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.FilamentPresetChoice
import app.orcinus.shadow.core.model.FilamentPresetList
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushVolumes
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintedSurface
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetKindComparison
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabOutcome
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
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.WipeTowerOutcome
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
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
class NativeSlicerEngine(context: Context) : SlicerEngine, PlateInspector, PresetManager, PresetSettingsEditor {
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
                    instanceCounts = plate.instanceCounts,
                    placements = plate.placements,
                    autoDrops = plate.autoDrops,
                    instancePrintable = plate.printable,
                    objectSettingKeys = plate.settingKeys,
                    objectSettingValues = plate.settingValues,
                    partCounts = plate.partCounts,
                    partShapes = plate.partShapes,
                    partTypes = plate.partTypes,
                    partMatrices = plate.partMatrices,
                    partSettingKeys = plate.partSettingKeys,
                    partSettingValues = plate.partSettingValues,
                    rangeCounts = plate.rangeCounts,
                    rangeHeights = plate.rangeHeights,
                    rangeSettingKeys = plate.rangeSettingKeys,
                    rangeSettingValues = plate.rangeSettingValues,
                    painted = plate.painted,
                    partPainted = plate.partPainted,
                    plateSettingKeys = request.plateSettings.keys(),
                    plateSettingValues = request.plateSettings.values(),
                    outputPath = request.output.value,
                    toolpathsPath = request.toolpaths?.value,
                    wipeTowerPath = request.wipeTower?.value,
                    thumbnailSizes = request.thumbnails.flatMap { listOf(it.size.width, it.size.height) }.toIntArray(),
                    thumbnailPaths = request.thumbnails.map { it.path.value }.toTypedArray(),
                    printerProfile = request.printerProfile.value,
                    filamentProfile = request.filamentProfile.value,
                    filamentProfiles = request.filamentProfiles.map(ProfileId::value).toTypedArray(),
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
            plateInstanceCounts = objects.instanceCounts,
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

    override suspend fun describeWipeTower(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
    ): WipeTowerOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext WipeTowerOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(plate)
        val tower = NativeBindings.describeWipeTower(
            modelPaths = objects.modelPaths,
            instanceCounts = objects.instanceCounts,
            placements = objects.placements,
            autoDrops = objects.autoDrops,
            instancePrintable = objects.printable,
            objectSettingKeys = objects.settingKeys,
            objectSettingValues = objects.settingValues,
            partCounts = objects.partCounts,
            partShapes = objects.partShapes,
            partTypes = objects.partTypes,
            partMatrices = objects.partMatrices,
            partSettingKeys = objects.partSettingKeys,
            partSettingValues = objects.partSettingValues,
            rangeCounts = objects.rangeCounts,
            rangeHeights = objects.rangeHeights,
            rangeSettingKeys = objects.rangeSettingKeys,
            rangeSettingValues = objects.rangeSettingValues,
            plateSettingKeys = plateSettings.keys(),
            plateSettingValues = plateSettings.values(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            filamentProfiles = profiles.allFilaments.map(ProfileId::value).toTypedArray(),
            processProfile = profiles.process.value,
        )
        if (tower.status != NativeSliceResult.SUCCESS) {
            return@withContext WipeTowerOutcome.Failure(tower.message)
        }
        WipeTowerOutcome.Success(
            WipeTower(
                shown = tower.shown,
                x = tower.x,
                y = tower.y,
                width = tower.width,
                depth = tower.depth,
                height = tower.height,
                rotation = tower.rotation,
                brimWidth = tower.brimWidth,
                filaments = tower.filaments.map { it.toInt() },
            ),
        )
    }

    override suspend fun describeFlushVolumes(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
    ): FlushVolumesOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext FlushVolumesOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(plate)
        val volumes = NativeBindings.describeFlushVolumes(
            modelPaths = objects.modelPaths,
            instanceCounts = objects.instanceCounts,
            placements = objects.placements,
            autoDrops = objects.autoDrops,
            instancePrintable = objects.printable,
            objectSettingKeys = objects.settingKeys,
            objectSettingValues = objects.settingValues,
            partCounts = objects.partCounts,
            partShapes = objects.partShapes,
            partTypes = objects.partTypes,
            partMatrices = objects.partMatrices,
            partSettingKeys = objects.partSettingKeys,
            partSettingValues = objects.partSettingValues,
            rangeCounts = objects.rangeCounts,
            rangeHeights = objects.rangeHeights,
            rangeSettingKeys = objects.rangeSettingKeys,
            rangeSettingValues = objects.rangeSettingValues,
            plateSettingKeys = plateSettings.keys(),
            plateSettingValues = plateSettings.values(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            filamentProfiles = profiles.allFilaments.map(ProfileId::value).toTypedArray(),
            processProfile = profiles.process.value,
        )
        volumes.toOutcome()
    }

    override suspend fun updateFlushVolumes(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
        change: FlushVolumesChange,
        index: Int,
    ): FlushVolumesOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext FlushVolumesOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(plate)
        NativeBindings.updateFlushVolumes(
            modelPaths = objects.modelPaths,
            instanceCounts = objects.instanceCounts,
            placements = objects.placements,
            autoDrops = objects.autoDrops,
            instancePrintable = objects.printable,
            objectSettingKeys = objects.settingKeys,
            objectSettingValues = objects.settingValues,
            partCounts = objects.partCounts,
            partShapes = objects.partShapes,
            partTypes = objects.partTypes,
            partMatrices = objects.partMatrices,
            partSettingKeys = objects.partSettingKeys,
            partSettingValues = objects.partSettingValues,
            rangeCounts = objects.rangeCounts,
            rangeHeights = objects.rangeHeights,
            rangeSettingKeys = objects.rangeSettingKeys,
            rangeSettingValues = objects.rangeSettingValues,
            plateSettingKeys = plateSettings.keys(),
            plateSettingValues = plateSettings.values(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            filamentProfiles = profiles.allFilaments.map(ProfileId::value).toTypedArray(),
            processProfile = profiles.process.value,
            change = change.ordinal.toLong(),
            index = index.toLong(),
        ).toOutcome()
    }

    /** FlushVolumes and FlushVolumesUpdate in orca_engine_adapter.hpp. */
    private fun NativeFlushVolumes.toOutcome(): FlushVolumesOutcome {
        if (status != NativeSliceResult.SUCCESS) {
            return FlushVolumesOutcome.Failure(message)
        }
        return FlushVolumesOutcome.Success(
            FlushVolumes(
                filaments = filaments.toInt(),
                nozzles = nozzles.toInt(),
                matrix = matrix.toList(),
                automatic = automatic.toList(),
                multipliers = multipliers.toList(),
                modified = modified,
            ),
            updated = updated,
        )
    }

    override suspend fun beginPainting(
        plateObject: PlacedModel,
        part: Int?,
        profiles: SlicingProfileSelection,
        facets: PaintedFacets,
        meshPrefix: ScenePath,
    ): PaintingOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext PaintingOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(listOf(plateObject))
        painting(
            NativeBindings.beginPainting(
                modelPath = objects.modelPaths.first(),
                instanceCounts = objects.instanceCounts,
                placements = objects.placements,
                autoDrops = objects.autoDrops,
                partCounts = objects.partCounts,
                partShapes = objects.partShapes,
                partTypes = objects.partTypes,
                partMatrices = objects.partMatrices,
                part = part ?: -1,
                printerProfile = profiles.printer.value,
                filamentProfile = profiles.filament.value,
                filamentProfiles = profiles.allFilaments.map(ProfileId::value).toTypedArray(),
                processProfile = profiles.process.value,
                facets = facets.value,
                meshPrefix = meshPrefix.value,
            ),
        )
    }

    override suspend fun paint(stroke: PaintStroke, meshPrefix: ScenePath): PaintingOutcome = withContext(Dispatchers.IO) {
        painting(
            NativeBindings.paintStroke(
                origin = doubleArrayOf(stroke.origin.x, stroke.origin.y, stroke.origin.z),
                direction = doubleArrayOf(stroke.direction.x, stroke.direction.y, stroke.direction.z),
                filament = stroke.filament,
                radius = stroke.radius,
                tool = stroke.tool.ordinal.toLong(),
                angle = stroke.angle,
                meshPrefix = meshPrefix.value,
            ),
        )
    }

    override suspend fun endPainting(): PaintingOutcome = withContext(Dispatchers.IO) {
        painting(NativeBindings.endPainting())
    }

    private fun painting(state: NativePainting): PaintingOutcome = if (state.status != NativeSliceResult.SUCCESS) {
        PaintingOutcome.Failure(state.message)
    } else {
        PaintingOutcome.Success(
            PaintedSurface(
                hit = state.hit,
                filaments = state.filaments.map { it.toInt() },
                meshes = state.meshes.map(::ScenePath),
                facets = PaintedFacets(state.facets),
            ),
        )
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
            instanceCounts = objects.instanceCounts,
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
        PlateInspectionOutcome.Success(
            result.objects.mapIndexed { index, copies -> copies.map { it.toInspection(plate[index].mesh) } },
        )
    }

    override suspend fun addPart(
        plateObject: PlacedModel,
        shape: String,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        val engineStatus = status()
        if (!engineStatus.ready) {
            return@withContext ModelInspectionOutcome.Failure(engineStatus.message ?: "OrcaSlicer engine is not ready")
        }
        val objects = NativePlate(listOf(plateObject))
        NativeBindings.addObjectPart(
            modelPath = plateObject.model.nativePath(),
            instanceCounts = objects.instanceCounts,
            placements = objects.placements,
            autoDrops = objects.autoDrops,
            shape = shape,
            type = type.ordinal.toLong(),
            printerProfile = profiles.printer.value,
            filamentProfile = profiles.filament.value,
            processProfile = profiles.process.value,
            meshPath = mesh.value,
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

    override suspend fun presets(): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.describePresets().toOutcome()
    }

    /** PresetChangeAction in orca_engine_adapter.hpp. */
    private val PresetChangeAction.native: Long
        get() = when (this) {
            PresetChangeAction.ASK -> NativePresetChangeAction.ASK
            PresetChangeAction.TRANSFER -> NativePresetChangeAction.TRANSFER
            PresetChangeAction.DISCARD -> NativePresetChangeAction.DISCARD
        }

    override suspend fun selectPreset(choice: PresetChoice, action: PresetChangeAction): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        val selection = action.native
        when (choice) {
            is PresetChoice.Printer -> NativeBindings.selectPreset(NativePresetChoice.PRINTER, choice.preset.value, selection)
            is PresetChoice.PrinterModel -> NativeBindings.selectPreset(NativePresetChoice.PRINTER_MODEL, choice.model, selection)
            is PresetChoice.NozzleDiameter -> NativeBindings.selectPreset(NativePresetChoice.NOZZLE_DIAMETER, choice.diameter, selection)
            is PresetChoice.Filament -> NativeBindings.selectPreset(NativePresetChoice.FILAMENT, choice.preset.value, selection)
            is PresetChoice.Process -> NativeBindings.selectPreset(NativePresetChoice.PROCESS, choice.preset.value, selection)
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

    override suspend fun settingsTab(kind: PresetKind): SettingsTabOutcome = whenReady(SettingsTabOutcome::Failure) {
        NativeBindings.describeSettingDefinitions(kind.native).toTabOutcome(kind)
    }

    override suspend fun settings(
        kind: PresetKind,
        page: String,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.describeSettings(
            kind.native,
            page,
            answers.answerIds(),
            answers.answerFlags(),
            model.settings.keys(),
            model.settings.values(),
            model.plate.keys(),
            model.plate.values(),
            model.parent.keys(),
            model.parent.values(),
        ).toOutcome()
    }

    override suspend fun changeSetting(
        kind: PresetKind,
        page: String,
        id: String,
        text: String,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.changeSetting(
            kind.native,
            page,
            id,
            text,
            answers.answerIds(),
            answers.answerFlags(),
            model.settings.keys(),
            model.settings.values(),
            model.plate.keys(),
            model.plate.values(),
            model.parent.keys(),
            model.parent.values(),
        ).toOutcome()
    }

    override suspend fun resetSettings(
        kind: PresetKind,
        page: String,
        ids: List<String>,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.resetSettings(
            kind.native,
            page,
            ids.toTypedArray(),
            answers.answerIds(),
            answers.answerFlags(),
            model.settings.keys(),
            model.settings.values(),
            model.plate.keys(),
            model.plate.values(),
            model.parent.keys(),
            model.parent.values(),
        ).toOutcome()
    }

    override suspend fun setSettingOverride(
        kind: PresetKind,
        page: String,
        id: String,
        enabled: Boolean,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.setSettingOverride(kind.native, page, id, enabled, answers.answerIds(), answers.answerFlags()).toOutcome()
    }

    override suspend fun setCompatiblePresets(
        kind: PresetKind,
        page: String,
        key: String,
        presets: List<String>,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.setCompatiblePresets(kind.native, page, key, presets.toTypedArray(), answers.answerIds(), answers.answerFlags()).toOutcome()
    }

    override suspend fun transferPresetOptions(kind: PresetKind, from: String, to: String, options: List<String>): PresetsOutcome =
        whenReady(PresetsOutcome::Failure) {
            NativeBindings.transferPresetOptions(kind.native, from, to, options.toTypedArray()).toOutcome()
        }

    override suspend fun createFilamentOptions(type: String, baseFilament: String): CreateFilamentOptionsOutcome =
        whenReady(CreateFilamentOptionsOutcome::Failure) {
            val result = NativeBindings.createFilamentOptions(type, baseFilament)
            if (result.status != NativeSceneStatus.SUCCESS) {
                CreateFilamentOptionsOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the filaments" })
            } else {
                CreateFilamentOptionsOutcome.Success(
                    vendors = result.vendors.toList(),
                    types = result.types.toList(),
                    baseFilaments = result.baseFilaments.toList(),
                    presets = choices(result.presetPrinters, result.presetNames),
                    copyPresets = choices(result.copyPrinters, result.copyNames),
                )
            }
        }

    /** The printers and presets the check boxes of a filament dialog stand for. */
    private fun choices(printers: Array<String>, presets: Array<String>): List<FilamentPresetChoice> =
        printers.mapIndexedNotNull { index, printer ->
            presets.getOrNull(index)?.let { FilamentPresetChoice(printer, it) }
        }

    override suspend fun createFilament(request: CreateFilamentRequest, answers: Map<String, Boolean>): PresetCreationOutcome =
        whenReady(PresetCreationOutcome::Failure) {
            NativeBindings.createFilament(
                request.vendor,
                request.customVendor,
                request.type,
                request.serial,
                request.presets.map(FilamentPresetChoice::printer).toTypedArray(),
                request.presets.map(FilamentPresetChoice::preset).toTypedArray(),
                answers.answerIds(),
                answers.answerFlags(),
            ).toOutcome()
        }

    override suspend fun createPrinterOptions(
        vendor: String,
        nozzle: String,
        presetVendor: String,
        printerPreset: String,
    ): CreatePrinterOptionsOutcome = whenReady(CreatePrinterOptionsOutcome::Failure) {
        val result = NativeBindings.createPrinterOptions(vendor, nozzle, presetVendor, printerPreset)
        if (result.status != NativeSceneStatus.SUCCESS) {
            CreatePrinterOptionsOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the printers" })
        } else {
            CreatePrinterOptionsOutcome.Success(
                vendors = result.vendors.toList(),
                models = result.models.toList(),
                nozzleDiameters = result.nozzleDiameters.toList(),
                presetVendors = result.presetVendors.toList(),
                printerPresets = result.printerPresets.toList(),
                filamentPresets = result.filamentPresets.toList(),
                processPresets = result.processPresets.toList(),
                printableArea = result.printableArea.toPoints(),
                maxPrintHeight = result.maxPrintHeight,
            )
        }
    }

    override suspend fun createPrinter(request: CreatePrinterRequest, answers: Map<String, Boolean>): PresetCreationOutcome =
        whenReady(PresetCreationOutcome::Failure) {
            NativeBindings.createPrinter(
                request.model,
                request.nozzle,
                request.printableArea.flatMap { listOf(it.x, it.y) }.toDoubleArray(),
                request.maxPrintHeight,
                request.customTexture,
                request.customModel,
                request.presetVendor,
                request.printerPreset,
                request.filamentPresets.toTypedArray(),
                request.processPresets.toTypedArray(),
                answers.answerIds(),
                answers.answerFlags(),
            ).toOutcome()
        }

    override suspend fun customFilaments(): CustomFilamentsOutcome = whenReady(CustomFilamentsOutcome::Failure) {
        val result = NativeBindings.customFilaments()
        if (result.status != NativeSceneStatus.SUCCESS) {
            CustomFilamentsOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the filaments" })
        } else {
            CustomFilamentsOutcome.Success(
                result.ids.mapIndexedNotNull { index, id -> result.names.getOrNull(index)?.let { CustomFilament(id, it) } },
            )
        }
    }

    override suspend fun filamentPresets(filamentId: String): FilamentPresetsOutcome = whenReady(FilamentPresetsOutcome::Failure) {
        val result = NativeBindings.filamentPresets(filamentId)
        if (result.status != NativeSceneStatus.SUCCESS) {
            FilamentPresetsOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the presets" })
        } else {
            FilamentPresetsOutcome.Success(
                FilamentPresetList(
                    name = result.name,
                    vendor = result.vendor,
                    type = result.type,
                    serial = result.serial,
                    presets = choices(result.printers, result.presets),
                ),
            )
        }
    }

    override suspend fun deleteFilamentPreset(preset: String, answers: Map<String, Boolean>): PresetCreationOutcome =
        whenReady(PresetCreationOutcome::Failure) {
            NativeBindings.deleteFilamentPreset(preset, answers.answerIds(), answers.answerFlags()).toOutcome()
        }

    override suspend fun addFilament(): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.addFilament().toOutcome()
    }

    override suspend fun removeFilament(index: Int): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.removeFilament(index.toLong()).toOutcome()
    }

    override suspend fun selectFilament(index: Int, name: ProfileId, action: PresetChangeAction): PresetsOutcome =
        whenReady(PresetsOutcome::Failure) {
            NativeBindings.selectFilament(index.toLong(), name.value, action.native).toOutcome()
        }

    override suspend fun setFilamentColor(index: Int, color: String): PresetsOutcome = whenReady(PresetsOutcome::Failure) {
        NativeBindings.setFilamentColor(index.toLong(), color).toOutcome()
    }

    override suspend fun physicalPrinters(): PhysicalPrintersOutcome = whenReady(PhysicalPrintersOutcome::Failure) {
        NativeBindings.physicalPrinters().toOutcome()
    }

    override suspend fun savePhysicalPrinter(printer: PhysicalPrinter, renamedFrom: String?): PhysicalPrintersOutcome =
        whenReady(PhysicalPrintersOutcome::Failure) {
            NativeBindings.savePhysicalPrinter(
                name = printer.name,
                presetNames = printer.presetNames.toTypedArray(),
                keys = printer.settings.keys(),
                values = printer.settings.values(),
                renamedFrom = renamedFrom,
            ).toOutcome()
        }

    override suspend fun deletePhysicalPrinter(name: String): PhysicalPrintersOutcome = whenReady(PhysicalPrintersOutcome::Failure) {
        NativeBindings.deletePhysicalPrinter(name).toOutcome()
    }

    override suspend fun importPresets(paths: List<String>, answers: Map<String, ConfigOverwriteAnswer>): ConfigTransferOutcome =
        whenReady(ConfigTransferOutcome::Failure) {
            NativeBindings.importPresets(
                paths.toTypedArray(),
                answers.keys.toTypedArray(),
                answers.values.map { it.ordinal.toLong() }.toLongArray(),
            ).toOutcome()
        }

    override suspend fun configExportOptions(kind: ConfigExportKind): ConfigExportOptionsOutcome =
        whenReady(ConfigExportOptionsOutcome::Failure) {
            val result = NativeBindings.configExportOptions(kind.ordinal.toLong())
            if (result.status != NativeSceneStatus.SUCCESS) {
                ConfigExportOptionsOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not list the presets" })
            } else {
                ConfigExportOptionsOutcome.Success(
                    entries = result.names.mapIndexed { index, name ->
                        ConfigExportEntry(name, result.counts.getOrElse(index) { 0 }.toInt())
                    },
                    note = result.note,
                )
            }
        }

    override suspend fun exportConfigs(kind: ConfigExportKind, names: List<String>, directory: String): ConfigTransferOutcome =
        whenReady(ConfigTransferOutcome::Failure) {
            NativeBindings.exportConfigs(kind.ordinal.toLong(), names.toTypedArray(), directory).toOutcome()
        }

    override suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean): PresetComparisonOutcome =
        whenReady(PresetComparisonOutcome::Failure) {
            NativeBindings.comparePresets(left.names(), right.names(), showAll).toComparison()
        }

    /** The order the bridge passes the presets of a side in. */
    private fun ComparedPresets.names() = arrayOf(printer, print, filament)

    /** PresetComparison in orca_engine_adapter.hpp. */
    private fun NativePresetComparison.toComparison(): PresetComparisonOutcome {
        if (status != NativeSceneStatus.SUCCESS) {
            return PresetComparisonOutcome.Failure(message.ifBlank { "OrcaSlicer could not compare the presets" })
        }
        return PresetComparisonOutcome.Success(
            kinds.map { compared ->
                PresetKindComparison(
                    kind = PresetKind.entries.getOrElse(compared.kind.toInt()) { PresetKind.PRINT },
                    leftPresets = compared.leftPresets.map { it.toItem() },
                    rightPresets = compared.rightPresets.map { it.toItem() },
                    left = compared.left,
                    right = compared.right,
                    problem = compared.problem,
                    changes = compared.changes.map(NativePresetChange::toChange),
                    edited = compared.edited,
                    editedDirty = compared.editedDirty,
                )
            },
        )
    }

    override suspend fun searchCatalog(): SearchCatalogOutcome = whenReady(SearchCatalogOutcome::Failure) {
        NativeBindings.searchCatalog().toOutcome()
    }

    override suspend fun thumbnailSizes(profiles: SlicingProfileSelection): ThumbnailSizesOutcome =
        whenReady(ThumbnailSizesOutcome::Failure) {
            val result = NativeBindings.thumbnailSizes(
                printerProfile = profiles.printer.value,
                filamentProfile = profiles.filament.value,
                filamentProfiles = profiles.filaments.map(ProfileId::value).toTypedArray(),
                processProfile = profiles.process.value,
            )
            if (result.status != NativeSceneStatus.SUCCESS) {
                ThumbnailSizesOutcome.Failure(result.message.ifBlank { "OrcaSlicer could not read the thumbnails of the printer" })
            } else {
                ThumbnailSizesOutcome.Success(result.sizes.toList().chunked(2).filter { it.size == 2 }.map { ThumbnailSize(it[0], it[1]) })
            }
        }

    override suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome =
        whenReady(GcodePlaceholdersOutcome::Failure) {
            NativeBindings.describeGcodePlaceholders(kind.native, key).toOutcome()
        }

    override suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo =
        whenReady({ GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true) }) {
            NativeBindings.describeGcodePlaceholder(key, presets).toInfo()
        }

    override suspend fun editCustomGcode(
        kind: PresetKind,
        page: String,
        key: String,
        value: String,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.editCustomGcode(kind.native, page, key, value, answers.answerIds(), answers.answerFlags()).toOutcome()
    }

    override suspend fun setRammingParameters(
        kind: PresetKind,
        page: String,
        parameters: String,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.setRammingParameters(kind.native, page, parameters, answers.answerIds(), answers.answerFlags()).toOutcome()
    }

    override suspend fun bedShape(): BedShapeOutcome = whenReady(BedShapeOutcome::Failure) {
        NativeBindings.describeBedShape().toOutcome()
    }

    override suspend fun setBedShape(shape: BedShape, customPath: ModelPath?, answers: Map<String, Boolean>): PresetSettingsOutcome =
        whenReady(PresetSettingsOutcome::Failure) {
            NativeBindings.setBedShape(
                kind = shape.kind.ordinal.toLong(),
                sizeX = shape.sizeX,
                sizeY = shape.sizeY,
                originX = shape.originX,
                originY = shape.originY,
                diameter = shape.diameter,
                customPath = customPath?.value,
                texture = shape.texture,
                model = shape.model,
                answerIds = answers.answerIds(),
                answers = answers.answerFlags(),
            ).toOutcome()
        }

    override suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome =
        whenReady(PresetNamesOutcome::Failure) {
            NativeBindings.compatiblePresetChoices(kind.native, key).toOutcome()
        }

    override suspend fun setSettingsMode(kind: PresetKind, mode: SettingsMode, model: ModelSettingsRequest): PresetSettingsOutcome =
        whenReady(PresetSettingsOutcome::Failure) {
            NativeBindings.setSettingsMode(
                kind.native,
                mode.native,
                model.settings.keys(),
                model.settings.values(),
                model.plate.keys(),
                model.plate.values(),
                model.parent.keys(),
                model.parent.values(),
            ).toOutcome()
        }

    override suspend fun setSettingsVariant(
        kind: PresetKind,
        page: String,
        variant: Int,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome =
        whenReady(PresetSettingsOutcome::Failure) {
            NativeBindings.setSettingsVariant(
                kind.native,
                page,
                variant.toLong(),
                answers.answerIds(),
                answers.answerFlags(),
                model.settings.keys(),
                model.settings.values(),
                model.plate.keys(),
                model.plate.values(),
                model.parent.keys(),
                model.parent.values(),
            ).toOutcome()
        }

    override suspend fun settingTooltip(kind: PresetKind, id: String): List<OrcaText> = whenReady({ emptyList() }) {
        NativeBindings.settingTooltip(kind.native, id).map { it.toText() }
    }

    override suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = whenReady(PresetNameOutcome::Failure) {
        NativeBindings.checkPresetName(kind.native, name).toOutcome()
    }

    override suspend fun savePreset(kind: PresetKind, name: String): PresetSettingsOutcome = whenReady(PresetSettingsOutcome::Failure) {
        NativeBindings.savePreset(kind.native, name).toOutcome()
    }

    override suspend fun deletePreset(kind: PresetKind, answers: Map<String, Boolean>): PresetSettingsOutcome =
        whenReady(PresetSettingsOutcome::Failure) {
            NativeBindings.deletePreset(kind.native, answers.answerIds(), answers.answerFlags()).toOutcome()
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
        if (asksUnsavedChanges) {
            return PresetsOutcome.UnsavedChanges(
                presets = toPresets(),
                kind = PresetKind.entries.getOrElse(changedKind.toInt()) { PresetKind.PRINT },
                changes = unsavedChanges.map(NativePresetChange::toChange),
                canTransfer = canTransfer,
                saveName = saveName,
                saveNameCopySuffix = saveNameCopySuffix,
            )
        }
        return PresetsOutcome.Success(toPresets())
    }

    private fun NativePresetState.toPresets(): Presets = Presets(
        selection = SlicingProfileSelection(
            printer = ProfileId(printer),
            filament = ProfileId(filament),
            process = ProfileId(process),
            filaments = filamentSlots.map(::ProfileId),
        ),
        setupRequired = setupRequired,
        printers = printers.map { it.toItem() },
        filaments = filaments.map { it.toItem() },
        processes = processes.map { it.toItem() },
        filamentColors = filamentColors.toList(),
        filamentTypes = filamentTypes.toList(),
        nozzleDiameters = nozzleDiameters.toList(),
        nozzleDiameter = nozzleDiameter,
    )

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
        val instanceCounts = IntArray(objects.size) { objects[it].instances.size }
        private val instances = objects.flatMap(PlacedModel::instances)
        val placements = instances.flatMap { it.placement.columns }.toDoubleArray()
        val autoDrops = BooleanArray(instances.size) { instances[it].autoDrop }
        val printable = BooleanArray(instances.size) { instances[it].printable }
        val settingKeys = Array(objects.size) { objects[it].settings.keys() }
        val settingValues = Array(objects.size) { objects[it].settings.values() }
        val partCounts = IntArray(objects.size) { objects[it].parts.size }
        private val parts = objects.flatMap(PlacedModel::parts)
        val partShapes = Array(parts.size) { parts[it].shape }
        val partTypes = LongArray(parts.size) { parts[it].type.ordinal.toLong() }
        val partMatrices = parts.flatMap { it.placement.columns }.toDoubleArray()
        val partSettingKeys = Array(parts.size) { parts[it].settings.keys() }
        val partSettingValues = Array(parts.size) { parts[it].settings.values() }
        val rangeCounts = IntArray(objects.size) { objects[it].layerRanges.size }
        private val ranges = objects.flatMap(PlacedModel::layerRanges)
        val rangeHeights = ranges.flatMap { listOf(it.bottom, it.top) }.toDoubleArray()
        val rangeSettingKeys = Array(ranges.size) { ranges[it].settings.keys() }
        val rangeSettingValues = Array(ranges.size) { ranges[it].settings.values() }
        /** The facets painted with the filaments of the plate, per object and per part. */
        val painted = Array(objects.size) { objects[it].painted.value }
        val partPainted = Array(parts.size) { parts[it].painted.value }
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
                wipeTower = request.wipeTower?.takeIf { result.wipeTowerWritten },
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

/** The overrides of an object or of the plate, as parallel arrays for the bridge. */
private fun ModelSettings.keys(): Array<String> = values.keys.toTypedArray()

private fun ModelSettings.values(): Array<String> = values.values.toTypedArray()

/** The overrides of every selected object, one pair of arrays each. */
private fun List<ModelSettings>.keys(): Array<Array<String>> = Array(size) { this[it].keys() }

private fun List<ModelSettings>.values(): Array<Array<String>> = Array(size) { this[it].values() }
