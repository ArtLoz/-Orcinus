package app.orcinus.shadow.slicing.service;

/** CalibrationParams: mode is the CalibrationMode's name. */
parcelable CalibrationParcel {
    String mode;
    int extruderId;
    double start;
    double end;
    double step;
    boolean printNumbers;
    double freqStartX;
    double freqEndX;
    double freqStartY;
    double freqEndY;
    int testModel;
    @nullable String shaperType;
    @nullable double[] accelerations;
    @nullable double[] speeds;
}
