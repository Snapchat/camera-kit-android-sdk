# TODO: remove once fixed in R8 version 2.1.52 R8
# see: https://partnerissuetracker.corp.google.com/issues/160971124
-keepclassmembers enum * {
    <fields>; # Needed for reflectively looking up the values via getEnumConstants
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Loaded reflectively from the main app based on the information provided in META-INF/services.
-keep public class com.snap.camerakit.sample.DefaultPlugin { *; }
