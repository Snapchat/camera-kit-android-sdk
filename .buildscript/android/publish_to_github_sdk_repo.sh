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
readonly camerakit_publish_repo_base_path="${GITHUB_ANDROID_SDK_REPO}"
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

process_samples() {
    local samples_dir="${repository_dir}/Samples"
    local original_samples_dir="${repo_root}/samples/android" 

    pushd "${original_samples_dir}"

    ./gradlew clean
    rm -rf maven/*

    # version file is used by build gradle
    cp "${repo_root}/VERSION" "${repository_dir}"

    ./gradlew eject -PoutputDir="${samples_dir}" -Pflavor="public"

    # update the path for the VERSION file in newly ejected Gradle script
    sed -i "" "s|../../VERSION|../VERSION|" "${samples_dir}/build.gradle"
    
    popd

    pushd "${samples_dir}"

   ./gradlew assembleDebug

    # Removed generated and uncecessary files    
    rm -rf `find . -type d -name build`
    rm -rf .gradle
    rm "README.md"
    rm "P2D.md"
    rm "Profiling.md"

    #move to root repo
    mv ".gitignore" "${repository_dir}"

    popd
}

main() {
    local output_dir=$(pwd)
    local repository_dir=$(mktemp -d -t "camerakit-android-publish-repository-XXXXXXXXXX")
    git clone --depth 1 "${camerakit_publish_repo_http_url}" "${repository_dir}"

    pushd "${repository_dir}"
    local branch="sync/${version_name}/${random_id}"
    local base_branch=$(git rev-parse --abbrev-ref HEAD)

    git checkout -b "${branch}"
    git rm -r '*' || true

    process_samples

    # copy other repo files
    cp "${repo_root}/LICENSE" "${repository_dir}"
    cp "${repo_root}/NOTICE" "${repository_dir}"
    cp "${repo_root}/public/android/README.md" "${repository_dir}"
    sed -i "" "s/@camera_kit_sdk_version/${version_name}/g" "${repository_dir}/README.md"

    ${script_dir}/../filter_changelog.swift "${repo_root}/CHANGELOG.md" "Android" > "${repository_dir}"/CHANGELOG.md

    # handle sensitive strings
    for sensitive_string in ${sensitive_strings[@]}
    do
        find . \( -type d -name .git -prune \) -o \( -type f ! -name "*.doc" -print0 \) | LC_ALL=C xargs -0 -P 16 sed -i "" "s/${sensitive_string//\//\\/}/${sensitive_string_replacement}/g"
    done

    # publish a draft PR for the new release    
    git add --all

    local update_title="[All] Sync changes for the ${version_name} release"
    local update_body="This syncs all changes for the ${version_name} CameraKit release."

    git commit -m "$update_title"
    git push --set-upstream origin "${branch}" -f

    echo "Calling function to create PR draft with the following parameters:"
    create_pr_draft "${update_title}" "${branch}" "${base_branch}" "${update_body}" "${output_dir}"

    # clean up public camera-kit-android-sdk git repo
    rm -rf "${repository_dir}"

    popd
}

main