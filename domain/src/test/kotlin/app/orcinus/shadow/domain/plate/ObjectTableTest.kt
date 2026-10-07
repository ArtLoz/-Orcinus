package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObjectTableTest {
    @Test
    fun `an object's row shows its overrides over the process preset's, and a volume's row its own over the object's`() {
        val modifier = part(VolumeType.MODIFIER, ModelSettings(mapOf("wall_loops" to "5", "extruder" to "2")))
        val negative = part(VolumeType.NEGATIVE, ModelSettings(mapOf("extruder" to "3")))
        val cube = cube("cube", ModelSettings(mapOf("wall_loops" to "3", "sparse_infill_density" to "15%", "extruder" to "2")), modifier, negative)

        val rows = PROCESS.copy(objects = listOf(cube)).objectTableRows()

        // The object, then its own mesh and its two parts.
        assertEquals(listOf(null, 0, 1, 2), rows.map { it.volume })
        val (objectRow, own, modifierRow, negativeRow) = rows
        assertEquals("3", objectRow.values[ObjectTableColumn.WALL_LOOPS])
        assertEquals("2", objectRow.originals[ObjectTableColumn.WALL_LOOPS])
        assertTrue(objectRow.modified(ObjectTableColumn.WALL_LOOPS))
        // "15%" of the object and "15" of the process tab are the same density.
        assertFalse(objectRow.modified(ObjectTableColumn.FILL_DENSITY))
        assertEquals(2, objectRow.filament)
        // A volume takes the object's values, which its reset brings back.
        assertEquals("3", own.values[ObjectTableColumn.WALL_LOOPS])
        assertFalse(own.modified(ObjectTableColumn.WALL_LOOPS))
        assertEquals("5", modifierRow.values[ObjectTableColumn.WALL_LOOPS])
        assertEquals("3", modifierRow.originals[ObjectTableColumn.WALL_LOOPS])
        assertTrue(modifierRow.modified(ObjectTableColumn.WALL_LOOPS))
        // The object's filament is no filament of a volume's own.
        assertEquals(2, modifierRow.filament)
        assertFalse(modifierRow.ownFilament)
        assertTrue(negativeRow.ownFilament)
        // A volume's row has no cell of an object's settings, and a negative volume no filament editor.
        assertFalse(modifierRow.shows(ObjectTableColumn.LAYER_HEIGHT))
        assertFalse(modifierRow.modified(ObjectTableColumn.LAYER_HEIGHT))
        assertTrue(modifierRow.editable(ObjectTableColumn.FILAMENT))
        assertFalse(negativeRow.editable(ObjectTableColumn.FILAMENT))
        assertFalse(objectRow.editable(ObjectTableColumn.PLATE))
    }

    @Test
    fun `the default order is by plate, outside last, then by name, and a second sort by a column turns it descending`() {
        val rows = listOf(
            row("b", plate = 0),
            row("outside", plate = null),
            row("a", plate = 1),
            row("c", plate = 0),
            row("c", volume = 1),
        )
        val name = { row: ObjectTableRow -> row.mesh.value }

        val sorted = ObjectTableOrder().byDefault(rows, name)

        assertEquals(listOf("b", "c", "a", "outside"), sorted.objects.map { it.value })
        // The volumes follow their object.
        assertEquals(listOf(null, null, 1, null, null), sorted.arrange(rows).map { it.volume })
        // m_sort_col is the printable column after the default sort, so the
        // plate sorts ascending first.
        val byPlate = sorted.by(ObjectTableColumn.PLATE, rows, name)
        assertEquals(ObjectTableColumn.PLATE, byPlate.sortColumn)
        assertEquals(sorted.objects, byPlate.objects)
        val descending = byPlate.by(ObjectTableColumn.PLATE, rows, name)
        assertEquals(listOf("outside", "a", "c", "b"), descending.objects.map { it.value })
        assertEquals(null, descending.sortColumn)
        // The other columns do not sort.
        assertEquals(descending, descending.by(ObjectTableColumn.WALL_LOOPS, rows, name))
    }

    @Test
    fun `filaments sort as text, and ties keep the order they stood in`() {
        val rows = listOf(row("x", filament = 2), row("y", filament = 10), row("z", filament = 2))

        val sorted = ObjectTableOrder().by(ObjectTableColumn.FILAMENT, rows) { it.mesh.value }

        // std::to_string(10) sorts before "2".
        assertEquals(listOf("y", "x", "z"), sorted.objects.map { it.value })
    }

    private fun row(name: String, plate: Int? = 0, volume: Int? = null, filament: Int = 1) =
        ObjectTableRow(mesh = ScenePath(name), volume = volume, plate = plate, filament = filament)

    private fun part(type: VolumeType, settings: ModelSettings) =
        ObjectPart(shape = "Cube", type = type, mesh = ScenePath("/scene/objects/$type.mesh"), placement = Transform3.IDENTITY, settings = settings)

    private fun cube(name: String, settings: ModelSettings, vararg parts: ObjectPart) = PlateObject.CalibrationCube(
        instances = listOf(
            PlateInstance(
                ModelInspection(
                    facetCount = 12,
                    dimensions = ModelDimensions(20.0, 20.0, 20.0),
                    boxCenter = Vector3(0.0, 0.0, 10.0),
                    mesh = ScenePath("/scene/objects/$name.mesh"),
                    placement = Transform3.IDENTITY,
                    fit = BuildVolumeFit.INSIDE,
                    boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
                    rotationDegrees = Vector3(0.0, 0.0, 0.0),
                    unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
                ),
            ),
        ),
        settings = settings,
        parts = parts.toList(),
    )

    private companion object {
        /** The process tab as the engine describes its fields. */
        val PROCESS = PlateState().let { state ->
            val values = mapOf(
                "layer_height" to "0.2",
                "wall_loops" to "2",
                "sparse_infill_density" to "15",
                "enable_support" to "0",
                "brim_type" to "auto_brim",
                "outer_wall_speed" to "200",
            )
            val settings = PresetSettings(
                kind = PresetKind.PRINT,
                preset = "process",
                label = "process",
                dirty = false,
                isDefault = false,
                isSystem = true,
                hasParent = false,
                canDelete = false,
                mode = SettingsMode.EXPERT,
                pages = emptyList(),
                activePage = "",
                settings = values.map { (key, value) -> SettingState(key, key, value, modified = false, system = true, enabled = true, visible = true) },
                saveName = "process",
                saveNameCopySuffix = false,
            )
            state.copy(settingsTabs = state.settingsTabs + (PresetKind.PRINT to SettingsTabState(PresetKind.PRINT, settings = settings)))
        }
    }
}
