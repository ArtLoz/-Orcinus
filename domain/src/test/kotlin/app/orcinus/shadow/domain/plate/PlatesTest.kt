package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.LayerSequence
import app.orcinus.shadow.core.model.plateSettingsChoice
import app.orcinus.shadow.core.model.withPlateSettingsChoice
import app.orcinus.shadow.core.model.withName
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EnginePlate
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.PlateGrid
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.listPlateOf
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PlatesTest {
    @Test
    fun `the plates stand in rows of the columns OrcaSlicer counts, a fifth of a plate apart`() {
        val grid = PlateGrid(AREA)
        assertEquals(listOf(1, 2, 2, 2, 3, 3, 3, 3, 3, 4), (1..10).map(PlateGrid::columns))
        assertEquals(Point2(420.0, 0.0), grid.originOf(1, 2))
        assertEquals(Point2(420.0, -420.0), grid.originOf(4, 5))
        // compute_origin_for_unprintable(): a full square of plates puts it in a new column.
        assertEquals(Point2(840.0, -420.0), grid.unprintableOrigin(4))
        assertEquals(Point2(0.0, -420.0), grid.unprintableOrigin(2))
    }

    @Test
    fun `a plate that needs another column moves the plates and what stands on them`() {
        // The fourth plate of four stands in the second row; a fifth one makes three columns.
        val cube = cubeAt(420.0 + 175.0, -420.0 + 175.0)
        val repository = FakeRepository(state(listOf(cube), plates = 4, current = 3))

        AddPlateUseCase(repository)()

        val state = repository.state.value
        assertEquals(5, state.plates.size)
        assertEquals(4, state.currentPlate)
        assertEquals(175.0, state.objects.single().instances.single().inspection.placement.columns[12])
        assertEquals(-245.0, state.objects.single().instances.single().inspection.placement.columns[13])
        assertEquals(1, state.history.undo.size)
    }

    @Test
    fun `deleting a plate sends its objects off the plates and the next plate takes its place`() {
        val kept = ModelSettings(mapOf("curr_bed_type" to "High Temp Plate"))
        val cube = cubeAt(175.0, 175.0)
        val repository = FakeRepository(
            state(listOf(cube), plates = 2, current = 0).copy(plates = listOf(PartPlate(), PartPlate(settings = kept))),
        )

        DeletePlateUseCase(repository)(0)

        val state = repository.state.value
        assertEquals(1, state.plates.size)
        assertEquals(0, state.currentPlate)
        assertEquals(kept, state.plateSettings)
        // compute_origin_for_unprintable() of one plate: beside it.
        assertEquals(595.0, state.objects.single().instances.single().inspection.placement.columns[12])
    }

    @Test
    fun `another plate brings its own settings and G-code, and the one left keeps its own`() {
        val own = ModelSettings(mapOf("curr_bed_type" to "Textured PEI Plate"))
        val cube = cubeAt(175.0, 175.0)
        val sliced = PlateSliceResult(SliceJobId("first"), listOf(cube), OutputPath("/gcode/first.gcode"), SliceStatistics(1, 1, 1.0))
        val repository = FakeRepository(
            state(listOf(cube), plates = 2, current = 0).copy(plates = listOf(PartPlate(), PartPlate(settings = own)), result = sliced),
        )

        SelectPlateUseCase(repository)(1)
        val second = repository.state.value
        assertEquals(own, second.plateSettings)
        assertNull(second.result)

        // Once the engine knows the second plate, the first one can be selected again.
        repository.update { it.copy(enginePlate = EnginePlate(1, 2)) }
        SelectPlateUseCase(repository)(0)
        assertEquals(sliced, repository.state.value.result)
        assertEquals(ModelSettings(), repository.state.value.plateSettings)
    }

    @Test
    fun `arranging keeps the plates it needed and recycles the empty ones at the end`() {
        val first = cubeAt(175.0, 175.0)
        val third = cubeAt(175.0, -245.0)
        val arranged = state(listOf(first, third), plates = 1, current = 0).withArrangedPlates(4)

        // The third plate holds a cube, so the fourth goes and the second stays.
        assertEquals(3, arranged.plates.size)
        assertEquals(listOf(first, third), arranged.objects)
        assertEquals(1, state(listOf(first), plates = 1, current = 0).withArrangedPlates(3).plates.size)
    }

    @Test
    fun `a plate moved to the front takes its objects and settings, and the tower positions stay with the places`() {
        val tower = ModelSettings(mapOf("wipe_tower_x" to "10.000", "wipe_tower_y" to "20.000"))
        val named = PartPlate(name = "Brackets", settings = ModelSettings(mapOf("curr_bed_type" to "High Temp Plate")))
        val cube = cubeAt(420.0 + 175.0, 175.0)
        val repository = FakeRepository(
            state(listOf(cube), plates = 2, current = 0).copy(plates = listOf(PartPlate(settings = tower), named), plateSettings = tower),
        )

        MovePlateToFrontUseCase(repository)(1)

        val state = repository.state.value
        assertEquals(listOf("Brackets", ""), state.plates.map(PartPlate::name))
        assertEquals(0, state.currentPlate)
        assertEquals(ModelSettings(mapOf("curr_bed_type" to "High Temp Plate") + tower.values), state.plateSettings)
        assertEquals(175.0, state.objects.single().instances.single().inspection.placement.columns[12])
        assertEquals(1, state.history.undo.size)
    }

    @Test
    fun `the plate settings are written as OrcaSlicer's plate config keeps them, and read back`() {
        val choice = PlateSettingsChoice(
            printSequence = "by object",
            firstLayerSequence = listOf(2, 1),
            otherLayersSequence = listOf(LayerSequence(2, 10, listOf(2, 1)), LayerSequence(11, LayerSequence.END_LAYER, listOf(1, 2))),
        )
        val settings = ModelSettings(mapOf("wipe_tower_x" to "10.000")).withPlateSettingsChoice(choice)

        assertEquals("2,1", settings.values["first_layer_print_sequence"])
        assertEquals("2,10,2,1,11,2147483646,1,2", settings.values["other_layers_print_sequence"])
        assertEquals("2", settings.values["other_layers_print_sequence_nums"])
        assertEquals(choice, settings.plateSettingsChoice())
        assertEquals(ModelSettings(mapOf("wipe_tower_x" to "10.000")), settings.withPlateSettingsChoice(PlateSettingsChoice()))
    }

    @Test
    fun `spiral vase mode needs the user's agreement, which the objects on the plate follow`() {
        val repository = FakeRepository(state(listOf(cubeAt(175.0, 175.0), cubeAt(595.0, 175.0).withName("Other")), plates = 2, current = 0))

        SetPlateSettingsUseCase(repository)(PlateSettingsChoice(spiralMode = true), vaseSettingsAgreed = false)
        assertEquals(null, repository.state.value.plateSettings.plateSettingsChoice().spiralMode)

        SetPlateSettingsUseCase(repository)(PlateSettingsChoice(spiralMode = true), vaseSettingsAgreed = true)
        val state = repository.state.value
        assertEquals(true, state.plateSettings.plateSettingsChoice().spiralMode)
        assertEquals("1", state.objects[0].settings.values["wall_loops"])
        assertEquals("0%", state.objects[0].settings.values["sparse_infill_density"])
        assertEquals(ModelSettings(), state.objects[1].settings)
    }

    @Test
    fun `the plate menu selects the objects standing on the plate whole and deletes every one touching it`() {
        val inside = cubeAt(175.0, 175.0)
        val astride = cubeAt(345.0, 175.0, "astride")
        val other = cubeAt(595.0, 175.0, "other")
        val repository = FakeRepository(state(listOf(inside, astride, other), plates = 2, current = 0))

        // The list shows the cube over the plate's edge under "Outside".
        assertEquals(listOf(0, null, 1), repository.state.value.let { state -> state.objects.map(state::listPlateOf) })

        PlateObjectsUseCase(repository).selectCurrentPlate()
        assertEquals(setOf(PlateInstanceId(inside.mesh, 0)), repository.state.value.selectedInstances)

        PlateObjectsUseCase(repository).deleteCurrentPlate()
        assertEquals(listOf(other), repository.state.value.objects)
        assertEquals(emptySet(), repository.state.value.selectedInstances)
    }

    @Test
    fun `selecting another plate leaves the project as it was saved`() {
        val saved = state(listOf(cubeAt(175.0, 175.0), cubeAt(595.0, 175.0, "other")), plates = 2, current = 0)
            .let { it.copy(project = it.projectBaseline()) }
        val repository = FakeRepository(saved)

        SelectPlateUseCase(repository)(1)
        // The engine judges the copies against the second plate.
        repository.update { state ->
            state.copy(
                objects = state.objects.map { plateObject ->
                    plateObject.withInstances(
                        plateObject.instances.map { copy ->
                            val fit = if (copy.inspection.placement.columns[12] > 400.0) BuildVolumeFit.INSIDE else BuildVolumeFit.OUTSIDE
                            copy.copy(inspection = copy.inspection.copy(fit = fit))
                        },
                    )
                },
            )
        }

        assertFalse(repository.state.value.projectDirty)
    }

    @Test
    fun `the all plates item counts the sliced plates, and shows their statistics once all of them are`() {
        val first = cubeAt(175.0, 175.0)
        val second = cubeAt(595.0, 175.0, "second")
        fun sliced(id: String) = PlateSliceResult(SliceJobId(id), emptyList(), OutputPath("/gcode/$id.gcode"), SliceStatistics(10, 60, 100.0))
        val half = state(listOf(first, second), plates = 2, current = 0).copy(result = sliced("first"))
        assertEquals(AllPlatesSliceState.SLICING, half.allPlatesStats()?.state)
        assertEquals(1, half.allPlatesStats()?.sliced)

        val all = half.copy(plates = listOf(PartPlate(), PartPlate(result = sliced("second"))))
        assertEquals(AllPlatesSliceState.SLICED, all.allPlatesStats()?.state)
        assertEquals(2, all.allPlatesStats()?.statistics?.size)

        // A cube over the second plate's edge keeps it from being sliced.
        val over = state(listOf(first, cubeAt(420.0 + 345.0, 175.0, "over")), plates = 2, current = 0)
        assertEquals(AllPlatesSliceState.FAILED, over.allPlatesStats()?.state)
        // With objects on one plate alone there is no item.
        assertNull(state(listOf(first), plates = 2, current = 0).allPlatesStats())
    }

    private class FakeRepository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    private companion object {
        val AREA = listOf(Point2(0.0, 0.0), Point2(350.0, 0.0), Point2(350.0, 350.0), Point2(0.0, 350.0))

        fun state(objects: List<PlateObject>, plates: Int, current: Int) = PlateState(
            plate = PlateDescription(
                PlateGeometry(AREA, 350.0, emptyList(), emptyList(), emptyList(), emptyList(), null, null),
                ColorRgba(1f, 1f, 1f),
            ),
            objects = objects,
            plates = List(plates) { PartPlate() },
            currentPlate = current,
            enginePlate = EnginePlate(current, plates),
        )

        fun cubeAt(x: Double, y: Double, mesh: String = "cube") = PlateObject.CalibrationCube(
            listOf(
                PlateInstance(
                    ModelInspection(
                        facetCount = 12,
                        dimensions = ModelDimensions(20.0, 20.0, 20.0),
                        boxCenter = Vector3(x, y, 10.0),
                        mesh = ScenePath("/scene/objects/$mesh.mesh"),
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
