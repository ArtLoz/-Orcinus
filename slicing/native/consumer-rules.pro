# The JNI bridge constructs and calls these classes by name. A class it names
# must keep its name and its constructor, and so must every class in that
# constructor's signature: the bridge looks the constructor up by descriptor.
-keep class app.orcinus.shadow.slicing.nativebridge.NativeBindings { native <methods>; }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSliceResult { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeThumbnailSizes { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePlateDescription { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeWipeTower { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeFlushVolumes { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePainting { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeModelInspection { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePlateInspection { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeFlatteningPlanes { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeImportedObject { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeImportedModels { <init>(...); }
# The bridge reads the plate's fields by name.
-keep class app.orcinus.shadow.slicing.nativebridge.NativePlate { <fields>; }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetItem { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetState { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetChange { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupPrinterModel { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupPrinters { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupFilament { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSetupFilaments { <init>(...); }
# The settings tabs: their definitions, pages, lines, values and message boxes.
-keep class app.orcinus.shadow.slicing.nativebridge.NativeUiText { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingDefinition { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingDefinitions { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingsLineOption { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingsLine { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingsGroup { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingsPage { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingState { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSettingsDialog { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetSettings { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetNameValidation { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeDirtyPreset { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeDirtyPresets { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCalibrationPrinter { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCutObject { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCutPlane { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCutParts { <init>(...); }
# The bridge builds these and also reads them back by field name.
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCalibration { <init>(...); <fields>; }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeProjectPlate { <init>(...); <fields>; }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetNames { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetComparison { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetKindComparison { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSearchOption { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeSearchCatalog { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeBedShape { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeGcodePlaceholder { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeGcodePlaceholders { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeGcodePlaceholderInfo { <init>(...); }
# Physical printers and the configuration files of the File menu.
-keep class app.orcinus.shadow.slicing.nativebridge.NativePrinterConnection { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeConfigTransfer { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeConfigExportOptions { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCreateFilamentOptions { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCreatePrinterOptions { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativePresetCreation { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeCustomFilaments { <init>(...); }
-keep class app.orcinus.shadow.slicing.nativebridge.NativeFilamentPresets { <init>(...); }
# The bridge looks onProgress up on the listener object, which is usually a
# lambda that R8 synthesizes; a rule on implementing classes does not reach it.
# Keeping the interface method keeps its name in every implementation.
-keep interface app.orcinus.shadow.slicing.nativebridge.NativeProgressListener { void onProgress(int, java.lang.String); }
