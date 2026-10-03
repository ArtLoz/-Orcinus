package app.orcinus.shadow.slicing.service;

/** PlacedInstance: one copy of an object on the plate (ModelInstance). */
parcelable PlacedInstanceParcel {
    /** Instance transformation, column-major 4 x 4. */
    double[] placement;
    boolean autoDrop;
    /** ModelInstance::printable. */
    boolean printable;
    /** ModelInstance::m_assemble_transformation, column-major 4 x 4; null while the copy has none. */
    @nullable double[] assemble;
    /** ModelInstance::m_offset_to_assembly; null for none. */
    @nullable double[] offsetToAssembly;
}
