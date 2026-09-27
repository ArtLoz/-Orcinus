package app.orcinus.shadow.slicing.service;

/** CutPlaneOutcome flattened; a non-null error means failure. */
parcelable CutPlaneParcel {
    @nullable String error;
    double[] min;
    double[] max;
    boolean validContour;
    @nullable String contour;
    @nullable String section;
}
