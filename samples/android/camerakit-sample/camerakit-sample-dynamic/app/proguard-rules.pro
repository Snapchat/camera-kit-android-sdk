# Keep all CameraKit classes that are loaded dynamically for a feature/plugin.
-keep class com.snap.camerakit** { *; }
# Repackage to avoid clashes with classes in CameraKit SDK.
-repackageclasses 'com.snap.camerakit.sample'
