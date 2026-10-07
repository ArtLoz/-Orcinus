package app.orcinus.shadow.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.HostStorageOutcome
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.ElegooOptions
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PreviewOnlyKind
import app.orcinus.shadow.core.model.PrintHostJob
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.model.ProfileUpdatesNotice
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.core.model.SentFilament
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.PlateNotice
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PrinterConnection
import app.orcinus.shadow.core.model.PrintHostType
import app.orcinus.shadow.core.model.allSliceResultsReady
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.shownInPreview
import app.orcinus.shadow.domain.plate.AllPlatesStats
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.EditLayerGcodesUseCase
import app.orcinus.shadow.domain.plate.ExportGcodeUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.ObservePrinterConnectionUseCase
import app.orcinus.shadow.domain.plate.OpenSimplifyUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SelectSlicedPlateUseCase
import app.orcinus.shadow.domain.plate.SendGcodeUseCase
import app.orcinus.shadow.domain.plate.SetSliceModeUseCase
import app.orcinus.shadow.domain.plate.ShareGcodeUseCase
import app.orcinus.shadow.domain.plate.ShowAllPlatesStatsUseCase
import app.orcinus.shadow.domain.plate.SliceActionUseCase
import app.orcinus.shadow.domain.plate.allPlatesStats
import app.orcinus.shadow.domain.plate.currentBedType
import app.orcinus.shadow.domain.plate.FindValidationSettingUseCase
import app.orcinus.shadow.domain.plate.PlateSlice
import app.orcinus.shadow.domain.plate.SaveProjectUseCase
import app.orcinus.shadow.domain.plate.plateSlices
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.preferences.RecentSendChoicesUseCase
import app.orcinus.shadow.domain.preferences.SetPreferenceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

data class PreviewUiState(
    /** The printer's plate for the 3D view; null until the engine described it. */
    val plate: PlateDescription?,
    val result: PlateSliceResult?,
    val canSlice: Boolean,
    /** What the slice button slices, and whether it can now (MainFrame::get_enable_slice_status). */
    val sliceMode: SliceMode = SliceMode.PLATE,
    val sliceEnabled: Boolean = canSlice,
    /** The codes the layer slider put on the layers. */
    val layerGcodes: List<LayerGcode> = emptyList(),
    /** The colour of every filament of the plate, "#RRGGBB". */
    val filamentColors: List<String> = emptyList(),
    /** How far the slice that is running has got, from 0 to 1; null when nothing is being sliced. */
    val slicingProgress: Float? = null,
    /** The slice that runs, its status text, and whether it is being cancelled (SlicingProgressNotification). */
    val slicingJob: SliceJobId? = null,
    val slicingDetail: String? = null,
    val slicingCancelling: Boolean = false,
    /** PlateState.slicesCompleted, which "Slice ok." follows. */
    val slicesCompleted: Int = 0,
    /** Where every plate stands (PartPlateList), and the one whose G-code the preview shows. */
    val plateOrigins: List<Point2> = listOf(Point2(0.0, 0.0)),
    val currentPlate: Int = 0,
    /** PartPlate::get_bed_type() of the current plate, whose pictures a Bambu Lab plate shows. */
    val bedType: String? = null,
    /**
     * Plater::priv::set_current_panel()'s enable_select_plate_toolbar(): the
     * plate bar of several plates, but not of a G-code file nor of an exported
     * file of one sliced plate.
     */
    val plateBar: Boolean = plateOrigins.size > 1,
    /** Another plate can be shown: nothing is being sliced or changed. */
    val canSelectPlate: Boolean = false,
    /** The plates' names, empty for one the user did not name. */
    val plateNames: List<String> = listOf(""),
    /** The all plates stats item of the plate bar; null while there is none. */
    val allPlates: AllPlatesStats? = null,
    /** "Slice all" can start, which the all plates stats item does. */
    val canSliceAll: Boolean = false,
    /** GCodeViewer::load_shells(): the objects the print holds, each with the height its raft lifts it to. */
    val shells: List<Pair<PlateObject, Double>> = emptyList(),
    /** PlateState.exportFinished: the file the last export wrote, while its notification shows. */
    val exportFinished: String? = null,
    /** PlateState.simplifySuggestions: the objects advised to be simplified. */
    val simplifySuggestions: List<PlateObject> = emptyList(),
    /** PlateState.profileUpdates: "Configuration can update now." shows while it is notified. */
    val profileUpdates: ProfileUpdatesNotice? = null,
    /** PlateState.profileUpdatesInstalled: the packages a forced update installed. */
    val profileUpdatesInstalled: List<ProfileUpdate> = emptyList(),
    /** The preview-only mode of a G-code file or an exported file the preview shows; null for none. */
    val previewOnly: PreviewOnlyKind? = null,
    /** The file whose G-code load_gcode() or load_gcode_files() reads while the preview waits for it (wxBusyCursor). */
    val gcodeLoading: String? = null,
    /** IMToolbarItem::SliceState of every plate of the plate bar. */
    val plateSlices: List<PlateSlice> = emptyList(),
    /**
     * The notifications NotificationManager keeps while the preview shows
     * (set_in_preview()): the validation error, the objects laid over the
     * plate's boundary, the warnings of the plate's filaments and the plate's
     * problem; the plater warnings, the validation warning, the plates'
     * information and the objects' hide.
     */
    val validationError: PreviewValidationError? = null,
    val clashedObjects: List<PlateObject> = emptyList(),
    val plateNotices: List<PlateNotice> = emptyList(),
    val problem: PlateProblem? = null,
    /** The objects of [problem] still on the plate, which its "Jump to" names. */
    val problemObjects: List<PlateObject> = emptyList(),
    /** PlateState.slicesCancelled, which "Slicing Canceled" follows. */
    val slicesCancelled: Int = 0,
    /**
     * PartPlate::is_slice_result_ready_for_export(): the G-code may be printed
     * and the plate has a printable copy inside it, which "Export plate sliced
     * file" needs.
     */
    val readyForExport: Boolean = false,
    /** MainFrame::can_send_gcode(): the printer preset names a host, or the plate has no object. */
    val canSendGcode: Boolean = false,
    /**
     * Sidebar::update_all_preset_comboboxes(): a printer that takes the sliced
     * plate as a .gcode.3mf (use_3mf) and has no host exports the plate's
     * sliced file by default instead of its G-code.
     */
    val exportsSlicedFile: Boolean = false,
    /** PresetBundle::is_bbl_vendor(): a Bambu Lab printer, whose print button prints the plate (ePrintPlate). */
    val bambuVendor: Boolean = false,
    /**
     * The print button's "Print all" of a Bambu Lab printer sent to a host of
     * its own (bbl_use_printhost): SimplyPrint alone takes every plate.
     */
    val printAllSupported: Boolean = false,
    /** PartPlateList::is_all_slice_results_ready_for_print(), which "Print all" needs. */
    val allReadyForPrint: Boolean = false,
) {
    /** The codes changed since the slice: its G-code no longer holds them (PartPlate's invalid slice result). */
    val outdated: Boolean get() = result != null && result.layerGcodes != layerGcodes
}

/** A message of the plate's validation, with the copy it is about and the setting its "Jump to" opens. */
data class PreviewValidationError(
    val text: String,
    val target: PlateInstanceId?,
    val targetObject: PlateObject?,
    val option: String,
)

class PreviewViewModel(
    observePlate: ObservePlateUseCase,
    private val sliceAction: SliceActionUseCase,
    private val setSliceMode: SetSliceModeUseCase,
    private val selectSlicedPlate: SelectSlicedPlateUseCase,
    private val showAllPlatesStats: ShowAllPlatesStatsUseCase,
    private val printerConnection: ObservePrinterConnectionUseCase,
    private val sendGcode: SendGcodeUseCase,
    private val exportGcode: ExportGcodeUseCase,
    private val shareGcode: ShareGcodeUseCase,
    private val editLayerGcodes: EditLayerGcodesUseCase,
    preferences: AppPreferences,
    private val setPreference: SetPreferenceUseCase,
    /** The send dialogs' choices OrcaSlicer.conf keeps in "recent". */
    private val recentSendChoices: RecentSendChoicesUseCase? = null,
    /** SlicingProgressNotification's Cancel on the preview's canvas. */
    private val cancelPlateSlicing: CancelPlateSlicingUseCase? = null,
    /** A slicing notification's "Jump to" selects the object it names. */
    private val selectPlateObject: SelectPlateObjectUseCase? = null,
    /** The export's notification closes. */
    private val dismissPlateProblem: DismissPlateProblemUseCase? = null,
    /** "Simplify model" of the advice to simplify an object. */
    private val openSimplify: OpenSimplifyUseCase? = null,
    /** The setting a validation error's "Jump to" opens. */
    private val findValidationSetting: FindValidationSettingUseCase? = null,
    /** "Export plate sliced file" of the print button (Plater::export_gcode_3mf()). */
    private val saveProject: SaveProjectUseCase? = null,
) : ViewModel() {
    private val plate = observePlate()

    /** What the Preferences change on the canvas. */
    val canvas: StateFlow<CanvasPreferences> = preferences.canvas

    private val preferencesValues = preferences.values

    /** An item of the canvas's View menu, which OrcaSlicer.conf keeps. */
    fun setCanvasOption(key: String, value: String) = setPreference(key, value)

    /**
     * NotificationManager's "Jump to": the object list selects the object the
     * slicing notification names; the 3D editor opens on it.
     */
    fun jumpTo(mesh: ScenePath, instance: Int) {
        selectPlateObject?.invoke(PlateInstanceId(mesh, instance))
    }

    /** push_slicing_error_notification()'s "Jump to": the objects the error names; the 3D editor opens on them. */
    fun jumpToObjects(meshes: List<ScenePath>) {
        selectPlateObject?.objects(meshes)
    }

    /** The setting a validation error's "Jump to" opens, for the page to show. */
    private val settingJumps = Channel<SearchOption>(Channel.CONFLATED)
    val settingToOpen: Flow<SearchOption> = settingJumps.receiveAsFlow()

    /**
     * push_validate_error_notification()'s "Jump to": the object or the copy
     * the error is about is selected, and the setting it names opens on its
     * tab (Sidebar::jump_to_option()); one that names none opens the 3D editor
     * ([onEditor]).
     */
    fun jumpToValidation(error: PreviewValidationError, onEditor: () -> Unit) {
        error.target?.let { selectPlateObject?.invoke(it) }
        if (error.option.isEmpty()) return onEditor()
        val find = findValidationSetting ?: return
        viewModelScope.launch { find(error.option)?.let { settingJumps.trySend(it) } }
    }

    /**
     * The printer's connection as the print button reads it, read again when
     * the presets change or the connection is saved; null until it is read.
     */
    private val connection = MutableStateFlow<PrinterConnection?>(null)

    init {
        viewModelScope.launch {
            plate.map { it.presets to it.connectionSaves }.distinctUntilChanged().collect {
                connection.value = (printerConnection() as? PrinterConnectionOutcome.Success)?.connection
            }
        }
    }

    val state: StateFlow<PreviewUiState> = combine(observePlate(), connection) { plateState, printer -> plateState.toPreviewUiState(printer) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toPreviewUiState(null))

    /** The slice button: the plate or all plates, as its drop-down chose. */
    fun slice() = sliceAction()

    fun chooseSliceMode(mode: SliceMode) = setSliceMode(mode)

    /** The preview's plate bar (GLCanvas3D::_render_imgui_select_plate_toolbar): another plate's G-code. */
    fun selectPlate(index: Int) = selectSlicedPlate(index)

    /** The plate bar's all plates stats item: every plate is sliced, then their statistics show. */
    fun showAllPlates() = showAllPlatesStats()

    /** The layer slider's menu (IMSlider::add_code_as_tick, add_custom_gcode, delete_tick). */
    fun addPause(printZ: Double) = editLayerGcodes.add(printZ, LayerGcodeType.PAUSE_PRINT)

    fun addTemplate(printZ: Double) = editLayerGcodes.add(printZ, LayerGcodeType.TEMPLATE)

    fun changeFilament(printZ: Double, filament: Int) = editLayerGcodes.add(printZ, LayerGcodeType.TOOL_CHANGE, filament)

    fun setCustomGcode(printZ: Double, gcode: String) = editLayerGcodes.addCustom(printZ, gcode)

    fun deleteLayerGcode(printZ: Double) = editLayerGcodes.delete(printZ)

    /** Plater::send_gcode_legacy(): the host of the printer preset the G-code goes to. */
    suspend fun printerHost(): PrinterConnectionOutcome = printerConnection()

    /** PrintHost::upload: the G-code of the last slice joins the upload queue for the printer. */
    fun send(printer: PhysicalPrinter, startPrint: Boolean, options: PrintOptions) = sendGcode(printer, startPrint, options)

    /** PrintHostQueueDialog: the uploads sent since the app started. */
    val uploadJobs: StateFlow<List<PrintHostJob>> get() = sendGcode.jobs

    fun cancelUpload(id: Int) = sendGcode.cancel(id)

    fun acknowledgeUpload(id: Int) = sendGcode.acknowledge(id)

    /** CrealityPrintHostSendDialog: the slots of the printer's material boxes. */
    suspend fun printerSlots(printer: PhysicalPrinter): PrinterSlotsOutcome = sendGcode.printerSlots(printer)

    /**
     * The plate's type as a BedType value (curr_bed_type), which the Elegoo
     * send dialog checks its plate side against; OrcaSlicer's default is the
     * cool plate, 1.
     */
    fun plateBedType(): Int = ElegooOptions.bedTypeOf(plate.value.plateSettings.values["curr_bed_type"].orEmpty()) ?: 1

    /** FlashforgePrintHostSendDialog: the slots of the printer's material station. */
    suspend fun flashforgeSlots(printer: PhysicalPrinter): FlashforgeSlotsOutcome = sendGcode.flashforgeSlots(printer)

    suspend fun hostGroups(printer: PhysicalPrinter): List<String> = sendGcode.groups(printer)

    suspend fun hostStorage(printer: PhysicalPrinter): HostStorageOutcome = sendGcode.storage(printer)

    /** "Switch to Device tab after upload." as the last send left it (open_device_tab_post_upload). */
    fun switchToDeviceTab(): Boolean = preferencesValues.value[AppConfigKeys.OPEN_DEVICE_TAB_POST_UPLOAD] == "1"

    fun keepSwitchToDeviceTab(on: Boolean) = setPreference(AppConfigKeys.OPEN_DEVICE_TAB_POST_UPLOAD, if (on) "1" else "0")

    fun cancelSlicing() {
        cancelPlateSlicing?.invoke()
    }

    /** The choices a send dialog opens with (init()). */
    suspend fun recentSendChoices(keys: List<String>): Map<String, String> = recentSendChoices?.get(keys).orEmpty()

    /** The choices a send dialog keeps when it uploads (EndModal(wxID_OK)); written after the sheet is gone. */
    fun keepSendChoices(values: Map<String, String>) {
        val choices = recentSendChoices ?: return
        viewModelScope.launch { choices.set(values) }
    }

    suspend fun printer3dOsLists(printer: PhysicalPrinter): Printer3dOsListsOutcome = sendGcode.printer3dOsLists(printer)

    /**
     * The filaments of the plate, as the send dialogs match them to the
     * printer's slots, each marked with whether the slice prints with it
     * (its statistics number the filaments from 1).
     */
    fun sentFilaments(): List<SentFilament> {
        val state = plate.value
        val presets = state.presets ?: return emptyList()
        val used = state.result?.statistics?.filaments?.map { it.filament - 1 }?.toSet()
        return presets.filamentColors.mapIndexed { index, color ->
            SentFilament(
                color = color,
                type = presets.filamentTypes.getOrElse(index) { "" },
                tool = index,
                used = used == null || index in used,
            )
        }
    }

    /** Export G-code: the name the file is offered under, and the save itself. */
    fun gcodeName(): String = exportGcode.suggestedName() ?: "plate.gcode"

    /** Why filename_format could not name the G-code, which stops Export and Send; null when it could. */
    fun gcodeNameError(): String? = exportGcode.nameError()

    suspend fun exportGcode(document: ExternalDocumentReference): Boolean = exportGcode.invoke(document)

    /** "Export plate sliced file": the name it offers, the template's error that stops it, and the export of the current plate. */
    fun slicedName(): String = saveProject?.slicedName() ?: "plate.gcode.3mf"

    fun slicedNameError(): String? = saveProject?.slicedNameError()

    suspend fun exportSliced(document: ExternalDocumentReference): Boolean = saveProject?.exportSliced(document, all = false) == true

    /** The name the exported document goes by, which the export's notification shows. */
    fun dismissExportFinished() {
        dismissPlateProblem?.exportFinished()
    }

    /** The close button of the plate's problem. */
    fun dismissProblem() {
        dismissPlateProblem?.invoke()
    }

    fun dismissSimplifySuggestion(mesh: ScenePath) {
        dismissPlateProblem?.simplifySuggestion(mesh)
    }

    /** "Detail." ([detail]), or the close button, of "Configuration can update now.". */
    fun closeProfileUpdates(detail: Boolean) {
        dismissPlateProblem?.profileUpdates(detail)
    }

    fun dismissProfileUpdateInstalled(update: ProfileUpdate) {
        dismissPlateProblem?.profileUpdateInstalled(update)
    }

    /** "Simplify model": the 3D editor opens with the Simplify gizmo on the object. */
    fun simplifySuggested(mesh: ScenePath) {
        openSimplify?.suggested(mesh)
    }

    /** The G-code as the share sheet takes it; null when there is none. */
    suspend fun shareGcode(): ExternalDocumentReference? = shareGcode.invoke()

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toPreviewUiState(connection: PrinterConnection?) = PreviewUiState(
    plate = plate,
    result = result,
    canSlice = canSlice,
    sliceMode = sliceMode,
    sliceEnabled = sliceEnabled,
    layerGcodes = layerGcodes,
    filamentColors = presets?.filamentColors.orEmpty(),
    slicingProgress = slicing?.let { it.progress?.fraction ?: 0f },
    slicingJob = slicing?.jobId,
    slicingDetail = slicing?.progress?.detail,
    slicingCancelling = slicing?.cancelling == true,
    slicesCompleted = slicesCompleted,
    plateOrigins = plateOrigins(),
    plateBar = when (previewOnly?.kind) {
        null -> true
        PreviewOnlyKind.GCODE -> false
        PreviewOnlyKind.EXPORTED_FILE -> (previewOnly?.slicedPlates ?: 0) > 1
    },
    currentPlate = currentPlate,
    bedType = currentBedType(),
    canSelectPlate = !busy && !slicingAll,
    plateNames = plates.map(PartPlate::name),
    allPlates = allPlatesStats(),
    canSliceAll = canSliceAll,
    shells = validation?.printObjects.orEmpty().mapNotNull { printed -> objects.getOrNull(printed.objectIndex)?.let { it to printed.printZMin } },
    exportFinished = exportFinished,
    simplifySuggestions = simplifySuggestions.mapNotNull { mesh -> objects.firstOrNull { it.mesh == mesh } },
    profileUpdates = profileUpdates,
    profileUpdatesInstalled = profileUpdatesInstalled,
    previewOnly = previewOnly?.kind,
    gcodeLoading = previewOnly?.fileName?.takeIf { importing && result?.toolpaths == null },
    plateSlices = plateSlices(),
    validationError = validation?.error?.let { error ->
        val target = objects.getOrNull(error.objectIndex)
        PreviewValidationError(error.text, target?.let { PlateInstanceId(it.mesh, error.instanceIndex.coerceAtLeast(0)) }, target, error.option)
    },
    clashedObjects = objects.filter { plateObject -> plateObject.instances.any { it.printable && it.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE } },
    plateNotices = validation?.notices.orEmpty(),
    problem = problem?.takeIf { it.kind.shownInPreview },
    problemObjects = problem?.objects.orEmpty().mapNotNull { mesh -> objects.firstOrNull { it.mesh == mesh } },
    slicesCancelled = slicesCancelled,
    readyForExport = result?.printReady == true && copies().any { it.printable && it.inspection.fit == BuildVolumeFit.INSIDE },
    canSendGcode = objects.isEmpty() || !connection?.settings?.values?.get(PRINT_HOST).isNullOrEmpty(),
    // ...without the host's page (PrintHost::get_print_host_webui()), unless it prints over Bambu Lab's network.
    exportsSlicedFile = connection?.use3mf == true && !useBblNetwork(connection) &&
        listOf(PRINT_HOST_WEBUI, PRINT_HOST).all { connection.settings.values[it].isNullOrEmpty() },
    bambuVendor = presets?.plateBedTypeSelectable == true,
    printAllSupported = presets?.plateBedTypeSelectable == true && connection != null && !useBblNetwork(connection) &&
        connection.settings.values[HOST_TYPE] == PrintHostType.SIMPLYPRINT.key,
    allReadyForPrint = allSliceResultsReady(),
)

/** PresetBundle::use_bbl_network(): a Bambu Lab printer that is not sent to a host of its own (bbl_use_printhost). */
private fun PlateState.useBblNetwork(connection: PrinterConnection): Boolean =
    presets?.plateBedTypeSelectable == true && connection.settings.values[BBL_USE_PRINTHOST] != "1"

private const val PRINT_HOST = "print_host"
private const val PRINT_HOST_WEBUI = "print_host_webui"
private const val HOST_TYPE = "host_type"
private const val BBL_USE_PRINTHOST = "bbl_use_printhost"
