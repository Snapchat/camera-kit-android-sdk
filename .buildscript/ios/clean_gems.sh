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
readonly gem_path="${script_dir}/../../samples/ios/CameraKitSample/.gem-out"

main() {
    rm -rf "${gem_path}"

    # Xcode bundles ruby along with other frameworks in the subdir
    # Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/System/Library/Frameworks
    # so this path is a static path to ruby's headers that will get added to search paths when executing ruby
    pushd "$(xcode-select -p)/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/System/Library/Frameworks/Ruby.framework/Versions/2.6/usr/include/ruby-2.6.0"

    # Patch ruby env on old xcode installed on v12 nodes
    if [ -f "universal-darwin20/ruby/config.h" ]; then
        if [ ! -f "universal-darwin21/ruby/config.h" ]; then
            sudo ln -s "universal-darwin20" "universal-darwin21"
        fi
    fi

    popd
}

main
