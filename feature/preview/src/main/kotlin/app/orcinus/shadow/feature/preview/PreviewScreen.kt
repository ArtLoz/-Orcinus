package app.orcinus.shadow.feature.preview

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.filamentLength
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.printTime
import app.orcinus.shadow.core.ui.settings.SendToPrinterSheet
import app.orcinus.shadow.core.ui.settings.SentFilament
import app.orcinus.shadow.render.gcode.ToolpathsLayer
import app.orcinus.shadow.render.scene.PlateView
import kotlinx.coroutines.launch
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

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
        printers = PrinterActions(
            load = viewModel::printers,
            save = viewModel::savePrinter,
            delete = viewModel::deletePrinter,
            send = viewModel::send,
            slots = viewModel::printerSlots,
            test = viewModel::testPrinter,
            presets = viewModel::printerPresets,
            filaments = viewModel::sentFilaments,
        ),
        gcodeName = viewModel::gcodeName,
        onExportGcode = viewModel::exportGcode,
        layerGcodeActions = LayerGcodeActions(
            addPause = viewModel::addPause,
            addTemplate = viewModel::addTemplate,
            changeFilament = viewModel::changeFilament,
            setCustom = viewModel::setCustomGcode,
            delete = viewModel::deleteLayerGcode,
        ),
    )
}

/** What the send sheet needs of the app: the printers, and what to do with them. */
internal class PrinterActions(
    val load: suspend () -> PhysicalPrintersOutcome,
    val save: suspend (PhysicalPrinter, String?) -> PhysicalPrintersOutcome,
    val delete: suspend (String) -> PhysicalPrintersOutcome,
    val send: suspend (PhysicalPrinter, Boolean, PrintOptions, (Float) -> Unit) -> PrintHostUploadOutcome,
    /** The slots of a printer's material boxes, and the plate's filaments they are matched to. */
    val slots: suspend (PhysicalPrinter) -> PrinterSlotsOutcome = { PrinterSlotsOutcome.Success(emptyList()) },
    /** PhysicalPrinterDialog's Test button (PrintHost::test). */
    val test: suspend (PhysicalPrinter) -> PrintHostTestOutcome = { PrintHostTestOutcome.Failure("") },
    /** The printer presets a printer can be bound to. */
    val presets: suspend () -> List<String> = { emptyList() },
    val filaments: () -> List<SentFilament> = { emptyList() },
) {
    companion object {
        val NONE = PrinterActions(
            load = { PhysicalPrintersOutcome.Failure("") },
            save = { _, _ -> PhysicalPrintersOutcome.Failure("") },
            delete = { PhysicalPrintersOutcome.Failure("") },
            send = { _, _, _, _ -> PrintHostUploadOutcome.Failure("") },
        )
    }
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
    printers: PrinterActions = PrinterActions.NONE,
    /** Export G-code: the name the file is offered under, and the save itself. */
    gcodeName: () -> String = { "plate.gcode" },
    onExportGcode: suspend (ExternalDocumentReference) -> Boolean = { false },
    layerGcodeActions: LayerGcodeActions = LayerGcodeActions.NONE,
) {
    val result = state.result
    var sending by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Android 17 asks the user before an app reaches a device of the local
    // network; a printer on Wi-Fi is one, so the sheet opens after the answer.
    var localNetworkDenied by rememberSaveable { mutableStateOf(false) }
    val localNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localNetworkDenied = !granted
        sending = true
    }
    val openSending = {
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK &&
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            localNetworkDenied = false
            sending = true
        }
    }
    var sent by remember { mutableStateOf<PrintHostUploadOutcome?>(null) }
    // Http::on_progress while the file travels; null when nothing is going out.
    var progress by remember { mutableStateOf<Float?>(null) }
    var saved by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    // Export G-code: the file goes into a document of the user's own.
    val gcodePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GCODE_MIME_TYPE)) { uri ->
        uri?.let { scope.launch { saved = onExportGcode(ExternalDocumentReference(it.toString())) } }
    }
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
                // PrintHost::upload: the sliced plate goes to a printer of the
                // user, which the desktop app sends from its sidebar.
                // A code changed on the slider makes the slice invalid
                // (PartPlate::update_slice_result_valid_state): the plate is sliced again first.
                if (state.outdated) {
                    Text(
                        text = stringResource(R.string.layer_codes_outdated),
                        color = OrcaTheme.colors.textSide,
                        style = OrcaTheme.typography.body13,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    )
                }
                if (state.outdated) {
                    // MainFrame::update_slice_print_status(): the slice button is back, printing waits for it.
                    val progress = state.slicingProgress
                    OrcaButton(
                        text = if (progress != null) stringResource(R.string.slicing_progress, (progress * 100).roundToInt()) else stringResource(R.string.slice_plate),
                        onClick = onSlice,
                        enabled = state.canSlice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Export G-code of the desktop app's File menu.
                        OrcaButton(
                            text = stringResource(UiR.string.gcode_save),
                            onClick = { gcodePicker.launch(gcodeName()) },
                            modifier = Modifier.weight(1f),
                        )
                        OrcaButton(
                            text = stringResource(UiR.string.printer_host_send),
                            onClick = openSending,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    }
                }
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
                    layerGcodes = LayerGcodeUi(
                        codes = state.layerGcodes,
                        rules = result.layerGcodeRules,
                        filamentColors = state.filamentColors.map { value -> parseFilamentColor(value)?.let { Color(it.red, it.green, it.blue, it.alpha) } ?: OrcaTheme.colors.accent },
                        actions = layerGcodeActions,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                )

                shown == null -> Column(
                    modifier = Modifier
                        .align(if (layout == OrcaWindowLayout.Wide) Alignment.BottomStart else Alignment.BottomCenter)
                        .then(if (layout == OrcaWindowLayout.Wide) Modifier.widthIn(max = 480.dp) else Modifier),
                ) {
                    SlicedInfo(result = result)
                    // PrintHost::upload: the G-code goes to a printer of the user.
                    OrcaButton(
                        text = stringResource(UiR.string.printer_host_send),
                        onClick = openSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (sending) {
        SendToPrinterSheet(
            load = printers.load,
            onSave = printers.save,
            onDelete = printers.delete,
            onSend = { printer, startPrint, options ->
                sending = false
                progress = 0f
                // The screen's own scope: the sheet is gone while the file travels.
                scope.launch {
                    sent = printers.send(printer, startPrint, options) { part -> progress = part }
                    progress = null
                }
            },
            onDismiss = { sending = false },
            loadSlots = printers.slots,
            onTest = printers.test,
            loadPresets = printers.presets,
            filaments = printers.filaments(),
            notice = if (localNetworkDenied) stringResource(UiR.string.printer_host_local_network) else null,
        )
    }
    progress?.let { part ->
        SendProgressDialog(part)
    }
    sent?.let { outcome ->
        SendResultDialog(outcome, onDismiss = { sent = null })
    }
    saved?.let { written ->
        SaveResultDialog(written, onDismiss = { saved = null })
    }
}

/** Whether the G-code reached the document the user picked. */
@Composable
private fun SaveResultDialog(written: Boolean, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString("Export G-code"), style = OrcaTheme.typography.head16) },
        text = {
            Text(
                text = stringResource(if (written) UiR.string.gcode_saved else UiR.string.gcode_save_failed),
                color = if (written) colors.text else colors.error,
                style = OrcaTheme.typography.body14,
            )
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** What the upload did, as the desktop app reports it. */
/** Http::on_progress while the G-code travels to the printer; it cannot be dismissed. */
@Composable
private fun SendProgressDialog(part: Float) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        confirmButton = {},
        title = { Text(orcaString("Send G-code"), style = OrcaTheme.typography.head16) },
        text = {
            Column {
                Text(
                    text = stringResource(UiR.string.printer_host_sending, (part * 100).roundToInt()),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                )
                LinearProgressIndicator(
                    progress = { part },
                    color = colors.accent,
                    trackColor = colors.separator,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

@Composable
private fun SendResultDialog(outcome: PrintHostUploadOutcome, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString("Send G-code"), style = OrcaTheme.typography.head16) },
        text = {
            when (outcome) {
                is PrintHostUploadOutcome.Failure -> Text(outcome.message, color = colors.error, style = OrcaTheme.typography.body14)
                is PrintHostUploadOutcome.Success -> Text(
                    text = stringResource(UiR.string.printer_host_sent, outcome.path),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
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

/** The type a G-code file is offered under, as OrcaSlicer writes .gcode. */
private const val GCODE_MIME_TYPE = "text/x-gcode"

private val PreviewResult = PlateSliceResult(
    jobId = SliceJobId("preview"),
    objects = listOf(
        PlateObject.CalibrationCube(
            listOf(
                PlateInstance(
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

/** Android 17, which guards the local network with ACCESS_LOCAL_NETWORK. */
private const val LOCAL_NETWORK_SDK = 37
