package app.orcinus.shadow.slicing.service;

parcelable ProfilesParcel {
    String printer;
    String filament;
    String process;
    /** Every filament of the plate, in the order the sidebar lists them. */
    @nullable String[] filaments;
}
