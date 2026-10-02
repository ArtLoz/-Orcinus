package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.LayerRangeParcel;
import app.orcinus.shadow.slicing.service.ModelSourceParcel;
import app.orcinus.shadow.slicing.service.ObjectPartParcel;
import app.orcinus.shadow.slicing.service.ObjectVolumeParcel;
import app.orcinus.shadow.slicing.service.PlacedInstanceParcel;

/** PlacedModel. */
parcelable PlacedModelParcel {
    ModelSourceParcel model;
    String meshPath;
    /** The copies of the object on the plate (ModelObject::instances). */
    PlacedInstanceParcel[] instances;
    /** The settings of the object; null when it overrides none. */
    @nullable ModelSettingsParcel settings;
    /** The parts added to the object (ModelObject::volumes). */
    @nullable ObjectPartParcel[] parts;
    /** The height ranges of the object (ModelObject::layer_config_ranges). */
    @nullable LayerRangeParcel[] layerRanges;
    /** The facets painted with the filaments of the plate; null when none are. */
    @nullable String painted;
    /** Where the model's own mesh stands in the object, column-major 4 x 4; null centres it. */
    @nullable double[] frame;
    /** The model's own mesh as a volume. */
    @nullable ObjectVolumeParcel volume;
    /** ModelObject::name; empty keeps the engine's. */
    String name = "";
    /** The object's cut id flattened (CutId.values); null for none. */
    @nullable long[] cutId;
    /** ModelObject::layer_height_profile; null for none. */
    @nullable double[] layerHeightProfile;
}
