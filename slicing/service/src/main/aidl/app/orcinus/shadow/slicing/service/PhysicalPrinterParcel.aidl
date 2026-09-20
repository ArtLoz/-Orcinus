package app.orcinus.shadow.slicing.service;

/** PhysicalPrinter: a printer the app can send G-code to. */
parcelable PhysicalPrinterParcel {
    String name;
    @nullable String[] presetNames;
    @nullable String[] keys;
    @nullable String[] values;
}
