package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.InspectionParcel;

/** ModelInspectionOutcome of a placed copy: the copy, and the copies that followed it, by index. */
parcelable PlacementParcel {
    @nullable InspectionParcel inspection;
    @nullable int[] synchronizedIndexes;
    @nullable InspectionParcel[] synchronizedCopies;
}
