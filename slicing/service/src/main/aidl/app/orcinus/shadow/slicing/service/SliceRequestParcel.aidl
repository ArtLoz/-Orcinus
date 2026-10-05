package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.CalibrationParcel;
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
    /** Where the engine writes the plate's slice info for its 3MF files; null writes none. */
    @nullable String sliceInfoPath;
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
    /** The codes on the layers: height, LayerGcodeType name, filament, colour and G-code of each. */
    @nullable double[] layerGcodeHeights;
    @nullable String[] layerGcodeTypes;
    @nullable int[] layerGcodeExtruders;
    @nullable String[] layerGcodeColors;
    @nullable String[] layerGcodeExtras;
    /** The calibration the plate prints; null for none. */
    @nullable CalibrationParcel calibration;
    /** The PA pattern the plate's handles print; null for none. */
    @nullable CalibrationParcel paPattern;
    /** SliceOutputNaming: what the G-code's name is made of. */
    @nullable String outputFilenameBase;
    @nullable String outputPlateName;
    @nullable String outputModelName;
}
