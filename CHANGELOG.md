# CHANGELOG

All notable changes to the Camera Kit SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and the Camera Kit SDK adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

<a name="unreleased"></a>
## [Unreleased]

<a name="1.45.0"></a>
## [1.45.0] - 2025-11-13
### Features
- Lens Studio 5.16 support

### Updates
- A Lens WarmUp API to preload a lens without rendering it

### Bug Fixes
- Fixed an issue that allowed Surfaces usage after the Session had been released in Camera Kit

<a name="1.44.0"></a>
## [1.44.0] - 2025-10-17
### Features
- Lens Studio 5.15 support

<a name="1.43.0"></a>
## [1.43.0] - 2025-09-08
### Features
- Lens Studio 5.13 support

<a name="1.42.0"></a>
## [1.42.0] - 2025-07-23
### Features
- Lens Studio 5.12 support

### Updates
- 16KB page size support

<a name="1.41.0"></a>
## [1.41.0] - 2025-06-23
### Features
- Lens Studio 5.11 support

### Bug Fixes
- Fixed memory leak related to lens keyboard
- **Android/iOS:** Minor fixes

<a name="1.40.0"></a>
## [1.40.0] - 2025-04-03
### Features
- Lens Studio 5.8 support

### Updates
- **Android/iOS:** Improvement for HTTP request validation, now accepting empty optional parameters
<a name="1.39.0"></a>
## [1.39.0] - 2025-03-05
### Features
- Lens Studio 5.7 support
<a name="1.38.0"></a>
## [1.38.0] - 2025-02-05
### Features
- Lens Studio 5.6 support

### Updates
- Addition of Jetpack compose basic sample app

<a name="1.37.0"></a>
## [1.37.0] - 2024-12-18
### Features
- Lens Studio 5.4 support
- Support for Lens HTTP requests

<a name="1.36.1"></a>
## [1.36.1] - 2024-11-22
<a name="1.36.0"></a>
## [1.36.0] - 2024-11-14
### Features
- Lens Studio 5.3 support

### Updates
- Updated basic sample application to apply single specified Lens
- Optimized Camera Kit Session initialization for better performance
- Launched a dedicated Android repository: https://github.com/Snapchat/camera-kit-android-sdk
<a name="1.35.0"></a>
## [1.35.0] - 2024-10-16
### Features
- Lens Studio 5.2 support

<a name="1.34.0"></a>
## [1.34.0] - 2024-09-17
### Features
- Lens Studio 5.1 support

<a name="1.33.0"></a>
## [1.33.0] - 2024-08-21
### Features
- Lens Studio 5.0.19 support

<a name="1.32.0"></a>
## [1.32.0] - 2024-07-24
### Features
- Lens Studio 5.0.17 support

<a name="1.31.0"></a>
## [1.31.0] - 2024-06-12
### Features
- Lens Studio 5.0.14 support

### Updates
- New `camerakit-sample-basic` sample app demonstrates a simplistic way to integrate Camera Kit into your application.
<a name="1.30.0"></a>
## [1.30.0] - 2024-04-24
### Features
- Lens Studio 5.0.10 support
### Updates
- Added new `LensPushToDeviceService#initiatePairing` and `LensPushToDeviceService#unpair` methods to pair and unpair Push-to-Device without using default UI.
<a name="1.29.0"></a>
## [1.29.0] - 2024-03-20
- Fix for incorrect processing size that sometimes resulted in pixelated image on some of the Android devices
<a name="1.28.0"></a>
## [1.28.0] - 2024-02-08
### Bug Fixes
- Fix an issue when `LensesComponent.Processor#clear()` doesn't remove a lens while using custom UI.
- Fix a crash when using Push-To-Device.

<a name="1.27.0"></a>
## [1.27.0] - 2023-12-13
### Features
- Lens Studio 5.0.2 support

### Updates
- Addressed behavior changes for apps targetting Android 14 or higher ([behavior-changes-14](https://developer.android.com/about/versions/14/behavior-changes-14)).

<a name="1.26.2"></a>
## [1.26.2] - 2023-11-13
<a name="1.26.1"></a>
## [1.26.1] - 2023-10-12
### Bug Fixes
- Lens elements that use overlay render target may be get incorrectly positioned when camera input does not match preview output aspect ratio
- Lens audio playback is not muted when app is paused
- [HIGH_SAMPLING_RATE_SENSORS](https://developer.android.com/reference/android/Manifest.permission#HIGH_SAMPLING_RATE_SENSORS) permission declaration is missing in the core SDK manifest
- Early display rotation notification causing incorrect input processing size calculation on Android 14

<a name="1.26.0"></a>
## [1.26.0] - 2023-09-13 - _LTS_
### Features
- Lens Studio 4.55 support

### Updates
- Helper method to record `ImageProcessor` output into a video file, `connectOutput(file: File, width: Int, height: Int, captureAudio: Boolean)`, has been moved into a separate Maven artifact,  `com.snap.camerakit:support-media-recording`. This artifact can now be excluded from an app&#39;s dependencies if the provided video recording functionality is not needed. Note that the `com.snap.camerakit:support-camerax`  and  `com.snap.camerakit:support-arcore` now depend on this new artifact transitively in order to implement the `com.snap.camerakit.support.camera.AllowsVideoCapture` interface.
- Helper method to create an instance of `Source<MediaProcessor>` based on `android.provider.MediaStore`, `mediaStoreSourceFor(context: Context, executorService: ExecutorService): Source<MediaProcessor>`, has been moved into a separate Maven artifact, `com.snap.camerakit:support-media-picker-source`. This artifact can now be excluded from an app&#39;s dependencies if the provided media source functionality is not needed. Note that the `com.snap.camerakit:support-camera-layout` now depends on this new artifact transitively in order to obtain media for media picker lenses.
- Default lenses carousel UI has been removed from the core `com.snap.camerakit:camerakit` artifact. Instead, `com.snap.camerakit:support-lenses-carousel` artifact should be added to app dependencies to use default lenses carousel UI. Note that the `com.snap.camerakit:support-camera-layout` now depends on this artifact transitively in order to show lenses carousel UI.
- Default media picker UI has been moved from the core `com.snap.camerakit:camerakit` artifact, and moved into a separate Maven artifact, `com.snap.camerakit:support-media-picker-ui`. This new artifact should be added to app dependencies to use default media picker UI. Note that the `com.snap.camerakit:support-camera-layout` now depends on this new artifact transitively in order to show media picker UI.

### Known Issues
- Lens UI elements assinged to the overlay render target can get incorrectly positioned when device camera input frame does not match preview preview frame aspect ratio

<a name="1.25.0"></a>
## [1.25.0] - 2023-08-15
### Features
- Lens Studio 4.53 support

### Bug Fixes
- Fix a bug in the ARCore field of view, which was causing poor performance and incorrect object positioning for world tracking lenses
- Fix a bug when the first lens in the carousel has not been applied if `LensesComponent.Carousel.Configuration#disableIdle` set to `true`

<a name="1.24.0"></a>
## [1.24.0] - 2023-06-27
### Features
- Lens Studio 4.49 support
- Add a Profiling Extension to monitor the Camera Kit performance. See [Profiling](./samples/android/Profiling.md).
- Add a new API to get lens snapcode image and deep link URIs, usage example:
```kotlin
session.lenses.repository.get(LensesComponent.Repository.QueryCriteria.Available("lens-group-id")) { result ->
result.whenHasFirst { lens ->
val snapcodePngUri = lens.snapcodes.find { it is LensesComponent.Lens.Media.Image.Png }?.uri
val snapcodeDeepLinkUri = lens.snapcodes.any { it is LensesComponent.Lens.Media.DeepLink }?.uri
}
}
```

### Updates
- Added `android.Manifest.permission.READ_MEDIA_VIDEO` and `android.Manifest.permission.READ_MEDIA_IMAGES` permissions for the
`camerakit-sample-full` and `camerakit-sample-simple` apps. Those are the permissions required to access media files on devices when using the
Media Picker lenses feature.

<a name="1.23.0"></a>
## [1.23.0] - 2023-05-17
### Features
- Lens Studio 4.47 support
- Add a new API to get lens preview sequences - `LensesComponent.Lens.Media.Sequence`, usage example:
```kotlin
session.lenses.repository.get(LensesComponent.Repository.QueryCriteria.Available("lens-group-id")) { result ->
result.whenHasFirst { lens ->
(lens.previews.find { preview ->
preview is LensesComponent.Lens.Media.Sequence.Webp
} as? LensesComponent.Lens.Media.Sequence.Webp)?.let { webpSequence ->
webpSequence.values.forEach { imageUri ->
// do something with each image
}
}
}
}
```
- Prompt users to install a new ArCore version when available when using lenses that require it

<a name="1.22.0"></a>
## [1.22.0] - 2023-05-08
### Updates
- Deprecate application ID, remove its use across sample apps
- Add a debug dialog to swap API token for testing

### Features
- Lens Studio 4.46 support

### Bug Fixes
- Fix a bug introduced in the `1.18.0` version where lenses exported from the Lens Studio version `4.31` and prior can lead to a crash on Adreno GPU based devices
<a name="1.21.1"></a>
## [1.21.1] - 2023-03-29
<a name="1.21.0"></a>
## [1.21.0] - 2023-03-24
### Features
- Lens Studio 4.43 support

<a name="1.20.0"></a>
## [1.20.0] - 2023-02-21
### Updates
- Remove "Share with Snapchat" placeholder button
- Add examples on how to remove the control strip from the `CameraActivity`

### Features
- Lens Studio 4.40 support
- Add a way to collect Camera Kit diagnostics information on-demand. This feature can be enabled when an issue in Camera Kit is encountered, please reach out to the Camera Kit support for further instructions.

<a name="1.19.2"></a>
## [1.19.2] - 2023-01-12
<a name="1.19.1"></a>
## [1.19.1] - 2023-01-09
### Bug Fixes
- Fix a crash caused by `RejectedExecutionException` after `Session` is closed

<a name="1.19.0"></a>
## [1.19.0] - 2022-12-28
### Features
- Lens Studio 4.36 support
- Add a new sample app that demonstrates a custom implementation of lenses carousel and camera preview layout
### Updates
- Remove deprecated lens icon/preview accessors:
- `LensesComponent.Lens.iconUri` replaced by `icons.find { it is LensesComponent.Lens.Media.Image.Png }?.uri`
- `LensesComponent.Lens.preview` replaced by `previews.find { it is LensesComponent.Lens.Media.Image.Webp }?.uri`

### Bug Fixes
- Fix occasional camera preview freeze in `CameraXImageProcessorSource` when switching from an ARCore based camera preview source

<a name="1.18.1"></a>
## [1.18.1] - 2022-11-30
### Bug Fixes
- Fix an issue where touch gestures are not be processed by lenses if no `View` is provided to `LensesComponent.Builder#dispatchTouchEventsTo`

<a name="1.18.0"></a>
## [1.18.0] - 2022-11-21
### Features
- Lens Studio 4.34 support
- [Custom Location AR](https://docs.snap.com/lens-studio/references/templates/landmarker/custom-landmarker) (Beta feature) support

### Updates
- Snap attribition support as per [Design Guidelines](https://docs.snap.com/snap-kit/camera-kit/release/design-guide)
- Staging Watermark applies on staging builds of Camera Kit integrations
- Update CameraX dependencies to 1.1.0
- Update sample app project to the latest Gradle/AGP 7+ and Kotlin 1.6.21 versions

### Bug Fixes
- Fix crash due to exceeded number of listeners registered via `TelephonyRegistryManager`

<a name="1.17.1"></a>
## [1.17.1] - 2022-11-30
### Bug Fixes
- Fix an issue where touch gestures are not be processed by lenses if no `View` is provided to `LensesComponent.Builder#dispatchTouchEventsTo`

<a name="1.17.0"></a>
## [1.17.0] - 2022-10-12
### Features
- Lens Studio 4.31 support
- Add support for City-Scale AR Lenses (Beta)
- Add support for Push-to-Device (P2D) feature (Beta), which allows developers to send Lenses from Lens Studio to their Camera Kit application for testing. Note that on Android, P2D is only supported if your application uses the built-in lenses carousel.
- Expose new API to obtain WebP lens icon resources, switch the built-in lenses carousel to use it by default. Note that PNG lens icon resources are deprecated, to be removed in 1.19.0.

### Bug Fixes
- Fix an issue causing ArCore camera freeze

<a name="1.16.0"></a>
## [1.16.0] - 2022-09-09
### Features
- Lens Studio 4.28.x support
- Add support for Connected Lenses (Closed Beta)
<a name="1.15.1"></a>
## [1.15.1] - 2022-07-20
### Bug Fixes
- Fix crash when switching ArCore powered lenses

<a name="1.15.0"></a>
## [1.15.0] - 2022-07-18
### Notes
- This version has critical issues on Android. Use version 1.15.1 instead

### Features
- Lens Studio 4.25 support
- New method to apply a lens while resetting its state if the lens was applied already. Useful for cases where app resume from background or other screen should reset lens state matching Snapchat-like behavior. Usage example:
`session.lenses.processor.apply(lens, reset = true)`

### Bug Fixes
- Improve ARCore performance
- Fix possible crash when internal remote service is not available

<a name="1.14.1"></a>
## [1.14.1] - 2022-06-30
### Bug Fixes
- Fix critical issues with lenses configuration introduced in 1.14.0

<a name="1.14.0"></a>
## [1.14.0] - 2022-06-27
### Notes
- This version has critical issues on Android. Use version 1.14.1 instead

### Features
- Lens Studio 4.22 support
- Add support for lenses with static assets
- New API to obtain the current version of the Camera Kit SDK
<a name="1.13.0"></a>
## [1.13.0] - 2022-05-27
### Features
- New API to support lenses which use the remote service [feature](https://docs.snap.com/lens-studio/references/guides/lens-features/remote-apis/remote-service-module)
- New tone-mapping and portrait camera adjustments
- Add support for ring flash mode for front-facing camera flash
### Bug Fixes
- Add missing permission HIGH_SAMPLING_RATE_SENSORS for host-apk dynamic sample
- Fix processed bitmap rotation when no lens is applied

<a name="1.12.0"></a>
## [1.12.0] - 2022-04-22
### Notes
- Starting with this release, an API token **must** be provided as part of the Camera Kit configuration, failure to do so will result in a runtime exception. See [Android](https://docs.snap.com/snap-kit/camera-kit/configuration/android#service-authorization) and [iOS](https://docs.snap.com/snap-kit/camera-kit/configuration/ios/#service-authorization) documentation for examples on how to obtain and provide an API token.
- The legal agreement prompt has been updated to use a more user friendly text copy. Updating to this release will result in users needing to accept the updated prompt which includes a new link to the Camera Kit&#39;s "learn more" [page](https://support.snapchat.com/en-US/article/camera-information-use).

### Features
- Lens Studio 4.19 support
- Add dynamic feature loading (DFM) reference sample app
- New `ImageProcessor.Input.Option.Crop` which allows to specify the crop region that should be applied to each frame before processing
- `CameraXImageProcessorSource#startPreview` takes aspect ratio and crop option parameters
- Further binary size reduction of about 500KB

### Bug Fixes
- Missing `android.permission.ACCESS_COARSE_LOCATION` permission added to the `camerakit-support-gms-location` artifact to support apps targeting Android API 31
- Image capture of certain lenses results in an unexpected alpha channel
- Race condition of incorrectly evicting currently applied lens content from cache while prefetching other lenses
### Known Issues
- Lenses using the new [Text to Speech](https://docs.snap.com/lens-studio/references/guides/lens-features/audio/text-to-speech) feature throw a runtime exception on Android and simply do nothing on iOS. This is expected as the feature is currently unavailable in Camera Kit.

<a name="1.11.1"></a>
## [1.11.1] - 2022-04-05
### Bug Fixes
- Fix bug where landmarkers would not work properly

<a name="1.11.0"></a>
## [1.11.0] - 2022-03-14
### Features
- Add support for text input in lenses
- Lens Studio 4.16 support

<a name="1.10.0"></a>
## [1.10.0] - 2022-02-28
### Bug Fixes
- Use consistent directory names for files related to Camera Kit
- Certain emulator images fail to render lenses

### Features
- Expose new API to switch camera facing based on lens facing preference

<a name="1.9.2"></a>
## [1.9.2] - 2022-02-10
### Bug Fixes
- Remove R8 specific consumer rules to support legacy Proguard builds
- Fix race conditions during face detection in the default Media Picker

<a name="1.9.1"></a>
## [1.9.1] - 2022-01-26
### Bug Fixes
- Don't start LegalPromptActivity if the legal prompt is already accepted
- Remote service calls fail after `Session` is used for more than 60 seconds
<a name="1.9.0"></a>
## [1.9.0] - 2022-01-18
### Features
- Lens Studio 4.13 support
- Persist custom lens groups in sample app
### Bug Fixes
- Custom `Source<ImageProcessor>>` is not respected in `CameraLayout`
- Warn if no API token is provided
<a name="1.8.4"></a>
## [1.8.4] - 2022-01-14
<a name="1.8.3"></a>
## [1.8.3] - 2022-01-12
### Bug Fixes
- Fix sharing captured media in sample app for some Android OS versions
- Eliminate native libraries binary size regression
- Extension fail to register when Kotlin reflect library is included
- Remove unused code which gets flagged as [zip path traversal vulnerability](https://support.google.com/faqs/answer/9294009)
### Features
- Expose a method to observe LegalProcessor results
- Flash functionality in `CameraLayout` and `CameraXImageProcessorSource`
<a name="1.8.2"></a>
## [1.8.2] - 2021-12-15
### Bug Fixes
-  Missing localized strings
- Lenses using ML features crash when app targets Android 12 (API level 31)
- Crop ARCore video to screen size by default

<a name="1.8.1"></a>
## [1.8.1] - 2021-12-09
### Bug Fixes
-  Too-large images fail to load in media picker
-  Lens content downloads use non-optimal CDN links
<a name="1.8.0"></a>
## [1.8.0] - 2021-12-07
### Bug Fixes
- Add audio recording permission check for Custom Video Sample app
- Rendering performance improvement
- Add thread monitoring and safety to video sample
### Features
- Legal agreement prompt pop-up dialog support
- Rotation detection for continuous focus
- Tap-To-Focus support
- Support API token based authorization
- Lenses audio mute/unmute support
- Add sample app for custom implementation of audio and video recording
<a name="1.7.6"></a>
## [1.7.6] - 2021-11-08
### Bug Fixes
-  Extension API mismatch

<a name="1.7.5"></a>
## [1.7.5] - 2021-10-28
### Bug Fixes
- Kotlin Intrinsics leak into the public Plugin API
- CameraLayout treats optional permissions as required
### Features
-  Lens Studio 4.7 support

<a name="1.7.4"></a>
## [1.7.4] - 2021-10-20
### Bug Fixes
- Fix an issue introduced in 1.7.1 that downgraded SDK performance

<a name="1.7.3"></a>
## [1.7.3] - 2021-10-11
<a name="1.7.2"></a>
## [1.7.2] - 2021-10-07
<a name="1.7.1"></a>
## [1.7.1] - 2021-10-01
<a name="1.7.0"></a>
## [1.7.0] - 2021-09-22
### Bug Fixes
- Fix touch re-dispatch when lenses carousel de-activated
- Fix multiple startPreview leading to a crash in CameraX
### Features
- CameraActivity for simple use cases
- CameraLayout support view for simplified integration
- Lenses Carousel reference UI
- Gallery media source support for the MediaProcessor
- Enable/disable SnapButtonView based on lens download status
- Added SRE metrics
<a name="1.6.21"></a>
## [1.6.21] - 2021-10-11
<a name="1.6.20"></a>
## [1.6.20] - 2021-10-07
<a name="1.6.19"></a>
## [1.6.19] - 2021-10-01
<a name="1.6.18"></a>
## [1.6.18] - 2021-09-29
### Bug Fixes
-  Landmarkers localisation issues

<a name="1.6.17"></a>
## [1.6.17] - 2021-09-22
### Bug Fixes
- Face tracking issues introduced in 1.6.15

<a name="1.6.16"></a>
## [1.6.16] - 2021-09-20
<a name="1.6.15"></a>
## [1.6.15] - 2021-09-14
### Features
-  LensStudio 4.5 support

<a name="1.6.14"></a>
## [1.6.14] - 2021-09-03
### Bug Fixes
-  Stability issues

<a name="1.6.13"></a>
## [1.6.13] - 2021-09-02
<a name="1.6.12"></a>
## [1.6.12] - 2021-08-17
### Bug Fixes
- Lenses Carousel does not appear on some devices
- Avoid reading cache size config on the Main thread

<a name="1.6.11"></a>
## [1.6.11] - 2021-08-06
### Bug Fixes
- Lens processing failure after image/video capture
- SurfaceTexture based Output Surface leak

<a name="1.6.10"></a>
## [1.6.10] - 2021-07-23
### Bug Fixes
- Too broad Proguard rule for GMS FaceDetector

<a name="1.6.9"></a>
## [1.6.9] - 2021-07-19
<a name="1.6.8"></a>
## [1.6.8] - 2021-07-13
### Bug Fixes
- Fix Surface not released if Output connection is cancelled

<a name="1.6.7"></a>
## [1.6.7] - 2021-07-08
### Bug Fixes
- Increase max lenses content size
- Late input connection leads to no processed frames
<a name="1.6.6"></a>
## [1.6.6] - 2021-06-22
<a name="1.6.5"></a>
## [1.6.5] - 2021-06-17
### Bug Fixes
- Lens localized hint strings are cached incorrectly
- Incorrect lens download status

<a name="1.6.4"></a>
## [1.6.4] - 2021-06-16
<a name="1.6.3"></a>
## [1.6.3] - 2021-06-16
### Bug Fixes
- Carousel accessibility improvements

<a name="1.6.1"></a>
## [1.6.1] - 2021-05-10
### Bug Fixes
- Lens is not applied when carousel&#39;s disableIdle = true
<a name="1.6.0"></a>
## [1.6.0] - 2021-04-26
### Features
- Add support for client defined safe render area
- Add Media Picker support for sample app
- Switch to ARCore for surface tracking in the sample app
- SnapButtonView responds to volume up events to start capture
- Dialog to update lens group IDs in the sample app
- SnapButtonView re-dispatch touch events to lenses carousel
- Landmarker lenses support
<a name="1.5.11"></a>
## [1.5.11] - 2021-03-17

<a name="1.5.10"></a>
## [1.5.10] - 2021-03-03
### Bug Fixes
- Negotiate MediaCodec supported resolution when video recording

<a name="1.5.9"></a>
## [1.5.9] - 2021-02-26

<a name="1.5.8"></a>
## [1.5.8] - 2021-02-24
### Features
- Expose outputRotationDegrees parameter for photo processing

<a name="1.5.7"></a>
## [1.5.7] - 2021-02-18
### Features
- Better accessibility support

<a name="1.5.6"></a>
## [1.5.6] - 2021-02-03
### Bug Fixes
- Lens Single Tap should work without touch blocking

<a name="1.5.5"></a>
## [1.5.5] - 2021-01-26
### Bug Fixes
- OpenGL memory leak after Session is closed

<a name="1.5.4"></a>
## [1.5.4] - 2021-01-15
### Features
- Expose lens loading overlay configuration

<a name="1.5.3"></a>
## [1.5.3] - 2021-01-06
### Bug Fixes
- Crash when client includes grpc-census library
<a name="1.5.2"></a>
## [1.5.2] - 2020-12-22
### Bug Fixes
- Fix carousel actions being ignored after re-activation

<a name="1.5.1"></a>
## [1.5.1] - 2020-12-22
### Features
- Add ability to clear ImageProcessor.Output on disconnect

<a name="1.5.0"></a>
## [1.5.0] - 2020-12-03
### Bug Fixes
- Dynamic Plugin class loading is not reliable
### Features
- Use externally published Plugin interface for dynamic loading
<a name="1.4.5"></a>
## [1.4.5] - 2020-12-01

<a name="1.4.4"></a>
## [1.4.4] - 2020-11-20

<a name="1.4.3"></a>
## [1.4.3] - 2020-11-18

<a name="1.4.2"></a>
## [1.4.2] - 2020-11-17

<a name="1.4.1"></a>
## [1.4.1] - 2020-11-16
### Bug Fixes
- Dynamic Plugin class loading is not reliable
- Missing lenses carousel center icon
- Better portrait orientation support
### Features
- Use externally published Plugin interface for dynamic loading
- Customize lenses carousel with custom item positions
- Expose API to disable default camera preview rendering
- Expose lens preview model
- Use exposed lenses carousel API to implement lens button
- Improve dynamic loading sample plugin example
- Camera zoom support example
<a name="1.3.6"></a>
## [1.3.6] - 2020-11-04
### Bug Fixes
- Missing lens placeholder icon
- Better portrait orientation support
- Missing lenses carousel center icon
- Crash when user app targets API level 30 on Android Q (11) devices
- Crash after required permissions accepted
### Features
- Added Configuration for Processor to support different input frame rotation behaviors
- Customize lenses carousel with custom item positions and activation flow
- Expose lens preview model
- Improve dynamic loading sample plugin example
- Expose API to disable default camera preview rendering
- Dynamic feature-as-a-plugin example
<a name="1.4.0"></a>
## [1.4.0] - 2020-10-28
### Bug Fixes
- Missing lenses carousel center icon
- Better portrait orientation support
### Features
- Customize lenses carousel with custom item positions
- Expose API to disable default camera preview rendering
- Expose lens preview model
- Use exposed lenses carousel API to implement lens button
- Improve dynamic loading sample plugin example
- Camera zoom support example
<a name="1.3.5"></a>
## [1.3.5] - 2020-10-20
### Bug Fixes
- Missing lenses carousel center icon

### Features
- Customize lenses carousel with custom item positions and activation flow

<a name="1.3.4"></a>
## [1.3.4] - 2020-10-15
### Features
- Expose lens preview model
<a name="1.3.3"></a>
## [1.3.3] - 2020-10-15
### Bug Fixes
- Crash when user app targets API level 30 on Android Q (11) devices

<a name="1.3.2"></a>
## [1.3.2] - 2020-10-15

<a name="1.3.1"></a>
## [1.3.1] - 2020-10-09
### Bug Fixes
- Better portrait orientation support
- Crash after required permissions accepted
### Features
- Improve dynamic loading sample plugin example
- Expose API to disable default camera preview rendering
- Dynamic feature-as-a-plugin example

<a name="1.3.0"></a>
## [1.3.0] - 2020-09-25
### Features
- Support photo API captured image processing
- Support dynamic feature loading

<a name="1.2.0"></a>
## [1.2.0] - 2020-08-27
### Bug Fixes
- Processed texture interpolation artifacts when resized
- OpenGL out of memory crash
- Lenses Processor apply callback not invoked

### Features
- Add instrumentation test helpers
- Invalidate metadata cache on cold-start when network is available
- Add ability to check if device is supported
- Reapply lens with launch data if available
- Add x86/x86_64 support
- Progress cycle repeat parameters for SnapButtonView
<a name="1.1.0"></a>
## [1.1.0] - 2020-07-29
### Features
- Add support for dynamic lens launch data
- Add ability to provide ImageProcessor.Output rotation
- Add post capture preview screen
- Add support to provide user data
<a name="1.0.0"></a>
## [1.0.0] - 2020-07-08
### Bug Fixes
- Memory leaks caused by delayed operations
- Handle/abort connection to invalid output surface

### Features
- Offline lens repository support
- Add support for prefetching lenses content
- Add support for lens hints
- Expose Lens vendor data
<a name="0.5.0"></a>
## [0.5.0] - 2020-06-03
### Bug Fixes
- Remove 3rd-party R classes jars from the SDK binary

<a name="0.4.0"></a>
## [0.4.0] - 2020-04-22
### Features
- Audio processing (analysis and effects) support
- Use lens lifecycle events to update camera UI
- Add support for internal cache configuration
- Integrate SnapButtonView for photo/video capture
<a name="0.3.0"></a>
## [0.3.0] - 2020-03-30
### Bug Fixes
- Allow simultaneous touch handling while recording
- Picture/video sharing does not work on Android 10
- Notify lenses list change once network is available
### Features
- Integrate provided lenses carousel
- Add video/picture capture support
<a name="0.2.0"></a>
## [0.2.0] - 2020-02-27
### Bug Fixes
- Shutdown Camera Kit when app ID is unauthorized
- Restart lens tracking on single tap gesture
- Audio playback continues when app is in background

### Features
- Display loading overlay as lens downloads
- Add support for remote lens metadata and content
<a name="0.1.0"></a>
## 0.1.0 - 2020-02-12
### Bug Fixes
- Add missing application ID
### Features
- Add version information to the side menu of sample app
- Save applied lens ID and camera facing in instance state
- Add camera flip button
- Open side drawer on lens button click
- Add next/previous lens buttons to the sample app
- Use Lens name in side bar listing
