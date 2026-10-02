package app.orcinus.shadow.slicing.service;

/** PlateValidation: the current plate applied to its print and validated. */
parcelable PlateValidationParcel {
    /** The error's text, null for none; its object and copy indexes, and its setting. */
    @nullable String errorText;
    int errorObject;
    int errorInstance;
    @nullable String errorOption;
    @nullable String warningText;
    int warningObject;
    int warningInstance;
    @nullable String warningOption;
    /** The clearance outlines: each one's point count, then x and y of every point. */
    @nullable int[] clearanceCounts;
    @nullable double[] clearance;
    /** The outlines' union as triangles, x and y of every corner. */
    @nullable double[] clearanceFill;
    /** The height limits as triangles, x, y and z of every corner. */
    @nullable double[] heightFill;
    @nullable int[] sequence;
}
