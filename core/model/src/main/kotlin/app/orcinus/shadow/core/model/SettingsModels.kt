package app.orcinus.shadow.core.model

/**
 * What OrcaSlicer's settings tabs edit: its presets, and the settings an object
 * or the plate overrides the process preset with.
 */
enum class PresetKind {
    PRINT,
    FILAMENT,
    PRINTER,

    /** TabPrintObject: the process settings of one object on the plate. */
    OBJECT,

    /** TabPrintPlate: the settings of the plate. */
    PLATE,

    /** TabPrintPart: the settings of one part of an object. */
    PART,

    /** TabPrintLayer: the settings of one height range of an object. */
    LAYER;

    /** Whether the tab edits an object, a part or range of it, or the plate instead of a preset. */
    val isModel: Boolean get() = this != PRINT && this != FILAMENT && this != PRINTER
}

/**
 * The settings an object or the plate overrides the process preset with, as
 * OrcaSlicer keeps them in a ModelConfig: the keys the user set, with their
 * values the way OrcaSlicer writes them into a project.
 */
@JvmInline
value class ModelSettings(val values: Map<String, String> = emptyMap()) {
    val isEmpty: Boolean get() = values.isEmpty()

    /**
     * ObjectDataViewModel::GetExtruderNumber(): the filament the item prints
     * with, where 0 is the "default" the desktop list shows for an item that
     * follows what it belongs to.
     */
    val extruderNumber: Int get() = values["extruder"]?.toIntOrNull() ?: 0
}

sealed interface ModelSettingsOutcome {
    data class Success(val settings: ModelSettings) : ModelSettingsOutcome

    data class Failure(val message: String) : ModelSettingsOutcome
}

/**
 * What a request of an object's or the plate's settings carries, since the
 * engine keeps no plate of its own: the overrides of the object or plate the
 * tab edits, the ones of the plate, which an object's settings follow, and, for
 * a part, the ones of the object it belongs to, which its own sit on.
 */
data class ModelSettingsRequest(
    /** One per object the list has selected, in the plate's order; the plate's settings are a single entry. */
    val settings: List<ModelSettings> = emptyList(),
    val plate: ModelSettings = ModelSettings(),
    val parent: ModelSettings = ModelSettings(),
)

/**
 * One setting the search can find (Search::Option): the tab it is on, the page
 * and the group it sits in, and its label. The texts are OrcaSlicer's, so the
 * app translates them before it matches a query against them.
 */
data class SearchOption(
    val kind: PresetKind,
    val key: String,
    /** The id of its field, which the tab shows the setting under. */
    val id: String,
    val page: List<OrcaText>,
    val group: List<OrcaText>,
    val label: List<OrcaText>,
    val mode: SettingsMode,
)

/** ParamType of OrcaSlicer's EditGCodeDialog: how a placeholder is written into G-code. */
enum class GcodePlaceholderType {
    /** A group of placeholders. */
    GROUP,

    /** "key" */
    SCALAR,

    /** "key[]", with the index to fill in. */
    VECTOR,

    /** "key[current_extruder]" */
    FILAMENT_VECTOR,
}

/**
 * A node of EditGCodeDialog's list of placeholders: a group, a subgroup, or a
 * placeholder, in the order the dialog lists them.
 */
data class GcodePlaceholder(
    /** The group the node is in, as an index of the list; -1 for a group. */
    val parent: Int,
    val type: GcodePlaceholderType,
    /** The name of a group or subgroup. */
    val label: List<OrcaText>,
    /** The setting a placeholder stands for. */
    val key: String,
    /** What the list shows for a placeholder, which the dialog writes into the G-code. */
    val text: String,
    /** An icon name of OrcaSlicer's resources/images. */
    val icon: String,
    /** The dialog opens with the group expanded. */
    val expanded: Boolean,
)

/** What EditGCodeDialog opens with: the tab's G-code, and the placeholders it lists. */
data class GcodePlaceholders(
    val value: String,
    val placeholders: List<GcodePlaceholder>,
)

sealed interface GcodePlaceholdersOutcome {
    data class Success(val placeholders: GcodePlaceholders) : GcodePlaceholdersOutcome

    data class Failure(val message: String) : GcodePlaceholdersOutcome
}

/** What EditGCodeDialog says about the placeholder it has selected. */
data class GcodePlaceholderInfo(
    /** The label of its definition; empty when it has none, and the key stands for it. */
    val label: List<OrcaText>,
    /** The type of its value ("float", "integer[]"), which the dialog does not translate. */
    val type: String,
    val description: List<OrcaText>,
    /** No definition was found. */
    val undefined: Boolean,
)

sealed interface SearchCatalogOutcome {
    data class Success(val options: List<SearchOption>) : SearchCatalogOutcome

    data class Failure(val message: String) : SearchCatalogOutcome
}

/**
 * One line of the search results (Search::FoundOption): the setting, the text
 * the list shows ("Page : Group : Label" as OptionsSearcher builds it), and
 * where the query matched it, which the list marks.
 */
data class SearchResult(
    val option: SearchOption,
    val text: String,
    val matches: List<Int>,
    val score: Int,
)

/** BedShape::PageType: the shape the printable area of a printer has. */
enum class BedShapeKind { RECTANGLE, CIRCLE, CUSTOM }

/**
 * The printable area of the edited printer as OrcaSlicer's BedShapeDialog shows
 * it. Every shape but a circle is described by the bounding box of the area and
 * the distance of the G-code origin from its front left corner.
 */
data class BedShape(
    val kind: BedShapeKind = BedShapeKind.RECTANGLE,
    val sizeX: Double = 0.0,
    val sizeY: Double = 0.0,
    val originX: Double = 0.0,
    val originY: Double = 0.0,
    val diameter: Double = 0.0,
    /** bed_custom_texture and bed_custom_model of the printer preset. */
    val texture: String = "",
    val model: String = "",
    /** The points of the area, which a custom shape is drawn from. */
    val points: List<Point2> = emptyList(),
)

sealed interface BedShapeOutcome {
    data class Success(val shape: BedShape) : BedShapeOutcome

    data class Failure(val message: String) : BedShapeOutcome
}

/** Which settings OrcaSlicer's tabs show (ConfigOptionMode), from the fewest to the most. */
enum class SettingsMode {
    SIMPLE,
    ADVANCED,
    EXPERT,
    DEVELOP,
}

/**
 * A text of OrcaSlicer, translated with its catalogue as its _() does: [msgid]
 * with [context], or for [count] its plural [msgidPlural]; then its placeholders
 * (%s, %d, %.3f, %1%) are filled with [args], translated first when
 * [translateArgs] is set. A text OrcaSlicer shows untranslated is not in the catalogue.
 */
data class OrcaText(
    val msgid: String,
    val args: List<String> = emptyList(),
    val translateArgs: Boolean = false,
    val context: String = "",
    val msgidPlural: String = "",
    val count: Long = 0,
)

/** The value types of OrcaSlicer's settings (ConfigOptionType). */
enum class SettingType {
    NONE,
    FLOAT,
    FLOATS,
    INT,
    INTS,
    STRING,
    STRINGS,
    PERCENT,
    PERCENTS,
    FLOAT_OR_PERCENT,
    FLOATS_OR_PERCENTS,
    POINT,
    POINTS,
    POINT3,
    BOOL,
    BOOLS,
    ENUM,
    ENUMS,
    OTHER,
}

/** The control a setting's field uses in place of its type's (ConfigOptionDef::GUIType). */
enum class SettingControl {
    DEFAULT,

    /** A combo box of integers that accepts other integers. */
    INT_ENUM_OPEN,

    /** A combo box of numbers that accepts other numbers. */
    FLOAT_ENUM_OPEN,
    COLOR,
    SELECT_OPEN,
    SLIDER,
    LEGEND,
    ONE_STRING,
}

/**
 * A setting as OrcaSlicer defines it (ConfigOptionDef), with what its field
 * needs to draw it. Texts are OrcaSlicer's msgids. The rest of the definition
 * stays in the engine, which checks values, tells the field what to show, and
 * writes the tooltip (settingTooltip): a tab's settings cross two processes, so
 * what the app never reads is not sent.
 */
data class SettingDefinition(
    val key: String,
    val type: SettingType,
    val label: String,
    /** A unit, usually. */
    val sidetext: String,
    /** The page of the tab the setting belongs to, which the object list names its settings by. */
    val category: String,
    val mode: SettingsMode,
    val control: SettingControl,
    val enumValues: List<String>,
    val enumLabels: List<String>,
    val multiline: Boolean,
    val fullWidth: Boolean,
    val isCode: Boolean,
    /** In lines of text; -1 for the default. */
    val height: Int,
)

/** What a line of a settings tab shows in place of the fields of its options. */
enum class SettingWidget {
    NONE,

    /** The shape of the printable area (BedShapeDialog). */
    BED_SHAPE,

    /** The printers the preset is for. */
    COMPATIBLE_PRINTERS,

    /** The processes the preset is for. */
    COMPATIBLE_PRINTS,

    /** The ramming parameters of a filament (RammingDialog). */
    RAMMING,
}

/** An option of a line of a settings tab, as the tab lays it out. */
data class SettingsLineOption(
    /** The option's id: its key, and for one value of a vector setting its index ("retraction_length#0"). */
    val id: String,
    val key: String,
    /** -1 for a setting the line shows whole. */
    val index: Int = -1,
    /** The definition's label, or the one the tab gave the option. */
    val label: String = "",
    val fullWidth: Boolean = false,
    val isCode: Boolean = false,
    val multiline: Boolean = false,
    /** In lines of text; -1 for the default. */
    val height: Int = -1,
    /** The field offers the custom G-code editor (EditGCodeDialog). */
    val editCustomGcode: Boolean = false,
)

/** A line of an option group, as OrcaSlicer's Tab appends it. */
data class SettingsLine(
    /** The line's label; for a line of one option its label. */
    val label: String = "",
    val tooltip: String = "",
    /** append_separator(): a line without options. */
    val separator: Boolean = false,
    val widget: SettingWidget = SettingWidget.NONE,
    /** A filament override: the check box before the label switches it on. */
    val hasOverride: Boolean = false,
    val options: List<SettingsLineOption> = emptyList(),
)

data class SettingsGroup(
    val title: String,
    /** An icon name of OrcaSlicer's resources/images. */
    val icon: String,
    val lines: List<SettingsLine>,
)

data class SettingsPage(
    /** The page's name, which requests name it by. */
    val title: String,
    /** What the tab shows for it (Tab::translate_category). */
    val label: List<OrcaText>,
    val icon: String,
    val groups: List<SettingsGroup>,
)

/** A settings tab: the definitions of the settings it can show, by key. */
data class SettingsTab(
    val kind: PresetKind,
    val definitions: Map<String, SettingDefinition>,
)

sealed interface SettingsTabOutcome {
    data class Success(val tab: SettingsTab) : SettingsTabOutcome

    data class Failure(val message: String) : SettingsTabOutcome
}

/** How a message box of OrcaSlicer looks. */
enum class DialogIcon {
    INFO,
    WARNING,
    ERROR,
    QUESTION,
}

/** A message box OrcaSlicer shows while it applies a change. */
data class SettingsDialog(
    /** The check that shows it; an answer to a question names it. */
    val id: String,
    val icon: DialogIcon,
    /** The caption's parts; none for the app's own caption. */
    val title: List<OrcaText>,
    val text: List<OrcaText>,
    /** Asks Yes or No; otherwise it only informs. */
    val question: Boolean,
    /** Labels of the buttons, when the box has its own. */
    val yes: OrcaText?,
    val no: OrcaText?,
)

/** An entry of a combo box whose entries the tab sets. */
data class SettingChoice(
    val value: String,
    val label: String,
)

/** A setting of the edited preset as its field shows it. */
data class SettingState(
    /** The option's id, as the lines of the pages name it. */
    val id: String,
    val key: String,
    /** The field's text: "0.2", "15%", "1" or "0" for a check box, the enum key ("grid") for a combo box. */
    val value: String,
    /** Differs from the saved preset: the field offers to undo the change. */
    val modified: Boolean,
    /** Equals the preset the edited one inherits from. */
    val system: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    /** The entries the tab gives the combo box in place of the definition's; null for the definition's. */
    val choices: List<SettingChoice>? = null,
    /** A filament override: the value may be left to the printer or the process. */
    val nullable: Boolean = false,
    /** The override is not set: [value] is the value of the preset it would override. */
    val isNil: Boolean = false,
    /** The selected objects disagree on the value, so the field shows none; a change applies to all of them. */
    val mixed: Boolean = false,
    /** Whether the override can be switched on at all. */
    val overrideEnabled: Boolean = true,
    /** The presets of compatible_printers and compatible_prints; empty for every preset. */
    val listValues: List<String> = emptyList(),
)

/** The edited preset of a kind as OrcaSlicer's settings tab shows it. */
data class PresetSettings(
    val kind: PresetKind,
    val preset: String,
    /** What the preset combo box shows: the name, after "* " when modified. */
    val label: String,
    val dirty: Boolean,
    val isDefault: Boolean,
    val isSystem: Boolean,
    /** The preset inherits from another one, whose values count as system values. */
    val hasParent: Boolean,
    /** A user preset, which can be deleted. */
    val canDelete: Boolean,
    val mode: SettingsMode,
    /** The pages the tab lays out for this preset. */
    val pages: List<SettingsPage>,
    /** The page whose fields the tab toggled. */
    val activePage: String,
    /**
     * Tab::m_variant_combo: the extruder variants the filament tab can show
     * ("drive: nozzle") and the one it shows. The desktop switch appears only
     * with more than one variant, so a shorter list means no switch.
     */
    val variants: List<String> = emptyList(),
    val variant: Int = 0,
    val settings: List<SettingState>,
    /**
     * The name the save dialog suggests: [saveName], followed by " - " and the
     * translation of "Copy" (context "PresetName") when [saveNameCopySuffix] is set.
     */
    val saveName: String,
    val saveNameCopySuffix: Boolean,
    /**
     * The settings of the objects or of the plate the tab edits, after the
     * request, in the order the request carried them; null for a preset tab.
     */
    val modelSettings: List<ModelSettings>? = null,
)

sealed interface PresetSettingsOutcome {
    /** Applied; the message boxes the change showed only informed. */
    data class Success(val settings: PresetSettings, val notices: List<SettingsDialog>) : PresetSettingsOutcome

    /**
     * Nothing was applied: the change is requested again with the answer to
     * [question]. With [loadsSelection], loading the selected preset asked it,
     * and the settings are described again with the answer instead.
     */
    data class Question(val question: SettingsDialog, val loadsSelection: Boolean) : PresetSettingsOutcome

    data class Failure(val message: String) : PresetSettingsOutcome
}

/** Whether a preset can be saved under a name (SavePresetDialog). */
enum class PresetNameCheck {
    VALID,

    /** A preset of that name exists and would be overwritten. */
    WARNING,
    INVALID,
}

data class PresetNameValidation(
    val check: PresetNameCheck,
    /** Why, when the name is not simply valid. */
    val info: List<OrcaText>,
)

sealed interface PresetNameOutcome {
    data class Success(val validation: PresetNameValidation) : PresetNameOutcome

    data class Failure(val message: String) : PresetNameOutcome
}

sealed interface PresetNamesOutcome {
    data class Success(val names: List<String>) : PresetNamesOutcome

    data class Failure(val message: String) : PresetNamesOutcome
}

/**
 * A value the edited preset changed, as OrcaSlicer's UnsavedChangesDialog lists
 * it: where its setting sits in the tab, and the value before and after.
 */
data class PresetChange(
    /** The option's id, as the tab's lines name it ("retraction_length#0"). */
    val id: String,
    /** The page of the tab, and the option group. */
    val category: List<OrcaText>,
    val group: List<OrcaText>,
    val label: List<OrcaText>,
    val oldValue: List<OrcaText>,
    val newValue: List<OrcaText>,
)

/**
 * PrintHostType: the kind of a printer's host, as OrcaSlicer's host_type names
 * them, in the order and with the names PhysicalPrinterDialog lists them by.
 * The app tests and sends to the ones the port covers ([supported]); the
 * others can be chosen and kept, as the desktop app keeps them.
 */
enum class PrintHostType(val key: String, val label: String) {
    /** Prusa's own host, which takes a key or a user and password (digest). */
    PRUSA_LINK("prusalink", "PrusaLink"),

    /** Prusa's cloud, which takes PrusaLink's requests at connect.prusa3d.com. */
    PRUSA_CONNECT("prusaconnect", "PrusaConnect"),
    OCTOPRINT("octoprint", "Octo/Klipper"),
    DUET("duet", "Duet"),
    FLASHAIR("flashair", "FlashAir"),
    ASTROBOX("astrobox", "AstroBox"),
    REPETIER("repetier", "Repetier"),

    /** MKS boards: the file over http, the G-code over a TCP console on port 8080. */
    MKS("mks", "MKS"),
    ESP3D("esp3d", "ESP3D"),

    /** Creality's own firmware (CrealityPrint): the K2 family prints from its CFS material boxes. */
    CREALITY_PRINT("crealityprint", "CrealityPrint"),
    OBICO("obico", "Obico"),
    FLASHFORGE("flashforge", "Flashforge"),
    SIMPLYPRINT("simplyprint", "SimplyPrint"),
    ELEGOO_LINK("elegoolink", "Elegoo Link"),
    PRINTER_3D_OS("3dprinteros", "3DPrinterOS"),
    MOONRAKER("moonraker", "Moonraker (Klipper)"),
    ;

    /** Whether the app can test the host and send G-code to it. */
    val supported: Boolean get() = this !in UNSUPPORTED

    /** PrintHost::is_cloud(): a host reached through an account, whose Test button logs in. */
    val isCloud: Boolean get() = this == OBICO || this == SIMPLYPRINT || this == PRINTER_3D_OS

    /** Whether the host takes a user and a password instead of a key (AuthorizationType). */
    val takesUserPassword: Boolean get() = this == PRUSA_LINK

    /**
     * PhysicalPrinterDialog::update(): the hosts that print on one of several
     * printers of the server (printhost_port), which its Refresh button lists.
     */
    val supportsMultiplePrinters: Boolean get() = this == REPETIER || this == OBICO

    /** PhysicalPrinterDialog::update(): Flashforge's local API also takes the printer's serial number. */
    val takesSerialNumber: Boolean get() = this == FLASHFORGE

    /** PhysicalPrinterDialog::update(): the cloud hosts that keep their key and page themselves. */
    val showsApiKey: Boolean get() = this != SIMPLYPRINT && this != PRINTER_3D_OS

    val showsWebUi: Boolean get() = this != SIMPLYPRINT && this != PRINTER_3D_OS

    /** PhysicalPrinterDialog::update(): SimplyPrint's address is its own. */
    val hostEditable: Boolean get() = this != SIMPLYPRINT

    /**
     * get_post_upload_actions() has StartPrint: whether the send dialog offers
     * "Upload and Print". FlashAir only stores, SimplyPrint only queues.
     */
    val startsPrint: Boolean get() = this != FLASHAIR && this != SIMPLYPRINT

    /**
     * PrintHost::has_auto_discovery(): whether PhysicalPrinterDialog offers its
     * Browse button. OctoPrint and the hosts built on it (PrusaLink,
     * PrusaConnect, AstroBox) look for OctoPrint's service; Creality's
     * firmware for its K-series printers, Flashforge for its own.
     */
    val hasAutoDiscovery: Boolean
        get() = this == OCTOPRINT || this == PRUSA_LINK || this == PRUSA_CONNECT || this == ASTROBOX || this == CREALITY_PRINT ||
            this == FLASHFORGE

    /** get_test_ok_msg(): what the Test button says when the host answered, as OrcaSlicer's msgid. */
    val testOkMessage: String
        get() = when (this) {
            PRUSA_LINK -> "Connection to PrusaLink is working correctly."
            PRUSA_CONNECT -> "Connection to Prusa Connect is working correctly."
            OCTOPRINT -> "Connection to OctoPrint is working correctly."
            DUET -> "Connection to Duet is working correctly."
            FLASHAIR -> "Connection to FlashAir is working correctly and upload is enabled."
            ASTROBOX -> "Connection to AstroBox is working correctly."
            REPETIER -> "Connection to Repetier is working correctly."
            MKS -> "Connection to MKS is working correctly."
            ESP3D -> "Connection to ESP3D is working correctly."
            CREALITY_PRINT -> "Connected to CrealityPrint successfully!"
            OBICO -> "Connected to Obico successfully!"
            FLASHFORGE -> "Serial connection to Flashforge is working correctly."
            SIMPLYPRINT -> "Connected to SimplyPrint successfully!"
            ELEGOO_LINK -> "Connection to ElegooLink is working correctly."
            PRINTER_3D_OS -> "Connection to 3DPrinterOS cloud works correctly."
            MOONRAKER -> "Connection to Moonraker is working correctly."
        }

    /**
     * get_test_failed_msg(): "<this>: <why>", as OrcaSlicer's msgid, and the
     * note some hosts add below.
     */
    val testFailedMessage: String
        get() = when (this) {
            PRUSA_LINK -> "Could not connect to PrusaLink"
            PRUSA_CONNECT -> "Could not connect to Prusa Connect"
            OCTOPRINT -> "Could not connect to OctoPrint"
            DUET -> "Could not connect to Duet"
            FLASHAIR -> "Could not connect to FlashAir"
            ASTROBOX -> "Could not connect to AstroBox"
            REPETIER -> "Could not connect to Repetier"
            MKS -> "Could not connect to MKS"
            ESP3D -> "Could not connect to ESP3D"
            CREALITY_PRINT -> "Could not connect to CrealityPrint"
            OBICO -> "Could not connect to Obico"
            FLASHFORGE -> "Could not connect to Flashforge via serial"
            SIMPLYPRINT -> "Could not connect to SimplyPrint"
            ELEGOO_LINK -> "Could not connect to ElegooLink"
            PRINTER_3D_OS -> "Error session check"
            MOONRAKER -> "Could not connect to Moonraker"
        }

    val testFailedNote: String?
        get() = when (this) {
            OCTOPRINT -> "Note: OctoPrint version 1.1.0 or higher is required."
            FLASHAIR -> "Note: FlashAir with firmware 2.00.02 or newer and activated upload function is required."
            ASTROBOX -> "Note: AstroBox version 1.1.0 or higher is required."
            REPETIER -> "Note: Repetier version 0.90.0 or higher is required."
            else -> null
        }

    companion object {
        /** The hosts the port does not cover yet: the cloud ones and Elegoo Link. */
        private val UNSUPPORTED = setOf(OBICO, SIMPLYPRINT, ELEGOO_LINK, PRINTER_3D_OS)

        fun of(key: String): PrintHostType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A printer the app sends G-code to: the name the send dialog shows it by, and
 * the settings of its host as the printer preset holds them (host_type,
 * print_host, printhost_apikey and the rest, which PhysicalPrinterDialog edits).
 */
data class PhysicalPrinter(
    val name: String,
    val settings: ModelSettings = ModelSettings(),
) {
    /** host_type of the printer; null when it is one the app cannot send to. */
    val hostType: PrintHostType? get() = PrintHostType.of(settings.values["host_type"].orEmpty())

    /** print_host: where the printer answers. */
    val host: String get() = settings.values["print_host"].orEmpty()

    /** printhost_apikey: what the host authorises the upload with. */
    val apiKey: String get() = settings.values["printhost_apikey"].orEmpty()

    /** printhost_user / printhost_password: what a host that asks for a login takes. */
    val user: String get() = settings.values["printhost_user"].orEmpty()

    val password: String get() = settings.values["printhost_password"].orEmpty()

    /**
     * printhost_authorization_type: whether the host is given the key or the
     * user and password (atKeyPassword / atUserPassword).
     */
    val usesUserPassword: Boolean get() = settings.values["printhost_authorization_type"] == "user"

    /** printhost_port: the printer of a server that serves several (Repetier). */
    val port: String get() = settings.values["printhost_port"].orEmpty()

    /** flashforge_serial_number: what Flashforge's local API knows the printer by. */
    val serialNumber: String get() = settings.values["flashforge_serial_number"].orEmpty()

    /**
     * The printer's gcode_flavor, which comes with the host's settings because
     * Flashforge's serial console tells a Klipper firmware from the older one.
     */
    val gcodeFlavor: String get() = settings.values["gcode_flavor"].orEmpty()

    /**
     * Flashforge's local API instead of its serial console: the printer has a
     * serial number and a check code (printhost_apikey), as Flashforge::test()
     * and Plater::send_gcode_legacy() decide.
     */
    val usesFlashforgeLocalApi: Boolean
        get() = hostType == PrintHostType.FLASHFORGE && serialNumber.isNotEmpty() && apiKey.isNotEmpty()

    /** get_test_ok_msg(): as OrcaSlicer's msgid; Flashforge says which of its two ways answered. */
    val testOkMessage: String?
        get() = if (usesFlashforgeLocalApi) "Connected to Flashforge local API successfully." else hostType?.testOkMessage

    /** get_test_failed_msg()'s first part, as OrcaSlicer's msgid. */
    val testFailedMessage: String?
        get() = if (usesFlashforgeLocalApi) "Could not connect to Flashforge local API" else hostType?.testFailedMessage

    /** Whether the app knows how to send G-code to it. */
    val canSend: Boolean get() = hostType?.supported == true && host.isNotBlank()
}

/** FlashforgeDiscoveredPrinter: a Flashforge printer that answered the broadcast of the local network. */
data class FlashforgeDiscoveredPrinter(val name: String, val serialNumber: String, val ipAddress: String)

sealed interface FlashforgeDiscoveryOutcome {
    data class Success(val printers: List<FlashforgeDiscoveredPrinter>) : FlashforgeDiscoveryOutcome

    data class Failure(val message: String) : FlashforgeDiscoveryOutcome
}

/**
 * FlashforgeMaterialSlot: a slot of the printer's material station (IFS), as
 * its local API reports it; slots are numbered from 1.
 */
data class FlashforgeMaterialSlot(
    val slotId: Int,
    val hasFilament: Boolean,
    val materialName: String,
    val materialColor: String,
)

/** Flashforge::fetch_material_slots(): the station's slots, and whether the printer reports a station at all. */
sealed interface FlashforgeSlotsOutcome {
    data class Success(val slots: List<FlashforgeMaterialSlot>, val supportsMaterialStation: Boolean) : FlashforgeSlotsOutcome

    data class Failure(val message: String) : FlashforgeSlotsOutcome
}

/** A filament of the print fed from a slot of the material station (FlashforgePrintHostSendDialog::extendedInfo()). */
data class FlashforgeMapping(
    /** The filament's index, the G-code's tool. */
    val toolId: Int,
    val slotId: Int,
    val materialName: String,
    val toolMaterialColor: String,
    val slotMaterialColor: String,
)

/**
 * What FlashforgePrintHostSendDialog adds for the local API: level the bed
 * first, record a time-lapse, print from the material station (IFS) with the
 * slot every filament is fed from.
 */
data class FlashforgeOptions(
    val levelingBeforePrint: Boolean = false,
    val timeLapseVideo: Boolean = false,
    val useMaterialStation: Boolean = false,
    val mappings: List<FlashforgeMapping> = emptyList(),
)

/**
 * PhysicalPrinterDialog::update(), which runs whenever the dialog opens and a
 * setting changes: the address a cloud host fills in by itself is cleared once
 * another kind of host is chosen, and PrusaConnect, Obico, SimplyPrint and
 * 3DPrinterOS fill in their own when the field is empty (SimplyPrint always).
 */
fun ModelSettings.withHostDefaults(): ModelSettings {
    val type = PrintHostType.of(values["host_type"].orEmpty()) ?: return this
    var host = values["print_host"].orEmpty()
    var webUi = values["print_host_webui"].orEmpty()
    if (host in CLOUD_HOST_ADDRESSES) host = ""
    when (type) {
        PrintHostType.PRUSA_CONNECT -> if (host.isEmpty()) host = PRUSA_CONNECT_ADDRESS
        PrintHostType.OBICO -> if (host.isEmpty()) host = OBICO_ADDRESS
        PrintHostType.SIMPLYPRINT -> {
            host = SIMPLYPRINT_PANEL
            if (webUi.isNotEmpty()) webUi = SIMPLYPRINT_PANEL
        }
        PrintHostType.PRINTER_3D_OS -> if (host.isEmpty()) host = PRINTER_3D_OS_ADDRESS
        else -> Unit
    }
    if (host == values["print_host"].orEmpty() && webUi == values["print_host_webui"].orEmpty()) return this
    return ModelSettings(values + ("print_host" to host) + ("print_host_webui" to webUi))
}

private const val PRUSA_CONNECT_ADDRESS = "https://connect.prusa3d.com"
private const val OBICO_ADDRESS = "https://app.obico.io"
private const val SIMPLYPRINT_PANEL = "https://simplyprint.io/panel"

/** C3DPrinterOS::default_host(). */
private const val PRINTER_3D_OS_ADDRESS = "https://cloud.3dprinteros.com"

private val CLOUD_HOST_ADDRESSES =
    setOf(PRUSA_CONNECT_ADDRESS, OBICO_ADDRESS, "https://simplyprint.io", SIMPLYPRINT_PANEL, PRINTER_3D_OS_ADDRESS)

/**
 * PrintHost::get_printers(): the printers a server that serves several prints
 * on (their printhost_port), which the dialog's Refresh button lists.
 */
sealed interface HostPrintersOutcome {
    data class Success(val printers: List<String>) : HostPrintersOutcome

    data class Failure(val message: String) : HostPrintersOutcome
}

/**
 * A slot the printer can feed filament from, as a Creality printer reports its
 * material boxes (CrealityPrint::query_boxes_info): a slot of a CFS box, or the
 * spool holder at the back, which is box 0.
 */
data class PrinterSlot(
    /** "T1A": the box and the slot, as the printer names them. */
    val toolId: String,
    /** The filament's type, "PLA", "PETG" ... */
    val type: String,
    /** "#RRGGBB". */
    val color: String,
    val boxId: Int,
    val materialId: Int,
) {
    /** The spool holder, which prints one filament only. */
    val isSpoolHolder: Boolean get() = boxId == 0

    /** How the desktop dialog lists it: "1A - PLA", or "Ext - PLA" for the spool holder. */
    val label: String get() = if (isSpoolHolder) "Ext - $type" else "${toolId.drop(1)} - $type"
}

/**
 * CrealityPrintHostSendDialog: the slot a filament of the plate is fed from
 * unless the user picks another. The desktop dialog looks for a CFS slot with
 * the filament's type and colour, then one with its type, then the spool holder
 * with both, then with the type, and otherwise takes the slot in the filament's
 * place.
 */
fun defaultSlotFor(index: Int, color: String, type: String, slots: List<PrinterSlot>): Int {
    val passes = listOf<(PrinterSlot) -> Boolean>(
        { !it.isSpoolHolder && it.type == type && sameColor(it.color, color) },
        { !it.isSpoolHolder && it.type == type },
        { it.isSpoolHolder && it.type == type && sameColor(it.color, color) },
        { it.isSpoolHolder && it.type == type },
    )
    for (pass in passes) {
        val found = slots.indexOfFirst(pass)
        if (found >= 0) return found
    }
    return if (index < slots.size) index else 0
}

/** wxColour's comparison: the same red, green and blue, whatever the case. */
private fun sameColor(first: String, second: String): Boolean =
    first.removePrefix("#").take(6).equals(second.removePrefix("#").take(6), ignoreCase = true)

sealed interface PrinterSlotsOutcome {
    data class Success(val slots: List<PrinterSlot>) : PrinterSlotsOutcome

    data class Failure(val message: String) : PrinterSlotsOutcome
}

/**
 * What the send dialog adds for a printer with material boxes
 * (CrealityPrintHostSendDialog::extendedInfo): whether it calibrates before it
 * prints, and the slot every filament of the plate is fed from, in the
 * filaments' order.
 */
data class PrintOptions(
    val selfTest: Boolean = false,
    val slots: List<PrinterSlot> = emptyList(),
    /** FlashforgePrintHostSendDialog's choices, for a Flashforge printer on its local API. */
    val flashforge: FlashforgeOptions? = null,
)

/**
 * The printer's host as the edited printer preset holds it, which the
 * sidebar's Connection button edits (PhysicalPrinterDialog), sending G-code
 * goes to (Plater::send_gcode_legacy()) and the Device tab shows the page of.
 */
data class PrinterConnection(
    /** host_type, print_host, print_host_webui, printhost_apikey and the rest. */
    val settings: ModelSettings,
    /**
     * The name the dialog saves the preset under: the user preset's own,
     * "Untitled" for the default one, or a system preset's with the
     * translated " - Copy" after it when [saveNameCopySuffix].
     */
    val saveName: String,
    val saveNameCopySuffix: Boolean,
    /**
     * Sidebar::update_all_preset_comboboxes(): the page the Device tab loads —
     * the host's page (PrintHost::get_print_host_webui()) or OrcaSlicer's page
     * that asks for a connection — and the API key its requests carry.
     */
    val webUi: String,
    val apiKey: String,
    /** PresetBundle::use_bbl_device_tab(): a BambuLab printer, whose Device tab is BambuLab's own monitor. */
    val bambuDeviceTab: Boolean,
) {
    /** The host as sending G-code takes it, named after [presetName]. */
    fun printer(presetName: String) = PhysicalPrinter(presetName, settings)
}

sealed interface PrinterConnectionOutcome {
    data class Success(val connection: PrinterConnection) : PrinterConnectionOutcome

    data class Failure(val message: String) : PrinterConnectionOutcome
}

/**
 * PhysicalPrinterDialog's Test button: whether the host at the printer's
 * address answers and is the kind of host the printer says it is
 * (PrintHost::test).
 */
sealed interface PrintHostTestOutcome {
    /** The host answered; [description] is what it said it is, when it said so. */
    data class Success(val description: String) : PrintHostTestOutcome

    data class Failure(val message: String) : PrintHostTestOutcome
}

/**
 * BonjourReply: a service of the local network that answered a lookup — the
 * address it answered from, the port it listens on, its name and host, and the
 * TXT values the lookup asked for.
 */
data class BonjourReply(
    val ip: String,
    val port: Int,
    val serviceName: String,
    val hostname: String,
    val txtData: Map<String, String>,
) {
    /** BonjourReply::path(): the TXT "path", starting with a slash, or "/" without it. */
    val path: String
        get() = txtData["path"].orEmpty().ifEmpty { "/" }.let { if (it.startsWith('/')) it else "/$it" }

    /**
     * The address BonjourDialog lists and the host field takes: https for port
     * 443, the port unless it is 80 or 443, and the path unless it is "/".
     */
    val fullAddress: String
        get() {
            val proto = if (port == 443) "https://" else ""
            val portSuffix = if (port != 443 && port != 80) ":$port" else ""
            return proto + ip + portSuffix + path.takeIf { it != "/" }.orEmpty()
        }
}

/** CrealityHost: a Creality K-series printer the scan of the local network found. */
data class CrealityHost(
    /** The IPv4 address it answered from. */
    val ip: String,
    /** The service type it announces itself by, "_Creality-543324280CDB19._udp.local.". */
    val serviceName: String,
    /** "K2-DB19": the last four digits of the service type's suffix. */
    val hostname: String,
    /** What http://<ip>/info says the model is, "F008"; empty when it did not answer. */
    val modelCode: String = "",
    /** "K2 Plus", "K2 Pro" or "K2" for a model of the K2 family; empty otherwise. */
    val modelName: String = "",
    val mac: String = "",
    /** Whether the model is of the K2 family, which prints from CFS boxes. */
    val cfsCapable: Boolean = false,
)

/** What sending G-code to a printer did (PrintHost::upload). */
sealed interface PrintHostUploadOutcome {
    /** The file arrived; [path] is what the host called it. */
    data class Success(val path: String) : PrintHostUploadOutcome

    data class Failure(val message: String) : PrintHostUploadOutcome
}

/**
 * The presets a configuration file brought in, or the files an export wrote
 * (OrcaSlicer's Import Configs and Export Preset Bundle).
 */
sealed interface ConfigTransferOutcome {
    /** The files that were written, or the presets that were imported. */
    data class Success(val names: List<String>) : ConfigTransferOutcome

    /**
     * ConfigsOverwriteConfirmDialog: a preset of this name is already there, and
     * the import runs again once the user has said what to do with it.
     */
    data class Overwrite(val preset: String, val names: List<String>) : ConfigTransferOutcome

    data class Failure(val message: String) : ConfigTransferOutcome
}

/** What the user answers about a preset the import would replace, as the dialog's buttons read. */
enum class ConfigOverwriteAnswer {
    NO,
    YES,
    NO_TO_ALL,
    YES_TO_ALL,
}

/**
 * DiffPresetDialog: what two presets of the same kind differ in, each setting
 * with the value both of them hold. Empty changes mean the presets are the same.
 */
/** CreateFilamentPresetDialog: a preset it offers, with the printer it would be made for. */
data class FilamentPresetChoice(
    val printer: String,
    val preset: String,
)

/** What the dialog offers: the vendors, the types, and the presets to copy. */
sealed interface CreateFilamentOptionsOutcome {
    data class Success(
        val vendors: List<String>,
        val types: List<String>,
        /** "Create Based on Current Filament": the filaments of the chosen type. */
        val baseFilaments: List<String>,
        /** The presets of the chosen filament. */
        val presets: List<FilamentPresetChoice>,
        /** "Copy Current Filament Preset": every preset of the chosen type. */
        val copyPresets: List<FilamentPresetChoice>,
    ) : CreateFilamentOptionsOutcome

    data class Failure(val message: String) : CreateFilamentOptionsOutcome
}

/** CreatePrinterPresetDialog: what its two pages offer. */
sealed interface CreatePrinterOptionsOutcome {
    data class Success(
        /** The first page: the vendors and models the dialog knows, and the nozzles. */
        val vendors: List<String>,
        val models: List<String>,
        val nozzleDiameters: List<String>,
        /** The second page: the vendors whose profiles the app has, and their printer presets. */
        val presetVendors: List<String>,
        val printerPresets: List<String>,
        /** The presets that come with the chosen printer preset. */
        val filamentPresets: List<String>,
        val processPresets: List<String>,
        /** The printable area of the chosen printer preset, and how high it prints. */
        val printableArea: List<Point2>,
        val maxPrintHeight: Double,
    ) : CreatePrinterOptionsOutcome

    data class Failure(val message: String) : CreatePrinterOptionsOutcome
}

/** What its Create button was filled in with. */
data class CreatePrinterRequest(
    val model: String,
    val nozzle: String,
    val printableArea: List<Point2>,
    val maxPrintHeight: Double,
    val customTexture: String = "",
    val customModel: String = "",
    val presetVendor: String,
    val printerPreset: String,
    val filamentPresets: List<String>,
    val processPresets: List<String>,
)

/** What its Create button was filled in with. */
data class CreateFilamentRequest(
    val vendor: String,
    /** The vendor the user wrote, which the dialog checks differently. */
    val customVendor: Boolean,
    val type: String,
    val serial: String,
    val presets: List<FilamentPresetChoice>,
)

/** What came of creating a filament, or of deleting one of its presets. */
sealed interface PresetCreationOutcome {
    /** The name of the filament ("<vendor> <type> <serial>"), or of the deleted preset. */
    data class Success(val name: String) : PresetCreationOutcome

    /** OrcaSlicer asks before it goes on; the app answers and asks again. */
    data class Question(val question: SettingsDialog) : PresetCreationOutcome

    data class Failure(val message: String) : PresetCreationOutcome
}

/** A filament of the user's own, as the app lists them to edit. */
data class CustomFilament(
    /** The filament id its presets share. */
    val id: String,
    val name: String,
)

sealed interface CustomFilamentsOutcome {
    data class Success(val filaments: List<CustomFilament>) : CustomFilamentsOutcome

    data class Failure(val message: String) : CustomFilamentsOutcome
}

/** EditFilamentPresetDialog: what it shows for a filament of the user's own. */
data class FilamentPresetList(
    val name: String,
    val vendor: String,
    val type: String,
    val serial: String,
    /** Its presets, by the printer each of them is for. */
    val presets: List<FilamentPresetChoice>,
)

sealed interface FilamentPresetsOutcome {
    data class Success(val filament: FilamentPresetList) : FilamentPresetsOutcome

    data class Failure(val message: String) : FilamentPresetsOutcome
}

/** ExportConfigsDialog: what the dialog writes (the radio buttons it opens with). */
enum class ConfigExportKind {
    /** "Printer config bundle(.orca_printer)": a printer with the presets it prints with. */
    PRINTER_BUNDLE,

    /** "Filament bundle(.orca_filament)": every preset of one filament. */
    FILAMENT_BUNDLE,

    /** "Printer presets(.zip)" */
    PRINTER_PRESETS,

    /** "Filament presets(.zip)" */
    FILAMENT_PRESETS,

    /** "Process presets(.zip)" */
    PROCESS_PRESETS,
}

/** A check box of the dialog: a printer, or the name a filament's presets share. */
data class ConfigExportEntry(
    val name: String,
    /** How many presets it carries. */
    val count: Int,
)

/** What the dialog offers for an export kind. */
sealed interface ConfigExportOptionsOutcome {
    data class Success(
        val entries: List<ConfigExportEntry>,
        /** The line under the list (m_serial_text), untranslated. */
        val note: String,
    ) : ConfigExportOptionsOutcome

    data class Failure(val message: String) : ConfigExportOptionsOutcome
}

/**
 * The presets one side of DiffPresetDialog selects in its combo boxes. An empty
 * name is the preset the app has selected, which the dialog opens with.
 */
data class ComparedPresets(
    val printer: String = "",
    val print: String = "",
    val filament: String = "",
)

/**
 * A row of DiffPresetDialog: the combo boxes of one preset kind, and what the
 * presets they select differ in.
 */
data class PresetKindComparison(
    val kind: PresetKind,
    /** PresetComboBox::update() of either side, and the preset it selects. */
    val leftPresets: List<PresetListItem>,
    val rightPresets: List<PresetListItem>,
    val left: String,
    val right: String,
    /** Why they were not compared ("One of the presets does not exist"); empty when they were. */
    val problem: String,
    /** The settings they differ in; none means they hold the same values. */
    val changes: List<PresetChange>,
    /** The preset the app edits of this kind, and whether it has unsaved changes. */
    val edited: String,
    val editedDirty: Boolean,
)

/** DiffPresetDialog's Transfer: the values [options] hold in [from] move into [to]. */
data class PresetTransfer(
    val kind: PresetKind,
    val from: String,
    val to: String,
    /** The ids of the settings the tree has selected ("retraction_length#0"). */
    val options: List<String>,
)

sealed interface PresetComparisonOutcome {
    data class Success(val kinds: List<PresetKindComparison>) : PresetComparisonOutcome

    data class Failure(val message: String) : PresetComparisonOutcome
}

/** What happens to the unsaved changes of a preset when another one is selected. */
enum class PresetChangeAction {
    /** A dirty preset selects nothing; the app asks what to do with its changes. */
    ASK,

    /** The changed values move to the preset that is selected. */
    TRANSFER,

    /** The changes are lost with the preset they were made in. */
    DISCARD,
}

/** The unsaved changes a preset selection stopped at (UnsavedChangesDialog). */
data class PendingPresetChange(
    /** The choice the app makes once the changes are dealt with. */
    val choice: PresetChoice,
    val kind: PresetKind,
    val changes: List<PresetChange>,
    /** The changes can be moved to the preset that is selected. */
    val canTransfer: Boolean,
    /** The name the dialog's Save button suggests. */
    val saveName: String,
    val saveNameCopySuffix: Boolean,
)

/** A request to a settings tab, which runs again with the answers to its questions. */
sealed interface SettingsRequest {
    /** The tab for the selected presets. */
    data object Describe : SettingsRequest

    /** The page the tab shows, whose fields it toggles. */
    data class SelectPage(val page: String) : SettingsRequest

    /** The field of the setting [id] changed to [text]. */
    data class Change(val id: String, val text: String) : SettingsRequest

    /** The undo buttons: [ids], or every modified setting when empty, back to the saved preset. */
    data class Reset(val ids: List<String>) : SettingsRequest

    /** The check box of a filament override. */
    data class SetOverride(val id: String, val enabled: Boolean) : SettingsRequest

    /** The presets the preset is compatible with; empty for every preset. */
    data class SetCompatible(val key: String, val presets: List<String>) : SettingsRequest

    data class SetMode(val mode: SettingsMode) : SettingsRequest

    /** Tab::m_variant_combo: the tab shows the values of another extruder variant. */
    data class SetVariant(val variant: Int) : SettingsRequest

    /** RammingDialog's OK: the filament's ramming parameters, as the dialog writes them. */
    data class SetRammingParameters(val parameters: String) : SettingsRequest

    /** EditGCodeDialog's OK: the custom G-code [key] edited to [gcode]. */
    data class EditCustomGcode(val key: String, val gcode: String) : SettingsRequest

    /** SavePresetDialog's OK. */
    data class Save(val name: String) : SettingsRequest

    /**
     * PhysicalPrinterDialog's OK, of the printer tab: the host's [settings] on
     * the edited printer preset, which is saved as [name].
     */
    data class SaveConnection(val settings: ModelSettings, val name: String) : SettingsRequest

    data object Delete : SettingsRequest
}

/** A question a request asked: it runs again with [answers] and the answer to [dialog]. */
data class PendingSettingsQuestion(
    val dialog: SettingsDialog,
    val request: SettingsRequest,
    val answers: Map<String, Boolean>,
)

/** One of OrcaSlicer's settings tabs as the app shows it. */
data class SettingsTabState(
    val kind: PresetKind,
    /** The definitions of the settings; null until the engine described them. */
    val tab: SettingsTab? = null,
    /** The edited preset with the pages of its tab; null until the engine described it. */
    val settings: PresetSettings? = null,
    /** The page the app shows. */
    val page: String = "",
    /** A request is running. */
    val changing: Boolean = false,
    /** Message boxes the requests showed, in order, until they are dismissed. */
    val notices: List<SettingsDialog> = emptyList(),
    /** A question a request waits for. */
    val question: PendingSettingsQuestion? = null,
    /** Why the last request failed. */
    val problem: String? = null,
)
