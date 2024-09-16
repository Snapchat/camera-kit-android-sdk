#! /usr/bin/env bash

set -exo pipefail

readonly script_dir=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)
readonly buildscript_dir=$(dirname "$(dirname "${script_dir}")")

source "$script_dir/setup_environment.sh"

export APPCENTER_DISTRIBUTION_GROUP="CameraKit-Android-Testers Engineering-AllAccess"
export APPCENTER_APP_NAME="CameraKit-Sample-Partner"
export APPCENTER_OWNER_NAME="app-2q6u"
export APPCENTER_ENABLE_DOWNLOAD=1

pushd "${buildscript_dir}"
  ./android/publish.sh
popd
