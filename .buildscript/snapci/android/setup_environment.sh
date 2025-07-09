#! /usr/bin/env bash

# Environment variables that were available on Jenkins for compatibility.

source "${CI_WORKSPACE}/.buildscript/snapci/setup_environment.sh"

export ANDROID_HOME=${HOME}/.android-sdk
export ANDROID_SDK_ROOT=${ANDROID_HOME}

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=/usr/lib/jvm/java-11-openjdk-amd64/bin:${JAVA_HOME}/bin:${ANDROID_HOME}/tools:${PATH}
export _JAVA_OPTIONS="-Xmx16384m"