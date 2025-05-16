#! /usr/bin/env bash

JDK_BUILD="jdk-11.0.22+7"
JDK_URL="https://github.com/adoptium/temurin11-binaries/releases/download/${JDK_BUILD}/OpenJDK11U-jdk_aarch64_mac_hotspot_11.0.22_7.tar.gz"
JDK_SHA256="4243345d963f8247e430590c6f857b9e020d9ff0ec8f20a8b7e0cd4fff2cbd78"

curl -L -o openjdk11.tar.gz "$JDK_URL"

# Verify SHA256
ACTUAL_HASH=$(shasum -a 256 openjdk11.tar.gz | awk '{print $1}')
if [[ "$ACTUAL_HASH" != "$JDK_SHA256" ]]; then
  echo "❌ SHA256 hash mismatch!"
  echo "Expected: $JDK_SHA256"
  echo "Actual:   $ACTUAL_HASH"
  exit 1
else
  echo "✅ SHA256 hash verified: $ACTUAL_HASH"
fi

# Extract JDK
mkdir -p "$HOME/jdk"
tar -xzf openjdk11.tar.gz -C "$HOME/jdk"

export JAVA_HOME="$HOME/jdk/$JDK_BUILD/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

export ANDROID_HOME=${HOME}/.android-sdk
export ANDROID_SDK_ROOT=${ANDROID_HOME}

bash "${CI_WORKSPACE}/.buildscript/android/install_sdk.sh" "${ANDROID_HOME}"

export PATH=${ANDROID_HOME}/tools:${PATH}

