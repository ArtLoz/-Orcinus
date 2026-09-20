package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** GcodePlaceholderInfo: what EditGCodeDialog says about a placeholder. */
parcelable GcodePlaceholderInfoParcel {
    OrcaTextParcel[] label;
    String type;
    OrcaTextParcel[] description;
    boolean undefined;
}
