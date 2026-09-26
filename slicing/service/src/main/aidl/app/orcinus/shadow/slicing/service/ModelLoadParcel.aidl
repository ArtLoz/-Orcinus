package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.LoadedObjectParcel;
import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.SettingsDialogParcel;

/** ModelLoadOutcome flattened: an error, a question, or the objects. */
parcelable ModelLoadParcel {
    @nullable String error;
    @nullable SettingsDialogParcel question;
    SettingsDialogParcel[] notices;
    @nullable LoadedObjectParcel[] objects;
    /** The objects join the end of the plate's list and the edited one leaves it. */
    boolean appended;
    /** The volume of the edited object the list selects; -1 for none. */
    int selectedVolume = -1;
    /** LoadedProject: set for a 3MF file opened as a project. */
    @nullable ModelSettingsParcel plateSettings;
    @nullable double[] layerGcodeHeights;
    @nullable String[] layerGcodeTypes;
    @nullable int[] layerGcodeExtruders;
    @nullable String[] layerGcodeColors;
    @nullable String[] layerGcodeExtras;
    boolean presetsChanged;
    /** LoadedProject.info */
    @nullable String projectInfo;
}
