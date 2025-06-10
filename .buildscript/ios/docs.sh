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
readonly program_name=$0

readonly ios_dir="${script_dir}/../../samples/ios"
readonly sample_dir="${ios_dir}/CameraKitSample"
readonly repo_root="${script_dir}/../../.."
readonly version_file="${repo_root}/VERSION"
readonly version=$( cat "${version_file}" | tr -d " \t\n\r" )

usage() {
    echo "usage: ${program_name} [-e --eject-to path]"
    echo "  -e eject-to path [optional] specify filesystem path to eject publishable documentation files to"
    echo "                   Default: temporary directory"
}

main() {
    local eject_to=$1
    
    local temp_dir="${script_dir}/docs_temp"
    local readme_zip="${temp_dir}/readme.zip"
    local docs_dir="${temp_dir}/docs"
    local derived_data="${temp_dir}/derived_data"
    local build_path="${derived_data}/Build/Products/Debug-iphoneos"
    local jazzy_path="${script_dir}/jazzy/jazzy.sh"
    local gem_path="${script_dir}/.gem-out"
    local author="Snap Inc."
    local readme_docs="${temp_dir}/readme"

    local sdk_dir="${docs_dir}/SCSDKCameraKit"
    local refui_dir="${docs_dir}/SCSDKCameraKitReferenceUI"
    local swiftui_dir="${docs_dir}/SCSDKCameraKitReferenceSwiftUI"

    local sdk_header_path="${ios_dir}/__CameraKitSupport/CameraKit/CameraKit/Sources/SCSDKCameraKit.xcframework/ios-arm64/SCSDKCameraKit.framework/Headers"
    local camera_kit_support_dir="${ios_dir}/__CameraKitSupport"
    local headers_dir="${temp_dir}/headers"
    local sandbox_workspace_path="${sample_dir}/CameraKitSample.xcworkspace"

    pushd ${ios_dir}

    rm -rf $temp_dir
    rm -rf $gem_path
    mkdir -p $docs_dir

    # copy readme docs
    source "${camera_kit_support_dir}/.build"
    local gs_docs="gs://snapengine-maven-publish/camera-kit-ios/releases/${CAMERA_KIT_COMMIT}/${CAMERA_KIT_BUILD}/docs.zip"
    gsutil cp "${gs_docs}" "${readme_zip}"
    unzip -q "${readme_zip}" -d "${readme_docs}"

    ./focus -s CameraKitSample --skip-xcode

    # Copy headers for SCSDKCameraKit doc generation
    mkdir -p $headers_dir
    cp -r "${sdk_header_path}"/* "$headers_dir"
    find "$headers_dir" -type f -exec sed -i '' 's/<SCSDKCameraKit\/\([^>]*\)>/"\1"/g' {} +
    
    # Generate docs for the SCSDKCameraKit framework
    $jazzy_path \
    --objc \
    --umbrella-header ${headers_dir}/SCSDKCameraKit.h \
    --author "${author}" \
    --author_url https://kit.snapchat.com/camera-kit \
    --module SCSDKCameraKit \
    --module-version "${version}" \
    --theme fullwidth \
    --output "${sdk_dir}" \
    --readme $readme_docs/SCSDKCameraKit.md \

    xcodebuild docbuild CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO \
    -workspace ${sandbox_workspace_path} \
    -scheme CameraKitSample \
    -configuration Debug \
    -sdk iphoneos \
    -derivedDataPath $derived_data

    cp -R $build_path/SCCameraKitReferenceUI/SCCameraKitReferenceUI.doccarchive $build_path/SCCameraKitReferenceSwiftUI/SCCameraKitReferenceSwiftUI.doccarchive $docs_dir/.

    $jazzy_path --clean \
    --author "${author}" \
    --author_url https://kit.snapchat.com/camera-kit \
    --readme $readme_docs/SCSDKCameraKitReferenceUI.md \
    --module SCSDKCameraKitReferenceUI \
    --module-version "${version}" \
    --xcodebuild-arguments -workspace,$sandbox_workspace_path,-scheme,CameraKitSample,-config,Debug,-sdk,iphoneos \
    --theme fullwidth \
    --output "${refui_dir}"

    $jazzy_path --clean \
    --author "${author}" \
    --author_url https://kit.snapchat.com/camera-kit \
    --readme $readme_docs/SCSDKCameraKitReferenceSwiftUI.md \
    --module SCSDKCameraKitReferenceSwiftUI \
    --module-version "${version}" \
    --xcodebuild-arguments -workspace,$sandbox_workspace_path,-scheme,CameraKitSample,-config,Debug,-sdk,iphoneos \
    --theme fullwidth \
    --output "${swiftui_dir}"

    rm -rf "${docs_dir}/SCCameraKitReferenceUI.doccarchive"
    rm -rf "${docs_dir}/SCCameraKitReferenceSwiftUI.doccarchive"

    popd

    # copy README to act as root index for static html site
    cp "$readme_docs/site_index.md" "$docs_dir/README.md"

    # cleanup
    rm -rf $temp_dir

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
