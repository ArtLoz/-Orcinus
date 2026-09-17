package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ArrangeSettings
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
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
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
import app.orcinus.shadow.domain.PlaceModelsUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
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
import kotlinx.coroutines.CompletableDeferred
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
        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), request.objects.single().model)
        assertEquals(OutputPath("/gcode/calibration-cube-20mm.gcode"), request.output)
        assertEquals(PROFILES.process, request.processProfile)
        val state = repository.state.value
        assertNull(state.slicing)
        assertEquals(listOf(CUBE), state.result?.objects)
        assertEquals(STATISTICS, state.result?.statistics)
    }

    @Test
    fun `an imported model is sliced from its stored file into a G-code named after the document`() {
        val model = PlateObject.ImportedModel(ImportedModelFile(ModelPath("/imports/benchy.stl"), "benchy.stl"), INSPECTION)
        val repository = FakeRepository(readyState(model))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        assertEquals(ModelSource.LocalFile(ModelPath("/imports/benchy.stl")), engine.request?.objects?.single()?.model)
        assertEquals(OutputPath("/gcode/benchy.gcode"), engine.request?.output)
    }

    @Test
    fun `every object on the plate is sliced where it stands, and the G-code is named after the first one the plate prints`() {
        val benchy = PlateObject.ImportedModel(
            ImportedModelFile(ModelPath("/imports/benchy.stl"), "benchy.stl"),
            INSPECTION.copy(mesh = ScenePath("/scene/objects/benchy.mesh"), placement = translated(-100.0, 175.0, 10.0), fit = BuildVolumeFit.OUTSIDE),
            autoDrop = false,
        )
        val cube = CUBE.copy(inspection = INSPECTION.copy(placement = translated(100.0, 120.0, 10.0)))
        val repository = FakeRepository(readyState(benchy, cube))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        val objects = checkNotNull(engine.request).objects
        assertEquals(
            listOf(
                PlacedModel(ModelSource.LocalFile(ModelPath("/imports/benchy.stl")), benchy.inspection.mesh, translated(-100.0, 175.0, 10.0), autoDrop = false),
                PlacedModel(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), cube.inspection.mesh, translated(100.0, 120.0, 10.0)),
            ),
            objects,
        )
        assertEquals(OutputPath("/gcode/calibration-cube-20mm.gcode"), engine.request?.output)
        assertEquals(listOf(benchy, cube), repository.state.value.result?.objects)
    }

    @Test
    fun `a plate is sliced when an object is on it, even with another entirely off it`() {
        val off = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/off.mesh"), fit = BuildVolumeFit.OUTSIDE))

        assertTrue(readyState(CUBE, off).canSlice)
        assertFalse(readyState(off).canSlice)
        assertFalse(readyState().canSlice)
    }

    @Test
    fun `a moved object is placed by OrcaSlicer, sliced where it stands, and its old G-code is dropped`() {
        val moved = translated(100.0, 120.0, 30.0)
        val dropped = translated(100.0, 120.0, 10.0)
        val inspector = FakeInspector(placed = { INSPECTION.copy(placement = dropped, dimensions = ModelDimensions(20.0, 20.0, 20.0)) })
        val repository = FakeRepository(readyState(CUBE).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)))
        val engine = FakeEngine()

        placePlateObject(inspector, repository)(INSPECTION.mesh, moved)
        slicePlate(engine, repository)()

        assertEquals(moved, inspector.placements.single())
        assertEquals(INSPECTION.placement, inspector.previous)
        assertEquals(Manipulation.Move, inspector.manipulation)
        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), inspector.inspected.single())
        assertEquals(dropped, engine.request?.objects?.single()?.placement)
        assertEquals(dropped, repository.state.value.objects.single().inspection.placement)
        assertFalse(repository.state.value.objects.single().placing)
    }

    @Test
    fun `a manipulation of one object leaves the others as they are`() {
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh"), placement = translated(250.0, 175.0, 10.0)))
        val inspector = FakeInspector()
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))

        assertEquals(translated(100.0, 120.0, 10.0), repository.state.value.objects.first().inspection.placement)
        assertEquals(other, repository.state.value.objects.last())
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
        assertFalse(repository.state.value.objects.single().autoDrop)

        setAutoDrop(INSPECTION.mesh, true)

        assertEquals(Manipulation.EnsureOnBed, inspector.manipulation)
        assertEquals(true, inspector.autoDrop)
        assertTrue(repository.state.value.objects.single().autoDrop)
    }

    @Test
    fun `a placement that settles where the object stood keeps its G-code`() {
        val result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)
        val inspector = FakeInspector(placed = { INSPECTION })
        val repository = FakeRepository(readyState(CUBE).copy(result = result))

        placePlateObject(inspector, repository)(INSPECTION.mesh, INSPECTION.placement, Manipulation.ResetRotation)

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
        val repository = FakeRepository(readyState(CUBE).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))

        val state = repository.state.value
        assertTrue(state.objects.single().placing)
        assertEquals(translated(100.0, 120.0, 10.0), state.objects.single().inspection.placement)
        assertNull(state.result)
        assertFalse(state.canSlice)
    }

    @Test
    fun `an object across the plate boundary cannot be sliced`() {
        val inspector = FakeInspector(placed = { INSPECTION.copy(placement = it, fit = BuildVolumeFit.PARTLY_OUTSIDE) })
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val repository = FakeRepository(readyState(CUBE, other))
        val engine = FakeEngine()

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(0.0, 175.0, 10.0))
        slicePlate(engine, repository)()

        assertEquals(BuildVolumeFit.PARTLY_OUTSIDE, repository.state.value.objects.first().inspection.fit)
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
        assertFalse(state.objects.single().placing)
    }

    @Test
    fun `nothing moves while slicing`() {
        val inspector = FakeInspector()
        val repository = FakeRepository(readyState(CUBE).copy(slicing = PlateSlicing(SliceJobId("job"))))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))

        assertEquals(listOf(CUBE), repository.state.value.objects)
        assertTrue(inspector.placements.isEmpty())
    }

    @Test
    fun `a placement for an object no longer on the plate changes nothing`() {
        val inspector = FakeInspector()
        val repository = FakeRepository(readyState(CUBE))

        placePlateObject(inspector, repository)(ScenePath("/scene/objects/deleted.mesh"), translated(100.0, 120.0, 10.0))

        assertEquals(listOf(CUBE), repository.state.value.objects)
        assertTrue(inspector.placements.isEmpty())
    }

    @Test
    fun `arranging places every object as OrcaSlicer answers and drops the old G-code`() {
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val settings = ArrangeSettings(distance = 6.0)
        val inspector = FakeInspector(placedObjects = { plate -> plate.mapIndexed { index, placed -> INSPECTION.copy(mesh = placed.mesh, placement = translated(150.0 + 50.0 * index, 175.0, 10.0)) } })
        val repository = FakeRepository(readyState(CUBE, other).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE, other), OutputPath("/gcode/old.gcode"), STATISTICS)))

        placePlateObjects(inspector, repository)(PlateManipulation.Arrange(settings))

        assertEquals(PlateManipulation.Arrange(settings), inspector.plateManipulation)
        assertEquals(listOf(CUBE.inspection.mesh, other.inspection.mesh), inspector.plate.map(PlacedModel::mesh))
        val state = repository.state.value
        assertEquals(listOf(translated(150.0, 175.0, 10.0), translated(200.0, 175.0, 10.0)), state.objects.map { it.inspection.placement })
        assertTrue(state.objects.none(PlateObject::placing))
        assertNull(state.result)
    }

    @Test
    fun `auto orient with a selected object places only that object`() {
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val inspector = FakeInspector(placedObjects = null)
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObjects(inspector, repository)(PlateManipulation.AutoOrient(setOf(other.inspection.mesh)))

        assertEquals(listOf(false, true), repository.state.value.objects.map(PlateObject::placing))
        assertEquals(2, inspector.plate.size)
    }

    @Test
    fun `auto orient with nothing selected places every object`() {
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val inspector = FakeInspector(placedObjects = null)
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObjects(inspector, repository)(PlateManipulation.AutoOrient())

        assertEquals(listOf(true, true), repository.state.value.objects.map(PlateObject::placing))
    }

    @Test
    fun `an object moved while the plate is arranged keeps the placement the user gave it`() {
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val answer = CompletableDeferred<PlateInspectionOutcome>()
        val inspector = FakeInspector(arranged = answer)
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObjects(inspector, repository)(PlateManipulation.Arrange(ArrangeSettings()))
        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(60.0, 60.0, 10.0))
        answer.complete(
            PlateInspectionOutcome.Success(
                listOf(
                    INSPECTION.copy(placement = translated(150.0, 175.0, 10.0)),
                    other.inspection.copy(placement = translated(200.0, 175.0, 10.0)),
                ),
            ),
        )

        val state = repository.state.value
        assertEquals(listOf(translated(60.0, 60.0, 10.0), translated(200.0, 175.0, 10.0)), state.objects.map { it.inspection.placement })
        assertTrue(state.objects.none(PlateObject::placing))
    }

    @Test
    fun `no job places the plate while a placement is unsettled`() {
        val inspector = FakeInspector(placed = null)
        val repository = FakeRepository(readyState(CUBE))

        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(100.0, 120.0, 10.0))
        placePlateObjects(inspector, repository)(PlateManipulation.Arrange(ArrangeSettings()))

        assertNull(inspector.plateManipulation)
    }

    @Test
    fun `a job OrcaSlicer rejects is reported and leaves the objects where they were`() {
        val inspector = FakeInspector(arranged = CompletableDeferred(PlateInspectionOutcome.Failure("no room")))
        val repository = FakeRepository(readyState(CUBE))

        placePlateObjects(inspector, repository)(PlateManipulation.Arrange(ArrangeSettings()))

        val state = repository.state.value
        assertEquals(listOf(CUBE), state.objects)
        assertEquals(PlateProblemKind.PLACEMENT_FAILED, state.problem?.kind)
        assertEquals("no room", state.problem?.detail)
    }

    @Test
    fun `deleting an object takes it and its mesh off the plate and drops the G-code`() {
        val other = CUBE.copy(inspection = INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val repository = FakeRepository(readyState(CUBE, other).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE, other), OutputPath("/gcode/old.gcode"), STATISTICS)))
        val files = FakeSceneFiles()

        DeletePlateObjectUseCase(files, repository)(CUBE.inspection.mesh)

        assertEquals(listOf(other), repository.state.value.objects)
        assertNull(repository.state.value.result)
        assertEquals(listOf(CUBE.inspection.mesh), files.deleted)
    }

    @Test
    fun `nothing is deleted while slicing`() {
        val repository = FakeRepository(readyState(CUBE).copy(slicing = PlateSlicing(SliceJobId("job"))))
        val files = FakeSceneFiles()

        DeletePlateObjectUseCase(files, repository)(CUBE.inspection.mesh)

        assertEquals(listOf(CUBE), repository.state.value.objects)
        assertTrue(files.deleted.isEmpty())
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
        val repository = FakeRepository(PlateState(presets = PRESETS, objects = listOf(CUBE)))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        assertNull(engine.request)
        assertNull(repository.state.value.slicing)
    }

    @Test
    fun `an engine crash is reported as its own problem`() {
        val repository = FakeRepository(readyState(CUBE))
        val engine = FakeEngine(outcome = { SliceOutcome.Failure(it.jobId, SliceFailureCode.ENGINE_CRASHED, "died", recoverable = true) })
        val files = FakeSceneFiles()

        slicePlate(engine, repository, files)()

        assertEquals(PlateProblemKind.ENGINE_CRASHED, repository.state.value.problem?.kind)
        assertNull(repository.state.value.result)
        assertNull(files.keptToolpaths)
    }

    @Test
    fun `a slice keeps its toolpaths for the preview and deletes those of earlier results`() {
        val old = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS, ScenePath("/scene/toolpaths/old.toolpaths"))
        val repository = FakeRepository(readyState(CUBE).copy(result = old))
        val engine = FakeEngine()
        val files = FakeSceneFiles()

        slicePlate(engine, repository, files)()

        val toolpaths = files.toolpaths.single()
        assertEquals(toolpaths, engine.request?.toolpaths)
        assertEquals(toolpaths, repository.state.value.result?.toolpaths)
        assertEquals(toolpaths, files.keptToolpaths)
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
    fun `an imported document joins the objects on the plate, placed among them by OrcaSlicer`() {
        val result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)
        val repository = FakeRepository(readyState(CUBE).copy(result = result))
        val file = ImportedModelFile(ModelPath("/imports/a.stl"), "a.stl")
        val inspector = FakeInspector()
        val files = FakeSceneFiles()

        addModel(repository, ModelImportOutcome.Success(file), inspector, files)(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(2, state.objects.size)
        assertEquals(CUBE, state.objects.first())
        val placed = state.objects.last() as PlateObject.ImportedModel
        assertEquals(file, placed.file)
        assertEquals(ModelSource.LocalFile(file.path), inspector.inspected.single())
        assertEquals(PROFILES, inspector.profiles)
        assertEquals(listOf(PlacedModel(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), CUBE.inspection.mesh, CUBE.inspection.placement)), inspector.plate)
        assertEquals(files.created.single(), placed.inspection.mesh)
        assertTrue(files.deleted.isEmpty())
        assertNull(state.result)
    }

    @Test
    fun `the calibration cube is loaded by the engine`() {
        val repository = FakeRepository(readyState())
        val inspector = FakeInspector()

        AddCalibrationCubeToPlateUseCase(InspectModelUseCase(inspector), FakeSceneFiles(), repository, scope)()

        assertEquals(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM), inspector.inspected.single())
        assertTrue(inspector.plate.isEmpty())
        assertIsCube(repository.state.value.objects.single())
    }

    @Test
    fun `a failed import keeps the plate and reports the problem`() {
        val repository = FakeRepository(readyState(CUBE))
        val files = FakeSceneFiles()

        addModel(repository, ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, "empty"), FakeInspector(), files)(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(listOf(CUBE), state.objects)
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
        assertEquals(listOf(CUBE), state.objects)
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
    fun `a started engine reports the presets it remembers and describes the plate of their printer`() {
        val repository = FakeRepository(PlateState())
        val files = FakeSceneFiles()
        val inspector = FakeInspector()

        runSuspend { startEngine(FakeEngine(), FakePresetManager(), inspector, repository, files)() }

        val state = repository.state.value
        assertEquals(EngineAvailability.READY, state.engine.availability)
        assertEquals(PRESETS, state.presets)
        assertEquals(PROFILES, state.profiles)
        assertEquals(PLATE, state.plate)
        assertEquals(listOf(PROFILES), inspector.described)
        assertTrue(files.clearedObjects)
    }

    @Test
    fun `a started engine without a printer set up leaves the plate for the Setup Wizard`() {
        val repository = FakeRepository(PlateState())
        val inspector = FakeInspector()
        val presets = FakePresetManager(presets = PresetsOutcome.Success(PRESETS.copy(setupRequired = true)))

        runSuspend { startEngine(FakeEngine(), presets, inspector, repository)() }

        val state = repository.state.value
        assertTrue(state.presets?.setupRequired == true)
        assertNull(state.profiles)
        assertNull(state.plate)
        assertTrue(inspector.described.isEmpty())
        assertFalse(state.canSlice)
    }

    @Test
    fun `an engine that cannot start is reported as unavailable`() {
        val repository = FakeRepository(PlateState())
        val engine = FakeEngine(status = EngineStatus(EngineVersion("orca"), ready = false, message = "no profiles"))

        runSuspend { startEngine(engine, FakePresetManager(), FakeInspector(), repository)() }

        val state = repository.state.value
        assertEquals(EngineAvailability.UNAVAILABLE, state.engine.availability)
        assertEquals(PlateProblemKind.ENGINE_UNAVAILABLE, state.problem?.kind)
        assertEquals("no profiles", state.problem?.detail)
        assertNull(state.presets)
        assertNull(state.plate)
    }

    @Test
    fun `another printer describes its plate, judges whether the objects fit, and drops the G-code`() {
        val result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)
        val repository = FakeRepository(readyState(CUBE).copy(result = result))
        val inspector = FakeInspector(placedObjects = { plate -> plate.map { INSPECTION.copy(mesh = it.mesh, placement = it.placement, fit = BuildVolumeFit.OUTSIDE) } })
        val presets = FakePresetManager(selected = { PresetsOutcome.Success(OTHER_PRINTER) })
        val choice = PresetChoice.PrinterModel("Other Printer")

        SelectPresetUseCase(presets, platePresets(inspector, repository), repository, scope)(choice)

        val state = repository.state.value
        assertEquals(listOf<PresetChoice>(choice), presets.choices)
        assertEquals(OTHER_PRINTER.selection, state.profiles)
        assertFalse(state.changingPresets)
        assertNull(state.result)
        assertEquals(listOf(OTHER_PRINTER.selection), inspector.described)
        assertEquals(PlateManipulation.UpdatePrintVolume, inspector.plateManipulation)
        assertEquals(BuildVolumeFit.OUTSIDE, state.objects.single().inspection.fit)
        assertFalse(state.objects.single().placing)
    }

    @Test
    fun `another process keeps the plate as it is`() {
        val repository = FakeRepository(readyState(CUBE).copy(plate = PLATE))
        val inspector = FakeInspector()
        val otherProcess = PRESETS.copy(selection = PROFILES.copy(process = ProfileId("other process")))
        val presets = FakePresetManager(selected = { PresetsOutcome.Success(otherProcess) })

        SelectPresetUseCase(presets, platePresets(inspector, repository), repository, scope)(PresetChoice.Process(ProfileId("other process")))

        assertEquals(otherProcess, repository.state.value.presets)
        assertTrue(inspector.described.isEmpty())
        assertNull(inspector.plateManipulation)
    }

    @Test
    fun `a preset the engine cannot select is reported and the selection stays`() {
        val repository = FakeRepository(readyState(CUBE))
        val presets = FakePresetManager(selected = { PresetsOutcome.Failure("Configuration incompatible") })

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), repository, scope)(PresetChoice.NozzleDiameter("0.6"))

        val state = repository.state.value
        assertEquals(PRESETS, state.presets)
        assertFalse(state.changingPresets)
        assertEquals(PlateProblemKind.PRESETS_FAILED, state.problem?.kind)
        assertEquals("Configuration incompatible", state.problem?.detail)
    }

    @Test
    fun `no preset is selected while the plate is busy`() {
        val repository = FakeRepository(readyState(CUBE).copy(importing = true))
        val presets = FakePresetManager()

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), repository, scope)(PresetChoice.Filament(ProfileId("PLA")))

        assertTrue(presets.choices.isEmpty())
        assertFalse(repository.state.value.changingPresets)
    }

    @Test
    fun `finishing the Setup Wizard installs the chosen printers and describes the plate of the one selected`() {
        val repository = FakeRepository(readyState().copy(presets = PRESETS.copy(setupRequired = true)))
        val inspector = FakeInspector()
        val presets = FakePresetManager()
        val applySetup = ApplySetupUseCase(presets, platePresets(inspector, repository), repository, scope)

        val outcome = runSuspend { applySetup(listOf("Creality K2 Plus"), listOf("Generic PLA @K2 Plus-all")) }

        assertEquals(PresetsOutcome.Success(PRESETS), outcome)
        assertEquals(listOf("Creality K2 Plus") to listOf("Generic PLA @K2 Plus-all"), presets.appliedSetup)
        assertEquals(PROFILES, repository.state.value.profiles)
        assertEquals(PLATE, repository.state.value.plate)
        assertEquals(listOf(PROFILES), inspector.described)
    }

    @Test
    fun `closing the Setup Wizard installs OrcaSlicer's default printer`() {
        val repository = FakeRepository(readyState().copy(presets = PRESETS.copy(setupRequired = true)))
        val presets = FakePresetManager()

        runSuspend { ApplySetupUseCase(presets, platePresets(FakeInspector(), repository), repository, scope).defaults() }

        assertTrue(presets.appliedDefaults)
        assertEquals(PROFILES, repository.state.value.profiles)
    }

    private fun startEngine(
        engine: FakeEngine,
        presets: FakePresetManager,
        inspector: FakeInspector,
        repository: FakeRepository,
        files: FakeSceneFiles = FakeSceneFiles(),
    ) = StartEngineUseCase(GetEngineStatusUseCase(engine), presets, platePresets(inspector, repository, files), files, repository)

    private fun platePresets(inspector: FakeInspector, repository: FakeRepository, files: FakeSceneFiles = FakeSceneFiles()) =
        PlatePresets(inspector, files, repository, placePlateObjects(inspector, repository))

    private fun slicePlate(engine: FakeEngine, repository: PlateRepository, files: FakeSceneFiles = FakeSceneFiles()) = SlicePlateUseCase(
        sliceModel = SliceModelUseCase(engine),
        outputs = GcodeOutputs { OutputPath("/gcode/$it.gcode") },
        sceneFiles = files,
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

    private fun placePlateObjects(inspector: FakeInspector, repository: FakeRepository) =
        PlacePlateObjectsUseCase(PlaceModelsUseCase(inspector), repository, scope)

    private fun translated(x: Double, y: Double, z: Double) =
        Transform3(INSPECTION.placement.columns.toMutableList().also { it[12] = x; it[13] = y; it[14] = z })

    private fun readyState(vararg objects: PlateObject) = PlateState(
        presets = PRESETS,
        engine = EngineState(EngineAvailability.READY, EngineVersion("orca")),
        objects = objects.toList(),
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
        /** The placed objects for a job; null leaves the call suspended. */
        private val placedObjects: ((List<PlacedModel>) -> List<ModelInspection>)? = { plate -> plate.map { INSPECTION.copy(mesh = it.mesh, placement = it.placement) } },
        /** When set, the answer to a job, once completed. */
        private val arranged: CompletableDeferred<PlateInspectionOutcome>? = null,
    ) : PlateInspector {
        val inspected = mutableListOf<ModelSource>()
        val placements = mutableListOf<Transform3>()
        var plate: List<PlacedModel> = emptyList()
        var previous: Transform3? = null
        var autoDrop: Boolean? = null
        var manipulation: Manipulation? = null
        var plateManipulation: PlateManipulation? = null
        var profiles: SlicingProfileSelection? = null
        val described = mutableListOf<SlicingProfileSelection>()

        override suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath): PlateDescriptionOutcome {
            described += profiles
            return PlateDescriptionOutcome.Success(PLATE)
        }

        override suspend fun inspect(model: ModelSource, profiles: SlicingProfileSelection, mesh: ScenePath, plate: List<PlacedModel>): ModelInspectionOutcome {
            inspected += model
            this.profiles = profiles
            this.plate = plate
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

        override suspend fun placeObjects(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            manipulation: PlateManipulation,
        ): PlateInspectionOutcome {
            this.plate = plate
            plateManipulation = manipulation
            arranged?.let { return it.await() }
            val place = placedObjects ?: return suspendCancellableCoroutine { }
            return PlateInspectionOutcome.Success(place(plate))
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

        val toolpaths = mutableListOf<ScenePath>()
        var keptToolpaths: ScenePath? = ScenePath("never cleaned")

        override fun newToolpaths() = ScenePath("/scene/toolpaths/${toolpaths.size}.toolpaths").also(toolpaths::add)

        override fun deleteToolpathsExcept(keep: ScenePath?) {
            keptToolpaths = keep
        }
    }

    private class FakeEngine(
        private val status: EngineStatus = EngineStatus(EngineVersion("orca"), ready = true),
        private val onSlice: (SliceRequest, SliceProgressListener) -> Unit = { _, _ -> },
        private val outcome: (SliceRequest) -> SliceOutcome = { SliceOutcome.Success(it.jobId, OutputPath("/gcode/out.gcode"), STATISTICS, it.toolpaths) },
    ) : SlicerEngine {
        var request: SliceRequest? = null
        var cancelled: SliceJobId? = null

        override suspend fun status() = status

        override suspend fun slice(request: SliceRequest, progressListener: SliceProgressListener): SliceOutcome {
            this.request = request
            onSlice(request, progressListener)
            return outcome(request)
        }

        override suspend fun cancel(jobId: SliceJobId): Boolean {
            cancelled = jobId
            return true
        }
    }

    private class FakePresetManager(
        private val presets: PresetsOutcome = PresetsOutcome.Success(PRESETS),
        private val selected: (PresetChoice) -> PresetsOutcome = { PresetsOutcome.Success(PRESETS) },
        private val setup: PresetsOutcome = PresetsOutcome.Success(PRESETS),
    ) : PresetManager {
        val choices = mutableListOf<PresetChoice>()
        var appliedSetup: Pair<List<String>, List<String>>? = null
        var appliedDefaults = false

        override suspend fun presets() = presets

        override suspend fun selectPreset(choice: PresetChoice): PresetsOutcome {
            choices += choice
            return selected(choice)
        }

        override suspend fun setupPrinters() = SetupPrintersOutcome.Failure("not used")

        override suspend fun setupFilaments(models: List<String>) = SetupFilamentsOutcome.Failure("not used")

        override suspend fun applySetup(models: List<String>, filaments: List<String>): PresetsOutcome {
            appliedSetup = models to filaments
            return setup
        }

        override suspend fun applyDefaultSetup(): PresetsOutcome {
            appliedDefaults = true
            return setup
        }
    }

    private companion object {
        val PROFILES = SlicingProfileSelection(ProfileId("printer"), ProfileId("filament"), ProfileId("process"))
        val PRESETS = Presets(
            selection = PROFILES,
            setupRequired = false,
            printers = emptyList(),
            filaments = emptyList(),
            processes = emptyList(),
            nozzleDiameters = listOf("0.4"),
            nozzleDiameter = "0.4",
        )
        val OTHER_PRINTER = PRESETS.copy(selection = SlicingProfileSelection(ProfileId("other printer"), ProfileId("filament"), ProfileId("other process")))
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
