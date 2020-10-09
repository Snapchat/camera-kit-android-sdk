package com.snap.camerakit.sample;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.snap.camerakit.ImageProcessor;
import com.snap.camerakit.Session;
import com.snap.camerakit.Source;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import dalvik.system.PathClassLoader;

import static android.content.Context.CONTEXT_IGNORE_SECURITY;
import static android.content.Context.CONTEXT_INCLUDE_CODE;

/**
 * Defines the interface that a dynamic feature module should implement to provide the full CameraKit implementation.
 */
public interface CameraKitFeature {

    /**
     * Always called to attach a {@link Context} that implementation must use when creating new {@link Session.Builder}
     * and other instances through this class. The provided {@link Context} is configured with access to resources
     * that belong to the feature implementation package while delegating everything else to the host application.
     */
    CameraKitFeature attach(Context context);

    /**
     * @return True if CameraKit feature is supported on this device.
     */
    boolean supported();

    /**
     * @return New instance of {@link Session.Builder} that can be used create a fully functioning {@link Session}
     * to interact with CameraKit feature.
     */
    Session.Builder newSessionBuilder();

    /**
     * Creates a new instance of {@link Source} for {@link ImageProcessor} that uses the provided video file to read
     * image frames from.
     */
    Source<ImageProcessor> sourceFrom(File videoFile);

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
                    final Context packageContext =
                            context.createPackageContext(packageName, CONTEXT_IGNORE_SECURITY | CONTEXT_INCLUDE_CODE);
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
                        final Context packageContextWrapper;
                        if (context instanceof LifecycleOwner) {
                            packageContextWrapper = new LifecycleOwnerContextWrapper(
                                    new PackageContextWrapper(packageContext, classLoader, context),
                                    (LifecycleOwner) context
                            );
                        } else {
                            packageContextWrapper= new PackageContextWrapper(packageContext, classLoader, context);
                        }
                        return serviceLoader(packageContextWrapper, classLoader).load();
                    };
                } catch (PackageManager.NameNotFoundException e) {
                    return null;
                }
            }

            /**
             * Creates a new instance of {@link Loader} which loads {@link CameraKitFeature} when available using the
             * {@link ServiceLoader} using the {@link ClassLoader} of the {@link CameraKitFeature} class.
             *
             * @see Loader.Factory#serviceLoader(Context, ClassLoader).
             */
            static Loader serviceLoader(Context context) {
                return serviceLoader(context, CameraKitFeature.class.getClassLoader());
            }

            /**
             * Creates a new {@link Loader} which loads {@link CameraKitFeature} when available using the
             * {@link ServiceLoader} for the provided {@link ClassLoader}.
             *
             * In order for implementations of {@link CameraKitFeature} to be visible to this method of loading, they need
             * to embed {@code com.snap.camerakit.sample.CameraKitFeature} text file in {@code resources/META-INF/services}
             * which contains only a single line with a name of an implementation class.
             */
            static Loader serviceLoader(Context context, ClassLoader classLoader) {
                return () -> ServiceLoader
                        .load(CameraKitFeature.class, classLoader)
                        .iterator()
                        .next()
                        .attach(context);
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

            /**
             * {@link ContextWrapper} which delegates all requests for resources and classes to the provided
             * packageContext and packageClassLoader while hostContext is used to represent an application/activity that
             * interacts with resources and code loaded in the external package.
             */
            private static final class PackageContextWrapper extends ContextWrapper {

                private final ClassLoader packageClassLoader;
                private final Context hostContext;

                public PackageContextWrapper(
                        Context packageContext, ClassLoader packageClassLoader, Context hostContext) {
                    super(packageContext);
                    this.packageClassLoader = packageClassLoader;
                    this.hostContext = hostContext;
                }

                @Override
                public ClassLoader getClassLoader() {
                    return packageClassLoader;
                }

                @Override
                public Context getApplicationContext() {
                    return new PackageApplication(
                            getBaseContext(), packageClassLoader, hostContext.getApplicationContext());
                }
            }

            /**
             * {@link Application} which delegates all requests for resources and classes to the provided packageContext
             * and packageClassLoader while applicationContext handles the rest.
             */
            private static class PackageApplication extends Application {

                private final ClassLoader packageClassLoader;
                private final Context packageContext;

                PackageApplication(Context packageContext, ClassLoader packageClassLoader, Context applicationContext) {
                    attachBaseContext(applicationContext);
                    this.packageClassLoader = packageClassLoader;
                    this.packageContext = packageContext;
                }

                @Override
                public Context getApplicationContext() {
                    return this;
                }

                @Override
                public ApplicationInfo getApplicationInfo() {
                    return packageContext.getApplicationInfo();
                }

                @Override
                public Resources getResources() {
                    return packageContext.getResources();
                }

                @Override
                public AssetManager getAssets() {
                    return packageContext.getResources().getAssets();
                }

                @Override
                public ClassLoader getClassLoader() {
                    return packageClassLoader;
                }
            }

            /**
             * Allows to represent the provided base {@link Context} as a {@link LifecycleOwner}.
             */
            private static final class LifecycleOwnerContextWrapper extends ContextWrapper implements LifecycleOwner {

                private final LifecycleOwner lifecycleOwner;

                public LifecycleOwnerContextWrapper(Context base, LifecycleOwner lifecycleOwner) {
                    super(base);
                    this.lifecycleOwner = lifecycleOwner;
                }

                @NonNull
                @Override
                public Lifecycle getLifecycle() {
                    return lifecycleOwner.getLifecycle();
                }
            }
        }
    }
}
