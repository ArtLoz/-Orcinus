package app.orcinus.shadow.slicing.service;

/** MeasureFeature: MeasureFeatureType's name, its points (pt3 empty for none), its value, and a plane's triangles (null for none). */
parcelable MeasureFeatureParcel {
    String type = "POINT";
    double[] pt1 = {};
    double[] pt2 = {};
    double[] pt3 = {};
    double value;
    @nullable float[] planeTriangles;
}
