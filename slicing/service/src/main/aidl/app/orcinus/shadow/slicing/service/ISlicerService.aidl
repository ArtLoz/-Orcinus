package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.EngineStatusParcel;
import app.orcinus.shadow.slicing.service.InspectionParcel;
import app.orcinus.shadow.slicing.service.ISliceCallback;
import app.orcinus.shadow.slicing.service.SliceRequestParcel;

/** Binder interface of SlicerService. Calls block; clients call off the main thread. */
interface ISlicerService {
    EngineStatusParcel status();

    InspectionParcel inspect(String modelPath);

    /** Returns at once; the result arrives through the callback. */
    void slice(in SliceRequestParcel request, ISliceCallback callback);

    boolean cancel(String jobId);
}
