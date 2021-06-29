// swift-tools-version:5.3
import PackageDescription

let package = Package(
    name: "CameraKit",
    platforms: [
        .iOS(.v11)
    ],
    products: [
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
            resources: [
                .process("Resources/Reference.xcassets")
            ]),
    ]
)
