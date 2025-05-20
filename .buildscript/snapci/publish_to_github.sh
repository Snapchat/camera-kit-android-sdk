#! /usr/bin/env bash

export GITHUB_REPO="Snapchat/camera-kit-reference"

source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_android.sh"

python3 -m venv .venv
source .venv/bin/activate

source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_ios.sh"
bash "${CI_WORKSPACE}/.buildscript/publish_to_github.sh"

