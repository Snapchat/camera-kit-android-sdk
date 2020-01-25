#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly samples_android_root="../../samples/android"
readonly eject_to_directory=$(mktemp -d -t camerakit-eject-XXXXXXXXXX)

source prepare_build_environment.sh
echo "Android SDK root: ${ANDROID_SDK_ROOT}"

pushd "${samples_android_root}/kotlin"
./gradlew check assembleDebug
./gradlew eject -PoutputDir="${eject_to_directory}"
popd