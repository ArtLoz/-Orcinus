package app.orcinus.shadow.slicing.service;

/** FlushVolumesOutcome flattened; a non-null error means failure. */
parcelable FlushVolumesParcel {
    @nullable String error;
    int filaments;
    int nozzles;
    /** nozzles * filaments * filaments, row by row. */
    @nullable double[] matrix;
    @nullable double[] automatic;
    @nullable double[] multipliers;
    boolean modified;
    /** For an update: the project's volumes changed. */
    boolean updated;
}
