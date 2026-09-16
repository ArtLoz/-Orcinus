package app.orcinus.shadow.feature.prepare

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaCanvas
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasTool
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbar
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.designsystem.component.OrcaProgressNotification
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.sizeText
import app.orcinus.shadow.core.ui.title
import kotlin.math.roundToInt

@Composable
internal fun PrepareRoute(
    viewModel: PrepareViewModel,
    onSliceRequested: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addModel(it.toString()) }
    }
    PrepareScreen(
        state = state,
        layout = currentOrcaWindowLayout(),
        onAddModel = { modelPicker.launch(arrayOf("*/*")) },
        onAddCalibrationCube = viewModel::addCalibrationCube,
        onSlice = {
            onSliceRequested()
            viewModel.slice()
        },
        onCancelSlicing = viewModel::cancelSlicing,
        onDismissProblem = viewModel::dismissProblem,
    )
}

/**
 * OrcaSlicer's Prepare page: the plate canvas, edge to edge, with the model
 * toolbar and the notifications. The sidebar belongs to the app shell.
 */
@Composable
internal fun PrepareScreen(
    state: PrepareUiState,
    layout: OrcaWindowLayout,
    onAddModel: () -> Unit,
    onAddCalibrationCube: () -> Unit,
    onSlice: () -> Unit,
    onCancelSlicing: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    OrcaCanvas(Modifier.fillMaxSize()) {
        // The canvas runs under the system bars; its controls stay clear of them.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            OrcaCanvasToolbar(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            ) {
                OrcaCanvasTool(DesignR.drawable.orca_toolbar_open, stringResource(R.string.add_model), onAddModel, enabled = state.canEditPlate)
                OrcaCanvasTool(DesignR.drawable.orca_tab_3d_active, stringResource(R.string.add_calibration_cube), onAddCalibrationCube, enabled = state.canEditPlate)
            }
            when {
                state.importing -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OrcaTheme.colors.accent)
                    Text(
                        text = stringResource(R.string.importing_model),
                        color = OrcaTheme.colors.textSide,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                state.plateObject == null -> Text(
                    text = stringResource(R.string.empty_plate),
                    color = OrcaTheme.colors.textSide,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Notifications(state, onCancelSlicing, onDismissProblem)
                // In a wide window the slice button sits in the tab bar, as on desktop.
                if (layout == OrcaWindowLayout.Compact) {
                    OrcaButton(stringResource(R.string.slice_plate), onClick = onSlice, enabled = state.canSlice)
                }
            }
        }
    }
}

/** Notifications of the canvas: problems, progress, the object info. */
@Composable
private fun Notifications(
    state: PrepareUiState,
    onCancelSlicing: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    state.problem?.let { problem ->
        OrcaNotification(
            action = {
                Text(
                    text = stringResource(R.string.dismiss),
                    color = OrcaTheme.colors.accent,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable(role = Role.Button, onClick = onDismissProblem),
                )
            },
        ) {
            OrcaNotificationText(problem.title(), emphasized = true)
            problem.detail?.takeIf { it.isNotBlank() }?.let { OrcaNotificationText(it) }
        }
    }
    val slicing = state.slicing
    if (slicing != null) {
        val fraction = slicing.progress?.fraction ?: 0f
        OrcaProgressNotification(
            title = if (slicing.cancelling) {
                stringResource(R.string.slicing_cancelling)
            } else {
                stringResource(R.string.slicing_progress, (fraction * 100).roundToInt())
            },
            detail = slicing.progress?.detail,
            progress = fraction,
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onCancelSlicing,
        )
    } else {
        state.plateObject?.let { ObjectInfo(it) }
    }
}

@Composable
private fun ObjectInfo(plateObject: PlateObject) {
    OrcaNotification {
        OrcaNotificationText(stringResource(R.string.object_name, plateObject.displayName()), emphasized = true)
        if (plateObject is PlateObject.ImportedModel) {
            OrcaNotificationText(stringResource(R.string.object_size, plateObject.inspection.dimensions.sizeText()))
            OrcaNotificationText(stringResource(R.string.object_triangles, plateObject.inspection.facetCount))
        }
    }
}

private val PreviewState = PrepareUiState(
    importing = false,
    plateObject = PlateObject.CalibrationCube,
    slicing = PlateSlicing(SliceJobId("preview"), SliceProgress(SliceJobId("preview"), 0.45f, SliceStage.SLICING, "Generating infill toolpath")),
    problem = null,
    canEditPlate = false,
    canSlice = false,
)

@Preview(name = "Compact", widthDp = 400, heightDp = 800)
@Preview(name = "Compact dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrepareCompactPreview() = OrcinusTheme {
    PrepareScreen(PreviewState, OrcaWindowLayout.Compact, {}, {}, {}, {}, {})
}

@Preview(name = "Wide", widthDp = 1000, heightDp = 640)
@Composable
private fun PrepareWidePreview() = OrcinusTheme {
    PrepareScreen(PreviewState.copy(slicing = null, canEditPlate = true, canSlice = true), OrcaWindowLayout.Wide, {}, {}, {}, {}, {})
}
