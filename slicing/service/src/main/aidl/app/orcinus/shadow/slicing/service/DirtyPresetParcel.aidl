package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PresetChangeParcel;

/** DirtyPreset: a preset with unsaved changes; kind is the PresetKind's name. */
parcelable DirtyPresetParcel {
    String kind;
    String name;
    boolean canOverwrite;
    String saveName;
    boolean saveNameCopySuffix;
    PresetChangeParcel[] changes;
}
