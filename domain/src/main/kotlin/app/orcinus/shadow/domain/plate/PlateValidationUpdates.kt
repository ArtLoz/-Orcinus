package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
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
 * Plater::priv::update_background_process(): after every change of the plate,
 * its presets or its settings the desktop app applies the plate to its print
 * and validates it, shows the error and the warning, keeps the error from
 * slicing, and numbers the copies in their print order. The app asks the
 * engine on the same changes, and the plate keeps the answer.
 */
class PlateValidationUpdates(
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
        /** The plate the engine knows as the current one is the one validated. */
        val plate: EnginePlate,
        /** Plater::priv::update(): the Preferences' "Remove mixed temperature restriction". */
        val mixedTemperatures: String?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        applicationScope.launch {
            combine(repository.state, preferences.values) { state, values ->
                Input(
                    state.objects.map(PlateObject::placed),
                    state.profiles,
                    state.plateSettings,
                    state.presetValues(),
                    state.enginePlate,
                    values[AppConfigKeys.ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING],
                )
            }
                .distinctUntilChanged()
                .mapLatest { input ->
                    val profiles = input.profiles
                    // background_process.empty(): nothing to validate.
                    if (profiles == null || input.objects.isEmpty()) null else inspector.validatePlate(input.objects, profiles, input.plateSettings)
                }
                .collect { validation ->
                    repository.update { state -> if (state.validation == validation) state else state.copy(validation = validation) }
                }
        }
    }
}
