package app.orcinus.shadow.slicing.service;

/** CutPartsOutcome flattened; a non-null error means failure. */
parcelable CutPartsParcel {
    @nullable String error;
    String[] meshes;
    boolean[] upper;
    boolean[] modifiers;
}
