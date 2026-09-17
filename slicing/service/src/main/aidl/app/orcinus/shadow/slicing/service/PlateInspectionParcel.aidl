package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.InspectionParcel;

/** PlateInspectionOutcome flattened; a non-null error means failure. */
parcelable PlateInspectionParcel {
    @nullable String error;
    /** Every object as placed, in the plate's order. */
    @nullable InspectionParcel[] inspections;
}
