package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SettingDefinitionParcel;

/** SettingsTabOutcome flattened; a non-null error means failure. kind is the PresetKind's name. */
parcelable SettingsTabParcel {
    @nullable String error;
    @nullable String kind;
    @nullable SettingDefinitionParcel[] definitions;
}
