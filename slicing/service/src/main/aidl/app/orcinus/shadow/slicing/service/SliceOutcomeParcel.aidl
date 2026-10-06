package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SliceNoticeParcel;

/** SliceOutcome flattened; kind selects which fields are meaningful. */
parcelable SliceOutcomeParcel {
    const String SUCCESS = "SUCCESS";
    const String FAILURE = "FAILURE";
    const String CANCELLED = "CANCELLED";

    String jobId;
    String kind;
    @nullable String gcodePath;
    /** The toolpaths file of a success, when one was written. */
    @nullable String toolpathsPath;
    /** The wipe tower mesh of a success, when the plate prints one. */
    @nullable String wipeTowerPath;
    /** The slice info of a success, when it was written. */
    @nullable String sliceInfoPath;
    int layerCount;
    long estimatedPrintTimeSeconds;
    double filamentMillimeters;
    double cost;
    /** SliceStatistics.filaments: the filaments, and eight amounts of each (filamentUsagesOf). */
    @nullable int[] filaments;
    @nullable double[] filamentAmounts;
    /** SliceFailureCode name. */
    @nullable String failureCode;
    @nullable String message;
    boolean recoverable;
    /** LayerGcodeRules of a success. */
    boolean sequential;
    boolean canChangeFilament;
    boolean hasTemplate;
    /** The name of a success's G-code, or the template's error. */
    @nullable String outputName;
    @nullable String outputNameError;
    /** The notices of a success, whether it may be printed, and whether post-processing scripts were skipped. */
    @nullable SliceNoticeParcel[] notices;
    boolean printReady;
    boolean postProcessSkipped;
    boolean primeTowerOutside;
    /** LayerGcodeRules.spiralVase and topZ, and SliceOutcome.Success.addLineNumber. */
    boolean spiralVase;
    double topZ;
    boolean addLineNumber;
    /** SliceOutcome.Success.patternGcodes, when patternGcodesSet. */
    boolean patternGcodesSet;
    @nullable double[] patternGcodeHeights;
    @nullable String[] patternGcodeTypes;
    @nullable int[] patternGcodeExtruders;
    @nullable String[] patternGcodeColors;
    @nullable String[] patternGcodeExtras;
}
