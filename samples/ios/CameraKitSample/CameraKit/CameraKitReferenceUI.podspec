Pod::Spec.new do |spec|
  spec.name                 = "CameraKitReferenceUI"
  spec.version = "1.7.0"
  spec.summary              = "Carousel, Camera Ring, and other UI components to support CameraKit"
  spec.dependency           "CameraKit"
  spec.module_name          = "SCSDKCameraKitReferenceUI"
  spec.homepage             = "https://kit.snapchat.com/camera-kit"
  spec.license              = { :type => "Copyright", :text => "Copyright Snap Inc. All rights reserved." }
  spec.author               = "Snap Inc."
  spec.platform             = :ios, "11.0"
  spec.source               = { :git => "", :tag => spec.version }
  spec.ios.source_files     = "Sources/SCSDKCameraKitReferenceUI/UIKit/**/*.swift"
  spec.ios.resource_bundle  = { "CameraKitReferenceUI" => "Sources/SCSDKCameraKitReferenceUI/UIKit/Resources/Reference.xcassets" }
  spec.swift_version        = '5.0'
end
