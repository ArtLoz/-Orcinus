package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BrimEarHit
import app.orcinus.shadow.core.model.BrimEarsOutcome
import app.orcinus.shadow.core.model.BrimPoint
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ClippingPlane
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateHistory
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.slicing.api.BrimEarsEditor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The undo stacks of the tools: the brim ears tool's own stack
 * (enter_gizmos_stack()), the steps of the measuring and assembly tools made
 * one (reduce_noisy_snapshots()), and check_gizmos_closed_except().
 */
class GizmoStacksTest {
    @Test
    fun `the brim ears tool undoes on a stack of its own, and leaves its ears as one step`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE)).recorded())
        val mainUndo = repository.state.value.history.undo
        val tool = EditBrimEarsUseCase(NO_EDITOR, repository)

        tool.enter()
        tool.commit(CUBE.mesh, listOf(EAR))
        tool.commit(CUBE.mesh, listOf(EAR, OTHER_EAR))

        val inside = repository.state.value.history
        assertEquals(2, inside.undo.size)
        assertSame(mainUndo, inside.main?.undo)
        assertEquals(emptyList(), inside.main?.beforeTool?.objects?.single()?.brimPoints)

        tool.leave()

        val left = repository.state.value
        assertNull(left.history.main)
        assertEquals(mainUndo.size + 1, left.history.undo.size)
        assertEquals(emptyList(), left.history.undo.last().objects.single().brimPoints)
        assertTrue(left.history.undo.last().gizmoAction)
        assertEquals(listOf(EAR, OTHER_EAR), left.objects.single().brimPoints)
    }

    @Test
    fun `a brim ears session that leaves the ears as they were adds no step`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE)))
        val tool = EditBrimEarsUseCase(NO_EDITOR, repository)

        tool.enter()
        tool.commit(CUBE.mesh, listOf(EAR))
        tool.commit(CUBE.mesh, emptyList())
        tool.leave()

        assertEquals(PlateHistory(), repository.state.value.history)
    }

    @Test
    fun `the measuring tool's steps in a row since it opened are one, from before the first`() {
        val before = PlateState(objects = listOf(CUBE)).snapshot()
        val entered = before.copy(currentPlate = 0)
        val first = before.copy(gizmoAction = true)
        val second = before.copy(gizmoAction = true, selectedInstances = setOf(PlateInstanceId(CUBE.mesh)))
        val action = before.copy(selectedPart = ObjectPartId(CUBE.mesh, 0))
        val third = before.copy(gizmoAction = true, selectedPart = ObjectPartId(CUBE.mesh, 0))
        val history = PlateHistory(undo = listOf(entered, first, second, action, third), redo = listOf(before))

        val reduced = history.reducedNoisySnapshots(entered)

        assertEquals(listOf(entered, first, action, third), reduced.undo)
        assertSame(first, reduced.undo[1])
        assertTrue(reduced.redo.isEmpty())
        // The stack undone past where the tool was entered keeps what it has.
        assertSame(history, history.reducedNoisySnapshots(before.copy(currentPlate = 0, gizmoAction = true)))
        assertEquals(listOf(first), PlateHistory(undo = listOf(first, second)).reducedNoisySnapshots(null).undo)
    }

    @Test
    fun `simplify and replacing a volume wait for the tools to close, simplify opening again on its own`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE), gizmoOpen = true))
        val simplify = OpenSimplifyUseCase(repository)

        simplify.ofObject(PlateInstanceId(CUBE.mesh), wholeObject = false)

        assertNull(repository.state.value.simplifyTarget)
        assertEquals("gizmos_open", repository.state.value.plateNotices.single().id)
        assertFalse(repository.gizmosClosed())
        assertEquals("close_gizmos", repository.state.value.plateNotices.last().id)

        // The gizmo itself is the tool open: it closes and opens again on the selection.
        repository.update { it.copy(simplifyTarget = ObjectPartId(CUBE.mesh, 0), plateNotices = emptyList()) }
        simplify.ofVolume(ObjectPartId(CUBE.mesh, 0))

        assertEquals(ObjectPartId(CUBE.mesh, 0), repository.state.value.simplifyTarget)
        assertTrue(repository.state.value.plateNotices.isEmpty())

        repository.update { it.copy(gizmoOpen = false) }
        assertTrue(repository.gizmosClosed())
    }

    private class FakeRepository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    private companion object {
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
        val CUBE: PlateObject = PlateObject.CalibrationCube(listOf(PlateInstance(INSPECTION)))
        val EAR = BrimPoint(Vector3(10.0, 0.0, 0.0), 4.0)
        val OTHER_EAR = BrimPoint(Vector3(-10.0, 0.0, 0.0), 4.0)

        /** The tool's engine, which the stacks do not call. */
        val NO_EDITOR = object : BrimEarsEditor {
            override suspend fun beginBrimEars(plate: List<PlacedModel>, index: Int, instance: Int, profiles: SlicingProfileSelection) =
                BrimEarsOutcome.Failure("No engine")

            override suspend fun hitBrimEars(origin: Vector3, direction: Vector3, clipping: ClippingPlane?): BrimEarHit? = null

            override suspend fun generateBrimEars(points: List<BrimPoint>, maxAngle: Double, detectionRadius: Double, headDiameter: Double) = points

            override suspend fun checkBrimEars(points: List<BrimPoint>) = emptyList<Int>()

            override suspend fun endBrimEars() = Unit
        }
    }
}
