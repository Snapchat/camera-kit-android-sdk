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
readonly program_name=$0

usage() {
    echo "usage: ${program_name} [-p --platform <platforms>]"
    echo "  -p platform <platforms> [optional] specify platforms to build"
    echo "                          Default: android,ios"
}

main() {
    local platforms=$1
    for platform in ${platforms//,/ }
    do
        if [ "${platform}" == "android" ]; then
            echo "Building platform: ${platform}"

            pushd "${script_dir}/android"
            source build.sh
            popd
            
            echo ""
        elif [ "${platform}" == "ios" ]; then
            echo "Building platform: ${platform}"
            echo ""
        else
            echo "Unrecognized platform: ${platform}"
            exit 1
        fi

    done
    :
}

platform="android,ios"

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -p | --platform)
        platform="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${platform}"
