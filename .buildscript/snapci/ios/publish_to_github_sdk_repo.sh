#! /usr/bin/env bash

set -x
set -euo pipefail
set -o xtrace

export GITHUB_IOS_SDK_REPO="Snapchat/camera-kit-ios-sdk"

python3 -m venv .venv
source .venv/bin/activate

source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_ios.sh"
bash "${CI_WORKSPACE}/.buildscript/ios/publish_to_github_sdk_repo.sh"