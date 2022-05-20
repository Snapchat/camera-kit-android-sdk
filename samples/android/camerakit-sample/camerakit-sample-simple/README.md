# CameraKit Sample Simple App

An app that demonstrates how to launch the CameraKit's support `CameraActivity` with different parameters and get results back:

![demo](../.doc/sample_simple_demo.gif)

The full-featured `CameraActivity` is a great option for existing apps to integrate CameraKit without much additional code with the most minimal setup being:

- Add to a `build.gradle`:
    ```groovy
    // Add dependency to the CameraActivity support artifact which pulls all the other necessary dependencies
    implementation "com.snap.camerakit:support-camera-activity:$cameraKitVersion"
    ```
- Add to an `Activity`:
    ```kotlin
    val captureLauncher = registerForActivityResult(CameraActivity.Capture) { result ->
        if (result is CameraActivity.Capture.Result.Success) {
            // ...
        }
    }
    findViewById<Button>(R.id.some_button).setOnClickListener {
        captureLauncher.launch(CameraActivity.Configuration.WithLenses(
            // NOTE: replace the values with values obtained from https://kit.snapchat.com/manage
            cameraKitApplicationId = "b21cfb27-04e9-4b77-bd1a-1435522fb471",
            cameraKitApiToken = "eyJhbGciOiJIUzI1NiIsImtpZCI6IkNhbnZhc1MyU0hNQUNQcm9kIiwidHlwIjoiSldUIn0.eyJhdWQiOiJjYW52YXMtY2FudmFzYXBpIiwiaXNzIjoiY2FudmFzLXMyc3Rva2VuIiwibmJmIjoxNjM4NDc0OTE0LCJzdWIiOiJiMjFjZmIyNy0wNGU5LTRiNzctYmQxYS0xNDM1NTIyZmI0NzF-U1RBR0lOR34zMzQxMmZkZC0zMDA3LTRiMTgtOGE5OC1hNjAzZTY4MzJhMmEifQ.BBVRgyVT4I_Z_qevzAqVwkWNXZGMHQ0s4tRJst9qfwE",
            // NOTE: replace the value with lenses group ID from https://camera-kit.snapchat.com
            lensGroupIds = "5685839489138688"
        ))
    }
    ```

## Build

To build, install and launch the `camerakit-sample-simple` on a connected device:

### Command Line

- `./gradlew camerakit-sample-simple:installDebug`

- `adb shell am start -n com.snap.camerakit.sample.simple/com.snap.camerakit.sample.MainActivity`

### IDE

Select the `camerakit-sample-simple` module configuration and click run:

![run-android-studio](../.doc/sample_simple_run_android_studio.png)
