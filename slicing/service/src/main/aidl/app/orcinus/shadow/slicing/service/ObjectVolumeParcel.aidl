package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;

/** ObjectVolume: an object's own mesh as a volume (its first ModelVolume). */
parcelable ObjectVolumeParcel {
    String name = "";
    @nullable ModelSettingsParcel settings;
    boolean splittable;
    boolean convertedFromInches;
    boolean convertedFromMeters;
    String inputFile = "";
    /** ObjectVolume.cutInfo flattened (CutInfo.values). */
    @nullable double[] cutInfo;
}
