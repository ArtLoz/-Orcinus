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
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.core.model.SentFilament
import app.orcinus.shadow.domain.plate.AllPlatesStats
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.EditLayerGcodesUseCase
import app.orcinus.shadow.domain.plate.ExportGcodeUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.ObservePrinterConnectionUseCase
import app.orcinus.shadow.domain.plate.SelectSlicedPlateUseCase
import app.orcinus.shadow.domain.plate.SendGcodeUseCase
import app.orcinus.shadow.domain.plate.SetSliceModeUseCase
import app.orcinus.shadow.domain.plate.ShareGcodeUseCase
import app.orcinus.shadow.domain.plate.ShowAllPlatesStatsUseCase
import app.orcinus.shadow.domain.plate.SliceActionUseCase
import app.orcinus.shadow.domain.plate.allPlatesStats
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.preferences.RecentSendChoicesUseCase
import app.orcinus.shadow.domain.preferences.SetPreferenceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
) {
    /** The codes changed since the slice: its G-code no longer holds them (PartPlate's invalid slice result). */
    val outdated: Boolean get() = result != null && result.layerGcodes != layerGcodes
}

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
) : ViewModel() {
    private val plate = observePlate()

    /** What the Preferences change on the canvas. */
    val canvas: StateFlow<CanvasPreferences> = preferences.canvas

    private val preferencesValues = preferences.values

    /** An item of the canvas's View menu, which OrcaSlicer.conf keeps. */
    fun setCanvasOption(key: String, value: String) = setPreference(key, value)
    val state: StateFlow<PreviewUiState> = observePlate()
        .map(PlateState::toPreviewUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toPreviewUiState())

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

    /** PrintHost::upload: the G-code of the last slice goes to the printer. */
    suspend fun send(
        printer: PhysicalPrinter,
        startPrint: Boolean,
        options: PrintOptions,
        onProgress: (Float) -> Unit,
    ): PrintHostUploadOutcome = sendGcode(printer, startPrint, options, onProgress)

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

    suspend fun exportGcode(document: ExternalDocumentReference): Boolean = exportGcode.invoke(document)

    /** The name the exported document goes by, which the export's notification shows. */
    suspend fun exportedName(document: ExternalDocumentReference): String = exportGcode.displayName(document) ?: gcodeName()

    /** The G-code as the share sheet takes it; null when there is none. */
    suspend fun shareGcode(): ExternalDocumentReference? = shareGcode.invoke()

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toPreviewUiState() = PreviewUiState(
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
    currentPlate = currentPlate,
    canSelectPlate = !busy && !slicingAll,
    plateNames = plates.map(PartPlate::name),
    allPlates = allPlatesStats(),
    canSliceAll = canSliceAll,
    shells = validation?.printObjects.orEmpty().mapNotNull { printed -> objects.getOrNull(printed.objectIndex)?.let { it to printed.printZMin } },
)
