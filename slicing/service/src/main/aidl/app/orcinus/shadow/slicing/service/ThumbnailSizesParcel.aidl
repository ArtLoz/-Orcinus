package app.orcinus.shadow.slicing.service;

/** ThumbnailSizes: the thumbnails the G-code of a printer holds. */
parcelable ThumbnailSizesParcel {
    /** Why the sizes could not be read; null on success. */
    @nullable String error;
    /** Width and height of each thumbnail, one after another. */
    @nullable int[] sizes;
}
