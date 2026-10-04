package app.orcinus.shadow.slicing.service;

/** MeshExportOutcome flattened; a non-null error means failure. */
parcelable MeshExportParcel {
    @nullable String error;
    /** OrcaSlicer's notification when only the positive volumes were written. */
    @nullable String warning;
    /** Every file written as a file of its own, and the object it is named after. */
    @nullable String[] names;
    @nullable String[] paths;
}
