package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;

/**
 * ProjectPlate: a plate of a project. The layer codes are parallel arrays,
 * their types by LayerGcodeType name; the picture has no path when there is none.
 */
parcelable ProjectPlateParcel {
    @nullable String name;
    boolean locked;
    @nullable ModelSettingsParcel settings;
    @nullable double[] layerGcodeHeights;
    @nullable String[] layerGcodeTypes;
    @nullable int[] layerGcodeExtruders;
    @nullable String[] layerGcodeColors;
    @nullable String[] layerGcodeExtras;
    int thumbnailWidth;
    int thumbnailHeight;
    @nullable String thumbnailPath;
}
