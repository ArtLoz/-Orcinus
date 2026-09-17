package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ArrangeSettingsParcel;
import app.orcinus.shadow.slicing.service.EngineStatusParcel;
import app.orcinus.shadow.slicing.service.FlatteningPlanesParcel;
import app.orcinus.shadow.slicing.service.InspectionParcel;
import app.orcinus.shadow.slicing.service.ISliceCallback;
import app.orcinus.shadow.slicing.service.ModelSourceParcel;
import app.orcinus.shadow.slicing.service.PlacedModelParcel;
import app.orcinus.shadow.slicing.service.PlateDescriptionParcel;
import app.orcinus.shadow.slicing.service.PlateInspectionParcel;
import app.orcinus.shadow.slicing.service.PresetsParcel;
import app.orcinus.shadow.slicing.service.ProfilesParcel;
import app.orcinus.shadow.slicing.service.SetupFilamentsParcel;
import app.orcinus.shadow.slicing.service.SetupPrintersParcel;
import app.orcinus.shadow.slicing.service.SliceRequestParcel;

/** Binder interface of SlicerService. Calls block; clients call off the main thread. */
interface ISlicerService {
    EngineStatusParcel status();

    PlateDescriptionParcel describePlate(in ProfilesParcel profiles, String directory);
    InspectionParcel inspect(in ModelSourceParcel model, in ProfilesParcel profiles, String meshPath, in PlacedModelParcel[] plate);
    /**
     * Placements: instance transformations, column-major 4 x 4; manipulation:
     * the Manipulation's simple name, with faceNormal for LayOnFace.
     */
    InspectionParcel place(
        in ModelSourceParcel model,
        in ProfilesParcel profiles,
        String meshPath,
        in double[] previous,
        in double[] placement,
        boolean autoDrop,
        String manipulation,
        in @nullable double[] faceNormal
    );
    /**
     * manipulation: the PlateManipulation's simple name, with the selected
     * mesh paths for AutoOrient and arrangeSettings for Arrange.
     */
    PlateInspectionParcel placeObjects(
        in PlacedModelParcel[] plate,
        in ProfilesParcel profiles,
        String manipulation,
        in String[] selected,
        in @nullable ArrangeSettingsParcel arrangeSettings
    );
    FlatteningPlanesParcel flatteningPlanes(in ModelSourceParcel model, in ProfilesParcel profiles, String meshPath, in double[] placement);

    PresetsParcel presets();
    /** kind: the PresetChoice's simple name; value: its preset name, printer model, or nozzle diameter. */
    PresetsParcel selectPreset(String kind, String value);
    /**
     * The Setup Wizard's data comes in two calls, since every filament of every
     * printer model together would exceed the binder transaction limit.
     */
    SetupPrintersParcel setupPrinters();
    SetupFilamentsParcel setupFilaments(in String[] models);
    PresetsParcel applySetup(in String[] models, in String[] filaments);
    PresetsParcel applyDefaultSetup();

    /** Returns at once; the result arrives through the callback. */
    void slice(in SliceRequestParcel request, ISliceCallback callback);

    boolean cancel(String jobId);
}
