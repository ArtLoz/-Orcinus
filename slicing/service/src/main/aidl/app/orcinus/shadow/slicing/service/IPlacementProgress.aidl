package app.orcinus.shadow.slicing.service;

/**
 * Job::Ctl::update_status() of a job that places objects: the PlateJob's
 * ordinal, its percent and the object it got to. One-way calls to the same
 * binder are delivered in order.
 */
oneway interface IPlacementProgress {
    void onProgress(int job, int percent, String name);
}
