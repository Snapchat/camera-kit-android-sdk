#! /usr/bin/env bash

set -exo pipefail

readonly script_dir=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)
readonly buildscript_dir=$(dirname "$(dirname "${script_dir}")")

source "$script_dir/setup_environment.sh"

export APPLIVERY_PUBLICATIONS="camerakit-sample-partner-android-release"
export APPLIVERY_APP_NAME="camerakit-sample-partner-android"
export APPLIVERY_ENABLE_DOWNLOAD=1

pushd "${buildscript_dir}"
  ./android/publish.sh
popd
