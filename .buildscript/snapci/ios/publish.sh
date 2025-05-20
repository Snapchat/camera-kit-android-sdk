#! /usr/bin/env bash

set -exo pipefail

readonly script_dir=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)
readonly buildscript_dir=$(dirname "$(dirname "${script_dir}")")

export APPLIVERY_PUBLICATIONS="camerakit-sample-partner-ios-release"
export APPLIVERY_APP_NAME="camerakit-sample-partner-ios"
export APPLIVERY_ENABLE_DOWNLOAD=1

source "${buildscript_dir}/snapci/image/provision_mac_for_ios.sh" --skip-python
source "${buildscript_dir}/snapci/setup_environment.sh"

pushd "${buildscript_dir}"
  ./ios/publish.sh
popd

