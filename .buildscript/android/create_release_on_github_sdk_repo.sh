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

get_latest_commit() {
    curl -s -H "Authorization: token ${GITHUB_APIKEY}" \
         "https://api.github.com/repos/${GITHUB_ANDROID_SDK_REPO}/commits/main" | \
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
  "name": "Camera Kit for Android v${version_name}",
  "body": ${changelog_escaped},
  "draft": false,
  "prerelease": false
}
EOF
)
    
    curl -X POST -H "Authorization: token ${GITHUB_APIKEY}" \
         -H "Content-Type: application/json" \
         -d "${release_data}" \
         "https://api.github.com/repos/${GITHUB_ANDROID_SDK_REPO}/releases"
}

main() {
    # Extract a piece of changelog for the current version
    echo "version name ${version_name}"
    local changelog=$(${script_dir}/../filter_changelog.swift "${script_dir}/../../CHANGELOG.md" "Android" "${version_name}")

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
}

main
