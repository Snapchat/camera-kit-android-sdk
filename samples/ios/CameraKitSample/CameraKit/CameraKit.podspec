Pod::Spec.new do |spec|
  spec.name         = "CameraKit"
  spec.version = "1.8.0"
  spec.summary      = "Bring the power of Snapchat’s Camera into your app and create unique AR experiences."
  spec.homepage     = "https://kit.snapchat.com/camera-kit"
  spec.license      = { :type => "Copyright", :text => "Copyright Snap Inc. All rights reserved." }
  spec.author       = "Snap Inc."
  spec.platform     = :ios, "11.0"
  spec.source       = { :git => "", :tag => spec.version }
  spec.vendored_frameworks = "Sources/SCSDKCameraKit.xcframework"
end
