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
    double[] connectorValues;
    int[] connectorKinds;
    double snapSpace;
    double snapBulge;
    String connectorName;
    boolean dovetail;
    double[] groove;
    double radius;
    /** CutPartSelection: its plane, empty for none, and where each piece goes. */
    double[] partsPlane;
    boolean[] parts;
}
