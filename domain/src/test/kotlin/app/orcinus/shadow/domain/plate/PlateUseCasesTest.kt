package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelImportFailureCode
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.PlaceModelUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.ModelFileImporter
import app.orcinus.shadow.storage.api.SceneFiles
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
import kotlinx.coroutines.suspendCancellableCoroutine

class PlateUseCasesTest {
    // Unconfined runs launched work immediately, so each test reads the final state.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `slicing the cube sends a built-in model with the plate profiles and stores the result`() {
        val repository = FakeRepository(readyState(CUBE))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        val request = checkNotNull(engine.request)
        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), request.model)
        assertEquals(OutputPath("/gcode/calibration-cube-20mm.gcode"), request.output)
        assertEquals(PROFILES.process, request.processProfile)
        val state = repository.state.value
        assertNull(state.slicing)
        assertEquals(CUBE, state.result?.plateObject)
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
    fun `a moved object is placed by OrcaSlicer, sliced where it stands, and its old G-code is dropped`() {
        val moved = translated(100.0, 120.0, 30.0)
        val dropped = translated(100.0, 120.0, 10.0)
        val inspector = FakeInspector(placed = { INSPECTION.copy(placement = dropped, dimensions = ModelDimensions(20.0, 20.0, 20.0)) })
        val repository = FakeRepository(readyState(CUBE).copy(result = PlateSliceResult(SliceJobId("old"), CUBE, OutputPath("/gcode/old.gcode"), STATISTICS)))
        val engine = FakeEngine()

        placePlateObject(inspector, repository)(INSPECTION.mesh, moved)
        slicePlate(engine, repository)()

        assertEquals(moved, inspector.placements.single())
        assertEquals(INSPECTION.placement, inspector.previous)
        assertEquals(Manipulation.Move, inspector.manipulation)
        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), inspector.inspected.single())
        assertEquals(dropped, engine.request?.placement)
        assertEquals(dropped, repository.state.value.plateObject?.inspection?.placement)
        assertFalse(checkNotNull(repository.state.value.plateObject).placing)
    }

    @Test
    fun `auto orient asks OrcaSlicer for the orientation even though the placement stays`() {
        val oriented = translated(175.0, 175.0, 12.0)
        val inspector = FakeInspector(placed = { INSPECTION.copy(placement = oriented) })
        val repository = FakeRepository(readyState(CUBE))

        placePlateObject(inspector, repository)(INSPECTION.mesh, INSPECTION.placement, Manipulation.AutoOrient)

        assertEquals(Manipulation.AutoOrient, inspector.manipulation)
        assertEquals(oriented, repository.state.value.plateObject?.inspection?.placement)
    }

    @Test
    fun `with auto drop off a lifted object is placed as is, and turning it on rests it on the plate`() {
        val lifted = translated(175.0, 175.0, 40.0)
        val inspector = FakeInspector(placed = { INSPECTION.copy(placement = it) })
        val repository = FakeRepository(readyState(CUBE))
        val place = placePlateObject(inspector, repository)
        val setAutoDrop = SetPlateObjectAutoDropUseCase(repository, place)

        setAutoDrop(INSPECTION.mesh, false)
        place(INSPECTION.mesh, lifted)

        assertEquals(false, inspector.autoDrop)
        assertFalse(checkNotNull(repository.state.value.plateObject).autoDrop)

        setAutoDrop(INSPECTION.mesh, true)

        assertEquals(Manipulation.EnsureOnBed, inspector.manipulation)
        assertEquals(true, inspector.autoDrop)
        assertTrue(checkNotNull(repository.state.value.plateObject).autoDrop)
    }

    @Test
    fun `a placement that settles where the object stood keeps its G-code`() {
        val result = PlateSliceResult(SliceJobId("old"), CUBE, OutputPath("/gcode/old.gcode"), STATISTICS)
        val inspector = FakeInspector(placed = { INSPECTION })
        val repository = FakeRepository(readyState(CUBE).copy(result = result))

        placePlateObject(inspector, repository)(INSPECTION.mesh, INSPECTION.placement, Manipulation.AutoOrient)

        assertEquals(result, repository.state.value.result)
    }

    @Test
    fun `a move that ends where it began changes nothing`() {
        val inspector = FakeInspector()
        val repository = FakeRepository(readyState(CUBE))

        placePlateObject(inspector, repository)(INSPECTION.mesh, INSPECTION.placement, Manipulation.Rotate)

        assertTrue(inspector.placements.isEmpty())
    }

    @Test
    fun `the plate is not sliced until OrcaSlicer confirms the new placement`() {
        val inspector = FakeInspector(placed = null)
        val repository = FakeRepository(readyState(CUBE).copy(result = PlateSliceResult(SliceJobId("old"), CUBE, OutputPath("/gcode/old.gcode"), STATISTICS)))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))

        val state = repository.state.value
        assertTrue(checkNotNull(state.plateObject).placing)
        assertEquals(translated(100.0, 120.0, 10.0), state.plateObject?.inspection?.placement)
        assertNull(state.result)
        assertFalse(state.canSlice)
    }

    @Test
    fun `an object across the plate boundary cannot be sliced`() {
        val inspector = FakeInspector(placed = { INSPECTION.copy(placement = it, fit = BuildVolumeFit.PARTLY_OUTSIDE) })
        val repository = FakeRepository(readyState(CUBE))
        val engine = FakeEngine()

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(0.0, 175.0, 10.0))
        slicePlate(engine, repository)()

        assertEquals(BuildVolumeFit.PARTLY_OUTSIDE, repository.state.value.plateObject?.inspection?.fit)
        assertFalse(repository.state.value.canSlice)
        assertNull(engine.request)
    }

    @Test
    fun `a placement OrcaSlicer rejects is reported and the object can be placed again`() {
        val inspector = FakeInspector(placed = { error("engine gone") })
        val repository = FakeRepository(readyState(CUBE))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))

        val state = repository.state.value
        assertEquals(PlateProblemKind.PLACEMENT_FAILED, state.problem?.kind)
        assertFalse(checkNotNull(state.plateObject).placing)
    }

    @Test
    fun `nothing moves while slicing`() {
        val inspector = FakeInspector()
        val repository = FakeRepository(readyState(CUBE).copy(slicing = PlateSlicing(SliceJobId("job"))))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))

        assertEquals(CUBE, repository.state.value.plateObject)
        assertTrue(inspector.placements.isEmpty())
    }

    @Test
    fun `a placement for an object no longer on the plate changes nothing`() {
        val inspector = FakeInspector()
        val repository = FakeRepository(readyState(CUBE))

        placePlateObject(inspector, repository)(ScenePath("/scene/objects/replaced.mesh"), translated(100.0, 120.0, 10.0))

        assertEquals(CUBE, repository.state.value.plateObject)
        assertTrue(inspector.placements.isEmpty())
    }

    @Test
    fun `progress reaches the plate while the job runs`() {
        val repository = FakeRepository(readyState(CUBE))
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
        val repository = FakeRepository(PlateState(PROFILES, plateObject = CUBE))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        assertNull(engine.request)
        assertNull(repository.state.value.slicing)
    }

    @Test
    fun `an engine crash is reported as its own problem`() {
        val repository = FakeRepository(readyState(CUBE))
        val engine = FakeEngine(outcome = { SliceOutcome.Failure(it, SliceFailureCode.ENGINE_CRASHED, "died", recoverable = true) })

        slicePlate(engine, repository)()

        assertEquals(PlateProblemKind.ENGINE_CRASHED, repository.state.value.problem?.kind)
        assertNull(repository.state.value.result)
    }

    @Test
    fun `cancelling marks the running job`() {
        val job = SliceJobId("job-1")
        val repository = FakeRepository(readyState(CUBE).copy(slicing = PlateSlicing(job)))
        val engine = FakeEngine()

        CancelPlateSlicingUseCase(CancelSliceUseCase(engine), repository, scope)()

        assertEquals(job, engine.cancelled)
        assertTrue(repository.state.value.slicing?.cancelling == true)
    }

    @Test
    fun `an imported document goes on the plate as Orca placed it and replaces the previous mesh`() {
        val repository = FakeRepository(readyState(CUBE))
        val file = ImportedModelFile(ModelPath("/imports/a.stl"), "a.stl")
        val inspector = FakeInspector()
        val files = FakeSceneFiles()

        addModel(repository, ModelImportOutcome.Success(file), inspector, files)(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        val placed = state.plateObject as PlateObject.ImportedModel
        assertEquals(file, placed.file)
        assertEquals(ModelSource.LocalFile(file.path), inspector.inspected.single())
        assertEquals(PROFILES, inspector.profiles)
        assertEquals(files.created.single(), placed.inspection.mesh)
        assertEquals(listOf(CUBE.inspection.mesh), files.deleted)
    }

    @Test
    fun `the calibration cube is loaded by the engine`() {
        val repository = FakeRepository(readyState(null))
        val inspector = FakeInspector()

        AddCalibrationCubeToPlateUseCase(InspectModelUseCase(inspector), FakeSceneFiles(), repository, scope)()

        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), inspector.inspected.single())
        assertIsCube(repository.state.value.plateObject)
    }

    @Test
    fun `a failed import keeps the plate and reports the problem`() {
        val repository = FakeRepository(readyState(CUBE))
        val files = FakeSceneFiles()

        addModel(repository, ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, "empty"), FakeInspector(), files)(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(CUBE, state.plateObject)
        assertEquals(PlateProblemKind.IMPORT_FAILED, state.problem?.kind)
        assertEquals("empty", state.problem?.detail)
        assertTrue(files.deleted.isEmpty())
    }

    @Test
    fun `a model Orca cannot load keeps the plate and leaves no mesh behind`() {
        val repository = FakeRepository(readyState(CUBE))
        val file = ImportedModelFile(ModelPath("/imports/broken.stl"), "broken.stl")
        val files = FakeSceneFiles()

        addModel(repository, ModelImportOutcome.Success(file), FakeInspector(ModelInspectionOutcome.Failure("no facets")), files)(REFERENCE)

        val state = repository.state.value
        assertEquals(CUBE, state.plateObject)
        assertEquals("no facets", state.problem?.detail)
        assertEquals(files.created, files.deleted)
    }

    @Test
    fun `nothing is loaded while the plate is busy`() {
        val repository = FakeRepository(readyState(CUBE).copy(slicing = PlateSlicing(SliceJobId("job"))))
        val inspector = FakeInspector()

        AddCalibrationCubeToPlateUseCase(InspectModelUseCase(inspector), FakeSceneFiles(), repository, scope)()

        assertTrue(inspector.inspected.isEmpty())
    }

    @Test
    fun `a started engine describes the plate`() {
        val repository = FakeRepository(PlateState(PROFILES))
        val files = FakeSceneFiles()

        runSuspend { StartEngineUseCase(GetEngineStatusUseCase(FakeEngine()), FakeInspector(), files, repository)() }

        val state = repository.state.value
        assertEquals(EngineAvailability.READY, state.engine.availability)
        assertEquals(PLATE, state.plate)
        assertTrue(files.clearedObjects)
    }

    @Test
    fun `an engine that cannot start is reported as unavailable`() {
        val repository = FakeRepository(PlateState(PROFILES))
        val engine = FakeEngine(status = EngineStatus(EngineVersion("orca"), ready = false, message = "no profiles"))

        runSuspend { StartEngineUseCase(GetEngineStatusUseCase(engine), FakeInspector(), FakeSceneFiles(), repository)() }

        val state = repository.state.value
        assertEquals(EngineAvailability.UNAVAILABLE, state.engine.availability)
        assertEquals(PlateProblemKind.ENGINE_UNAVAILABLE, state.problem?.kind)
        assertEquals("no profiles", state.problem?.detail)
        assertNull(state.plate)
    }

    private fun slicePlate(engine: FakeEngine, repository: PlateRepository) = SlicePlateUseCase(
        sliceModel = SliceModelUseCase(engine),
        outputs = GcodeOutputs { OutputPath("/gcode/$it.gcode") },
        repository = repository,
        applicationScope = scope,
    )

    private fun addModel(repository: PlateRepository, imported: ModelImportOutcome, inspector: FakeInspector, files: FakeSceneFiles) =
        AddModelToPlateUseCase(
            importModel = ImportModelUseCase(object : ModelFileImporter {
                override suspend fun importModel(reference: ExternalDocumentReference) = imported
            }),
            inspectModel = InspectModelUseCase(inspector),
            sceneFiles = files,
            repository = repository,
            applicationScope = scope,
        )

    private fun assertIsCube(plateObject: PlateObject?) {
        assertTrue(plateObject is PlateObject.CalibrationCube, "Expected the calibration cube, got $plateObject")
    }

    private fun placePlateObject(inspector: FakeInspector, repository: FakeRepository) =
        PlacePlateObjectUseCase(PlaceModelUseCase(inspector), repository, scope)

    private fun translated(x: Double, y: Double, z: Double) =
        Transform3(INSPECTION.placement.columns.toMutableList().also { it[12] = x; it[13] = y; it[14] = z })

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

    private class FakeInspector(
        private val outcome: ModelInspectionOutcome? = null,
        /** The placed object for a placement; null leaves the call suspended. */
        private val placed: ((Transform3) -> ModelInspection)? = { INSPECTION.copy(placement = it) },
    ) : PlateInspector {
        val inspected = mutableListOf<ModelSource>()
        val placements = mutableListOf<Transform3>()
        var previous: Transform3? = null
        var autoDrop: Boolean? = null
        var manipulation: Manipulation? = null
        var profiles: SlicingProfileSelection? = null

        override suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath) =
            PlateDescriptionOutcome.Success(PLATE)

        override suspend fun inspect(model: ModelSource, profiles: SlicingProfileSelection, mesh: ScenePath): ModelInspectionOutcome {
            inspected += model
            this.profiles = profiles
            return outcome ?: ModelInspectionOutcome.Success(INSPECTION.copy(mesh = mesh))
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
            inspected += model
            placements += placement
            this.previous = previous
            this.autoDrop = autoDrop
            this.manipulation = manipulation
            val place = placed ?: return suspendCancellableCoroutine { }
            return ModelInspectionOutcome.Success(place(placement).copy(mesh = mesh))
        }

        override suspend fun flatteningPlanes(
            model: ModelSource,
            profiles: SlicingProfileSelection,
            mesh: ScenePath,
            placement: Transform3,
        ) = FlatteningPlanesOutcome.Failure("not used")
    }

    private class FakeSceneFiles : SceneFiles {
        val created = mutableListOf<ScenePath>()
        val deleted = mutableListOf<ScenePath>()
        var clearedObjects = false

        override fun plateDirectory() = ScenePath("/scene/plate")

        override fun newObjectMesh() = ScenePath("/scene/objects/${created.size}.mesh").also(created::add)

        override fun deleteObjectMesh(mesh: ScenePath) {
            deleted += mesh
        }

        override fun deleteAllObjectMeshes() {
            clearedObjects = true
        }
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
        val INSPECTION = ModelInspection(
            facetCount = 12,
            dimensions = ModelDimensions(20.0, 20.0, 20.0),
            boxCenter = Vector3(0.0, 0.0, 10.0),
            mesh = ScenePath("/scene/objects/cube.mesh"),
            placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
            fit = BuildVolumeFit.INSIDE,
            boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
            rotationDegrees = Vector3(0.0, 0.0, 0.0),
            unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
        )
        val CUBE = PlateObject.CalibrationCube(INSPECTION)
        val PLATE = PlateDescription(
            geometry = PlateGeometry(
                printableArea = listOf(Point2(0.0, 0.0), Point2(350.0, 0.0), Point2(350.0, 350.0), Point2(0.0, 350.0)),
                printableHeight = 350.0,
                plateTriangles = emptyList(),
                excludeTriangles = emptyList(),
                thinGridLines = emptyList(),
                boldGridLines = emptyList(),
                bedModel = null,
                bedTexture = null,
            ),
            filamentColor = ColorRgba(0.95f, 0.46f, 0.31f),
        )
        val REFERENCE = ExternalDocumentReference("content://model")
    }
}
