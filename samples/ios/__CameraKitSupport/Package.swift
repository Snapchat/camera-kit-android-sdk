// swift-tools-version:5.4

import PackageDescription

let package = Package(
    name: "CameraKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "SCSDKCameraKit", targets: ["SCSDKCameraKit"]),
        .library(name: "SCSDKCameraKitBaseExtension", targets: ["SCSDKCameraKitBaseExtension_Wrapper"]),
        .library(name: "SCSDKCameraKitLoginKitAuth", targets: ["SCSDKCameraKitLoginKitAuth_Wrapper"]),
        .library(name: "SCSDKCameraKitPushToDeviceExtension", targets: ["SCSDKCameraKitPushToDeviceExtension_Wrapper"]),
        .library(name: "SCSDKCameraKitReferenceUI", targets: ["SCSDKCameraKitReferenceUI"]),
        .library(name: "SCSDKCameraKitReferenceSwiftUI", targets: ["SCSDKCameraKitReferenceSwiftUI"]),
    ],
    dependencies: [
        .package(url: "https://github.com/Snapchat/snap-kit-spm", .upToNextMajor(from: "2.5.0"))
    ],
    targets: [
        .binaryTarget(
            name: "SCSDKCameraKit",
            path: "CameraKit/CameraKit/Sources/SCSDKCameraKit.xcframework"
        ),
        
        .binaryTarget(
            name: "SCSDKCameraKitBaseExtension",
            path: "CameraKit/CameraKitBaseExtension/Sources/SCSDKCameraKitBaseExtension.xcframework"
        ),
        .target(
            name: "SCSDKCameraKitBaseExtension_Wrapper",
            dependencies: [
                .target(name: "SCSDKCameraKitBaseExtension"),
                .target(name: "SCSDKCameraKit")
            ],
            path: "CameraKitBaseExtension_Wrapper"
        ),
        
        .binaryTarget(
            name: "SCSDKCameraKitLoginKitAuth",
            path: "CameraKit/CameraKitLoginKitAuth/Sources/SCSDKCameraKitLoginKitAuth.xcframework"
        ),
        
        .target(
            name: "SCSDKCameraKitLoginKitAuth_Wrapper",
            dependencies: [
                .target(name: "SCSDKCameraKitLoginKitAuth"),
                .target(name: "SCSDKCameraKit"),
                .product(name: "SnapSDK", package: "snap-kit-spm")
            ],
            path: "CameraKitLoginKitAuth_Wrapper"
        ),
        
        .binaryTarget(
            name: "SCSDKCameraKitPushToDeviceExtension",
            path: "CameraKit/CameraKitPushToDeviceExtension/Sources/SCSDKCameraKitPushToDeviceExtension.xcframework"
        ),
        .target(
            name: "SCSDKCameraKitPushToDeviceExtension_Wrapper",
            dependencies: [
                .target(name: "SCSDKCameraKitPushToDeviceExtension"),
                .target(name: "SCSDKCameraKitBaseExtension_Wrapper"),
            ],
            path: "CameraKitPushToDeviceExtension_Wrapper"
        ),
        
        .target(
            name: "SCSDKCameraKitReferenceUI",
            dependencies: ["SCSDKCameraKit"],
            path: "CameraKit/CameraKitReferenceUI/Sources"
        ),
        
        .target(
            name: "SCSDKCameraKitReferenceSwiftUI",
            dependencies: ["SCSDKCameraKitReferenceUI"],
            path: "CameraKit/CameraKitReferenceSwiftUI/Sources"
        )
    ]
)
