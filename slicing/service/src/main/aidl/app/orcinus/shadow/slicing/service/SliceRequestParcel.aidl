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
    /** Instance transformation, column-major 4 x 4; null for OrcaSlicer's placement of a new object. */
    @nullable double[] placement;
    boolean autoDrop;
}
