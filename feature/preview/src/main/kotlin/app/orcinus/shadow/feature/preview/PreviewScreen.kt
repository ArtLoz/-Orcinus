package app.orcinus.shadow.feature.preview

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaCanvas
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaInfoItem
import app.orcinus.shadow.core.designsystem.component.OrcaInfoPanel
import app.orcinus.shadow.core.designsystem.layout.OrcaSidebarToggleSpace
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.HostStorageOutcome
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.SentFilament
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.core.ui.ExportResultDialog
import app.orcinus.shadow.core.ui.LocalToolpathsExport
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.filamentLength
import app.orcinus.shadow.core.ui.network.rememberLocalNetworkAccess
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.plate.CanvasViewButtons
import app.orcinus.shadow.core.ui.plate.DailyTipsPanel
import app.orcinus.shadow.core.ui.plate.ExportFinishedNotification
import app.orcinus.shadow.core.ui.plate.PlateStrip
import app.orcinus.shadow.core.ui.plate.PostProcessSkippedNotification
import app.orcinus.shadow.core.ui.plate.SliceButton
import app.orcinus.shadow.core.ui.plate.SliceCompletedNotification
import app.orcinus.shadow.core.ui.plate.SliceNoticeNotification
import app.orcinus.shadow.core.ui.plate.SlicingNotification
import app.orcinus.shadow.core.ui.plate.navigatorFaceLabels
import app.orcinus.shadow.core.ui.printTime
import app.orcinus.shadow.core.ui.settings.SendToPrinterSheet
import app.orcinus.shadow.core.ui.settings.SettingsNoticeDialog
import app.orcinus.shadow.core.ui.settings.openInBrowser
import app.orcinus.shadow.core.ui.shareDocument
import app.orcinus.shadow.domain.plate.AllPlatesSliceState
import app.orcinus.shadow.render.gcode.GcodeLines
import app.orcinus.shadow.render.gcode.ToolpathsLayer
import app.orcinus.shadow.render.gcode.ToolpathsMoveType
import app.orcinus.shadow.render.scene.PlateGraphics
import app.orcinus.shadow.render.scene.PlateNavigator
import app.orcinus.shadow.render.scene.PlateShells
import app.orcinus.shadow.render.scene.PlateView
import app.orcinus.shadow.render.scene.PlateViewOptions
import app.orcinus.shadow.render.scene.rememberPlateViewCamera
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PreviewRoute(
    viewModel: PreviewViewModel,
    onSliceRequested: () -> Unit,
    onOpenDevice: () -> Unit = {},
    onOpenPrepare: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canvas by viewModel.canvas.collectAsStateWithLifecycle()
    PreviewScreen(
        state = state,
        layout = currentOrcaWindowLayout(),
        canvas = canvas,
        onSetCanvas = viewModel::setCanvasOption,
        onSelectPlate = viewModel::selectPlate,
        onJumpTo = { mesh, instance ->
            viewModel.jumpTo(mesh, instance)
            onOpenPrepare()
        },
        onSlice = {
            onSliceRequested()
            viewModel.slice()
        },
        onSliceModeChange = viewModel::chooseSliceMode,
        onShowAllPlates = viewModel::showAllPlates,
        printers = PrinterActions(
            load = viewModel::printerHost,
            send = viewModel::send,
            slots = viewModel::printerSlots,
            filaments = viewModel::sentFilaments,
            flashforgeSlots = viewModel::flashforgeSlots,
            plateBedType = viewModel::plateBedType,
            printer3dOsLists = viewModel::printer3dOsLists,
            recentChoices = viewModel::recentSendChoices,
            keepChoices = viewModel::keepSendChoices,
            groups = viewModel::hostGroups,
            storage = viewModel::hostStorage,
            switchToDeviceTab = viewModel::switchToDeviceTab,
            keepSwitchToDeviceTab = viewModel::keepSwitchToDeviceTab,
            openDevice = onOpenDevice,
        ),
        gcodeName = viewModel::gcodeName,
        gcodeNameError = viewModel::gcodeNameError,
        onCancelSlicing = viewModel::cancelSlicing,
        onExportGcode = viewModel::exportGcode,
        exportedName = viewModel::exportedName,
        onShareGcode = viewModel::shareGcode,
        layerGcodeActions = LayerGcodeActions(
            addPause = viewModel::addPause,
            addTemplate = viewModel::addTemplate,
            changeFilament = viewModel::changeFilament,
            setCustom = viewModel::setCustomGcode,
            delete = viewModel::deleteLayerGcode,
        ),
    )
}

/** What the send sheet needs of the app: the printer preset's host, and what to do with it. */
internal class PrinterActions(
    val load: suspend () -> PrinterConnectionOutcome,
    val send: suspend (PhysicalPrinter, Boolean, PrintOptions, (Float) -> Unit) -> PrintHostUploadOutcome,
    /** The slots of a printer's material boxes, and the plate's filaments they are matched to. */
    val slots: suspend (PhysicalPrinter) -> PrinterSlotsOutcome = { PrinterSlotsOutcome.Success(emptyList()) },
    val filaments: () -> List<SentFilament> = { emptyList() },
    /** The slots of a Flashforge printer's material station. */
    val flashforgeSlots: suspend (PhysicalPrinter) -> FlashforgeSlotsOutcome = { FlashforgeSlotsOutcome.Failure("") },
    /** The plate's type as a BedType value, which the Elegoo dialog checks its plate side against. */
    val plateBedType: () -> Int = { 1 },
    /** 3DPrinterOS's session check and the cloud's projects and printer types. */
    val printer3dOsLists: suspend (PhysicalPrinter) -> Printer3dOsListsOutcome = { Printer3dOsListsOutcome.Failure("") },
    /** The send dialogs' last choices, which OrcaSlicer.conf keeps in "recent". */
    val recentChoices: suspend (List<String>) -> Map<String, String> = { emptyMap() },
    val keepChoices: (Map<String, String>) -> Unit = {},
    /** The groups and the storages the dialog offers (get_groups(), get_storage()). */
    val groups: suspend (PhysicalPrinter) -> List<String> = { emptyList() },
    val storage: suspend (PhysicalPrinter) -> HostStorageOutcome = { HostStorageOutcome.Success(emptyList(), emptyList()) },
    /** open_device_tab_post_upload, which the dialog's check box shows and keeps. */
    val switchToDeviceTab: () -> Boolean = { false },
    val keepSwitchToDeviceTab: (Boolean) -> Unit = {},
    /** The Device tab, which an upload with the check box shows. */
    val openDevice: () -> Unit = {},
) {
    companion object {
        val NONE = PrinterActions(
            load = { PrinterConnectionOutcome.Failure("") },
            send = { _, _, _, _ -> PrintHostUploadOutcome.Failure("") },
        )
    }
}

/** GCodeViewer::load_shells()' alpha, and GLCanvas3D::set_shell_transparence()'s once the G-code is loaded. */
private const val SHELL_ALPHA = 0.5f
private const val SHELL_ALPHA_GCODE = 0.2f

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
    /** The message that stops Export and Send when filename_format could not name the G-code. */
    gcodeNameError: () -> String? = { null },
    /** SlicingProgressNotification's Cancel. */
    onCancelSlicing: () -> Unit = {},
    onExportGcode: suspend (ExternalDocumentReference) -> Boolean = { false },
    /** The name the exported document goes by, for its notification. */
    exportedName: suspend (ExternalDocumentReference) -> String = { "" },
    /** The G-code as Android's share sheet takes it; null when there is none. */
    onShareGcode: suspend () -> ExternalDocumentReference? = { null },
    layerGcodeActions: LayerGcodeActions = LayerGcodeActions.NONE,
    onSelectPlate: (Int) -> Unit = {},
    /** "Jump to" of a slicing notification: the copy of the object on the 3D editor. */
    onJumpTo: (ScenePath, Int) -> Unit = { _, _ -> },
    onSliceModeChange: (SliceMode) -> Unit = {},
    onShowAllPlates: () -> Unit = {},
    canvas: CanvasPreferences = CanvasPreferences(),
    /** An item of the canvas's View menu, which OrcaSlicer.conf keeps. */
    onSetCanvas: (key: String, value: String) -> Unit = { _, _ -> },
) {
    val result = state.result
    val viewCamera = rememberPlateViewCamera()
    val untitled = orcaString("Untitled")
    var sending by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // A printer on Wi-Fi is a device of the local network, so the sheet opens
    // once Android 17 has asked the user for it.
    var localNetworkDenied by rememberSaveable { mutableStateOf(false) }
    val openSending = rememberLocalNetworkAccess { denied ->
        localNetworkDenied = denied
        sending = true
    }
    // Plater::priv::warnings_dialog(): once an export or an upload begins, the
    // warnings of the slice's steps, the first line of each.
    var warningsShown by remember { mutableStateOf(false) }
    val stepWarnings = result?.notices.orEmpty().filter { it.stepWarning }
    val beginExport = { if (stepWarnings.isNotEmpty()) warningsShown = true }
    // PartPlate::is_slice_result_ready_for_print(): exporting, sending and printing wait for it.
    val printReady = result?.printReady == true
    // Plater::export_gcode() and Plater::send_gcode(): a template that could
    // not name the file shows its error, and nothing is saved or sent.
    var nameError by remember { mutableStateOf<String?>(null) }
    val send = { gcodeNameError()?.let { nameError = it } ?: openSending() }
    var sent by remember { mutableStateOf<PrintHostUploadOutcome?>(null) }
    // Http::on_progress while the file travels; null when nothing is going out.
    var progress by remember { mutableStateOf<Float?>(null) }
    var saved by remember { mutableStateOf<Boolean?>(null) }
    // ExportFinishedNotification: the name of the file the last export wrote.
    var exported by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Export G-code: the file goes into a document of the user's own.
    val gcodePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GCODE_MIME_TYPE)) { uri ->
        uri?.let {
            scope.launch {
                val document = ExternalDocumentReference(it.toString())
                beginExport()
                // Plater::export_gcode(): a notification says where the file went; a failure keeps its message box.
                if (onExportGcode(document)) exported = exportedName(document) else saved = false
            }
        }
    }
    val inspection = LocalInspectionMode.current
    val toolpaths = result?.toolpaths
    // render_position_window()'s properties_shown, kept while the app runs.
    var propertiesShown by rememberSaveable { mutableStateOf(false) }
    var profileShown by rememberSaveable { mutableStateOf(false) }
    // GCodeWindow::load_gcode(): the G-code of the slice, with where its lines end.
    val gcodePath = result?.gcode?.value
    val gcodeLines by produceState<GcodeLines?>(null, gcodePath) {
        value = if (inspection) null else gcodePath?.let { GcodeLines.open(it) }
    }
    // The plate view owns the layer once it has it.
    val layer by produceState<ToolpathsLayer?>(null, toolpaths) {
        value = if (inspection) null else toolpaths?.let { ToolpathsLayer.load(it) }
    }
    val shown = layer
    val view = shown?.view?.collectAsStateWithLifecycle()?.value
    // The File menu's "Export toolpaths as OBJ" writes what the preview shows, while it shows toolpaths.
    val toolpathsExport = LocalToolpathsExport.current
    val exportable = shown?.takeIf { view != null }
    DisposableEffect(exportable) {
        toolpathsExport.value = exportable?.let { layer -> { path: String -> withContext(Dispatchers.IO) { layer.exportToObj(path) } } }
        onDispose { toolpathsExport.value = null }
    }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // GLCanvas3D::_render_imgui_select_plate_toolbar(): the statistics of all
    // plates show instead of the plate once they are all sliced.
    val allPlates = state.allPlates
    val allPlatesShown = allPlates != null && allPlates.selected && allPlates.state == AllPlatesSliceState.SLICED
    val peek = if (shown != null && view != null && !allPlatesShown) SheetPeekHeight + navigationBar else 0.dp

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
                    SliceButton(
                        mode = state.sliceMode,
                        enabled = state.sliceEnabled,
                        onSlice = onSlice,
                        onModeChange = onSliceModeChange,
                        text = progress?.let { stringResource(R.string.slicing_progress, (it * 100).roundToInt()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Export G-code of the desktop app's File menu.
                        OrcaButton(
                            text = stringResource(UiR.string.gcode_save),
                            onClick = { gcodeNameError()?.let { nameError = it } ?: gcodePicker.launch(gcodeName()) },
                            enabled = printReady,
                            modifier = Modifier.weight(1f),
                        )
                        OrcaButton(
                            text = stringResource(UiR.string.printer_host_send),
                            onClick = send,
                            enabled = printReady,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                        // Android's share sheet, the phone's way of handing the file to another app.
                        OrcaIconButton(
                            icon = DesignR.drawable.app_share,
                            contentDescription = stringResource(UiR.string.share),
                            onClick = { scope.launch { onShareGcode()?.let { context.shareDocument(it, GCODE_MIME_TYPE) } } },
                            enabled = printReady,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                ToolpathsSheet(
                    view = view,
                    statistics = shown.statistics,
                    imperial = canvas.imperialUnits,
                    onViewTypeChange = shown::setViewType,
                    onRoleVisibleChange = shown::setRoleVisible,
                    onOptionVisibleChange = shown::setOptionVisible,
                    gcodeWindow = canvas.gcodeWindow,
                    onGcodeWindowChange = { onSetCanvas(AppConfigKeys.SHOW_GCODE_WINDOW, it.toString()) },
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
                    // Preview::load_print_as_fff(): the shells, more see-through once the G-code shows.
                    shells = PlateShells(state.shells, if (view != null) SHELL_ALPHA_GCODE else SHELL_ALPHA),
                    toolMarker = view?.marker,
                    filamentColors = state.filamentColors.mapNotNull(::parseFilamentColor),
                    // The toolpaths stand where the current plate does (GCodeProcessor::set_xy_offset).
                    plateOrigins = state.plateOrigins,
                    currentPlate = state.currentPlate,
                    followCurrentPlate = true,
                    plateNames = state.plateNames.map { it.ifEmpty { untitled } },
                    orbitSpeed = canvas.orbitSpeed,
                    freeCamera = canvas.freeCamera,
                    zoomToFingers = canvas.zoomToMouse,
                    // The FPS overlay beside the sidebar button; the layer slider takes the top right corner.
                    graphics = PlateGraphics(canvas.fxaa, canvas.fpsCap, canvas.fpsOverlay, Alignment.TopStart, PaddingValues(start = 58.dp, top = 19.dp)),
                    antialiasingSamples = canvas.antialiasingSamples,
                    options = PlateViewOptions(
                        perspective = canvas.perspective,
                        autoPerspective = canvas.autoPerspective,
                        axes = canvas.axes,
                        gridlines = canvas.gridlines,
                        phong = canvas.realistic && canvas.phong,
                        ssao = canvas.realistic && canvas.ssao,
                    ),
                    onPerspectiveChange = { onSetCanvas(AppConfigKeys.USE_PERSPECTIVE_CAMERA, it.toString()) },
                    camera = viewCamera,
                    smoothNormals = canvas.realistic && canvas.smoothNormals,
                )
                // The 3D navigator and the canvas toolbar (View menu and zoom button) under it; the layer
                // slider takes the right side and the legend and the move slider the bottom, so they stand
                // under the sidebar's button.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(start = 12.dp, top = 56.dp),
                ) {
                    if (canvas.navigator) PlateNavigator(viewCamera, navigatorFaceLabels())
                    CanvasViewButtons(
                        canvas = canvas,
                        onView = { view -> if (view == null) viewCamera.defaultView() else viewCamera.selectView(view) },
                        onSet = { key, value ->
                            onSetCanvas(key, value)
                            // MainFrame's camera menu items run update_ui_from_settings().
                            if (key == AppConfigKeys.USE_PERSPECTIVE_CAMERA) viewCamera.applyFreeCameraCorrection()
                        },
                        onZoom = viewCamera::zoomToFit,
                        preview = true,
                    )
                }
            }
            if (allPlatesShown) {
                AllPlatesStatsPanel(
                    statistics = allPlates.statistics,
                    imperial = canvas.imperialUnits,
                    filamentColors = state.filamentColors.map { value -> parseFilamentColor(value)?.let { Color(it.red, it.green, it.blue, it.alpha) } ?: OrcaTheme.colors.accent },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                )
            }
            // The notifications of the preview's canvas, under the plate bar, beside the
            // navigator and clear of the layer slider and of the label of its top layer.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 96.dp, end = 72.dp, top = 112.dp),
            ) {
                val showHints = canvas.showHints
                val keepHints = { on: Boolean -> onSetCanvas(AppConfigKeys.SHOW_HINTS, on.toString()) }
                // The slicing progress Orca shows on the preview's canvas once Slice moved there.
                val progress = state.slicingProgress
                val job = state.slicingJob
                SliceCompletedNotification(state.slicesCompleted, sliceRunning = progress != null && job != null) { DailyTipsPanel(showHints, keepHints) }
                if (progress != null && job != null) {
                    SlicingNotification(
                        job = job,
                        title = if (state.slicingCancelling) {
                            stringResource(R.string.slicing_cancelling)
                        } else {
                            stringResource(R.string.slicing_progress, (progress * 100).roundToInt())
                        },
                        detail = state.slicingDetail,
                        progress = progress,
                        cancelLabel = orcaString("Cancel"),
                        onCancel = onCancelSlicing,
                        showHints = showHints,
                        onShowHints = keepHints,
                    )
                }
                exported?.let { name -> ExportFinishedNotification(name, onClose = { exported = null }) }
                // The slicing notifications stay on the preview (NotificationManager::set_in_preview()).
                state.result?.let { sliced ->
                    sliced.notices.forEach { notice ->
                        val target: PlateObject? = sliced.objects.getOrNull(notice.objectIndex)
                        SliceNoticeNotification(
                            notice,
                            target?.displayName(),
                            onJumpTo = { if (target != null) onJumpTo(target.mesh, notice.instanceIndex.coerceAtLeast(0)) },
                        )
                    }
                    if (sliced.postProcessSkipped) PostProcessSkippedNotification()
                }
            }
            // The preview's plate bar (GLCanvas3D::_render_imgui_select_plate_toolbar), once there are several.
            if (state.plateOrigins.size > 1) {
                PlateStrip(
                    count = state.plateOrigins.size,
                    // No plate is picked while the statistics of all of them are.
                    current = if (allPlates?.selected == true) -1 else state.currentPlate,
                    onSelect = onSelectPlate,
                    enabled = state.canSelectPlate,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(start = OrcaSidebarToggleSpace, top = 12.dp),
                    leading = allPlates?.let { stats ->
                        { AllPlatesItem(stats, enabled = state.canSliceAll, onClick = onShowAllPlates) }
                    },
                )
            }
            val controls = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            when {
                allPlatesShown -> Unit

                result == null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(controls),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.preview_empty), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14)
                    if (state.sliceEnabled) {
                        SliceButton(mode = state.sliceMode, enabled = true, onSlice = onSlice, onModeChange = onSliceModeChange)
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
                    // GCodeViewer::SequentialView::render(): the window while it is on and the move has a line.
                    gcodeWindow = gcodeLines?.takeIf { canvas.gcodeWindow && view.currentLine > 0 }?.let { lines ->
                        { windowModifier -> GcodeWindow(lines, view.currentLine, windowModifier) }
                    },
                    // SequentialView::render(): the marker's position window while the marker shows.
                    positionWindow = view.vertex?.let { vertex ->
                        { windowModifier ->
                            ToolPositionWindow(vertex, view.viewType, propertiesShown, { propertiesShown = it }, windowModifier)
                        }
                    },
                    positionDetails = view.vertex?.takeIf { propertiesShown }?.let { vertex ->
                        {
                            // The profile's window by the properties (ToolPositionTableWnd), above them here.
                            val profileExists = vertex.extrusion || vertex.type == ToolpathsMoveType.Travel || vertex.type == ToolpathsMoveType.Wipe
                            view.speedProfile?.takeIf { profileShown && profileExists }?.let { ActualSpeedWindow(it, Modifier.fillMaxWidth()) }
                            ToolPropertiesWindow(vertex, profileShown, { profileShown = it }, Modifier.fillMaxWidth())
                        }
                    },
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
                        onClick = send,
                        enabled = printReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (warningsShown) {
        SettingsNoticeDialog(
            SettingsDialog(
                id = "warnings_dialog",
                icon = DialogIcon.INFO,
                title = listOf(OrcaText("warnings")),
                text = listOf(OrcaText("There are warnings after slicing models:"), OrcaText("\n")) +
                    stepWarnings.map { warning -> OrcaText("\n" + warning.text.firstOrNull()?.msgid.orEmpty().substringBefore('\n')) },
                question = false,
                yes = null,
                no = null,
            ),
            onDismiss = { warningsShown = false },
        )
    }

    if (sending) {
        SendToPrinterSheet(
            load = printers.load,
            onSend = { printer, startPrint, options ->
                sending = false
                beginExport()
                progress = 0f
                // The screen's own scope: the sheet is gone while the file travels.
                scope.launch {
                    // send_gcode_legacy(): a .gcode.3mf names the plate it carries, 1-based.
                    val sentOptions = if (options.use3mf) options.copy(plateIndex = state.currentPlate + 1) else options
                    val outcome = printers.send(printer, startPrint, sentOptions) { part -> progress = part }
                    sent = outcome
                    progress = null
                    // PrintHostJobQueue::priv::perform_job(): the Device tab once the upload went through.
                    if (outcome is PrintHostUploadOutcome.Success && sentOptions.switchToDeviceTab) printers.openDevice()
                    // A host that opens a page after the upload (SimplyPrint's import, 3DPrinterOS's quick print):
                    // the desktop opens it in the browser.
                    (outcome as? PrintHostUploadOutcome.Success)?.openUrl?.let { openInBrowser(context, it) }
                }
            },
            onDismiss = { sending = false },
            loadSlots = printers.slots,
            loadFlashforgeSlots = printers.flashforgeSlots,
            loadPrinter3dOsLists = printers.printer3dOsLists,
            loadRecent = printers.recentChoices,
            keepRecent = printers.keepChoices,
            uploadName = gcodeName(),
            loadGroups = printers.groups,
            loadStorage = printers.storage,
            switchToDeviceTab = printers.switchToDeviceTab(),
            keepSwitchToDeviceTab = printers.keepSwitchToDeviceTab,
            plateBedType = printers.plateBedType(),
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
        ExportResultDialog(orcaString("Export G-code"), written, stringResource(UiR.string.gcode_saved), onDismiss = { saved = null })
    }
    nameError?.let { message ->
        NameErrorDialog(message, onDismiss = { nameError = null })
    }
}

/**
 * show_error() with a monospaced font, as Plater shows a PlaceholderParserError:
 * "Failed processing of the filename_format template." and the parser's message.
 */
@Composable
private fun NameErrorDialog(message: String, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    val first = message.substringBefore('\n')
    val rest = message.substringAfter('\n', "")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString("Error"), style = OrcaTheme.typography.head16) },
        text = {
            Text(
                text = orcaString(first) + if (rest.isEmpty()) "" else "\n" + rest,
                color = colors.text,
                style = OrcaTheme.typography.body13.copy(fontFamily = FontFamily.Monospace),
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
            // The objects the plate printed: a copy inside its build volume when it was sliced.
            OrcaInfoItem(
                stringResource(R.string.model),
                result.objects.filter { plateObject -> plateObject.instances.any { it.printable && it.inspection.fit == BuildVolumeFit.INSIDE } }
                    .map { it.displayName() }.joinToString(),
            ),
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

