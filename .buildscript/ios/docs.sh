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
readonly sample_app_dir="${script_dir}/../../samples/ios/CameraKitSample"
readonly program_name=$0
readonly version_file="${script_dir}/../../VERSION"
readonly version="$(sed -n 1p ${version_file})"

usage() {
    echo "usage: ${program_name} [-e --eject-to path]"
    echo "  -e eject-to path [optional] specify filesystem path to eject publishable documentation files to"
    echo "                   Default: temporary directory"
}

main() {
    local eject_to=$1

    pushd "${sample_app_dir}"

    if [[ -z "$eject_to" ]]; then
        eject_to=$(mktemp -d -t "camerakit-ios-docs-XXXXXXXXXX")
    fi

    ./focus --skip-xcode

    bundle exec jazzy \
    --objc \
    --author Snap Inc. \
    --author_url https://kit.snapchat.com/camera-kit \
    --umbrella-header CameraKit/Sources/SCSDKCameraKit.xcframework/ios-x86_64-simulator/SCSDKCameraKit.framework/Headers/SCSDKCameraKit.h \
    --framework-root CameraKit/Sources/SCSDKCameraKit.xcframework/ios-x86_64-simulator/SCSDKCameraKit.framework \
    --module SCSDKCameraKit \
    --module-version "${version}" \
    --sdk iphonesimulator \
    --output "${eject_to}"

    echo "Outputted docs to ${eject_to}"

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
