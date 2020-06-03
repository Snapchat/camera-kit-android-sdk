#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail

readonly script_name=$(basename "${0}")
readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
readonly repo_root="${script_dir}/.."
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly checklist_file="${repo_root}/RELEASING.md"

main() {
    local version_name_next=$1
    if [[ -z "$version_name_next" ]]; then
        echo "---next-version is required"
        exit 1
    fi

    sed -e "s|\${version}|${version_name}|g" -e "s|\${version_next}|${version_name_next}|g" "${checklist_file}"
}

version_name_next=""

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -nv | --next-version)
        version_name_next="$2"
        shift
        shift
        ;;
    *)
        exit
        ;;
    esac
done

main "${version_name_next}"
