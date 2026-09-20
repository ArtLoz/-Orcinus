package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.OrcaTextParcel;

/** SettingsDialog; icon is the DialogIcon's name. */
parcelable SettingsDialogParcel {
    String id;
    String icon;
    OrcaTextParcel[] title;
    OrcaTextParcel[] text;
    boolean question;
    @nullable OrcaTextParcel yes;
    @nullable OrcaTextParcel no;
}
