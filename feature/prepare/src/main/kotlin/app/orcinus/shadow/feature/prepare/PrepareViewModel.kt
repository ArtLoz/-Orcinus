package app.orcinus.shadow.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.BrimEarsOutcome
import app.orcinus.shadow.core.model.BrimPoint
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutConnectorShape
import app.orcinus.shadow.core.model.CutConnectorStyle
import app.orcinus.shadow.core.model.CutConnectorType
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPartsOutcome
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.EmbossPlacement
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.EmbossVolumeOutcome
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.model.LayerEditing
import app.orcinus.shadow.core.model.LayerEditingOutcome
import app.orcinus.shadow.core.model.LayerHeightEdit
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeasureHover
import app.orcinus.shadow.core.model.MeasureHoverOutcome
import app.orcinus.shadow.core.model.MeasureOutcome
import app.orcinus.shadow.core.model.MeasureRay
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.Measurement
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectCut
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsScope
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.SvgFileEdit
import app.orcinus.shadow.core.model.SvgPreviewOutcome
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.EmbossTransform
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.domain.DescribeFlatteningPlanesUseCase
import app.orcinus.shadow.domain.plate.AddCalibrationCubeToPlateUseCase
import app.orcinus.shadow.domain.plate.AddLayerRangeUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.AddObjectPartUseCase
import app.orcinus.shadow.domain.plate.AddPlateInstanceUseCase
import app.orcinus.shadow.domain.plate.AddPlateUseCase
import app.orcinus.shadow.domain.plate.AddPrimitiveUseCase
import app.orcinus.shadow.domain.plate.ApplySimplifyUseCase
import app.orcinus.shadow.domain.plate.BrimEarsTarget
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.ClonePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.CopyProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.CopyToClipboardUseCase
import app.orcinus.shadow.domain.plate.CutObjectUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DeletePlateUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.EditBrimEarsUseCase
import app.orcinus.shadow.domain.plate.EditLayerHeightsUseCase
import app.orcinus.shadow.domain.plate.EditPlateObjectUseCase
import app.orcinus.shadow.domain.plate.EmbossUseCase
import app.orcinus.shadow.domain.plate.EnablePaintedBrimUseCase
import app.orcinus.shadow.domain.plate.EnablePaintedFuzzySkinUseCase
import app.orcinus.shadow.domain.plate.ExportObjectMeshUseCase
import app.orcinus.shadow.domain.plate.FillBedWithInstancesUseCase
import app.orcinus.shadow.domain.plate.FindValidationSettingUseCase
import app.orcinus.shadow.domain.plate.InvalidateCutInfoUseCase
import app.orcinus.shadow.domain.plate.LockPlateUseCase
import app.orcinus.shadow.domain.plate.MeasureTarget
import app.orcinus.shadow.domain.plate.MeasureUseCase
import app.orcinus.shadow.domain.plate.MovePlateToFrontUseCase
import app.orcinus.shadow.domain.plate.MoveWipeTowerUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.OpenSimplifyUseCase
import app.orcinus.shadow.domain.plate.PaintObjectUseCase
import app.orcinus.shadow.domain.plate.PasteFromClipboardUseCase
import app.orcinus.shadow.domain.plate.PasteProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.PlateJobsUseCase
import app.orcinus.shadow.domain.plate.PreviewSimplifyUseCase
import app.orcinus.shadow.domain.plate.RemoveLastPlateInstancesUseCase
import app.orcinus.shadow.domain.plate.RemoveObjectPartUseCase
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.RenamePlateUseCase
import app.orcinus.shadow.domain.plate.ReplaceAllVolumesUseCase
import app.orcinus.shadow.domain.plate.ReplaceObjectVolumeUseCase
import app.orcinus.shadow.domain.plate.RequestEmbossUseCase
import app.orcinus.shadow.domain.plate.SelectLayerRangeUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SelectPlateUseCase
import app.orcinus.shadow.domain.plate.SeparatePlateInstancesUseCase
import app.orcinus.shadow.domain.plate.SetArrangeSettingsUseCase
import app.orcinus.shadow.domain.plate.SetExtruderUseCase
import app.orcinus.shadow.domain.plate.SetFlushOptionUseCase
import app.orcinus.shadow.domain.plate.SetNumberOfInstancesUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectPrintableUseCase
import app.orcinus.shadow.domain.plate.SetPlateSettingsUseCase
import app.orcinus.shadow.domain.plate.SetSettingsScopeUseCase
import app.orcinus.shadow.domain.plate.SetSliceModeUseCase
import app.orcinus.shadow.domain.plate.SimplifyPreview
import app.orcinus.shadow.domain.plate.SliceActionUseCase
import app.orcinus.shadow.domain.plate.TextFontsUseCase
import app.orcinus.shadow.domain.plate.TextStyleList
import app.orcinus.shadow.domain.plate.TextStylesUseCase
import app.orcinus.shadow.domain.plate.UndoRedoPlateUseCase
import app.orcinus.shadow.domain.plate.embossKindOf
import app.orcinus.shadow.domain.plate.isTextVolume
import app.orcinus.shadow.domain.plate.layerEditingObject
import app.orcinus.shadow.domain.plate.makeUniqueName
import app.orcinus.shadow.domain.plate.measuredVolumes
import app.orcinus.shadow.domain.plate.selectedEmbossVolume
import app.orcinus.shadow.domain.plate.toggledBold
import app.orcinus.shadow.domain.plate.toggledItalic
import app.orcinus.shadow.domain.plate.withFamily
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.preferences.SetPreferenceUseCase
import app.orcinus.shadow.render.scene.BrimEarsTouch
import app.orcinus.shadow.render.scene.CameraEye
import app.orcinus.shadow.render.scene.CutConnectorEvent
import app.orcinus.shadow.render.scene.CutLineEvent
import app.orcinus.shadow.render.scene.CutPlanes
import app.orcinus.shadow.render.scene.MeasureTouch
import app.orcinus.shadow.render.scene.ObjectTransforms
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.SurfaceHit
import app.orcinus.shadow.render.scene.WIPE_TOWER_INDEX
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class PrepareViewModel(
    observePlate: ObservePlateUseCase,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val addPrimitive: AddPrimitiveUseCase,
    private val addCalibrationCubeToPlate: AddCalibrationCubeToPlateUseCase,
    private val placePlateObject: PlacePlateObjectUseCase,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val setPlateObjectAutoDrop: SetPlateObjectAutoDropUseCase,
    private val addPlateInstance: AddPlateInstanceUseCase,
    private val removePlateInstance: RemovePlateInstanceUseCase,
    private val removeLastPlateInstances: RemoveLastPlateInstancesUseCase,
    private val setNumberOfInstances: SetNumberOfInstancesUseCase,
    private val addObjectPart: AddObjectPartUseCase,
    private val addLayerRange: AddLayerRangeUseCase,
    private val selectLayerRange: SelectLayerRangeUseCase,
    private val setSettingsScope: SetSettingsScopeUseCase,
    private val editPlateObject: EditPlateObjectUseCase,
    private val invalidateCutInfo: InvalidateCutInfoUseCase,
    private val clonePlateObjects: ClonePlateObjectsUseCase,
    private val separatePlateInstances: SeparatePlateInstancesUseCase,
    private val fillBedWithInstances: FillBedWithInstancesUseCase,
    private val setArrangeSettings: SetArrangeSettingsUseCase,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val pasteFromClipboard: PasteFromClipboardUseCase,
    private val undoRedoPlate: UndoRedoPlateUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val describeFlatteningPlanes: DescribeFlatteningPlanesUseCase,
    private val selectPlateObject: SelectPlateObjectUseCase,
    private val moveTower: MoveWipeTowerUseCase,
    private val paintObject: PaintObjectUseCase,
    private val sliceAction: SliceActionUseCase,
    private val setSliceMode: SetSliceModeUseCase,
    private val cancelPlateSlicing: CancelPlateSlicingUseCase,
    private val dismissPlateProblem: DismissPlateProblemUseCase,
    private val setPlateObjectPrintable: SetPlateObjectPrintableUseCase,
    private val setExtruder: SetExtruderUseCase,
    private val setFlushOption: SetFlushOptionUseCase,
    private val enablePaintedFuzzySkin: EnablePaintedFuzzySkinUseCase,
    private val cutObject: CutObjectUseCase,
    private val copyProcessSettings: CopyProcessSettingsUseCase,
    private val pasteProcessSettings: PasteProcessSettingsUseCase,
    private val exportObjectMesh: ExportObjectMeshUseCase,
    private val replaceObjectVolume: ReplaceObjectVolumeUseCase,
    private val openSimplify: OpenSimplifyUseCase,
    private val previewSimplify: PreviewSimplifyUseCase,
    private val applySimplifyUseCase: ApplySimplifyUseCase,
    private val replaceAllVolumesUseCase: ReplaceAllVolumesUseCase,
    private val selectPlate: SelectPlateUseCase,
    private val addPlate: AddPlateUseCase,
    private val deletePlate: DeletePlateUseCase,
    private val lockPlate: LockPlateUseCase,
    private val renamePlate: RenamePlateUseCase,
    private val movePlateToFront: MovePlateToFrontUseCase,
    private val plateJobs: PlateJobsUseCase,
    private val setPlateSettings: SetPlateSettingsUseCase,
    private val editLayerHeights: EditLayerHeightsUseCase,
    private val emboss: EmbossUseCase,
    private val textFonts: TextFontsUseCase,
    private val textStyles: TextStylesUseCase,
    private val requestEmboss: RequestEmbossUseCase,
    private val removeObjectPart: RemoveObjectPartUseCase,
    private val measureFeatures: MeasureUseCase,
    private val brimEarsTool: EditBrimEarsUseCase,
    private val enablePaintedBrim: EnablePaintedBrimUseCase,
    preferences: AppPreferences,
    private val setPreference: SetPreferenceUseCase,
    private val findValidationSetting: FindValidationSettingUseCase? = null,
) : ViewModel() {
    private val plate = observePlate()

    /** What the Preferences change on the canvas and the clone dialog. */
    val canvas: StateFlow<CanvasPreferences> = preferences.canvas

    /** An item of the canvas's View menu, which OrcaSlicer.conf keeps. */
    fun setCanvasOption(key: String, value: String) = setPreference(key, value)

    /** A stroke is being painted; the next one waits for the engine to answer. */
    private var painting = false

    /** A stroke began with a touch that was dropped: the next touch sent starts it. */
    private var strokeStarts = false

    /** The painting tool closing, which the next one waits for. */
    private var closingPainting: Job? = null

    /**
     * The gap areas the gap fill shows the painting with, null leaving it;
     * a slider sends many, so only the last one waiting is shown.
     */
    private val gapAreas = Channel<Double?>(Channel.CONFLATED)

    /** The planes the cut gizmo asks the engine about, with its connectors; a drag sends many, so only the last one waiting is described. */
    private val cutPlanes = Channel<CutMode>(Channel.CONFLATED)

    /** The cut gizmo as it was left, which it opens with again (the gizmo's members outlive it). */
    private var lastCut: CutMode? = null

    /** GLGizmoBase::INV_ZOOM of the 3D view: millimetres per desktop pixel at its target. */
    private var viewPixel = 0.1
    private var openingCut: Job? = null
    private var closingCut: Job? = null

    /**
     * The text and style the text tool's window asks to be embossed; a typist
     * sends many, so only the last one waiting is embossed (the job of
     * GLGizmoEmboss::process() cancels the one before).
     */
    private val textEdits = Channel<TextMode>(Channel.CONFLATED)

    /** StyleManager's styles and the last one loaded, read when the text tool first needs them. */
    private var styleList: TextStyleList? = null

    /**
     * StyleManager's m_style_cache.style_index: the stored style the tool's
     * style comes from, which a new text takes without the window's changes
     * (discard_style_changes()); null for a temporary style.
     */
    private var styleIndex: Int? = null

    /** One change of the text's volume at a time: the window's edits and the renaming of a style. */
    private val textLock = Mutex()

    /** GLGizmoEmboss::m_keep_up: the text's up is kept as it faces the camera; on from the start. */
    private var textKeepUp = true

    /** Translates the names of OrcaSlicer's default text styles (_u8L("NORMAL"), ...). */
    var styleNames: (String) -> String = { it }

    /** The fonts of the phone, by family, which the text tool offers once loaded. */
    private val fontFamilies = MutableStateFlow<List<TextFontFamily>>(emptyList())
    val textFamilies: StateFlow<List<TextFontFamily>> = fontFamilies.asStateFlow()

    /** The height under the finger on the variable layer height bar; null once it let go. */
    private val layerPress = MutableStateFlow<Double?>(null)

    /** The presses on the bar the engine works through, then accept_changes(). */
    private var layerPresses: Job? = null

    /** What each painting tool was left with, which it opens with again, as the desktop gizmos keep it. */
    private val paintingTools = mutableMapOf<PaintKind, PaintingMode>()

    /** The measuring tool's touches and resets, which the engine works through in their order. */
    private val measureCommands = Channel<MeasureCommand>(Channel.UNLIMITED)

    /** The brim ears tool's touches, which the engine works through in their order. */
    private val brimEarsTouches = Channel<BrimEarsTouch>(Channel.UNLIMITED)
    private val view = MutableStateFlow(PrepareViewState())

    /**
     * GLGizmoSimplify::m_configuration and the slider's static reduction,
     * which the gizmo keeps from one opening to the next; "Show wireframe" too.
     */
    private var simplifyConfig = SimplifyConfig()
    private var simplifyReduction = DEFAULT_REDUCTION
    private var simplifyWireframe = false

    /** What the gizmo asks the engine to work out; a newer request drops the older one (GLGizmoSimplify::process()). */
    private val simplifyRequests = MutableStateFlow<Pair<ObjectPartId, SimplifyConfig>?>(null)

    val state: StateFlow<PrepareUiState> = combine(plate, view, PlateState::toPrepareUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), plate.value.toPrepareUiState(PrepareViewState()))

    /** GLGizmosManager::m_restore_realistic_view_after_paint */
    private var restoreRealisticView = false

    init {
        // GLGizmosManager::open_gizmo(): the support, seam and fuzzy skin painting turn the realistic
        // view off while they are open, and it comes back once no gizmo is open after them.
        viewModelScope.launch {
            view.map { it.painting?.kind ?: it.gizmo ?: it.cut ?: it.simplify }.distinctUntilChanged().collect { gizmo ->
                when {
                    gizmo == null -> if (restoreRealisticView) {
                        setPreference(AppConfigKeys.OPENGL_REALISTIC_MODE, "true")
                        restoreRealisticView = false
                    }
                    gizmo in REALISTIC_OFF_PAINTING -> if (canvas.value.realistic) {
                        restoreRealisticView = true
                        setPreference(AppConfigKeys.OPENGL_REALISTIC_MODE, "false")
                    }
                    else -> restoreRealisticView = false
                }
            }
        }
        viewModelScope.launch {
            for (asked in cutPlanes) {
                val plane = asked.plane ?: continue
                if (view.value.cut == null) continue
                val groove = asked.groove.takeIf { asked.kind == CutKind.DOVETAIL }
                val parts = asked.partSelection.takeIf { asked.kind == CutKind.PLANAR }
                // reset_cut_by_contours(): the dovetail's parts once nothing is dragged.
                val outcome = cutObject.describe(plane, asked.connectors, asked.snapSpace, asked.snapBulge, groove, preview = groove != null && !asked.shaping, parts = parts)
                if (outcome !is CutPlaneOutcome.Success) continue
                view.update { state ->
                    state.cut?.let {
                        state.copy(
                            cut = it.copy(
                                described = outcome.plane,
                                describedPlane = plane,
                                describedConnectors = asked.connectors,
                                describedGroove = groove,
                                describedParts = parts?.selected,
                            ),
                        )
                    } ?: state
                }
            }
        }
        // GLGizmoEmboss::process(): the latest text and style the window asks
        // for, embossed anew; the volume the engine wrote is the one edited next.
        viewModelScope.launch {
            for (asked in textEdits) {
                textLock.withLock {
                    val open = view.value.text ?: return@withLock
                    if (open.volume != asked.volume || asked.blank || asked.unknownFont) return@withLock
                    view.update { it.copy(text = it.text?.copy(busy = true)) }
                    val edited = emboss.update(asked.volume, asked.text, asked.style)
                    view.update { state ->
                        val now = state.text ?: return@update state
                        state.copy(text = now.copy(volume = edited?.takeIf { now.volume == asked.volume } ?: now.volume, busy = false))
                    }
                }
            }
        }
        // GLGizmoEmboss::data_changed() and on_mouse_change_selection(): the
        // tool follows the selection to another text, and closes on anything else.
        viewModelScope.launch {
            plate.map { it.selectedInstances to it.selectedPart }.distinctUntilChanged().collect {
                if (textLock.isLocked) return@collect
                view.value.text?.takeUnless { it.busy }?.let { open ->
                    val selected = plate.value.selectedEmbossVolume(EmbossKind.TEXT)
                    when {
                        selected == null -> closeText()
                        selected != open.volume -> openText(selected)
                    }
                }
                view.value.svg?.takeUnless { it.busy }?.let { open ->
                    val selected = plate.value.selectedEmbossVolume(EmbossKind.SVG)
                    when {
                        selected == null -> closeSvg()
                        selected != open.volume -> openSvg(selected)
                    }
                }
            }
        }
        // The object list's "Edit text": the tool opens on the volume.
        viewModelScope.launch {
            plate.map { it.embossRequest }.distinctUntilChanged().collect { request ->
                if (request is EmbossRequest.Edit) {
                    requestEmboss.done()
                    when (plate.value.embossKindOf(request.volume)) {
                        EmbossKind.TEXT -> openText(request.volume)
                        EmbossKind.SVG -> openSvg(request.volume)
                        null -> Unit
                    }
                }
            }
        }
        // Plater::priv::selection_changed(): the variable layer height closes once
        // the selection is no object it can edit.
        viewModelScope.launch {
            plate.map { it.layerEditing && it.layerEditingObject() == null }.distinctUntilChanged().collect { lost ->
                if (lost) editLayerHeights.enable(false)
            }
        }
        // Plater::priv::on_action_layersediting(): the bar opens over the other
        // tools of the canvas, which close (GLGizmosManager::reset_all_states()).
        viewModelScope.launch {
            plate.map { it.layerEditing }.distinctUntilChanged().collect { on ->
                if (!on) return@collect
                closeCut()
                closePainting()
                closeEmbossTools()
                openSimplify.close()
                view.update { it.copy(gizmo = null, arrangeOptionsOpen = false) }
            }
        }
        // LayersEditing::select_object(): the engine opens on the object the bar
        // edits, and anew once it changed otherwise than the bar changed it.
        viewModelScope.launch {
            plate.map(editLayerHeights::targetOf).distinctUntilChanged().collectLatest { target ->
                if (target == null) {
                    layerPress.value = null
                    editLayerHeights.end()
                    view.update { it.copy(layerDescription = null, layerCursor = null) }
                    return@collectLatest
                }
                // A page made anew has not seen what the engine opened before it.
                val seen = view.value.layerDescription?.first == target.mesh
                when (val outcome = editLayerHeights.open(target, again = !seen)) {
                    is LayerEditingOutcome.Success -> showLayers(target.mesh, outcome.editing)
                    is LayerEditingOutcome.Failure -> view.update { it.copy(layerDescription = null) }
                    null -> Unit
                }
            }
        }
        // GLGizmoMeasure::data_changed(): while the tool is open the engine
        // measures the selected volumes, registered anew with the selections
        // reset whenever the selection or the plate changes; the tool closes
        // once nothing is selected (GLGizmosManager::refresh_on_off_state()).
        viewModelScope.launch {
            combine(plate, view.map { it.measure != null }.distinctUntilChanged()) { state, open ->
                when {
                    !open -> MeasureChange.Closed
                    state.measuredVolumes().isEmpty() -> MeasureChange.Lost
                    else -> measureFeatures.targetOf(state)?.let(MeasureChange::Open) ?: MeasureChange.Waiting
                }
            }.distinctUntilChanged().collectLatest { change ->
                when (change) {
                    MeasureChange.Closed -> measureFeatures.end()
                    MeasureChange.Lost -> closeMeasure()
                    MeasureChange.Waiting -> Unit
                    is MeasureChange.Open -> when (val outcome = measureFeatures.open(change.target)) {
                        is MeasureOutcome.Success -> showMeasurement(outcome.measurement)
                        is MeasureOutcome.Failure -> closeMeasure()
                        null -> Unit
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                var command = measureCommands.receive()
                // A finger moving faster than the engine answers: only its latest place is explored.
                while (command is MeasureCommand.Touch && command.touch is MeasureTouch.Explore) {
                    command = measureCommands.tryReceive().getOrNull() ?: break
                }
                val pointSelection = view.value.measure?.pointSelection ?: continue
                when (command) {
                    is MeasureCommand.Reset -> (measureFeatures.reset(command.reset) as? MeasureOutcome.Success)?.let { showMeasurement(it.measurement) }
                    is MeasureCommand.Scale -> (measureFeatures.scale(command.ratio) as? MeasureOutcome.Success)?.let { showMeasurement(it.measurement) }
                    is MeasureCommand.Touch -> when (val touch = command.touch) {
                        is MeasureTouch.Explore -> (measureFeatures.hover(touch.ray(pointSelection)) as? MeasureHoverOutcome.Success)?.let { showMeasureHover(it.hover) }
                        // on_mouse() for a left press, and the finger is gone: nothing is under it any more.
                        is MeasureTouch.Select -> (measureFeatures.select(touch.ray(pointSelection)) as? MeasureOutcome.Success)?.let { showMeasurement(it.measurement) }
                        MeasureTouch.Leave -> view.update { state -> state.measure?.let { state.copy(measure = it.copy(hover = null)) } ?: state }
                    }
                }
            }
        }
        // GLGizmoBrimEars: the engine keeps the first layer of the copy the tool
        // is open on, sliced anew once the copy changed otherwise than by its
        // ears; the tool closes once the selection is another
        // (EVT_GLCANVAS_RESETGIZMOS of on_render()).
        viewModelScope.launch {
            combine(plate, view.map { it.brimEars?.copy }.distinctUntilChanged()) { state, copy ->
                when {
                    copy == null -> BrimEarsChange.Closed
                    brimEarsTool.copyOf(state) != copy -> BrimEarsChange.Lost
                    else -> brimEarsTool.targetOf(state, copy)?.let(BrimEarsChange::Open) ?: BrimEarsChange.Waiting
                }
            }.distinctUntilChanged().collectLatest { change ->
                when (change) {
                    BrimEarsChange.Closed -> brimEarsTool.end()
                    BrimEarsChange.Lost -> closeBrimEars()
                    BrimEarsChange.Waiting -> Unit
                    is BrimEarsChange.Open -> when (val outcome = brimEarsTool.open(change.target)) {
                        is BrimEarsOutcome.Success -> view.update { state ->
                            state.brimEars?.let { mode ->
                                state.copy(
                                    brimEars = mode.copy(
                                        setup = outcome.setup,
                                        headDiameter = mode.headDiameter ?: outcome.setup.defaultHeadDiameter,
                                        detectionRadius = mode.detectionRadius.coerceAtMost(outcome.setup.detectionRadiusMax),
                                    ),
                                )
                            } ?: state
                        }
                        is BrimEarsOutcome.Failure -> closeBrimEars()
                        null -> Unit
                    }
                }
            }
        }
        // find_single() whenever the ears change: the ones that touch nothing.
        viewModelScope.launch {
            combine(plate, view) { state, current -> current.brimEars?.takeIf { it.setup != null }?.let { mode -> brimEarsOf(state, mode) } }
                .distinctUntilChanged()
                .collectLatest { points ->
                    if (points == null) return@collectLatest
                    val invalid = brimEarsTool.invalid(points).toSet()
                    view.update { state -> state.brimEars?.let { state.copy(brimEars = it.copy(invalid = invalid)) } ?: state }
                }
        }
        viewModelScope.launch {
            while (true) {
                var touch = brimEarsTouches.receive()
                // A finger faster than the engine: only its latest place is explored, and a dragged ear goes to the last.
                while (true) {
                    val next = brimEarsTouches.tryReceive().getOrNull() ?: break
                    if (touch !is BrimEarsTouch.Explore && !(touch is BrimEarsTouch.Drag && next is BrimEarsTouch.Drag)) handleBrimEarsTouch(touch)
                    touch = next
                }
                handleBrimEarsTouch(touch)
            }
        }
        viewModelScope.launch {
            for (area in gapAreas) {
                if (view.value.painting != null) paintObject.setGapFill(area).also(::showStrokes)
            }
        }
        // GLGizmoFuzzySkin warns while fuzzy skin is disabled for the object it
        // paints, as the object's settings and the process preset have it now.
        viewModelScope.launch {
            val fuzzySkinPainting = view.map { it.painting?.takeIf { mode -> mode.kind == PaintKind.FUZZY_SKIN }?.mesh }.distinctUntilChanged()
            combine(plate, fuzzySkinPainting) { state, mesh -> mesh?.let { FuzzySkinSettings.of(state, it) } }
                .distinctUntilChanged()
                .collectLatest { settings ->
                    val disabled = settings != null && paintObject.fuzzySkinDisabled(settings.mesh)
                    view.update { state ->
                        state.painting?.takeIf { it.kind == PaintKind.FUZZY_SKIN }?.let { state.copy(painting = it.copy(fuzzySkinDisabled = disabled)) } ?: state
                    }
                }
        }
        // "Simplify Model" opens the gizmo from the canvas and from the object
        // list; it closes once the plate no longer has the volume.
        viewModelScope.launch {
            plate.map { state -> state.simplifyTarget?.takeIf { target -> state.objects.any { it.mesh == target.mesh } } to state.simplifyTarget }
                .distinctUntilChanged()
                .collect { (target, requested) ->
                    when {
                        requested != null && target == null -> openSimplify.close()
                        target == null -> endSimplify()
                        view.value.simplify?.volume != target -> startSimplify(target)
                    }
                }
        }
        viewModelScope.launch {
            simplifyRequests.collectLatest { request ->
                val (volume, config) = request ?: return@collectLatest
                view.update { it.copy(simplify = it.simplify?.copy(running = true)) }
                val preview = previewSimplify(volume, config)
                val mode = view.value.simplify
                if (preview == null || mode == null || mode.volume != volume) {
                    preview?.let(previewSimplify::discard)
                    view.update { it.copy(simplify = it.simplify?.copy(running = false)) }
                    return@collectLatest
                }
                mode.preview?.let(previewSimplify::discard)
                view.update { it.copy(simplify = mode.shown(preview)) }
            }
        }
        // The objects a load or an edit added come selected; the rotation
        // window then starts from the one the tools work on.
        viewModelScope.launch {
            var known: Set<ScenePath>? = null
            plate.collect { state ->
                val added = known?.let { before -> state.objects.any { it.mesh !in before } } ?: false
                known = state.objects.mapTo(HashSet(), PlateObject::mesh)
                if (added) view.update { it.copy(rotationStart = state.selectedCopy?.inspection?.placement) }
            }
        }
        // GLGizmoFlatten::is_plane_update_necessary(): the faces follow the object and its scale.
        viewModelScope.launch {
            combine(plate, view) { plate, view ->
                val target = plate.selected?.takeIf { view.gizmo == PlateGizmo.LAY_ON_FACE }
                val copy = plate.selectedCopy
                val profiles = plate.profiles
                if (target == null || copy == null || profiles == null) {
                    null
                } else {
                    FlatteningKey(target, copy, profiles, copy.inspection.dimensions, copy.inspection.unscaledDimensions)
                }
            }
                .distinctUntilChanged { old, new -> old?.sameFaces(new) ?: (new == null) }
                .collectLatest { key ->
                    view.update { it.copy(flatteningPlanes = emptyList()) }
                    if (key == null) return@collectLatest
                    val outcome = describeFlatteningPlanes(key.plateObject, key.instance, key.profiles)
                    if (outcome is FlatteningPlanesOutcome.Success) view.update { it.copy(flatteningPlanes = outcome.planes) }
                }
        }
    }

    private class FlatteningKey(
        val plateObject: PlateObject,
        val instance: PlateInstance,
        val profiles: SlicingProfileSelection,
        val dimensions: ModelDimensions,
        val unscaledDimensions: ModelDimensions,
    ) {
        fun sameFaces(other: FlatteningKey?) = other != null && other.plateObject.mesh == plateObject.mesh &&
            other.dimensions == dimensions && other.unscaledDimensions == unscaledDimensions && other.profiles == profiles
    }

    /** The documents the user picked at once (Plater::add_file()). */
    fun addModels(references: List<String>) = addModelToPlate(references.map(::ExternalDocumentReference))

    fun addCalibrationCube() = addCalibrationCubeToPlate()

    /** The setting a validation notification's "Jump to" opens, for the page to show. */
    private val settingJumps = Channel<SearchOption>(Channel.CONFLATED)
    val settingToOpen: Flow<SearchOption> = settingJumps.receiveAsFlow()

    /**
     * "Jump to" of a validation notification: the object or the copy it is
     * about is selected (ObjectList::select_items()), and the setting it names
     * opens on its tab (Sidebar::jump_to_option()).
     */
    fun jumpTo(notice: ValidationNotice) {
        notice.target?.let { selectPlateObject(it) }
        val find = findValidationSetting ?: return
        if (notice.option.isEmpty()) return
        viewModelScope.launch { find(notice.option)?.let { settingJumps.trySend(it) } }
    }

    /** Clearing the selection closes the gizmo, as GLGizmosManager does when it is no longer activable. */
    fun selectObject(index: Int?) {
        // The wipe tower is no object of the plate: picking it takes the
        // selection off the objects, as the desktop canvas does.
        if (index == WIPE_TOWER_INDEX) {
            view.update { it.copy(wipeTowerSelected = true) }
            select(null, null)
            return
        }
        view.update { if (it.wipeTowerSelected) it.copy(wipeTowerSelected = false) else it }
        val copy = index?.let { state.value.sceneCopies.getOrNull(it) }
        select(copy?.id, copy?.instance?.inspection)
    }

    /** GLCanvas3D::WipeTowerInfo::apply_wipe_tower(): the tower was dragged across the plate. */
    fun moveWipeTower(x: Double, y: Double) = moveTower(x, y)

    /** A tap on a plate or its number: it becomes the current plate. */
    fun selectPlate(index: Int) = selectPlate.invoke(index)

    /** The toolbar's "Add plate". */
    fun addPlate() = addPlate.invoke()

    /** The plate's "Remove current plate (if not last one)". */
    fun deletePlate(index: Int) = deletePlate.invoke(index)

    /** The plate's lock icon. */
    fun lockPlate(index: Int) = lockPlate.invoke(index)

    /** PlateNameEditDialog's OK. */
    fun renamePlate(index: Int, name: String) = renamePlate.invoke(index, name)

    /** The plate's "Move plate to the front". */
    fun movePlateToFront(index: Int) = movePlateToFront.invoke(index)

    /** The plate's "Auto orient objects on current plate" and "Arrange objects on current plate". */
    fun orientPlate(index: Int) = plateJobs.orient(index)

    fun arrangePlate(index: Int) = plateJobs.arrange(index)

    /** PlateSettingsDialog's OK for the current plate. */
    fun setPlateSettings(choice: PlateSettingsChoice, vaseSettingsAgreed: Boolean) = setPlateSettings.invoke(choice, vaseSettingsAgreed)

    /**
     * The painting gizmos (GLGizmoPainterBase): the tool of [kind] opens on
     * the selected object, paints while a finger moves over it, and keeps the
     * paint when it closes; the tool of another kind closes first, as
     * GLGizmosManager opens one gizmo at a time. A stroke is dropped while the
     * engine is still painting the one before, so a fast finger does not pile
     * up work.
     */
    fun togglePainting(kind: PaintKind = PaintKind.COLOR) {
        closeCut()
        closeEmbossTools()
        editLayerHeights.enable(false)
        val open = view.value.painting
        if (open != null) {
            closePainting()
            if (open.kind == kind) return
        }
        val mesh = state.value.sceneCopies.getOrNull(state.value.selectedObject ?: -1)?.plateObject?.mesh ?: return
        openSimplify.close()
        // GLGizmoFdmSupports::on_shutdown() left the highlight at 0.
        val mode = paintingTools[kind]?.copy(mesh = mesh, painted = false, canUndo = false, canRedo = false, highlightAngle = 0.0) ?: PaintingMode(
            mesh = mesh,
            kind = kind,
            // GLGizmoFdmSupports paints with the circle, the colour tool with the sphere.
            tool = if (kind == PaintKind.COLOR) PaintTool.BRUSH else PaintTool.CIRCLE,
        )
        view.update { it.copy(painting = mode, gizmo = null) }
        val closing = closingPainting
        viewModelScope.launch {
            closing?.join()
            paintObject.begin(mesh, kind).also(::showStrokes)
            if (mode.tool == PaintTool.GAP_FILL) paintObject.setGapFill(mode.gapArea).also(::showStrokes)
        }
    }

    /** The object menu's "Simplify Model" over the copy at [index] (ObjectList::simplify). */
    fun simplifyAt(index: Int) = copyAt(index)?.let { openSimplify.ofObject(it, wholeObject = false) }

    /** check_gizmos_closed_except(): the gizmo opens once no other tool of the canvas is. */
    private fun startSimplify(volume: ObjectPartId) {
        val current = view.value
        if (current.painting != null || current.gizmo != null || current.cut != null) {
            openSimplify.refuse()
            return
        }
        current.simplify?.preview?.let(previewSimplify::discard)
        closeEmbossTools()
        // A gizmo running closes the variable layer height (_deactivate_layersediting_menu()).
        editLayerHeights.enable(false)
        // A volume selected anew: half the triangles taken away, and the detail level kept.
        val config = simplifyConfig.copy(decimateRatio = DEFAULT_DECIMATE_RATIO, wantedCount = -1)
        view.update { it.copy(simplify = SimplifyMode(volume, config, simplifyReduction, simplifyWireframe), arrangeOptionsOpen = false) }
        simplifyRequests.value = volume to config
    }

    private fun endSimplify() {
        val mode = view.value.simplify ?: return
        simplifyConfig = mode.config
        simplifyReduction = mode.reduction
        simplifyWireframe = mode.wireframe
        simplifyRequests.value = null
        mode.preview?.let(previewSimplify::discard)
        view.update { it.copy(simplify = null) }
    }

    /** The radio buttons: the detail level, or the decimate ratio. */
    fun setSimplifyUseCount(useCount: Boolean) = updateSimplify { it.copy(config = it.config.copy(useCount = useCount)) }

    /** The detail level's step slider: the largest error a collapsed edge may have. */
    fun setSimplifyReduction(reduction: Int) = updateSimplify { mode ->
        val level = reduction.coerceIn(0, MAX_ERRORS.lastIndex)
        mode.copy(reduction = level, config = mode.config.copy(maxError = MAX_ERRORS[level]))
    }

    /** The decimate ratio's slider and field: the share of the triangles taken away. */
    fun setSimplifyDecimateRatio(ratio: Float) = updateSimplify { mode ->
        val clamped = when {
            ratio < 0f -> 0.01f
            ratio > 100f -> 100f
            else -> ratio
        }
        val original = mode.preview?.original ?: return@updateSimplify mode.copy(config = mode.config.copy(decimateRatio = clamped, wantedCount = -1))
        mode.copy(config = mode.config.copy(decimateRatio = clamped).withCountByRatio(original))
    }

    fun setSimplifyWireframe(wireframe: Boolean) {
        view.update { it.copy(simplify = it.simplify?.copy(wireframe = wireframe)) }
    }

    /** Apply: the volume takes the mesh the gizmo shows. */
    fun applySimplify() {
        val mode = view.value.simplify?.takeIf { it.canApply } ?: return
        applySimplifyUseCase(mode.volume, mode.config)
    }

    /** Cancel (GLGizmoSimplify::close()). */
    fun closeSimplify() = openSimplify.close()

    override fun onCleared() {
        // The gizmo's mesh file goes with the screen that showed it.
        view.value.simplify?.preview?.let(previewSimplify::discard)
    }

    /** A change of the configuration: the engine works the mesh out again. */
    private fun updateSimplify(change: (SimplifyMode) -> SimplifyMode) {
        val mode = view.value.simplify ?: return
        val changed = change(mode)
        view.update { it.copy(simplify = changed) }
        if (changed.config.request() != mode.config.request()) simplifyRequests.value = changed.volume to changed.config
    }

    fun closePainting() {
        val open = view.value.painting ?: return
        paintingTools[open.kind] = open
        view.update { it.copy(painting = null) }
        closingPainting = viewModelScope.launch { paintObject.end() }
    }

    /** The state the finger paints: a filament, enforcing or blocking, or the eraser ([PaintState.NONE]). */
    fun paintWith(state: Int) {
        view.update { view -> view.painting?.let { view.copy(painting = it.copy(state = state)) } ?: view }
    }

    fun setFillAngle(angle: Double) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(fillAngle = angle)) } ?: state }
    }

    /** "Erase all" of the painting tool, which its Undo brings back. */
    fun clearPainting() {
        if (view.value.painting == null) return
        viewModelScope.launch { paintObject.clear().also(::showStrokes) }
    }

    fun setBrushRadius(radius: Double) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(radius = radius)) } ?: state }
    }

    /**
     * GLGizmoFdmSupports::tool_changed(): the gap fill shows the painting as
     * it would leave it while it is chosen (the selectors' filter state).
     */
    fun setPaintTool(tool: PaintTool) {
        val before = view.value.painting ?: return
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(tool = tool)) } ?: state }
        when {
            tool == PaintTool.GAP_FILL && before.tool != PaintTool.GAP_FILL -> gapAreas.trySend(before.gapArea)
            tool != PaintTool.GAP_FILL && before.tool == PaintTool.GAP_FILL -> gapAreas.trySend(null)
        }
    }

    /** "Gap area" of the gap fill, which the painting shows the gap fill with at once. */
    fun setGapArea(area: Double) {
        val mode = view.value.painting ?: return
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(gapArea = area)) } ?: state }
        if (mode.tool == PaintTool.GAP_FILL) gapAreas.trySend(area)
    }

    /** "Perform" of the gap fill, which the tool's Undo brings back. */
    fun fillGaps() {
        if (view.value.painting?.tool != PaintTool.GAP_FILL) return
        viewModelScope.launch { paintObject.fillGaps().also(::showStrokes) }
    }

    /**
     * The toolbar's Cut (GLGizmoCut3D): the gizmo opens on the selected copy,
     * the other tools closing first, or closes. Its plane starts at the centre
     * of the copy's bounding box, not rotated, unless the gizmo was left on
     * that very box (update_bb()); "After cut" stays as it was left.
     */
    fun toggleCut() {
        if (view.value.cut != null) return closeCut()
        val state = state.value
        val copy = state.sceneCopies.getOrNull(state.selectedObject ?: -1) ?: return
        if (!state.canManipulate) return
        closePainting()
        openSimplify.close()
        closeEmbossTools()
        editLayerHeights.enable(false)
        val left = lastCut
        val placement = copy.instance.inspection.placement.columns
        val mode = CutMode(
            mesh = copy.id.mesh,
            instance = copy.id.instance,
            instanceOffset = Vector3(placement[12], placement[13], placement[14]),
            keepUpper = left?.keepUpper ?: true,
            keepLower = left?.keepLower ?: true,
            keepAsParts = left?.keepAsParts ?: false,
            placeOnCutUpper = left?.placeOnCutUpper ?: true,
            placeOnCutLower = left?.placeOnCutLower ?: false,
            flipUpper = left?.flipUpper ?: false,
            flipLower = left?.flipLower ?: false,
        )
        view.update { it.copy(cut = mode, gizmo = null, arrangeOptionsOpen = false) }
        val closing = closingCut
        openingCut = viewModelScope.launch {
            closing?.join()
            val outcome = cutObject.begin(mode.mesh, mode.instance)
            if (outcome !is CutObjectOutcome.Success) {
                view.update { state -> if (state.cut?.mesh == mode.mesh) state.copy(cut = null) else state }
                return@launch
            }
            val kept = left?.takeIf { it.mesh == mode.mesh && it.instance == mode.instance && it.boundsMin == outcome.min && it.boundsMax == outcome.max }
            // update_bb(): a new box gives the grooves their first size, half the
            // mean grabber size (32 desktop pixels) deep and four times as wide.
            val depth = maxOf(1.0, 0.5 * 32.0 * viewPixel)
            val grooveInit = CutGroove(depth = depth, width = 4.0 * depth, flapsAngle = Math.PI / 3.0, angle = 0.0)
            val groove = kept?.groove ?: (left?.groove ?: CutGroove()).copy(
                depth = grooveInit.depth,
                width = grooveInit.width,
                flapsAngle = grooveInit.flapsAngle,
                angle = grooveInit.angle,
            )
            val bounds = mode.copy(
                boundsMin = outcome.min,
                boundsMax = outcome.max,
                kind = left?.kind ?: CutKind.PLANAR,
                groove = groove,
                grooveInit = kept?.grooveInit ?: grooveInit,
            )
            val plane = kept?.plane ?: CutPlanes.at(bounds.boundsCenter ?: return@launch)
            // The object keeps its connectors while the gizmo is closed (ModelObject::cut_connectors).
            val opened = bounds.copy(plane = plane, connectors = kept?.connectors.orEmpty(), snapSpace = left?.snapSpace ?: CutMode.SNAP_SPACE, snapBulge = left?.snapBulge ?: CutMode.SNAP_BULGE)
                .let { it.copy(snapshots = listOf(it.current()), snapshot = 0) }
            view.update { state -> if (state.cut?.mesh == mode.mesh && state.cut.instance == mode.instance) state.copy(cut = opened) else state }
            cutPlanes.trySend(opened)
        }
    }

    /** "Cancel" (reset_all_gizmos()): the gizmo closes, and the engine lets its object go. */
    fun closeCut() {
        val open = view.value.cut ?: return
        if (open.plane != null) lastCut = open
        view.update { it.copy(cut = null) }
        val opening = openingCut
        closingCut = viewModelScope.launch {
            opening?.join()
            cutObject.end()
        }
    }

    /**
     * The plane a grabber of the 3D view moved or turned the cut to
     * (on_dragging()); the plane keeps within reach of the object as
     * set_center_pos() keeps it. [finished] once the finger let go, which takes
     * the gizmo's snapshot ("Move cut plane", "Rotate cut plane").
     */
    fun setCutPlane(plane: Transform3, finished: Boolean) {
        val mode = view.value.cut ?: return
        val current = mode.plane ?: return
        val placed = if (CutPlanes.sameRotation(plane, current)) movedCut(mode, CutPlanes.center(plane)) ?: current else plane
        updateCut(finished) { it.copy(plane = placed, shaping = !finished) }
    }

    /** A tap on the plane outside the section: flip_cut_plane(), which turns the pieces over too (turn_over_selection()). */
    fun flipCutPlane() {
        val plane = view.value.cut?.plane ?: return
        updateCut(snapshot = true) { it.copy(plane = CutPlanes.flipped(plane), parts = it.parts?.map { part -> part.copy(upper = !part.upper) }) }
    }

    /**
     * A long press on the object (a right click, GLGizmoCut3D::on_mouse()): the
     * object is split into its pieces at the plane the first time
     * (process_contours()), and the piece the finger's ray from [origin] along
     * [direction] meets first goes to the other part.
     */
    fun selectCutPart(origin: Vector3, direction: Vector3) {
        val mode = view.value.cut?.takeIf { it.kind == CutKind.PLANAR && !it.editingConnectors } ?: return
        val plane = mode.plane ?: return
        val selection = mode.partSelection ?: CutPartSelection(plane, emptyList())
        viewModelScope.launch {
            val outcome = cutObject.selectPart(selection, origin, direction)
            if (outcome !is CutPartsOutcome.Success) return@launch
            // The plane moved meanwhile: its pieces are gone with it.
            updateCut(snapshot = false) { cut ->
                if (cut.plane != plane || cut.kind != CutKind.PLANAR) cut else cut.copy(parts = outcome.parts, partsPlane = selection.plane, keepAsParts = false)
            }
        }
    }

    /** "Draw cut line" (Shift + drag on the desktop): the next drag of a finger draws it. */
    fun setCutLineDrawing(drawing: Boolean) = updateCut(snapshot = false) { it.copy(drawingLine = drawing) }

    /**
     * process_cut_line(): a finger draws the line and, once it lets go, the
     * plane is laid across it ("Cut by line") where its centre still lies in
     * the object's box; drawing it takes the pieces away and holds the
     * dovetail's parts back (m_groove_editing).
     */
    fun cutLineEvent(event: CutLineEvent) {
        val mode = view.value.cut ?: return
        when (event) {
            CutLineEvent.Start -> updateCut(snapshot = false) { it.copy(parts = null, partsPlane = null, shaping = it.kind == CutKind.DOVETAIL) }
            is CutLineEvent.Drawn -> {
                val boundsCenter = mode.boundsCenter
                val candidate = boundsCenter?.let { CutPlanes.byLine(event.start, event.end, event.direction, it) }
                if (candidate == null) {
                    updateCut(snapshot = false) { it.copy(drawingLine = false, shaping = false) }
                    return
                }
                viewModelScope.launch {
                    val outcome = cutObject.describe(candidate, emptyList(), mode.snapSpace, mode.snapBulge)
                    val fits = outcome is CutPlaneOutcome.Success &&
                        CutPlanes.lineCutFits(candidate, mode.instanceOffset, outcome.plane.min, outcome.plane.max)
                    updateCut(snapshot = fits) { cut ->
                        val done = cut.copy(drawingLine = false, shaping = false)
                        if (fits) done.copy(plane = candidate) else done
                    }
                }
            }
        }
    }

    /** "Cut position": the height of the plane's centre (render_move_center_input(Z)). */
    fun setCutPosition(z: Double) {
        val mode = view.value.cut ?: return
        val plane = mode.plane ?: return
        val center = CutPlanes.center(plane)
        val moved = movedCut(mode, Vector3(center.x, center.y, z)) ?: return
        updateCut(snapshot = true) { it.copy(plane = moved) }
    }

    /** "Reset cutting plane", and "Reset", which also takes the connectors off: reset_cut_plane(). */
    fun resetCutPlane() {
        val center = view.value.cut?.boundsCenter ?: return
        updateCut(snapshot = true) { it.copy(plane = CutPlanes.at(center)) }
    }

    /** "Keep" of the upper part and of the lower part. */
    fun setCutKeep(upper: Boolean, keep: Boolean) = updateCut(snapshot = false) {
        if (it.keepAsParts) it else if (upper) it.copy(keepUpper = keep) else it.copy(keepLower = keep)
    }

    /** "Place on cut", which clears "Flip" of the part. */
    fun setCutPlaceOnCut(upper: Boolean, place: Boolean) = updateCut(snapshot = false) {
        when {
            it.keepAsParts -> it
            upper -> if (it.keepUpper) it.copy(placeOnCutUpper = place, flipUpper = if (place) false else it.flipUpper) else it
            else -> if (it.keepLower) it.copy(placeOnCutLower = place, flipLower = if (place) false else it.flipLower) else it
        }
    }

    /** "Flip", which clears "Place on cut" of the part. */
    fun setCutFlip(upper: Boolean, flip: Boolean) = updateCut(snapshot = false) {
        when {
            it.keepAsParts -> it
            upper -> if (it.keepUpper) it.copy(flipUpper = flip, placeOnCutUpper = if (flip) false else it.placeOnCutUpper) else it
            else -> if (it.keepLower) it.copy(flipLower = flip, placeOnCutLower = if (flip) false else it.placeOnCutLower) else it
        }
    }

    /** "Cut to parts": both halves kept as the parts of one object, neither placed on the cut nor flipped; not with pieces. */
    fun setCutToParts(parts: Boolean) = updateCut(snapshot = false) {
        if (it.parts != null) {
            it
        } else if (parts) {
            it.copy(
                keepAsParts = true,
                keepUpper = true,
                keepLower = true,
                placeOnCutUpper = false,
                placeOnCutLower = false,
                flipUpper = false,
                flipLower = false,
            )
        } else {
            it.copy(keepAsParts = false)
        }
    }

    /** "Perform cut": the gizmo closes, then the object is cut (perform_cut()); the connectors' volumes are named after [connectorName]. */
    fun performCut(connectorName: String = "Connector") {
        val mode = view.value.cut?.takeIf { it.canPerform } ?: return
        val plane = mode.plane ?: return
        closeCut()
        editPlateObject.cut(
            mode.mesh,
            ObjectCut(
                instance = mode.instance,
                plane = plane,
                keepUpper = mode.keepUpper,
                keepLower = mode.keepLower,
                keepAsParts = mode.keepAsParts,
                placeOnCutUpper = mode.placeOnCutUpper,
                placeOnCutLower = mode.placeOnCutLower,
                flipUpper = mode.flipUpper,
                flipLower = mode.flipLower,
                connectors = mode.connectors,
                snapSpace = mode.snapSpace,
                snapBulge = mode.snapBulge,
                connectorName = connectorName,
                dovetail = mode.kind == CutKind.DOVETAIL,
                groove = mode.groove,
                radius = mode.radius,
                parts = mode.partSelection.takeIf { mode.kind == CutKind.PLANAR },
            ),
        )
    }

    /** The 3D view's millimetres per desktop pixel, which the grooves take their first size from. */
    fun setViewPixel(pixel: Double) {
        viewPixel = pixel
    }

    /** "Mode": "Planar" or "Dovetail" ("Change cut mode"). */
    fun setCutKind(kind: CutKind) = updateCut(snapshot = true) {
        // switch_to_mode(): the pieces go (reset_cut_by_contours()).
        if (it.connectors.isEmpty() && it.kind != kind) it.copy(kind = kind, shaping = false, parts = null, partsPlane = null) else it
    }

    /**
     * The groove's inputs: its depth and width with their tolerances, the flap
     * and groove angles, the count and the gap; [finished] once a slider is let
     * go, which works the parts out again (m_is_slider_editing_done).
     */
    fun setCutGroove(finished: Boolean, change: (CutGroove) -> CutGroove) = updateCut(snapshot = finished) { cut ->
        val groove = change(cut.groove).let { it.copy(count = it.count.coerceIn(1, 100)) }
        cut.copy(groove = groove, shaping = !finished)
    }

    /** A reset of the groove's inputs, which brings back what the gizmo started with ("Reset: <label>"). */
    fun resetCutGroove(change: (current: CutGroove, init: CutGroove) -> CutGroove) = updateCut(snapshot = true) { cut ->
        cut.copy(groove = change(cut.groove, cut.grooveInit), shaping = false)
    }

    /** "Add connectors" or "Edit connectors": the connectors' window opens (set_connectors_editing(true)). */
    fun editCutConnectors() = updateCut(snapshot = false) { if (it.canEditConnectors) it.copy(editingConnectors = true) else it }

    /** "Confirm connectors": the window closes with the connectors unselected. */
    fun confirmCutConnectors() = updateCut(snapshot = false) { it.withSelection(emptySet()).copy(editingConnectors = false) }

    /** "Cancel" of the connectors' window: the connectors are gone (reset_connectors()) and the window closes. */
    fun cancelCutConnectors() = updateCut(snapshot = false) { it.copy(connectors = emptyList()).withSelection(emptySet()).copy(editingConnectors = false) }

    /** "Remove connectors" */
    fun removeCutConnectors() = updateCut(snapshot = true) { it.copy(connectors = emptyList()).withSelection(emptySet()) }

    /** "Flip cut plane" of the connectors' window. */
    fun flipCutPlaneForConnectors() = flipCutPlane()

    /**
     * The 3D view's connector events while the connectors' window is open
     * (gizmo_event()): a touch on the section adds one ("Add connector"), a
     * touch on a connector selects it alone, a long press adds it to the
     * selection or takes it out, a drag moves it ("Move connector"), and a
     * touch elsewhere unselects them all.
     */
    fun cutConnectorEvent(event: CutConnectorEvent) {
        val mode = view.value.cut?.takeIf { it.editingConnectors } ?: return
        when (event) {
            is CutConnectorEvent.Add -> updateCut(snapshot = true) { cut ->
                val settings = cut.withSelection(emptySet()).connectorSettings
                val added = CutConnector(
                    position = event.position,
                    radius = (settings.size ?: 2.5) * 0.5,
                    height = settings.depth ?: 3.0,
                    radiusTolerance = (settings.sizeTolerance ?: 0.0) * 0.5,
                    heightTolerance = settings.depthTolerance ?: 0.1,
                    zAngle = settings.angle ?: 0.0,
                    type = settings.type ?: CutConnectorType.PLUG,
                    style = settings.style ?: CutConnectorStyle.PRISM,
                    shape = settings.shape ?: CutConnectorShape.CIRCLE,
                )
                cut.copy(connectors = cut.connectors + added).withSelection(setOf(cut.connectors.size))
            }
            is CutConnectorEvent.Select -> updateCut(snapshot = false) { cut ->
                val selected = when {
                    !event.toggle -> setOf(event.index)
                    event.index in cut.selectedConnectors -> cut.selectedConnectors - event.index
                    else -> cut.selectedConnectors + event.index
                }
                cut.withSelection(selected)
            }
            is CutConnectorEvent.Move -> updateCut(snapshot = event.finished) { cut ->
                if (event.index !in cut.connectors.indices) return@updateCut cut
                val moved = cut.connectors.toMutableList().also { it[event.index] = it[event.index].copy(position = event.position) }
                cut.copy(connectors = moved).let { if (event.finished) it.withSelection(setOf(event.index)) else it }
            }
            CutConnectorEvent.Deselect -> if (mode.selectedConnectors.isNotEmpty()) updateCut(snapshot = false) { it.withSelection(emptySet()) }
        }
    }

    /** Ctrl+A of the desktop: "Select all connectors". */
    fun selectAllCutConnectors() = updateCut(snapshot = false) { it.withSelection(it.connectors.indices.toSet()) }

    /** Delete of the desktop, and its right click: the selected connectors go ("Delete connector"). */
    fun deleteCutConnectors() {
        val mode = view.value.cut ?: return
        if (mode.connectors.isEmpty()) return
        updateCut(snapshot = true) { cut ->
            cut.copy(connectors = cut.connectors.filterIndexed { index, _ -> index !in cut.selectedConnectors }).withSelection(emptySet())
        }
    }

    /**
     * The connectors' window sets what it adds, and the selected connectors
     * with it (apply_selected_connectors()): a dowel is a prism, a snap round.
     */
    fun setCutConnectorSettings(change: (CutConnectorSettings) -> CutConnectorSettings) = updateCut(snapshot = false) { cut ->
        val before = cut.connectorSettings
        var settings = change(before)
        if (settings.type != before.type && settings.type == CutConnectorType.DOWEL) settings = settings.copy(style = CutConnectorStyle.PRISM)
        if (settings.type != before.type && settings.type == CutConnectorType.SNAP) settings = settings.copy(shape = CutConnectorShape.CIRCLE)
        val connectors = cut.connectors.mapIndexed { index, connector ->
            if (index !in cut.selectedConnectors) return@mapIndexed connector
            connector.copy(
                type = settings.type ?: connector.type,
                style = settings.style ?: connector.style,
                shape = settings.shape ?: connector.shape,
                height = settings.depth?.takeIf { it > 0.0 } ?: connector.height,
                heightTolerance = settings.depthTolerance?.takeIf { it >= 0.0 } ?: connector.heightTolerance,
                radius = settings.size?.takeIf { it > 0.0 }?.let { it * 0.5 } ?: connector.radius,
                radiusTolerance = settings.sizeTolerance?.takeIf { it >= 0.0 }?.let { it * 0.5 } ?: connector.radiusTolerance,
                zAngle = settings.angle ?: connector.zAngle,
            )
        }
        cut.copy(connectorSettings = settings, connectors = connectors)
    }

    /** "Bulge" and "Space" of the snaps, proportions of their radius. */
    fun setCutSnap(space: Double, bulge: Double) = updateCut(snapshot = false) { it.copy(snapSpace = space, snapBulge = bulge) }

    /** A slider of the connectors' window let go, or its reset ("Edited: <label>", "Reset: <label>"). */
    fun snapshotCutConnectors() = updateCut(snapshot = true) { it }

    /** set_center_pos(center, true): the plane at [center], or null where it would leave the object behind. */
    private fun movedCut(mode: CutMode, center: Vector3): Transform3? {
        val plane = mode.plane ?: return null
        val described = mode.described
        val describedPlane = mode.describedPlane
        val boundsCenter = mode.boundsCenter
        if (described != null && describedPlane != null && boundsCenter != null &&
            !CutPlanes.mayMoveTo(plane, center, describedPlane, described.min, described.max, boundsCenter)
        ) {
            return null
        }
        return CutPlanes.withCenter(plane, center)
    }

    /**
     * A change of the cut gizmo; [snapshot] takes the gizmo's snapshot. A plane
     * moved takes the connectors along onto it (put_connectors_on_cut_plane()),
     * and a plane or connectors changed are described anew.
     */
    private fun updateCut(snapshot: Boolean, change: (CutMode) -> CutMode) {
        val before = view.value.cut ?: return
        view.update { state ->
            state.cut?.let { mode ->
                change(mode)
                    .let { changed -> if (changed.plane != mode.plane) changed.copy(connectors = onPlane(changed.connectors, changed.plane)) else changed }
                    // A plane moved or turned takes the pieces away (reset_cut_by_contours()); a flip turns them over itself.
                    .let { changed -> if (changed.plane != mode.plane && changed.parts === mode.parts) changed.copy(parts = null, partsPlane = null) else changed }
                    .let { if (snapshot) it.snapshotted() else it }
            }?.let { state.copy(cut = it) } ?: state
        }
        describeCut(before)
    }

    /** Undo and Redo while the cut gizmo is open: its snapshot at [index]. */
    private fun restoreCut(index: Int) {
        val before = view.value.cut ?: return
        view.update { state -> state.cut?.let { state.copy(cut = it.restored(index)) } ?: state }
        describeCut(before)
    }

    /** The engine describes the plane anew once it, the connectors or the snaps changed from [before]. */
    private fun describeCut(before: CutMode) {
        val after = view.value.cut ?: return
        if (after.plane != null &&
            (after.plane != before.plane || after.connectors != before.connectors || after.snapSpace != before.snapSpace || after.snapBulge != before.snapBulge ||
                after.kind != before.kind || after.groove != before.groove || after.shaping != before.shaping || after.parts != before.parts)
        ) {
            cutPlanes.trySend(after)
        }
    }

    /** put_connectors_on_cut_plane(): every connector moved along the normal of [plane] onto it. */
    private fun onPlane(connectors: List<CutConnector>, plane: Transform3?): List<CutConnector> {
        if (plane == null || connectors.isEmpty()) return connectors
        val normal = CutPlanes.normal(plane)
        val center = CutPlanes.center(plane)
        val offset = normal.x * center.x + normal.y * center.y + normal.z * center.z
        return connectors.map { connector ->
            val p = connector.position
            val distance = offset - (normal.x * p.x + normal.y * p.y + normal.z * p.z)
            connector.copy(position = Vector3(p.x + normal.x * distance, p.y + normal.y * distance, p.z + normal.z * distance))
        }
    }

    /** "Enable painted fuzzy skin for this object" of the fuzzy skin tool's warning. */
    fun enablePaintedFuzzySkin() {
        val mode = view.value.painting?.takeIf { it.kind == PaintKind.FUZZY_SKIN } ?: return
        enablePaintedFuzzySkin(mode.mesh)
    }

    /** "Highlight overhang areas", which the 3D view tints the object with. */
    fun setHighlightAngle(angle: Double) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(highlightAngle = angle)) } ?: state }
    }

    /** "On highlighted overhangs only". */
    fun setOverhangsOnly(only: Boolean) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(overhangsOnly = only)) } ?: state }
    }

    /** The seam tool's "Vertical". */
    fun setVerticalOnly(vertical: Boolean) {
        view.update { state -> state.painting?.let { state.copy(painting = it.copy(verticalOnly = vertical)) } ?: state }
    }

    /**
     * A touch of the finger; [starts] marks the first of a stroke. A touch that
     * arrives while the last one is still painted is dropped, but the start of
     * a stroke is carried to the next touch sent, so the tool keeps what its
     * Undo returns to.
     */
    fun paint(origin: Vector3, direction: Vector3, starts: Boolean = false) {
        val mode = view.value.painting ?: return
        // The gap fill paints no strokes.
        if (mode.tool == PaintTool.GAP_FILL) return
        if (starts) strokeStarts = true
        if (painting) return
        painting = true
        val first = strokeStarts
        strokeStarts = false
        viewModelScope.launch {
            try {
                paintObject.stroke(
                    PaintStroke(
                        origin = origin,
                        direction = direction,
                        state = mode.state,
                        radius = mode.radius,
                        tool = mode.tool,
                        angle = mode.fillAngle,
                        overhangAngle = if (mode.overhangsOnly) mode.highlightAngle else 0.0,
                        startsStroke = first,
                    ),
                ).also(::showStrokes)
            } finally {
                painting = false
            }
        }
    }

    /** What the painting tool carries and can undo and redo after [outcome]. */
    private fun showStrokes(outcome: PaintingOutcome) {
        val surface = (outcome as? PaintingOutcome.Success)?.surface ?: return
        view.update { state ->
            state.painting?.let {
                state.copy(painting = it.copy(painted = surface.states.isNotEmpty(), canUndo = surface.canUndo, canRedo = surface.canRedo))
            } ?: state
        }
    }

    private fun select(id: PlateInstanceId?, selected: ModelInspection?) {
        val before = plate.value.selectedInstance
        selectPlateObject(id)
        view.update { view ->
            view.copy(
                rotationStart = if (id != before) selected?.placement else view.rotationStart,
                gizmo = if (id == null) null else view.gizmo,
            )
        }
    }

    /** A gizmo's toolbar icon opens the gizmo, or closes it when it is open. */
    fun toggleGizmo(type: PlateGizmo) {
        val state = state.value
        if (!state.canManipulate) return
        openSimplify.close()
        closeCut()
        closeEmbossTools()
        editLayerHeights.enable(false)
        view.update { view ->
            view.copy(
                gizmo = if (state.gizmo == type) null else type,
                rotationStart = state.selectedCopy?.instance?.inspection?.placement,
                // Another toolbar item closes the arrange options.
                arrangeOptionsOpen = false,
            )
        }
    }

    /** The Arrange toolbar item opens its options window, or closes it; a gizmo closes. */
    fun toggleArrangeOptions() {
        if (!state.value.canArrange) return
        editLayerHeights.enable(false)
        view.update { it.copy(arrangeOptionsOpen = !it.arrangeOptionsOpen, gizmo = null) }
    }

    /**
     * The toolbar's Text (GLGizmoEmboss::on_shortcut_key()): the tool closes
     * when open; it opens on the selected text volume, or a text is added to
     * the selected object where [hit] says, or the plate gets an object of a
     * text standing at [bedPoint] when nothing is selected.
     */
    fun toggleText(hit: SurfaceHit?, bedPoint: Point2?, defaultText: String) {
        if (view.value.text != null) return closeText()
        val state = plate.value
        state.selectedEmbossVolume(EmbossKind.TEXT)?.let { return openText(it) }
        val copy = this.state.value.selectedCopy
        if (copy == null) {
            createText(EmbossPlacement.onBed(bedPoint), VolumeType.PART, defaultText)
        } else {
            createText(placementOn(copy.id, hit), VolumeType.PART, defaultText)
        }
    }

    /**
     * MenuFactory's "Text" of "Add part", "Add negative part" or "Add
     * modifier" over the copy at [copy]: the text joins it where [hit] says,
     * beside it without a hit; over empty space ([copy] null), an object of a
     * text stands at [bedPoint].
     */
    fun addText(copy: Int?, type: VolumeType, hit: SurfaceHit?, bedPoint: Point2?, defaultText: String) {
        val id = copy?.let { state.value.sceneCopies.getOrNull(it)?.id }
        createText(if (id == null) EmbossPlacement.onBed(bedPoint) else placementOn(id, hit), type, defaultText)
    }

    /** The object list's "Add part" > "Text": the canvas places it as [addText] without a position. */
    fun addRequestedText(request: EmbossRequest.Add, hit: SurfaceHit?, defaultText: String) {
        requestEmboss.done()
        if (request.kind != EmbossKind.TEXT) return
        createText(placementOn(PlateInstanceId(request.mesh, 0), hit), request.type, defaultText)
    }

    /** The menus' "Edit text" over a volume. */
    fun editText(volume: ObjectPartId) = openText(volume)

    private fun placementOn(copy: PlateInstanceId, hit: SurfaceHit?): EmbossPlacement {
        val index = plate.value.objects.indexOfFirst { it.mesh == copy.mesh }
        return EmbossPlacement(index, copy.instance, hit?.position, hit?.normal)
    }

    /**
     * GLGizmoEmboss::create_volume(): init_create() makes no text on a part of
     * a cut, and takes the stored style without the window's changes.
     */
    private fun createText(placement: EmbossPlacement, type: VolumeType, defaultText: String) {
        if (!state.value.canEditPlate) return
        if (placement.objectIndex >= 0 && plate.value.objects.getOrNull(placement.objectIndex)?.isCut == true) return
        viewModelScope.launch {
            loadFamilies()
            val style = discardStyleChanges() ?: return@launch
            // The tool leaves the text it was open on for the new one.
            closeText()
            closeOtherTools()
            val created = emboss.create(placement, type, defaultText, style) ?: return@launch
            openText(created, style)
        }
    }

    /**
     * The tool opens on the text [volume] (GLGizmoEmboss::set_volume_by_selection()):
     * its text and style as the engine reads them, the font the phone's own
     * when it has it, by its file or else its face name, or unknown.
     */
    private fun openText(volume: ObjectPartId, created: TextStyle? = null) {
        closeSvg()
        closeOtherTools()
        view.update { it.copy(text = TextMode(volume, text = "", style = created ?: TextStyle("", ""), busy = true)) }
        viewModelScope.launch {
            val described = (emboss.describe(volume) as? EmbossVolumeOutcome.Success)?.volume
            if (described == null || described.kind != EmbossKind.TEXT) {
                view.update { if (it.text?.volume == volume) it.copy(text = null) else it }
                return@launch
            }
            val families = loadFamilies()
            val faces = families.flatMap(TextFontFamily::faces)
            var style = described.style
            var unknown = false
            if (faces.none { it.path == style.fontPath && it.index == (style.collectionNumber ?: 0) }) {
                // get_installed_face_name(): another computer's font, found by its face name.
                val installed = families.firstOrNull { it.name.equals(style.faceName, ignoreCase = true) }?.faces?.firstOrNull()
                if (installed != null) {
                    style = style.copy(fontPath = installed.path, collectionNumber = installed.index.takeIf { it > 0 })
                } else {
                    unknown = true
                }
            }
            // Find style in stored styles
            val stored = ensureStyles()?.styles.orEmpty()
            val found = stored.indexOfFirst { it.name == style.name }
            if (found < 0) {
                // style was not found
                styleIndex = null
            } else if (loadStyle(found) == null) {
                // can`t load stored style
                eraseStyle(found)
                styleIndex = null
            }
            view.update { state ->
                val open = state.text?.takeIf { it.volume == volume } ?: return@update state
                state.copy(
                    text = open.copy(
                        text = described.text,
                        style = style,
                        described = described,
                        styles = styleList?.styles.orEmpty(),
                        styleIndex = styleIndex,
                        unknownFont = unknown,
                        keepUp = textKeepUp,
                        busy = false,
                    ),
                )
            }
        }
    }

    /** GLGizmoEmboss::close(): an empty text goes, its object with it when it is the object's only part. */
    fun closeText() {
        val open = view.value.text ?: return
        view.update { it.copy(text = null) }
        // on_set_state(): the styles' order and the active one go into the app configuration.
        viewModelScope.launch { storeStyles() }
        if (open.blank) {
            val copy = PlateInstanceId(open.volume.mesh, 0)
            if (open.onlyPart) deletePlateObject(copy.mesh) else removeObjectPart(open.volume)
        }
    }

    /** The window's text input: the volume is embossed anew (when the text is not blank). */
    fun setText(text: String) = editText { it.copy(text = text) }

    /** A change of the style the window edits. */
    fun setTextStyle(change: (TextStyle) -> TextStyle) = editText { it.copy(style = change(it.style)) }

    /** GLGizmoEmboss::select_facename(): the [family]'s face of normal style and weight. */
    fun setTextFont(family: TextFontFamily) {
        val open = view.value.text ?: return
        val style = open.style.withFamily(family) ?: return
        editText { it.copy(unknownFont = false, style = style) }
    }

    /** draw_italic_button() */
    fun toggleTextItalic() = editText { mode -> mode.copy(style = mode.style.toggledItalic(fontFamilies.value)) }

    /** draw_bold_button() */
    fun toggleTextBold() = editText { mode -> mode.copy(style = mode.style.toggledBold(fontFamilies.value)) }

    /**
     * The window's style list (draw_style_list()): the stored style at
     * [index] becomes the tool's, with the text's turn and distance kept,
     * which fix_transformation() would change; a style whose font does not
     * load goes from the list, as the desktop app tells.
     */
    fun selectTextStyle(index: Int) {
        if (view.value.text == null) return
        viewModelScope.launch {
            val list = styleList ?: return@launch
            val style = loadStyle(index)
            if (style == null) {
                val name = list.styles.getOrNull(index)?.name ?: return@launch
                eraseStyle(index)
                view.update { it.copy(text = it.text?.copy(styles = styleList?.styles.orEmpty(), styleIndex = styleIndex, notice = TextNotice.InvalidStyle(name))) }
                return@launch
            }
            val open = view.value.text ?: return@launch
            val changed = open.copy(unknownFont = false, style = style, styles = styleList?.styles.orEmpty(), styleIndex = styleIndex)
            // fix_transformation(): the text turns and moves to the style's angle and distance.
            val turn = (style.angle ?: 0.0) - (open.style.angle ?: 0.0)
            val move = (style.distance ?: 0.0) - (open.style.distance ?: 0.0)
            if (sameOptional(open.style.angle, style.angle) && sameOptional(open.style.distance, style.distance)) {
                editText { changed }
            } else {
                view.update { it.copy(text = changed) }
                transformEmboss(EmbossTransform(rotate = turn, move = move), reEmboss = true, angleFromVolume = false)
            }
        }
    }

    /**
     * The Rotation slider let go: the text turns about its own Z axis to
     * [degrees] clockwise (do_local_z_rotate()), and its angle is measured
     * anew (calc_angle()).
     */
    fun rotateText(degrees: Double) {
        val open = view.value.text ?: return
        // convert back to radians and CCW
        var angle = -Math.toRadians(degrees)
        // Geometry::to_range_pi_pi()
        angle = atan2(sin(angle), cos(angle))
        val turn = angle - (open.style.angle ?: 0.0)
        if (abs(turn) < ANGLE_EPSILON) return
        transformEmboss(EmbossTransform(rotate = turn), reEmboss = false, angleFromVolume = true)
    }

    /**
     * The From surface slider let go: the text moves along its own Z axis to
     * [distance] millimetres from the surface (do_local_z_move()); unset
     * moves it back onto it.
     */
    fun moveText(distance: Double?) {
        val open = view.value.text ?: return
        val move = (distance ?: 0.0) - (open.style.distance ?: 0.0)
        view.update { it.copy(text = it.text?.copy(style = open.style.copy(distance = distance))) }
        if (move == 0.0) return
        transformEmboss(EmbossTransform(move = move), reEmboss = false, angleFromVolume = false)
    }

    /** The lock beside Rotation: whether the text's up is kept as it faces the camera. */
    fun setTextKeepUp(keepUp: Boolean) {
        textKeepUp = keepUp
        view.update { it.copy(text = it.text?.copy(keepUp = keepUp)) }
    }

    /** "Set text to face camera" (face_selected_volume_to_camera()) with the camera at [eye]. */
    fun faceTextToCamera(eye: CameraEye?) {
        val camera = eye ?: return
        transformEmboss(
            EmbossTransform(cameraPosition = camera.position, cameraForward = camera.forward, perspective = camera.perspective, keepUp = textKeepUp),
            reEmboss = false,
            angleFromVolume = !textKeepUp,
        )
    }

    /** SurfaceDrag let go: the text takes [placement] in its object, and is embossed anew there (volume_transformation_changed()). */
    fun dragText(placement: Transform3) {
        val open = view.value.text ?: return
        viewModelScope.launch {
            textLock.withLock {
                view.update { it.copy(text = it.text?.copy(busy = true)) }
                val moved = emboss.update(open.volume, open.text, open.style, placement)
                finishTransform(open.volume, moved, angleFromVolume = !textKeepUp)
            }
        }
    }

    /**
     * The text turned and moved as [transform] says, embossed anew with the
     * window's text and style when [reEmboss] is set; its angle measured
     * anew from the volume when [angleFromVolume] is set.
     */
    private fun transformEmboss(transform: EmbossTransform, reEmboss: Boolean, angleFromVolume: Boolean) {
        val open = view.value.text ?: return
        val instance = plate.value.selectedInstances.firstOrNull { it.mesh == open.volume.mesh }?.instance ?: 0
        viewModelScope.launch {
            textLock.withLock {
                view.update { it.copy(text = it.text?.copy(busy = true)) }
                val now = view.value.text ?: return@withLock
                val moved = emboss.transform(open.volume, instance, transform, now.text, now.style, reEmboss)
                finishTransform(open.volume, moved, angleFromVolume)
            }
        }
    }

    /** The volume the text became, with its angle as the engine measures it when [angleFromVolume] is set. */
    private suspend fun finishTransform(volume: ObjectPartId, moved: ObjectPartId?, angleFromVolume: Boolean) {
        val described = moved?.takeIf { angleFromVolume }?.let { (emboss.describe(it) as? EmbossVolumeOutcome.Success)?.volume }
        view.update { state ->
            val now = state.text?.takeIf { it.volume == volume } ?: return@update state.copy(text = state.text?.copy(busy = false))
            state.copy(
                text = now.copy(
                    volume = moved ?: now.volume,
                    style = if (described != null) now.style.copy(angle = described.style.angle) else now.style,
                    described = described ?: now.described,
                    busy = false,
                ),
            )
        }
    }

    /** GLGizmoSVG::m_keep_up and m_keep_ratio, kept from one opening to the next. */
    private var svgKeepUp = true
    private var svgKeepRatio = true

    /** What the SVG document the picker gives next is for: a new SVG placed so, or another file of the open one. */
    private sealed interface SvgPick {
        data class Create(val placement: EmbossPlacement, val type: VolumeType) : SvgPick

        data class Change(val volume: ObjectPartId) : SvgPick
    }

    private var svgPick: SvgPick? = null

    /**
     * MenuFactory's "SVG" of "Add Primitive", "Add part", "Add negative part"
     * or "Add modifier": the SVG goes over the copy at [copy] where [hit] says,
     * beside it without a hit, or as an object at [bedPoint], once the picker
     * gives its file (choose_svg_file()).
     */
    fun chooseSvg(copy: Int?, type: VolumeType, hit: SurfaceHit?, bedPoint: Point2?) {
        val id = copy?.let { state.value.sceneCopies.getOrNull(it)?.id }
        svgPick = SvgPick.Create(if (id == null) EmbossPlacement.onBed(bedPoint) else placementOn(id, hit), type)
    }

    /** The object list's "Add part" > "SVG", which the canvas places as without a position. */
    fun chooseRequestedSvg(request: EmbossRequest.Add, hit: SurfaceHit?) {
        requestEmboss.done()
        if (request.kind != EmbossKind.SVG) return
        svgPick = SvgPick.Create(placementOn(PlateInstanceId(request.mesh, 0), hit), request.type)
    }

    /** "Change file" of the SVG window: the picker's file replaces the open SVG's. */
    fun chooseSvgFile() {
        val open = view.value.svg ?: return
        svgPick = SvgPick.Change(open.volume)
    }

    /** The SVG document the picker gave, or none when it was cancelled. */
    fun svgPicked(document: String?) {
        val pick = svgPick ?: return
        svgPick = null
        if (document == null) return
        viewModelScope.launch {
            val file = emboss.importSvg(ExternalDocumentReference(document)) ?: return@launch
            when (pick) {
                is SvgPick.Create -> {
                    if (!state.value.canEditPlate) return@launch
                    closeText()
                    closeSvg()
                    closeOtherTools()
                    val created = emboss.createSvg(pick.placement, pick.type, file) ?: return@launch
                    openSvg(created)
                }
                is SvgPick.Change -> svgOperation { open -> emboss.updateSvg(open.volume, open.depth, open.useSurface, file.path) }
            }
        }
    }

    /** "Edit SVG" of the menus. */
    fun editSvg(volume: ObjectPartId) = openSvg(volume)

    /** GLGizmoSVG::set_volume_by_selection(): the window opens on the SVG [volume]. */
    private fun openSvg(volume: ObjectPartId) {
        closeText()
        closeOtherTools()
        view.update { it.copy(svg = SvgMode(volume, keepUp = svgKeepUp, keepRatio = svgKeepRatio)) }
        viewModelScope.launch { refreshSvg(volume, volume) }
    }

    /** The window closes (GLGizmoSVG::close()). */
    fun closeSvg() {
        if (view.value.svg == null) return
        view.update { it.copy(svg = null) }
    }

    /** The text and SVG tools close, as another gizmo opens; the measuring and brim ears tools too. */
    private fun closeEmbossTools() {
        closeText()
        closeSvg()
        closeMeasure()
        closeBrimEars()
    }

    /** What the engine tells of the SVG [volume] and its picture, for the window open on [open]. */
    private suspend fun refreshSvg(open: ObjectPartId, volume: ObjectPartId) {
        val described = (emboss.describe(volume) as? EmbossVolumeOutcome.Success)?.volume
        if (described == null || described.kind != EmbossKind.SVG) {
            view.update { if (it.svg?.volume == open) it.copy(svg = null) else it }
            return
        }
        val preview = (emboss.previewSvg(volume, SVG_PREVIEW_SIZE) as? SvgPreviewOutcome.Success)?.preview
        view.update { state ->
            val now = state.svg?.takeIf { it.volume == open } ?: return@update state
            state.copy(svg = now.copy(volume = volume, described = described, preview = preview, previewVersion = now.previewVersion + 1, busy = false))
        }
    }

    /** An edit of the open SVG by the engine; the window tells what the SVG became. */
    private fun svgOperation(edit: suspend (SvgMode) -> ObjectPartId?) {
        val open = view.value.svg ?: return
        viewModelScope.launch {
            textLock.withLock {
                view.update { it.copy(svg = it.svg?.copy(busy = true)) }
                val edited = edit(open) ?: open.volume
                refreshSvg(open.volume, edited)
            }
        }
    }

    /** The SVG turned, moved, sized or mirrored as [transform] says. */
    private fun transformSvg(transform: EmbossTransform) = svgOperation { open ->
        val instance = plate.value.selectedInstances.firstOrNull { it.mesh == open.volume.mesh }?.instance ?: 0
        emboss.transform(open.volume, instance, transform, "", TextStyle("", ""), reEmboss = false)
    }

    /** draw_depth() */
    fun setSvgDepth(depth: Double) {
        val open = view.value.svg ?: return
        val value = depth.coerceIn(SVG_DEPTH_MIN, SVG_DEPTH_MAX)
        if (abs(value - open.depth) < 1e-4) return
        svgOperation { emboss.updateSvg(it.volume, value, it.useSurface) }
    }

    /** draw_use_surface() */
    fun setSvgUseSurface(use: Boolean) = svgOperation { emboss.updateSvg(it.volume, it.depth, use) }

    /**
     * draw_size(): the SVG [width] or [height] millimetres, the other with it
     * while the ratio is locked; a change too small or too big is no change.
     */
    fun setSvgSize(width: Double?, height: Double?) {
        val open = view.value.svg ?: return
        val described = open.described ?: return
        val scale = when {
            width != null && described.width > 0.0 -> (width / described.width).let { ratio ->
                if (open.keepRatio) Vector3(ratio, ratio, 1.0) else Vector3(ratio, 1.0, 1.0)
            }
            height != null && described.height > 0.0 -> Vector3(1.0, height / described.height, 1.0)
            else -> return
        }
        if (!isValidScaleRatio(if (width != null) scale.x else scale.y)) return
        transformSvg(EmbossTransform(scale = scale))
    }

    /** The size's reset: the SVG unscaled again. */
    fun resetSvgSize() {
        val described = view.value.svg?.described ?: return
        transformSvg(EmbossTransform(scale = Vector3(1.0 / described.scaleWidth, 1.0 / described.scaleHeight, 1.0)))
    }

    /** The lock of the size: the ratio of width and height kept. */
    fun setSvgKeepRatio(keep: Boolean) {
        svgKeepRatio = keep
        view.update { it.copy(svg = it.svg?.copy(keepRatio = keep)) }
    }

    /** draw_distance(): the SVG [distance] millimetres from the surface; none moves it back onto it. */
    fun moveSvg(distance: Double?) {
        val open = view.value.svg ?: return
        val move = (distance ?: 0.0) - (open.distance ?: 0.0)
        if (move == 0.0) return
        transformSvg(EmbossTransform(move = move))
    }

    /** draw_rotation(): the SVG turned to [degrees] clockwise. */
    fun rotateSvg(degrees: Double) {
        val open = view.value.svg ?: return
        var angle = -Math.toRadians(degrees)
        angle = atan2(sin(angle), cos(angle))
        val turn = angle - (open.angle ?: 0.0)
        if (abs(turn) < ANGLE_EPSILON) return
        transformSvg(EmbossTransform(rotate = turn))
    }

    /** The rotation's reset. */
    fun resetSvgRotation() {
        val angle = view.value.svg?.angle ?: return
        transformSvg(EmbossTransform(rotate = -angle))
    }

    /** The lock beside Rotation: the SVG's up kept as it is dragged and faced to the camera. */
    fun setSvgKeepUp(keep: Boolean) {
        svgKeepUp = keep
        view.update { it.copy(svg = it.svg?.copy(keepUp = keep)) }
    }

    /** draw_mirroring() */
    fun mirrorSvg(axis: Axis) = transformSvg(EmbossTransform(mirror = axis))

    /** draw_face_the_camera() with the camera at [eye]. */
    fun faceSvgToCamera(eye: CameraEye?) {
        val camera = eye ?: return
        transformSvg(EmbossTransform(cameraPosition = camera.position, cameraForward = camera.forward, perspective = camera.perspective, keepUp = svgKeepUp))
    }

    /** SurfaceDrag let go: the SVG takes [placement] in its object, embossed anew on the surface. */
    fun dragSvg(placement: Transform3) = svgOperation { emboss.updateSvg(it.volume, it.depth, it.useSurface, placement = placement) }

    /** draw_model_type(): Join, Cut or Modifier; the last solid part keeps its type. */
    fun setSvgType(type: VolumeType) {
        val open = view.value.svg ?: return
        if (open.onlyPart || open.described?.type == type) return
        svgOperation { emboss.changeSvgType(it.volume, type, it.depth, it.useSurface) }
    }

    /** The reload button: the SVG from its file anew, or its path forgotten when the file is gone. */
    fun reloadSvg() {
        val path = view.value.svg?.preview?.svgPath?.takeIf(String::isNotEmpty) ?: return
        if (File(path).exists()) {
            svgOperation { emboss.updateSvg(it.volume, it.depth, it.useSurface, ModelPath(path)) }
        } else {
            forgetSvgPath()
        }
    }

    /** "Forget the file path" */
    fun forgetSvgPath() = svgOperation { emboss.editSvgFile(it.volume, SvgFileEdit.FORGET_PATH) }

    /** "Bake": the SVG becomes a part of no SVG, and the window closes. */
    fun bakeSvg() {
        val open = view.value.svg ?: return
        viewModelScope.launch {
            textLock.withLock {
                view.update { it.copy(svg = it.svg?.copy(busy = true)) }
                emboss.editSvgFile(open.volume, SvgFileEdit.BAKE)
                closeSvg()
            }
        }
    }

    /** The name "Save as" offers: the file's name, ".svg" after it. */
    fun svgSaveName(): String? = view.value.svg?.described?.svgName?.let { "$it.svg" }

    /** "Save as" into the [document] the user created. */
    fun saveSvgAs(document: String) {
        val name = svgSaveName() ?: return
        svgOperation { emboss.saveSvgAs(it.volume, name, ExternalDocumentReference(document)) }
    }

    /** "Collection" of a font file of several faces: the face at [index]. */
    fun setTextCollection(index: Int) = editText { it.copy(style = it.style.copy(collectionNumber = index.takeIf { at -> at > 0 })) }

    /** Reset (reset_to_default_style()): the first stored style. */
    fun resetTextStyle() = selectTextStyle(0)

    /**
     * draw_style_save_button() and draw_style_add_button() of a temporary
     * style (store_styles_to_app_config()): the stored style takes the
     * window's, or it joins the list under a name of its own; then the
     * styles are kept.
     */
    fun saveTextStyle() {
        val open = view.value.text ?: return
        viewModelScope.launch {
            val list = styleList ?: return@launch
            val index = styleIndex
            var current = open.style
            val styles = list.styles.toMutableList()
            if (index != null && index in styles.indices) {
                // update stored item
                styles[index] = current
            } else {
                // add new into stored list
                current = current.copy(name = makeUniqueName(styles, current.name))
                styleIndex = styles.size
                styles += current
            }
            styleList = list.copy(styles = styles)
            storeStyles()
            view.update { state -> state.copy(text = state.text?.copy(style = current, styles = styles, styleIndex = styleIndex)) }
        }
    }

    /**
     * draw_style_save_as_popup(): the window's style joins the list as [name]
     * (add_style()) and the styles are kept; the text takes the name, which
     * the popup edits in the volume's text configuration.
     */
    fun addTextStyle(name: String) {
        val open = view.value.text ?: return
        viewModelScope.launch {
            val list = styleList ?: return@launch
            val current = open.style.copy(name = makeUniqueName(list.styles, name))
            styleIndex = list.styles.size
            styleList = list.copy(styles = list.styles + current)
            storeStyles()
            editText { it.copy(style = current, styles = styleList?.styles.orEmpty(), styleIndex = styleIndex) }
        }
    }

    /**
     * draw_style_rename_popup(): the stored style and every text of the
     * plate in it take [name], and the styles are kept.
     */
    fun renameTextStyle(name: String) {
        if (view.value.text == null) return
        val index = styleIndex ?: return
        val old = styleList?.styles?.getOrNull(index)?.name ?: return
        viewModelScope.launch {
            textLock.withLock {
                view.update { it.copy(text = it.text?.copy(busy = true)) }
                val renamed = textStyles.renameInVolumes(old, name)
                styleList = styleList?.let { list ->
                    list.copy(styles = list.styles.mapIndexed { at, style -> if (at == index) style.copy(name = name) else style })
                }
                storeStyles()
                view.update { state ->
                    val now = state.text ?: return@update state
                    val volume = renamed[now.volume.mesh]?.let { mesh -> ObjectPartId(mesh, now.volume.index) } ?: now.volume
                    state.copy(text = now.copy(volume = volume, style = now.style.copy(name = name), styles = styleList?.styles.orEmpty(), busy = false))
                }
            }
        }
    }

    /**
     * draw_delete_style_button(): the style beside the tool's must load, or it
     * goes and the next one is tried; then the question, or the message that
     * the last style can't go.
     */
    fun askDeleteTextStyle() {
        if (view.value.text == null) return
        viewModelScope.launch {
            var changed = false
            var notice: TextNotice? = null
            var asked: String? = null
            while (true) {
                val list = styleList ?: break
                // NOTE: can't use previous loaded activ index -> erase could change index
                val active = styleIndex ?: break
                val next = if (active > 0) active - 1 else active + 1
                if (next >= list.styles.size) {
                    notice = TextNotice.LastStyle
                    break
                }
                // clean unactivable styles
                if (!textStyles.canLoad(list.styles[next])) {
                    eraseStyle(next)
                    changed = true
                    continue
                }
                asked = list.styles[active].name
                break
            }
            if (changed) storeStyles()
            view.update { state ->
                state.copy(text = state.text?.copy(styles = styleList?.styles.orEmpty(), styleIndex = styleIndex, notice = notice, deleting = asked))
            }
        }
    }

    /** The answer to draw_delete_style_button()'s question: Yes erases the style, and the text takes the one beside it. */
    fun deleteTextStyle(confirmed: Boolean) {
        view.update { it.copy(text = it.text?.copy(deleting = null)) }
        if (!confirmed) return
        viewModelScope.launch {
            val active = styleIndex ?: return@launch
            val next = if (active > 0) active - 1 else active + 1
            val style = loadStyle(next) ?: return@launch
            // delete style
            eraseStyle(active)
            storeStyles()
            editText { it.copy(unknownFont = false, style = style, styles = styleList?.styles.orEmpty(), styleIndex = styleIndex) }
        }
    }

    /** The window's message box closes. */
    fun dismissTextNotice() = view.update { it.copy(text = it.text?.copy(notice = null)) }

    /**
     * StyleManager::swap(), as dragging a style of the list over its
     * neighbour does; the order is kept when the tool closes.
     */
    fun swapTextStyles(first: Int, second: Int) {
        val list = styleList ?: return
        if (first !in list.styles.indices || second !in list.styles.indices) return
        val styles = list.styles.toMutableList()
        styles[first] = list.styles[second]
        styles[second] = list.styles[first]
        styleList = list.copy(styles = styles)
        // fix selected index
        styleIndex = when (styleIndex) {
            first -> second
            second -> first
            else -> styleIndex
        }
        view.update { it.copy(text = it.text?.copy(styles = styles, styleIndex = styleIndex)) }
    }

    /** "Advanced" opens or closes. */
    fun setTextAdvanced(open: Boolean) = view.update { it.copy(text = it.text?.copy(advanced = open)) }

    /** draw_model_type(): Join, Cut or Modifier; the last solid part keeps its type. */
    fun setTextType(type: VolumeType) {
        val open = view.value.text ?: return
        if (open.onlyPart || open.described?.type == type) return
        viewModelScope.launch {
            view.update { it.copy(text = it.text?.copy(busy = true)) }
            val changed = emboss.changeType(open.volume, type, open.text, open.style)
            view.update { state ->
                val now = state.text ?: return@update state
                state.copy(
                    text = now.copy(
                        volume = changed ?: now.volume,
                        described = now.described?.copy(type = if (changed != null) type else now.described.type),
                        busy = false,
                    ),
                )
            }
        }
    }

    private fun editText(change: (TextMode) -> TextMode) {
        val open = view.value.text ?: return
        val changed = change(open)
        view.update { it.copy(text = changed) }
        textEdits.trySend(changed)
    }

    private suspend fun loadFamilies(): List<TextFontFamily> =
        fontFamilies.value.ifEmpty { textFonts.families().also { fontFamilies.value = it } }

    /** StyleManager::init(), once: the styles, and the active one the tool's. */
    private suspend fun ensureStyles(): TextStyleList? {
        styleList?.let { return it }
        loadFamilies()
        val list = textStyles.init(styleNames) ?: return null
        styleList = list
        styleIndex = list.lastIndex
        return list
    }

    /** StyleManager::load_style(index): the stored style, the tool's and the last loaded; null when its font does not load. */
    private suspend fun loadStyle(index: Int): TextStyle? {
        val list = styleList ?: return null
        val style = list.styles.getOrNull(index) ?: return null
        if (!textStyles.canLoad(style)) return null
        styleIndex = index
        styleList = list.copy(lastIndex = index)
        return style
    }

    /**
     * StyleManager::discard_style_changes(): the stored style the tool's
     * came from, or else the last loaded; when it does not load, the first
     * style that does (load_valid_style()).
     */
    private suspend fun discardStyleChanges(): TextStyle? {
        val list = ensureStyles() ?: return null
        val index = styleIndex
        val loaded = if (index != null) loadStyle(index) else loadStyle(list.lastIndex)
        if (loaded != null) return loaded
        // try to save situation by load some font
        val valid = textStyles.loadValidStyle(list.styles, styleNames) ?: return null
        styleList = valid
        styleIndex = valid.lastIndex
        return valid.styles[valid.lastIndex]
    }

    /** StyleManager::erase(): the style leaves the list; the tool's index follows, or goes with it. */
    private fun eraseStyle(index: Int) {
        val list = styleList ?: return
        if (index !in list.styles.indices) return
        // fix selected index
        styleIndex = styleIndex?.let { at ->
            when {
                index < at -> at - 1
                index == at -> null
                else -> at
            }
        }
        styleList = list.copy(styles = list.styles.filterIndexed { at, _ -> at != index })
    }

    /** store_styles_to_app_config(false): the styles and the active one's index, the tool's or else the last loaded. */
    private suspend fun storeStyles() {
        val list = styleList ?: return
        textStyles.store(list.styles, styleIndex ?: list.lastIndex)
    }

    /** The text tool and the other tools of the canvas close each other, as GLGizmosManager opens one gizmo. */
    private fun closeOtherTools() {
        closeCut()
        closePainting()
        closeMeasure()
        closeBrimEars()
        openSimplify.close()
        editLayerHeights.enable(false)
        view.update { it.copy(gizmo = null, arrangeOptionsOpen = false) }
    }

    /**
     * The toolbar's Measure (GLGizmoMeasure): the tool opens on the selected
     * volumes, the other tools of the canvas closing first, or closes.
     */
    fun toggleMeasure() {
        if (view.value.measure != null) return closeMeasure()
        if (!state.value.canMeasure) return
        closeOtherTools()
        closeEmbossTools()
        view.update { it.copy(measure = MeasureMode()) }
    }

    /** "Done" (reset_all_gizmos()): the tool closes, and the engine lets the volumes go. */
    fun closeMeasure() {
        if (view.value.measure == null) return
        view.update { it.copy(measure = null) }
    }

    /** What a finger does on the 3D view while the tool is open. */
    fun measureTouch(touch: MeasureTouch) {
        if (view.value.measure != null) measureCommands.trySend(MeasureCommand.Touch(touch))
    }

    /** The window's reset buttons (reset_feature1(), reset_feature2()), and Delete's "Restart selection" (reset_all_feature()). */
    fun resetMeasure(reset: MeasureReset) {
        if (view.value.measure != null) measureCommands.trySend(MeasureCommand.Reset(reset))
    }

    /**
     * Esc (gizmo_event(SLAGizmoEventType::Escape)): the second selection
     * goes, or else the first; with none, the tool closes.
     */
    fun escapeMeasure() {
        val mode = view.value.measure ?: return
        when {
            mode.measurement.first == null -> closeMeasure()
            mode.measurement.second != null -> resetMeasure(MeasureReset.SECOND)
            else -> resetMeasure(MeasureReset.FIRST)
        }
    }

    /** The distance label's "Edit to scale": the box asks for the distance it read, [distance] millimetres. */
    fun editMeasureDistance(distance: Double) {
        view.update { state -> state.measure?.let { state.copy(measure = it.copy(editingDistance = distance)) } ?: state }
    }

    /** The box's Cancel. */
    fun cancelMeasureScale() {
        view.update { state -> state.measure?.let { state.copy(measure = it.copy(editingDistance = null)) } ?: state }
    }

    /**
     * "Scale all" (perform_scale()): the selection scaled so the distance
     * reads [value] where it read [current], both in the label's units; the
     * same value, or none above zero, scales nothing.
     */
    fun scaleMeasure(value: Double, current: Double) {
        cancelMeasureScale()
        if (value == current || value <= 0.0 || current <= 0.0) return
        if (view.value.measure != null) measureCommands.trySend(MeasureCommand.Scale(value / current))
    }

    /** Shift held or let go: a finger selects points on the features (EMode::PointSelection), or the features. */
    fun setMeasurePointSelection(on: Boolean) {
        view.update { state -> state.measure?.let { state.copy(measure = it.copy(pointSelection = on)) } ?: state }
    }

    /** The selections and what they measure; nothing is under the finger once it let go. */
    private fun showMeasurement(measurement: Measurement) {
        view.update { state -> state.measure?.let { state.copy(measure = it.copy(measurement = measurement, hover = null)) } ?: state }
    }

    /**
     * What the finger is on. A plane the engine told before comes without its
     * triangles, which the tool still has from the hover, the selection or
     * the measurement that told it.
     */
    private fun showMeasureHover(hover: MeasureHover) {
        view.update { state ->
            val mode = state.measure ?: return@update state
            val feature = hover.feature
            val shown = if (hover.unchanged && feature != null && feature.planeMesh == null) {
                val known = listOfNotNull(
                    mode.hover?.feature,
                    mode.measurement.hovered,
                    mode.measurement.first?.feature,
                    mode.measurement.first?.source,
                    mode.measurement.second?.feature,
                    mode.measurement.second?.source,
                ).firstOrNull { it.planeMesh != null && it.sameAs(feature) }
                hover.copy(feature = feature.copy(planeMesh = known?.planeMesh))
            } else {
                hover
            }
            state.copy(measure = mode.copy(hover = shown))
        }
    }

    /**
     * The toolbar's "Brim Ears" (GLGizmoBrimEars): the tool opens on the
     * selected copy, the other tools of the canvas closing first, or closes.
     */
    fun toggleBrimEars() {
        if (view.value.brimEars != null) return closeBrimEars()
        val copy = brimEarsTool.copyOf(plate.value)?.takeIf { state.value.canEditBrimEars } ?: return
        closeOtherTools()
        closeEmbossTools()
        view.update { it.copy(brimEars = BrimEarsMode(copy)) }
    }

    /** "Done" (reset_all_gizmos()): the tool closes; the object keeps its ears. */
    fun closeBrimEars() {
        if (view.value.brimEars == null) return
        view.update { it.copy(brimEars = null) }
    }

    /** What a finger does on the 3D view while the tool is open. */
    fun brimEarsTouch(touch: BrimEarsTouch) {
        if (view.value.brimEars != null) brimEarsTouches.trySend(touch)
    }

    /**
     * "Head diameter" let go (apply_radius_change()): new ears take it, and
     * the selected ears too, after the snapshot "Change point head diameter".
     */
    fun setBrimEarDiameter(diameter: Double) {
        val mode = view.value.brimEars ?: return
        view.update { state -> state.brimEars?.let { state.copy(brimEars = it.copy(headDiameter = diameter)) } ?: state }
        if (mode.selected.isEmpty()) return
        val points = brimEarsOf(plate.value, mode) ?: return
        brimEarsTool.commit(mode.copy.mesh, points.mapIndexed { index, point -> if (index in mode.selected) point.copy(radius = diameter / 2) else point })
    }

    fun setBrimEarMaxAngle(angle: Double) {
        view.update { state -> state.brimEars?.let { state.copy(brimEars = it.copy(maxAngle = angle)) } ?: state }
    }

    fun setBrimEarDetectionRadius(radius: Double) {
        view.update { state -> state.brimEars?.let { state.copy(brimEars = it.copy(detectionRadius = radius)) } ?: state }
    }

    /** "Auto-generate" (auto_generate()): ears along the first layer's corners, after the snapshot "Auto generate brim ear". */
    fun generateBrimEars() {
        val mode = view.value.brimEars ?: return
        val diameter = mode.headDiameter ?: return
        val points = brimEarsOf(plate.value, mode) ?: return
        viewModelScope.launch {
            brimEarsTool.commit(mode.copy.mesh, brimEarsTool.generate(points, mode.maxAngle, mode.detectionRadius, diameter))
        }
    }

    /** "Remove" > "Selected" (delete_selected_points()), after the snapshot "Delete brim ear". */
    fun removeSelectedBrimEars() {
        val mode = view.value.brimEars ?: return
        val points = brimEarsOf(plate.value, mode) ?: return
        brimEarsTool.commit(mode.copy.mesh, points.filterIndexed { index, _ -> index !in mode.selected })
        view.update { state -> state.brimEars?.let { state.copy(brimEars = it.copy(selected = emptySet())) } ?: state }
    }

    /** "Remove" > "All": every ear selected and removed. */
    fun removeAllBrimEars() {
        val mode = view.value.brimEars ?: return
        brimEarsTool.commit(mode.copy.mesh, emptyList())
        view.update { state -> state.brimEars?.let { state.copy(brimEars = it.copy(selected = emptySet())) } ?: state }
    }

    /** The warning's link: the object's brim becomes "painted". */
    fun setPaintedBrim() {
        view.value.brimEars?.let { enablePaintedBrim(it.copy.mesh) }
    }

    /** The ears the tool works on: the dragged ones, or the object's. */
    private fun brimEarsOf(state: PlateState, mode: BrimEarsMode): List<BrimPoint>? =
        mode.draft ?: state.objects.firstOrNull { it.mesh == mode.copy.mesh }?.brimPoints

    private fun updateBrimEars(change: (BrimEarsMode) -> BrimEarsMode) {
        view.update { state -> state.brimEars?.let { state.copy(brimEars = change(it)) } ?: state }
    }

    /** gizmo_event() and the grabbers' dragging, for a finger on the 3D view. */
    private suspend fun handleBrimEarsTouch(touch: BrimEarsTouch) {
        val mode = view.value.brimEars ?: return
        val points = brimEarsOf(plate.value, mode) ?: return
        when (touch) {
            // Moving: the ear the mouse would place, on the copy under it.
            is BrimEarsTouch.Explore -> brimEarsTool.hit(touch.origin, touch.direction).let { hit -> updateBrimEars { it.copy(hover = hit?.position) } }
            BrimEarsTouch.Leave -> updateBrimEars { it.copy(hover = null) }
            is BrimEarsTouch.Place -> {
                updateBrimEars { it.copy(hover = null) }
                // If there is some selection, don't add new point and deselect everything instead.
                if (mode.selected.isNotEmpty()) return updateBrimEars { it.copy(selected = emptySet()) }
                val diameter = mode.headDiameter ?: return
                val hit = brimEarsTool.hit(touch.origin, touch.direction) ?: return
                val ear = BrimPoint(hit.ear, diameter / 2)
                // add_point_to_cache(): an ear there already is is not added again.
                if (ear !in points) brimEarsTool.commit(mode.copy.mesh, points + ear)
            }
            is BrimEarsTouch.Select -> points.getOrNull(touch.index)?.let { ear ->
                updateBrimEars { it.copy(selected = setOf(touch.index), headDiameter = ear.radius * 2, held = null) }
            }
            is BrimEarsTouch.Drag -> {
                val ear = points.getOrNull(touch.index) ?: return
                // on_start_dragging() selects the ear alone; on_dragging() moves it in X and Y to the copy under the finger.
                val hit = brimEarsTool.hit(touch.origin, touch.direction)
                val moved = hit?.let { ear.copy(position = Vector3(it.position.x, it.position.y, ear.position.z)) } ?: ear
                updateBrimEars {
                    it.copy(
                        draft = points.mapIndexed { index, point -> if (index == touch.index) moved else point },
                        selected = setOf(touch.index),
                        headDiameter = ear.radius * 2,
                        held = touch.index,
                    )
                }
            }
            is BrimEarsTouch.Dropped -> {
                updateBrimEars { it.copy(draft = null, held = null) }
                // on_stop_dragging(): the snapshot "Move support point" when the ear moved.
                mode.draft?.let { draft -> brimEarsTool.commit(mode.copy.mesh, draft) }
            }
            is BrimEarsTouch.Delete -> {
                if (touch.index !in points.indices) return
                brimEarsTool.commit(mode.copy.mesh, points.filterIndexed { index, _ -> index != touch.index })
                updateBrimEars { it.copy(selected = emptySet(), held = null) }
            }
        }
    }

    /** The toolbar's "Variable layer height": the bar opens on the selected object, or closes. */
    fun toggleLayerEditing() {
        if (plate.value.layerEditing) return editLayerHeights.enable(false)
        if (!state.value.canEditLayers) return
        editLayerHeights.enable(true)
    }

    /** The window's Done: the bar closes. */
    fun closeLayerEditing() = editLayerHeights.enable(false)

    /** The object list's variable layer height mark: the bar opens on that object. */
    fun editLayersOf(mesh: ScenePath) = editLayerHeights.enableFor(mesh)

    /**
     * A finger on the bar at [z] (GLCanvas3D::_perform_layer_editing_action()):
     * the band around it changes as the window's action says, again every
     * 100 ms while the finger stays (on_timer()) and at once where it slides,
     * and the object takes the profile once it lets go (accept_changes()).
     */
    fun pressLayerBar(z: Double) {
        if (state.value.layerEditing?.editing == null) return
        layerPress.value = z
        view.update { it.copy(layerCursor = z) }
        if (layerPresses?.isActive == true) return
        layerPresses = viewModelScope.launch {
            while (layerPress.value != null) {
                while (true) {
                    val at = layerPress.value ?: break
                    val mode = state.value.layerEditing ?: break
                    val outcome = editLayerHeights.edit(mode.tools.action, at, LAYER_EDIT_STRENGTH, mode.tools.bandWidth)
                    if (outcome is LayerEditingOutcome.Success) showLayers(mode.mesh, outcome.editing)
                    withTimeoutOrNull(LAYER_EDIT_INTERVAL_MILLIS) { layerPress.first { it != at } }
                }
                val mesh = state.value.layerEditing?.mesh ?: break
                val accepted = editLayerHeights.accept()
                if (accepted is LayerEditingOutcome.Success) {
                    editLayerHeights.commit(mesh, accepted.editing.profile)
                    showLayers(mesh, accepted.editing)
                }
            }
        }
    }

    /** The finger slides along the bar. */
    fun moveLayerBar(z: Double) {
        if (layerPress.value == null) return
        layerPress.value = z
        view.update { it.copy(layerCursor = z) }
    }

    /** The finger lets go of the bar. */
    fun releaseLayerBar() {
        layerPress.value = null
        view.update { it.copy(layerCursor = null) }
    }

    /** What a press on the bar does. */
    fun setLayerEditAction(action: LayerHeightEdit) = updateLayerTools { it.copy(action = action) }

    /** The band a press edits: the mouse wheel over the bar, between 1.5 and 10 mm. */
    fun setLayerBandWidth(width: Double) = updateLayerTools {
        it.copy(bandWidth = width.coerceIn(LayerEditingTools.MIN_BAND_WIDTH, LayerEditingTools.MAX_BAND_WIDTH))
    }

    fun setAdaptiveQuality(quality: Double) = updateLayerTools { it.copy(adaptiveQuality = quality.coerceIn(0.0, 1.0)) }

    fun setSmoothRadius(radius: Int) = updateLayerTools {
        it.copy(smoothRadius = radius.coerceIn(LayerEditingTools.MIN_SMOOTH_RADIUS, LayerEditingTools.MAX_SMOOTH_RADIUS))
    }

    fun setKeepMin(keepMin: Boolean) = updateLayerTools { it.copy(keepMin = keepMin) }

    /** Adaptive: the profile the object's shape asks for at the window's quality (GLCanvas3D::adaptive_layer_height_profile()). */
    fun adaptiveLayers() = changeLayers { mode -> editLayerHeights.adaptive(mode.tools.adaptiveQuality) }

    /** Smooth (GLCanvas3D::smooth_layer_height_profile()). */
    fun smoothLayers() = changeLayers { mode -> editLayerHeights.smooth(mode.tools.smoothRadius, mode.tools.keepMin) }

    /** Reset (GLCanvas3D::reset_layer_height_profile()): the object has no profile of its own. */
    fun resetLayers() = changeLayers(own = { emptyList() }) { editLayerHeights.reset() }

    /** A button of the window: the engine changes the profile, and the object takes it with its snapshot. */
    private fun changeLayers(own: (LayerEditing) -> List<Double> = LayerEditing::profile, change: suspend (LayerEditingMode) -> LayerEditingOutcome) {
        val mode = state.value.layerEditing?.takeIf { it.editing != null } ?: return
        if (layerPress.value != null) return
        viewModelScope.launch {
            val outcome = change(mode)
            if (outcome !is LayerEditingOutcome.Success) return@launch
            editLayerHeights.commit(mode.mesh, own(outcome.editing))
            showLayers(mode.mesh, outcome.editing)
        }
    }

    private fun updateLayerTools(change: (LayerEditingTools) -> LayerEditingTools) = view.update { it.copy(layerTools = change(it.layerTools)) }

    private fun showLayers(mesh: ScenePath, editing: LayerEditing) = view.update { it.copy(layerDescription = mesh to editing) }

    fun changeArrangeSettings(settings: ArrangeSettings) = setArrangeSettings(settings)

    /** _render_arrange_menu()'s Reset: OrcaSlicer's default arrange settings. */
    fun resetArrangeSettings() = setArrangeSettings(ArrangeSettings())

    /** _render_arrange_menu()'s Arrange: ArrangeJob for every object on the plate. */
    fun arrange() = placePlateObjects(PlateManipulation.Arrange(state.value.arrangeSettings))

    /** The toolbar's OrientJob: the selected object, or every object when none is selected. */
    fun autoOrient() = placePlateObjects(PlateManipulation.AutoOrient(setOfNotNull(state.value.selectedPlateObject?.mesh)))

    /** The toolbar's "Add instance": another copy of the selected one (Plater::increase_instances). */
    fun addInstance() {
        if (!state.value.canCopy) return
        selectedId()?.let { addPlateInstance(it.mesh) }
    }

    /** The toolbar's "Remove instance" (Plater::decrease_instances): the object's last copy goes. */
    fun removeInstance() {
        if (!state.value.canRemoveCopy) return
        selectedId()?.let { removeLastPlateInstances(it.mesh) }
    }

    /** The object menu of the copy at [index] of the 3D view (MenuFactory::object_menu). */
    fun addInstanceOf(index: Int) = copyAt(index)?.let { addPlateInstance(it.mesh) }

    fun removeInstanceOf(index: Int) = copyAt(index)?.let { removeLastPlateInstances(it.mesh) }

    fun setInstancesOf(index: Int, number: Int) = copyAt(index)?.let { setNumberOfInstances(it.mesh, number) }

    /** Center, Drop and Mirror of the copy where it stands. */
    fun manipulate(index: Int, manipulation: Manipulation) = copyAt(index)?.let { placePlateObject(it, manipulation) }

    fun addPartTo(index: Int, shape: String, type: VolumeType, name: String) = copyAt(index)?.let { addObjectPart(it.mesh, shape, type, name) }

    /** ObjectList::layers_editing(): the new range is selected with its settings. */
    fun addHeightRangeTo(index: Int) {
        val id = copyAt(index) ?: return
        val added = addLayerRange(id.mesh, null) ?: return
        selectLayerRange(added)
        setSettingsScope(SettingsScope.OBJECT)
    }

    /** The object menu's commands that change the meshes of the object of the copy at [index]. */
    fun editObjectAt(index: Int, edit: ObjectEdit) = copyAt(index)?.let { editPlateObject(it.mesh, edit) }

    /** "Invalidate cut info" over a copy of a part of a cut. */
    fun invalidateCutInfoAt(index: Int) = copyAt(index)?.let { invalidateCutInfo(it.mesh) }

    /** "Fill bed with instances" over a copy, which the new copies are modelled on. */
    fun fillBedWith(index: Int) = copyAt(index)?.let { fillBedWithInstances(it.mesh, it.instance) }

    /** "Set as an individual object" over a copy: the canvas selects that copy alone. */
    fun setAsIndividualObject(index: Int) = copyAt(index)?.let { separatePlateInstances(it.mesh, setOf(it.instance)) }

    /** Cut, Copy and Paste over a copy, which the canvas selects alone. */
    fun cutObjectAt(index: Int) = copyAt(index)?.let { copyToClipboard.objects(setOf(it), cut = true) }

    fun copyObjectAt(index: Int) = copyAt(index)?.let { copyToClipboard.objects(setOf(it)) }

    fun pasteInto(index: Int) = copyAt(index)?.let { pasteFromClipboard(it) }

    /** The canvas menu's "Add Primitive" and "Add Handy models". */
    fun addPrimitiveShape(shape: String, name: String) = addPrimitive(shape, name)

    fun addHandyModel(model: HandyModel) = addModelToPlate.handy(model)

    /** Paste over empty space: the objects the clipboard holds. */
    fun paste() = pasteFromClipboard(null)

    /**
     * The top bar's Undo and Redo (Plater::undo, Plater::redo); while the
     * painting tool is open they undo and redo its strokes, as the desktop
     * app switches to the gizmo's stack.
     */
    fun undo() {
        view.value.cut?.let { mode ->
            if (mode.canUndo) restoreCut(mode.snapshot - 1)
            return
        }
        if (view.value.painting == null) return undoRedoPlate.undo()
        viewModelScope.launch { paintObject.undo().also(::showStrokes) }
    }

    fun redo() {
        view.value.cut?.let { mode ->
            if (mode.canRedo) restoreCut(mode.snapshot + 1)
            return
        }
        if (view.value.painting == null) return undoRedoPlate.redo()
        viewModelScope.launch { paintObject.redo().also(::showStrokes) }
    }

    /** The clone dialog's OK over a copy, which the canvas selects alone. */
    fun clone(index: Int, count: Int, arrange: Boolean) = copyAt(index)?.let { clonePlateObjects(setOf(it), count, arrange) }

    /** The object menu's Printable: the copy the menu was opened over. */
    fun setPrintableAt(index: Int, printable: Boolean) = copyAt(index)?.let { setPlateObjectPrintable(it, printable) }

    /** "Change Filament" of the object. */
    fun setFilamentAt(index: Int, filament: Int) = copyAt(index)?.let { setExtruder(it.mesh, filament) }

    fun toggleFlushOptionAt(index: Int, option: FlushOption) = copyAt(index)?.let { setFlushOption(it.mesh, option) }

    /**
     * ObjectList::switch_to_object_process(): the copy is selected and the
     * settings show the object's own; the sidebar that holds them opens.
     */
    fun editProcessSettingsAt(index: Int) {
        val id = copyAt(index) ?: return
        selectPlateObject(id)
        setSettingsScope(SettingsScope.OBJECT)
    }

    fun copyProcessSettingsAt(index: Int) = copyAt(index)?.let { copyProcessSettings(SettingsItem.Object(it.mesh)) }

    fun pasteProcessSettingsAt(index: Int) = copyAt(index)?.let { pasteProcessSettings(SettingsItem.Object(it.mesh)) }

    /** The copy the object menu was opened over, which the document pickers act on once they answer. */
    fun copyOf(index: Int): PlateInstanceId? = copyAt(index)

    /** "Export as one STL/DRC" of the object into the document the user picked. */
    fun exportMesh(mesh: ScenePath, format: MeshFormat, document: String) {
        viewModelScope.launch { exportObjectMesh(mesh, format, ExternalDocumentReference(document)) }
    }

    /** "Replace all with 3D files" from the folder the user picked. */
    fun replaceAllVolumes(copy: PlateInstanceId, folder: String) = replaceAllVolumesUseCase(copy, ExternalDocumentReference(folder))

    /** "Replace 3D file": the object's own mesh takes the one of the document the user picked. */
    fun replaceMesh(copy: PlateInstanceId, document: String) = replaceObjectVolume(copy, 0, ExternalDocumentReference(document))

    private fun copyAt(index: Int): PlateInstanceId? = state.value.sceneCopies.getOrNull(index)?.id

    /**
     * The mesh the gizmo shows: without the count to keep, the count and
     * ratio the window then shows are those of the mesh
     * (GLGizmoSimplify::on_render_input_window()); a count still to be worked
     * out from the ratio is now known.
     */
    private fun SimplifyMode.shown(preview: SimplifyPreview): SimplifyMode {
        val shownConfig = when {
            !config.useCount -> config.copy(
                wantedCount = preview.triangles.toInt(),
                decimateRatio = (1f - preview.triangles.toFloat() / preview.original.coerceAtLeast(1)) * 100f,
            )
            config.wantedCount < 0 -> config.withCountByRatio(preview.original)
            else -> config
        }
        return copy(config = shownConfig, preview = preview, running = false)
    }

    /** What the engine decimates by: the count kept or the largest error, whichever the window picked. */
    private fun SimplifyConfig.request(): Any = if (useCount) Triple(true, wantedCount, decimateRatio) else Pair(false, maxError)

    /**
     * GizmoObjectManipulation::change_rotation_value(): the selected object turns
     * by [degrees] about the world [axis] through its bounding sphere's centre.
     */
    fun rotateBy(axis: Int, degrees: Double) {
        if (degrees == 0.0) return
        val target = selected() ?: return
        placePlateObject(selectedId() ?: return, ObjectTransforms.rotated(target.placement, axis, degrees, target.boundingSphere.center), Manipulation.Rotate)
    }

    /** GizmoObjectManipulation::change_absolute_rotation_value(): turns by the difference to the rotation shown. */
    fun setRotation(axis: Int, degrees: Double) {
        val target = selected() ?: return
        rotateBy(axis, degrees - target.rotationDegrees[axis])
    }

    /** GizmoObjectManipulation::reset_rotation_value(true): the rotation from when the tool opened. */
    fun resetRotation() {
        val target = selected() ?: return
        val start = view.value.rotationStart ?: return
        placePlateObject(selectedId() ?: return, ObjectTransforms.withLinearPartOf(target.placement, start), Manipulation.Rotate)
    }

    /** GizmoObjectManipulation::reset_rotation_value(false): no rotation. */
    fun resetRotationToZero() {
        val target = selected() ?: return
        placePlateObject(selectedId() ?: return, target.placement, Manipulation.ResetRotation)
    }

    fun setUniformScale(uniform: Boolean) {
        view.update { it.copy(uniformScale = uniform) }
    }

    /** GizmoObjectManipulation::change_scale_value(): [percent] of the unscaled size on [axis], or on every axis when uniform. */
    fun setScale(axis: Int, percent: Double) {
        val current = state.value.selectedScale ?: return
        if (percent <= 0.0) return
        val factors = if (view.value.uniformScale) {
            Vector3(percent / 100.0, percent / 100.0, percent / 100.0)
        } else {
            current.with(axis, percent).let { Vector3(it.x / 100.0, it.y / 100.0, it.z / 100.0) }
        }
        scaleTo(factors)
    }

    /** GizmoObjectManipulation::change_size_value(): [millimeters] on [axis], as a scale of the unscaled size. */
    fun setSize(axis: Int, millimeters: Double) {
        val target = selected() ?: return
        if (millimeters <= 0.0) return
        val unscaled = target.unscaledDimensions.vector()
        val size = target.dimensions.vector().with(axis, millimeters.coerceAtMost(MAX_NUM))
        val factors = Vector3(size.x / unscaled.x, size.y / unscaled.y, size.z / unscaled.z)
        scaleTo(if (view.value.uniformScale) factors[axis].let { Vector3(it, it, it) } else factors)
    }

    /** GizmoObjectManipulation::reset_scale_value(): the unscaled size. */
    fun resetScale() = scaleTo(Vector3(1.0, 1.0, 1.0))

    private fun scaleTo(factors: Vector3) {
        val target = selected() ?: return
        val unscaled = target.unscaledDimensions.vector()
        // limit_scaling_ratio(): no side beyond MAX_NUM.
        val limited = Vector3(
            factors.x.coerceAtMost(MAX_NUM / unscaled.x),
            factors.y.coerceAtMost(MAX_NUM / unscaled.y),
            factors.z.coerceAtMost(MAX_NUM / unscaled.z),
        )
        val placement = ObjectTransforms.scaled(target.placement, limited, unscaled, target.dimensions.vector(), target.boxCenter)
        placePlateObject(selectedId() ?: return, placement, Manipulation.Scale)
    }

    private fun ModelDimensions.vector() = Vector3(widthMillimeters, depthMillimeters, heightMillimeters)

    private fun Vector3.with(axis: Int, value: Double) = when (axis) {
        0 -> copy(x = value)
        1 -> copy(y = value)
        else -> copy(z = value)
    }

    private fun selected() = state.value.selectedCopy?.instance?.inspection

    /** The copy the tools work on, as the plate names it. */
    private fun selectedId() = state.value.selectedCopy?.id

    fun closeGizmo() {
        view.update { it.copy(gizmo = null) }
    }

    /** GizmoObjectManipulation::change_position_value(): the object moves so its position on [axis] is [value]. */
    fun setPosition(axis: Int, value: Double) {
        val state = state.value
        val index = state.selectedObject ?: return
        val current = state.sceneCopies[index].instance.inspection.placement.columns
        val clamped = value.coerceIn(-MAX_NUM, MAX_NUM)
        if (abs(current[12 + axis] - clamped) < POSITION_EPSILON) return
        placeObject(index, Transform3(current.toMutableList().also { it[12 + axis] = clamped }), Manipulation.Move)
    }

    fun placeObject(index: Int, placement: Transform3, manipulation: Manipulation) {
        state.value.sceneCopies.getOrNull(index)?.let { placePlateObject(it.id, placement, manipulation) }
    }

    /** The object menu's "Auto Drop": ObjectList::toggle_auto_drop() for the object [index]. */
    fun setAutoDrop(index: Int, enabled: Boolean) {
        state.value.sceneCopies.getOrNull(index)?.let { setPlateObjectAutoDrop(it.id, enabled) }
    }

    /** The object menu's "Delete": Plater::remove_selected() for the object [index]. */
    /**
     * Selection::erase() over the copy at [index]: that copy of an object that
     * has several, or the object with its only one.
     */
    fun deleteObject(index: Int) {
        copyAt(index)?.let(removePlateInstance::invoke)
    }

    /** The slice button: the plate or all plates, as its drop-down chose. */
    fun slice() = sliceAction()

    fun chooseSliceMode(mode: SliceMode) = setSliceMode(mode)

    fun cancelSlicing() = cancelPlateSlicing()

    fun dismissProblem() = dismissPlateProblem()

    /** is_valid_scale_ratio() of draw_size(): a ratio of effect, not too big, and positive. */
    private fun isValidScaleRatio(ratio: Double): Boolean = abs(ratio - 1.0) >= SVG_SCALE_RATIO_MIN && ratio <= SVG_SCALE_RATIO_MAX && ratio >= 1e-4

    /** is_approx() of two optional values: both unset, or both set and alike. */
    private fun sameOptional(a: Double?, b: Double?): Boolean = if (a == null || b == null) a == b else abs(a - b) < ANGLE_EPSILON

    private companion object {
        /** libslic3r's EPSILON, which is_approx() compares the text's angles and distances by. */
        const val ANGLE_EPSILON = 1e-4

        /** The longer side of the SVG window's picture (GuiCfg::texture_max_size_px), in pixels. */
        const val SVG_PREVIEW_SIZE = 512

        /** GLGizmoSVG's limits: depth, and the relative scale ratio of a change of size. */
        const val SVG_DEPTH_MIN = 0.01
        const val SVG_DEPTH_MAX = 1e4
        const val SVG_SCALE_RATIO_MIN = 1e-5
        const val SVG_SCALE_RATIO_MAX = 1e4

        /** The gizmos that turn the realistic view off while they are open: Seam, FdmSupports and FuzzySkin. */
        val REALISTIC_OFF_PAINTING = setOf(PaintKind.SEAM, PaintKind.SUPPORTS, PaintKind.FUZZY_SKIN)

        /** GLGizmoSimplify's "static int reduction = 2": Medium. */
        const val DEFAULT_REDUCTION = 2

        /** Configuration::decimate_ratio when a volume is selected. */
        const val DEFAULT_DECIMATE_RATIO = 50f

        /** The detail levels' max_error, from Extra high to Extra low. */
        val MAX_ERRORS = listOf(1e-3f, 1e-2f, 0.1f, 0.5f, 1f)

        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** LayersEditing::strength, and the timer that repeats a press held on the bar (GLCanvas3D::_start_timer()). */
        const val LAYER_EDIT_STRENGTH = 0.005
        const val LAYER_EDIT_INTERVAL_MILLIS = 100L

        // GizmoObjectManipulation.cpp: MAX_NUM, and the change it ignores (EPSILON).
        const val MAX_NUM = 9999.99
        const val POSITION_EPSILON = 1e-4
    }
}

/** What the measuring tool's engine session follows: the volumes to measure, or none. */
private sealed interface MeasureChange {
    /** The tool is closed. */
    data object Closed : MeasureChange

    /** Nothing is selected any more, which closes the tool. */
    data object Lost : MeasureChange

    /** The plate cannot change for now: the session stays as it is. */
    data object Waiting : MeasureChange

    data class Open(val target: MeasureTarget) : MeasureChange
}

/** What the brim ears tool's engine session follows: the copy to work on, or none. */
private sealed interface BrimEarsChange {
    /** The tool is closed. */
    data object Closed : BrimEarsChange

    /** The selection is another, which closes the tool. */
    data object Lost : BrimEarsChange

    /** The plate cannot change for now: the session stays as it is. */
    data object Waiting : BrimEarsChange

    data class Open(val target: BrimEarsTarget) : BrimEarsChange
}

/** What the measuring tool asks the engine, in order. */
private sealed interface MeasureCommand {
    data class Touch(val touch: MeasureTouch) : MeasureCommand

    data class Reset(val reset: MeasureReset) : MeasureCommand

    data class Scale(val ratio: Double) : MeasureCommand
}

private fun MeasureTouch.Explore.ray(pointSelection: Boolean) = MeasureRay(origin, direction, pointSelection, sphereRadius = sphereRadius)

private fun MeasureTouch.Select.ray(pointSelection: Boolean) = MeasureRay(origin, direction, pointSelection, sphereRadius = sphereRadius)

/**
 * What fuzzy skin of the object painted with it comes from: the object's own
 * settings, the presets chosen and the process preset's values as edited.
 */
private data class FuzzySkinSettings(
    val mesh: ScenePath,
    val settings: ModelSettings?,
    val presets: Presets?,
    val process: PresetSettings?,
) {
    companion object {
        fun of(state: PlateState, mesh: ScenePath) = FuzzySkinSettings(
            mesh = mesh,
            settings = state.objects.firstOrNull { it.mesh == mesh }?.settings,
            presets = state.presets,
            process = state.settingsTabs[PresetKind.PRINT]?.settings,
        )
    }
}
