package app.orcinus.shadow.slicing.nativebridge

/** Called by the native bridge, possibly from an Orca worker thread. */
internal fun interface NativeProgressListener {
    fun onProgress(percent: Int, message: String)
}

/** Constructed by the native bridge; field order matches native_bridge.cpp. */
internal class NativeSliceResult(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val layerCount: Long,
    @JvmField val estimatedPrintTimeSeconds: Long,
    @JvmField val filamentMicrometers: Long,
    @JvmField val toolpathsWritten: Boolean,
    @JvmField val wipeTowerWritten: Boolean,
) {
    companion object {
        const val SUCCESS = 0L
        const val CANCELLED = 1L
        const val BUSY = 2L
        const val OUTPUT_WRITE_FAILED = 3L
        const val SLICING_FAILED = 4L
        const val PROFILE_NOT_FOUND = 5L
        const val MODEL_READ_FAILED = 6L
        const val ENGINE_NOT_READY = 7L
        const val INVALID_PRINT = 8L
    }
}

/** Constructed by the native bridge; see PlateDescription in orca_engine_adapter.hpp. */
internal class NativePlateDescription(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val printableArea: DoubleArray,
    @JvmField val printableHeight: Double,
    @JvmField val plateTriangles: FloatArray,
    @JvmField val excludeTriangles: FloatArray,
    @JvmField val thinGridLines: FloatArray,
    @JvmField val boldGridLines: FloatArray,
    @JvmField val bedModelMesh: String,
    @JvmField val bedTexture: String,
    @JvmField val filamentColour: String,
)

/** Constructed by the native bridge; see WipeTowerState in orca_engine_adapter.hpp. */
internal class NativeWipeTower(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val shown: Boolean,
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val width: Double,
    @JvmField val depth: Double,
    @JvmField val height: Double,
    @JvmField val rotation: Double,
    @JvmField val brimWidth: Double,
    /** The filaments printed on the plate, 1-based. */
    @JvmField val filaments: DoubleArray,
)

/** Constructed by the native bridge; see FlushVolumes in orca_engine_adapter.hpp. */
internal class NativeFlushVolumes(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val filaments: Long,
    @JvmField val nozzles: Long,
    @JvmField val matrix: DoubleArray,
    @JvmField val automatic: DoubleArray,
    @JvmField val multipliers: DoubleArray,
    @JvmField val modified: Boolean,
    /** update_flush_volumes(): the project's volumes changed. */
    @JvmField val updated: Boolean,
)

/** Constructed by the native bridge; see PaintingState in orca_engine_adapter.hpp. */
internal class NativePainting(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val hit: Boolean,
    /** The filaments the model is painted with, 1-based. */
    @JvmField val filaments: DoubleArray,
    /** The mesh of the triangles painted with each of them. */
    @JvmField val meshes: Array<String>,
    @JvmField val facets: String,
)

/** Constructed by the native bridge; see ModelInspection in orca_engine_adapter.hpp. */
internal class NativeModelInspection(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val facetCount: Long,
    @JvmField val sizeX: Double,
    @JvmField val sizeY: Double,
    @JvmField val sizeZ: Double,
    @JvmField val instanceMatrix: DoubleArray,
    /** NativeVolumeState. */
    @JvmField val volumeState: Long,
    @JvmField val sphereCenter: DoubleArray,
    @JvmField val sphereRadius: Double,
    @JvmField val rotationDegrees: DoubleArray,
    @JvmField val unscaledSize: DoubleArray,
    @JvmField val boxCenter: DoubleArray,
)

/** Constructed by the native bridge; see PlateInspection in orca_engine_adapter.hpp. */
internal class NativePlateInspection(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** The copies of every object of the plate as placed, in the plate's order. */
    @JvmField val objects: Array<Array<NativeModelInspection>>,
)

internal class NativeFlatteningPlanes(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** x, y, z per plane. */
    @JvmField val normals: DoubleArray,
    /** Points per plane. */
    @JvmField val vertexCounts: IntArray,
    /** x, y, z per point of every plane in turn. */
    @JvmField val vertices: FloatArray,
)

/** Constructed by the native bridge; see PresetItem in orca_engine_adapter.hpp. */
internal class NativePresetItem(
    @JvmField val name: String,
    @JvmField val label: String,
    /** PresetGroup. */
    @JvmField val group: Long,
    @JvmField val subgroup: String,
    @JvmField val selected: Boolean,
)

/** Constructed by the native bridge; see PresetState in orca_engine_adapter.hpp. */
internal class NativePresetState(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val setupRequired: Boolean,
    @JvmField val printer: String,
    @JvmField val filament: String,
    @JvmField val process: String,
    @JvmField val filamentSlots: Array<String>,
    @JvmField val filamentColors: Array<String>,
    @JvmField val filamentTypes: Array<String>,
    @JvmField val printers: Array<NativePresetItem>,
    @JvmField val filaments: Array<NativePresetItem>,
    @JvmField val processes: Array<NativePresetItem>,
    @JvmField val nozzleDiameters: Array<String>,
    @JvmField val nozzleDiameter: String,
    @JvmField val asksUnsavedChanges: Boolean,
    @JvmField val changedKind: Long,
    @JvmField val unsavedChanges: Array<NativePresetChange>,
    @JvmField val canTransfer: Boolean,
    @JvmField val saveName: String,
    @JvmField val saveNameCopySuffix: Boolean,
)

/** Constructed by the native bridge; see PresetChange in orca_engine_adapter.hpp. */
internal class NativePresetChange(
    @JvmField val id: String,
    @JvmField val category: Array<NativeUiText>,
    @JvmField val group: Array<NativeUiText>,
    @JvmField val label: Array<NativeUiText>,
    @JvmField val oldValue: Array<NativeUiText>,
    @JvmField val newValue: Array<NativeUiText>,
)

/** Constructed by the native bridge; see SetupPrinterModel in orca_engine_adapter.hpp. */
internal class NativeSetupPrinterModel(
    @JvmField val vendor: String,
    @JvmField val model: String,
    @JvmField val name: String,
    @JvmField val nozzleDiameters: Array<String>,
    @JvmField val defaultMaterials: Array<String>,
    @JvmField val cover: String,
    @JvmField val installedNozzles: Array<String>,
)

internal class NativeSetupPrinters(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val models: Array<NativeSetupPrinterModel>,
)

/** Constructed by the native bridge; see SetupFilament in orca_engine_adapter.hpp. */
internal class NativeSetupFilament(
    @JvmField val name: String,
    @JvmField val vendor: String,
    @JvmField val type: String,
    @JvmField val models: IntArray,
    @JvmField val selected: Boolean,
)

internal class NativeSetupFilaments(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val filaments: Array<NativeSetupFilament>,
)

/** Constructed by the native bridge; see UiText in orca_engine_adapter.hpp. */
internal class NativeUiText(
    @JvmField val context: String,
    @JvmField val msgid: String,
    @JvmField val msgidPlural: String,
    @JvmField val count: Long,
    @JvmField val args: Array<String>,
    @JvmField val translateArgs: Boolean,
)

/** Constructed by the native bridge; see SettingDefinition in orca_engine_adapter.hpp. */
internal class NativeSettingDefinition(
    @JvmField val key: String,
    /** ConfigOptionType. */
    @JvmField val type: Long,
    @JvmField val label: String,
    @JvmField val sidetext: String,
    /** The page of the tab the setting belongs to, which the object list names its settings by. */
    @JvmField val category: String,
    /** ConfigOptionMode. */
    @JvmField val mode: Long,
    /** ConfigOptionDef::GUIType. */
    @JvmField val guiType: Long,
    @JvmField val enumValues: Array<String>,
    @JvmField val enumLabels: Array<String>,
    @JvmField val multiline: Boolean,
    @JvmField val fullWidth: Boolean,
    @JvmField val isCode: Boolean,
    @JvmField val height: Int,
)

internal class NativeSettingDefinitions(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val settings: Array<NativeSettingDefinition>,
)

/** Constructed by the native bridge; see SettingsDialog in orca_engine_adapter.hpp. */
internal class NativeSettingsDialog(
    @JvmField val id: String,
    /** DialogIcon. */
    @JvmField val icon: Long,
    @JvmField val title: Array<NativeUiText>,
    @JvmField val text: Array<NativeUiText>,
    @JvmField val question: Boolean,
    @JvmField val yes: NativeUiText,
    @JvmField val no: NativeUiText,
)

/** Constructed by the native bridge; see SettingsLineOption in orca_engine_adapter.hpp. */
internal class NativeSettingsLineOption(
    @JvmField val id: String,
    @JvmField val key: String,
    @JvmField val index: Int,
    @JvmField val label: String,
    @JvmField val fullWidth: Boolean,
    @JvmField val isCode: Boolean,
    @JvmField val multiline: Boolean,
    @JvmField val height: Int,
    @JvmField val editCustomGcode: Boolean,
)

/** Constructed by the native bridge; see SettingsLine in orca_engine_adapter.hpp. */
internal class NativeSettingsLine(
    @JvmField val label: String,
    @JvmField val tooltip: String,
    @JvmField val separator: Boolean,
    /** SettingWidget. */
    @JvmField val widget: Long,
    @JvmField val hasOverride: Boolean,
    @JvmField val options: Array<NativeSettingsLineOption>,
)

/** Constructed by the native bridge; see SettingsGroup in orca_engine_adapter.hpp. */
internal class NativeSettingsGroup(
    @JvmField val title: String,
    @JvmField val icon: String,
    @JvmField val lines: Array<NativeSettingsLine>,
)

/** Constructed by the native bridge; see SettingsPage in orca_engine_adapter.hpp. */
internal class NativeSettingsPage(
    @JvmField val title: String,
    @JvmField val label: Array<NativeUiText>,
    @JvmField val icon: String,
    @JvmField val groups: Array<NativeSettingsGroup>,
)

/** Constructed by the native bridge; see SettingState in orca_engine_adapter.hpp. */
internal class NativeSettingState(
    @JvmField val id: String,
    @JvmField val key: String,
    @JvmField val value: String,
    @JvmField val modified: Boolean,
    @JvmField val system: Boolean,
    @JvmField val enabled: Boolean,
    @JvmField val visible: Boolean,
    @JvmField val hasChoices: Boolean,
    @JvmField val choiceValues: Array<String>,
    @JvmField val choiceLabels: Array<String>,
    @JvmField val nullable: Boolean,
    @JvmField val isNil: Boolean,
    /** The selected objects disagree on the value. */
    @JvmField val mixed: Boolean,
    @JvmField val overrideEnabled: Boolean,
    @JvmField val listValues: Array<String>,
)

/** Constructed by the native bridge; see PresetSettings in orca_engine_adapter.hpp. */
internal class NativePresetSettings(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** PresetKind. */
    @JvmField val kind: Long,
    @JvmField val preset: String,
    @JvmField val label: String,
    @JvmField val dirty: Boolean,
    @JvmField val isDefault: Boolean,
    @JvmField val isSystem: Boolean,
    @JvmField val hasParent: Boolean,
    @JvmField val canDelete: Boolean,
    /** SettingsMode. */
    @JvmField val mode: Long,
    @JvmField val pages: Array<NativeSettingsPage>,
    @JvmField val activePage: String,
    /** Tab::m_variant_combo: the extruder variants of the filament tab, and the shown one. */
    @JvmField val variants: Array<String>,
    @JvmField val variant: Long,
    @JvmField val settings: Array<NativeSettingState>,
    @JvmField val saveName: String,
    @JvmField val saveNameCopySuffix: Boolean,
    @JvmField val notices: Array<NativeSettingsDialog>,
    @JvmField val hasQuestion: Boolean,
    @JvmField val question: NativeSettingsDialog,
    @JvmField val questionLoadsSelection: Boolean,
    /** The settings of every selected object, or of the plate, after the request. */
    @JvmField val hasModelSettings: Boolean,
    @JvmField val modelSettingKeys: Array<Array<String>>,
    @JvmField val modelSettingValues: Array<Array<String>>,
)

/** Constructed by the native bridge; see PresetNames in orca_engine_adapter.hpp. */
/** Constructed by the native bridge; see PhysicalPrinterState in orca_engine_adapter.hpp. */
internal class NativePhysicalPrinter(
    @JvmField val name: String,
    @JvmField val presetNames: Array<String>,
    @JvmField val keys: Array<String>,
    @JvmField val values: Array<String>,
)

internal class NativePhysicalPrinters(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val printers: Array<NativePhysicalPrinter>,
)

/** Constructed by the native bridge; see CreateFilamentOptions in orca_engine_adapter.hpp. */
internal class NativeCreateFilamentOptions(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val vendors: Array<String>,
    @JvmField val types: Array<String>,
    @JvmField val baseFilaments: Array<String>,
    /** The presets of the chosen filament: the printer of each, and its name. */
    @JvmField val presetPrinters: Array<String>,
    @JvmField val presetNames: Array<String>,
    /** Every preset of the chosen type, the same way. */
    @JvmField val copyPrinters: Array<String>,
    @JvmField val copyNames: Array<String>,
)

/** Constructed by the native bridge; see CreatePrinterOptions in orca_engine_adapter.hpp. */
internal class NativeCreatePrinterOptions(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val vendors: Array<String>,
    @JvmField val models: Array<String>,
    @JvmField val nozzleDiameters: Array<String>,
    @JvmField val presetVendors: Array<String>,
    @JvmField val printerPresets: Array<String>,
    @JvmField val filamentPresets: Array<String>,
    @JvmField val processPresets: Array<String>,
    /** x and y of every point of the printable area. */
    @JvmField val printableArea: DoubleArray,
    @JvmField val maxPrintHeight: Double,
)

/** Constructed by the native bridge; see PresetCreation in orca_engine_adapter.hpp. */
internal class NativePresetCreation(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val hasQuestion: Boolean,
    @JvmField val question: NativeSettingsDialog,
    @JvmField val name: String,
)

/** Constructed by the native bridge; see CustomFilaments in orca_engine_adapter.hpp. */
internal class NativeCustomFilaments(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val ids: Array<String>,
    @JvmField val names: Array<String>,
)

/** Constructed by the native bridge; see FilamentPresetList in orca_engine_adapter.hpp. */
internal class NativeFilamentPresets(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val name: String,
    @JvmField val vendor: String,
    @JvmField val type: String,
    @JvmField val serial: String,
    @JvmField val printers: Array<String>,
    @JvmField val presets: Array<String>,
)

/** Constructed by the native bridge; see ConfigExportOptions in orca_engine_adapter.hpp. */
internal class NativeConfigExportOptions(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** The name of every entry, with how many presets it carries. */
    @JvmField val names: Array<String>,
    @JvmField val counts: LongArray,
    @JvmField val note: String,
)

/** Constructed by the native bridge; see ConfigTransfer in orca_engine_adapter.hpp. */
internal class NativeConfigTransfer(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val names: Array<String>,
    /** The preset the import waits for an answer about; empty when it does not. */
    @JvmField val overwritePreset: String,
)

/** Constructed by the native bridge; see PresetKindComparison in orca_engine_adapter.hpp. */
internal class NativePresetKindComparison(
    /** PresetKind. */
    @JvmField val kind: Long,
    @JvmField val leftPresets: Array<NativePresetItem>,
    @JvmField val rightPresets: Array<NativePresetItem>,
    @JvmField val left: String,
    @JvmField val right: String,
    @JvmField val problem: String,
    @JvmField val changes: Array<NativePresetChange>,
    @JvmField val edited: String,
    @JvmField val editedDirty: Boolean,
)

/** Constructed by the native bridge; see PresetComparison in orca_engine_adapter.hpp. */
internal class NativePresetComparison(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val kinds: Array<NativePresetKindComparison>,
)

/** Constructed by the native bridge; see SearchOption in orca_engine_adapter.hpp. */
internal class NativeSearchOption(
    @JvmField val kind: Long,
    @JvmField val key: String,
    @JvmField val id: String,
    @JvmField val page: Array<NativeUiText>,
    @JvmField val group: Array<NativeUiText>,
    @JvmField val label: Array<NativeUiText>,
    @JvmField val mode: Long,
)

internal class NativeSearchCatalog(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val options: Array<NativeSearchOption>,
)

/** Constructed by the native bridge; see ThumbnailSizes in orca_engine_adapter.hpp. */
internal class NativeThumbnailSizes(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val sizes: IntArray,
)

/** Constructed by the native bridge; see GcodePlaceholder in orca_engine_adapter.hpp. */
internal class NativeGcodePlaceholder(
    @JvmField val parent: Int,
    /** GcodePlaceholderType. */
    @JvmField val type: Long,
    @JvmField val label: Array<NativeUiText>,
    @JvmField val key: String,
    @JvmField val text: String,
    @JvmField val icon: String,
    @JvmField val expanded: Boolean,
)

internal class NativeGcodePlaceholders(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val value: String,
    @JvmField val placeholders: Array<NativeGcodePlaceholder>,
)

/** Constructed by the native bridge; see GcodePlaceholderInfo in orca_engine_adapter.hpp. */
internal class NativeGcodePlaceholderInfo(
    @JvmField val label: Array<NativeUiText>,
    @JvmField val type: String,
    @JvmField val description: Array<NativeUiText>,
    @JvmField val undefined: Boolean,
)

/** Constructed by the native bridge; see BedShapeState in orca_engine_adapter.hpp. */
internal class NativeBedShape(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val kind: Long,
    @JvmField val sizeX: Double,
    @JvmField val sizeY: Double,
    @JvmField val originX: Double,
    @JvmField val originY: Double,
    @JvmField val diameter: Double,
    @JvmField val texture: String,
    @JvmField val model: String,
    @JvmField val points: DoubleArray,
)

internal class NativePresetNames(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val names: Array<String>,
)

/** Constructed by the native bridge; see PresetNameValidation in orca_engine_adapter.hpp. */
internal class NativePresetNameValidation(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** PresetNameCheck. */
    @JvmField val check: Long,
    @JvmField val info: Array<NativeUiText>,
)

/** SceneStatus in orca_engine_adapter.hpp. */
internal object NativeSceneStatus {
    const val SUCCESS = 0L
}

/** PresetGroup in orca_engine_adapter.hpp. */
internal object NativePresetGroup {
    const val USER = 0L
    const val BUNDLE = 1L
}

/** PresetChoice in orca_engine_adapter.hpp. */
internal object NativePresetChoice {
    const val PRINTER = 0L
    const val PRINTER_MODEL = 1L
    const val NOZZLE_DIAMETER = 2L
    const val FILAMENT = 3L
    const val PROCESS = 4L
}

/** PresetChangeAction in orca_engine_adapter.hpp. */
internal object NativePresetChangeAction {
    const val ASK = 0L
    const val TRANSFER = 1L
    const val DISCARD = 2L
}

/** VolumeState in orca_engine_adapter.hpp. */
internal object NativeVolumeState {
    const val INSIDE = 0L
    const val PARTLY_OUTSIDE = 1L
}

internal object NativeBindings {
    init {
        System.loadLibrary("orcinus_engine")
    }

    external fun engineVersion(): String

    /** Returns null when the engine is ready, otherwise the reason it is not. */
    external fun initialize(dataDir: String, resourcesDir: String, temporaryDir: String): String?

    /**
     * Slices the objects of the plate, given per object: [modelPaths] (empty for
     * the built-in 20 mm calibration cube), 16 [placements] elements of its
     * instance transformation, column-major 4 x 4, and [autoDrops].
     */
    external fun slice(
        jobId: String,
        modelPaths: Array<String>,
        /** The number of copies of every object, whose placements follow in turn. */
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        /** ModelInstance::printable of every copy. */
        instancePrintable: BooleanArray,
        /** The settings of every object, and of the plate, in the plate's order. */
        objectSettingKeys: Array<Array<String>>,
        objectSettingValues: Array<Array<String>>,
        /** The parts of every object: how many each has, then their shapes, types and transformations. */
        partCounts: IntArray,
        partShapes: Array<String>,
        partTypes: LongArray,
        partMatrices: DoubleArray,
        partSettingKeys: Array<Array<String>>,
        partSettingValues: Array<Array<String>>,
        rangeCounts: IntArray,
        rangeHeights: DoubleArray,
        rangeSettingKeys: Array<Array<String>>,
        rangeSettingValues: Array<Array<String>>,
        /** The facets painted with the filaments of the plate, per object and per part. */
        painted: Array<String>,
        partPainted: Array<String>,
        plateSettingKeys: Array<String>,
        plateSettingValues: Array<String>,
        outputPath: String,
        /** Where the toolpaths for libvgcode go; null writes none. */
        toolpathsPath: String?,
        /** Where the wipe tower the slice builds goes for the 3D view; null writes none. */
        wipeTowerPath: String?,
        /** The thumbnails the app rendered: width and height of each, and its file. */
        thumbnailSizes: IntArray,
        thumbnailPaths: Array<String>,
        printerProfile: String,
        filamentProfile: String,
        /** Every filament of the plate, in the order the sidebar lists them. */
        filamentProfiles: Array<String>,
        processProfile: String,
        progressListener: NativeProgressListener,
    ): NativeSliceResult

    /** The thumbnails the G-code of the printer holds: width and height of each. */
    external fun thumbnailSizes(
        printerProfile: String,
        filamentProfile: String,
        filamentProfiles: Array<String>,
        processProfile: String,
    ): NativeThumbnailSizes

    /** The wipe tower of the plate, as the desktop canvas draws it. */
    external fun describeWipeTower(
        modelPaths: Array<String>,
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        instancePrintable: BooleanArray,
        objectSettingKeys: Array<Array<String>>,
        objectSettingValues: Array<Array<String>>,
        partCounts: IntArray,
        partShapes: Array<String>,
        partTypes: LongArray,
        partMatrices: DoubleArray,
        partSettingKeys: Array<Array<String>>,
        partSettingValues: Array<Array<String>>,
        rangeCounts: IntArray,
        rangeHeights: DoubleArray,
        rangeSettingKeys: Array<Array<String>>,
        rangeSettingValues: Array<Array<String>>,
        plateSettingKeys: Array<String>,
        plateSettingValues: Array<String>,
        printerProfile: String,
        filamentProfile: String,
        filamentProfiles: Array<String>,
        processProfile: String,
    ): NativeWipeTower

    /** The project's flushing volumes after a change of the filaments; [change] is FlushVolumesChange. */
    external fun updateFlushVolumes(
        modelPaths: Array<String>,
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        instancePrintable: BooleanArray,
        objectSettingKeys: Array<Array<String>>,
        objectSettingValues: Array<Array<String>>,
        partCounts: IntArray,
        partShapes: Array<String>,
        partTypes: LongArray,
        partMatrices: DoubleArray,
        partSettingKeys: Array<Array<String>>,
        partSettingValues: Array<Array<String>>,
        rangeCounts: IntArray,
        rangeHeights: DoubleArray,
        rangeSettingKeys: Array<Array<String>>,
        rangeSettingValues: Array<Array<String>>,
        plateSettingKeys: Array<String>,
        plateSettingValues: Array<String>,
        printerProfile: String,
        filamentProfile: String,
        filamentProfiles: Array<String>,
        processProfile: String,
        change: Long,
        index: Long,
    ): NativeFlushVolumes

    /** The flushing volumes of the plate (WipingDialog). */
    external fun describeFlushVolumes(
        modelPaths: Array<String>,
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        instancePrintable: BooleanArray,
        objectSettingKeys: Array<Array<String>>,
        objectSettingValues: Array<Array<String>>,
        partCounts: IntArray,
        partShapes: Array<String>,
        partTypes: LongArray,
        partMatrices: DoubleArray,
        partSettingKeys: Array<Array<String>>,
        partSettingValues: Array<Array<String>>,
        rangeCounts: IntArray,
        rangeHeights: DoubleArray,
        rangeSettingKeys: Array<Array<String>>,
        rangeSettingValues: Array<Array<String>>,
        plateSettingKeys: Array<String>,
        plateSettingValues: Array<String>,
        printerProfile: String,
        filamentProfile: String,
        filamentProfiles: Array<String>,
        processProfile: String,
    ): NativeFlushVolumes

    /** GLGizmoMmuSegmentation: opens the painting tool on an object or one of its parts. */
    external fun beginPainting(
        modelPath: String,
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        partCounts: IntArray,
        partShapes: Array<String>,
        partTypes: LongArray,
        partMatrices: DoubleArray,
        /** The part to paint; -1 paints the object's own mesh. */
        part: Int,
        printerProfile: String,
        filamentProfile: String,
        filamentProfiles: Array<String>,
        processProfile: String,
        facets: String,
        meshPrefix: String,
    ): NativePainting

    /** One touch of the finger on the model being painted. */
    external fun paintStroke(
        origin: DoubleArray,
        direction: DoubleArray,
        filament: Int,
        radius: Double,
        tool: Long,
        angle: Double,
        meshPrefix: String,
    ): NativePainting

    external fun endPainting(): NativePainting

    external fun cancel(jobId: String): Boolean

    external fun describePlate(
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        outputDirectory: String,
    ): NativePlateDescription

    /**
     * An empty [modelPath] inspects the built-in 20 mm calibration cube. The
     * objects already on the plate are given as for [slice].
     */
    external fun inspectModel(
        modelPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        meshPath: String,
        plateModelPaths: Array<String>,
        plateInstanceCounts: IntArray,
        platePlacements: DoubleArray,
        plateAutoDrops: BooleanArray,
    ): NativeModelInspection

    /**
     * Commits [manipulation] (Manipulation in orca_engine_adapter.hpp) of the object of [modelPath]
     * (empty for the cube) from [previousPlacement] to [placement], column-major 4 x 4.
     */
    external fun placeModel(
        modelPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        previousPlacement: DoubleArray,
        placement: DoubleArray,
        autoDrop: Boolean,
        manipulation: Long,
        /** For lay_on_face: x, y, z in object coordinates. */
        faceNormal: DoubleArray?,
    ): NativeModelInspection

    /**
     * Commits [manipulation] (PlateManipulation in orca_engine_adapter.hpp) of
     * the objects of the plate, given as for [slice], with a [selected] flag per
     * object for auto orient.
     */
    external fun placeObjects(
        modelPaths: Array<String>,
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        selected: BooleanArray,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        manipulation: Long,
        /** ArrangeSettings for arrange. */
        arrangeDistance: Double,
        arrangeEnableRotation: Boolean,
        arrangeAllowMultiMaterials: Boolean,
        arrangeAlignToYAxis: Boolean,
    ): NativePlateInspection

    /** ObjectList::load_generic_subobject(): a shape added to the object as a part. */
    external fun addObjectPart(
        modelPath: String,
        instanceCounts: IntArray,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        shape: String,
        type: Long,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        meshPath: String,
    ): NativeModelInspection

    external fun describeFlatteningPlanes(
        modelPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        placement: DoubleArray,
    ): NativeFlatteningPlanes

    external fun describePresets(): NativePresetState

    /** [choice]: NativePresetChoice. */
    external fun selectPreset(choice: Long, value: String, action: Long): NativePresetState

    /** Sidebar::add_custom_filament(), delete_filament() and the combo box of a slot. */
    external fun addFilament(): NativePresetState

    external fun removeFilament(index: Long): NativePresetState

    external fun selectFilament(index: Long, name: String, action: Long): NativePresetState

    external fun setFilamentColor(index: Long, color: String): NativePresetState

    external fun describeSetupPrinters(): NativeSetupPrinters

    /** For the printer model ids [models]. */
    external fun describeSetupFilaments(models: Array<String>): NativeSetupFilaments

    external fun applySetup(models: Array<String>, filaments: Array<String>): NativePresetState

    external fun applyDefaultSetup(): NativePresetState

    /** [kind]: PresetKind in orca_engine_adapter.hpp. */
    external fun describeSettingDefinitions(kind: Long): NativeSettingDefinitions

    /**
     * The tab with [page] shown; the page it shows already when [page] is
     * empty. The answers to a change's questions come as parallel arrays of
     * dialog ids and Yes flags.
     */
    /**
     * The settings tab of [kind]. For the settings of an object or of the plate
     * the request carries their overrides, since the engine holds no plate:
     * [modelKeys] and [modelValues] are the ones the tab edits, [plateKeys] and
     * [plateValues] the ones of the plate, and [parentKeys] and [parentValues] the
     * ones of the object a part belongs to. A preset tab passes empty arrays.
     */
    external fun describeSettings(
        kind: Long,
        page: String,
        answerIds: Array<String>,
        answers: BooleanArray,
        modelKeys: Array<Array<String>>,
        modelValues: Array<Array<String>>,
        plateKeys: Array<String>,
        plateValues: Array<String>,
        parentKeys: Array<String>,
        parentValues: Array<String>,
    ): NativePresetSettings

    external fun changeSetting(
        kind: Long,
        page: String,
        id: String,
        text: String,
        answerIds: Array<String>,
        answers: BooleanArray,
        modelKeys: Array<Array<String>>,
        modelValues: Array<Array<String>>,
        plateKeys: Array<String>,
        plateValues: Array<String>,
        parentKeys: Array<String>,
        parentValues: Array<String>,
    ): NativePresetSettings

    /** Every modified setting when [ids] is empty. */
    external fun resetSettings(
        kind: Long,
        page: String,
        ids: Array<String>,
        answerIds: Array<String>,
        answers: BooleanArray,
        modelKeys: Array<Array<String>>,
        modelValues: Array<Array<String>>,
        plateKeys: Array<String>,
        plateValues: Array<String>,
        parentKeys: Array<String>,
        parentValues: Array<String>,
    ): NativePresetSettings

    /** The check box of a filament override. */
    external fun setSettingOverride(
        kind: Long,
        page: String,
        id: String,
        enabled: Boolean,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetSettings

    /** The presets a preset is compatible with; none for every preset. */
    external fun setCompatiblePresets(
        kind: Long,
        page: String,
        key: String,
        presets: Array<String>,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetSettings

    /** PhysicalPrinterCollection: the printers the app can send G-code to. */
    external fun physicalPrinters(): NativePhysicalPrinters

    external fun savePhysicalPrinter(
        name: String,
        presetNames: Array<String>,
        keys: Array<String>,
        values: Array<String>,
        renamedFrom: String?,
    ): NativePhysicalPrinters

    external fun deletePhysicalPrinter(name: String): NativePhysicalPrinters

    /**
     * PresetBundle::import_presets(): the user presets the files hold, with
     * what the user answered about every preset it would replace.
     */
    external fun importPresets(paths: Array<String>, answerPresets: Array<String>, answers: LongArray): NativeConfigTransfer

    /** CreateFilamentPresetDialog: the vendors, types and presets it offers. */
    external fun createFilamentOptions(type: String, baseFilament: String): NativeCreateFilamentOptions

    /** Its Create button: a filament of the user's own for every chosen preset. */
    external fun createFilament(
        vendor: String,
        customVendor: Boolean,
        type: String,
        serial: String,
        printers: Array<String>,
        presets: Array<String>,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetCreation

    /** CreatePrinterPresetDialog: what its two pages offer. */
    external fun createPrinterOptions(
        vendor: String,
        nozzle: String,
        presetVendor: String,
        printerPreset: String,
    ): NativeCreatePrinterOptions

    /** Its Create button: a printer of the user's own with the presets it prints with. */
    external fun createPrinter(
        model: String,
        nozzle: String,
        printableArea: DoubleArray,
        maxPrintHeight: Double,
        customTexture: String,
        customModel: String,
        presetVendor: String,
        printerPreset: String,
        filamentPresets: Array<String>,
        processPresets: Array<String>,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetCreation

    /** The filaments of the user's own, which the app lists to edit. */
    external fun customFilaments(): NativeCustomFilaments

    /** EditFilamentPresetDialog: what it shows for one of them. */
    external fun filamentPresets(filamentId: String): NativeFilamentPresets

    /** Its Delete button. */
    external fun deleteFilamentPreset(preset: String, answerIds: Array<String>, answers: BooleanArray): NativePresetCreation

    /** ExportConfigsDialog: what it offers for an export kind. */
    external fun configExportOptions(kind: Long): NativeConfigExportOptions

    /** Its OK: the chosen entries are written into [directory]. */
    external fun exportConfigs(kind: Long, names: Array<String>, directory: String): NativeConfigTransfer

    /**
     * DiffPresetDialog: the presets each side selects (the printer, the process
     * and the filament, in that order), and what they differ in.
     */
    external fun comparePresets(left: Array<String>, right: Array<String>, showAll: Boolean): NativePresetComparison

    /** Tab::transfer_options(): the values of [options] move from one preset to another. */
    external fun transferPresetOptions(kind: Long, from: String, to: String, options: Array<String>): NativePresetState

    /** Search::OptionsSearcher: every setting the preset tabs show. */
    external fun searchCatalog(): NativeSearchCatalog

    /** EditGCodeDialog: the G-code of [key] on the tab of [kind], and the placeholders it lists. */
    external fun describeGcodePlaceholders(kind: Long, key: String): NativeGcodePlaceholders

    /** EditGCodeDialog::selection_changed(): what the dialog says about a placeholder. */
    external fun describeGcodePlaceholder(key: String, presets: Boolean): NativeGcodePlaceholderInfo

    /** Tab::edit_custom_gcode() once the dialog is closed with OK. */
    external fun editCustomGcode(
        kind: Long,
        page: String,
        key: String,
        value: String,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetSettings

    /** RammingDialog closed with OK: filament_ramming_parameters becomes [parameters]. */
    external fun setRammingParameters(
        kind: Long,
        page: String,
        parameters: String,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetSettings

    /** BedShapeDialog: the shape of the printable area of the edited printer. */
    external fun describeBedShape(): NativeBedShape

    /** [kind]: BedShapeKind in orca_engine_adapter.hpp. */
    external fun setBedShape(
        kind: Long,
        sizeX: Double,
        sizeY: Double,
        originX: Double,
        originY: Double,
        diameter: Double,
        customPath: String?,
        texture: String?,
        model: String?,
        answerIds: Array<String>,
        answers: BooleanArray,
    ): NativePresetSettings

    /** The names the list of compatible presets offers. */
    external fun compatiblePresetChoices(kind: Long, key: String): NativePresetNames

    /** [mode]: SettingsMode in orca_engine_adapter.hpp. */
    external fun setSettingsMode(
        kind: Long,
        mode: Long,
        modelKeys: Array<Array<String>>,
        modelValues: Array<Array<String>>,
        plateKeys: Array<String>,
        plateValues: Array<String>,
        parentKeys: Array<String>,
        parentValues: Array<String>,
    ): NativePresetSettings

    /** Tab::m_variant_combo: the tab shows the values of another extruder variant. */
    external fun setSettingsVariant(
        kind: Long,
        page: String,
        variant: Long,
        answerIds: Array<String>,
        answers: BooleanArray,
        modelKeys: Array<Array<String>>,
        modelValues: Array<Array<String>>,
        plateKeys: Array<String>,
        plateValues: Array<String>,
        parentKeys: Array<String>,
        parentValues: Array<String>,
    ): NativePresetSettings

    external fun settingTooltip(kind: Long, id: String): Array<NativeUiText>

    external fun checkPresetName(kind: Long, name: String): NativePresetNameValidation

    external fun savePreset(kind: Long, name: String): NativePresetSettings

    external fun deletePreset(kind: Long, answerIds: Array<String>, answers: BooleanArray): NativePresetSettings
}
