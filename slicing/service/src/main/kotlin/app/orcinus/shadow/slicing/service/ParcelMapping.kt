package app.orcinus.shadow.slicing.service

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.FlatteningPlane
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
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
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3

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
    it.printerProfile = printerProfile.value
    it.filamentProfile = filamentProfile.value
    it.processProfile = processProfile.value
}

internal fun SliceRequestParcel.toSliceRequest() = SliceRequest(
    jobId = SliceJobId(jobId),
    objects = objects.toPlacedModels(),
    output = OutputPath(outputPath),
    toolpaths = toolpathsPath?.let(::ScenePath),
    printerProfile = ProfileId(printerProfile),
    filamentProfile = ProfileId(filamentProfile),
    processProfile = ProfileId(processProfile),
)

internal fun List<PlacedModel>.toParcels(): Array<PlacedModelParcel> = Array(size) { index ->
    val placed = this[index]
    PlacedModelParcel().also {
        it.model = placed.model.toParcel()
        it.meshPath = placed.mesh.value
        it.placement = placed.placement.columns.toDoubleArray()
        it.autoDrop = placed.autoDrop
    }
}

internal fun Array<PlacedModelParcel>.toPlacedModels(): List<PlacedModel> = map {
    PlacedModel(
        model = checkNotNull(it.model) { "A plate object has no model" }.toModelSource(),
        mesh = ScenePath(it.meshPath),
        placement = Transform3(it.placement.toList()),
        autoDrop = it.autoDrop,
    )
}

internal fun SliceOutcome.toParcel() = SliceOutcomeParcel().also {
    it.jobId = jobId.value
    when (this) {
        is SliceOutcome.Success -> {
            it.kind = SliceOutcomeParcel.SUCCESS
            it.gcodePath = gcodePath.value
            it.toolpathsPath = toolpaths?.value
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
            toolpaths = toolpathsPath?.let(::ScenePath),
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
}

internal fun ProfilesParcel.toProfiles() = SlicingProfileSelection(ProfileId(printer), ProfileId(filament), ProfileId(process))

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
            ModelInspectionOutcome.Success(inspections[index]).toParcel()
        }
    }
}

internal fun PlateInspectionParcel.toPlateInspectionOutcome(): PlateInspectionOutcome {
    error?.let { return PlateInspectionOutcome.Failure(it) }
    return PlateInspectionOutcome.Success(checkNotNull(inspections).map { it.toInspection() })
}

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
}

internal fun Manipulation.parcelFaceNormal(): DoubleArray? = (this as? Manipulation.LayOnFace)?.normal?.let { doubleArrayOf(it.x, it.y, it.z) }

internal fun manipulationOf(name: String, faceNormal: DoubleArray?): Manipulation = when (name) {
    "Move" -> Manipulation.Move
    "Rotate" -> Manipulation.Rotate
    "Scale" -> Manipulation.Scale
    "ResetRotation" -> Manipulation.ResetRotation
    "LayOnFace" -> Manipulation.LayOnFace(checkNotNull(faceNormal).toVector())
    "EnsureOnBed" -> Manipulation.EnsureOnBed
    else -> error("Unknown manipulation $name")
}

internal fun PlateManipulation.parcelName(): String = when (this) {
    is PlateManipulation.AutoOrient -> "AutoOrient"
    is PlateManipulation.Arrange -> "Arrange"
    PlateManipulation.UpdatePrintVolume -> "UpdatePrintVolume"
}

internal fun PlateManipulation.parcelSelected(): Array<String> =
    (this as? PlateManipulation.AutoOrient)?.selected.orEmpty().map(ScenePath::value).toTypedArray()

internal fun PlateManipulation.parcelArrangeSettings(): ArrangeSettingsParcel? = (this as? PlateManipulation.Arrange)?.settings?.let { settings ->
    ArrangeSettingsParcel().also {
        it.distance = settings.distance
        it.enableRotation = settings.enableRotation
        it.allowMultiMaterialsOnSamePlate = settings.allowMultiMaterialsOnSamePlate
        it.alignToYAxis = settings.alignToYAxis
    }
}

internal fun plateManipulationOf(name: String, selected: Array<String>, arrange: ArrangeSettingsParcel?): PlateManipulation = when (name) {
    "AutoOrient" -> PlateManipulation.AutoOrient(selected.mapTo(LinkedHashSet(), ::ScenePath))
    "Arrange" -> checkNotNull(arrange).let {
        PlateManipulation.Arrange(ArrangeSettings(it.distance, it.enableRotation, it.allowMultiMaterialsOnSamePlate, it.alignToYAxis))
    }
    "UpdatePrintVolume" -> PlateManipulation.UpdatePrintVolume
    else -> error("Unknown manipulation $name")
}

internal fun PresetsOutcome.toParcel() = PresetsParcel().also {
    when (this) {
        is PresetsOutcome.Failure -> it.error = message
        is PresetsOutcome.Success -> with(presets) {
            it.selection = selection.toParcel()
            it.setupRequired = setupRequired
            it.printers = printers.toParcels()
            it.filaments = filaments.toParcels()
            it.processes = processes.toParcels()
            it.nozzleDiameters = nozzleDiameters.toTypedArray()
            it.nozzleDiameter = nozzleDiameter
        }
    }
}

internal fun PresetsParcel.toPresetsOutcome(): PresetsOutcome {
    error?.let { return PresetsOutcome.Failure(it) }
    return PresetsOutcome.Success(
        Presets(
            selection = checkNotNull(selection).toProfiles(),
            setupRequired = setupRequired,
            printers = printers.toItems(),
            filaments = filaments.toItems(),
            processes = processes.toItems(),
            nozzleDiameters = nozzleDiameters.orEmpty().toList(),
            nozzleDiameter = nozzleDiameter.orEmpty(),
        ),
    )
}

private fun List<PresetListItem>.toParcels(): Array<PresetItemParcel> = Array(size) { index ->
    val item = this[index]
    PresetItemParcel().also {
        it.name = item.name
        it.label = item.label
        it.group = item.group.name
        it.subgroup = item.subgroup
        it.selected = item.selected
    }
}

private fun Array<PresetItemParcel>?.toItems(): List<PresetListItem> =
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
