#!/bin/bash

set -euo pipefail
set -x 

# CONFIG
PYTHON_VERSION="3.9.16"
PYTHON_SHA256="1ad539e9dbd2b42df714b69726e0693bc6b9d2d2c8e91c2e43204026605140c5"

OPENSSL_VERSION="3.0.8"
OPENSSL_SHA256="6c13d2bf38fdf31eac3ce2a347073673f5d63263398f1f69d0df4a41253e4b3e"

INSTALL_DIR="$HOME/python"
OPENSSL_DIR="$HOME/openssl"

mkdir -p /tmp/build
pushd /tmp/build

# Build OpenSSL from source
curl -LO "https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz"

# Verify OpenSSL SHA256
ACTUAL_OPENSSL_HASH=$(shasum -a 256 "openssl-${OPENSSL_VERSION}.tar.gz" | awk '{print $1}')
if [[ "$ACTUAL_OPENSSL_HASH" != "$OPENSSL_SHA256" ]]; then
  echo "❌ OpenSSL SHA256 hash mismatch!"
  echo "Expected: $OPENSSL_SHA256"
  echo "Actual:   $ACTUAL_OPENSSL_HASH"
  exit 1
else
  echo "✅ OpenSSL SHA256 hash verified: $ACTUAL_OPENSSL_HASH"
fi

tar -xzf "openssl-${OPENSSL_VERSION}.tar.gz"
pushd "openssl-${OPENSSL_VERSION}"

./Configure darwin64-arm64-cc --prefix="$OPENSSL_DIR" no-shared
make -j"$(sysctl -n hw.ncpu)"
make install_sw

popd  # Back to /tmp/build

# Build Python from source
curl -O "https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tgz"

# Verify Python SHA256
ACTUAL_PYTHON_HASH=$(shasum -a 256 "Python-${PYTHON_VERSION}.tgz" | awk '{print $1}')
if [[ "$ACTUAL_PYTHON_HASH" != "$PYTHON_SHA256" ]]; then
  echo "❌ Python SHA256 hash mismatch!"
  echo "Expected: $PYTHON_SHA256"
  echo "Actual:   $ACTUAL_PYTHON_HASH"
  exit 1
else
  echo "✅ Python SHA256 hash verified: $ACTUAL_PYTHON_HASH"
fi

tar -xzf "Python-${PYTHON_VERSION}.tgz"
pushd "Python-${PYTHON_VERSION}"

export PKG_CONFIG_PATH="$OPENSSL_DIR/lib/pkgconfig"

./configure \
  --prefix="$INSTALL_DIR" \
  --enable-optimizations \
  --with-openssl="$OPENSSL_DIR" \
  CPPFLAGS="-I$OPENSSL_DIR/include" \
  LDFLAGS="-L$OPENSSL_DIR/lib"

make -j"$(sysctl -n hw.ncpu)"
make install

popd  # Back to /tmp/build
popd  # Return to original working directory

export PATH="$INSTALL_DIR/bin:$PATH"

# Install packages inside the venv
python -m pip install --upgrade pip
python -m pip install grip