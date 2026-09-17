package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.ArrangeSettingsParcel;
import app.orcinus.shadow.slicing.service.EngineStatusParcel;
import app.orcinus.shadow.slicing.service.FlatteningPlanesParcel;
import app.orcinus.shadow.slicing.service.InspectionParcel;
import app.orcinus.shadow.slicing.service.ISliceCallback;
import app.orcinus.shadow.slicing.service.ModelSourceParcel;
import app.orcinus.shadow.slicing.service.PlateDescriptionParcel;
import app.orcinus.shadow.slicing.service.ProfilesParcel;
import app.orcinus.shadow.slicing.service.SliceRequestParcel;

/** Binder interface of SlicerService. Calls block; clients call off the main thread. */
interface ISlicerService {
    EngineStatusParcel status();

    PlateDescriptionParcel describePlate(in ProfilesParcel profiles, String directory);
    InspectionParcel inspect(in ModelSourceParcel model, in ProfilesParcel profiles, String meshPath);
    /**
     * Placements: instance transformations, column-major 4 x 4; manipulation:
     * the Manipulation's simple name, with faceNormal for LayOnFace and
     * arrangeSettings for Arrange.
     */
    InspectionParcel place(
        in ModelSourceParcel model,
        in ProfilesParcel profiles,
        String meshPath,
        in double[] previous,
        in double[] placement,
        boolean autoDrop,
        String manipulation,
        in @nullable double[] faceNormal,
        in @nullable ArrangeSettingsParcel arrangeSettings
    );
    FlatteningPlanesParcel flatteningPlanes(in ModelSourceParcel model, in ProfilesParcel profiles, String meshPath, in double[] placement);

    /** Returns at once; the result arrives through the callback. */
    void slice(in SliceRequestParcel request, ISliceCallback callback);

    boolean cancel(String jobId);
}
