package app.orcinus.shadow.slicing.service;

/** ExportConfigsDialog: what it offers for an export kind. */
parcelable ConfigExportOptionsParcel {
    /** Why it could not be listed; null on success. */
    @nullable String error;
    /** The name of every entry, with how many presets it carries. */
    @nullable String[] names;
    @nullable long[] counts;
    String note = "";
}
