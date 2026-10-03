package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.withInstance

/**
 * Plater::priv::has_assemble_view(): a copy has its place in the assembly view
 * (ModelInstance::is_assemble_initialized()), which the toolbar's "Assembly
 * View" then opens.
 */
fun PlateState.hasAssembleView(): Boolean = objects.any { plateObject -> plateObject.instances.any { it.assemble != null } }

/**
 * Plater::TakeSnapshot of an action that changes nothing the plate keeps, as
 * the assembly view's "Hide" and "Show" take one ("Set Selected Objects
 * Visible in AssembleView"): Undo steps over it.
 */
class TakePlateSnapshotUseCase(private val repository: PlateRepository) {
    operator fun invoke() = repository.update { state -> if (state.busy) state else state.recorded() }
}

/**
 * GLCanvas3D::do_move() and do_rotate() of the assembly view: a snapshot, and
 * the copy's assemble transformation takes the placement the gizmo or the
 * window left (ModelInstance::set_assemble_transformation(), or
 * set_assemble_from_transform() after a rotation). A move of a hundredth of
 * a millimetre or less leaves it; nothing drops on the plate (ensure_on_bed()
 * skips the assembly view), and the G-code still applies.
 */
class PlaceInAssemblyUseCase(private val repository: PlateRepository) {
    operator fun invoke(id: PlateInstanceId, assemble: Transform3, manipulation: Manipulation) = repository.update { state ->
        val target = state.objects.withMesh(id.mesh)
        val copy = target?.instances?.getOrNull(id.instance)
        if (target == null || copy == null || state.busy) return@update state
        val recorded = state.recorded()
        val before = (copy.assemble ?: Transform3.IDENTITY).columns
        val moved = (12..14).sumOf { (before[it] - assemble.columns[it]).let { offset -> offset * offset } } > MOVE_THRESHOLD * MOVE_THRESHOLD
        if (manipulation == Manipulation.Move && !moved) return@update recorded
        recorded.copy(objects = state.objects.replaced(target.withInstance(id.instance, copy.copy(assemble = assemble))))
    }

    private companion object {
        /** do_move(): the assemble offset changes by more than 1e-2. */
        const val MOVE_THRESHOLD = 1e-2
    }
}
