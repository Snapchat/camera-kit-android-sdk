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
readonly samples_root="${script_dir}/../samples"
readonly distribution_archive_basename="camerakit-distribution"
readonly program_name=$0
readonly version_name=$( cat "${repo_root}/VERSION" | tr -d " \t\n\r" )

usage() {
    echo "usage: ${program_name} [-p --platform <platforms>]"
    echo "  -p platform <platforms> [optional] specify platforms to build"
    echo "                          Default: android,ios"
    echo "  -e export-to uri        [optional] specify uri to export artifacts to"
    echo "                          Default: none, no artifacts will be exported"
}

main() {
    local platforms=$1
    local export_to=$2

    local eject_dir=$(mktemp -d -t "camerakit-eject-XXXXXXXXXX")

    local samples_eject_dir="${eject_dir}/samples"
    mkdir -p "${samples_eject_dir}"
    
    local samples_readme_src="${samples_root}/README.md"
    if [ -e "${samples_readme_src}" ]; then
        cp "${samples_readme_src}" "${samples_eject_dir}/README.md"
    fi

    for platform in ${platforms//,/ }
    do
        local platform_eject_dir="${samples_eject_dir}/$platform"
        mkdir -p "${platform_eject_dir}"

        if [ "${platform}" == "android" ]; then
            echo "Building platform: ${platform}"

            pushd "${script_dir}/android"
            ./build.sh -e "${platform_eject_dir}/camerakit-sample"
            popd
            
            echo ""
        elif [ "${platform}" == "ios" ]; then
            echo "Building platform: ${platform}"

            pushd "${script_dir}/ios"
            ./build.sh -e "${platform_eject_dir}"
            popd

            echo ""
        else
            echo "Unrecognized platform: ${platform}"
            exit 1
        fi
    done

    local distribution_basedir="$(mktemp -d -t "camerakit-distribution-XXXXXXXXXX")"
    local distribution_dir="${distribution_basedir}/${distribution_archive_basename}"
    local distribution_zip="${distribution_basedir}/${distribution_archive_basename}.zip"
    mv "${eject_dir}" "${distribution_dir}"
    cp -r "${repo_root}/.doc" "${distribution_dir}"
    sed -e "s/\${version}/${version_name}/" "${repo_root}/README.partner.md" > "${distribution_dir}/README.md"

    pushd "${distribution_basedir}"
    zip -r "${distribution_zip}" ./*
    popd

    if [ -n "${export_to}" ]; then
        local export_to_path="${export_to}"
        echo "Exporting artifacts to: ${export_to_path}"
        if [[ $export_to == gs* ]]; then
            gsutil cp "${distribution_zip}" "${export_to_path}"
        else
            mkdir -p "${export_to_path}"
            cp "${distribution_zip}" "${export_to_path}"
        fi
    fi

    :
}

platform="android,ios"
artifact_export_uri=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -p | --platform)
        platform="$2"
        shift
        shift
        ;;
    -e | --export-to)
        artifact_export_uri="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${platform}" "${artifact_export_uri}"
