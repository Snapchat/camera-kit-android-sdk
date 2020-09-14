package com.snap.camerakit.sample;

import android.content.Context;

import com.snap.camerakit.Session;

import java.util.ServiceLoader;

/**
 * Defines the interface that a dynamic feature module should implement to provide the full CameraKit implementation.
 */
public interface CameraKitFeature {

    /**
     * @return True if CameraKit feature is supported on this device.
     */
    boolean supported(Context context);

    /**
     * @return New instance of {@link Session.Builder} that can be used create a fully functioning {@link Session}
     * to interact with CameraKit feature.
     */
    Session.Builder newSessionBuilder(Context context);

    /**
     * Simple way to find and load {@link CameraKitFeature} when it is available.
     */
    final class Loader {

        /**
         * @return New instance of {@link CameraKitFeature} when available to be loaded using the {@link ServiceLoader}.
         * In order for implementations of {@link CameraKitFeature} to be visible to this method of loading, they need
         * to embed {@code com.snap.camerakit.sample.CameraKitFeature} text file in {@code resources/META-INF/services}
         * which contains only a single line with a name of an implementation class.
         */
        static CameraKitFeature load() {
            // Using a simple iterator form so that R8 can get this rewritten to simple array access,
            // see: https://r8.googlesource.com/r8/+/master/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
            return ServiceLoader
                    .load(CameraKitFeature.class, CameraKitFeature.class.getClassLoader())
                    .iterator()
                    .next();
        }
    }
}
