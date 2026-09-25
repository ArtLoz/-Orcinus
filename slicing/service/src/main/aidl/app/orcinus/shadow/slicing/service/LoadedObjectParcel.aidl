package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.InspectionParcel;
import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.ObjectPartParcel;

/** LoadedObject: an object of a model file, as OrcaSlicer loaded and placed it. */
parcelable LoadedObjectParcel {
    String name;
    String source;
    /** Column-major 4 x 4. */
    double[] frame;
    ObjectPartParcel[] parts;
    ModelSettingsParcel settings;
    /** ModelVolume::name and config of its own mesh. */
    String volumeName;
    ModelSettingsParcel volumeSettings;
    InspectionParcel[] instances;
}
