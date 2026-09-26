package app.orcinus.shadow.feature.preview

import androidx.lifecycle.ViewModel
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.plateOrigins
import app.orcinus.shadow.domain.plate.SelectPlateUseCase
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.ui.settings.SentFilament
import app.orcinus.shadow.domain.plate.DeletePhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.EditLayerGcodesUseCase
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.domain.plate.PrinterPresetNamesUseCase
import app.orcinus.shadow.domain.plate.ExportGcodeUseCase
import app.orcinus.shadow.domain.plate.ObservePhysicalPrintersUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.SavePhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.SendGcodeUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PreviewUiState(
    /** The printer's plate for the 3D view; null until the engine described it. */
    val plate: PlateDescription?,
    val result: PlateSliceResult?,
    val canSlice: Boolean,
    /** The codes the layer slider put on the layers. */
    val layerGcodes: List<LayerGcode> = emptyList(),
    /** The colour of every filament of the plate, "#RRGGBB". */
    val filamentColors: List<String> = emptyList(),
    /** How far the slice that is running has got, from 0 to 1; null when nothing is being sliced. */
    val slicingProgress: Float? = null,
    /** Where every plate stands (PartPlateList), and the one whose G-code the preview shows. */
    val plateOrigins: List<Point2> = listOf(Point2(0.0, 0.0)),
    val currentPlate: Int = 0,
    /** Another plate can be shown: nothing is being sliced or changed. */
    val canSelectPlate: Boolean = false,
    /** The plates' names, empty for one the user did not name. */
    val plateNames: List<String> = listOf(""),
) {
    /** The codes changed since the slice: its G-code no longer holds them (PartPlate's invalid slice result). */
    val outdated: Boolean get() = result != null && result.layerGcodes != layerGcodes
}

class PreviewViewModel(
    observePlate: ObservePlateUseCase,
    private val slicePlate: SlicePlateUseCase,
    private val physicalPrinters: ObservePhysicalPrintersUseCase,
    private val savePhysicalPrinter: SavePhysicalPrinterUseCase,
    private val deletePhysicalPrinter: DeletePhysicalPrinterUseCase,
    private val printerPresetNames: PrinterPresetNamesUseCase,
    private val sendGcode: SendGcodeUseCase,
    private val exportGcode: ExportGcodeUseCase,
    private val editLayerGcodes: EditLayerGcodesUseCase,
    private val selectPlate: SelectPlateUseCase,
) : ViewModel() {
    private val plate = observePlate()
    val state: StateFlow<PreviewUiState> = observePlate()
        .map(PlateState::toPreviewUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toPreviewUiState())

    fun slice() = slicePlate()

    /** The preview's plate bar (GLCanvas3D::_render_imgui_select_plate_toolbar): another plate's G-code. */
    fun selectPlate(index: Int) = selectPlate.invoke(index)

    /** The layer slider's menu (IMSlider::add_code_as_tick, add_custom_gcode, delete_tick). */
    fun addPause(printZ: Double) = editLayerGcodes.add(printZ, LayerGcodeType.PAUSE_PRINT)

    fun addTemplate(printZ: Double) = editLayerGcodes.add(printZ, LayerGcodeType.TEMPLATE)

    fun changeFilament(printZ: Double, filament: Int) = editLayerGcodes.add(printZ, LayerGcodeType.TOOL_CHANGE, filament)

    fun setCustomGcode(printZ: Double, gcode: String) = editLayerGcodes.addCustom(printZ, gcode)

    fun deleteLayerGcode(printZ: Double) = editLayerGcodes.delete(printZ)

    /** PhysicalPrinterDialog: the printers the sliced G-code can be sent to. */
    suspend fun printers(): PhysicalPrintersOutcome = physicalPrinters()

    suspend fun savePrinter(printer: PhysicalPrinter, renamedFrom: String?): PhysicalPrintersOutcome =
        savePhysicalPrinter(printer, renamedFrom)

    suspend fun deletePrinter(name: String): PhysicalPrintersOutcome = deletePhysicalPrinter(name)

    /** PrintHost::upload: the G-code of the last slice goes to the printer. */
    suspend fun send(
        printer: PhysicalPrinter,
        startPrint: Boolean,
        options: PrintOptions,
        onProgress: (Float) -> Unit,
    ): PrintHostUploadOutcome = sendGcode(printer, startPrint, options, onProgress)

    /** CrealityPrintHostSendDialog: the slots of the printer's material boxes. */
    suspend fun printerSlots(printer: PhysicalPrinter): PrinterSlotsOutcome = sendGcode.printerSlots(printer)

    /** PhysicalPrinterDialog's preset combo box: the printer presets a printer can be bound to. */
    suspend fun printerPresets(): List<String> = (printerPresetNames() as? PresetNamesOutcome.Success)?.names.orEmpty()

    /** PhysicalPrinterDialog's Test button: whether the printer's host answers. */
    suspend fun testPrinter(printer: PhysicalPrinter): PrintHostTestOutcome = sendGcode.testPrinter(printer)

    /** The filaments of the plate, as the send dialog matches them to the printer's slots. */
    fun sentFilaments(): List<SentFilament> {
        val presets = plate.value.presets ?: return emptyList()
        return presets.filamentColors.mapIndexed { index, color ->
            SentFilament(color = color, type = presets.filamentTypes.getOrElse(index) { "" })
        }
    }

    /** Export G-code: the name the file is offered under, and the save itself. */
    fun gcodeName(): String = exportGcode.suggestedName() ?: "plate.gcode"

    suspend fun exportGcode(document: ExternalDocumentReference): Boolean = exportGcode.invoke(document)

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toPreviewUiState() = PreviewUiState(
    plate = plate,
    result = result,
    canSlice = canSlice,
    layerGcodes = layerGcodes,
    filamentColors = presets?.filamentColors.orEmpty(),
    slicingProgress = slicing?.let { it.progress?.fraction ?: 0f },
    plateOrigins = plateOrigins(),
    currentPlate = currentPlate,
    canSelectPlate = !busy,
    plateNames = plates.map(PartPlate::name),
)
