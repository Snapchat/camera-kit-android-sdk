#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

main() {
    if [[ "$USER" != "snapci" ]]; then
        source "${script_dir}/.envconfig"
        sudo xcode-select -s "/Applications/Xcode${CAMERA_KIT_XCODE_VERSION}.app/Contents/Developer"
        sudo xcrun simctl shutdown all
        sudo killall -9 com.apple.CoreSimulator.CoreSimulatorService
        sudo xcrun simctl erase all

        "$script_dir/clean_gems.sh"
    else
        echo "Skipping setup for snapci'"
    fi
}

main
