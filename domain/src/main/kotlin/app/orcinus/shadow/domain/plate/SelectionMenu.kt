package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.plateCenter
import app.orcinus.shadow.core.model.selectedCopies
import app.orcinus.shadow.core.model.selectedObjectMeshes
import app.orcinus.shadow.core.model.selectionBox
import app.orcinus.shadow.core.model.selectsWhole
import app.orcinus.shadow.core.model.withInstances

/**
 * MenuFactory::multi_selection_menu() over several objects, held whole
 * (Selection::is_multiple_full_object()) or some of them by some copies
 * (Selection::is_mixed()): what its items do to all of them at once, each item
 * as one step of Undo.
 */
class SelectionMenuUseCase(
    private val repository: PlateRepository,
    private val placePlateObject: PlacePlateObjectUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val removePlateInstance: RemovePlateInstanceUseCase,
) {
    /**
     * Selection::center(): the selection moves on the plate, keeping its
     * height, until the centre of its box stands over the centre of the
     * current plate (PartPlate::get_center_origin()); do_move() then rests
     * every copy that drops automatically on the plate.
     */
    fun center() {
        val state = repository.state.value
        val box = state.selectionBox() ?: return
        val center = state.plateCenter() ?: return
        moveSelection(state, center.x - box.centerX, center.y - box.centerY, 0.0)
    }

    /** Selection::drop(): the selection moves down, or up, until its box stands on the plate. */
    fun drop() {
        val state = repository.state.value
        val box = state.selectionBox() ?: return
        if (kotlin.math.abs(box.minZ) <= SINKING_Z_THRESHOLD) return
        moveSelection(state, 0.0, 0.0, -box.minZ)
    }

    /**
     * Plater::remove_selected() ("Delete Selected Objects"): the selected
     * objects leave the plate as one step of Undo, the last first; a part of a
     * cut asks first, as Plater::priv::delete_object_from_model() does for
     * each one, and Cancel stops there. Of a mixed selection (Selection::erase()),
     * an object goes when all its copies are picked, or it has one, and
     * otherwise only its picked copies go; the copies go before the objects.
     */
    fun delete() {
        val state = repository.state.value
        val meshes = state.selectedObjectMeshes()
        if (state.busy || meshes.isEmpty()) return
        val whole = meshes.filter { state.selectsWhole(it) }
        val copies = state.selectedCopies().filter { it.mesh !in whole }
        if (copies.isNotEmpty()) removePlateInstance.all(copies)
        if (whole.isNotEmpty()) deletePlateObject.all(whole, recorded = copies.isNotEmpty())
    }

    /** Selection::set_printable(): every copy of every selected object. */
    fun setPrintable(printable: Boolean) = repository.update { state ->
        val meshes = state.selectedObjectMeshes()
        if (state.busy || meshes.isEmpty()) return@update state
        state.recorded().copy(
            objects = state.objects.map { plateObject ->
                if (plateObject.mesh in meshes) plateObject.withInstances(plateObject.instances.map { it.copy(printable = printable) }) else plateObject
            },
            result = null,
        )
    }

    /**
     * Selection::set_auto_drop(): every copy of every selected object; the
     * copies stay where they are until they are moved again.
     */
    fun setAutoDrop(enabled: Boolean) = repository.update { state ->
        val meshes = state.selectedObjectMeshes()
        if (state.busy || meshes.isEmpty()) return@update state
        state.recorded().copy(
            objects = state.objects.map { plateObject ->
                if (plateObject.mesh in meshes) plateObject.withInstances(plateObject.instances.map { it.copy(autoDrop = enabled) }) else plateObject
            },
        )
    }

    /**
     * Selection::move_to_center() of every selected copy by the distance,
     * then GLCanvas3D::do_move("Move Object"): one step of Undo, the copies
     * settled as a move settles them.
     */
    private fun moveSelection(state: PlateState, dx: Double, dy: Double, dz: Double) {
        if (state.busy) return
        state.selectedCopies().forEachIndexed { index, id ->
            val copy = state.objects.withMesh(id.mesh)?.instances?.getOrNull(id.instance) ?: return@forEachIndexed
            val placement = copy.inspection.placement.translated(dx, dy, dz)
            placePlateObject(id, placement, Manipulation.Move, record = index == 0)
        }
    }

    private companion object {
        const val SINKING_Z_THRESHOLD = 0.001
    }
}

/** The transformation moved by the distance in the world (a translation applied after it). */
private fun Transform3.translated(dx: Double, dy: Double, dz: Double): Transform3 =
    Transform3(columns.mapIndexed { index, value -> value + when (index) { 12 -> dx; 13 -> dy; 14 -> dz; else -> 0.0 } })
