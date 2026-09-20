package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;

/** LayerRange: one height range of an object (ModelObject::layer_config_ranges). */
parcelable LayerRangeParcel {
    double bottom;
    double top;
    @nullable ModelSettingsParcel settings;
}
