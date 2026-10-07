package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;
import app.orcinus.shadow.slicing.service.SettingsGroupParcel;

/** SettingsPage. */
parcelable SettingsPageParcel {
    String title;
    OrcaTextParcel[] label;
    String icon;
    SettingsGroupParcel[] groups;
    boolean modified;
}
