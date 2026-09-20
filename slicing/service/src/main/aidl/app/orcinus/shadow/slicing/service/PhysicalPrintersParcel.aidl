package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PhysicalPrinterParcel;

/** PhysicalPrinterCollection: the printers the user set up. */
parcelable PhysicalPrintersParcel {
    /** Why they could not be listed or saved; null on success. */
    @nullable String error;
    @nullable PhysicalPrinterParcel[] printers;
}
