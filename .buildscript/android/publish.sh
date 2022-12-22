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
readonly samples_android_root="${script_dir}/../../samples/android"
readonly samples_android_kotlin_root="${samples_android_root}/camerakit-sample-full"

usage() {
    echo "usage: ${program_name} [-b, --build-type]"
    echo "  -b, --build-type    [optional] specify Android application build type"
    echo "                      Default: release"
}

main() {
    local build_type=$1
    local release_notes_prefix=""

    if [ "$build_type" != "release" ]
    then
        echo "Generating short-lived LCA token"
        pushd "${script_dir}/.."
        export CAMERAKIT_REMOTE_ACCESS_TOKEN=$( ./generate_access_token.sh --audience "${CAMERAKIT_REMOTE_ACCESS_AUDIENCE:-default}" )
        popd
        release_notes_prefix="AUTH_EXPIRES=$( echo "$(expr $(date +%s) + 3600)" | TZ=":America/Los_Angeles" awk '{print strftime("%c", $0)}' ), "
    fi

    pushd "${script_dir}"
    ./build.sh -b "${build_type}"
    popd

    local apk_path="${samples_android_kotlin_root}/build/outputs/apk/${build_type}/camerakit-sample-full-${build_type}.apk"
    if [ -e "${apk_path}" ]
    then
        echo "Publishing apk: ${apk_path}"
        pushd "${script_dir}/.."
        ./publish_to_appcenter.sh -a "${apk_path}" -rnp "${release_notes_prefix}"
        popd
    else
        echo "Could not find apk built in: ${apk_path}"
        exit 1
    fi
  
    :
}

build_type="release"

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -b | --build-type)
        build_type="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${build_type}"