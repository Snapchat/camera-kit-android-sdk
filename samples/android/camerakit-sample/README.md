# CameraKit Samples for Android

Demonstrates uses of CameraKit SDK on the Android platform.

Table of contents
=================

<!--ts-->
   * [Installation](#installation)
      * [Requirements](#requirements)
      * [Build](#build)
         * [Command Line](#command-line)
         * [IDE](#ide)
      * [Configuration](#configuration)
      * [Proguard](#proguard)
   * [Getting Started](#getting-started)
      * [Lifecycle](#lifecycle)
      * [Java or Kotlin?](#java-or-kotlin)
<!--te-->

## Installation

### Requirements

- Recent Android Studio (3.5+), download latest at: https://developer.android.com/studio.
- Android 5.0 device or emulator, with a minimum SDK API level of 21, is required.

### Build

To build, install and launch the `camerakit-sample-partner` on a connected device:

#### Command Line

- `./gradlew camerakit-sample-partner:installDebug`

- `adb shell am start -n com.snap.camerakit.sample.partner/com.snap.camerakit.sample.MainActivity`

#### IDE

Open the `camerakit-sample` project in Android Studio by opening the root `build.gradle` file:

![open-android-studio](.doc/open_android_studio.png)

Select the `camerakit-sample-partner` module configuration and click run:

![run-android-studio](.doc/run_android_studio.png)

### Configuration

The current version of CameraKit SDK is not yet available on public Maven repositories however this project includes an embedded [maven](./maven) repository of all CameraKit artifacts required to build. All CameraKit artifacts are published under a single version (see [CHANGELOG](../../../CHANGELOG.md) for a summary of changes in each release) and it is possible to pick and choose the dependencies necessary for your specific project:

```groovy
    implementation "com.snap.camerakit:camerakit-partner:$cameraKitVersion"
    implementation "com.snap.camerakit:camerakit-kotlin:$cameraKitVersion"
    implementation "com.snap.camerakit:lenses-bundle-partner:$cameraKitVersion"
    implementation "com.snap.camerakit:support-camerax:$cameraKitVersion"
```

In order for CameraKit to be able to communicate with remote services to get content such as lenses, app needs to provide CameraKit its unique "application ID" which is associated with app's package name (Android application ID).  The easiest way to do this is to first define a manifest placeholder with CameraKit application ID value:

```groovy
android {
    defaultConfig {
        applicationId 'com.snap.camerakit.sample.partner'
        manifestPlaceholders = [
            // NOTE: replace the value with ID specific to your application
            'cameraKitApplicationId': 'feba9432-74f4-4226-aa3e-21c1e3775f1a'
        ]
    }
}
```

Then, the placeholder can be used within the app's  [AndroidManifest.xml](./camerakit-sample-partner/src/main/AndroidManifest.xml):

```xml
<application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">

        <meta-data android:name="com.snap.camerakit.app.id" android:value="${cameraKitApplicationId}" />

</application>
```

CameraKit is built targeting Java8 bytecode which requires enabling Java8 compatibility (desugar) support via Android Gradle Plugin (AGP) `compileOptions` for your app:

```groovy
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
```

*For more information, see build configuration in `camerakit-sample-partner` [build.gradle](./camerakit-sample-partner/build.gradle).*

### Proguard

The CameraKit SDK artifacts ship with consumer Proguard rules which cover all CameraKit specific cases without being too broad. CameraKit is tested against the R8 optimizer running in full mode, enabled in [gradle.properties](./gradle.properties).

## Getting Started

The main point of entry to all CameraKit SDK features is the `Session` interface which can be built using a traditional builder which allows to customize certain aspects of the SDK such as lenses data sources etc. 

To obtain a new `Session`, use of one of the provided static or extension builder methods:

```kotlin
  cameraKitSession = Session(this) {// <- Lambda with Session.Builder as receiver 
      
      // Customize general functionality shared by all CameraKit components
      
      configureLenses {
          // Customize functionality exposed by lenses
      }
  }
```

### Lifecycle

`Session` instance is typically shared within a single Android application, service or activity lifecycle scope as `Session` is costly in terms of memory and cpu resources it requires to operate. Once done with a `Session`, It is **essential** to dispose it using `Session#close` method which releases all the acquired resources in CameraKit safe manner. 

The basic use of CameraKit and its lifecycle can be presented as:

![usage_lifecycle](.doc/usage_lifecycle.png)

### Java or Kotlin?

The base `camerakit-partner` module is designed to be fully Java compatible therefore it does not require Kotlin standard library nor its toolchain to be available in pure Java projects. On the other hand, Kotlin projects are advised to use the `camerakit-kotlin` for official extensions. 

Here is an example of applying a lens with CameraKit in Java:

```java
public final class BasicActivity extends AppCompatActivity implements LifecycleOwner {

    private Session cameraKitSession;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // CameraKit support implementation of ImageProcessor that is backed by CameraX library:
        // https://developer.android.com/training/camerax
        CameraXImageProcessorSource imageProcessorSource = new CameraXImageProcessorSource( 
            this /*context*/, this /*lifecycleOwner*/
        );
        imageProcessorSource.startPreview(true /*cameraFacingFront*/);

        cameraKitSession = Sessions.newBuilder(this)
                .imageProcessorSource(imageProcessorSource)
                .attachTo(findViewById(R.id.camerakit_stub))
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraKitSession.getLenses().getRepository().get(new Available("1"), available -> {
            Log.d(TAG, "Available lenses: " + available);
            Lenses.whenHasFirst(available, lens -> cameraKitSession.getLenses().getProcessor().apply(lens, result -> {
                Log.d(TAG,  "Apply lens [" + lens + "] success: " + result);
            }));
        });
    }

    @Override
    protected void onPause() {
        cameraKitSession.getLenses().getProcessor().clear(result -> {
            Log.d(TAG,  "Clear lenses success: " + result);
        });
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cameraKitSession.close();
        super.onDestroy();
    }
}
```
