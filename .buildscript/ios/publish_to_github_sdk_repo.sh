#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly script_name=$(basename "${0}")
readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
readonly repo_root="${script_dir}/../.."
readonly camera_kit_artifacts_dir="${script_dir}/CameraKit"
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly camerakit_publish_repo_base_path="${GITHUB_IOS_SDK_REPO}"
readonly camerakit_publish_repo_http_url="https://${GITHUB_USERNAME}:${GITHUB_APIKEY}@github.com/${camerakit_publish_repo_base_path}.git"
readonly github_api_repos_url="https://api.github.com/repos"
readonly random_id=$(openssl rand -hex 4)

sensitive_strings=()
while IFS= read -r line; do
  sensitive_strings+=("$line")
done < "${script_dir}/../sensitive_strings.txt"

readonly sensitive_string_replacement="REPLACE-THIS-WITH-YOUR-OWN-APP-SPECIFIC-VALUE"

create_pr_draft() {
    local title=$1
    local head=$2
    local base=$3
    local body=$4
    local output_dir=$5
    local params="{ \"title\":\"${title}\", \"head\":\"${head}\", \"base\":\"${base}\", \"body\":$( jq -aRs . <<<  "${body}" ), \"draft\":true }"
    curl -H "Authorization: token ${GITHUB_APIKEY}" -H "Content-Type: application/json" "${github_api_repos_url}/${camerakit_publish_repo_base_path}/pulls" -d "${params}" -o "${output_dir}/pr_request_response.json"
}

process_framework() {
    local sdk_name=$1
    local package_swift_path=$2
    local xcframework_dir="${camera_kit_artifacts_dir}/${sdk_name}/Sources"

    pushd "${xcframework_dir}"

    zip -r "SCSDK${sdk_name}.xcframework.zip" "SCSDK${sdk_name}.xcframework"

    gsutil cp "SCSDK${sdk_name}.xcframework.zip" "gs://snap-kit-build/scsdk/camera-kit-ios/releases-spm/${version_name}/SCSDK${sdk_name}.xcframework.zip"
    sed -i "" "s/@SCSDK${sdk_name}_url/https:\/\/storage.googleapis.com\/snap-kit-build\/scsdk\/camera-kit-ios\/releases-spm\/${version_name}\/SCSDK${sdk_name}.xcframework.zip/g" "${package_swift_path}"

    local checksum=$(swift package compute-checksum "SCSDK${sdk_name}.xcframework.zip")
    sed -i "" "s/@SCSDK${sdk_name}_checksum/${checksum}/g" "${package_swift_path}"

    popd     
}

process_sample() {
    local sample_name=$1

    local sample_dir="${repository_dir}/Samples/${sample_name}"

    # copy sample code
    cp -R "${repo_root}/samples/ios/${sample_name}" "${repository_dir}/Samples"

    # remove not needed files
    rm "${sample_dir}/Podfile.template" || true
    rm "${sample_dir}/Podfile" || true
    rm "${sample_dir}/Podfile.lock" || true
    rm -rf "${sample_dir}/Pods" || true
    rm -rf "${sample_dir}/${sample_name}.xcworkspace" || true
    rm -rf "${sample_dir}/${sample_name}.xcodeproj"

    # prepare SPM bases xcodeproj
    mv "${sample_dir}/${sample_name}-SPM.xcodeproj" "${sample_dir}/${sample_name}.xcodeproj"

    # make xcodeproject working with the repo's root Package.swift
    sed -i "" "s/\.\.\/__CameraKitSupport/\.\.\/\.\./g" "${sample_dir}/${sample_name}.xcodeproj/project.pbxproj"

    # build the sample

    pushd "${sample_dir}"

    xcodebuild clean build \
        -project "${sample_name}.xcodeproj" \
        -scheme  "${sample_name}" \
        -sdk iphonesimulator \
        -destination "id=${simulator_id}"
        
    popd
}

main() {
    # clone camera-kit-ios-sdk git repo

    local output_dir=$(pwd)

    local repository_dir=$(mktemp -d -t "camerakit-ios-publish-repository-XXXXXXXXXX")
    rm -rf "${repository_dir}"

    # --config credential.helper='' added to resolve issue on snapCI Mac builders when fetching credentials 
    git clone --config credential.helper='' --depth 1 "${camerakit_publish_repo_http_url}" "${repository_dir}"

    pushd "${repository_dir}"
    local branch="sync/${version_name}/${random_id}"
    local base_branch=$(git rev-parse --abbrev-ref HEAD)

    # remove all non-hidden contents from the camera-kit-ios-sdk repository.

    git checkout -b "${branch}"
    git rm -r '*' || true

    # copy Package.swift

    local package_swift_path="${repository_dir}/Package.swift"

    cp "${repo_root}/public/ios/Package.swift.template" "${package_swift_path}"

    # download camera kit artifacts

    ${repo_root}/samples/ios/ck_fetch "${camera_kit_artifacts_dir}"

    # for each  binary xcframework: zip + checksum + upload + fill Package.swift

    local sdk_names=("CameraKit" "CameraKitBaseExtension" "CameraKitLoginKitAuth" "CameraKitPushToDeviceExtension")

    for sdk_name in "${sdk_names[@]}"; do
        process_framework "${sdk_name}" "${package_swift_path}"
    done

    # copy Reference UI sources
    mkdir -p "Sources/SCSDKCameraKitReferenceUI"    
    cp -R "${camera_kit_artifacts_dir}/CameraKitReferenceUI/Sources"/* "${repository_dir}/Sources/SCSDKCameraKitReferenceUI"

    mkdir -p "Sources/SCSDKCameraKitReferenceSwiftUI"
    cp -R "${camera_kit_artifacts_dir}/CameraKitReferenceSwiftUI/Sources"/* "${repository_dir}/Sources/SCSDKCameraKitReferenceSwiftUI"

    # copy Wrappers
    local camera_support_dir="${repo_root}/samples/ios/__CameraKitSupport"
    cp -R "${camera_support_dir}/CameraKitBaseExtension_Wrapper" "${repository_dir}/Sources/"
    cp -R "${camera_support_dir}/CameraKitLoginKitAuth_Wrapper" "${repository_dir}/Sources/"
    cp -R "${camera_support_dir}/CameraKitPushToDeviceExtension_Wrapper" "${repository_dir}/Sources/"

    # cleanup after ck_fetch_latest

    rm -rf "${camera_kit_artifacts_dir}"

    # copy, prepare and build ios samples
    mkdir "Samples"

    source "${script_dir}/.envconfig"
    local simulator_id=$(xcrun simctl create "CamKitSim" ${CAMERA_KIT_XCODE_SIM_DEVICE_TYPE} ${CAMERA_KIT_XCODE_SIM_RUNTIME})
    xcrun simctl boot $simulator_id

    local sample_names=("CameraKitSample" "CameraKitBasicSample" "CameraKitAlternateCarouselSample")
    for sample_name in "${sample_names[@]}"; do
        process_sample "${sample_name}"
    done

    # copy other repo files

    cp "${repo_root}/public/ios/README.md" "${repository_dir}"
    sed -i "" "s/@camera_kit_sdk_version/${version_name}/g" "${repository_dir}/README.md"

    cp "${repo_root}/public/ios/.gitignore" "${repository_dir}"
    cp "${repo_root}/LICENSE" "${repository_dir}"
    cp "${repo_root}/NOTICE" "${repository_dir}"

    ${script_dir}/../filter_changelog.swift "${repo_root}/CHANGELOG.md" "iOS" > "${repository_dir}"/CHANGELOG.md

    cp "${version_file}" "${repository_dir}"

    # handle sensitive strings

    pushd "${repository_dir}/Samples"

    for sensitive_string in ${sensitive_strings[@]}
    do
        find . \( -type d -name .git -prune \) -o -type f -print0 | LC_ALL=C xargs -0 -P 16 sed -i'.bak' "s/${sensitive_string//\//\\/}/${sensitive_string_replacement}/g"
    done
    find . -type f -name "*.bak" -exec rm -rf {} \;

    popd

    # publish a draft PR for the new release
  
    git add --all

    local update_title="[All] Sync changes for the ${version_name} release"
    local update_body="This syncs all changes for the ${version_name} CameraKit release."

    git commit -m "$update_title"
    git push --set-upstream origin "${branch}" -f

    create_pr_draft "${update_title}" "${branch}" "${base_branch}" "${update_body}" "${output_dir}"

    # clean up public camera-kit-ios-sdk git repo
    rm -rf "${repository_dir}"

    popd
}

main
