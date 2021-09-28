Pod::Spec.new do |spec|
  spec.name               = "CameraKitReferenceSwiftUI"
  spec.version = "1.8.0"
  spec.summary            = "SwiftUI implementations of Carousel, Camera Ring, and other UI components to support CameraKit"
  spec.dependency           "CameraKitReferenceUI"
  spec.module_name        = "SCSDKCameraKitReferenceSwiftUI"
  spec.homepage           = "https://kit.snapchat.com/camera-kit"
  spec.license            = { :type => "Copyright", :text => "Copyright Snap Inc. All rights reserved." }
  spec.author             = "Snap Inc."
  spec.platform           = :ios, "11.0"
  spec.source             = { :git => "", :tag => spec.version }
  spec.ios.source_files   = "Sources/SCSDKCameraKitReferenceUI/SwiftUI/**/*.swift"
  spec.swift_version      = '5.0'
end
