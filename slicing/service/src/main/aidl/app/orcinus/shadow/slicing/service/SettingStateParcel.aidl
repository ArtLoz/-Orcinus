package app.orcinus.shadow.slicing.service;

/** SettingState; the choices are parallel arrays, null for the definition's. */
parcelable SettingStateParcel {
    String id;
    String key;
    String value;
    boolean modified;
    boolean system;
    boolean enabled;
    boolean visible;
    @nullable String[] choiceValues;
    @nullable String[] choiceLabels;
    boolean nullable;
    boolean isNil;
    /** The selected objects disagree on the value. */
    boolean mixed;
    boolean overrideEnabled;
    @nullable String[] listValues;
}
