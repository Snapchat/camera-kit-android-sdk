#!/bin/bash
# The argument passed to this script is the task name or function you want to run

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# set -o xtrace

pushd ".buildscript/snapci/release_pipeline"

python3 -m pipeline_app.pipeline_runner "${run_step}" "${predefined_state_json_bucket_path}"

popd

