package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelImportOutcome

interface ModelFileImporter {
    suspend fun importModel(reference: ExternalDocumentReference): ModelImportOutcome

    /** The documents the user picked at once, which all stay in app storage together. */
    suspend fun importModels(references: List<ExternalDocumentReference>): List<ModelImportOutcome> = references.map { importModel(it) }
}
