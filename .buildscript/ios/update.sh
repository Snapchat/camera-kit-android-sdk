#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
# set -o xtrace

readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
readonly program_name=$0
readonly github_base_url="https://github.sc-corp.net"
readonly github_api_repos_url="${github_base_url}/api/v3/repos"
readonly camerakit_distro_repo_base_path="Snapchat/camera-kit-distribution"
readonly phantom_repo_camerakit_sdk_path="SDKs/CameraKit"
readonly phantom_repo_base_path="Snapchat/phantom"
readonly build_file_path="${script_dir}/../../samples/ios/CameraKitSample/.build"
readonly pr_template_file_path="${script_dir}/../../.github/PULL_REQUEST_TEMPLATE.md"

usage() {
    echo "usage: ${program_name} [-r, --revision] [-b, --build] [-p, --create-pr] [-n, --no-branch]"
    echo "  -r, --revision  [required] specify the phantom revision (commit) of SDK to update to"
    echo "  -b, --build         [required] specify the build number of SDK to update to"
    echo "  -p, --create-pr [optional] indicate if PR should be opened"
    echo "                   Default: false"
    echo "  -n, --no-branch [optional] indicate if a new branch should not be created"
    echo "                   Default: false"
}

check_build_artifacts() {
    local -r artifacts_base_url=$1

    gsutil -q stat "${artifacts_base_url}/packages.zip" || handle_missing_artifacts $artifacts_base_url
    gsutil -q stat "${artifacts_base_url}/dSYMs.zip" || handle_missing_artifacts $artifacts_base_url
    gsutil -q stat "${artifacts_base_url}/docs.zip" || handle_missing_artifacts $artifacts_base_url
}

handle_missing_artifacts() {
    local -r artifacts_base_url=$1

    echo "One or more of the expected build artifacts is missing at ${artifacts_base_url}/"
    exit 1
}

update_build_file() {
    local -r base_branch=$1
    local -r update_branch=$2
    local -r revision=$3
    local -r no_branch=$4

    if [[ "$no_branch" = false ]]
    then
        git checkout -B $update_branch
    fi

    echo "CAMERA_KIT_COMMIT=\"${revision}\"" > $build_file_path
    echo "CAMERA_KIT_BUILD=\"${build_number}\"" >> $build_file_path

    git add $build_file_path
}

fetch_commit() {
    local sha=$1
    curl \
        -s \
        -X "GET" \
        -H "Authorization: token ${GITHUB_APIKEY}" \
        "${github_api_repos_url}/${phantom_repo_base_path}/commits/${sha}"
}

fetch_commit_list() {
    local -r sha=$1
    local -r page=$2
    local -r path=$3
    local -r since=$4
    curl \
        -s \
        -X GET \
        -H "Authorization: token ${GITHUB_APIKEY}" \
        "${github_api_repos_url}/${phantom_repo_base_path}/commits?sha=${sha}&path=${path}&per_page=100&page=${page}&since=${since}"
}

create_pr_draft() {
    local -r title=$1
    local -r update_branch=$2
    local -r body=$3
    local -r base_branch=$4
    local -r revision=$5

    git push -u origin $update_branch

    local -r params="{ \"title\":\"${title}\", \"head\":\"${update_branch}\", \"body\":\"${body}\", \"base\":\"${base_branch}\", \"draft\":true }"

    local -r pr_url=$(curl \
        -s \
        -X POST \
        -H "Authorization: token ${GITHUB_APIKEY}" \
        "${github_api_repos_url}/${camerakit_distro_repo_base_path}/pulls" \
        -d "${params}" | \
        jq -r ".issue_url")

    if [ -z ${pr_url} ]
    then
        echo "Failed to open a pull request."
        exit 1
    fi

    assign_pr $pr_url $revision
}

assign_pr() {
    local -r pr_url=$1
    local -r revision=$2

    local -r author=$(curl \
        -s \
        -H "Authorization: token ${GITHUB_APIKEY}" \
        "${github_api_repos_url}/${phantom_repo_base_path}/commits/${revision}" | \
        jq -r ".author.login")

    local -r params="{ \"assignees\":[\"${author}\"] }"

    curl \
        -s \
        -X POST \
        -H "Authorization: token ${GITHUB_APIKEY}" \
        $pr_url \
        -d "${params}"
}

main() {
    local -r revision=$1
    local -r build_number=$2
    local -r should_create_pr=$3
    local -r no_branch=$4

    local -r base_branch=$(git rev-parse --abbrev-ref HEAD)
    local -r update_branch="ios-sdk-update/${revision}/${build_number}"

    local -r artifacts_base_url="gs://snapengine-maven-publish/camera-kit-ios/releases/${revision}/${build_number}"

    if [[ -f $build_file_path ]]
    then
        local prev_revision=$(head -n 1 $build_file_path)
        prev_revision=${prev_revision#*=}
        prev_revision=${prev_revision//$'\"'/}
    else
        echo ".build not found at expected path, aborting"
        exit 1
    fi

    check_build_artifacts $artifacts_base_url
    update_build_file  $base_branch $update_branch $revision $no_branch

    local -r title="[Build][iOS] Update SDK to ${revision}."

    local included_sdk_commits=()
    local prev_revision_element=$( fetch_commit $prev_revision )

    if [[ -n "$prev_revision_element" ]]
    then
        local -r prev_revision_date=$( echo "${prev_revision_element}" | jq -r .commit.author.date  )
        echo "Previous revision date: ${prev_revision_date}"

        local current_page=1
        while :
        do
            local included_commits_count_per_page=${#included_sdk_commits[@]}

            local commit_list=$(fetch_commit_list $revision $current_page $phantom_repo_camerakit_sdk_path $prev_revision_date)

            for row in $(echo "${commit_list}" | jq -r '.[] | @base64'); do
                decode_row() {
                    echo ${row} | base64 --decode
                }

                local sha=$(decode_row | jq -r '.sha')
                if [[ $sha == *"${prev_revision}"* ]] 
                then
                    prev_revision_element=$(decode_row)
                    echo "Found the previous revision ${sha}:"
                    break
                else 
                    echo "Including ${sha}"     
                fi

                included_sdk_commits+=( "$(decode_row)" )
            done

            if [[ -n "$prev_revision_element" ]]
            then 
                break
            fi

            if (( $(expr "${#included_sdk_commits[@]}" - "${included_commits_count_per_page}") <= 0))
            then 
                echo "No more commits to process"
                break
            fi

            current_page=$((current_page+1))
        done

    else
        echo "Could not find the previous revision ${prev_revision}, aborting"
        exit 1
    fi

    local commit_body=""
    if (( "${#included_sdk_commits[@]}" > 0 ))
    then
        commit_body="Updating iOS SDK with the following commits:"
    fi

    for commit in "${included_sdk_commits[@]}"
    do
       local commit_message=$(echo "$commit" | jq -r ".commit.message" | head -n 1)
       commit_message=${commit_message//$'\"'/$'\\"'}
       local commit_http_url=$(echo "$commit" | jq -r ".html_url")
       commit_body="${commit_body}"$'\n'"- ${commit_message}: ${commit_http_url}"
    done

    git commit -m "${title}
    
    ${commit_body}"
    
    if [[ "$should_create_pr" = true ]] && [[ "$no_branch" = false ]]
    then
        local -r template=$(<$pr_template_file_path)
        local -r change_description="Updates commit to \`${revision}\` and build number to \`${build_number}\` in \`.build\`."
        local pr_body=${template//$"**Background**"/$"**Background**\n${commit_body}"}
        pr_body=${pr_body//$"**Change**"/$"**Change**\n${change_description}"}
        pr_body=${pr_body//$'\n'/$'\\n'}
        create_pr_draft "${title}" "${update_branch}" "${pr_body}" "${base_branch}" "${revision}"
    fi
}

revision=""
build_number=""
should_create_pr=false
no_branch=false

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -r | --revision)
        revision="$2"
        shift
        shift
        ;;
    -b | --build)
        build_number="$2"
        shift
        shift
        ;;
    -p | --create-pr)
        should_create_pr=true
        shift
        ;;
    -n | --no-branch)
        no_branch=true
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

if [[ -n "$revision" && -n "$build_number" ]]
then
    main "${revision}" "${build_number}" $should_create_pr $no_branch
else
    echo "Missing paramaters"
    usage
    exit 1
fi
