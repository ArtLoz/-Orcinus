package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EnginePlate
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateJob
import app.orcinus.shadow.core.model.PlateJobProgress
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.domain.PlaceModelsUseCase
import app.orcinus.shadow.slicing.api.PlateInspector
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PlateJobsTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `a job shows its progress until it ends, and a cancelled one places nothing`() {
        val cube = cubeAt(175.0, 175.0)
        val repository = FakeRepository(state(listOf(cube), plates = 1).copy(problem = PlateProblem(PlateProblemKind.NO_ARRANGEABLE_OBJECTS)))
        val inspector = JobInspector()
        val place = PlacePlateObjectsUseCase(PlaceModelsUseCase(inspector), repository, scope)

        // ArrangeJob::process() and prepare(): "Arranging", "Arranging...", and the plate's info closed.
        place(PlateManipulation.Arrange(ArrangeSettings()))
        val first = inspector.jobs.single()
        assertEquals(PlateJobProgress(PlateJob.ARRANGE), repository.state.value.plateJob)
        assertEquals(first, repository.state.value.arrangeOngoing)
        assertNull(repository.state.value.problem)
        inspector.progress!!(PlateJobProgress(PlateJob.ARRANGE, 50, "cube"))
        assertEquals(PlateJobProgress(PlateJob.ARRANGE, 50, "cube"), repository.state.value.plateJob)

        // Worker::cancel(): the engine is told, and finalize() places nothing; "Arranging..." stays.
        place.cancel()
        assertEquals(listOf(first), inspector.cancelled)
        inspector.answer.complete(PlateInspectionOutcome.Cancelled)
        val cancelled = repository.state.value
        assertNull(cancelled.plateJob)
        assertEquals(first, cancelled.arrangeOngoing)
        assertEquals(cube.instances.single().inspection, cancelled.objects.single().instances.single().inspection)
        assertTrue(cancelled.objects.none(PlateObject::placing))
        place.cancel()
        assertEquals(listOf(first), inspector.cancelled)

        // A finished arrangement closes "Arranging..." and tells of the objects without area.
        inspector.answer = CompletableDeferred()
        place(PlateManipulation.ArrangePlate(ArrangeSettings()))
        val second = inspector.jobs.last()
        assertTrue(second > first)
        assertEquals(second, repository.state.value.arrangeOngoing)
        val moved = cube.instances.single().inspection.copy(boxCenter = Vector3(100.0, 100.0, 10.0))
        inspector.answer.complete(PlateInspectionOutcome.Success(listOf(listOf(moved)), zeroSizeObjects = listOf("flat")))
        val arranged = repository.state.value
        assertNull(arranged.plateJob)
        assertNull(arranged.arrangeOngoing)
        assertEquals(listOf("flat"), arranged.zeroSizeObjects)
        assertEquals(moved, arranged.objects.single().instances.single().inspection)
        DismissPlateProblemUseCase(repository).zeroSizeObject(0)
        assertTrue(repository.state.value.zeroSizeObjects.isEmpty())
    }

    @Test
    fun `a bed fill goes on as an arrangement, which pushes Arranging`() {
        val repository = FakeRepository(state(listOf(cubeAt(175.0, 175.0)), plates = 1))
        val inspector = JobInspector()
        val place = PlacePlateObjectsUseCase(PlaceModelsUseCase(inspector), repository, scope)

        place(PlateManipulation.FillBed(ScenePath("/scene/objects/cube.mesh"), null, ArrangeSettings()))
        assertEquals(PlateJobProgress(PlateJob.FILL_BED), repository.state.value.plateJob)
        assertNull(repository.state.value.arrangeOngoing)
        inspector.progress!!(PlateJobProgress(PlateJob.ARRANGE, 0))
        assertEquals(inspector.jobs.single(), repository.state.value.arrangeOngoing)
        // Judging the fit for another printer is no job.
        inspector.answer.complete(PlateInspectionOutcome.Cancelled)
        inspector.answer = CompletableDeferred()
        repository.update { it.copy(enginePlate = EnginePlate(0, 2)) }
        place.judgeOn(EnginePlate(0, 1))
        assertTrue(repository.state.value.objects.all(PlateObject::placing))
        assertNull(repository.state.value.plateJob)
    }

    @Test
    fun `a copy that joins a plate printing in spiral vase mode takes its settings`() {
        val spiral = state(listOf(cubeAt(420.0 + 175.0, 175.0)), plates = 2).let { state ->
            state.copy(plates = listOf(PartPlate(), PartPlate(settings = ModelSettings(mapOf("spiral_mode" to "1")))))
        }
        val mesh = spiral.objects.single().mesh
        val stood = cubeAt(175.0, 175.0).instances.single()

        // Moved over from the first plate: the message, and the settings.
        val moved = spiral.joiningPlate(mesh, 0, stood)
        assertEquals("1", moved.objects.single().settings.values["wall_loops"])
        assertEquals("0%", moved.objects.single().settings.values["sparse_infill_density"])
        assertEquals(listOf("spiral_mode"), moved.plateNotices.map { it.id })
        // A new object takes them without a word.
        val added = spiral.joiningPlate(mesh, 0, null)
        assertEquals("1", added.objects.single().settings.values["wall_loops"])
        assertTrue(added.plateNotices.isEmpty())
        // A copy that still crosses the plate it stood on stays with it.
        assertEquals(spiral, spiral.joiningPlate(mesh, 0, cubeAt(420.0 - 5.0, 175.0).instances.single()))
        // A plate that prints by the process preset changes nothing.
        val plain = spiral.copy(plates = listOf(PartPlate(), PartPlate()))
        assertEquals(plain, plain.joiningPlate(mesh, 0, stood))
    }

    /** The engine's placing jobs: each waits for [answer], telling [progress] meanwhile. */
    private class JobInspector : PlateInspector by unexpected() {
        var answer = CompletableDeferred<PlateInspectionOutcome>()
        var progress: ((PlateJobProgress) -> Unit)? = null
        val jobs = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()

        override suspend fun placeObjects(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            manipulation: PlateManipulation,
            job: Long,
            progress: (PlateJobProgress) -> Unit,
        ): PlateInspectionOutcome {
            jobs += job
            this.progress = progress
            return answer.await()
        }

        override suspend fun placeObjects(
            plate: List<PlacedModel>,
            profiles: SlicingProfileSelection,
            manipulation: PlateManipulation,
        ): PlateInspectionOutcome = answer.await()

        override fun cancelPlacement(job: Long) {
            cancelled += job
        }
    }

    private class FakeRepository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    private companion object {
        val AREA = listOf(Point2(0.0, 0.0), Point2(350.0, 0.0), Point2(350.0, 350.0), Point2(0.0, 350.0))
        val PROFILES = SlicingProfileSelection(ProfileId("printer"), ProfileId("filament"), ProfileId("process"))

        /** Every call the test does not expect fails. */
        fun unexpected(): PlateInspector = Proxy.newProxyInstance(PlateInspector::class.java.classLoader, arrayOf(PlateInspector::class.java)) { _, method, _ ->
            error("Unexpected ${method.name}")
        } as PlateInspector

        fun state(objects: List<PlateObject>, plates: Int) = PlateState(
            plate = PlateDescription(
                PlateGeometry(AREA, 350.0, emptyList(), emptyList(), emptyList(), emptyList(), null, null),
                ColorRgba(1f, 1f, 1f),
            ),
            presets = Presets(
                selection = PROFILES,
                setupRequired = false,
                printers = emptyList(),
                filaments = emptyList(),
                processes = emptyList(),
                nozzleDiameters = listOf("0.4"),
                nozzleDiameter = "0.4",
            ),
            objects = objects,
            plates = List(plates) { PartPlate() },
            enginePlate = EnginePlate(0, plates),
        )

        fun cubeAt(x: Double, y: Double) = PlateObject.CalibrationCube(
            listOf(
                PlateInstance(
                    ModelInspection(
                        facetCount = 12,
                        dimensions = ModelDimensions(20.0, 20.0, 20.0),
                        boxCenter = Vector3(x, y, 10.0),
                        mesh = ScenePath("/scene/objects/cube.mesh"),
                        placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }.toMutableList().also { it[12] = x; it[13] = y }),
                        fit = BuildVolumeFit.INSIDE,
                        boundingSphere = BoundingSphere(Vector3(x, y, 10.0), 17.32),
                        rotationDegrees = Vector3(0.0, 0.0, 0.0),
                        unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
                    ),
                ),
            ),
        )
    }
}
