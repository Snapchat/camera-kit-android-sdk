# CHANGELOG

<a name="unreleased"></a>
## [Unreleased]

<a name="1.4.0"></a>
## [1.4.0] - 2020-09-25

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

