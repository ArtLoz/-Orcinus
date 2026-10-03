package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.MeasurementParcel;
import app.orcinus.shadow.slicing.service.ModelLoadParcel;

/** MeasureScaleOutcome: the tool after the scale and the objects that changed, or the error. */
parcelable MeasureScaleParcel {
    @nullable String error;
    @nullable MeasurementParcel measurement;
    @nullable ModelLoadParcel edit;
    int[] objectIndexes = {};
}
