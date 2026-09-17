# The JNI bridge binds these methods by name and constructs these classes.
-keep class app.orcinus.shadow.render.gcode.NativeToolpaths { native <methods>; }
-keep class app.orcinus.shadow.render.gcode.NativeToolpathsStatistics { <init>(...); }
-keep class app.orcinus.shadow.render.gcode.NativeToolpathsSnapshot { <init>(...); }
