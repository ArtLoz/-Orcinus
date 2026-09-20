package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.PresetChangeParcel;
import app.orcinus.shadow.slicing.service.PresetItemParcel;

/** A row of DiffPresetDialog: the combo boxes of one preset kind and their difference. */
parcelable PresetKindComparisonParcel {
    /** PresetKind. */
    String kind = "PRINT";
    @nullable PresetItemParcel[] leftPresets;
    @nullable PresetItemParcel[] rightPresets;
    String left = "";
    String right = "";
    /** Why they were not compared; empty when they were. */
    String problem = "";
    @nullable PresetChangeParcel[] changes;
    /** The preset the app edits of this kind, and whether it has unsaved changes. */
    String edited = "";
    boolean editedDirty;
}
