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
readonly changelog="${repo_root}/CHANGELOG.md"
readonly changelog_old="${repo_root}/CHANGELOG.old.md"
readonly git_chglog_version="0.15.0"

main() {
    downloadUrl="none"
    sha512sum="none"
    if [ "$(uname)" == "Darwin" ]
    then
        downloadUrl="https://github.com/git-chglog/git-chglog/releases/download/v${git_chglog_version}/git-chglog_${git_chglog_version}_darwin_amd64.tar.gz"
        sha512sum=9e39032f840b5b35946dcbb91b2449ef34add1d5d29cedf0a7c54dee56c22cf7fcdbcdabc9b2eefec9c2891934bd8f81cb3bb63a4966a0b3a8f2d79226e0c349
    else
        downloadUrl="https://github.com/git-chglog/git-chglog/releases/download/v${git_chglog_version}/git-chglog_${git_chglog_version}_linux_amd64.tar.gz"
        sha512sum=bf5b0f1fb7db02c14e96d53842a81f7d874bd637e681807785799988bf5b03eea44c8916727013eeeb227c3a0d26ab9c74b2d7e007389f781113e676fc3f28df
    fi

    tempPath="/tmp/git-chglog-${git_chglog_version}"
    downloadPath="${tempPath}.tar.gz"

    if [ ! -f "${downloadPath}" ]; then
        echo "Downloading $downloadUrl"
        curl -L -o "${downloadPath}" "${downloadUrl}"
    fi

    sum=$(openssl dgst -hex -sha512 ${downloadPath} | cut -d "=" -f 2 | sed -e 's/^[[:space:]]*//')
    if [ "$sha512sum" != "$sum" ]
    then
        echo "Sha512 checksum don't match expected: $sha512sum, got: $sum"
        exit 1
    fi

    mkdir -p "${tempPath}"
    tar -xf "${downloadPath}" -C "${tempPath}"
    binPath="${tempPath}/git-chglog"
    chmod +x "${binPath}"

    local next_tag=$1
    if [[ -z "$next_tag" ]]; then
        optional_next_tag=""
    else
        optional_next_tag="--next-tag $next_tag"
    fi
    # We are starting with 1.7.1 and appending an older changelog due to the fact that our commit/tag history
    # got messed up prior to 1.7.1 and the result changelog had entries interleaved etc.
    "${binPath}" $optional_next_tag -o "${changelog}" 1.7.1..
    cat "${changelog_old}" >> "${changelog}"
}

next_tag=""

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -nt | --next-tag)
        next_tag="$2"
        shift
        shift
        ;;
    *)
        exit
        ;;
    esac
done

main "${next_tag}"