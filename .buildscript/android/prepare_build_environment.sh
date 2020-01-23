#!/bin/bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

export ANDROID_HOME=${HOME}/.android-sdk
export ANDROID_SDK_ROOT=${ANDROID_HOME}

bash install_sdk.sh "${ANDROID_HOME}"
