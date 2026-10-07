package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PresetChangeParcel;
import app.orcinus.shadow.slicing.service.PresetItemParcel;
import app.orcinus.shadow.slicing.service.ProfilesParcel;

/** PresetsOutcome flattened; a non-null error means failure. */
parcelable PresetsParcel {
    @nullable String error;
    @nullable ProfilesParcel selection;
    boolean setupRequired;
    @nullable PresetItemParcel[] printers;
    @nullable PresetItemParcel[] filaments;
    @nullable PresetItemParcel[] processes;
    /** Presets.tabPrinters and tabFilaments */
    @nullable PresetItemParcel[] tabPrinters;
    @nullable PresetItemParcel[] tabFilaments;
    /** The colour of every filament of the plate, "#RRGGBB". */
    @nullable String[] filamentColors;
    /** filament_type of every filament of the plate. */
    @nullable String[] filamentTypes;
    /** The displayed type of every filament of the plate. */
    @nullable String[] filamentDisplayTypes;
    @nullable String[] nozzleDiameters;
    @nullable String nozzleDiameter;
    /** Presets.bedTypes: the values and their labels. */
    @nullable String[] bedTypeValues;
    @nullable String[] bedTypeLabels;
    /** Presets.bedType, bedTypeSelectable and plateBedTypeSelectable. */
    @nullable String bedType;
    boolean bedTypeSelectable;
    boolean plateBedTypeSelectable;
    /** Presets.multiMaterialButtons, i3Structure and sequentialPrint */
    boolean multiMaterialButtons;
    boolean i3Structure;
    boolean sequentialPrint;
    /** Presets.minLayerHeights */
    double[] minLayerHeights;
    /** Presets.printerCover, nozzleType, extruderCount and pelletPrinter */
    @nullable String printerCover;
    @nullable String nozzleType;
    int extruderCount;
    boolean pelletPrinter;
    /** Nothing was selected: the preset of changedKind has these unsaved changes. */
    boolean asksUnsavedChanges;
    @nullable String changedKind;
    @nullable PresetChangeParcel[] unsavedChanges;
    boolean canTransfer;
    @nullable String saveName;
    boolean saveNameCopySuffix;
    boolean saveCanOverwrite;
    /** PresetsOutcome.UnsavedChanges' presetName, transferDropsVariants and cancelSelects */
    @nullable String changedPreset;
    boolean transferDropsVariants;
    boolean cancelSelects;
}
