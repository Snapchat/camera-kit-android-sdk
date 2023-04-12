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
readonly version_file="${repo_root}/VERSION"
readonly version_name=$( cat "${version_file}" | tr -d " \t\n\r" )
readonly license_file="${repo_root}/LICENSE"
readonly notice_file="${repo_root}/NOTICE"
readonly changelog_file="${repo_root}/CHANGELOG.md"

usage() {
    echo "usage: ${program_name} [-p --platform <platforms>]"
    echo "  -p platform <platforms> [optional] specify platforms to build"
    echo "                          Default: android,ios"
    echo "  -e export-to uri        [optional] specify uri to export artifacts to"
    echo "                          Default: none, no artifacts will be exported"
    echo "  -k karma-test boolean   [optional] specify if tests on karma automation" 
    echo "                          service should run to complete build"
    echo "                          Default: true, tests will be scheduled to run"
    echo "  -z zip-export boolean   [optional] specify if the export should be zipped" 
    echo "                          or kept as a directory"
    echo "                          Default: true, export is zipped"
    echo "  -f flavor <flavor>      [optional] specify the flavor of the build to perform" 
    echo "                          Default: partner. Other flavors available: public"
}

main() {
    local platforms=$1
    local export_to=$2
    local karma_test=$3
    local zip_export=$4
    local flavor=$5
    local docs_only=$6

    local eject_dir=$(mktemp -d -t "camerakit-eject-XXXXXXXXXX")

    # Copy VERSION file so that ejected builds can use it when running sanity checks
    cp "${version_file}" "${eject_dir}/VERSION"

    local samples_eject_dir="${eject_dir}/samples"
    mkdir -p "${samples_eject_dir}"

    local samples_readme_src="${samples_root}/README.md"
    if [ -e "${samples_readme_src}" ]; then
        cp "${samples_readme_src}" "${samples_eject_dir}/README.md"
    fi

    local docs_eject_dir="${eject_dir}/docs"
    mkdir -p "${docs_eject_dir}"

    pushd "${script_dir}/jenkins-pipeline"
    # This runs a build of the Camera Kit pipeline project which includes quick sanity tests on the release pipeline script.
    ./gradlew build
    popd

    for platform in ${platforms//,/ }
    do
        local platform_samples_eject_dir="${samples_eject_dir}/$platform"
        local platform_docs_eject_dir="${docs_eject_dir}/api/$platform"
        local platform_docs_eject_dir_versioned="${platform_docs_eject_dir}/${version_name}"

        mkdir -p "${platform_samples_eject_dir}"
        mkdir -p "${platform_docs_eject_dir_versioned}"

        pushd "${platform_docs_eject_dir}"
        ln -s "./${version_name}" ./latest
        popd

        if [ "${platform}" == "android" ]; then
            echo "Building platform: ${platform}"

            pushd "${script_dir}/android"
            if [ "$docs_only" = false ]
            then
                ./build.sh -e "${platform_samples_eject_dir}" -k $karma_test -b release -f "${flavor}"
            fi
            ./docs.sh -e "${platform_docs_eject_dir_versioned}"
            popd
            
            echo ""
        elif [ "${platform}" == "ios" ]; then
            echo "Building platform: ${platform}"

            pushd "${script_dir}/ios"
            if [ "$docs_only" = false ]
            then
                ./build.sh -e "${platform_samples_eject_dir}" -f "${flavor}"
            fi
            ./docs.sh -e "${platform_docs_eject_dir_versioned}"
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
    local readme_file="${repo_root}/README.${flavor}.md"
    mv "${eject_dir}" "${distribution_dir}"
    cp "${version_file}" "${distribution_dir}"
    cp "${license_file}" "${distribution_dir}"
    cp "${notice_file}" "${distribution_dir}"
    cp "${changelog_file}" "${distribution_dir}"
    cp "${repo_root}/README.${flavor}.md" "${distribution_dir}/README.md"
    cp -r "${repo_root}/docs" "${distribution_dir}"

    # Replace using suggestions from https://unix.stackexchange.com/a/112024
    find $distribution_dir -type f -name "*.md" -exec sed -i'.bak' -e "s/\${version}/${version_name}/g" {} +
    find $distribution_dir -type f -name "*.bak" -exec rm -rf {} \;

    # When hosting statically we need to convert all markdown files to html,
    # if installed (in the global CI env) we use: https://github.com/joeyespo/grip.
    if ! command -v grip &> /dev/null
    then
        echo "python grip not found, will not convert markdown files to html"
    else
        echo "Converting documentation markdown files to html with python grip"
        for file in $(find "${distribution_dir}/docs" -name '*.md'); do 
            grip "${file}" --export "${file%/*}/index.html" --title=" "; 
        done
    fi

    local distribution_export="${distribution_dir}/."
    if [ "$zip_export" = true ]
    then
        pushd "${distribution_basedir}"
        zip -r "${distribution_zip}" ./*
        popd
        distribution_export="${distribution_zip}"
    fi

    if [ -n "${export_to}" ]; then
        local export_to_path="${export_to}"
        echo "Exporting artifacts to: ${export_to_path}"
        if [[ $export_to == gs* ]]; then
            gsutil cp "${distribution_export}" "${export_to_path}"
        else
            mkdir -p "${export_to_path}"
            cp -PR "${distribution_export}" "${export_to_path}"
        fi
    fi

    :
}

platform="android,ios"
artifact_export_uri=""
karma_test=true
zip_export=true
flavor="partner"
docs_only=false

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
    -k | --karma-test)
        karma_test="$2"
        shift
        shift
        ;;
    -z | --zip-export)
        zip_export="$2"
        shift
        shift
        ;;
    -f | --flavor)
        flavor="$2"
        shift
        shift
        ;;
    -d | --docs-only)
        docs_only="$2"
        shift
        shift
        ;;
    *)
        usage
        exit
        ;;
    esac
done

main "${platform}" "${artifact_export_uri}" "${karma_test}" "${zip_export}" "${flavor}" "${docs_only}"
