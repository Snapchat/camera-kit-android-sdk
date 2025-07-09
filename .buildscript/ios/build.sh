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
readonly public_ios_root="${script_dir}/../../public/ios"
readonly program_name=$0
readonly export_options_plist="${script_dir}/exportOptions.plist"
readonly archive_path="${script_dir}/archive/CameraKitSample.xcarchive"
readonly version_file="${script_dir}/../../VERSION"
readonly version="$(sed -n 1p ${version_file})"

usage() {
    echo "usage: ${program_name} [-e --eject-to path] [-i --ipa-path path] [-f --flavor <partner/public>]"
    echo "  -e eject-to path        [optional] specify filesystem path to eject publishable project sources to"
    echo "                          Default: none, build only, no sources are ejected"
    echo "  -i --ipa-path          [optional] specify filesystem path to publish ipa to"
    echo "                          Default: none, build only, no ipa is generated"
    echo "  -f flavor <flavor>      [optional] specify the flavor of the build to perform" 
    echo "                          Default: partner. Other flavors available: public"
}

main() {
    source "${script_dir}/.envconfig"
    
    local eject_to=$1
    local ipa_dir=$2
    local flavor=$3

    $script_dir/setup.sh

    pushd "${samples_ios_root}"
    ./focus --skip-xcode --flavor "${flavor}"
    popd

    pushd "${samples_ios_root}/CameraKitSample"

    rm -rf ${archive_path}

    local sample_info_plist="CameraKitSample/Info.plist"

    if [[ "${flavor}" == "partner" ]]; then
        # if it's partner flavor we need to ensure local SDKs/podspecs/etc match the expected build

        local framework_full_version="$(plutil -extract CFBundleVersion xml1 -o - ../__CameraKitSupport/CameraKit/CameraKit/Sources/SCSDKCameraKit.xcframework/ios-arm64/SCSDKCameraKit.framework/Info.plist | sed -n "s/.*<string>\(.*\)<\/string>.*/\1/p")"
        local framework_short_version="$(plutil -extract CFBundleShortVersionString xml1 -o - ../__CameraKitSupport/CameraKit/CameraKit/Sources/SCSDKCameraKit.xcframework/ios-arm64/SCSDKCameraKit.framework/Info.plist | sed -n "s/.*<string>\(.*\)<\/string>.*/\1/p")"
        plutil -replace CFBundleShortVersionString -string "${version}" "${sample_info_plist}"
        plutil -replace CFBundleVersion -string "${framework_full_version}" "${sample_info_plist}"

        scsdk_podspec_version="$(grep 'spec.version' ../__CameraKitSupport/CameraKit/CameraKit/SCCameraKit.podspec | head -1 | grep -o '".*"' | sed 's/"//g')"

        refui_podspec_version="$(grep 'spec.version' ../__CameraKitSupport/CameraKit/CameraKitReferenceUI/SCCameraKitReferenceUI.podspec | head -1 | grep -o '".*"' | sed 's/"//g')"

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
    fi

    simulator_id=$(xcrun simctl create "CamKitSim" ${CAMERA_KIT_XCODE_SIM_DEVICE_TYPE} ${CAMERA_KIT_XCODE_SIM_RUNTIME})
    xcrun simctl boot $simulator_id
    xcodebuild clean test \
        -workspace CameraKitSample.xcworkspace \
        -scheme CameraKitSample \
        -sdk iphonesimulator \
        -destination "id=${simulator_id}"


    if [[ -n "$ipa_dir" ]]; then
        xcodebuild archive \
            -workspace CameraKitSample.xcworkspace \
            -scheme CameraKitSample \
            -sdk iphoneos \
            -configuration Enterprise \
            -archivePath ${archive_path} \
            CODE_SIGN_IDENTITY='iPhone Distribution: Snapchat Inc' \
            PROVISIONING_PROFILE_SPECIFIER='2025-2026 Dev Tools Wildcard Provisioning Profile' \
            DEVELOPMENT_TEAM='RRXKNUJYAH'

        xcodebuild -exportArchive \
            -archivePath ${archive_path} \
            -exportPath ${ipa_dir} \
            -exportOptionsPlist ${export_options_plist}
    fi

    popd

    if [[ -n "$eject_to" ]]; then
        cp -R "${samples_ios_root}/." "${eject_to}"
        cp -R "${public_ios_root}/.gitignore" "${eject_to}/.gitignore"
        
        # cleanup CI artifacts

        pushd "${eject_to}"
        
        rm -rf __CameraKitSupport
        rm -rf .bundle
        rm -rf .gem-out
        rm -f focus
        rm -f ck_fetch
        rm -f Gemfile
        rm -f Gemfile.lock
        
        popd

        local sample_names=("CameraKitSample" "CameraKitBasicSample" "CameraKitAlternateCarouselSample")
        for sample_name in "${sample_names[@]}"; do
            pushd "${eject_to}/${sample_name}"
            plutil -replace SCSDKClientId -string "[Enter the OAuth2 client ID you get from the Snap Kit developer portal]" "${sample_name}/Info.plist"
            rm -f .gitignore
            rm -f Podfile.template
            rm -f Podfile.lock            
            rm -rf Pods
            rm -rf ${sample_name}.xcworkspace
            rm -rf ${sample_name}-SPM.xcodeproj
            popd
        done
    fi
}

eject_to_directory=""
ipa_path=""
flavor="partner"

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
    -f | --flavor)
        flavor="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${eject_to_directory}" "${ipa_path}" "${flavor}"
