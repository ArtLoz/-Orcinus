package app.orcinus.shadow.slicing.service;

/** PlacedInstance: one copy of an object on the plate (ModelInstance). */
parcelable PlacedInstanceParcel {
    /** Instance transformation, column-major 4 x 4. */
    double[] placement;
    boolean autoDrop;
    /** ModelInstance::printable. */
    boolean printable;
}
