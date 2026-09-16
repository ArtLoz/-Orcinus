package app.orcinus.shadow.slicing.service;

parcelable SliceRequestParcel {
    String jobId;
    /** Set for an imported file; null for a built-in model. */
    @nullable String modelPath;
    /** BuiltInModel name; null for an imported file. */
    @nullable String builtInModel;
    String outputPath;
    String printerProfile;
    String filamentProfile;
    String processProfile;
}
