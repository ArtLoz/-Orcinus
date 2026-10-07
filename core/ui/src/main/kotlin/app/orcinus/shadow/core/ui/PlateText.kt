package app.orcinus.shadow.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.ui.orca.orcaString
import java.util.Locale

// Texts for plate models shared by the Prepare and Preview screens.

@Composable
fun PlateObject.displayName(): String = when (this) {
    // An object written anew from the calibration cube has no name of its own either.
    is PlateObject.ImportedModel -> file.displayName.ifEmpty { stringResource(R.string.calibration_cube_name) }
    is PlateObject.CalibrationCube -> name ?: stringResource(R.string.calibration_cube_name)
}

@Composable
fun printTime(seconds: Long): String {
    val hours = (seconds / 3_600).toInt()
    val minutes = ((seconds % 3_600) / 60).toInt()
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

@Composable
fun filamentLength(millimeters: Double): String =
    stringResource(R.string.length_meters, String.format(Locale.ROOT, "%.2f", millimeters / 1_000.0))

@Composable
fun PlateProblem.title(): String = when (kind) {
    PlateProblemKind.ENGINE_UNAVAILABLE -> stringResource(R.string.problem_engine_unavailable)
    PlateProblemKind.IMPORT_FAILED -> stringResource(R.string.problem_import_failed)
    // The error's own text, which the engine translated.
    PlateProblemKind.SLICING_ERROR -> detail.orEmpty()
    PlateProblemKind.ENGINE_CRASHED -> stringResource(R.string.problem_engine_crashed)
    PlateProblemKind.PLACEMENT_FAILED -> stringResource(R.string.problem_placement_failed)
    PlateProblemKind.PRESETS_FAILED -> stringResource(R.string.problem_presets_failed)
    PlateProblemKind.EXPORT_FAILED -> stringResource(R.string.problem_export_failed)
    // The notification of Plater::export_stl() when the negative parts could not be taken out.
    PlateProblemKind.EXPORT_WITHOUT_NEGATIVE_VOLUMES ->
        orcaString("Unable to perform boolean operation on model meshes. Only positive parts will be exported.")
    PlateProblemKind.MESH_BOOLEAN_FAILED -> orcaString("Unable to perform boolean operation on selected parts")
    PlateProblemKind.PLATE_LOCKED_ARRANGE -> orcaString("This plate is locked.\nCannot auto-arrange on this plate.")
    PlateProblemKind.PLATE_LOCKED_ORIENT -> orcaString("This plate is locked.\nCannot auto-orient on this plate.")
    PlateProblemKind.SELECTION_LOCKED_ARRANGE -> orcaString("All the selected objects are on a locked plate.\nCannot auto-arrange these objects.")
    PlateProblemKind.SELECTION_LOCKED_ORIENT -> orcaString("All the selected objects are on a locked plate.\nCannot auto-orient these objects.")
    PlateProblemKind.NO_ARRANGEABLE_OBJECTS -> orcaString("No arrangeable objects are selected.")
}

/** OrcaSlicer shows a preset's alias: its name without the " @printer" suffix. */
fun presetAlias(name: String): String = name.substringBefore(" @")

