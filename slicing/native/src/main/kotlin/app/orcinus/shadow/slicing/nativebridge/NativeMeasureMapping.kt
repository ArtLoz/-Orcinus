package app.orcinus.shadow.slicing.nativebridge

import app.orcinus.shadow.core.model.AssemblyMode
import app.orcinus.shadow.core.model.MeasureAngle
import app.orcinus.shadow.core.model.MeasureAssembly
import app.orcinus.shadow.core.model.MeasureDistance
import app.orcinus.shadow.core.model.MeasureFeature
import app.orcinus.shadow.core.model.MeasureFeatureType
import app.orcinus.shadow.core.model.MeasureHover
import app.orcinus.shadow.core.model.MeasureHoverOutcome
import app.orcinus.shadow.core.model.MeasureOutcome
import app.orcinus.shadow.core.model.MeasurePlaneMesh
import app.orcinus.shadow.core.model.MeasureRay
import app.orcinus.shadow.core.model.MeasureResult
import app.orcinus.shadow.core.model.MeasureSelection
import app.orcinus.shadow.core.model.Measurement
import app.orcinus.shadow.core.model.Vector3

internal fun NativeMeasureState.toMeasureOutcome(): MeasureOutcome = if (status != NativeSceneStatus.SUCCESS) {
    MeasureOutcome.Failure(message.ifBlank { "OrcaSlicer could not measure" })
} else {
    MeasureOutcome.Success(
        Measurement(
            first = first.toSelection(),
            second = second.toSelection(),
            result = MeasureResult(
                angle = angle.takeIf { it.size >= 18 }?.let { values ->
                    MeasureAngle(
                        angle = values[0],
                        center = values.vector(3),
                        edge1 = values.vector(6) to values.vector(9),
                        edge2 = values.vector(12) to values.vector(15),
                        radius = values[1],
                        coplanar = values[2] != 0.0,
                    )
                },
                distanceInfinite = distanceInfinite.toDistance(),
                distanceStrict = distanceStrict.toDistance(),
                distanceXyz = distanceXyz.takeIf { it.size >= 3 }?.vector(0),
            ),
            assembly = MeasureAssembly(
                canSetToParallel = canSetToParallel,
                canSetToCenterCoincidence = canSetToCenterCoincidence,
                canSetFeature1ReverseRotation = canSetFeature1ReverseRotation,
                canSetFeature2ReverseRotation = canSetFeature2ReverseRotation,
                canAroundCenterOfFaces = canAroundCenterOfFaces,
                hasParallelDistance = hasParallelDistance,
                parallelDistance = parallelDistance,
            ),
            canSetXyzDistance = canSetXyzDistance,
            hitVolumes = hitVolumes,
            sameObject = sameObject,
            showResetFirstTip = showResetFirstTip,
            hovered = hovered.toFeature(),
            wrongFeatureTip = wrongFeatureTip,
        ),
    )
}

/** The engine's AssemblyMode of a ray: 0 for the measuring tool. */
internal fun MeasureRay.assemblyModeValue(): Int = when (assemblyMode) {
    null -> 0
    AssemblyMode.FACE_FACE -> 1
    AssemblyMode.POINT_POINT -> 2
}

internal fun NativeMeasureState.toMeasureHoverOutcome(): MeasureHoverOutcome = if (status != NativeSceneStatus.SUCCESS) {
    MeasureHoverOutcome.Failure(message.ifBlank { "OrcaSlicer could not measure" })
} else {
    MeasureHoverOutcome.Success(
        MeasureHover(
            feature = hovered.toFeature(),
            unchanged = hoveredUnchanged,
            point = hoveredPoint.takeIf { it.size >= 3 }?.vector(0),
            sphere = hoveredSphere,
        ),
    )
}

private fun NativeMeasureItem.toSelection(): MeasureSelection? {
    if (!selected) return null
    val selectedFeature = feature.toFeature() ?: return null
    return MeasureSelection(
        isCenter = isCenter,
        source = source.toFeature() ?: selectedFeature,
        feature = selectedFeature,
        objectIndex = objectIndex,
        volumeIndex = volumeIndex,
    )
}

private fun NativeMeasureFeature.toFeature(): MeasureFeature? {
    val featureType = when (type) {
        1 -> MeasureFeatureType.POINT
        2 -> MeasureFeatureType.EDGE
        4 -> MeasureFeatureType.CIRCLE
        8 -> MeasureFeatureType.PLANE
        else -> return null
    }
    return MeasureFeature(
        type = featureType,
        pt1 = pt1.vector(0),
        pt2 = pt2.vector(0),
        pt3 = pt3.takeIf { it.size >= 3 }?.vector(0),
        value = value,
        planeMesh = planeTriangles.takeIf { it.isNotEmpty() }?.let(::MeasurePlaneMesh),
    )
}

private fun DoubleArray.toDistance(): MeasureDistance? = takeIf { it.size >= 7 }?.let { MeasureDistance(it[0], it.vector(1), it.vector(4)) }

private fun DoubleArray.vector(at: Int) = Vector3(getOrElse(at) { 0.0 }, getOrElse(at + 1) { 0.0 }, getOrElse(at + 2) { 0.0 })

internal fun Vector3.values() = doubleArrayOf(x, y, z)
