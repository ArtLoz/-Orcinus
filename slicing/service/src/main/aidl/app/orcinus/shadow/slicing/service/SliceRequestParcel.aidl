package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PlacedModelParcel;

parcelable SliceRequestParcel {
    String jobId;
    /** The objects on the plate. */
    PlacedModelParcel[] objects;
    String outputPath;
    /** Where the engine writes the toolpaths for the preview; null writes none. */
    @nullable String toolpathsPath;
    String printerProfile;
    String filamentProfile;
    String processProfile;
}
