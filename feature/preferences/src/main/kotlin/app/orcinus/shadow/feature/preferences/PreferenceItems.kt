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
     * that index; [labels] are msgids.
     */
    data class Choice(
        override val key: String,
        override val title: String,
        override val tooltip: String,
        val labels: List<String>,
        val values: List<String>? = null,
    ) : PreferenceItem {
        /** The entry the stored [value] selects: atoi() of it, or its position among [values]; the first otherwise. */
        fun selected(value: String): Int =
            (values?.indexOf(value) ?: value.toIntOrNull() ?: 0).takeIf { it in labels.indices } ?: 0

        fun valueOf(index: Int): String = values?.get(index) ?: index.toString()
    }

    /** create_item_spinctrl(): a whole number kept within [range] (SpinInput clamps what it shows too), with [unit] beside it. */
    data class Spin(
        override val key: String,
        override val title: String,
        override val tooltip: String,
        val range: IntRange,
        val unit: String,
    ) : PreferenceItem

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
 * Orca's cloud (the Online tab, shared profiles, multi-device management),
 * and the items whose features are not ported yet — see docs/work-plan.md.
 */
internal val PREFERENCE_PAGES = listOf(
    PreferencePage(
        "General",
        listOf(
            PreferenceSection(
                "Project",
                listOf(
                    PreferenceItem.Check(AppConfigKeys.NO_WARN_WHEN_MODIFIED_GCODES, "Don't warn when loading 3MF with modified G-code"),
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
                ),
            ),
        ),
    ),
    PreferencePage(
        "Graphics",
        listOf(
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
                ),
            ),
        ),
    ),
    PreferencePage(
        "Developer",
        listOf(
            PreferenceSection("Settings", listOf(PreferenceItem.Check(AppConfigKeys.DEVELOPER_MODE, "Developer mode"))),
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

