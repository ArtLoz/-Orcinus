package app.orcinus.shadow.slicing.service

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushVolumes
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.LoadedObject
import app.orcinus.shadow.core.model.LoadedProject
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.ObjectVolume
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjectPart
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
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChange
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetKind
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
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.LayerGcodeRules
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.WipeTowerOutcome

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
}

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
}

private fun ObjectVolumeParcel?.toObjectVolume(): ObjectVolume = this?.let {
    ObjectVolume(
        name = it.name.orEmpty(),
        settings = it.settings.toModelSettings(),
        splittable = it.splittable,
        convertedFromInches = it.convertedFromInches,
        convertedFromMeters = it.convertedFromMeters,
        inputFile = it.inputFile.orEmpty(),
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
)

internal fun ModelLoadOutcome.toParcel() = ModelLoadParcel().also {
    it.notices = notices.map { dialog -> dialog.toParcel() }.toTypedArray()
    it.appended = this is ModelLoadOutcome.Success && appended
    it.selectedVolume = (this as? ModelLoadOutcome.Success)?.selectedVolume ?: -1
    it.presetsChanged = this is ModelLoadOutcome.Success && presetsChanged
    (this as? ModelLoadOutcome.Success)?.project?.let { project ->
        it.plateSettings = project.plateSettings.toParcel()
        it.layerGcodeHeights = project.layerGcodes.map(LayerGcode::printZ).toDoubleArray()
        it.layerGcodeTypes = project.layerGcodes.map { code -> code.type.name }.toTypedArray()
        it.layerGcodeExtruders = project.layerGcodes.map(LayerGcode::extruder).toIntArray()
        it.layerGcodeColors = project.layerGcodes.map(LayerGcode::color).toTypedArray()
        it.layerGcodeExtras = project.layerGcodes.map(LayerGcode::extra).toTypedArray()
    }
    when (this) {
        is ModelLoadOutcome.Failure -> it.error = message
        is ModelLoadOutcome.Question -> it.question = question.toParcel()
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
            }
        }.toTypedArray()
    }
}

internal fun ModelLoadParcel.toModelLoadOutcome(): ModelLoadOutcome {
    val shown = notices.orEmpty().map { it.toDialog() }
    error?.let { return ModelLoadOutcome.Failure(it, shown) }
    question?.let { return ModelLoadOutcome.Question(it.toDialog(), shown) }
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
            )
        },
        shown,
        appended,
        selectedVolume.takeIf { it >= 0 },
        project = plateSettings?.let { settings ->
            LoadedProject(
                plateSettings = settings.toModelSettings(),
                layerGcodes = layerGcodesOf(layerGcodeHeights, layerGcodeTypes, layerGcodeExtruders, layerGcodeColors, layerGcodeExtras),
            )
        },
        presetsChanged = presetsChanged,
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
            it.layerCount = statistics.layerCount
            it.estimatedPrintTimeSeconds = statistics.estimatedPrintTimeSeconds
            it.filamentMillimeters = statistics.filamentMillimeters
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
            statistics = SliceStatistics(layerCount, estimatedPrintTimeSeconds, filamentMillimeters),
            toolpaths = toolpathsPath?.let(::ScenePath),
            wipeTower = wipeTowerPath?.let(::ScenePath),
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
        is PlateInspectionOutcome.Success -> it.inspections = Array(inspections.size) { index ->
            PlateObjectInspectionParcel().also { object_ ->
                object_.instances = Array(inspections[index].size) { copy ->
                    ModelInspectionOutcome.Success(inspections[index][copy]).toParcel()
                }
            }
        }
    }
}

internal fun PlateInspectionParcel.toPlateInspectionOutcome(): PlateInspectionOutcome {
    error?.let { return PlateInspectionOutcome.Failure(it) }
    return PlateInspectionOutcome.Success(checkNotNull(inspections).map { object_ -> object_.instances.orEmpty().map { it.toInspection() } })
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
            it.filaments = filaments.toIntArray()
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
            filaments = filaments?.toList().orEmpty(),
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
): PlateManipulation {
    fun settings() = checkNotNull(arrange).let {
        ArrangeSettings(it.distance, it.enableRotation, it.allowMultiMaterialsOnSamePlate, it.alignToYAxis)
    }
    return when (name) {
        "AutoOrient" -> PlateManipulation.AutoOrient(selected.mapTo(LinkedHashSet(), ::ScenePath))
        "Arrange" -> PlateManipulation.Arrange(settings())
        "UpdatePrintVolume" -> PlateManipulation.UpdatePrintVolume
        "ArrangePlate" -> PlateManipulation.ArrangePlate(settings())
        "FillBed" -> PlateManipulation.FillBed(ScenePath(selected.single()), instance.takeIf { it >= 0 }, settings())
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
            parcel.unsavedChanges = Array(changes.size) { index ->
                val change = changes[index]
                PresetChangeParcel().also {
                    it.id = change.id
                    it.category = change.category.toParcels()
                    it.group = change.group.toParcels()
                    it.label = change.label.toParcels()
                    it.oldValue = change.oldValue.toParcels()
                    it.newValue = change.newValue.toParcels()
                }
            }
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
    )
    if (!asksUnsavedChanges) {
        return PresetsOutcome.Success(presets)
    }
    return PresetsOutcome.UnsavedChanges(
        presets = presets,
        kind = PresetKind.valueOf(changedKind.orEmpty()),
        changes = unsavedChanges.orEmpty().map { change ->
            PresetChange(
                id = change.id,
                category = change.category.toTexts(),
                group = change.group.toTexts(),
                label = change.label.toTexts(),
                oldValue = change.oldValue.toTexts(),
                newValue = change.newValue.toTexts(),
            )
        },
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
        it.selected = item.selected
    }
}

internal fun Array<PresetItemParcel>?.toItems(): List<PresetListItem> =
    orEmpty().map { PresetListItem(it.name, it.label, PresetGroup.valueOf(it.group), it.subgroup, it.selected) }

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
