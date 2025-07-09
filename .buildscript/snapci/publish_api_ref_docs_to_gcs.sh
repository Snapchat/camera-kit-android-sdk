#! /usr/bin/env bash

source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_android.sh"
source "${CI_WORKSPACE}/.buildscript/snapci/image/provision_mac_for_ios.sh"

echo "Installing grip to convert markdown to html, see: https://github.com/joeyespo/grip"
pip3 install --user grip
export PATH="/Users/devicelab/.local/bin:/Users/devicelab/Library/Python/3.9/bin:/Users/devicelab/Library/Python/3.8/bin:/Users/devicelab/Library/Python/3.7/bin:$PATH"
if ! command -v grip &> /dev/null
then
	echo "python grip was not installed correctly!"
    exit 1
fi

bash .buildscript/publish_api_ref_docs_to_gcs.sh --uri "${gcs_bucket_uri}"
