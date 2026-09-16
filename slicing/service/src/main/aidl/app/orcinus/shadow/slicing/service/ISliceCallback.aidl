package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SliceOutcomeParcel;

/** One-way calls to the same binder are delivered in order, so onFinished is last. */
oneway interface ISliceCallback {
    void onProgress(String jobId, float fraction, String stage, @nullable String detail);

    void onFinished(in SliceOutcomeParcel outcome);
}
