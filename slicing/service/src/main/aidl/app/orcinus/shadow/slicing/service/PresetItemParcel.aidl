package app.orcinus.shadow.slicing.service;

/** PresetListItem; group is the PresetGroup's name. */
parcelable PresetItemParcel {
    String name;
    String label;
    String group;
    String subgroup;
    boolean subgroupMsgid;
    boolean selected;
    /** PresetListItem.color */
    @nullable String color;
}
