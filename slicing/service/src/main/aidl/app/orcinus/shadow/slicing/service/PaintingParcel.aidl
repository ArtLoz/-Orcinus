package app.orcinus.shadow.slicing.service;

/** PaintingOutcome flattened; a non-null error means failure. */
parcelable PaintingParcel {
    @nullable String error;
    /** Whether the stroke met the model. */
    boolean hit;
    /** The states the model is painted with (PaintState; the filaments for colour). */
    @nullable int[] states;
    /** The mesh of the triangles painted in each of them, in the same order, and the volume it lies on. */
    @nullable String[] meshes;
    @nullable int[] volumes;
    /** The painted facets of the object's own mesh and of each part, reported when the tool closes. */
    @nullable String facets;
    @nullable String[] partFacets;
    /** Whether the tool can undo or redo a stroke. */
    boolean canUndo;
    boolean canRedo;
}
