package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.TextStyleParcel;

/** EmbossVolumeOutcome: the volume, or the error. */
parcelable EmbossVolumeParcel {
    @nullable String error;
    /** EmbossKind's name. */
    String kind = "TEXT";
    String text = "";
    @nullable TextStyleParcel style;
    String svgName = "";
    boolean svgReloadable;
    double width;
    double height;
    /** VolumeType's name. */
    String type = "PART";
    boolean onlyPart;
    double scaleHeight;
    double scaleDepth;
    double scaleWidth = 1.0;
    /** EmbossShape::fix_3mf_tr, column-major; empty for none. */
    double[] fix = {};
}
