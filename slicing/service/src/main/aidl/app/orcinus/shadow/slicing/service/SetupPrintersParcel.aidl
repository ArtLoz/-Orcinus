package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SetupPrinterModelParcel;

/** SetupPrintersOutcome flattened; a non-null error means failure. */
parcelable SetupPrintersParcel {
    @nullable String error;
    @nullable SetupPrinterModelParcel[] models;
}
