package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** PresetNameOutcome flattened; a non-null error means failure. check is the PresetNameCheck's name. */
parcelable PresetNameParcel {
    @nullable String error;
    @nullable String check;
    @nullable OrcaTextParcel[] info;
}
