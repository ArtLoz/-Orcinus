package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.withLayerRanges
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.core.model.withSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class FilamentRenumberingTest {
    @Test
    fun `deleting the second of three filaments renumbers the objects, parts, ranges, orders and filament changes`() {
        val state = plate().withFilamentDeleted(filament = 1, replace = -1)
        val cube = state.objects.single()

        // The object on filament 3 comes to 2; its support on filament 2 goes.
        assertEquals(mapOf("extruder" to "2", "support_interface_filament" to "1"), cube.settings.values)
        // Parts: on filament 2 the first filament takes it, on 3 it comes to 2, without one it stays so.
        assertEquals(listOf(mapOf("extruder" to "1"), mapOf("extruder" to "2"), emptyMap()), cube.parts.map { it.settings.values })
        // A range on filament 2 takes its object's, one on 3 comes to 2.
        assertEquals(listOf("0", "2"), cube.layerRanges.map { it.settings.values["extruder"] })
        assertEquals("2,1", state.plateSettings.values["first_layer_print_sequence"])
        assertEquals("2,5,2,1", state.plateSettings.values["other_layers_print_sequence"])
        // The change to filament 2 goes, the one to 3 comes to 2, a colour change stays.
        assertEquals(
            listOf(LayerGcode(1.0, LayerGcodeType.TOOL_CHANGE, 1), LayerGcode(3.0, LayerGcodeType.TOOL_CHANGE, 2), LayerGcode(4.0, LayerGcodeType.COLOR_CHANGE, 2)),
            state.layerGcodes,
        )
        assertEquals("2,1", state.plates.last().settings.values["first_layer_print_sequence"])
    }

    @Test
    fun `merging the second filament into the third gives its objects and changes the third, renumbered`() {
        // Sidebar::delete_filament() passes the third filament's index among those left.
        val state = plate().withFilamentDeleted(filament = 1, replace = 1)
        val cube = state.objects.single()

        assertEquals("2", cube.settings.values["extruder"])
        assertEquals(listOf(mapOf("extruder" to "2"), mapOf("extruder" to "2"), emptyMap()), cube.parts.map { it.settings.values })
        assertEquals(listOf("2", "2"), cube.layerRanges.map { it.settings.values["extruder"] })
        assertEquals(listOf(1, 2, 2, 2), state.layerGcodes.map { it.extruder })
    }

    @Test
    fun `fewer filaments take away those beyond them, and more lengthen the orders`() {
        val fewer = plate().withFilamentCount(2)
        val cube = fewer.objects.single()
        // The object beyond them takes the first one.
        assertEquals(mapOf("extruder" to "1", "support_filament" to "2", "support_interface_filament" to "1"), cube.settings.values)
        assertEquals(listOf(mapOf("extruder" to "2"), emptyMap(), emptyMap()), cube.parts.map { it.settings.values })
        assertEquals("2,1", fewer.plateSettings.values["first_layer_print_sequence"])

        val more = plate().withFilamentCount(4)
        assertEquals("2,3,1,4", more.plateSettings.values["first_layer_print_sequence"])
        assertEquals("2,5,3,2,1,4", more.plateSettings.values["other_layers_print_sequence"])
    }

    private fun plate(): PlateState {
        val cube = cube()
            .withSettings(ModelSettings(mapOf("extruder" to "3", "support_filament" to "2", "support_interface_filament" to "1")))
            .withParts(
                listOf(
                    part(ModelSettings(mapOf("extruder" to "2"))),
                    part(ModelSettings(mapOf("extruder" to "3"))),
                    part(ModelSettings()),
                ),
            )
            .withLayerRanges(
                listOf(
                    LayerRange(1.0, 2.0, ModelSettings(mapOf("extruder" to "2"))),
                    LayerRange(2.0, 3.0, ModelSettings(mapOf("extruder" to "3"))),
                ),
            )
        val sequences = ModelSettings(
            mapOf(
                "first_layer_print_sequence" to "2,3,1",
                "other_layers_print_sequence" to "2,5,3,2,1",
                "other_layers_print_sequence_nums" to "1",
            ),
        )
        val changes = listOf(
            LayerGcode(1.0, LayerGcodeType.TOOL_CHANGE, 1),
            LayerGcode(2.0, LayerGcodeType.TOOL_CHANGE, 2),
            LayerGcode(3.0, LayerGcodeType.TOOL_CHANGE, 3),
            LayerGcode(4.0, LayerGcodeType.COLOR_CHANGE, 2),
        )
        return PlateState(
            objects = listOf(cube),
            plates = listOf(PartPlate(), PartPlate(settings = sequences)),
            plateSettings = sequences,
            layerGcodes = changes,
        )
    }

    private fun part(settings: ModelSettings) =
        ObjectPart("Cube", VolumeType.PART, ScenePath("/scene/part.mesh"), IDENTITY, settings)

    private fun cube() = PlateObject.CalibrationCube(
        listOf(
            PlateInstance(
                ModelInspection(
                    facetCount = 12,
                    dimensions = ModelDimensions(20.0, 20.0, 20.0),
                    boxCenter = Vector3(0.0, 0.0, 10.0),
                    mesh = ScenePath("/scene/objects/cube.mesh"),
                    placement = IDENTITY,
                    fit = BuildVolumeFit.INSIDE,
                    boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
                    rotationDegrees = Vector3(0.0, 0.0, 0.0),
                    unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
                ),
            ),
        ),
    )

    private companion object {
        val IDENTITY = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 })
    }
}
