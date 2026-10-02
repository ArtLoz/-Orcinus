package app.orcinus.shadow.slicing.service;

/** LayerEditingOutcome: the layer heights of the object being edited, or the error. */
parcelable LayerEditingParcel {
    @nullable String error;
    @nullable double[] profile;
    @nullable double[] layers;
    double objectMaxZ;
    double layerHeight;
    double minLayerHeight;
    double maxLayerHeight;
    double objectPrintZHeight;
    boolean fixed;
}
