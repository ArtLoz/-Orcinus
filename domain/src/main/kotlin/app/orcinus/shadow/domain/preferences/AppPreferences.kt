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
            imperialUnits = AppConfigKeys.imperialUnits(values[AppConfigKeys.USE_INCHES]),
            // DailyTipsPanel compares with "true" alone.
            showHints = values[AppConfigKeys.SHOW_HINTS] == "true",
            freeCamera = AppConfigKeys.bool(values[AppConfigKeys.USE_FREE_CAMERA]),
            // GLCanvas3D::on_mouse_wheel() compares with "true" alone.
            zoomToMouse = values[AppConfigKeys.ZOOM_TO_MOUSE] == "true",
            fxaa = AppConfigKeys.bool(values[AppConfigKeys.OPENGL_FXAA_ENABLED]),
            fpsCap = AppConfigKeys.fpsCap(values[AppConfigKeys.OPENGL_FPS_CAP]),
            fpsOverlay = AppConfigKeys.bool(values[AppConfigKeys.OPENGL_SHOW_FPS_OVERLAY]),
            // Plater::priv::apply_free_camera_correction() compares with "true" alone, as the canvas does "show_axes".
            perspective = values[AppConfigKeys.USE_PERSPECTIVE_CAMERA]?.let { it == "true" } ?: true,
            autoPerspective = AppConfigKeys.bool(values[AppConfigKeys.AUTO_PERSPECTIVE]),
            navigator = values[AppConfigKeys.SHOW_3D_NAVIGATOR]?.let(AppConfigKeys::bool) ?: true,
            zoomButton = values[AppConfigKeys.SHOW_CANVAS_ZOOM_BUTTON]?.let(AppConfigKeys::bool) ?: true,
            overhang = AppConfigKeys.bool(values[AppConfigKeys.SHOW_OVERHANG]),
            outline = AppConfigKeys.bool(values[AppConfigKeys.SHOW_OUTLINE]),
            labels = AppConfigKeys.bool(values[AppConfigKeys.SHOW_LABELS]),
            realistic = AppConfigKeys.bool(values[AppConfigKeys.OPENGL_REALISTIC_MODE]),
            phong = values[AppConfigKeys.OPENGL_REALISTIC_PHONG]?.let(AppConfigKeys::bool) ?: true,
            shadows = AppConfigKeys.bool(values[AppConfigKeys.OPENGL_PHONG_BASIC_PLATE_SHADOWS]),
            ssao = AppConfigKeys.bool(values[AppConfigKeys.OPENGL_PHONG_SSAO]),
            smoothNormals = AppConfigKeys.bool(values[AppConfigKeys.OPENGL_PHONG_SMOOTH_NORMALS]),
            axes = values[AppConfigKeys.SHOW_AXES]?.let { it == "true" } ?: true,
            gridlines = values[AppConfigKeys.SHOW_PLATE_GRIDLINES]?.let(AppConfigKeys::bool) ?: true,
            gcodeWindow = values[AppConfigKeys.SHOW_GCODE_WINDOW]?.let(AppConfigKeys::bool) ?: true,
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
