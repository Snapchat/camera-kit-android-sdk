#!/bin/bash

set -euo pipefail

readonly build_info_file="build_info.json"

pushd "${CI_WORKSPACE}"
cat > "${build_info_file}" << EOF
{
    "commit": "$CI_CURRENT_COMMIT",
    "build_number": "${BUILD_NUMBER}",
    "branch": "${CI_CURRENT_BRANCH}",
    "pipeline_id": "${CI_PIPELINE_ID}"
}
EOF
popd

cp "${build_info_file}" "${CI_OUTPUTS}/"
gsutil cp "${build_info_file}" "gs://snapengine-builder-artifacts/${CODE_PIPELINE_STEP_NAME}/${CI_PIPELINE_ID}/${build_info_file}"

