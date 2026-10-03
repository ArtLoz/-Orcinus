package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.MeasureFeatureParcel;
import app.orcinus.shadow.slicing.service.MeasureSelectionParcel;

/** MeasureOutcome: the measuring tool's state, or the error. */
parcelable MeasurementParcel {
    @nullable String error;
    @nullable MeasureSelectionParcel first;
    @nullable MeasureSelectionParcel second;
    /** The angle, its radius, 1 when coplanar, its centre and the two edges' ends; empty for none. */
    double[] angle = {};
    /** The distance and its ends; empty for none. */
    double[] distanceInfinite = {};
    double[] distanceStrict = {};
    double[] distanceXyz = {};
    boolean canSetToParallel;
    boolean canSetToCenterCoincidence;
    boolean canSetFeature1ReverseRotation;
    boolean canSetFeature2ReverseRotation;
    boolean canAroundCenterOfFaces;
    boolean hasParallelDistance;
    double parallelDistance;
    boolean canSetXyzDistance;
    int hitVolumes;
    boolean sameObject;
    boolean showResetFirstTip;
    @nullable MeasureFeatureParcel hovered;
}
