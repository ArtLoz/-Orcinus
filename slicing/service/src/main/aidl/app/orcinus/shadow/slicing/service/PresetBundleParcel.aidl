package app.orcinus.shadow.slicing.service;

/** A row of PresetBundleDialog's top list, with the presets its bottom list shows. */
parcelable PresetBundleParcel {
    String id = "";
    String name = "";
    /** PresetBundleType. */
    String type = "DEFAULT";
    String version = "";
    @nullable String[] printers;
    @nullable String[] filaments;
    @nullable String[] processes;
    boolean updateAvailable;
    boolean unauthorized;
}
