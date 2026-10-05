package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.AppConfigParcel;
import app.orcinus.shadow.slicing.service.BrimEarsParcel;
import app.orcinus.shadow.slicing.service.CalibrationParcel;
import app.orcinus.shadow.slicing.service.CalibrationPrinterParcel;
import app.orcinus.shadow.slicing.service.DirtyPresetsParcel;
import app.orcinus.shadow.slicing.service.ArrangeSettingsParcel;
import app.orcinus.shadow.slicing.service.BedShapeParcel;
import app.orcinus.shadow.slicing.service.ConfigExportOptionsParcel;
import app.orcinus.shadow.slicing.service.CreateFilamentOptionsParcel;
import app.orcinus.shadow.slicing.service.CreatePrinterOptionsParcel;
import app.orcinus.shadow.slicing.service.CreatePrinterRequestParcel;
import app.orcinus.shadow.slicing.service.CustomFilamentsParcel;
import app.orcinus.shadow.slicing.service.CutObjectParcel;
import app.orcinus.shadow.slicing.service.CutParcel;
import app.orcinus.shadow.slicing.service.CutPartsParcel;
import app.orcinus.shadow.slicing.service.CutPlaneParcel;
import app.orcinus.shadow.slicing.service.EmbossPlacementParcel;
import app.orcinus.shadow.slicing.service.EmbossVolumeParcel;
import app.orcinus.shadow.slicing.service.FontFaceParcel;
import app.orcinus.shadow.slicing.service.LayerEditingParcel;
import app.orcinus.shadow.slicing.service.MeasureHoverParcel;
import app.orcinus.shadow.slicing.service.MeasureEditParcel;
import app.orcinus.shadow.slicing.service.MeasurementParcel;
import app.orcinus.shadow.slicing.service.PlateValidationParcel;
import app.orcinus.shadow.slicing.service.PresetCreationParcel;
import app.orcinus.shadow.slicing.service.FilamentPresetsParcel;
import app.orcinus.shadow.slicing.service.ConfigTransferParcel;
import app.orcinus.shadow.slicing.service.PresetBundlesParcel;
import app.orcinus.shadow.slicing.service.EngineStatusParcel;
import app.orcinus.shadow.slicing.service.FlatteningPlanesParcel;
import app.orcinus.shadow.slicing.service.VolumeDescriptionParcel;
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
import app.orcinus.shadow.slicing.service.TextStyleParcel;
import app.orcinus.shadow.slicing.service.SvgPreviewParcel;
import app.orcinus.shadow.slicing.service.TextStylesParcel;
import app.orcinus.shadow.slicing.service.WipeTowerParcel;
import app.orcinus.shadow.slicing.service.PresetNameParcel;
import app.orcinus.shadow.slicing.service.PresetNamesParcel;
import app.orcinus.shadow.slicing.service.PresetSettingsParcel;
import app.orcinus.shadow.slicing.service.PrinterConnectionParcel;
import app.orcinus.shadow.slicing.service.SimplifyParcel;
import app.orcinus.shadow.slicing.service.ModelSettingsOutcomeParcel;
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
     * Plater::priv::load_files() for model files; answers are by dialog id.
     * load is the ModelLoad's name, and chosen whether ProjectDropDialog chose
     * it. stepMeshes holds StepMeshDialog's answer for every file, four values
     * each: chosen (1 or 0), the linear and angle deflections, split (1 or 0).
     * askMulti asks whether several files make one object. objColorCounts
     * holds ObjColorDialog's answer for every file: -1 for none, otherwise
     * how many of objColorFilaments, in turn, are its filaments.
     */
    ModelLoadParcel load(
        in String[] sources,
        in ProfilesParcel profiles,
        in PlacedModelParcel[] plate,
        String prefix,
        in String[] answerIds,
        in boolean[] answers,
        String load,
        boolean chosen,
        in double[] stepMeshes,
        boolean askMulti,
        in int[] objColorCounts,
        in int[] objColorFilaments
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
        in boolean[] answers,
        in @nullable CutParcel cut
    );
    /** edit_objects(): edit is the ObjectEdit's name. */
    ModelLoadParcel editObjects(
        in PlacedModelParcel[] plate,
        in int[] indexes,
        String edit,
        in ProfilesParcel profiles,
        String prefix,
        in String[] answerIds,
        in boolean[] answers
    );
    /** begin_cut(): the cut gizmo opened on a copy of the object. */
    CutObjectParcel beginCut(in PlacedModelParcel plateObject, int instance, in ProfilesParcel profiles);
    CutPlaneParcel describeCutPlane(
        in double[] plane,
        in double[] connectorValues,
        in int[] connectorKinds,
        double snapSpace,
        double snapBulge,
        boolean dovetail,
        in double[] groove,
        boolean preview,
        String meshPrefix,
        in double[] partsPlane,
        in boolean[] parts
    );
    /** select_cut_part(): a right click turns a piece over. */
    CutPartsParcel selectCutPart(in double[] partsPlane, in boolean[] selected, in double[] origin, in double[] direction, String meshPrefix);
    void endCut();
    /** describe_fonts() and the text and SVG tools (EmbossEditor). */
    FontFaceParcel[] describeFonts(in String[] paths);
    ModelLoadParcel createText(in PlacedModelParcel[] plate, in EmbossPlacementParcel placement, String type, String text, in TextStyleParcel style, in ProfilesParcel profiles, String prefix);
    ModelLoadParcel updateText(in PlacedModelParcel[] plate, int index, int volume, String text, in TextStyleParcel style, in @nullable double[] placement, in ProfilesParcel profiles, String prefix);
    ModelLoadParcel createSvg(in PlacedModelParcel[] plate, in EmbossPlacementParcel placement, String type, String svg, in ProfilesParcel profiles, String prefix);
    ModelLoadParcel updateSvg(in PlacedModelParcel[] plate, int index, int volume, double depth, boolean useSurface, @nullable String svg, in @nullable double[] placement, in ProfilesParcel profiles, String prefix);
    EmbossVolumeParcel describeEmboss(in PlacedModelParcel[] plate, int index, int volume, in ProfilesParcel profiles);
    ModelLoadParcel transformEmboss(in PlacedModelParcel[] plate, int index, int instance, int volume, double rotate, double move, in double[] cameraPosition, in double[] cameraForward, boolean perspective, boolean keepUp, in double[] scale, int mirror, String text, in TextStyleParcel style, boolean reEmboss, in ProfilesParcel profiles, String prefix);
    SvgPreviewParcel previewSvg(in PlacedModelParcel[] plate, int index, int volume, String picture, int maxSize, in ProfilesParcel profiles);
    ModelLoadParcel editSvgFile(in PlacedModelParcel[] plate, int index, int volume, String edit, String path, in ProfilesParcel profiles, String prefix);
    ModelLoadParcel renameTextStyle(in PlacedModelParcel[] plate, int index, String oldName, String newName, in ProfilesParcel profiles, String prefix);
    TextStylesParcel textStyles();
    TextStylesParcel storeTextStyles(in TextStyleParcel[] styles, int active);
    /** begin_layer_editing() and the calls of the variable layer height; action is the LayerHeightEdit's name. */
    LayerEditingParcel beginLayerEditing(in PlacedModelParcel[] plate, int index, in ProfilesParcel profiles, in ModelSettingsParcel plateSettings);
    LayerEditingParcel editLayerHeights(String action, double z, double strength, double bandWidth);
    LayerEditingParcel adaptiveLayerHeights(double quality);
    LayerEditingParcel smoothLayerHeights(int radius, boolean keepMin);
    LayerEditingParcel resetLayerHeights();
    LayerEditingParcel acceptLayerHeights();
    void endLayerEditing();
    /** begin_measure() and the calls of the measuring tool; volumes are triples of an object's, a copy's and a volume's index (-1 for all), reset is the MeasureReset's name. */
    MeasurementParcel beginMeasure(in PlacedModelParcel[] plate, in int[] volumes, in ProfilesParcel profiles, boolean assemblyView);
    /** assemblyMode: the AssemblyMode's name, or empty for the measuring tool. */
    MeasureHoverParcel hoverMeasure(in double[] origin, in double[] direction, boolean pointSelection, boolean onlySelectPlane, double sphereRadius, String assemblyMode);
    MeasurementParcel selectMeasure(in double[] origin, in double[] direction, boolean pointSelection, boolean onlySelectPlane, double sphereRadius, String assemblyMode);
    MeasurementParcel resetMeasure(String reset);
    MeasureEditParcel scaleMeasure(in PlacedModelParcel[] plate, double ratio, in ProfilesParcel profiles, String prefix);
    /** action: the AssemblyAction's name. */
    MeasureEditParcel assembleMeasure(in PlacedModelParcel[] plate, String action, in double[] values, in ProfilesParcel profiles, String prefix);
    void endMeasure();
    /** begin_brim_ears() and the calls of the brim ears tool; points hold x, y, z and the radius of every ear. */
    BrimEarsParcel beginBrimEars(in PlacedModelParcel[] plate, int index, int instance, in ProfilesParcel profiles);
    /** The hit's position and the ear's, one after the other; empty without a hit. The section's plane is ClippingPlane's four values. */
    double[] hitBrimEars(in double[] origin, in double[] direction, in double[] clippingPlane);
    double[] generateBrimEars(in double[] points, double maxAngle, double detectionRadius, double headDiameter);
    int[] checkBrimEars(in double[] points);
    void endBrimEars();
    /** save_project(): the error message, null once saved; sliced is the SlicedPlates' name. */
    @nullable String saveProject(
        String path,
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        in ProjectPlateParcel[] plates,
        @nullable String projectInfo,
        int currentPlate,
        String sliced
    );
    /** export_object_mesh(): format is the MeshFormat's name. */
    MeshExportParcel exportMesh(
        in PlacedModelParcel[] plate,
        int index,
        String format,
        in ProfilesParcel profiles,
        String path
    );
    /** export_meshes(): the copies by object and copy index, every object for none; format is the MeshFormat's name. */
    MeshExportParcel exportMeshes(
        in PlacedModelParcel[] plate,
        in int[] objects,
        in int[] instances,
        boolean multi,
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
        String prefix,
        in @nullable double[] stepMesh
    );
    /** place_volume(): manipulation is VolumeManipulation's name. */
    ModelLoadParcel placeVolume(
        in PlacedModelParcel[] plate,
        int index,
        int volume,
        in double[] matrix,
        String manipulation,
        in ProfilesParcel profiles,
        String prefix
    );
    /** mesh_boolean(): operation is MeshBooleanOperation's name. */
    ModelLoadParcel meshBoolean(
        in PlacedModelParcel[] plate,
        int index,
        int source,
        int tool,
        String operation,
        boolean deleteInput,
        in ProfilesParcel profiles,
        String prefix
    );
    /** move_volume() */
    ModelLoadParcel moveVolume(
        in PlacedModelParcel[] plate,
        int index,
        int from,
        int to,
        in ProfilesParcel profiles,
        String prefix
    );
    /** reload_volumes(): objColorFilaments null while ObjColorDialog has not answered. */
    ModelLoadParcel reloadVolumes(
        in PlacedModelParcel[] plate,
        int index,
        in int[] volumes,
        String source,
        in ProfilesParcel profiles,
        String prefix,
        in @nullable int[] objColorFilaments
    );
    /** load_volume(): type is VolumeType's name. */
    ModelLoadParcel loadVolume(
        in PlacedModelParcel[] plate,
        int index,
        String source,
        String name,
        String type,
        in ProfilesParcel profiles,
        String prefix,
        in @nullable double[] stepMesh
    );
    /** StepMeshDialog: the triangles of the STEP file it asks about; stopping runs beside the count. */
    long stepTriangleCount(String source, double linear, double angle);
    oneway void stopStepTriangleCount();
    void releaseStepFile();
    /** ObjColorDialog: its colours clustered into count, and the colours let go. */
    String[] objColorClusters(String source, int count);
    void releaseObjColors();
    ModelLoadParcel objColorPreview(String source, in int[] filaments, String prefix);
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

    /** describe_calibration_printer(): what the dialogs of Input Shaping and Cornering read of the printer. */
    CalibrationPrinterParcel describeCalibrationPrinter(in ProfilesParcel profiles);

    /** prepare_flow_rate_calibration(): the flow ratio test on the empty plate. */
    ModelLoadParcel prepareFlowRateCalibration(boolean linear, int pass, String topSurfacePattern, in ProfilesParcel profiles, String prefix);
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
        in int[] lockedPlates,
        in ModelSettingsParcel[] plateSettings
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
    /** painted_colors(): the colours painted on the object, outside the painting tools. */
    PaintingParcel paintedColors(in PlacedModelParcel plateObject, in ProfilesParcel profiles, String meshPrefix);
    /** GLGizmoPainterBase: the painting tools of the 3D view; kind is the PaintKind's name. */
    PaintingParcel beginPainting(
        in PlacedModelParcel plateObject,
        String kind,
        in ProfilesParcel profiles,
        String meshPrefix,
        int instance,
        boolean assemblyView,
        double explosionRatio
    );
    /** GLGizmoFuzzySkin's warning: fuzzy skin is disabled for the object. */
    boolean fuzzySkinDisabled(in PlacedModelParcel plateObject, in ProfilesParcel profiles);
    /** The assembly view's section: plane holds the normal and the offset; the mesh written, null for none. */
    @nullable String assemblySection(in PlacedModelParcel[] plate, in ProfilesParcel profiles, in double[] plane, double explosionRatio, String meshPath);
    PaintingParcel paintStroke(
        in double[] origin,
        in double[] direction,
        int state,
        double radius,
        String tool,
        double angle,
        double overhangAngle,
        boolean starts,
        double cursorHeight,
        in double[] clippingPlane,
        boolean sinkingLimit,
        String meshPrefix
    );
    /** A painting tool's section: placement and plane (normal and offset); the mesh written, null for none. */
    @nullable String paintingSection(in PlacedModelParcel plateObject, in ProfilesParcel profiles, in double[] placement, in double[] plane, String meshPath);
    PaintingParcel undoPainting(String meshPrefix);
    PaintingParcel redoPainting(String meshPrefix);
    PaintingParcel clearPainting(String meshPrefix);
    /** remap_painting(): remap gives each filament, 0-based, the one it becomes. */
    PaintingParcel remapPainting(in int[] remap, String meshPrefix);
    /** The gap fill tool with its gap area; a negative area leaves it. */
    PaintingParcel setGapFill(double gapArea, String meshPrefix);
    PaintingParcel fillGaps(String meshPrefix);
    PaintingParcel endPainting();
    FlatteningPlanesParcel flatteningPlanes(in PlacedModelParcel plateObject, in ProfilesParcel profiles, in double[] placement);
    VolumeDescriptionParcel describeVolume(in PlacedModelParcel plateObject, in ProfilesParcel profiles, in double[] placement, int volume);
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
    /** update_saved_presets() */
    void updateSavedPresets();
    PresetsParcel addFilament(@nullable String color);
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
    /** PhysicalPrinterDialog: the printer's host on the edited printer preset, and its OK. */
    PrinterConnectionParcel printerConnection();
    PresetSettingsParcel savePrinterConnection(in String[] keys, in String[] values, String name);
    /** Import Configs and Export Preset Bundle of the desktop app's File menu. */
    ConfigTransferParcel importPresets(in String[] paths, in String[] answerPresets, in long[] answers);
    /** CreateFilamentPresetDialog: the vendors, types and presets it offers. */
    CreateFilamentOptionsParcel createFilamentOptions(String type, String baseFilament);
    /** Its Create button. */
    PresetCreationParcel createFilament(
        String vendor, boolean customVendor, String type, String serial,
        in String[] printers, in String[] presets, in String[] answerIds, in boolean[] answers);
    /** CreatePrinterPresetDialog: what its two pages offer. */
    CreatePrinterOptionsParcel createPrinterOptions(in CreatePrinterRequestParcel request);
    /** Its first page's OK. */
    PresetCreationParcel checkPrinterPage(in CreatePrinterRequestParcel request, in String[] answerIds, in boolean[] answers);
    /** Its Create button. */
    PresetCreationParcel createPrinter(in CreatePrinterRequestParcel request, in String[] answerIds, in boolean[] answers);
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
    /** PresetBundleDialog::ListBundles(): the preset bundles the user has. */
    PresetBundlesParcel presetBundles();
    /** Its "Delete bundle": the bundles left. */
    PresetBundlesParcel deletePresetBundle(String id);
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
        in double[] customPoints,
        String texture,
        String model,
        in String[] answerIds,
        in boolean[] answers
    );
    /** BedShapePanel::load_stl(), and Bed_2D's grid as bed_preview_grid() writes it. */
    BedShapeParcel loadBedShape(String path);
    double[] bedPreviewGrid(in double[] points);
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
    PresetSettingsParcel savePreset(String kind, String name, boolean detach, boolean saveToProject);
    PresetSettingsParcel deletePreset(String kind, in String[] answerIds, in boolean[] answers);

    /** PreferencesDialog: the app configuration's values of the keys. */
    AppConfigParcel appConfigValues(in String[] keys, String section);
    AppConfigParcel setAppConfigValue(String key, String value, String section);
    /** MainFrame's recent projects: the values, the most recent first. */
    AppConfigParcel recentProjects();
    AppConfigParcel setRecentProjects(in String[] projects);

    /** Returns at once; the result arrives through the callback. */
    void slice(in SliceRequestParcel request, ISliceCallback callback);
    /** The thumbnails the G-code of the printer holds: width and height of each; null on failure, with the reason in error. */
    ThumbnailSizesParcel thumbnailSizes(in ProfilesParcel profiles);
    /** GUI_App::load_language(): the engine's own messages in the language of OrcaSlicer's catalogue. */
    void setLanguage(String catalog);
    /** Plater::priv::update_background_process()'s validation of the plate; null when it cannot be read. */
    @nullable PlateValidationParcel validatePlate(in PlacedModelParcel[] plate, in ProfilesParcel profiles, in ModelSettingsParcel plateSettings);
    /** GLVolumeCollection::get_selection_support_normal_z(); NaN without presets. */
    double overhangNormalZ(in ProfilesParcel profiles);
    /** select_plate(): the plate the next requests are for, among count plates. */
    void selectPlate(int index, int count);

    boolean cancel(String jobId);
}
