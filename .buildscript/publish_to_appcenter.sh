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
readonly appcenter_app_content_type="application/octet-stream"
readonly appcenter_api_path_apps_release_uploads="${appcenter_api_path_apps}/${appcenter_owner_name}/${appcenter_app_name}/uploads/releases"
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

function upload_app {
    local upload_url=$1
    local finish_upload_url=$2
    local app_path=$3
    local chunck_size=$4
    
    local app_folder=$(dirname $app_path)
    mkdir -p "$app_folder/app_chunks"
    split -b $chunck_size $app_path "$app_folder/app_chunks/chunk"
    
    # Upload chunks to App Center
    local block_number=0
    for i in $app_folder/app_chunks/*
    do
        block_number=$(($block_number + 1))
        local length=$(wc -c "$i" | awk '{print $1}')
        local upload_chunck=$(curl -X POST "$upload_url/&block_number=$block_number" --data-binary "@$i" -H "Content-Length: $length" -H "Content-Type: $appcenter_app_content_type")
    done
    
    # Call finish uploading URL
    local upload_chunck=$(curl -d POST -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Token: $appcenter_token" "$finish_upload_url")
}

function commit_release {
    local release_uploads=$1
    local upload_id=$2
    
    # Send Patch request
    local id_response=$(curl -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Token: $appcenter_token" --data '{"upload_status": "uploadFinished"}' -X PATCH "$release_uploads/$upload_id")
    
    local retry_count=0
    local response_data
    local upload_status
    while [[ $retry_count -lt 60 ]];
    do
        sleep 2 # Waiting a bit for release to be ready for distribution
        
        response_data=$(curl -H "Content-Type: application/json" -H "X-API-Token: $appcenter_token" "$release_uploads/$upload_id")
        upload_status=$(echo $response_data | jq -r .upload_status)
        
        if [ "$upload_status" = "readyToBePublished" ]; then
            break
        fi
        
        if [ "$upload_status" = "error" ]; then
            echo "Error: app is not ready to be published, see upload_status: $upload_status"
            exit 1
        fi
        
        retry_count=$(( $retry_count + 1 ))
    done
    
    local release_id=$(echo $response_data | jq -r .release_distinct_id)
    echo "$release_id"
}

function appcenter_upload {
    local app_binary_path=$1
    local release_notes_prefix=$2

    local upload_info=$(curl -X POST "${appcenter_api_path_apps_release_uploads}" -H "X-API-Token: ${appcenter_token}" -H "Content-Type: application/json")
    local upload_domain=$(echo $upload_info | jq -r .upload_domain)
    local asset_id=$(echo $upload_info | jq -r .package_asset_id)
    local token=$(echo $upload_info | jq -r .url_encoded_token)
    local upload_id=$(echo $upload_info | jq -r .id)
    local app_size=$(wc -c "$app_binary_path" | awk '{print $1}')
    local app_name=$(basename $app_binary_path)
    local upload_data_url="$upload_domain/upload/set_metadata/$asset_id?file_name=$app_name&file_size=$app_size&content_type=$appcenter_app_content_type&token=$token"
    local upload_data=$(curl -s -d POST -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Token: $appcenter_token" "$upload_data_url")
    local chunck_size=$(echo $upload_data | jq -r .chunk_size)

    local upload_app_url="$upload_domain/upload/upload_chunk/$asset_id?token=$token"
    local finish_upload_url="$upload_domain/upload/finished/$asset_id?token=$token"
    
    # Upload all chuncks to App Center
    upload_app $upload_app_url $finish_upload_url $app_binary_path $chunck_size
    
    local appcenter_release_id=$(commit_release $appcenter_api_path_apps_release_uploads $upload_id)
    local distribution_url="${appcenter_api_path_apps_releases}/${appcenter_release_id}"
    local release_notes="${release_notes_prefix}COMMIT_SHA:${head_sha}, BUILD_TYPE:${job_name} build off ${branch} branch authored by ${committer_name}, BUILD LOGS: https://developer-portal.sc-corp.net/log-viewer/jenkins-classic/${job_name}/${build_number}, BUILD ARTIFACTS: https://console.cloud.google.com/storage/browser/snapengine-builder-artifacts/$job_name/$build_number"

    if [[ "${appcenter_enable_download}" -ne 0 ]];then
        IFS=' '  read -a groups <<< "${appcenter_distribution_group}"
        for group in "${groups[@]}"
            do
                local update_release_id=$(curl -X PATCH -H "Content-Type: application/json" -H "Accept: application/json" -H "X-API-Token:${appcenter_token}" --data '{"destinations": [{ "name": "'"$group"'"}], "release_notes": "'"$release_notes"'", "notify_testers": false}' "$distribution_url")
            done
    else
        local update_release_id=$(curl -X PUT -H "Content-Type: application/json" -H "accept: application/json" -H "X-API-Token: ${appcenter_token}" -d "{ \"release_notes\": \"${release_notes}\"  }" "$appcenter_api_path_apps_releases/$appcenter_release_id")
    fi
    unset IFS

    echo "${appcenter_release_id}"
}

function appcenter_upload_with_retries {
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
            break
        fi
        retry_count=$(( $retry_count + 1 ))
    done
    echo "${appcenter_release_id}"
}

main() {
    local app_binary_path=$1
    local release_notes_prefix=$2
    
    if [[ -n "$app_binary_path" ]]; then
        local appcenter_release_id=$((appcenter_upload_with_retries "${app_binary_path}" "${release_notes_prefix}") || true)
        if [[ -z $appcenter_release_id ]]; then
            echo "AppCenter Upload failed"
            exit 1
        fi

        local download_link="https://install.appcenter.ms/orgs/${appcenter_owner_name}/apps/${appcenter_app_name}/releases/${appcenter_release_id}"
        echo "$download_link"
    else
        echo "No app binary path provided, exiting"
        exit 1
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
