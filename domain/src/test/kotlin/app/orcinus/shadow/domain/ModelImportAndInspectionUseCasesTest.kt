package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.slicing.api.PlateInspector
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

        val actual = runSuspend { InspectModelUseCase(inspector)(ModelSource.LocalFile(ModelPath("")), PROFILES, MESH) }

        assertIs<ModelInspectionOutcome.Failure>(actual)
        assertEquals(null, inspector.model)
    }

    @Test
    fun `a model, the profiles and the mesh file are delegated to the inspection port`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val model = ModelSource.LocalFile(ModelPath("/internal/imports/model.stl"))

        val actual = runSuspend { InspectModelUseCase(inspector)(model, PROFILES, MESH) }

        assertEquals(VALID_INSPECTION, actual)
        assertEquals(model, inspector.model)
        assertEquals(PROFILES, inspector.profiles)
        assertEquals(MESH, inspector.mesh)
    }

    @Test
    fun `a placement is delegated to the inspection port`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 })

        val actual = runSuspend {
            PlaceModelUseCase(inspector)(ModelSource.LocalFile(ModelPath("/imports/model.stl")), PROFILES, MESH, placement, placement, true, Manipulation.Rotate)
        }

        assertEquals(VALID_INSPECTION, actual)
        assertEquals(placement, inspector.placement)
        assertEquals(MESH, inspector.mesh)
    }

    @Test
    fun `a placement that is not finite is rejected before the engine`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val placement = Transform3(List(16) { if (it == 12) Double.NaN else 0.0 })

        val actual = runSuspend {
            PlaceModelUseCase(inspector)(ModelSource.LocalFile(ModelPath("/imports/model.stl")), PROFILES, MESH, placement, placement, true, Manipulation.Move)
        }

        assertIs<ModelInspectionOutcome.Failure>(actual)
        assertEquals(null, inspector.placement)
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
    ) : PlateInspector {
        var model: ModelSource? = null
        var profiles: SlicingProfileSelection? = null
        var mesh: ScenePath? = null
        var placement: Transform3? = null

        override suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath) =
            PlateDescriptionOutcome.Failure("not used")

        override suspend fun inspect(model: ModelSource, profiles: SlicingProfileSelection, mesh: ScenePath): ModelInspectionOutcome {
            this.model = model
            this.profiles = profiles
            this.mesh = mesh
            return outcome
        }

        override suspend fun place(
            model: ModelSource,
            profiles: SlicingProfileSelection,
            mesh: ScenePath,
            previous: Transform3,
            placement: Transform3,
            autoDrop: Boolean,
            manipulation: Manipulation,
        ): ModelInspectionOutcome {
            this.model = model
            this.mesh = mesh
            this.placement = placement
            return outcome
        }

        override suspend fun flatteningPlanes(
            model: ModelSource,
            profiles: SlicingProfileSelection,
            mesh: ScenePath,
            placement: Transform3,
        ) = FlatteningPlanesOutcome.Failure("not used")
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
        val PROFILES = SlicingProfileSelection(ProfileId("printer"), ProfileId("filament"), ProfileId("process"))
        val MESH = ScenePath("/scene/objects/model.mesh")
        val VALID_INSPECTION = ModelInspectionOutcome.Success(
            ModelInspection(
                facetCount = 12,
                dimensions = ModelDimensions(20.0, 20.0, 20.0),
                boxCenter = Vector3(0.0, 0.0, 10.0),
                mesh = MESH,
                placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
                fit = BuildVolumeFit.INSIDE,
    boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
                rotationDegrees = Vector3(0.0, 0.0, 0.0),
                unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
            ),
        )
    }
}
