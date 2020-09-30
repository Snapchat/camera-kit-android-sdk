# CameraKit Sample Dynamic

Demonstrates how to integrate CameraKit SDK into an app that loads the SDK dynamically as an on-demand feature. Dynamic loading is useful for use cases where size of the CameraKit SDK is considered to be too big to be included together with the core app features. To support such use cases, the public CameraKit interfaces and classes are available in a separate `camerakit-api` Maven artifact that should have everything nececessary to interface with CameraKit SDK in the core app.

The sample [`app`](./app) uses two approaches to load CameraKit SDK through the shared [`CameraKitFeature`](./api/src/main/java/com/snap/camerakit/sample/CameraKitFeature.java) interface:
- As a [`plugin`](./plugin) that is installed as a separate apk on a device and loaded by looking up its application ID.
- As an [`ondemand`](./ondemand) module installed using Google Play split apk / [app bundle](https://developer.android.com/guide/app-bundle) support.

In this specific sample app, when user clicks on the **INSTALL CAMERAKIT** button, we first check if plugin application is installed otherwise we attempt install CameraKit SDK using Google Play's [`SplitInstallManager`](https://developer.android.com/reference/com/google/android/play/core/splitinstall/SplitInstallManager). If installing of the CameraKit SDK loading is successful, we then attempt to load it to present user with a list of available lenses:

![demo](../.doc/sample_dynamic_demo.gif)

## Build

To build, install and launch `camerakit-sample-dynamic-app` with:

- `camerakit_sample_dynamic_ondemand` feature splits included on a connected device:

    - Command Line

        - `./gradlew camerakit-sample-dynamic-app:installApkSplitsForTestDebug`

        - `adb shell am start -n com.snap.camerakit.sample.dynamic.app/com.snap.camerakit.sample.MainActivity`

    - IDE

        - Select the `camerakit-sample-dynamic-app` module configuration and click run:

            ![run-android-studio](../.doc/sample_dynamic_run_android_studio.png)
    
-  `camerakit-sample-dynamic-plugin` installed separately on a connected device:

    - Command Line

        - `./gradlew camerakit-sample-dynamic-app:installDebug`
        
        - `./gradlew camerakit-sample-dynamic-plugin:installDebug`
        
        - `adb shell am start -n com.snap.camerakit.sample.dynamic.app/com.snap.camerakit.sample.MainActivity`
