package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.GcodePlaceholderParcel;

/** EditGCodeDialog: the G-code it opens with, and the placeholders it lists. */
parcelable GcodePlaceholdersParcel {
    /** Why the placeholders could not be listed; null on success. */
    @nullable String error;
    String value;
    @nullable GcodePlaceholderParcel[] placeholders;
}
