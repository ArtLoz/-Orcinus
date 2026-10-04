package app.orcinus.shadow.slicing.service;

/** What CreatePrinterPresetDialog's two pages are filled in with. */
parcelable CreatePrinterRequestParcel {
    boolean createNozzle;
    boolean customPrinter;
    @nullable String vendor;
    @nullable String model;
    @nullable String existingPrinter;
    @nullable String nozzle;
    boolean customNozzle;
    @nullable String customNozzleDiameter;
    double sizeX;
    double sizeY;
    double originX;
    double originY;
    double maxPrintHeight;
    @nullable String customTexture;
    @nullable String customModel;
    @nullable String presetVendor;
    @nullable String printerPreset;
    boolean fromTemplate;
    @nullable String[] filamentPresets;
    @nullable String[] processPresets;
}
