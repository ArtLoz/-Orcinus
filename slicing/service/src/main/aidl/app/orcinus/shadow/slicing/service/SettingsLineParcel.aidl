package app.orcinus.shadow.slicing.service;

import app.orcinus.shadow.slicing.service.SettingsLineOptionParcel;

/** SettingsLine; widget is the SettingWidget's name. */
parcelable SettingsLineParcel {
    @nullable String label;
    @nullable String tooltip;
    SettingsLineOptionParcel[] options;
    boolean separator;
    String widget;
    boolean hasOverride;
    /** SettingsLine.labelPath */
    @nullable String labelPath;
}
