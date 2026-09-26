package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PlateRequest
import app.orcinus.shadow.core.model.EnginePlate
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.ProjectPrompt
import app.orcinus.shadow.core.model.ImportBatch
import app.orcinus.shadow.core.model.PlateProject
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.LoadedProject
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingsTab
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.PlateHistory
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushVolumes
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelImportFailureCode
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.LayerGcodeRules
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.storage.api.DocumentFolders
import app.orcinus.shadow.core.model.withVolume
import app.orcinus.shadow.core.model.SimplifyOutcome
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.core.model.flushesInto
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.MeshExportOutcome
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.LoadedObject
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.ObjectVolume
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintedMesh
import app.orcinus.shadow.core.model.PaintedSurface
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.model.PlacedInstance
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.PresetTransfer
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabOutcome
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
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.WipeTowerOutcome
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withInstance
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.PlaceModelUseCase
import app.orcinus.shadow.domain.PlaceModelsUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import app.orcinus.shadow.storage.api.CachedPlate
import app.orcinus.shadow.storage.api.ConfigFiles
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.ModelFileImporter
import app.orcinus.shadow.storage.api.PlateCache
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
import kotlinx.coroutines.launch
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
    fun `slice all slices the plates with something to print in turn, passing over an empty one`() {
        val third = PlateObject.CalibrationCube(listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/third.mesh")))))
        val onPlate = mapOf(CUBE.mesh to 0, third.mesh to 2)
        val repository = FakeRepository(
            readyState(CUBE, third).copy(plates = List(3) { PartPlate() }, enginePlate = EnginePlate(0, 3)).judgedOn(0, onPlate),
        )
        // The engine learns every plate that becomes current and judges the copies against it (EnginePlateSync).
        val engineSync = scope.launch {
            repository.state.collect { state ->
                val plate = EnginePlate(state.currentPlate, state.plates.size)
                if (state.enginePlate != plate) repository.update { it.copy(enginePlate = plate).judgedOn(plate.index, onPlate) }
            }
        }

        SliceAllPlatesUseCase(slicePlate(FakeEngine(), repository), repository, scope)()
        engineSync.cancel()

        val state = repository.state.value
        assertFalse(state.slicingAll)
        assertEquals(2, state.currentPlate)
        assertEquals(listOf(true, false, true), state.partPlates().map { it.result != null })
    }

    @Test
    fun `the G-code carries the thumbnails the printer asks for, rendered from the plate`() {
        val repository = FakeRepository(readyState(CUBE).copy(plate = PLATE))
        val sizes = listOf(ThumbnailSize(300, 300), ThumbnailSize(96, 96))
        val engine = FakeEngine(thumbnails = ThumbnailSizesOutcome.Success(sizes))
        var rendered: Triple<List<PlateObject>, List<String>, List<ThumbnailSize>>? = null
        val renderer = PlateThumbnailRenderer { objects, _, _, colors, asked, _, fileFor ->
            rendered = Triple(objects, colors, asked)
            asked.map { ThumbnailImage(it, fileFor(it)) }
        }

        slicePlate(engine, repository, thumbnails = renderer)()

        assertEquals(Triple(listOf(CUBE), PRESETS.filamentColors, sizes), rendered)
        assertEquals(
            listOf(
                ThumbnailImage(ThumbnailSize(300, 300), ScenePath("/scene/toolpaths/0.toolpaths.300x300.rgba")),
                ThumbnailImage(ThumbnailSize(96, 96), ScenePath("/scene/toolpaths/0.toolpaths.96x96.rgba")),
            ),
            engine.request?.thumbnails,
        )
    }

    @Test
    fun `a plate whose thumbnails cannot be rendered is sliced without them`() {
        val repository = FakeRepository(readyState(CUBE).copy(plate = PLATE))
        val engine = FakeEngine(thumbnails = ThumbnailSizesOutcome.Success(listOf(ThumbnailSize(300, 300))))

        slicePlate(engine, repository, thumbnails = PlateThumbnailRenderer { _, _, _, _, _, _, _ -> error("no GL context") })()

        assertEquals(emptyList(), engine.request?.thumbnails)
        assertEquals(STATISTICS, repository.state.value.result?.statistics)
    }

    @Test
    fun `an imported model is sliced from its stored file into a G-code named after the document`() {
        val model = PlateObject.ImportedModel(ImportedModelFile(ModelPath("/imports/benchy.stl"), "benchy.stl"), listOf(PlateInstance(INSPECTION)))
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
            listOf(
                PlateInstance(
                    INSPECTION.copy(
                        mesh = ScenePath("/scene/objects/benchy.mesh"),
                        placement = translated(-100.0, 175.0, 10.0),
                        fit = BuildVolumeFit.OUTSIDE,
                    ),
                    autoDrop = false,
                ),
            ),
        )
        val cube = CUBE.withInspection(INSPECTION.copy(placement = translated(100.0, 120.0, 10.0)))
        val repository = FakeRepository(readyState(benchy, cube))
        val engine = FakeEngine()

        slicePlate(engine, repository)()

        val objects = checkNotNull(engine.request).objects
        assertEquals(
            listOf(
                PlacedModel(
                    ModelSource.LocalFile(ModelPath("/imports/benchy.stl")),
                    benchy.inspection.mesh,
                    listOf(PlacedInstance(translated(-100.0, 175.0, 10.0), autoDrop = false)),
                    name = "benchy.stl",
                ),
                PlacedModel(
                    ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM),
                    cube.inspection.mesh,
                    listOf(PlacedInstance(translated(100.0, 120.0, 10.0))),
                ),
            ),
            objects,
        )
        assertEquals(OutputPath("/gcode/calibration-cube-20mm.gcode"), engine.request?.output)
        assertEquals(listOf(benchy, cube), repository.state.value.result?.objects)
    }

    @Test
    fun `a plate is sliced when an object is on it, even with another entirely off it`() {
        val off = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/off.mesh"), fit = BuildVolumeFit.OUTSIDE))

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
        assertEquals(dropped, engine.request?.objects?.single()?.instances?.single()?.placement)
        assertEquals(dropped, repository.state.value.objects.single().inspection.placement)
        assertFalse(repository.state.value.objects.single().placing)
    }

    @Test
    fun `a manipulation of one object leaves the others as they are`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh"), placement = translated(250.0, 175.0, 10.0)))
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
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
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
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val settings = ArrangeSettings(distance = 6.0)
        val inspector = FakeInspector(
            placedObjects = { plate ->
                plate.mapIndexed { index, placed ->
                    listOf(INSPECTION.copy(mesh = placed.mesh, placement = translated(150.0 + 50.0 * index, 175.0, 10.0)))
                }
            },
        )
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
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val inspector = FakeInspector(placedObjects = null)
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObjects(inspector, repository)(PlateManipulation.AutoOrient(setOf(other.inspection.mesh)))

        assertEquals(listOf(false, true), repository.state.value.objects.map(PlateObject::placing))
        assertEquals(2, inspector.plate.size)
    }

    @Test
    fun `auto orient with nothing selected places every object`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val inspector = FakeInspector(placedObjects = null)
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObjects(inspector, repository)(PlateManipulation.AutoOrient())

        assertEquals(listOf(true, true), repository.state.value.objects.map(PlateObject::placing))
    }

    @Test
    fun `an object moved while the plate is arranged keeps the placement the user gave it`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val answer = CompletableDeferred<PlateInspectionOutcome>()
        val inspector = FakeInspector(arranged = answer)
        val repository = FakeRepository(readyState(CUBE, other))

        placePlateObjects(inspector, repository)(PlateManipulation.Arrange(ArrangeSettings()))
        placePlateObject(inspector, repository)(INSPECTION.mesh, translated(60.0, 60.0, 10.0))
        answer.complete(
            PlateInspectionOutcome.Success(
                listOf(
                    listOf(INSPECTION.copy(placement = translated(150.0, 175.0, 10.0))),
                    listOf(other.inspection.copy(placement = translated(200.0, 175.0, 10.0))),
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
    fun `deleting an object takes it off the plate, keeps it for Undo and drops the G-code`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val repository = FakeRepository(readyState(CUBE, other).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE, other), OutputPath("/gcode/old.gcode"), STATISTICS)))

        DeletePlateObjectUseCase(repository)(CUBE.inspection.mesh)

        assertEquals(listOf(other), repository.state.value.objects)
        assertNull(repository.state.value.result)
        assertEquals(listOf(CUBE, other), repository.state.value.history.undo.single().objects)
    }

    @Test
    fun `an object the list marks as not printable stays on the plate, is not sliced, and drops the G-code`() {
        val repository = FakeRepository(
            readyState(CUBE).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)),
        )

        SetPlateObjectPrintableUseCase(repository)(PlateInstanceId(CUBE.inspection.mesh), printable = false)

        val state = repository.state.value
        assertEquals(1, state.objects.size)
        assertFalse(state.objects.single().printable)
        assertNull(state.result)
        // GLCanvas3D::reload_scene(): nothing printable is nothing to slice.
        assertFalse(state.canSlice)
    }

    @Test
    fun `deleting the selected object leaves the plate selected`() {
        val repository = FakeRepository(readyState(CUBE).copy(selectedInstances = setOf(PlateInstanceId(CUBE.inspection.mesh))))

        DeletePlateObjectUseCase(repository)(CUBE.inspection.mesh)

        assertTrue(repository.state.value.selectedInstances.isEmpty())
    }

    @Test
    fun `nothing is deleted while slicing`() {
        val repository = FakeRepository(readyState(CUBE).copy(slicing = PlateSlicing(SliceJobId("job"))))

        DeletePlateObjectUseCase(repository)(CUBE.inspection.mesh)

        assertEquals(listOf(CUBE), repository.state.value.objects)
        assertTrue(repository.state.value.history.undo.isEmpty())
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
        val file = ImportedModelFile(ModelPath("/imports/a.step"), "a.step")
        val inspector = FakeInspector()
        val files = FakeSceneFiles()

        addModel(repository, ModelImportOutcome.Success(file), inspector, files)(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(2, state.objects.size)
        assertEquals(CUBE, state.objects.first())
        val placed = state.objects.last() as PlateObject.ImportedModel
        // The object is loaded from the mesh the engine wrote, not from the document.
        assertEquals(ImportedModelFile(LOADED.source, LOADED.name), placed.file)
        assertEquals(LOADED.frame, placed.frame)
        assertEquals(LOADED.parts, placed.parts)
        assertEquals(LOADED.volume, placed.volume)
        assertEquals(LOADED.instances, placed.instances)
        assertEquals(file.path, inspector.loads.single().source)
        assertEquals(files.importPrefixes.single(), inspector.loads.single().prefix)
        assertEquals(PROFILES, inspector.profiles)
        assertEquals(
            listOf(
                PlacedModel(
                    ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM),
                    CUBE.inspection.mesh,
                    listOf(PlacedInstance(CUBE.inspection.placement)),
                ),
            ),
            inspector.plate,
        )
        assertTrue(files.deletedImports.isEmpty())
        assertNull(state.result)
    }

    @Test
    fun `a 3MF file asks how it opens on a plate with objects, and a project takes the plate's place`() {
        val repository = FakeRepository(readyState(CUBE).let { it.copy(history = PlateHistory(undo = listOf(it.snapshot()))) })
        val file = ImportedModelFile(ModelPath("/imports/p.3mf"), "p.3mf")
        val inspector = FakeInspector()
        val first = ProjectPlate(settings = ModelSettings(mapOf("curr_bed_type" to "Textured PEI Plate")), layerGcodes = listOf(LayerGcode(2.0, LayerGcodeType.PAUSE_PRINT)))
        val second = ProjectPlate(name = "Brackets", locked = true, layerGcodes = listOf(LayerGcode(3.0, LayerGcodeType.PAUSE_PRINT)))
        val project = LoadedProject(listOf(first, second))
        inspector.load = { ModelLoadOutcome.Success(listOf(LOADED), emptyList(), project = project, presetsChanged = true) }
        val addModel = addModel(repository, ModelImportOutcome.Success(file), inspector, FakeSceneFiles())

        addModel(REFERENCE)
        // ProjectDropDialog waits, and the file is not loaded yet.
        assertEquals(file.path, repository.state.value.projectDrop)
        assertTrue(inspector.loads.isEmpty())

        addModel.openAs(ModelLoad.PROJECT)

        val load = inspector.loads.single()
        assertEquals(ModelLoad.PROJECT, load.load)
        assertTrue(load.chosen)
        // Plater::load_project() resets the plate first.
        assertTrue(inspector.plate.isEmpty())
        val state = repository.state.value
        assertNull(state.projectDrop)
        assertFalse(state.importing)
        assertEquals(1, state.objects.size)
        // Its plates, the first one current.
        assertEquals(listOf("", "Brackets"), state.plates.map(PartPlate::name))
        assertEquals(listOf(false, true), state.plates.map(PartPlate::locked))
        assertEquals(0, state.currentPlate)
        assertEquals(first.settings, state.plateSettings)
        assertEquals(first.layerGcodes, state.layerGcodes)
        assertEquals(second.layerGcodes, state.plates[1].layerGcodes)
        assertTrue(state.history.undo.isEmpty())

        // On an empty plate the project opens without the question; Cancel loads nothing.
        val empty = FakeRepository(readyState())
        val emptyInspector = FakeInspector()
        addModel(empty, ModelImportOutcome.Success(file), emptyInspector, FakeSceneFiles())(REFERENCE)
        assertEquals(ModelLoad.PROJECT, emptyInspector.loads.single().load)
        assertFalse(emptyInspector.loads.single().chosen)

        val cancelled = FakeRepository(readyState(CUBE))
        val cancelledInspector = FakeInspector()
        val cancelling = addModel(cancelled, ModelImportOutcome.Success(file), cancelledInspector, FakeSceneFiles())
        cancelling(REFERENCE)
        cancelling.openAs(null)
        assertTrue(cancelledInspector.loads.isEmpty())
        assertFalse(cancelled.state.value.importing)
        assertEquals(listOf(CUBE), cancelled.state.value.objects)
    }

    @Test
    fun `a saved project goes by its document's name, and Save writes that document again`() {
        val repository = FakeRepository(readyState(CUBE).copy(layerGcodes = listOf(LayerGcode(2.0, LayerGcodeType.PAUSE_PRINT))))
        val inspector = FakeInspector()
        var saved: List<LayerGcode>? = null
        inspector.saveProject = { _, _, plates -> saved = plates.single().layerGcodes; ProjectSaveOutcome.Success }
        val documents = FakeDocuments(name = "Box.3mf")
        val save = SaveProjectUseCase(inspector, { _, _, _, _, _, _, _ -> emptyList() }, FakeSceneFiles(), documents, repository, scope)
        val document = ExternalDocumentReference("content://documents/box")

        assertTrue(save.needsDocument)
        assertTrue(repository.state.value.projectDirty)
        save(document)

        assertEquals("Box", repository.state.value.project.name)
        assertEquals(document, repository.state.value.project.document)
        // What was saved is the project now.
        assertFalse(repository.state.value.projectDirty)
        assertEquals(listOf(LayerGcode(2.0, LayerGcodeType.PAUSE_PRINT)), saved)
        assertFalse(save.needsDocument)
        save()
        assertEquals(listOf(document, document), documents.copied.map { it.second })

        // A document that cannot be written gets OrcaSlicer's message box, and the project keeps its name.
        val failing = SaveProjectUseCase(inspector, { _, _, _, _, _, _, _ -> emptyList() }, FakeSceneFiles(), FakeDocuments(succeeds = false), repository, scope)
        failing(ExternalDocumentReference("content://documents/other"))
        assertEquals("save_project_failed", repository.state.value.plateNotices.single().id)
        assertEquals("Box", repository.state.value.project.name)
        assertEquals(document, repository.state.value.project.document)
    }

    @Test
    fun `a new project asks to save a changed plate first, and starts afresh unless cancelled`() {
        fun lifecycle(repository: FakeRepository, inspector: FakeInspector, documents: FakeDocuments) = ProjectLifecycleUseCase(
            repository,
            SaveProjectUseCase(inspector, { _, _, _, _, _, _, _ -> emptyList() }, FakeSceneFiles(), documents, repository, scope),
            FakePresetManager(),
            NoSettingsEditor,
            PresetsApplier { _, _ -> },
            scope,
        )
        val changed = readyState(CUBE).let { it.copy(history = PlateHistory(undo = listOf(it.snapshot()))) }

        // Cancel: the plate stays.
        val cancelled = FakeRepository(changed)
        val cancelling = lifecycle(cancelled, FakeInspector(), FakeDocuments())
        assertTrue(cancelled.state.value.projectDirty)
        cancelling.newProject()
        assertEquals(ProjectPrompt.SaveChanges, cancelled.state.value.projectPrompt)
        cancelling.answerSaveChanges(null)
        assertEquals(listOf(CUBE), cancelled.state.value.objects)
        assertNull(cancelled.state.value.projectPrompt)

        // No: a new, untitled project.
        val dropped = FakeRepository(changed)
        lifecycle(dropped, FakeInspector(), FakeDocuments()).run {
            newProject()
            answerSaveChanges(false)
        }
        val fresh = dropped.state.value
        assertTrue(fresh.objects.isEmpty())
        assertEquals(PlateHistory(), fresh.history)
        assertNull(fresh.project.name)
        assertFalse(fresh.projectDirty)

        // Yes: Save Project asks for a document, saves, and the new project follows.
        val saving = FakeRepository(changed)
        val inspector = FakeInspector()
        var saved = false
        inspector.saveProject = { _, _, _ -> saved = true; ProjectSaveOutcome.Success }
        val documents = FakeDocuments(name = "Box.3mf")
        lifecycle(saving, inspector, documents).run {
            newProject()
            answerSaveChanges(true)
            assertEquals(ProjectPrompt.SaveAs, saving.state.value.projectPrompt)
            saveTo(ExternalDocumentReference("content://documents/box"))
        }
        assertTrue(saved)
        assertEquals(1, documents.copied.size)
        assertTrue(saving.state.value.objects.isEmpty())
    }

    @Test
    fun `a question of the load waits on the plate, and the load goes on with the answer`() {
        val repository = FakeRepository(readyState(CUBE))
        val file = ImportedModelFile(ModelPath("/imports/stacked.amf"), "stacked.amf")
        val inspector = FakeInspector()
        inspector.load = { answers ->
            if ("multipart_object" in answers) {
                ModelLoadOutcome.Success(listOf(LOADED), listOf(NOTICE))
            } else {
                ModelLoadOutcome.Question(QUESTION, listOf(NOTICE))
            }
        }
        val files = FakeSceneFiles()
        val addModel = addModel(repository, ModelImportOutcome.Success(file), inspector, files)

        addModel(REFERENCE)

        val asked = repository.state.value
        assertTrue(asked.importing)
        assertEquals(QUESTION, asked.plateQuestion?.question)
        assertEquals(PlateRequest.Import(file.path, ImportBatch(document = REFERENCE, displayName = "stacked.amf")), asked.plateQuestion?.request)
        assertEquals(listOf(NOTICE), asked.plateNotices)
        assertEquals(listOf(CUBE), asked.objects)
        // Nothing the load wrote before it asked stays.
        assertEquals(files.importPrefixes, files.deletedImports)

        DismissPlateNoticeUseCase(repository)()
        addModel.answer(true)

        val loaded = repository.state.value
        assertEquals(mapOf("multipart_object" to true), inspector.loads.last().answers)
        assertEquals(file.path, inspector.loads.last().source)
        assertFalse(loaded.importing)
        assertNull(loaded.plateQuestion)
        assertEquals(2, loaded.objects.size)
        // The message box the first load showed does not show again.
        assertTrue(loaded.plateNotices.isEmpty())
    }

    @Test
    fun `an edited object takes the place of the one it was, and its old meshes go`() {
        val other = CUBE.copy(instances = listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))))
        val repository = FakeRepository(readyState(CUBE, other))
        val inspector = FakeInspector()
        val files = FakeSceneFiles()

        EditPlateObjectUseCase(inspector, files, repository, scope)(CUBE.mesh, ObjectEdit.SPLIT_TO_PARTS)

        val state = repository.state.value
        assertFalse(state.editing)
        assertEquals(FakeInspector.Edit(0, ObjectEdit.SPLIT_TO_PARTS, null, emptyMap()), inspector.edits.single())
        assertEquals(listOf(LOADED.instances.single().inspection.mesh, other.mesh), state.objects.map { it.mesh })
        assertEquals(setOf(PlateInstanceId(LOADED.instances.single().inspection.mesh)), state.selectedInstances)
        // The object before the edit waits for Undo.
        assertEquals(listOf(CUBE, other), state.history.undo.single().objects)
    }

    @Test
    fun `objects an edit loads anew join the end of the plate, and the edited one leaves it`() {
        val first = LOADED.copy(instances = listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/split-0.mesh")))))
        val second = LOADED.copy(instances = listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/split-1.mesh")))))
        val other = CUBE.copy(instances = listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))))
        val repository = FakeRepository(readyState(CUBE, other))
        val inspector = FakeInspector()
        inspector.editOutcome = { answers ->
            if ("split_auto_drop" in answers) {
                ModelLoadOutcome.Success(listOf(first, second), emptyList(), appended = true)
            } else {
                ModelLoadOutcome.Question(QUESTION.copy(id = "split_auto_drop"), emptyList())
            }
        }

        EditPlateObjectUseCase(inspector, FakeSceneFiles(), repository, scope).let { edit ->
            edit(CUBE.mesh, ObjectEdit.SPLIT_TO_OBJECTS)
            val asked = repository.state.value
            assertTrue(asked.busy)
            assertEquals(PlateRequest.Edit(CUBE.mesh, ObjectEdit.SPLIT_TO_OBJECTS), asked.plateQuestion?.request)

            AnswerPlateQuestionUseCase(repository, addModel(repository, ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, ""), inspector, FakeSceneFiles()), edit, settingsTabs(repository))(false)
        }

        val state = repository.state.value
        assertEquals(mapOf("split_auto_drop" to false), inspector.edits.last().answers)
        assertFalse(state.busy)
        assertNull(state.plateQuestion)
        assertEquals(listOf(other.mesh, first.instances.single().inspection.mesh, second.instances.single().inspection.mesh), state.objects.map { it.mesh })
        assertEquals(2, state.selectedInstances.size)
    }

    @Test
    fun `an edit that changes nothing leaves the plate and says why`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        inspector.editOutcome = { ModelLoadOutcome.Success(emptyList(), listOf(NOTICE)) }

        EditPlateObjectUseCase(inspector, FakeSceneFiles(), repository, scope)(CUBE.mesh, ObjectEdit.SPLIT_TO_OBJECTS)

        val state = repository.state.value
        assertFalse(state.editing)
        assertEquals(listOf(CUBE), state.objects)
        assertEquals(listOf(NOTICE), state.plateNotices)
    }

    @Test
    fun `a clone pastes new objects after the plate's, selects the last paste and arranges the plate`() {
        val settings = ArrangeSettings(distance = 4.0)
        val repository = FakeRepository(readyState(CUBE).copy(arrangeSettings = settings))
        val inspector = FakeInspector()

        ClonePlateObjectsUseCase(inspector, FakeSceneFiles(), repository, placePlateObjects(inspector, repository), scope)(
            setOf(PlateInstanceId(CUBE.mesh)),
            count = 2,
            arrange = true,
        )

        val copy = inspector.copies.single()
        assertEquals(listOf(CUBE.mesh), copy.sources.map(PlacedModel::mesh))
        assertEquals(2, copy.count)
        assertEquals(CopyPlacement.PASTE, copy.placement)
        val state = repository.state.value
        assertFalse(state.busy)
        assertEquals(listOf(CUBE.mesh, ScenePath("/scene/objects/copy-0.mesh"), ScenePath("/scene/objects/copy-1.mesh")), state.objects.map { it.mesh })
        assertEquals(setOf(PlateInstanceId(ScenePath("/scene/objects/copy-1.mesh"))), state.selectedInstances)
        // The copies of the cube name their G-code as the cube does.
        assertEquals("calibration-cube-20mm", (state.objects.last() as PlateObject.ImportedModel).inputName)
        assertEquals(PlateManipulation.ArrangePlate(settings), inspector.plateManipulation)
    }

    @Test
    fun `a clone of one copy copies that copy alone and leaves the plate unarranged when asked`() {
        val twoCopies = CUBE.copy(instances = listOf(PlateInstance(INSPECTION), PlateInstance(INSPECTION.copy(placement = translated(40.0, 0.0, 0.0)))))
        val repository = FakeRepository(readyState(twoCopies))
        val inspector = FakeInspector()

        ClonePlateObjectsUseCase(inspector, FakeSceneFiles(), repository, placePlateObjects(inspector, repository), scope)(
            setOf(PlateInstanceId(CUBE.mesh, 1)),
            count = 1,
            arrange = false,
        )

        assertEquals(listOf(translated(40.0, 0.0, 0.0)), inspector.copies.single().sources.single().instances.map { it.placement })
        assertNull(inspector.plateManipulation)
        assertEquals(2, repository.state.value.objects.size)
    }

    @Test
    fun `setting every copy as an object makes an object of each but the first, the last first`() {
        val copies = List(3) { PlateInstance(INSPECTION.copy(placement = translated(40.0 * it, 0.0, 0.0))) }
        val repository = FakeRepository(readyState(CUBE.copy(instances = copies)))
        val inspector = FakeInspector()

        SeparatePlateInstancesUseCase(inspector, FakeSceneFiles(), repository, scope)(CUBE.mesh, setOf(0, 1, 2))

        val copy = inspector.copies.single()
        assertEquals(CopyPlacement.KEEP, copy.placement)
        assertEquals(listOf(listOf(translated(80.0, 0.0, 0.0)), listOf(translated(40.0, 0.0, 0.0))), copy.sources.map { it.instances.map { i -> i.placement } })
        val state = repository.state.value
        assertEquals(listOf(copies.first()), state.objects.first().instances)
        assertEquals(3, state.objects.size)
        assertEquals(setOf(PlateInstanceId(CUBE.mesh)), state.selectedInstances)
    }

    @Test
    fun `setting a copy as an object takes it out of its object`() {
        val copies = List(3) { PlateInstance(INSPECTION.copy(placement = translated(40.0 * it, 0.0, 0.0))) }
        val repository = FakeRepository(readyState(CUBE.copy(instances = copies)))
        val inspector = FakeInspector()

        SeparatePlateInstancesUseCase(inspector, FakeSceneFiles(), repository, scope)(CUBE.mesh, setOf(1))

        assertEquals(listOf(translated(40.0, 0.0, 0.0)), inspector.copies.single().sources.single().instances.map { it.placement })
        val state = repository.state.value
        assertEquals(listOf(copies[0], copies[2]), state.objects.first().instances)
        assertEquals(2, state.objects.size)
    }

    @Test
    fun `an object of one copy is not set apart`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()

        SeparatePlateInstancesUseCase(inspector, FakeSceneFiles(), repository, scope)(CUBE.mesh, setOf(0))

        assertTrue(inspector.copies.isEmpty())
        assertEquals(listOf(CUBE), repository.state.value.objects)
    }

    @Test
    fun `filling the bed adds the copies the job packed onto the plate`() {
        val settings = ArrangeSettings(distance = 2.0)
        val inspector = FakeInspector(
            placedObjects = { plate ->
                plate.map { placed -> List(4) { INSPECTION.copy(mesh = placed.mesh, placement = translated(40.0 * it, 0.0, 0.0)) } }
            },
        )
        val repository = FakeRepository(readyState(CUBE).copy(arrangeSettings = settings))

        FillBedWithInstancesUseCase(repository, placePlateObjects(inspector, repository))(CUBE.mesh, 0)

        assertEquals(PlateManipulation.FillBed(CUBE.mesh, 0, settings), inspector.plateManipulation)
        val instances = repository.state.value.objects.single().instances
        assertEquals(List(4) { translated(40.0 * it, 0.0, 0.0) }, instances.map { it.inspection.placement })
        assertTrue(instances.all { it.printable && it.autoDrop && !it.placing })
    }

    @Test
    fun `copied objects wait on the clipboard and are pasted beside them`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        val files = FakeSceneFiles()
        val copy = CopyToClipboardUseCase(
            inspector,
            files,
            repository,
            RemoveObjectPartUseCase(repository),
            scope,
        )

        copy.objects(setOf(PlateInstanceId(CUBE.mesh)))

        assertEquals(CopyPlacement.KEEP, inspector.copies.single().placement)
        val clipboard = repository.state.value.clipboard as PlateClipboard.Objects
        assertEquals(listOf(ScenePath("/scene/objects/copy-0.mesh")), clipboard.objects.map { it.mesh })
        // Copying leaves the plate and its G-code as they are.
        assertEquals(listOf(CUBE), repository.state.value.objects)

        PasteFromClipboardUseCase(inspector, files, repository, scope)()

        val paste = inspector.copies.last()
        assertEquals(CopyPlacement.PASTE, paste.placement)
        assertEquals(listOf(ScenePath("/scene/objects/copy-0.mesh")), paste.sources.map(PlacedModel::mesh))
        val state = repository.state.value
        assertEquals(2, state.objects.size)
        assertEquals(listOf(state.objects.last()).allCopies(), state.selectedInstances)
        // Copying is no step of Undo; pasting is.
        assertEquals(listOf(listOf(CUBE)), state.history.undo.map { it.objects })
    }

    @Test
    fun `cut takes the copies off the plate once the clipboard holds them`() {
        val twoCopies = CUBE.copy(instances = listOf(PlateInstance(INSPECTION), PlateInstance(INSPECTION.copy(placement = translated(40.0, 0.0, 0.0)))))
        val repository = FakeRepository(readyState(twoCopies))
        val files = FakeSceneFiles()
        val copy = CopyToClipboardUseCase(
            FakeInspector(),
            files,
            repository,
            RemoveObjectPartUseCase(repository),
            scope,
        )

        copy.objects(setOf(PlateInstanceId(CUBE.mesh, 1)), cut = true)

        assertEquals(listOf(PlateInstance(INSPECTION)), repository.state.value.objects.single().instances)

        copy.objects(setOf(PlateInstanceId(CUBE.mesh, 0)), cut = true)

        assertTrue(repository.state.value.objects.isEmpty())
        assertTrue(repository.state.value.clipboard is PlateClipboard.Objects)
    }

    @Test
    fun `copied volumes join the object they are pasted into and are selected`() {
        val withPart = LOADED.toPlateObject("model.stl")
        val repository = FakeRepository(readyState(withPart))
        val inspector = FakeInspector()
        val files = FakeSceneFiles()
        val copy = CopyToClipboardUseCase(
            inspector,
            files,
            repository,
            RemoveObjectPartUseCase(repository),
            scope,
        )
        val target = PlateInstanceId(withPart.mesh)

        copy.volumes(target, setOf(1))
        val clipboard = repository.state.value.clipboard as PlateClipboard.Volumes
        assertEquals(listOf(1), clipboard.volumes)

        PasteFromClipboardUseCase(inspector, files, repository, scope)(target)

        val paste = inspector.pasted!!
        assertEquals(0, paste.index)
        assertEquals(0, paste.instance)
        assertEquals(listOf(1), paste.volumes)
        assertTrue(paste.sameInputFile)
        val state = repository.state.value
        assertEquals(ObjectPartId(LOADED.instances.single().inspection.mesh, 1), state.selectedPart)
        assertFalse(state.editing)
    }

    @Test
    fun `volumes are not pasted without an object to join`() {
        val repository = FakeRepository(readyState(CUBE).copy(clipboard = PlateClipboard.Volumes(CUBE, listOf(0))))
        val inspector = FakeInspector()

        PasteFromClipboardUseCase(inspector, FakeSceneFiles(), repository, scope)()

        assertNull(inspector.pasted)
        assertEquals(listOf(CUBE), repository.state.value.objects)
    }

    @Test
    fun `the calibration cube written anew keeps the name the app gives it`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        inspector.editOutcome = { ModelLoadOutcome.Success(listOf(LOADED.copy(name = "calibration-cube-20mm")), emptyList()) }

        EditPlateObjectUseCase(inspector, FakeSceneFiles(), repository, scope)(CUBE.mesh, ObjectEdit.FIX)

        val fixed = repository.state.value.objects.single() as PlateObject.ImportedModel
        // No name of its own: the app names it as the cube, and the engine keeps calling it its own way.
        assertEquals("", fixed.file.displayName)
        assertEquals("calibration-cube-20mm", fixed.inputName)
        assertEquals("", fixed.placed().name)
    }

    @Test
    fun `undo brings a deleted object back with its selection, and redo takes it away again`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val selected = setOf(PlateInstanceId(CUBE.mesh))
        val repository = FakeRepository(readyState(CUBE, other).copy(selectedInstances = selected))
        val inspector = FakeInspector()
        val history = UndoRedoPlateUseCase(repository, placePlateObjects(inspector, repository), settingsTabs(repository), scope)

        DeletePlateObjectUseCase(repository)(CUBE.mesh)
        assertTrue(repository.state.value.canUndo)
        assertFalse(repository.state.value.canRedo)

        history.undo()

        var state = repository.state.value
        assertEquals(listOf(CUBE.mesh, other.mesh), state.objects.map { it.mesh })
        assertEquals(selected, state.selectedInstances)
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
        // Plater::priv::undo_redo_to() judges the copies against the build volume again.
        assertEquals(PlateManipulation.UpdatePrintVolume, inspector.plateManipulation)

        history.redo()

        state = repository.state.value
        assertEquals(listOf(other.mesh), state.objects.map { it.mesh })
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun `a new action after Undo drops what Undo left`() {
        val repository = FakeRepository(readyState(CUBE))
        val history = UndoRedoPlateUseCase(repository, placePlateObjects(FakeInspector(), repository), settingsTabs(repository), scope)

        RenamePlateItemUseCase(repository)(CUBE.mesh, "first")
        history.undo()
        assertTrue(repository.state.value.canRedo)

        RenamePlateItemUseCase(repository)(CUBE.mesh, "second")

        val state = repository.state.value
        assertFalse(state.canRedo)
        assertEquals(1, state.history.undo.size)
        assertEquals("second", (state.objects.single() as PlateObject.CalibrationCube).name)
    }

    @Test
    fun `judging the fit for another printer is no step of Undo, arranging is`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()

        placePlateObjects(inspector, repository)(PlateManipulation.UpdatePrintVolume)
        assertTrue(repository.state.value.history.undo.isEmpty())

        placePlateObjects(inspector, repository)(PlateManipulation.Arrange(ArrangeSettings()))
        assertEquals(1, repository.state.value.history.undo.size)
    }

    @Test
    fun `adding a copy is one step of Undo, the placing of the copy included`() {
        val repository = FakeRepository(readyState(CUBE).copy(plate = PLATE))
        val inspector = FakeInspector()
        val place = PlacePlateObjectUseCase(PlaceModelUseCase(inspector), repository, scope)

        AddPlateInstanceUseCase(repository, place, SelectPlateObjectUseCase(repository))(CUBE.mesh)

        val state = repository.state.value
        assertEquals(2, state.objects.single().instances.size)
        assertEquals(listOf(listOf(CUBE)), state.history.undo.map { it.objects })
    }

    @Test
    fun `undo leaves the flushing volumes of the project as they are`() {
        val repository = FakeRepository(readyState(CUBE))
        val history = UndoRedoPlateUseCase(repository, placePlateObjects(FakeInspector(), repository), settingsTabs(repository), scope)

        RenamePlateItemUseCase(repository)(CUBE.mesh, "renamed")
        SetFlushVolumesUseCase(repository)(listOf(0.0, 100.0, 100.0, 0.0), listOf(1.0))
        history.undo()

        val state = repository.state.value
        assertNull((state.objects.single() as PlateObject.CalibrationCube).name)
        assertEquals("0.00,100.00,100.00,0.00", state.plateSettings.values["flush_volumes_matrix"])
    }

    @Test
    fun `a mesh goes once neither the plate, its history nor the clipboard refers to it`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val repository = FakeRepository(readyState(CUBE, other))
        val files = FakeSceneFiles()
        val job = scope.launch { ObjectMeshRetention(repository, files).run() }
        val history = UndoRedoPlateUseCase(repository, placePlateObjects(FakeInspector(), repository), settingsTabs(repository), scope)

        DeletePlateObjectUseCase(repository)(other.mesh)
        // Undo can still bring it back.
        assertTrue(files.deleted.isEmpty())

        history.undo()
        history.redo()
        assertTrue(files.deleted.isEmpty())

        // The history forgets the deleted object once a new action drops what follows the active state:
        // undo the deletion, then act otherwise.
        history.undo()
        RenamePlateItemUseCase(repository)(CUBE.mesh, "kept")
        assertTrue(files.deleted.isEmpty())
        repository.update { it.copy(history = PlateHistory()) }
        DeletePlateObjectUseCase(repository)(other.mesh)
        repository.update { it.copy(history = PlateHistory()) }

        assertEquals(listOf(other.mesh), files.deleted)
        job.cancel()
    }

    @Test
    fun `painting is one step of Undo once the tool closes with the object painted otherwise`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        val paint = PaintObjectUseCase(inspector, FakeSceneFiles(), repository)

        runSuspend { paint.begin(CUBE.mesh) }
        assertFalse(repository.state.value.canUndo)
        runSuspend { paint.stroke(PaintStroke(origin = Vector3(0.0, 0.0, 50.0), direction = Vector3(0.0, 0.0, -1.0), filament = 2)) }
        runSuspend { paint.end() }

        val state = repository.state.value
        assertNull(state.history.beforeTool)
        val before = state.history.undo.single().objects.single()
        assertEquals(CUBE.painted, before.painted)
        assertEquals(PaintedFacets("painted"), state.objects.single().painted)
    }

    @Test
    fun `the painting tool undoes a stroke with its own stack and shows what is left`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        val paint = PaintObjectUseCase(inspector, FakeSceneFiles(), repository)

        runSuspend { paint.begin(CUBE.mesh) }
        runSuspend { paint.stroke(PaintStroke(Vector3(0.0, 0.0, 50.0), Vector3(0.0, 0.0, -1.0), filament = 2, startsStroke = true)) }
        assertEquals(true, inspector.stroke?.startsStroke)
        val undone = runSuspend { paint.undo() }

        assertEquals(1, inspector.undone)
        assertTrue((undone as PaintingOutcome.Success).surface.canRedo)
        assertTrue(repository.state.value.objects.single().paintedMeshes.isEmpty())
    }

    @Test
    fun `a handy model of two files loads both as one step of Undo, selects both and arranges the plate`() {
        val repository = FakeRepository(readyState())
        val inspector = FakeInspector()
        inspector.load = {
            val mesh = ScenePath("/scene/objects/handy-${inspector.loads.size}.mesh")
            ModelLoadOutcome.Success(listOf(LOADED.copy(instances = listOf(PlateInstance(INSPECTION.copy(mesh = mesh))))), emptyList())
        }

        addModel(repository, ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, ""), inspector, FakeSceneFiles()).handy(HandyModel.ORCA_CUBE)

        val state = repository.state.value
        assertEquals(
            listOf(ModelPath("/resources/handy_models/OrcaCube_v2.drc"), ModelPath("/resources/handy_models/OrcaPlug_v2.drc")),
            inspector.loads.map { it.source },
        )
        assertEquals(2, state.objects.size)
        assertEquals(state.objects.allCopies(), state.selectedInstances)
        // "Import Object" once, then "Arrange".
        assertEquals(listOf(0, 2), state.history.undo.map { it.objects.size })
        assertFalse(state.importing)
        assertEquals(PlateManipulation.ArrangePlate(state.arrangeSettings), inspector.plateManipulation)
        assertEquals("OrcaCube_v2.drc", (state.objects.first() as PlateObject.ImportedModel).inputName)
    }

    @Test
    fun `a handy model's 3MF file loads its geometry alone, as load_files() with LoadModel does`() {
        val repository = FakeRepository(readyState())
        val inspector = FakeInspector()

        addModel(repository, ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, ""), inspector, FakeSceneFiles()).handy(HandyModel.ORCASLICED_COMBO)

        assertEquals(ModelPath("/resources/handy_models/OrcaSliced.3mf"), inspector.loads.first().source)
        assertTrue(inspector.loads.all { it.load == ModelLoad.GEOMETRY && !it.chosen })
        assertEquals(3, inspector.loads.size)
        assertFalse(repository.state.value.importing)
    }

    @Test
    fun `Orca String Hell suggests the top surface threshold, and Yes sets it to 0`() {
        val process = PresetSettings(
            kind = PresetKind.PRINT,
            preset = "process",
            label = "process",
            dirty = false,
            isDefault = false,
            isSystem = true,
            hasParent = true,
            canDelete = false,
            mode = SettingsMode.SIMPLE,
            pages = emptyList(),
            activePage = "",
            settings = listOf(
                SettingState("only_one_wall_top", "only_one_wall_top", "1", modified = false, system = true, enabled = true, visible = true),
                SettingState("min_width_top_surface", "min_width_top_surface", "300%", modified = false, system = true, enabled = true, visible = true),
            ),
            saveName = "process",
            saveNameCopySuffix = true,
        )
        val ready = readyState()
        val repository = FakeRepository(ready.copy(settingsTabs = ready.settingsTabs + (PresetKind.PRINT to SettingsTabState(PresetKind.PRINT, tab = SettingsTab(PresetKind.PRINT, emptyMap()), settings = process))))
        val inspector = FakeInspector()
        val editor = ChangeRecordingEditor()
        val add = addModel(repository, ModelImportOutcome.Failure(ModelImportFailureCode.EMPTY_FILE, ""), inspector, FakeSceneFiles())

        add.handy(HandyModel.ORCA_STRING_HELL)

        assertEquals(PlateRequest.TopSurfaceSuggestion, repository.state.value.plateQuestion?.request)
        assertEquals("Suggestion", repository.state.value.plateQuestion?.question?.title?.single()?.msgid)

        val tabs = PresetSettingsTabs(editor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)
        AnswerPlateQuestionUseCase(repository, add, EditPlateObjectUseCase(inspector, FakeSceneFiles(), repository, scope), tabs)(true)

        assertNull(repository.state.value.plateQuestion)
        assertEquals(listOf(Triple(PresetKind.PRINT, "min_width_top_surface", "0")), editor.changes)
    }

    @Test
    fun `a primitive joins the plate as an object named as the shape, selected, one step of Undo`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()

        AddPrimitiveUseCase(inspector, FakeSceneFiles(), repository, scope)("Cylinder", "Цилиндр")

        assertEquals(listOf("Cylinder" to "Цилиндр"), inspector.primitives)
        val state = repository.state.value
        assertEquals(2, state.objects.size)
        assertEquals("Цилиндр", (state.objects.last() as PlateObject.ImportedModel).inputName)
        assertEquals(listOf(state.objects.last()).allCopies(), state.selectedInstances)
        assertEquals(listOf(listOf(CUBE)), state.history.undo.map { it.objects })
        assertFalse(state.importing)
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
        assertTrue(files.importPrefixes.isEmpty())
    }

    @Test
    fun `a model Orca cannot load keeps the plate and leaves no mesh behind`() {
        val repository = FakeRepository(readyState(CUBE))
        val file = ImportedModelFile(ModelPath("/imports/broken.stl"), "broken.stl")
        val files = FakeSceneFiles()
        val inspector = FakeInspector()
        inspector.load = { ModelLoadOutcome.Failure("no facets") }

        addModel(repository, ModelImportOutcome.Success(file), inspector, files)(REFERENCE)

        val state = repository.state.value
        assertFalse(state.importing)
        assertEquals(listOf(CUBE), state.objects)
        assertEquals("no facets", state.problem?.detail)
        assertEquals(files.importPrefixes, files.deletedImports)
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
    fun `the plate of the last run is shown while the engine starts, and described again when it is ready`() {
        val repository = FakeRepository(PlateState())
        val inspector = FakeInspector()
        val cache = FakePlateCache(CachedPlate(PROFILES.printer.value, PLATE))

        runSuspend {
            startEngine(
                FakeEngine(),
                FakePresetManager(),
                inspector,
                repository,
                cache = cache,
                onStarting = {
                    // The engine has not answered yet: the view already has a plate.
                    val starting = repository.state.value
                    assertEquals(PLATE, starting.plate)
                    assertTrue(starting.plateFromCache)
                    assertTrue(inspector.described.isEmpty())
                },
            )()
        }

        val state = repository.state.value
        assertEquals(PLATE, state.plate)
        assertFalse(state.plateFromCache)
        // The engine describes the plate of its own presets, and it is kept for the next run.
        assertEquals(listOf(PROFILES), inspector.described)
        assertEquals(PROFILES.printer.value to PLATE, cache.written)
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
        val inspector = FakeInspector(
            placedObjects = { plate ->
                plate.map { placed -> placed.instances.map { INSPECTION.copy(mesh = placed.mesh, placement = it.placement, fit = BuildVolumeFit.OUTSIDE) } }
            },
        )
        val presets = FakePresetManager(selected = { PresetsOutcome.Success(OTHER_PRINTER) })
        val choice = PresetChoice.PrinterModel("Other Printer")

        SelectPresetUseCase(presets, platePresets(inspector, repository), NO_FLUSH_UPDATES, settingsTabs(repository), repository, scope)(choice)

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

        SelectPresetUseCase(presets, platePresets(inspector, repository), NO_FLUSH_UPDATES, settingsTabs(repository), repository, scope)(PresetChoice.Process(ProfileId("other process")))

        assertEquals(otherProcess, repository.state.value.presets)
        assertTrue(inspector.described.isEmpty())
        assertNull(inspector.plateManipulation)
    }

    @Test
    fun `the diff dialog moves the chosen values into the right presets, and the G-code goes`() {
        val result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)
        val repository = FakeRepository(readyState(CUBE).copy(plate = PLATE, result = result))
        val otherProcess = PRESETS.copy(selection = PROFILES.copy(process = ProfileId("other process")))
        val presets = FakePresetManager(selected = { PresetsOutcome.Success(otherProcess) })

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), NO_FLUSH_UPDATES, settingsTabs(repository), repository, scope).transfer(
            listOf(
                PresetTransfer(PresetKind.PRINTER, "a printer", "a printer", emptyList()),
                PresetTransfer(PresetKind.PRINT, "other process", "a process", listOf("layer_height#0")),
            ),
        )

        // A kind with nothing chosen moves nothing (Tab::transfer_options).
        assertEquals(listOf(PresetTransfer(PresetKind.PRINT, "other process", "a process", listOf("layer_height#0"))), presets.transfers)
        val state = repository.state.value
        assertEquals(otherProcess, state.presets)
        assertFalse(state.changingPresets)
        assertNull(state.result)
    }

    @Test
    fun `a transfer the engine refuses is reported and the presets stay`() {
        val repository = FakeRepository(readyState(CUBE))
        val presets = FakePresetManager(selected = { PresetsOutcome.Failure("You can only transfer to current active profile because it has been modified.") })

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), NO_FLUSH_UPDATES, settingsTabs(repository), repository, scope).transfer(
            listOf(PresetTransfer(PresetKind.PRINT, "a process", "other process", listOf("layer_height"))),
        )

        val state = repository.state.value
        assertEquals(PRESETS, state.presets)
        assertFalse(state.changingPresets)
        assertEquals(PlateProblemKind.PRESETS_FAILED, state.problem?.kind)
    }

    @Test
    fun `a preset the engine cannot select is reported and the selection stays`() {
        val repository = FakeRepository(readyState(CUBE))
        val presets = FakePresetManager(selected = { PresetsOutcome.Failure("Configuration incompatible") })

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), NO_FLUSH_UPDATES, settingsTabs(repository), repository, scope)(PresetChoice.NozzleDiameter("0.6"))

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

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), NO_FLUSH_UPDATES, settingsTabs(repository), repository, scope)(PresetChoice.Filament(ProfileId("PLA")))

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

    @Test
    fun `the height ranges of an object are added where the desktop app adds them`() {
        val repository = FakeRepository(readyState(CUBE))
        val add = AddLayerRangeUseCase(repository)

        // ObjectList::layers_editing(): the first range is 0 to 2 mm.
        val first = add(CUBE.mesh)

        assertEquals(LayerRangeId(CUBE.mesh, 0), first)
        assertEquals(listOf(0.0 to 2.0), repository.state.value.objects.single().layerRanges.map { it.bottom to it.top })

        // add_layer_range_after_current(): after the last one, 2 mm high.
        add(CUBE.mesh, first)

        assertEquals(listOf(0.0 to 2.0, 2.0 to 4.0), repository.state.value.objects.single().layerRanges.map { it.bottom to it.top })

        // A range that touches the next one splits it in half.
        add(CUBE.mesh, LayerRangeId(CUBE.mesh, 0))

        assertEquals(
            listOf(0.0 to 2.0, 2.0 to 3.0, 3.0 to 4.0),
            repository.state.value.objects.single().layerRanges.map { it.bottom to it.top },
        )
    }

    @Test
    fun `a new range fills the gap up to the next one`() {
        val cube = CUBE.copy(layerRanges = listOf(LayerRange(0.0, 2.0), LayerRange(5.0, 7.0)))
        val repository = FakeRepository(readyState(cube))

        AddLayerRangeUseCase(repository)(cube.mesh, LayerRangeId(cube.mesh, 0))

        assertEquals(
            listOf(0.0 to 2.0, 2.0 to 5.0, 5.0 to 7.0),
            repository.state.value.objects.single().layerRanges.map { it.bottom to it.top },
        )
    }

    @Test
    fun `a range spans other heights, keeps its settings, and does not reach into its neighbours`() {
        val settings = ModelSettings(mapOf("layer_height" to "0.1"))
        val cube = CUBE.copy(layerRanges = listOf(LayerRange(0.0, 2.0, settings), LayerRange(4.0, 6.0)))
        val repository = FakeRepository(readyState(cube).copy(selectedRange = LayerRangeId(cube.mesh, 0)))
        val edit = EditLayerRangeUseCase(repository)

        edit(LayerRangeId(cube.mesh, 0), 1.0, 3.0)

        val moved = repository.state.value.objects.single().layerRanges.first()
        assertEquals(1.0 to 3.0, moved.bottom to moved.top)
        assertEquals(settings, moved.settings)

        // ObjectList keeps the ranges apart: a span that overlaps the next one is refused.
        edit(LayerRangeId(cube.mesh, 0), 1.0, 5.0)

        assertEquals(1.0 to 3.0, repository.state.value.objects.single().layerRanges.first().let { it.bottom to it.top })
    }

    @Test
    fun `a removed range takes the selection with it and the G-code no longer applies`() {
        val cube = CUBE.copy(layerRanges = listOf(LayerRange(0.0, 2.0), LayerRange(2.0, 4.0)))
        val repository = FakeRepository(
            readyState(cube).copy(
                selectedRange = LayerRangeId(cube.mesh, 1),
                result = PlateSliceResult(SliceJobId("old"), listOf(cube), OutputPath("/gcode/old.gcode"), STATISTICS),
            ),
        )

        RemoveLayerRangeUseCase(repository)(LayerRangeId(cube.mesh, 1))

        assertEquals(listOf(0.0 to 2.0), repository.state.value.objects.single().layerRanges.map { it.bottom to it.top })
        assertNull(repository.state.value.selectedRange)
        assertNull(repository.state.value.result)
    }

    @Test
    fun `the height ranges of an object reach the slicer with it`() {
        val cube = CUBE.copy(layerRanges = listOf(LayerRange(0.0, 2.0, ModelSettings(mapOf("layer_height" to "0.1")))))
        val repository = FakeRepository(readyState(cube))
        val engine = FakeEngine()

        runSuspend { slicePlate(engine, repository)() }

        assertEquals(cube.layerRanges, engine.request?.objects?.single()?.layerRanges)
    }

    @Test
    fun `an object prints with the filament it is given, and its parts follow it`() {
        val cube = CUBE.copy(
            settings = ModelSettings(mapOf("extruder" to "2")),
            parts = listOf(PART, PART.copy(type = VolumeType.MODIFIER, settings = ModelSettings(mapOf("extruder" to "2")))),
        )
        val repository = FakeRepository(twoFilaments(cube).copy(result = PlateSliceResult(SliceJobId("old"), listOf(cube), OutputPath("/gcode/old.gcode"), STATISTICS)))

        SetExtruderUseCase(repository)(cube.mesh, 1)

        val printed = repository.state.value.objects.single()
        assertEquals("1", printed.settings.values["extruder"])
        // set_extruder_for_selected_items(): the parts of the model lose the
        // filament of their own, and a modifier keeps the one it was given.
        assertEquals(0, printed.parts.first().settings.extruderNumber)
        assertEquals(2, printed.parts.last().settings.extruderNumber)
        assertNull(repository.state.value.result)
    }

    @Test
    fun `a part prints with its own filament, and its default is the one of its object`() {
        val cube = CUBE.copy(settings = ModelSettings(mapOf("extruder" to "2")), parts = listOf(PART))
        val repository = FakeRepository(twoFilaments(cube))
        val set = SetExtruderUseCase(repository)
        val part = ObjectPartId(cube.mesh, 1)

        set(part, 1)

        assertEquals(1, repository.state.value.objects.single().parts.single().settings.extruderNumber)

        // "default" on a part of the model is the filament of its object.
        set(part, 0)

        assertEquals(2, repository.state.value.objects.single().parts.single().settings.extruderNumber)
    }

    @Test
    fun `the object's own mesh has a row of its own once the object has parts`() {
        val cube = CUBE.copy(settings = ModelSettings(mapOf("extruder" to "2")), parts = listOf(PART))
        val repository = FakeRepository(twoFilaments(cube))
        val own = ObjectPartId(cube.mesh, 0)

        SelectObjectPartUseCase(repository)(own)

        assertEquals(own, repository.state.value.selectedPart)
        assertEquals(cube.mesh, repository.state.value.selectedObjectPart?.mesh)

        SetExtruderUseCase(repository)(own, 1)

        assertEquals(1, repository.state.value.objects.single().volume.settings.extruderNumber)

        // set_extruder_for_selected_items(): the object's filament takes the
        // one of its own mesh away, as it does the parts'.
        SetExtruderUseCase(repository)(cube.mesh, 2)

        assertEquals(0, repository.state.value.objects.single().volume.settings.extruderNumber)
    }

    @Test
    fun `an object that is its own mesh alone lists no volume`() {
        val repository = FakeRepository(twoFilaments(CUBE))

        SelectObjectPartUseCase(repository)(ObjectPartId(CUBE.mesh, 0))

        assertNull(repository.state.value.selectedPart)
    }

    @Test
    fun `once its last part goes, the settings of the object's own mesh become the object's`() {
        val cube = CUBE.copy(
            parts = listOf(PART),
            volume = ObjectVolume(settings = ModelSettings(mapOf("extruder" to "2", "wall_loops" to "5"))),
        )
        val repository = FakeRepository(twoFilaments(cube).copy(selectedPart = ObjectPartId(cube.mesh, 0)))
        val files = FakeSceneFiles()

        RemoveObjectPartUseCase(repository)(ObjectPartId(cube.mesh, 1))

        val removed = repository.state.value.objects.single()
        assertTrue(removed.parts.isEmpty())
        // ObjectList::del_subobject_from_object()
        assertEquals(mapOf("extruder" to "2", "wall_loops" to "5"), removed.settings.values)
        assertTrue(removed.volume.settings.values.isEmpty())
        // The row it had is gone with the parts.
        assertNull(repository.state.value.selectedPart)
        // Undo can bring the part back, so its mesh stays.
        assertTrue(PART.mesh in repository.state.value.referencedMeshes())
    }

    @Test
    fun `the object's own mesh stays in the object`() {
        val cube = CUBE.copy(parts = listOf(PART))
        val repository = FakeRepository(twoFilaments(cube))

        RemoveObjectPartUseCase(repository)(ObjectPartId(cube.mesh, 0))

        assertEquals(listOf(PART), repository.state.value.objects.single().parts)
    }

    @Test
    fun `more copies are offset from the last one and the last new copy is selected`() {
        val second = PlateInstance(INSPECTION.copy(placement = moved(INSPECTION.placement, 17.5)))
        val cube = CUBE.copy(instances = CUBE.instances + second)
        val repository = FakeRepository(readyState(cube).copy(plate = PLATE))
        val inspector = FakeInspector()
        val place = placePlateObject(inspector, repository)

        AddPlateInstanceUseCase(repository, place, SelectPlateObjectUseCase(repository))(cube.mesh, 2)

        val copies = repository.state.value.objects.single().instances
        assertEquals(4, copies.size)
        // Plater::increase_instances(): 5% of the 350 mm bed, from the last copy.
        val lastX = second.inspection.placement.columns[12]
        assertEquals(listOf(lastX + 17.5, lastX + 35.0), copies.drop(2).map { it.inspection.placement.columns[12] })
        assertEquals(setOf(PlateInstanceId(cube.mesh, 3)), repository.state.value.selectedInstances)
    }

    @Test
    fun `a copy left out of the print keeps the object from getting more`() {
        val cube = CUBE.copy(instances = listOf(CUBE.instances.single().copy(printable = false)))
        val repository = FakeRepository(readyState(cube).copy(plate = PLATE))

        AddPlateInstanceUseCase(repository, placePlateObject(FakeInspector(), repository), SelectPlateObjectUseCase(repository))(cube.mesh)

        assertEquals(1, repository.state.value.objects.single().instances.size)
    }

    @Test
    fun `removing a copy takes the last one, and the number of copies is set as asked`() {
        val copies = (0 until 3).map { PlateInstance(INSPECTION.copy(placement = moved(INSPECTION.placement, 20.0 * it))) }
        val cube = CUBE.copy(instances = copies)
        val repository = FakeRepository(readyState(cube).copy(plate = PLATE))
        val files = FakeSceneFiles()
        val delete = DeletePlateObjectUseCase(repository)
        val removeLast = RemoveLastPlateInstancesUseCase(repository, delete)
        val add = AddPlateInstanceUseCase(repository, placePlateObject(FakeInspector(), repository), SelectPlateObjectUseCase(repository))
        val setNumber = SetNumberOfInstancesUseCase(repository, add, removeLast, delete)

        // Plater::decrease_instances()
        removeLast(cube.mesh)

        assertEquals(copies.take(2), repository.state.value.objects.single().instances)
        assertEquals(setOf(PlateInstanceId(cube.mesh, 1)), repository.state.value.selectedInstances)

        setNumber(cube.mesh, 5)

        assertEquals(5, repository.state.value.objects.single().instances.size)

        setNumber(cube.mesh, 1)

        assertEquals(copies.take(1), repository.state.value.objects.single().instances)

        // None takes the object off the plate.
        setNumber(cube.mesh, 0)

        assertTrue(repository.state.value.objects.isEmpty())
    }

    @Test
    fun `an object and its volumes take the names the user enters, but not one a file name cannot hold`() {
        val file = ImportedModelFile(ModelPath("/scene/objects/a-source.mesh"), "a.step")
        val imported = PlateObject.ImportedModel(file, listOf(PlateInstance(INSPECTION)), parts = listOf(PART), inputName = "a.step")
        val repository = FakeRepository(readyState(imported))
        val rename = RenamePlateItemUseCase(repository)

        rename(imported.mesh, "bracket")
        rename(imported.mesh, "bad/name")
        rename(ObjectPartId(imported.mesh, 0), "body")
        rename(ObjectPartId(imported.mesh, 1), "insert")

        val renamed = repository.state.value.objects.single() as PlateObject.ImportedModel
        assertEquals("bracket", renamed.file.displayName)
        // The G-code is still named after the file (input_filename_base).
        assertEquals("a.step", renamed.inputName)
        assertEquals("body", renamed.volume.name)
        assertEquals("insert", renamed.parts.single().name)
    }

    @Test
    fun `the object menu manipulates a copy where it stands`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()

        placePlateObject(inspector, repository)(PlateInstanceId(CUBE.mesh), Manipulation.Mirror(Axis.X))

        assertEquals(Manipulation.Mirror(Axis.X), inspector.manipulation)
        assertEquals(CUBE.inspection.placement, inspector.placements.single())
    }

    @Test
    fun `a height range prints with its own filament and a support blocker takes none`() {
        val blocker = PART.copy(type = VolumeType.SUPPORT_BLOCKER)
        val cube = CUBE.copy(layerRanges = listOf(LayerRange(0.0, 2.0)), parts = listOf(blocker))
        val repository = FakeRepository(twoFilaments(cube))
        val set = SetExtruderUseCase(repository)

        set(LayerRangeId(cube.mesh, 0), 2)

        assertEquals(2, repository.state.value.objects.single().layerRanges.single().settings.extruderNumber)

        // The range follows the object again.
        set(LayerRangeId(cube.mesh, 0), 0)

        assertEquals(0, repository.state.value.objects.single().layerRanges.single().settings.extruderNumber)

        // Only a part of the model and a modifier print with a filament of their own.
        set(ObjectPartId(cube.mesh, 1), 2)

        assertEquals(0, repository.state.value.objects.single().parts.single().settings.extruderNumber)
    }

    @Test
    fun `a filament the plate does not have is refused`() {
        val repository = FakeRepository(twoFilaments(CUBE))

        SetExtruderUseCase(repository)(CUBE.mesh, 3)

        assertEquals(0, repository.state.value.objects.single().settings.extruderNumber)
    }

    @Test
    fun `the wipe tower dragged across the plate is kept among the plate's settings`() {
        val tower = WipeTower(shown = true, x = 165.0, y = 250.0, width = 24.0, depth = 24.0, height = 20.0)
        val repository = FakeRepository(
            twoFilaments(CUBE).copy(
                wipeTower = tower,
                result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS),
            ),
        )

        MoveWipeTowerUseCase(repository)(40.5, 30.25)

        // apply_wipe_tower(): the corner is written into the project.
        assertEquals("40.500", repository.state.value.plateSettings.values["wipe_tower_x"])
        assertEquals("30.250", repository.state.value.plateSettings.values["wipe_tower_y"])
        // The tower is drawn where it was dropped until the engine answers.
        assertEquals(40.5 to 30.25, repository.state.value.wipeTower?.let { it.x to it.y })
        // The print changes with the tower, so the G-code no longer applies.
        assertNull(repository.state.value.result)
    }

    @Test
    fun `a plate without a wipe tower keeps its settings`() {
        val repository = FakeRepository(twoFilaments(CUBE))

        MoveWipeTowerUseCase(repository)(40.0, 30.0)

        assertTrue(repository.state.value.plateSettings.isEmpty)
    }

    @Test
    fun `the flushing volumes the dialog submits are kept among the plate's settings`() {
        val repository = FakeRepository(
            twoFilaments(CUBE).copy(
                result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS),
            ),
        )

        SetFlushVolumesUseCase(repository)(listOf(0.0, 280.0, 140.0, 0.0), listOf(1.0))

        // The project keeps the matrix the way OrcaSlicer writes a vector.
        val settings = repository.state.value.plateSettings.values
        assertEquals("0.00,280.00,140.00,0.00", settings["flush_volumes_matrix"])
        assertEquals("1.00", settings["flush_multiplier"])
        // The print changes with them, so the G-code no longer applies.
        assertNull(repository.state.value.result)
    }

    @Test
    fun `the flushing volumes are described for the plate as it stands`() {
        val inspector = FakeInspector()
        val repository = FakeRepository(twoFilaments(CUBE).copy(plateSettings = ModelSettings(mapOf("flush_multiplier" to "2"))))

        val outcome = runSuspend { DescribeFlushVolumesUseCase(inspector, repository)() }

        assertTrue(outcome is FlushVolumesOutcome.Success)
        assertEquals(listOf(CUBE.placed()), inspector.flushPlate)
        assertEquals("2", inspector.flushSettings?.values?.get("flush_multiplier"))
    }

    @Test
    fun `painting an object keeps its colours and drops the G-code`() {
        val inspector = FakeInspector()
        val sceneFiles = FakeSceneFiles()
        val repository = FakeRepository(
            twoFilaments(CUBE).copy(
                result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS),
            ),
        )
        val paint = PaintObjectUseCase(inspector, sceneFiles, repository)

        val opened = runSuspend { paint.begin(CUBE.mesh) }

        assertTrue(opened is PaintingOutcome.Success)
        // The object is opened with the facets it already carries, and the
        // painted triangles are shown as meshes at once.
        assertEquals(CUBE.painted, inspector.paintedFacets)
        assertEquals(listOf(PaintedMesh(2, ScenePath("/scene/objects/painted-2.mesh"))), repository.state.value.objects.single().paintedMeshes)

        val stroke = PaintStroke(origin = Vector3(0.0, 0.0, 50.0), direction = Vector3(0.0, 0.0, -1.0), filament = 2)
        runSuspend { paint.stroke(stroke) }

        assertEquals(stroke, inspector.stroke)

        runSuspend { paint.end() }

        // The painted facets stay with the object, so the slicer prints them.
        assertEquals(PaintedFacets("painted"), repository.state.value.objects.single().painted)
        assertNull(repository.state.value.result)
    }

    @Test
    fun `a stroke without an open painting tool is refused`() {
        val repository = FakeRepository(twoFilaments(CUBE))
        val paint = PaintObjectUseCase(FakeInspector(), FakeSceneFiles(), repository)

        val outcome = runSuspend { paint.stroke(PaintStroke(Vector3(0.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0), filament = 1)) }

        assertTrue(outcome is PaintingOutcome.Failure)
    }

    @Test
    fun `a new wipe tower is kept where the plate shows it, so the slicer builds it there`() {
        val tower = WipeTower(shown = true, x = 165.0, y = 250.0, width = 24.0, depth = 24.0, height = 20.0, filaments = listOf(1, 2))
        val inspector = FakeInspector(tower = tower)
        val repository = FakeRepository(twoFilaments(CUBE))

        WipeTowerUpdates(inspector, repository, scope).start()

        // set_default_wipe_tower_pos_for_plate(): the position goes into the project.
        assertEquals(tower, repository.state.value.wipeTower)
        assertEquals("165.000", repository.state.value.plateSettings.values["wipe_tower_x"])
        assertEquals("250.000", repository.state.value.plateSettings.values["wipe_tower_y"])
    }

    @Test
    fun `a wipe tower the user placed keeps its place`() {
        val tower = WipeTower(shown = true, x = 165.0, y = 250.0, width = 24.0, depth = 24.0, height = 20.0, filaments = listOf(1, 2))
        val placed = ModelSettings(mapOf("wipe_tower_x" to "40.000", "wipe_tower_y" to "30.000"))
        val repository = FakeRepository(twoFilaments(CUBE).copy(plateSettings = placed))

        WipeTowerUpdates(FakeInspector(tower = tower), repository, scope).start()

        assertEquals(placed, repository.state.value.plateSettings)
    }

    @Test
    fun `an imported configuration reaches the engine and the presets follow it`() {
        val repository = FakeRepository(readyState())
        val presets = FakePresetManager()
        val files = FakeConfigFiles(copied = "/configs/process.json")
        val editor = RecordingSettingsEditor(imported = ConfigTransferOutcome.Success(listOf("Orcinus process")))

        val outcome = runSuspend {
            ImportConfigUseCase(editor, presets, files, platePresets(FakeInspector(), repository))(listOf(REFERENCE))
        }

        assertEquals(ConfigTransferOutcome.Success(listOf("Orcinus process")), outcome)
        assertEquals(listOf("/configs/process.json"), editor.importedPaths)
        // The installed presets changed, so the plate was brought to them.
        assertEquals(PRESETS, repository.state.value.presets)
    }

    @Test
    fun `an import that met a preset of the same name comes back with it and runs again with the answer`() {
        val repository = FakeRepository(readyState())
        val files = FakeConfigFiles(copied = "/configs/process.json")
        val editor = RecordingSettingsEditor(imported = ConfigTransferOutcome.Overwrite("Orcinus process", emptyList()))
        val importConfig = ImportConfigUseCase(editor, FakePresetManager(), files, platePresets(FakeInspector(), repository))

        val asked = runSuspend { importConfig(listOf(REFERENCE)) }

        assertEquals(ConfigTransferOutcome.Overwrite("Orcinus process", emptyList()), asked)

        val answers = mapOf("Orcinus process" to ConfigOverwriteAnswer.YES)
        runSuspend { importConfig(listOf(REFERENCE), answers) }

        assertEquals(answers, editor.importedAnswers)
    }

    @Test
    fun `a document that cannot be read is reported and nothing is imported`() {
        val files = FakeConfigFiles(copied = null)
        val editor = RecordingSettingsEditor()

        val outcome = runSuspend {
            ImportConfigUseCase(editor, FakePresetManager(), files, platePresets(FakeInspector(), FakeRepository(readyState())))(listOf(REFERENCE))
        }

        assertTrue(outcome is ConfigTransferOutcome.Failure)
        assertTrue(editor.importedPaths.isEmpty())
    }

    @Test
    fun `an export writes the engine's files into the folder the user picked`() {
        val files = FakeConfigFiles(copied = null, written = 2)
        val editor = RecordingSettingsEditor(exported = ConfigTransferOutcome.Success(listOf("/configs/a.json", "/configs/b.json")))

        val outcome = runSuspend {
            ExportConfigUseCase(editor, files)(ConfigExportKind.PRINTER_BUNDLE, listOf("a printer"), REFERENCE)
        }

        assertEquals(ConfigTransferOutcome.Success(listOf("/configs/a.json", "/configs/b.json")), outcome)
        assertEquals("/configs", editor.exportedDirectory)
        assertEquals(ConfigExportKind.PRINTER_BUNDLE to listOf("a printer"), editor.exportedNames)
        assertEquals(listOf("/configs/a.json", "/configs/b.json") to REFERENCE, files.copiedOut)
    }

    @Test
    fun `an export that could not write every file says so`() {
        val files = FakeConfigFiles(copied = null, written = 1)
        val editor = RecordingSettingsEditor(exported = ConfigTransferOutcome.Success(listOf("/configs/a.json", "/configs/b.json")))

        val outcome = runSuspend {
            ExportConfigUseCase(editor, files)(ConfigExportKind.PRINTER_BUNDLE, listOf("a printer"), REFERENCE)
        }

        assertTrue(outcome is ConfigTransferOutcome.Failure)
    }

    private fun startEngine(
        engine: FakeEngine,
        presets: FakePresetManager,
        inspector: FakeInspector,
        repository: FakeRepository,
        files: FakeSceneFiles = FakeSceneFiles(),
        cache: PlateCache = NoPlateCache,
        onStarting: () -> Unit = {},
    ) = StartEngineUseCase(
        GetEngineStatusUseCase(engine),
        object : PresetManager by presets {
            override suspend fun presets(): PresetsOutcome {
                onStarting()
                return presets.presets()
            }
        },
        platePresets(inspector, repository, files, cache),
        files,
        cache,
        repository,
    )

    private fun settingsTabs(repository: PlateRepository) = PresetSettingsTabs(NoSettingsEditor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope)

    private fun platePresets(
        inspector: FakeInspector,
        repository: FakeRepository,
        files: FakeSceneFiles = FakeSceneFiles(),
        cache: PlateCache = NoPlateCache,
    ) =
        PlatePresets(
            inspector,
            files,
            cache,
            repository,
            placePlateObjects(inspector, repository),
            PresetSettingsTabs(NoSettingsEditor, FakePresetManager(), NO_FLUSH_UPDATES, repository, scope),
        )

    private fun slicePlate(
        engine: FakeEngine,
        repository: PlateRepository,
        files: FakeSceneFiles = FakeSceneFiles(),
        thumbnails: PlateThumbnailRenderer = PlateThumbnailRenderer { _, _, _, _, _, _, _ -> emptyList() },
    ) = SlicePlateUseCase(
        sliceModel = SliceModelUseCase(engine),
        renderThumbnails = RenderThumbnailsUseCase(engine, thumbnails, files),
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
            inspector = inspector,
            sceneFiles = files,
            repository = repository,
            placePlateObjects = PlacePlateObjectsUseCase(PlaceModelsUseCase(inspector), repository, scope),
            presetManager = FakePresetManager(),
            platePresets = PresetsApplier { _, _ -> },
            confirmClose = ProjectCloseConfirmation { true },
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

    /** [placement] shifted along X. */
    private fun moved(placement: Transform3, x: Double) =
        Transform3(placement.columns.mapIndexed { index, value -> if (index == 12) value + x else value })

    private fun readyState(vararg objects: PlateObject) = PlateState(
        presets = PRESETS,
        engine = EngineState(EngineAvailability.READY, EngineVersion("orca")),
        objects = objects.toList(),
    )

    /** The copies judged against the plate at [index]: inside it when [onPlate] puts their object there. */
    private fun PlateState.judgedOn(index: Int, onPlate: Map<ScenePath, Int>) = copy(
        objects = objects.map { plateObject ->
            val fit = if (onPlate[plateObject.mesh] == index) BuildVolumeFit.INSIDE else BuildVolumeFit.OUTSIDE
            plateObject.withInstances(plateObject.instances.map { it.copy(inspection = it.inspection.copy(fit = fit)) })
        },
    )

    /** A plate whose printer prints with two filaments. */
    private fun twoFilaments(vararg objects: PlateObject) = readyState(*objects).copy(presets = TWO_FILAMENTS)

    @Test
    fun `the flushing volumes the engine worked out again are kept with the plate, and an unchanged project is left alone`() {
        val repository = FakeRepository(
            twoFilaments(CUBE).copy(result = PlateSliceResult(SliceJobId("old"), listOf(CUBE), OutputPath("/gcode/old.gcode"), STATISTICS)),
        )
        val inspector = FakeInspector()
        inspector.flushUpdateOutcome = FlushVolumesOutcome.Success(
            FlushVolumes(filaments = 2, matrix = listOf(0.0, 575.0, 352.0, 0.0), multipliers = listOf(0.3)),
            updated = true,
        )

        runSuspend { UpdateFlushVolumesUseCase(inspector, repository).update(FlushVolumesChange.COLOR_CHANGED, 1) }

        assertEquals(FlushVolumesChange.COLOR_CHANGED to 1, inspector.flushUpdate)
        val settings = repository.state.value.plateSettings.values
        assertEquals("0.00,575.00,352.00,0.00", settings["flush_volumes_matrix"])
        assertEquals("0.30", settings["flush_multiplier"])
        // The print changes with them.
        assertNull(repository.state.value.result)

        inspector.flushUpdateOutcome = FlushVolumesOutcome.Success(FlushVolumes(filaments = 2, matrix = listOf(0.0, 1.0, 1.0, 0.0)), updated = false)
        runSuspend { UpdateFlushVolumesUseCase(inspector, repository).update(FlushVolumesChange.FILAMENT_CHANGED, 0) }

        assertEquals("0.00,575.00,352.00,0.00", repository.state.value.plateSettings.values["flush_volumes_matrix"])
    }

    @Test
    fun `another printer works every filament's flushing volumes out again`() {
        val repository = FakeRepository(readyState(CUBE))
        val presets = FakePresetManager(selected = { PresetsOutcome.Success(PRESETS.copy(selection = PROFILES.copy(printer = ProfileId("other printer")))) })
        var updated: Pair<FlushVolumesChange, Int>? = null
        val flush = FlushVolumesUpdater { change, index -> updated = change to index }

        SelectPresetUseCase(presets, platePresets(FakeInspector(), repository), flush, settingsTabs(repository), repository, scope)(
            PresetChoice.Printer(ProfileId("other printer")),
        )

        assertEquals(FlushVolumesChange.PRINTER_CHANGED to -1, updated)
    }

    @Test
    fun `export as one STL writes the object into the document picked, then deletes what the engine wrote`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        val files = FakeSceneFiles()
        val documents = FakeDocuments()

        val exported = runSuspend { ExportObjectMeshUseCase(inspector, files, documents, repository)(CUBE.mesh, MeshFormat.STL, REFERENCE) }

        assertTrue(exported)
        val written = ScenePath("/scene/objects/import-0-export.stl")
        assertEquals(listOf(Triple(0, MeshFormat.STL, written)), inspector.exports)
        assertEquals(listOf(written.value to REFERENCE), documents.copied)
        assertEquals(files.importPrefixes, files.deletedImports)
        assertNull(repository.state.value.problem)
        // Nothing on the plate changes, so there is nothing to undo.
        assertTrue(repository.state.value.history.undo.isEmpty())
    }

    @Test
    fun `an export of the positive parts alone says so, and one that cannot be written fails`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        inspector.export = MeshExportOutcome.Success("negative parts left in")

        runSuspend { ExportObjectMeshUseCase(inspector, FakeSceneFiles(), FakeDocuments(), repository)(CUBE.mesh, MeshFormat.DRC, REFERENCE) }

        assertEquals(PlateProblemKind.EXPORT_WITHOUT_NEGATIVE_VOLUMES, repository.state.value.problem?.kind)

        val failed = runSuspend { ExportObjectMeshUseCase(inspector, FakeSceneFiles(), FakeDocuments(succeeds = false), repository)(CUBE.mesh, MeshFormat.DRC, REFERENCE) }

        assertFalse(failed)
        assertEquals(PlateProblemKind.EXPORT_FAILED, repository.state.value.problem?.kind)
    }

    @Test
    fun `replace 3D file puts the object the engine wrote in place of the old one, selected, as one step of Undo`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        val file = ImportedModelFile(ModelPath("/imports/new.stl"), "new.stl")
        val replace = ReplaceObjectVolumeUseCase(
            ImportModelUseCase(object : ModelFileImporter {
                override suspend fun importModel(reference: ExternalDocumentReference) = ModelImportOutcome.Success(file)
            }),
            inspector,
            FakeSceneFiles(),
            repository,
            scope,
        )

        replace(PlateInstanceId(CUBE.mesh), 0, REFERENCE)

        assertEquals(listOf(FakeInspector.Replacement(0, 0, file.path)), inspector.replacements)
        val state = repository.state.value
        assertFalse(state.editing)
        assertEquals(1, state.objects.size)
        val replaced = state.objects.single()
        assertEquals(LOADED.instances.single().inspection.mesh, replaced.mesh)
        assertEquals(setOf(PlateInstanceId(replaced.mesh)), state.selectedInstances)
        assertEquals(listOf(listOf(CUBE)), state.history.undo.map { it.objects })
    }

    @Test
    fun `copied process settings of an object carry its own, and those of a part carry the object's under them`() {
        val part = ObjectPart("Cube", VolumeType.MODIFIER, ScenePath("/scene/objects/part.mesh"), INSPECTION.placement, ModelSettings(mapOf("wall_loops" to "5")))
        val cube = CUBE.withSettings(ModelSettings(mapOf("layer_height" to "0.1", "wall_loops" to "3"))).withParts(listOf(part))
        val repository = FakeRepository(readyState(cube))
        val copy = CopyProcessSettingsUseCase(repository)

        copy(SettingsItem.Object(cube.mesh))

        assertEquals(
            SettingsClipboard(SettingsItemKind.OBJECT, ModelSettings(mapOf("layer_height" to "0.1", "wall_loops" to "3"))),
            repository.state.value.settingsClipboard,
        )

        copy(SettingsItem.Volume(ObjectPartId(cube.mesh, 1)))

        assertEquals(
            SettingsClipboard(SettingsItemKind.VOLUME, ModelSettings(mapOf("layer_height" to "0.1", "wall_loops" to "5"))),
            repository.state.value.settingsClipboard,
        )
    }

    @Test
    fun `pasted process settings are the engine's, as one step of Undo, and only into an item of the same kind`() {
        val other = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))
        val clipboard = SettingsClipboard(SettingsItemKind.OBJECT, ModelSettings(mapOf("wall_loops" to "4")))
        val repository = FakeRepository(readyState(CUBE, other).copy(settingsClipboard = clipboard))
        val editor = PastingEditor(ModelSettings(mapOf("wall_loops" to "4", "extruder" to "1")))
        val paste = PasteProcessSettingsUseCase(editor, repository, settingsTabs(repository), scope)

        paste(SettingsItem.Volume(ObjectPartId(other.mesh, 0)))

        assertTrue(editor.pasted.isEmpty())

        paste(SettingsItem.Object(other.mesh))

        assertEquals(listOf(Triple<ModelSettings, ModelSettings, ModelSettings?>(clipboard.settings, ModelSettings(), null)), editor.pasted)
        val state = repository.state.value
        assertEquals(ModelSettings(mapOf("wall_loops" to "4", "extruder" to "1")), state.objects.last().settings)
        assertEquals(ModelSettings(), state.objects.first().settings)
        assertEquals(1, state.history.undo.size)
    }

    @Test
    fun `a flush option starts from the process preset's value and is the object's own once switched, with the prime tower`() {
        val tower = WipeTower(filaments = listOf(1, 2), primeTower = true, flushInto = setOf(FlushOption.SUPPORT))
        val repository = FakeRepository(twoFilaments(CUBE).copy(flushing = tower))
        val flush = SetFlushOptionUseCase(repository, settingsTabs(repository), scope)

        flush(CUBE.mesh, FlushOption.SUPPORT)
        flush(CUBE.mesh, FlushOption.INFILL)

        val settings = repository.state.value.objects.single().settings
        assertEquals(mapOf("flush_into_support" to "0", "flush_into_infill" to "1"), settings.values)
        assertFalse(repository.state.value.objects.single().flushesInto(FlushOption.SUPPORT, tower.flushInto))
        // The desktop app changes the object's config without a snapshot.
        assertTrue(repository.state.value.history.undo.isEmpty())

        val off = FakeRepository(twoFilaments(CUBE).copy(flushing = tower.copy(primeTower = false)))
        SetFlushOptionUseCase(off, settingsTabs(off), scope)(CUBE.mesh, FlushOption.OBJECTS)

        assertEquals(ModelSettings(), off.state.value.objects.single().settings)
    }

    @Test
    fun `the printable check box of an object switches every copy as one step of Undo`() {
        val copies = CUBE.withInstances(listOf(PlateInstance(INSPECTION), PlateInstance(INSPECTION.copy(placement = translated(60.0, 0.0, 10.0)))))
        val repository = FakeRepository(readyState(copies))

        SetPlateObjectPrintableUseCase(repository).all(CUBE.mesh, false)

        val state = repository.state.value
        assertTrue(state.objects.single().instances.none(PlateInstance::printable))
        assertEquals(1, state.history.undo.size)
    }

    @Test
    fun `simplify opens on an object of one volume, and OrcaSlicer refuses an object of several`() {
        val part = ObjectPart("Cube", VolumeType.PART, ScenePath("/scene/objects/part.mesh"), INSPECTION.placement)
        val parted = CUBE.withInspection(INSPECTION.copy(mesh = ScenePath("/scene/objects/parted.mesh"))).withParts(listOf(part))
        val repository = FakeRepository(readyState(CUBE, parted))
        val open = OpenSimplifyUseCase(repository)

        open.ofObject(PlateInstanceId(parted.mesh), wholeObject = false)

        assertNull(repository.state.value.simplifyTarget)
        assertEquals("simplify_single_part", repository.state.value.plateNotices.single().id)

        open.ofObject(PlateInstanceId(CUBE.mesh), wholeObject = false)

        assertEquals(ObjectPartId(CUBE.mesh, 0), repository.state.value.simplifyTarget)
        assertEquals(setOf(PlateInstanceId(CUBE.mesh)), repository.state.value.selectedInstances)

        // A part of the object list's own row.
        open.close()
        open.ofVolume(ObjectPartId(parted.mesh, 1))

        assertEquals(ObjectPartId(parted.mesh, 1), repository.state.value.simplifyTarget)
        assertEquals(ObjectPartId(parted.mesh, 1), repository.state.value.selectedPart)

        open.refuse()

        assertNull(repository.state.value.simplifyTarget)
        assertEquals("gizmos_open", repository.state.value.plateNotices.last().id)
    }

    @Test
    fun `a simplify preview is the engine's mesh in a file of its own, dropped when it fails`() {
        val repository = FakeRepository(readyState(CUBE))
        val inspector = FakeInspector()
        val files = FakeSceneFiles()
        val preview = PreviewSimplifyUseCase(inspector, files, repository)
        val config = SimplifyConfig(useCount = true, decimateRatio = 50f)

        val shown = runSuspend { preview(ObjectPartId(CUBE.mesh, 0), config) }

        assertEquals(SimplifyPreview(ScenePath("/scene/objects/import-0-simplified.mesh"), 6, 12, ScenePath("/scene/objects/import-0")), shown)
        assertEquals(listOf(FakeInspector.Simplification(0, 0, config, ScenePath("/scene/objects/import-0-simplified.mesh"))), inspector.simplifications)
        assertTrue(files.deletedImports.isEmpty())

        inspector.simplified = SimplifyOutcome.Failure("no")
        assertNull(runSuspend { preview(ObjectPartId(CUBE.mesh, 0), config) })
        assertEquals(listOf(ScenePath("/scene/objects/import-1")), files.deletedImports)

        preview.discard(checkNotNull(shown))
        assertEquals(ScenePath("/scene/objects/import-0"), files.deletedImports.last())
    }

    @Test
    fun `applied simplification replaces the object as one step of Undo, keeps it selected and closes the gizmo`() {
        val repository = FakeRepository(readyState(CUBE).copy(simplifyTarget = ObjectPartId(CUBE.mesh, 0), selectedInstances = setOf(PlateInstanceId(CUBE.mesh))))
        val inspector = FakeInspector()
        val config = SimplifyConfig(maxError = 0.1f)

        ApplySimplifyUseCase(inspector, FakeSceneFiles(), repository, scope)(ObjectPartId(CUBE.mesh, 0), config)

        assertEquals(config, inspector.appliedSimplifications.single().config)
        val state = repository.state.value
        assertFalse(state.editing)
        assertNull(state.simplifyTarget)
        val simplified = state.objects.single()
        assertEquals(setOf(PlateInstanceId(simplified.mesh)), state.selectedInstances)
        assertEquals(listOf(listOf(CUBE)), state.history.undo.map { it.objects })
    }

    @Test
    fun `a volume changes type as one step of Undo, and the list selects it where the sorting put it`() {
        val part = ObjectPart("Cube", VolumeType.PART, ScenePath("/scene/objects/part.mesh"), INSPECTION.placement)
        val parted = CUBE.withParts(listOf(part))
        val repository = FakeRepository(readyState(parted))
        val inspector = FakeInspector()

        ChangeVolumeTypeUseCase(inspector, FakeSceneFiles(), repository, scope)(ObjectPartId(parted.mesh, 1), VolumeType.MODIFIER)

        assertEquals(listOf(Triple(0, 1, VolumeType.MODIFIER)), inspector.typeChanges)
        val state = repository.state.value
        val changed = state.objects.single()
        assertEquals(ObjectPartId(changed.mesh, 2), state.selectedPart)
        assertEquals(1, state.history.undo.size)

        // A volume already of that type is left alone.
        ChangeVolumeTypeUseCase(inspector, FakeSceneFiles(), repository, scope)(ObjectPartId(changed.mesh, 0), VolumeType.PART)
        assertEquals(1, inspector.typeChanges.size)
    }

    @Test
    fun `replace all takes the files of the same names from the folder, a step of Undo each, and says what it did`() {
        val wheel = ObjectPart("", VolumeType.PART, ScenePath("/scene/objects/wheel.mesh"), INSPECTION.placement, name = "wheel", inputFile = "/imports/wheel.stl")
        val body = CUBE.withVolume(ObjectVolume(name = "body", inputFile = "/imports/body.stl")).withParts(listOf(wheel))
        val repository = FakeRepository(readyState(body))
        val inspector = FakeInspector()
        val folders = object : DocumentFolders {
            override suspend fun find(folder: ExternalDocumentReference, name: String) =
                ExternalDocumentReference("content://models/$name").takeIf { name == "wheel.stl" }

            override suspend fun displayName(folder: ExternalDocumentReference) = "Models"
        }
        val file = ImportedModelFile(ModelPath("/imports/wheel.stl"), "wheel.stl")
        val importModel = ImportModelUseCase(object : ModelFileImporter {
            override suspend fun importModel(reference: ExternalDocumentReference) = ModelImportOutcome.Success(file)
        })

        ReplaceAllVolumesUseCase(importModel, folders, inspector, FakeSceneFiles(), repository, scope)(
            PlateInstanceId(body.mesh),
            ExternalDocumentReference("content://models"),
        )

        assertEquals(listOf(FakeInspector.Replacement(0, 1, file.path)), inspector.replacements)
        val state = repository.state.value
        assertFalse(state.editing)
        assertEquals(1, state.history.undo.size)
        val notice = state.plateNotices.single()
        assertEquals("replaced_volumes", notice.id)
        assertEquals(
            listOf("Replaced with 3D files from directory:\n", "%s", "✖ Skipped %1%: file does not exist.\n", "✔ Replaced %1%.\n"),
            notice.text.map { it.msgid },
        )
        assertEquals(listOf("body"), notice.text[2].args)
        assertEquals(listOf("wheel"), notice.text[3].args)
    }

    @Test
    fun `the layer slider keeps one code per layer, and G-code of the user's own takes the layer's place`() {
        val result = PlateSliceResult(SliceJobId("done"), listOf(CUBE), OutputPath("/gcode/done.gcode"), STATISTICS, layerGcodeRules = LayerGcodeRules(hasTemplate = false))
        val repository = FakeRepository(readyState(CUBE).copy(result = result))
        val edit = EditLayerGcodesUseCase(repository)

        edit.add(2.0, LayerGcodeType.PAUSE_PRINT)
        // IMSlider::add_code_as_tick(): a layer with a code takes no other one.
        edit.add(2.0, LayerGcodeType.PAUSE_PRINT)
        // No template in the printer, no filament to change to.
        edit.add(4.0, LayerGcodeType.TEMPLATE)
        edit.add(4.0, LayerGcodeType.TOOL_CHANGE, 2)

        assertEquals(listOf(LayerGcode(2.0, LayerGcodeType.PAUSE_PRINT)), repository.state.value.layerGcodes)

        edit.addCustom(2.0, "M117 hi")
        assertEquals(listOf(LayerGcode(2.0, LayerGcodeType.CUSTOM, extra = "M117 hi")), repository.state.value.layerGcodes)

        edit.delete(2.0)
        assertTrue(repository.state.value.layerGcodes.isEmpty())

        // A print by object offers no codes at all.
        val sequential = FakeRepository(readyState(CUBE).copy(result = result.copy(layerGcodeRules = LayerGcodeRules(sequential = true))))
        EditLayerGcodesUseCase(sequential).add(2.0, LayerGcodeType.PAUSE_PRINT)
        assertTrue(sequential.state.value.layerGcodes.isEmpty())
    }

    /** The document picked for an export, which records what was copied into it. */
    private class FakeDocuments(private val succeeds: Boolean = true, private val name: String? = null) : DocumentExport {
        val copied = mutableListOf<Pair<String, ExternalDocumentReference>>()

        override suspend fun copyTo(path: String, document: ExternalDocumentReference): Boolean {
            copied += path to document
            return succeeds
        }

        override suspend fun displayName(document: ExternalDocumentReference): String? = name
    }

    /** The engine's settings editor, which answers a paste with [result]. */
    private class PastingEditor(private val result: ModelSettings) : PresetSettingsEditor by NoSettingsEditor {
        val pasted = mutableListOf<Triple<ModelSettings, ModelSettings, ModelSettings?>>()

        override suspend fun pasteModelSettings(clipboard: ModelSettings, target: ModelSettings, parent: ModelSettings?): ModelSettingsOutcome {
            pasted += Triple(clipboard, target, parent)
            return ModelSettingsOutcome.Success(result)
        }
    }

    /** The flushing volumes stay as they are. */
    private val NO_FLUSH_UPDATES = FlushVolumesUpdater { _, _ -> }

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
        /** The wipe tower the engine describes. */
        private val tower: WipeTower = WipeTower(),
        /** The placed object for a placement; null leaves the call suspended. */
        private val placed: ((Transform3) -> ModelInspection)? = { INSPECTION.copy(placement = it) },
        /** The placed objects for a job; null leaves the call suspended. */
        private val placedObjects: ((List<PlacedModel>) -> List<List<ModelInspection>>)? =
            { plate -> plate.map { placed -> placed.instances.map { INSPECTION.copy(mesh = placed.mesh, placement = it.placement) } } },
        /** When set, the answer to a job, once completed. */
        private val arranged: CompletableDeferred<PlateInspectionOutcome>? = null,
    ) : PlateInspector {
        /** What the painting tool was opened and painted with. */
        var paintedFacets: PaintedFacets? = null
        var stroke: PaintStroke? = null

        override suspend fun beginPainting(
            plateObject: PlacedModel,
            part: Int?,
            profiles: SlicingProfileSelection,
            facets: PaintedFacets,
            meshPrefix: ScenePath,
        ): PaintingOutcome {
            paintedFacets = facets
            return PaintingOutcome.Success(painted(meshPrefix))
        }

        override suspend fun paint(stroke: PaintStroke, meshPrefix: ScenePath): PaintingOutcome {
            this.stroke = stroke
            return PaintingOutcome.Success(painted(meshPrefix))
        }

        override suspend fun endPainting(): PaintingOutcome =
            PaintingOutcome.Success(PaintedSurface(facets = PaintedFacets("painted")))

        /** The tool's own Undo, which takes every painted triangle off. */
        var undone = 0

        override suspend fun undoPainting(meshPrefix: ScenePath): PaintingOutcome {
            undone++
            return PaintingOutcome.Success(PaintedSurface(canRedo = true))
        }

        override suspend fun redoPainting(meshPrefix: ScenePath): PaintingOutcome = PaintingOutcome.Success(painted(meshPrefix))

        private fun painted(meshPrefix: ScenePath) = PaintedSurface(
            hit = true,
            filaments = listOf(2),
            meshes = listOf(ScenePath("${meshPrefix.value}-2.mesh")),
        )
        /** What the last flushing volumes request carried. */
        var flushPlate: List<PlacedModel>? = null
        var flushSettings: ModelSettings? = null

        override suspend fun describeFlushVolumes(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plateSettings: ModelSettings,
        ): FlushVolumesOutcome {
            flushPlate = plate
            flushSettings = plateSettings
            return FlushVolumesOutcome.Success(FlushVolumes(filaments = 2))
        }

        /** What the last update of the flushing volumes asked for, and what it answers. */
        var flushUpdate: Pair<FlushVolumesChange, Int>? = null
        var flushUpdateOutcome: FlushVolumesOutcome = FlushVolumesOutcome.Success(FlushVolumes())

        override suspend fun updateFlushVolumes(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plateSettings: ModelSettings,
            change: FlushVolumesChange,
            index: Int,
        ): FlushVolumesOutcome {
            flushUpdate = change to index
            flushSettings = plateSettings
            return flushUpdateOutcome
        }
        override suspend fun describeWipeTower(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plateSettings: ModelSettings,
        ): WipeTowerOutcome = WipeTowerOutcome.Success(tower)
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

        /** A request to load a model file. */
        data class Load(
            val source: ModelPath,
            val prefix: ScenePath,
            val answers: Map<String, Boolean>,
            val load: ModelLoad = ModelLoad.GEOMETRY,
            val chosen: Boolean = false,
        )

        val loads = mutableListOf<Load>()

        /** What the load of a model file answers, for the answers it was given; by default one object. */
        var load: (Map<String, Boolean>) -> ModelLoadOutcome = { ModelLoadOutcome.Success(listOf(LOADED), emptyList()) }

        override suspend fun load(
            source: ModelPath,
            profiles: SlicingProfileSelection,
            plate: List<PlacedModel>,
            prefix: ScenePath,
            answers: Map<String, Boolean>,
            load: ModelLoad,
            chosen: Boolean,
        ): ModelLoadOutcome {
            loads += Load(source, prefix, answers, load, chosen)
            this.profiles = profiles
            this.plate = plate
            return load(answers)
        }

        /** A request to edit an object of the plate. */
        data class Edit(val index: Int, val edit: ObjectEdit, val volume: Int?, val answers: Map<String, Boolean>)

        val edits = mutableListOf<Edit>()

        /** What an edit answers, for the answers it was given; by default the object as LOADED. */
        var editOutcome: (Map<String, Boolean>) -> ModelLoadOutcome = { ModelLoadOutcome.Success(listOf(LOADED), emptyList()) }

        override suspend fun edit(
            plate: List<PlacedModel>,
            index: Int,
            edit: ObjectEdit,
            volume: Int?,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
            answers: Map<String, Boolean>,
        ): ModelLoadOutcome {
            edits += Edit(index, edit, volume, answers)
            this.plate = plate
            return editOutcome(answers)
        }

        data class Copy(val sources: List<PlacedModel>, val count: Int, val placement: CopyPlacement)

        val copies = mutableListOf<Copy>()

        /** A new object per source and round, named after its place in the answer. */
        var copyOutcome: (List<PlacedModel>, Int) -> ModelLoadOutcome = { sources, count ->
            ModelLoadOutcome.Success(
                List(sources.size * count) { LOADED.copy(instances = listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/copy-$it.mesh"))))) },
                emptyList(),
            )
        }

        override suspend fun copy(
            plate: List<PlacedModel>,
            sources: List<PlacedModel>,
            count: Int,
            placement: CopyPlacement,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ): ModelLoadOutcome {
            copies += Copy(sources, count, placement)
            this.plate = plate
            return copyOutcome(sources, count)
        }

        val primitives = mutableListOf<Pair<String, String>>()

        override suspend fun addPrimitive(
            plate: List<PlacedModel>,
            shape: String,
            name: String,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ): ModelLoadOutcome {
            primitives += shape to name
            return ModelLoadOutcome.Success(listOf(LOADED.copy(name = name)), emptyList())
        }

        override suspend fun handyModel(file: String): ModelPath = ModelPath("/resources/handy_models/$file")

        val exports = mutableListOf<Triple<Int, MeshFormat, ScenePath>>()
        var export: MeshExportOutcome = MeshExportOutcome.Success(null)

        override suspend fun saveProject(
            path: ScenePath,
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plates: List<ProjectPlate>,
            projectInfo: ScenePath?,
        ): ProjectSaveOutcome = saveProject(path, plate, plates)

        /** What saving a project answers; by default it is saved. */
        var saveProject: (ScenePath, List<PlacedModel>, List<ProjectPlate>) -> ProjectSaveOutcome =
            { _, _, _ -> ProjectSaveOutcome.Success }

        override suspend fun exportMesh(
            plate: List<PlacedModel>,
            index: Int,
            format: MeshFormat,
            profiles: SlicingProfileSelection,
            path: ScenePath,
        ): MeshExportOutcome {
            exports += Triple(index, format, path)
            return export
        }

        data class Simplification(val index: Int, val volume: Int, val config: SimplifyConfig, val path: ScenePath)

        val simplifications = mutableListOf<Simplification>()
        var simplified: SimplifyOutcome = SimplifyOutcome.Success(6, 12)

        override suspend fun simplifyVolume(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            config: SimplifyConfig,
            profiles: SlicingProfileSelection,
            path: ScenePath,
        ): SimplifyOutcome {
            simplifications += Simplification(index, volume, config, path)
            return simplified
        }

        val appliedSimplifications = mutableListOf<Simplification>()

        val typeChanges = mutableListOf<Triple<Int, Int, VolumeType>>()
        var typeChange: ModelLoadOutcome = ModelLoadOutcome.Success(listOf(LOADED), emptyList(), selectedVolume = 2)

        override suspend fun setVolumeType(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            type: VolumeType,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ): ModelLoadOutcome {
            typeChanges += Triple(index, volume, type)
            return typeChange
        }

        override suspend fun applySimplify(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            config: SimplifyConfig,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ): ModelLoadOutcome {
            appliedSimplifications += Simplification(index, volume, config, prefix)
            return replacement
        }

        data class Replacement(val index: Int, val volume: Int, val source: ModelPath)

        val replacements = mutableListOf<Replacement>()
        var replacement: ModelLoadOutcome = ModelLoadOutcome.Success(listOf(LOADED), emptyList())

        override suspend fun replaceVolume(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            source: ModelPath,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ): ModelLoadOutcome {
            replacements += Replacement(index, volume, source)
            return replacement
        }

        data class Paste(val index: Int, val instance: Int, val source: PlacedModel, val volumes: List<Int>, val sameInputFile: Boolean)

        var pasted: Paste? = null

        override suspend fun pasteVolumes(
            plate: List<PlacedModel>,
            index: Int,
            instance: Int,
            source: PlacedModel,
            volumes: List<Int>,
            sameInputFile: Boolean,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ): ModelLoadOutcome {
            pasted = Paste(index, instance, source, volumes, sameInputFile)
            this.plate = plate
            return ModelLoadOutcome.Success(listOf(LOADED), emptyList(), selectedVolume = 1)
        }

        override suspend fun place(
            plateObject: PlacedModel,
            profiles: SlicingProfileSelection,
            previous: Transform3,
            placement: Transform3,
            autoDrop: Boolean,
            manipulation: Manipulation,
        ): ModelInspectionOutcome {
            inspected += plateObject.model
            placements += placement
            this.previous = previous
            this.autoDrop = autoDrop
            this.manipulation = manipulation
            val place = placed ?: return suspendCancellableCoroutine { }
            return ModelInspectionOutcome.Success(place(placement).copy(mesh = plateObject.mesh))
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

        override suspend fun addPart(
            plateObject: PlacedModel,
            shape: String,
            type: VolumeType,
            profiles: SlicingProfileSelection,
            mesh: ScenePath,
        ) = ModelInspectionOutcome.Failure("not used")

        override suspend fun flatteningPlanes(
            plateObject: PlacedModel,
            profiles: SlicingProfileSelection,
            placement: Transform3,
        ) = FlatteningPlanesOutcome.Failure("not used")
    }

    private class FakeSceneFiles : SceneFiles {
        val importPrefixes = mutableListOf<ScenePath>()
        val deletedImports = mutableListOf<ScenePath>()

        override fun newImportPrefix() = ScenePath("/scene/objects/import-${importPrefixes.size}").also(importPrefixes::add)

        override fun deleteImport(prefix: ScenePath) {
            deletedImports += prefix
        }

        override fun newPaintedMeshes(): ScenePath = ScenePath("/scene/objects/painted")
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

        override fun wipeTowerMeshOf(toolpaths: ScenePath) = ScenePath(toolpaths.value + ".tower.mesh")

        override fun thumbnailOf(toolpaths: ScenePath, size: ThumbnailSize) = ScenePath("${toolpaths.value}.${size.width}x${size.height}.rgba")

        override fun deleteToolpathsExcept(keep: Collection<ScenePath>) {
            keptToolpaths = keep.singleOrNull()
        }
    }

    private class FakeEngine(
        private val status: EngineStatus = EngineStatus(EngineVersion("orca"), ready = true),
        private val onSlice: (SliceRequest, SliceProgressListener) -> Unit = { _, _ -> },
        private val outcome: (SliceRequest) -> SliceOutcome = { SliceOutcome.Success(it.jobId, OutputPath("/gcode/out.gcode"), STATISTICS, it.toolpaths) },
        private val thumbnails: ThumbnailSizesOutcome = ThumbnailSizesOutcome.Success(emptyList()),
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

        override suspend fun thumbnailSizes(profiles: SlicingProfileSelection) = thumbnails
    }

    /** The documents of an import and an export, which the app copies. */
    private class FakeConfigFiles(private val copied: String?, private val written: Int = 0) : ConfigFiles {
        var copiedOut: Pair<List<String>, ExternalDocumentReference>? = null

        override suspend fun copyIn(reference: ExternalDocumentReference): String? = copied

        override fun exportDirectory(): String = "/configs"

        override suspend fun copyOut(files: List<String>, folder: ExternalDocumentReference): Int {
            copiedOut = files to folder
            return written
        }
    }

    /** The engine's settings editor, which records the settings changed. */
    private class ChangeRecordingEditor : PresetSettingsEditor by NoSettingsEditor {
        val changes = mutableListOf<Triple<PresetKind, String, String>>()

        override suspend fun changeSetting(
            kind: PresetKind,
            page: String,
            id: String,
            text: String,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ): PresetSettingsOutcome {
            changes += Triple(kind, id, text)
            return PresetSettingsOutcome.Failure("recorded")
        }
    }

    /** The engine's settings editor, which records what the transfers asked of it. */
    private class RecordingSettingsEditor(
        private val imported: ConfigTransferOutcome = ConfigTransferOutcome.Failure("no import"),
        private val exported: ConfigTransferOutcome = ConfigTransferOutcome.Failure("no export"),
    ) : PresetSettingsEditor by NoSettingsEditor {
        var importedPaths: List<String> = emptyList()
        var importedAnswers: Map<String, ConfigOverwriteAnswer> = emptyMap()
        var exportedDirectory: String? = null
        var exportedNames: Pair<ConfigExportKind, List<String>>? = null

        override suspend fun importPresets(paths: List<String>, answers: Map<String, ConfigOverwriteAnswer>): ConfigTransferOutcome {
            importedPaths = paths
            importedAnswers = answers
            return imported
        }

        override suspend fun exportConfigs(kind: ConfigExportKind, names: List<String>, directory: String): ConfigTransferOutcome {
            exportedDirectory = directory
            exportedNames = kind to names
            return exported
        }
    }

    /** The process tab is covered by ProcessSettingsTest. */
    /** The plate of the last run. */
    private class FakePlateCache(private val kept: CachedPlate?) : PlateCache {
        var written: Pair<String, PlateDescription>? = null

        override fun read(): CachedPlate? = kept

        override fun write(printer: String, description: PlateDescription) {
            written = printer to description
        }

        override fun clear() = Unit
    }

    /** The plate of the last run, which the tests neither keep nor read. */
    private object NoPlateCache : PlateCache {
        override fun read(): CachedPlate? = null

        override fun write(printer: String, description: PlateDescription) = Unit

        override fun clear() = Unit
    }

    private object NoSettingsEditor : PresetSettingsEditor {
        override suspend fun settingsTab(kind: PresetKind) = SettingsTabOutcome.Failure("not used")

        override suspend fun settings(kind: PresetKind, page: String, answers: Map<String, Boolean>, model: ModelSettingsRequest) =
            PresetSettingsOutcome.Failure("not used")

        override suspend fun changeSetting(
            kind: PresetKind,
            page: String,
            id: String,
            text: String,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ) = PresetSettingsOutcome.Failure("not used")

        override suspend fun resetSettings(
            kind: PresetKind,
            page: String,
            ids: List<String>,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ) = PresetSettingsOutcome.Failure("not used")

        override suspend fun pasteModelSettings(clipboard: ModelSettings, target: ModelSettings, parent: ModelSettings?): ModelSettingsOutcome =
            ModelSettingsOutcome.Failure("not used")

        override suspend fun setSettingOverride(kind: PresetKind, page: String, id: String, enabled: Boolean, answers: Map<String, Boolean>) =
            PresetSettingsOutcome.Failure("not used")

        override suspend fun setCompatiblePresets(kind: PresetKind, page: String, key: String, presets: List<String>, answers: Map<String, Boolean>) =
            PresetSettingsOutcome.Failure("not used")

        override suspend fun compatiblePresetChoices(kind: PresetKind, key: String) = PresetNamesOutcome.Failure("not used")

        override suspend fun searchCatalog() = SearchCatalogOutcome.Failure("no catalogue")

        override suspend fun physicalPrinters() = PhysicalPrintersOutcome.Failure("no printers")

        override suspend fun savePhysicalPrinter(printer: PhysicalPrinter, renamedFrom: String?) =
            PhysicalPrintersOutcome.Failure("no printers")

        override suspend fun deletePhysicalPrinter(name: String) = PhysicalPrintersOutcome.Failure("no printers")

        override suspend fun importPresets(paths: List<String>, answers: Map<String, ConfigOverwriteAnswer>) =
            ConfigTransferOutcome.Failure("no import")

        override suspend fun configExportOptions(kind: ConfigExportKind) = ConfigExportOptionsOutcome.Failure("no export")

        override suspend fun exportConfigs(kind: ConfigExportKind, names: List<String>, directory: String) =
            ConfigTransferOutcome.Failure("no export")

        override suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean) =
            PresetComparisonOutcome.Failure("no comparison")

        override suspend fun gcodePlaceholders(kind: PresetKind, key: String) = GcodePlaceholdersOutcome.Failure("no placeholders")

        override suspend fun gcodePlaceholder(key: String, presets: Boolean) = GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true)

        override suspend fun editCustomGcode(kind: PresetKind, page: String, key: String, value: String, answers: Map<String, Boolean>) =
            PresetSettingsOutcome.Failure("not used")

        override suspend fun setRammingParameters(kind: PresetKind, page: String, parameters: String, answers: Map<String, Boolean>) =
            PresetSettingsOutcome.Failure("not used")

        override suspend fun bedShape() = BedShapeOutcome.Failure("no bed shape")

        override suspend fun setBedShape(shape: BedShape, customPath: ModelPath?, answers: Map<String, Boolean>) =
            PresetSettingsOutcome.Failure("no bed shape")

        override suspend fun setSettingsMode(kind: PresetKind, mode: SettingsMode, model: ModelSettingsRequest) =
            PresetSettingsOutcome.Failure("not used")

        override suspend fun setSettingsVariant(
            kind: PresetKind,
            page: String,
            variant: Int,
            answers: Map<String, Boolean>,
            model: ModelSettingsRequest,
        ) = PresetSettingsOutcome.Failure("not used")

        override suspend fun settingTooltip(kind: PresetKind, id: String) = emptyList<OrcaText>()

        override suspend fun checkPresetName(kind: PresetKind, name: String) = PresetNameOutcome.Failure("not used")

        override suspend fun savePreset(kind: PresetKind, name: String) = PresetSettingsOutcome.Failure("not used")

        override suspend fun deletePreset(kind: PresetKind, answers: Map<String, Boolean>) = PresetSettingsOutcome.Failure("not used")
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

        /** The filaments of the plate, which these tests do not change. */
        var filamentCalls = mutableListOf<String>()

        override suspend fun createFilamentOptions(type: String, baseFilament: String) =
            CreateFilamentOptionsOutcome.Failure("not used")

        override suspend fun createFilament(request: CreateFilamentRequest, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun customFilaments() = CustomFilamentsOutcome.Failure("not used")

        override suspend fun filamentPresets(filamentId: String) = FilamentPresetsOutcome.Failure("not used")

        override suspend fun deleteFilamentPreset(preset: String, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun createPrinterOptions(vendor: String, nozzle: String, presetVendor: String, printerPreset: String) =
            CreatePrinterOptionsOutcome.Failure("not used")

        override suspend fun createPrinter(request: CreatePrinterRequest, answers: Map<String, Boolean>) =
            PresetCreationOutcome.Failure("not used")

        override suspend fun addFilament(): PresetsOutcome {
            filamentCalls += "add"
            return presets
        }

        override suspend fun removeFilament(index: Int): PresetsOutcome {
            filamentCalls += "remove:$index"
            return presets
        }

        override suspend fun selectFilament(index: Int, name: ProfileId, action: PresetChangeAction): PresetsOutcome {
            filamentCalls += "select:$index:${name.value}"
            return presets
        }

        override suspend fun setFilamentColor(index: Int, color: String): PresetsOutcome {
            filamentCalls += "color:$index:$color"
            return presets
        }

        override suspend fun selectPreset(choice: PresetChoice, action: PresetChangeAction): PresetsOutcome {
            choices += choice
            return selected(choice)
        }

        /** DiffPresetDialog's Transfer: what it moved, in the order it moved it. */
        val transfers = mutableListOf<PresetTransfer>()

        override suspend fun transferPresetOptions(kind: PresetKind, from: String, to: String, options: List<String>): PresetsOutcome {
            val transfer = PresetTransfer(kind, from, to, options)
            transfers += transfer
            return selected(
                when (kind) {
                    PresetKind.PRINTER -> PresetChoice.Printer(ProfileId(to))
                    PresetKind.FILAMENT -> PresetChoice.Filament(ProfileId(to))
                    else -> PresetChoice.Process(ProfileId(to))
                },
            )
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
        val PART = ObjectPart(
            shape = "Cube",
            type = VolumeType.PART,
            mesh = ScenePath("/scene/objects/part.mesh"),
            placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
        )
        val TWO_FILAMENTS = PRESETS.copy(
            selection = PROFILES.copy(filaments = listOf(ProfileId("filament"), ProfileId("other filament"))),
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
        val CUBE = PlateObject.CalibrationCube(listOf(PlateInstance(INSPECTION)))
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
        val LOADED = LoadedObject(
            name = "a",
            source = ModelPath("/scene/objects/import-0-0-source.mesh"),
            frame = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
            parts = listOf(
                ObjectPart(
                    shape = "",
                    type = VolumeType.PART,
                    mesh = ScenePath("/scene/objects/import-0-0-part-1.mesh"),
                    placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
                    source = ModelPath("/scene/objects/import-0-0-part-1.mesh"),
                    name = "b",
                ),
            ),
            settings = ModelSettings(),
            volume = ObjectVolume("a body", ModelSettings(mapOf("extruder" to "2"))),
            instances = listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/import-0-0.mesh")))),
        )
        val NOTICE = SettingsDialog("zero_volume", DialogIcon.INFO, emptyList(), listOf(OrcaText("Objects with zero volume removed")), false, null, null)
        val QUESTION = SettingsDialog("multipart_object", DialogIcon.WARNING, emptyList(), listOf(OrcaText("several objects")), true, null, null)
    }
}

/** The one copy of a test object, which every test but the instance ones has. */
private val PlateObject.inspection: ModelInspection get() = instances.first().inspection

private val PlateObject.autoDrop: Boolean get() = instances.first().autoDrop

private val PlateObject.printable: Boolean get() = instances.first().printable

private val PlateObject.placing: Boolean get() = instances.first().placing

/** The object with its one copy placed anew. */
private fun PlateObject.withInspection(inspection: ModelInspection): PlateObject =
    withInstance(0, instances.first().copy(inspection = inspection))

/** The tests name the one copy of an object by its mesh file. */
private operator fun PlacePlateObjectUseCase.invoke(mesh: ScenePath, placement: Transform3, manipulation: Manipulation = Manipulation.Move) =
    this(PlateInstanceId(mesh), placement, manipulation)

private operator fun SetPlateObjectAutoDropUseCase.invoke(mesh: ScenePath, autoDrop: Boolean) = this(PlateInstanceId(mesh), autoDrop)

private operator fun SetPlateObjectPrintableUseCase.invoke(mesh: ScenePath, printable: Boolean) = this(PlateInstanceId(mesh), printable)
