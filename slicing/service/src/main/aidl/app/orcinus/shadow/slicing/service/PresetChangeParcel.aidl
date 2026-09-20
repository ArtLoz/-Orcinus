package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** PresetChange: a value the edited preset changed. */
parcelable PresetChangeParcel {
    String id;
    @nullable OrcaTextParcel[] category;
    @nullable OrcaTextParcel[] group;
    @nullable OrcaTextParcel[] label;
    @nullable OrcaTextParcel[] oldValue;
    @nullable OrcaTextParcel[] newValue;
}
