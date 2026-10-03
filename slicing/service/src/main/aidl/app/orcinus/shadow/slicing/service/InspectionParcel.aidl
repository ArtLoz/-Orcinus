package app.orcinus.shadow.slicing.service;

/** ModelInspectionOutcome flattened; a non-null error means failure. */
parcelable InspectionParcel {
    @nullable String error;
    long facetCount;
    long openEdges;
    double volume;
    double widthMillimeters;
    double depthMillimeters;
    double heightMillimeters;
    @nullable String meshPath;
    /** Instance transformation, column-major 4 x 4. */
    @nullable double[] placement;
    /** BuildVolumeFit name. */
    @nullable String fit;
    /** x, y, z */
    @nullable double[] sphereCenter;
    double sphereRadius;
    /** x, y, z in degrees */
    @nullable double[] rotationDegrees;
    /** width, depth, height */
    @nullable double[] unscaledSize;
    /** x, y, z */
    @nullable double[] boxCenter;
}
