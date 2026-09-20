package app.orcinus.shadow.slicing.service;

/** CreatePrinterPresetDialog: what its two pages offer. */
parcelable CreatePrinterOptionsParcel {
    /** Why it could not be listed; null on success. */
    @nullable String error;
    @nullable String[] vendors;
    @nullable String[] models;
    @nullable String[] nozzleDiameters;
    @nullable String[] presetVendors;
    @nullable String[] printerPresets;
    @nullable String[] filamentPresets;
    @nullable String[] processPresets;
    /** x and y of every point of the printable area. */
    @nullable double[] printableArea;
    double maxPrintHeight;
}
