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
readonly repo_root="${script_dir}/.."
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly camerakit_publish_repo_base_path="${GITHUB_REPO}"
readonly camerakit_publish_repo_http_url="https://${GITHUB_USERNAME}:${GITHUB_APIKEY}@github.com/${camerakit_publish_repo_base_path}.git"
readonly github_api_repos_url="https://api.github.com/repos"
readonly random_id=$(openssl rand -hex 4)

create_pr_draft() {
    local title=$1
    local head=$2
    local base=$3
    local body=$4
    local params="{ \"title\":\"${title}\", \"head\":\"${head}\", \"base\":\"${base}\", \"body\":$( jq -aRs . <<<  "${body}" ), \"draft\":true }"
    curl -s -X "POST" -H "Authorization: token ${GITHUB_APIKEY}" "${github_api_repos_url}/${camerakit_publish_repo_base_path}/pulls" -d "${params}"
}

main() {
    local repository_dir=$(mktemp -d -t "camerakit-publish-repository-XXXXXXXXXX")
    git clone "${camerakit_publish_repo_http_url}" "${repository_dir}"

    pushd "${repository_dir}"
    local branch="sync/${version_name}/${random_id}"
    local base_branch=$(git rev-parse --abbrev-ref HEAD)

    git checkout -b "${branch}"
    git rm -r .

    "${script_dir}/build.sh" -k false -z false -e "${repository_dir}" 

    git add .

    local update_title="[All] Sync changes for the ${version_name} release"
    local update_body="This syncs all changes fore the ${version_name} CameraKit release."

    git commit -m "$update_title"
    git push --set-upstream origin "${branch}" -f
    
    local pr=$(create_pr_draft "${update_title}" "${branch}" "${base_branch}" "${update_body}")
    local pr_html_url=$(echo "${pr}" | jq -r .html_url)

    echo "Created new PR at: ${pr_html_url}"
    popd
}

main
