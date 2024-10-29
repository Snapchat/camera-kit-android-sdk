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
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly camerakit_publish_repo_http_url="https://${GITHUB_USERNAME}:${GITHUB_APIKEY}@github.com/${GITHUB_IOS_SDK_REPO}.git"
readonly github_api_repos_url="https://api.github.com/repos"

get_latest_commit() {
    curl -s -H "Authorization: token ${GITHUB_APIKEY}" \
         "https://api.github.com/repos/${GITHUB_IOS_SDK_REPO}/commits/main" | \
    jq -r .sha
}

create_github_release() {
    local changelog="$1"
    local commit_hash="$2"

    local changelog_escaped=$(echo "$changelog" | jq -Rs .)
    
    release_data=$(cat <<EOF
{
  "tag_name": "${version_name}",
  "target_commitish": "${commit_hash}",
  "name": "Camera Kit for iOS v${version_name}",
  "body": ${changelog_escaped},
  "draft": false,
  "prerelease": false
}
EOF
)
    
    curl -X POST -H "Authorization: token ${GITHUB_APIKEY}" \
         -H "Content-Type: application/json" \
         -d "${release_data}" \
         "https://api.github.com/repos/${GITHUB_IOS_SDK_REPO}/releases"
}

download_and_attach_binary() {
    local sdk_name="$1"
    local release_id="$2"
    
    local url="https://storage.googleapis.com/snap-kit-build/scsdk/camera-kit-ios/releases-spm/${version_name}/SCSDK${sdk_name}.xcframework.zip"
    local filename="SCSDK${sdk_name}.xcframework.zip"
    
    curl -L -o "${filename}" "${url}"
    
    curl -X POST -H "Authorization: token ${GITHUB_APIKEY}" \
         -H "Content-Type: application/zip" \
         --data-binary @"${filename}" \
         "https://uploads.github.com/repos/${GITHUB_IOS_SDK_REPO}/releases/${release_id}/assets?name=${filename}"
    
    rm "${filename}"
}

main() {
    # Extract a piece of changelog for the current version
    local changelog=$(${script_dir}/../filter_changelog.swift "${script_dir}/../../CHANGELOG.md" "iOS" "${version_name}")

    if [ -z "${changelog}" ] || [ "${changelog}" == "null" ]; then
        echo "Failed to extract changelog"
        exit 1
    fi

    # Get the latest commit hash

    local commit_hash=$(get_latest_commit)

    if [ -z "${commit_hash}" ] || [ "${commit_hash}" == "null" ]; then
        echo "Failed to get commit_hash"
        exit 1
    fi

    # Create release
    local release_response=$(create_github_release "${changelog}" "${commit_hash}")
    local release_id=$(echo "${release_response}" | jq -r .id)

    if [ -z "${release_id}" ] || [ "${release_id}" == "null" ]; then
        echo "Failed to create release"
        exit 1
    fi

    # Attach binaries
    local sdk_names=("CameraKit" "CameraKitBaseExtension" "CameraKitLoginKitAuth" "CameraKitPushToDeviceExtension")

    for sdk in "${sdk_names[@]}"; do
        download_and_attach_binary "${sdk}" "${release_id}"
    done
}

main
