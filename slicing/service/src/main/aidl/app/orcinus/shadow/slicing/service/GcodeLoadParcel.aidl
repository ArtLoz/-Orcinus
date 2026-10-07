package app.orcinus.shadow.slicing.service;

/** GcodeLoadOutcome flattened: a failure has its message alone. */
parcelable GcodeLoadParcel {
    boolean success;
    @nullable String message;
    boolean valid;
    int layerCount;
    long estimatedPrintTimeSeconds;
    double filamentMillimeters;
    double cost;
    /** SliceStatistics.filaments: the filaments, and eight amounts of each (filamentUsagesOf). */
    @nullable int[] filaments;
    @nullable double[] filamentAmounts;
    /** The toolpaths file, when one was written. */
    @nullable String toolpathsPath;
    /** The slice info, when it was written. */
    @nullable String sliceInfoPath;
    /** GcodeSettingsIds. */
    @nullable String printerSettings;
    @nullable String printSettings;
    @nullable String[] filamentSettings;
    boolean bedTypeChanged;
}
