package app.orcinus.shadow.slicing.service

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.BedTypeChoice
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.CalibrationMode
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.CalibrationPrinter
import app.orcinus.shadow.core.model.CalibrationPrinterOutcome
import app.orcinus.shadow.core.model.LayerEditing
import app.orcinus.shadow.core.model.LayerEditingOutcome
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutId
import app.orcinus.shadow.core.model.CutInfo
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPartsOutcome
import app.orcinus.shadow.core.model.CutPlaneDescription
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.CutPreviewPart
import app.orcinus.shadow.core.model.DirtyPreset
import app.orcinus.shadow.core.model.DirtyPresetsOutcome
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.FilamentUsage
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.FlushVolumes
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeRules
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LoadedObject
import app.orcinus.shadow.core.model.LoadedProject
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjectCut
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.ObjectVolume
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintedSurface
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlacedInstance
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateValidation
import app.orcinus.shadow.core.model.PlateValidationMessage
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SetupFilament
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrinterModel
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.StepMeshOptions
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.WipeTowerOutcome
import app.orcinus.shadow.core.model.amounts
import app.orcinus.shadow.core.model.connectorKinds
import app.orcinus.shadow.core.model.connectorValues
import app.orcinus.shadow.core.model.cutConnectors
import app.orcinus.shadow.core.model.filamentUsagesOf

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
    it.objects = objects.toParcels()
    it.outputPath = output.value
    it.toolpathsPath = toolpaths?.value
    it.wipeTowerPath = wipeTower?.value
    it.sliceInfoPath = sliceInfo?.value
    it.printerProfile = printerProfile.value
    it.filamentProfile = filamentProfile.value
    it.filamentProfiles = filamentProfiles.map(ProfileId::value).toTypedArray()
    it.processProfile = processProfile.value
    it.plateSettings = plateSettings.toParcel()
    it.thumbnailSizes = thumbnails.flatMap { image -> listOf(image.size.width, image.size.height) }.toIntArray()
    it.thumbnailPaths = thumbnails.map { image -> image.path.value }.toTypedArray()
    it.layerGcodeHeights = layerGcodes.map(LayerGcode::printZ).toDoubleArray()
    it.layerGcodeTypes = layerGcodes.map { code -> code.type.name }.toTypedArray()
    it.layerGcodeExtruders = layerGcodes.map(LayerGcode::extruder).toIntArray()
    it.layerGcodeColors = layerGcodes.map(LayerGcode::color).toTypedArray()
    it.layerGcodeExtras = layerGcodes.map(LayerGcode::extra).toTypedArray()
    it.calibration = calibration?.toParcel()
    it.paPattern = paPattern?.toParcel()
}

internal fun CalibrationParams.toParcel() = CalibrationParcel().also {
    it.mode = mode.name
    it.extruderId = extruderId
    it.start = start
    it.end = end
    it.step = step
    it.printNumbers = printNumbers
    it.freqStartX = freqStartX
    it.freqEndX = freqEndX
    it.freqStartY = freqStartY
    it.freqEndY = freqEndY
    it.testModel = testModel
    it.shaperType = shaperType
    it.accelerations = accelerations.toDoubleArray()
    it.speeds = speeds.toDoubleArray()
}

internal fun CalibrationParcel.toCalibrationParams() = CalibrationParams(
    mode = CalibrationMode.valueOf(mode),
    start = start,
    end = end,
    step = step,
    printNumbers = printNumbers,
    freqStartX = freqStartX,
    freqEndX = freqEndX,
    freqStartY = freqStartY,
    freqEndY = freqEndY,
    testModel = testModel,
    shaperType = shaperType.orEmpty(),
    accelerations = accelerations?.toList().orEmpty(),
    speeds = speeds?.toList().orEmpty(),
    extruderId = extruderId,
)

/** The layer codes of a parcel's parallel arrays; none without heights. */
internal fun layerGcodesOf(
    heights: DoubleArray?,
    types: Array<String>?,
    extruders: IntArray?,
    colors: Array<String>?,
    extras: Array<String>?,
): List<LayerGcode> = heights?.let {
    heights.indices.map { index ->
        LayerGcode(
            printZ = heights[index],
            type = LayerGcodeType.valueOf(checkNotNull(types)[index]),
            extruder = checkNotNull(extruders)[index],
            color = checkNotNull(colors)[index],
            extra = checkNotNull(extras)[index],
        )
    }
}.orEmpty()

internal fun SliceRequestParcel.toSliceRequest() = SliceRequest(
    jobId = SliceJobId(jobId),
    objects = objects.toPlacedModels(),
    output = OutputPath(outputPath),
    toolpaths = toolpathsPath?.let(::ScenePath),
    wipeTower = wipeTowerPath?.let(::ScenePath),
    sliceInfo = sliceInfoPath?.let(::ScenePath),
    printerProfile = ProfileId(printerProfile),
    filamentProfile = ProfileId(filamentProfile),
    filamentProfiles = filamentProfiles.orEmpty().map(::ProfileId),
    processProfile = ProfileId(processProfile),
    plateSettings = plateSettings.toModelSettings(),
    thumbnails = thumbnailPaths.orEmpty().mapIndexedNotNull { index, path ->
        val sizes = thumbnailSizes ?: return@mapIndexedNotNull null
        if (2 * index + 1 >= sizes.size) null else ThumbnailImage(ThumbnailSize(sizes[2 * index], sizes[2 * index + 1]), ScenePath(path))
    },
    layerGcodes = layerGcodesOf(layerGcodeHeights, layerGcodeTypes, layerGcodeExtruders, layerGcodeColors, layerGcodeExtras),
    calibration = calibration?.toCalibrationParams(),
    paPattern = paPattern?.toCalibrationParams(),
)

internal fun ThumbnailSizesOutcome.toParcel() = ThumbnailSizesParcel().also {
    when (this) {
        is ThumbnailSizesOutcome.Failure -> it.error = message
        is ThumbnailSizesOutcome.Success -> it.sizes = sizes.flatMap { size -> listOf(size.width, size.height) }.toIntArray()
    }
}

internal fun ThumbnailSizesParcel.toThumbnailSizesOutcome(): ThumbnailSizesOutcome {
    error?.let { return ThumbnailSizesOutcome.Failure(it) }
    val widthsAndHeights = sizes ?: IntArray(0)
    return ThumbnailSizesOutcome.Success(widthsAndHeights.toList().chunked(2).filter { it.size == 2 }.map { ThumbnailSize(it[0], it[1]) })
}

internal fun List<PlacedModel>.toParcels(): Array<PlacedModelParcel> = Array(size) { index ->
    val placed = this[index]
    PlacedModelParcel().also { parcel ->
        parcel.model = placed.model.toParcel()
        parcel.meshPath = placed.mesh.value
        parcel.instances = Array(placed.instances.size) { copy ->
            val instance = placed.instances[copy]
            PlacedInstanceParcel().also {
                it.placement = instance.placement.columns.toDoubleArray()
                it.autoDrop = instance.autoDrop
                it.printable = instance.printable
            }
        }
        parcel.settings = placed.settings.toParcel()
        parcel.parts = Array(placed.parts.size) { placed.parts[it].toParcel() }
        parcel.painted = placed.painted.value.takeUnless(String::isEmpty)
        parcel.frame = placed.frame?.columns?.toDoubleArray()
        parcel.volume = placed.volume.toParcel()
        parcel.name = placed.name
        parcel.layerRanges = placed.layerRanges.toParcels()
        parcel.cutId = placed.cutId?.values()
        parcel.layerHeightProfile = placed.layerHeightProfile.toDoubleArray()
    }
}

private fun List<LayerRange>.toParcels(): Array<LayerRangeParcel> = Array(size) { at ->
    val range = this[at]
    LayerRangeParcel().also {
        it.bottom = range.bottom
        it.top = range.top
        it.settings = range.settings.toParcel()
    }
}

private fun Array<LayerRangeParcel>?.toLayerRanges(): List<LayerRange> =
    orEmpty().map { LayerRange(bottom = it.bottom, top = it.top, settings = it.settings.toModelSettings()) }

private fun ObjectVolume.toParcel() = ObjectVolumeParcel().also {
    it.name = name
    it.settings = settings.toParcel()
    it.splittable = splittable
    it.convertedFromInches = convertedFromInches
    it.convertedFromMeters = convertedFromMeters
    it.inputFile = inputFile
    it.cutInfo = cutInfo.values()
}

private fun ObjectVolumeParcel?.toObjectVolume(): ObjectVolume = this?.let {
    ObjectVolume(
        name = it.name.orEmpty(),
        settings = it.settings.toModelSettings(),
        splittable = it.splittable,
        convertedFromInches = it.convertedFromInches,
        convertedFromMeters = it.convertedFromMeters,
        inputFile = it.inputFile.orEmpty(),
        cutInfo = CutInfo.of(it.cutInfo),
    )
} ?: ObjectVolume()

internal fun Array<PlacedModelParcel>.toPlacedModels(): List<PlacedModel> = map { parcel ->
    PlacedModel(
        model = checkNotNull(parcel.model) { "A plate object has no model" }.toModelSource(),
        mesh = ScenePath(parcel.meshPath),
        instances = parcel.instances.orEmpty().map {
            PlacedInstance(placement = Transform3(it.placement.toList()), autoDrop = it.autoDrop, printable = it.printable)
        },
        settings = parcel.settings.toModelSettings(),
        parts = parcel.parts.orEmpty().map { it.toObjectPart() },
        layerRanges = parcel.layerRanges.toLayerRanges(),
        painted = PaintedFacets(parcel.painted.orEmpty()),
        frame = parcel.frame?.let { Transform3(it.toList()) },
        volume = parcel.volume.toObjectVolume(),
        name = parcel.name.orEmpty(),
        cutId = CutId.of(parcel.cutId),
        layerHeightProfile = parcel.layerHeightProfile?.toList().orEmpty(),
    )
}

private fun ObjectPart.toParcel() = ObjectPartParcel().also {
    it.shape = shape
    it.type = type.name
    it.meshPath = mesh.value
    it.placement = placement.columns.toDoubleArray()
    it.settings = settings.toParcel()
    it.painted = painted.value.takeUnless(String::isEmpty)
    it.source = source?.value
    it.name = name
    it.splittable = splittable
    it.convertedFromInches = convertedFromInches
    it.convertedFromMeters = convertedFromMeters
    it.inputFile = inputFile
    it.cutInfo = cutInfo.values()
}

private fun ObjectPartParcel.toObjectPart() = ObjectPart(
    shape = shape,
    type = VolumeType.valueOf(type),
    mesh = ScenePath(meshPath),
    placement = Transform3(placement.toList()),
    settings = settings.toModelSettings(),
    painted = PaintedFacets(painted.orEmpty()),
    source = source?.let(::ModelPath),
    name = name.orEmpty(),
    splittable = splittable,
    convertedFromInches = convertedFromInches,
    convertedFromMeters = convertedFromMeters,
    inputFile = inputFile.orEmpty(),
    cutInfo = CutInfo.of(cutInfo),
)

internal fun ProjectPlate.toParcel() = ProjectPlateParcel().also {
    it.name = name
    it.locked = locked
    it.settings = settings.toParcel()
    it.layerGcodeHeights = layerGcodes.map(LayerGcode::printZ).toDoubleArray()
    it.layerGcodeTypes = layerGcodes.map { code -> code.type.name }.toTypedArray()
    it.layerGcodeExtruders = layerGcodes.map(LayerGcode::extruder).toIntArray()
    it.layerGcodeColors = layerGcodes.map(LayerGcode::color).toTypedArray()
    it.layerGcodeExtras = layerGcodes.map(LayerGcode::extra).toTypedArray()
    it.thumbnailWidth = thumbnail?.size?.width ?: 0
    it.thumbnailHeight = thumbnail?.size?.height ?: 0
    it.thumbnailPath = thumbnail?.path?.value
    it.noLightThumbnailPath = noLightThumbnail?.path?.value
    it.topThumbnailPath = topThumbnail?.path?.value
    it.pickThumbnailPath = pickThumbnail?.path?.value
    it.sliceInfoPath = sliceInfo?.value
    it.gcodePath = gcode?.value
}

internal fun ProjectPlateParcel.toProjectPlate() = ProjectPlate(
    name = name.orEmpty(),
    locked = locked,
    settings = settings.toModelSettings(),
    layerGcodes = layerGcodesOf(layerGcodeHeights, layerGcodeTypes, layerGcodeExtruders, layerGcodeColors, layerGcodeExtras),
    thumbnail = thumbnailPath?.let { ThumbnailImage(ThumbnailSize(thumbnailWidth, thumbnailHeight), ScenePath(it)) },
    noLightThumbnail = noLightThumbnailPath?.let { ThumbnailImage(ThumbnailSize(thumbnailWidth, thumbnailHeight), ScenePath(it)) },
    topThumbnail = topThumbnailPath?.let { ThumbnailImage(ThumbnailSize(thumbnailWidth, thumbnailHeight), ScenePath(it)) },
    pickThumbnail = pickThumbnailPath?.let { ThumbnailImage(ThumbnailSize(thumbnailWidth, thumbnailHeight), ScenePath(it)) },
    sliceInfo = sliceInfoPath?.let(::ScenePath),
    gcode = gcodePath?.let(::OutputPath),
)

/** StepMeshOptions as the service passes them: linear and angle deflections, and split as 1 or 0. */
internal fun StepMeshOptions.toArray() = doubleArrayOf(linearDeflection, angleDeflection, if (splitCompound) 1.0 else 0.0)

internal fun DoubleArray.toStepMeshOptions() = StepMeshOptions(this[0], this[1], this[2] != 0.0)

/** StepMeshDialog's answers for [count] files as the service passes them: chosen as 1 or 0, then StepMeshOptions.toArray(). */
internal fun Map<Int, StepMeshOptions>.toArray(count: Int) = (0 until count).flatMap { file ->
    this[file]?.let { listOf(1.0) + it.toArray().toList() } ?: listOf(0.0, 0.0, 0.0, 0.0)
}.toDoubleArray()

internal fun DoubleArray.toStepMeshes(): Map<Int, StepMeshOptions> = (0 until size / 4)
    .filter { this[it * 4] != 0.0 }
    .associateWith { copyOfRange(it * 4 + 1, it * 4 + 4).toStepMeshOptions() }

internal fun ModelLoadOutcome.toParcel() = ModelLoadParcel().also {
    it.notices = notices.map { dialog -> dialog.toParcel() }.toTypedArray()
    it.appended = this is ModelLoadOutcome.Success && appended
    it.selectedVolume = (this as? ModelLoadOutcome.Success)?.selectedVolume ?: -1
    it.presetsChanged = this is ModelLoadOutcome.Success && presetsChanged
    it.calibration = (this as? ModelLoadOutcome.Success)?.calibration?.toParcel()
    it.plateCount = (this as? ModelLoadOutcome.Success)?.plateCount ?: 0
    it.splitToObjects = this is ModelLoadOutcome.Success && splitToObjects
    (this as? ModelLoadOutcome.Success)?.project?.let { project ->
        it.plates = project.plates.map { plate -> plate.toParcel() }.toTypedArray()
        it.projectInfo = project.info?.value
    }
    when (this) {
        is ModelLoadOutcome.Failure -> it.error = message
        is ModelLoadOutcome.Question -> it.question = question.toParcel()
        is ModelLoadOutcome.StepMesh -> {
            it.stepMesh = options.toArray()
            it.stepFile = file
        }
        is ModelLoadOutcome.Success -> it.objects = objects.map { loaded ->
            LoadedObjectParcel().also { parcel ->
                parcel.name = loaded.name
                parcel.source = loaded.source.value
                parcel.frame = loaded.frame.columns.toDoubleArray()
                parcel.parts = loaded.parts.map { part -> part.toParcel() }.toTypedArray()
                parcel.settings = loaded.settings.toParcel()
                parcel.volume = loaded.volume.toParcel()
                parcel.instances = loaded.instances.map { instance -> ModelInspectionOutcome.Success(instance.inspection).toParcel() }.toTypedArray()
                parcel.autoDrops = loaded.instances.map(PlateInstance::autoDrop).toBooleanArray()
                parcel.printables = loaded.instances.map(PlateInstance::printable).toBooleanArray()
                parcel.painted = loaded.painted.value.takeUnless(String::isEmpty)
                parcel.layerRanges = loaded.layerRanges.toParcels()
                parcel.cutId = loaded.cutId?.values()
                parcel.inputFile = loaded.inputFile.takeUnless(String::isEmpty)
                parcel.layerHeightProfile = loaded.layerHeightProfile.toDoubleArray()
            }
        }.toTypedArray()
    }
}

internal fun ModelLoadParcel.toModelLoadOutcome(): ModelLoadOutcome {
    val shown = notices.orEmpty().map { it.toDialog() }
    error?.let { return ModelLoadOutcome.Failure(it, shown) }
    question?.let { return ModelLoadOutcome.Question(it.toDialog(), shown) }
    stepMesh?.let { return ModelLoadOutcome.StepMesh(it.toStepMeshOptions(), shown, stepFile) }
    return ModelLoadOutcome.Success(
        objects.orEmpty().map { parcel ->
            LoadedObject(
                name = parcel.name,
                source = ModelPath(parcel.source),
                frame = Transform3(parcel.frame.toList()),
                parts = parcel.parts.orEmpty().map { it.toObjectPart() },
                settings = parcel.settings.toModelSettings(),
                volume = parcel.volume.toObjectVolume(),
                instances = parcel.instances.orEmpty().mapIndexed { index, instance ->
                    PlateInstance(
                        instance.toInspection(),
                        autoDrop = parcel.autoDrops?.getOrNull(index) ?: true,
                        printable = parcel.printables?.getOrNull(index) ?: true,
                    )
                },
                painted = PaintedFacets(parcel.painted.orEmpty()),
                layerRanges = parcel.layerRanges.toLayerRanges(),
                cutId = CutId.of(parcel.cutId),
                inputFile = parcel.inputFile.orEmpty(),
                layerHeightProfile = parcel.layerHeightProfile?.toList().orEmpty(),
            )
        },
        shown,
        appended,
        selectedVolume.takeIf { it >= 0 },
        project = plates?.let { parcels ->
            LoadedProject(
                plates = parcels.map { it.toProjectPlate() }.ifEmpty { listOf(ProjectPlate()) },
                info = projectInfo?.let(::ScenePath),
            )
        },
        presetsChanged = presetsChanged,
        calibration = calibration?.toCalibrationParams(),
        plateCount = plateCount,
        splitToObjects = splitToObjects,
    )
}

internal fun SliceOutcome.toParcel() = SliceOutcomeParcel().also {
    it.jobId = jobId.value
    when (this) {
        is SliceOutcome.Success -> {
            it.kind = SliceOutcomeParcel.SUCCESS
            it.gcodePath = gcodePath.value
            it.toolpathsPath = toolpaths?.value
            it.wipeTowerPath = wipeTower?.value
            it.sliceInfoPath = sliceInfo?.value
            it.layerCount = statistics.layerCount
            it.estimatedPrintTimeSeconds = statistics.estimatedPrintTimeSeconds
            it.filamentMillimeters = statistics.filamentMillimeters
            it.cost = statistics.cost
            it.filaments = statistics.filaments.map(FilamentUsage::filament).toIntArray()
            it.filamentAmounts = statistics.filaments.amounts()
            it.sequential = layerGcodeRules.sequential
            it.canChangeFilament = layerGcodeRules.canChangeFilament
            it.hasTemplate = layerGcodeRules.hasTemplate
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
            statistics = SliceStatistics(
                layerCount,
                estimatedPrintTimeSeconds,
                filamentMillimeters,
                cost,
                filamentUsagesOf(filaments ?: IntArray(0), filamentAmounts ?: DoubleArray(0)),
            ),
            toolpaths = toolpathsPath?.let(::ScenePath),
            wipeTower = wipeTowerPath?.let(::ScenePath),
            sliceInfo = sliceInfoPath?.let(::ScenePath),
            layerGcodeRules = LayerGcodeRules(sequential, canChangeFilament, hasTemplate),
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

internal fun SlicingProfileSelection.toParcel() = ProfilesParcel().also {
    it.printer = printer.value
    it.filament = filament.value
    it.process = process.value
    it.filaments = filaments.map(ProfileId::value).toTypedArray()
}

internal fun ProfilesParcel.toProfiles() = SlicingProfileSelection(
    printer = ProfileId(printer),
    filament = ProfileId(filament),
    process = ProfileId(process),
    filaments = filaments.orEmpty().map(::ProfileId),
)

internal fun ModelSource.toParcel() = ModelSourceParcel().also {
    when (this) {
        is ModelSource.LocalFile -> it.modelPath = path.value
        is ModelSource.BuiltIn -> it.builtInModel = model.name
    }
}

internal fun ModelSourceParcel.toModelSource(): ModelSource =
    builtInModel?.let { ModelSource.BuiltIn(BuiltInModel.valueOf(it)) }
        ?: ModelSource.LocalFile(ModelPath(checkNotNull(modelPath) { "Model source has no model" }))

internal fun ModelInspectionOutcome.toParcel() = InspectionParcel().also {
    when (this) {
        is ModelInspectionOutcome.Failure -> it.error = message
        is ModelInspectionOutcome.Success -> {
            it.facetCount = inspection.facetCount
            it.openEdges = inspection.openEdges
            it.widthMillimeters = inspection.dimensions.widthMillimeters
            it.depthMillimeters = inspection.dimensions.depthMillimeters
            it.heightMillimeters = inspection.dimensions.heightMillimeters
            it.meshPath = inspection.mesh.value
            it.placement = inspection.placement.columns.toDoubleArray()
            it.fit = inspection.fit.name
            with(inspection.boundingSphere) {
                it.sphereCenter = doubleArrayOf(center.x, center.y, center.z)
                it.sphereRadius = radius
            }
            it.rotationDegrees = with(inspection.rotationDegrees) { doubleArrayOf(x, y, z) }
            it.unscaledSize = with(inspection.unscaledDimensions) { doubleArrayOf(widthMillimeters, depthMillimeters, heightMillimeters) }
            it.boxCenter = with(inspection.boxCenter) { doubleArrayOf(x, y, z) }
        }
    }
}

internal fun InspectionParcel.toInspectionOutcome(): ModelInspectionOutcome {
    error?.let { return ModelInspectionOutcome.Failure(it) }
    return ModelInspectionOutcome.Success(toInspection())
}

private fun InspectionParcel.toInspection() = ModelInspection(
    facetCount = facetCount,
    openEdges = openEdges,
    dimensions = ModelDimensions(widthMillimeters, depthMillimeters, heightMillimeters),
    boxCenter = checkNotNull(boxCenter).toVector(),
    mesh = ScenePath(checkNotNull(meshPath)),
    placement = Transform3(checkNotNull(placement).toList()),
    fit = BuildVolumeFit.valueOf(checkNotNull(fit)),
    boundingSphere = BoundingSphere(checkNotNull(sphereCenter).toVector(), sphereRadius),
    rotationDegrees = checkNotNull(rotationDegrees).toVector(),
    unscaledDimensions = checkNotNull(unscaledSize).let { ModelDimensions(it[0], it[1], it[2]) },
)

internal fun PlateInspectionOutcome.toParcel() = PlateInspectionParcel().also {
    when (this) {
        is PlateInspectionOutcome.Failure -> it.error = message
        is PlateInspectionOutcome.Success -> {
            it.inspections = Array(inspections.size) { index ->
                PlateObjectInspectionParcel().also { object_ ->
                    object_.instances = Array(inspections[index].size) { copy ->
                        ModelInspectionOutcome.Success(inspections[index][copy]).toParcel()
                    }
                }
            }
            it.plates = plates ?: 0
        }
    }
}

internal fun PlateInspectionParcel.toPlateInspectionOutcome(): PlateInspectionOutcome {
    error?.let { return PlateInspectionOutcome.Failure(it) }
    return PlateInspectionOutcome.Success(
        checkNotNull(inspections).map { object_ -> object_.instances.orEmpty().map { it.toInspection() } },
        plates = plates.takeIf { it > 0 },
    )
}

internal fun FlushVolumesOutcome.toParcel() = FlushVolumesParcel().also {
    when (this) {
        is FlushVolumesOutcome.Failure -> it.error = message
        is FlushVolumesOutcome.Success -> with(volumes) {
            it.filaments = filaments
            it.nozzles = nozzles
            it.matrix = matrix.toDoubleArray()
            it.automatic = automatic.toDoubleArray()
            it.multipliers = multipliers.toDoubleArray()
            it.modified = modified
        }
    }
    it.updated = this is FlushVolumesOutcome.Success && updated
}

internal fun FlushVolumesParcel.toOutcome(): FlushVolumesOutcome = error?.let(FlushVolumesOutcome::Failure)
    ?: FlushVolumesOutcome.Success(
        FlushVolumes(
            filaments = filaments,
            nozzles = nozzles,
            matrix = matrix?.toList().orEmpty(),
            automatic = automatic?.toList().orEmpty(),
            multipliers = multipliers?.toList().orEmpty(),
            modified = modified,
        ),
        updated = updated,
    )

internal fun PaintingOutcome.toParcel() = PaintingParcel().also {
    when (this) {
        is PaintingOutcome.Failure -> it.error = message
        is PaintingOutcome.Success -> with(surface) {
            it.hit = hit
            it.states = states.toIntArray()
            it.meshes = meshes.map(ScenePath::value).toTypedArray()
            it.facets = facets.value
            it.canUndo = canUndo
            it.canRedo = canRedo
        }
    }
}

internal fun PaintingParcel.toOutcome(): PaintingOutcome = error?.let(PaintingOutcome::Failure)
    ?: PaintingOutcome.Success(
        PaintedSurface(
            hit = hit,
            states = states?.toList().orEmpty(),
            meshes = meshes?.map(::ScenePath).orEmpty(),
            facets = PaintedFacets(facets.orEmpty()),
            canUndo = canUndo,
            canRedo = canRedo,
        ),
    )

internal fun WipeTowerOutcome.toParcel() = WipeTowerParcel().also {
    when (this) {
        is WipeTowerOutcome.Failure -> it.error = message
        is WipeTowerOutcome.Success -> with(tower) {
            it.shown = shown
            it.x = x
            it.y = y
            it.width = width
            it.depth = depth
            it.height = height
            it.rotation = rotation
            it.brimWidth = brimWidth
            it.filaments = filaments.toIntArray()
            it.primeTower = primeTower
            it.flushInto = flushInto.map(FlushOption::name).toTypedArray()
        }
    }
}

internal fun WipeTowerParcel.toOutcome(): WipeTowerOutcome = error?.let(WipeTowerOutcome::Failure)
    ?: WipeTowerOutcome.Success(
        WipeTower(
            shown = shown,
            x = x,
            y = y,
            width = width,
            depth = depth,
            height = height,
            rotation = rotation,
            brimWidth = brimWidth,
            filaments = filaments?.toList().orEmpty(),
            primeTower = primeTower,
            flushInto = flushInto.orEmpty().mapNotNullTo(mutableSetOf()) { name -> FlushOption.entries.firstOrNull { it.name == name } },
        ),
    )

internal fun PlateDescriptionOutcome.toParcel() = PlateDescriptionParcel().also {
    when (this) {
        is PlateDescriptionOutcome.Failure -> it.error = message
        is PlateDescriptionOutcome.Success -> with(description) {
            it.printableArea = geometry.printableArea.toCoordinates()
            it.printableHeight = geometry.printableHeight
            it.plateTriangles = geometry.plateTriangles.toCoordinates()
            it.excludeTriangles = geometry.excludeTriangles.toCoordinates()
            it.thinGridLines = geometry.thinGridLines.toCoordinates()
            it.boldGridLines = geometry.boldGridLines.toCoordinates()
            it.bedModel = geometry.bedModel?.value
            it.bedTexture = geometry.bedTexture?.value
            it.filamentColor = floatArrayOf(filamentColor.red, filamentColor.green, filamentColor.blue, filamentColor.alpha)
        }
    }
}

internal fun PlateDescriptionParcel.toPlateDescriptionOutcome(): PlateDescriptionOutcome {
    error?.let { return PlateDescriptionOutcome.Failure(it) }
    val color = checkNotNull(filamentColor)
    return PlateDescriptionOutcome.Success(
        PlateDescription(
            geometry = PlateGeometry(
                printableArea = printableArea.toPoints(),
                printableHeight = printableHeight,
                plateTriangles = plateTriangles.toPoints(),
                excludeTriangles = excludeTriangles.toPoints(),
                thinGridLines = thinGridLines.toPoints(),
                boldGridLines = boldGridLines.toPoints(),
                bedModel = bedModel?.let(::ScenePath),
                bedTexture = bedTexture?.let(::ScenePath),
            ),
            filamentColor = ColorRgba(color[0], color[1], color[2], color[3]),
        ),
    )
}

private fun List<Point2>.toCoordinates() = DoubleArray(size * 2) { index ->
    val point = this[index / 2]
    if (index % 2 == 0) point.x else point.y
}

private fun DoubleArray?.toPoints(): List<Point2> {
    val coordinates = this ?: return emptyList()
    return (coordinates.indices step 2).map { Point2(coordinates[it], coordinates[it + 1]) }
}

private fun DoubleArray.toVector() = Vector3(this[0], this[1], this[2])

internal fun Manipulation.parcelName(): String = when (this) {
    Manipulation.Move -> "Move"
    Manipulation.Rotate -> "Rotate"
    Manipulation.Scale -> "Scale"
    Manipulation.ResetRotation -> "ResetRotation"
    is Manipulation.LayOnFace -> "LayOnFace"
    Manipulation.EnsureOnBed -> "EnsureOnBed"
    is Manipulation.Mirror -> "Mirror${axis.name}"
    Manipulation.Center -> "Center"
    Manipulation.Drop -> "Drop"
}

internal fun Manipulation.parcelFaceNormal(): DoubleArray? = (this as? Manipulation.LayOnFace)?.normal?.let { doubleArrayOf(it.x, it.y, it.z) }

internal fun manipulationOf(name: String, faceNormal: DoubleArray?): Manipulation = when (name) {
    "Move" -> Manipulation.Move
    "Rotate" -> Manipulation.Rotate
    "Scale" -> Manipulation.Scale
    "ResetRotation" -> Manipulation.ResetRotation
    "LayOnFace" -> Manipulation.LayOnFace(checkNotNull(faceNormal).toVector())
    "EnsureOnBed" -> Manipulation.EnsureOnBed
    "MirrorX" -> Manipulation.Mirror(Axis.X)
    "MirrorY" -> Manipulation.Mirror(Axis.Y)
    "MirrorZ" -> Manipulation.Mirror(Axis.Z)
    "Center" -> Manipulation.Center
    "Drop" -> Manipulation.Drop
    else -> error("Unknown manipulation $name")
}

internal fun PlateManipulation.parcelName(): String = when (this) {
    is PlateManipulation.AutoOrient -> "AutoOrient"
    is PlateManipulation.Arrange -> "Arrange"
    PlateManipulation.UpdatePrintVolume -> "UpdatePrintVolume"
    is PlateManipulation.ArrangePlate -> "ArrangePlate"
    is PlateManipulation.FillBed -> "FillBed"
}

internal fun PlateManipulation.parcelSelected(): Array<String> = when (this) {
    is PlateManipulation.AutoOrient -> selected.map(ScenePath::value).toTypedArray()
    is PlateManipulation.FillBed -> arrayOf(mesh.value)
    else -> emptyArray()
}

internal fun PlateManipulation.parcelInstance(): Int = (this as? PlateManipulation.FillBed)?.instance ?: -1

internal fun PlateManipulation.parcelArrangeSettings(): ArrangeSettingsParcel? = when (this) {
    is PlateManipulation.Arrange -> settings
    is PlateManipulation.ArrangePlate -> settings
    is PlateManipulation.FillBed -> settings
    else -> null
}?.let { settings ->
    ArrangeSettingsParcel().also {
        it.distance = settings.distance
        it.enableRotation = settings.enableRotation
        it.allowMultiMaterialsOnSamePlate = settings.allowMultiMaterialsOnSamePlate
        it.alignToYAxis = settings.alignToYAxis
    }
}

internal fun plateManipulationOf(
    name: String,
    selected: Array<String>,
    arrange: ArrangeSettingsParcel?,
    instance: Int,
    lockedPlates: IntArray,
): PlateManipulation {
    fun settings() = checkNotNull(arrange).let {
        ArrangeSettings(it.distance, it.enableRotation, it.allowMultiMaterialsOnSamePlate, it.alignToYAxis)
    }
    val locked = lockedPlates.toSet()
    return when (name) {
        "AutoOrient" -> PlateManipulation.AutoOrient(selected.mapTo(LinkedHashSet(), ::ScenePath), locked)
        "Arrange" -> PlateManipulation.Arrange(settings(), locked)
        "UpdatePrintVolume" -> PlateManipulation.UpdatePrintVolume
        "ArrangePlate" -> PlateManipulation.ArrangePlate(settings(), locked)
        "FillBed" -> PlateManipulation.FillBed(ScenePath(selected.single()), instance.takeIf { it >= 0 }, settings(), locked)
        else -> error("Unknown manipulation $name")
    }
}

internal fun PresetsOutcome.toParcel() = PresetsParcel().also { parcel ->
    when (this) {
        is PresetsOutcome.Failure -> parcel.error = message
        is PresetsOutcome.Success -> parcel.fill(presets)
        is PresetsOutcome.UnsavedChanges -> {
            parcel.fill(presets)
            parcel.asksUnsavedChanges = true
            parcel.changedKind = kind.name
            parcel.canTransfer = canTransfer
            parcel.saveName = saveName
            parcel.saveNameCopySuffix = saveNameCopySuffix
            parcel.unsavedChanges = changes.map { it.toParcel() }.toTypedArray()
        }
    }
}

private fun PresetsParcel.fill(presets: Presets) {
    selection = presets.selection.toParcel()
    setupRequired = presets.setupRequired
    printers = presets.printers.toParcels()
    filaments = presets.filaments.toParcels()
    processes = presets.processes.toParcels()
    filamentColors = presets.filamentColors.toTypedArray()
    filamentTypes = presets.filamentTypes.toTypedArray()
    nozzleDiameters = presets.nozzleDiameters.toTypedArray()
    nozzleDiameter = presets.nozzleDiameter
    bedTypeValues = presets.bedTypes.map(BedTypeChoice::value).toTypedArray()
    bedTypeLabels = presets.bedTypes.map(BedTypeChoice::label).toTypedArray()
}

internal fun PresetChange.toParcel() = PresetChangeParcel().also {
    it.id = id
    it.category = category.toParcels()
    it.group = group.toParcels()
    it.label = label.toParcels()
    it.oldValue = oldValue.toParcels()
    it.newValue = newValue.toParcels()
}

internal fun PresetChangeParcel.toPresetChange() = PresetChange(
    id = id,
    category = category.toTexts(),
    group = group.toTexts(),
    label = label.toTexts(),
    oldValue = oldValue.toTexts(),
    newValue = newValue.toTexts(),
)

internal fun DirtyPresetsOutcome.toParcel() = DirtyPresetsParcel().also { parcel ->
    when (this) {
        is DirtyPresetsOutcome.Failure -> parcel.error = message
        is DirtyPresetsOutcome.Success -> parcel.presets = presets.map { preset ->
            DirtyPresetParcel().also {
                it.kind = preset.kind.name
                it.name = preset.name
                it.canOverwrite = preset.canOverwrite
                it.saveName = preset.saveName
                it.saveNameCopySuffix = preset.saveNameCopySuffix
                it.changes = preset.changes.map { change -> change.toParcel() }.toTypedArray()
            }
        }.toTypedArray()
    }
}

internal fun DirtyPresetsParcel.toDirtyPresetsOutcome(): DirtyPresetsOutcome {
    error?.let { return DirtyPresetsOutcome.Failure(it) }
    return DirtyPresetsOutcome.Success(
        presets.orEmpty().map { parcel ->
            DirtyPreset(
                kind = PresetKind.valueOf(parcel.kind),
                name = parcel.name,
                canOverwrite = parcel.canOverwrite,
                saveName = parcel.saveName,
                saveNameCopySuffix = parcel.saveNameCopySuffix,
                changes = parcel.changes.orEmpty().map { it.toPresetChange() },
            )
        },
    )
}

internal fun PresetsParcel.toPresetsOutcome(): PresetsOutcome {
    error?.let { return PresetsOutcome.Failure(it) }
    val presets = Presets(
        selection = checkNotNull(selection).toProfiles(),
        setupRequired = setupRequired,
        printers = printers.toItems(),
        filaments = filaments.toItems(),
        processes = processes.toItems(),
        filamentColors = filamentColors.orEmpty().toList(),
        filamentTypes = filamentTypes.orEmpty().toList(),
        nozzleDiameters = nozzleDiameters.orEmpty().toList(),
        nozzleDiameter = nozzleDiameter.orEmpty(),
        bedTypes = bedTypeValues.orEmpty().zip(bedTypeLabels.orEmpty(), ::BedTypeChoice),
    )
    if (!asksUnsavedChanges) {
        return PresetsOutcome.Success(presets)
    }
    return PresetsOutcome.UnsavedChanges(
        presets = presets,
        kind = PresetKind.valueOf(changedKind.orEmpty()),
        changes = unsavedChanges.orEmpty().map { it.toPresetChange() },
        canTransfer = canTransfer,
        saveName = saveName.orEmpty(),
        saveNameCopySuffix = saveNameCopySuffix,
    )
}

internal fun List<PresetListItem>.toParcels(): Array<PresetItemParcel> = Array(size) { index ->
    val item = this[index]
    PresetItemParcel().also {
        it.name = item.name
        it.label = item.label
        it.group = item.group.name
        it.subgroup = item.subgroup
        it.subgroupMsgid = item.subgroupMsgid
        it.selected = item.selected
    }
}

internal fun Array<PresetItemParcel>?.toItems(): List<PresetListItem> =
    orEmpty().map { PresetListItem(it.name, it.label, PresetGroup.valueOf(it.group), it.subgroup, it.selected, it.subgroupMsgid) }

internal fun PresetChoice.parcelKind(): String = when (this) {
    is PresetChoice.Printer -> "Printer"
    is PresetChoice.PrinterModel -> "PrinterModel"
    is PresetChoice.NozzleDiameter -> "NozzleDiameter"
    is PresetChoice.Filament -> "Filament"
    is PresetChoice.Process -> "Process"
}

internal fun PresetChoice.parcelValue(): String = when (this) {
    is PresetChoice.Printer -> preset.value
    is PresetChoice.PrinterModel -> model
    is PresetChoice.NozzleDiameter -> diameter
    is PresetChoice.Filament -> preset.value
    is PresetChoice.Process -> preset.value
}

internal fun presetChoiceOf(kind: String, value: String): PresetChoice = when (kind) {
    "Printer" -> PresetChoice.Printer(ProfileId(value))
    "PrinterModel" -> PresetChoice.PrinterModel(value)
    "NozzleDiameter" -> PresetChoice.NozzleDiameter(value)
    "Filament" -> PresetChoice.Filament(ProfileId(value))
    "Process" -> PresetChoice.Process(ProfileId(value))
    else -> error("Unknown preset choice $kind")
}

internal fun SetupPrintersOutcome.toParcel() = SetupPrintersParcel().also {
    when (this) {
        is SetupPrintersOutcome.Failure -> it.error = message
        is SetupPrintersOutcome.Success -> it.models = Array(models.size) { index ->
            val model = models[index]
            SetupPrinterModelParcel().also { parcel ->
                parcel.vendor = model.vendor
                parcel.id = model.id
                parcel.name = model.name
                parcel.nozzleDiameters = model.nozzleDiameters.toTypedArray()
                parcel.defaultMaterials = model.defaultMaterials.toTypedArray()
                parcel.cover = model.cover
                parcel.installedNozzles = model.installedNozzles.toTypedArray()
            }
        }
    }
}

internal fun SetupPrintersParcel.toSetupPrintersOutcome(): SetupPrintersOutcome {
    error?.let { return SetupPrintersOutcome.Failure(it) }
    return SetupPrintersOutcome.Success(
        checkNotNull(models).map {
            SetupPrinterModel(
                vendor = it.vendor,
                id = it.id,
                name = it.name,
                nozzleDiameters = it.nozzleDiameters.toList(),
                defaultMaterials = it.defaultMaterials.toList(),
                cover = it.cover,
                installedNozzles = it.installedNozzles.toList(),
            )
        },
    )
}

internal fun SetupFilamentsOutcome.toParcel() = SetupFilamentsParcel().also {
    when (this) {
        is SetupFilamentsOutcome.Failure -> it.error = message
        is SetupFilamentsOutcome.Success -> it.filaments = Array(filaments.size) { index ->
            val filament = filaments[index]
            SetupFilamentParcel().also { parcel ->
                parcel.name = filament.name
                parcel.vendor = filament.vendor
                parcel.type = filament.type
                parcel.models = filament.models.toIntArray()
                parcel.selected = filament.selected
            }
        }
    }
}

internal fun SetupFilamentsParcel.toSetupFilamentsOutcome(): SetupFilamentsOutcome {
    error?.let { return SetupFilamentsOutcome.Failure(it) }
    return SetupFilamentsOutcome.Success(
        checkNotNull(filaments).map { SetupFilament(it.name, it.vendor, it.type, it.models.toList(), it.selected) },
    )
}

internal fun FlatteningPlanesOutcome.toParcel() = FlatteningPlanesParcel().also {
    when (this) {
        is FlatteningPlanesOutcome.Failure -> it.error = message
        is FlatteningPlanesOutcome.Success -> {
            it.normals = planes.flatMap { plane -> listOf(plane.normal.x, plane.normal.y, plane.normal.z) }.toDoubleArray()
            it.vertexCounts = planes.map { plane -> plane.polygon.size }.toIntArray()
            it.vertices = planes.flatMap { plane -> plane.polygon.flatMap { point -> listOf(point.x, point.y, point.z) } }.toDoubleArray()
        }
    }
}

internal fun FlatteningPlanesParcel.toOutcome(): FlatteningPlanesOutcome {
    error?.let { return FlatteningPlanesOutcome.Failure(it) }
    val normals = checkNotNull(normals)
    val counts = checkNotNull(vertexCounts)
    val vertices = checkNotNull(vertices)
    var offset = 0
    return FlatteningPlanesOutcome.Success(
        counts.indices.map { plane ->
            val polygon = (0 until counts[plane]).map { point ->
                val index = (offset + point) * 3
                Vector3(vertices[index], vertices[index + 1], vertices[index + 2])
            }
            offset += counts[plane]
            FlatteningPlane(Vector3(normals[plane * 3], normals[plane * 3 + 1], normals[plane * 3 + 2]), polygon)
        },
    )
}

internal fun CalibrationPrinterOutcome.toParcel() = CalibrationPrinterParcel().also {
    when (this) {
        is CalibrationPrinterOutcome.Success -> {
            it.gcodeFlavor = printer.gcodeFlavor
            it.junctionDeviation = printer.junctionDeviation
            it.shaperTypes = printer.shaperTypes.toTypedArray()
        }
        is CalibrationPrinterOutcome.Failure -> {
            it.error = message
            it.gcodeFlavor = ""
            it.shaperTypes = emptyArray()
        }
    }
}

internal fun CalibrationPrinterParcel.toOutcome(): CalibrationPrinterOutcome = error?.let { CalibrationPrinterOutcome.Failure(it) }
    ?: CalibrationPrinterOutcome.Success(CalibrationPrinter(gcodeFlavor, junctionDeviation, shaperTypes.toList()))

internal fun ObjectCut.toParcel() = CutParcel().also {
    it.instance = instance
    it.plane = plane.columns.toDoubleArray()
    it.keepUpper = keepUpper
    it.keepLower = keepLower
    it.keepAsParts = keepAsParts
    it.placeOnCutUpper = placeOnCutUpper
    it.placeOnCutLower = placeOnCutLower
    it.flipUpper = flipUpper
    it.flipLower = flipLower
    it.connectorValues = connectors.connectorValues()
    it.connectorKinds = connectors.connectorKinds()
    it.snapSpace = snapSpace
    it.snapBulge = snapBulge
    it.connectorName = connectorName
    it.dovetail = dovetail
    it.groove = groove.values()
    it.radius = radius
    it.partsPlane = parts?.plane?.columns?.toDoubleArray() ?: DoubleArray(0)
    it.parts = parts?.selected?.toBooleanArray() ?: BooleanArray(0)
}

/** CutPartSelection from its plane and flags, none for an empty plane. */
internal fun cutParts(plane: DoubleArray, selected: BooleanArray): CutPartSelection? =
    if (plane.size == 16) CutPartSelection(Transform3(plane.toList()), selected.toList()) else null

internal fun CutParcel.toCut() = ObjectCut(
    instance = instance,
    plane = Transform3(plane.toList()),
    keepUpper = keepUpper,
    keepLower = keepLower,
    keepAsParts = keepAsParts,
    placeOnCutUpper = placeOnCutUpper,
    placeOnCutLower = placeOnCutLower,
    flipUpper = flipUpper,
    flipLower = flipLower,
    connectors = cutConnectors(connectorValues, connectorKinds),
    snapSpace = snapSpace,
    snapBulge = snapBulge,
    connectorName = connectorName,
    dovetail = dovetail,
    groove = CutGroove.of(groove),
    radius = radius,
    parts = cutParts(partsPlane ?: DoubleArray(0), parts ?: BooleanArray(0)),
)

internal fun CutObjectOutcome.toParcel() = CutObjectParcel().also {
    when (this) {
        is CutObjectOutcome.Success -> {
            it.min = doubleArrayOf(min.x, min.y, min.z)
            it.max = doubleArrayOf(max.x, max.y, max.z)
        }
        is CutObjectOutcome.Failure -> {
            it.error = message
            it.min = DoubleArray(3)
            it.max = DoubleArray(3)
        }
    }
}

internal fun CutObjectParcel.toOutcome(): CutObjectOutcome = error?.let { CutObjectOutcome.Failure(it) }
    ?: CutObjectOutcome.Success(Vector3(min[0], min[1], min[2]), Vector3(max[0], max[1], max[2]))

internal fun CutPlaneOutcome.toParcel() = CutPlaneParcel().also {
    when (this) {
        is CutPlaneOutcome.Success -> {
            it.min = doubleArrayOf(plane.min.x, plane.min.y, plane.min.z)
            it.max = doubleArrayOf(plane.max.x, plane.max.y, plane.max.z)
            it.validContour = plane.validContour
            it.contour = plane.contour?.value
            it.section = plane.section?.value
            it.invalidConnectors = plane.invalidConnectors.toIntArray()
            it.outsideCutContour = plane.outsideCutContour
            it.outsideBoundingBox = plane.outsideBoundingBox
            it.overlap = plane.overlap
            it.connectorMeshes = plane.connectorMeshes.map(ScenePath::value).toTypedArray()
            it.groovePlane = plane.groovePlane?.value
            it.validGroove = plane.validGroove
            it.previewMeshes = plane.previewParts.map { part -> part.mesh.value }.toTypedArray()
            it.previewUpper = plane.previewParts.map(CutPreviewPart::upper).toBooleanArray()
            it.previewModifiers = plane.previewParts.map(CutPreviewPart::modifier).toBooleanArray()
        }
        is CutPlaneOutcome.Failure -> {
            it.error = message
            it.min = DoubleArray(3)
            it.max = DoubleArray(3)
            it.invalidConnectors = IntArray(0)
            it.connectorMeshes = emptyArray()
            it.previewMeshes = emptyArray()
            it.previewUpper = BooleanArray(0)
            it.previewModifiers = BooleanArray(0)
        }
    }
}

internal fun CutPartsOutcome.toParcel() = CutPartsParcel().also {
    when (this) {
        is CutPartsOutcome.Success -> {
            it.meshes = parts.map { part -> part.mesh.value }.toTypedArray()
            it.upper = parts.map(CutPreviewPart::upper).toBooleanArray()
            it.modifiers = parts.map(CutPreviewPart::modifier).toBooleanArray()
        }
        is CutPartsOutcome.Failure -> {
            it.error = message
            it.meshes = emptyArray()
            it.upper = BooleanArray(0)
            it.modifiers = BooleanArray(0)
        }
    }
}

internal fun CutPartsParcel.toOutcome(): CutPartsOutcome = error?.let { CutPartsOutcome.Failure(it) }
    ?: CutPartsOutcome.Success(meshes.indices.map { index -> CutPreviewPart(ScenePath(meshes[index]), upper[index], modifiers[index]) })

internal fun CutPlaneParcel.toOutcome(): CutPlaneOutcome = error?.let { CutPlaneOutcome.Failure(it) }
    ?: CutPlaneOutcome.Success(
        CutPlaneDescription(
            min = Vector3(min[0], min[1], min[2]),
            max = Vector3(max[0], max[1], max[2]),
            validContour = validContour,
            contour = contour?.let(::ScenePath),
            section = section?.let(::ScenePath),
            invalidConnectors = invalidConnectors.toList(),
            outsideCutContour = outsideCutContour,
            outsideBoundingBox = outsideBoundingBox,
            overlap = overlap,
            connectorMeshes = connectorMeshes.map(::ScenePath),
            groovePlane = groovePlane?.let(::ScenePath),
            validGroove = validGroove,
            previewParts = previewMeshes.indices.map { index -> CutPreviewPart(ScenePath(previewMeshes[index]), previewUpper[index], previewModifiers[index]) },
        ),
    )

internal fun PlateValidation.toParcel() = PlateValidationParcel().also { parcel ->
    parcel.errorText = error?.text
    parcel.errorObject = error?.objectIndex ?: -1
    parcel.errorInstance = error?.instanceIndex ?: -1
    parcel.errorOption = error?.option
    parcel.warningText = warning?.text
    parcel.warningObject = warning?.objectIndex ?: -1
    parcel.warningInstance = warning?.instanceIndex ?: -1
    parcel.warningOption = warning?.option
    parcel.clearanceCounts = clearance.map { it.size }.toIntArray()
    parcel.clearance = clearance.flatten().flatMap { listOf(it.x, it.y) }.toDoubleArray()
    parcel.clearanceFill = clearanceFill.flatMap { listOf(it.x, it.y) }.toDoubleArray()
    parcel.heightFill = heightLimitFill.flatMap { listOf(it.x, it.y, it.z) }.toDoubleArray()
    parcel.sequence = sequence.toIntArray()
}

internal fun PlateValidationParcel.toPlateValidation(): PlateValidation {
    fun message(text: String?, objectIndex: Int, instanceIndex: Int, option: String?) =
        text?.let { PlateValidationMessage(it, objectIndex, instanceIndex, option.orEmpty()) }
    fun outlines(counts: IntArray?, points: DoubleArray?): List<List<Point2>> {
        val values = points ?: DoubleArray(0)
        var at = 0
        return (counts ?: IntArray(0)).map { count ->
            List(count) { point -> Point2(values[(at + point) * 2], values[(at + point) * 2 + 1]) }.also { at += count }
        }
    }
    return PlateValidation(
        error = message(errorText, errorObject, errorInstance, errorOption),
        warning = message(warningText, warningObject, warningInstance, warningOption),
        clearance = outlines(clearanceCounts, clearance),
        clearanceFill = (clearanceFill ?: DoubleArray(0)).let { fill -> List(fill.size / 2) { Point2(fill[it * 2], fill[it * 2 + 1]) } },
        heightLimitFill = (heightFill ?: DoubleArray(0)).let { fill -> List(fill.size / 3) { Vector3(fill[it * 3], fill[it * 3 + 1], fill[it * 3 + 2]) } },
        sequence = (sequence ?: IntArray(0)).toList(),
    )
}

internal fun LayerEditingOutcome.toParcel() = LayerEditingParcel().also {
    when (this) {
        is LayerEditingOutcome.Failure -> it.error = message
        is LayerEditingOutcome.Success -> {
            it.profile = editing.profile.toDoubleArray()
            it.layers = editing.layers.toDoubleArray()
            it.objectMaxZ = editing.objectMaxZ
            it.layerHeight = editing.layerHeight
            it.minLayerHeight = editing.minLayerHeight
            it.maxLayerHeight = editing.maxLayerHeight
            it.objectPrintZHeight = editing.objectPrintZHeight
            it.fixed = editing.fixed
        }
    }
}

internal fun LayerEditingParcel.toLayerEditingOutcome(): LayerEditingOutcome = error?.let(LayerEditingOutcome::Failure)
    ?: LayerEditingOutcome.Success(
        LayerEditing(
            profile = profile?.toList().orEmpty(),
            layers = layers?.toList().orEmpty(),
            objectMaxZ = objectMaxZ,
            layerHeight = layerHeight,
            minLayerHeight = minLayerHeight,
            maxLayerHeight = maxLayerHeight,
            objectPrintZHeight = objectPrintZHeight,
            fixed = fixed,
        ),
    )

