package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.TextStyleParcel;

/** TextStylesOutcome: the styles with the active one's index (-1 for none), or the error. */
parcelable TextStylesParcel {
    @nullable String error;
    TextStyleParcel[] styles = {};
    int active = -1;
}
