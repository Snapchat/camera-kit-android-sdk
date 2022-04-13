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

if [ ! -f "${install_path}/cmdline-tools/latest/bin/sdkmanager" ]
then
    echo "Installing Android SDK $install_path"
    echo "$(uname) $(uname -m)"

    downloadUrl="none"
    sha512sum="none"
    if [ "$(uname)" == "Darwin" ]
    then
        downloadUrl="https://dl.google.com/android/repository/commandlinetools-mac-8092744_latest.zip"
        sha512sum=92959263d4a7ea5c701c84145d185c71324ea97c59967e59d2be7a819cabaf1b0c602eb2f7a82b70c5cd6a71b92cf1e92e84a3899edc5ab657e984c04aa2b7bc
    else
        downloadUrl="https://dl.google.com/android/repository/commandlinetools-linux-8092744_latest.zip"
        sha512sum=db80ec1466acc4fdc2ef2eaa68450b947f6302a1e340411ff5be828d0c2ecfbbdab85d20f3607b939882cfa3a5db2dfd5ad1180e085af4e0c1482bd8bf208d52
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

    # sdkmanager checks if it is under /cmdline-tools/latest/bin so move it there.
    if [[ ! -f "${install_path}/cmdline-tools/latest/bin/sdkmanager" ]]; then
        mkdir $install_path/latest
        mv $install_path/cmdline-tools/* $install_path/latest
        mv $install_path/latest $install_path/cmdline-tools
    fi

    rm "${downloadPath}"
else
   echo "Looks like Android SDK is already installed into ${install_path}"
fi

yes | "${install_path}/cmdline-tools/latest/bin/sdkmanager" --licenses || true
