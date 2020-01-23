#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly install_path="$1"

echo "Android SDK will be installed to: $install_path"

if [ ! -f "$install_path/tools/android" ]
then
    echo "Installing Android SDK $install_path"
    echo "$(uname) $(uname -m)"

    downloadUrl="none"
    sha512sum="none"
    if [ "$(uname)" == "Darwin" ]
    then
        downloadUrl="https://dl.google.com/android/repository/sdk-tools-darwin-3859397.zip"
        sha512sum=2e0a32b0db836b7692e28ccf80f277a71d90383cb515771e41f9bacbe803c144167bcb6e1215811a9e533aa5b43c5a022048b0229d2700f0b3592d2e96dd8cf2
    else
        downloadUrl="https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip"
        sha512sum=ad0d271ca1b1ee5eb41caa3ab0265e882a0f7813810426dedb35ffd357dd6cd3edce2131f23b0182c0845f20d6f04bc5de6767abfd309bb3a3a7e26a8894bdd6
    fi

    echo "Downloading $downloadUrl"
    downloadPath="/tmp/android-sdk.zip"
    curl -o "${downloadPath}" "${downloadUrl}"

    sum=$(openssl dgst -hex -sha512 ${downloadPath} | cut -d "=" -f 2 | sed -e 's/^[[:space:]]*//')
    if [ "$sha512sum" != "$sum" ]
    then
        echo "Sha512 checksum don't match expected: $sha512sum, got: $sum"
        exit 1
    fi

    unzip "${downloadPath}" -d "${install_path}"
    rm $downloadPath
else
   echo "Looks like Android SDK is already installed into ${install_path}"
fi

yes | "${install_path}/tools/bin/sdkmanager" --licenses || true
