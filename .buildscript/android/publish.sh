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
readonly samples_android_kotlin_root="${samples_android_root}/camerakit-sample/camerakit-sample-kotlin"

main() {
    local build_type=$1
    pushd "${script_dir}"
    ./build.sh -b "${build_type}"
    popd

    local apk_path="${samples_android_kotlin_root}/build/outputs/apk/${build_type}/camerakit-sample-kotlin-${build_type}.apk"
    if [ -e "${apk_path}" ]
    then
        echo "Publishing apk: ${apk_path}"
        pushd "${script_dir}/.."
        ./publish_to_appcenter.sh -a "${apk_path}"
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