package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateObject

/** What the engine loads for the object. */
internal fun PlateObject.source(): ModelSource = when (this) {
    is PlateObject.ImportedModel -> ModelSource.LocalFile(file.path)
    is PlateObject.CalibrationCube -> ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM)
}

/** The object as the engine loads it for the plate: its model, mesh, and instance. */
internal fun PlateObject.placed() = PlacedModel(source(), inspection.mesh, inspection.placement, autoDrop)
