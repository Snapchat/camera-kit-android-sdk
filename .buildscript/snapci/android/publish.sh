#! /usr/bin/env bash

set -exo pipefail

readonly script_dir=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)
readonly buildscript_dir=$(dirname "$(dirname "${script_dir}")")

export APPLIVERY_PUBLICATIONS="camerakit-sample-partner-android-release"
export APPLIVERY_APP_NAME="camerakit-sample-partner-android"
export APPLIVERY_ENABLE_DOWNLOAD=1

source "$script_dir/setup_environment.sh"

if [ "$test_mode" = "true" ]; then
    echo "🔍 [DEBUG] Test mode is enabled, skipping script execution"
    # Create applivery_release_info.json with download URL
    cat > "$CI_OUTPUTS/applivery_release_info.json" << 'EOF'
{
  "download_url": "https://store.snap.applivery.io/camerakit-sample-partner-android-release?os=android&build=685e1f1ad3ca6391d47ffc88"
}
EOF
    exit 0
fi

pushd "${buildscript_dir}"
  ./android/publish.sh
popd

