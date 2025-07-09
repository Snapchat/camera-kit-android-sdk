#! /usr/bin/env bash

set -x
set -euo pipefail

[[ -z "${BUILD_NUMBER:-}" ]] && echo "export BUILD_NUMBER=\"\$(date +%s)\"" >> ~/.bash_profile
[[ -z "${BUILD_URL:-}" ]] && echo "export BUILD_URL=\"https://ci-portal.mesh.sc-corp.net/cp/pipelines/p/\$CI_PIPELINE_ID\"" >> ~/.bash_profile

source ~/.bash_profile

if ! command -v pip &> /dev/null; then
    sudo apt-get update
    sudo apt install -y python3 python3-pip
fi

if ! command -v gh &> /dev/null; then
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
    sudo apt-get update
    sudo apt install gh

    echo $GITHUB_APIKEY | gh auth login --with-token --hostname github.sc-corp.net && gh auth status --hostname github.sc-corp.net

fi

pip install -r .buildscript/snapci/release_pipeline/requirements.txt