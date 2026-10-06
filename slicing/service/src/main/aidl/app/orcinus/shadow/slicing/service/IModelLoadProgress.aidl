package app.orcinus.shadow.slicing.service;

/** The ProgressDialog of Plater::priv::load_files(); one-way calls to the same binder are delivered in order. */
oneway interface IModelLoadProgress {
    void onProgress(int percent, String file);
}
