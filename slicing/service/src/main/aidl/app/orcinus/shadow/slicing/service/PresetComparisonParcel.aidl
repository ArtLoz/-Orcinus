package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PresetKindComparisonParcel;

/** DiffPresetDialog: the presets each side selects, and what they differ in. */
parcelable PresetComparisonParcel {
    /** Why they could not be compared; null on success. */
    @nullable String error;
    @nullable PresetKindComparisonParcel[] kinds;
}
