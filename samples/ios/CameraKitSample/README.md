# CameraKit iOS

### Usage Philosophy

In general, CameraKit attaches to your existing camera workflow. You are still responsible for configuring and managing an AVCaptureSession, which CameraKit will attach onto. You may start, stop, and reconfigure your session as needed.

## Getting Started

### Requirements

CameraKit requires a minimum of iOS 11, and a 64 bit processor. CameraKit will compile, but not run on a Simulator (due to lack of AVCaptureSession support).

In order to test sharing to Snapchat, make sure you also add your Snap Kit client id `SCSDKClientId` in `Info.plist`

### Dependency Management

CameraKit currently supports CocoaPods.

#### CocoaPods

The `Podfile` should already be setup correctly to reference the camera kit sdk podpsec directory, but if you choose to move the SDK podspec directory somewhere else just be sure to add/modify this line:
`pod 'CameraKit', :path => 'CameraKit'`
where `:path` points to the directory that contains the camera kit podspec.

Before opening the workspace make sure you run
`pod install`
and open `CameraKitSample.xcworkspace`

#### Configure your AVCaptureSession Pipeline

First, create and configure an AVCaptureSession. Apple provides a full-featured [reference](https://developer.apple.com/documentation/avfoundation/cameras_and_media_capture/setting_up_a_capture_session).

For example, if you want to setup a regular capture session for the front facing camera you'd do:
```
let captureSession = AVCaptureSession()
captureSession.beginConfiguration()
guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
    let videoDeviceInput = try? AVCaptureDeviceInput(device: videoDevice),
    captureSession.canAddInput(videoDeviceInput) else { return }
captureSession.addInput(videoDeviceInput)
captureSession.commitConfiguration()
captureSession.startRunning()
```

(PS: don't forget to add `NSCameraUsageDescription` in your Info.plist and make sure you have set up the proper permissions/authorization flows)

#### Configuring the CameraKit Pipeline

CameraKit works similarly to AVCaptureSession – it also has inputs and outputs. We'll create a CameraKit session, and connect it to your existing AVCaptureSession.

To begin, first instantiate a `Session`. A `Session` object will be your main entry point into CameraKit. Through it, you can access components like lenses.

```swift
let cameraKit = Session()
```

Next, create a CameraKit input and start your CameraKit Session with it. AVSessionInput is an input that CameraKit provides that wraps up lens-specific details of AVCaptureSession configuration (such as setting the pixel format).

```swift
let input = AVSessionInput(session: yourAVCaptureSession)
cameraKit.start(with: input)
```

To display the processed output of CameraKit, we provide a `PreviewView` that behaves similarly to `AVCaptureVideoPreviewLayer`. The `PreviewView` is a CameraKit `Output` – it receives processed frames and displays them. We'll also set `automaticallyConfiguresTouchHandler` so that CameraKit can process touch events and users can interact with lenses. Add it to the view heirarchy like any other `UIView`, and connect it to the Session by calling `add(output:)`.

```swift
let previewView = PreviewView()
previewView.automaticallyConfiguresTouchHandler = true
cameraKit.add(output: previewView)
```

At this point, if you build and run your app, you should see your camera input displaying onscreen without any lenses applied to it. We'll discuss how to activate lenses in the next section.

#### Activating Lenses

CameraKit lenses are provided by the `LensRepository` class. You can access this through `cameraKit.lenses.respository`. Lenses are fetched asynchronously, and you may wish to hold a reference to the lenses returned from `LensRepository`.

```swift
cameraKit.lenses.repository.availableLenses { lenses, error in
	self.lenses = lenses
}
```

The `LensProcessor` is responsible for applying and clearing lenses. You can access it through `cameraKit.lenses.processor`.

We can now take the lens that we've retrieved from the repository, and apply it to the lens processor.

```swift
cameraKit.lenses.processor?.apply { success in
	// If success == true, the lens is now applied
}
```

The preview view should now be showing camera input with a lens applied.

When you are done with a lens and want to remove it from the camera input, you can call `clear` on the lens processor. It is _not_ neccessary to clear a lens before applying a new one.

```swift
cameraKit.lenses.processor?.clear() { success in
	// If success == true, the lens has been cleared
}
```

The preview view should once again be showing camera input with no lenses applied.
