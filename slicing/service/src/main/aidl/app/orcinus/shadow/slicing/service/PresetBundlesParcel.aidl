package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PresetBundleParcel;

/** PresetBundleDialog::ListBundles(): the preset bundles the user has. */
parcelable PresetBundlesParcel {
    /** Why they could not be listed or the bundle not removed; null on success. */
    @nullable String error;
    @nullable PresetBundleParcel[] bundles;
}
