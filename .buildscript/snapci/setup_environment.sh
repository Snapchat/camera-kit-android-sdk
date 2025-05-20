#! /usr/bin/env bash

# Environment variables that were available on Jenkins for compatibility.

export CIRCLECI=1
export CI=1
export repo="$CI_REPO_NAME"
export JOB_NAME="$CODE_PIPELINE_STEP_NAME"
export BUILD_NUMBER="$(date +%s)"
export branch="$CI_BRANCH"
export pull_number="${CI_PULL_REQUEST:-}"

