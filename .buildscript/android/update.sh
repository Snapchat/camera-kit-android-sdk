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
readonly repo_root="${script_dir}/../.."
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly samples_android_root="${script_dir}/../../samples/android"
readonly samples_android_root_build="${samples_android_root}/camerakit-sample/build.gradle"
readonly github_api_repos_url="https://github.sc-corp.net/api/v3/repos"
readonly camerakit_distro_repo_base_path="Snapchat/camera-kit-distribution"
readonly android_repo_base_path="Snapchat/android"
readonly android_repo_camerakit_sdk_path="snapchat/sdks/camerakit"

usage() {
    echo "usage: ${program_name} [-v, --version] [-p, --create-pr]"
    echo "  -v, --version [required] specify the version of SDK to update to"
    echo "  -p, --create-pr [optional] indicate if PR should be opened"
    echo "                  Default: false"
}

fetch_commit() {
    local sha=$1
    curl -s -X "GET" -H "Authorization: token ${GITHUB_APIKEY}" "${github_api_repos_url}/${android_repo_base_path}/commits/${sha}"
}

fetch_commit_list() {
    local sha=$1
    local page=$2
    local path=$3
    local since=$4
    #  Not using GET /repos/:owner/:repo/compare/:base...:head as it does not support pagination.
    curl -s -X "GET" -H "Authorization: token ${GITHUB_APIKEY}" "${github_api_repos_url}/${android_repo_base_path}/commits?sha=${sha}&path=${path}&per_page=100&page=${page}&since=${since}"
}

create_pr_draft() {
    local title=$1
    local head=$2
    local base=$3
    local body=$4
    local params="{ \"title\":\"${title}\", \"head\":\"${head}\", \"base\":\"${base}\", \"body\":$( jq -aRs . <<<  "${body}" ), \"draft\":true }"
    curl -s -X "POST" -H "Authorization: token ${GITHUB_APIKEY}" "${github_api_repos_url}/${camerakit_distro_repo_base_path}/pulls" -d "${params}"
}

main() {
    local next_version_name=$1
    local next_version_rev=$2
    local next_version_build_number=$3
    local create_pr=$4

    local current_version_rev=""
    local current_version_build_number=""
    if [[ $(cat "${samples_android_root_build}") =~ \"(\$cameraKitDistributionVersion)\-(.*?)\.([0-9]+)\" ]]
    then
        current_version_rev="${BASH_REMATCH[2]}"
        current_version_build_number="${BASH_REMATCH[3]}"
    fi

    if [[ $version_name != $next_version_name ]]
    then
        echo "This script does not support different version names, current: ${version_name}, next: ${next_version_name}"
        exit 1
    fi

    if [[ -n "$current_version_rev" ]] 
    then
        echo "Current version: ${version_name}, revision: ${current_version_rev}, build number: ${current_version_build_number}"

        local included_sdk_commits=()
        local current_version_rev_element=$( fetch_commit $current_version_rev )

        if [[ -n "$current_version_rev_element" ]]
        then
            local current_version_rev_date=$( echo "${current_version_rev_element}" | jq -r .commit.author.date )
            echo "Current version date: ${current_version_rev_date}"

            local current_page=1
            while :
            do
                local included_commits_count_per_page=${#included_sdk_commits[@]}

                local commit_list=$(fetch_commit_list $next_version_rev $current_page $android_repo_camerakit_sdk_path $current_version_rev_date)

                for row in $(echo "${commit_list}" | jq -r '.[] | @base64'); do
                    decode_row() {
                        echo ${row} | base64 --decode
                    }

                    local sha=$(decode_row | jq -r '.sha')
                    if [[ $sha == *"${current_version_rev}"* ]] 
                    then
                        current_version_rev_element=$(decode_row)
                        echo "Found the current version revision ${sha}:"
                        break
                    else 
                        echo "Including ${sha}"     
                    fi

                    included_sdk_commits+=( "$(decode_row)" )
                done

                if [[ -n "$current_version_rev_element" ]]
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
            echo "Could not find the current version revision ${current_version_rev}, aborting"
            exit 1
        fi

        local next_version_name="${version_name}-${next_version_rev}.${next_version_build_number}"
        local update_title="[Build][Android] Update SDK to ${next_version_name}"
        local update_body="This updates Android SDK to \`${next_version_name}\` built in https://snapengine-builder.sc-corp.net/jenkins/job/camerakit-android-publish/${next_version_build_number}"

        if (( "${#included_sdk_commits[@]}" > 0 ))
        then
            update_body="${update_body} which includes:"
        fi

        for commit in "${included_sdk_commits[@]}"
        do
           local commit_message=$(echo "$commit" | jq -r ".commit.message" | head -n 1)
           local commit_http_url=$(echo "$commit" | jq -r ".html_url")
           update_body="${update_body}"$'\n'"- ${commit_message}: ${commit_http_url}"
        done

        echo "Updating current version ${version_name}-${current_version_rev}.${current_version_build_number} to ${next_version_name}-${next_version_rev}.${next_version_build_number} in ${samples_android_root_build}"

        sed -i "s/${current_version_rev}.${current_version_build_number}/${next_version_rev}.${next_version_build_number}/g" "${samples_android_root_build}" 

        git add "${samples_android_root_build}"
        local branch="android-sdk-update/${next_version_name}"
        git checkout -B "${branch}"

        local update_commit_message="${update_title}

        ${update_body}"
        git commit -m "${update_commit_message}"

        if [ "$create_pr" = true ]
        then
            git push origin "${branch}"

            local pr=$(create_pr_draft "${update_title}" "${branch}" "master" "${update_body}")
            local pr_html_url=$(echo "${pr}" | jq -r .html_url)

            echo "Created new PR at: ${pr_html_url}"
        fi
    else
        echo "Could not find current version revision in ${samples_android_root_build}"
        exit 1
    fi
}

next_version_name=""
next_version_rev=""
create_pr=false

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -v | --version)
        next_version_name="$2"
        shift
        shift
        ;;
    -p | --create-pr)
        create_pr=true
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

if [[ -n "$next_version_name" ]]
then
    if [[ "$next_version_name" =~ ^([0-9]+\.[0-9]+\.[0-9])+\-(.*?)\.([0-9]+) ]]
    then
        next_version="${BASH_REMATCH[1]}"
        next_version_rev="${BASH_REMATCH[2]}"
        next_version_build_number="${BASH_REMATCH[3]}"

        echo "Next version: ${next_version}, revision: ${next_version_rev}, build number: ${next_version_build_number}" 

        main "${next_version}" "${next_version_rev}" "${next_version_build_number}" $create_pr
    else
        echo "Could not parse version parts from ${next_version_name}"
    fi
else 
    usage
    exit 1
fi
