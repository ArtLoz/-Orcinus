package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.EnginePlate
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.slicing.api.PlateInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * GLCanvas3D::Labels' "Sequence#": the desktop app's background process
 * applies every change of the plate, its presets or its settings to the
 * plate's print and validates it, which numbers the copies in their print
 * order. The app asks the engine for those numbers on the same changes, while
 * the canvas shows its labels, and the plate keeps them.
 */
class PrintSequenceUpdates(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
    private val preferences: AppPreferences,
    private val applicationScope: CoroutineScope,
) {
    private data class Input(
        val objects: List<PlacedModel>,
        val profiles: SlicingProfileSelection?,
        val plateSettings: ModelSettings,
        val presetValues: List<List<String>?>,
        /** The order is the one of the plate the engine knows as the current one. */
        val plate: EnginePlate,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        applicationScope.launch {
            val plate = repository.state
                .map { state -> Input(state.objects.map(PlateObject::placed), state.profiles, state.plateSettings, state.presetValues(), state.enginePlate) }
                .distinctUntilChanged()
            combine(plate, preferences.canvas.map { it.labels }.distinctUntilChanged()) { input, labels -> input.takeIf { labels } }
                .distinctUntilChanged()
                .mapLatest { input ->
                    val profiles = input?.profiles
                    if (profiles == null || input.objects.isEmpty()) null else inspector.printSequence(input.objects, profiles, input.plateSettings)
                }
                .collect { sequence ->
                    repository.update { state -> if (state.printSequence == sequence) state else state.copy(printSequence = sequence) }
                }
        }
    }
}
