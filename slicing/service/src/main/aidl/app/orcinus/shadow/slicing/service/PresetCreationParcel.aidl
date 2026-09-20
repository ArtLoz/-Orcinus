package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SettingsDialogParcel;

/** What came of creating a filament, or of deleting one of its presets. */
parcelable PresetCreationParcel {
    /** Why it failed; null when it did not. */
    @nullable String error;
    /** The question OrcaSlicer asks before it goes on; null when it asks none. */
    @nullable SettingsDialogParcel question;
    /** The name of the filament, or of the deleted preset. */
    String name = "";
}
