package app.orcinus.shadow.feature.sidebar

import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.ScenePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectListSearchTest {
    @Test
    fun `a row is named after the rows it hangs under, a plate by its own name`() {
        assertEquals(
            listOf(
                "Plate 1",
                "Plate 1:Quality",
                "Plate 1:Cube",
                "Plate 1:Cube:Cube",
                "Plate 1:Cube:Cube:Strength",
                "Plate 1:Cube:Cylinder",
                "Plate 1:Cube:Layers",
                "Plate 1:Cube:Layers:Range 0.00-2.00 (mm)",
                "Outside",
                "Outside:Cone",
            ),
            ObjectListSearch.names(TREE).map { it.name },
        )
    }

    @Test
    fun `no text finds every row, unmarked`() {
        val names = ObjectListSearch.names(TREE)
        val found = ObjectListSearch.search(names, "")

        assertEquals(names.map { it.name }, found.map { it.name })
        assertEquals(names.indices.toList(), found.map { it.row })
        assertTrue(found.all { it.matches.isEmpty() })
    }

    @Test
    fun `the text is found whatever its case, every place it stands in marked`() {
        val found = ObjectListSearch.search(ObjectListSearch.names(TREE), "cUbE")

        assertEquals(listOf(2, 3, 4, 5, 6, 7), found.map { it.row })
        val part = found.first { it.name == "Plate 1:Cube:Cube" }
        assertEquals(ObjectListTarget.Part(ObjectPartId(MESH, 0)), part.target)
        assertEquals(listOf(8..11, 13..16), part.matches)
        assertEquals(listOf(8..11), found.first { it.name == "Plate 1:Cube:Cylinder" }.matches)
    }

    @Test
    fun `the places the text stands in follow one another without overlapping`() {
        val found = ObjectListSearch.search(listOf(ObjectListName(ObjectListTarget.Outside, "aaa")), "aa")

        assertEquals(listOf(0..1), found.single().matches)
    }

    @Test
    fun `a row the text is not in is not found`() {
        assertTrue(ObjectListSearch.search(ObjectListSearch.names(TREE), "sphere").isEmpty())
    }

    private companion object {
        val MESH = ScenePath("scene/cube.stl")

        val TREE = listOf(
            ObjectListNode(
                ObjectListTarget.Plate(0),
                "Plate 1",
                listOf(
                    ObjectListNode(ObjectListTarget.PlateSettings(0), "Quality"),
                    ObjectListNode(
                        ObjectListTarget.Object(PlateInstanceId(MESH)),
                        "Cube",
                        listOf(
                            ObjectListNode(
                                ObjectListTarget.Part(ObjectPartId(MESH, 0)),
                                "Cube",
                                listOf(ObjectListNode(ObjectListTarget.PartSettings(ObjectPartId(MESH, 0)), "Strength")),
                            ),
                            ObjectListNode(ObjectListTarget.Part(ObjectPartId(MESH, 1)), "Cylinder"),
                            ObjectListNode(
                                ObjectListTarget.Layers(MESH),
                                "Layers",
                                listOf(ObjectListNode(ObjectListTarget.Range(LayerRangeId(MESH, 0)), "Range 0.00-2.00 (mm)")),
                            ),
                        ),
                    ),
                ),
            ),
            ObjectListNode(
                ObjectListTarget.Outside,
                "Outside",
                listOf(ObjectListNode(ObjectListTarget.Object(PlateInstanceId(ScenePath("scene/cone.stl"))), "Cone")),
            ),
        )
    }
}
