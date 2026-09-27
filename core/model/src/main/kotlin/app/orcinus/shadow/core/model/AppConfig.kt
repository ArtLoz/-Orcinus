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
)

/**
 * The keys of the app configuration's "app" section that OrcaSlicer's
 * Preferences dialog edits and the app reads; AppConfig::set_defaults() gives
 * their defaults.
 */
object AppConfigKeys {
    const val NO_WARN_WHEN_MODIFIED_GCODES = "no_warn_when_modified_gcodes"
    const val DRC_BITS = "drc_bits"
    const val EXPORT_SOURCES_FULL_PATHNAMES = "export_sources_full_pathnames"
    const val REMEMBER_PRINTER_CONFIG = "remember_printer_config"
    const val AUTO_CALCULATE_FLUSH = "auto_calculate_flush"
    const val AUTO_ARRANGE = "auto_arrange"
    const val ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING = "enable_high_low_temp_mixed_printing"
    const val CAMERA_ORBIT_MULT = "camera_orbit_mult"
    const val OPENGL_ANTIALIASING_SAMPLES = "opengl_antialiasing_samples"
    const val DEVELOPER_MODE = "developer_mode"
    const val KEEP_PAINTING = "keep_painting"
    const val LOG_SEVERITY_LEVEL = "log_severity_level"

    /** Every key the app reads once the engine has started. */
    val ALL = listOf(
        NO_WARN_WHEN_MODIFIED_GCODES,
        DRC_BITS,
        EXPORT_SOURCES_FULL_PATHNAMES,
        REMEMBER_PRINTER_CONFIG,
        AUTO_CALCULATE_FLUSH,
        AUTO_ARRANGE,
        ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING,
        CAMERA_ORBIT_MULT,
        OPENGL_ANTIALIASING_SAMPLES,
        DEVELOPER_MODE,
        KEEP_PAINTING,
        LOG_SEVERITY_LEVEL,
    )

    /** AppConfig::get_bool(): "true" or "1". */
    fun bool(value: String?): Boolean = value == "true" || value == "1"

    /** GLCanvas3D::on_mouse(): the orbit speed multiplier, 1 while none is set. */
    fun orbitSpeed(value: String?): Double = value?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 1.0

    /** OpenGLManager::create_wxglcanvas(): 0, 2, 4, 8 or 16 samples, 4 for anything else. */
    fun antialiasingSamples(value: String?): Int = value?.takeIf { it in setOf("0", "2", "4", "8", "16") }?.toInt() ?: 4
}
