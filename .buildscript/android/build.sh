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
    echo "usage: ${program_name} [-e, --eject-to path] [-b, --build-type] [-f, --build-flavor] [-k, --run-karma]"
    echo "  -e, --eject-to path [optional] specify filesystem path to eject publishable project sources to"
    echo "                      Default: none, build only, no sources are ejected"
    echo "  -b, --build-type    [optional] specify Android application build type"
    echo "                      Default: debug"
    echo "  -f  --build-flavor  [optional] specify the flavor of the build to perform" 
    echo "                      Default: partner. Other flavors available: public, dev"
    echo "  -k, --run-karma     [optional] specify if tests should run on Karma"
    echo "                      Default: false"
}

main() {
    local eject_to=$1
    local build_type=$2
    local build_flavor=$3
    local run_karma=$4

    if [ "$USER" != "snapci" ]; then
        source "${script_dir}/prepare_build_environment.sh"
    fi

    echo "Android SDK root: ${ANDROID_SDK_ROOT}"

    pushd "${samples_android_root}"

    ./gradlew clean
    rm -rf maven/*

    local build_type_assemble_task_name="assemble"$(echo $build_type | awk '{ print toupper(substr($0, 1, 1)) substr($0, 2) }')""

    declare -a  extra_tasks=()
    if [ "$run_karma" = true ]
    then
        extra_tasks+=("karmaTest")
        extra_tasks+=("-PtestBuildType=${build_type}")
    fi

    ./gradlew check "${build_type_assemble_task_name}" "${extra_tasks[@]:+${extra_tasks[@]}}"

    if [[ -n "$eject_to" ]]; then
        ./gradlew eject -PoutputDir="${eject_to}" -Pflavor="${build_flavor}"
        echo "Sanity check of ejected project build in: ${eject_to}"
        pushd "${eject_to}"
        ./gradlew assembleDebug
        rm -rf `find . -type d -name build`
        rm -rf .gradle
        popd
    fi

    popd
    :
}

eject_to_directory=""
build_type="debug"
build_flavor="partner"
run_karma=false

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
    -f | --build-flavor)
        build_flavor="$2"
        shift
        shift
        ;;
    -k | --run-karma)
        run_karma="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${eject_to_directory}" "${build_type}" "${build_flavor}" "${run_karma}"