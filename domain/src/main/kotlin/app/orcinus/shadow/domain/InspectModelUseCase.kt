package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.PlateInspector

/**
 * Loads a model and places it on the plate of [profiles]'s printer beside the
 * objects already there, writing its mesh to a file.
 */
class InspectModelUseCase(
    private val inspector: PlateInspector,
) {
    suspend operator fun invoke(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        plate: List<PlacedModel>,
    ): ModelInspectionOutcome {
        if (model is ModelSource.LocalFile && model.path.value.isBlank()) {
            return ModelInspectionOutcome.Failure("Model path is empty")
        }
        return inspector.inspect(model, profiles, mesh, plate)
    }
}
