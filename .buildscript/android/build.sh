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
readonly samples_android_root="${script_dir}/../../samples/android"
readonly program_name=$0

usage() {
    echo "usage: ${program_name} [-e, --eject-to path] [-b, build-type]"
    echo "  -e, eject-to path [optional] specify filesystem path to eject publishable project sources to"
    echo "                    Default: none, build only, no sources are ejected"
    echo "  -b, build-type    [optional] specify Android application build type"
    echo "                    Default: debug"
}

main() {
    local eject_to=$1
    local build_type=$2

    source "${script_dir}/prepare_build_environment.sh"
    echo "Android SDK root: ${ANDROID_SDK_ROOT}"

    pushd "${samples_android_root}/camerakit-sample"

    local build_type_assemble_task_name="assemble"$(echo $build_type | awk '{ print toupper(substr($0, 1, 1)) substr($0, 2) }')""
    ./gradlew check "${build_type_assemble_task_name}"

    if [[ -n "$eject_to" ]]; then
        ./gradlew eject -PoutputDir="${eject_to}"
    fi

    popd
    :
}

eject_to_directory=""
build_type="debug"

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -e | --ejecto-to)
        eject_to_directory="$2"
        shift
        shift
        ;;
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

main "${eject_to_directory}" "${build_type}"