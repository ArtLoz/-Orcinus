package app.orcinus.shadow.feature.preferences

import app.orcinus.shadow.core.model.AppConfigKeys

/**
 * An item of PreferencesDialog::create_items(), with OrcaSlicer's msgids: its
 * [title] and the [tooltip] its label shows, the title again when it has none.
 */
internal sealed interface PreferenceItem {
    val key: String
    val title: String
    val tooltip: String

    /** create_item_checkbox(): AppConfig::set_bool(), "true" or "false". */
    data class Check(override val key: String, override val title: String, override val tooltip: String = "") : PreferenceItem

    /**
     * create_item_combobox(): the entry's index, or with [values] the value at
     * that index; [labels] are msgids, each followed by its untranslated
     * suffix in [suffixes] when there are any.
     */
    data class Choice(
        override val key: String,
        override val title: String,
        override val tooltip: String,
        val labels: List<String>,
        val values: List<String>? = null,
        val suffixes: List<String>? = null,
    ) : PreferenceItem {
        /** The entry the stored [value] selects: atoi() of it, or its position among [values]; the first otherwise. */
        fun selected(value: String): Int =
            (values?.indexOf(value) ?: value.toIntOrNull() ?: 0).takeIf { it in labels.indices } ?: 0

        fun valueOf(index: Int): String = values?.get(index) ?: index.toString()
    }

    /**
     * create_item_spinctrl(): a whole number kept within [range] (SpinInput
     * clamps what it shows too), with [unit] beside it and its second title
     * [note], which the row writes under the title.
     */
    data class Spin(
        override val key: String,
        override val title: String,
        override val tooltip: String,
        val range: IntRange,
        val unit: String,
        val note: String = "",
    ) : PreferenceItem

    /**
     * create_item_auto_reslice() and create_item_backup(): the checkbox of
     * [key] and beside it the seconds of [secondsKey], a whole number the
     * field takes only while the box is ticked; [secondsTooltip] is the
     * field's own tooltip.
     */
    data class CheckSeconds(
        override val key: String,
        val secondsKey: String,
        override val title: String,
        override val tooltip: String,
        val secondsTooltip: String,
    ) : PreferenceItem

    /**
     * create_item_language_combobox(): the languages of OrcaSlicer's catalogues
     * (ORCA_LANGUAGES), the app's own selected.
     */
    data class Language(override val key: String, override val title: String, override val tooltip: String = "") : PreferenceItem

    /** create_item_button() of "Clear my choice on...": the button empties the remembered choice. */
    data class Clear(override val key: String, override val title: String, override val tooltip: String) : PreferenceItem

    /** create_item_input(): digits only (wxFILTER_DIGITS), written as they are typed. */
    data class Digits(override val key: String, override val title: String, override val tooltip: String) : PreferenceItem

    /** create_camera_orbit_mult_input(): a number kept within [min]..[max], written with two decimals. */
    data class Decimal(
        override val key: String,
        override val title: String,
        override val tooltip: String,
        val min: Double,
        val max: Double,
    ) : PreferenceItem
}

/** get_string_logging_level() of libslic3r, in its order; declared before the pages, which use it. */
private val LOG_LEVELS = listOf("fatal", "error", "warning", "info", "debug", "trace")

/** A group of items under create_item_title(). */
internal data class PreferenceSection(val title: String, val items: List<PreferenceItem>)

/** A tab of the dialog (m_pref_tabs). */
internal data class PreferencePage(val title: String, val sections: List<PreferenceSection>)

/**
 * The dialog's tabs in its order, with the items whose features the app has.
 * Left out: what belongs to a desktop window, a mouse or Windows (single
 * instance, window buttons, the downloads folder, camera style, mouse drag and
 * wheel, file associations), BambuLab's network plug-in and devices, and
 * Orca's cloud (logins, the user presets' sync, shared profiles, multi-device
 * management), and the items whose features are not ported yet — see
 * docs/work-plan.md.
 */
internal val PREFERENCE_PAGES = listOf(
    PreferencePage(
        "General",
        listOf(
            PreferenceSection(
                "Settings",
                listOf(
                    PreferenceItem.Language(AppConfigKeys.LANGUAGE, "Language"),
                    PreferenceItem.Choice(
                        AppConfigKeys.USE_INCHES,
                        "Units",
                        "",
                        labels = listOf("Metric", "Imperial"),
                        suffixes = listOf(" (mm, g)", " (in, oz)"),
                    ),
                    PreferenceItem.Choice(
                        AppConfigKeys.DEFAULT_PAGE,
                        "Default page",
                        "Set the page opened on startup.",
                        labels = listOf("Home", "Prepare"),
                    ),
                ),
            ),
            PreferenceSection(
                "Project",
                listOf(
                    PreferenceItem.Choice(
                        AppConfigKeys.PROJECT_LOAD_BEHAVIOUR,
                        "Load behaviour",
                        "Should printer/filament/process settings be loaded when opening a 3MF file?",
                        labels = listOf("Load All", "Ask When Relevant", "Always Ask", "Load Geometry Only"),
                        values = listOf(
                            AppConfigKeys.LOAD_ALL,
                            AppConfigKeys.ASK_WHEN_RELEVANT,
                            AppConfigKeys.ALWAYS_ASK,
                            AppConfigKeys.LOAD_GEOMETRY_ONLY,
                        ),
                    ),
                    PreferenceItem.CheckSeconds(
                        AppConfigKeys.BACKUP_SWITCH,
                        AppConfigKeys.BACKUP_INTERVAL,
                        "Auto backup",
                        "Backup your project periodically to help with restoring from an occasional crash.",
                        "The period of backup in seconds.",
                    ),
                    PreferenceItem.Digits(AppConfigKeys.MAX_RECENT_COUNT, "Maximum recent files", "Maximum count of recent files"),
                    PreferenceItem.Check(AppConfigKeys.RECENT_MODELS, "Add STL/STEP files to recent files list"),
                    PreferenceItem.Check(AppConfigKeys.NO_WARN_WHEN_MODIFIED_GCODES, "Don't warn when loading 3MF with modified G-code"),
                    PreferenceItem.Check(
                        AppConfigKeys.ENABLE_STEP_MESH_SETTING,
                        "Show options when importing STEP file",
                        "If enabled, a parameter settings dialog will appear during STEP file import.",
                    ),
                    PreferenceItem.Spin(
                        AppConfigKeys.DRC_BITS,
                        "Quality level for Draco export",
                        "Controls the quantization bit depth used when compressing the mesh to Draco format.\n" +
                            "0 = lossless compression (geometry is preserved at full precision). Valid lossy values range from 8 to 30.\n" +
                            "Lower values produce smaller files but lose more geometric detail; higher values preserve more detail at the cost of larger files.",
                        range = DRC_BITS_MIN..DRC_BITS_MAX,
                        unit = "bits",
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.EXPORT_SOURCES_FULL_PATHNAMES,
                        "Store full source file paths in projects",
                        "If enabled, saved projects store the absolute path to imported source files (STEP/STL/...), so " +
                            "\"Reload from disk\" still works when the source file is kept in a different folder than the project. " +
                            "If disabled, only the filename is stored, which keeps projects portable and avoids embedding absolute paths.",
                    ),
                ),
            ),
            PreferenceSection(
                "Preset",
                listOf(
                    PreferenceItem.Check(
                        AppConfigKeys.REMEMBER_PRINTER_CONFIG,
                        "Remember printer configuration",
                        "If enabled, Orca will remember and switch filament/process configuration for each printer automatically.",
                    ),
                    PreferenceItem.Choice(
                        AppConfigKeys.GROUP_FILAMENT_PRESETS,
                        "Group user filament presets",
                        "Group user filament presets based on selection",
                        labels = listOf("All", "None", "By type", "By vendor"),
                    ),
                ),
            ),
        ),
    ),
    PreferencePage(
        "Control",
        listOf(
            PreferenceSection(
                "Behaviour",
                listOf(
                    PreferenceItem.Choice(
                        AppConfigKeys.AUTO_CALCULATE_FLUSH,
                        "Auto flush after changing...",
                        "Auto calculate flushing volumes when selected values changed",
                        labels = listOf("All", "Color", "None"),
                        values = listOf("all", "color change", "disabled"),
                    ),
                    PreferenceItem.Check(AppConfigKeys.AUTO_ARRANGE, "Auto arrange plate after cloning"),
                ),
            ),
            PreferenceSection(
                "Slicing",
                listOf(
                    PreferenceItem.CheckSeconds(
                        AppConfigKeys.AUTO_SLICE_AFTER_CHANGE,
                        AppConfigKeys.AUTO_SLICE_CHANGE_DELAY_SECONDS,
                        "Auto slice after changes",
                        "If enabled, OrcaSlicer will re-slice automatically whenever slicing-related settings change.",
                        "Delay in seconds before auto slicing starts, allowing multiple edits to be grouped. Use 0 to slice immediately.",
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING,
                        "Remove mixed temperature restriction",
                        "With this option enabled, you can print materials with a large temperature difference together.",
                    ),
                ),
            ),
            PreferenceSection(
                "Camera",
                listOf(
                    PreferenceItem.Decimal(
                        AppConfigKeys.CAMERA_ORBIT_MULT,
                        "Orbit speed multiplier",
                        "Multiplies the orbit speed for finer or coarser camera movement.",
                        min = 0.05,
                        max = 2.0,
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.ZOOM_TO_MOUSE,
                        "Zoom to mouse position",
                        "Zoom in towards the mouse pointer's position in the 3D view, rather than the 2D window center.",
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.USE_FREE_CAMERA,
                        "Use free camera",
                        "If enabled, use free camera. If not enabled, use constrained camera.",
                    ),
                ),
            ),
            PreferenceSection(
                "Clear my choice on...",
                listOf(
                    PreferenceItem.Clear(AppConfigKeys.SAVE_PROJECT_CHOISE, "Unsaved projects", "Clear my choice on the unsaved projects."),
                    PreferenceItem.Clear(AppConfigKeys.SAVE_PRESET_CHOISE, "Unsaved presets", "Clear my choice on the unsaved presets."),
                ),
            ),
        ),
    ),
    PreferencePage(
        "Graphics",
        listOf(
            PreferenceSection(
                "Realistic View",
                listOf(
                    PreferenceItem.Check(AppConfigKeys.OPENGL_REALISTIC_PHONG, "Phong shading", "Uses Phong shading inside realistic view."),
                    PreferenceItem.Check(AppConfigKeys.OPENGL_PHONG_SSAO, "SSAO ambient occlusion", "Applies SSAO in realistic view."),
                    PreferenceItem.Check(
                        AppConfigKeys.OPENGL_PHONG_BASIC_PLATE_SHADOWS,
                        "Shadows",
                        "Renders cast shadows on the plate in realistic view.",
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.OPENGL_PHONG_SMOOTH_NORMALS,
                        "Smooth normals",
                        "Applies smooth normals to the realistic view.\n\nRequires manual scene reload to take effect " +
                            "(right-click on 3D view → \"Reload All\").",
                    ),
                ),
            ),
            PreferenceSection(
                "Anti-aliasing",
                listOf(
                    PreferenceItem.Choice(
                        AppConfigKeys.OPENGL_ANTIALIASING_SAMPLES,
                        "MSAA Multiplier",
                        "Set the Multi-Sample Anti-Aliasing level.\n" +
                            "Higher values result in smoother edges, but the impact on performance is exponential.\n" +
                            "Lower values improve performance, at the cost of jagged edges.\n" +
                            "If disabled, its recommended to enable FXAA to reduce jagged edges with minimal performance impact.\n\n" +
                            "Requires application restart.",
                        labels = listOf("Disabled", "2x", "4x", "8x", "16x"),
                        values = listOf("0", "2", "4", "8", "16"),
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.OPENGL_FXAA_ENABLED,
                        "FXAA post-processing",
                        "Applies Fast Approximate Anti-Aliasing as a screen-space pass.\n" +
                            "Useful for disabling or reducing the MSAA setting to improve performance.\n\n" +
                            "Takes effect immediately.",
                    ),
                ),
            ),
            PreferenceSection(
                "FPS",
                listOf(
                    PreferenceItem.Spin(
                        AppConfigKeys.OPENGL_FPS_CAP,
                        "FPS cap",
                        "Limits viewport frame rate to reduce GPU load and power usage.\n" +
                            "Set to 0 for unlimited frame rate.",
                        range = 0..AppConfigKeys.MAX_FPS_CAP,
                        unit = "FPS",
                        note = "(0 = unlimited)",
                    ),
                    PreferenceItem.Check(
                        AppConfigKeys.OPENGL_SHOW_FPS_OVERLAY,
                        "Show FPS overlay",
                        "Displays current viewport FPS in the top-right corner.",
                    ),
                ),
            ),
        ),
    ),
    PreferencePage(
        "Online",
        listOf(
            PreferenceSection(
                "Connection",
                listOf(
                    PreferenceItem.Check(
                        AppConfigKeys.STEALTH_MODE,
                        "Stealth mode",
                        "This disables all cloud features, including Orca Cloud profile syncing. Users who prefer to work entirely offline " +
                            "can enable this option.\nNote: When Stealth Mode is enabled, your user profiles will not be backed up to Orca Cloud.",
                    ),
                ),
            ),
            PreferenceSection(
                "Update & sync",
                listOf(PreferenceItem.Check(AppConfigKeys.SYNC_SYSTEM_PRESET, "Update built-in presets automatically.")),
            ),
        ),
    ),
    PreferencePage(
        "Developer",
        listOf(
            PreferenceSection(
                "Settings",
                listOf(
                    PreferenceItem.Check(AppConfigKeys.DEVELOPER_MODE, "Developer mode"),
                    PreferenceItem.Check(
                        AppConfigKeys.SHOW_UNSUPPORTED_PRESETS,
                        "Show unsupported presets",
                        "Show incompatible/unsupported presets in the printer and filament dropdown lists. These presets cannot be selected.",
                    ),
                ),
            ),
            PreferenceSection(
                "Experimental Features",
                listOf(
                    PreferenceItem.Check(
                        AppConfigKeys.KEEP_PAINTING,
                        "Keep painted feature after mesh change",
                        "Attempt to keep painted features (color/seam/support/fuzzy etc.) after changing the object mesh " +
                            "(such as cut/reload from disk/simplify/fix etc.)\nHighly experimental! Slow and may create artifact.",
                    ),
                ),
            ),
            PreferenceSection(
                "Log Level",
                listOf(
                    // create_item_loglevel_combobox(): the level's name is the value.
                    PreferenceItem.Choice(
                        AppConfigKeys.LOG_SEVERITY_LEVEL,
                        "Log Level",
                        "Log Level",
                        labels = LOG_LEVELS,
                        values = LOG_LEVELS,
                    ),
                ),
            ),
        ),
    ),
)

// DRC.hpp
private const val DRC_BITS_MIN = 8
private const val DRC_BITS_MAX = 30

