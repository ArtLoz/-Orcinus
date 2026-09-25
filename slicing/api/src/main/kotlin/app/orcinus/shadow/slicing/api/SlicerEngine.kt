package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PhysicalPrintersOutcome
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabOutcome
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.Transform3
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
    /** Describes the plate and writes its bed model and texture into [directory]. */
    suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath): PlateDescriptionOutcome

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
     * Plater::priv::load_files() for the model file [source] of any type the
     * desktop app imports: its objects are loaded, with the questions and
     * message boxes of the desktop app, and placed on the plate that holds
     * [plate]. A question is answered by loading again with [answers], by
     * dialog id. The engine writes every object's meshes into files whose
     * names start with [prefix].
     */
    suspend fun load(
        source: ModelPath,
        profiles: SlicingProfileSelection,
        plate: List<PlacedModel>,
        prefix: ScenePath,
        answers: Map<String, Boolean> = emptyMap(),
    ): ModelLoadOutcome

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
     * GLGizmoMmuSegmentation: opens the colour painting tool on [plateObject],
     * or on one of its parts, with the facets it is already painted with. The
     * engine keeps the tool open until [endPainting], as the desktop gizmo
     * keeps its selectors, and writes the painted triangles as meshes named
     * after [meshPrefix] for the 3D view.
     */
    suspend fun beginPainting(
        plateObject: PlacedModel,
        /** The part to paint; null paints the object's own mesh. */
        part: Int?,
        profiles: SlicingProfileSelection,
        facets: PaintedFacets,
        meshPrefix: ScenePath,
    ): PaintingOutcome

    /** One touch of a finger on the model being painted. */
    suspend fun paint(stroke: PaintStroke, meshPrefix: ScenePath): PaintingOutcome

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

    /** PhysicalPrinterCollection: the printers the app can send G-code to. */
    suspend fun physicalPrinters(): PhysicalPrintersOutcome

    /** PhysicalPrinterCollection::save_printer(): [renamedFrom] is the name it had before. */
    suspend fun savePhysicalPrinter(printer: PhysicalPrinter, renamedFrom: String? = null): PhysicalPrintersOutcome

    suspend fun deletePhysicalPrinter(name: String): PhysicalPrintersOutcome

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
