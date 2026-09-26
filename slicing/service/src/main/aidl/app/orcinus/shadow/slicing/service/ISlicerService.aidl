package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.CalibrationParcel;
import app.orcinus.shadow.slicing.service.DirtyPresetsParcel;
import app.orcinus.shadow.slicing.service.ArrangeSettingsParcel;
import app.orcinus.shadow.slicing.service.BedShapeParcel;
import app.orcinus.shadow.slicing.service.ConfigExportOptionsParcel;
import app.orcinus.shadow.slicing.service.CreateFilamentOptionsParcel;
import app.orcinus.shadow.slicing.service.CreatePrinterOptionsParcel;
import app.orcinus.shadow.slicing.service.CustomFilamentsParcel;
import app.orcinus.shadow.slicing.service.PresetCreationParcel;
import app.orcinus.shadow.slicing.service.FilamentPresetsParcel;
import app.orcinus.shadow.slicing.service.ConfigTransferParcel;
import app.orcinus.shadow.slicing.service.EngineStatusParcel;
import app.orcinus.shadow.slicing.service.FlatteningPlanesParcel;
import app.orcinus.shadow.slicing.service.InspectionParcel;
import app.orcinus.shadow.slicing.service.ISliceCallback;
import app.orcinus.shadow.slicing.service.ModelLoadParcel;
import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.ModelSourceParcel;
import app.orcinus.shadow.slicing.service.OrcaTextParcel;
import app.orcinus.shadow.slicing.service.PlacedModelParcel;
import app.orcinus.shadow.slicing.service.PlateDescriptionParcel;
import app.orcinus.shadow.slicing.service.PlateInspectionParcel;
import app.orcinus.shadow.slicing.service.MeshExportParcel;
import app.orcinus.shadow.slicing.service.FlushVolumesParcel;
import app.orcinus.shadow.slicing.service.PaintingParcel;
import app.orcinus.shadow.slicing.service.WipeTowerParcel;
import app.orcinus.shadow.slicing.service.PresetNameParcel;
import app.orcinus.shadow.slicing.service.PresetNamesParcel;
import app.orcinus.shadow.slicing.service.PresetSettingsParcel;
import app.orcinus.shadow.slicing.service.SimplifyParcel;
import app.orcinus.shadow.slicing.service.ModelSettingsOutcomeParcel;
import app.orcinus.shadow.slicing.service.PhysicalPrinterParcel;
import app.orcinus.shadow.slicing.service.PhysicalPrintersParcel;
import app.orcinus.shadow.slicing.service.PresetComparisonParcel;
import app.orcinus.shadow.slicing.service.PresetsParcel;
import app.orcinus.shadow.slicing.service.ProjectPlateParcel;
import app.orcinus.shadow.slicing.service.ProfilesParcel;
import app.orcinus.shadow.slicing.service.SearchCatalogParcel;
import app.orcinus.shadow.slicing.service.ThumbnailSizesParcel;
import app.orcinus.shadow.slicing.service.GcodePlaceholderInfoParcel;
import app.orcinus.shadow.slicing.service.GcodePlaceholdersParcel;
import app.orcinus.shadow.slicing.service.SetupFilamentsParcel;
import app.orcinus.shadow.slicing.service.SettingsTabParcel;
import app.orcinus.shadow.slicing.service.SetupPrintersParcel;
import app.orcinus.shadow.slicing.service.SliceRequestParcel;

/** Binder interface of SlicerService. Calls block; clients call off the main thread. */
interface ISlicerService {
    EngineStatusParcel status();

    PlateDescriptionParcel describePlate(in ProfilesParcel profiles, String directory);
    InspectionParcel inspect(in ModelSourceParcel model, in ProfilesParcel profiles, String meshPath, in PlacedModelParcel[] plate);
    /**
     * Plater::priv::load_files() for a model file; answers are by dialog id.
     * load is the ModelLoad's name, and chosen whether ProjectDropDialog chose it.
     */
    ModelLoadParcel load(
        String source,
        in ProfilesParcel profiles,
        in PlacedModelParcel[] plate,
        String prefix,
        in String[] answerIds,
        in boolean[] answers,
        String load,
        boolean chosen
    );
    /** edit_object(): edit is the ObjectEdit's name; volume -1 edits the whole object. */
    ModelLoadParcel edit(
        in PlacedModelParcel[] plate,
        int index,
        String edit,
        int volume,
        in ProfilesParcel profiles,
        String prefix,
        in String[] answerIds,
        in boolean[] answers
    );
    /** save_project(): the error message, null once saved. */
    @nullable String saveProject(
        String path,
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        in ProjectPlateParcel[] plates,
        @nullable String projectInfo
    );
    /** export_object_mesh(): format is the MeshFormat's name. */
    MeshExportParcel exportMesh(
        in PlacedModelParcel[] plate,
        int index,
        String format,
        in ProfilesParcel profiles,
        String path
    );
    /** replace_volume(): the volume takes the mesh of the file at source. */
    /** simplify_volume(): the triangles of the decimated mesh and of the volume. */
    SimplifyParcel simplifyVolume(
        in PlacedModelParcel[] plate,
        int index,
        int volume,
        boolean useCount,
        int wantedCount,
        float decimateRatio,
        float maxError,
        in ProfilesParcel profiles,
        String path
    );
    ModelLoadParcel setVolumeType(
        in PlacedModelParcel[] plate,
        int index,
        int volume,
        String type,
        in ProfilesParcel profiles,
        String prefix
    );
    ModelLoadParcel applySimplify(
        in PlacedModelParcel[] plate,
        int index,
        int volume,
        boolean useCount,
        int wantedCount,
        float decimateRatio,
        float maxError,
        in ProfilesParcel profiles,
        String prefix
    );
    ModelLoadParcel replaceVolume(
        in PlacedModelParcel[] plate,
        int index,
        int volume,
        String source,
        in ProfilesParcel profiles,
        String prefix
    );
    /** add_primitive(): a shape of create_mesh() as an object of its own. */
    ModelLoadParcel addPrimitive(
        in PlacedModelParcel[] plate,
        String shape,
        String name,
        in ProfilesParcel profiles,
        String prefix
    );
    /** The path of a handy model under resources/handy_models; null when it is not there. */
    @nullable String handyModel(String file);
    /** prepare_calibration(): the calibration's model on the empty plate. */
    ModelLoadParcel prepareCalibration(in CalibrationParcel params, in ProfilesParcel profiles, String prefix);
    /** paste_volumes(): source is the one object the clipboard copied the volumes from. */
    ModelLoadParcel pasteVolumes(
        in PlacedModelParcel[] plate,
        int index,
        int instance,
        in PlacedModelParcel source,
        in int[] volumes,
        boolean sameInputFile,
        in ProfilesParcel profiles,
        String prefix
    );
    /** copy_objects(): placement is the CopyPlacement's name. */
    ModelLoadParcel copy(
        in PlacedModelParcel[] plate,
        in PlacedModelParcel[] sources,
        int count,
        String placement,
        in ProfilesParcel profiles,
        String prefix
    );
    /**
     * Placements: instance transformations, column-major 4 x 4; manipulation:
     * the Manipulation's simple name, with faceNormal for LayOnFace.
     */
    InspectionParcel place(
        in PlacedModelParcel plateObject,
        in ProfilesParcel profiles,
        in double[] previous,
        in double[] placement,
        boolean autoDrop,
        String manipulation,
        in @nullable double[] faceNormal
    );
    /**
     * manipulation: the PlateManipulation's simple name, with the selected
     * mesh paths for AutoOrient and FillBed, arrangeSettings for Arrange,
     * ArrangePlate and FillBed, and FillBed's instance, -1 for none.
     */
    PlateInspectionParcel placeObjects(
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        String manipulation,
        in String[] selected,
        in @nullable ArrangeSettingsParcel arrangeSettings,
        int instance,
        in int[] lockedPlates
    );
    /** The wipe tower of the plate (GLCanvas3D's wipe tower volume). */
    WipeTowerParcel describeWipeTower(
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        in ModelSettingsParcel plateSettings
    );
    /** The flushing volumes of the plate (WipingDialog). */
    FlushVolumesParcel describeFlushVolumes(
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        in ModelSettingsParcel plateSettings
    );
    /** The project's flushing volumes after a change of the filaments; change is a FlushVolumesChange's name. */
    FlushVolumesParcel updateFlushVolumes(
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        in ModelSettingsParcel plateSettings,
        String change,
        int index
    );
    /** GLGizmoMmuSegmentation: the colour painting tool of the 3D view. */
    PaintingParcel beginPainting(
        in PlacedModelParcel plateObject,
        int part,
        in ProfilesParcel profiles,
        String facets,
        String meshPrefix
    );
    PaintingParcel paintStroke(
        in double[] origin,
        in double[] direction,
        int filament,
        double radius,
        String tool,
        double angle,
        boolean starts,
        String meshPrefix
    );
    PaintingParcel undoPainting(String meshPrefix);
    PaintingParcel redoPainting(String meshPrefix);
    PaintingParcel endPainting();
    FlatteningPlanesParcel flatteningPlanes(in PlacedModelParcel plateObject, in ProfilesParcel profiles, in double[] placement);
    /** ObjectList::load_generic_subobject(); type is the VolumeType's name. */
    InspectionParcel addObjectPart(in PlacedModelParcel object, String shape, String type, in ProfilesParcel profiles, String meshPath);

    PresetsParcel presets();
    /** kind: the PresetChoice's simple name; value: its preset name, printer model, or nozzle diameter. */
    PresetsParcel selectPreset(String kind, String value, String action);
    /** Sidebar::add_custom_filament(), delete_filament() and the combo box of a slot. */
    /** dirty_presets(), discard_preset_changes() and reset_project_presets(). */
    DirtyPresetsParcel dirtyPresets();
    PresetsParcel discardPresetChanges();
    PresetsParcel resetProjectPresets();
    PresetsParcel addFilament();
    PresetsParcel removeFilament(int index);
    PresetsParcel selectFilament(int index, String name, String action);
    PresetsParcel setFilamentColor(int index, String color);
    /**
     * The Setup Wizard's data comes in two calls, since every filament of every
     * printer model together would exceed the binder transaction limit.
     */
    SetupPrintersParcel setupPrinters();
    SetupFilamentsParcel setupFilaments(in String[] models);
    PresetsParcel applySetup(in String[] models, in String[] filaments);
    PresetsParcel applyDefaultSetup();

    /**
     * The settings tabs; kind and mode are the PresetKind's and SettingsMode's
     * names. The answers to a change's questions are parallel arrays of dialog
     * ids and Yes flags.
     */
    SettingsTabParcel settingsTab(String kind);
    /**
     * For the settings of an object or of the plate (kind OBJECT and PLATE) the
     * request carries their overrides, since the engine holds no plate: models
     * are the ones of the selected objects, plate the one of the plate, and
     * parent the one of the object a part belongs to (kind PART).
     */
    PresetSettingsParcel settings(
        String kind,
        String page,
        in String[] answerIds,
        in boolean[] answers,
        in @nullable ModelSettingsParcel[] models,
        in @nullable ModelSettingsParcel plate,
        in @nullable ModelSettingsParcel parent
    );
    PresetSettingsParcel changeSetting(
        String kind,
        String page,
        String id,
        String text,
        in String[] answerIds,
        in boolean[] answers,
        in @nullable ModelSettingsParcel[] models,
        in @nullable ModelSettingsParcel plate,
        in @nullable ModelSettingsParcel parent
    );
    PresetSettingsParcel resetSettings(
        String kind,
        String page,
        in String[] ids,
        in String[] answerIds,
        in boolean[] answers,
        in @nullable ModelSettingsParcel[] models,
        in @nullable ModelSettingsParcel plate,
        in @nullable ModelSettingsParcel parent
    );
    ModelSettingsOutcomeParcel pasteModelSettings(
        in ModelSettingsParcel clipboard,
        in ModelSettingsParcel target,
        in @nullable ModelSettingsParcel parent
    );
    PresetSettingsParcel setSettingOverride(String kind, String page, String id, boolean enabled, in String[] answerIds, in boolean[] answers);
    PresetSettingsParcel setCompatiblePresets(String kind, String page, String key, in String[] presets, in String[] answerIds, in boolean[] answers);
    PresetNamesParcel compatiblePresetChoices(String kind, String key);
    /** PhysicalPrinterDialog: the printers the app can send G-code to. */
    PhysicalPrintersParcel physicalPrinters();
    PhysicalPrintersParcel savePhysicalPrinter(in PhysicalPrinterParcel printer, @nullable String renamedFrom);
    PhysicalPrintersParcel deletePhysicalPrinter(String name);
    /** Import Configs and Export Preset Bundle of the desktop app's File menu. */
    ConfigTransferParcel importPresets(in String[] paths, in String[] answerPresets, in long[] answers);
    /** CreateFilamentPresetDialog: the vendors, types and presets it offers. */
    CreateFilamentOptionsParcel createFilamentOptions(String type, String baseFilament);
    /** Its Create button. */
    PresetCreationParcel createFilament(
        String vendor, boolean customVendor, String type, String serial,
        in String[] printers, in String[] presets, in String[] answerIds, in boolean[] answers);
    /** CreatePrinterPresetDialog: what its two pages offer. */
    CreatePrinterOptionsParcel createPrinterOptions(String vendor, String nozzle, String presetVendor, String printerPreset);
    /** Its Create button. */
    PresetCreationParcel createPrinter(
        String model, String nozzle, in double[] printableArea, double maxPrintHeight,
        String customTexture, String customModel, String presetVendor, String printerPreset,
        in String[] filamentPresets, in String[] processPresets, in String[] answerIds, in boolean[] answers);
    /** The filaments of the user's own. */
    CustomFilamentsParcel customFilaments();
    /** EditFilamentPresetDialog: what it shows for one of them. */
    FilamentPresetsParcel filamentPresets(String filamentId);
    /** Its Delete button. */
    PresetCreationParcel deleteFilamentPreset(String preset, in String[] answerIds, in boolean[] answers);
    /** ExportConfigsDialog: what it offers for an export kind. */
    ConfigExportOptionsParcel configExportOptions(String kind);
    /** Its OK: the chosen entries are written into the directory. */
    ConfigTransferParcel exportConfigs(String kind, in String[] names, String directory);
    /** DiffPresetDialog: what two presets of a kind differ in. */
    PresetComparisonParcel comparePresets(in String[] left, in String[] right, boolean showAll);
    /** DiffPresetDialog's Transfer: the values of [options] move from one preset to another. */
    PresetsParcel transferPresetOptions(String kind, String from, String to, in String[] options);
    /** Search::OptionsSearcher: every setting the preset tabs show. */
    SearchCatalogParcel searchCatalog();
    /** EditGCodeDialog: the G-code of a custom G-code setting, the placeholders, and its OK. */
    GcodePlaceholdersParcel gcodePlaceholders(String kind, String key);
    GcodePlaceholderInfoParcel gcodePlaceholder(String key, boolean presets);
    PresetSettingsParcel editCustomGcode(String kind, String page, String key, String value, in String[] answerIds, in boolean[] answers);
    /** RammingDialog closed with OK. */
    PresetSettingsParcel setRammingParameters(String kind, String page, String parameters, in String[] answerIds, in boolean[] answers);
    /** BedShapeDialog: the printable area of the edited printer, and the shape it is set to. */
    BedShapeParcel bedShape();
    PresetSettingsParcel setBedShape(
        String kind,
        double sizeX,
        double sizeY,
        double originX,
        double originY,
        double diameter,
        @nullable String customPath,
        String texture,
        String model,
        in String[] answerIds,
        in boolean[] answers
    );
    PresetSettingsParcel setSettingsMode(
        String kind,
        String mode,
        in @nullable ModelSettingsParcel[] models,
        in @nullable ModelSettingsParcel plate,
        in @nullable ModelSettingsParcel parent
    );
    PresetSettingsParcel setSettingsVariant(
        String kind,
        String page,
        int variant,
        in String[] answerIds,
        in boolean[] answers,
        in @nullable ModelSettingsParcel[] models,
        in @nullable ModelSettingsParcel plate,
        in @nullable ModelSettingsParcel parent
    );
    OrcaTextParcel[] settingTooltip(String kind, String id);
    PresetNameParcel checkPresetName(String kind, String name);
    PresetSettingsParcel savePreset(String kind, String name);
    PresetSettingsParcel deletePreset(String kind, in String[] answerIds, in boolean[] answers);

    /** Returns at once; the result arrives through the callback. */
    void slice(in SliceRequestParcel request, ISliceCallback callback);
    /** The thumbnails the G-code of the printer holds: width and height of each; null on failure, with the reason in error. */
    ThumbnailSizesParcel thumbnailSizes(in ProfilesParcel profiles);
    /** select_plate(): the plate the next requests are for, among count plates. */
    void selectPlate(int index, int count);

    boolean cancel(String jobId);
}
