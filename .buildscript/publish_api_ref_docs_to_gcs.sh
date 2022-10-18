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

main() {
    local uri=$1
    local eject_dir=$(mktemp -d -t "camerakit-eject-XXXXXXXXXX")

    "${script_dir}/build.sh" -k false -z false -e "${eject_dir}" -f "public" --docs-only true

    gsutil -o GSUtil:parallel_process_count=1 -o GSUtil:parallel_thread_count=24 cp -R "${eject_dir}/docs/api/*" "${uri}"
}


gcs_uri="gs://snap-kit-reference-docs-staging/CameraKit"

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -u | --uri)
        gcs_uri="$2"
        shift
        shift
        ;;
    *)
        exit
        ;;
    esac
done

main "${gcs_uri}" 
