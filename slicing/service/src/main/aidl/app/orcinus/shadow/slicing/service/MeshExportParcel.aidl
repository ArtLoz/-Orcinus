package app.orcinus.shadow.slicing.service;

/** MeshExportOutcome flattened; a non-null error means failure. */
parcelable MeshExportParcel {
    @nullable String error;
    /** OrcaSlicer's notification when only the positive volumes were written. */
    @nullable String warning;
}
