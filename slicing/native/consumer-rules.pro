# The JNI bridge constructs and calls these classes by name.
-keep class app.orcinus.shadow.slicing.nativebridge.NativeBindings { native <methods>; }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSliceResult { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePlateDescription { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeModelInspection { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePlateInspection { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeFlatteningPlanes { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetItem { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetState { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupPrinterModel { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupPrinters { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupFilament { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupFilaments { <init>(...); }
# The bridge looks onProgress up on the listener object, which is usually a
# lambda that R8 synthesizes; a rule on implementing classes does not reach it.
# Keeping the interface method keeps its name in every implementation.
-keep interface app.orcinus.shadow.slicing.nativebridge.NativeProgressListener { void onProgress(int, java.lang.String); }
