package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PlateObjectInspectionParcel;

/** PlateInspectionOutcome flattened; a non-null error means failure. */
parcelable PlateInspectionParcel {
    @nullable String error;
    /** The copies of every object as placed, in the plate's order. */
    @nullable PlateObjectInspectionParcel[] inspections;
    /** PlateInspectionOutcome.Success.plates; 0 when unknown. */
    int plates;
    /** PlateInspectionOutcome.Success.objectOrder. */
    @nullable int[] objectOrder;
}
