package app.orcinus.shadow.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelSettings
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
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsScope
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
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
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.ClonePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.CopyProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.CopyToClipboardUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DeletePlateUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.EditPlateObjectUseCase
import app.orcinus.shadow.domain.plate.EnablePaintedFuzzySkinUseCase
import app.orcinus.shadow.domain.plate.ExportObjectMeshUseCase
import app.orcinus.shadow.domain.plate.FillBedWithInstancesUseCase
import app.orcinus.shadow.domain.plate.LockPlateUseCase
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
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.RenamePlateUseCase
import app.orcinus.shadow.domain.plate.ReplaceAllVolumesUseCase
import app.orcinus.shadow.domain.plate.ReplaceObjectVolumeUseCase
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
import app.orcinus.shadow.domain.plate.UndoRedoPlateUseCase
import app.orcinus.shadow.render.scene.ObjectTransforms
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.WIPE_TOWER_INDEX
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val plate = observePlate()

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

    /** What each painting tool was left with, which it opens with again, as the desktop gizmos keep it. */
    private val paintingTools = mutableMapOf<PaintKind, PaintingMode>()
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

    init {
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

    fun addModel(reference: String) = addModelToPlate(ExternalDocumentReference(reference))

    fun addCalibrationCube() = addCalibrationCubeToPlate()

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
        if (current.painting != null || current.gizmo != null) {
            openSimplify.refuse()
            return
        }
        current.simplify?.preview?.let(previewSimplify::discard)
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
        view.update { it.copy(arrangeOptionsOpen = !it.arrangeOptionsOpen, gizmo = null) }
    }

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
        if (view.value.painting == null) return undoRedoPlate.undo()
        viewModelScope.launch { paintObject.undo().also(::showStrokes) }
    }

    fun redo() {
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

    private companion object {
        /** GLGizmoSimplify's "static int reduction = 2": Medium. */
        const val DEFAULT_REDUCTION = 2

        /** Configuration::decimate_ratio when a volume is selected. */
        const val DEFAULT_DECIMATE_RATIO = 50f

        /** The detail levels' max_error, from Extra high to Extra low. */
        val MAX_ERRORS = listOf(1e-3f, 1e-2f, 0.1f, 0.5f, 1f)

        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // GizmoObjectManipulation.cpp: MAX_NUM, and the change it ignores (EPSILON).
        const val MAX_NUM = 9999.99
        const val POSITION_EPSILON = 1e-4
    }
}

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
