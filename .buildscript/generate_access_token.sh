#! /usr/bin/env bash

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# trace what gets executed
set -o xtrace

service_account=$( gcloud auth list --filter=status:ACTIVE --format="value(account)" )
audience=camera-kit-staging.snap
# Max expiry is 1 hour
readonly expiry_seconds=3600

while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
    -sa | --service-acount)
        service_account="$2"
        shift
        shift
        ;;
    -au | --audience)
        audience="$2"
        shift
        shift
        ;;
    *)
        echo "Unrecognised parameter: ${1}"
        exit
        ;;
    esac
done

echo "{\"iss\":\"${service_account}\",\"aud\":\"${audience}\",\"iat\":$(date +%s),\"exp\":$(expr $(date +%s) + $expiry_seconds)}" \
\ | gcloud beta iam service-accounts sign-jwt /dev/stdin /dev/stdout --iam-account ${service_account} 2> /dev/null
