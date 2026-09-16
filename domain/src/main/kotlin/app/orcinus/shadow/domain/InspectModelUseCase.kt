package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.slicing.api.ModelInspector

class InspectModelUseCase(
    private val inspector: ModelInspector,
) {
    suspend operator fun invoke(path: ModelPath): ModelInspectionOutcome {
        if (path.value.isBlank()) {
            return ModelInspectionOutcome.Failure("Model path is empty")
        }
        return inspector.inspect(ModelSource.LocalFile(path))
    }
}
