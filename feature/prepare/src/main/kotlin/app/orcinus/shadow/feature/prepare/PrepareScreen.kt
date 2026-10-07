package app.orcinus.shadow.feature.prepare

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCanvas
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasTool
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbar
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbarSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaGizmoPanel
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLevel
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLink
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.designsystem.component.OrcaProgressNotification
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.layout.OrcaSidebarToggleSpace
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.AssemblyMode
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.CoordinateSystem
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.EmbossVolume
import app.orcinus.shadow.core.model.ImperialUnits
import app.orcinus.shadow.core.model.CutConnectorStyle
import app.orcinus.shadow.core.model.CutConnectorType
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintRay
import app.orcinus.shadow.core.model.PaintState
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeManipulation
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.cppNumber
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.plate.AddObjectItems
import app.orcinus.shadow.core.ui.plate.CanvasViewButtons
import app.orcinus.shadow.core.ui.plate.CloneDialog
import app.orcinus.shadow.core.ui.plate.DailyTipsPanel
import app.orcinus.shadow.core.ui.plate.ExportFinishedNotification
import app.orcinus.shadow.core.ui.plate.MenuFilament
import app.orcinus.shadow.core.ui.plate.NoticeNotification
import app.orcinus.shadow.core.ui.plate.NumberOfInstancesDialog
import app.orcinus.shadow.core.ui.plate.ObjectClashedNotification
import app.orcinus.shadow.core.ui.plate.ObjectMenuActions
import app.orcinus.shadow.core.ui.plate.ObjectMenuItems
import app.orcinus.shadow.core.ui.plate.PartMenuActions
import app.orcinus.shadow.core.ui.plate.PartMenuItems
import app.orcinus.shadow.core.ui.plate.PartPlateMenuActions
import app.orcinus.shadow.core.ui.plate.PartPlateMenuItems
import app.orcinus.shadow.core.ui.plate.PartPlateMenuState
import app.orcinus.shadow.core.ui.plate.PartShapeSheet
import app.orcinus.shadow.core.ui.plate.PlateIconActions
import app.orcinus.shadow.core.ui.plate.PlateMenuItems
import app.orcinus.shadow.core.ui.plate.PlateNameDialog
import app.orcinus.shadow.core.ui.plate.PlateNoticeNotification
import app.orcinus.shadow.core.ui.plate.PlateProblemNotification
import app.orcinus.shadow.core.ui.plate.PlateSettingsSheet
import app.orcinus.shadow.core.ui.plate.PlateStrip
import app.orcinus.shadow.core.ui.plate.PlaterWarningNotification
import app.orcinus.shadow.core.ui.plate.PostProcessSkippedNotification
import app.orcinus.shadow.core.ui.plate.ProfileUpdateAvailableNotification
import app.orcinus.shadow.core.ui.plate.ProfileUpdateFinishedNotification
import app.orcinus.shadow.core.ui.plate.RenameDialog
import app.orcinus.shadow.core.ui.plate.SelectionMenuActions
import app.orcinus.shadow.core.ui.plate.SelectionMenuItems
import app.orcinus.shadow.core.ui.plate.SeqPrintInfoNotification
import app.orcinus.shadow.core.ui.plate.SimplifySuggestionNotification
import app.orcinus.shadow.core.ui.plate.SliceButton
import app.orcinus.shadow.core.ui.plate.SliceCancelledNotification
import app.orcinus.shadow.core.ui.plate.SliceCompletedNotification
import app.orcinus.shadow.core.ui.plate.SliceNoticeNotification
import app.orcinus.shadow.core.ui.plate.SlicingNotification
import app.orcinus.shadow.core.ui.plate.UpdatedItemsInfoNotification
import app.orcinus.shadow.core.ui.plate.ValidationNotification
import app.orcinus.shadow.core.ui.plate.VolumesMenuActions
import app.orcinus.shadow.core.ui.plate.VolumesMenuItems
import app.orcinus.shadow.core.ui.plate.exportFileName
import app.orcinus.shadow.core.ui.plate.navigatorFaceLabels
import app.orcinus.shadow.core.ui.plate.objectMenuState
import app.orcinus.shadow.core.ui.plate.partMenuState
import app.orcinus.shadow.core.ui.plate.volumeName
import app.orcinus.shadow.core.ui.plate.volumesMenuState
import app.orcinus.shadow.render.scene.AssemblyView
import app.orcinus.shadow.render.scene.BrimEarState
import app.orcinus.shadow.render.scene.BrimEarView
import app.orcinus.shadow.render.scene.BrimEarsView
import app.orcinus.shadow.render.scene.CutConnectorView
import app.orcinus.shadow.render.scene.CutView
import app.orcinus.shadow.render.scene.LayerHeightBar
import app.orcinus.shadow.render.scene.MeasureView
import app.orcinus.shadow.render.scene.MeshBooleanView
import app.orcinus.shadow.render.scene.PaintingView
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.PlateGraphics
import app.orcinus.shadow.render.scene.PlateLabel
import app.orcinus.shadow.render.scene.PlateNavigator
import app.orcinus.shadow.render.scene.PlateView
import app.orcinus.shadow.render.scene.PlateViewOptions
import app.orcinus.shadow.render.scene.SidebarField
import app.orcinus.shadow.render.scene.SidebarHint
import app.orcinus.shadow.render.scene.SurfaceHit
import app.orcinus.shadow.render.scene.TextDragView
import app.orcinus.shadow.render.scene.rememberPlateViewCamera
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun PrepareRoute(
    viewModel: PrepareViewModel,
    /** How many times the workspace switched to the page (EVT_GLVIEWTOOLBAR_3D), which then shows the 3D view. */
    shown: Int = 0,
    onSliceRequested: () -> Unit,
    onOpenSidebar: () -> Unit = {},
    /** A setting a validation notification jumps to opens on its tab's page. */
    onOpenSetting: (SearchOption) -> Unit = {},
    /** The object menus' "Edit in Parameter Table" (Plater::PopupObjectTableBySelection). */
    onOpenObjectTable: (SettingsItem?) -> Unit = {},
    /** The assembly view shows in the 3D view's place, or no longer, which the Calibration menu goes by. */
    onAssemblyViewChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.settingToOpen.collect(onOpenSetting) }
    val assemblyShown = state.assemblyView != null
    LaunchedEffect(assemblyShown) { onAssemblyViewChange(assemblyShown) }
    // select_view_3D("3D") once the workspace switched to the page; a page made anew,
    // or turned with the phone, has seen the switches before it.
    var seenShown by rememberSaveable { mutableIntStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown != seenShown) {
            seenShown = shown
            viewModel.returnFromAssemblyView()
        }
    }
    val canvas by viewModel.canvas.collectAsStateWithLifecycle()
    val textFamilies by viewModel.textFamilies.collectAsStateWithLifecycle()
    // The default styles of the text tool are named in the app's language (_u8L()).
    val catalog = LocalOrcaCatalog.current
    SideEffect { viewModel.styleNames = { name -> catalog.translate(name) } }
    // The file dialog of the desktop app takes several files at once.
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addModels(uris.map { it.toString() })
    }
    // GUI_App::import_zip(): "Choose ZIP file", of the ZIP files alone.
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importZip(uri.toString())
    }
    // "Export as one STL/DRC" and "Replace 3D file": the object waits for the
    // document the user picks, as the desktop app waits for its file dialog.
    var exportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var exportFormat by rememberSaveable { mutableStateOf(MeshFormat.STL) }
    val meshExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MESH_MIME_TYPE)) { uri ->
        val mesh = exportTarget
        exportTarget = null
        if (uri != null && mesh != null) viewModel.exportMesh(ScenePath(mesh), exportFormat, uri.toString())
    }
    // The multi-selection menu's export: one file, or the folder of a file each.
    var selectionExportFormat by rememberSaveable { mutableStateOf(MeshFormat.STL) }
    val selectionExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MESH_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportSelection(selectionExportFormat, multi = false, uri.toString())
    }
    val selectionExportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.exportSelection(selectionExportFormat, multi = true, uri.toString())
    }
    val untitledName = orcaString("Untitled")
    val selectionReplacementFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.replaceAllInSelection(uri.toString())
    }
    // The plate menu's "Replace all with 3D files": the plate whose objects the folder's files replace.
    var replaceAllPlate by rememberSaveable { mutableStateOf<Int?>(null) }
    val plateReplacementFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val plate = replaceAllPlate
        replaceAllPlate = null
        if (uri != null && plate != null) viewModel.replaceAllOnPlate(plate, uri.toString())
    }
    var replaceAllTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var replaceAllInstance by rememberSaveable { mutableStateOf(0) }
    val replacementFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val mesh = replaceAllTarget
        replaceAllTarget = null
        if (uri != null && mesh != null) viewModel.replaceAllVolumes(PlateInstanceId(ScenePath(mesh), replaceAllInstance), uri.toString())
    }
    // ObjectList::load_subobject()'s file dialog (GUI_App::import_model), which takes several files at once.
    var loadTarget by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    val volumePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = loadTarget
        loadTarget = null
        if (uris.isNotEmpty() && target != null) {
            viewModel.loadVolumes(ScenePath(target.first), VolumeType.valueOf(target.second), uris.map { it.toString() })
        }
    }
    var replaceTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var replaceInstance by rememberSaveable { mutableStateOf(0) }
    // The volume the file replaces: the object's own mesh, or the part menu's volume.
    var replaceVolume by rememberSaveable { mutableStateOf(0) }
    val replacement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mesh = replaceTarget
        replaceTarget = null
        if (uri != null && mesh != null) viewModel.replaceMesh(PlateInstanceId(ScenePath(mesh), replaceInstance), replaceVolume, uri.toString())
    }
    // choose_svg_file() and "Save as" of the SVG tool.
    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> viewModel.svgPicked(uri?.toString()) }
    val svgSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SVG_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.saveSvgAs(uri.toString())
    }
    PrepareScreen(
        state = state,
        layout = currentOrcaWindowLayout(),
        onAddModel = { modelPicker.launch(arrayOf("*/*")) },
        onAddCalibrationCube = viewModel::addCalibrationCube,
        onSelectObject = viewModel::selectObject,
        onToggleSelected = viewModel::toggleSelected,
        onAddToSelection = viewModel::addToSelection,
        onDoubleTap = viewModel::switchSettingsScope,
        onDropFiles = viewModel::dropFiles,
        onMoveWipeTower = viewModel::moveWipeTower,
        plateActions = PlateActions(
            select = viewModel::selectPlate,
            add = viewModel::addPlate,
            delete = viewModel::deletePlate,
            lock = viewModel::lockPlate,
            rename = viewModel::renamePlate,
            moveToFront = viewModel::movePlateToFront,
            orient = viewModel::orientPlate,
            arrange = viewModel::arrangePlate,
            settings = viewModel::setPlateSettings,
            cancelSettings = viewModel::cancelPlateSettings,
        ),
        onTogglePainting = viewModel::togglePainting,
        paintingActions = PaintingActions(
            paint = viewModel::paint,
            setState = viewModel::paintWith,
            setRadius = viewModel::setBrushRadius,
            setTool = viewModel::setPaintTool,
            setFillAngle = viewModel::setFillAngle,
            setHighlightAngle = viewModel::setHighlightAngle,
            setOverhangsOnly = viewModel::setOverhangsOnly,
            setVerticalOnly = viewModel::setVerticalOnly,
            setGapArea = viewModel::setGapArea,
            fillGaps = viewModel::fillGaps,
            enableFuzzySkin = viewModel::enablePaintedFuzzySkin,
            clear = viewModel::clearPainting,
            close = viewModel::closePainting,
            setSection = viewModel::setPaintingSection,
            setHorizontalOnly = viewModel::setHorizontalOnly,
            setEdgeDetection = viewModel::setEdgeDetection,
            setCursorHeight = viewModel::setCursorHeight,
            setRemap = viewModel::setRemap,
            resetRemap = viewModel::resetRemap,
            remap = viewModel::remapFilaments,
            resetSectionDirection = viewModel::resetPaintingSectionDirection,
            sectionPlane = viewModel::setPaintingSectionPlane,
        ),
        onPlaceObject = viewModel::placeObject,
        onPlaceObjects = viewModel::placeObjects,
        onGroupSphere = viewModel::setGroupSphere,
        onSetAutoDrop = viewModel::setAutoDrop,
        onDeleteObject = viewModel::deleteObject,
        objectMenuActions = PrepareObjectMenuActions(
            selection = SelectionMenuActions(
                cut = { viewModel.cutSelection() },
                copy = { viewModel.copySelection() },
                paste = { viewModel.pasteIntoSelection() },
                edit = { viewModel.editSelection(it) },
                center = { viewModel.centerSelection() },
                drop = { viewModel.dropSelection() },
                delete = { viewModel.deleteSelection() },
                setPrintable = { viewModel.setSelectionPrintable(it) },
                setAutoDrop = { viewModel.setSelectionAutoDrop(it) },
                editProcessSettings = {
                    viewModel.editSelectionProcessSettings()
                    onOpenSidebar()
                },
                pasteProcessSettings = { viewModel.pasteSelectionProcessSettings() },
                setFilament = { viewModel.setSelectionFilament(it) },
                replaceAll = { if (viewModel.toolsClosedForReplace()) selectionReplacementFolder.launch(null) },
                export = { format, multi ->
                    selectionExportFormat = format
                    if (multi) selectionExportFolder.launch(null) else selectionExport.launch(viewModel.selectionExportName(format, untitledName))
                },
            ),
            addInstance = viewModel::addInstanceOf,
            removeInstance = viewModel::removeInstanceOf,
            setNumberOfInstances = viewModel::setInstancesOf,
            manipulate = viewModel::manipulate,
            addPart = viewModel::addPartTo,
            addHeightRange = viewModel::addHeightRangeTo,
            edit = viewModel::editObjectAt,
            fillBed = viewModel::fillBedWith,
            setAsIndividual = viewModel::setAsIndividualObject,
            clone = viewModel::clone,
            cut = viewModel::cutObjectAt,
            copy = viewModel::copyObjectAt,
            paste = viewModel::pasteInto,
            setPrintable = viewModel::setPrintableAt,
            setFilament = viewModel::setFilamentAt,
            toggleFlushOption = viewModel::toggleFlushOptionAt,
            editProcessSettings = { index ->
                viewModel.editProcessSettingsAt(index)
                onOpenSidebar()
            },
            copyProcessSettings = viewModel::copyProcessSettingsAt,
            pasteProcessSettings = viewModel::pasteProcessSettingsAt,
            invalidateCutInfo = viewModel::invalidateCutInfoAt,
            simplify = viewModel::simplifyAt,
            replace = { index ->
                viewModel.copyOf(index)?.takeIf { viewModel.toolsClosedForReplace() }?.let { copy ->
                    replaceTarget = copy.mesh.value
                    replaceInstance = copy.instance
                    replaceVolume = 0
                    replacement.launch(arrayOf("*/*"))
                }
            },
            reloadFromDisk = viewModel::reloadFromDiskAt,
            loadPart = { index, type ->
                viewModel.copyOf(index)?.let { copy ->
                    loadTarget = copy.mesh.value to type.name
                    volumePicker.launch(arrayOf("*/*"))
                }
            },
            replaceAll = { index ->
                viewModel.copyOf(index)?.takeIf { viewModel.toolsClosedForReplace() }?.let { copy ->
                    replaceAllTarget = copy.mesh.value
                    replaceAllInstance = copy.instance
                    replacementFolder.launch(null)
                }
            },
            export = { index, format, name ->
                viewModel.copyOf(index)?.let { copy ->
                    exportTarget = copy.mesh.value
                    exportFormat = format
                    meshExport.launch(exportFileName(name, format))
                }
            },
            part = PreparePartMenuActions(
                rename = viewModel::renameVolume,
                copy = viewModel::copyVolumeAt,
                delete = viewModel::removeVolume,
                edit = viewModel::editVolume,
                simplify = viewModel::simplifyVolumeAt,
                center = viewModel::centerVolumeAt,
                drop = viewModel::dropVolumeAt,
                mirror = viewModel::mirrorVolumeAt,
                editProcessSettings = { index, volume ->
                    viewModel.editVolumeProcessSettingsAt(index, volume)
                    onOpenSidebar()
                },
                copyProcessSettings = viewModel::copyVolumeProcessSettings,
                pasteProcessSettings = viewModel::pasteVolumeProcessSettings,
                changeType = viewModel::setVolumeType,
                reloadFromDisk = viewModel::reloadVolumeFromDisk,
                replace = { index, volume ->
                    viewModel.copyOf(index)?.takeIf { viewModel.toolsClosedForReplace() }?.let { copy ->
                        replaceTarget = copy.mesh.value
                        replaceInstance = copy.instance
                        replaceVolume = volume
                        replacement.launch(arrayOf("*/*"))
                    }
                },
                setFilament = viewModel::setVolumeFilament,
            ),
            volumes = { index ->
                VolumesMenuActions(
                    center = { viewModel.centerVolumesAt(index) },
                    drop = { viewModel.dropVolumesAt(index) },
                    delete = viewModel::removeSelectedVolumes,
                    edit = viewModel::editSelectedVolumes,
                    replaceAll = { if (viewModel.toolsClosedForReplace()) selectionReplacementFolder.launch(null) },
                    editProcessSettings = {
                        viewModel.editSelectedVolumesProcessSettings()
                        onOpenSidebar()
                    },
                    pasteProcessSettings = viewModel::pasteSelectedProcessSettings,
                    changeType = viewModel::setSelectedVolumesType,
                    setFilament = viewModel::setSelectedFilament,
                )
            },
            openParameterTable = onOpenObjectTable,
        ),
        onPaste = viewModel::paste,
        plateMenuActions = PlateMenuActions(
            addPrimitive = viewModel::addPrimitiveShape,
            addHandyModel = viewModel::addHandyModel,
            showLabels = { viewModel.setCanvasOption(AppConfigKeys.SHOW_LABELS, it.toString()) },
            deleteAll = viewModel::deleteAllObjects,
            duplicatePlate = viewModel::duplicatePlate,
            importZip = { zipPicker.launch(arrayOf(ZIP_MIME_TYPE)) },
            selectPlateObjects = viewModel::selectPlateObjects,
            selectAllPlates = viewModel::selectAllPlates,
            deletePlateObjects = viewModel::deletePlateObjects,
            reloadAll = viewModel::reloadAll,
            replaceAllOnPlate = { index ->
                if (viewModel.toolsClosedForReplace()) {
                    replaceAllPlate = index
                    plateReplacementFolder.launch(null)
                }
            },
        ),
        onToggleGizmo = viewModel::toggleGizmo,
        onCloseGizmo = viewModel::closeGizmo,
        onSetPosition = viewModel::setPosition,
        onSetMoveObjectCoordinates = viewModel::setMoveObjectCoordinates,
        onTranslateInObject = viewModel::translateInObject,
        onPlaceVolume = viewModel::placeVolume,
        onAutoOrient = viewModel::autoOrient,
        onAddInstance = viewModel::addInstance,
        onRemoveInstance = viewModel::removeInstance,
        arrangeActions = ArrangeActions(
            toggle = viewModel::toggleArrangeOptions,
            change = viewModel::changeArrangeSettings,
            reset = viewModel::resetArrangeSettings,
            arrange = viewModel::arrange,
            clearCalibrationRegion = viewModel::clearCalibrationRegion,
        ),
        rotationActions = RotationActions(
            rotateBy = viewModel::rotateBy,
            setRotation = viewModel::setRotation,
            reset = viewModel::resetRotation,
            resetToZero = viewModel::resetRotationToZero,
        ),
        scaleActions = ScaleActions(
            setScale = viewModel::setScale,
            setSize = viewModel::setSize,
            setUniform = viewModel::setUniformScale,
            reset = viewModel::resetScale,
            setCoordinates = viewModel::setScaleCoordinates,
            setObjectCoordinates = viewModel::setMoveObjectCoordinates,
        ),
        onSlice = {
            onSliceRequested()
            viewModel.slice()
        },
        onSliceModeChange = viewModel::chooseSliceMode,
        onCancelSlicing = viewModel::cancelSlicing,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onDismissProblem = viewModel::dismissProblem,
        notificationActions = NotificationActions(
            closeSeqPrintInfo = viewModel::dismissSeqPrintInfo,
            closeExportFinished = viewModel::dismissExportFinished,
            closeNotice = viewModel::dismissNoticeNotification,
            simplify = viewModel::simplifySuggested,
            closeSimplifySuggestion = viewModel::dismissSimplifySuggestion,
            profileUpdates = viewModel::closeProfileUpdates,
            closeProfileUpdateInstalled = viewModel::dismissProfileUpdateInstalled,
            jumpToObjects = viewModel::jumpToObjects,
            cancelPlateJob = viewModel::cancelPlateJob,
            closeArrangeOngoing = viewModel::dismissArrangeOngoing,
            closeZeroSizeObject = viewModel::dismissZeroSizeObject,
        ),
        onRepairObject = viewModel::repairSelected,
        onJumpTo = viewModel::jumpTo,
        canvas = canvas,
        onSetCanvas = viewModel::setCanvasOption,
        cutActions = CutActions(
            toggle = viewModel::toggleCut,
            setPlane = viewModel::setCutPlane,
            flip = viewModel::flipCutPlane,
            setPosition = viewModel::setCutPosition,
            resetPlane = viewModel::resetCutPlane,
            setKeep = viewModel::setCutKeep,
            setPlaceOnCut = viewModel::setCutPlaceOnCut,
            setFlip = viewModel::setCutFlip,
            setCutToParts = viewModel::setCutToParts,
            perform = viewModel::performCut,
            cancel = viewModel::closeCut,
            setKind = viewModel::setCutKind,
            pixelSize = viewModel::setViewPixel,
            setGroove = viewModel::setCutGroove,
            resetGroove = viewModel::resetCutGroove,
            selectPart = viewModel::selectCutPart,
            drawLine = viewModel::setCutLineDrawing,
            line = viewModel::cutLineEvent,
            reset = viewModel::resetCut,
            connectors = CutConnectorActions(
                edit = viewModel::editCutConnectors,
                confirm = viewModel::confirmCutConnectors,
                cancel = viewModel::cancelCutConnectors,
                removeAll = viewModel::removeCutConnectors,
                flipPlane = viewModel::flipCutPlaneForConnectors,
                event = viewModel::cutConnectorEvent,
                selectAll = viewModel::selectAllCutConnectors,
                deleteSelected = viewModel::deleteCutConnectors,
                setSettings = viewModel::setCutConnectorSettings,
                setSnap = viewModel::setCutSnap,
                settingsDone = viewModel::finishCutConnectorEdit,
                setAngle = viewModel::setCutConnectorAngle,
                resetAngle = viewModel::resetCutConnectorAngle,
            ),
        ),
        textActions = TextActions(
            toggle = viewModel::toggleText,
            add = viewModel::addText,
            addRequested = viewModel::addRequestedText,
            edit = viewModel::editText,
            close = viewModel::closeText,
            setText = viewModel::setText,
            setStyle = viewModel::setTextStyle,
            setFont = viewModel::setTextFont,
            toggleItalic = viewModel::toggleTextItalic,
            toggleBold = viewModel::toggleTextBold,
            selectStyle = viewModel::selectTextStyle,
            reset = viewModel::resetTextStyle,
            setAdvanced = viewModel::setTextAdvanced,
            setType = viewModel::setTextType,
            saveStyle = viewModel::saveTextStyle,
            addStyle = viewModel::addTextStyle,
            renameStyle = viewModel::renameTextStyle,
            askDeleteStyle = viewModel::askDeleteTextStyle,
            deleteStyle = viewModel::deleteTextStyle,
            swapStyles = viewModel::swapTextStyles,
            dismissNotice = viewModel::dismissTextNotice,
            moveText = viewModel::moveText,
            rotateText = viewModel::rotateText,
            setKeepUp = viewModel::setTextKeepUp,
            faceCamera = viewModel::faceTextToCamera,
            setCollection = viewModel::setTextCollection,
            drag = viewModel::dragText,
            turn = viewModel::turnText,
            openByDoubleTap = viewModel::doubleTapVolume,
        ),
        textFamilies = textFamilies,
        svgActions = SvgActions(
            choose = viewModel::chooseSvg,
            chooseRequested = viewModel::chooseRequestedSvg,
            pickFile = { svgPicker.launch(arrayOf(SVG_MIME_TYPE)) },
            changeFile = {
                viewModel.chooseSvgFile()
                svgPicker.launch(arrayOf(SVG_MIME_TYPE))
            },
            edit = viewModel::editSvg,
            close = viewModel::closeSvg,
            setDepth = viewModel::setSvgDepth,
            setUseSurface = viewModel::setSvgUseSurface,
            setSize = viewModel::setSvgSize,
            resetSize = viewModel::resetSvgSize,
            setKeepRatio = viewModel::setSvgKeepRatio,
            move = viewModel::moveSvg,
            rotate = viewModel::rotateSvg,
            setKeepUp = viewModel::setSvgKeepUp,
            mirror = viewModel::mirrorSvg,
            faceCamera = viewModel::faceSvgToCamera,
            setType = viewModel::setSvgType,
            reload = viewModel::reloadSvg,
            forgetPath = viewModel::forgetSvgPath,
            bake = viewModel::bakeSvg,
            saveAs = { viewModel.svgSaveName()?.let(svgSaver::launch) },
            drag = viewModel::dragSvg,
            turn = viewModel::turnSvg,
        ),
        measureActions = MeasureActions(
            toggle = viewModel::toggleMeasure,
            close = viewModel::closeMeasure,
            touch = viewModel::measureTouch,
            reset = viewModel::resetMeasure,
            escape = viewModel::escapeMeasure,
            setPointSelection = viewModel::setMeasurePointSelection,
            editDistance = viewModel::editMeasureDistance,
            scale = viewModel::scaleMeasure,
            cancelScale = viewModel::cancelMeasureScale,
        ),
        assemblyActions = AssemblyActions(
            toggle = viewModel::toggleAssembly,
            setMode = viewModel::setAssemblyMode,
            assemble = viewModel::assemble,
            flip = viewModel::flipByFace2,
        ),
        assemblyViewActions = AssemblyViewActions(
            open = viewModel::openAssemblyView,
            back = viewModel::returnFromAssemblyView,
            setExplosionRatio = viewModel::setExplosionRatio,
            setVisible = viewModel::setAssemblyVisible,
            fillColor = viewModel::fillColor,
            place = viewModel::placeObjectInAssembly,
            setSectionPosition = viewModel::setSectionPosition,
            resetSectionDirection = viewModel::resetSectionDirection,
            sectionPlane = viewModel::setSectionPlane,
            setPartSelection = viewModel::setAssemblyPartSelection,
            selectVolume = viewModel::selectAssemblyVolume,
        ),
        meshBooleanActions = MeshBooleanActions(
            toggle = viewModel::toggleMeshBoolean,
            close = viewModel::closeMeshBoolean,
            pick = viewModel::pickMeshBooleanVolume,
            setOperation = viewModel::setMeshBooleanOperation,
            selectTool = viewModel::selectMeshBooleanTool,
            clear = viewModel::clearMeshBooleanVolume,
            setDeleteInput = viewModel::setMeshBooleanDeleteInput,
            apply = viewModel::applyMeshBoolean,
        ),
        brimEarsActions = BrimEarsActions(
            toggle = viewModel::toggleBrimEars,
            close = viewModel::closeBrimEars,
            touch = viewModel::brimEarsTouch,
            setDiameter = viewModel::setBrimEarDiameter,
            typeDiameter = viewModel::typeBrimEarDiameter,
            setMaxAngle = viewModel::setBrimEarMaxAngle,
            setDetectionRadius = viewModel::setBrimEarDetectionRadius,
            generate = viewModel::generateBrimEars,
            removeSelected = viewModel::removeSelectedBrimEars,
            removeAll = viewModel::removeAllBrimEars,
            setPainted = viewModel::setPaintedBrim,
            setSection = viewModel::setBrimEarsSection,
        ),
        layerActions = LayerEditingActions(
            toggle = viewModel::toggleLayerEditing,
            close = viewModel::closeLayerEditing,
            press = viewModel::pressLayerBar,
            move = viewModel::moveLayerBar,
            release = viewModel::releaseLayerBar,
            setAction = viewModel::setLayerEditAction,
            setBandWidth = viewModel::setLayerBandWidth,
            setAdaptiveQuality = viewModel::setAdaptiveQuality,
            adaptive = viewModel::adaptiveLayers,
            setSmoothRadius = viewModel::setSmoothRadius,
            setKeepMin = viewModel::setKeepMin,
            smooth = viewModel::smoothLayers,
            reset = viewModel::resetLayers,
        ),
        simplifyActions = SimplifyActions(
            setUseCount = viewModel::setSimplifyUseCount,
            setReduction = viewModel::setSimplifyReduction,
            setDecimateRatio = viewModel::setSimplifyDecimateRatio,
            setWireframe = viewModel::setSimplifyWireframe,
            apply = viewModel::applySimplify,
            cancel = viewModel::closeSimplify,
        ),
    )
}

/** What a painting tool does while it is open (GLGizmoPainterBase). */
internal class PaintingActions(
    /** A touch of the finger, as rays in world coordinates: where it is, then its path back to its last touch; [starts] for the first of a stroke. */
    val paint: (rays: List<PaintRay>, starts: Boolean) -> Unit,
    /** The state the finger paints ([PaintState]); for colour, the filament. */
    val setState: (Int) -> Unit,
    val setRadius: (Double) -> Unit,
    val setTool: (PaintTool) -> Unit,
    val setFillAngle: (Double) -> Unit,
    /** "Highlight overhang areas" and "On highlighted overhangs only". */
    val setHighlightAngle: (Double) -> Unit,
    val setOverhangsOnly: (Boolean) -> Unit,
    /** The seam tool's "Vertical". */
    val setVerticalOnly: (Boolean) -> Unit,
    /** The gap fill's area and its "Perform". */
    val setGapArea: (Double) -> Unit,
    val fillGaps: () -> Unit,
    /** "Enable painted fuzzy skin for this object" of the fuzzy skin tool's warning. */
    val enableFuzzySkin: () -> Unit,
    /** "Erase all". */
    val clear: () -> Unit,
    val close: () -> Unit,
    /** "Section view", "Reset direction", and the plane the 3D view placed for it. */
    val setSection: (Double) -> Unit = {},
    val resetSectionDirection: () -> Unit = {},
    val sectionPlane: (normal: Vector3, offset: Double) -> Unit = { _, _ -> },
    /** The colour tool's "Horizontal", "Edge detection" and the height range's height. */
    val setHorizontalOnly: (Boolean) -> Unit = {},
    val setEdgeDetection: (Boolean) -> Unit = {},
    val setCursorHeight: (Double) -> Unit = {},
    /** "Remap filaments": a filament's target, "Reset" and "Remap". */
    val setRemap: (source: Int, target: Int) -> Unit = { _, _ -> },
    val resetRemap: () -> Unit = {},
    val remap: () -> Unit = {},
) {
    companion object {
        val NONE = PaintingActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * OrcaSlicer's Prepare page: the 3D plate view, edge to edge, with the model
 * toolbar and the notifications over it. The sidebar belongs to the app shell.
 */
@Composable
internal fun PrepareScreen(
    state: PrepareUiState,
    layout: OrcaWindowLayout,
    onAddModel: () -> Unit,
    onAddCalibrationCube: () -> Unit,
    onSelectObject: (Int?) -> Unit,
    onMoveWipeTower: (Double, Double) -> Unit,
    onTogglePainting: (PaintKind) -> Unit,
    paintingActions: PaintingActions,
    onPlaceObject: (Int, Transform3, Manipulation) -> Unit,
    onPlaceObjects: (List<Pair<Int, Transform3>>, Manipulation) -> Unit,
    onSetAutoDrop: (index: Int, enabled: Boolean) -> Unit,
    onDeleteObject: (index: Int) -> Unit,
    objectMenuActions: PrepareObjectMenuActions,
    onPaste: () -> Unit,
    onToggleGizmo: (PlateGizmo) -> Unit,
    onCloseGizmo: () -> Unit,
    onSetPosition: (axis: Int, value: Double) -> Unit,
    onAutoOrient: () -> Unit,
    onAddInstance: () -> Unit,
    onRemoveInstance: () -> Unit,
    arrangeActions: ArrangeActions,
    rotationActions: RotationActions,
    scaleActions: ScaleActions,
    onSlice: () -> Unit,
    onCancelSlicing: () -> Unit,
    onDismissProblem: () -> Unit,
    notificationActions: NotificationActions = NotificationActions.NONE,
    /** The info notification's " (Repair)". */
    onRepairObject: () -> Unit = {},
    /** "Jump to" of a validation notification. */
    onJumpTo: (ValidationNotice) -> Unit = {},
    onSliceModeChange: (SliceMode) -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    plateMenuActions: PlateMenuActions = PlateMenuActions.NONE,
    simplifyActions: SimplifyActions = SimplifyActions.NONE,
    plateActions: PlateActions = PlateActions.NONE,
    cutActions: CutActions = CutActions.NONE,
    layerActions: LayerEditingActions = LayerEditingActions.NONE,
    textActions: TextActions = TextActions.NONE,
    textFamilies: List<TextFontFamily> = emptyList(),
    svgActions: SvgActions = SvgActions.NONE,
    measureActions: MeasureActions = MeasureActions.NONE,
    brimEarsActions: BrimEarsActions = BrimEarsActions.NONE,
    meshBooleanActions: MeshBooleanActions = MeshBooleanActions.NONE,
    assemblyActions: AssemblyActions = AssemblyActions.NONE,
    assemblyViewActions: AssemblyViewActions = AssemblyViewActions.NONE,
    canvas: CanvasPreferences = CanvasPreferences(),
    /** An item of the canvas's View menu, which OrcaSlicer.conf keeps. */
    onSetCanvas: (key: String, value: String) -> Unit = { _, _ -> },
    /** The move window's coordinates, and its "Translate(Relative)" in object coordinates. */
    onSetMoveObjectCoordinates: (Boolean) -> Unit = {},
    onTranslateInObject: (axis: Int, value: Double) -> Unit = { _, _ -> },
    /** A gizmo or a finger moved the selected volume of the copy at index by a change in the world. */
    onPlaceVolume: (index: Int, change: Transform3, manipulation: VolumeManipulation) -> Unit = { _, _, _ -> },
    /** The 3D view found the smallest sphere around a group of copies, for the rotation window. */
    onGroupSphere: (BoundingSphere?) -> Unit = {},
    /** The selection mode's Ctrl click on a copy, and the copies its rectangle covers. */
    onToggleSelected: (Int) -> Unit = {},
    onAddToSelection: (Set<Int>) -> Unit = {},
    /** A double tap of the 3D view: the settings switch to the selected object, or to the global ones. */
    onDoubleTap: () -> Unit = {},
    /** Documents dropped on the 3D view, with the copy at the drop and where it was hit, or the bed's point there. */
    onDropFiles: (documents: List<String>, copy: Int?, hit: SurfaceHit?, bedPoint: Point2?) -> Unit = { _, _, _, _ -> },
) {
    OrcaCanvas(Modifier.fillMaxSize()) {
        val viewCamera = rememberPlateViewCamera()
        // PlaterDropTarget: documents another app drags onto the 3D view (split screen, a desktop
        // mode). The copy under the drop is the one a ray through it hits nearest the camera.
        val activity = LocalActivity.current
        var viewCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val copies by rememberUpdatedState(state.sceneCopies.size)
        val dropFiles by rememberUpdatedState(onDropFiles)
        val fileDrop = remember(viewCamera) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val dragEvent = event.toAndroidDragEvent()
                    val clip = dragEvent.clipData ?: return false
                    val documents = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri?.toString() }
                    if (documents.isEmpty()) return false
                    // The documents of another app are readable while the activity holds the drop's permissions.
                    activity?.requestDragAndDropPermissions(dragEvent)
                    val at = viewCoordinates?.let { it.localPositionOf(it.findRootCoordinates(), Offset(dragEvent.x, dragEvent.y)) }
                    val eye = viewCamera.eye()?.position
                    val hit = at?.let { point ->
                        (0 until copies).mapNotNull { copy -> viewCamera.surfaceHit(copy, point)?.let { copy to it } }
                            .minByOrNull { (_, surface) ->
                                val position = surface.position
                                eye?.let { hypot(hypot(position.x - it.x, position.y - it.y), position.z - it.z) } ?: 0.0
                            }
                    }
                    dropFiles(documents, hit?.first, hit?.second, if (hit == null) viewCamera.bedPoint(at) else null)
                    return true
                }
            }
        }
        var objectMenu by remember { mutableStateOf<ObjectMenu?>(null) }
        // _render_assemble_info(): the size of the assembly view's selection, which its view tells.
        var assemblySelection by remember { mutableStateOf<Vector3?>(null) }
        // Where a finger held empty space, and the plate there (null off the plates).
        var plateMenu by remember { mutableStateOf<Pair<Offset, Int?>?>(null) }
        var askingCopies by remember { mutableStateOf<Int?>(null) }
        var cloning by remember { mutableStateOf<Int?>(null) }
        var addingPart by remember { mutableStateOf<Triple<Int, VolumeType, Offset>?>(null) }
        // OrcaSlicer's "Embossed text", the text a new text starts with.
        val defaultText = orcaString("Embossed text")
        // The object list's "Add part" > "Text": the canvas places it on the
        // object's first copy, as the desktop app does without a mouse position.
        LaunchedEffect(state.embossRequest) {
            val request = state.embossRequest as? EmbossRequest.Add ?: return@LaunchedEffect
            val mesh = request.mesh
            val copy = mesh?.let { state.sceneCopies.indexOfFirst { it.id == PlateInstanceId(mesh, 0) } } ?: -1
            val hit = copy.takeIf { it >= 0 }?.let { viewCamera.surfaceHit(it) }
            // An object of its own goes under the centre of the view.
            val bedPoint = if (mesh == null) viewCamera.bedPoint() else null
            if (request.kind == EmbossKind.SVG) {
                // choose_svg_file() first.
                svgActions.chooseRequested(request, hit, bedPoint)
                svgActions.pickFile()
            } else {
                textActions.addRequested(request, hit, bedPoint, defaultText)
            }
        }
        // wxEVT_DATAVIEW_ITEM_ACTIVATED of a row of the object list: the camera frames the selection.
        var zoomedToSelection by remember { mutableIntStateOf(state.zoomToSelection) }
        LaunchedEffect(state.zoomToSelection) {
            if (state.zoomToSelection == zoomedToSelection) return@LaunchedEffect
            zoomedToSelection = state.zoomToSelection
            viewCamera.zoomToSelection()
        }
        // Plater::new_project() frames the bed, load_project() every plate, both from the front and above.
        var projectViewed by remember { mutableIntStateOf(state.projectResets) }
        LaunchedEffect(state.projectResets) {
            if (state.projectResets == projectViewed) return@LaunchedEffect
            projectViewed = state.projectResets
            viewCamera.projectView(allPlates = state.projectOpened)
        }
        // The phone's Back leaves the assembly view as its "Return" does; an open tool takes it first.
        BackHandler(enabled = state.assemblyView != null) { assemblyViewActions.back() }
        // The desktop measuring tool's Esc: the phone's Back drops the last selection, and with none closes the tool.
        BackHandler(enabled = state.measure != null) { measureActions.escape() }
        var renamingPlate by remember { mutableStateOf<Int?>(null) }
        // The keys a phone has not: taps add to the selection, and a finger over empty space draws a rectangle.
        var selectionMode by rememberSaveable { mutableStateOf(false) }
        // The part menu's Rename: the volume, with the name it has.
        var renamingVolume by remember { mutableStateOf<Pair<ObjectPartId, String>?>(null) }
        // Plater::select_plate_by_hover_id(), action 5: the plate is selected, then its settings open.
        var customizingPlate by remember { mutableStateOf<Int?>(null) }
        // GLCanvas3D::handle_sidebar_focus_event(): the field of the move, rotate or scale window being edited, by its row and axis.
        var sidebarField by remember { mutableStateOf<Pair<SidebarField, Int>?>(null) }
        val onFieldFocus: (SidebarField, Int, Boolean) -> Unit = { field, axis, focused ->
            if (focused) sidebarField = field to axis else if (sidebarField == field to axis) sidebarField = null
        }
        val untitled = orcaString("Untitled")
        // Previews have no OpenGL; they show the canvas colour.
        if (!LocalInspectionMode.current) {
            PlateView(
                plate = state.plate,
                objects = state.sceneObjects,
                wipeTower = state.wipeTower,
                filamentColors = state.filamentColors,
                builtWipeTower = state.builtWipeTower,
                onMoveWipeTower = onMoveWipeTower,
                painting = state.painting?.let {
                    // The brush alone keeps to a column or a row (ToolType::BRUSH).
                    PaintingView(
                        it.mesh,
                        it.kind,
                        it.highlightAngle,
                        it.verticalOnly && it.brushing,
                        state.paintSection,
                        it.horizontalOnly && it.brushing,
                        it.cursor,
                    )
                },
                onPaintSection = paintingActions.sectionPlane,
                onPaint = paintingActions.paint,
                cut = state.cut?.let { mode ->
                    mode.plane?.let { plane ->
                        CutView(
                            mesh = mode.mesh,
                            instance = mode.instance,
                            plane = plane,
                            radius = mode.radius,
                            contour = mode.described?.contour,
                            canCut = mode.canPerform,
                            section = mode.described?.section,
                            connectors = mode.connectors.mapIndexed { index, connector ->
                                CutConnectorView(
                                    position = connector.position,
                                    radius = connector.radius,
                                    height = connector.height,
                                    zAngle = connector.zAngle,
                                    mesh = mode.described?.takeIf { mode.describedConnectors?.getOrNull(index)?.let { it.type == connector.type && it.style == connector.style && it.shape == connector.shape } == true }
                                        ?.connectorMeshes?.getOrNull(index),
                                    dowel = connector.type == CutConnectorType.DOWEL,
                                    prism = connector.style == CutConnectorStyle.PRISM,
                                    selected = index in mode.selectedConnectors,
                                    invalid = index in mode.invalidConnectors,
                                )
                            },
                            editingConnectors = mode.editingConnectors,
                            dovetail = mode.kind == CutKind.DOVETAIL,
                            groovePlane = mode.described?.groovePlane?.takeIf { mode.describedGroove != null },
                            grooveAngle = mode.groove.angle,
                            previewParts = mode.previewParts,
                            drawingLine = mode.drawingLine && !mode.editingConnectors,
                        )
                    }
                },
                onCutPlane = cutActions.setPlane,
                onFlipCutPlane = cutActions.flip,
                onCutConnector = cutActions.connectors.event,
                onCutPart = cutActions.selectPart,
                onCutLine = cutActions.line,
                onPixelSize = cutActions.pixelSize,
                selectedObject = state.selectedObject,
                selectedObjects = state.selectedObjects,
                gizmo = state.gizmo,
                moveFrame = state.moveFrame.takeIf { state.gizmo == PlateGizmo.MOVE || state.gizmo == PlateGizmo.SCALE },
                flatteningPlanes = state.flatteningPlanes,
                wireframes = state.wireframes,
                editable = state.canEditPlate,
                onSelectObject = onSelectObject,
                selectionMode = selectionMode && state.assemblyView == null,
                onToggleObject = onToggleSelected,
                onAddObjects = onAddToSelection,
                onPlaceObject = onPlaceObject,
                onPlaceObjects = onPlaceObjects,
                // GLCanvas3D::on_mouse(): no menu while a tool is open.
                onOpenObjectMenu = { index, position -> if (!state.toolOpen) objectMenu = ObjectMenu(index, position) },
                onOpenPlateMenu = { position, plate ->
                    if (!state.toolOpen) {
                        // A right click on empty space deselects all first, then the canvas's menu shows.
                        if (state.selectedObject != null || state.selectedObjects.isNotEmpty()) onSelectObject(null)
                        plateMenu = position to plate
                    }
                },
                onDoubleTap = onDoubleTap,
                contentDescription = stringResource(R.string.plate_view),
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { viewCoordinates = it }
                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = fileDrop),
                plateOrigins = state.plateOrigins,
                currentPlate = state.currentPlate,
                onSelectPlate = plateActions.select,
                plateNames = state.plateNames.map { it.ifEmpty { untitled } },
                orbitSpeed = canvas.orbitSpeed,
                freeCamera = canvas.freeCamera,
                zoomToFingers = canvas.zoomToMouse,
                // The FPS overlay under the toolbar's left end; the navigator takes the top right corner.
                graphics = PlateGraphics(canvas.fxaa, canvas.fpsCap, canvas.fpsOverlay, Alignment.TopStart, PaddingValues(top = CanvasNavigatorTop, start = CanvasMargin)),
                options = PlateViewOptions(
                    perspective = canvas.perspective,
                    autoPerspective = canvas.autoPerspective,
                    axes = canvas.axes,
                    gridlines = canvas.gridlines,
                    outline = canvas.outline,
                    phong = canvas.realistic && canvas.phong,
                    shadows = canvas.realistic && canvas.shadows,
                    ssao = canvas.realistic && canvas.ssao,
                ),
                onPerspectiveChange = { onSetCanvas(AppConfigKeys.USE_PERSPECTIVE_CAMERA, it.toString()) },
                camera = viewCamera,
                overhangNormalZ = state.overhangNormalZ.takeIf { canvas.overhang },
                labels = if (canvas.labels) objectLabels(state) else emptyMap(),
                smoothNormals = canvas.realistic && canvas.smoothNormals,
                // _render_sequential_clearance(): with no gizmo open, or the move, rotation or scale gizmo.
                clearance = state.clearance.takeIf { clearanceShown(state) },
                copyClearances = state.copyClearances.takeIf { clearanceShown(state) }.orEmpty(),
                sequentialPrint = state.sequentialPrint,
                layerRangeHint = state.layerRangeHint,
                sidebarHint = sidebarField?.let { (field, axis) -> sidebarHintOf(state, field, axis) },
                printsByObject = state.sequentialPrint != null,
                bedType = state.bedType,
                calibrationLogo = true,
                selectionCoordinates = state.selectionCoordinates,
                gizmoRunning = state.toolOpen,
                antialiasingSamples = canvas.antialiasingSamples,
                layerEditing = state.layerEditing?.view(),
                // SurfaceDrag: the text the tool is open on follows a finger over its object.
                textDrag = state.text?.takeUnless { it.busy }?.let { embossDragOf(it.volume, it.described, keepUp = true, state.sceneCopies) }
                    ?: state.svg?.takeUnless { it.busy }?.let { embossDragOf(it.volume, it.described, keepUp = it.keepUp, state.sceneCopies) },
                onTextDragged = { placement -> if (state.text != null) textActions.drag(placement) else svgActions.drag(placement) },
                onTextTurned = { turn -> if (state.text != null) textActions.turn(turn) else svgActions.turn(turn) },
                measure = state.measure?.let { mode ->
                    MeasureView(
                        copies = state.measuredCopies,
                        volumes = state.measuredVolumes,
                        pointSelection = mode.pointSelection,
                        hover = mode.hover,
                        measurement = mode.measurement,
                        imperial = canvas.imperialUnits,
                        units = orcaString(if (canvas.imperialUnits) "in" else "mm"),
                        // m_hit_different_volumes.size() < 2 in the 3D view; the assembly view
                        // offers none, as do_scale() would write the scaled assemble transformation onto the plate.
                        editToScale = mode.measurement.hitVolumes < 2 && state.assemblyView == null,
                        editToScaleDescription = orcaString("Edit to scale"),
                        editingDistance = mode.editingDistance != null,
                        faceToFace = mode.assembly == AssemblyMode.FACE_FACE,
                    )
                },
                onMeasure = measureActions.touch,
                onEditMeasureDistance = measureActions.editDistance,
                brimEars = state.brimEars?.let { mode -> brimEarsViewOf(state, mode) },
                onBrimEars = brimEarsActions.touch,
                meshBoolean = state.meshBoolean?.let { mode ->
                    val target = state.sceneCopies.firstOrNull { it.id == mode.copy }?.plateObject
                    MeshBooleanView(
                        copy = mode.copy,
                        source = mode.source?.let { target?.volumeAt(it)?.mesh?.value },
                        tool = mode.tool?.let { target?.volumeAt(it)?.mesh?.value },
                    )
                },
                onMeshBooleanPick = meshBooleanActions.pick,
                assembly = state.assemblyView?.let { AssemblyView(it.explosionRatio, it.hidden, it.sectionPosition, it.sectionResets, it.section, it.partSelection) },
                onSelectVolume = assemblyViewActions.selectVolume,
                onDoubleTapVolume = textActions.openByDoubleTap,
                onAssemblySelection = { assemblySelection = it },
                onPlaceInAssembly = assemblyViewActions.place,
                onAssemblySection = assemblyViewActions.sectionPlane,
                selectedVolume = state.selectedVolume?.mesh?.value,
                highlightedVolumes = state.highlightedVolumes,
                selectedVolumeSphere = state.volumeSphere,
                groupUniformScale = state.group?.uniformOnly == true,
                onGroupSphere = onGroupSphere,
                selectedVolumeScale = state.volumeScale,
                onPlaceVolume = onPlaceVolume,
            )
            state.measure?.editingDistance?.let { distance ->
                MeasureScaleDialog(distance, canvas.imperialUnits, measureActions.scale, measureActions.cancelScale)
            }
        }
        plateMenu?.let { (position, plate) ->
            // An object of a text or an SVG standing where the finger held the bed.
            val addText = { textActions.add(null, VolumeType.PART, null, viewCamera.bedPoint(position), defaultText) }
            val addSvg = {
                svgActions.choose(null, VolumeType.PART, null, viewCamera.bedPoint(position))
                svgActions.pickFile()
            }
            if (plate != null) {
                PartPlateContextMenu(
                    state,
                    plate,
                    position,
                    onDismiss = { plateMenu = null },
                    onPaste = onPaste,
                    actions = PartPlateMenuActions(
                        selectPlateObjects = plateMenuActions.selectPlateObjects,
                        selectAllPlates = plateMenuActions.selectAllPlates,
                        deletePlateObjects = plateMenuActions.deletePlateObjects,
                        arrangePlate = plateActions.arrange,
                        reloadAll = plateMenuActions.reloadAll,
                        orientPlate = plateActions.orient,
                        deletePlate = plateActions.delete,
                        addPrimitive = plateMenuActions.addPrimitive,
                        addHandyModel = plateMenuActions.addHandyModel,
                        addModels = onAddModel,
                        replaceAllOnPlate = plateMenuActions.replaceAllOnPlate,
                        lockPlate = plateActions.lock,
                        rename = { renamingPlate = it },
                        addText = addText,
                        addSvg = addSvg,
                    ),
                )
            } else {
                PlateContextMenu(
                    state,
                    position,
                    onDismiss = { plateMenu = null },
                    onAddModel = onAddModel,
                    onPaste = onPaste,
                    actions = plateMenuActions,
                    labels = canvas.labels,
                    onAddText = addText,
                    onAddSvg = addSvg,
                )
            }
        }
        // Plater::priv::on_right_click() in the assembly view: its own menu.
        objectMenu?.takeIf { state.assemblyView != null }?.let { menu ->
            AssemblyObjectMenu(
                state = state,
                index = menu.index,
                position = menu.position,
                onDismiss = { objectMenu = null },
                onDelete = onDeleteObject,
                onSetFilament = objectMenuActions.setFilament,
                actions = assemblyViewActions,
            )
        }
        objectMenu?.takeIf { state.assemblyView == null }?.let { menu ->
            ObjectContextMenu(
                state,
                menu,
                onDismiss = { objectMenu = null },
                onSetAutoDrop,
                onDeleteObject,
                objectMenuActions,
                onChooseShape = { type -> addingPart = Triple(menu.index, type, menu.position) },
                onEditText = { volume -> textActions.edit(volume) },
                onEditSvg = { volume -> svgActions.edit(volume) },
                onAskNumberOfInstances = { askingCopies = menu.index },
                onAskClone = { cloning = menu.index },
                onAskRenameVolume = { volume, name -> renamingVolume = volume to name },
            )
        }
        renamingVolume?.let { (volume, name) ->
            RenameDialog(
                name = name,
                onDismiss = { renamingVolume = null },
                onRename = { newName ->
                    renamingVolume = null
                    objectMenuActions.part?.rename(volume, newName)
                },
            )
        }
        cloning?.let { index ->
            CloneDialog(
                autoArrange = canvas.autoArrange,
                onDismiss = { cloning = null },
                onFill = {
                    cloning = null
                    objectMenuActions.fillBed(index)
                },
                onClone = { count, arrange ->
                    cloning = null
                    objectMenuActions.clone(index, count, arrange)
                },
            )
        }
        // Plater::set_number_of_copies() asks first; the shapes of a part are a sheet.
        askingCopies?.let { index ->
            val copies = state.sceneCopies.getOrNull(index)?.plateObject?.instances?.size
            if (copies == null) {
                askingCopies = null
            } else {
                NumberOfInstancesDialog(
                    current = copies,
                    onDismiss = { askingCopies = null },
                    onConfirm = { number ->
                        askingCopies = null
                        objectMenuActions.setNumberOfInstances(index, number)
                    },
                )
            }
        }
        renamingPlate?.let { index ->
            PlateNameDialog(
                name = state.plateNames.getOrNull(index).orEmpty(),
                onDismiss = { renamingPlate = null },
                onConfirm = { name ->
                    renamingPlate = null
                    plateActions.rename(index, name)
                },
            )
        }
        // The plate tab's "Customize" of a filament sequence opens the dialog with the sequences alone.
        val sequencesOnly = customizingPlate == null && state.layerSequencePrompt
        (customizingPlate ?: state.currentPlate.takeIf { sequencesOnly })?.takeIf { it == state.currentPlate && state.canEditPlate }?.let { index ->
            PlateSettingsSheet(
                name = state.plateNames.getOrNull(index).orEmpty(),
                choice = state.plateSettings,
                bedTypes = state.bedTypes,
                bedTypeSelectable = state.plateBedTypeSelectable,
                filamentColors = state.filamentColors.map { Color(it.red, it.green, it.blue, it.alpha) },
                spiralOn = state.spiralVaseMode,
                i3 = state.printerI3,
                onlyLayerSequence = sequencesOnly,
                onDismiss = { name ->
                    customizingPlate = null
                    plateActions.cancelSettings()
                    plateActions.rename(index, name)
                },
                onConfirm = { name, choice, agreed ->
                    customizingPlate = null
                    plateActions.settings(choice, agreed)
                    plateActions.rename(index, name)
                },
            )
        }
        addingPart?.let { (index, type, at) ->
            PartShapeSheet(
                type = type,
                onDismiss = { addingPart = null },
                onChoose = { shape, name ->
                    addingPart = null
                    objectMenuActions.addPart(index, shape, type, name)
                },
                // append_menu_item_add_text(): where the finger held the object, or beside it on a miss.
                onText = {
                    addingPart = null
                    textActions.add(index, type, viewCamera.surfaceHit(index, at), null, defaultText)
                },
                onSvg = {
                    addingPart = null
                    svgActions.choose(index, type, viewCamera.surfaceHit(index, at), null)
                    svgActions.pickFile()
                },
                onLoad = {
                    addingPart = null
                    objectMenuActions.loadPart(index, type)
                },
            )
        }
        // The bottoms of the controls at the top and the top of those at the
        // bottom, between which the variable layer height bar stands.
        var topControlsBottom by remember { mutableFloatStateOf(0f) }
        var navigatorBottom by remember { mutableFloatStateOf(0f) }
        var bottomControlsTop by remember { mutableFloatStateOf(Float.NaN) }
        // The canvas runs under the system bars; its controls stay clear of them.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            // OrcaSlicer's 3D navigator and canvas toolbar (the View menu and the zoom button) of its
            // bottom-left corner; a phone keeps them in the top right corner under the toolbar, as
            // mobile CAD apps place their view cube, clear of the plates, Undo and the slice button.
            // The gizmo windows open over them.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = CanvasNavigatorTop, end = CanvasMargin)
                    .onGloballyPositioned { navigatorBottom = it.boundsInParent().bottom },
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
                    assembly = state.assemblyView != null,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // A tool's window stands over the notifications, which its
                    // buttons would otherwise lie under on a phone.
                    .zIndex(1f)
                    .padding(top = 12.dp, start = CanvasMargin, end = CanvasMargin)
                    .onGloballyPositioned { topControlsBottom = it.boundsInParent().bottom },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The toolbar starts after the sidebar button; the gizmo windows below may use the whole width.
                Box(Modifier.padding(start = OrcaSidebarToggleSpace - CanvasMargin)) {
                    if (state.assemblyView != null) {
                        AssemblyViewToolbar(
                            state,
                            assemblyViewActions,
                            onToggleGizmo,
                            onToggleMeasure = measureActions.toggle,
                            onToggleAssembly = assemblyActions.toggle,
                            onTogglePainting = onTogglePainting,
                        )
                    } else CanvasToolbar(
                        state,
                        onTogglePainting,
                        onAddModel,
                        onAddCalibrationCube,
                        plateActions.add,
                        onAutoOrient,
                        onAddInstance,
                        onRemoveInstance,
                        onSplit = { edit -> state.selectedObject?.let { objectMenuActions.edit(it, edit) } },
                        arrangeActions.toggle,
                        onToggleGizmo,
                        onToggleCut = cutActions.toggle,
                        onToggleLayerEditing = layerActions.toggle,
                        onToggleText = {
                            // GLGizmoEmboss::on_shortcut_key(): on the selected copy, where its volume nearest the view's centre is hit.
                            val hit = state.selectedObject?.let { viewCamera.surfaceHit(it) }
                            textActions.toggle(hit, viewCamera.bedPoint(), defaultText)
                        },
                        onToggleMeasure = measureActions.toggle,
                        onToggleBrimEars = brimEarsActions.toggle,
                        onToggleMeshBoolean = meshBooleanActions.toggle,
                        onToggleAssembly = assemblyActions.toggle,
                        onOpenAssemblyView = assemblyViewActions.open,
                        selectionMode = selectionMode,
                        onToggleSelectionMode = { selectionMode = !selectionMode },
                    )
                }
                // _render_paint_toolbar(): the assembly view's filament buttons.
                if (state.assemblyView != null) AssemblyPaintToolbar(state, assemblyViewActions.fillColor)
                val position = state.selectedPosition
                val rotation = state.selectedRotation
                val scale = state.selectedScale
                val size = state.selectedSize
                // A tool's window keeps folded while the engine rewrites the volume it works on,
                // when the window leaves the screen for a moment; it opens unfolded again.
                val toolWindowFolds = remember { mutableStateMapOf<String, Boolean>() }
                LaunchedEffect(state.toolOpen) { if (!state.toolOpen) toolWindowFolds.clear() }
                CompositionLocalProvider(LocalToolWindowFolds provides toolWindowFolds) {
                    when {
                        state.simplify != null -> SimplifyPanel(
                            state.sceneCopies.firstOrNull { it.id.mesh == state.simplify.volume.mesh }?.plateObject,
                            state.simplify,
                            simplifyActions,
                        )
                        state.cut != null -> CutPanel(
                            state.cut,
                            cutActions,
                            imperial = canvas.imperialUnits,
                            plateSize = state.plate?.geometry?.printableArea?.let { area ->
                                maxOf(area.maxOf { it.x } - area.minOf { it.x }, area.maxOf { it.y } - area.minOf { it.y })
                            } ?: 350.0,
                        )
                        state.painting?.kind == PaintKind.COLOR -> PaintingPanel(state, state.painting, paintingActions)
                        state.painting?.kind == PaintKind.SUPPORTS -> SupportPaintingPanel(state.painting, paintingActions)
                        state.painting?.kind == PaintKind.SEAM -> SeamPaintingPanel(state.painting, paintingActions)
                        state.painting?.kind == PaintKind.FUZZY_SKIN -> FuzzySkinPaintingPanel(state.painting, paintingActions)
                        state.text != null -> TextPanel(state.text, textFamilies, textActions, canvas.imperialUnits, eye = viewCamera::eye, modifiersOffered = state.modifiersOffered)
                        state.svg != null -> SvgPanel(state.svg, svgActions, canvas.imperialUnits, eye = viewCamera::eye, modifiersOffered = state.modifiersOffered)
                        state.measure?.assembly != null ->
                            AssemblyPanel(state.measure, measureActions, assemblyActions, canvas.imperialUnits, inAssemblyView = state.assemblyView != null)
                        state.measure != null -> MeasurePanel(state.measure, measureActions, canvas.imperialUnits)
                        state.meshBoolean != null -> MeshBooleanPanel(
                            state.meshBoolean,
                            state.sceneCopies.firstOrNull { it.id == state.meshBoolean.copy }?.plateObject,
                            meshBooleanActions,
                        )
                        state.brimEars != null -> BrimEarsPanel(
                            state.brimEars,
                            ears = (state.brimEars.draft ?: state.sceneCopies.firstOrNull { it.id == state.brimEars.copy }?.plateObject?.brimPoints).orEmpty().size,
                            actions = brimEarsActions,
                        )
                        state.layerEditing != null -> LayerEditingPanel(state.layerEditing, layerActions)
                        state.arrangeOptionsOpen -> ArrangeOptionsPanel(state.arrangeSettings, state.lidar, arrangeActions)
                        state.gizmo == PlateGizmo.SCALE && scale != null && size != null ->
                            ScaleGizmoPanel(state, scale, size, canvas.imperialUnits, scaleActions, onCloseGizmo, onFieldFocus)
                        state.gizmo == PlateGizmo.MOVE && position != null -> MoveGizmoPanel(
                            position = position,
                            imperial = canvas.imperialUnits,
                            objectCoordinates = state.moveObjectCoordinates,
                            canObjectCoordinates = state.canMoveObjectCoordinates,
                            // A volume shows its offset in the object; a copy, how far it moves along its own axes.
                            relative = state.selectedVolume == null,
                            onCoordinates = onSetMoveObjectCoordinates,
                            onSetPosition = onSetPosition,
                            onTranslate = onTranslateInObject,
                            onDone = onCloseGizmo,
                            onFieldFocus = onFieldFocus,
                        )
                        state.gizmo == PlateGizmo.ROTATE && rotation != null -> RotateGizmoPanel(state, rotation, rotationActions, onCloseGizmo, onFieldFocus)
                    }
                }
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

                state.plate != null && state.sceneObjects.isEmpty() -> Text(
                    text = stringResource(R.string.empty_plate),
                    color = OrcaTheme.colors.textSide,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // The variable layer height bar (LayersEditing::get_bar_rect_screen()):
            // the desktop bar fills the canvas's right edge; here it stands at
            // the right between the controls at the top and those at the bottom.
            val layerView = state.layerEditing?.view()
            if (layerView != null && !bottomControlsTop.isNaN()) {
                val density = LocalDensity.current
                val top = maxOf(topControlsBottom, navigatorBottom) + with(density) { CanvasMargin.toPx() }
                val height = bottomControlsTop - with(density) { CanvasMargin.toPx() } - top
                if (height > with(density) { LayerBarMinHeight.toPx() }) {
                    LayerHeightBar(
                        camera = viewCamera,
                        editing = layerView,
                        onPress = layerActions.press,
                        onMove = layerActions.move,
                        onRelease = layerActions.release,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(0, top.roundToInt()) }
                            .padding(end = CanvasMargin)
                            .height(with(density) { height.toDp() })
                            .systemGestureExclusion(),
                    )
                }
            }
            // The plates and Undo at the bottom left, the notifications and the
            // slice button beside them at the bottom right, never over each other;
            // the assembly view's controls over them (_render_assemble_control()).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .onGloballyPositioned { bottomControlsTop = it.boundsInParent().top },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.assemblyView?.let { mode ->
                    AssemblyViewPanel(mode, assemblySelection, assemblyViewActions, painting = state.painting?.kind == PaintKind.COLOR)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // The plates' numbers and icons, under the thumb once there are several; the assembly view has no plates.
                        if (state.plateOrigins.size > 1 && state.assemblyView == null) {
                            PlateStrip(
                                count = state.plateOrigins.size,
                                current = state.currentPlate,
                                onSelect = plateActions.select,
                                enabled = state.canEditPlate,
                                locked = state.lockedPlates,
                            ) { index, dismiss ->
                                // PartPlate's icons (Plater::select_plate_by_hover_id).
                                PlateMenuItems(
                                    actions = PlateIconActions(
                                        delete = { plateActions.delete(index) },
                                        orient = { plateActions.orient(index) },
                                        arrange = { plateActions.arrange(index) },
                                        lock = { plateActions.lock(index) },
                                        settings = {
                                            plateActions.select(index)
                                            customizingPlate = index
                                        },
                                        moveToFront = { plateActions.moveToFront(index) },
                                        rename = { renamingPlate = index },
                                    ),
                                    dismiss = dismiss,
                                    enabled = state.canEditPlate,
                                    locked = index in state.lockedPlates,
                                    deletable = state.canDeletePlate,
                                    first = index == 0,
                                    customized = index in state.customizedPlates,
                                )
                            }
                        }
                        // BBLTopbar's Undo and Redo, which a phone keeps under the thumb over the plate.
                        OrcaCanvasToolbar {
                            OrcaCanvasTool(DesignR.drawable.orca_topbar_undo, orcaString("Undo"), onUndo, enabled = state.canUndo)
                            OrcaCanvasTool(DesignR.drawable.orca_topbar_redo, orcaString("Redo"), onRedo, enabled = state.canRedo)
                        }
                    }
                    Column(
                        // The notifications take the width the plates and Undo leave.
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // GLCanvas3D::_render() renders no notifications over the assembly view.
                        if (state.assemblyView == null) {
                            Notifications(
                                state, canvas.imperialUnits, onCancelSlicing, onDismissProblem, onRepairObject, onJumpTo,
                                actions = notificationActions,
                                showHints = canvas.showHints,
                                onShowHints = { onSetCanvas(AppConfigKeys.SHOW_HINTS, it.toString()) },
                            )
                        }
                        // In a wide window the slice button sits in the tab bar, as on desktop.
                        if (layout == OrcaWindowLayout.Compact) {
                            SliceButton(mode = state.sliceMode, enabled = state.sliceEnabled, onSlice = onSlice, onModeChange = onSliceModeChange)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The type of the documents "Export as one STL/DRC" writes: any file, so the
 * document provider keeps the extension the suggested name has.
 */
private const val MESH_MIME_TYPE = "application/octet-stream"

/** FT_SVG of the desktop app's file dialogs. */
private const val ZIP_MIME_TYPE = "application/zip"

private const val SVG_MIME_TYPE = "image/svg+xml"

/** The context menu asked for over the object [index], at [position] in the plate view. */
private data class ObjectMenu(val index: Int, val position: Offset)

/** What the object menu of the 3D view does to the copy at an index of the scene. */
internal class PrepareObjectMenuActions(
    val addInstance: (index: Int) -> Unit,
    val removeInstance: (index: Int) -> Unit,
    val setNumberOfInstances: (index: Int, number: Int) -> Unit,
    val manipulate: (index: Int, Manipulation) -> Unit,
    val addPart: (index: Int, shape: String, type: VolumeType, name: String) -> Unit,
    val addHeightRange: (index: Int) -> Unit,
    val edit: (index: Int, ObjectEdit) -> Unit,
    val fillBed: (index: Int) -> Unit,
    val setAsIndividual: (index: Int) -> Unit,
    val clone: (index: Int, count: Int, arrange: Boolean) -> Unit,
    val cut: (index: Int) -> Unit,
    val copy: (index: Int) -> Unit,
    val paste: (index: Int) -> Unit,
    /** "Simplify Model": the gizmo opens on the object. */
    val simplify: (index: Int) -> Unit,
    val setPrintable: (index: Int, Boolean) -> Unit,
    val setFilament: (index: Int, filament: Int) -> Unit,
    val toggleFlushOption: (index: Int, FlushOption) -> Unit,
    val editProcessSettings: (index: Int) -> Unit,
    val copyProcessSettings: (index: Int) -> Unit,
    val pasteProcessSettings: (index: Int) -> Unit,
    /** Opens the document picker for the object's new mesh. */
    val replace: (index: Int) -> Unit,
    /** Opens the folder picker whose files replace the object's volumes. */
    val replaceAll: (index: Int) -> Unit,
    /** Opens where the object is written, suggesting a file named after it. */
    val export: (index: Int, MeshFormat, name: String) -> Unit,
    /** "Invalidate cut info" of a part of a cut. */
    val invalidateCutInfo: (index: Int) -> Unit = {},
    /** "Load..." of the submenus that add a part: opens the file picker for the volumes. */
    val loadPart: (index: Int, type: VolumeType) -> Unit = { _, _ -> },
    /** Plater::reload_from_disk() of the object's volumes. */
    val reloadFromDisk: (index: Int) -> Unit = {},
    /** The multi-selection menu's items over the selected objects. */
    val selection: SelectionMenuActions? = null,
    /** The part menu's items over the volume the canvas selected alone. */
    val part: PreparePartMenuActions? = null,
    /** The menu of several volumes of one object over the copy at the index of the scene, which the canvas picked them on. */
    val volumes: ((index: Int) -> VolumesMenuActions)? = null,
    /** "Edit in Parameter Table" of the object and the part menus: the table opens on the item's row, or on none. */
    val openParameterTable: (SettingsItem?) -> Unit = {},
) {
    companion object {
        val NONE = PrepareObjectMenuActions(
            {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, { _, _ -> }, {}, {}, { _, _, _ -> }, {}, {}, {}, {},
            { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, { _, _, _ -> },
        )
    }
}

/**
 * What the part menu of the 3D view does to a volume selected alone
 * (Selection::Volume): by the volume itself, or by its index in the object
 * over the copy at an index of the scene, which the canvas picked it on.
 */
internal class PreparePartMenuActions(
    /** ObjectList::rename_item() of the volume. */
    val rename: (ObjectPartId, name: String) -> Unit,
    /** Cut, or Copy, of the volume over the copy. */
    val copy: (index: Int, volume: Int, cut: Boolean) -> Unit,
    val delete: (ObjectPartId) -> Unit,
    /** The commands that change the volume's mesh. */
    val edit: (ObjectPartId, ObjectEdit) -> Unit,
    /** "Simplify Model": the gizmo opens on the volume. */
    val simplify: (index: Int, volume: Int) -> Unit,
    /** Center, Drop and Mirror of the volume in the world, by its box over the copy. */
    val center: (index: Int, volume: Int) -> Unit,
    val drop: (index: Int, volume: Int) -> Unit,
    val mirror: (index: Int, volume: Int, Axis) -> Unit,
    val editProcessSettings: (index: Int, volume: Int) -> Unit,
    val copyProcessSettings: (ObjectPartId) -> Unit,
    val pasteProcessSettings: (ObjectPartId) -> Unit,
    val changeType: (ObjectPartId, VolumeType) -> Unit,
    val reloadFromDisk: (ObjectPartId) -> Unit,
    /** Opens the document picker for the volume's new mesh. */
    val replace: (index: Int, volume: Int) -> Unit,
    /** "Change Filament", 0 for the object's. */
    val setFilament: (ObjectPartId, filament: Int) -> Unit,
)

/** What the plates of the 3D view do: selected, added after the last one, and what their icons do. */
internal class PlateActions(
    val select: (index: Int) -> Unit,
    val add: () -> Unit,
    val delete: (index: Int) -> Unit,
    val lock: (index: Int) -> Unit,
    val rename: (index: Int, name: String) -> Unit,
    val moveToFront: (index: Int) -> Unit,
    val orient: (index: Int) -> Unit,
    val arrange: (index: Int) -> Unit,
    /** PlateSettingsDialog's OK for the current plate, and whether the user agreed to what spiral vase mode needs. */
    val settings: (PlateSettingsChoice, vaseSettingsAgreed: Boolean) -> Unit,
    /** PlateSettingsDialog closed without OK. */
    val cancelSettings: () -> Unit,
) {
    companion object {
        val NONE = PlateActions({}, {}, {}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> }, {})
    }
}

/** What the canvas menu over empty space adds (MenuFactory::create_default_menu). */
internal class PlateMenuActions(
    /** ObjectList::load_shape_object(): a shape of create_mesh() with its translated name. */
    val addPrimitive: (shape: String, name: String) -> Unit,
    val addHandyModel: (HandyModel) -> Unit,
    /** Plater::show_view3D_labels(), which keeps show_labels. */
    val showLabels: (Boolean) -> Unit,
    /** The Edit menu's "Delete all" and "Duplicate Current Plate", which a phone offers over the plate. */
    val deleteAll: () -> Unit = {},
    val duplicatePlate: () -> Unit = {},
    /** The File menu's Import > "Import Zip Archive", which a phone offers with the other ways to add models. */
    val importZip: () -> Unit = {},
    /** The items of a plate's menu (MenuFactory::plate_menu()) that act on the current plate, and its "Replace all with 3D files". */
    val selectPlateObjects: () -> Unit = {},
    val selectAllPlates: () -> Unit = {},
    val deletePlateObjects: () -> Unit = {},
    val reloadAll: () -> Unit = {},
    val replaceAllOnPlate: (Int) -> Unit = {},
) {
    companion object {
        val NONE = PlateMenuActions({ _, _ -> }, {}, {})
    }
}

/**
 * MenuFactory::default_menu(), which the canvas opens over empty space:
 * Add Primitive, Add Handy models and Add Models, then Show Labels, checked
 * while the 3D view shows the objects' names ([labels]). Paste stands first,
 * as a phone offers it where a finger is held, for the Edit menu's Paste.
 */
@Composable
private fun PlateContextMenu(
    state: PrepareUiState,
    position: Offset,
    onDismiss: () -> Unit,
    onAddModel: () -> Unit,
    onPaste: () -> Unit,
    actions: PlateMenuActions,
    labels: Boolean,
    onAddText: () -> Unit = {},
    onAddSvg: () -> Unit = {},
) {
    OrcaContextMenu(
        expanded = true,
        position = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        OrcaMenuItem(
            text = orcaString("Paste"),
            enabled = state.canPasteOnPlate,
            onClick = {
                onDismiss()
                onPaste()
            },
        )
        // MainFrame::can_delete_all(): objects on the plates.
        OrcaMenuItem(
            text = orcaString("Delete all"),
            enabled = state.canEditPlate && state.sceneCopies.isNotEmpty(),
            onClick = {
                onDismiss()
                actions.deleteAll()
            },
        )
        OrcaMenuItem(
            text = orcaString("Duplicate Current Plate"),
            enabled = state.canAddPlate,
            onClick = {
                onDismiss()
                actions.duplicatePlate()
            },
        )
        OrcaMenuSeparator()
        AddObjectItems(
            enabled = state.canEditPlate,
            dismiss = onDismiss,
            addPrimitive = actions.addPrimitive,
            addHandyModel = actions.addHandyModel,
            addModels = onAddModel,
            addText = onAddText,
            addSvg = onAddSvg,
        )
        OrcaMenuItem(
            text = orcaString("Import Zip Archive") + "...",
            enabled = state.canEditPlate,
            onClick = {
                onDismiss()
                actions.importZip()
            },
        )
        OrcaMenuSeparator()
        // Plater::is_view3D_shown(): the assembly view has no labels.
        val shown = state.assemblyView == null
        OrcaMenuCheckItem(
            text = orcaString("Show Labels"),
            checked = labels && shown,
            enabled = shown,
            onClick = {
                onDismiss()
                actions.showLabels(!labels)
            },
        )
    }
}

/**
 * on_plate_right_click(): the menu of the plate a finger held on, which became
 * the current plate first (MenuFactory::plate_menu()), as the object list's
 * plate item opens it. Paste stands first, as in the menu over empty space.
 */
@Composable
private fun PartPlateContextMenu(
    state: PrepareUiState,
    plate: Int,
    position: Offset,
    onDismiss: () -> Unit,
    onPaste: () -> Unit,
    actions: PartPlateMenuActions,
) {
    OrcaContextMenu(
        expanded = true,
        position = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        OrcaMenuItem(
            text = orcaString("Paste"),
            enabled = state.canPasteOnPlate,
            onClick = {
                onDismiss()
                onPaste()
            },
        )
        OrcaMenuSeparator()
        val current = state.canEditPlate && plate == state.currentPlate
        PartPlateMenuItems(
            state = PartPlateMenuState(
                index = plate,
                current = current,
                occupied = current && state.currentPlateCopies.isNotEmpty(),
                locked = plate in state.lockedPlates,
                deletable = state.canDeletePlate,
                anyObjects = state.sceneObjects.isNotEmpty(),
                enabled = state.canEditPlate,
            ),
            actions = actions,
            dismiss = onDismiss,
        )
    }
}

/** The object menu the canvas opens over a copy (Plater::priv::on_right_click, MenuFactory::object_menu). */
@Composable
private fun ObjectContextMenu(
    state: PrepareUiState,
    menu: ObjectMenu,
    onDismiss: () -> Unit,
    onSetAutoDrop: (index: Int, enabled: Boolean) -> Unit,
    onDeleteObject: (index: Int) -> Unit,
    actions: PrepareObjectMenuActions,
    onChooseShape: (VolumeType) -> Unit,
    onAskNumberOfInstances: () -> Unit,
    onAskClone: () -> Unit,
    onEditText: (ObjectPartId) -> Unit = {},
    onEditSvg: (ObjectPartId) -> Unit = {},
    /** The part menu's Rename: the canvas asks for the volume's name. */
    onAskRenameVolume: (ObjectPartId, name: String) -> Unit = { _, _ -> },
) {
    val copy = state.sceneCopies.getOrNull(menu.index)
    OrcaContextMenu(
        expanded = copy != null,
        position = IntOffset(menu.position.x.roundToInt(), menu.position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        if (copy == null) return@OrcaContextMenu
        val index = menu.index
        val filaments = state.filamentNames.zip(state.filamentColors) { filament, color ->
            MenuFilament(filament, Color(color.red, color.green, color.blue, color.alpha))
        }
        // Plater::priv::on_right_click() over a copy of a selection of several objects.
        val selection = actions.selection
        val selectionMenu = state.selectionMenu
        if (selection != null && selectionMenu != null && index in state.selectedObjects) {
            SelectionMenuItems(
                state = selectionMenu.copy(filaments = filaments.takeIf { it.size > 1 }.orEmpty()),
                actions = selection,
                dismiss = onDismiss,
                onClone = onAskClone,
            )
            return@OrcaContextMenu
        }
        // Plater::priv::on_right_click() over the copy whose volumes are selected together
        // (Selection::is_multiple_volume()): multi_selection_menu()'s branch of several volumes.
        val volumes = actions.volumes
        val group = state.volumeGroup
        if (volumes != null && group.size > 1 && index == state.selectedObject && group.first().mesh == copy.id.mesh) {
            VolumesMenuItems(
                volumesMenuState(
                    copy.plateObject,
                    group.map(ObjectPartId::index),
                    state.canEditPlate,
                    filaments = filaments.takeIf { it.size > 1 }.orEmpty(),
                    settingsClipboard = state.settingsClipboard,
                ),
                volumes(index),
                onDismiss,
            )
            return@OrcaContextMenu
        }
        // Plater::priv::on_right_click() over the copy whose volume is selected alone
        // (Selection::is_single_volume() or is_single_modifier()): MenuFactory::part_menu(),
        // or text_part_menu() and svg_part_menu() of a volume embossed from a text or an SVG.
        val part = actions.part
        val volume = state.selectedVolume?.id?.takeIf { index == state.selectedObject && it.mesh == copy.id.mesh }
        val partMenu = volume?.let {
            partMenuState(
                copy.plateObject,
                it.index,
                copy.instance,
                state.canEditPlate,
                clipboard = state.clipboard,
                simplifying = state.simplify != null,
                filaments = filaments,
                settingsClipboard = state.settingsClipboard,
            )
        }
        if (part != null && volume != null && partMenu != null) {
            val at = volume.index
            val volumeName = copy.plateObject.volumeName(at)
            PartMenuItems(
                partMenu,
                PartMenuActions(
                    rename = { onAskRenameVolume(volume, volumeName) },
                    // The Edit menu for the volume over the copy the canvas picked it on.
                    cut = { part.copy(index, at, true) },
                    copy = { part.copy(index, at, false) },
                    paste = { actions.paste(index) },
                    editText = { onEditText(volume) },
                    editSvg = { onEditSvg(volume) },
                    delete = { part.delete(volume) },
                    edit = { part.edit(volume, it) },
                    simplify = { part.simplify(index, at) },
                    center = { part.center(index, at) },
                    drop = { part.drop(index, at) },
                    mirror = { part.mirror(index, at, it) },
                    editProcessSettings = { part.editProcessSettings(index, at) },
                    copyProcessSettings = { part.copyProcessSettings(volume) },
                    pasteProcessSettings = { part.pasteProcessSettings(volume) },
                    changeType = { part.changeType(volume, it) },
                    reloadFromDisk = { part.reloadFromDisk(volume) },
                    replace = { part.replace(index, at) },
                    setFilament = { part.setFilament(volume, it) },
                    editInParameterTable = { actions.openParameterTable(SettingsItem.Volume(volume)) },
                ),
                onDismiss,
            )
            return@OrcaContextMenu
        }
        val name = copy.plateObject.displayName()
        ObjectMenuItems(
            state = objectMenuState(
                copy.plateObject,
                copy.instance,
                state.plate,
                state.canEditPlate,
                clipboard = state.clipboard,
                listClipboard = state.listClipboard,
                simplifying = state.simplify != null,
                filaments = filaments,
                flushing = state.flushing,
                settingsClipboard = state.settingsClipboard,
            ),
            actions = ObjectMenuActions(
                cut = { actions.cut(index) },
                copy = { actions.copy(index) },
                paste = { actions.paste(index) },
                addInstance = { actions.addInstance(index) },
                removeInstance = { actions.removeInstance(index) },
                setNumberOfInstances = onAskNumberOfInstances,
                fillBedWithInstances = { actions.fillBed(index) },
                setAsIndividual = { actions.setAsIndividual(index) },
                clone = onAskClone,
                center = { actions.manipulate(index, Manipulation.Center) },
                drop = { actions.manipulate(index, Manipulation.Drop) },
                mirror = { actions.manipulate(index, Manipulation.Mirror(it)) },
                delete = { onDeleteObject(index) },
                addPart = onChooseShape,
                addHeightRange = { actions.addHeightRange(index) },
                setAutoDrop = { onSetAutoDrop(index, it) },
                edit = { actions.edit(index, it) },
                simplify = { actions.simplify(index) },
                setPrintable = { actions.setPrintable(index, it) },
                setFilament = { actions.setFilament(index, it) },
                toggleFlushOption = { actions.toggleFlushOption(index, it) },
                editProcessSettings = { actions.editProcessSettings(index) },
                copyProcessSettings = { actions.copyProcessSettings(index) },
                pasteProcessSettings = { actions.pasteProcessSettings(index) },
                reloadFromDisk = { actions.reloadFromDisk(index) },
                replace = { actions.replace(index) },
                replaceAll = { actions.replaceAll(index) },
                export = { format -> actions.export(index, format, name) },
                invalidateCutInfo = { actions.invalidateCutInfo(index) },
                // append_menu_item_edit_text(): an object made of a text alone.
                editText = ObjectPartId(copy.id.mesh, 0)
                    .takeIf { copy.plateObject.parts.isEmpty() && copy.plateObject.volume.emboss?.kind == EmbossKind.TEXT }
                    ?.let { volume -> { onEditText(volume) } },
                // append_menu_item_edit_svg(): an object made of an SVG alone.
                editSvg = ObjectPartId(copy.id.mesh, 0)
                    .takeIf { copy.plateObject.parts.isEmpty() && copy.plateObject.volume.emboss?.kind == EmbossKind.SVG }
                    ?.let { volume -> { onEditSvg(volume) } },
                // Plater::PopupObjectTableBySelection() of the object list's selection: the object's
                // row, which a copy of several selected alone leaves to none (its instance item).
                editInParameterTable = {
                    actions.openParameterTable(SettingsItem.Object(copy.id.mesh).takeIf { copy.plateObject.instances.size == 1 })
                },
            ),
            dismiss = onDismiss,
        )
    }
}

/** Notifications of the canvas: problems, progress, the object info. */
@Composable
private fun Notifications(
    state: PrepareUiState,
    imperial: Boolean,
    onCancelSlicing: () -> Unit,
    onDismissProblem: () -> Unit,
    onRepairObject: () -> Unit,
    onJumpTo: (ValidationNotice) -> Unit,
    actions: NotificationActions = NotificationActions.NONE,
    /** show_hints: whether the daily tips under the slicing progress are expanded, and its keeping. */
    showHints: Boolean = false,
    onShowHints: (Boolean) -> Unit = {},
) {
    // update_background_process() pushes the validation's messages again after
    // every change of the plate, and reload_scene() the clash: a closed one
    // shows again once the objects change.
    state.validationWarning?.let { notice ->
        ValidationNotification(OrcaNotificationLevel.Warning, orcaString("WARNING:"), notice.text, notice.targetObject?.displayName(), notice.option, state.sceneObjects) { onJumpTo(notice) }
    }
    state.validationError?.let { notice ->
        ValidationNotification(OrcaNotificationLevel.Error, orcaString("Error:"), notice.text, notice.targetObject?.displayName(), notice.option, state.sceneObjects) { onJumpTo(notice) }
    }
    if (state.clashedObjects.isNotEmpty()) ObjectClashedNotification(state.clashedObjects.map { it.displayName() }, state.sceneObjects)
    // Plater::priv::on_slicing_update() and GLCanvas3D::_update_slice_error_status()
    // of the current plate's G-code.
    state.sliceNotices.forEach { view ->
        SliceNoticeNotification(view.notice, view.jump?.targetObject?.displayName(), onJumpTo = { view.jump?.let(onJumpTo) })
    }
    if (state.postProcessSkipped) PostProcessSkippedNotification()
    // GLCanvas3D::reload_scene(): the plate's tower and filaments, as the 3D
    // editor's warnings (push_plater_warning_notification() and the
    // customized ones, "Warning:" above the text).
    if (state.primeTowerOutside) PlaterWarningNotification(orcaString("The prime tower extends beyond the plate boundary."))
    if (state.somethingNotShown) PlaterWarningNotification(orcaString("Only the object being edited is visible."))
    state.text?.takeIf { it.unknownFont && !it.busy }?.let { text ->
        val name = text.style.faceName.ifEmpty { text.style.fontPath }
        var closed by remember(name) { mutableStateOf(false) }
        if (!closed) UnknownFontNotification(name) { closed = true }
    }
    if (state.seqPrintInfo) SeqPrintInfoNotification(onClose = actions.closeSeqPrintInfo)
    state.exportFinished?.let { name -> ExportFinishedNotification(name, onClose = actions.closeExportFinished) }
    state.noticeNotifications.forEach { notice -> NoticeNotification(notice, onClose = { actions.closeNotice(notice) }) }
    state.simplifySuggestions.forEach { target ->
        SimplifySuggestionNotification(
            target.displayName(),
            onSimplify = { actions.simplify(target.mesh) },
            onClose = { actions.closeSimplifySuggestion(target.mesh) },
        )
    }
    if (state.profileUpdates?.notified == true) {
        ProfileUpdateAvailableNotification(onDetail = { actions.profileUpdates(true) }, onClose = { actions.profileUpdates(false) })
    }
    state.profileUpdatesInstalled.forEach { update ->
        ProfileUpdateFinishedNotification(update.vendor, update.version, onClose = { actions.closeProfileUpdateInstalled(update) })
    }
    // GLGizmoMmuSegmentation::on_opening(): show_notification_extruders_limit_exceeded().
    if (state.painting?.kind == PaintKind.COLOR && state.filamentColors.size > MMU_EXTRUDERS_LIMIT) {
        var closed by remember { mutableStateOf(false) }
        if (!closed) {
            OrcaNotification(onClose = { closed = true }) {
                OrcaNotificationText(
                    orcaText(
                        OrcaText(
                            "Filament count exceeds the maximum number that painting tool supports. Only the first %1% filaments will be available in painting tool.",
                            listOf(MMU_EXTRUDERS_LIMIT.toString()),
                        ),
                    ),
                )
            }
        }
    }
    state.plateNotices.forEach { PlateNoticeNotification(it) }
    state.problem?.let { problem ->
        val named = problem.objects.mapNotNull { mesh -> state.sceneObjects.firstOrNull { it.mesh == mesh } }
        PlateProblemNotification(problem, named.map { it.displayName() }, onJumpTo = { actions.jumpToObjects(problem.objects) }, onClose = onDismissProblem)
    }
    // ArrangeJob's, OrientJob's and FillBedJob's notifications.
    state.zeroSizeObjects.forEachIndexed { index, name -> ZeroSizeObjectNotification(name) { actions.closeZeroSizeObject(index) } }
    state.arrangeOngoing?.let { job -> ArrangeOngoingNotification(job, actions.closeArrangeOngoing) }
    state.plateJob?.let { PlateJobNotification(it, actions.cancelPlateJob) }
    val slicing = state.slicing
    UpdatedItemsInfoNotification(state.cutPartsLoaded, state.cutPartsLoads)
    SliceCompletedNotification(state.slicesCompleted, sliceRunning = slicing != null) { DailyTipsPanel(showHints, onShowHints) }
    SliceCancelledNotification(state.slicesCancelled, sliceRunning = slicing != null) { DailyTipsPanel(showHints, onShowHints) }
    if (slicing != null) {
        val fraction = slicing.progress?.fraction ?: 0f
        SlicingNotification(
            job = slicing.jobId,
            title = if (slicing.cancelling) {
                stringResource(R.string.slicing_cancelling)
            } else {
                stringResource(R.string.slicing_progress, (fraction * 100).roundToInt())
            },
            detail = slicing.progress?.detail,
            progress = fraction,
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onCancelSlicing,
            showHints = showHints,
            onShowHints = onShowHints,
        )
    } else {
        state.objectInfo?.let { ObjectInfoNotification(it, imperial, onRepairObject) }
    }
}

/** What the buttons and links of the 3D editor's notifications do (NotificationManager). */
internal class NotificationActions(
    /** The close button of the advice to arrange a plate printed by object. */
    val closeSeqPrintInfo: () -> Unit = {},
    /** The export's notification closes. */
    val closeExportFinished: () -> Unit = {},
    /** A notice of the engine shown as a notification closes. */
    val closeNotice: (SettingsDialog) -> Unit = {},
    /** "Simplify model", and the close button, of the advice to simplify an object. */
    val simplify: (ScenePath) -> Unit = {},
    val closeSimplifySuggestion: (ScenePath) -> Unit = {},
    /** "Detail." (true), and the close button, of "Configuration can update now.". */
    val profileUpdates: (detail: Boolean) -> Unit = {},
    /** The close button of a forced update's "Configuration package: ... updated to ...". */
    val closeProfileUpdateInstalled: (ProfileUpdate) -> Unit = {},
    /** "Jump to" of a slicing error: the objects it names. */
    val jumpToObjects: (List<ScenePath>) -> Unit = {},
    /** The Cancel of an arrangement's, an orientation's or a bed fill's progress. */
    val cancelPlateJob: () -> Unit = {},
    /** "Arranging..." of the job numbered so closes, and the warning of an object without area at an index. */
    val closeArrangeOngoing: (Long) -> Unit = {},
    val closeZeroSizeObject: (Int) -> Unit = {},
) {
    companion object {
        val NONE = NotificationActions()
    }
}

/**
 * GLGizmoEmboss::create_notification_not_valid_font(): the text's font is
 * not on the phone, and only another font can be chosen. The phone picks no
 * similar font, so both names are the one the project gave.
 */
@Composable
private fun UnknownFontNotification(name: String, onClose: () -> Unit) {
    OrcaNotification(level = OrcaNotificationLevel.Warning, onClose = onClose) {
        OrcaNotificationText(
            orcaText(
                OrcaText(
                    "Can't load exactly same font (\"%1%\"). Application selected a similar one (\"%2%\"). You have to specify font for enable edit text.",
                    listOf(name, name),
                ),
            ),
        )
    }
}

/**
 * Plater::show_object_info(): the texts of OrcaSlicer's catalogue, line by
 * line, the mesh errors in the error colour; open edges colour the notification
 * as a warning with " (Repair)" (bbl_show_objectsinfo_notification()). It shows
 * every line from the start (set_Multiline(true)); closed, it stays away until
 * the selection or what it tells of it changes.
 */
@Composable
private fun ObjectInfoNotification(info: ObjectInfo, imperial: Boolean, onRepair: () -> Unit) {
    var closed by remember(info) { mutableStateOf(false) }
    if (closed) return
    val close = { closed = true }
    when (info) {
        is ObjectInfo.Count -> OrcaNotification(onClose = close, multiline = true) {
            val text = orcaText(OrcaText("Number of currently selected objects: %1%\n", listOf(info.objects.toString())))
            OrcaNotificationText(text.trimEnd('\n'))
        }
        is ObjectInfo.PartCount -> OrcaNotification(onClose = close, multiline = true) {
            val text = orcaText(OrcaText("Number of currently selected parts: %1%\n", listOf(info.parts.toString())))
            OrcaNotificationText(text.trimEnd('\n'))
        }
        is ObjectInfo.Single -> {
            val koef = if (imperial) ImperialUnits.MM_TO_IN else 1.0
            val name = info.part?.let { info.plateObject.volumeName(it) } ?: info.plateObject.displayName()
            val text = orcaText(
                listOf(
                    OrcaText(if (info.part != null) "Part name: %1%\n" else "Object name: %1%\n", listOf(name)),
                    OrcaText(
                        if (imperial) "Size: %1% x %2% x %3% in\n" else "Size: %1% x %2% x %3% mm\n",
                        listOf(info.size.x, info.size.y, info.size.z).map { cppNumber(it * koef) },
                    ),
                    OrcaText(if (imperial) "Volume: %1% in³\n" else "Volume: %1% mm³\n", listOf(cppNumber(info.volume * koef.pow(3)))),
                    OrcaText("Triangles: %1%\n", listOf(info.facets.toString())),
                ),
            )
            // ObjectList::get_mesh_errors_info(): the errors the repair fixed, and
            // the open edges before them with the tip while there are any.
            val repaired = if (info.repairedErrors > 0) {
                orcaText(
                    OrcaText(
                        "%1\$d error repaired",
                        listOf(info.repairedErrors.toString()),
                        msgidPlural = "%1\$d errors repaired",
                        count = info.repairedErrors.toLong(),
                    ),
                )
            } else {
                ""
            }
            val errors = if (info.openEdges > 0) {
                orcaText(
                    OrcaText(
                        "Error: %1\$d non-manifold edge.",
                        listOf(info.openEdges.toString()),
                        msgidPlural = "Error: %1\$d non-manifold edges.",
                        count = info.openEdges,
                    ),
                ) + (if (repaired.isNotEmpty()) "\n" + repaired else "") +
                    "\n" + orcaString("Tips:") + "\n" + orcaString("Use \"Fix Model\" to repair the mesh.")
            } else {
                repaired
            }
            OrcaNotification(
                level = if (info.openEdges > 0) OrcaNotificationLevel.Warning else OrcaNotificationLevel.Regular,
                onClose = close,
                multiline = true,
            ) {
                text.trimEnd('\n').split('\n').forEachIndexed { index, line -> OrcaNotificationText(line, emphasized = index == 0) }
                if (errors.isNotEmpty()) {
                    errors.split('\n').forEach { OrcaNotificationText(it, error = true) }
                }
                // bbl_show_objectsinfo_notification(): " (Repair)" while there are open edges.
                if (info.openEdges > 0) {
                    OrcaNotificationLink(orcaString(" (Repair)").trim(), onClick = onRepair)
                }
            }
        }
    }
}

/**
 * OrcaSlicer's toolbars over the 3D view, in its order: the main toolbar
 * (GLCanvas3D::_init_main_toolbar), a separator, the gizmos a single-filament
 * FFF printer shows (GLGizmosManager::get_selectable_idxs), a separator, and
 * the assembly view toolbar. The calibration cube follows Add. Tools whose
 * features the app does not have yet stay disabled.
 */
@Composable
private fun CanvasToolbar(
    state: PrepareUiState,
    onTogglePainting: (PaintKind) -> Unit,
    onAddModel: () -> Unit,
    onAddCalibrationCube: () -> Unit,
    onAddPlate: () -> Unit,
    onAutoOrient: () -> Unit,
    onAddInstance: () -> Unit,
    onRemoveInstance: () -> Unit,
    onSplit: (ObjectEdit) -> Unit,
    onToggleArrange: () -> Unit,
    onToggleGizmo: (PlateGizmo) -> Unit,
    onToggleCut: () -> Unit = {},
    onToggleLayerEditing: () -> Unit = {},
    onToggleText: () -> Unit = {},
    onToggleMeasure: () -> Unit = {},
    onToggleBrimEars: () -> Unit = {},
    onToggleMeshBoolean: () -> Unit = {},
    onToggleAssembly: () -> Unit = {},
    onOpenAssemblyView: () -> Unit = {},
    selectionMode: Boolean = false,
    onToggleSelectionMode: () -> Unit = {},
) {
    @Composable
    fun gizmo(icon: Int, name: Int, gizmo: PlateGizmo?) = OrcaCanvasTool(
        icon = icon,
        contentDescription = stringResource(name),
        onClick = { gizmo?.let(onToggleGizmo) },
        enabled = gizmo != null && state.canOpen(gizmo),
        selected = gizmo != null && state.gizmo == gizmo,
    )

    OrcaCanvasToolbar {
        // A phone's Ctrl and Shift for the canvas's selection: a tap adds a copy or takes it out,
        // and a finger over empty space draws the selection rectangle (GLSelectionRectangle).
        OrcaCanvasTool(
            icon = R.drawable.selection_mode,
            contentDescription = stringResource(R.string.toolbar_selection_mode),
            onClick = onToggleSelectionMode,
            selected = selectionMode,
        )
        OrcaCanvasToolbarSeparator()
        OrcaCanvasTool(DesignR.drawable.orca_toolbar_open, stringResource(R.string.add_model), onAddModel, enabled = state.canEditPlate)
        OrcaCanvasTool(DesignR.drawable.orca_tab_3d_active, stringResource(R.string.add_calibration_cube), onAddCalibrationCube, enabled = state.canEditPlate)
        OrcaCanvasTool(DesignR.drawable.orca_toolbar_add_plate, stringResource(R.string.toolbar_add_plate), onAddPlate, enabled = state.canAddPlate)
        OrcaCanvasTool(DesignR.drawable.orca_toolbar_orient, stringResource(R.string.toolbar_orient), onAutoOrient, enabled = state.canArrange)
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_arrange,
            contentDescription = stringResource(R.string.toolbar_arrange),
            onClick = onToggleArrange,
            enabled = state.canArrange,
            selected = state.arrangeOptionsOpen,
        )
        OrcaCanvasToolbarSeparator()
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_instance_add,
            contentDescription = stringResource(R.string.toolbar_add_instance),
            onClick = onAddInstance,
            enabled = state.canCopy,
        )
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_instance_remove,
            contentDescription = stringResource(R.string.toolbar_remove_instance),
            onClick = onRemoveInstance,
            enabled = state.canRemoveCopy,
        )
        // Plater::split_object() and split_volume() of the selected object.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_split_objects,
            contentDescription = stringResource(R.string.toolbar_split_objects),
            onClick = { onSplit(ObjectEdit.SPLIT_TO_OBJECTS) },
            enabled = state.canSplitToObjects,
        )
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_split_parts,
            contentDescription = stringResource(R.string.toolbar_split_parts),
            onClick = { onSplit(ObjectEdit.SPLIT_TO_PARTS) },
            enabled = state.canSplitToParts,
        )
        // Plater::can_layers_editing(): one object selected, standing above the bed.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_variable_layer_height,
            contentDescription = stringResource(R.string.toolbar_variable_layer_height),
            onClick = onToggleLayerEditing,
            enabled = state.canEditLayers || state.layerEditing != null,
            selected = state.layerEditing != null,
        )
        OrcaCanvasToolbarSeparator()
        gizmo(DesignR.drawable.orca_toolbar_move, R.string.gizmo_move, PlateGizmo.MOVE)
        gizmo(DesignR.drawable.orca_toolbar_rotate, R.string.gizmo_rotate, PlateGizmo.ROTATE)
        gizmo(DesignR.drawable.orca_toolbar_scale, R.string.gizmo_scale, PlateGizmo.SCALE)
        gizmo(DesignR.drawable.orca_toolbar_flatten, R.string.gizmo_lay_on_face, PlateGizmo.LAY_ON_FACE)
        // GLGizmoCut3D: on_is_activable() is a single full instance selected,
        // but for the dowel a cut made an object of; simple mode has no Cut.
        if (state.cutSelectable) {
            OrcaCanvasTool(
                icon = DesignR.drawable.orca_toolbar_cut,
                contentDescription = stringResource(R.string.gizmo_cut),
                onClick = onToggleCut,
                enabled = state.canCut,
                selected = state.cut != null,
            )
        }
        // GLGizmoMeshBoolean: two volumes of the selected copy joined, subtracted or intersected.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_meshboolean,
            contentDescription = stringResource(R.string.gizmo_mesh_boolean),
            onClick = onToggleMeshBoolean,
            enabled = state.canMeshBoolean || state.meshBoolean != null,
            selected = state.meshBoolean != null,
        )
        // GLGizmoFdmSupports: supports are enforced or blocked where they are painted.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_support,
            contentDescription = stringResource(R.string.gizmo_support_painting),
            onClick = { onTogglePainting(PaintKind.SUPPORTS) },
            enabled = state.canPaintFacets,
            selected = state.painting?.kind == PaintKind.SUPPORTS,
        )
        // GLGizmoSeam: the seam is enforced or blocked where it is painted.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_seam,
            contentDescription = stringResource(R.string.gizmo_seam_painting),
            onClick = { onTogglePainting(PaintKind.SEAM) },
            enabled = state.canPaintFacets,
            selected = state.painting?.kind == PaintKind.SEAM,
        )
        // GLGizmoFuzzySkin: the walls get fuzzy skin where it is painted.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_fuzzy_skin_paint,
            contentDescription = stringResource(R.string.gizmo_fuzzy_skin_painting),
            onClick = { onTogglePainting(PaintKind.FUZZY_SKIN) },
            enabled = state.canPaintFacets,
            selected = state.painting?.kind == PaintKind.FUZZY_SKIN,
        )
        // GLGizmoMmuSegmentation: the object is painted with the filaments of the plate;
        // on_is_selectable(): the toolbar has it with more than one filament.
        if (state.filamentColors.size > 1) {
            OrcaCanvasTool(
                icon = DesignR.drawable.orca_mmu_segmentation,
                contentDescription = stringResource(R.string.gizmo_color_painting),
                onClick = { onTogglePainting(PaintKind.COLOR) },
                enabled = state.canPaint,
                selected = state.painting?.kind == PaintKind.COLOR,
            )
        }
        // GLGizmoEmboss: the tool opens on the selected text, or adds a text.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_text,
            contentDescription = stringResource(R.string.gizmo_emboss),
            onClick = onToggleText,
            enabled = state.canEditPlate,
            selected = state.text != null,
        )
        // GLGizmoMeasure: the selected volumes are measured.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_measure,
            contentDescription = stringResource(R.string.gizmo_measure),
            onClick = onToggleMeasure,
            enabled = state.canMeasure,
            selected = state.measure != null && state.measure.assembly == null,
        )
        // GLGizmoAssembly: two selected volumes are assembled.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_assembly,
            contentDescription = stringResource(R.string.gizmo_assembly),
            onClick = onToggleAssembly,
            enabled = state.canAssemble,
            selected = state.measure?.assembly != null,
        )
        // GLGizmoBrimEars: ears of the brim placed under the selected copy.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_brimears,
            contentDescription = stringResource(R.string.gizmo_brim_ears),
            onClick = onToggleBrimEars,
            enabled = state.canEditBrimEars,
            selected = state.brimEars != null,
        )
        OrcaCanvasToolbarSeparator()
        // The assembly view toolbar's "Assembly View" (EVT_GLVIEWTOOLBAR_ASSEMBLE), which Plater::has_assmeble_view() enables.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_assemble,
            contentDescription = stringResource(R.string.toolbar_assembly_view),
            onClick = onOpenAssemblyView,
            enabled = state.canOpenAssemblyView,
        )
    }
}

/**
 * GLGizmoMmuSegmentation's window while it is open: the filaments, with the
 * eraser in place of the desktop's Shift, the tool — circle, sphere,
 * triangles, height range, fill or gap fill — with its brush size and
 * "Vertical" / "Horizontal", the fill's "Edge detection" and smart fill angle,
 * the height range's height, or the gap area and "Perform", "Section view",
 * "Erase all" and "Done".
 */
@Composable
private fun PaintingPanel(state: PrepareUiState, painting: PaintingMode, actions: PaintingActions) {
    PaintingPanelFrame(stringResource(R.string.gizmo_color_painting), stringResource(R.string.painting_done), actions.close) {
        Text(
            text = orcaString("Filaments"),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            // GLGizmoMmuSegmentation::EXTRUDERS_LIMIT: the first 16 filaments alone.
            state.filamentColors.take(MMU_EXTRUDERS_LIMIT).forEachIndexed { index, color ->
                val filament = index + 1
                OrcaFilamentSlot(
                    number = filament,
                    color = Color(color.red, color.green, color.blue, color.alpha),
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            width = if (painting.state == filament) 2.dp else 1.dp,
                            color = if (painting.state == filament) OrcaTheme.colors.accent else OrcaTheme.colors.border,
                        )
                        .clickable { actions.setState(filament) },
                )
            }
            // The eraser takes the paint off again, as the desktop gizmo does
            // with Shift (EnforcerBlockerType::NONE).
            OrcaButton(
                text = stringResource(R.string.painting_eraser),
                style = if (painting.state == PaintState.NONE) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
                size = OrcaButtonSize.Compact,
                onClick = { actions.setState(PaintState.NONE) },
            )
        }
        FilamentRemap(state, painting, actions)
        Text(
            text = orcaString("Tool type"),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
        )
        PaintingChoices(
            listOf(
                PaintTool.CIRCLE to orcaString("Circle"),
                PaintTool.BRUSH to orcaString("Sphere"),
                PaintTool.TRIANGLE to orcaString("Triangle"),
                PaintTool.HEIGHT_RANGE to orcaString("Height Range"),
                PaintTool.BUCKET to orcaString("Fill"),
                PaintTool.GAP_FILL to orcaString("Gap Fill"),
            ),
            selected = painting.tool,
            onSelect = actions.setTool,
        )
        when (painting.tool) {
            PaintTool.CIRCLE, PaintTool.BRUSH, PaintTool.TRIANGLE -> {
                if (painting.tool != PaintTool.TRIANGLE) {
                    PaintingSlider(
                        label = orcaString("Brush size"),
                        value = painting.radius.toFloat(),
                        range = painting.radiusMin.toFloat()..BRUSH_MAX,
                        text = String.format(textLocale(), "%.2f", painting.radius),
                        onChange = { actions.setRadius(it.toDouble()) },
                    )
                }
                PaintingCheck(orcaString("Vertical"), painting.verticalOnly, actions.setVerticalOnly)
                PaintingCheck(orcaString("Horizontal"), painting.horizontalOnly, actions.setHorizontalOnly)
            }
            PaintTool.BUCKET -> {
                if (painting.edgeDetection) {
                    PaintingSlider(
                        label = orcaString("Smart fill angle"),
                        value = painting.fillAngle.toFloat(),
                        range = SMART_FILL_ANGLE_MIN..SMART_FILL_ANGLE_MAX,
                        text = String.format(textLocale(), "%.0f°", painting.fillAngle),
                        onChange = { actions.setFillAngle(it.toDouble()) },
                    )
                }
                PaintingCheck(orcaString("Edge detection"), painting.edgeDetection, actions.setEdgeDetection)
            }
            PaintTool.HEIGHT_RANGE -> PaintingSlider(
                label = orcaString("Height range"),
                value = painting.cursorHeight.toFloat(),
                range = CURSOR_HEIGHT_MIN..CURSOR_HEIGHT_MAX,
                text = String.format(textLocale(), "%.2f", painting.cursorHeight) + " " + stringResource(R.string.unit_mm),
                onChange = { actions.setCursorHeight(it.toDouble()) },
            )
            PaintTool.GAP_FILL -> {
                PaintingSlider(
                    label = orcaString("Gap area"),
                    value = painting.gapArea.toFloat(),
                    range = GAP_AREA_MIN..GAP_AREA_MAX,
                    text = String.format(textLocale(), "%.2f", painting.gapArea),
                    onChange = { actions.setGapArea(it.toDouble()) },
                )
                OrcaButton(
                    text = orcaString("Perform"),
                    size = OrcaButtonSize.Compact,
                    onClick = actions.fillGaps,
                )
            }
            PaintTool.FILL -> Unit
        }
        PaintingSection(painting, actions)
        OrcaButton(
            text = orcaString("Erase all"),
            size = OrcaButtonSize.Compact,
            enabled = painting.painted,
            onClick = actions.clear,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * render_filament_remap_ui(): "Remap filaments", folded until opened, with a
 * swatch per filament the object uses, which shows the one it is to become;
 * a tap opens "To:" with every filament. "Remap" applies the mapping, "Reset"
 * drops it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilamentRemap(state: PrepareUiState, painting: PaintingMode, actions: PaintingActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<Int?>(null) }
    val colors = state.filamentColors.take(MMU_EXTRUDERS_LIMIT).map { Color(it.red, it.green, it.blue, it.alpha) }
    val hasMapping = painting.remap.withIndex().any { (source, target) -> source != target }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = orcaString("Remap filaments"),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.weight(1f),
        )
        Text(text = if (open) "▴" else "▾", color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body12)
    }
    if (!open) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.paintedFilaments.forEach { source ->
            val target = painting.remap.getOrElse(source) { source }
            Box {
                OrcaFilamentSlot(
                    number = source + 1,
                    color = colors.getOrElse(source) { OrcaTheme.colors.accent },
                    modifier = Modifier
                        .size(36.dp)
                        .border(width = if (choosing == source) 2.dp else 1.dp, color = if (choosing == source) OrcaTheme.colors.accent else OrcaTheme.colors.border)
                        .clickable { choosing = source },
                )
                // The bubble of the filament it is to become.
                if (target != source) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .background(colors.getOrElse(target) { OrcaTheme.colors.accent }, CircleShape)
                            .border(1.dp, OrcaTheme.colors.border, CircleShape),
                    )
                }
                DropdownMenu(expanded = choosing == source, onDismissRequest = { choosing = null }) {
                    Text(
                        text = orcaString("To:"),
                        color = OrcaTheme.colors.onCanvasPanel,
                        style = OrcaTheme.typography.body12,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        colors.forEachIndexed { index, color ->
                            OrcaFilamentSlot(
                                number = index + 1,
                                color = color,
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(width = if (index == target) 2.dp else 1.dp, color = if (index == target) OrcaTheme.colors.accent else OrcaTheme.colors.border)
                                    .clickable {
                                        actions.setRemap(source, index)
                                        choosing = null
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        OrcaButton(text = orcaString("Remap"), size = OrcaButtonSize.Compact, enabled = hasMapping, onClick = actions.remap)
        if (hasMapping) OrcaButton(text = orcaString("Reset"), size = OrcaButtonSize.Compact, onClick = actions.resetRemap)
    }
}

/**
 * The painting tools' "Section view": its slider from 0 to 1, with "Reset
 * direction" in place of the caption while it clips.
 */
@Composable
private fun PaintingSection(painting: PaintingMode, actions: PaintingActions) {
    if (painting.sectionPosition > 0.0) {
        OrcaButton(
            text = orcaString("Reset direction"),
            size = OrcaButtonSize.Compact,
            onClick = actions.resetSectionDirection,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    PaintingSlider(
        label = orcaString("Section view"),
        value = painting.sectionPosition.toFloat(),
        range = 0f..1f,
        text = String.format(textLocale(), "%.2f", painting.sectionPosition),
        onChange = { actions.setSection(it.toDouble()) },
    )
}

/**
 * GLGizmoFdmSupports' window while it is open: whether the finger enforces or
 * blocks supports or takes them off (the left and right mouse buttons and
 * Shift of the desktop, as buttons a thumb reaches), the tool — circle,
 * sphere, smart fill or gap fill — with its brush size, fill angle or gap
 * area and "Perform", painting on highlighted overhangs only, the overhang
 * highlight, "Erase all" and "Done".
 */
@Composable
private fun SupportPaintingPanel(painting: PaintingMode, actions: PaintingActions) {
    PaintingPanelFrame(stringResource(R.string.gizmo_support_painting), orcaString("Done"), actions.close) {
        PaintingChoices(
            listOf(
                PaintState.ENFORCER to orcaString("Enforce supports"),
                PaintState.BLOCKER to orcaString("Block supports"),
                PaintState.NONE to orcaString("Erase"),
            ),
            selected = painting.state,
            onSelect = actions.setState,
        )
        Text(
            text = orcaString("Tool type"),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
        )
        PaintingChoices(
            listOf(
                PaintTool.CIRCLE to orcaString("Circle"),
                PaintTool.BRUSH to orcaString("Sphere"),
                PaintTool.FILL to orcaString("Fill"),
                PaintTool.GAP_FILL to orcaString("Gap Fill"),
            ),
            selected = painting.tool,
            onSelect = actions.setTool,
        )
        if (painting.tool == PaintTool.GAP_FILL) {
            PaintingSlider(
                label = orcaString("Gap area"),
                value = painting.gapArea.toFloat(),
                range = GAP_AREA_MIN..GAP_AREA_MAX,
                text = String.format(textLocale(), "%.2f", painting.gapArea),
                onChange = { actions.setGapArea(it.toDouble()) },
            )
            OrcaButton(
                text = orcaString("Perform"),
                size = OrcaButtonSize.Compact,
                onClick = actions.fillGaps,
            )
        } else if (painting.tool == PaintTool.FILL) {
            PaintingSlider(
                label = orcaString("Smart fill angle"),
                value = painting.fillAngle.toFloat(),
                range = SMART_FILL_ANGLE_MIN..SMART_FILL_ANGLE_MAX,
                text = String.format(textLocale(), "%.0f°", painting.fillAngle),
                onChange = { actions.setFillAngle(it.toDouble()) },
            )
        } else {
            PaintingSlider(
                label = orcaString("Brush size"),
                value = painting.radius.toFloat(),
                range = painting.radiusMin.toFloat()..BRUSH_MAX,
                text = String.format(textLocale(), "%.2f", painting.radius),
                onChange = { actions.setRadius(it.toDouble()) },
            )
        }
        if (painting.tool != PaintTool.GAP_FILL) {
            // Allows painting only on facets selected by "Highlight overhang areas".
            PaintingCheck(orcaString("On highlighted overhangs only"), painting.overhangsOnly, actions.setOverhangsOnly)
        }
        PaintingSlider(
            label = orcaString("Highlight overhang areas"),
            value = painting.highlightAngle.toFloat(),
            range = 0f..90f,
            text = String.format(textLocale(), "%.0f", painting.highlightAngle),
            onChange = { actions.setHighlightAngle(it.toDouble()) },
        )
        PaintingSection(painting, actions)
        OrcaButton(
            text = orcaString("Erase all"),
            size = OrcaButtonSize.Compact,
            enabled = painting.painted,
            onClick = actions.clear,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * GLGizmoSeam's window while it is open: whether the finger enforces or
 * blocks the seam or takes it off, the circle or the sphere with its brush
 * size, "Vertical", "Erase all" and "Done".
 */
@Composable
private fun SeamPaintingPanel(painting: PaintingMode, actions: PaintingActions) {
    PaintingPanelFrame(stringResource(R.string.gizmo_seam_painting), orcaString("Done"), actions.close) {
        PaintingChoices(
            listOf(
                PaintState.ENFORCER to orcaString("Enforce seam"),
                PaintState.BLOCKER to orcaString("Block seam"),
                PaintState.NONE to orcaString("Erase"),
            ),
            selected = painting.state,
            onSelect = actions.setState,
        )
        Text(
            text = orcaString("Tool type"),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
        )
        PaintingChoices(
            listOf(
                PaintTool.CIRCLE to orcaString("Circle"),
                PaintTool.BRUSH to orcaString("Sphere"),
            ),
            selected = painting.tool,
            onSelect = actions.setTool,
        )
        PaintingSlider(
            label = orcaString("Brush size"),
            value = painting.radius.toFloat(),
            range = painting.radiusMin.toFloat()..BRUSH_MAX,
            text = String.format(textLocale(), "%.2f", painting.radius),
            onChange = { actions.setRadius(it.toDouble()) },
        )
        PaintingCheck(orcaString("Vertical"), painting.verticalOnly, actions.setVerticalOnly)
        PaintingSection(painting, actions)
        OrcaButton(
            text = orcaString("Erase all"),
            size = OrcaButtonSize.Compact,
            enabled = painting.painted,
            onClick = actions.clear,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * GLGizmoFuzzySkin's window while it is open: whether the finger adds fuzzy
 * skin or removes it (the left mouse button and Shift of the desktop, as
 * buttons a thumb reaches), the tool — circle, sphere, triangles or smart
 * fill — with its brush size or fill angle, "Erase all", "Done", and the
 * warning while fuzzy skin is disabled for the object, with the link that
 * enables it.
 */
@Composable
private fun FuzzySkinPaintingPanel(painting: PaintingMode, actions: PaintingActions) {
    PaintingPanelFrame(stringResource(R.string.gizmo_fuzzy_skin_painting), orcaString("Done"), actions.close) {
        PaintingChoices(
            listOf(
                PaintState.ENFORCER to orcaString("Add fuzzy skin"),
                PaintState.NONE to orcaString("Remove fuzzy skin"),
            ),
            selected = painting.state,
            onSelect = actions.setState,
        )
        Text(
            text = orcaString("Tool type"),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
        )
        PaintingChoices(
            listOf(
                PaintTool.CIRCLE to orcaString("Circle"),
                PaintTool.BRUSH to orcaString("Sphere"),
                PaintTool.TRIANGLE to orcaString("Triangle"),
                PaintTool.FILL to orcaString("Fill"),
            ),
            selected = painting.tool,
            onSelect = actions.setTool,
        )
        when (painting.tool) {
            PaintTool.FILL -> PaintingSlider(
                label = orcaString("Smart fill angle"),
                value = painting.fillAngle.toFloat(),
                range = SMART_FILL_ANGLE_MIN..SMART_FILL_ANGLE_MAX,
                text = String.format(textLocale(), "%.0f°", painting.fillAngle),
                onChange = { actions.setFillAngle(it.toDouble()) },
            )
            PaintTool.TRIANGLE -> Unit
            else -> PaintingSlider(
                label = orcaString("Brush size"),
                value = painting.radius.toFloat(),
                range = painting.radiusMin.toFloat()..BRUSH_MAX,
                text = String.format(textLocale(), "%.2f", painting.radius),
                onChange = { actions.setRadius(it.toDouble()) },
            )
        }
        PaintingSection(painting, actions)
        OrcaButton(
            text = orcaString("Erase all"),
            size = OrcaButtonSize.Compact,
            enabled = painting.painted,
            onClick = actions.clear,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (painting.fuzzySkinDisabled) {
            Text(
                text = orcaString("Warning: Fuzzy skin is disabled, painted fuzzy skin will not take effect!"),
                color = OrcaTheme.colors.warning,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(top = 4.dp),
            )
            OrcaLink(text = orcaString("Enable painted fuzzy skin for this object"), onClick = actions.enableFuzzySkin)
        }
    }
}

/** A check box of a painting tool, the whole row its touch target. */
@Composable
internal fun PaintingCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = null)
        Text(
            text = text,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Which tool windows, by title, the finger folded while their tools are open. */
internal val LocalToolWindowFolds = compositionLocalOf<SnapshotStateMap<String, Boolean>?> { null }

/**
 * The window of a painting tool: its title with "Done", and its controls,
 * which fold away into the title row so the finger reaches the model under
 * them; the desktop window stands beside the model instead.
 */
@Composable
internal fun PaintingPanelFrame(title: String, done: String, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val folds = LocalToolWindowFolds.current
    var ownExpanded by rememberSaveable { mutableStateOf(true) }
    val expanded = folds?.let { it[title] != true } ?: ownExpanded
    OrcaGizmoPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = OrcaTheme.colors.onCanvasPanel,
                style = OrcaTheme.typography.head14,
                modifier = Modifier.weight(1f),
            )
            OrcaIconButton(
                icon = DesignR.drawable.orca_drop_down,
                contentDescription = stringResource(if (expanded) R.string.painting_collapse else R.string.painting_expand),
                onClick = { if (folds != null) folds[title] = expanded else ownExpanded = !ownExpanded },
                tint = OrcaTheme.colors.onCanvasPanel,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
            OrcaButton(
                text = done,
                size = OrcaButtonSize.Compact,
                onClick = onDone,
            )
        }
        if (expanded) {
            // A window taller than the phone's canvas scrolls, as ImGui's
            // window would grow past it, so its buttons stay in reach.
            Column(
                Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * TOOL_WINDOW_HEIGHT).dp)
                    .verticalScroll(rememberScrollState()),
            ) { content() }
        }
    }
}

/** How much of the screen's height a tool's window takes at most before it scrolls. */
private const val TOOL_WINDOW_HEIGHT = 0.6f

/** A painting tool's choices, the chosen one filled, wrapping onto more rows as the window needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> PaintingChoices(items: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        items.forEach { (item, label) ->
            OrcaButton(
                text = label,
                style = if (item == selected) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
                size = OrcaButtonSize.Compact,
                onClick = { onSelect(item) },
            )
        }
    }
}

/** A slider of a painting tool with its label before it and its value after it. */
@Composable
internal fun PaintingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
    onFinished: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDimmed,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(end = 8.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** GLGizmoPainterBase::CursorRadiusMax; the least is the tool's own ([PaintingMode.radiusMin]). */
private const val BRUSH_MAX = 8.0f

/** GLGizmoPainterBase::SmartFillAngleMin and SmartFillAngleMax. */
private const val SMART_FILL_ANGLE_MIN = 0f
private const val SMART_FILL_ANGLE_MAX = 90f

/** GLGizmoPainterBase::CursorHeightMin and CursorHeightMax. */
private const val CURSOR_HEIGHT_MIN = 0.1f
private const val CURSOR_HEIGHT_MAX = 8f

/** TriangleSelectorPatch::GapAreaMin and GapAreaMax. */
private const val GAP_AREA_MIN = 0f
private const val GAP_AREA_MAX = 5f

/**
 * GizmoObjectManipulation::do_render_move_window() in world coordinates: the
 * object's position per axis, applied when an input is done, and the button
 * that closes the gizmo. [imperial] units show and take it in inches. In
 * object coordinates a copy is moved by a distance along its own axes
 * ([relative]), a volume takes a position in the object.
 */
@Composable
private fun MoveGizmoPanel(
    position: ObjectPosition,
    imperial: Boolean,
    objectCoordinates: Boolean,
    canObjectCoordinates: Boolean,
    relative: Boolean,
    onCoordinates: (Boolean) -> Unit,
    onSetPosition: (axis: Int, value: Double) -> Unit,
    onTranslate: (axis: Int, value: Double) -> Unit,
    onDone: () -> Unit,
    onFieldFocus: (SidebarField, Int, Boolean) -> Unit = { _, _, _ -> },
) {
    OrcaGizmoPanel {
        // do_render_move_window(): "World coordinates" and, for a single copy, "Object coordinates".
        val modes = if (canObjectCoordinates) listOf(false, true) else listOf(false)
        val world = orcaString("World coordinates")
        val objectCs = orcaString("Object coordinates")
        OrcaComboBox(
            items = modes,
            selected = objectCoordinates && canObjectCoordinates,
            label = { if (it) objectCs else world },
            onSelect = onCoordinates,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // The captions' column is as wide as both captions need, as the window measures them.
        val translate = orcaString("Translate(Relative)")
        // A group's "Position" starts at zero and moves it by what is typed.
        val positionCaption = stringResource(R.string.gizmo_position)
        val labelWidth = captionWidth(listOf(translate, positionCaption), PositionLabelWidth)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(labelWidth))
            AxisHeaders()
        }
        if (objectCoordinates && relative) {
            // In object coordinates the copy moves by what is typed along its own axes.
            GizmoValueRow(
                label = translate,
                values = listOf(0.0, 0.0, 0.0),
                unit = lengthUnit(imperial),
                labelWidth = labelWidth,
                onValue = { axis, value -> onTranslate(axis, value * inputKoef(imperial)) },
                onFocus = { axis, focused -> onFieldFocus(SidebarField.POSITION, axis, focused) },
            )
        } else {
            GizmoValueRow(
                label = positionCaption,
                values = listOf(position.x, position.y, position.z).map { it * displayKoef(imperial) },
                unit = lengthUnit(imperial),
                labelWidth = labelWidth,
                onValue = { axis, value -> onSetPosition(axis, value * inputKoef(imperial)) },
                onFocus = { axis, focused -> onFieldFocus(SidebarField.POSITION, axis, focused) },
            )
        }
        GizmoPanelFooter(onDone)
    }
}

/** What the arrange options window does. */
internal class ArrangeActions(
    val toggle: () -> Unit,
    val change: (ArrangeSettings) -> Unit,
    val reset: () -> Unit,
    val arrange: () -> Unit,
    /** The window over a printer without the lidar turns "Avoid extrusion calibration region" off. */
    val clearCalibrationRegion: () -> Unit = {},
)

/**
 * GLCanvas3D::_render_arrange_menu() for FFF printers: the spacing slider and
 * its input, auto spacing at 0, the rotation, materials, and Y-axis options,
 * and the Arrange and Reset buttons. "Avoid extrusion calibration region"
 * shows for a Bambu Lab printer with the lidar ([lidar]); over another
 * printer the window turns it off.
 */
@Composable
private fun ArrangeOptionsPanel(settings: ArrangeSettings, lidar: Boolean?, actions: ArrangeActions) {
    val colors = OrcaTheme.colors
    LaunchedEffect(lidar, settings.avoidExtrusionCaliRegion) {
        if (lidar == false && settings.avoidExtrusionCaliRegion) actions.clearCalibrationRegion()
    }
    OrcaGizmoPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.arrange_spacing), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13)
            Slider(
                value = settings.distance.toFloat(),
                onValueChange = { actions.change(settings.copy(distance = it.toDouble())) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.border),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f),
            )
            PositionField(
                value = settings.distance,
                onValue = { actions.change(settings.copy(distance = it)) },
                modifier = Modifier.width(PositionFieldWidth),
            )
        }
        Text(stringResource(R.string.arrange_spacing_auto), color = colors.textSide, style = OrcaTheme.typography.body12)
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.canvasPanelSeparator),
        )
        ArrangeOption(stringResource(R.string.arrange_rotation), settings.enableRotation) {
            actions.change(settings.copy(enableRotation = it))
        }
        ArrangeOption(stringResource(R.string.arrange_multi_materials), settings.allowMultiMaterialsOnSamePlate) {
            actions.change(settings.copy(allowMultiMaterialsOnSamePlate = it))
        }
        if (lidar == true) {
            ArrangeOption(orcaString("Avoid extrusion calibration region"), settings.avoidExtrusionCaliRegion) {
                actions.change(settings.copy(avoidExtrusionCaliRegion = it))
            }
        }
        // No alignment to the Y axis while rotation is allowed.
        ArrangeOption(stringResource(R.string.arrange_align_y), settings.alignToYAxis && !settings.enableRotation, enabled = !settings.enableRotation) {
            actions.change(settings.copy(alignToYAxis = it))
        }
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.canvasPanelSeparator),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrcaButton(stringResource(R.string.arrange), onClick = actions.arrange, size = OrcaButtonSize.Compact)
            OrcaButton(stringResource(R.string.arrange_reset), onClick = actions.reset, size = OrcaButtonSize.Compact, style = OrcaButtonStyle.Regular)
        }
    }
}

@Composable
private fun ArrangeOption(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OrcaCheckBox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDisabled,
            style = OrcaTheme.typography.body13,
        )
    }
}

/** What the rotation window does. */
internal class RotationActions(
    val rotateBy: (axis: Int, degrees: Double) -> Unit,
    val setRotation: (axis: Int, degrees: Double) -> Unit,
    val reset: () -> Unit,
    val resetToZero: () -> Unit,
)

/**
 * GizmoObjectManipulation::do_render_rotate_window(): a relative rotation per
 * axis, applied when an input is done and shown as zero again, the rotation of
 * the object per axis, and the buttons that reset the rotation to when the
 * tool opened and to none.
 */
@Composable
private fun RotateGizmoPanel(
    state: PrepareUiState,
    rotation: Vector3,
    actions: RotationActions,
    onDone: () -> Unit,
    onFieldFocus: (SidebarField, Int, Boolean) -> Unit = { _, _, _ -> },
) {
    val colors = OrcaTheme.colors
    OrcaGizmoPanel {
        Text(
            text = stringResource(R.string.gizmo_world_coordinates),
            color = colors.onCanvasPanel,
            style = OrcaTheme.typography.body13,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(RotationLabelWidth))
            AxisHeaders()
        }
        GizmoValueRow(
            label = stringResource(R.string.gizmo_rotate_relative),
            values = listOf(0.0, 0.0, 0.0),
            unit = stringResource(R.string.unit_degrees),
            labelWidth = RotationLabelWidth,
            onValue = actions.rotateBy,
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, stringResource(R.string.gizmo_reset_rotation), state.canResetRotation, actions.reset),
            onFocus = { axis, focused -> onFieldFocus(SidebarField.ROTATION, axis, focused) },
        )
        GizmoValueRow(
            label = stringResource(R.string.gizmo_rotate_absolute),
            values = listOf(rotation.x, rotation.y, rotation.z),
            unit = stringResource(R.string.unit_degrees),
            labelWidth = RotationLabelWidth,
            onValue = actions.setRotation,
            reset = GizmoReset(
                DesignR.drawable.orca_toolbar_reset_zero,
                stringResource(R.string.gizmo_reset_rotation_zero),
                state.canResetRotationToZero,
                actions.resetToZero,
            ),
            onFocus = { axis, focused -> onFieldFocus(SidebarField.ABSOLUTE_ROTATION, axis, focused) },
        )
        GizmoPanelFooter(onDone)
    }
}

/** What the scale window does. */
internal class ScaleActions(
    val setScale: (axis: Int, percent: Double) -> Unit,
    val setSize: (axis: Int, millimeters: Double) -> Unit,
    val setUniform: (Boolean) -> Unit,
    val reset: () -> Unit,
    /** The window's coordinates of a volume selected alone. */
    val setCoordinates: (CoordinateSystem) -> Unit = {},
    /** "Object coordinates" of a copy, which the move window shares (set_coordinates_type()). */
    val setObjectCoordinates: (Boolean) -> Unit = {},
)

/**
 * GizmoObjectManipulation::do_render_scale_input_window(): the coordinates
 * it scales in — a volume's world, object or own ones, a single copy's world
 * or its own (the move window's choice too) — scale ratios per axis with the
 * button that resets them, the size per axis, and whether scaling keeps the
 * proportions.
 */
@Composable
private fun ScaleGizmoPanel(
    state: PrepareUiState,
    scale: Vector3,
    size: Vector3,
    imperial: Boolean,
    actions: ScaleActions,
    onDone: () -> Unit,
    onFieldFocus: (SidebarField, Int, Boolean) -> Unit = { _, _, _ -> },
) {
    val canReset = listOf(scale.x, scale.y, scale.z).let { ratios -> sqrt(ratios.sumOf { (it / 100.0 - 1.0).pow(2) }) > 0.001 }
    OrcaGizmoPanel {
        if (state.scaleCoordinates == null && state.canMoveObjectCoordinates) {
            // do_render_scale_input_window() of a single copy: "World coordinates" or "Object coordinates".
            val labels = listOf(orcaString("World coordinates"), orcaString("Object coordinates"))
            OrcaComboBox(
                items = listOf(false, true),
                selected = state.moveObjectCoordinates,
                label = { labels[if (it) 1 else 0] },
                onSelect = actions.setObjectCoordinates,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        state.scaleCoordinates?.let { coordinates ->
            // do_render_scale_input_window(): the coordinates a volume scales in.
            val labels = mapOf(
                CoordinateSystem.WORLD to orcaString("World coordinates"),
                CoordinateSystem.INSTANCE to orcaString("Object coordinates"),
                CoordinateSystem.LOCAL to orcaString("Part coordinates"),
            )
            OrcaComboBox(
                items = CoordinateSystem.entries,
                selected = coordinates,
                label = { labels.getValue(it) },
                onSelect = actions.setCoordinates,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(PositionLabelWidth))
            AxisHeaders()
        }
        GizmoValueRow(
            label = stringResource(R.string.gizmo_scale_ratio),
            values = listOf(scale.x, scale.y, scale.z),
            unit = stringResource(R.string.unit_percent),
            labelWidth = PositionLabelWidth,
            onValue = actions.setScale,
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, stringResource(R.string.gizmo_reset_scale), canReset, actions.reset),
            onFocus = { axis, focused -> onFieldFocus(SidebarField.SCALE, axis, focused) },
        )
        GizmoValueRow(
            label = stringResource(R.string.gizmo_size),
            values = listOf(size.x, size.y, size.z).map { it * displayKoef(imperial) },
            unit = lengthUnit(imperial),
            labelWidth = PositionLabelWidth,
            onValue = { axis, value -> actions.setSize(axis, value * inputKoef(imperial)) },
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, "", visible = false, onClick = {}),
            onFocus = { axis, focused -> onFieldFocus(SidebarField.SIZE, axis, focused) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OrcaCheckBox(checked = state.uniformScale, onCheckedChange = actions.setUniform)
            Text(
                text = stringResource(R.string.gizmo_uniform_scale),
                color = OrcaTheme.colors.onCanvasPanel,
                style = OrcaTheme.typography.body13,
            )
        }
        GizmoPanelFooter(onDone)
    }
}

/** A reset button at the end of a gizmo window row, shown only when there is something to reset. */
private class GizmoReset(val icon: Int, val description: String, val visible: Boolean, val onClick: () -> Unit)

@Composable
private fun AxisHeaders() {
    AxisNames.forEachIndexed { axis, name ->
        Text(
            text = name,
            color = AxisColors[axis],
            style = OrcaTheme.typography.body13,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(PositionFieldWidth),
        )
    }
}

/** m_new_unit_string: "in" or "mm". */
@Composable
private fun lengthUnit(imperial: Boolean): String = if (imperial) orcaString("in") else stringResource(R.string.unit_mm)

/** update_buffered_value() shows millimetres times mm_to_in, and on_change() takes inches times in_to_mm. */
internal fun displayKoef(imperial: Boolean) = if (imperial) ImperialUnits.MM_TO_IN else 1.0

internal fun inputKoef(imperial: Boolean) = if (imperial) ImperialUnits.IN_TO_MM else 1.0

@Composable
private fun GizmoValueRow(
    label: String,
    values: List<Double>,
    unit: String,
    labelWidth: Dp,
    onValue: (axis: Int, value: Double) -> Unit,
    reset: GizmoReset? = null,
    onFocus: (axis: Int, focused: Boolean) -> Unit = { _, _ -> },
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = label,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.width(labelWidth),
        )
        for (axis in 0 until 3) {
            PositionField(
                value = values[axis],
                onValue = { onValue(axis, it) },
                onFocusChange = { onFocus(axis, it) },
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(PositionFieldWidth),
            )
        }
        // The unit keeps its line; "in" in other languages is longer than the column.
        Text(
            text = unit,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body13,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(start = 6.dp)
                .widthIn(min = UnitWidth),
        )
        if (reset != null) {
            // An invisible button keeps the rows aligned, as the window does.
            Box(Modifier.size(ResetButtonSize), contentAlignment = Alignment.Center) {
                if (reset.visible) {
                    OrcaIconButton(reset.icon, reset.description, reset.onClick, tint = Color.Unspecified)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.GizmoPanelFooter(onDone: () -> Unit) {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(OrcaTheme.colors.canvasPanelSeparator),
    )
    OrcaButton(
        text = stringResource(R.string.gizmo_done),
        onClick = onDone,
        size = OrcaButtonSize.Compact,
        modifier = Modifier.align(Alignment.End),
    )
}

/**
 * ImGui::BBLInputDouble with "%.2f": the value with two decimals, applied when
 * the input is done or loses focus. Text that is not a number is dropped.
 * [onFocusChange] hears it being edited and no longer (ImGui's active item),
 * as it leaves too.
 */
@Composable
internal fun PositionField(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    /** The ImGui format the field shows the value in. */
    format: String = "%.2f",
) {
    val shown = String.format(Locale.ROOT, format, value)
    var text by remember(shown) { mutableStateOf(shown) }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val focusChange by rememberUpdatedState(onFocusChange)
    DisposableEffect(Unit) { onDispose { if (focused) focusChange(false) } }
    fun apply() {
        text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && text != shown }?.let(onValue)
        text = shown
    }
    OrcaTextField(
        value = text,
        onValueChange = { text = it },
        // The small font of OrcaSlicer's gizmo windows keeps -9999.99 visible.
        textStyle = OrcaTheme.typography.body12,
        modifier = modifier.onFocusChanged {
            if (!it.isFocused && text != shown) apply()
            if (it.isFocused != focused) {
                focused = it.isFocused
                focusChange(it.isFocused)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            apply()
            focusManager.clearFocus()
        }),
    )
}

private val AxisNames = listOf("X", "Y", "Z")

// ColorRGBA::X(), Y(), Z(), which the move window uses for the axis names.
private val AxisColors = listOf(Color(255, 60, 91), Color(100, 200, 24), Color(47, 136, 233))
private val CanvasMargin = 12.dp

/** The navigator and the FPS overlay stand under the toolbar, which takes the top 12 dp and its own height. */
private val CanvasNavigatorTop = 62.dp

/** The variable layer height bar is left out where the controls leave it less room than this. */
private val LayerBarMinHeight = 120.dp

// A gizmo window fits a phone: label, three fields, unit, and reset button in 360 dp.
private val PositionLabelWidth = 64.dp

/**
 * The width a column of [captions] needs so that none breaks inside a word:
 * the widest word among them, and at least [minimum]; a caption still wraps
 * between its words.
 */
@Composable
private fun captionWidth(captions: List<String>, minimum: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val style = OrcaTheme.typography.body13
    val density = LocalDensity.current
    val widest = captions.flatMap { it.split(' ') }.maxOfOrNull { word -> measurer.measure(word, style, softWrap = false).size.width } ?: 0
    return maxOf(minimum, with(density) { widest.toDp() })
}
private val RotationLabelWidth = 76.dp
internal val PositionFieldWidth = 60.dp
private val UnitWidth = 24.dp
private val ResetButtonSize = 28.dp

private val PreviewArrangeActions = ArrangeActions({}, {}, {}, {})
private val PreviewRotationActions = RotationActions({ _, _ -> }, { _, _ -> }, {}, {})
private val PreviewScaleActions = ScaleActions({ _, _ -> }, { _, _ -> }, {}, {})

private val PreviewInspection = ModelInspection(
    facetCount = 12,
    dimensions = ModelDimensions(20.0, 20.0, 20.0),
    boxCenter = Vector3(0.0, 0.0, 10.0),
    mesh = ScenePath("preview.mesh"),
    placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
    fit = BuildVolumeFit.INSIDE,
    boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
    rotationDegrees = Vector3(0.0, 0.0, 0.0),
    unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
)

private val PreviewState = PrepareUiState(
    plate = null,
    importing = false,
    sceneObjects = listOf(PlateObject.CalibrationCube(listOf(PlateInstance(PreviewInspection)))),
    selectedObject = null,
    gizmo = null,
    flatteningPlanes = emptyList(),
    arrangeOptionsOpen = false,
    arrangeSettings = ArrangeSettings(),
    selectedPosition = null,
    selectedRotation = null,
    canResetRotation = false,
    canResetRotationToZero = false,
    selectedScale = null,
    selectedSize = null,
    uniformScale = true,
    objectClashed = false,
    slicing = PlateSlicing(SliceJobId("preview"), SliceProgress(SliceJobId("preview"), 0.45f, SliceStage.SLICING, "Generating infill toolpath")),
    problem = null,
    canEditPlate = false,
    canSlice = false,
)

@Preview(name = "Compact", widthDp = 400, heightDp = 800)
@Preview(name = "Compact dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrepareCompactPreview() = OrcinusTheme {
    PrepareScreen(
        PreviewState, OrcaWindowLayout.Compact, {}, {}, {}, { _, _ -> }, {}, PaintingActions.NONE, { _, _, _ -> }, { _, _ -> }, { _, _ -> }, {}, PrepareObjectMenuActions.NONE, {}, {}, {}, { _, _ -> }, {}, {}, {},
        PreviewArrangeActions, PreviewRotationActions, PreviewScaleActions, {}, {}, {},
    )
}

@Preview(name = "Wide", widthDp = 1000, heightDp = 640)
@Composable
private fun PrepareWidePreview() = OrcinusTheme {
    PrepareScreen(
        PreviewState.copy(
            slicing = null,
            selectedObject = 0,
            gizmo = PlateGizmo.MOVE,
            selectedPosition = ObjectPosition(175.0, 175.0, 10.0),
            canEditPlate = true,
            canSlice = true,
        ),
        OrcaWindowLayout.Wide, {}, {}, {}, { _, _ -> }, {}, PaintingActions.NONE, { _, _, _ -> }, { _, _ -> }, { _, _ -> }, {}, PrepareObjectMenuActions.NONE, {}, {}, {}, { _, _ -> }, {}, {}, {},
        PreviewArrangeActions, PreviewRotationActions, PreviewScaleActions, {}, {}, {},
    )
}

/**
 * GLCanvas3D::m_sidebar_field of the window of [field]'s gizmo while it shows,
 * with the reference system of the window's coordinates: the move window's
 * object coordinates, the scale window's of a volume (part coordinates its
 * own) or of a copy, none for the rotation window's world coordinates.
 */
private fun sidebarHintOf(state: PrepareUiState, field: SidebarField, axis: Int): SidebarHint? = when (field) {
    SidebarField.POSITION -> SidebarHint(field, axis, state.moveFrame).takeIf { state.gizmo == PlateGizmo.MOVE }
    SidebarField.ROTATION, SidebarField.ABSOLUTE_ROTATION -> SidebarHint(field, axis).takeIf { state.gizmo == PlateGizmo.ROTATE }
    SidebarField.SCALE, SidebarField.SIZE -> {
        val reference = if (state.selectedVolume != null) {
            state.volumeScale?.reference?.takeIf { state.scaleCoordinates != CoordinateSystem.WORLD }
        } else {
            state.moveFrame
        }
        SidebarHint(field, axis, reference, local = state.scaleCoordinates == CoordinateSystem.LOCAL, uniformScale = state.uniformScale)
            .takeIf { state.gizmo == PlateGizmo.SCALE }
    }
}

/**
 * GLCanvas3D::_render_sequential_clearance(): with no tool open, or the move,
 * rotation or scale tool (can_sequential_clearance_show_in_gizmo()).
 */
private fun clearanceShown(state: PrepareUiState): Boolean =
    !state.toolOpen || state.gizmo == PlateGizmo.MOVE || state.gizmo == PlateGizmo.ROTATE || state.gizmo == PlateGizmo.SCALE

/**
 * GLCanvas3D::Labels::render(): the copies of the current plate, each named
 * after its object, with its number when the object has several, and its
 * place in the print order while the plate prints by object; none while a
 * gizmo runs.
 */
@Composable
private fun objectLabels(state: PrepareUiState): Map<Int, PlateLabel> {
    // GLGizmosManager::is_running(): any tool.
    if (state.toolOpen) return emptyMap()
    val sequence = orcaString("Sequence")
    return state.currentPlateCopies.associateWith { index ->
        val copy = state.sceneCopies[index]
        val copies = copy.plateObject.instances.size
        PlateLabel(
            name = copy.plateObject.displayName() + if (copies > 1) " (${copy.id.instance + 1})" else "",
            printOrder = state.printSequence.getOrNull(index)?.takeIf { it >= 0 }?.let { "$sequence#: $it" },
        )
    }
}

/**
 * The text or SVG [volume] the tool is open on as the canvas drags it: a part,
 * drawn by its own mesh in its placement, or the object's own mesh, which the
 * scene draws from each copy's mesh in place; null before the engine
 * described it.
 */
private fun embossDragOf(volume: ObjectPartId, described: EmbossVolume?, keepUp: Boolean, copies: List<SceneCopy>): TextDragView? {
    described ?: return null
    val plateObject = copies.firstOrNull { it.id.mesh == volume.mesh }?.plateObject ?: return null
    if (volume.index == 0) {
        return TextDragView(
            keys = plateObject.instances.mapTo(HashSet()) { it.inspection.mesh.value },
            placement = (plateObject as? PlateObject.ImportedModel)?.frame ?: Transform3.IDENTITY,
            sceneFrame = Transform3.IDENTITY,
            fix = described.fix,
            onlyPart = described.onlyPart,
            keepUp = keepUp,
        )
    }
    val part = plateObject.parts.getOrNull(volume.index - 1) ?: return null
    return TextDragView(setOf(part.mesh.value), part.placement, part.placement, described.fix, described.onlyPart, keepUp)
}

/**
 * render_points(): the ears of the copy the brim ears tool is open on, in
 * the world, with the ear the finger would place; null once the copy is gone.
 */
private fun brimEarsViewOf(state: PrepareUiState, mode: BrimEarsMode): BrimEarsView? {
    val copy = state.sceneCopies.firstOrNull { it.id == mode.copy } ?: return null
    val placement = copy.instance.inspection.placement.columns
    fun world(point: Vector3) = Vector3(
        placement[0] * point.x + placement[4] * point.y + placement[8] * point.z + placement[12],
        placement[1] * point.x + placement[5] * point.y + placement[9] * point.z + placement[13],
        placement[2] * point.x + placement[6] * point.y + placement[10] * point.z + placement[14],
    )
    val points = mode.draft ?: copy.plateObject.brimPoints
    return BrimEarsView(
        copy = mode.copy,
        ears = points.mapIndexed { index, point ->
            val shown = when {
                index == mode.held -> BrimEarState.HELD
                index in mode.selected -> BrimEarState.SELECTED
                index in mode.invalid -> BrimEarState.ERROR
                else -> BrimEarState.NORMAL
            }
            BrimEarView(world(point.position), point.radius, shown)
        },
        hover = mode.hover?.let { point -> BrimEarView(world(point), (mode.headDiameter ?: 0.0) / 2, BrimEarState.HOVER) },
        section = state.paintSection,
    )
}

/** GLGizmoMmuSegmentation::EXTRUDERS_LIMIT: the filaments the colour painting tool offers. */
private const val MMU_EXTRUDERS_LIMIT = 16
