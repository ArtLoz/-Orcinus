package app.orcinus.shadow.slicing.service;

/** SliceOutcome flattened; kind selects which fields are meaningful. */
parcelable SliceOutcomeParcel {
    const String SUCCESS = "SUCCESS";
    const String FAILURE = "FAILURE";
    const String CANCELLED = "CANCELLED";

    String jobId;
    String kind;
    @nullable String gcodePath;
    int layerCount;
    long estimatedPrintTimeSeconds;
    double filamentMillimeters;
    /** SliceFailureCode name. */
    @nullable String failureCode;
    @nullable String message;
    boolean recoverable;
}
