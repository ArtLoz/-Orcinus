package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSourceParcel;

/** PlacedModel. */
parcelable PlacedModelParcel {
    ModelSourceParcel model;
    String meshPath;
    /** Instance transformation, column-major 4 x 4. */
    double[] placement;
    boolean autoDrop;
}
