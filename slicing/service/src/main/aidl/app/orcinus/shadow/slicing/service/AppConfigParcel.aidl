package app.orcinus.shadow.slicing.service;

/** AppConfigOutcome: values of the app configuration by key; a non-null error means failure. */
parcelable AppConfigParcel {
    @nullable String error;
    @nullable String[] keys;
    @nullable String[] values;
}
