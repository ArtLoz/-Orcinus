package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.slicing.api.PlateInspector

/** Commits a manipulation of a placed model from one instance transformation to another, as OrcaSlicer does. */
class PlaceModelUseCase(
    private val inspector: PlateInspector,
) {
    suspend operator fun invoke(
        plateObject: PlacedModel,
        profiles: SlicingProfileSelection,
        previous: Transform3,
        placement: Transform3,
        autoDrop: Boolean,
        manipulation: Manipulation,
        /** The copy of [plateObject] the manipulation is of; its others may follow it. */
        instance: Int = 0,
    ): ModelInspectionOutcome {
        if ((previous.columns + placement.columns).any { !it.isFinite() }) {
            return ModelInspectionOutcome.Failure("The placement is not a finite transformation")
        }
        return inspector.place(plateObject, profiles, previous, placement, autoDrop, manipulation, instance)
    }
}
