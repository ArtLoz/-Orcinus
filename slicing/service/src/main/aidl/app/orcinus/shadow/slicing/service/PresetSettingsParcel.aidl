package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.SettingStateParcel;
import app.orcinus.shadow.slicing.service.SettingsDialogParcel;
import app.orcinus.shadow.slicing.service.SettingsPageParcel;

/**
 * PresetSettingsOutcome flattened: a non-null error means failure, a non-null
 * question a question; otherwise the settings. kind and mode are the enum
 * constants' names.
 */
parcelable PresetSettingsParcel {
    @nullable String error;
    @nullable SettingsDialogParcel question;
    boolean questionLoadsSelection;
    @nullable String kind;
    @nullable String preset;
    @nullable String label;
    boolean dirty;
    boolean savedDirty;
    boolean isDefault;
    boolean isSystem;
    boolean hasParent;
    boolean canDelete;
    @nullable String mode;
    @nullable SettingsPageParcel[] pages;
    @nullable String activePage;
    /** Tab::m_variant_combo: the extruder variants of the filament tab, and the shown one. */
    @nullable String[] variants;
    int variant;
    @nullable SettingStateParcel[] settings;
    @nullable String saveName;
    boolean saveNameCopySuffix;
    @nullable SettingsDialogParcel[] notices;
    /** The settings of every selected object, or of the plate; null for a preset. */
    @nullable ModelSettingsParcel[] modelSettings;
}
