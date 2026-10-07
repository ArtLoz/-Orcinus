package app.orcinus.shadow.feature.sidebar

import android.content.res.Configuration
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.BedFileOutcome
import app.orcinus.shadow.core.model.BedTypeChoice
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.core.model.ListClipboard
import app.orcinus.shadow.core.model.PresetBundlesOutcome
import app.orcinus.shadow.core.model.PresetSave
import app.orcinus.shadow.core.model.allSliceResultsReady
import app.orcinus.shadow.core.model.selectedCopies
import app.orcinus.shadow.core.ui.ExportResultDialog
import app.orcinus.shadow.core.ui.LocalToolpathsExport
import app.orcinus.shadow.core.ui.shortcuts.LocalKeyboardShortcuts
import app.orcinus.shadow.core.ui.plate.DailyTipsWindow
import app.orcinus.shadow.core.ui.plate.NameErrorDialog
import app.orcinus.shadow.core.ui.plate.SelectionMenuActions
import app.orcinus.shadow.core.ui.plate.VolumesMenuActions
import app.orcinus.shadow.core.ui.plate.SelectionMenuState
import app.orcinus.shadow.core.ui.plate.selectionMenuState
import app.orcinus.shadow.core.ui.plate.selectsSeveralObjects
import app.orcinus.shadow.core.ui.settings.BedShapeFileActions
import app.orcinus.shadow.core.ui.settings.CreatePresetSuccessfulDialog
import app.orcinus.shadow.core.ui.settings.PresetBundleDialog
import app.orcinus.shadow.core.ui.shareDocument
import app.orcinus.shadow.domain.plate.BedShapeFilesUseCase
import app.orcinus.shadow.domain.plate.CopyLayerRangesUseCase
import app.orcinus.shadow.domain.plate.CutConnectorsUseCase
import app.orcinus.shadow.domain.plate.EditLayerHeightsUseCase
import app.orcinus.shadow.domain.plate.ExportGcodeUseCase
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarSection
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarTitle
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.OrcaDialogWidth
import app.orcinus.shadow.core.designsystem.component.OrcaPickerAnchor
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.component.orcaPickerAnchor
import app.orcinus.shadow.core.designsystem.component.rememberOrcaPickerAnchor
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
import app.orcinus.shadow.core.model.DialogIcon
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
import app.orcinus.shadow.core.model.ShortcutAction
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
import app.orcinus.shadow.core.ui.settings.SettingsNoticeDialog
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
import app.orcinus.shadow.domain.plate.PresetBundlesUseCase
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
import app.orcinus.shadow.domain.plate.VolumeMenuUseCase
import app.orcinus.shadow.domain.plate.canDeletePlate
import app.orcinus.shadow.domain.plate.canMoveObject
import app.orcinus.shadow.domain.preferences.AppPreferences
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.ui.plate.PlateNameDialog
import app.orcinus.shadow.domain.plate.PickListItemUseCase
import app.orcinus.shadow.domain.plate.ObjectListColumnsUseCase
import app.orcinus.shadow.domain.plate.CanvasRequestsUseCase
import app.orcinus.shadow.domain.plate.paintingsOf
import app.orcinus.shadow.core.model.AppConfigKeys
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

data class SidebarUiState(
    val engine: EngineState,
    /** Null until the engine reported the presets. */
    val presets: Presets? = null,
    /** PlateState.flushVolumesModified: the flushing button's mark. */
    val flushVolumesModified: Boolean = false,
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
    /** PlateState.selectedParts(): the selected volumes of one object, [selectedPart] alone or several. */
    val selectedParts: List<ObjectPartId> = emptyList(),
    /** The height range whose settings are shown, when one is selected. */
    val selectedRange: LayerRangeId? = null,
    /** PlateState.selectedRanges(): the selected ranges of one object, [selectedRange] alone or several. */
    val selectedRanges: List<LayerRangeId> = emptyList(),
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
    /**
     * MainFrame::can_save_as(): the project can be saved, not while the plate
     * shows a G-code file or an exported file; a slice going on does not stop
     * it, as nothing but a slice runs beside the desktop app's menus.
     */
    val canSaveProject: Boolean = false,
    /**
     * MainFrame::can_start_new_project() and can_open_project(): New Project
     * and Open Project, which wait for no slice, and the calibrations, which
     * start a project of their own.
     */
    val canStartProject: Boolean = false,
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
    /** The kinds of paint on each object, by its mesh, as the object list's columns show them. */
    val paintedKinds: Map<ScenePath, Set<PaintKind>> = emptyMap(),
    /** PlateState.objectProcessHints: each change blinks the arrow at the switch of the settings. */
    val objectProcessHints: Int = 0,
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
    private val presetBundles: PresetBundlesUseCase,
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
    private val volumeMenu: VolumeMenuUseCase,
    private val changeVolumeType: ChangeVolumeTypeUseCase,
    private val replaceAllVolumesUseCase: ReplaceAllVolumesUseCase,
    private val reloadFromDiskUseCase: ReloadFromDiskUseCase,
    private val saveProject: SaveProjectUseCase,
    private val exportGcode: ExportGcodeUseCase,
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
    private val pickListItem: PickListItemUseCase,
    private val objectColumns: ObjectListColumnsUseCase,
    private val canvasRequests: CanvasRequestsUseCase,
    private val preferences: AppPreferences,
) : ViewModel() {
    /** What the Preferences change on the clone dialog. */
    val canvas: StateFlow<CanvasPreferences> = preferences.canvas

    /** Sidebar::update_filaments_counter()'s filaments_area_preferred_count. */
    val filamentsPreferredCount: StateFlow<Int> = preferences.values
        .map { AppConfigKeys.filamentsAreaPreferredCount(it[AppConfigKeys.FILAMENTS_AREA_PREFERRED_COUNT]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfigKeys.filamentsAreaPreferredCount(null))

    /** The variable layer height mark of an object's row: the bar opens on the object. */
    fun editLayersOf(mesh: ScenePath) = editLayerHeights.enableFor(mesh)

    /** A row picked while several are being picked (ObjectList::fix_multiselection_conflicts()). */
    fun pickCopy(id: PlateInstanceId, copyRow: Boolean) = pickListItem.copy(id, copyRow)

    fun pickPart(id: ObjectPartId) = pickListItem.part(id)

    fun pickRange(id: LayerRangeId) = pickListItem.range(id)

    /** The paint columns of an object's row: the tool of the kind opens on the object, or closes. */
    fun paint(mesh: ScenePath, kind: PaintKind) = canvasRequests.paint(mesh, kind)

    /** The sinking column of an object's row: "Shift objects to bed". */
    fun shiftToBed(mesh: ScenePath) = objectColumns.shiftToBed(mesh)

    /** A row activated by a second tap: the 3D view frames the selection. */
    fun zoomToSelection() = canvasRequests.zoomToSelection()

    /** "Text" and "SVG" of the plate menu's "Add Primitive": objects of their own, which the canvas places. */
    fun addTextObject() = requestEmboss.addObject(EmbossKind.TEXT)

    fun addSvgObject() = requestEmboss.addObject(EmbossKind.SVG)

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
    fun replaceAllInSelection(folder: ExternalDocumentReference) {
        // Several volumes of one object: those volumes alone.
        val parts = plateState.value.selectedParts().takeIf { it.size > 1 }
        if (parts != null) {
            replaceAllVolumesUseCase.volumes(parts.first().mesh, parts.map(ObjectPartId::index), folder)
        } else {
            replaceAllVolumesUseCase.selected(folder)
        }
    }

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

    fun slicedNameError(): String? = saveProject.slicedNameError()

    /** The name "Save Project as" and "Export Generic 3MF" offer (get_export_file(FT_3MF)). */
    fun projectFileName(untitled: String): String = saveProject.projectFileName(untitled)

    /** The File menu's "Export G-code": the name filename_format made, why it could not, and the export. */
    fun gcodeName(): String = exportGcode.suggestedName() ?: "plate.gcode"

    fun gcodeNameError(): String? = exportGcode.nameError()

    fun exportGcode(document: ExternalDocumentReference) {
        viewModelScope.launch { exportGcode.invoke(document) }
    }

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
            setSettingsScope.objectProcess()
            openSettings()
        },
        pasteProcessSettings = { pasteSettings.selected() },
        setFilament = { setExtruder.selected(it) },
        replaceAll = replaceAll,
        export = export,
    )

    /** The multi-selection menu's items over the selected volumes of one object. */
    fun volumesActions(openSettings: () -> Unit, replaceAll: () -> Unit = {}) = VolumesMenuActions(
        // The list picks the volumes over the object's first copy.
        center = {
            val parts = plateState.value.selectedParts()
            parts.firstOrNull()?.let { volumeMenu.centerAll(PlateInstanceId(it.mesh, 0), parts.map(ObjectPartId::index)) }
        },
        drop = {
            val parts = plateState.value.selectedParts()
            parts.firstOrNull()?.let { volumeMenu.dropAll(PlateInstanceId(it.mesh, 0), parts.map(ObjectPartId::index)) }
        },
        delete = {
            val parts = plateState.value.selectedParts()
            // Plater::remove_selected(): the own mesh goes in the same step of Undo.
            val removed = removeObjectPart.all(parts)
            if (removed.ownVolume) editPlateObject.deleteOwnVolume(parts.first().mesh, joined = removed.recorded)
        },
        edit = { edit ->
            val parts = plateState.value.selectedParts()
            parts.firstOrNull()?.let { editPlateObject.volumes(it.mesh, edit, parts.map(ObjectPartId::index)) }
        },
        replaceAll = replaceAll,
        editProcessSettings = {
            setSettingsScope.objectProcess()
            openSettings()
        },
        pasteProcessSettings = { pasteSettings.selected() },
        changeType = { changeVolumeType.all(plateState.value.selectedParts(), it) },
        setFilament = { setExtruder.selected(it) },
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

    /** ObjectList::update_info_items(): the kinds of paint on each object, read off its paintings as they change. */
    private val paintedKinds: Flow<Map<ScenePath, Set<PaintKind>>> = observePlate()
        .map { paintingsOf(it.objects) }
        .distinctUntilChanged()
        .map { objectColumns.paintedKinds(it) }

    val state: StateFlow<SidebarUiState> = observePlate()
        .map(PlateState::toSidebarUiState)
        .combine(paintedKinds.onStart { emit(emptyMap()) }) { state, kinds -> state.copy(paintedKinds = kinds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toSidebarUiState())

    fun choose(choice: PresetChoice) = selectPreset(choice)

    fun chooseBedType(value: String) = selectPreset.selectBedType(value)

    fun chooseSettingsScope(scope: SettingsScope) = setSettingsScope(scope)

    /** The filaments of the plate, as OrcaSlicer's sidebar keeps them. */
    fun addFilament() = plateFilaments.add()

    fun removeFilament(index: Int) = plateFilaments.remove(index)

    /** "Merge with" of a slot's menu (Sidebar::change_filament()): [index] leaves, and [into] takes its objects. */
    fun mergeFilament(index: Int, into: Int) = plateFilaments.remove(index, into)

    /** "Edit" of a slot (Sidebar::edit_filament()): the filament tab edits that slot. */
    fun editFilament(index: Int) = selectPreset.editFilament(index)

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

    /** A row of a part tapped while several items are being picked. */

    fun openSettingsOfPart(id: ObjectPartId) {
        selectObjectPart(id)
        setSettingsScope(SettingsScope.OBJECT)
    }

    /**
     * The height ranges of an object, which the object list edits: a range
     * after the one [after], or with none ObjectList::layers_editing() of the
     * "Height range Modifier" item and the "Layers" row.
     */
    fun addRange(mesh: ScenePath, after: LayerRangeId?) {
        viewModelScope.launch {
            val added = (if (after == null) addLayerRange.layersEditing(mesh) else addLayerRange(mesh, after)) ?: return@launch
            selectLayerRange(added)
            setSettingsScope(SettingsScope.OBJECT)
        }
    }

    fun removeRange(id: LayerRangeId) = removeLayerRange(id)

    /** ObjectList::remove() of the ranges selected together, and del_layers_from_object() of the "Layers" row. */
    fun removeSelectedRanges() = removeLayerRange.selected()

    fun removeAllRanges(mesh: ScenePath) = removeLayerRange.all(mesh)

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

    fun copySelectedRanges() = copyLayerRanges.selected()

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

    /** How many files the import was given, which its result counts (MainFrame::load_config_file()). */
    val configImportFiles: Int get() = importDocuments.size

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

    /** PresetBundleDialog: the bundles it lists. */
    suspend fun listPresetBundles(): PresetBundlesOutcome = presetBundles.list()

    /** Its "Delete bundle", which runs on if the dialog closes meanwhile. */
    suspend fun deletePresetBundle(id: String): PresetBundlesOutcome = viewModelScope.async { presetBundles.delete(id) }.await()

    /** The settings row of the object list: the item and its settings are shown. */
    fun openSettingsOf(id: PlateInstanceId?) {
        selectPlateObject(id)
        setSettingsScope(SettingsScope.OBJECT)
    }

    fun setObjectPrintable(id: PlateInstanceId, printable: Boolean) = setPlateObjectPrintable(id, printable)

    /** The check box of an object's row: every copy of it. */
    fun setWholeObjectPrintable(mesh: ScenePath, printable: Boolean) = setPlateObjectPrintable.all(mesh, printable)

    fun toggleFlushOption(mesh: ScenePath, option: FlushOption) = setFlushOption(mesh, option)

    /** ObjectList::switch_to_object_process(): the item is selected and its own settings are shown, with the tip of it. */
    fun editProcessSettings(item: SettingsItem) {
        when (item) {
            is SettingsItem.Object -> selectPlateObject(PlateInstanceId(item.mesh))
            is SettingsItem.Volume -> selectObjectPart(item.id)
            is SettingsItem.Layer -> selectLayerRange(item.id)
        }
        setSettingsScope.objectProcess()
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

    /** The part menu's Center, Drop and Mirror of the volume, over the object's first copy as the list picks it. */
    fun centerVolume(id: ObjectPartId) = volumeMenu.center(PlateInstanceId(id.mesh, 0), id.index)

    fun dropVolume(id: ObjectPartId) = volumeMenu.drop(PlateInstanceId(id.mesh, 0), id.index)

    fun mirrorVolume(id: ObjectPartId, axis: Axis) = volumeMenu.mirror(PlateInstanceId(id.mesh, 0), id.index, axis)

    /** "Change type" of a volume. */
    fun setVolumeType(id: ObjectPartId, type: VolumeType) = changeVolumeType(id, type)

    /** "Replace all with 3D files" from the folder the user picked. */
    fun replaceAllVolumes(copy: PlateInstanceId, folder: ExternalDocumentReference) = replaceAllVolumesUseCase(copy, folder)

    /** "Replace 3D file": the volume takes the mesh of the document the user picked. */
    fun replaceVolume(copy: PlateInstanceId, volume: Int, document: ExternalDocumentReference) = replaceObjectVolume(copy, volume, document)

    /**
     * replace_with_stl() and replace_all_with_stl() before their dialogs
     * open: check_gizmos_closed_except(Undefined), which says so otherwise.
     */
    fun toolsClosedForReplace(): Boolean = replaceObjectVolume.toolsClosed()

    /** Plater::reload_from_disk() of the object's volumes, or of its volume [volume]. */
    fun reloadFromDisk(mesh: ScenePath, volume: Int?) = reloadFromDiskUseCase(mesh, volume)

    /** Plater::reload_all_from_disk() */
    fun reloadAll() = reloadFromDiskUseCase.all()

    fun setObjectAutoDrop(id: PlateInstanceId, autoDrop: Boolean) = setPlateObjectAutoDrop(id, autoDrop)

    /** The Auto Drop of an object's row: every copy. */
    fun setWholeObjectAutoDrop(mesh: ScenePath, autoDrop: Boolean) = setPlateObjectAutoDrop.wholeObject(mesh, autoDrop)

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

    /** MenuFactory::instance_menu() over a copy: the selected copies of its object, or the copy alone. */
    fun setAsIndividualOver(id: PlateInstanceId) = separatePlateInstances.overCopy(id)

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

/**
 * The plate changes in a way that the desktop app runs in its menus' place
 * (a load, an edit of the object menu, a preset change), as opposed to the
 * background slice, which leaves its File menu working.
 */
private val PlateState.busyBesideSlice: Boolean get() = copy(slicing = null).busy

private fun PlateState.toSidebarUiState() = SidebarUiState(
    engine = engine,
    presets = presets,
    flushVolumesModified = flushVolumesModified,
    filamentColor = plate?.filamentColor,
    canChoose = profiles != null && !busy && objects.none(PlateObject::placing),
    changing = changingPresets,
    processSettings = settingsTabs[PresetKind.PRINT] ?: SettingsTabState(PresetKind.PRINT),
    presetChange = presetChange,
    settingsScope = settingsScope,
    objects = objects,
    selectedInstances = selectedInstances,
    selectedPart = selectedPart,
    selectedParts = selectedParts(),
    selectedRange = selectedRange,
    selectedRanges = selectedRanges(),
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
    canSaveProject = profiles != null && !busyBesideSlice && previewOnly == null,
    canStartProject = profiles != null && !busy,
    // PartPlate::is_slice_result_ready_for_export()
    canExportSliced = profiles != null && !busyBesideSlice && objects.isNotEmpty() && result?.printReady == true,
    canExportAllSliced = profiles != null && !busyBesideSlice && objects.isNotEmpty() && allSliceResultsReady(),
    canExportModel = profiles != null && !busyBesideSlice && objects.isNotEmpty(),
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
    objectProcessHints = objectProcessHints,
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
@Composable
private fun LayerRangeSheet(range: LayerRange, onFocus: (LayerRangeEditor) -> Unit, onDismiss: () -> Unit, onApply: (Double, Double) -> Unit) {
    val colors = OrcaTheme.colors
    var bottom by rememberSaveable(range) { mutableStateOf(formatHeight(range.bottom)) }
    var top by rememberSaveable(range) { mutableStateOf(formatHeight(range.top)) }
    var invalid by rememberSaveable(range) { mutableStateOf(false) }
    val apply = {
        // LayerRangeEditor::get_value(): a field left as it was keeps its height;
        // one that is no height of zero or more says so and takes its height back.
        val from = if (bottom == formatHeight(range.bottom)) range.bottom else layerRangeValue(bottom)
        val to = if (top == formatHeight(range.top)) range.top else layerRangeValue(top)
        if (from == null) bottom = formatHeight(range.bottom)
        if (to == null) top = formatHeight(range.top)
        invalid = from == null || to == null
        if (from != null && to != null) onApply(from, to)
    }
    // A small dialog of its own on a large window (OrcaSheet).
    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = OrcaDialogWidth.Small) {
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
            // show_error() of LayerRangeEditor::get_value(), which a phone shows under the fields.
            if (invalid) Text(text = orcaString("Invalid numeric."), color = colors.error, style = OrcaTheme.typography.body14)
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

/** LayerRangeEditor::get_value(): either decimal separator, "." for 0; null for no height of zero or more. */
private fun layerRangeValue(text: String): Double? {
    val value = text.replace(',', '.')
    if (value == ".") return 0.0
    return value.toDoubleOrNull()?.takeIf { it >= 0.0 }
}

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

/**
 * What the import or the export did, as the desktop app reports it:
 * MainFrame::load_config_file()'s "Import result", which counts the [files]
 * picked, and ExportConfigsDialog::show_export_result().
 */
@Composable
private fun ConfigTransferDialog(outcome: ConfigTransferOutcome, exported: Boolean, files: Int, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        title = { Text(orcaString(if (exported) "Info" else "Import result"), style = OrcaTheme.typography.head16) },
        text = {
            Column {
                when (outcome) {
                    is ConfigTransferOutcome.Failure -> Text(orcaString(outcome.message), color = colors.error, style = OrcaTheme.typography.body14)
                    // An import that still waits for an answer shows no result.
                    is ConfigTransferOutcome.Overwrite -> Unit
                    is ConfigTransferOutcome.Success -> Text(
                        text = if (exported) {
                            orcaString("Export successful")
                        } else {
                            orcaText(
                                OrcaText(
                                    "There is %d config imported. (Only non-system and compatible configs)",
                                    listOf(files.toString()),
                                    msgidPlural = "There are %d configs imported. (Only non-system and compatible configs)",
                                    count = files.toLong(),
                                ),
                            ) + if (files == 0) orcaString("\nHint: Make sure you have added the corresponding printer before importing the configs.") else ""
                        },
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
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
    /** The File menu's "Export G-code" of the current plate. */
    val exportGcode: () -> Unit = {},
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
    canStartProject: Boolean = canSave,
    /** BBLTopbar::EnableUndoRedoItems(): the Calibration button works on the Prepare tab alone. */
    prepareShown: Boolean = true,
    /** Plater::is_view3D_shown(), which every item of the Calibration menu wants. */
    view3DShown: Boolean = true,
) {
    var fileMenu by remember { mutableStateOf(false) }
    var calibrationMenu by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(false) }
    var rangeTest by remember { mutableStateOf<RangeTest?>(null) }
    var pressureAdvance by remember { mutableStateOf(false) }
    var pressureAdvanceChoice by CalibrationDialogs::pressureAdvance
    var flowRate by remember { mutableStateOf(false) }
    var flowRateChoice by CalibrationDialogs::flowRate
    var printerTest by remember { mutableStateOf<PrinterTest?>(null) }
    var calibrationPrinter by remember { mutableStateOf<CalibrationPrinterOutcome?>(null) }
    // Plater::priv::update_title_dirty_status()
    val title = (if (dirty) "*" else "") + (name ?: orcaString("Untitled"))
    OrcaSidebarTitle(title, DesignR.drawable.orca_open_project) {
        // MainFrame::can_save(): a project with changes to save.
        OrcaIconButton(
            icon = DesignR.drawable.orca_save,
            contentDescription = orcaString("Save Project"),
            onClick = actions.save,
            enabled = canSave && dirty,
        )
        Box {
            OrcaIconButton(
                icon = DesignR.drawable.orca_calib_sf,
                contentDescription = orcaString("Calibration"),
                onClick = { calibrationMenu = true },
                enabled = prepareShown,
            )
            OrcaContextMenu(expanded = calibrationMenu && prepareShown, position = IntOffset.Zero, onDismissRequest = { calibrationMenu = false }) {
                CalibrationMenuItems(
                    enabled = canStartProject,
                    view3D = view3DShown,
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
                    enabled = canStartProject,
                )
                OrcaMenuItem(
                    text = orcaString("Open Project") + "…",
                    onClick = {
                        fileMenu = false
                        actions.open()
                    },
                    enabled = canStartProject,
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
                // MainFrame::can_export_gcode(): the plate's G-code is ready for print.
                OrcaMenuItem(
                    text = orcaString("Export G-code") + "…",
                    onClick = {
                        fileMenu = false
                        actions.exportGcode()
                    },
                    enabled = canExportSliced,
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
    /** "Merge with": the slot leaves, and the other one takes its objects. */
    val merge: (index: Int, into: Int) -> Unit = { _, _ -> },
    /** "Edit": the filament tab edits the slot. */
    val edit: (Int) -> Unit = {},
    /** WipingDialog: the flushing volumes of the plate, read when its sheet opens. */
    val describeFlushVolumes: suspend () -> FlushVolumesOutcome = { FlushVolumesOutcome.Failure("") },
    val setFlushVolumes: (matrix: List<Double>, multipliers: List<Double>) -> Unit = { _, _ -> },
) {
    companion object {
        val NONE = FilamentActions(add = {}, remove = {}, select = { _, _ -> }, setColor = { _, _ -> })
    }
}

/**
 * The edit button of a slot with several materials and its menu
 * (MenuFactory::create_filament_action_menu()): "Edit", "Merge with" the other
 * slots, each by its preset's name and colour, and "Delete" last, apart.
 */
@Composable
private fun FilamentSlotMenu(
    slot: Int,
    labels: List<String>,
    colors: List<Color>,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMerge: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OrcaIconButton(
            icon = DesignR.drawable.orca_menu_filament,
            contentDescription = orcaString("Click to edit preset"),
            onClick = { open = true },
            enabled = enabled,
        )
        if (open) {
            OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { open = false }) {
                OrcaMenuItem(orcaString("Edit"), onClick = {
                    open = false
                    onEdit()
                })
                OrcaSubmenu(orcaString("Merge with"), enabled = labels.size > 1) {
                    labels.forEachIndexed { other, label ->
                        if (other == slot) return@forEachIndexed
                        OrcaMenuItem(
                            text = label.ifEmpty { orcaText(OrcaText("Filament %d", listOf((other + 1).toString()))) },
                            onClick = {
                                open = false
                                onMerge(other)
                            },
                            leading = { OrcaFilamentSlot(number = other + 1, color = colors[other], modifier = Modifier.size(OrcaTheme.dimensions.iconSmall)) },
                        )
                    }
                }
                OrcaMenuItem(orcaString("Delete"), onClick = {
                    open = false
                    onDelete()
                })
            }
        }
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

/** The G-code the File menu exports. */
private const val GCODE_MIME_TYPE = "text/x-gcode"

/** The OBJ file of "Export toolpaths as OBJ" and its materials. */
private const val OBJ_MIME_TYPE = "model/obj"
private const val MATERIALS_MIME_TYPE = "model/mtl"

/**
 * Which Setup Wizard page a preset list opens: its printers or its filaments;
 * or Help's "Setup Wizard" (GUI_App::ShowUserGuide()), the whole guide.
 */
enum class PresetWizardPage { PRINTERS, FILAMENTS, GUIDE }

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
    /** The Troubleshoot Center of OrcaSlicer's Help menu (GUI_App::troubleshoot()). */
    onOpenTroubleshoot: () -> Unit = {},
    /** A tool of the canvas opened from the sidebar: a drawer over the canvas gets out of its way. */
    onShowCanvas: () -> Unit = {},
    /** A calibration starts its project: the 3D view shows it (Plater::new_project selects tp3DEditor). */
    onShowPrepare: () -> Unit = {},
    /** Plater::PopupObjectTable(): the Parameter Table, on the row of the item or on none. */
    onOpenObjectTable: (SettingsItem?) -> Unit = {},
    /** The Prepare tab shows, and on it the 3D view rather than the assembly view, which the Calibration menu wants. */
    prepareShown: Boolean = true,
    view3DShown: Boolean = true,
    /** Help's "Open Network Test" (NetworkTestDialog). */
    onOpenNetworkTest: () -> Unit = {},
    /**
     * The Setup Wizard's "Create" asked for CreatePrinterPresetDialog
     * (Sidebar::create_printer_preset()), which opens once the sidebar shows
     * and [onCreatePrinterTaken] is told.
     */
    createPrinterPending: Boolean = false,
    onCreatePrinterTaken: () -> Unit = {},
) {
    val viewModel = viewModel { createViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canvas by viewModel.canvas.collectAsStateWithLifecycle()
    LaunchedEffect(createPrinterPending) {
        if (createPrinterPending) {
            viewModel.openCreatePrinter()
            onCreatePrinterTaken()
        }
    }
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
    val saveProjectAs = { projectPicker.launch(viewModel.projectFileName(untitled)) }
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
    // Plater::export_gcode() and export_gcode_3mf(): a template that could not name the file shows its error.
    var nameError by remember { mutableStateOf<String?>(null) }
    nameError?.let { message -> NameErrorDialog(message, onDismiss = { nameError = null }) }
    val gcodePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GCODE_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportGcode(ExternalDocumentReference(uri.toString()))
    }
    val exportGcode = {
        viewModel.gcodeNameError()?.let { nameError = it } ?: gcodePicker.launch(viewModel.gcodeName())
    }
    val exportSliced: (Boolean) -> Unit = { all ->
        viewModel.slicedNameError()?.let { nameError = it } ?: run {
            exportingAll = all
            slicedPicker.launch(viewModel.slicedName() ?: ((state.projectName ?: untitled) + ".gcode.3mf"))
        }
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
    val exportGeneric = { genericPicker.launch(viewModel.projectFileName(untitled)) }
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
    // The top menu's Preset Bundle (GUI_App::open_presetbundledialog()).
    var presetBundlesOpen by rememberSaveable { mutableStateOf(false) }
    if (presetBundlesOpen) {
        PresetBundleDialog(
            load = viewModel::listPresetBundles,
            delete = viewModel::deletePresetBundle,
            onDismiss = { presetBundlesOpen = false },
        )
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
        ConfigTransferDialog(outcome, exported = viewModel.configExported, files = viewModel.configImportFiles, onDismiss = viewModel::dismissConfigTransfer)
    }
    PlateSidebarContent(
        state,
        onChoose = viewModel::choose,
        onChooseBedType = viewModel::chooseBedType,
        onChooseScope = viewModel::chooseSettingsScope,
        objectList = ObjectListActions(
            selection = viewModel.selectionActions(
                openSettings = {},
                replaceAll = { if (viewModel.toolsClosedForReplace()) selectionFolderPicker.launch(null) },
                export = exportSelection,
            ),
            volumes = viewModel.volumesActions(
                openSettings = {},
                replaceAll = { if (viewModel.toolsClosedForReplace()) selectionFolderPicker.launch(null) },
            ),
            select = viewModel::chooseSettingsTarget,
            selectAlone = { viewModel.chooseSettingsTarget(it, add = false) },
            selectSettings = viewModel::openSettingsOf,
            setPrintable = viewModel::setObjectPrintable,
            setAutoDrop = viewModel::setObjectAutoDrop,
            setWholeObjectAutoDrop = viewModel::setWholeObjectAutoDrop,
            addInstance = viewModel::addInstance,
            removeInstance = viewModel::removeInstance,
            setNumberOfInstances = viewModel::setInstances,
            removeCopy = viewModel::removeObjectCopy,
            fillBed = viewModel::fillBed,
            setAsIndividual = viewModel::setAsIndividual,
            setAsIndividualOver = viewModel::setAsIndividualOver,
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
            pickCopy = viewModel::pickCopy,
            pickPart = viewModel::pickPart,
            pickRange = viewModel::pickRange,
            selectPartSettings = viewModel::openSettingsOfPart,
            addRange = viewModel::addRange,
            removeRange = viewModel::removeRange,
            removeSelectedRanges = viewModel::removeSelectedRanges,
            removeAllRanges = viewModel::removeAllRanges,
            selectRange = viewModel::chooseSettingsRange,
            selectRangeSettings = viewModel::openSettingsOfRange,
            resetSettings = { kind -> viewModel.requestSettings(kind, SettingsRequest.Reset(emptyList())) },
            editRange = viewModel::editRange,
            setObjectExtruder = viewModel::setObjectExtruder,
            setPartExtruder = viewModel::setPartExtruder,
            setRangeExtruder = viewModel::setRangeExtruder,
            delete = viewModel::deleteObject,
            setObjectPrintable = viewModel::setWholeObjectPrintable,
            // The bar is a window of the canvas.
            editLayers = { mesh ->
                viewModel.editLayersOf(mesh)
                onShowCanvas()
            },
            // The painting tools are windows of the canvas.
            paint = { mesh, kind ->
                viewModel.paint(mesh, kind)
                onShowCanvas()
            },
            shiftToBed = viewModel::shiftToBed,
            // The 3D view frames the selection where the drawer lets it show.
            zoomToSelection = {
                viewModel.zoomToSelection()
                onShowCanvas()
            },
            toggleFlushOption = viewModel::toggleFlushOption,
            editProcessSettings = viewModel::editProcessSettings,
            copyProcessSettings = viewModel::copyProcessSettings,
            editInParameterTable = onOpenObjectTable,
            copyRange = viewModel::copyRange,
            canMoveObject = viewModel::canMoveObject,
            moveObject = viewModel::moveObject,
            moveVolume = viewModel::moveVolume,
            copyRanges = viewModel::copyRanges,
            copySelectedRanges = viewModel::copySelectedRanges,
            focusRangeField = viewModel::focusRangeField,
            pasteProcessSettings = viewModel::pasteProcessSettings,
            replaceVolume = { copy, volume ->
                if (viewModel.toolsClosedForReplace()) {
                    replacing = Triple(copy.mesh.value, copy.instance, volume)
                    replacementPicker.launch(arrayOf("*/*"))
                }
            },
            replaceAllVolumes = { copy ->
                if (viewModel.toolsClosedForReplace()) {
                    replacingAll = copy.mesh.value to copy.instance
                    replacementFolderPicker.launch(null)
                }
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
            centerVolume = viewModel::centerVolume,
            dropVolume = viewModel::dropVolume,
            mirrorVolume = viewModel::mirrorVolume,
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
                if (viewModel.toolsClosedForReplace()) {
                    replacingPlate = index
                    plateFolderPicker.launch(null)
                }
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
            // The text tool and the SVG's file picker are of the canvas.
            addTextObject = {
                viewModel.addTextObject()
                onShowCanvas()
            },
            addSvgObject = {
                viewModel.addSvgObject()
                onShowCanvas()
            },
            addModels = { modelPicker.launch(arrayOf("*/*")) },
        ),
        onOpenWizard = onOpenWizard,
        onOpenSettings = onOpenSettings,
        onOpenSetting = onOpenSetting,
        searchCatalog = viewModel::searchCatalog,
        onOpenAbout = onOpenAbout,
        onOpenPreferences = onOpenPreferences,
        onOpenTroubleshoot = onOpenTroubleshoot,
        onOpenPresetBundles = { presetBundlesOpen = true },
        onOpenObjectTable = onOpenObjectTable,
        prepareShown = prepareShown,
        view3DShown = view3DShown,
        onOpenNetworkTest = onOpenNetworkTest,
        filamentsPreferredCount = viewModel.filamentsPreferredCount.collectAsStateWithLifecycle().value,
        autoArrange = canvas.autoArrange,
        onImportConfig = { configPicker.launch(arrayOf("*/*")) },
        onExportConfig = { exporting = true },
        project = ProjectActions(
            save = { if (viewModel.projectNeedsDocument) saveProjectAs() else viewModel.saveProject() },
            saveAs = saveProjectAs,
            new = viewModel::newProject,
            share = shareProject,
            exportSliced = exportSliced,
            exportGcode = exportGcode,
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
            merge = viewModel::mergeFilament,
            edit = viewModel::editFilament,
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
    /** The sidebar's plate type (Plater::priv::on_select_bed_type). */
    onChooseBedType: (String) -> Unit = {},
    objectList: ObjectListActions,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenSettings: (PresetKind) -> Unit,
    onOpenSetting: (SearchOption) -> Unit = {},
    searchCatalog: suspend () -> SearchCatalogOutcome = { SearchCatalogOutcome.Failure("") },
    onOpenAbout: () -> Unit,
    onOpenPreferences: () -> Unit = {},
    onOpenTroubleshoot: () -> Unit = {},
    /** The top menu's Preset Bundle. */
    onOpenPresetBundles: () -> Unit = {},
    /** ParamsPanel's table button and the object menus: the Parameter Table, on the row of the item or on none. */
    onOpenObjectTable: (SettingsItem?) -> Unit = {},
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
    /** The Prepare tab shows (MainFrame's tp3DEditor), and on it the 3D view rather than the assembly view. */
    prepareShown: Boolean = true,
    view3DShown: Boolean = true,
    /** Help's "Open Network Test" (NetworkTestDialog). */
    onOpenNetworkTest: () -> Unit = {},
    /** filaments_area_preferred_count, up to which the filament title leaves out the count. */
    filamentsPreferredCount: Int = AppConfigKeys.filamentsAreaPreferredCount(null),
) {
    // DiffPresetDialog, which the compare button of the process panel opens.
    var comparing by rememberSaveable { mutableStateOf(false) }
    // Plater::PopupObjectTable()'s "Not available" of a printer with several extruders.
    var tableUnavailable by rememberSaveable { mutableStateOf(false) }
    var showingTips by rememberSaveable { mutableStateOf(false) }
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
    // The search bar above the object list, and the rows it goes through while it is open.
    val listState = rememberLazyListState()
    val objectSearch = rememberObjectListSearch()
    val searchedRows = objectSearchRows(state, objectSearch)
    // The folds of the object list, and where its rows stand in the sidebar's list.
    val objectTree = rememberObjectListTree()
    // ObjectList::update_selections(): what the plate selects unfolds in the
    // list, whose current row then shows (ensure_current_item_visible()).
    val selectionRows = state.selectionRows()
    LaunchedEffect(selectionRows) {
        val current = selectionRows.lastOrNull() ?: return@LaunchedEffect
        objectTree.expand(selectionRows.flatMap(ObjectListRowPath::ancestors))
        // The rows the folds let out are laid out first.
        withFrameNanos {}
        objectTree.reveal(listState, current.key)
    }
    // Sidebar::jump_to_object(): the row the search picked shows, once the list's rows are back.
    LaunchedEffect(objectSearch.reveal) {
        val target = objectSearch.reveal ?: return@LaunchedEffect
        val row = state.rowOf(target)
        objectTree.expand(row.ancestors)
        withFrameNanos {}
        objectTree.reveal(listState, row.key)
        objectSearch.reveal = null
    }
    // Plater::set_number_of_copies() and ObjectList::rename_item() ask first.
    var askingCopies by remember { mutableStateOf<ScenePath?>(null) }
    var cloning by remember { mutableStateOf<ScenePath?>(null) }
    var renaming by remember { mutableStateOf<RenameRequest?>(null) }
    // The flushing volumes are asked for when their sheet opens.
    var openFlushVolumes by remember { mutableStateOf(false) }
    var flushVolumes by remember { mutableStateOf<FlushVolumesOutcome?>(null) }
    // m_panel_printer_title folds the printer's settings until another printer
    // is selected (Sidebar::priv::layout_printer()): the printer it was folded over.
    var printerFoldedOver by rememberSaveable { mutableStateOf<String?>(null) }
    // m_panel_filament_title folds the filaments.
    var filamentsFolded by rememberSaveable { mutableStateOf(false) }
    // Sidebar::add_filament() and delete_filament(): the list unfolds, and shows its end once the count changes.
    var revealFilaments by remember { mutableStateOf(false) }
    val presets = state.presets
    val enabled = state.canChoose && presets != null
    val process = state.processSettings
    val global = state.settingsScope == SettingsScope.GLOBAL
    // Sidebar::show_object_list(false) hides the search bar with the list, which ends a search.
    LaunchedEffect(global) { if (global && objectSearch.active) objectSearch.close() }
    val slotCount = presets?.selection?.allFilaments?.size ?: 0
    // The combo boxes the preset lists drop down from on a large window.
    val printerAnchor = rememberOrcaPickerAnchor()
    val processAnchor = rememberOrcaPickerAnchor()
    // The search button, which Search::SearchDialog pops up under.
    val searchAnchor = rememberOrcaPickerAnchor()
    val filamentAnchors = remember(slotCount) { List(slotCount) { OrcaPickerAnchor() } }
    LaunchedEffect(slotCount) {
        if (!revealFilaments) return@LaunchedEffect
        revealFilaments = false
        withFrameNanos {}
        val shown = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "filament" } ?: return@LaunchedEffect
        val below = shown.offset + shown.size - listState.layoutInfo.viewportEndOffset
        if (below > 0) listState.animateScrollBy(below.toFloat())
    }
    // ParamsPanel::notify_object_config_changed(): an object or a volume has
    // settings of its own (SettingsFactory::get_bundle() of its config).
    val objectDefinitions = (state.objectSettings.tab ?: process.tab)?.definitions.orEmpty()
    val objectConfigs = state.objects.any { plateObject ->
        (listOf(plateObject.settings, plateObject.volume.settings) + plateObject.parts.map { it.settings }).any { it.categories(objectDefinitions).isNotEmpty() }
    }
    // ParamsPanel::switch_to_object(true): its Highlighter blinks the arrow at
    // the switch, shown and hidden every 300 ms until it has turned 11 times.
    var arrowHints by remember { mutableIntStateOf(state.objectProcessHints) }
    var arrowShown by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.objectProcessHints) {
        if (state.objectProcessHints == arrowHints) return@LaunchedEffect
        arrowHints = state.objectProcessHints
        try {
            arrowShown = false
            repeat(ARROW_BLINKS - 1) {
                delay(ARROW_BLINK_MILLIS)
                arrowShown = arrowShown == false
            }
        } finally {
            arrowShown = null
        }
    }
    val processTab = rememberSettingsTab(process, settings, enabled)
    val modelTab = rememberSettingsTab(state.modelSettings, settings, enabled)
    val tab = if (global) processTab else modelTab
    // The settings of the plate and of an object are described for what the
    // plate has selected, and again when the user picks other objects.
    LaunchedEffect(global, state.modelKind, state.selectedInstances, state.selectedParts, state.selectedRanges, enabled) {
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
        state = listState,
        content = objectTree.counted {
        item(key = "project") {
            ProjectTitle(
                state.projectName,
                state.projectDirty,
                state.canSaveProject,
                project,
                state.canExportSliced,
                state.canExportAllSliced,
                state.canExportModel,
                canStartProject = state.canStartProject,
                prepareShown = prepareShown,
                view3DShown = view3DShown,
            )
        }

        item(key = "printer") {
        val printerLabel = presets?.printers?.selectedLabel(presets.selection.printer.value).orEmpty()
        val printerFolded = printerFoldedOver != null && printerFoldedOver == presets?.selection?.printer?.value
        // A tap on the title folds the printer's settings, and the folded title names the printer.
        OrcaSidebarTitle(
            stringResource(R.string.section_printer) + if (printerFolded) "  |  $printerLabel" else "",
            DesignR.drawable.orca_printer,
            modifier = Modifier.clickable(role = Role.Button) { printerFoldedOver = if (printerFolded) null else presets?.selection?.printer?.value },
        ) {
            // Plater's m_printer_connect: the printers of the network the app
            // sends the sliced G-code to.
            OrcaIconButton(
                icon = DesignR.drawable.orca_monitor_signal_strong,
                contentDescription = orcaString("Connection"),
                onClick = openConnection,
                enabled = enabled,
            )
        }
        if (!printerFolded) OrcaSidebarSection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sidebar::update_printer_thumbnail(): the cover of the printer's model.
                PrinterThumbnail(presets?.printerCover.orEmpty())
                Spacer(Modifier.width(8.dp))
                OrcaComboField(
                    text = printerLabel,
                    enabled = enabled,
                    onClick = { openList = PresetList.PRINTERS },
                    modifier = Modifier
                        .weight(1f)
                        .orcaPickerAnchor(printerAnchor),
                )
                // The edit button of the sidebar, which opens the tab of the preset.
                OrcaIconButton(
                    icon = DesignR.drawable.orca_edit,
                    contentDescription = orcaString("Click to edit preset"),
                    onClick = { onOpenSettings(PresetKind.PRINTER) },
                    enabled = enabled,
                )
            }
            // Sidebar::priv::layout_printer(): the nozzle of a printer of one
            // extruder (panel_nozzle_dia), with its type under it (label_nozzle_type).
            if (presets != null && presets.nozzleDiameters.isNotEmpty() && presets.extruderCount < 2) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.nozzle_diameter),
                        color = OrcaTheme.colors.textLabel,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    // The type takes the width it needs under the combo, as the
                    // nozzle combo is narrower than "Hardened Steel" in some languages.
                    Column(horizontalAlignment = Alignment.End) {
                        OrcaComboBox(
                            items = presets.nozzleDiameters,
                            selected = presets.nozzleDiameter,
                            label = { it },
                            onSelect = { onChoose(PresetChoice.NozzleDiameter(it)) },
                            enabled = enabled && presets.nozzleDiameters.size > 1,
                            modifier = Modifier.width(96.dp),
                        )
                        if (presets.nozzleType.isNotEmpty()) {
                            Text(
                                if (presets.nozzleType == "-") "-" else orcaString(presets.nozzleType),
                                color = OrcaTheme.colors.textSide,
                                style = OrcaTheme.typography.body12,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
            // Sidebar's plate type (combo_printer_bed), which a Bambu Lab printer and
            // one that supports several plate types show (panel_printer_bed).
            if (presets != null && presets.bedTypeSelectable && presets.bedTypes.isNotEmpty()) {
                val bedLabels = presets.bedTypes.associate { it.value to orcaString(it.label) }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Sidebar::get_cur_select_bed_image(): the picture of the plate type.
                    Image(
                        painterResource(bedTypeThumbnail(presets.bedType)),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(PRINTER_THUMBNAIL),
                    )
                    Text(
                        orcaString("Bed type"),
                        color = OrcaTheme.colors.textLabel,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    OrcaComboBox(
                        items = presets.bedTypes.map(BedTypeChoice::value),
                        selected = presets.bedType,
                        label = { bedLabels[it] ?: it },
                        onSelect = onChooseBedType,
                        enabled = enabled,
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
        }
        }

        item(key = "filament") {
        // Sidebar: one row per filament of the plate, with the buttons that add
        // one and take one away (Sidebar::add_custom_filament / delete_filament).
        // Sidebar::show_SEMM_buttons(): one extruder printing several materials,
        // or a Bambu Lab printer, adds filaments, and with several removes the
        // last one, edits the flushing volumes and has a menu on each slot;
        // another printer's slots each open their own preset.
        val semm = presets?.multiMaterialButtons == true
        val multiMaterial = semm && slotCount > 1
        // Sidebar::update_presets(): a pellet printer's section is "Pellets";
        // update_filaments_counter(): the count follows the title while the
        // list is folded or longer than filaments_area_preferred_count.
        val pellets = presets?.pelletPrinter == true
        val filamentTitle = if (pellets) orcaString("Pellets") else stringResource(R.string.section_filament)
        // A tap on the title folds the filaments.
        OrcaSidebarTitle(
            filamentTitle + if (filamentsFolded || slotCount > filamentsPreferredCount) " ($slotCount)" else "",
            if (pellets) DesignR.drawable.orca_pellets else DesignR.drawable.orca_filament,
            modifier = Modifier.clickable(role = Role.Button) { filamentsFolded = !filamentsFolded },
        ) {
            if (multiMaterial) {
                // set_flushing_volume_warning(): OrcaSlicer's orange marks volumes of the project's own.
                OrcaIconButton(
                    icon = DesignR.drawable.orca_param_flush,
                    contentDescription = orcaString("Flushing volumes"),
                    onClick = { openFlushVolumes = true },
                    enabled = enabled,
                    tint = if (state.flushVolumesModified) OrcaTheme.colors.secondary else OrcaTheme.colors.textSide,
                )
                OrcaIconButton(
                    icon = DesignR.drawable.orca_delete_filament,
                    contentDescription = orcaString("Remove last filament"),
                    onClick = {
                        filamentsFolded = false
                        revealFilaments = true
                        filaments.remove(slotCount - 1)
                    },
                    enabled = enabled,
                )
            }
            if (semm) {
                OrcaIconButton(
                    icon = DesignR.drawable.orca_add_filament,
                    contentDescription = orcaString("Add one filament"),
                    onClick = {
                        filamentsFolded = false
                        revealFilaments = true
                        filaments.add()
                    },
                    enabled = enabled,
                )
            }
        }
        if (!filamentsFolded) OrcaSidebarSection {
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
                        modifier = Modifier
                            .weight(1f)
                            .then(filamentAnchors.getOrNull(index)?.let { Modifier.orcaPickerAnchor(it) } ?: Modifier),
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
                    // The slot's edit button: with several materials, the filament
                    // action menu (MenuFactory::create_filament_action_menu());
                    // otherwise the filament tab on the slot's preset.
                    val edit = {
                        filaments.edit(index)
                        onOpenSettings(PresetKind.FILAMENT)
                    }
                    if (multiMaterial) {
                        FilamentSlotMenu(
                            slot = index,
                            labels = slots.map { other -> presets?.filaments?.labelOf(other.value).orEmpty() },
                            colors = slots.indices.map { slotColor(presets, state, it) },
                            enabled = enabled,
                            onEdit = edit,
                            onMerge = { into -> filaments.merge(index, into) },
                            onDelete = {
                                revealFilaments = true
                                filaments.remove(index)
                            },
                        )
                    } else {
                        OrcaIconButton(
                            icon = DesignR.drawable.orca_edit,
                            contentDescription = orcaString("Click to edit preset"),
                            onClick = edit,
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
            // ParamsPanel's table button (m_setting_btn) after the mode switch:
            // Plater::PopupObjectTable() with no row, which a printer of several
            // extruders (one min_layer_height each) does not offer.
            OrcaIconButton(
                icon = DesignR.drawable.orca_table,
                contentDescription = orcaString("View all object's settings"),
                onClick = { if ((presets?.minLayerHeights?.size ?: 0) > 1) tableUnavailable = true else onOpenObjectTable(null) },
                enabled = enabled,
            )
            // The search button of the tab (Tab::m_btn_search), which finds a
            // setting and opens its page.
            OrcaIconButton(
                icon = DesignR.drawable.orca_search,
                contentDescription = orcaString("Search in preset"),
                onClick = { searching = true },
                enabled = enabled,
                modifier = Modifier.orcaPickerAnchor(searchAnchor),
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
                    modifier = Modifier
                        .weight(1f)
                        .orcaPickerAnchor(processAnchor),
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
                    // "Objects" in the modified colour while an object or a volume has settings of its own.
                    modifiedIndex = 1.takeIf { objectConfigs },
                )
                // ParamsPanel's m_tips_arrow, there while it blinks.
                arrowShown?.let { shown ->
                    Icon(
                        painterResource(DesignR.drawable.orca_tips_arrow),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .alpha(if (shown) 1f else 0f)
                            .size(OrcaTheme.dimensions.iconSmall),
                    )
                }
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

        // Sidebar::show_object_list(): the list and its search bar show in the
        // objects' mode alone (ParamsPanel::set_active_tab()).
        if (!global) item(key = "objects") {
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
        if (!global) objectSearchItems(objectSearch, searchedRows, onChoose = objectList::jumpTo)
        if (!global && !objectSearch.active) objectListItems(
            state = state,
            enabled = enabled,
            // ObjectList::show_context_menu() opens nothing over Preview's canvas; the
            // sidebar of another tab, which the desktop app has none of, neither.
            preview = !prepareShown,
            tree = objectTree,
            drag = rowDrag,
            picking = picking,
            filaments = filamentColors,
            menuFilaments = menuFilaments,
            paintedKinds = state.paintedKinds,
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
        SidebarAction(DesignR.drawable.orca_add, orcaString("Import Configs"), onImportConfig)
        SidebarAction(DesignR.drawable.orca_save, orcaString("Export Preset Bundle"), onExportConfig)
        // The top menu's Preferences (MainFrame's ConfigMenuPreferences).
        SidebarAction(DesignR.drawable.orca_cog, orcaString("Preferences"), onOpenPreferences)
        // Its Preset Bundle, after Preferences (MainFrame's top menu).
        SidebarAction(DesignR.drawable.orca_menu_edit_preset, orcaString("Preset Bundle"), onOpenPresetBundles)
        // Help's "Keyboard Shortcuts" (GUI_App::keyboard_shortcuts()), first of generate_help_menu()'s items.
        val shortcuts = LocalKeyboardShortcuts.current
        if (shortcuts != null) SidebarAction(DesignR.drawable.orca_help, orcaString("Keyboard Shortcuts")) { shortcuts.perform(ShortcutAction.ShowShortcuts) }
        // Its "Setup Wizard" (GUI_App::ShowUserGuide()).
        SidebarAction(DesignR.drawable.orca_help, orcaString("Setup Wizard")) { onOpenWizard(PresetWizardPage.GUIDE) }
        // Its "Troubleshoot Center" and "Open Network Test", before "Show Tip of the Day".
        SidebarAction(DesignR.drawable.orca_help, stringResource(R.string.troubleshoot), onOpenTroubleshoot)
        SidebarAction(DesignR.drawable.orca_help, orcaString("Open Network Test"), onOpenNetworkTest)
        // Help's "Show Tip of the Day" (DailyTipsWindow::open()).
        SidebarAction(DesignR.drawable.orca_help, orcaString("Show Tip of the Day")) { showingTips = true }
        SidebarAction(DesignR.drawable.orca_help, stringResource(R.string.about), onOpenAbout)
        }
    })

    SettingsTabDialogs(tab)

    if (showingTips) {
        DailyTipsWindow(onDismiss = { showingTips = false })
    }

    if (comparing) {
        DiffPresetDialog(comparison, onDismiss = { comparing = false })
    }

    if (tableUnavailable) {
        // Plater::PopupObjectTable(): MessageDialog(..., _L("Not available"), wxOK | wxICON_WARNING).
        SettingsNoticeDialog(
            SettingsDialog(
                id = "object_table_multi_extruder",
                icon = DialogIcon.WARNING,
                title = listOf(OrcaText("Not available")),
                text = listOf(OrcaText("Currently, the object configuration form cannot be used with a multiple-extruder printer.")),
                question = false,
                yes = null,
                no = null,
            ),
            onDismiss = { tableUnavailable = false },
        )
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
            // Tab::m_btn_search of the process tab (or of an object's, which
            // edits process settings): Plater::search(false, m_type).
            kind = PresetKind.PRINT,
            onChoose = { option ->
                searching = false
                onOpenSetting(option)
            },
            onDismiss = { searching = false },
            anchor = searchAnchor,
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
        if (request is RenameRequest.Plate) {
            // PlateNameEditDialog, as the plate's own "Edit Plate Name" opens it.
            PlateNameDialog(
                name = request.name,
                onDismiss = { renaming = null },
                onConfirm = { name ->
                    renaming = null
                    objectList.renamePlate(request.index, name)
                },
            )
            return@let
        }
        RenameDialog(
            name = request.name,
            onDismiss = { renaming = null },
            onRename = { name ->
                renaming = null
                when (request) {
                    is RenameRequest.Object -> objectList.rename(request.mesh, name)
                    is RenameRequest.Volume -> objectList.renamePart(request.id, name)
                    is RenameRequest.Plate -> Unit
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
                anchor = printerAnchor,
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
                anchor = filamentAnchors.getOrNull(editingSlot),
                // The list of a slot marks the preset of that slot.
                items = presets.selection.allFilaments.getOrNull(editingSlot)?.let { slot ->
                    presets.filaments.map { it.copy(selected = it.name == slot.value) }
                } ?: presets.filaments,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    // Plater::priv::on_select_preset(): the only filament goes through
                    // the filament tab, which asks about its changes; with several the
                    // slot takes the preset alone.
                    if (presets.selection.allFilaments.size <= 1) {
                        onChoose(PresetChoice.SlotFilament(ProfileId(item.name)))
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
                anchor = processAnchor,
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

/** PRINTER_THUMBNAIL_SIZE of the sidebar's printer and plate pictures. */
private val PRINTER_THUMBNAIL = 40.dp

/** The turns of ParamsPanel's Highlighter, and the time between them. */
private const val ARROW_BLINKS = 11
private const val ARROW_BLINK_MILLIS = 300L

/** The covers read so far, by their file. */
private val printerCovers = LruCache<String, ImageBitmap>(8)

/** Sidebar's image_printer: the cover at [path], read off the main thread, or printer_placeholder without one. */
@Composable
private fun PrinterThumbnail(path: String) {
    val cover by produceState(printerCovers.get(path), path) {
        if (value == null && path.isNotEmpty()) {
            value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }?.also { printerCovers.put(path, it) }
        }
    }
    val modifier = Modifier.size(PRINTER_THUMBNAIL)
    cover?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier) }
        ?: Image(painterResource(DesignR.drawable.orca_printer_placeholder), contentDescription = null, modifier = modifier)
}

/** bed_type_thumbnails of curr_bed_type's value; printer_placeholder for a type without a picture. */
private fun bedTypeThumbnail(value: String): Int = when (value) {
    "Cool Plate" -> DesignR.drawable.orca_bed_cool
    "Engineering Plate" -> DesignR.drawable.orca_bed_engineering
    "High Temp Plate" -> DesignR.drawable.orca_bed_high_templ
    "Textured PEI Plate" -> DesignR.drawable.orca_bed_pei
    "Textured Cool Plate" -> DesignR.drawable.orca_bed_pei_cool
    "Supertack Plate" -> DesignR.drawable.orca_bed_cool_supertack
    else -> DesignR.drawable.orca_printer_placeholder
}

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
