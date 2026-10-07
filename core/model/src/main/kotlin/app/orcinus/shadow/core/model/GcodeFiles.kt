package app.orcinus.shadow.core.model

/**
 * GCodeProcessorResult::SettingsIds: the presets the G-code's own
 * configuration names, which the legend's "Settings" lists for a G-code file
 * the preview shows on its own; empty for what the G-code does not name.
 */
data class GcodeSettingsIds(
    val printer: String = "",
    val print: String = "",
    /** By the G-code's filament, from the first. */
    val filaments: List<String> = emptyList(),
)

/**
 * Plater::load_gcode() and Print::export_gcode_from_previous_file(): what the
 * engine read of a G-code file for the preview.
 */
sealed interface GcodeLoadOutcome {
    data class Success(
        val statistics: SliceStatistics,
        /** The toolpaths file, or null when none was written (no valid G-code). */
        val toolpaths: ScenePath?,
        /** The requested slice info, or null when none was written. */
        val sliceInfo: ScenePath? = null,
        val settingsIds: GcodeSettingsIds,
        /** get_gcode_layers_zs() is not empty: the file holds G-code the preview shows. */
        val valid: Boolean,
        /** The G-code's plate type became the project's (curr_bed_type), which the presets then show. */
        val bedTypeChanged: Boolean = false,
    ) : GcodeLoadOutcome

    data class Failure(val message: String) : GcodeLoadOutcome
}

/**
 * Plater's preview-only modes: a G-code file opened on its own (m_only_gcode,
 * Plater::load_gcode()), or a project of no objects whose plates carry their
 * G-code (m_exported_file, the .gcode.3mf of "Export plate sliced file").
 */
enum class PreviewOnlyKind { GCODE, EXPORTED_FILE }

/**
 * The file the preview-only mode shows, and the name the questions about it
 * use (m_preview_only_filename: the project's name with ".gcode" or ".3mf").
 */
data class PreviewOnly(
    val kind: PreviewOnlyKind,
    val fileName: String,
    /** The document it was opened from, which the same file opened again is told by (m_last_loaded_gcode). */
    val document: ExternalDocumentReference? = null,
    /** m_valid_plates_count: the plates whose G-code the file carries. */
    val slicedPlates: Int = 1,
)
