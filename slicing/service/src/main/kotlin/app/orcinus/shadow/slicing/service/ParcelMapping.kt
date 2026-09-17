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
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
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
    when (val source = model) {
        is ModelSource.LocalFile -> it.modelPath = source.path.value
        is ModelSource.BuiltIn -> it.builtInModel = source.model.name
    }
    it.outputPath = output.value
    it.printerProfile = printerProfile.value
    it.filamentProfile = filamentProfile.value
    it.processProfile = processProfile.value
    it.placement = placement?.columns?.toDoubleArray()
    it.autoDrop = autoDrop
}

internal fun SliceRequestParcel.toSliceRequest() = SliceRequest(
    jobId = SliceJobId(jobId),
    model = builtInModel?.let { ModelSource.BuiltIn(BuiltInModel.valueOf(it)) }
        ?: ModelSource.LocalFile(ModelPath(checkNotNull(modelPath) { "Slice request has no model" })),
    output = OutputPath(outputPath),
    printerProfile = ProfileId(printerProfile),
    filamentProfile = ProfileId(filamentProfile),
    processProfile = ProfileId(processProfile),
    placement = placement?.let { Transform3(it.toList()) },
    autoDrop = autoDrop,
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
    return ModelInspectionOutcome.Success(
        ModelInspection(
            facetCount = facetCount,
            dimensions = ModelDimensions(widthMillimeters, depthMillimeters, heightMillimeters),
            boxCenter = checkNotNull(boxCenter).toVector(),
            mesh = ScenePath(checkNotNull(meshPath)),
            placement = Transform3(checkNotNull(placement).toList()),
            fit = BuildVolumeFit.valueOf(checkNotNull(fit)),
            boundingSphere = BoundingSphere(checkNotNull(sphereCenter).toVector(), sphereRadius),
            rotationDegrees = checkNotNull(rotationDegrees).toVector(),
            unscaledDimensions = checkNotNull(unscaledSize).let { ModelDimensions(it[0], it[1], it[2]) },
        ),
    )
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
    Manipulation.AutoOrient -> "AutoOrient"
    is Manipulation.LayOnFace -> "LayOnFace"
    is Manipulation.Arrange -> "Arrange"
    Manipulation.EnsureOnBed -> "EnsureOnBed"
}

internal fun Manipulation.parcelArrangeSettings(): ArrangeSettingsParcel? = (this as? Manipulation.Arrange)?.settings?.let { settings ->
    ArrangeSettingsParcel().also {
        it.distance = settings.distance
        it.enableRotation = settings.enableRotation
        it.allowMultiMaterialsOnSamePlate = settings.allowMultiMaterialsOnSamePlate
        it.alignToYAxis = settings.alignToYAxis
    }
}

internal fun Manipulation.parcelFaceNormal(): DoubleArray? = (this as? Manipulation.LayOnFace)?.normal?.let { doubleArrayOf(it.x, it.y, it.z) }

internal fun manipulationOf(name: String, faceNormal: DoubleArray?, arrange: ArrangeSettingsParcel?): Manipulation = when (name) {
    "Move" -> Manipulation.Move
    "Rotate" -> Manipulation.Rotate
    "Scale" -> Manipulation.Scale
    "ResetRotation" -> Manipulation.ResetRotation
    "AutoOrient" -> Manipulation.AutoOrient
    "LayOnFace" -> Manipulation.LayOnFace(checkNotNull(faceNormal).toVector())
    "EnsureOnBed" -> Manipulation.EnsureOnBed
    "Arrange" -> checkNotNull(arrange).let {
        Manipulation.Arrange(ArrangeSettings(it.distance, it.enableRotation, it.allowMultiMaterialsOnSamePlate, it.alignToYAxis))
    }
    else -> error("Unknown manipulation $name")
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
