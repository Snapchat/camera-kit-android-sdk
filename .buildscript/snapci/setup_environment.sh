#! /usr/bin/env bash

# Environment variables that were available on Jenkins for compatibility.

export CIRCLECI=1
export CI=1
export repo="$CI_CURRENT_REPO"
export JOB_NAME="$CODE_PIPELINE_STEP_NAME"
export BUILD_NUMBER="$(date +%s)"
export branch="$CI_CURRENT_BRANCH"
export pull_number="${CI_PULL_REQUEST:-}"

bash "$CI_WORKSPACE/.buildscript/snapci/set_build_info.sh"