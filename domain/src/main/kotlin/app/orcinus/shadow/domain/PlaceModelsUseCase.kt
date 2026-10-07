package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateJobProgress
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.PlateInspector

/**
 * Commits a manipulation of every object on the plate at once, as OrcaSlicer's
 * orient and arrange jobs do: the job numbered [job] tells [progress] what it
 * got to, and [cancel] stops it.
 */
class PlaceModelsUseCase(
    private val inspector: PlateInspector,
) {
    suspend operator fun invoke(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        manipulation: PlateManipulation,
        job: Long = 0L,
        progress: ((PlateJobProgress) -> Unit)? = null,
    ): PlateInspectionOutcome {
        if (plate.isEmpty()) {
            return PlateInspectionOutcome.Failure("The plate has no objects")
        }
        if (plate.any { placed -> placed.instances.any { copy -> copy.placement.columns.any { !it.isFinite() } } }) {
            return PlateInspectionOutcome.Failure("A placement is not a finite transformation")
        }
        return if (progress == null) {
            inspector.placeObjects(plate, profiles, manipulation)
        } else {
            inspector.placeObjects(plate, profiles, manipulation, job, progress)
        }
    }

    /** The Cancel of the job numbered [job]. */
    fun cancel(job: Long) = inspector.cancelPlacement(job)
}
