package app.orcinus.shadow.slicing.service;

/** CalibrationPrinterOutcome flattened; a non-null error means failure. */
parcelable CalibrationPrinterParcel {
    @nullable String error;
    String gcodeFlavor;
    boolean junctionDeviation;
    String[] shaperTypes;
}
