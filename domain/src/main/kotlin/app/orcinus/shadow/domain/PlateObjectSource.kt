package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlacedInstance
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.mesh

/** What the engine loads for the object. */
internal fun PlateObject.source(): ModelSource = when (this) {
    is PlateObject.ImportedModel -> ModelSource.LocalFile(file.path)
    is PlateObject.CalibrationCube -> ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM)
}

/**
 * The object as the engine loads it for the plate: its model, mesh, copies,
 * settings, the parts added to it and its height ranges, which all reach the
 * print (ModelObject::volumes and layer_config_ranges).
 */
internal fun PlateObject.placed() = PlacedModel(
    model = source(),
    mesh = mesh,
    instances = instances.map { PlacedInstance(it.inspection.placement, it.autoDrop, it.printable) },
    settings = settings,
    parts = parts,
    layerRanges = layerRanges,
    painted = painted,
    frame = (this as? PlateObject.ImportedModel)?.frame,
    volume = volume,
    name = when (this) {
        is PlateObject.ImportedModel -> file.displayName
        is PlateObject.CalibrationCube -> name.orEmpty()
    },
    cutId = cutId,
)
