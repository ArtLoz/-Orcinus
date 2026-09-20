package app.orcinus.shadow.slicing.service;

/** The filaments of the user's own, which the app lists to edit. */
parcelable CustomFilamentsParcel {
    /** Why they could not be listed; null on success. */
    @nullable String error;
    @nullable String[] ids;
    @nullable String[] names;
}
