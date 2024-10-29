
<div align="center">

![camerakit_icon](https://github.com/user-attachments/assets/d16b846d-0b4f-48da-bc18-6a40ddc3cb26)

# Camera Kit for iOS

[Home Page](https://developers.snap.com/camera-kit/home) | [Documentation & Guides](https://developers.snap.com/camera-kit/getting-started/what-is-camera-kit) | [Showcase](https://ar.snap.com/camera-kit) | [API Reference](https://kit.snapchat.com/reference/CameraKit/ios/1.34.0/index.html) | [Developer Portal](https://kit.snapchat.com/manage/) | [My Lenses](https://my-lenses.snapchat.com/) | [Discord](https://discord.gg/snapar)
</div>

Camera Kit brings the power of Snap's AR platform to your websites and mobile apps on iOS and Android. It has never been easier to create and deliver scalable, multi-platform AR experiences to meet your customers, wherever they are.

<p align="center">
 <img src="https://github.com/user-attachments/assets/c7a6e407-ee76-4dd6-b174-4ac03f641131" width="9%" alt="distort" />
 <img src="https://github.com/user-attachments/assets/8285ec1d-8b3a-4d1d-a7d2-db62b16d7ee3" width="9%" alt="hair_simulation" /> 
 <img src="https://github.com/user-attachments/assets/8530eb66-567c-4432-958d-15285d50d6cb" width="9%" alt="chane_physics" />
 <img src="https://github.com/user-attachments/assets/4af949f9-9426-413d-8011-0292278106ea" width="9%" alt="try_on" />
 <img src="https://github.com/user-attachments/assets/b79dff9b-34cd-4949-8c8f-fa46399d5351" width="9%" alt="3d_hand_tracking" /> 
 <img src="https://github.com/user-attachments/assets/ff32ab27-e48d-4aed-aa1a-8f46726e5b0b" width="9%" alt="wrist_wear_try_on" />
 <img src="https://github.com/user-attachments/assets/dff811af-b7b4-4e86-be28-d149b4860e5b" width="9%" alt="eye_wear_try_on" />
 <img src="https://github.com/user-attachments/assets/6005c5ed-ad31-45c6-8fad-90a388724ec0" width="9%" alt="true_size_object" />   
 <img src="https://github.com/user-attachments/assets/2ed8522c-280a-4694-bc3e-79ce450fb0a0" width="9%" aly="vfx">
 <img src="https://github.com/user-attachments/assets/c4097e49-855f-4c94-8a35-2753f7bcbd83" width="9%" alt="landmarkers" />
</p>

## Features

### AR Capabilities
- Face Effects
- Body / Face / Hand Tracking
- World Tracking
- Background Segmentation
- Location AR
- and [many more](https://developers.snap.com/camera-kit/ar-content/ar-overview) 

### iOS SDK
- Integrate with AVCaptureSession, which allows to maintain full control over session configuration, management, and lifecycle
- Fetch and display your lenses
- Capture media
- Leverage Reference UI modules to quickly build Camera Kit based experiences
- Supports iOS 13.0+

## Integration Steps
1. [Setup](https://developers.snap.com/camera-kit/getting-started/setting-up-accounts) your Camera Kit application using [Developer Portal](https://kit.snapchat.com/manage)
2. [Integrate](https://developers.snap.com/camera-kit/integrate-sdk/mobile/ios) Camera Kit SDK into your iOS application
3. [Create](https://developers.snap.com/camera-kit/ar-content/build-lenses) AR experiences using [Lens Studio](https://ar.snap.com/lens-studio) and manage them using [My Lenses](https://my-lenses.snapchat.com/) site

## Modules

A core module that handles AR experiences:
- `SCSDKCameraKit`

A set of [open-sourced](./Sources), reusable UI components and utilities for easier development of Camera Kit based experiences:
- `SCSDKCameraKitReferenceUI`
- `SCSDKCameraKitReferenceSwiftUI`

Modules required for testing your in-development lenses (see [Push-to-Device](https://developers.snap.com/camera-kit/guides/mobile-customization/in-app-lens-testing)):
- `SCSDKCameraKitBaseExtension`
- `SCSDKCameraKitLoginKitAuth`
- `SCSDKCameraKitPushToDeviceExtension`

## Installation
### [Swift Package Manager](https://github.com/apple/swift-package-manager)
1. In Xcode, select "File" → "Swift Packages" → "Add Package Dependency"
2. Enter the repository URL: `https://github.com/Snapchat/camera-kit-ios-sdk.git`
3. Select the version and modules you want to use in your project

### [CocoaPods](https://cocoapods.org/)
If you use CocoaPods to manage your dependencies, simply add the following to your `Podfile`:
```ruby
pod 'SCCameraKit'
```

If you'd like to use the Reference UI:

```ruby
pod 'SCCameraKitReferenceUI'
pod 'SCCameraKitReferenceSwiftUI'
```

### Manual
1. Download the binaries from the [Releases](https://github.com/Snapchat/camera-kit-ios-sdk/releases) section
2. Unzip and add the Camera Kit SDK .xcframework to your Xcode project
3. In your target's "General" settings, add the framework to the "Frameworks, Libraries, and Embedded Content" section

## Usage
You can find a more detailed tutorial [here](https://developers.snap.com/camera-kit/guides/tutorials/mobile-tutorials/building-your-first-ios-camera-kit-app)

### Initializing Camera Kit
Obtain an API token for your Camera Kit application on [Developer Portal](https://kit.snapchat.com/manage)

```swift
import SCSDKCameraKit

let sessionConfig = SessionConfig(apiToken: "<YOUR_API_TOKEN>")
let lensesConfig = LensesConfig(cacheConfig: CacheConfig(lensContentMaxSize: 150*1024*1024))
        
let cameraKit = Session(sessionConfig: sessionConfig, lensesConfig: lensesConfig, errorHandler: self)
```

### Implementing Camera View
```swift
// Configure the camera preview view
let previewView = PreviewView()
previewView.automaticallyConfiguresTouchHandler = true
cameraKit.add(output: previewView)

// Configure the capture session
let captureSession = AVCaptureSession()
let input = AVSessionInput(session: captureSession)
let arInput = ARSessionInput()        
cameraKit.start(input: input, arInput: arInput)

DispatchQueue.global(qos: .background).async {
    input.startRunning()
}
```

### Applying AR Lens
You can find lens group IDs and specific lens IDs on [My Lenses](https://my-lenses.snapchat.com/) site
```swift
// Observe a particular lens
cameraKit.lenses.repository.addObserver(self, specificLensID: "<LENS_ID>", inGroupID: "<LENS_GROUP_ID>")

// Implement LensRepositorySpecificObserver to receive observed Lens object
extension MyViewController: LensRepositorySpecificObserver {
    func repository(_ repository: LensRepository, didUpdate lens: Lens, forGroupID groupID: String) {
        lensQueue.async { [weak self] in
            guard let self = self else { return }

            // Apply a lens, which makes it visible on previewView
            self.cameraKit.lenses.processor?.apply(lens: lens, launchData: nil) { success in
                if success {
                    print("\\(lens.name ?? "Unnamed") (\\(lens.id)) Applied")
                } else {
                    print("Lens failed to apply")
                }
            }
        }
    }
    
    func repository(_ repository: LensRepository, didFailToUpdateLensID lensID: String, forGroupID groupID: String, error: Error?) {
        // Handle errors
    }
}
```

## Samples
We provide several sample apps that demonstrate different approaches to integrating the Camera Kit SDK:
- [CameraKitBasicSample](./Samples/CameraKitBasicSample) implements the bare minimum to show a single lens
- [CameraKitSample](./Samples/CameraKitSample) contains a fully functioning camera capture with lenses and preview flow
- [CameraKitAlternateCarouselSample](./Samples/CameraKitAlternateCarouselSample) demonstrates how to build your own carousel and preview screen

## License
Camera Kit for iOS is available under a custom license. See the [LICENSE](./LICENSE) file for more info.

## Contributing
We welcome contributions to the open-sourced parts of Camera Kit for iOS - Reference UI and Sample Apps. Feel free to open issues and submit PRs.

## Changelog
See [CHANGELOG.md](CHANGELOG.md) for details on each release.
