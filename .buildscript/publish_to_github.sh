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

sensitive_strings=()
while IFS= read -r line; do
  sensitive_strings+=("$line")
done < "${script_dir}/sensitive_strings.txt"

readonly sensitive_string_replacement="REPLACE-THIS-WITH-YOUR-OWN-APP-SPECIFIC-VALUE"

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
    git clone --depth 1 "${camerakit_publish_repo_http_url}" "${repository_dir}"

    pushd "${repository_dir}"
    local branch="sync/${version_name}/${random_id}"
    local base_branch=$(git rev-parse --abbrev-ref HEAD)

    git checkout -b "${branch}"
    git rm -r samples || true
    # Legacy dirs not needed anymore
    git rm -r .doc || true
    git rm -r "docs/api/${version_name}" || true
    # Remove any lingering symlinks or dirs to latest docs
    git rm -r "docs/api/android/latest" || true
    git rm -r "docs/api/android/${version_name}" || true
    git rm -r "docs/api/ios/latest" || true
    git rm -r "docs/api/ios/${version_name}" || true

    "${script_dir}/build.sh" -k false -z false -e "${repository_dir}" -f "public"

    for sensitive_string in ${sensitive_strings[@]}
    do
        find . \( -type d -name .git -prune \) -o -type f -print0 | LC_ALL=C xargs -0 sed -i'.bak' "s/${sensitive_string//\//\\/}/${sensitive_string_replacement}/g"
        find . -type f -name "*.bak" -exec rm -rf {} \;
    done
  
    git add --all

    local update_title="[All] Sync changes for the ${version_name} release"
    local update_body="This syncs all changes for the ${version_name} CameraKit release."

    git commit -m "$update_title"
    git push --set-upstream origin "${branch}" -f
    
    local pr=$(create_pr_draft "${update_title}" "${branch}" "${base_branch}" "${update_body}")
    local pr_html_url=$(echo "${pr}" | jq -r .html_url)

    echo "Created new PR at: ${pr_html_url}"
    popd
}

main
