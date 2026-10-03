package app.orcinus.shadow.feature.prepare

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.CanvasPreferences
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
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintState
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeManipulation
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.plate.AddObjectItems
import app.orcinus.shadow.core.ui.plate.CanvasViewButtons
import app.orcinus.shadow.core.ui.plate.CloneDialog
import app.orcinus.shadow.core.ui.plate.MenuFilament
import app.orcinus.shadow.core.ui.plate.NumberOfInstancesDialog
import app.orcinus.shadow.core.ui.plate.ObjectMenuActions
import app.orcinus.shadow.core.ui.plate.ObjectMenuItems
import app.orcinus.shadow.core.ui.plate.PartShapeSheet
import app.orcinus.shadow.core.ui.plate.PlateIconActions
import app.orcinus.shadow.core.ui.plate.PlateMenuItems
import app.orcinus.shadow.core.ui.plate.PlateNameDialog
import app.orcinus.shadow.core.ui.plate.PlateSettingsSheet
import app.orcinus.shadow.core.ui.plate.PlateStrip
import app.orcinus.shadow.core.ui.plate.SliceButton
import app.orcinus.shadow.core.ui.plate.exportFileName
import app.orcinus.shadow.core.ui.plate.navigatorFaceLabels
import app.orcinus.shadow.core.ui.plate.objectMenuState
import app.orcinus.shadow.core.ui.sizeText
import app.orcinus.shadow.core.ui.title
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
import app.orcinus.shadow.render.scene.TextDragView
import app.orcinus.shadow.render.scene.rememberPlateViewCamera
import java.util.Locale
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.settingToOpen.collect(onOpenSetting) }
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
    // "Export as one STL/DRC" and "Replace 3D file": the object waits for the
    // document the user picks, as the desktop app waits for its file dialog.
    var exportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var exportFormat by rememberSaveable { mutableStateOf(MeshFormat.STL) }
    val meshExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MESH_MIME_TYPE)) { uri ->
        val mesh = exportTarget
        exportTarget = null
        if (uri != null && mesh != null) viewModel.exportMesh(ScenePath(mesh), exportFormat, uri.toString())
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
    val replacement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mesh = replaceTarget
        replaceTarget = null
        if (uri != null && mesh != null) viewModel.replaceMesh(PlateInstanceId(ScenePath(mesh), replaceInstance), uri.toString())
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
        ),
        onPlaceObject = viewModel::placeObject,
        onSetAutoDrop = viewModel::setAutoDrop,
        onDeleteObject = viewModel::deleteObject,
        objectMenuActions = PrepareObjectMenuActions(
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
                viewModel.copyOf(index)?.let { copy ->
                    replaceTarget = copy.mesh.value
                    replaceInstance = copy.instance
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
                viewModel.copyOf(index)?.let { copy ->
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
        ),
        onPaste = viewModel::paste,
        plateMenuActions = PlateMenuActions(
            addPrimitive = viewModel::addPrimitiveShape,
            addHandyModel = viewModel::addHandyModel,
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
                settingsDone = viewModel::snapshotCutConnectors,
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
            setMaxAngle = viewModel::setBrimEarMaxAngle,
            setDetectionRadius = viewModel::setBrimEarDetectionRadius,
            generate = viewModel::generateBrimEars,
            removeSelected = viewModel::removeSelectedBrimEars,
            removeAll = viewModel::removeAllBrimEars,
            setPainted = viewModel::setPaintedBrim,
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
    /** A touch of the finger, as a ray in world coordinates; [starts] for the first of a stroke. */
    val paint: (origin: Vector3, direction: Vector3, starts: Boolean) -> Unit,
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
) {
    companion object {
        val NONE = PaintingActions({ _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
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
) {
    OrcaCanvas(Modifier.fillMaxSize()) {
        val viewCamera = rememberPlateViewCamera()
        var objectMenu by remember { mutableStateOf<ObjectMenu?>(null) }
        // _render_assemble_info(): the size of the assembly view's selection, which its view tells.
        var assemblySelection by remember { mutableStateOf<Vector3?>(null) }
        var plateMenu by remember { mutableStateOf<Offset?>(null) }
        var askingCopies by remember { mutableStateOf<Int?>(null) }
        var cloning by remember { mutableStateOf<Int?>(null) }
        var addingPart by remember { mutableStateOf<Triple<Int, VolumeType, Offset>?>(null) }
        // OrcaSlicer's "Embossed text", the text a new text starts with.
        val defaultText = orcaString("Embossed text")
        // The object list's "Add part" > "Text": the canvas places it on the
        // object's first copy, as the desktop app does without a mouse position.
        LaunchedEffect(state.embossRequest) {
            val request = state.embossRequest as? EmbossRequest.Add ?: return@LaunchedEffect
            val copy = state.sceneCopies.indexOfFirst { it.id == PlateInstanceId(request.mesh, 0) }
            val hit = copy.takeIf { it >= 0 }?.let { viewCamera.surfaceHit(it) }
            if (request.kind == EmbossKind.SVG) {
                // choose_svg_file() first.
                svgActions.chooseRequested(request, hit)
                svgActions.pickFile()
            } else {
                textActions.addRequested(request, hit, defaultText)
            }
        }
        // The phone's Back leaves the assembly view as its "Return" does; an open tool takes it first.
        BackHandler(enabled = state.assemblyView != null) { assemblyViewActions.back() }
        // The desktop measuring tool's Esc: the phone's Back drops the last selection, and with none closes the tool.
        BackHandler(enabled = state.measure != null) { measureActions.escape() }
        var renamingPlate by remember { mutableStateOf<Int?>(null) }
        // Plater::select_plate_by_hover_id(), action 5: the plate is selected, then its settings open.
        var customizingPlate by remember { mutableStateOf<Int?>(null) }
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
                painting = state.painting?.let { PaintingView(it.mesh, it.kind, it.highlightAngle, it.verticalOnly) },
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
                moveFrame = state.moveFrame.takeIf { state.gizmo == PlateGizmo.MOVE },
                flatteningPlanes = state.flatteningPlanes,
                wireframes = state.wireframes,
                editable = state.canEditPlate,
                onSelectObject = onSelectObject,
                onPlaceObject = onPlaceObject,
                onOpenObjectMenu = { index, position -> objectMenu = ObjectMenu(index, position) },
                onOpenPlateMenu = { position -> plateMenu = position },
                contentDescription = stringResource(R.string.plate_view),
                modifier = Modifier.fillMaxSize(),
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
                clearance = state.clearance.takeIf {
                    state.painting == null && state.cut == null && state.simplify == null && state.measure == null && state.gizmo != PlateGizmo.LAY_ON_FACE
                },
                antialiasingSamples = canvas.antialiasingSamples,
                layerEditing = state.layerEditing?.view(),
                // SurfaceDrag: the text the tool is open on follows a finger over its object.
                textDrag = state.text?.takeUnless { it.busy }?.let { embossDragOf(it.volume, it.described, keepUp = true, state.sceneCopies) }
                    ?: state.svg?.takeUnless { it.busy }?.let { embossDragOf(it.volume, it.described, keepUp = it.keepUp, state.sceneCopies) },
                onTextDragged = { placement -> if (state.text != null) textActions.drag(placement) else svgActions.drag(placement) },
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
                assembly = state.assemblyView?.let { AssemblyView(it.explosionRatio, it.hidden, it.sectionPosition, it.sectionResets, it.section) },
                onAssemblySelection = { assemblySelection = it },
                onPlaceInAssembly = assemblyViewActions.place,
                onAssemblySection = assemblyViewActions.sectionPlane,
                selectedVolume = state.selectedVolume?.mesh?.value,
                onPlaceVolume = onPlaceVolume,
            )
            state.measure?.editingDistance?.let { distance ->
                MeasureScaleDialog(distance, canvas.imperialUnits, measureActions.scale, measureActions.cancelScale)
            }
        }
        plateMenu?.let { position ->
            PlateContextMenu(
                state,
                position,
                onDismiss = { plateMenu = null },
                onAddModel = onAddModel,
                onPaste = onPaste,
                actions = plateMenuActions,
                // An object of a text standing where the finger held the bed.
                onAddText = { textActions.add(null, VolumeType.PART, null, viewCamera.bedPoint(position), defaultText) },
                onAddSvg = {
                    svgActions.choose(null, VolumeType.PART, null, viewCamera.bedPoint(position))
                    svgActions.pickFile()
                },
            )
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
        customizingPlate?.takeIf { it == state.currentPlate && state.canEditPlate }?.let { index ->
            PlateSettingsSheet(
                name = state.plateNames.getOrNull(index).orEmpty(),
                choice = state.plateSettings,
                bedTypes = state.bedTypes,
                filamentColors = state.filamentColors.map { Color(it.red, it.green, it.blue, it.alpha) },
                spiralOn = state.spiralVaseMode,
                i3 = state.printerI3,
                onDismiss = { name ->
                    customizingPlate = null
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
                    onSet = onSetCanvas,
                    onZoom = viewCamera::zoomToFit,
                    assembly = state.assemblyView != null,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
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
                    )
                }
                // _render_paint_toolbar(): the assembly view's filament buttons.
                if (state.assemblyView != null) AssemblyPaintToolbar(state, assemblyViewActions.fillColor)
                val position = state.selectedPosition
                val rotation = state.selectedRotation
                val scale = state.selectedScale
                val size = state.selectedSize
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
                    state.text != null -> TextPanel(state.text, textFamilies, textActions, canvas.imperialUnits, eye = viewCamera::eye)
                    state.svg != null -> SvgPanel(state.svg, svgActions, canvas.imperialUnits, eye = viewCamera::eye)
                    state.measure?.assembly != null -> AssemblyPanel(state.measure, measureActions, assemblyActions, canvas.imperialUnits)
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
                    state.arrangeOptionsOpen -> ArrangeOptionsPanel(state.arrangeSettings, arrangeActions)
                    state.gizmo == PlateGizmo.SCALE && scale != null && size != null ->
                        ScaleGizmoPanel(state, scale, size, canvas.imperialUnits, scaleActions, onCloseGizmo)
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
                    )
                    state.gizmo == PlateGizmo.ROTATE && rotation != null -> RotateGizmoPanel(state, rotation, rotationActions, onCloseGizmo)
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
                                    workable = index in state.workablePlates,
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
                        if (state.assemblyView == null) Notifications(state, canvas.imperialUnits, onCancelSlicing, onDismissProblem, onJumpTo)
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
) {
    companion object {
        val NONE = PrepareObjectMenuActions(
            {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _, _ -> }, {}, { _, _ -> }, {}, {}, { _, _, _ -> }, {}, {}, {}, {},
            { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, { _, _, _ -> },
        )
    }
}

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
) {
    companion object {
        val NONE = PlateActions({}, {}, {}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> })
    }
}

/** What the canvas menu over empty space adds (MenuFactory::create_default_menu). */
internal class PlateMenuActions(
    /** ObjectList::load_shape_object(): a shape of create_mesh() with its translated name. */
    val addPrimitive: (shape: String, name: String) -> Unit,
    val addHandyModel: (HandyModel) -> Unit,
) {
    companion object {
        val NONE = PlateMenuActions({ _, _ -> }, {})
    }
}

/**
 * MenuFactory::default_menu(), which the canvas opens over empty space, with
 * the items the app has: Add Primitive, Add Handy models and Add Models. Paste
 * stands first, as a phone offers it where a finger is held, for the Edit
 * menu's Paste.
 */
@Composable
private fun PlateContextMenu(
    state: PrepareUiState,
    position: Offset,
    onDismiss: () -> Unit,
    onAddModel: () -> Unit,
    onPaste: () -> Unit,
    actions: PlateMenuActions,
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
) {
    val copy = state.sceneCopies.getOrNull(menu.index)
    OrcaContextMenu(
        expanded = copy != null,
        position = IntOffset(menu.position.x.roundToInt(), menu.position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        if (copy == null) return@OrcaContextMenu
        val index = menu.index
        val name = copy.plateObject.displayName()
        ObjectMenuItems(
            state = objectMenuState(
                copy.plateObject,
                copy.instance,
                state.plate,
                state.canEditPlate,
                clipboard = state.clipboard,
                simplifying = state.simplify != null,
                filaments = state.filamentNames.zip(state.filamentColors) { filament, color ->
                    MenuFilament(filament, Color(color.red, color.green, color.blue, color.alpha))
                },
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
    onJumpTo: (ValidationNotice) -> Unit,
) {
    // Plater::priv::process_validation_warning() and push_validate_error_notification().
    state.validationWarning?.let { ValidationNotification(it, OrcaNotificationLevel.Warning, orcaString("WARNING:"), onJumpTo) }
    state.validationError?.let { ValidationNotification(it, OrcaNotificationLevel.Error, orcaString("Error:"), onJumpTo) }
    if (state.objectClashed) {
        // GLCanvas3D::EWarning::ObjectClashed
        OrcaNotification(level = OrcaNotificationLevel.Error) {
            OrcaNotificationText(stringResource(R.string.object_clashed))
        }
    }
    state.problem?.let { problem ->
        OrcaNotification(
            action = {
                Text(
                    text = stringResource(R.string.dismiss),
                    color = OrcaTheme.colors.accent,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable(role = Role.Button, onClick = onDismissProblem),
                )
            },
        ) {
            OrcaNotificationText(problem.title(), emphasized = true)
            problem.detail?.takeIf { it.isNotBlank() }?.let { OrcaNotificationText(it) }
        }
    }
    val slicing = state.slicing
    if (slicing != null) {
        val fraction = slicing.progress?.fraction ?: 0f
        OrcaProgressNotification(
            title = if (slicing.cancelling) {
                stringResource(R.string.slicing_cancelling)
            } else {
                stringResource(R.string.slicing_progress, (fraction * 100).roundToInt())
            },
            detail = slicing.progress?.detail,
            progress = fraction,
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onCancelSlicing,
        )
    } else {
        state.selectedCopy?.let { ObjectInfo(it, imperial) }
    }
}

/**
 * A message of the plate's validation: "Error:" or "WARNING:" over its text,
 * and "Jump to" with the name of the object it is about and its setting.
 */
@Composable
private fun ValidationNotification(notice: ValidationNotice, level: OrcaNotificationLevel, title: String, onJumpTo: (ValidationNotice) -> Unit) {
    val name = notice.targetObject?.displayName()
    val link = if (name != null || notice.option.isNotEmpty()) {
        orcaString("Jump to") + (name?.let { " [$it]" }.orEmpty()) + notice.option.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
    } else {
        null
    }
    OrcaNotification(level = level) {
        OrcaNotificationText(title, emphasized = true)
        OrcaNotificationText(notice.text.trimEnd())
        link?.let { OrcaNotificationLink(it, onClick = { onJumpTo(notice) }) }
    }
}

@Composable
private fun ObjectInfo(copy: SceneCopy, imperial: Boolean) {
    OrcaNotification {
        OrcaNotificationText(stringResource(R.string.object_name, copy.plateObject.displayName()), emphasized = true)
        OrcaNotificationText(stringResource(R.string.object_size, copy.instance.inspection.dimensions.sizeText(imperial)))
        OrcaNotificationText(stringResource(R.string.object_triangles, copy.instance.inspection.facetCount))
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
) {
    @Composable
    fun gizmo(icon: Int, name: Int, gizmo: PlateGizmo?) = OrcaCanvasTool(
        icon = icon,
        contentDescription = stringResource(name),
        onClick = { gizmo?.let(onToggleGizmo) },
        enabled = gizmo != null && state.canManipulate,
        selected = gizmo != null && state.gizmo == gizmo,
    )

    OrcaCanvasToolbar {
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
        // but for the dowel a cut made an object of.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_cut,
            contentDescription = stringResource(R.string.gizmo_cut),
            onClick = onToggleCut,
            enabled = state.canCut,
            selected = state.cut != null,
        )
        // GLGizmoMeshBoolean: two volumes of the selected copy joined, subtracted or intersected.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_meshboolean,
            contentDescription = stringResource(R.string.gizmo_mesh_boolean),
            onClick = onToggleMeshBoolean,
            enabled = state.canMeshBoolean || state.meshBoolean != null,
            selected = state.meshBoolean != null,
        )
        // GLGizmoMmuSegmentation: the object is painted with the filaments of the plate.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_mmu_segmentation,
            contentDescription = stringResource(R.string.gizmo_color_painting),
            onClick = { onTogglePainting(PaintKind.COLOR) },
            enabled = state.canPaint,
            selected = state.painting?.kind == PaintKind.COLOR,
        )
        // GLGizmoFdmSupports: supports are enforced or blocked where they are painted.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_support,
            contentDescription = stringResource(R.string.gizmo_support_painting),
            onClick = { onTogglePainting(PaintKind.SUPPORTS) },
            enabled = state.canManipulate,
            selected = state.painting?.kind == PaintKind.SUPPORTS,
        )
        // GLGizmoSeam: the seam is enforced or blocked where it is painted.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_seam,
            contentDescription = stringResource(R.string.gizmo_seam_painting),
            onClick = { onTogglePainting(PaintKind.SEAM) },
            enabled = state.canManipulate,
            selected = state.painting?.kind == PaintKind.SEAM,
        )
        // GLGizmoFuzzySkin: the walls get fuzzy skin where it is painted.
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_fuzzy_skin_paint,
            contentDescription = stringResource(R.string.gizmo_fuzzy_skin_painting),
            onClick = { onTogglePainting(PaintKind.FUZZY_SKIN) },
            enabled = state.canManipulate,
            selected = state.painting?.kind == PaintKind.FUZZY_SKIN,
        )
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
 * The colour painting tool while it is open (GLGizmoMmuSegmentation's window):
 * which filament the finger paints with, how wide the brush is and which tool
 * paints. The desktop window has a row per filament with its colour; a phone
 * shows them as chips a thumb can reach.
 */
@Composable
private fun PaintingPanel(state: PrepareUiState, painting: PaintingMode, actions: PaintingActions) {
    PaintingPanelFrame(stringResource(R.string.gizmo_color_painting), stringResource(R.string.painting_done), actions.close) {
        Text(
            text = stringResource(R.string.painting_filament),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            state.filamentColors.forEachIndexed { index, color ->
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
            // with the right button (EnforcerBlockerType::NONE).
            OrcaButton(
                text = stringResource(R.string.painting_eraser),
                style = if (painting.state == PaintState.NONE) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
                size = OrcaButtonSize.Compact,
                onClick = { actions.setState(PaintState.NONE) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                PaintTool.BRUSH to R.string.painting_tool_brush,
                PaintTool.FILL to R.string.painting_tool_fill,
                PaintTool.BUCKET to R.string.painting_tool_bucket,
            ).forEach { (tool, label) ->
                OrcaButton(
                    text = stringResource(label),
                    style = if (painting.tool == tool) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
                    size = OrcaButtonSize.Compact,
                    onClick = { actions.setTool(tool) },
                )
            }
        }
        if (painting.tool == PaintTool.BRUSH) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = stringResource(R.string.painting_brush),
                    color = OrcaTheme.colors.onCanvasPanel,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Slider(
                    value = painting.radius.toFloat(),
                    onValueChange = { actions.setRadius(it.toDouble()) },
                    valueRange = BRUSH_MIN..BRUSH_MAX,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = String.format(textLocale(), "%.1f", painting.radius) + " " + stringResource(R.string.unit_mm),
                    color = OrcaTheme.colors.onCanvasPanel,
                    style = OrcaTheme.typography.body12,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
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
                range = BRUSH_MIN..BRUSH_MAX,
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
            range = BRUSH_MIN..BRUSH_MAX,
            text = String.format(textLocale(), "%.2f", painting.radius),
            onChange = { actions.setRadius(it.toDouble()) },
        )
        PaintingCheck(orcaString("Vertical"), painting.verticalOnly, actions.setVerticalOnly)
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
                range = BRUSH_MIN..BRUSH_MAX,
                text = String.format(textLocale(), "%.2f", painting.radius),
                onChange = { actions.setRadius(it.toDouble()) },
            )
        }
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

/**
 * The window of a painting tool: its title with "Done", and its controls,
 * which fold away into the title row so the finger reaches the model under
 * them; the desktop window stands beside the model instead.
 */
@Composable
internal fun PaintingPanelFrame(title: String, done: String, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
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
                onClick = { expanded = !expanded },
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

/** A row of a painting tool's choices, the chosen one filled. */
@Composable
internal fun <T> PaintingChoices(items: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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

/** GLGizmoPainterBase::get_cursor_radius_min/max for a finger. */
private const val BRUSH_MIN = 0.4f
private const val BRUSH_MAX = 8.0f

/** GLGizmoPainterBase::SmartFillAngleMin and SmartFillAngleMax. */
private const val SMART_FILL_ANGLE_MIN = 0f
private const val SMART_FILL_ANGLE_MAX = 90f

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(PositionLabelWidth))
            AxisHeaders()
        }
        if (objectCoordinates && relative) {
            // In object coordinates the copy moves by what is typed along its own axes.
            GizmoValueRow(
                label = orcaString("Translate(Relative)"),
                values = listOf(0.0, 0.0, 0.0),
                unit = lengthUnit(imperial),
                labelWidth = PositionLabelWidth,
                onValue = { axis, value -> onTranslate(axis, value * inputKoef(imperial)) },
            )
        } else {
            GizmoValueRow(
                label = stringResource(R.string.gizmo_position),
                values = listOf(position.x, position.y, position.z).map { it * displayKoef(imperial) },
                unit = lengthUnit(imperial),
                labelWidth = PositionLabelWidth,
                onValue = { axis, value -> onSetPosition(axis, value * inputKoef(imperial)) },
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
)

/**
 * GLCanvas3D::_render_arrange_menu() for FFF printers: the spacing slider and
 * its input, auto spacing at 0, the rotation, materials, and Y-axis options,
 * and the Arrange and Reset buttons. The calibration-region option is only for
 * printers with a lidar and does not show.
 */
@Composable
private fun ArrangeOptionsPanel(settings: ArrangeSettings, actions: ArrangeActions) {
    val colors = OrcaTheme.colors
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
)

/**
 * GizmoObjectManipulation::do_render_scale_input_window() in world
 * coordinates: scale ratios per axis with the button that resets them, the
 * size per axis, and whether scaling keeps the proportions.
 */
@Composable
private fun ScaleGizmoPanel(
    state: PrepareUiState,
    scale: Vector3,
    size: Vector3,
    imperial: Boolean,
    actions: ScaleActions,
    onDone: () -> Unit,
) {
    val canReset = listOf(scale.x, scale.y, scale.z).let { ratios -> sqrt(ratios.sumOf { (it / 100.0 - 1.0).pow(2) }) > 0.001 }
    OrcaGizmoPanel {
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
        )
        GizmoValueRow(
            label = stringResource(R.string.gizmo_size),
            values = listOf(size.x, size.y, size.z).map { it * displayKoef(imperial) },
            unit = lengthUnit(imperial),
            labelWidth = PositionLabelWidth,
            onValue = { axis, value -> actions.setSize(axis, value * inputKoef(imperial)) },
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, "", visible = false, onClick = {}),
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
 */
@Composable
internal fun PositionField(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = String.format(Locale.ROOT, "%.2f", value)
    var text by remember(shown) { mutableStateOf(shown) }
    val focusManager = LocalFocusManager.current
    fun apply() {
        text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && text != shown }?.let(onValue)
        text = shown
    }
    OrcaTextField(
        value = text,
        onValueChange = { text = it },
        // The small font of OrcaSlicer's gizmo windows keeps -9999.99 visible.
        textStyle = OrcaTheme.typography.body12,
        modifier = modifier.onFocusChanged { if (!it.isFocused && text != shown) apply() },
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
        PreviewState, OrcaWindowLayout.Compact, {}, {}, {}, { _, _ -> }, {}, PaintingActions.NONE, { _, _, _ -> }, { _, _ -> }, {}, PrepareObjectMenuActions.NONE, {}, {}, {}, { _, _ -> }, {}, {}, {},
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
        OrcaWindowLayout.Wide, {}, {}, {}, { _, _ -> }, {}, PaintingActions.NONE, { _, _, _ -> }, { _, _ -> }, {}, PrepareObjectMenuActions.NONE, {}, {}, {}, { _, _ -> }, {}, {}, {},
        PreviewArrangeActions, PreviewRotationActions, PreviewScaleActions, {}, {}, {},
    )
}

/**
 * GLCanvas3D::Labels::render(): the copies of the current plate, each named
 * after its object, with its number when the object has several, and its
 * place in the print order while the plate prints by object; none while a
 * gizmo runs.
 */
@Composable
private fun objectLabels(state: PrepareUiState): Map<Int, PlateLabel> {
    if (state.gizmo != null || state.painting != null || state.cut != null || state.simplify != null) return emptyMap()
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
    )
}
