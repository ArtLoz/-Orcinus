package app.orcinus.shadow.slicing.service;

/** TextStyle; NaN stands for an unset number. */
parcelable TextStyleParcel {
    String name = "";
    String fontPath = "";
    double sizeInMm;
    boolean perGlyph;
    /** TextHorizontalAlign's and TextVerticalAlign's names. */
    String horizontalAlign = "CENTER";
    String verticalAlign = "CENTER";
    double charGap;
    double lineGap;
    double boldness;
    double skew;
    double collectionNumber;
    String family = "";
    String faceName = "";
    String style = "";
    String weight = "";
    double depth;
    boolean useSurface;
    double angle;
    double distance;
}
