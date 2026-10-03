package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.MeasurementParcel;
import app.orcinus.shadow.slicing.service.ModelLoadParcel;

/** MeasureEditOutcome: the tool after the scale and the objects that changed, or the error. */
parcelable MeasureEditParcel {
    @nullable String error;
    @nullable MeasurementParcel measurement;
    @nullable ModelLoadParcel edit;
    int[] objectIndexes = {};
}
