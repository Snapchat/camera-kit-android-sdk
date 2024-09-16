#! /usr/bin/env bash

set -x
set -euo pipefail
set -o xtrace

readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
readonly repo_dir=$(dirname "$(dirname "$(dirname "${script_dir}")")")

sudo apt-get update
# required for pyenv https://github.com/pyenv/pyenv/wiki/Common-build-problems
sudo apt-get install -y make build-essential libssl-dev zlib1g-dev libreadline-dev libbz2-dev libsqlite3-dev wget curl llvm libncurses5-dev zip unzip
# required for gsutil https://cloud.google.com/storage/docs/gsutil_install#deb
sudo apt-get install -y apt-transport-https ca-certificates gnupg curl
# Import the Google Cloud public key
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor --yes -o /usr/share/keyrings/cloud.google.gpg
# Add the gcloud CLI distribution URI as a package source
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
# Update and install the gcloud CLI
sudo apt-get update && sudo apt-get install -y google-cloud-cli
# Install jq tool needed to publish artifacts to the AppCenter.
sudo apt-get install -y jq

# Install JDK
sudo apt install -y openjdk-11-jdk
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export _JAVA_OPTIONS="-Xmx16384m"

# Install Android
export ANDROID_HOME=${HOME}/.android-sdk
export ANDROID_SDK_ROOT=${ANDROID_HOME}
export PATH=${ANDROID_HOME}/tools:${PATH}
bash "$repo_dir/.buildscript/android/install_sdk.sh" "${ANDROID_HOME}"

# Verify Gradle is installed and works
pushd "$repo_dir/samples/android"
  ./gradlew clean
popd

