package app.orcinus.shadow.slicing.service;

/** SliceOutcome flattened; kind selects which fields are meaningful. */
parcelable SliceOutcomeParcel {
    const String SUCCESS = "SUCCESS";
    const String FAILURE = "FAILURE";
    const String CANCELLED = "CANCELLED";

    String jobId;
    String kind;
    @nullable String gcodePath;
    /** The toolpaths file of a success, when one was written. */
    @nullable String toolpathsPath;
    /** The wipe tower mesh of a success, when the plate prints one. */
    @nullable String wipeTowerPath;
    int layerCount;
    long estimatedPrintTimeSeconds;
    double filamentMillimeters;
    /** SliceFailureCode name. */
    @nullable String failureCode;
    @nullable String message;
    boolean recoverable;
}
