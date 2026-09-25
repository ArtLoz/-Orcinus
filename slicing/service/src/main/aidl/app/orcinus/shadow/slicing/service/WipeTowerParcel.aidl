package app.orcinus.shadow.slicing.service;

/** WipeTowerOutcome flattened; a non-null error means failure. */
parcelable WipeTowerParcel {
    @nullable String error;
    boolean shown;
    double x;
    double y;
    double width;
    double depth;
    double height;
    double rotation;
    double brimWidth;
    /** The filaments printed on the plate, 1-based. */
    @nullable int[] filaments;
    boolean primeTower;
    /** The names of the FlushOption entries the process preset enables. */
    @nullable String[] flushInto;
}
