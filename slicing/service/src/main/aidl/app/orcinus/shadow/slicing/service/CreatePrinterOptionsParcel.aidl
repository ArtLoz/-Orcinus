package app.orcinus.shadow.slicing.service;

/** CreatePrinterPresetDialog: what its two pages offer. */
parcelable CreatePrinterOptionsParcel {
    /** Why it could not be listed; null on success. */
    @nullable String error;
    @nullable String[] vendors;
    @nullable String[] models;
    @nullable String[] nozzleDiameters;
    @nullable String[] existingPrinters;
    @nullable String[] presetVendors;
    @nullable String[] printerPresets;
    @nullable String[] filamentPresets;
    @nullable String[] processPresets;
    boolean templateAllowed;
    /** The dialog's message when the chosen printer preset cannot be read. */
    @nullable String message;
}
