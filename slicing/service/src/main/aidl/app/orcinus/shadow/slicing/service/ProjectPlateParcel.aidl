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
    /** The other pictures, as large as the picture: without light, from the top, and the pick picture. */
    @nullable String noLightThumbnailPath;
    @nullable String topThumbnailPath;
    @nullable String pickThumbnailPath;
    /** While the plate's slice result is valid: its slice info and its G-code. */
    @nullable String sliceInfoPath;
    @nullable String gcodePath;
}
