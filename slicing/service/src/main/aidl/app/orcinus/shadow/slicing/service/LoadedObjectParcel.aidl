package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.InspectionParcel;
import app.orcinus.shadow.slicing.service.LayerRangeParcel;
import app.orcinus.shadow.slicing.service.ObjectVolumeParcel;
import app.orcinus.shadow.slicing.service.ModelSettingsParcel;
import app.orcinus.shadow.slicing.service.ObjectPartParcel;

/** LoadedObject: an object of a model file, as OrcaSlicer loaded and placed it. */
parcelable LoadedObjectParcel {
    String name;
    String source;
    /** Column-major 4 x 4. */
    double[] frame;
    ObjectPartParcel[] parts;
    ModelSettingsParcel settings;
    ObjectVolumeParcel volume;
    InspectionParcel[] instances;
    /** ModelInstance::auto_drop and printable of every copy. */
    boolean[] autoDrops;
    boolean[] printables;
    /** Every copy's place in the assembly view, 16 each, which counts only where assembled; and its offset to it, 3 each. */
    @nullable double[] assembleMatrices;
    @nullable boolean[] assembled;
    @nullable double[] offsetsToAssembly;
    @nullable String painted;
    @nullable LayerRangeParcel[] layerRanges;
    /** The object's cut id flattened (CutId.values); null for none. */
    @nullable long[] cutId;
    /** The name of the file it came from when the load read several; null otherwise. */
    @nullable String inputFile;
    /** ModelObject::layer_height_profile; null for none. */
    @nullable double[] layerHeightProfile;
    /** ModelObject::brim_points: x, y, z and the radius of each; null for none. */
    @nullable double[] brimPoints;
}
