package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.LoadedObjectParcel;
import app.orcinus.shadow.slicing.service.SettingsDialogParcel;

/** ModelLoadOutcome flattened: an error, a question, or the objects. */
parcelable ModelLoadParcel {
    @nullable String error;
    @nullable SettingsDialogParcel question;
    SettingsDialogParcel[] notices;
    @nullable LoadedObjectParcel[] objects;
}
