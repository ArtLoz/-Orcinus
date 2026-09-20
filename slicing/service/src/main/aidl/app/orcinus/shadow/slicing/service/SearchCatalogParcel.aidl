package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SearchOptionParcel;

/** Search::OptionsSearcher: every setting the preset tabs show. */
parcelable SearchCatalogParcel {
    /** Why the settings could not be listed; null on success. */
    @nullable String error;
    @nullable SearchOptionParcel[] options;
}
