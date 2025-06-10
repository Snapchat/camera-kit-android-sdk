#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

readonly script_dir=$( cd "$( dirname ${BASH_SOURCE:-$0} )" && pwd )
readonly gemfile_path="${script_dir}/Gemfile"
readonly gem_out="${script_dir}/.gem-out"

readonly ruby_version=2.6.10
readonly rbenv_hash="9a1f19c4564cc8954bca75640d320fcc98cf40187d73531156629edd36606f2f"
readonly ruby_dir="$HOME/bin"

compareHash() {
    local filepath=$1
    local expected_hash=$2

    # Compute the hash of the file
    local computed_hash
    computed_hash=$(shasum -a 256 "${filepath}" | awk '{ print $1 }')

    # Compare the computed hash with the expected hash
    if [ "$computed_hash" != "$expected_hash" ]; then
        echo "Hash verification failed for file ${filepath}: Expected $expected_hash but got $computed_hash"
        exit 1
    else
        echo "Hash verification succeeded for file ${filepath}."
        chmod +x "${filepath}"
    fi
}

install_ruby() {
    mkdir -p "$ruby_dir"

    echo "Downloading rbenv installer..."
    curl -fsSL -o "${ruby_dir}/rbenv-installer" https://github.com/rbenv/rbenv-installer/raw/main/bin/rbenv-installer
    compareHash "${ruby_dir}/rbenv-installer"  "$rbenv_hash"

    echo "Installing rbenv..."
    "${ruby_dir}/rbenv-installer" 

    export PATH="$HOME/.rbenv/bin:$PATH"
    eval "$(rbenv init -)"

    echo "Installing Ruby ${ruby_version}..."
    rbenv install "$ruby_version"
    rbenv global "$ruby_version"
}

run_jazzy_or_sourcekitten() {
    local jazzy_path
    local sourcekitten_path

    BUNDLE_GEMFILE="$gemfile_path" xcrun bundle install --path "$gem_out"

    jazzy_path=$(BUNDLE_GEMFILE="$gemfile_path" xcrun bundle show jazzy --paths | tail -n 1)
    sourcekitten_path="$jazzy_path/bin/sourcekitten"

    if [ "$1" == "sourcekitten" ]; then
        local outpath=$2
        shift 2
        "$sourcekitten_path" "$@" > "$outpath"
    else
        BUNDLE_GEMFILE="$gemfile_path" xcrun bundle exec jazzy "$@"
    fi
}

main() {
    # install_ruby
    run_jazzy_or_sourcekitten "$@"
}

# Main script execution
main "$@"
