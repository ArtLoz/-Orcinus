package app.orcinus.shadow.slicing.service;

/** PaintingOutcome flattened; a non-null error means failure. */
parcelable PaintingParcel {
    @nullable String error;
    /** Whether the stroke met the model. */
    boolean hit;
    /** The filaments the model is painted with, 1-based. */
    @nullable int[] filaments;
    /** The mesh of the triangles painted with each of them, in the same order. */
    @nullable String[] meshes;
    /** The painted facets, reported when the tool closes. */
    @nullable String facets;
    /** Whether the tool can undo or redo a stroke. */
    boolean canUndo;
    boolean canRedo;
}
