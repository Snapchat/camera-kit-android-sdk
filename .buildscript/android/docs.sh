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
    echo "usage: ${program_name} [-e --eject-to path]"
    echo "  -e eject-to path [optional] specify filesystem path to eject publishable documentation files to"
    echo "                   Default: temporary directory"
}

main() {
    local eject_to=$1

    pushd "${samples_android_root}/camerakit-sample"

    if [[ -z "$eject_to" ]]; then
        eject_to=$(mktemp -d -t "camerakit-android-docs-XXXXXXXXXX")
    fi

    ./gradlew ejectDocs -PoutputDir="${eject_to}"

    popd
    :
}


eject_to_directory=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -e | --ejecto-to)
        eject_to_directory="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${eject_to_directory}"
