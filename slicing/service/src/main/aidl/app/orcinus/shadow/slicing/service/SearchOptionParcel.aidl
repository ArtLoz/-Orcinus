package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** SearchOption: one setting the search can find (Search::Option). */
parcelable SearchOptionParcel {
    /** The PresetKind's name. */
    String kind;
    String key;
    String id;
    OrcaTextParcel[] page;
    OrcaTextParcel[] group;
    OrcaTextParcel[] label;
    /** The SettingsMode's name. */
    String mode;
}
