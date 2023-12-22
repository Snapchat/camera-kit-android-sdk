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
    pushd "$(xcrun --sdk macosx --show-sdk-path)/System/Library/Frameworks/Ruby.framework/Versions/2.6/usr/include/ruby-2.6.0"
    
    if [ -f "universal-darwin23/ruby/config.h" ]; then
        # see description below, some scripts are looking for `universal-darwin22`
        # but in MacOS Sonoma / Xcode 15 there is only `universal-darwin23`
        # fixing it by creating symlink to `universal-darwin22`
        if [ ! -f "universal-darwin22/ruby/config.h" ]; then
            sudo ln -s "universal-darwin23" "universal-darwin22"
        fi
    elif [ -f "universal-darwin22/ruby/config.h" ]; then
        # We migrated this build job to Xcode 14.1.
        # Xcode 14.1 gives us artifacts in `universal-darwin22`.
        # It seems that some gems (like `json`) are looking for `universal-darwin21`.
        # This is leading to crashes.
        # We can workaround this for now.
        # We symlink `universal-darwin22` to `universal-darwin21`.
        # We saw a similar situation when we migrated to Xcode 13.3.
        if [ ! -f "universal-darwin21/ruby/config.h" ]; then
            sudo ln -s "universal-darwin22" "universal-darwin21"
        fi
    fi

    popd
}

main
