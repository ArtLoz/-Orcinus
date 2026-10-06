package app.orcinus.shadow.core.model

/** The objects the selection holds, in the plate's order. */
fun PlateState.selectedObjectMeshes(): List<ScenePath> {
    val meshes = selectedInstances.mapTo(HashSet()) { it.mesh }
    return objects.map { it.mesh }.filter { it in meshes }
}

/**
 * The copies the selection holds, object by object in the plate's order: the
 * copies picked one by one, or every copy of an object picked whole
 * (Selection::add_object()), which the selection holds as all its copies or as
 * its first copy alone, the copy its row in the list stands for.
 */
fun PlateState.selectedCopies(): List<PlateInstanceId> = selectedObjectMeshes().flatMap { mesh ->
    val count = objects.firstOrNull { it.mesh == mesh }?.instances?.size ?: 0
    val picked = selectedInstances.filter { it.mesh == mesh && it.instance < count }.map { it.instance }.sorted()
    (if (selectsWhole(mesh)) (0 until count).toList() else picked).map { PlateInstanceId(mesh, it) }
}

/** The object with the [mesh] file is picked whole: every copy of it, or its first copy alone. */
fun PlateState.selectsWhole(mesh: ScenePath): Boolean {
    val count = objects.firstOrNull { it.mesh == mesh }?.instances?.size ?: return false
    val picked = selectedInstances.filter { it.mesh == mesh && it.instance < count }.mapTo(HashSet()) { it.instance }
    return picked.size == count || picked == setOf(0)
}

/**
 * Selection::is_mixed() of several objects: some of them have only some of
 * their copies picked, which the multi-selection menu moves and deletes copy by copy.
 */
fun PlateState.selectsMixed(): Boolean = selectedObjectMeshes().let { meshes -> meshes.size > 1 && meshes.any { !selectsWhole(it) } }

/** The box around some copies (Selection::get_bounding_box()), in millimetres. */
data class SelectionBox(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double, val minZ: Double) {
    val centerX: Double get() = (minX + maxX) / 2
    val centerY: Double get() = (minY + maxY) / 2

    companion object {
        /** The box around [copies]; null for none. */
        fun of(copies: List<PlateInstance>): SelectionBox? {
            val boxes = copies.map(PlateInstance::inspection).ifEmpty { return null }
            return SelectionBox(
                minX = boxes.minOf { it.boxCenter.x - it.dimensions.widthMillimeters / 2 },
                maxX = boxes.maxOf { it.boxCenter.x + it.dimensions.widthMillimeters / 2 },
                minY = boxes.minOf { it.boxCenter.y - it.dimensions.depthMillimeters / 2 },
                maxY = boxes.maxOf { it.boxCenter.y + it.dimensions.depthMillimeters / 2 },
                minZ = boxes.minOf { it.boxCenter.z - it.dimensions.heightMillimeters / 2 },
            )
        }
    }
}

/** The box around the selected copies (Selection::get_bounding_box()); null for an empty selection. */
fun PlateState.selectionBox(): SelectionBox? = SelectionBox.of(
    selectedCopies().mapNotNull { id -> objects.firstOrNull { it.mesh == id.mesh }?.instances?.getOrNull(id.instance) },
)

/** PartPlate::get_center_origin() of the current plate; null before the plate is described. */
fun PlateState.plateCenter(): Point2? {
    val area = plate?.geometry?.printableArea?.takeIf { it.isNotEmpty() } ?: return null
    val origin = plateOrigin
    return Point2(origin.x + (area.minOf { it.x } + area.maxOf { it.x }) / 2, origin.y + (area.minOf { it.y } + area.maxOf { it.y }) / 2)
}
