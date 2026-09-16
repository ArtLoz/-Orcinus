package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelImportOutcome

interface ModelFileImporter {
    suspend fun importModel(reference: ExternalDocumentReference): ModelImportOutcome
}
