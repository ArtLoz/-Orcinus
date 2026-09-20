package app.orcinus.shadow.slicing.service;

/** ConfigTransfer: the presets a file brought in, or the files an export wrote. */
parcelable ConfigTransferParcel {
    /** Why the transfer failed; null on success. */
    @nullable String error;
    @nullable String[] names;
    /** The preset the import waits for an answer about; empty when it does not. */
    String overwritePreset = "";
}
