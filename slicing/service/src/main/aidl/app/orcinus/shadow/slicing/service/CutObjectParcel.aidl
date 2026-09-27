package app.orcinus.shadow.slicing.service;

/** CutObjectOutcome flattened; a non-null error means failure. */
parcelable CutObjectParcel {
    @nullable String error;
    double[] min;
    double[] max;
}
