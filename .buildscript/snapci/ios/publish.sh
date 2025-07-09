#! /usr/bin/env bash

set -exo pipefail

readonly script_dir=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)
readonly buildscript_dir=$(dirname "$(dirname "${script_dir}")")

export APPLIVERY_PUBLICATIONS="camerakit-sample-partner-ios-release"
export APPLIVERY_APP_NAME="camerakit-sample-partner-ios"
export APPLIVERY_ENABLE_DOWNLOAD=1

source "${buildscript_dir}/snapci/setup_environment.sh"

if [ "$test_mode" = "true" ]; then
    echo "🔍 [DEBUG] Test mode is enabled, skipping script execution"
    # Create applivery_release_info.json with download URL
    cat > "$CI_OUTPUTS/applivery_release_info.json" << 'EOF'
{
  "download_url": "https://store.snap.applivery.io/camerakit-sample-partner-ios-release?os=ios&build=685e1f1ad3ca6391d47ffc88"
}
EOF
    exit 0
fi

source "${buildscript_dir}/snapci/image/provision_mac_for_ios.sh" --skip-python

pushd "${buildscript_dir}"
  ./ios/publish.sh
popd