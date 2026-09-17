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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
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
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.filamentLength
import app.orcinus.shadow.core.ui.printTime
import app.orcinus.shadow.render.gcode.ToolpathsLayer
import app.orcinus.shadow.render.scene.PlateView

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
 * OrcaSlicer's Preview page. The canvas fills the window edge to edge with the
 * plate and the sliced toolpaths. The legend is a bottom sheet whose collapsed
 * part sums the print up, and the layer and move controls float over the
 * canvas. Until the toolpaths are read, the slicing result sits at the bottom,
 * as the "Sliced Info" box at the bottom of OrcaSlicer's sidebar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewScreen(
    state: PreviewUiState,
    layout: OrcaWindowLayout,
    onSlice: () -> Unit,
) {
    val result = state.result
    val inspection = LocalInspectionMode.current
    val toolpaths = result?.toolpaths
    // The plate view owns the layer once it has it.
    val layer by produceState<ToolpathsLayer?>(null, toolpaths) {
        value = if (inspection) null else toolpaths?.let { ToolpathsLayer.load(it) }
    }
    val shown = layer
    val view = shown?.view?.collectAsStateWithLifecycle()?.value
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peek = if (shown != null && view != null) SheetPeekHeight + navigationBar else 0.dp

    BottomSheetScaffold(
        sheetContent = {
            if (shown != null && view != null) {
                ToolpathsSheet(
                    view = view,
                    statistics = shown.statistics,
                    onViewTypeChange = shown::setViewType,
                    onRoleVisibleChange = shown::setRoleVisible,
                    onOptionVisibleChange = shown::setOptionVisible,
                )
            }
        },
        sheetPeekHeight = peek,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContainerColor = OrcaTheme.colors.window,
        sheetContentColor = OrcaTheme.colors.text,
        sheetShadowElevation = 8.dp,
        sheetDragHandle = null,
        containerColor = Color.Transparent,
    ) {
        OrcaCanvas(Modifier.fillMaxSize()) {
            // Previews have no OpenGL; they show the canvas colour.
            if (!inspection) {
                PlateView(
                    plate = state.plate,
                    objects = emptyList(),
                    selectedObject = null,
                    gizmo = null,
                    flatteningPlanes = emptyList(),
                    editable = false,
                    onSelectObject = {},
                    onPlaceObject = { _, _, _ -> },
                    onOpenObjectMenu = { _, _ -> },
                    contentDescription = stringResource(R.string.preview_view),
                    modifier = Modifier.fillMaxSize(),
                    layer = shown,
                )
            }
            val controls = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            when {
                result == null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(controls),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.preview_empty), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14)
                    if (state.canSlice) {
                        OrcaButton(stringResource(R.string.slice_plate), onClick = onSlice)
                    }
                }

                shown != null && view != null -> ToolpathsControls(
                    layer = shown,
                    view = view,
                    bottomInset = peek,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                )

                shown == null -> SlicedInfo(
                    result = result,
                    modifier = Modifier
                        .align(if (layout == OrcaWindowLayout.Wide) Alignment.BottomStart else Alignment.BottomCenter)
                        .then(if (layout == OrcaWindowLayout.Wide) Modifier.widthIn(max = 480.dp) else Modifier),
                )
            }
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
            OrcaInfoItem(stringResource(R.string.model), result.objects.map { it.displayName() }.joinToString()),
        ),
        modifier = modifier,
    )
}

private val PreviewResult = PlateSliceResult(
    jobId = SliceJobId("preview"),
    objects = listOf(
        PlateObject.CalibrationCube(
            ModelInspection(
                facetCount = 12,
                dimensions = ModelDimensions(20.0, 20.0, 20.0),
                boxCenter = Vector3(0.0, 0.0, 10.0),
                mesh = ScenePath("preview.mesh"),
                placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
                fit = BuildVolumeFit.INSIDE,
                boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
                rotationDegrees = Vector3(0.0, 0.0, 0.0),
                unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
            ),
        ),
    ),
    gcode = OutputPath("/files/gcode/calibration-cube-20mm.gcode"),
    statistics = SliceStatistics(100, 731, 1209.0),
)

@Preview(name = "Compact", widthDp = 400, heightDp = 800)
@Preview(name = "Compact dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCompactPreview() = OrcinusTheme {
    Box(Modifier.fillMaxSize()) {
        PreviewScreen(PreviewUiState(plate = null, result = PreviewResult, canSlice = true), OrcaWindowLayout.Compact, onSlice = {})
    }
}

@Preview(name = "Wide", widthDp = 1000, heightDp = 640)
@Composable
private fun PreviewWidePreview() = OrcinusTheme {
    PreviewScreen(PreviewUiState(plate = null, result = PreviewResult, canSlice = true), OrcaWindowLayout.Wide, onSlice = {})
}
