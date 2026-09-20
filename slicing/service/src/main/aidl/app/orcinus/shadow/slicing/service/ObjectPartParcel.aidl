package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;

/** ObjectPart: a part added to an object (ModelVolume). */
parcelable ObjectPartParcel {
    /** One of OrcaSlicer's shapes: "Cube", "Cylinder", ... */
    String shape;
    /** The VolumeType's name. */
    String type;
    String meshPath;
    /** Its transformation in the object, column-major 4 x 4. */
    double[] placement;
    @nullable ModelSettingsParcel settings;
    /** The facets painted with the filaments of the plate; null when none are. */
    @nullable String painted;
}
