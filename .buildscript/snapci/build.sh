#! /usr/bin/env bash
set -eo pipefail
 
source "${CI_WORKSPACE}/.buildscript/snapci/setup_environment.sh"

if [ "$test_mode" = "true" ]; then
    echo "🔍 [DEBUG] Test mode is enabled, skipping script execution"
    gsutil cp "gs://snapengine-builder-artifacts/Build: CameraKit Distribution/538522b2-1ec7-4559-93d9-3cf3ffec7afc/camerakit-distribution.zip" "gs://snapengine-builder-artifacts/camkit_distribution_build/${CI_PIPELINE_ID}/camerakit-distribution.zip"
    exit 0
fi

source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_android.sh"
source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_ios.sh"

pushd "${CI_WORKSPACE}/.buildscript"
  ./build.sh -p ios,android -e "gs://snapengine-builder-artifacts/camkit_distribution_build/${CI_PIPELINE_ID}/camerakit-distribution.zip"
popd