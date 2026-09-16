package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PlateState
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the plate. Screens observe it; plate use cases are
 * the only writers. Implemented by :data:plate.
 */
interface PlateRepository {
    val state: StateFlow<PlateState>

    /** Applies [transform] atomically to the current state. */
    fun update(transform: (PlateState) -> PlateState)
}
