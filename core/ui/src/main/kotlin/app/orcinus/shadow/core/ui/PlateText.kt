package app.orcinus.shadow.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import java.util.Locale

// Texts for plate models shared by the Prepare and Preview screens.

@Composable
fun PlateObject.displayName(): String = when (this) {
    is PlateObject.ImportedModel -> file.displayName
    PlateObject.CalibrationCube -> stringResource(R.string.calibration_cube_name)
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
fun ModelDimensions.sizeText(): String = stringResource(
    R.string.size_millimeters,
    widthMillimeters.twoDecimals(),
    depthMillimeters.twoDecimals(),
    heightMillimeters.twoDecimals(),
)

@Composable
fun PlateProblem.title(): String = stringResource(
    when (kind) {
        PlateProblemKind.ENGINE_UNAVAILABLE -> R.string.problem_engine_unavailable
        PlateProblemKind.IMPORT_FAILED -> R.string.problem_import_failed
        PlateProblemKind.SLICE_FAILED -> R.string.problem_slice_failed
        PlateProblemKind.ENGINE_CRASHED -> R.string.problem_engine_crashed
        PlateProblemKind.SLICE_CANCELLED -> R.string.problem_slice_cancelled
    },
)

/** OrcaSlicer shows a preset's alias: its name without the " @printer" suffix. */
fun presetAlias(name: String): String = name.substringBefore(" @")

private fun Double.twoDecimals(): String = String.format(Locale.ROOT, "%.2f", this)
