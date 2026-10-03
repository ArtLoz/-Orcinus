package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.MeasureFeatureParcel;

/** MeasureHoverOutcome: what is under the finger, or the error. */
parcelable MeasureHoverParcel {
    @nullable String error;
    @nullable MeasureFeatureParcel feature;
    boolean unchanged;
    /** Empty for none. */
    double[] point = {};
    int sphere;
}
