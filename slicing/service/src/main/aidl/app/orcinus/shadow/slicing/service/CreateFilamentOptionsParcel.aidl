package app.orcinus.shadow.slicing.service;

/** CreateFilamentPresetDialog: the vendors, types and presets it offers. */
parcelable CreateFilamentOptionsParcel {
    /** Why it could not be listed; null on success. */
    @nullable String error;
    @nullable String[] vendors;
    @nullable String[] types;
    @nullable String[] baseFilaments;
    /** The presets of the chosen filament: the printer of each, and its name. */
    @nullable String[] presetPrinters;
    @nullable String[] presetNames;
    /** Every preset of the chosen type, the same way. */
    @nullable String[] copyPrinters;
    @nullable String[] copyNames;
}
