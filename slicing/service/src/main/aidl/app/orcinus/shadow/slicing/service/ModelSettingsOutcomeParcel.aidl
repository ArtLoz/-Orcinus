package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;

/** ModelSettingsOutcome flattened; a non-null error means failure. */
parcelable ModelSettingsOutcomeParcel {
    @nullable String error;
    @nullable ModelSettingsParcel settings;
}
