# CameraKit Sample Dynamic

Demonstrates how to integrate CameraKit SDK into an app that loads the SDK dynamically as an on-demand feature. Dynamic loading is useful for use cases where size of the CameraKit SDK is considered to be too big to be included together with the core app features. To support such use cases, the public CameraKit interfaces and classes are available in a separate `camerakit-api` and special `camera-kit-plugin-api` Maven artifacts that should have everything nececessary to interface with CameraKit SDK in the core app.

The sample [`app`](./app) uses two approaches to load CameraKit SDK through the provided `Plugin.Loader` interface:
- As a [`plugin`](./plugin) that is installed as a separate apk on a device and loaded by looking up its application ID.
- As an [`ondemand`](./ondemand) module installed using Google Play split apk / [app bundle](https://developer.android.com/guide/app-bundle) support.

In this specific sample app, when user clicks on the **START CAMERAKIT** button, we first check if dynamic feature is installed otherwise we attempt to load `Plugin` from a separate application and if that fails we install CameraKit SDK using Google Play's [`SplitInstallManager`](https://developer.android.com/reference/com/google/android/play/core/splitinstall/SplitInstallManager). If loading of the CameraKit `Plugin` is successful, we then present user with a list of available lenses that can can be clicked on to preview:

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
