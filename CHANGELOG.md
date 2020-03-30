# CHANGELOG

<a name="unreleased"></a>
## [Unreleased]

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

