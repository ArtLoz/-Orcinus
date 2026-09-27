package app.orcinus.shadow.domain.preferences

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.domain.plate.PlateRepository
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.PresetsApplier
import app.orcinus.shadow.slicing.api.AppConfigStore
import app.orcinus.shadow.slicing.api.PresetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * OrcaSlicer's app configuration as its GUI reads it (GUI_App::app_config):
 * the values of the keys the app uses, read once the engine has started and
 * kept as the Preferences change them. The engine saves them with the presets,
 * in OrcaSlicer.conf; until it has answered, [values] is empty and [canvas]
 * holds the defaults.
 */
class AppPreferences(private val store: AppConfigStore) {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = state.asStateFlow()

    private val canvasState = MutableStateFlow(CanvasPreferences())
    val canvas: StateFlow<CanvasPreferences> = canvasState.asStateFlow()

    /**
     * OpenGLManager::create_wxglcanvas() reads the samples as the canvas is
     * created, so a new value waits for the next start ("Requires application
     * restart"): the canvas keeps the value the app started with.
     */
    private var startSamples: Int? = null

    /** Reads every key the app uses; false when the engine could not answer. */
    suspend fun load(): Boolean = when (val outcome = store.appConfigValues(AppConfigKeys.ALL)) {
        is AppConfigOutcome.Success -> {
            publish(outcome.values)
            true
        }
        is AppConfigOutcome.Failure -> false
    }

    operator fun get(key: String): String = state.value[key].orEmpty()

    /** AppConfig::get_bool() */
    fun bool(key: String): Boolean = AppConfigKeys.bool(get(key))

    /** AppConfig::set() and save(): null once written, why not otherwise. */
    suspend fun set(key: String, value: String): String? = when (val outcome = store.setAppConfigValue(key, value)) {
        is AppConfigOutcome.Success -> {
            publish(state.value + outcome.values)
            null
        }
        is AppConfigOutcome.Failure -> outcome.message
    }

    private fun publish(values: Map<String, String>) {
        state.value = values
        val samples = startSamples ?: AppConfigKeys.antialiasingSamples(values[AppConfigKeys.OPENGL_ANTIALIASING_SAMPLES]).also { startSamples = it }
        canvasState.value = CanvasPreferences(
            orbitSpeed = AppConfigKeys.orbitSpeed(values[AppConfigKeys.CAMERA_ORBIT_MULT]),
            antialiasingSamples = samples,
            // CloneDialog compares with "true" alone.
            autoArrange = values[AppConfigKeys.AUTO_ARRANGE] == "true",
        )
    }
}

/**
 * An item of PreferencesDialog: the value is written at once, as the dialog's
 * controls write it, with what their handlers do besides
 * (create_item_checkbox, create_item_combobox): Developer mode switches the
 * settings mode (GUI_App::update_mode()), so the preset tabs are described
 * again; the grouping of user filaments and the unsupported presets change the
 * sidebar's lists (Sidebar::update_presets()).
 */
class SetPreferenceUseCase(
    private val preferences: AppPreferences,
    private val settingsTabs: PresetSettingsTabs,
    private val presetManager: PresetManager,
    private val platePresets: PresetsApplier,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /** Writes [value] in the application's scope, which outlives the page; [onFailure] hears why it was not written. */
    operator fun invoke(key: String, value: String, onFailure: (String) -> Unit = {}) {
        applicationScope.launch {
            preferences.set(key, value)?.let {
                onFailure(it)
                return@launch
            }
            when (key) {
                AppConfigKeys.DEVELOPER_MODE -> settingsTabs.refresh()
                AppConfigKeys.GROUP_FILAMENT_PRESETS, AppConfigKeys.SHOW_UNSUPPORTED_PRESETS ->
                    platePresets.apply(repository.state.value.profiles, presetManager.presets())
            }
        }
    }
}
