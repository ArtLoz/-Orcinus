package app.orcinus.shadow.slicing.service;

/** SettingDefinition; type, mode, and control are the enum constants' names. */
parcelable SettingDefinitionParcel {
    String key;
    String type;
    String label;
    String sidetext;
    /** The page of the tab the setting belongs to. */
    @nullable String category;
    String mode;
    String control;
    String[] enumValues;
    String[] enumLabels;
    boolean multiline;
    boolean fullWidth;
    boolean isCode;
    int height;
}
