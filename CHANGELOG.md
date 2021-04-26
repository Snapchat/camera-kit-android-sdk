# CHANGELOG

<a name="unreleased"></a>
## [Unreleased]

<a name="1.7.0"></a>
## [1.7.0] - 2021-04-26

<a name="1.6.0"></a>
## [1.6.0] - 2021-04-23
### Features
- **Android:**  Add support for client defined safe render area
- **Android:**  Add Media Picker support for sample app
- **Android:**  Switch to ARCore for surface tracking in the sample app
- **Android:**  SnapButtonView responds to volume up events to start capture
- **Android:**  Dialog to update lens group IDs in the sample app
- **Android:**  SnapButtonView re-dispatch touch events to lenses carousel
- **Android:**  Landmarker lenses support
- **iOS:**  Landmarkers support
- **iOS:**  Media picker support
- **iOS:**  Safe area support


<a name="1.5.11"></a>
## [1.5.11] - 2021-03-17

<a name="1.5.10"></a>
## [1.5.10] - 2021-03-03
### Bug Fixes
- **Android:**  Negotiate MediaCodec supported resolution when video recording


<a name="1.5.9"></a>
## [1.5.9] - 2021-02-26

<a name="1.5.8"></a>
## [1.5.8] - 2021-02-24
### Features
- **Android:**  Expose outputRotationDegrees parameter for photo processing


<a name="1.5.7"></a>
## [1.5.7] - 2021-02-18
### Features
- **Android:**  Better accessibility support


<a name="1.5.6"></a>
## [1.5.6] - 2021-02-03
### Bug Fixes
- **Android:**  Lens Single Tap should work without touch blocking


<a name="1.5.5"></a>
## [1.5.5] - 2021-01-26
### Bug Fixes
- **Android:**  OpenGL memory leak after Session is closed


<a name="1.5.4"></a>
## [1.5.4] - 2021-01-15
### Features
- **Android:**  Expose lens loading overlay configuration


<a name="1.5.3"></a>
## [1.5.3] - 2021-01-06
### Bug Fixes
- **Android:**  Crash when client includes grpc-census library
- **iOS:**  FileHandle exceptions and lens processor crash


<a name="1.5.2"></a>
## [1.5.2] - 2020-12-22
### Bug Fixes
- **Android:**  Fix carousel actions being ignored after re-activation


<a name="1.5.1"></a>
## [1.5.1] - 2020-12-22
### Features
- **Android:**  Add ability to clear ImageProcessor.Output on disconnect


<a name="1.5.0"></a>
## [1.5.0] - 2020-12-03
### Bug Fixes
- **Android:**  Dynamic Plugin class loading is not reliable
- **iOS:**  CarouselView crashing sometimes when swiping

### Features
- **Android:**  Use externally published Plugin interface for dynamic loading
- **iOS:**  Add first frame ready event to processor observer


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
- **Android:**  Dynamic Plugin class loading is not reliable
- **Android:**  Missing lenses carousel center icon
- **Android:**  Better portrait orientation support
- **iOS:**  Lock orientation when recording
- **iOS:**  Carousel sometimes resetting transform on reloading data
- **iOS:**  ARKit video is stretched
- **iOS:**  Image hints are present in videos
- **iOS:**  Some lenses won't download after the internet is back
- **iOS:**  Bundled hints not localizing properly if host app doesn't support localization
- **iOS:**  Recorded video frozen when returning from background

### Features
- **Android:**  Use externally published Plugin interface for dynamic loading
- **Android:**  Customize lenses carousel with custom item positions
- **Android:**  Expose API to disable default camera preview rendering
- **Android:**  Expose lens preview model
- **Android:**  Use exposed lenses carousel API to implement lens button
- **Android:**  Improve dynamic loading sample plugin example
- **Android:**  Camera zoom support example
- **iOS:**  Add LiDAR support
- **iOS:**  Improve AVSessionInput camera performance
- **iOS:**  Expose lens preview model


<a name="1.3.6"></a>
## [1.3.6] - 2020-11-04
### Bug Fixes
- **Android:**  Missing lens placeholder icon
- **iOS:**  Lens repo sometimes returning stale data

### Features
- **Android:**  Added Configuration for Processor to support different input frame rotation behaviors


<a name="1.3.5"></a>
## [1.3.5] - 2020-10-20
### Bug Fixes
- **Android:**  Missing lenses carousel center icon

### Features
- **Android:**  Customize lenses carousel with custom item positions and activation flow


<a name="1.3.4"></a>
## [1.3.4] - 2020-10-15
### Features
- **Android:**  Expose lens preview model
- **iOS:**  Expose lens preview model


<a name="1.3.3"></a>
## [1.3.3] - 2020-10-15
### Bug Fixes
- **Android:**  Crash when user app targets API level 30 on Android Q (11) devices


<a name="1.3.2"></a>
## [1.3.2] - 2020-10-15

<a name="1.3.1"></a>
## [1.3.1] - 2020-10-09
### Bug Fixes
- **Android:**  Better portrait orientation support
- **Android:**  Crash after required permissions accepted
- **iOS:**  Some lenses won't download after the internet is back
- **iOS:**  Race condition sometimes when retrying requests due to no internet
- **iOS:**  Requests sometime failing if app is open for too long

### Features
- **Android:**  Improve dynamic loading sample plugin example
- **Android:**  Expose API to disable default camera preview rendering
- **Android:**  Dynamic feature-as-a-plugin example


<a name="1.3.0"></a>
## [1.3.0] - 2020-09-25
### Features
- **Android:**  Support photo API captured image processing
- **Android:**  Support dynamic feature loading


<a name="1.2.0"></a>
## [1.2.0] - 2020-08-27
### Bug Fixes
- **Android:**  Processed texture interpolation artifacts when resized
- **Android:**  OpenGL out of memory crash
- **Android:**  Lenses Processor apply callback not invoked

### Features
- **Android:**  Add instrumentation test helpers
- **Android:**  Invalidate metadata cache on cold-start when network is available
- **Android:**  Add ability to check if device is supported
- **Android:**  Reapply lens with launch data if available
- **Android:**  Add x86/x86_64 support
- **Android:**  Progress cycle repeat parameters for SnapButtonView
- **iOS:**  Invalidate metadata cache on cold-start when network is available


<a name="1.1.0"></a>
## [1.1.0] - 2020-07-29
### Features
- **Android:**  Add support for dynamic lens launch data
- **Android:**  Add ability to provide ImageProcessor.Output rotation
- **Android:**  Add post capture preview screen
- **Android:**  Add support to provide user data
- **iOS:**  Add support for dynamic lens launch data
- **iOS:**  Expose user data provider


<a name="1.0.0"></a>
## [1.0.0] - 2020-07-08
### Bug Fixes
- **Android:**  Memory leaks caused by delayed operations
- **Android:**  Handle/abort connection to invalid output surface

### Features
- **Android:**  Offline lens repository support
- **Android:**  Add support for prefetching lenses content
- **Android:**  Add support for lens hints
- **Android:**  Expose Lens vendor data
- **iOS:**  Expose vendor data
- **iOS:**  Add lens prefetcher support
- **iOS:**  Add support for ARKit
- **iOS:**  Add support for localized hints


<a name="0.5.0"></a>
## [0.5.0] - 2020-06-03
### Bug Fixes
- **Android:**  Remove 3rd-party R classes jars from the SDK binary


<a name="0.4.0"></a>
## [0.4.0] - 2020-04-22
### Bug Fixes
- **iOS:**  Original lens should be active on app opening
- **iOS:**  First lens doesn't work on fresh install
- **iOS:**  Carousel in landscape is not aligned
- **iOS:**  Fix carousel ux: close button goes to empty lens

### Features
- **Android:**  Audio processing (analysis and effects) support
- **Android:**  Use lens lifecycle events to update camera UI
- **Android:**  Add support for internal cache configuration
- **Android:**  Integrate SnapButtonView for photo/video capture
- **iOS:**  Add processor observer
- **iOS:**  Add share to Snapchat
- **iOS:**  Capture and image preview support
- **iOS:**  Add support for remote assets and unbundle tracking data
- **iOS:**  Add sample video preview


<a name="0.3.0"></a>
## [0.3.0] - 2020-03-30
### Bug Fixes
- **Android:**  Allow simultaneous touch handling while recording
- **Android:**  Picture/video sharing does not work on Android 10
- **Android:**  Notify lenses list change once network is available
- **iOS:**  Correct effect viewport and aspect ratio for lenses

### Features
- **Android:**  Integrate provided lenses carousel
- **Android:**  Add video/picture capture support
- **iOS:**  Restructure Repository API
- **iOS:**  Add video recording support
- **iOS:**  Add sample UI and migrate CameraViewController to reference UI
- **iOS:**  Add snap camera button
- **iOS:**  Add Carousel


<a name="0.2.0"></a>
## [0.2.0] - 2020-02-27
### Bug Fixes
- **Android:**  Shutdown CameraKit when app ID is unauthorized
- **Android:**  Restart lens tracking on single tap gesture
- **Android:**  Audio playback continues when app is in background

### Features
- **Android:**  Display loading overlay as lens downloads
- **Android:**  Add support for remote lens metadata and content
- **iOS:**  Add support for remote lens metadata and content


<a name="0.1.0"></a>
## 0.1.0 - 2020-02-12
### Bug Fixes
- **Android:**  Add missing application ID
- **iOS:**  Re-apply lens when entering foreground

### Features
- **Android:**  Add version information to the side menu of sample app
- **Android:**  Save applied lens ID and camera facing in instance state
- **Android:**  Add camera flip button
- **Android:**  Open side drawer on lens button click
- **Android:**  Add next/previous lens buttons to the sample app ([#8](https://github.sc-corp.net/Snapchat/camera-kit-distribution/issues/8))
- **Android:**  Use Lens name in side bar listing
- **iOS:**  Add Camera Flip Button
- **iOS:**  add prev next buttons to flip between lenses ([#6](https://github.sc-corp.net/Snapchat/camera-kit-distribution/issues/6))
- **iOS:**  use lens name property ([#3](https://github.sc-corp.net/Snapchat/camera-kit-distribution/issues/3))

