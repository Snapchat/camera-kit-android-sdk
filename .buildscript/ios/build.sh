#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
readonly samples_ios_root="${script_dir}/../../samples/ios"
readonly program_name=$0

usage() {
    echo "usage: ${program_name} [-e --eject-to path]"
    echo "  -e eject-to path [optional] specify filesystem path to eject publishable project sources to"
    echo "                   Default: none, build only, no sources are ejected"
}

main() {
    local eject_to=$1

    pushd "${samples_ios_root}/CameraKitSample"
    rm -rf camera-kit-ios-releases
    git clone git@github.sc-corp.net:Snapchat/camera-kit-ios-releases.git
    rm -rf camera-kit-ios-releases/.git
    sed -i '' 's;git@github.sc-corp.net:Snapchat/camera-kit-ios-releases.git;;g' camera-kit-ios-releases/CameraKit.podspec
    rm -f Podfile
    mv Podfile.ci Podfile
    pod install
    xcodebuild -workspace CameraKitSample.xcworkspace -scheme CameraKitSample -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 8' test

    if [[ -n "$eject_to" ]]; then
        cp -R "${samples_ios_root}/." "${eject_to}"
    fi

    popd
    :
}

eject_to_directory=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -e | --ejecto-to)
        eject_to_directory="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${eject_to_directory}"