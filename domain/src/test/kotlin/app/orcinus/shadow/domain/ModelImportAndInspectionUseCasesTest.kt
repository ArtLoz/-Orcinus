package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelGeometryPreview
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.slicing.api.ModelInspector
import app.orcinus.shadow.storage.api.ModelFileImporter
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModelImportAndInspectionUseCasesTest {
    @Test
    fun `import delegates opaque document reference to storage port`() {
        val reference = ExternalDocumentReference("content://documents/model-42")
        val expected = ModelImportOutcome.Success(
            ImportedModelFile(ModelPath("/internal/imports/model.stl"), "model.stl"),
        )
        val importer = RecordingImporter(expected)

        val actual = runSuspend { ImportModelUseCase(importer)(reference) }

        assertEquals(expected, actual)
        assertEquals(reference, importer.reference)
    }

    @Test
    fun `blank model path is rejected before inspection`() {
        val inspector = RecordingInspector(VALID_INSPECTION)

        val actual = runSuspend { InspectModelUseCase(inspector)(ModelPath("")) }

        assertIs<ModelInspectionOutcome.Failure>(actual)
        assertEquals(null, inspector.model)
    }

    @Test
    fun `local model path is delegated to inspection port`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val path = ModelPath("/internal/imports/model.stl")

        val actual = runSuspend { InspectModelUseCase(inspector)(path) }

        assertEquals(VALID_INSPECTION, actual)
        assertEquals(ModelSource.LocalFile(path), inspector.model)
    }

    private class RecordingImporter(
        private val outcome: ModelImportOutcome,
    ) : ModelFileImporter {
        var reference: ExternalDocumentReference? = null

        override suspend fun importModel(
            reference: ExternalDocumentReference,
        ): ModelImportOutcome {
            this.reference = reference
            return outcome
        }
    }

    private class RecordingInspector(
        private val outcome: ModelInspectionOutcome,
    ) : ModelInspector {
        var model: ModelSource.LocalFile? = null

        override suspend fun inspect(model: ModelSource.LocalFile): ModelInspectionOutcome {
            this.model = model
            return outcome
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        })
        return checkNotNull(completion).getOrThrow()
    }

    private companion object {
        val VALID_INSPECTION = ModelInspectionOutcome.Success(
            ModelInspection(
                facetCount = 12,
                dimensions = ModelDimensions(20.0, 20.0, 20.0),
                geometryPreview = ModelGeometryPreview(
                    samplingStepMillimeters = 0.2,
                    sampledPlaneCount = 100,
                    nonEmptyPlaneCount = 100,
                    contourCount = 100,
                ),
            ),
        )
    }
}
