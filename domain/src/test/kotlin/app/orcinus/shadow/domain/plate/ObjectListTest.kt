package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsScope
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import java.io.StringReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObjectListTest {
    @Test
    fun `a volume picked among objects is refused, and the objects stay selected`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE, OTHER), selectedInstances = setOf(PlateInstanceId(CUBE.mesh))))

        PickListItemUseCase(repository).part(ObjectPartId(CUBE.mesh, 1))

        val state = repository.state.value
        assertEquals(setOf(PlateInstanceId(CUBE.mesh)), state.selectedInstances)
        assertNull(state.selectedPart)
        val notice = state.plateNotices.single()
        assertEquals("selection_conflicts", notice.id)
        assertEquals("If the first selected item is an object, the second should also be an object.", notice.text.last().msgid)
    }

    @Test
    fun `volumes of one object are picked together, and one of another object is refused`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE, OTHER)))
        val pick = PickListItemUseCase(repository)

        pick.part(ObjectPartId(CUBE.mesh, 1))
        pick.part(ObjectPartId(CUBE.mesh, 0))
        pick.part(ObjectPartId(OTHER.mesh, 1))

        val state = repository.state.value
        assertEquals(listOf(ObjectPartId(CUBE.mesh, 0), ObjectPartId(CUBE.mesh, 1)), state.selectedParts())
        assertEquals("If the first selected item is a part, the second should be a part in the same object.", state.plateNotices.single().text.last().msgid)
    }

    @Test
    fun `an object picked among volumes leaves them, and a copy is refused`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE, OTHER)))
        val pick = PickListItemUseCase(repository)
        pick.part(ObjectPartId(CUBE.mesh, 1))

        pick.copy(PlateInstanceId(OTHER.mesh), copyRow = false)
        assertEquals(listOf(ObjectPartId(CUBE.mesh, 1)), repository.state.value.selectedParts())
        assertTrue(repository.state.value.plateNotices.isEmpty())

        pick.copy(PlateInstanceId(OTHER.mesh), copyRow = true)
        assertEquals(listOf(ObjectPartId(CUBE.mesh, 1)), repository.state.value.selectedParts())
        assertEquals(1, repository.state.value.plateNotices.size)
    }

    @Test
    fun `copies join the selection and leave it`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE, OTHER)))
        val pick = PickListItemUseCase(repository)

        pick.copy(PlateInstanceId(CUBE.mesh), copyRow = false)
        pick.copy(PlateInstanceId(OTHER.mesh), copyRow = false)
        assertEquals(setOf(PlateInstanceId(CUBE.mesh), PlateInstanceId(OTHER.mesh)), repository.state.value.selectedInstances)

        pick.copy(PlateInstanceId(CUBE.mesh), copyRow = false)
        assertEquals(setOf(PlateInstanceId(OTHER.mesh)), repository.state.value.selectedInstances)
    }

    @Test
    fun `ranges of one object are picked, copied and deleted together`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE, OTHER)))
        val pick = PickListItemUseCase(repository)

        pick.range(LayerRangeId(CUBE.mesh, 0))
        pick.range(LayerRangeId(CUBE.mesh, 2))
        pick.range(LayerRangeId(OTHER.mesh, 0))
        pick.part(ObjectPartId(CUBE.mesh, 0))

        assertEquals(listOf(LayerRangeId(CUBE.mesh, 0), LayerRangeId(CUBE.mesh, 2)), repository.state.value.selectedRanges())
        assertEquals(2, repository.state.value.plateNotices.size)

        CopyLayerRangesUseCase(repository).selected()
        assertEquals(listOf(RANGES[0], RANGES[2]), repository.state.value.listClipboard?.ranges)

        RemoveLayerRangeUseCase(repository).selected()
        val state = repository.state.value
        assertEquals(listOf(RANGES[1]), state.objects.first().layerRanges)
        assertEquals(1, state.history.undo.size)
        assertTrue(state.selectedRanges().isEmpty())
    }

    @Test
    fun `a range picked alone again leaves the ones picked with it`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE)))
        val pick = PickListItemUseCase(repository)
        pick.range(LayerRangeId(CUBE.mesh, 0))
        pick.range(LayerRangeId(CUBE.mesh, 1))

        SelectLayerRangeUseCase(repository)(LayerRangeId(CUBE.mesh, 1))

        assertEquals(listOf(LayerRangeId(CUBE.mesh, 1)), repository.state.value.selectedRanges())
    }

    @Test
    fun `the Layers row deletes every range of its object as one step`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE)))

        RemoveLayerRangeUseCase(repository).all(CUBE.mesh)

        val state = repository.state.value
        assertTrue(state.objects.single().layerRanges.isEmpty())
        assertEquals(1, state.history.undo.size)
    }

    @Test
    fun `a painting names the kinds it holds, and one without names is colour`() {
        assertEquals(setOf(PaintKind.SUPPORTS, PaintKind.COLOR), paintedKinds(StringReader("supports=0a0b;color=00ff")))
        assertEquals(setOf(PaintKind.SEAM, PaintKind.FUZZY_SKIN), paintedKinds(StringReader("seam=01;fuzzy_skin=02")))
        assertEquals(setOf(PaintKind.COLOR), paintedKinds(StringReader("0a0b0c")))
        assertTrue(paintedKinds(StringReader("")).isEmpty())
    }

    @Test
    fun `an object sinks while a copy reaches below the bed`() {
        assertFalse(CUBE.isSinking)
        val sunk = CUBE.withInstanceAt(1, INSPECTION.copy(boxCenter = Vector3(40.0, 0.0, 5.0)))
        assertTrue(sunk.isSinking)
    }

    @Test
    fun `Edit Process Settings switches to the objects, blinks the arrow and tells of it until turned off`() {
        val repository = FakeRepository(PlateState())

        SetSettingsScopeUseCase(repository, objectProcessTipsOff = { false }).objectProcess()
        val state = repository.state.value
        assertEquals(SettingsScope.OBJECT, state.settingsScope)
        assertEquals(1, state.objectProcessHints)
        assertEquals(AppConfigKeys.DO_NOT_SHOW_OBJECT_PROCESS_TIPS, state.plateNotices.single().id)

        SetSettingsScopeUseCase(repository, objectProcessTipsOff = { true }).objectProcess()
        assertEquals(2, repository.state.value.objectProcessHints)
        assertEquals(1, repository.state.value.plateNotices.size)
    }

    @Test
    fun `the plate menu's Text makes an object of its own with nothing selected`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE), selectedInstances = setOf(PlateInstanceId(CUBE.mesh))))

        RequestEmbossUseCase(repository).addObject(EmbossKind.TEXT)

        val state = repository.state.value
        assertEquals(EmbossRequest.Add(EmbossKind.TEXT, null, VolumeType.PART), state.embossRequest)
        assertTrue(state.selectedInstances.isEmpty())
    }

    @Test
    fun `a paint column selects its object and asks the canvas for the tool`() {
        val repository = FakeRepository(PlateState(objects = listOf(CUBE, OTHER), selectedInstances = setOf(PlateInstanceId(OTHER.mesh))))
        val requests = CanvasRequestsUseCase(repository)

        requests.paint(CUBE.mesh, PaintKind.SUPPORTS)
        assertEquals(setOf(PlateInstanceId(CUBE.mesh)), repository.state.value.selectedInstances)
        assertEquals(PaintKind.SUPPORTS, repository.state.value.paintingRequest)

        requests.paintingTaken()
        assertNull(repository.state.value.paintingRequest)
    }

    private class FakeRepository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    private companion object {
        val IDENTITY = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 })
        val INSPECTION = ModelInspection(
            facetCount = 12,
            dimensions = ModelDimensions(20.0, 20.0, 20.0),
            boxCenter = Vector3(0.0, 0.0, 10.0),
            mesh = ScenePath("/scene/objects/cube.mesh"),
            placement = IDENTITY,
            fit = BuildVolumeFit.INSIDE,
            boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
            rotationDegrees = Vector3(0.0, 0.0, 0.0),
            unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
        )
        val PART = ObjectPart(shape = "Cube", type = VolumeType.PART, mesh = ScenePath("/scene/objects/part.mesh"), placement = IDENTITY)
        val RANGES = listOf(LayerRange(0.0, 2.0), LayerRange(2.0, 4.0), LayerRange(4.0, 6.0))
        val CUBE = PlateObject.CalibrationCube(
            listOf(PlateInstance(INSPECTION), PlateInstance(INSPECTION.copy(boxCenter = Vector3(40.0, 0.0, 10.0)))),
            parts = listOf(PART),
            layerRanges = RANGES,
        )
        val OTHER = PlateObject.CalibrationCube(
            listOf(PlateInstance(INSPECTION.copy(mesh = ScenePath("/scene/objects/other.mesh")))),
            parts = listOf(PART.copy(mesh = ScenePath("/scene/objects/other-part.mesh"))),
            layerRanges = RANGES,
        )

        fun PlateObject.withInstanceAt(index: Int, inspection: ModelInspection): PlateObject = when (this) {
            is PlateObject.CalibrationCube -> copy(instances = instances.mapIndexed { at, copy -> if (at == index) copy.copy(inspection = inspection) else copy })
            is PlateObject.ImportedModel -> copy(instances = instances.mapIndexed { at, copy -> if (at == index) copy.copy(inspection = inspection) else copy })
        }
    }
}
