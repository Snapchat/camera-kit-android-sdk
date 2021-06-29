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
readonly export_options_plist="${script_dir}/exportOptions.plist"
readonly archive_path="${script_dir}/archive/CameraKitSample.xcarchive"
readonly version_file="${script_dir}/../../VERSION"
readonly version="$(sed -n 1p ${version_file})"

usage() {
    echo "usage: ${program_name} [-e --eject-to path]"
    echo "  -e eject-to path [optional] specify filesystem path to eject publishable project sources to"
    echo "                   Default: none, build only, no sources are ejected"
}

main() {
    local eject_to=$1
    local ipa_dir=$2

    pushd "${samples_ios_root}/CameraKitSample"

    rm -rf xcarchive_path

    ./focus --skip-xcode

    local framework_full_version="$(plutil -extract CFBundleVersion xml1 -o - CameraKit/Sources/SCSDKCameraKit.xcframework/ios-x86_64-simulator/SCSDKCameraKit.framework/Info.plist | sed -n "s/.*<string>\(.*\)<\/string>.*/\1/p")"
    local framework_short_version="$(plutil -extract CFBundleShortVersionString xml1 -o - CameraKit/Sources/SCSDKCameraKit.xcframework/ios-x86_64-simulator/SCSDKCameraKit.framework/Info.plist | sed -n "s/.*<string>\(.*\)<\/string>.*/\1/p")"
    local sample_info_plist="CameraKitSample/Info.plist"
    plutil -replace CFBundleShortVersionString -string "${version}" "${sample_info_plist}"
    plutil -replace CFBundleVersion -string "${framework_full_version}" "${sample_info_plist}"

    scsdk_podspec_version="$(grep 'spec.version' CameraKit/CameraKit.podspec | head -1 | grep -o '".*"' | sed 's/"//g')"

    refui_podspec_version="$(grep 'spec.version' CameraKit/CameraKitReferenceUI.podspec | head -1 | grep -o '".*"' | sed 's/"//g')"

    if [[ "$version" != "$framework_short_version" ]]; then
        echo "Distribution version ${version} and iOS SDK version ${framework_short_version} are not equal; exiting..."
        exit 1
    fi

    if [[ "$version" != "$scsdk_podspec_version" ]]; then
        echo "Distribution version ${version} and iOS SDK version ${scsdk_podspec_version} are not equal; exiting..."
        exit 1
    fi

    if [[ "$version" != "$refui_podspec_version" ]]; then
        echo "Distribution version ${version} and iOS SDK version ${refui_podspec_version} are not equal; exiting..."
        exit 1
    fi

    xcodebuild clean test \
        -workspace CameraKitSample.xcworkspace \
        -scheme CameraKitSample \
        -sdk iphonesimulator \
        -destination "platform=iOS Simulator,name=iPhone 11 Pro"

    if [[ -n "$ipa_dir" ]]; then
        xcodebuild archive \
            -workspace CameraKitSample.xcworkspace \
            -scheme CameraKitSample \
            -sdk iphoneos \
            -configuration Enterprise \
            -archivePath ${archive_path} \
            CODE_SIGN_IDENTITY='iPhone Distribution: Snapchat Inc' \
            PROVISIONING_PROFILE_SPECIFIER='CameraKit Sample Enterprise' \
            DEVELOPMENT_TEAM='RRXKNUJYAH'

        xcodebuild -exportArchive \
            -archivePath ${archive_path} \
            -exportPath ${ipa_dir} \
            -exportOptionsPlist ${export_options_plist}
    fi

    plutil -replace SCSDKClientId -string "[Enter the OAuth2 client ID you get from the Snap Kit developer portal]" "${sample_info_plist}"


    if [[ -n "$eject_to" ]]; then
        cp -R "${samples_ios_root}/." "${eject_to}"
        pushd "${eject_to}"
        # cleanup CI artifacts
        rm -f Gemfile
        rm -f Gemfile.lock
        rm -rf .bundle
        rm -rf .gem-out
        rm -rf gem-out
        rm -f .build
        rm -f focus
        popd
    fi

    popd
    :
}

eject_to_directory=""
ipa_path=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -e | --ejecto-to)
        eject_to_directory="$2"
        shift
        shift
        ;;
    -i | --ipa-path)
        ipa_path="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${eject_to_directory}" "${ipa_path}"
