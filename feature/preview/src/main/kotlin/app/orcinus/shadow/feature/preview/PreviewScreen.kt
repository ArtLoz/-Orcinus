package app.orcinus.shadow.feature.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaCanvas
import app.orcinus.shadow.core.designsystem.component.OrcaInfoItem
import app.orcinus.shadow.core.designsystem.component.OrcaInfoPanel
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.filamentLength
import app.orcinus.shadow.core.ui.printTime

@Composable
internal fun PreviewRoute(
    viewModel: PreviewViewModel,
    onSliceRequested: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PreviewScreen(
        state = state,
        layout = currentOrcaWindowLayout(),
        onSlice = {
            onSliceRequested()
            viewModel.slice()
        },
    )
}

/**
 * OrcaSlicer's Preview page. The canvas fills the window edge to edge; the
 * slicing result sits at the bottom, as the "Sliced Info" box at the bottom of
 * OrcaSlicer's sidebar.
 */
@Composable
internal fun PreviewScreen(
    state: PreviewUiState,
    layout: OrcaWindowLayout,
    onSlice: () -> Unit,
) {
    OrcaCanvas(Modifier.fillMaxSize()) {
        val result = state.result
        if (result == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.preview_empty), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14)
                if (state.canSlice) {
                    OrcaButton(stringResource(R.string.slice_plate), onClick = onSlice)
                }
            }
        } else {
            SlicedInfo(
                result = result,
                modifier = Modifier
                    .align(if (layout == OrcaWindowLayout.Wide) Alignment.BottomStart else Alignment.BottomCenter)
                    .then(if (layout == OrcaWindowLayout.Wide) Modifier.widthIn(max = 480.dp) else Modifier),
            )
        }
    }
}

@Composable
private fun SlicedInfo(result: PlateSliceResult, modifier: Modifier = Modifier) {
    OrcaInfoPanel(
        title = stringResource(R.string.sliced_info),
        items = listOf(
            OrcaInfoItem(stringResource(R.string.estimated_time), printTime(result.statistics.estimatedPrintTimeSeconds)),
            OrcaInfoItem(stringResource(R.string.used_filament), filamentLength(result.statistics.filamentMillimeters)),
            OrcaInfoItem(stringResource(R.string.layers), result.statistics.layerCount.toString()),
            OrcaInfoItem(stringResource(R.string.model), result.plateObject.displayName()),
        ),
        modifier = modifier,
    )
}

private val PreviewResult = PlateSliceResult(
    jobId = SliceJobId("preview"),
    plateObject = PlateObject.CalibrationCube,
    gcode = OutputPath("/files/gcode/calibration-cube-20mm.gcode"),
    statistics = SliceStatistics(100, 731, 1209.0),
)

@Preview(name = "Compact", widthDp = 400, heightDp = 800)
@Preview(name = "Compact dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCompactPreview() = OrcinusTheme {
    Box(Modifier.fillMaxSize()) {
        PreviewScreen(PreviewUiState(PreviewResult, canSlice = true), OrcaWindowLayout.Compact, onSlice = {})
    }
}

@Preview(name = "Wide", widthDp = 1000, heightDp = 640)
@Composable
private fun PreviewWidePreview() = OrcinusTheme {
    PreviewScreen(PreviewUiState(PreviewResult, canSlice = true), OrcaWindowLayout.Wide, onSlice = {})
}
