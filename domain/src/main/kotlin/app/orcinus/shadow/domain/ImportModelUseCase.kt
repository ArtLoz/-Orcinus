package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.storage.api.ModelFileImporter

class ImportModelUseCase(
    private val importer: ModelFileImporter,
) {
    suspend operator fun invoke(
        reference: ExternalDocumentReference,
    ): ModelImportOutcome = importer.importModel(reference)
}
