package app.orcinus.shadow.slicing.service;

/** ModelInspectionOutcome flattened; a non-null error means failure. */
parcelable InspectionParcel {
    @nullable String error;
    long facetCount;
    double widthMillimeters;
    double depthMillimeters;
    double heightMillimeters;
    boolean hasGeometryPreview;
    double samplingStepMillimeters;
    int sampledPlaneCount;
    int nonEmptyPlaneCount;
    long contourCount;
}
