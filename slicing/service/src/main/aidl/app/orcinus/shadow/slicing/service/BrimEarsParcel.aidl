package app.orcinus.shadow.slicing.service;

/** BrimEarsOutcome: what the brim ears tool opened with, or the error. */
parcelable BrimEarsParcel {
    @nullable String error;
    double detectionRadiusMax;
    double defaultHeadDiameter;
    boolean painted;
}
