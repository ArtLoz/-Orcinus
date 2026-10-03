package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.CalibrationPrinterOutcome
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPartsOutcome
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.DirtyPresetsOutcome
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlowRateCalibration
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeshExportOutcome
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjectCut
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintPlacement
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateValidation
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabOutcome
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SimplifyOutcome
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SlicedPlates
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.StepMeshOptions
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTowerOutcome

fun interface SliceProgressListener {
    fun onProgress(progress: SliceProgress)
}

/**
 * Stable port into a slicing engine. No OrcaSlicer, JNI, or Android type may
 * appear in this contract. Implementations may run the engine in another
 * process, so every call can suspend.
 */
interface SlicerEngine {
    /** Prepares the engine on first use and reports whether it can slice. */
    suspend fun status(): EngineStatus

    /**
     * Suspends until the job ends. Cancelling the calling coroutine cancels
     * the job. Progress may arrive on any thread.
     */
    suspend fun slice(
        request: SliceRequest,
        progressListener: SliceProgressListener,
    ): SliceOutcome

    /** Returns true only when the active job accepted the cancellation. */
    suspend fun cancel(jobId: SliceJobId): Boolean

    /**
     * GUI_App::load_language(): the engine's own messages, such as its print
     * validation, in the language of OrcaSlicer's catalogue [catalog] ("ru",
     * "zh_CN"; "en" for none), as the app shows its texts.
     */
    suspend fun setLanguage(catalog: String) = Unit

    /**
     * The thumbnails the G-code of the printer of [profiles] holds, which the
     * caller renders before it slices (SliceRequest.thumbnails).
     */
    suspend fun thumbnailSizes(profiles: SlicingProfileSelection): ThumbnailSizesOutcome
}

/**
 * The plate and its models as the engine sees them, for the 3D view: the plate
 * of the selected printer, and models placed on it the way slicing places them.
 * Geometry is written into files the caller chooses.
 */
interface PlateInspector {
    /**
     * PartPlateList::select_plate(): the requests that follow are for the
     * plate at [index] of [count] plates, which stands where PlateGrid puts
     * it: objects are judged by its build volume, new ones placed on it, and
     * slicing prints it.
     */
    suspend fun selectPlate(index: Int, count: Int) = Unit

    /** Describes the plate and writes its bed model and texture into [directory]. */
    suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath): PlateDescriptionOutcome

    /**
     * GLVolumeCollection::get_selection_support_normal_z(): the slope.normal_z
     * the canvas highlights overhangs from, after the support settings of the
     * edited presets of [profiles]; null when they cannot be selected.
     */
    suspend fun overhangNormalZ(profiles: SlicingProfileSelection): Float? = null

    /**
     * Plater::priv::update_background_process(): [plate] applied to its print
     * and validated, as the desktop app does after every change; null when it
     * cannot be read.
     */
    suspend fun validatePlate(plate: List<PlacedModel>, profiles: SlicingProfileSelection, plateSettings: ModelSettings): PlateValidation? = null

    /**
     * Loads [model], writes its mesh to [mesh], and places it as OrcaSlicer
     * places an object added to the plate that already holds [plate].
     */
    suspend fun inspect(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        plate: List<PlacedModel>,
    ): ModelInspectionOutcome

    /**
     * Plater::priv::load_files() for the model files [sources] of any type the
     * desktop app imports: their objects are loaded, with the questions and
     * message boxes of the desktop app, and placed together on the plate that
     * holds [plate]. A question is answered by loading again with [answers],
     * by dialog id; the questions about every file but the first are told
     * apart by the file's place ("model_in_meters@1"). The engine writes
     * every object's meshes into files whose names start with [prefix]. A
     * single 3MF file loads as [load] asks, which the user [chosen] in
     * ProjectDropDialog when set; a project loads onto an empty plate and
     * selects its presets. Several files are model files, none a 3MF file;
     * with [askMulti] the user is asked whether they make one object.
     */
    suspend fun load(
        sources: List<ModelPath>,
        profiles: SlicingProfileSelection,
        plate: List<PlacedModel>,
        prefix: ScenePath,
        answers: Map<String, Boolean> = emptyMap(),
        load: ModelLoad = ModelLoad.GEOMETRY,
        chosen: Boolean = false,
        /** StepMeshDialog's answers for the STEP files that asked, by their place among [sources]. */
        stepMeshes: Map<Int, StepMeshOptions> = emptyMap(),
        askMulti: Boolean = false,
    ): ModelLoadOutcome

    /**
     * Plater::export_3mf() for "Save project": the objects of every plate in
     * [plate], the configuration of [profiles] with the project's values the
     * [plates] keep (their wipe tower positions, the flushing volumes), the
     * presets the project brought, and the plates with their names, locks,
     * own settings, codes on their layers and pictures, written to [path] as
     * OrcaSlicer writes a project, with what the project it was opened from
     * holds besides its objects ([projectInfo], LoadedProject.info). With
     * [sliced], Plater::export_gcode_3mf(): the G-code and slice info of the
     * plate at [currentPlate] or of every sliced plate besides.
     */
    suspend fun saveProject(
        path: ScenePath,
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plates: List<ProjectPlate>,
        projectInfo: ScenePath? = null,
        currentPlate: Int = 0,
        sliced: SlicedPlates = SlicedPlates.NONE,
    ): ProjectSaveOutcome

    /**
     * Plater::export_stl(false, true): the object at [index] of [plate], whole,
     * written to [path] as [format].
     */
    suspend fun exportMesh(
        plate: List<PlacedModel>,
        index: Int,
        format: MeshFormat,
        profiles: SlicingProfileSelection,
        path: ScenePath,
    ): MeshExportOutcome

    /**
     * GLGizmoSimplify::process(): the mesh of the volume at [volume] of the
     * object at [index] decimated as [config] says, written to [path] as the
     * 3D view draws that volume.
     */
    suspend fun simplifyVolume(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        config: SimplifyConfig,
        profiles: SlicingProfileSelection,
        path: ScenePath,
    ): SimplifyOutcome

    /**
     * ObjectList::set_volume_type(): the volume at [volume] of the object at
     * [index] takes [type], and the volumes are sorted by type; the engine
     * writes the object anew, with the volume's new place.
     */
    suspend fun setVolumeType(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** GLGizmoSimplify::apply_simplify(): the volume takes its decimated mesh; the engine writes the object anew. */
    suspend fun applySimplify(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        config: SimplifyConfig,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /**
     * Plater::priv::replace_volume_with_stl(): the volume at [volume]
     * (ModelObject::volumes) of the object at [index] takes the mesh of
     * [source]; the engine writes the object anew, named after [prefix].
     */
    suspend fun replaceVolume(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        source: ModelPath,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
        stepMesh: StepMeshOptions? = null,
    ): ModelLoadOutcome

    /**
     * Plater::priv::reload_from_disk() of one file: [source] takes the place of
     * the [volumes] (ModelObject::volumes) of the object at [index] that came
     * from a file of its name or are named after it; the engine writes the
     * object anew, named after [prefix], when one changed, and names the
     * volumes the file had nothing for (ModelLoadOutcome.Success.failed).
     */
    suspend fun reloadVolumes(
        plate: List<PlacedModel>,
        index: Int,
        volumes: List<Int>,
        source: ModelPath,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = ModelLoadOutcome.Failure("Reloading volumes from a file is not supported")

    /**
     * ObjectList::load_modifier() of one file: [source] joins the object at
     * [index] as one volume of [type] named [name]; the engine writes the
     * object anew, named after [prefix], and tells the new volume
     * (ModelLoadOutcome.Success.selectedVolume). A STEP file waits for
     * StepMeshDialog without [stepMesh].
     */
    suspend fun loadVolume(
        plate: List<PlacedModel>,
        index: Int,
        source: ModelPath,
        name: String,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
        stepMesh: StepMeshOptions? = null,
    ): ModelLoadOutcome = ModelLoadOutcome.Failure("Loading a volume from a file is not supported")

    /**
     * StepMeshDialog::update_mesh_number_text(): the triangles the STEP file
     * [source] that waits for the dialog makes at these deflections; 0 when
     * the count was stopped.
     */
    suspend fun stepTriangleCount(source: ModelPath, linearDeflection: Double, angleDeflection: Double): Long

    /** StepMeshDialog::stop_task(): a running count stops at once. */
    suspend fun stopStepTriangleCount()

    /** The STEP file kept for the dialog is let go. */
    suspend fun releaseStepFile()

    /**
     * ObjectList::load_shape_object(): a [shape] of create_mesh() joins [plate]
     * as an object named [name], in the empty cell nearest to the centre of
     * the plate. The engine writes its meshes named after [prefix].
     */
    suspend fun addPrimitive(
        plate: List<PlacedModel>,
        shape: String,
        name: String,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** One of OrcaSlicer's handy models, the [file] under resources/handy_models; null when it is not there. */
    suspend fun handyModel(file: String): ModelPath?

    /**
     * Plater::calib_temp() and the other calibrations once the new project for
     * them stands: the calibration's model set up on the empty plate as the
     * calibration sets it, and the values of the selected presets it prints
     * with changed. The engine writes its meshes named after [prefix].
     */
    suspend fun prepareCalibration(
        params: CalibrationParams,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /**
     * What the dialogs of Input Shaping and Cornering read of the edited preset
     * of the printer [profiles] select: its firmware, its junction deviation
     * and the input shapers the firmware knows.
     */
    suspend fun describeCalibrationPrinter(profiles: SlicingProfileSelection): CalibrationPrinterOutcome

    /**
     * Plater::calib_flowrate() once the new project for it stands: the objects
     * of the flow ratio [test] set up on the empty plate, each with the flow
     * ratio its name tells, and the values of the selected presets changed.
     * The engine writes its meshes named after [prefix].
     */
    suspend fun prepareFlowRateCalibration(
        test: FlowRateCalibration,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /**
     * Selection::paste_volumes_from_clipboard(): the [volumes] (ModelObject::volumes)
     * of [source], the object the clipboard copied them from, join the object at
     * [index] of [plate] over its copy [instance]; they keep their place when
     * [sameInputFile] and the copies turn alike. The engine writes the object
     * anew, named after [prefix].
     */
    suspend fun pasteVolumes(
        plate: List<PlacedModel>,
        index: Int,
        instance: Int,
        source: PlacedModel,
        volumes: List<Int>,
        sameInputFile: Boolean,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /**
     * New objects copied from [sources], objects with only the copies taken,
     * onto [plate]: [count] rounds of them placed as [placement] says. The
     * engine writes their meshes named after [prefix].
     */
    suspend fun copy(
        plate: List<PlacedModel>,
        sources: List<PlacedModel>,
        count: Int,
        placement: CopyPlacement,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /**
     * The object menu's commands that change meshes: [edit] of the object at
     * [index] of [plate], or of its volume at [volume] (ModelObject::volumes;
     * null for the whole object). Questions are answered as for [load], and the
     * engine writes the meshes of the objects that come of it named after [prefix].
     */
    suspend fun edit(
        plate: List<PlacedModel>,
        index: Int,
        edit: ObjectEdit,
        volume: Int?,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
        answers: Map<String, Boolean> = emptyMap(),
        /** What [ObjectEdit.CUT] cuts with. */
        cut: ObjectCut? = null,
    ): ModelLoadOutcome

    /**
     * GLGizmoCut3D opened on the copy at [instance] of [plateObject]: the
     * engine keeps the object until [endCut], as the gizmo's clippers keep its
     * meshes, and reports the bounding box its plane starts in the centre of.
     */
    suspend fun beginCut(plateObject: PlacedModel, instance: Int, profiles: SlicingProfileSelection): CutObjectOutcome

    /**
     * What the cut gizmo shows of [plane] (ObjectCut.plane): the size of what it
     * cuts, whether it goes through the object, the outline of the section,
     * which of [connectors] cannot be cut with, and their shapes, written as
     * meshes named after [meshPrefix].
     */
    suspend fun describeCutPlane(
        plane: Transform3,
        connectors: List<CutConnector>,
        snapSpace: Double,
        snapBulge: Double,
        /** The dovetail cut's grooves; null for the planar cut. */
        groove: CutGroove?,
        /** Whether the parts of the dovetail cut are worked out too. */
        preview: Boolean,
        /** The pieces of a right click as they go now, whose joined contours the section leaves out. */
        parts: CutPartSelection?,
        meshPrefix: ScenePath,
    ): CutPlaneOutcome

    /**
     * A right click of the cut gizmo (PartSelection::toggle_selection()): the
     * pieces of the object split by [CutPartSelection.plane], set as
     * [CutPartSelection.selected] (as they fall where it does not fit), with
     * the piece the ray from [origin] along [direction] meets first turned over.
     */
    suspend fun selectCutPart(parts: CutPartSelection, origin: Vector3, direction: Vector3, meshPrefix: ScenePath): CutPartsOutcome

    /** Closes the cut gizmo, which lets its object go. */
    suspend fun endCut()

    /**
     * Commits [manipulation] of [plateObject], with its parts, from [previous]
     * to the instance transformation [placement], as OrcaSlicer does; with
     * [autoDrop] off the object is never moved onto the plate. Reports the
     * placed object.
     */
    suspend fun place(
        plateObject: PlacedModel,
        profiles: SlicingProfileSelection,
        previous: Transform3,
        placement: Transform3,
        autoDrop: Boolean,
        manipulation: Manipulation,
    ): ModelInspectionOutcome

    /**
     * The wipe tower of the plate as the desktop canvas draws it: where it
     * stands, how big it is and which filaments stripe it. [plateSettings]
     * carries wipe_tower_x and wipe_tower_y when the plate places it; without
     * them the engine answers with the position a new tower takes.
     */
    suspend fun describeWipeTower(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
    ): WipeTowerOutcome

    /**
     * The flushing volumes of the plate (WipingDialog): the matrix the project
     * carries in [plateSettings] as flush_volumes_matrix and flush_multiplier,
     * beside the volumes OrcaSlicer works out from the filament colours.
     */
    suspend fun describeFlushVolumes(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
    ): FlushVolumesOutcome

    /**
     * The project's flushing volumes after [change] to the filament at [index]:
     * the matrix of [plateSettings] brought to the filaments of [profiles], then
     * worked out again from the colours where OrcaSlicer does it, as its "Auto
     * flush after changing..." preference asks. The outcome tells whether the
     * project's volumes changed, which the caller then keeps.
     */
    suspend fun updateFlushVolumes(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
        change: FlushVolumesChange,
        index: Int,
    ): FlushVolumesOutcome

    /**
     * GLGizmoPainterBase: opens the painting tool of [kind] on [plateObject],
     * or on one of its parts, with the facets it is already painted with. The
     * engine keeps the tool open until [endPainting], as the desktop gizmo
     * keeps its selectors, and writes the painted triangles as meshes named
     * after [meshPrefix] for the 3D view.
     */
    suspend fun beginPainting(
        plateObject: PlacedModel,
        /** The part to paint; null paints the object's own mesh. */
        part: Int?,
        kind: PaintKind,
        profiles: SlicingProfileSelection,
        facets: PaintedFacets,
        meshPrefix: ScenePath,
        /** The copy painted and where it stands. */
        placement: PaintPlacement = PaintPlacement(),
    ): PaintingOutcome

    /** One touch of a finger on the model being painted. */
    suspend fun paint(stroke: PaintStroke, meshPrefix: ScenePath): PaintingOutcome

    /**
     * GLGizmoFuzzySkin's warning: fuzzy skin is "Disabled" for [plateObject],
     * by its own settings or else by the process preset of [profiles], so the
     * fuzzy skin painted on it does not take effect.
     */
    suspend fun fuzzySkinDisabled(plateObject: PlacedModel, profiles: SlicingProfileSelection): Boolean

    /**
     * ModelObjectsClipper::render_cut() of the assembly view: the sections of
     * every volume of [plate] with the "Section View" plane ([normal] and
     * [offset]), every object at its first copy's assemble transformation
     * spread by [explosionRatio], written to [meshPath]; null while the plane
     * meets nothing.
     */
    suspend fun assemblySection(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        normal: Vector3,
        offset: Double,
        explosionRatio: Double,
        meshPath: ScenePath,
    ): ScenePath? = null

    /** The painting tool's own Undo and Redo: the painting before the last stroke, or after the one undone. */
    suspend fun undoPainting(meshPrefix: ScenePath): PaintingOutcome

    suspend fun redoPainting(meshPrefix: ScenePath): PaintingOutcome

    /** "Erase all": the painting tool's kind of paint comes off the model, which its Undo brings back. */
    suspend fun clearPainting(meshPrefix: ScenePath): PaintingOutcome

    /**
     * The gap fill tool (TriangleSelectorPatch's filter state): while [gapArea]
     * is set, in square millimetres, the painted meshes show the painting as
     * the gap fill would leave it; null leaves the tool.
     */
    suspend fun setGapFill(gapArea: Double?, meshPrefix: ScenePath): PaintingOutcome

    /** "Perform" of the gap fill: the patches under the gap area take the state around them, which Undo brings back. */
    suspend fun fillGaps(meshPrefix: ScenePath): PaintingOutcome

    /** Closes the tool and reports the painted facets to keep with the object. */
    suspend fun endPainting(): PaintingOutcome

    /** Commits [manipulation] of the objects on the plate, [plate], and reports every object as placed. */
    suspend fun placeObjects(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        manipulation: PlateManipulation,
    ): PlateInspectionOutcome

    /**
     * ObjectList::load_generic_subobject(): one of OrcaSlicer's shapes joins
     * [plateObject] as a part, a negative volume, a modifier, or a support
     * blocker or enforcer. The engine writes the part's mesh to [mesh] and
     * reports where it stands in the object.
     */
    suspend fun addPart(
        plateObject: PlacedModel,
        shape: String,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
    ): ModelInspectionOutcome

    /** The faces [plateObject], with its parts, can lie on with the instance transformation [placement]. */
    suspend fun flatteningPlanes(
        plateObject: PlacedModel,
        profiles: SlicingProfileSelection,
        placement: Transform3,
    ): FlatteningPlanesOutcome
}

/**
 * OrcaSlicer's Preferences: the "app" section of the app configuration, which
 * the engine keeps and saves with the presets (OrcaSlicer.conf).
 */
interface AppConfigStore {
    /** AppConfig::get() of every key, its default where it was never set. */
    suspend fun appConfigValues(keys: List<String>): AppConfigOutcome

    /** AppConfig::set() and save(): the value the key has afterwards. */
    suspend fun setAppConfigValue(key: String, value: String): AppConfigOutcome

    /**
     * AppConfig::get_recent_projects(): MainFrame's recent projects as
     * OrcaSlicer.conf keeps them, the most recent first; null when the engine
     * could not answer.
     */
    suspend fun recentProjects(): List<String>? = null

    /** AppConfig::set_recent_projects() and save(): what the list holds afterwards; null when it could not be written. */
    suspend fun setRecentProjects(projects: List<String>): List<String>? = null
}

/**
 * OrcaSlicer's presets as its sidebar and Setup Wizard offer them. The engine
 * keeps the installed printers and filaments and the selection in its app
 * configuration, which outlives the process.
 */
interface PresetManager {
    /** The presets the sidebar offers for the selection the engine remembers. */
    suspend fun presets(): PresetsOutcome

    /** Selects a preset as the sidebar does and remembers the selection. */
    suspend fun selectPreset(choice: PresetChoice, action: PresetChangeAction = PresetChangeAction.ASK): PresetsOutcome

    /**
     * GUI_App::has_current_preset_changes(): the presets of the process,
     * filament and printer tabs that have unsaved changes.
     */
    suspend fun dirtyPresets(): DirtyPresetsOutcome = DirtyPresetsOutcome.Success(emptyList())

    /** Every tab's preset loses its unsaved changes (Tab's discard_current_changes). */
    suspend fun discardPresetChanges(): PresetsOutcome = presets()

    /** A new project: the presets the project before brought go (reset_project_embedded_presets). */
    suspend fun resetProjectPresets(): PresetsOutcome = presets()

    /**
     * DiffPresetDialog's Transfer (Tab::transfer_options): the values [options]
     * hold in the preset [from] move into the preset [to] of [kind], which the
     * app selects and edits with them as unsaved changes.
     */
    suspend fun transferPresetOptions(kind: PresetKind, from: String, to: String, options: List<String>): PresetsOutcome

    /**
     * CreateFilamentPresetDialog: the vendors and types it offers, the filaments
     * of [type], and the presets of [baseFilament]; both may be empty, as the
     * dialog opens with nothing chosen.
     */
    suspend fun createFilamentOptions(type: String = "", baseFilament: String = ""): CreateFilamentOptionsOutcome

    /** Its Create button: a filament of the user's own for every chosen preset. */
    suspend fun createFilament(request: CreateFilamentRequest, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome

    /**
     * CreatePrinterPresetDialog: the vendors and models its first page offers,
     * the printer presets of [presetVendor] its second page offers, and the
     * presets that come with [printerPreset].
     */
    suspend fun createPrinterOptions(
        vendor: String = "",
        nozzle: String = "",
        presetVendor: String = "",
        printerPreset: String = "",
    ): CreatePrinterOptionsOutcome

    /** Its Create button: a printer of the user's own with the presets it prints with. */
    suspend fun createPrinter(request: CreatePrinterRequest, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome

    /** The filaments of the user's own (GuideFrame::update_custom_filaments). */
    suspend fun customFilaments(): CustomFilamentsOutcome

    /** EditFilamentPresetDialog: what it shows for the filament with [filamentId]. */
    suspend fun filamentPresets(filamentId: String): FilamentPresetsOutcome

    /** Its Delete button: the preset is deleted once the user has answered. */
    suspend fun deleteFilamentPreset(preset: String, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome

    /** Sidebar::add_custom_filament(): another filament joins the plate. */
    suspend fun addFilament(): PresetsOutcome

    /** Sidebar::delete_filament(): the filament at [index] leaves it; the first one stays. */
    suspend fun removeFilament(index: Int): PresetsOutcome

    /** The preset of the filament at [index]. */
    suspend fun selectFilament(index: Int, name: ProfileId, action: PresetChangeAction = PresetChangeAction.ASK): PresetsOutcome

    /** The colour the sidebar shows for the filament at [index]. */
    suspend fun setFilamentColor(index: Int, color: String): PresetsOutcome

    /** Every printer model the Setup Wizard offers. */
    suspend fun setupPrinters(): SetupPrintersOutcome

    /** The filaments the Setup Wizard offers for the printer models with the ids [models]. */
    suspend fun setupFilaments(models: List<String>): SetupFilamentsOutcome

    /**
     * The Setup Wizard's Finish: installs the printer models with the ids
     * [models], each with all its nozzle diameters, and the [filaments] in place
     * of the installed ones, and selects the printer it added first.
     */
    suspend fun applySetup(models: List<String>, filaments: List<String>): PresetsOutcome

    /** The Setup Wizard closed while no printer is installed: OrcaSlicer's default printer and filament. */
    suspend fun applyDefaultSetup(): PresetsOutcome
}

/**
 * OrcaSlicer's settings tabs: the edited presets, which the engine keeps, as
 * the tabs show and change them, and saving and deleting user presets. A
 * change answers with the corrections OrcaSlicer made and the message boxes it
 * showed; a question it asks is answered by requesting the change again with
 * the answers so far, by dialog id (true for Yes).
 */
interface PresetSettingsEditor {
    /** The definitions of the settings the tab of [kind] can show. */
    suspend fun settingsTab(kind: PresetKind): SettingsTabOutcome

    /**
     * The edited preset of [kind] with its pages, [page] shown; the page the
     * tab shows already when [page] is empty. The settings of an object or of
     * the plate ([PresetKind.OBJECT], [PresetKind.PLATE]) are described with
     * the overrides [model] carries, and the result gives them back.
     */
    suspend fun settings(
        kind: PresetKind,
        page: String = "",
        answers: Map<String, Boolean> = emptyMap(),
        model: ModelSettingsRequest = ModelSettingsRequest(),
    ): PresetSettingsOutcome

    /** The field of the setting [id] changed to [text]. */
    suspend fun changeSetting(
        kind: PresetKind,
        page: String,
        id: String,
        text: String,
        answers: Map<String, Boolean> = emptyMap(),
        model: ModelSettingsRequest = ModelSettingsRequest(),
    ): PresetSettingsOutcome

    /** The undo buttons: the settings [ids], or every modified setting when empty, back to the saved preset. */
    suspend fun resetSettings(
        kind: PresetKind,
        page: String,
        ids: List<String>,
        answers: Map<String, Boolean> = emptyMap(),
        model: ModelSettingsRequest = ModelSettingsRequest(),
    ): PresetSettingsOutcome

    /**
     * ObjectList::paste_settings_into_list(): the settings of an item of the
     * object list once [clipboard], copied from an item of the same kind, is
     * pasted into its own [target]. A part or a height range sits on the
     * settings of its object, [parent]; an object has none.
     */
    suspend fun pasteModelSettings(clipboard: ModelSettings, target: ModelSettings, parent: ModelSettings?): ModelSettingsOutcome

    /**
     * The check box of a filament override: switched on, the setting takes the
     * value of the preset it overrides; switched off, the filament leaves it to it.
     */
    suspend fun setSettingOverride(
        kind: PresetKind,
        page: String,
        id: String,
        enabled: Boolean,
        answers: Map<String, Boolean> = emptyMap(),
    ): PresetSettingsOutcome

    /** The presets the edited preset is compatible with; empty for every preset. */
    suspend fun setCompatiblePresets(
        kind: PresetKind,
        page: String,
        key: String,
        presets: List<String>,
        answers: Map<String, Boolean> = emptyMap(),
    ): PresetSettingsOutcome

    /** The presets the list of compatible presets offers. */
    suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome

    /** The printer's host as the edited printer preset holds it (PhysicalPrinterDialog's m_config). */
    suspend fun printerConnection(): PrinterConnectionOutcome

    /** PhysicalPrinterDialog::OnOK(): the host's [settings] on the edited printer preset, saved as [name] and selected. */
    suspend fun savePrinterConnection(settings: ModelSettings, name: String): PresetSettingsOutcome

    /**
     * PresetBundle::import_presets(): the user presets the files hold, which
     * are OrcaSlicer's .json, .zip, .orca_printer, .orca_bundle and
     * .orca_filament. A preset of the same name is replaced.
     */
    suspend fun importPresets(paths: List<String>, answers: Map<String, ConfigOverwriteAnswer> = emptyMap()): ConfigTransferOutcome

    /** PresetBundle::export_current_configs(): the user presets of the selection, written into [directory]. */
    suspend fun configExportOptions(kind: ConfigExportKind): ConfigExportOptionsOutcome

    /**
     * Its OK: the entries chosen by name are written into [directory] as the
     * bundles and archives the desktop app writes.
     */
    suspend fun exportConfigs(kind: ConfigExportKind, names: List<String>, directory: String): ConfigTransferOutcome

    /** DiffPresetDialog: what the presets [left] and [right] of [kind] differ in. */
    suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean): PresetComparisonOutcome

    /** Search::OptionsSearcher: every setting the preset tabs show, with where it sits. */
    suspend fun searchCatalog(): SearchCatalogOutcome

    /**
     * EditGCodeDialog for the custom G-code [key] on the tab of [kind]: the
     * G-code it opens with, and the placeholders it lists.
     */
    suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome

    /**
     * What the dialog says about the placeholder [key]; [presets] when it is
     * one of the "Presets" group, whose definitions win.
     */
    suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo

    /**
     * Tab::edit_custom_gcode() once the dialog is closed with OK: the custom
     * G-code [key] becomes [value], without the checks a change of its field makes.
     */
    suspend fun editCustomGcode(
        kind: PresetKind,
        page: String,
        key: String,
        value: String,
        answers: Map<String, Boolean> = emptyMap(),
    ): PresetSettingsOutcome

    /**
     * RammingDialog closed with OK: filament_ramming_parameters of the edited
     * filament becomes [parameters], the string the dialog wrote.
     */
    suspend fun setRammingParameters(
        kind: PresetKind,
        page: String,
        parameters: String,
        answers: Map<String, Boolean> = emptyMap(),
    ): PresetSettingsOutcome

    /** BedShapeDialog: the printable area of the edited printer, as its dialog shows it. */
    suspend fun bedShape(): BedShapeOutcome

    /**
     * The shape the dialog was closed with, written into the edited printer
     * preset. A custom shape is the horizontal projection of the model at
     * [customPath] (BedShapePanel::load_stl).
     */
    suspend fun setBedShape(shape: BedShape, customPath: ModelPath? = null, answers: Map<String, Boolean> = emptyMap()): PresetSettingsOutcome

    /** Which settings the tabs show; remembered in the app configuration. */
    suspend fun setSettingsMode(
        kind: PresetKind,
        mode: SettingsMode,
        model: ModelSettingsRequest = ModelSettingsRequest(),
    ): PresetSettingsOutcome

    /**
     * Tab::m_variant_combo: the tab shows the values of the extruder variant
     * [variant] of its list (Tab::update_extruder_variants).
     */
    suspend fun setSettingsVariant(
        kind: PresetKind,
        page: String,
        variant: Int,
        answers: Map<String, Boolean> = emptyMap(),
        model: ModelSettingsRequest = ModelSettingsRequest(),
    ): PresetSettingsOutcome

    /** The tooltip of the setting [id]'s field, with the parent preset's value and the range; empty when unknown. */
    suspend fun settingTooltip(kind: PresetKind, id: String): List<OrcaText>

    /** Whether the edited preset can be saved as [name]. */
    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome

    /** Saves the edited preset as the user preset [name] and selects it. */
    suspend fun savePreset(kind: PresetKind, name: String): PresetSettingsOutcome

    /** Deletes the selected user preset, which asks first, and selects another. */
    suspend fun deletePreset(kind: PresetKind, answers: Map<String, Boolean> = emptyMap()): PresetSettingsOutcome
}
