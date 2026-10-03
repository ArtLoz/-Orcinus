package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SvgWarningParcel;

/** SvgPreviewOutcome: the preview, or the error. */
parcelable SvgPreviewParcel {
    @nullable String error;
    String picture = "";
    int width;
    int height;
    String svgPath = "";
    SvgWarningParcel[] warnings = {};
    long points;
}
