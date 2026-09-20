package app.orcinus.shadow.slicing.service;

/** EditFilamentPresetDialog: what it shows for a filament of the user's own. */
parcelable FilamentPresetsParcel {
    /** Why it could not be shown; null on success. */
    @nullable String error;
    String name = "";
    String vendor = "";
    String type = "";
    String serial = "";
    /** Its presets, by the printer each of them is for. */
    @nullable String[] printers;
    @nullable String[] presets;
}
