package app.orcinus.shadow.feature.sidebar

import android.content.res.Configuration
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.model.BedFileOutcome
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.core.model.ListClipboard
import app.orcinus.shadow.core.model.PresetSave
import app.orcinus.shadow.core.model.allSliceResultsReady
import app.orcinus.shadow.core.model.selectedCopies
import app.orcinus.shadow.core.ui.ExportResultDialog
import app.orcinus.shadow.core.ui.LocalToolpathsExport
import app.orcinus.shadow.core.ui.plate.SelectionMenuActions
import app.orcinus.shadow.core.ui.plate.SelectionMenuState
import app.orcinus.shadow.core.ui.plate.selectionMenuState
import app.orcinus.shadow.core.ui.plate.selectsSeveralObjects
import app.orcinus.shadow.core.ui.settings.BedShapeFileActions
import app.orcinus.shadow.core.ui.settings.CreatePresetSuccessfulDialog
import app.orcinus.shadow.core.ui.shareDocument
import app.orcinus.shadow.domain.plate.BedShapeFilesUseCase
import app.orcinus.shadow.domain.plate.CopyLayerRangesUseCase
import app.orcinus.shadow.domain.plate.CutConnectorsUseCase
import app.orcinus.shadow.domain.plate.EditLayerHeightsUseCase
import app.orcinus.shadow.domain.plate.ExportPlateMeshesUseCase
import app.orcinus.shadow.domain.plate.ExportToolpathsUseCase
import app.orcinus.shadow.domain.plate.LoadObjectVolumesUseCase
import app.orcinus.shadow.domain.plate.ObjectOrderUseCase
import app.orcinus.shadow.domain.plate.ObservePrinterConnectionUseCase
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.ui.settings.PrinterConnectionSheet
import app.orcinus.shadow.domain.plate.InvalidateCutInfoUseCase
import app.orcinus.shadow.domain.plate.ListHostPrintersUseCase
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
import androidx.compose.ui.unit.IntOffset
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
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
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
import app.orcinus.shadow.core.model.BonjourReply
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.CalibrationPrinterOutcome
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.CrealityHost
import app.orcinus.shadow.core.model.FlashforgeDiscoveryOutcome
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlowRateCalibration
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PendingPresetChange
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChangeAction
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
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsClipboard
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsScope
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.core.model.listPlateOf
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.network.rememberLocalNetworkAccess
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.plate.CloneDialog
import app.orcinus.shadow.core.ui.plate.MenuFilament
import app.orcinus.shadow.core.ui.plate.NumberOfInstancesDialog
import app.orcinus.shadow.core.ui.plate.PartShapeSheet
import app.orcinus.shadow.core.ui.plate.RenameDialog
import app.orcinus.shadow.core.ui.plate.exportFileName
import app.orcinus.shadow.core.ui.preset.PresetListSheet
import app.orcinus.shadow.core.ui.settings.CreatePrinterDialog
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
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.AddObjectPartUseCase
import app.orcinus.shadow.domain.plate.AddPlateInstanceUseCase
import app.orcinus.shadow.domain.plate.AddPrimitiveUseCase
import app.orcinus.shadow.domain.plate.BrowsePrintHostsUseCase
import app.orcinus.shadow.domain.plate.CalibrateUseCase
import app.orcinus.shadow.domain.plate.CloudLoginUseCase
import app.orcinus.shadow.domain.plate.ChangeVolumeTypeUseCase
import app.orcinus.shadow.domain.plate.ClonePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.CopyProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.CopyToClipboardUseCase
import app.orcinus.shadow.domain.plate.CustomPrinterUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DeletePlateUseCase
import app.orcinus.shadow.domain.plate.DescribeCalibrationPrinterUseCase
import app.orcinus.shadow.domain.plate.DescribeFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.EditLayerRangeUseCase
import app.orcinus.shadow.domain.plate.EditPlateObjectUseCase
import app.orcinus.shadow.domain.plate.ExportConfigUseCase
import app.orcinus.shadow.domain.plate.ExportObjectMeshUseCase
import app.orcinus.shadow.domain.plate.FillBedWithInstancesUseCase
import app.orcinus.shadow.domain.plate.ImportConfigUseCase
import app.orcinus.shadow.domain.plate.LockPlateUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.OpenSimplifyUseCase
import app.orcinus.shadow.domain.plate.PasteFromClipboardUseCase
import app.orcinus.shadow.domain.plate.PasteProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectUseCase
import app.orcinus.shadow.domain.plate.PlateFilamentsUseCase
import app.orcinus.shadow.domain.plate.PlateJobsUseCase
import app.orcinus.shadow.domain.plate.PlateObjectsUseCase
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.PrintHostCertificateUseCase
import app.orcinus.shadow.domain.plate.ProjectLifecycleUseCase
import app.orcinus.shadow.domain.plate.ReloadFromDiskUseCase
import app.orcinus.shadow.domain.plate.RemoveLastPlateInstancesUseCase
import app.orcinus.shadow.domain.plate.RemoveLayerRangeUseCase
import app.orcinus.shadow.domain.plate.RemoveObjectPartUseCase
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.RenamePlateItemUseCase
import app.orcinus.shadow.domain.plate.RenamePlateUseCase
import app.orcinus.shadow.domain.plate.ReplaceAllVolumesUseCase
import app.orcinus.shadow.domain.plate.ReplaceObjectVolumeUseCase
import app.orcinus.shadow.domain.plate.RequestEmbossUseCase
import app.orcinus.shadow.domain.plate.SaveProjectUseCase
import app.orcinus.shadow.domain.plate.SelectLayerRangeUseCase
import app.orcinus.shadow.domain.plate.SelectObjectPartUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SelectPlateUseCase
import app.orcinus.shadow.domain.plate.SelectPresetUseCase
import app.orcinus.shadow.domain.plate.SelectionMenuUseCase
import app.orcinus.shadow.domain.plate.SeparatePlateInstancesUseCase
import app.orcinus.shadow.domain.plate.SetBedShapeUseCase
import app.orcinus.shadow.domain.plate.SetExtruderUseCase
import app.orcinus.shadow.domain.plate.SetFlushOptionUseCase
import app.orcinus.shadow.domain.plate.SetFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.SetNumberOfInstancesUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectPrintableUseCase
import app.orcinus.shadow.domain.plate.SetSettingsScopeUseCase
import app.orcinus.shadow.domain.plate.TestPhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.canDeletePlate
import app.orcinus.shadow.domain.plate.canMoveObject
import app.orcinus.shadow.domain.preferences.AppPreferences
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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
    /** The copy whose object's "Cut connectors" item is the selection; null for none. */
    val selectedConnectors: PlateInstanceId? = null,
    /** OrcaSlicer's tabs for the plate, for the selected object and for its part. */
    val plateSettings: SettingsTabState = SettingsTabState(PresetKind.PLATE),
    val objectSettings: SettingsTabState = SettingsTabState(PresetKind.OBJECT),
    val partSettings: SettingsTabState = SettingsTabState(PresetKind.PART),
    val rangeSettings: SettingsTabState = SettingsTabState(PresetKind.LAYER),
    /** The plate, whose centre the object menu's Center moves an object over. */
    val plate: PlateDescription? = null,
    /** What Copy and Cut took, which the menus' Paste puts back. */
    val clipboard: PlateClipboard? = null,
    /** The tower as the engine last described it, which the object menu's Flush Options go by. */
    val flushing: WipeTower = WipeTower(),
    /** What "Copy Process Settings" took. */
    val settingsClipboard: SettingsClipboard? = null,
    /** MenuFactory::multi_selection_menu() while the selection holds several objects whole; null otherwise. */
    val selectionMenu: SelectionMenuState? = null,
    /** The height ranges the object list copied. */
    val listClipboard: ListClipboard? = null,
    /** The Simplify gizmo is open on the canvas. */
    val simplifying: Boolean = false,
    /** The project's name, none while it is "Untitled". */
    val projectName: String? = null,
    /** The project changed since it was opened, saved or started, which its name's star marks. */
    val projectDirty: Boolean = false,
    /** The project can be saved: the presets are known and the plate is not changing. */
    val canSaveProject: Boolean = false,
    /**
     * "Export plate sliced file" and "Export all plate sliced file" can write
     * their file (MainFrame::can_export_gcode(), can_export_all_gcode()).
     */
    val canExportSliced: Boolean = false,
    val canExportAllSliced: Boolean = false,
    /** The Export menu's objects and Generic 3MF can be written (MainFrame::can_export_model()). */
    val canExportModel: Boolean = false,
    /** The plates of the object list (ObjectDataViewModel's plate items), each with the objects under it. */
    val plates: List<ObjectListPlate> = listOf(ObjectListPlate(0)),
    /** The objects that stand on no plate whole, under "Outside". */
    val outsideObjects: List<PlateObject> = emptyList(),
    val currentPlate: Int = 0,
    /** Plater::can_delete_plate(). */
    val canDeletePlate: Boolean = false,
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
    private val bedShapeFiles: BedShapeFilesUseCase? = null,
    private val hostCertificates: PrintHostCertificateUseCase? = null,
    private val selectPlateObject: SelectPlateObjectUseCase,
    private val selectObjectPart: SelectObjectPartUseCase,
    private val addLayerRange: AddLayerRangeUseCase,
    private val removeLayerRange: RemoveLayerRangeUseCase,
    private val selectLayerRange: SelectLayerRangeUseCase,
    private val editLayerRange: EditLayerRangeUseCase,
    private val copyLayerRanges: CopyLayerRangesUseCase,
    private val objectOrder: ObjectOrderUseCase,
    private val setExtruder: SetExtruderUseCase,
    private val describeFlush: DescribeFlushVolumesUseCase,
    private val setFlush: SetFlushVolumesUseCase,
    private val importConfig: ImportConfigUseCase,
    private val exportConfig: ExportConfigUseCase,
    private val setSettingsScope: SetSettingsScopeUseCase,
    private val setPlateObjectPrintable: SetPlateObjectPrintableUseCase,
    private val setPlateObjectAutoDrop: SetPlateObjectAutoDropUseCase,
    private val addPlateInstance: AddPlateInstanceUseCase,
    private val removeLastPlateInstances: RemoveLastPlateInstancesUseCase,
    private val setNumberOfInstances: SetNumberOfInstancesUseCase,
    private val placePlateObject: PlacePlateObjectUseCase,
    private val renamePlateItem: RenamePlateItemUseCase,
    private val editPlateObject: EditPlateObjectUseCase,
    private val addObjectPart: AddObjectPartUseCase,
    private val loadObjectVolumes: LoadObjectVolumesUseCase,
    private val removeObjectPart: RemoveObjectPartUseCase,
    private val invalidateCutInfo: InvalidateCutInfoUseCase,
    private val cutConnectors: CutConnectorsUseCase,
    private val removePlateInstance: RemovePlateInstanceUseCase,
    private val clonePlateObjects: ClonePlateObjectsUseCase,
    private val separatePlateInstances: SeparatePlateInstancesUseCase,
    private val fillBedWithInstances: FillBedWithInstancesUseCase,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val pasteFromClipboard: PasteFromClipboardUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val printerConnection: ObservePrinterConnectionUseCase,
    private val testPhysicalPrinter: TestPhysicalPrinterUseCase,
    private val browsePrintHosts: BrowsePrintHostsUseCase,
    private val listHostPrinters: ListHostPrintersUseCase,
    private val cloudLogin: CloudLoginUseCase,
    private val setFlushOption: SetFlushOptionUseCase,
    private val copySettings: CopyProcessSettingsUseCase,
    private val pasteSettings: PasteProcessSettingsUseCase,
    private val exportObjectMesh: ExportObjectMeshUseCase,
    private val replaceObjectVolume: ReplaceObjectVolumeUseCase,
    private val openSimplify: OpenSimplifyUseCase,
    private val changeVolumeType: ChangeVolumeTypeUseCase,
    private val replaceAllVolumesUseCase: ReplaceAllVolumesUseCase,
    private val reloadFromDiskUseCase: ReloadFromDiskUseCase,
    private val saveProject: SaveProjectUseCase,
    private val exportToolpaths: ExportToolpathsUseCase,
    private val exportPlateMeshes: ExportPlateMeshesUseCase? = null,
    private val selectionMenu: SelectionMenuUseCase? = null,
    private val projectLifecycle: ProjectLifecycleUseCase,
    private val calibrateUseCase: CalibrateUseCase,
    private val describeCalibrationPrinterUseCase: DescribeCalibrationPrinterUseCase,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val selectPlate: SelectPlateUseCase,
    private val plateObjects: PlateObjectsUseCase,
    private val plateJobs: PlateJobsUseCase,
    private val deletePlate: DeletePlateUseCase,
    private val lockPlate: LockPlateUseCase,
    private val renamePlate: RenamePlateUseCase,
    private val addPrimitive: AddPrimitiveUseCase,
    private val editLayerHeights: EditLayerHeightsUseCase,
    private val requestEmboss: RequestEmbossUseCase,
    preferences: AppPreferences,
) : ViewModel() {
    /** What the Preferences change on the clone dialog. */
    val canvas: StateFlow<CanvasPreferences> = preferences.canvas

    /** The variable layer height mark of an object's row: the bar opens on the object. */
    fun editLayersOf(mesh: ScenePath) = editLayerHeights.enableFor(mesh)

    /** "Edit text" of a row: the canvas's text tool opens on the volume. */
    fun editTextOf(volume: ObjectPartId) = requestEmboss.edit(volume)

    /** "Text" of a row's "Add part": the canvas places a text on the object, as without a mouse position. */
    fun addTextTo(mesh: ScenePath, type: VolumeType) = requestEmboss.add(EmbossKind.TEXT, mesh, type)

    /** "Edit SVG" of a row: the canvas's SVG tool opens on the volume. */
    fun editSvgOf(volume: ObjectPartId) = requestEmboss.edit(volume)

    /** "SVG" of a row's "Add part": the canvas asks for the file and places the SVG on the object. */
    fun addSvgTo(mesh: ScenePath, type: VolumeType) = requestEmboss.add(EmbossKind.SVG, mesh, type)

    /** A plate item of the object list: nothing stays selected and the plate becomes current (ObjectList::selection_changed). */
    fun choosePlate(index: Int) {
        selectPlateObject(null)
        selectPlate(index)
    }

    /** The settings row of a plate, whose settings the parameter panel then edits. */
    fun openPlateSettings(index: Int) {
        openSettingsOf(null)
        selectPlate(index)
    }

    /** The plate menu's "Replace all with 3D files": the objects the list shows under the plate. */
    fun replaceAllOnPlate(index: Int, folder: ExternalDocumentReference) = replaceAllVolumesUseCase.onPlate(index, folder)

    /** The multi-selection menu's "Replace all with 3D files", from [folder]. */
    fun replaceAllInSelection(folder: ExternalDocumentReference) = replaceAllVolumesUseCase.selected(folder)

    /** The plate menu of the object list (MenuFactory::create_plate_menu) for the current plate. */
    fun selectPlateObjects() = plateObjects.selectCurrentPlate()

    fun selectAllPlates() = plateObjects.selectAll()

    fun deletePlateObjects() = plateObjects.deleteCurrentPlate()

    fun arrangePlate(index: Int) = plateJobs.arrange(index)

    fun orientPlate(index: Int) = plateJobs.orient(index)

    fun removePlate(index: Int) = deletePlate(index)

    fun togglePlateLock(index: Int) = lockPlate(index)

    fun setPlateName(index: Int, name: String) = renamePlate(index, name)

    /** The menu's Add Primitive, Add Handy models and Add Models. */
    fun addShape(shape: String, name: String) = addPrimitive(shape, name)

    fun addHandyModel(model: HandyModel) = addModelToPlate.handy(model)

    fun addModels(documents: List<ExternalDocumentReference>) = addModelToPlate(documents)

    /** Plater::new_project() */
    fun newProject() = projectLifecycle.newProject()

    /** A test of the Calibration menu: its own project, its model and the presets it prints with. */
    fun calibrate(params: CalibrationParams) = calibrateUseCase(params)

    /** The flow ratio test of the Calibration menu (Plater::calib_flowrate). */
    fun calibrateFlowRate(test: FlowRateCalibration) = calibrateUseCase(test)

    /** What the dialogs of Input Shaping and Cornering read of the printer. */
    suspend fun describeCalibrationPrinter(): CalibrationPrinterOutcome = describeCalibrationPrinterUseCase()

    /** Open Project: the document the user picked opens as a project. */
    fun openProject(document: ExternalDocumentReference) = addModelToPlate.openProject(document)

    /** "Save Project" asks for a document first when the project has none to write again. */
    val projectNeedsDocument: Boolean get() = saveProject.needsDocument

    /** Plater::save_project(): into [document] for "Save Project as", into the project's own otherwise. */
    fun saveProject(document: ExternalDocumentReference? = null) = saveProject.invoke(document)

    /** The project as Android's share sheet takes it, written under [name]; null when it could not be written. */
    suspend fun shareProject(name: String): ExternalDocumentReference? = saveProject.share(name)

    /** Plater::export_gcode_3mf() into [document]: the current plate's G-code, or every sliced plate's when [all]. */
    fun exportSliced(document: ExternalDocumentReference, all: Boolean) {
        viewModelScope.launch { saveProject.exportSliced(document, all) }
    }

    /** The name "Export plate sliced file" offers. */
    fun slicedName(): String? = saveProject.slicedName()

    /** Plater::export_core_3mf() into [document]. */
    fun exportGeneric(document: ExternalDocumentReference) {
        viewModelScope.launch { saveProject.exportGeneric(document) }
    }

    /** "Export all objects as one STL" (or DRC) into [document], or with [selection] "Export as one STL" of the selected objects. */
    fun exportAllMeshes(format: MeshFormat, document: ExternalDocumentReference, selection: Boolean = false) {
        viewModelScope.launch { exportPlateMeshes?.toDocument(format, document, selection) }
    }

    /** "Export all objects as STLs" (or DRCs) into [folder], or with [selection] "Export as STLs" of the selected objects. */
    fun exportEachMesh(format: MeshFormat, folder: ExternalDocumentReference, selection: Boolean = false) {
        viewModelScope.launch { exportPlateMeshes?.toFolder(format, folder, selection) }
    }

    /** The multi-selection menu's items over the selected objects. */
    fun selectionActions(openSettings: () -> Unit, replaceAll: () -> Unit, export: (MeshFormat, Boolean) -> Unit) = SelectionMenuActions(
        cut = { plateState.value.selectedCopies().takeIf { it.isNotEmpty() }?.let { copyToClipboard.objects(it.toSet(), cut = true) } },
        copy = { plateState.value.selectedCopies().takeIf { it.isNotEmpty() }?.let { copyToClipboard.objects(it.toSet()) } },
        paste = { plateState.value.selectedCopies().firstOrNull()?.let { pasteFromClipboard(it) } },
        edit = { editPlateObject.selected(it) },
        center = { selectionMenu?.center() },
        drop = { selectionMenu?.drop() },
        delete = { selectionMenu?.delete() },
        setPrintable = { selectionMenu?.setPrintable(it) },
        setAutoDrop = { selectionMenu?.setAutoDrop(it) },
        editProcessSettings = {
            setSettingsScope(SettingsScope.OBJECT)
            openSettings()
        },
        pasteProcessSettings = { pasteSettings.selected() },
        setFilament = { setExtruder.selected(it) },
        replaceAll = replaceAll,
        export = export,
    )

    /** The name "Export all objects as one STL" (or DRC) offers. */
    fun allMeshesName(format: MeshFormat, untitled: String): String =
        exportPlateMeshes?.suggestedName(format, untitled) ?: "$untitled.${format.extension}"

    /** The name "Export toolpaths as OBJ" offers its OBJ file under. */
    fun toolpathsName(untitled: String): String = exportToolpaths.suggestedName(untitled)

    /** "Export toolpaths as OBJ" into [document]: [write] writes what the preview shows; null when it could not. */
    suspend fun exportToolpaths(document: ExternalDocumentReference, untitled: String, write: suspend (String) -> Boolean): ExportToolpathsUseCase.Materials? =
        exportToolpaths.writeToolpaths(document, untitled, write)

    /** The materials of the toolpaths written, into [document], or dropped for null. */
    suspend fun saveToolpathMaterials(materials: ExportToolpathsUseCase.Materials, document: ExternalDocumentReference?): Boolean =
        exportToolpaths.saveMaterials(materials, document)

    private val plateState = observePlate()

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

    /** A field of the range being edited took the focus, whose plane the 3D view shows solid. */
    fun focusRangeField(field: LayerRangeEditor) = selectLayerRange.focus(field)

    fun copyRange(id: LayerRangeId) = copyLayerRanges(id)

    /** ObjectList::can_drop() of an object onto another, and OnDrop() of an object and of a volume. */
    fun canMoveObject(from: ScenePath, to: ScenePath) = plateState.value.canMoveObject(from, to)

    fun moveObject(from: ScenePath, to: ScenePath) = objectOrder.moveObject(from, to)

    fun moveVolume(mesh: ScenePath, from: Int, to: Int) = objectOrder.moveVolume(mesh, from, to)

    fun copyRanges(mesh: ScenePath) = copyLayerRanges.all(mesh)

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

    /** The check box of an object's row: every copy of it. */
    fun setWholeObjectPrintable(mesh: ScenePath, printable: Boolean) = setPlateObjectPrintable.all(mesh, printable)

    fun toggleFlushOption(mesh: ScenePath, option: FlushOption) = setFlushOption(mesh, option)

    /** ObjectList::switch_to_object_process(): the item's own settings are shown. */
    fun editProcessSettings(item: SettingsItem) = when (item) {
        is SettingsItem.Object -> openSettingsOf(PlateInstanceId(item.mesh))
        is SettingsItem.Volume -> openSettingsOfPart(item.id)
        is SettingsItem.Layer -> openSettingsOfRange(item.id)
    }

    fun copyProcessSettings(item: SettingsItem) = copySettings(item)

    fun pasteProcessSettings(item: SettingsItem) = pasteSettings(item)

    /** "Export as one STL/DRC" of the object into the document the user picked. */
    fun exportMesh(mesh: ScenePath, format: MeshFormat, document: ExternalDocumentReference) {
        viewModelScope.launch { exportObjectMesh(mesh, format, document) }
    }

    /** "Simplify Model": the gizmo opens on the canvas, over the whole object or one of its volumes. */
    fun simplifyObject(mesh: ScenePath) = openSimplify.ofObject(PlateInstanceId(mesh), wholeObject = true)

    fun simplifyVolume(id: ObjectPartId) = openSimplify.ofVolume(id)

    /** "Change type" of a volume. */
    fun setVolumeType(id: ObjectPartId, type: VolumeType) = changeVolumeType(id, type)

    /** "Replace all with 3D files" from the folder the user picked. */
    fun replaceAllVolumes(copy: PlateInstanceId, folder: ExternalDocumentReference) = replaceAllVolumesUseCase(copy, folder)

    /** "Replace 3D file": the volume takes the mesh of the document the user picked. */
    fun replaceVolume(copy: PlateInstanceId, volume: Int, document: ExternalDocumentReference) = replaceObjectVolume(copy, volume, document)

    /** Plater::reload_from_disk() of the object's volumes, or of its volume [volume]. */
    fun reloadFromDisk(mesh: ScenePath, volume: Int?) = reloadFromDiskUseCase(mesh, volume)

    /** Plater::reload_all_from_disk() */
    fun reloadAll() = reloadFromDiskUseCase.all()

    fun setObjectAutoDrop(id: PlateInstanceId, autoDrop: Boolean) = setPlateObjectAutoDrop(id, autoDrop)

    /** Plater::increase_instances(), decrease_instances() and set_number_of_copies(). */
    fun addInstance(mesh: ScenePath) = addPlateInstance(mesh)

    fun removeInstance(mesh: ScenePath) = removeLastPlateInstances(mesh)

    fun setInstances(mesh: ScenePath, number: Int) = setNumberOfInstances(mesh, number)

    /** Selection::erase() of one copy. */
    fun removeObjectCopy(id: PlateInstanceId) = removePlateInstance(id)

    /** "Fill bed with instances" of the whole object, which the list selects. */
    fun fillBed(mesh: ScenePath) = fillBedWithInstances(mesh)

    /** ObjectList::split_instances() of those copies of the object. */
    fun setAsIndividual(mesh: ScenePath, instances: Set<Int>) = separatePlateInstances(mesh, instances)

    /** Cut and Copy of copies of objects, and of volumes of an object over one of its copies. */
    fun copyObjects(copies: Set<PlateInstanceId>, cut: Boolean) = copyToClipboard.objects(copies, cut)

    fun copyVolumes(id: PlateInstanceId, volumes: Set<Int>, cut: Boolean) = copyToClipboard.volumes(id, volumes, cut)

    /** Paste over a copy: the objects the clipboard holds, or its volumes into that object. */
    fun paste(id: PlateInstanceId) = pasteFromClipboard(id)

    /** The clone dialog's OK for the whole object, with every copy of it. */
    fun clone(mesh: ScenePath, count: Int, arrange: Boolean) {
        val target = state.value.objects.firstOrNull { it.mesh == mesh } ?: return
        clonePlateObjects(target.instances.indices.mapTo(LinkedHashSet()) { PlateInstanceId(mesh, it) }, count, arrange)
    }

    /** The object menu's Center, Drop and Mirror. */
    fun manipulate(id: PlateInstanceId, manipulation: Manipulation) = placePlateObject(id, manipulation)

    /** ObjectList::rename_item() */
    fun rename(mesh: ScenePath, name: String) = renamePlateItem(mesh, name)

    fun renamePart(id: ObjectPartId, name: String) = renamePlateItem(id, name)

    /** The object menu's commands that change meshes. */
    fun editObject(mesh: ScenePath, edit: ObjectEdit, volume: Int?) = editPlateObject(mesh, edit, volume)

    fun addPart(mesh: ScenePath, shape: String, type: VolumeType, name: String) = addObjectPart(mesh, shape, type, name)

    /** ObjectList::load_subobject() of the documents the file picker gave. */
    fun loadPart(mesh: ScenePath, type: VolumeType, documents: List<ExternalDocumentReference>) = loadObjectVolumes(mesh, type, documents)

    /** ObjectList::del_subobject_item() of a volume: the object's own mesh goes through the engine. */
    fun removePart(id: ObjectPartId) = if (id.index == 0) editPlateObject.deleteOwnVolume(id.mesh) else removeObjectPart(id)

    /** "Invalidate cut info" of an object's menu. */
    fun invalidateCutInfoOf(mesh: ScenePath) = invalidateCutInfo(mesh)

    /** The "Cut connectors" item: picked, deleted, given a filament. */
    fun selectCutConnectors(copy: PlateInstanceId) = cutConnectors.select(copy)

    fun deleteCutConnectors(mesh: ScenePath) = cutConnectors.delete(mesh)

    fun setConnectorsExtruder(mesh: ScenePath, extruder: Int) = setExtruder.connectors(mesh, extruder)

    fun deleteObject(mesh: ScenePath) = deletePlateObject(mesh)

    fun resolvePresetChange(action: PresetChangeAction) = selectPreset.resolve(action)

    fun savePresetChange(save: PresetSave) = selectPreset.save(save)

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

    /** CreatePrinterPresetDialog: what its pages offer for what they are filled in with. */
    suspend fun printerOptions(request: CreatePrinterRequest): CreatePrinterOptionsOutcome = customPrinter.options(request)

    /** PhysicalPrinterDialog's Browse button for the HTTPS CA file: the path of the kept copy. */
    suspend fun keepCaFile(document: ExternalDocumentReference): String? = hostCertificates?.keep(document)

    /** load_texture() and load_model_stl() of the dialog: the file kept for the printer. */
    suspend fun keepPrinterBedFile(document: ExternalDocumentReference, texture: Boolean): BedFileOutcome =
        bedShapeFiles?.keepForPrinter(document, texture) ?: BedFileOutcome.Failure("Invalid file format.")

    var printerQuestion: SettingsDialog? by mutableStateOf(null)
        private set

    /** The dialog's message box ("Info"). */
    var printerMessage: String? by mutableStateOf(null)
        private set

    /** The page of the dialog: the second shows once the first page's OK is through. */
    var printerPage: Int by mutableStateOf(1)
        private set

    /**
     * Whether the dialog is open. Upstream runs it modally and ends it only
     * once the printer is created, so a question answered with Cancel comes
     * back to the pages the user has filled in.
     */
    var creatingPrinter: Boolean by mutableStateOf(false)
        private set

    /** CreatePresetSuccessfulDialog, which follows the dialog once the printer is made. */
    var printerCreated: Boolean by mutableStateOf(false)
        private set

    private var pendingPrinter: CreatePrinterRequest? = null
    private var pendingPrinterCheck = false
    private var printerAnswers: Map<String, Boolean> = emptyMap()

    fun openCreatePrinter() {
        creatingPrinter = true
        printerPage = 1
    }

    fun closeCreatePrinter() {
        creatingPrinter = false
        printerPage = 1
        printerQuestion = null
        printerMessage = null
        pendingPrinter = null
        printerAnswers = emptyMap()
    }

    /** The first page's OK (validate_input_valid()). */
    fun checkPrinterPage(request: CreatePrinterRequest) = runPrinterRequest(request, check = true)

    /** The second page's Return. */
    fun returnToPrinterPage() {
        printerPage = 1
    }

    fun createPrinter(request: CreatePrinterRequest) = runPrinterRequest(request, check = false)

    fun dismissPrinterMessage() {
        printerMessage = null
    }

    fun dismissPrinterCreated() {
        printerCreated = false
    }

    /** The question's Yes or Cancel: the request runs again with the answer. */
    fun answerPrinterQuestion(yes: Boolean) {
        val question = printerQuestion ?: return
        printerQuestion = null
        printerAnswers = printerAnswers + (question.id to yes)
        if (yes) runPrinterRequest() else pendingPrinter = null
    }

    private fun runPrinterRequest(request: CreatePrinterRequest, check: Boolean) {
        pendingPrinter = request
        pendingPrinterCheck = check
        printerAnswers = emptyMap()
        runPrinterRequest()
    }

    private fun runPrinterRequest() {
        val request = pendingPrinter ?: return
        val check = pendingPrinterCheck
        viewModelScope.launch {
            val outcome = if (check) customPrinter.check(request, printerAnswers) else customPrinter.create(request, printerAnswers)
            when (outcome) {
                is PresetCreationOutcome.Question -> printerQuestion = outcome.question
                is PresetCreationOutcome.Failure -> {
                    pendingPrinter = null
                    printerMessage = outcome.message.takeIf { it.isNotEmpty() }
                }
                is PresetCreationOutcome.Success -> {
                    pendingPrinter = null
                    when {
                        // data_init() and show_page2()
                        check && outcome.name.isEmpty() -> printerPage = 2
                        // The system printer the user switched to: EndModal(wxID_CANCEL).
                        check -> closeCreatePrinter()
                        else -> {
                            closeCreatePrinter()
                            printerCreated = true
                        }
                    }
                }
            }
        }
    }

    /** Search::OptionsSearcher: every setting the preset tabs show. */
    suspend fun searchCatalog(): SearchCatalogOutcome = settingsTabs.searchCatalog()

    /**
     * The Connection button of the sidebar's printer title (Plater's
     * m_printer_connect): the printer's host on its edited preset.
     */
    suspend fun printerConnection(): PrinterConnectionOutcome = printerConnection.invoke()

    /** PhysicalPrinterDialog's OK: the host on the edited printer preset, which is saved as [name]. */
    fun savePrinterConnection(settings: ModelSettings, name: String) =
        settingsTabs.request(PresetKind.PRINTER, SettingsRequest.SaveConnection(settings, name))

    suspend fun checkPrinterName(name: String): PresetNameOutcome = settingsTabs.checkPresetName(PresetKind.PRINTER, name)

    suspend fun testNetworkPrinter(printer: PhysicalPrinter): PrintHostTestOutcome = testPhysicalPrinter(printer)

    /** PhysicalPrinterDialog's Browse button: BonjourDialog's lookup of OctoPrint's service. */
    fun lookupPrintHosts(): Flow<List<BonjourReply>> = browsePrintHosts.lookup()

    /** PhysicalPrinterDialog's Browse button for Creality's firmware (CrealityDiscoveryDialog). */
    suspend fun scanCrealityPrinters(): List<CrealityHost> = browsePrintHosts.scanCreality()

    /** The Test button's login of a cloud host outside the app (OAuthDialog). */
    suspend fun cloudLogin(printer: PhysicalPrinter, openPage: (String) -> Unit): CloudLoginOutcome = cloudLogin.invoke(printer, openPage)

    suspend fun cloudLoggedIn(printer: PhysicalPrinter): Boolean = cloudLogin.isLoggedIn(printer)

    /** PhysicalPrinterDialog's Log Out button. */
    suspend fun cloudLogOut(printer: PhysicalPrinter) = cloudLogin.logOut(printer)

    /** PhysicalPrinterDialog's Browse button for Flashforge (Flashforge::discover_printers()). */
    suspend fun discoverFlashforge(): FlashforgeDiscoveryOutcome = browsePrintHosts.discoverFlashforge()

    /** PhysicalPrinterDialog's Refresh button: the printers of a server that serves several. */
    suspend fun hostPrinters(printer: PhysicalPrinter): HostPrintersOutcome = listHostPrinters(printer)

    /** DiffPresetDialog: the presets of either side, and what the selected ones differ in. */
    suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean): PresetComparisonOutcome =
        settingsTabs.comparePresets(left, right, showAll)

    /** Its Transfer button: the chosen values move into the right presets. */
    fun transferPresetOptions(transfers: List<PresetTransfer>) = selectPreset.transfer(transfers)

    /** BedShapeDialog: the printable area of the edited printer, and the shape it is set to. */
    suspend fun bedShape(): BedShapeOutcome = settingsTabs.bedShape()

    suspend fun setBedShape(shape: BedShape) = setBedShape.invoke(shape)

    /** BedShapeDialog's files and drawing. */
    val bedFiles: BedShapeFileActions = bedShapeFiles?.let { use ->
        BedShapeFileActions(loadShape = use::loadShape, keep = use::keep, preview = use::preview, exists = use::exists)
    } ?: BedShapeFileActions.NONE

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
    selectedConnectors = selectedConnectors.takeIf { connectorsSelected },
    plateSettings = settingsTabs[PresetKind.PLATE] ?: SettingsTabState(PresetKind.PLATE),
    objectSettings = settingsTabs[PresetKind.OBJECT] ?: SettingsTabState(PresetKind.OBJECT),
    partSettings = settingsTabs[PresetKind.PART] ?: SettingsTabState(PresetKind.PART),
    rangeSettings = settingsTabs[PresetKind.LAYER] ?: SettingsTabState(PresetKind.LAYER),
    plate = plate,
    clipboard = clipboard,
    flushing = flushing,
    settingsClipboard = settingsClipboard,
    selectionMenu = takeIf { it.selectsSeveralObjects() }?.let { selectionMenuState(it, profiles != null && !busy, clipboard, settingsClipboard, emptyList()) },
    listClipboard = listClipboard,
    simplifying = simplifyTarget != null,
    projectName = project.name,
    projectDirty = projectDirty,
    canSaveProject = profiles != null && !busy,
    canExportSliced = profiles != null && !busy && objects.isNotEmpty() && result != null,
    canExportAllSliced = profiles != null && !busy && objects.isNotEmpty() && allSliceResultsReady(),
    canExportModel = profiles != null && !busy && objects.isNotEmpty(),
    plates = objects.groupBy(::listPlateOf).let { groups ->
        partPlates().mapIndexed { index, plate ->
            ObjectListPlate(
                index = index,
                name = plate.name,
                locked = plate.locked,
                overrides = plate.settings,
                objects = groups[index].orEmpty(),
                occupied = copies().any { plateOf(it) == index },
            )
        }
    },
    outsideObjects = objects.filter { listPlateOf(it) == null },
    currentPlate = currentPlate,
    canDeletePlate = canDeletePlate,
)

/**
 * A plate item of the object list: the plate at [index], named "Plate N" or
 * "Plate N (name)", with its settings of its own and the objects the list
 * shows under it; [occupied] while a copy stands on it (PartPlate::get_objects).
 */
data class ObjectListPlate(
    val index: Int,
    val name: String = "",
    val locked: Boolean = false,
    val overrides: ModelSettings = ModelSettings(),
    val objects: List<PlateObject> = emptyList(),
    val occupied: Boolean = false,
)

/**
 * The heights a range spans (ObjectList::edit_layer_range), which the desktop
 * app edits in two fields of its object list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerRangeSheet(range: LayerRange, onFocus: (LayerRangeEditor) -> Unit, onDismiss: () -> Unit, onApply: (Double, Double) -> Unit) {
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
                        modifier = Modifier
                            .width(160.dp)
                            // LayerRangeEditor's wxEVT_SET_FOCUS: the 3D view shows this height's plane solid.
                            .onFocusChanged { if (it.isFocused) onFocus(if (isBottom) LayerRangeEditor.MIN_Z else LayerRangeEditor.MAX_Z) },
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

/**
 * The OBJ file of "Export toolpaths as OBJ" is written: its materials, the
 * colours of the toolpaths, are saved beside it under the [name] it gives them.
 */
@Composable
private fun ToolpathMaterialsDialog(name: String, onSave: () -> Unit, onSkip: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = {},
        confirmButton = { OrcaButton(orcaString("Save"), onClick = onSave) },
        dismissButton = { OrcaButton(orcaString("Skip"), onClick = onSkip, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Export toolpaths as OBJ"), style = OrcaTheme.typography.head16) },
        text = { Text(stringResource(UiR.string.toolpaths_materials, name), style = OrcaTheme.typography.body14) },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}

/** The desktop app's File menu for the project: its quick-access Save and "Save Project as". */
internal class ProjectActions(
    val save: () -> Unit,
    val saveAs: () -> Unit,
    val new: () -> Unit,
    val open: () -> Unit,
    /** Android's share sheet for the project, written as "Save Project as" writes it. */
    val share: () -> Unit = {},
    /** "Export plate sliced file" (all = false) and "Export all plate sliced file" of the Export menu. */
    val exportSliced: (all: Boolean) -> Unit = {},
    /** "Export all objects as one STL" (multi = false) or "as STLs", and their DRC kin. */
    val exportMeshes: (format: MeshFormat, multi: Boolean) -> Unit = { _, _ -> },
    /** "Export Generic 3MF". */
    val exportGeneric: () -> Unit = {},
    /** "Export toolpaths as OBJ" of the Export menu; null while the preview shows no toolpaths. */
    val exportToolpaths: (() -> Unit)? = null,
    /** A test of the Calibration menu, which starts a project of its own. */
    val calibrate: (CalibrationParams) -> Unit = {},
    /** The flow ratio test of the Calibration menu, which starts a project of its own. */
    val calibrateFlowRate: (FlowRateCalibration) -> Unit = {},
    /** What the dialogs of Input Shaping and Cornering read of the printer as they open. */
    val describeCalibrationPrinter: suspend () -> CalibrationPrinterOutcome = { CalibrationPrinterOutcome.Failure("") },
) {
    companion object {
        val NONE = ProjectActions(save = {}, saveAs = {}, new = {}, open = {})
    }
}

/**
 * The project the plate is, named as the desktop app's title bar names it
 * ("Untitled" until it has a name), with the Save button of its quick-access
 * bar, the Calibration menu of its top bar, and its File menu under the arrow.
 */
@Composable
private fun ProjectTitle(
    name: String?,
    dirty: Boolean,
    canSave: Boolean,
    actions: ProjectActions,
    canExportSliced: Boolean = false,
    canExportAllSliced: Boolean = false,
    canExportModel: Boolean = false,
) {
    var fileMenu by remember { mutableStateOf(false) }
    var calibrationMenu by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(false) }
    var rangeTest by remember { mutableStateOf<RangeTest?>(null) }
    var pressureAdvance by remember { mutableStateOf(false) }
    var pressureAdvanceChoice by remember { mutableStateOf(PressureAdvanceChoice()) }
    var flowRate by remember { mutableStateOf(false) }
    var flowRateChoice by remember { mutableStateOf(FlowRateChoice()) }
    var printerTest by remember { mutableStateOf<PrinterTest?>(null) }
    var calibrationPrinter by remember { mutableStateOf<CalibrationPrinterOutcome?>(null) }
    // Plater::priv::update_title_dirty_status()
    val title = (if (dirty) "*" else "") + (name ?: orcaString("Untitled"))
    OrcaSidebarTitle(title, DesignR.drawable.orca_open_project) {
        OrcaIconButton(
            icon = DesignR.drawable.orca_save,
            contentDescription = orcaString("Save Project"),
            onClick = actions.save,
            enabled = canSave,
        )
        Box {
            OrcaIconButton(
                icon = DesignR.drawable.orca_calib_sf,
                contentDescription = orcaString("Calibration"),
                onClick = { calibrationMenu = true },
            )
            OrcaContextMenu(expanded = calibrationMenu, position = IntOffset.Zero, onDismissRequest = { calibrationMenu = false }) {
                CalibrationMenuItems(
                    enabled = canSave,
                    dismiss = { calibrationMenu = false },
                    onTemperature = { temperature = true },
                    onRange = { rangeTest = it },
                    onPressureAdvance = { pressureAdvance = true },
                    onFlowRate = { flowRate = true },
                    onPrinterTest = { printerTest = it },
                )
            }
        }
        Box {
            OrcaIconButton(
                icon = DesignR.drawable.orca_drop_down,
                contentDescription = orcaString("File"),
                onClick = { fileMenu = true },
            )
            OrcaContextMenu(expanded = fileMenu, position = IntOffset.Zero, onDismissRequest = { fileMenu = false }) {
                OrcaMenuItem(
                    text = orcaString("New Project"),
                    onClick = {
                        fileMenu = false
                        actions.new()
                    },
                    enabled = canSave,
                )
                OrcaMenuItem(
                    text = orcaString("Open Project") + "…",
                    onClick = {
                        fileMenu = false
                        actions.open()
                    },
                    enabled = canSave,
                )
                OrcaMenuSeparator()
                OrcaMenuItem(
                    text = orcaString("Save Project as"),
                    onClick = {
                        fileMenu = false
                        actions.saveAs()
                    },
                    enabled = canSave,
                )
                OrcaMenuItem(
                    text = stringResource(UiR.string.share_project),
                    onClick = {
                        fileMenu = false
                        actions.share()
                    },
                    enabled = canSave,
                )
                // MainFrame's Export menu.
                OrcaMenuSeparator()
                listOf(
                    Triple("Export all objects as one STL", MeshFormat.STL, false),
                    Triple("Export all objects as STLs", MeshFormat.STL, true),
                    Triple("Export all objects as one DRC", MeshFormat.DRC, false),
                    Triple("Export all objects as DRCs", MeshFormat.DRC, true),
                ).forEach { (label, format, multi) ->
                    OrcaMenuItem(
                        text = orcaString(label) + "…",
                        onClick = {
                            fileMenu = false
                            actions.exportMeshes(format, multi)
                        },
                        enabled = canExportModel,
                    )
                }
                OrcaMenuItem(
                    text = orcaString("Export Generic 3MF") + "…",
                    onClick = {
                        fileMenu = false
                        actions.exportGeneric()
                    },
                    enabled = canExportModel,
                )
                OrcaMenuItem(
                    text = orcaString("Export plate sliced file") + "…",
                    onClick = {
                        fileMenu = false
                        actions.exportSliced(false)
                    },
                    enabled = canExportSliced,
                )
                OrcaMenuItem(
                    text = orcaString("Export all plate sliced file") + "…",
                    onClick = {
                        fileMenu = false
                        actions.exportSliced(true)
                    },
                    enabled = canExportAllSliced,
                )
                OrcaMenuItem(
                    text = orcaString("Export toolpaths as OBJ") + "…",
                    onClick = {
                        fileMenu = false
                        actions.exportToolpaths?.invoke()
                    },
                    enabled = actions.exportToolpaths != null,
                )
            }
        }
    }
    if (temperature) {
        TemperatureCalibrationSheet(
            onDismiss = { temperature = false },
            onStart = { params ->
                temperature = false
                actions.calibrate(params)
            },
        )
    }
    if (pressureAdvance) {
        PressureAdvanceSheet(
            choice = pressureAdvanceChoice,
            onChoice = { pressureAdvanceChoice = it },
            onDismiss = { pressureAdvance = false },
            onStart = { params ->
                pressureAdvance = false
                actions.calibrate(params)
            },
        )
    }
    if (flowRate) {
        FlowRateSheet(
            choice = flowRateChoice,
            onChoice = { flowRateChoice = it },
            onDismiss = { flowRate = false },
            onStart = { test ->
                flowRate = false
                actions.calibrateFlowRate(test)
            },
        )
    }
    // The dialogs of Input Shaping and Cornering, once they read the printer.
    printerTest?.let { test ->
        LaunchedEffect(test) { calibrationPrinter = actions.describeCalibrationPrinter() }
        val close = {
            printerTest = null
            calibrationPrinter = null
        }
        val start = { params: CalibrationParams ->
            close()
            actions.calibrate(params)
        }
        when (val outcome = calibrationPrinter) {
            null -> Unit
            is CalibrationPrinterOutcome.Failure -> AlertDialog(
                onDismissRequest = close,
                confirmButton = { OrcaButton(orcaString("OK"), onClick = close) },
                text = { Text(outcome.message, style = OrcaTheme.typography.body14) },
                containerColor = OrcaTheme.colors.window,
                textContentColor = OrcaTheme.colors.text,
                shape = OrcaTheme.shapes.window,
            )
            is CalibrationPrinterOutcome.Success -> when (test) {
                PrinterTest.INPUT_SHAPING_FREQUENCY -> InputShapingFrequencySheet(outcome.printer, onDismiss = close, onStart = start)
                PrinterTest.INPUT_SHAPING_DAMPING -> InputShapingDampingSheet(outcome.printer, onDismiss = close, onStart = start)
                PrinterTest.CORNERING -> CorneringSheet(outcome.printer, onDismiss = close, onStart = start)
            }
        }
    }
    rangeTest?.let { test ->
        RangeCalibrationSheet(
            test = test,
            onDismiss = { rangeTest = null },
            onStart = { params ->
                rangeTest = null
                actions.calibrate(params)
            },
        )
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

/**
 * The type of the documents "Export as one STL/DRC" writes: any file, so the
 * document provider keeps the extension the suggested name has.
 */
private const val MESH_MIME_TYPE = "application/octet-stream"

/** The media type of a 3MF project. */
private const val PROJECT_MIME_TYPE = "model/3mf"

/** The OBJ file of "Export toolpaths as OBJ" and its materials. */
private const val OBJ_MIME_TYPE = "model/obj"
private const val MATERIALS_MIME_TYPE = "model/mtl"

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
    /** OrcaSlicer's Preferences, which its top menu opens. */
    onOpenPreferences: () -> Unit = {},
    /** A tool of the canvas opened from the sidebar: a drawer over the canvas gets out of its way. */
    onShowCanvas: () -> Unit = {},
    /** A calibration starts its project: the 3D view shows it (Plater::new_project selects tp3DEditor). */
    onShowPrepare: () -> Unit = {},
) {
    val viewModel = viewModel { createViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canvas by viewModel.canvas.collectAsStateWithLifecycle()
    // "Export as one STL/DRC" and "Replace 3D file": the object waits for the
    // document the user picks, as the desktop app waits for its file dialog.
    var meshExport by rememberSaveable { mutableStateOf<Pair<String, MeshFormat>?>(null) }
    val meshExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MESH_MIME_TYPE)) { uri ->
        val target = meshExport
        meshExport = null
        if (uri != null && target != null) viewModel.exportMesh(ScenePath(target.first), target.second, ExternalDocumentReference(uri.toString()))
    }
    var replacingAll by rememberSaveable { mutableStateOf<Pair<String, Int>?>(null) }
    val replacementFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = replacingAll
        replacingAll = null
        if (uri != null && target != null) {
            viewModel.replaceAllVolumes(PlateInstanceId(ScenePath(target.first), target.second), ExternalDocumentReference(uri.toString()))
        }
    }
    val selectionFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.replaceAllInSelection(ExternalDocumentReference(uri.toString()))
    }
    // The plate menu's "Replace all with 3D files" (Plater::priv::replace_all_with_stl of a plate item).
    var replacingPlate by rememberSaveable { mutableStateOf<Int?>(null) }
    val plateFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val index = replacingPlate
        replacingPlate = null
        if (uri != null && index != null) viewModel.replaceAllOnPlate(index, ExternalDocumentReference(uri.toString()))
    }
    // ObjectList::load_subobject()'s file dialog (GUI_App::import_model), which takes several files at once.
    var loadingPart by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    val partPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = loadingPart
        loadingPart = null
        if (uris.isNotEmpty() && target != null) {
            viewModel.loadPart(ScenePath(target.first), VolumeType.valueOf(target.second), uris.map { ExternalDocumentReference(it.toString()) })
        }
    }
    var replacing by rememberSaveable { mutableStateOf<Triple<String, Int, Int>?>(null) }
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = replacing
        replacing = null
        if (uri != null && target != null) {
            viewModel.replaceVolume(PlateInstanceId(ScenePath(target.first), target.second), target.third, ExternalDocumentReference(uri.toString()))
        }
    }
    // "Save Project as" asks for the document; "Save Project" does too until
    // the project has one (Plater::save_project with saveAs).
    val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROJECT_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.saveProject(ExternalDocumentReference(uri.toString()))
    }
    val untitled = orcaString("Untitled")
    val saveProjectAs = { projectPicker.launch((state.projectName ?: untitled) + ".3mf") }
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val shareProject: () -> Unit = {
        shareScope.launch { viewModel.shareProject(state.projectName ?: untitled)?.let { context.shareDocument(it, PROJECT_MIME_TYPE) } }
    }
    // Plater::export_gcode_3mf()'s file dialog ("Save Sliced file as:"), for the current plate or all.
    var exportingAll by rememberSaveable { mutableStateOf(false) }
    val slicedPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROJECT_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportSliced(ExternalDocumentReference(uri.toString()), exportingAll)
    }
    val exportSliced: (Boolean) -> Unit = { all ->
        exportingAll = all
        slicedPicker.launch(viewModel.slicedName() ?: ((state.projectName ?: untitled) + ".gcode.3mf"))
    }
    // Plater::export_stl()'s file dialog for every object merged, or its folder dialog for a file each.
    var meshesFormat by rememberSaveable { mutableStateOf(MeshFormat.STL) }
    var meshesFromSelection by rememberSaveable { mutableStateOf(false) }
    val meshesPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MESH_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportAllMeshes(meshesFormat, ExternalDocumentReference(uri.toString()), meshesFromSelection)
    }
    val meshesFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.exportEachMesh(meshesFormat, ExternalDocumentReference(uri.toString()), meshesFromSelection)
    }
    val exportMeshes: (MeshFormat, Boolean) -> Unit = { format, multi ->
        meshesFormat = format
        meshesFromSelection = false
        if (multi) meshesFolderPicker.launch(null) else meshesPicker.launch(viewModel.allMeshesName(format, untitled))
    }
    // The multi-selection menu's "Export as one STL" and "Export as STLs", into the same pickers.
    val exportSelection: (MeshFormat, Boolean) -> Unit = { format, multi ->
        meshesFormat = format
        meshesFromSelection = true
        if (multi) meshesFolderPicker.launch(null) else meshesPicker.launch(viewModel.allMeshesName(format, untitled))
    }
    // Plater::export_core_3mf(): get_export_file(FT_3MF), named after the project.
    val genericPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PROJECT_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportGeneric(ExternalDocumentReference(uri.toString()))
    }
    val exportGeneric = { genericPicker.launch((state.projectName ?: untitled) + ".3mf") }
    // "Export toolpaths as OBJ": the preview's toolpaths go into the OBJ file
    // the user picks, and their materials into a second document beside it,
    // as a phone's picker grants the one document picked.
    val toolpathsWriter = LocalToolpathsExport.current.value
    var toolpathsExported by remember { mutableStateOf<Boolean?>(null) }
    var materials by remember { mutableStateOf<ExportToolpathsUseCase.Materials?>(null) }
    val materialsPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MATERIALS_MIME_TYPE)) { uri ->
        val waiting = materials ?: return@rememberLauncherForActivityResult
        materials = null
        shareScope.launch {
            toolpathsExported = viewModel.saveToolpathMaterials(waiting, uri?.let { ExternalDocumentReference(it.toString()) })
        }
    }
    val toolpathsPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(OBJ_MIME_TYPE)) { uri ->
        val write = toolpathsWriter
        if (uri != null && write != null) {
            shareScope.launch {
                val written = viewModel.exportToolpaths(ExternalDocumentReference(uri.toString()), untitled, write)
                if (written == null) toolpathsExported = false else materials = written
            }
        }
    }
    materials?.let { waiting ->
        ToolpathMaterialsDialog(
            name = waiting.name,
            onSave = { materialsPicker.launch(waiting.name) },
            onSkip = {
                materials = null
                shareScope.launch { viewModel.saveToolpathMaterials(waiting, null) }
            },
        )
    }
    toolpathsExported?.let { written ->
        ExportResultDialog(orcaString("Export toolpaths as OBJ"), written, stringResource(UiR.string.toolpaths_exported), onDismiss = { toolpathsExported = null })
    }
    // Open Project's file dialog (GUI_App::load_project).
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openProject(ExternalDocumentReference(uri.toString()))
    }
    // The plate menu's Add Models (Plater::add_file), whose file dialog takes several files at once.
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addModels(uris.map { ExternalDocumentReference(it.toString()) })
    }
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
            selection = viewModel.selectionActions(openSettings = {}, replaceAll = { selectionFolderPicker.launch(null) }, export = exportSelection),
            select = viewModel::chooseSettingsTarget,
            selectAlone = { viewModel.chooseSettingsTarget(it, add = false) },
            selectSettings = viewModel::openSettingsOf,
            setPrintable = viewModel::setObjectPrintable,
            setAutoDrop = viewModel::setObjectAutoDrop,
            addInstance = viewModel::addInstance,
            removeInstance = viewModel::removeInstance,
            setNumberOfInstances = viewModel::setInstances,
            removeCopy = viewModel::removeObjectCopy,
            fillBed = viewModel::fillBed,
            setAsIndividual = viewModel::setAsIndividual,
            clone = viewModel::clone,
            copyObjects = viewModel::copyObjects,
            copyVolumes = viewModel::copyVolumes,
            paste = viewModel::paste,
            manipulate = viewModel::manipulate,
            rename = viewModel::rename,
            renamePart = viewModel::renamePart,
            editObject = viewModel::editObject,
            addPart = viewModel::addPart,
            loadPart = { mesh, type ->
                loadingPart = mesh.value to type.name
                partPicker.launch(arrayOf("*/*"))
            },
            removePart = viewModel::removePart,
            invalidateCutInfo = viewModel::invalidateCutInfoOf,
            selectConnectors = viewModel::selectCutConnectors,
            deleteConnectors = viewModel::deleteCutConnectors,
            setConnectorsExtruder = viewModel::setConnectorsExtruder,
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
            setObjectPrintable = viewModel::setWholeObjectPrintable,
            editLayers = viewModel::editLayersOf,
            toggleFlushOption = viewModel::toggleFlushOption,
            editProcessSettings = viewModel::editProcessSettings,
            copyProcessSettings = viewModel::copyProcessSettings,
            copyRange = viewModel::copyRange,
            canMoveObject = viewModel::canMoveObject,
            moveObject = viewModel::moveObject,
            moveVolume = viewModel::moveVolume,
            copyRanges = viewModel::copyRanges,
            focusRangeField = viewModel::focusRangeField,
            pasteProcessSettings = viewModel::pasteProcessSettings,
            replaceVolume = { copy, volume ->
                replacing = Triple(copy.mesh.value, copy.instance, volume)
                replacementPicker.launch(arrayOf("*/*"))
            },
            replaceAllVolumes = { copy ->
                replacingAll = copy.mesh.value to copy.instance
                replacementFolderPicker.launch(null)
            },
            reloadFromDisk = viewModel::reloadFromDisk,
            reloadAll = viewModel::reloadAll,
            exportObject = { mesh, format, name ->
                meshExport = mesh.value to format
                meshExportPicker.launch(exportFileName(name, format))
            },
            simplifyObject = { mesh ->
                viewModel.simplifyObject(mesh)
                onShowCanvas()
            },
            simplifyVolume = { id ->
                viewModel.simplifyVolume(id)
                onShowCanvas()
            },
            // The text tool is a window of the canvas.
            editText = { id ->
                viewModel.editTextOf(id)
                onShowCanvas()
            },
            addText = { mesh, type ->
                viewModel.addTextTo(mesh, type)
                onShowCanvas()
            },
            editSvg = { id ->
                viewModel.editSvgOf(id)
                onShowCanvas()
            },
            addSvg = { mesh, type ->
                viewModel.addSvgTo(mesh, type)
                onShowCanvas()
            },
            changeVolumeType = viewModel::setVolumeType,
            selectPlate = viewModel::choosePlate,
            selectPlateSettings = viewModel::openPlateSettings,
            replaceAllOnPlate = { index ->
                replacingPlate = index
                plateFolderPicker.launch(null)
            },
            selectPlateObjects = viewModel::selectPlateObjects,
            selectAllPlates = viewModel::selectAllPlates,
            deletePlateObjects = viewModel::deletePlateObjects,
            arrangePlate = viewModel::arrangePlate,
            orientPlate = viewModel::orientPlate,
            deletePlate = viewModel::removePlate,
            lockPlate = viewModel::togglePlateLock,
            renamePlate = viewModel::setPlateName,
            addPrimitive = viewModel::addShape,
            addHandyModel = viewModel::addHandyModel,
            addModels = { modelPicker.launch(arrayOf("*/*")) },
        ),
        onOpenWizard = onOpenWizard,
        onOpenSettings = onOpenSettings,
        onOpenSetting = onOpenSetting,
        searchCatalog = viewModel::searchCatalog,
        onOpenAbout = onOpenAbout,
        onOpenPreferences = onOpenPreferences,
        autoArrange = canvas.autoArrange,
        onImportConfig = { configPicker.launch(arrayOf("*/*")) },
        onExportConfig = { exporting = true },
        project = ProjectActions(
            save = { if (viewModel.projectNeedsDocument) saveProjectAs() else viewModel.saveProject() },
            saveAs = saveProjectAs,
            new = viewModel::newProject,
            share = shareProject,
            exportSliced = exportSliced,
            exportMeshes = exportMeshes,
            exportGeneric = exportGeneric,
            exportToolpaths = toolpathsWriter?.let { { toolpathsPicker.launch(viewModel.toolpathsName(untitled)) } },
            open = { openPicker.launch(arrayOf("*/*")) },
            calibrate = { params ->
                viewModel.calibrate(params)
                onShowPrepare()
            },
            calibrateFlowRate = { test ->
                viewModel.calibrateFlowRate(test)
                onShowPrepare()
            },
            describeCalibrationPrinter = viewModel::describeCalibrationPrinter,
        ),
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
            bedShapeFiles = viewModel.bedFiles,
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
            check = viewModel::checkPrinterPage,
            create = viewModel::createPrinter,
            keepBedFile = viewModel::keepPrinterBedFile,
            answer = viewModel::answerPrinterQuestion,
            question = viewModel.printerQuestion,
            message = viewModel.printerMessage,
            dismissMessage = viewModel::dismissPrinterMessage,
            page = viewModel.printerPage,
            returnToFirstPage = viewModel::returnToPrinterPage,
            creating = viewModel.creatingPrinter,
            created = viewModel.printerCreated,
            dismissCreated = viewModel::dismissPrinterCreated,
            open = viewModel::openCreatePrinter,
            close = viewModel::closeCreatePrinter,
        ),
        network = NetworkPrinterActions(
            load = viewModel::printerConnection,
            checkName = viewModel::checkPrinterName,
            save = viewModel::savePrinterConnection,
            test = viewModel::testNetworkPrinter,
            lookup = viewModel::lookupPrintHosts,
            scanCreality = viewModel::scanCrealityPrinters,
            printers = viewModel::hostPrinters,
            discoverFlashforge = viewModel::discoverFlashforge,
            cloudLogin = viewModel::cloudLogin,
            loggedIn = viewModel::cloudLoggedIn,
            logOut = viewModel::cloudLogOut,
            keepCaFile = viewModel::keepCaFile,
        ),
    )
}

/**
 * PhysicalPrinterDialog as the sidebar opens it: the printer's host on its
 * edited preset, and the preset saved with it.
 */
internal class NetworkPrinterActions(
    val load: suspend () -> PrinterConnectionOutcome,
    val checkName: suspend (String) -> PresetNameOutcome,
    val save: (ModelSettings, String) -> Unit,
    val test: suspend (PhysicalPrinter) -> PrintHostTestOutcome,
    val lookup: () -> Flow<List<BonjourReply>>,
    val scanCreality: suspend () -> List<CrealityHost>,
    val printers: suspend (PhysicalPrinter) -> HostPrintersOutcome,
    val discoverFlashforge: suspend () -> FlashforgeDiscoveryOutcome,
    val cloudLogin: suspend (PhysicalPrinter, (String) -> Unit) -> CloudLoginOutcome,
    val loggedIn: suspend (PhysicalPrinter) -> Boolean,
    val logOut: suspend (PhysicalPrinter) -> Unit,
    /** The HTTPS CA file the Browse button picked, kept for the preset. */
    val keepCaFile: suspend (ExternalDocumentReference) -> String? = { null },
) {
    companion object {
        val NONE = NetworkPrinterActions(
            load = { PrinterConnectionOutcome.Failure("") },
            checkName = { PresetNameOutcome.Failure("") },
            save = { _, _ -> },
            test = { PrintHostTestOutcome.Failure("") },
            lookup = { emptyFlow() },
            scanCreality = { emptyList() },
            printers = { HostPrintersOutcome.Success(emptyList()) },
            discoverFlashforge = { FlashforgeDiscoveryOutcome.Failure("") },
            cloudLogin = { _, _ -> CloudLoginOutcome.Failure("") },
            loggedIn = { false },
            logOut = {},
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
    onOpenPreferences: () -> Unit = {},
    /** The Preferences' "Auto arrange plate after cloning", which the clone dialog starts with. */
    autoArrange: Boolean = true,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
    settings: SettingsActions,
    presetChange: PresetChangeActions,
    filaments: FilamentActions = FilamentActions.NONE,
    comparison: PresetComparisonActions = PresetComparisonActions.NONE,
    printers: CustomPrinterActions = CustomPrinterActions.NONE,
    network: NetworkPrinterActions = NetworkPrinterActions.NONE,
    project: ProjectActions = ProjectActions.NONE,
) {
    // DiffPresetDialog, which the compare button of the process panel opens.
    var comparing by rememberSaveable { mutableStateOf(false) }
    // Search::SearchDialog, which the search button of the panel opens.
    var searching by rememberSaveable { mutableStateOf(false) }
    // PhysicalPrinterDialog, which the Connection button of the printer opens
    // once Android 17 has asked for the local network its Test and Browse reach.
    var openNetworkPrinters by rememberSaveable { mutableStateOf(false) }
    var localNetworkDenied by rememberSaveable { mutableStateOf(false) }
    val openConnection = rememberLocalNetworkAccess { denied ->
        localNetworkDenied = denied
        openNetworkPrinters = true
    }
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
    // The drag of a row of the object list onto another, which its rows share.
    val rowDrag = remember { ObjectListDrag() }
    // Plater::set_number_of_copies() and ObjectList::rename_item() ask first.
    var askingCopies by remember { mutableStateOf<ScenePath?>(null) }
    var cloning by remember { mutableStateOf<ScenePath?>(null) }
    var renaming by remember { mutableStateOf<RenameRequest?>(null) }
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
    // "Change Filament" names every filament after its preset, as the combo box shows it.
    val menuFilaments = presets?.selection?.allFilaments.orEmpty().mapIndexed { index, id ->
        MenuFilament(presets?.filaments.orEmpty().labelOf(id.value), filamentColors[index])
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window),
    ) {
        item(key = "project") {
            ProjectTitle(state.projectName, state.projectDirty, state.canSaveProject, project, state.canExportSliced, state.canExportAllSliced, state.canExportModel)
        }

        item(key = "printer") {
        OrcaSidebarTitle(stringResource(R.string.section_printer), DesignR.drawable.orca_printer) {
            // Plater's m_printer_connect: the printers of the network the app
            // sends the sliced G-code to.
            OrcaIconButton(
                icon = DesignR.drawable.orca_monitor_signal_strong,
                contentDescription = orcaString("Connection"),
                onClick = openConnection,
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
                        // PlaterPresetComboBox of the slot shows the preset of that slot; the list
                        // marks the first slot's, which the filament tab edits.
                        text = presets?.filaments?.let { list -> if (index == 0) list.selectedLabel(slot.value) else list.labelOf(slot.value) }.orEmpty(),
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
            drag = rowDrag,
            picking = picking,
            filaments = filamentColors,
            menuFilaments = menuFilaments,
            actions = objectList,
            onChooseShape = { mesh, type -> addingPart = mesh to type },
            // The desktop app edits the heights of the selected range.
            onEditRange = {
                objectList.selectRange(it)
                editingRange = it
            },
            onAskNumberOfInstances = { askingCopies = it },
            onAskClone = { cloning = it },
            onAskRename = { renaming = it },
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
        // The top menu's Preferences (MainFrame's ConfigMenuPreferences).
        SidebarAction(DesignR.drawable.orca_cog, orcaString("Preferences"), onOpenPreferences)
        SidebarAction(DesignR.drawable.orca_help, stringResource(R.string.about), onOpenAbout)
        }
    }

    SettingsTabDialogs(tab)

    if (comparing) {
        DiffPresetDialog(comparison, onDismiss = { comparing = false })
    }

    if (openNetworkPrinters) {
        PrinterConnectionSheet(
            load = network.load,
            checkName = network.checkName,
            onSave = { settings, name ->
                openNetworkPrinters = false
                network.save(settings, name)
            },
            onDismiss = { openNetworkPrinters = false },
            onTest = network.test,
            lookup = network.lookup,
            scanCreality = network.scanCreality,
            loadPrinters = network.printers,
            discoverFlashforge = network.discoverFlashforge,
            cloudLogin = network.cloudLogin,
            cloudLoggedIn = network.loggedIn,
            cloudLogOut = network.logOut,
            notice = if (localNetworkDenied) stringResource(UiR.string.printer_host_local_network) else null,
            keepCaFile = network.keepCaFile,
        )
    }
    // CreatePrinterPresetDialog, which the printer list opens. It closes itself
    // once the printer is made, so a question keeps the filled-in pages.
    if (printers.creating) {
        CreatePrinterDialog(printers)
    }
    printers.question?.let { question ->
        SettingsQuestionDialog(question, onAnswer = printers.answer)
    }
    // Sidebar::create_printer_preset(): "Printer Setting" opens the printer's tab.
    if (printers.created) {
        CreatePresetSuccessfulDialog(
            printer = true,
            onOk = {
                printers.dismissCreated()
                onOpenSettings(PresetKind.PRINTER)
            },
            onDismiss = printers.dismissCreated,
        )
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
                onFocus = objectList.focusRangeField,
                onDismiss = { editingRange = null },
                onApply = { bottom, top ->
                    editingRange = null
                    objectList.editRange(id, bottom, top)
                },
            )
        }
    }

    askingCopies?.let { mesh ->
        val target = state.objects.firstOrNull { it.mesh == mesh }
        if (target == null) {
            askingCopies = null
        } else {
            NumberOfInstancesDialog(
                current = target.instances.size,
                onDismiss = { askingCopies = null },
                onConfirm = { number ->
                    askingCopies = null
                    objectList.setNumberOfInstances(mesh, number)
                },
            )
        }
    }
    cloning?.let { mesh ->
        CloneDialog(
            autoArrange = autoArrange,
            onDismiss = { cloning = null },
            onFill = {
                cloning = null
                objectList.fillBed(mesh)
            },
            onClone = { count, arrange ->
                cloning = null
                objectList.clone(mesh, count, arrange)
            },
        )
    }
    renaming?.let { request ->
        RenameDialog(
            name = request.name,
            onDismiss = { renaming = null },
            onRename = { name ->
                renaming = null
                when (request) {
                    is RenameRequest.Object -> objectList.rename(request.mesh, name)
                    is RenameRequest.Volume -> objectList.renamePart(request.id, name)
                    is RenameRequest.Plate -> objectList.renamePlate(request.index, name)
                }
            },
        )
    }

    // The submenu of shapes of the desktop app, as a sheet.
    addingPart?.let { (mesh, type) ->
        PartShapeSheet(
            type = type,
            onDismiss = { addingPart = null },
            onChoose = { shape, name ->
                addingPart = null
                objectList.addPart(mesh, shape, type, name)
            },
            onText = {
                addingPart = null
                objectList.addText(mesh, type)
            },
            onSvg = {
                addingPart = null
                objectList.addSvg(mesh, type)
            },
            onLoad = {
                addingPart = null
                objectList.loadPart(mesh, type)
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
                // The list of a slot marks the preset of that slot.
                items = presets.selection.allFilaments.getOrNull(editingSlot)?.takeIf { editingSlot > 0 }?.let { slot ->
                    presets.filaments.map { it.copy(selected = it.name == slot.value) }
                } ?: presets.filaments,
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

private fun List<PresetListItem>.labelOf(name: String): String = firstOrNull { it.name == name }?.label ?: name

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
            addInstance = {},
            removeInstance = {},
            setNumberOfInstances = { _, _ -> },
            removeCopy = {},
            fillBed = {},
            setAsIndividual = { _, _ -> },
            clone = { _, _, _ -> },
            copyObjects = { _, _ -> },
            copyVolumes = { _, _, _ -> },
            paste = {},
            manipulate = { _, _ -> },
            rename = { _, _ -> },
            renamePart = { _, _ -> },
            editObject = { _, _, _ -> },
            addPart = { _, _, _, _ -> },
            removePart = {},
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
            setBedShape = {},
            gcodePlaceholders = { _, _ -> GcodePlaceholdersOutcome.Failure("preview") },
            gcodePlaceholder = { _, _ -> GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true) },
        ),
        presetChange = PresetChangeActions(resolve = {}, save = {}, cancel = {}),
    )
}
