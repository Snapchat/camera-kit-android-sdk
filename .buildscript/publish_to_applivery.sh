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
readonly repo_root="${script_dir}/.."
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )

# Local build information
readonly head_sha=$(git rev-parse HEAD)
readonly committer_details=$(git --no-pager show -s --format='%an <%ae>')
readonly committer_name_full=$(echo $committer_details | awk -F'[<|>]' '{print $1}')
readonly committer_name=$(echo $committer_name_full | awk -F'[ ]' '{print $1}') # Grab the first name in case full name is specified with a space between first name and last name

# Applivery configuration
readonly applivery_app_token="${APPLIVERY_APP_TOKEN}"
readonly applivery_publications="${APPLIVERY_PUBLICATIONS}"
readonly applivery_app_name="${APPLIVERY_APP_NAME}"
readonly applivery_enable_download="${APPLIVERY_ENABLE_DOWNLOAD}"
readonly applivery_api_path_apps_release_uploads="https://upload.snap.applivery.io/v1/integrations/builds"

# CI environment
readonly job_name="${JOB_NAME}"
readonly build_number="${BUILD_NUMBER}"

function applivery_upload {
    local app_binary_path=$1
    local release_notes_prefix=$2

    local release_notes=""
    if [ "$USER" == "snapci" ]; then
        release_notes="${release_notes_prefix}COMMIT_SHA:${head_sha}, BUILD_TYPE:${job_name} build off BRANCH:${branch}  authored by ${committer_name}, BUILD LOGS: https://ci-portal.mesh.sc-corp.net/cp/pipelines/p/${CI_PIPELINE_ID}"
    else
        release_notes="${release_notes_prefix}COMMIT_SHA:${head_sha}, BUILD_TYPE:${job_name} build off BRANCH:${branch} authored by ${committer_name}, BUILD LOGS: https://developer-portal.sc-corp.net/log-viewer/jenkins-classic/${job_name}/${build_number}, BUILD ARTIFACTS: https://console.cloud.google.com/storage/browser/snapengine-builder-artifacts/$job_name/$build_number"
    fi

   local curl_cmd="curl -s --retry 3 \
    -X POST \"${applivery_api_path_apps_release_uploads}\" \
    -H \"Authorization: Bearer $applivery_app_token\" \
    -H \"Content-Type: multipart/form-data\" \
    -F \"build=@${app_binary_path}\" \
    -F \"changelog=${release_notes}\" \
    -F \"versionName=${version_name}\" \
    -F \"notifyCollaborators=false \" \
    -F \"notifyEmployees=false \""

    # An application can have multiple publications of which each publication can have any number distribution groups.
    # We assign a publication, and thus the distribution groups, to an uploaded artifact by adding a tag with the slug
    # for that publication we want

    if [[ "${applivery_enable_download}" -ne 0 ]];then
        curl_cmd+=" -F \"tags=${applivery_publications}\""
    fi

    local response=$(eval $curl_cmd)
    local applivery_build_id=$(echo "$response" | jq -r '.data.id' | tr -d '\n')

    echo "${applivery_build_id}"
}


main() {
    local app_binary_path=$1
    local operating_system=$2
    local release_notes_prefix=$3
    local output_dir=$4
    local output_file="${output_dir}/applivery_release_info.json"
    
    if [[ -n "$app_binary_path" ]]; then
        local applivery_build_id=$((applivery_upload "${app_binary_path}" "${release_notes_prefix}") || true)

        if [[ -z $applivery_build_id ]]; then
            echo "Applivery Upload failed"
            exit 1
        fi

        IFS=',' read -r -a publications_array <<< "$applivery_publications"

        if [[ -n ${publications_array[0]} && "${applivery_enable_download}" -ne 0 ]]; then
        
            # Getting the first publication
            local publication=${publications_array[0]}
 
            download_link="https://store.snap.applivery.io/${publication}?os=${operating_system}&build=${applivery_build_id}"
        else
            download_link="https://dashboard.snap.applivery.io/snap/apps/${applivery_app_name}/builds?s=build&id=${applivery_build_id}"
        fi


        if [ -n "$pull_number" ]; then
            curl    -H "Authorization: token ${GITHUB_APIKEY}" \
                    -H "Content-Type: application/json" \
                    https://github.sc-corp.net/api/v3/repos/Snapchat/camera-kit-distribution/issues/${pull_number}/comments \
                    --verbose -d "{\"body\": \"Download links for the '${job_name}' build requested:\n\n- Applivery: ${download_link}\"}"
        fi

        echo "{ \"download_url\" : \"$download_link\" }" >> "${output_file}"

        if [ "$USER" == "snapci" ]; then
            gsutil cp "${output_file}" "gs://snapengine-builder-artifacts/${job_name}/${CI_PIPELINE_ID}/applivery_release_info.json"
        fi
    else
        echo "No app binary path provided, exiting"
        exit 1
    fi
}

app_binary_path=""
operating_system=""
release_notes_prefix=""
output_dir="${CI_OUTPUTS:-$(dirname -- "$PWD")}"

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -a | --app-binary-path)
        app_binary_path="$2"
        shift
        shift
        ;;
    -os | --operating-system)
        operating_system="$2"
        shift
        shift
        ;;
    -rnp | --release-notes-prefix)
        release_notes_prefix="$2"
        shift
        shift
        ;;
    -o | --output-dir)
        output_dir="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${app_binary_path}" "${operating_system}" "${release_notes_prefix}" "${output_dir}"

