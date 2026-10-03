package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ModelSettingsParcel;

/** ObjectPart: a part of an object (ModelVolume). */
parcelable ObjectPartParcel {
    /** One of OrcaSlicer's shapes: "Cube", "Cylinder", ...; empty for a volume of a model file. */
    String shape;
    /** The VolumeType's name. */
    String type;
    String meshPath;
    /** Its transformation in the object, column-major 4 x 4. */
    double[] placement;
    @nullable ModelSettingsParcel settings;
    /** The facets painted with the filaments of the plate; null when none are. */
    @nullable String painted;
    /** The mesh a volume of a model file is loaded from; null for a generated shape. */
    @nullable String source;
    String name = "";
    boolean splittable;
    boolean convertedFromInches;
    boolean convertedFromMeters;
    /** ModelVolume::source.input_file; empty for a generated shape. */
    String inputFile = "";
    /** ObjectPart.cutInfo flattened (CutInfo.values). */
    @nullable double[] cutInfo;
    /** The file of what the part was embossed from, and the EmbossKind's name; null for none. */
    @nullable String emboss;
    @nullable String embossKind;
}
