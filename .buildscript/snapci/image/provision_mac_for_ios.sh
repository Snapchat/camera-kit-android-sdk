#!/bin/bash

set -euo pipefail
set -x 

# CONFIG
readonly python_version="3.9.16"
readonly python_hash="1ad539e9dbd2b42df714b69726e0693bc6b9d2d2c8e91c2e43204026605140c5"
readonly python_dir="$HOME/python"

readonly openssl_version="3.0.8"
readonly openssl_hash="6c13d2bf38fdf31eac3ce2a347073673f5d63263398f1f69d0df4a41253e4b3e"
readonly openssl_dir="$HOME/openssl"

readonly ruby_version=2.6.10
readonly rbenv_hash="9a1f19c4564cc8954bca75640d320fcc98cf40187d73531156629edd36606f2f"
readonly ruby_dir="$HOME/bin"

compareHash() {
    local filepath=$1
    local expected_hash=$2

    # Compute the hash of the file
    local computed_hash=$(shasum -a 256 "${filepath}" | awk '{ print $1 }')

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

    curl -fsSL -o "${ruby_dir}/rbenv-installer" https://github.com/rbenv/rbenv-installer/raw/main/bin/rbenv-installer
    compareHash "${ruby_dir}/rbenv-installer"  "$rbenv_hash"

    # Execute rbenv installer
    "${ruby_dir}/rbenv-installer" 

    export PATH="$HOME/.rbenv/bin:$PATH"
    eval "$(rbenv init -)"

    # Install Ruby inside user directory
    rbenv install $ruby_version
    rbenv global $ruby_version
}

install_python() {
    local tmp_dir="/tmp/build"
    local openssl_filename="openssl-${openssl_version}.tar.gz"
    local python_filename="Python-${python_version}.tgz"
    
    mkdir -p "${tmp_dir}"
    pushd "${tmp_dir}"

    # Build OpenSSL from source
    curl -LO "https://github.com/openssl/openssl/releases/download/openssl-${openssl_version}/openssl-${openssl_version}.tar.gz"

    compareHash "${tmp_dir}/${openssl_filename}" $openssl_hash

    tar -xzf $openssl_filename
    pushd "openssl-${openssl_version}"

    ./Configure darwin64-arm64-cc --prefix="$openssl_dir" no-shared
    make -j"$(sysctl -n hw.ncpu)"
    make install_sw

    popd  # Back to tmp_dir

    # Build Python from source
    curl -O "https://www.python.org/ftp/python/${python_version}/${python_filename}"

    compareHash "${tmp_dir}/${python_filename}" $python_hash

    tar -xzf $python_filename
    pushd "Python-${python_version}"

    export PKG_CONFIG_PATH="$openssl_dir/lib/pkgconfig"

    ./configure \
    --prefix="$python_dir" \
    --enable-optimizations \
    --with-openssl="$openssl_dir" \
    CPPFLAGS="-I$openssl_dir/include" \
    LDFLAGS="-L$openssl_dir/lib"

    make -j"$(sysctl -n hw.ncpu)"
    make install

    popd  # Back to tmp_dir
    popd  # Return to original working directory

    echo "export PATH=\"$python_dir/bin:\$PATH\"" >> ~/.bash_profile
    echo "export PKG_CONFIG_PATH=\"$openssl_dir/lib/pkgconfig\"" >> ~/.bash_profile

    source ~/.bash_profile

    # Install packages inside the venv
    python -m pip install --upgrade pip
    python -m pip install grip
}

main() {
    local skip_python=$1
    local skip_ruby=$2

    if [[ "$skip_python" == "false" ]]; then
        install_python
    else
        echo "Skipping Python install..."
    fi

    if [[ "$skip_ruby" == "false" ]]; then
        install_ruby
    else 
        echo "Skipping Ruby install..."
    fi
}

skip_python=false
skip_ruby=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -sp | --skip-python)
        skip_python=true
        shift
        ;;
    -sr | --skip-ruby)
        skip_ruby=true
        shift
        ;;
    *)
        echo "Usage: $0 [--skip-python] [--skip-ruby]"
        exit 1
        ;;
    esac
done

main "${skip_python}" "${skip_ruby}"

