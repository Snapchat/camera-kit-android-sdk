#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly script_name=$(basename "${0}")
readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
readonly repo_root="${script_dir}/.."
readonly git_chglog_version="0.9.1"

main() {
    downloadUrl="none"
    sha512sum="none"
    if [ "$(uname)" == "Darwin" ]
    then
        downloadUrl="https://github.com/git-chglog/git-chglog/releases/download/${git_chglog_version}/git-chglog_darwin_amd64"
        sha512sum=62b398bc295afcb5e8bb61a41cac1a3d6bb7fbda2b7241cf72c737fea8bc9adcd80ea0d52553702e4f28ba65834c75348e07326d36e902f94723bac33d12e291
    else
        downloadUrl="https://github.com/git-chglog/git-chglog/releases/download/${git_chglog_version}/git-chglog_linux_amd64"
        sha512sum=7fe1ccf84b6e6301f62dfc0c6f5005264f3d7cef85c4fb142c6841621abb745a68b28e15da4b3694df8375a4550b77dddec99c7d18959cd0454c1de81e1c7a2b
    fi

    echo "Downloading $downloadUrl"
    downloadPath="/tmp/git-chglog-${git_chglog_version}"

    if [ ! -f "${downloadPath}" ]; then
        curl -L -o "${downloadPath}" "${downloadUrl}"
    fi

    sum=$(openssl dgst -hex -sha512 ${downloadPath} | cut -d "=" -f 2 | sed -e 's/^[[:space:]]*//')
    if [ "$sha512sum" != "$sum" ]
    then
        echo "Sha512 checksum don't match expected: $sha512sum, got: $sum"
        exit 1
    fi

    chmod +x "${downloadPath}"

    "${downloadPath}" -o "${repo_root}/CHANGELOG.md"
}

main
