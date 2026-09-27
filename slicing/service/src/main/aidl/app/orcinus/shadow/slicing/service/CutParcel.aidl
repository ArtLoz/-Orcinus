package app.orcinus.shadow.slicing.service;

/** ObjectCut: the copy, the plane column by column, and "After cut". */
parcelable CutParcel {
    int instance;
    double[] plane;
    boolean keepUpper;
    boolean keepLower;
    boolean keepAsParts;
    boolean placeOnCutUpper;
    boolean placeOnCutLower;
    boolean flipUpper;
    boolean flipLower;
}
