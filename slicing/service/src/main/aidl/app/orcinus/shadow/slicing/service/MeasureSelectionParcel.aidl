package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.MeasureFeatureParcel;

/** MeasureSelection */
parcelable MeasureSelectionParcel {
    boolean isCenter;
    @nullable MeasureFeatureParcel source;
    @nullable MeasureFeatureParcel feature;
    int objectIndex;
    int volumeIndex;
}
