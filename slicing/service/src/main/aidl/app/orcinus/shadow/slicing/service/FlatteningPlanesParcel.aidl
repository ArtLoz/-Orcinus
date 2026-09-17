package app.orcinus.shadow.slicing.service;

/** FlatteningPlanesOutcome flattened; a non-null error means failure. */
parcelable FlatteningPlanesParcel {
    @nullable String error;
    /** x, y, z per plane. */
    @nullable double[] normals;
    /** Points per plane. */
    @nullable int[] vertexCounts;
    /** x, y, z per point of every plane in turn. */
    @nullable double[] vertices;
}
