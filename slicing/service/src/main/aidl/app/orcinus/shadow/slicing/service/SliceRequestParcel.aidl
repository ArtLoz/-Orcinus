package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.PlacedModelParcel;

parcelable SliceRequestParcel {
    String jobId;
    /** The objects on the plate. */
    PlacedModelParcel[] objects;
    String outputPath;
    /** Where the engine writes the toolpaths for the preview; null writes none. */
    @nullable String toolpathsPath;
    /** Where the engine writes the wipe tower the slice builds; null writes none. */
    @nullable String wipeTowerPath;
    String printerProfile;
    String filamentProfile;
    /** Every filament of the plate; null prints with filamentProfile alone. */
    @nullable String[] filamentProfiles;
    String processProfile;
    /** The settings of the plate; null when it overrides none. */
    @nullable ModelSettingsParcel plateSettings;
    /** The thumbnails the app rendered: width and height of each, and its file. */
    @nullable int[] thumbnailSizes;
    @nullable String[] thumbnailPaths;
}
