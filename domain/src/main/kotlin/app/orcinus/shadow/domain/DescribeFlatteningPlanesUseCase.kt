package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.PlateInspector

/** The faces a copy of an object on the plate can lie on, for OrcaSlicer's "Lay on face" tool. */
class DescribeFlatteningPlanesUseCase(
    private val inspector: PlateInspector,
) {
    suspend operator fun invoke(
        plateObject: PlateObject,
        instance: PlateInstance,
        profiles: SlicingProfileSelection,
    ): FlatteningPlanesOutcome =
        inspector.flatteningPlanes(plateObject.source(), profiles, plateObject.mesh, instance.inspection.placement)
}
