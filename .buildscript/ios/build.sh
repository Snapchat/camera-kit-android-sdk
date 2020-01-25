#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly samples_ios_root="../../samples/ios/CameraKitSample"

pushd "${samples_ios_root}"
rm -rf camera-kit-ios-releases
git clone git@github.sc-corp.net:Snapchat/camera-kit-ios-releases.git
rm -f Podfile
mv Podfile.ci Podfile
pod install
xcodebuild -workspace CameraKitSample.xcworkspace -scheme CameraKitSample -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 8' test
popd
