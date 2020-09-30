package com.snap.camerakit.sample;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import com.snap.camerakit.Session;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import dalvik.system.PathClassLoader;

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
    interface Loader {

        /**
         * @return a new instance of {@link CameraKitFeature} available to build new {@link Session}.
         */
        CameraKitFeature load();

        /**
         * Provides different implementations of {@link Loader} that use different class loading strategies.
         */
        class Factory {

            // Keeping a map of Classloader instances to re-use before they get GC'ed - we cannot load native
            // libraries into another Classloader which has not been GC'ed.
            private static final Map<String, WeakReference<ClassLoader>> PACKAGE_CLASSLOADERS = new HashMap<>();

            /**
             * Attempts to create a {@link Loader} that loads classes, resources and native libraries installed in a
             * separate application.
             *
             * @param context {@link Context} used to query applications available on a device.
             * @param packageName the application ID of an installation that contains classes, resources and native
             *                    libraries of {@link CameraKitFeature} implementation.
             * @return new instance of {@link Loader} or null when target application is not installed.
             */
            @Nullable
            static Loader pathClassLoader(Context context, String packageName) {
                try {
                    final ApplicationInfo applicationInfo =
                            context.getPackageManager().getApplicationInfo(packageName, 0);
                    return () -> {
                        ClassLoader classLoader;
                        synchronized (PACKAGE_CLASSLOADERS) {
                            WeakReference<ClassLoader> classLoaderReference = PACKAGE_CLASSLOADERS.get(packageName);
                            classLoader = classLoaderReference != null ? classLoaderReference.get() : null;
                            if (classLoader == null) {
                                String sourceDir = applicationInfo.sourceDir;
                                String nativeLibraryDir = applicationInfo.nativeLibraryDir;
                                classLoader = new FallbackPathClassLoader(
                                        sourceDir,
                                        nativeLibraryDir,
                                        CameraKitFeature.class.getClassLoader(),
                                        ClassLoader.getSystemClassLoader()
                                );
                                PACKAGE_CLASSLOADERS.put(packageName, new WeakReference<>(classLoader));
                            }
                        }
                        return serviceLoader(classLoader).load();
                    };
                } catch (PackageManager.NameNotFoundException e) {
                    return null;
                }
            }

            /**
             * Creates a new instance of {@link Loader} which loads {@link CameraKitFeature} when available using the
             * {@link ServiceLoader} using the {@link ClassLoader} of the {@link CameraKitFeature} class.
             *
             * @see Loader.Factory#serviceLoader(ClassLoader).
             */
            static Loader serviceLoader() {
                return serviceLoader(CameraKitFeature.class.getClassLoader());
            }

            /**
             * Creates a new {@link Loader} which loads {@link CameraKitFeature} when available using the
             * {@link ServiceLoader} for the provided {@link ClassLoader}.
             *
             * In order for implementations of {@link CameraKitFeature} to be visible to this method of loading, they need
             * to embed {@code com.snap.camerakit.sample.CameraKitFeature} text file in {@code resources/META-INF/services}
             * which contains only a single line with a name of an implementation class.
             */
            static Loader serviceLoader(ClassLoader classLoader) {
                return () -> ServiceLoader
                        .load(CameraKitFeature.class, classLoader)
                        .iterator()
                        .next();
            }

            /**
             * {@link PathClassLoader} which loads classes from the provided main {@link ClassLoader} falling back to
             * the provided fallback {@link ClassLoader} when a class cannot be found.
             */
            private static class FallbackPathClassLoader extends PathClassLoader {

                private final ClassLoader mainClassLoader;

                public FallbackPathClassLoader(
                        String dexPath,
                        String librarySearchPath,
                        ClassLoader mainClassLoader,
                        ClassLoader fallBackClassLoader) {
                    super(dexPath, librarySearchPath, fallBackClassLoader);
                    this.mainClassLoader = mainClassLoader;
                }

                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    try {
                        return mainClassLoader.loadClass(name);
                    } catch (ClassNotFoundException e) {
                        return super.findClass(name);
                    }
                }
            }
        }
    }
}
