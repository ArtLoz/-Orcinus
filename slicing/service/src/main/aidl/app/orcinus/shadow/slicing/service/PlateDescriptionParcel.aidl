package app.orcinus.shadow.slicing.service;

/** PlateDescriptionOutcome flattened; a non-null error means failure. Points are x, y pairs. */
parcelable PlateDescriptionParcel {
    @nullable String error;
    @nullable double[] printableArea;
    double printableHeight;
    @nullable double[] plateTriangles;
    @nullable double[] excludeTriangles;
    @nullable double[] thinGridLines;
    @nullable double[] boldGridLines;
    @nullable String bedModel;
    @nullable String bedTexture;
    /** Red, green, blue, alpha. */
    @nullable float[] filamentColor;
    /** BuildVolumeShape name, and the circle of a circular bed: x, y, radius. */
    @nullable String buildVolumeShape;
    @nullable double[] circle;
}
