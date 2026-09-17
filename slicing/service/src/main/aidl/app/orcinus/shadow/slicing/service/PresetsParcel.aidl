package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PresetItemParcel;
import app.orcinus.shadow.slicing.service.ProfilesParcel;

/** PresetsOutcome flattened; a non-null error means failure. */
parcelable PresetsParcel {
    @nullable String error;
    @nullable ProfilesParcel selection;
    boolean setupRequired;
    @nullable PresetItemParcel[] printers;
    @nullable PresetItemParcel[] filaments;
    @nullable PresetItemParcel[] processes;
    @nullable String[] nozzleDiameters;
    @nullable String nozzleDiameter;
}
