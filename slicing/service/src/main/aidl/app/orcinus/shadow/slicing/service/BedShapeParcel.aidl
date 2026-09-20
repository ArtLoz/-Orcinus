package app.orcinus.shadow.slicing.service;

/** BedShape: the printable area of the edited printer (BedShapeDialog). */
parcelable BedShapeParcel {
    /** Why it could not be described; null on success. */
    @nullable String error;
    /** The BedShapeKind's name. */
    String kind;
    double sizeX;
    double sizeY;
    double originX;
    double originY;
    double diameter;
    String texture;
    String model;
    /** The points of the area: x, y per point. */
    @nullable double[] points;
}
