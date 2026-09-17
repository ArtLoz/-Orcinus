package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SetupFilamentParcel;

/** SetupFilamentsOutcome flattened; a non-null error means failure. */
parcelable SetupFilamentsParcel {
    @nullable String error;
    @nullable SetupFilamentParcel[] filaments;
}
