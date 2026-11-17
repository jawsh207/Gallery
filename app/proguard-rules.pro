# R8 rules for release builds. The app itself uses no reflection; Coil and Media3 ship their
# own rules inside their libraries.

# WorkManager keeps its jobs in a Room database, and Room creates the generated database class
# through its no-argument constructor by reflection; R8's full mode strips it otherwise.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Gallery Plus: ONNX Runtime's native code calls back into its Java classes by name.
-keep class ai.onnxruntime.** { *; }
