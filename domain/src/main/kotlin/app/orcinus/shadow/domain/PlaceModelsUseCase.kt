package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.PlateInspector

/** Commits a manipulation of every object on the plate at once, as OrcaSlicer's orient and arrange jobs do. */
class PlaceModelsUseCase(
    private val inspector: PlateInspector,
) {
    suspend operator fun invoke(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        manipulation: PlateManipulation,
    ): PlateInspectionOutcome {
        if (plate.isEmpty()) {
            return PlateInspectionOutcome.Failure("The plate has no objects")
        }
        if (plate.any { placed -> placed.instances.any { copy -> copy.placement.columns.any { !it.isFinite() } } }) {
            return PlateInspectionOutcome.Failure("A placement is not a finite transformation")
        }
        return inspector.placeObjects(plate, profiles, manipulation)
    }
}
