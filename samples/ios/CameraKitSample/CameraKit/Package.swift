// swift-tools-version:5.3
import PackageDescription

let package = Package(
    name: "CameraKit",
    platforms: [
        .iOS(.v11)
    ],
    products: [
        .library(
            name: "SCSDKCameraKitReferenceSwiftUI",
            targets: [
                "SCSDKCameraKitReferenceSwiftUI"
            ]
        ),
        .library(
            name: "SCSDKCameraKitReferenceUI",
            targets: [
                "SCSDKCameraKitReferenceUI"
            ]
        ),
        .library(
            name: "SCSDKCameraKit",
            targets: [
                "SCSDKCameraKit"
            ]
        ),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "SCSDKCameraKit",
            path: "Sources/SCSDKCameraKit.xcframework"
        ),
        .target(
            name: "SCSDKCameraKitReferenceUI",
            dependencies: [
                "SCSDKCameraKit"
            ],
            path: "Sources/SCSDKCameraKitReferenceUI/UIKit",
            resources: [
                .process("Resources/Reference.xcassets")
            ]
        ),
        .target(
            name: "SCSDKCameraKitReferenceSwiftUI",
            dependencies: [
                "SCSDKCameraKit",
                "SCSDKCameraKitReferenceUI"
            ],
            path: "Sources/SCSDKCameraKitReferenceUI/SwiftUI",
            resources: []
        ),
    ]
)
