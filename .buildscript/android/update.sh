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
readonly current_version=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly samples_android_root="${script_dir}/../../samples/android"
readonly samples_android_root_build="${samples_android_root}/camerakit-sample/build.gradle"
readonly samples_android_root_properties="${samples_android_root}/camerakit-sample/gradle.properties"
readonly github_api_repos_url="https://github.sc-corp.net/api/v3/repos"
readonly camerakit_distro_repo_base_path="Snapchat/camera-kit-distribution"
readonly android_repo_base_path="Snapchat/android"
readonly android_repo_camerakit_sdk_path="snapchat/sdks/camerakit"

usage() {
    echo "usage: ${program_name} [-v, --version] [-p, --create-pr]"
    echo "  -v, --version   [required] specify the version of SDK to update to"
    echo "  -r, --revision  [optional] specify the revision (commit) of SDK to update to"
    echo "                   Default: attempt to parse revision from the supplied version"
    echo "  -b, --build     [optional] specify the build number of SDK to update to"
    echo "                   Default: attempt to parse build number from the supplied version"
    echo "  -p, --create-pr [optional] indicate if PR should be opened"
    echo "                   Default: false"
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
    local next_version_metadata=$2
    local next_version_rev=$3
    local next_version_build_number=$4
    local create_pr=$5

    local current_version_rev=$( sed -n 's/com.snap.camerakit.build.revision=//p' "${samples_android_root_properties}" )
    local current_version_build_number=$( sed -n 's/com.snap.camerakit.build.number=//p' "${samples_android_root_properties}" )

    if [[ $next_version_name != $current_version* ]]
    then
        echo "This script does not support different version names, current: ${current_version}, next: ${next_version_name}"
        exit 1
    fi

    if [[ -n "$current_version_rev" ]] 
    then
        echo "Current version: ${current_version}, revision: ${current_version_rev}, build number: ${current_version_build_number}"

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

        echo "Updating current version ${current_version}+${current_version_rev}.${current_version_build_number} to ${next_version_name} in ${samples_android_root_build}"

        sed -i "s/cameraKitVersion =.*/cameraKitVersion = \"\$cameraKitDistributionVersion${next_version_metadata}\"/g" "${samples_android_root_build}" 
        sed -i "s/com.snap.camerakit.build.revision=.*/com.snap.camerakit.build.revision=${next_version_rev}/g" "${samples_android_root_properties}"
        sed -i "s/com.snap.camerakit.build.number=.*/com.snap.camerakit.build.number=${next_version_build_number}/g" "${samples_android_root_properties}"

        git add "${samples_android_root_build}"
        git add "${samples_android_root_properties}"
        local branch="android-sdk-update/${next_version_name}"
        local base_branch=$(git rev-parse --abbrev-ref HEAD)
        git checkout -B "${branch}"

        local update_commit_message="${update_title}

        ${update_body}"
        git commit -m "${update_commit_message}"

        if [ "$create_pr" = true ]
        then
            git push origin "${branch}"

            local pr=$(create_pr_draft "${update_title}" "${branch}" "${base_branch}" "${update_body}")
            local pr_html_url=$(echo "${pr}" | jq -r .html_url)

            echo "Created new PR at: ${pr_html_url}"
        fi
    else
        echo "Could not find current version revision in ${samples_android_root_build}"
        exit 1
    fi
}

major_minor_patch=""
next_version_metadata=""
next_version_name=""
next_version_rev=""
next_version_build_number=""
create_pr=false

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -v | --version)
        next_version_name="$2"
        shift
        shift
        ;;
    -r | --revision)
        next_version_rev="$2"
        shift
        shift
        ;;
    -b | --build)
        next_version_build_number="$2"
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
    if [[ "$next_version_name" =~ ^([0-9]+\.[0-9]+\.[0-9]+)?(.*) ]]
    then
        major_minor_patch="${BASH_REMATCH[1]}"
        next_version_metadata="${BASH_REMATCH[2]}"

        if [[ "$next_version_metadata" =~ (\+)(.*?)\.([0-9]+) ]]
        then
            next_version_rev="${BASH_REMATCH[2]}"
            next_version_build_number="${BASH_REMATCH[3]}"
        fi
    else
        echo "Could not parse the provided version: $next_version_name"
    fi

    echo "Next version: ${major_minor_patch}, revision: '${next_version_rev}', build number: '${next_version_build_number}'" 

    if [[ -n "$next_version_rev" && -n "$next_version_build_number" ]]
    then
        main "${next_version_name}" "${next_version_metadata}" "${next_version_rev}" "${next_version_build_number}" $create_pr
    else
        echo "Missing parameters to update version to: ${next_version_name}"
        usage
        exit 1
    fi
    
else 
    usage
    exit 1
fi
