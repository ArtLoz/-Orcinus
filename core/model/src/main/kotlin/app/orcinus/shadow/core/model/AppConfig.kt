package app.orcinus.shadow.core.model

/** Values of OrcaSlicer's app configuration ("app" section) by key, or why they could not be read or written. */
sealed interface AppConfigOutcome {
    data class Success(val values: Map<String, String>) : AppConfigOutcome

    data class Failure(val message: String) : AppConfigOutcome
}

/** What OrcaSlicer's Preferences change on the 3D canvas and the dialogs over it. */
data class CanvasPreferences(
    /** camera_orbit_mult: how far a finger's motion turns the camera. */
    val orbitSpeed: Double = 1.0,
    /** opengl_antialiasing_samples, as the canvas was created with them. */
    val antialiasingSamples: Int = 4,
    /** auto_arrange: CloneDialog's "Auto arrange plate after cloning" when it opens. */
    val autoArrange: Boolean = true,
    /**
     * use_inches: the canvas's panels (the object manipulation, the object's
     * info, the cut and the G-code legend) show lengths in inches and weights
     * in ounces.
     */
    val imperialUnits: Boolean = false,
    /** use_free_camera: the view turns freely, not about the vertical. */
    val freeCamera: Boolean = false,
    /** zoom_to_mouse: a pinch zooms towards the fingers rather than the view's centre. */
    val zoomToMouse: Boolean = false,
    /** opengl_fxaa_enabled: the frame goes through FXAA. */
    val fxaa: Boolean = false,
    /** opengl_fps_cap: at most this many frames a second; 0 for no limit. */
    val fpsCap: Int = 0,
    /** opengl_show_fps_overlay: the frames per second in a corner of the view. */
    val fpsOverlay: Boolean = false,
    /** use_perspective_camera: the perspective projection; the orthographic one otherwise. */
    val perspective: Boolean = true,
    /** auto_perspective: the side views look orthographically, the others in perspective (Camera::auto_type()). */
    val autoPerspective: Boolean = false,
    /** show_3d_navigator: the cube of the camera's views in the view's corner (GLCanvas3D::_render_3d_navigator()). */
    val navigator: Boolean = true,
    /** show_canvas_zoom_button: the button that fits the plate or the selection to the view. */
    val zoomButton: Boolean = true,
    /** show_overhang: the 3D view of the Prepare page tints the overhangs its supports would hold up. */
    val overhang: Boolean = false,
    /** show_outline: the selected objects are drawn with their silhouettes outlined. */
    val outline: Boolean = false,
    /** show_labels: the Prepare page's 3D view labels every object on the plate with its name. */
    val labels: Boolean = false,
    /** opengl_realistic_mode: the canvas's "Realistic View". */
    val realistic: Boolean = false,
    /** opengl_realistic_phong: the realistic view shades the objects with Phong's model. */
    val phong: Boolean = true,
    /** show_axes: the coordinate axes at the origin of the plates (Bed3D::render_axes()). */
    val axes: Boolean = true,
    /** show_plate_gridlines: the plates' grids. */
    val gridlines: Boolean = true,
)

/** GizmoObjectManipulation's conversions, with which the canvas shows imperial units. */
object ImperialUnits {
    const val IN_TO_MM = 25.4
    const val MM_TO_IN = 0.0393700787
    const val OZ_TO_G = 28.34952
}

/**
 * The keys of the app configuration's "app" section that OrcaSlicer's
 * Preferences dialog edits and the app reads; AppConfig::set_defaults() gives
 * their defaults.
 */
object AppConfigKeys {
    /** wxLanguageInfo::CanonicalName of the app's language, which the Language combo box writes. */
    const val LANGUAGE = "language"
    const val USE_INCHES = "use_inches"
    const val NO_WARN_WHEN_MODIFIED_GCODES = "no_warn_when_modified_gcodes"
    const val DRC_BITS = "drc_bits"
    const val EXPORT_SOURCES_FULL_PATHNAMES = "export_sources_full_pathnames"
    const val REMEMBER_PRINTER_CONFIG = "remember_printer_config"
    const val AUTO_CALCULATE_FLUSH = "auto_calculate_flush"
    const val AUTO_ARRANGE = "auto_arrange"
    const val ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING = "enable_high_low_temp_mixed_printing"
    const val AUTO_SLICE_AFTER_CHANGE = "auto_slice_after_change"
    const val AUTO_SLICE_CHANGE_DELAY_SECONDS = "auto_slice_change_delay_seconds"
    const val CAMERA_ORBIT_MULT = "camera_orbit_mult"
    const val ZOOM_TO_MOUSE = "zoom_to_mouse"
    const val USE_PERSPECTIVE_CAMERA = "use_perspective_camera"
    const val AUTO_PERSPECTIVE = "auto_perspective"
    const val SHOW_3D_NAVIGATOR = "show_3d_navigator"
    const val SHOW_CANVAS_ZOOM_BUTTON = "show_canvas_zoom_button"
    const val SHOW_OVERHANG = "show_overhang"
    const val SHOW_OUTLINE = "show_outline"
    const val SHOW_LABELS = "show_labels"
    const val SHOW_AXES = "show_axes"
    const val SHOW_PLATE_GRIDLINES = "show_plate_gridlines"
    const val USE_FREE_CAMERA = "use_free_camera"
    const val OPENGL_ANTIALIASING_SAMPLES = "opengl_antialiasing_samples"
    const val OPENGL_FXAA_ENABLED = "opengl_fxaa_enabled"
    const val OPENGL_FPS_CAP = "opengl_fps_cap"
    const val OPENGL_SHOW_FPS_OVERLAY = "opengl_show_fps_overlay"
    const val OPENGL_REALISTIC_MODE = "opengl_realistic_mode"
    const val OPENGL_REALISTIC_PHONG = "opengl_realistic_phong"
    const val DEVELOPER_MODE = "developer_mode"
    const val KEEP_PAINTING = "keep_painting"
    const val LOG_SEVERITY_LEVEL = "log_severity_level"
    const val PROJECT_LOAD_BEHAVIOUR = "project_load_behaviour"
    const val GROUP_FILAMENT_PRESETS = "group_filament_presets"
    const val SHOW_UNSUPPORTED_PRESETS = "show_unsupported_presets"
    const val SAVE_PROJECT_CHOISE = "save_project_choise"
    const val SAVE_PRESET_CHOISE = "save_preset_choise"
    const val ENABLE_STEP_MESH_SETTING = "enable_step_mesh_setting"
    const val BACKUP_SWITCH = "backup_switch"
    const val BACKUP_INTERVAL = "backup_interval"

    /** StepMeshDialog's values, which its OK writes (the desktop app's spelling). */
    const val IS_SPLIT_COMPOUND = "is_split_compound"
    const val LINEAR_DEFLETION = "linear_defletion"
    const val ANGLE_DEFLETION = "angle_defletion"

    /** SETTING_PROJECT_LOAD_BEHAVIOUR's values (AppConfig.hpp). */
    const val LOAD_ALL = "load_all"
    const val ASK_WHEN_RELEVANT = "ask_when_relevant"
    const val ALWAYS_ASK = "always_ask"
    const val LOAD_GEOMETRY_ONLY = "load_geometry_only"

    /** Every key the app reads once the engine has started. */
    val ALL = listOf(
        USE_INCHES,
        NO_WARN_WHEN_MODIFIED_GCODES,
        DRC_BITS,
        EXPORT_SOURCES_FULL_PATHNAMES,
        REMEMBER_PRINTER_CONFIG,
        AUTO_CALCULATE_FLUSH,
        AUTO_ARRANGE,
        ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING,
        AUTO_SLICE_AFTER_CHANGE,
        AUTO_SLICE_CHANGE_DELAY_SECONDS,
        CAMERA_ORBIT_MULT,
        ZOOM_TO_MOUSE,
        USE_PERSPECTIVE_CAMERA,
        AUTO_PERSPECTIVE,
        SHOW_3D_NAVIGATOR,
        SHOW_CANVAS_ZOOM_BUTTON,
        SHOW_OVERHANG,
        SHOW_OUTLINE,
        SHOW_LABELS,
        SHOW_AXES,
        SHOW_PLATE_GRIDLINES,
        USE_FREE_CAMERA,
        OPENGL_ANTIALIASING_SAMPLES,
        OPENGL_FXAA_ENABLED,
        OPENGL_FPS_CAP,
        OPENGL_SHOW_FPS_OVERLAY,
        OPENGL_REALISTIC_MODE,
        OPENGL_REALISTIC_PHONG,
        DEVELOPER_MODE,
        KEEP_PAINTING,
        LOG_SEVERITY_LEVEL,
        PROJECT_LOAD_BEHAVIOUR,
        GROUP_FILAMENT_PRESETS,
        SHOW_UNSUPPORTED_PRESETS,
        SAVE_PROJECT_CHOISE,
        SAVE_PRESET_CHOISE,
        ENABLE_STEP_MESH_SETTING,
        BACKUP_SWITCH,
        BACKUP_INTERVAL,
    )

    /**
     * MainFrame's set_backup_interval(): the seconds between backups while
     * backup_switch is "true"; 0, no backups, otherwise or for a value that is
     * not a number.
     */
    fun backupInterval(switch: String?, interval: String?): Long =
        if (switch == "true") interval?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L else 0L

    /** GLCanvas3D::_get_effective_fps_cap(): std::stoi() of the value within 0 and 240, 0 for none. */
    fun fpsCap(value: String?): Int {
        val cap = STOL.find(value.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return cap.coerceIn(0, MAX_FPS_CAP)
    }

    const val MAX_FPS_CAP = 240

    /** get("use_inches") == "1", as the canvas asks; the Units combo box writes its index. */
    fun imperialUnits(value: String?): Boolean = value == "1"

    /** AppConfig::get_bool(): "true" or "1". */
    fun bool(value: String?): Boolean = value == "true" || value == "1"

    /** GLCanvas3D::on_mouse(): the orbit speed multiplier, 1 while none is set. */
    fun orbitSpeed(value: String?): Double = value?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 1.0

    /** OpenGLManager::create_wxglcanvas(): 0, 2, 4, 8 or 16 samples, 4 for anything else. */
    fun antialiasingSamples(value: String?): Int = value?.takeIf { it in setOf("0", "2", "4", "8", "16") }?.toInt() ?: 4

    /**
     * Plater::priv::auto_slice_delay_seconds(): std::stol() of the value (leading
     * blanks, a sign and the digits after them), 0 for none or one out of range,
     * kept within 0 and the seconds a timer of int milliseconds can wait.
     */
    fun autoSliceDelaySeconds(value: String?): Int {
        val seconds = STOL.find(value.orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return seconds.coerceIn(0L, Int.MAX_VALUE / 1000L).toInt()
    }

    private val STOL = Regex("""^\s*([+-]?\d+)""")
}
