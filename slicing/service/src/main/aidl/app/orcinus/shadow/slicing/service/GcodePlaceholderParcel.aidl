package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** GcodePlaceholder: a node of EditGCodeDialog's list of placeholders. */
parcelable GcodePlaceholderParcel {
    int parent;
    /** The GcodePlaceholderType's name. */
    String type;
    OrcaTextParcel[] label;
    String key;
    String text;
    String icon;
    boolean expanded;
}
