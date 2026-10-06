package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** SliceNotice. */
parcelable SliceNoticeParcel {
    /** SliceNoticeLevel name. */
    String level;
    OrcaTextParcel[] text;
    int objectIndex;
    int instanceIndex;
    boolean stepWarning;
}
