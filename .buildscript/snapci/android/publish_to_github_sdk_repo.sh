#! /usr/bin/env bash

set -exo pipefail

export GITHUB_ANDROID_SDK_REPO="Snapchat/camera-kit-android-sdk"
export ANDROID_HOME=${HOME}/.android-sdk
export ANDROID_SDK_ROOT=${ANDROID_HOME}
export PATH=${ANDROID_HOME}/tools:${PATH}

source .buildscript/snapci/image/provision_mac_for_android.sh
bash .buildscript/android/publish_to_github_sdk_repo.sh