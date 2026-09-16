package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelImportFailureCode
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.slicing.api.ModelInspector
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.ModelFileImporter
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PlateUseCasesTest {
    // Unconfined runs launched work immediately, so each test reads the final state.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `slicing the cube sends a built-in model with the plate profiles and stores the result`() {
        val repository = FakeRepository(readyState(PlateObject.CalibrationCube))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        val request = checkNotNull(engine.request)
        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), request.model)
        assertEquals(OutputPath("/gcode/calibration-cube-20mm.gcode"), request.output)
        assertEquals(PROFILES.process, request.processProfile)
        val state = repository.state.value
        assertNull(state.slicing)
        assertEquals(PlateObject.CalibrationCube, state.result?.plateObject)
        assertEquals(STATISTICS, state.result?.statistics)
    }

    @Test
    fun `an imported model is sliced from its stored file into a G-code named after the document`() {
        val model = PlateObject.ImportedModel(ImportedModelFile(ModelPath("/imports/benchy.stl"), "benchy.stl"), INSPECTION)
        val repository = FakeRepository(readyState(model))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        assertEquals(ModelSource.LocalFile(ModelPath("/imports/benchy.stl")), engine.request?.model)
        assertEquals(OutputPath("/gcode/benchy.gcode"), engine.request?.output)
    }

    @Test
    fun `progress reaches the plate while the job runs`() {
        val repository = FakeRepository(readyState(PlateObject.CalibrationCube))
        val seen = mutableListOf<SliceProgress?>()
        val engine = FakeEngine(onSlice = { request, listener ->
            listener.onProgress(SliceProgress(request.jobId, 0.5f, SliceStage.SLICING, "Generating walls"))
            seen += repository.state.value.slicing?.progress
        })

        slicePlate(engine, repository)()

        assertEquals("Generating walls", seen.single()?.detail)
    }

    @Test
    fun `nothing is sliced before the engine is ready`() {
        val repository = FakeRepository(PlateState(PROFILES, plateObject = PlateObject.CalibrationCube))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        assertNull(engine.request)
        assertNull(repository.state.value.slicing)
    }

    @Test
    fun `an engine crash is reported as its own problem`() {
        val repository = FakeRepository(readyState(PlateObject.CalibrationCube))
        val engine = FakeEngine(outcome = { SliceOutcome.Failure(it, SliceFailureCode.ENGINE_CRASHED, "died", recoverable = true) })

        slicePlate(engine, repository)()

        assertEquals(PlateProblemKind.ENGINE_CRASHED, repository.state.value.problem?.kind)
        assertNull(repository.state.value.result)
    }

    @Test
    fun `cancelling marks the running job`() {
        val job = SliceJobId("job-1")
        val repository = FakeRepository(readyState(PlateObject.CalibrationCube).copy(slicing = PlateSlicing(job)))
        val engine = FakeEngine()

        CancelPlateSlicingUseCase(CancelSliceUseCase(engine), repository, scope)()

        assertEquals(job, engine.cancelled)
        assertTrue(repository.state.value.slicing?.cancelling == true)
    }

    @Test
    fun `an imported and inspected document goes on the plate`() {
        val repository = FakeRepository(readyState(null))
        val file = ImportedModelFile(ModelPath("/imports/a.stl"), "a.stl")

        addModel(repository, ModelImportOutcome.Success(file), ModelInspectionOutcome.Success(INSPECTION))(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(PlateObject.ImportedModel(file, INSPECTION), state.plateObject)
    }

    @Test
    fun `a failed import keeps the plate and reports the problem`() {
        val repository = FakeRepository(readyState(PlateObject.CalibrationCube))

        addModel(
            repository,
            ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, "empty"),
            ModelInspectionOutcome.Success(INSPECTION),
        )(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(PlateObject.CalibrationCube, state.plateObject)
        assertEquals(PlateProblemKind.IMPORT_FAILED, state.problem?.kind)
        assertEquals("empty", state.problem?.detail)
    }

    @Test
    fun `an engine that cannot start is reported as unavailable`() {
        val repository = FakeRepository(PlateState(PROFILES))
        val engine = FakeEngine(status = EngineStatus(EngineVersion("orca"), ready = false, message = "no profiles"))

        runSuspend { StartEngineUseCase(GetEngineStatusUseCase(engine), repository)() }

        val state = repository.state.value
        assertEquals(EngineAvailability.UNAVAILABLE, state.engine.availability)
        assertEquals(PlateProblemKind.ENGINE_UNAVAILABLE, state.problem?.kind)
        assertEquals("no profiles", state.problem?.detail)
    }

    private fun slicePlate(engine: FakeEngine, repository: PlateRepository) = SlicePlateUseCase(
        sliceModel = SliceModelUseCase(engine),
        outputs = GcodeOutputs { OutputPath("/gcode/$it.gcode") },
        repository = repository,
        applicationScope = scope,
    )

    private fun addModel(repository: PlateRepository, imported: ModelImportOutcome, inspected: ModelInspectionOutcome) =
        AddModelToPlateUseCase(
            importModel = ImportModelUseCase(object : ModelFileImporter {
                override suspend fun importModel(reference: ExternalDocumentReference) = imported
            }),
            inspectModel = InspectModelUseCase(object : ModelInspector {
                override suspend fun inspect(model: ModelSource.LocalFile) = inspected
            }),
            repository = repository,
            applicationScope = scope,
        )

    private fun readyState(plateObject: PlateObject?) = PlateState(
        profiles = PROFILES,
        engine = EngineState(EngineAvailability.READY, EngineVersion("orca")),
        plateObject = plateObject,
    )

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

    private class FakeRepository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    private class FakeEngine(
        private val status: EngineStatus = EngineStatus(EngineVersion("orca"), ready = true),
        private val onSlice: (SliceRequest, SliceProgressListener) -> Unit = { _, _ -> },
        private val outcome: (SliceJobId) -> SliceOutcome = { SliceOutcome.Success(it, OutputPath("/gcode/out.gcode"), STATISTICS) },
    ) : SlicerEngine {
        var request: SliceRequest? = null
        var cancelled: SliceJobId? = null

        override suspend fun status() = status

        override suspend fun slice(request: SliceRequest, progressListener: SliceProgressListener): SliceOutcome {
            this.request = request
            onSlice(request, progressListener)
            return outcome(request.jobId)
        }

        override suspend fun cancel(jobId: SliceJobId): Boolean {
            cancelled = jobId
            return true
        }
    }

    private companion object {
        val PROFILES = SlicingProfileSelection(ProfileId("printer"), ProfileId("filament"), ProfileId("process"))
        val STATISTICS = SliceStatistics(100, 731, 1209.0)
        val INSPECTION = ModelInspection(12, ModelDimensions(20.0, 20.0, 20.0))
        val REFERENCE = ExternalDocumentReference("content://model")
    }
}
