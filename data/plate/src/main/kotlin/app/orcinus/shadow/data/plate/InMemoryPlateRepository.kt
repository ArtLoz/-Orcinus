package app.orcinus.shadow.data.plate

import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.domain.plate.PlateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The plate kept in memory for the life of the process. Saving and reopening
 * projects will replace this implementation, not its callers. The presets live
 * in the engine's app configuration, which outlives the process.
 */
class InMemoryPlateRepository : PlateRepository {
    private val mutableState = MutableStateFlow(PlateState())

    override val state: StateFlow<PlateState> = mutableState.asStateFlow()

    override fun update(transform: (PlateState) -> PlateState) {
        mutableState.update(transform)
    }
}
