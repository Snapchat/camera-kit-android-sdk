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
readonly export_path="${script_dir}/build"
readonly ipa_path="${export_path}/CameraKitSample.ipa"

main() {
    pushd "${script_dir}"
    ./build.sh -i "${export_path}"
    popd

    if [ -e "${ipa_path}" ]
    then
        echo "Publishing ipa: ${ipa_path}"
        pushd "${script_dir}/.."
        ./publish_to_appcenter.sh -a "${ipa_path}"
        popd
    else
        echo "Could not find ipa build in: ${ipa_path}"
        exit 1
    fi

    :
}

main
