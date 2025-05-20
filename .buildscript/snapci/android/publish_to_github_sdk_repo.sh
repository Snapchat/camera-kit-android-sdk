#! /usr/bin/env bash

set -exo pipefail

source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_android.sh"
bash .buildscript/android/publish_to_github_sdk_repo.sh