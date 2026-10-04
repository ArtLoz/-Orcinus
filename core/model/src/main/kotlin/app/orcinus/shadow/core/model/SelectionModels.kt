package app.orcinus.shadow.core.model

/** The objects the selection holds, in the plate's order. */
fun PlateState.selectedObjectMeshes(): List<ScenePath> {
    val meshes = selectedInstances.mapTo(HashSet()) { it.mesh }
    return objects.map { it.mesh }.filter { it in meshes }
}

/** Every copy of the selected objects, as Selection::add_object() selects an object whole. */
fun PlateState.selectedCopies(): List<PlateInstanceId> = selectedObjectMeshes().flatMap { mesh ->
    objects.firstOrNull { it.mesh == mesh }?.instances?.indices?.map { PlateInstanceId(mesh, it) }.orEmpty()
}

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

/** The box around every copy of the selected objects; null for an empty selection. */
fun PlateState.selectionBox(): SelectionBox? = SelectionBox.of(
    selectedCopies().mapNotNull { id -> objects.firstOrNull { it.mesh == id.mesh }?.instances?.getOrNull(id.instance) },
)

/** PartPlate::get_center_origin() of the current plate; null before the plate is described. */
fun PlateState.plateCenter(): Point2? {
    val area = plate?.geometry?.printableArea?.takeIf { it.isNotEmpty() } ?: return null
    val origin = plateOrigin
    return Point2(origin.x + (area.minOf { it.x } + area.maxOf { it.x }) / 2, origin.y + (area.minOf { it.y } + area.maxOf { it.y }) / 2)
}
