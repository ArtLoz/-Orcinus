package app.orcinus.shadow.core.model

/** The sections of OrcaSlicer's preset combo boxes. */
enum class PresetGroup {
    /** "User presets" */
    USER,

    /** "Bundle presets": presets of a subscribed preset bundle. */
    BUNDLE,

    /** "System presets" */
    SYSTEM,

    /** "Project-inside presets": the presets an opened project brought. */
    PROJECT,

    /** "Unsupported presets": incompatible with the printer; listed, not selectable. */
    UNSUPPORTED,
}

/** An entry of a preset combo box of OrcaSlicer's sidebar, in the combo box's order. */
data class PresetListItem(
    /** What choosing the entry selects: the preset name, or for a system printer its printer model. */
    val name: String,
    /** The text the combo box shows. */
    val label: String,
    val group: PresetGroup,
    /** The submenu: the vendor of a system filament, the bundle of a bundle preset; empty for none. */
    val subgroup: String,
    val selected: Boolean,
    /** The submenu is one of OrcaSlicer's msgids ("Custom", "Unspecified", "Project", "Unsupported"). */
    val subgroupMsgid: Boolean = false,
)

/** The presets OrcaSlicer's sidebar offers, for the selection its app configuration remembers. */
data class Presets(
    /** The selected printer, first filament, and process. */
    val selection: SlicingProfileSelection,
    /** OrcaSlicer would run its Setup Wizard: nothing was set up yet, or only default printers are installed. */
    val setupRequired: Boolean,
    val printers: List<PresetListItem>,
    val filaments: List<PresetListItem>,
    val processes: List<PresetListItem>,
    /** project_config's filament_colour: the colour of every filament of the plate, "#RRGGBB". */
    val filamentColors: List<String> = emptyList(),
    /** filament_type of every filament of the plate ("PLA", "PETG" ...), in the same order. */
    val filamentTypes: List<String> = emptyList(),
    /** The nozzle diameters of the selected printer model, "0.4". */
    val nozzleDiameters: List<String>,
    /** The nozzle diameter of the selected printer. */
    val nozzleDiameter: String,
    /**
     * The plate types the selected printer model supports (curr_bed_type), as
     * PlateSettingsDialog lists them; none for a printer of another vendor
     * than Bambu Lab, whose dialog does not choose one.
     */
    val bedTypes: List<BedTypeChoice> = emptyList(),
)

/** A plate type: curr_bed_type's [value] and OrcaSlicer's [label] for it. */
data class BedTypeChoice(val value: String, val label: String)

/**
 * A preset a settings tab edits with unsaved changes, as OrcaSlicer's
 * UnsavedChangesDialog lists it when a project is created or another one is
 * loaded. A preset that [canOverwrite] is saved under its own [name]; any
 * other is saved under a name the user gives, which the dialog suggests as
 * [saveName].
 */
data class DirtyPreset(
    val kind: PresetKind,
    val name: String,
    val canOverwrite: Boolean,
    val saveName: String,
    val saveNameCopySuffix: Boolean,
    val changes: List<PresetChange>,
)

sealed interface DirtyPresetsOutcome {
    data class Success(val presets: List<DirtyPreset>) : DirtyPresetsOutcome

    data class Failure(val message: String) : DirtyPresetsOutcome
}

sealed interface PresetsOutcome {
    data class Success(val presets: Presets) : PresetsOutcome

    /**
     * Nothing was selected: the edited preset of [kind] has unsaved changes.
     * The app asks what happens to them and selects again with a
     * [PresetChangeAction].
     */
    data class UnsavedChanges(
        val presets: Presets,
        val kind: PresetKind,
        val changes: List<PresetChange>,
        val canTransfer: Boolean,
        /** The name the dialog's Save button suggests. */
        val saveName: String,
        val saveNameCopySuffix: Boolean,
    ) : PresetsOutcome

    data class Failure(val message: String) : PresetsOutcome
}

/** What a choice in OrcaSlicer's sidebar selects. */
sealed interface PresetChoice {
    /** A printer preset; the process and filament the printer used last come with it. */
    data class Printer(val preset: ProfileId) : PresetChoice

    /** A system printer model, with the nozzle of the selected printer when it has one. */
    data class PrinterModel(val model: String) : PresetChoice

    /** Another nozzle diameter of the selected printer model. */
    data class NozzleDiameter(val diameter: String) : PresetChoice

    data class Filament(val preset: ProfileId) : PresetChoice

    data class Process(val preset: ProfileId) : PresetChoice
}

/** A printer model the Setup Wizard offers. */
data class SetupPrinterModel(
    /** The vendor bundle, for example "Creality". */
    val vendor: String,
    /** The model id, for example "Creality K2 Plus". */
    val id: String,
    val name: String,
    val nozzleDiameters: List<String>,
    /** Filament presets the wizard selects with the model. */
    val defaultMaterials: List<String>,
    /** The model's picture in the engine's resources. */
    val cover: String,
    /** The nozzle diameters already installed; empty for a model that is not. */
    val installedNozzles: List<String>,
)

sealed interface SetupPrintersOutcome {
    data class Success(val models: List<SetupPrinterModel>) : SetupPrintersOutcome

    data class Failure(val message: String) : SetupPrintersOutcome
}

/** A filament preset the Setup Wizard offers for the printer models chosen on its printer page. */
data class SetupFilament(
    val name: String,
    val vendor: String,
    val type: String,
    /** Indices of the chosen printer models the filament is for; empty for every printer. */
    val models: List<Int>,
    /** Installed, or a default material of a chosen model. */
    val selected: Boolean,
)

sealed interface SetupFilamentsOutcome {
    data class Success(val filaments: List<SetupFilament>) : SetupFilamentsOutcome

    data class Failure(val message: String) : SetupFilamentsOutcome
}
