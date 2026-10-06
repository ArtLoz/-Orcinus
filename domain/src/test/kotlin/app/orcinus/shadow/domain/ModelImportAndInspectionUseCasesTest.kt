package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.CalibrationPrinterOutcome
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPartsOutcome
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlowRateCalibration
import app.orcinus.shadow.core.model.FlushVolumes
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeshExportOutcome
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjColorChoice
import app.orcinus.shadow.core.model.ObjectCut
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintPlacement
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintedSurface
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlacedInstance
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SimplifyOutcome
import app.orcinus.shadow.core.model.SlicedPlates
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.StepMeshOptions
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.WipeTowerOutcome
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

        val actual = runSuspend { InspectModelUseCase(inspector)(ModelSource.LocalFile(ModelPath("")), PROFILES, MESH, emptyList()) }

        assertIs<ModelInspectionOutcome.Failure>(actual)
        assertEquals(null, inspector.model)
    }

    @Test
    fun `a model, the profiles, the mesh file and the plate are delegated to the inspection port`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val model = ModelSource.LocalFile(ModelPath("/internal/imports/model.stl"))
        val plate = listOf(PlacedModel(ModelSource.LocalFile(ModelPath("/internal/imports/other.stl")), ScenePath("/scene/objects/other.mesh"), IDENTITY))

        val actual = runSuspend { InspectModelUseCase(inspector)(model, PROFILES, MESH, plate) }

        assertEquals(VALID_INSPECTION, actual)
        assertEquals(model, inspector.model)
        assertEquals(PROFILES, inspector.profiles)
        assertEquals(MESH, inspector.mesh)
        assertEquals(plate, inspector.plate)
    }

    @Test
    fun `a placement is delegated to the inspection port`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 })

        val actual = runSuspend {
            PlaceModelUseCase(inspector)(PLACED, PROFILES, placement, placement, true, Manipulation.Rotate)
        }

        assertEquals(VALID_INSPECTION, actual)
        assertEquals(placement, inspector.placement)
        assertEquals(MESH, inspector.mesh)
    }

    @Test
    fun `a job for the plate is delegated to the inspection port`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val plate = listOf(PlacedModel(ModelSource.LocalFile(ModelPath("/imports/model.stl")), MESH, IDENTITY))

        val actual = runSuspend { PlaceModelsUseCase(inspector)(plate, PROFILES, PlateManipulation.AutoOrient()) }

        assertIs<PlateInspectionOutcome.Success>(actual)
        assertEquals(plate, inspector.plate)
    }

    @Test
    fun `a job for an empty plate or for a placement that is not finite is rejected before the engine`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val broken = Transform3(List(16) { if (it == 12) Double.NaN else 0.0 })

        val empty = runSuspend { PlaceModelsUseCase(inspector)(emptyList(), PROFILES, PlateManipulation.AutoOrient()) }
        val infinite = runSuspend {
            PlaceModelsUseCase(inspector)(listOf(PlacedModel(ModelSource.LocalFile(ModelPath("/imports/model.stl")), MESH, broken)), PROFILES, PlateManipulation.AutoOrient())
        }

        assertIs<PlateInspectionOutcome.Failure>(empty)
        assertIs<PlateInspectionOutcome.Failure>(infinite)
        assertEquals(null, inspector.plate)
    }

    @Test
    fun `a placement that is not finite is rejected before the engine`() {
        val inspector = RecordingInspector(VALID_INSPECTION)
        val placement = Transform3(List(16) { if (it == 12) Double.NaN else 0.0 })

        val actual = runSuspend {
            PlaceModelUseCase(inspector)(PLACED, PROFILES, placement, placement, true, Manipulation.Move)
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
        override suspend fun beginPainting(
            plateObject: PlacedModel,
            kind: PaintKind,
            profiles: SlicingProfileSelection,
            meshPrefix: ScenePath,
            placement: PaintPlacement,
        ): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())

        override suspend fun paint(stroke: PaintStroke, meshPrefix: ScenePath): PaintingOutcome =
            PaintingOutcome.Success(PaintedSurface())

        override suspend fun fuzzySkinDisabled(plateObject: PlacedModel, profiles: SlicingProfileSelection): Boolean = false

        override suspend fun endPainting(): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())

        override suspend fun undoPainting(meshPrefix: ScenePath): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())

        override suspend fun redoPainting(meshPrefix: ScenePath): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())

        override suspend fun clearPainting(meshPrefix: ScenePath): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())

        override suspend fun setGapFill(gapArea: Double?, meshPrefix: ScenePath): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())

        override suspend fun fillGaps(meshPrefix: ScenePath): PaintingOutcome = PaintingOutcome.Success(PaintedSurface())
        override suspend fun describeFlushVolumes(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plateSettings: ModelSettings,
        ): FlushVolumesOutcome = FlushVolumesOutcome.Success(FlushVolumes())

        override suspend fun updateFlushVolumes(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plateSettings: ModelSettings,
            change: FlushVolumesChange,
            index: Int,
        ): FlushVolumesOutcome = FlushVolumesOutcome.Success(FlushVolumes())

        override suspend fun describeWipeTower(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plateSettings: ModelSettings,
        ): WipeTowerOutcome = WipeTowerOutcome.Success(WipeTower())
        var model: ModelSource? = null
        var profiles: SlicingProfileSelection? = null
        var mesh: ScenePath? = null
        var placement: Transform3? = null
        var plate: List<PlacedModel>? = null

        override suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath) =
            PlateDescriptionOutcome.Failure("not used")

        override suspend fun inspect(model: ModelSource, profiles: SlicingProfileSelection, mesh: ScenePath, plate: List<PlacedModel>): ModelInspectionOutcome {
            this.model = model
            this.profiles = profiles
            this.mesh = mesh
            this.plate = plate
            return outcome
        }

        override suspend fun load(
            sources: List<ModelPath>,
            profiles: SlicingProfileSelection,
            plate: List<PlacedModel>,
            prefix: ScenePath,
            answers: Map<String, Boolean>,
            load: ModelLoad,
            chosen: Boolean,
            stepMeshes: Map<Int, StepMeshOptions>,
            askMulti: Boolean,
            objColors: Map<Int, ObjColorChoice>,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun stepTriangleCount(source: ModelPath, linearDeflection: Double, angleDeflection: Double) = 0L

        override suspend fun stopStepTriangleCount() = Unit

        override suspend fun releaseStepFile() = Unit

        override suspend fun edit(
            plate: List<PlacedModel>,
            index: Int,
            edit: ObjectEdit,
            volume: Int?,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
            answers: Map<String, Boolean>,
            cut: ObjectCut?,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun beginCut(plateObject: PlacedModel, instance: Int, profiles: SlicingProfileSelection): CutObjectOutcome =
            CutObjectOutcome.Failure("not used")

        override suspend fun describeCutPlane(
            plane: Transform3,
            connectors: List<CutConnector>,
            snapSpace: Double,
            snapBulge: Double,
            groove: CutGroove?,
            preview: Boolean,
            parts: CutPartSelection?,
            meshPrefix: ScenePath,
        ): CutPlaneOutcome = CutPlaneOutcome.Failure("not used")

        override suspend fun selectCutPart(parts: CutPartSelection, origin: Vector3, direction: Vector3, meshPrefix: ScenePath): CutPartsOutcome =
            CutPartsOutcome.Failure("not used")

        override suspend fun endCut() = Unit

        override suspend fun copy(
            plate: List<PlacedModel>,
            sources: List<PlacedModel>,
            count: Int,
            placement: CopyPlacement,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun addPrimitive(
            plate: List<PlacedModel>,
            shape: String,
            name: String,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun handyModel(file: String): ModelPath? = null

        override suspend fun prepareCalibration(params: CalibrationParams, profiles: SlicingProfileSelection, prefix: ScenePath) =
            ModelLoadOutcome.Failure("not used")

        override suspend fun prepareFlowRateCalibration(test: FlowRateCalibration, profiles: SlicingProfileSelection, prefix: ScenePath) =
            ModelLoadOutcome.Failure("not used")

        override suspend fun describeCalibrationPrinter(profiles: SlicingProfileSelection) = CalibrationPrinterOutcome.Failure("not used")

        override suspend fun saveProject(
            path: ScenePath,
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            plates: List<ProjectPlate>,
            projectInfo: ScenePath?,
            currentPlate: Int,
            sliced: SlicedPlates,
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
        ) = MeshExportOutcome.Failure("not used")

        override suspend fun replaceVolume(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            source: ModelPath,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
            stepMesh: StepMeshOptions?,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun simplifyVolume(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            config: SimplifyConfig,
            profiles: SlicingProfileSelection,
            path: ScenePath,
        ) = SimplifyOutcome.Failure("not used")

        override suspend fun setVolumeType(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            type: VolumeType,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun applySimplify(
            plate: List<PlacedModel>,
            index: Int,
            volume: Int,
            config: SimplifyConfig,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun pasteVolumes(
            plate: List<PlacedModel>,
            index: Int,
            instance: Int,
            source: PlacedModel,
            volumes: List<Int>,
            sameInputFile: Boolean,
            profiles: SlicingProfileSelection,
            prefix: ScenePath,
        ) = ModelLoadOutcome.Failure("not used")

        override suspend fun place(
            plateObject: PlacedModel,
            profiles: SlicingProfileSelection,
            previous: Transform3,
            placement: Transform3,
            autoDrop: Boolean,
            manipulation: Manipulation,
            instance: Int,
        ): ModelInspectionOutcome {
            this.model = plateObject.model
            this.mesh = plateObject.mesh
            this.placement = placement
            return outcome
        }

        override suspend fun placeObjects(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            manipulation: PlateManipulation,
        ): PlateInspectionOutcome {
            this.plate = plate
            return PlateInspectionOutcome.Success(plate.map { listOf((outcome as ModelInspectionOutcome.Success).inspection.copy(mesh = it.mesh)) })
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
        val IDENTITY = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 })
        val PLACED = PlacedModel(ModelSource.LocalFile(ModelPath("/imports/model.stl")), MESH, IDENTITY)
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

/**
 * The tests name the one copy every object in them has, as the app did before
 * OrcaSlicer's instances were ported.
 */
private fun PlacedModel(model: ModelSource, mesh: ScenePath, placement: Transform3, autoDrop: Boolean = true) =
    PlacedModel(model, mesh, listOf(PlacedInstance(placement, autoDrop)))
