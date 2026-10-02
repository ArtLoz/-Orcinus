package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.PlateInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * GLVolumeCollection::render() asks get_selection_support_normal_z() for every
 * frame, so the canvas's "Overhangs" follow the support settings as soon as
 * they change. The app asks the engine again whenever the selected presets or
 * the values of the edited ones change, and the plate keeps the answer.
 */
class OverhangUpdates(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    private data class Input(val profiles: SlicingProfileSelection?, val presetValues: List<List<String>?>)

    fun start() {
        applicationScope.launch {
            repository.state
                .map { state -> Input(state.profiles, state.presetValues()) }
                .distinctUntilChanged()
                .collect { input ->
                    val normalZ = input.profiles?.let { inspector.overhangNormalZ(it) }
                    repository.update { state -> if (state.overhangNormalZ == normalZ) state else state.copy(overhangNormalZ = normalZ) }
                }
        }
    }
}
