package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PlateState

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
