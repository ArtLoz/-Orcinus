package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.DirtyPresetParcel;

/** DirtyPresetsOutcome flattened; a non-null error means failure. */
parcelable DirtyPresetsParcel {
    @nullable String error;
    @nullable DirtyPresetParcel[] presets;
}
