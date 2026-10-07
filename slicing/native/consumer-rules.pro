# The JNI bridge constructs the classes of this package and reads their
# fields by name: a class it names must keep its name, its constructors and its
# fields, and so must every class in a constructor's signature, as the bridge
# looks a constructor up by its descriptor. One rule keeps them all, so a class
# added to the bridge is not missed: one per class was, and R8 renamed
# NativeSliceNotice and removed NativePresetBundle from the release build.
-keep class app.orcinus.shadow.slicing.nativebridge.Native* { <init>(...); <fields>; }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeBindings { native <methods>; }
# The bridge looks onProgress up on the listener object, which is usually a
# lambda that R8 synthesizes; a rule on implementing classes does not reach it.
# Keeping the interface method keeps its name in every implementation.
-keep interface app.orcinus.shadow.slicing.nativebridge.NativeProgressListener { void onProgress(int, java.lang.String); }
-keep interface app.orcinus.shadow.slicing.nativebridge.NativeLoadProgressListener { boolean onProgress(int, java.lang.String); }
-keep interface app.orcinus.shadow.slicing.nativebridge.NativePlacementProgressListener { void onProgress(int, int, java.lang.String); }
