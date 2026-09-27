package app.orcinus.shadow.slicing.service;

/** PrinterConnectionOutcome flattened; a non-null error means failure. */
parcelable PrinterConnectionParcel {
    @nullable String error;
    String[] keys;
    String[] values;
    String saveName;
    boolean saveNameCopySuffix;
    String webUi;
    String apiKey;
    boolean bambuDeviceTab;
}
