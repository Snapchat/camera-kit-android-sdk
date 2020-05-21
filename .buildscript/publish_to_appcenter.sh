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

# Local build information
readonly head_sha=$(git rev-parse HEAD)
readonly committer_details=$(git --no-pager show -s --format='%an <%ae>')
readonly committer_name_full=$(echo $committer_details | awk -F'[<|>]' '{print $1}')
readonly committer_name=$(echo $committer_name_full | awk -F'[ ]' '{print $1}') # Grab the first name in case full name is specified with a space between first name and last name
readonly committer_email=$(echo $committer_details | awk -F'[<|>]' '{print $2}')

# AppCenter configuration
readonly appcenter_token="${APPCENTER_TOKEN}"
readonly appcenter_owner_name="${APPCENTER_OWNER_NAME}"
readonly appcenter_app_name="${APPCENTER_APP_NAME}"
readonly appcenter_api_path_apps="https://api.appcenter.ms/v0.1/apps"
readonly appcenter_api_path_apps_release_uploads="${appcenter_api_path_apps}/${appcenter_owner_name}/${appcenter_app_name}/release_uploads"
readonly appcenter_api_path_apps_releases="${appcenter_api_path_apps}/${appcenter_owner_name}/${appcenter_app_name}/releases"
readonly appcenter_enable_download="${APPCENTER_ENABLE_DOWNLOAD}"
readonly appcenter_distribution_group="${APPCENTER_DISTRIBUTION_GROUP}"

# CI environment
readonly job_name="${JOB_NAME}"
readonly build_number="${BUILD_NUMBER}"

usage() {
    echo "usage: ${program_name} [-a, app-binary-path]"
    echo " -a, app-binary-path   [required] specify path app binary that should be published"
    echo "                       Default: none, no publishing will be performed"
}

function appcenter_upload {
    local app_binary_path=$1
    local release_notes_prefix=$2

    local upload_info=$(curl -X POST "${appcenter_api_path_apps_release_uploads}" -H "accept: application/json" -H "X-API-Token: ${appcenter_token}" -H "Content-Type: application/json")
    local upload_id=$(echo $upload_info | jq -r .upload_id)
    local upload_url=$(echo $upload_info | jq -r .upload_url)
    local upload_status=$(curl -F "ipa=@${app_binary_path}" "$upload_url")
   
    local update_status=$(curl -X PATCH -H "Content-Type: application/json" -H "accept: application/json" -H "X-API-Token: ${appcenter_token}" -d "{ \"status\": \"committed\"  }" "${appcenter_api_path_apps_release_uploads}/$upload_id")
    local release_url=$(echo $update_status | jq -r .release_url)
    local appcenter_release_id=$(echo $update_status | jq -r .release_id)
    local release_notes="${release_notes_prefix}COMMIT_SHA:${head_sha}, BUILD_TYPE:${job_name} build off ${branch} branch authored by ${committer_name}, BUILD LOGS: https://developer-portal.sc-corp.net/log-viewer/jenkins-classic/${job_name}/${build_number}, BUILD ARTIFACTS: https://console.cloud.google.com/storage/browser/snapengine-builder-artifacts/$job_name/$build_number"

    if [[ "${appcenter_enable_download}" -ne 0 ]];then
        IFS=' '  read -a groups <<< "${appcenter_distribution_group}"
        for group in "${groups[@]}"
            do
                local update_release_id=$(curl -X PATCH -H "Content-Type: application/json" -H "accept: application/json" -H "X-API-Token:${appcenter_token}" -d "{\"distribution_group_name\": \"$group\", \"release_notes\": \"$release_notes\", \"notify_testers\": false }" "https://api.appcenter.ms/$release_url")
            done
    else
        local update_release_id=$(curl -X PUT -H "Content-Type: application/json" -H "accept: application/json" -H "X-API-Token: ${appcenter_token}" -d "{ \"release_notes\": \"${release_notes}\"  }" "appcenter_api_path_apps_releases/${appcenter_release_id}")
    fi
    unset IFS

    echo "$appcenter_release_id"
}

function appcenter_upload_wtih_retries {
    local app_binary_path=$1
    local release_notes_prefix=$2
    local retry_count=0
    local appcenter_release_id
    while [[ $retry_count -lt 3 ]];
    do
        # 'true' is added to the next statement to override set -e
        appcenter_release_id=$((appcenter_upload "${app_binary_path}" "${release_notes_prefix}") || true)
        if [[ ! -z "$appcenter_release_id" ]];
        then
            echo "$appcenter_release_id"
            exit
        fi
        retry_count=$(( $retry_count + 1 ))
    done
    echo "$appcenter_release_id"
}

main() {
    local app_binary_path=$1
    local release_notes_prefix=$2
    
    if [[ -n "$app_binary_path" ]]; then
        appcenter_upload_wtih_retries "$app_binary_path" "$release_notes_prefix"
    else
        echo "No app binary path provided, exiting"
    fi
  
    :
}

app_binary_path=""
release_notes_prefix=""

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -a | --app-binary-path)
        app_binary_path="$2"
        shift
        shift
        ;;
    -rnp | --release-notes-prefix)
        release_notes_prefix="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${app_binary_path}" "${release_notes_prefix}"
