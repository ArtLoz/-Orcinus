package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.CalibrationParcel;
import app.orcinus.shadow.slicing.service.LoadedObjectParcel;
import app.orcinus.shadow.slicing.service.ProjectPlateParcel;
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
    /** LoadedProject.plates: set for a 3MF file opened as a project. */
    @nullable ProjectPlateParcel[] plates;
    boolean presetsChanged;
    /** LoadedProject.info */
    @nullable String projectInfo;
    /** A calibration's load: what the plate's print is told. */
    @nullable CalibrationParcel calibration;
    /** A calibration's load: the plates its objects stand on. */
    int plateCount;
    /** A STEP file waits for StepMeshDialog: its linear and angle deflections and split (1 or 0). */
    @nullable double[] stepMesh;
}
