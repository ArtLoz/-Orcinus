package app.orcinus.shadow.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.model.ImperialUnits
import app.orcinus.shadow.core.model.ModelDimensions
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

/** The size, in inches with [imperial] units (Plater::update_objects_info_notification()'s koef). */
@Composable
fun ModelDimensions.sizeText(imperial: Boolean): String {
    val koef = if (imperial) ImperialUnits.MM_TO_IN else 1.0
    return stringResource(
        if (imperial) R.string.size_inches else R.string.size_millimeters,
        (widthMillimeters * koef).twoDecimals(),
        (depthMillimeters * koef).twoDecimals(),
        (heightMillimeters * koef).twoDecimals(),
    )
}

@Composable
fun PlateProblem.title(): String = when (kind) {
    PlateProblemKind.ENGINE_UNAVAILABLE -> stringResource(R.string.problem_engine_unavailable)
    PlateProblemKind.IMPORT_FAILED -> stringResource(R.string.problem_import_failed)
    PlateProblemKind.SLICE_FAILED -> stringResource(R.string.problem_slice_failed)
    PlateProblemKind.ENGINE_CRASHED -> stringResource(R.string.problem_engine_crashed)
    PlateProblemKind.SLICE_CANCELLED -> stringResource(R.string.problem_slice_cancelled)
    PlateProblemKind.PLACEMENT_FAILED -> stringResource(R.string.problem_placement_failed)
    PlateProblemKind.PRESETS_FAILED -> stringResource(R.string.problem_presets_failed)
    PlateProblemKind.EXPORT_FAILED -> stringResource(R.string.problem_export_failed)
    // The notification of Plater::export_stl() when the negative parts could not be taken out.
    PlateProblemKind.EXPORT_WITHOUT_NEGATIVE_VOLUMES ->
        orcaString("Unable to perform boolean operation on model meshes. Only positive parts will be exported.")
}

/** OrcaSlicer shows a preset's alias: its name without the " @printer" suffix. */
fun presetAlias(name: String): String = name.substringBefore(" @")

private fun Double.twoDecimals(): String = String.format(Locale.ROOT, "%.2f", this)
