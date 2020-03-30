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
readonly releases_commit="c38254143c65a11f61c01a8b83e6ea77c4d0585e"

usage() {
    echo "usage: ${program_name} [-e --eject-to path]"
    echo "  -e eject-to path [optional] specify filesystem path to eject publishable project sources to"
    echo "                   Default: none, build only, no sources are ejected"
}

main() {
    local eject_to=$1
    local ipa_dir=$2
    local version_file="${script_dir}/../../VERSION"
    local version="$(sed -n 1p ${version_file})"

    pushd "${samples_ios_root}/CameraKitSample"
    plutil -replace CFBundleShortVersionString -string "${version}" "CameraKitSample/Info.plist"
    plutil -replace CFBundleVersion -string "1.${BUILD_NUMBER}" "CameraKitSample/Info.plist"
    rm -rf xcarchive_path
    rm -rf camera-kit-ios-releases
    git clone git@github.sc-corp.net:Snapchat/camera-kit-ios-releases.git

    pushd camera-kit-ios-releases
    git checkout $releases_commit
    rm -rf .git
    popd

    sed -i '' 's;git@github.sc-corp.net:Snapchat/camera-kit-ios-releases.git;;g' camera-kit-ios-releases/CameraKit.podspec
    rm -f Podfile
    mv Podfile.ci Podfile
    pod install
    xcodebuild test \
        -workspace CameraKitSample.xcworkspace \
        -scheme CameraKitSample \
        -sdk iphonesimulator \
        -destination 'platform=iOS Simulator,name=iPhone 8'

    if [[ -n "$ipa_dir" ]]; then
        xcodebuild archive \
            -workspace CameraKitSample.xcworkspace \
            -scheme CameraKitSample \
            -sdk iphoneos \
            -configuration Enterprise \
            -archivePath ${archive_path} \
            CODE_SIGN_IDENTITY='iPhone Distribution: Snapchat Inc' \
            PROVISIONING_PROFILE='16227dfc-5923-4bda-9635-51354e0d861c' \
            DEVELOPMENT_TEAM='RRXKNUJYAH'

        xcodebuild -exportArchive \
            -archivePath ${archive_path} \
            -exportPath ${ipa_dir} \
            -exportOptionsPlist ${export_options_plist}
    fi

    if [[ -n "$eject_to" ]]; then
        cp -R "${samples_ios_root}/." "${eject_to}"
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
