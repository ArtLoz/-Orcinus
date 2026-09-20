package app.orcinus.shadow.feature.sidebar

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarSection
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarTitle
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.component.orcaSelectable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PendingPresetChange
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetTransfer
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsScope
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.preset.PresetListSheet
import app.orcinus.shadow.core.ui.settings.CreatePrinterDialog
import app.orcinus.shadow.core.ui.settings.PhysicalPrintersSheet
import app.orcinus.shadow.core.ui.settings.CustomPrinterActions
import app.orcinus.shadow.core.ui.settings.DiffPresetDialog
import app.orcinus.shadow.core.ui.settings.ExportConfigsDialog
import app.orcinus.shadow.core.ui.settings.PresetChangeActions
import app.orcinus.shadow.core.ui.settings.PresetChangeDialog
import app.orcinus.shadow.core.ui.settings.PresetComparisonActions
import app.orcinus.shadow.core.ui.settings.SettingColorDialog
import app.orcinus.shadow.core.ui.settings.SettingsActions
import app.orcinus.shadow.core.ui.settings.SettingsModeSwitch
import app.orcinus.shadow.core.ui.settings.SettingsPresetButtons
import app.orcinus.shadow.core.ui.settings.SettingsQuestionDialog
import app.orcinus.shadow.core.ui.settings.SettingsSearchSheet
import app.orcinus.shadow.core.ui.settings.SettingsTabDialogs
import app.orcinus.shadow.core.ui.settings.rememberSettingsTab
import app.orcinus.shadow.core.ui.settings.settingsTabItems
import app.orcinus.shadow.domain.plate.AddLayerRangeUseCase
import app.orcinus.shadow.domain.plate.AddObjectPartUseCase
import app.orcinus.shadow.domain.plate.AddPlateInstanceUseCase
import app.orcinus.shadow.domain.plate.CustomPrinterUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DescribeFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.EditLayerRangeUseCase
import app.orcinus.shadow.domain.plate.ExportConfigUseCase
import app.orcinus.shadow.domain.plate.ImportConfigUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.PlateFilamentsUseCase
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.RemoveLayerRangeUseCase
import app.orcinus.shadow.domain.plate.RemoveObjectPartUseCase
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.SelectLayerRangeUseCase
import app.orcinus.shadow.domain.plate.SelectObjectPartUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SelectPresetUseCase
import app.orcinus.shadow.domain.plate.SetBedShapeUseCase
import app.orcinus.shadow.domain.plate.ObservePhysicalPrintersUseCase
import app.orcinus.shadow.domain.plate.SavePhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.DeletePhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.TestPhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.PrinterPresetNamesUseCase
import app.orcinus.shadow.domain.plate.SetExtruderUseCase
import app.orcinus.shadow.domain.plate.SetFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectPrintableUseCase
import app.orcinus.shadow.domain.plate.SetSettingsScopeUseCase
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SidebarUiState(
    val engine: EngineState,
    /** Null until the engine reported the presets. */
    val presets: Presets? = null,
    /** The first filament's colour, as the plate shows it. */
    val filamentColor: ColorRgba? = null,
    /** Presets can be chosen: the plate is not busy and no placement is settling. */
    val canChoose: Boolean = false,
    /** A preset choice is being applied. */
    val changing: Boolean = false,
    /** OrcaSlicer's process tab for the selected presets. */
    val processSettings: SettingsTabState = SettingsTabState(PresetKind.PRINT),
    /** A preset choice that waits for what happens to the unsaved changes. */
    val presetChange: PendingPresetChange? = null,
    /** ParamsPanel's switch: the presets, or the settings of the plate and its objects. */
    val settingsScope: SettingsScope = SettingsScope.GLOBAL,
    /** The objects on the plate, as the object list offers them. */
    val objects: List<PlateObject> = emptyList(),
    /** The copies whose settings are shown; none shows the plate's. */
    val selectedInstances: Set<PlateInstanceId> = emptySet(),
    /** The part of an object whose settings are shown, when one is selected. */
    val selectedPart: ObjectPartId? = null,
    /** The height range whose settings are shown, when one is selected. */
    val selectedRange: LayerRangeId? = null,
    /** OrcaSlicer's tabs for the plate, for the selected object and for its part. */
    val plateSettings: SettingsTabState = SettingsTabState(PresetKind.PLATE),
    val objectSettings: SettingsTabState = SettingsTabState(PresetKind.OBJECT),
    val partSettings: SettingsTabState = SettingsTabState(PresetKind.PART),
    val rangeSettings: SettingsTabState = SettingsTabState(PresetKind.LAYER),
    /** What the plate overrides the process preset with, which its list row marks. */
    val plateOverrides: ModelSettings = ModelSettings(),
) {
    /** Which settings the Objects side shows: a selected part's, the objects', or the plate's. */
    val modelKind: PresetKind get() = when {
        selectedRange != null -> PresetKind.LAYER
        selectedPart != null -> PresetKind.PART
        selectedInstances.isEmpty() -> PresetKind.PLATE
        else -> PresetKind.OBJECT
    }

    val modelSettings: SettingsTabState get() = when (modelKind) {
        PresetKind.LAYER -> rangeSettings
        PresetKind.PART -> partSettings
        PresetKind.OBJECT -> objectSettings
        else -> plateSettings
    }
}

class SidebarViewModel(
    observePlate: ObservePlateUseCase,
    private val selectPreset: SelectPresetUseCase,
    private val plateFilaments: PlateFilamentsUseCase,
    private val settingsTabs: PresetSettingsTabs,
    private val customPrinter: CustomPrinterUseCase,
    private val setBedShape: SetBedShapeUseCase,
    private val selectPlateObject: SelectPlateObjectUseCase,
    private val selectObjectPart: SelectObjectPartUseCase,
    private val addLayerRange: AddLayerRangeUseCase,
    private val removeLayerRange: RemoveLayerRangeUseCase,
    private val selectLayerRange: SelectLayerRangeUseCase,
    private val editLayerRange: EditLayerRangeUseCase,
    private val setExtruder: SetExtruderUseCase,
    private val describeFlush: DescribeFlushVolumesUseCase,
    private val setFlush: SetFlushVolumesUseCase,
    private val importConfig: ImportConfigUseCase,
    private val exportConfig: ExportConfigUseCase,
    private val setSettingsScope: SetSettingsScopeUseCase,
    private val setPlateObjectPrintable: SetPlateObjectPrintableUseCase,
    private val setPlateObjectAutoDrop: SetPlateObjectAutoDropUseCase,
    private val addPlateInstance: AddPlateInstanceUseCase,
    private val addObjectPart: AddObjectPartUseCase,
    private val removeObjectPart: RemoveObjectPartUseCase,
    private val removePlateInstance: RemovePlateInstanceUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val physicalPrinters: ObservePhysicalPrintersUseCase,
    private val savePhysicalPrinter: SavePhysicalPrinterUseCase,
    private val deletePhysicalPrinter: DeletePhysicalPrinterUseCase,
    private val testPhysicalPrinter: TestPhysicalPrinterUseCase,
    private val printerPresetNames: PrinterPresetNamesUseCase,
) : ViewModel() {
    val state: StateFlow<SidebarUiState> = observePlate()
        .map(PlateState::toSidebarUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toSidebarUiState())

    fun choose(choice: PresetChoice) = selectPreset(choice)

    fun chooseSettingsScope(scope: SettingsScope) = setSettingsScope(scope)

    /** The filaments of the plate, as OrcaSlicer's sidebar keeps them. */
    fun addFilament() = plateFilaments.add()

    fun removeFilament(index: Int) = plateFilaments.remove(index)

    fun selectFilament(index: Int, name: ProfileId) = plateFilaments.select(index, name)

    fun setFilamentColor(index: Int, color: String) = plateFilaments.setColor(index, color)

    /** WipingDialog: the flushing volumes of the plate and what the sheet writes back. */
    suspend fun describeFlushVolumes(): FlushVolumesOutcome = describeFlush()

    fun setFlushVolumes(matrix: List<Double>, multipliers: List<Double>) = setFlush(matrix, multipliers)

    /**
     * The plate or one of its objects, as the object list picks what the
     * settings are of; with [add] the object joins the selection or leaves it.
     */
    fun chooseSettingsTarget(id: PlateInstanceId?, add: Boolean = false) = selectPlateObject(id, add)

    /** The row of a part, whose own settings the parameter panel then edits. */
    fun chooseSettingsPart(id: ObjectPartId?) = selectObjectPart(id)

    fun openSettingsOfPart(id: ObjectPartId) {
        selectObjectPart(id)
        setSettingsScope(SettingsScope.OBJECT)
    }

    /** The height ranges of an object, which the object list edits. */
    fun addRange(mesh: ScenePath, after: LayerRangeId?) {
        val added = addLayerRange(mesh, after) ?: return
        selectLayerRange(added)
        setSettingsScope(SettingsScope.OBJECT)
    }

    fun removeRange(id: LayerRangeId) = removeLayerRange(id)

    fun chooseSettingsRange(id: LayerRangeId?) = selectLayerRange(id)

    fun openSettingsOfRange(id: LayerRangeId) {
        selectLayerRange(id)
        setSettingsScope(SettingsScope.OBJECT)
    }

    fun editRange(id: LayerRangeId, bottom: Double, top: Double) = editLayerRange(id, bottom, top)

    /** The filament column of the object list (set_extruder_for_selected_items). */
    fun setObjectExtruder(mesh: ScenePath, extruder: Int) = setExtruder(mesh, extruder)

    fun setPartExtruder(id: ObjectPartId, extruder: Int) = setExtruder(id, extruder)

    fun setRangeExtruder(id: LayerRangeId, extruder: Int) = setExtruder(id, extruder)

    /** Import Configs and Export Preset Bundle of the desktop app's File menu. */
    var configTransfer: ConfigTransferOutcome? by mutableStateOf(null)
        private set

    /** Whether the result that waits is of an export or of an import. */
    var configExported: Boolean by mutableStateOf(false)
        private set

    /**
     * ConfigsOverwriteConfirmDialog: the preset the import waits for an answer
     * about, and what the user has answered so far.
     */
    var configOverwrite: String? by mutableStateOf(null)
        private set

    private var importDocuments: List<ExternalDocumentReference> = emptyList()
    private val importAnswers = mutableMapOf<String, ConfigOverwriteAnswer>()

    fun importConfig(references: List<ExternalDocumentReference>) {
        importDocuments = references
        importAnswers.clear()
        runImport()
    }

    /** Its buttons: the import runs again with the answer. */
    fun answerOverwrite(answer: ConfigOverwriteAnswer) {
        val preset = configOverwrite ?: return
        configOverwrite = null
        importAnswers[preset] = answer
        runImport()
    }

    fun cancelOverwrite() {
        configOverwrite = null
    }

    private fun runImport() {
        viewModelScope.launch {
            configExported = false
            when (val outcome = importConfig.invoke(importDocuments, importAnswers.toMap())) {
                is ConfigTransferOutcome.Overwrite -> configOverwrite = outcome.preset
                else -> configTransfer = outcome
            }
        }
    }

    /** ExportConfigsDialog: what it offers for an export kind. */
    suspend fun configExportOptions(kind: ConfigExportKind): ConfigExportOptionsOutcome = exportConfig.options(kind)

    fun exportConfigs(kind: ConfigExportKind, names: List<String>, folder: ExternalDocumentReference) {
        viewModelScope.launch {
            configExported = true
            configTransfer = exportConfig.invoke(kind, names, folder)
        }
    }

    fun dismissConfigTransfer() {
        configTransfer = null
    }

    /** The settings row of the object list: the item and its settings are shown. */
    fun openSettingsOf(id: PlateInstanceId?) {
        selectPlateObject(id)
        setSettingsScope(SettingsScope.OBJECT)
    }

    fun setObjectPrintable(id: PlateInstanceId, printable: Boolean) = setPlateObjectPrintable(id, printable)

    fun setObjectAutoDrop(id: PlateInstanceId, autoDrop: Boolean) = setPlateObjectAutoDrop(id, autoDrop)

    fun copyObject(id: PlateInstanceId) = addPlateInstance(id)

    fun removeObjectCopy(id: PlateInstanceId) = removePlateInstance(id)

    fun addPart(mesh: ScenePath, shape: String, type: VolumeType) = addObjectPart(mesh, shape, type)

    fun removePart(mesh: ScenePath, index: Int) = removeObjectPart(mesh, index)

    fun deleteObject(mesh: ScenePath) = deletePlateObject(mesh)

    fun resolvePresetChange(action: PresetChangeAction) = selectPreset.resolve(action)

    fun savePresetChange(name: String) = selectPreset.save(name)

    fun cancelPresetChange() = selectPreset.cancelPresetChange()

    fun requestSettings(kind: PresetKind, request: SettingsRequest) = settingsTabs.request(kind, request)

    fun answerSettingsQuestion(kind: PresetKind, yes: Boolean) = settingsTabs.answer(kind, yes)

    fun dismissSettingsNotice(kind: PresetKind) = settingsTabs.dismissNotice(kind)

    suspend fun settingTooltip(kind: PresetKind, id: String): List<OrcaText> = settingsTabs.tooltip(kind, id)

    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = settingsTabs.checkPresetName(kind, name)

    suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome = settingsTabs.compatiblePresetChoices(kind, key)

    /** EditGCodeDialog: the G-code of a setting with the placeholders, and what a placeholder is. */
    suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome = settingsTabs.gcodePlaceholders(kind, key)

    suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo = settingsTabs.gcodePlaceholder(key, presets)

    /** CreatePrinterPresetDialog: what its pages offer, and its Create button. */
    suspend fun printerOptions(vendor: String, nozzle: String, presetVendor: String, printerPreset: String): CreatePrinterOptionsOutcome =
        customPrinter.options(vendor, nozzle, presetVendor, printerPreset)

    var printerQuestion: SettingsDialog? by mutableStateOf(null)
        private set

    /**
     * Whether the dialog is open. Upstream runs it modally and ends it only
     * once the printer is created, so a question answered with Cancel comes
     * back to the pages the user has filled in.
     */
    var creatingPrinter: Boolean by mutableStateOf(false)
        private set

    private var pendingPrinter: CreatePrinterRequest? = null
    private var printerAnswers: Map<String, Boolean> = emptyMap()

    fun openCreatePrinter() {
        creatingPrinter = true
    }

    fun closeCreatePrinter() {
        creatingPrinter = false
        printerQuestion = null
        pendingPrinter = null
        printerAnswers = emptyMap()
    }

    fun createPrinter(request: CreatePrinterRequest) {
        pendingPrinter = request
        printerAnswers = emptyMap()
        runPrinterCreation()
    }

    /** The question's Yes or Cancel: the creation runs again with the answer. */
    fun answerPrinterQuestion(yes: Boolean) {
        val question = printerQuestion ?: return
        printerQuestion = null
        printerAnswers = printerAnswers + (question.id to yes)
        if (yes) runPrinterCreation() else pendingPrinter = null
    }

    private fun runPrinterCreation() {
        val request = pendingPrinter ?: return
        viewModelScope.launch {
            when (val outcome = customPrinter.create(request, printerAnswers)) {
                is PresetCreationOutcome.Question -> printerQuestion = outcome.question
                is PresetCreationOutcome.Failure -> {
                    pendingPrinter = null
                    configTransfer = ConfigTransferOutcome.Failure(outcome.message)
                }
                is PresetCreationOutcome.Success -> {
                    pendingPrinter = null
                    creatingPrinter = false
                }
            }
        }
    }

    /** Search::OptionsSearcher: every setting the preset tabs show. */
    suspend fun searchCatalog(): SearchCatalogOutcome = settingsTabs.searchCatalog()

    /**
     * The Connection button of the sidebar's printer title
     * (Plater's m_printer_connect): the printers of the network the app sends
     * G-code to.
     */
    suspend fun networkPrinters(): PhysicalPrintersOutcome = physicalPrinters()

    suspend fun saveNetworkPrinter(printer: PhysicalPrinter, renamedFrom: String?): PhysicalPrintersOutcome =
        savePhysicalPrinter(printer, renamedFrom)

    suspend fun deleteNetworkPrinter(name: String): PhysicalPrintersOutcome = deletePhysicalPrinter(name)

    suspend fun testNetworkPrinter(printer: PhysicalPrinter): PrintHostTestOutcome = testPhysicalPrinter(printer)

    suspend fun networkPrinterPresets(): List<String> =
        (printerPresetNames() as? PresetNamesOutcome.Success)?.names.orEmpty()

    /** DiffPresetDialog: the presets of either side, and what the selected ones differ in. */
    suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean): PresetComparisonOutcome =
        settingsTabs.comparePresets(left, right, showAll)

    /** Its Transfer button: the chosen values move into the right presets. */
    fun transferPresetOptions(transfers: List<PresetTransfer>) = selectPreset.transfer(transfers)

    /** BedShapeDialog: the printable area of the edited printer, and the shape it is set to. */
    suspend fun bedShape(): BedShapeOutcome = settingsTabs.bedShape()

    suspend fun setBedShape(shape: BedShape, customPath: ModelPath?) = setBedShape.invoke(shape, customPath)

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toSidebarUiState() = SidebarUiState(
    engine = engine,
    presets = presets,
    filamentColor = plate?.filamentColor,
    canChoose = profiles != null && !busy && objects.none(PlateObject::placing),
    changing = changingPresets,
    processSettings = settingsTabs[PresetKind.PRINT] ?: SettingsTabState(PresetKind.PRINT),
    presetChange = presetChange,
    settingsScope = settingsScope,
    objects = objects,
    selectedInstances = selectedInstances,
    selectedPart = selectedPart,
    selectedRange = selectedRange,
    plateSettings = settingsTabs[PresetKind.PLATE] ?: SettingsTabState(PresetKind.PLATE),
    objectSettings = settingsTabs[PresetKind.OBJECT] ?: SettingsTabState(PresetKind.OBJECT),
    partSettings = settingsTabs[PresetKind.PART] ?: SettingsTabState(PresetKind.PART),
    rangeSettings = settingsTabs[PresetKind.LAYER] ?: SettingsTabState(PresetKind.LAYER),
    plateOverrides = plateSettings,
)

/**
 * The shapes a part can have (create_mesh of GUI_ObjectList.cpp), which the
 * desktop app offers in the submenu of its "Add part" items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartShapeSheet(type: VolumeType, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val colors = OrcaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(addPartName(type)),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PART_SHAPES.forEach { shape ->
                Text(
                    text = stringResource(shapeName(shape)),
                    color = colors.text,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .orcaClickable(role = Role.Button, onClick = { onChoose(shape) })
                        .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * The heights a range spans (ObjectList::edit_layer_range), which the desktop
 * app edits in two fields of its object list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerRangeSheet(range: LayerRange, onDismiss: () -> Unit, onApply: (Double, Double) -> Unit) {
    val colors = OrcaTheme.colors
    var bottom by rememberSaveable(range) { mutableStateOf(formatHeight(range.bottom)) }
    var top by rememberSaveable(range) { mutableStateOf(formatHeight(range.top)) }
    val apply = {
        val from = bottom.replace(',', '.').toDoubleOrNull()
        val to = top.replace(',', '.').toDoubleOrNull()
        if (from != null && to != null) onApply(from, to) else onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.object_menu_edit_range),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            listOf(
                R.string.object_range_bottom to true,
                R.string.object_range_top to false,
            ).forEach { entry ->
                val label = entry.first
                val isBottom = entry.second
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = stringResource(label),
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    OrcaTextField(
                        value = if (isBottom) bottom else top,
                        onValueChange = { if (isBottom) bottom = it else top = it },
                        unit = stringResource(R.string.object_range_unit),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { apply() }),
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
            OrcaButton(
                text = stringResource(R.string.object_range_apply),
                onClick = apply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** The heights of a range as the desktop app writes them: two decimals. */
private fun formatHeight(value: Double): String = String.format(Locale.US, "%.2f", value)

/** The engine could not describe the flushing volumes; its message is shown as it is. */
@Composable
private fun FlushVolumesFailureDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString("Flushing volumes"), style = OrcaTheme.typography.head16) },
        text = { Text(message, color = OrcaTheme.colors.error, style = OrcaTheme.typography.body14) },
        containerColor = OrcaTheme.colors.window,
    )
}

/**
 * ConfigsOverwriteConfirmDialog: the import met a preset that is already there.
 * Its buttons say what happens to that one, or to every one that follows.
 */
@Composable
private fun ConfigsOverwriteConfirmDialog(preset: String, onAnswer: (ConfigOverwriteAnswer) -> Unit, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(orcaString("Overwrite config"), style = OrcaTheme.typography.head16) },
        text = {
            Text(
                text = orcaText(OrcaText("A config exists with the same name: %s, do you want to overwrite it?", listOf(preset))),
                color = colors.text,
                style = OrcaTheme.typography.body14,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrcaButton(orcaString("Yes"), onClick = { onAnswer(ConfigOverwriteAnswer.YES) })
                OrcaButton(orcaString("Yes to All"), onClick = { onAnswer(ConfigOverwriteAnswer.YES_TO_ALL) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("No"), onClick = { onAnswer(ConfigOverwriteAnswer.NO) }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("No to All"), onClick = { onAnswer(ConfigOverwriteAnswer.NO_TO_ALL) }, style = OrcaButtonStyle.Regular)
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** What the import or the export did, as the desktop app reports it. */
@Composable
private fun ConfigTransferDialog(outcome: ConfigTransferOutcome, exported: Boolean, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString(if (exported) "Export result" else "Import result"), style = OrcaTheme.typography.head16) },
        text = {
            Column {
                when (outcome) {
                    is ConfigTransferOutcome.Failure -> Text(outcome.message, color = colors.error, style = OrcaTheme.typography.body14)
                    // An import that still waits for an answer shows no result.
                    is ConfigTransferOutcome.Overwrite -> Unit
                    is ConfigTransferOutcome.Success -> Text(
                        text = stringResource(
                            if (exported) R.string.config_exported else R.string.config_imported,
                            outcome.names.size,
                        ),
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
                    )
                }
                if (!exported) {
                    Text(
                        text = stringResource(R.string.config_import_hint),
                        color = colors.textSide,
                        style = OrcaTheme.typography.body12,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** A line of the sidebar's footer: an icon and what it does. */
@Composable
private fun SidebarAction(icon: Int, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .orcaClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = OrcaTheme.colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.icon))
        Text(text, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The filaments of the plate, as the sidebar changes them. */
internal class FilamentActions(
    val add: () -> Unit,
    val remove: (Int) -> Unit,
    val select: (Int, ProfileId) -> Unit,
    val setColor: (Int, String) -> Unit,
    /** WipingDialog: the flushing volumes of the plate, read when its sheet opens. */
    val describeFlushVolumes: suspend () -> FlushVolumesOutcome = { FlushVolumesOutcome.Failure("") },
    val setFlushVolumes: (matrix: List<Double>, multipliers: List<Double>) -> Unit = { _, _ -> },
) {
    companion object {
        val NONE = FilamentActions(add = {}, remove = {}, select = { _, _ -> }, setColor = { _, _ -> })
    }
}

/** The colour of a filament slot, as project_config keeps it. */
@Composable
internal fun slotColor(presets: Presets?, state: SidebarUiState, index: Int): Color {
    val color = presets?.filamentColors?.getOrNull(index)?.let(::parseFilamentColor)
    // The plate is drawn with the colour of the first filament, which the
    // engine reports with its description.
    val plate = state.filamentColor?.let { Color(it.red, it.green, it.blue, it.alpha) }
    return color ?: plate?.takeIf { index == 0 } ?: OrcaTheme.colors.accent
}

/** "#RRGGBB" as OrcaSlicer writes a colour. */
private fun parseFilamentColor(value: String): Color? {
    val hex = value.removePrefix("#")
    if (hex.length < 6) return null
    val rgb = hex.take(6).toLongOrNull(radix = 16) ?: return null
    return Color(0xFF000000L or rgb)
}

/** Which Setup Wizard page a preset list opens: its printers or its filaments. */
enum class PresetWizardPage { PRINTERS, FILAMENTS }

/**
 * OrcaSlicer's sidebar: printer, nozzle, material, and process of the plate,
 * each chosen from the lists of OrcaSlicer's preset combo boxes. It is shared
 * by Prepare and Preview, so the app shell places it; [createViewModel] builds
 * its view model from the app's dependencies. [onOpenWizard] opens the Setup
 * Wizard, and [onOpenAbout] the About page, which OrcaSlicer keeps in its Help menu.
 */
@Composable
fun PlateSidebar(
    createViewModel: () -> SidebarViewModel,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenSettings: (PresetKind) -> Unit,
    onOpenSetting: (SearchOption) -> Unit = {},
    onOpenAbout: () -> Unit,
) {
    val viewModel = viewModel { createViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Import Configs takes a document, Export Preset Bundle a folder.
    // The file dialog of the desktop app takes several files at once.
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importConfig(uris.map { ExternalDocumentReference(it.toString()) })
    }
    // The dialog picks what to export; the folder is asked for after its OK.
    var exporting by rememberSaveable { mutableStateOf(false) }
    var exportChoice by remember { mutableStateOf<Pair<ConfigExportKind, List<String>>?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val chosen = exportChoice
        if (uri != null && chosen != null) {
            viewModel.exportConfigs(chosen.first, chosen.second, ExternalDocumentReference(uri.toString()))
        }
        exportChoice = null
    }
    if (exporting) {
        ExportConfigsDialog(
            loadOptions = viewModel::configExportOptions,
            onExport = { kind, names ->
                exporting = false
                exportChoice = kind to names
                folderPicker.launch(null)
            },
            onDismiss = { exporting = false },
        )
    }
    viewModel.configOverwrite?.let { preset ->
        ConfigsOverwriteConfirmDialog(preset, onAnswer = viewModel::answerOverwrite, onDismiss = viewModel::cancelOverwrite)
    }
    viewModel.configTransfer?.let { outcome ->
        ConfigTransferDialog(outcome, exported = viewModel.configExported, onDismiss = viewModel::dismissConfigTransfer)
    }
    PlateSidebarContent(
        state,
        onChoose = viewModel::choose,
        onChooseScope = viewModel::chooseSettingsScope,
        objectList = ObjectListActions(
            select = viewModel::chooseSettingsTarget,
            selectAlone = { viewModel.chooseSettingsTarget(it, add = false) },
            selectSettings = viewModel::openSettingsOf,
            setPrintable = viewModel::setObjectPrintable,
            setAutoDrop = viewModel::setObjectAutoDrop,
            copy = viewModel::copyObject,
            removeCopy = viewModel::removeObjectCopy,
            addPart = viewModel::addPart,
            removePart = viewModel::removePart,
            selectPart = viewModel::chooseSettingsPart,
            selectPartSettings = viewModel::openSettingsOfPart,
            addRange = viewModel::addRange,
            removeRange = viewModel::removeRange,
            selectRange = viewModel::chooseSettingsRange,
            selectRangeSettings = viewModel::openSettingsOfRange,
            editRange = viewModel::editRange,
            setObjectExtruder = viewModel::setObjectExtruder,
            setPartExtruder = viewModel::setPartExtruder,
            setRangeExtruder = viewModel::setRangeExtruder,
            delete = viewModel::deleteObject,
        ),
        onOpenWizard = onOpenWizard,
        onOpenSettings = onOpenSettings,
        onOpenSetting = onOpenSetting,
        searchCatalog = viewModel::searchCatalog,
        onOpenAbout = onOpenAbout,
        onImportConfig = { configPicker.launch(arrayOf("*/*")) },
        onExportConfig = { exporting = true },
        filaments = FilamentActions(
            add = viewModel::addFilament,
            remove = viewModel::removeFilament,
            select = viewModel::selectFilament,
            setColor = viewModel::setFilamentColor,
            describeFlushVolumes = viewModel::describeFlushVolumes,
            setFlushVolumes = viewModel::setFlushVolumes,
        ),
        settings = SettingsActions(
            request = viewModel::requestSettings,
            answer = viewModel::answerSettingsQuestion,
            dismissNotice = viewModel::dismissSettingsNotice,
            tooltip = viewModel::settingTooltip,
            checkPresetName = viewModel::checkPresetName,
            compatibleChoices = viewModel::compatiblePresetChoices,
            bedShape = viewModel::bedShape,
            setBedShape = viewModel::setBedShape,
            gcodePlaceholders = viewModel::gcodePlaceholders,
            gcodePlaceholder = viewModel::gcodePlaceholder,
        ),
        presetChange = PresetChangeActions(
            resolve = viewModel::resolvePresetChange,
            save = viewModel::savePresetChange,
            cancel = viewModel::cancelPresetChange,
        ),
        comparison = PresetComparisonActions(
            compare = viewModel::comparePresets,
            transfer = viewModel::transferPresetOptions,
        ),
        printers = CustomPrinterActions(
            options = viewModel::printerOptions,
            create = viewModel::createPrinter,
            answer = viewModel::answerPrinterQuestion,
            question = viewModel.printerQuestion,
            creating = viewModel.creatingPrinter,
            open = viewModel::openCreatePrinter,
            close = viewModel::closeCreatePrinter,
        ),
        network = NetworkPrinterActions(
            load = viewModel::networkPrinters,
            save = viewModel::saveNetworkPrinter,
            delete = viewModel::deleteNetworkPrinter,
            test = viewModel::testNetworkPrinter,
            presets = viewModel::networkPrinterPresets,
        ),
    )
}

/**
 * PhysicalPrinterDialog as the sidebar opens it: the printers of the network
 * the app sends G-code to.
 */
internal class NetworkPrinterActions(
    val load: suspend () -> PhysicalPrintersOutcome,
    val save: suspend (PhysicalPrinter, String?) -> PhysicalPrintersOutcome,
    val delete: suspend (String) -> PhysicalPrintersOutcome,
    val test: suspend (PhysicalPrinter) -> PrintHostTestOutcome,
    val presets: suspend () -> List<String>,
) {
    companion object {
        val NONE = NetworkPrinterActions(
            load = { PhysicalPrintersOutcome.Failure("") },
            save = { _, _ -> PhysicalPrintersOutcome.Failure("") },
            delete = { PhysicalPrintersOutcome.Failure("") },
            test = { PrintHostTestOutcome.Failure("") },
            presets = { emptyList() },
        )
    }
}

private enum class PresetList { PRINTERS, FILAMENTS, PROCESSES }

@Composable
internal fun PlateSidebarContent(
    state: SidebarUiState,
    onChoose: (PresetChoice) -> Unit,
    onChooseScope: (SettingsScope) -> Unit,
    objectList: ObjectListActions,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenSettings: (PresetKind) -> Unit,
    onOpenSetting: (SearchOption) -> Unit = {},
    searchCatalog: suspend () -> SearchCatalogOutcome = { SearchCatalogOutcome.Failure("") },
    onOpenAbout: () -> Unit,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    settings: SettingsActions,
    presetChange: PresetChangeActions,
    filaments: FilamentActions = FilamentActions.NONE,
    comparison: PresetComparisonActions = PresetComparisonActions.NONE,
    printers: CustomPrinterActions = CustomPrinterActions.NONE,
    network: NetworkPrinterActions = NetworkPrinterActions.NONE,
) {
    // DiffPresetDialog, which the compare button of the process panel opens.
    var comparing by rememberSaveable { mutableStateOf(false) }
    // Search::SearchDialog, which the search button of the panel opens.
    var searching by rememberSaveable { mutableStateOf(false) }
    // PhysicalPrinterDialog, which the Connection button of the printer opens.
    var openNetworkPrinters by rememberSaveable { mutableStateOf(false) }
    var openList by rememberSaveable { mutableStateOf<PresetList?>(null) }
    // Whether a tap on a row adds the object to the selection or picks it alone.
    var picking by rememberSaveable { mutableStateOf(false) }
    // The object a part is being added to, and what kind of part it is.
    var addingPart by remember { mutableStateOf<Pair<ScenePath, VolumeType>?>(null) }
    // Which filament slot the preset list is opened for.
    var editingSlot by rememberSaveable { mutableStateOf(0) }
    // The filament whose colour is being picked (the combo box's clr_picker).
    var pickingColor by rememberSaveable { mutableStateOf<Int?>(null) }
    // The height range whose heights are being edited.
    var editingRange by remember { mutableStateOf<LayerRangeId?>(null) }
    // The flushing volumes are asked for when their sheet opens.
    var openFlushVolumes by remember { mutableStateOf(false) }
    var flushVolumes by remember { mutableStateOf<FlushVolumesOutcome?>(null) }
    val presets = state.presets
    val enabled = state.canChoose && presets != null
    val process = state.processSettings
    val global = state.settingsScope == SettingsScope.GLOBAL
    val processTab = rememberSettingsTab(process, settings, enabled)
    val modelTab = rememberSettingsTab(state.modelSettings, settings, enabled)
    val tab = if (global) processTab else modelTab
    // The settings of the plate and of an object are described for what the
    // plate has selected, and again when the user picks other objects.
    LaunchedEffect(global, state.modelKind, state.selectedInstances, state.selectedPart, state.selectedRange, enabled) {
        if (!global && enabled) settings.request(state.modelKind, SettingsRequest.Describe)
    }
    // The filament column of the object list is painted with the colours of the
    // plate's filaments, and is only there when the printer has several
    // (ObjectList::set_filament_column_hidden).
    val filamentColors = presets?.selection?.allFilaments.orEmpty().indices.map { slotColor(presets, state, it) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window),
    ) {
        item(key = "printer") {
        OrcaSidebarTitle(stringResource(R.string.section_printer), DesignR.drawable.orca_printer) {
            // Plater's m_printer_connect: the printers of the network the app
            // sends the sliced G-code to.
            OrcaIconButton(
                icon = DesignR.drawable.orca_monitor_signal_strong,
                contentDescription = orcaString("Connection"),
                onClick = { openNetworkPrinters = true },
                enabled = enabled,
            )
        }
        OrcaSidebarSection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrcaComboField(
                    text = presets?.printers?.selectedLabel(presets.selection.printer.value).orEmpty(),
                    enabled = enabled,
                    onClick = { openList = PresetList.PRINTERS },
                    modifier = Modifier.weight(1f),
                )
                // The edit button of the sidebar, which opens the tab of the preset.
                OrcaIconButton(
                    icon = DesignR.drawable.orca_edit,
                    contentDescription = orcaString("Click to edit preset"),
                    onClick = { onOpenSettings(PresetKind.PRINTER) },
                    enabled = enabled,
                )
            }
            if (presets != null && presets.nozzleDiameters.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.nozzle_diameter),
                        color = OrcaTheme.colors.textLabel,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    OrcaComboBox(
                        items = presets.nozzleDiameters,
                        selected = presets.nozzleDiameter,
                        label = { it },
                        onSelect = { onChoose(PresetChoice.NozzleDiameter(it)) },
                        enabled = enabled && presets.nozzleDiameters.size > 1,
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }
        }

        item(key = "filament") {
        // Sidebar: one row per filament of the plate, with the buttons that add
        // one and take one away (Sidebar::add_custom_filament / delete_filament).
        OrcaSidebarTitle(stringResource(R.string.section_filament), DesignR.drawable.orca_filament) {
            // Sidebar: the flushing volumes are edited from the filament
            // section, and only matter with several filaments.
            if ((presets?.selection?.allFilaments?.size ?: 0) > 1) {
                OrcaIconButton(
                    icon = DesignR.drawable.orca_param_flush,
                    contentDescription = orcaString("Flushing volumes"),
                    onClick = { openFlushVolumes = true },
                    enabled = enabled,
                )
            }
            OrcaIconButton(
                icon = DesignR.drawable.orca_add,
                contentDescription = stringResource(R.string.filament_add),
                onClick = filaments.add,
                enabled = enabled,
            )
        }
        OrcaSidebarSection {
            val slots = presets?.selection?.allFilaments.orEmpty()
            slots.forEachIndexed { index, slot ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp),
                ) {
                    OrcaComboField(
                        text = presets?.filaments?.selectedLabel(slot.value).orEmpty(),
                        enabled = enabled,
                        onClick = { openList = PresetList.FILAMENTS; editingSlot = index },
                        modifier = Modifier.weight(1f),
                    ) {
                        // PresetComboBox's clr_picker: the swatch picks the filament's colour.
                        OrcaFilamentSlot(
                            number = index + 1,
                            color = slotColor(presets, state, index),
                            modifier = Modifier.orcaClickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClickLabel = orcaString("Click to select filament color"),
                            ) { pickingColor = index },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (index == 0) {
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_edit,
                            contentDescription = orcaString("Click to edit preset"),
                            onClick = { onOpenSettings(PresetKind.FILAMENT) },
                            enabled = enabled,
                        )
                    } else {
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_delete,
                            contentDescription = stringResource(R.string.filament_remove),
                            onClick = { filaments.remove(index) },
                            enabled = enabled,
                        )
                    }
                }
            }
        }
        }

        item(key = "process") {
        OrcaSidebarTitle(stringResource(R.string.section_process), DesignR.drawable.orca_process) {
            val shown = if (global) process else state.modelSettings
            shown.settings?.let { SettingsModeSwitch(shown.kind, it, enabled, settings) }
            // The search button of the tab (Tab::m_btn_search), which finds a
            // setting and opens its page.
            OrcaIconButton(
                icon = DesignR.drawable.orca_search,
                contentDescription = orcaString("Search in preset"),
                onClick = { searching = true },
                enabled = enabled,
            )
            // ParamsPanel's compare button (m_compare_btn).
            OrcaIconButton(
                icon = DesignR.drawable.orca_compare,
                contentDescription = orcaString("Compare presets"),
                onClick = { comparing = true },
                enabled = enabled,
            )
        }
        OrcaSidebarSection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrcaComboField(
                    text = presets?.processes?.selectedLabel(presets.selection.process.value).orEmpty(),
                    enabled = enabled,
                    onClick = { openList = PresetList.PROCESSES },
                    modifier = Modifier.weight(1f),
                )
                process.settings?.let { SettingsPresetButtons(PresetKind.PRINT, it, enabled, settings) }
            }
            // ParamsPanel's switch: the settings of the presets, or the ones
            // the plate and its objects override them with.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrcaSegmentedSwitch(
                    options = listOf(orcaString("Global"), orcaString("Objects")),
                    selectedIndex = if (global) 0 else 1,
                    onSelect = { onChooseScope(if (it == 0) SettingsScope.GLOBAL else SettingsScope.OBJECT) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!global) {
                    val model = state.modelSettings.settings
                    if (model != null && model.dirty) {
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_undo,
                            contentDescription = orcaString("Click the icon to reset all settings of the object"),
                            onClick = { settings.request(state.modelKind, SettingsRequest.Reset(emptyList())) },
                            enabled = enabled,
                        )
                    }
                }
            }
        }
        }

        item(key = "objects") {
            OrcaSidebarTitle(stringResource(R.string.section_objects), DesignR.drawable.orca_split_objects) {
                // The desktop app adds to its selection with a Ctrl-click; a
                // phone has no modifier, so the list is switched to picking
                // several objects, as its file managers do.
                OrcaIconButton(
                    icon = DesignR.drawable.orca_checked,
                    contentDescription = orcaString("Select multiple objects"),
                    onClick = { picking = !picking },
                    enabled = enabled && state.objects.isNotEmpty(),
                    tint = if (picking) OrcaTheme.colors.accent else OrcaTheme.colors.textSide,
                )
            }
        }
        objectListItems(
            state = state,
            enabled = enabled,
            picking = picking,
            filaments = filamentColors,
            actions = objectList,
            onChooseShape = { mesh, type -> addingPart = mesh to type },
            onEditRange = { editingRange = it },
        )

        settingsTabItems(tab)

        item(key = "engine") {
        Text(
            text = when (state.engine.availability) {
                EngineAvailability.STARTING -> stringResource(R.string.engine_starting)
                EngineAvailability.READY -> stringResource(R.string.engine_ready, state.engine.version?.value.orEmpty())
                EngineAvailability.UNAVAILABLE -> stringResource(R.string.engine_unavailable)
            },
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(12.dp),
        )

        // Import Configs and Export Preset Bundle of the desktop app's File menu.
        SidebarAction(DesignR.drawable.orca_add, stringResource(R.string.config_import), onImportConfig)
        SidebarAction(DesignR.drawable.orca_save, stringResource(R.string.config_export), onExportConfig)
        SidebarAction(DesignR.drawable.orca_help, stringResource(R.string.about), onOpenAbout)
        }
    }

    SettingsTabDialogs(tab)

    if (comparing) {
        DiffPresetDialog(comparison, onDismiss = { comparing = false })
    }

    // CreatePrinterPresetDialog, which the printer list opens. It closes itself
    // once the printer is made, so a question keeps the filled-in pages.
    if (openNetworkPrinters) {
        PhysicalPrintersSheet(
            load = network.load,
            onSave = network.save,
            onDelete = network.delete,
            onDismiss = { openNetworkPrinters = false },
            onTest = network.test,
            loadPresets = network.presets,
        )
    }
    if (printers.creating) {
        CreatePrinterDialog(
            loadOptions = printers.options,
            onCreate = printers.create,
            onDismiss = printers.close,
        )
    }
    printers.question?.let { question ->
        SettingsQuestionDialog(question, onAnswer = printers.answer)
    }

    if (searching) {
        SettingsSearchSheet(
            mode = process.settings?.mode ?: SettingsMode.SIMPLE,
            loadCatalog = searchCatalog,
            onChoose = { option ->
                searching = false
                onOpenSetting(option)
            },
            onDismiss = { searching = false },
        )
    }

    // The colour of a filament, which the plate then paints its objects with
    // and works the flushing volumes out again for (on_filament_color_changed).
    pickingColor?.let { index ->
        SettingColorDialog(
            value = presets?.filamentColors?.getOrNull(index).orEmpty(),
            onPick = { color ->
                pickingColor = null
                if (!color.equals(presets?.filamentColors?.getOrNull(index), ignoreCase = true)) filaments.setColor(index, color)
            },
            onDismiss = { pickingColor = null },
        )
    }

    // WipingDialog: the flushing volumes of the plate, read when the sheet opens.
    if (openFlushVolumes) {
        LaunchedEffect(Unit) { flushVolumes = filaments.describeFlushVolumes() }
        when (val outcome = flushVolumes) {
            null -> Unit
            is FlushVolumesOutcome.Failure -> FlushVolumesFailureDialog(outcome.message) {
                openFlushVolumes = false
                flushVolumes = null
            }
            is FlushVolumesOutcome.Success -> FlushVolumesSheet(
                volumes = outcome.volumes,
                colors = outcome.volumes.filaments.let { count -> List(count) { slotColor(presets, state, it) } },
                onDismiss = {
                    openFlushVolumes = false
                    flushVolumes = null
                },
                onApply = { matrix, multipliers ->
                    openFlushVolumes = false
                    flushVolumes = null
                    filaments.setFlushVolumes(matrix, multipliers)
                },
            )
        }
    }

    // The heights of a range, which the desktop app edits in its object list.
    editingRange?.let { id ->
        val range = state.objects.firstOrNull { it.mesh == id.mesh }?.layerRanges?.getOrNull(id.index)
        if (range == null) {
            editingRange = null
        } else {
            LayerRangeSheet(
                range = range,
                onDismiss = { editingRange = null },
                onApply = { bottom, top ->
                    editingRange = null
                    objectList.editRange(id, bottom, top)
                },
            )
        }
    }

    // The submenu of shapes of the desktop app, as a sheet.
    addingPart?.let { (mesh, type) ->
        PartShapeSheet(
            type = type,
            onDismiss = { addingPart = null },
            onChoose = { shape ->
                addingPart = null
                objectList.addPart(mesh, shape, type)
            },
        )
    }

    if (presets != null) {
        when (openList) {
            PresetList.PRINTERS -> PresetListSheet(
                title = stringResource(R.string.section_printer),
                items = presets.printers,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    onChoose(if (item.group == PresetGroup.SYSTEM) PresetChoice.PrinterModel(item.name) else PresetChoice.Printer(ProfileId(item.name)))
                },
                action = stringResource(R.string.select_printers),
                onAction = {
                    openList = null
                    onOpenWizard(PresetWizardPage.PRINTERS)
                },
                // "Create printer" of the printer combo box.
                secondAction = orcaString("Create printer"),
                onSecondAction = {
                    openList = null
                    printers.open()
                },
            )
            PresetList.FILAMENTS -> PresetListSheet(
                title = stringResource(R.string.section_filament),
                items = presets.filaments,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    // The first slot is the preset the filament tab edits; the
                    // others are slots of the plate (PlaterPresetComboBox).
                    if (editingSlot == 0) {
                        onChoose(PresetChoice.Filament(ProfileId(item.name)))
                    } else {
                        filaments.select(editingSlot, ProfileId(item.name))
                    }
                },
                action = stringResource(R.string.select_filaments),
                onAction = {
                    openList = null
                    onOpenWizard(PresetWizardPage.FILAMENTS)
                },
            )
            PresetList.PROCESSES -> PresetListSheet(
                title = stringResource(R.string.section_process),
                items = presets.processes,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    onChoose(PresetChoice.Process(ProfileId(item.name)))
                },
            )
            null -> Unit
        }
    }

    PresetChangeDialog(state.presetChange, presets, presetChange, settings)
}

/** The label the combo box shows for its selected entry. */
private fun List<PresetListItem>.selectedLabel(fallback: String): String = firstOrNull(PresetListItem::selected)?.label ?: fallback

@Preview(name = "Light", widthDp = 320, heightDp = 480)
@Preview(name = "Dark", widthDp = 320, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlateSidebarPreview() = OrcinusTheme {
    val selection = SlicingProfileSelection(
        ProfileId("Creality K2 Plus 0.4 nozzle"),
        ProfileId("Generic PLA @K2 Plus-all"),
        ProfileId("0.20mm Standard @Creality K2 Plus 0.4 nozzle"),
    )
    PlateSidebarContent(
        SidebarUiState(
            engine = EngineState(EngineAvailability.READY, EngineVersion("orca-upstream/2.4.2")),
            presets = Presets(
                selection = selection,
                setupRequired = false,
                printers = listOf(PresetListItem("Creality K2 Plus", "Creality K2 Plus", PresetGroup.SYSTEM, "", selected = true)),
                filaments = listOf(PresetListItem(selection.filament.value, "Generic PLA", PresetGroup.SYSTEM, "Generic", selected = true)),
                processes = listOf(PresetListItem(selection.process.value, selection.process.value, PresetGroup.SYSTEM, "", selected = true)),
                nozzleDiameters = listOf("0.2", "0.4", "0.6", "0.8"),
                nozzleDiameter = "0.4",
            ),
            canChoose = true,
        ),
        onChoose = {},
        onChooseScope = {},
        objectList = ObjectListActions(
            select = { _, _ -> },
            selectAlone = {},
            selectSettings = {},
            setPrintable = { _, _ -> },
            setAutoDrop = { _, _ -> },
            copy = {},
            removeCopy = {},
            addPart = { _, _, _ -> },
            removePart = { _, _ -> },
            selectPart = {},
            selectPartSettings = {},
            addRange = { _, _ -> },
            removeRange = {},
            selectRange = {},
            selectRangeSettings = {},
            editRange = { _, _, _ -> },
            setObjectExtruder = { _, _ -> },
            setPartExtruder = { _, _ -> },
            setRangeExtruder = { _, _ -> },
            delete = {},
        ),
        onOpenWizard = {},
        onOpenSettings = {},
        onOpenAbout = {},
        onImportConfig = {},
        onExportConfig = {},
        settings = SettingsActions(
            request = { _, _ -> },
            answer = { _, _ -> },
            dismissNotice = {},
            tooltip = { _, _ -> emptyList() },
            checkPresetName = { _, _ -> PresetNameOutcome.Failure("preview") },
            compatibleChoices = { _, _ -> PresetNamesOutcome.Failure("preview") },
            bedShape = { BedShapeOutcome.Failure("preview") },
            setBedShape = { _, _ -> },
            gcodePlaceholders = { _, _ -> GcodePlaceholdersOutcome.Failure("preview") },
            gcodePlaceholder = { _, _ -> GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true) },
        ),
        presetChange = PresetChangeActions(resolve = {}, save = {}, cancel = {}),
    )
}
