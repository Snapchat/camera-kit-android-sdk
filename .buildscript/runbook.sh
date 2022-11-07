#! /usr/bin/env bash

# CameraKit release process turned into an interactive runbook, 
# inspired by https://blog.danslimmon.com/2019/07/15/do-nothing-scripting-the-key-to-gradual-automation/

# abort on nonzero exitstatus
set -o errexit
# abort on unbound variable
set -o nounset
# don't hide errors within pipes
set -o pipefail
# # trace what gets executed
# set -o xtrace

readonly script_name=$(basename "${0}")
readonly script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

readonly camerakit_distribution_repo_root="${script_dir}/.."
readonly camerakit_distribution_repo_name="camera-kit-distribution"
readonly camerakit_distribution_repo_url="https://github.sc-corp.net/Snapchat/${camerakit_distribution_repo_name}"
readonly camerakit_distribution_repo_main_branch="master"
readonly camerakit_distribution_repo_main_build_url="https://snapengine-builder.sc-corp.net/jenkins/job/camerakit-distribution-build"
readonly camerakit_distribution_repo_main_build_artifacts_url="https://console.cloud.google.com/storage/browser/snapengine-builder-artifacts/camerakit-distribution-build"
readonly camerakit_distribution_repo_android_publish="https://snapengine-builder.sc-corp.net/jenkins/job/camerakit-distribution-android-publish"
readonly camerakit_distribution_repo_ios_publish="https://snapengine-builder.sc-corp.net/jenkins/job/camerakit-distribution-ios-publish"
readonly camerakit_distribution_appcenter_org_url="https://appcenter.ms/orgs/app-2q6u"
readonly camerakit_distribution_appcenter_android_sample="${camerakit_distribution_appcenter_org_url}/apps/CameraKit-Sample-Partner"
readonly camerakit_distribution_appcenter_ios_sample="${camerakit_distribution_appcenter_org_url}/apps/CameraKit-Sample-Partner-iOS"
readonly camerakit_distribution_repo_ios_build_reference_file="${camerakit_distribution_repo_root}/samples/ios/CameraKitSample/.build"
readonly camerakit_distribution_public_release_doc="https://docs.google.com/document/d/1SQf2HoTjUiMyuRre0SsJxN5wRLWYkV90FCLM1x2E2Jk"

readonly version_file="${camerakit_distribution_repo_root}/VERSION"
readonly changelog_file="${camerakit_distribution_repo_root}/CHANGELOG.md"

readonly android_sdk_build_url="https://snapengine-builder.sc-corp.net/jenkins/job/snap-sdk-android-publish"
readonly ios_sdk_build_url="https://snapengine-builder.sc-corp.net/jenkins/job/camera-kit-ios-sdk"
readonly camera_kit_internal_chat_name="lenses-kit"
readonly camera_kit_eng_guest_chat_name="camkit-eng-guest"
readonly camera_kit_internal_chat_url="https://snap.slack.com/archives/GQZ111ECQ"
readonly android_repo_url=https://github.sc-corp.net/Snapchat/android
readonly android_repo_sdk_version_file="snapchat/sdks/camerakit/core/ext.gradle"
readonly android_repo_main_branch=master
readonly ios_repo_url=https://github.sc-corp.net/Snapchat/phantom
readonly ios_repo_main_branch=master

readonly color_bold='\033[1m'
readonly color_none='\033[0m'
readonly color_blue='\033[0;34m'
readonly color_green='\033[0;32m'  

bold() {
    echo -e "${color_bold}${1}${color_none}"
}

blue () {
    echo -e "${color_blue}${1}${color_none}"
}

read_version_name() {
    echo $( cat "${version_file}" | tr -d " \t\n\r" )
}

prompt_next_step_with_input() {
    input=
    while [[ $input = "" ]]; do
        input=$( prompt_next_step "$@" "" )
    done
    echo $input
}

prompt_next_step() {
    read -p "${1}
    ${2}
    " input
    echo $input 
}

prompt_run_next_step() {
    prompt_next_step "Run:" "$( bold "${1}" )"
}

prompt_yes_or_no() {
    while true; do
        read -p "${1} 
    
    " yn
        case $yn in
            [Yy]* ) break;;
            [Nn]* ) echo "" && ${@:2} && echo "";;
            * ) echo "Please answer yes or no." && echo "";;
        esac
    done 
    echo ""
}

new_release() {
    echo "
The new release is planned as: 
    "

    option_patch="Patch release - fix or improvement (1.8.0 -> 1.8.1)"
    option_minor="Minor release - new features, APIs, fixes and improvements (1.8.0 -> 1.9.0)"
    option_major="Major release - new, breaking API changes, rebranding (1.8.0 -> 2.0.0)"

    options=("$option_patch" "$option_minor" "$option_major")

    select option in "${options[@]}"; do
        case $option in
            $option_patch)
                new_release_patch
                break
                ;;
            $option_minor)
                new_release_minor
                break
                ;;
            $option_major)
                new_release_major
                break
                ;;
            *) echo "Invalid option $REPLY";;
        esac
    done
}

new_release_patch() {
    # Collect and verify version information
    echo ""
    release_version_name=$( prompt_next_step_with_input "Enter the release version to patch:" )
    semver=( ${release_version_name//./ } )
    major="${semver[0]}"
    minor="${semver[1]}"
    patch="${semver[2]}"

    next_version_name="${major}.${minor}.$(($patch + 1))"
    release_branch_name="release/${major}.${minor}.x"
    patch_branch_name="patch/$next_version_name/${release_branch_name}"
    sdk_release_branch_name="camerakit/release/${major}.${minor}.x"

    echo ""
    prompt_yes_or_no "Patched version to be released is: $( bold $next_version_name ), looks good? (y/n)" explain_patch_version_and_exit

    # Check if SDK builds are ready and collect their build/version references
    prompt_yes_or_no "Are both Android and iOS SDKs that contain fixes for the $( bold $next_version_name ) already built? (y/n)" echo "Please prepare SDKs by bumping their version to ${next_version_name} in the respective Android and iOS repositories and schedule builds:
    Android - $( blue "${android_sdk_build_url}/build" ), iOS: $( blue "${ios_sdk_build_url}/build" ). NOTE: Android SDK must always use a pre-release version such as ${next_version_name}-rc1 before release is verified."

    echo "NOTE: to find version & commit of an Android SDK published through the $( blue $android_sdk_build_url ) job, open a build and scroll down its logs and then you should find a message containing \"Published Snap SDKs\" with an artifact version below, for example:
    \"com.snap.camerakit:camerakit-api:1.8.2-rc1\" where [1.8.2-rc1] is the version you need.
To find a commit hash, copy the value from a COMMIT_SHA environment variable that gets logged.
"

    android_sdk_version_name=$( prompt_next_step_with_input "Enter the Android SDK version name which contains the patch for the $next_version_name:" )
    echo ""
    android_sdk_commit=$( prompt_next_step_with_input "Enter the full Android SDK commit which contains the patch for the $( bold $next_version_name ):" )
    echo ""
    android_build_job_number=$( prompt_next_step_with_input "Enter the Android SDK number of a build job which contains the patch for the $( bold $next_version_name ):" )
    echo ""
    ios_sdk_commit=$( prompt_next_step_with_input "Enter the full iOS SDK commit of the patch for the $( bold $next_version_name ): " )
    echo ""
    ios_build_job_number=$( prompt_next_step_with_input "Enter the iOS SDK number of a build job which contains the patch for the $( bold $next_version_name ):" )
    echo ""

    # Prepare a PR to deliver SDKs to the release branch
    echo "In [$( bold $camerakit_distribution_repo_name )] repository,"
    prompt_run_next_step "git fetch origin ${release_branch_name} && git checkout ${release_branch_name}"
    prompt_run_next_step "git checkout -b ${patch_branch_name}"
    prompt_run_next_step "echo \"${next_version_name}\" > ${version_file} && git add ${version_file} && git commit -m \"[Build] Bump version to ${next_version_name}\""

    prompt_run_next_step ".buildscript/android/update.sh -v ${android_sdk_version_name} -r ${android_sdk_commit} -b ${android_build_job_number} --no-branch"
    prompt_run_next_step ".buildscript/ios/update.sh -r ${ios_sdk_commit} -b ${ios_build_job_number} --no-branch"

    prompt_next_step "Update the ${changelog_file}:" "Create a new section for the $( bold $next_version_name ) release adding items that describe bug fixes or features delivered by the updated Android/iOS SDKs"
    prompt_run_next_step "git add ${changelog_file} && git commit -m \"[Doc] Update CHANGELOG for ${next_version_name} release\" && git push"
    
    prompt_next_step "Create a PR from the [$patch_branch_name] branch by opening:" "$( blue "${camerakit_distribution_repo_url}/compare/${release_branch_name}...${patch_branch_name}?expand=1" )"
    prompt_next_step "Get approval to merge the above PR and then :cool: or :fire: if all CI checks are green" "Wait for the PR to merge"

    # Verify pre-release builds
    prompt_next_step "Open:" "$( blue $camerakit_distribution_repo_main_build_url ) 
    ...
    There should be a new build job started for the ${release_branch_name} branch. 
    WARNING: If there is no build job or it failed, do not proceed further, investigate, try to resolve the issue yourself or ask for help.
    NOTE: a failure may be caused by a transient issue, you can try \"Rebuild\" it with the same parameters."

    main_build_job_number=$( prompt_next_step_with_input "Enter the build job number:" )
    echo ""
    main_build_job_url="${camerakit_distribution_repo_main_build_url}/${main_build_job_number}"
    main_build_job_artifact_url="${camerakit_distribution_repo_main_build_artifacts_url}/${main_build_job_number}"

    prompt_next_step "Wait for the build to finish:" "$( blue $main_build_job_url )
    ...
    When build is done, CameraKit distribution zip archive will be uploaded to: 
    $( blue $main_build_job_artifact_url )"

    prompt_next_step "Download and inspect contents:" "$( blue $main_build_job_artifact_url )
    ...
    While inspecting contents, post the above link to the #${camera_kit_internal_chat_name} Slack channel: $( blue  $camera_kit_internal_chat_url ).
    Other engineers $( bold "must" ) inspect contents of the release candidate and confirm if it is good to go."

    prompt_next_step "Wait for the sample apps to build and publish:" "Android build $( blue $camerakit_distribution_repo_android_publish ) publishes to: $( blue $camerakit_distribution_appcenter_android_sample )
    iOS build $( blue $camerakit_distribution_repo_ios_publish ) publishes to: $( blue $camerakit_distribution_appcenter_ios_sample )"

    release_verify_ticket=$( prompt_next_step_with_input "Create or find a JIRA ticket with links of the sample app builds above for QA to verify $( bold ${next_version_name} ). Paste a link to the ticket: " )
    echo ""

    prompt_yes_or_no "Wait for the $( bold ${next_version_name} ) release candidate verification in ${release_verify_ticket} JIRA ticket. Verified? (y/n)" echo "
    If any issues are discovered through verification, please prepare Android/iOS SDK builds that contain fixes and then use the ./buildscript/android|ios/update.sh scripts to deliver them to the [${camerakit_distribution_repo_name}] repository's ${release_branch_name} branch.
    "

    # Prepare and deliver Android SDK final release build
    prompt_next_step "Update Android SDK version to $( bold "${next_version_name}" ) for the final release" "Edit the [versionName] section in $( blue "${android_repo_url}/edit/${sdk_release_branch_name}/snapchat/sdks/camerakit/core/ext.gradle#L47" ). Create a PR, get approval and :fire: it to merge."

    prompt_next_step "Trigger Android SDK build:" "$( blue "${android_sdk_build_url}/build" )
    The [branch] and [commit] parameters should be set to [${sdk_release_branch_name}] and [maven_group_id] to [com.snap.camerakit]"

    android_build_job_number=$( prompt_next_step_with_input "Enter the Android SDK build job number:" )
    echo ""
    android_sdk_commit=$( prompt_next_step_with_input "Enter the full Android SDK commit found in $( blue "${android_sdk_build_url}/${android_build_job_number}/console" ):" )

    prompt_next_step "Wait for the job to successfully finish:" "$( blue "${android_sdk_build_url}/${android_build_job_number}" )"

    echo "In [$( bold $camerakit_distribution_repo_name )] repository,"

    prompt_run_next_step "git checkout ${release_branch_name} && git pull origin ${release_branch_name}"

    prompt_run_next_step ".buildscript/android/update.sh -v ${next_version_name} -r ${android_sdk_commit} -b ${android_build_job_number} --create-pr"

    prompt_next_step "Open the PR created by running the above command. Get approval, :fire: and then wait for the PR to get merged" ""

    # Download final release build, tag and publish it
    prompt_next_step "Open:" "$( blue $camerakit_distribution_repo_main_build_url )
    ...
    There should be a new build job started for the PR that you just merged."

    main_build_job_number=$( prompt_next_step_with_input "Enter the build job number:" )
    main_build_job_url="${camerakit_distribution_repo_main_build_url}/${main_build_job_number}"
    main_build_job_artifact_url="${camerakit_distribution_repo_main_build_artifacts_url}/${main_build_job_number}"

    prompt_next_step "Wait for the build to finish:" "$( blue $main_build_job_url )
    ...
    When build is done, CameraKit distribution zip archive will be uploaded to:
    $( blue $main_build_job_artifact_url )"

    prompt_next_step "Download:" "$( blue $main_build_job_artifact_url )"
    release_artifact_file_name="camera-kit-distribution-${next_version_name}.zip"
    prompt_next_step "Rename the downloaded artifact to ${release_artifact_file_name}" ""
    prompt_next_step "Create a new release by opening $( blue "${camerakit_distribution_repo_url}/releases/new" ):" "[Tag version]: $next_version_name
    [Target]: $release_branch_name
    [Release title]: $next_version_name
    [Description]: Copy exact items from the $next_version_name section in the CHANGELOG
    [Binaries]: Attach the ${release_artifact_file_name}"

     # TODO: replace with steps encoded in this script
    prompt_next_step "Complete the $( bold $release_version_name ) release publishing steps outlined in $( blue "${camerakit_distribution_public_release_doc}" )" ""

    prompt_next_step "Notify CameraKit partner engineers in the #${camera_kit_eng_guest_chat_name} channel about it" ""

    echo "Congratulations, patch release $( bold $next_version_name ) is done!"
}

new_release_minor() {
    # Collect and verify version information
    echo "In [$( bold $camerakit_distribution_repo_name )] repository,"
    prompt_run_next_step "git checkout ${camerakit_distribution_repo_main_branch} && git pull origin ${camerakit_distribution_repo_main_branch}"

    release_version_name=$( read_version_name )
    semver=( ${release_version_name//./ } )
    major="${semver[0]}"
    minor="${semver[1]}"
    patch="${semver[2]}"
    
    prompt_yes_or_no "Version to be released is: $( bold $release_version_name ), looks good? (y/n)" explain_branch_and_exit

    next_version_name="${major}.$(($minor + 1)).0"
    release_branch_name="release/${major}.${minor}.x"
    sdk_release_branch_name="camerakit/release/${major}.${minor}.x"

    echo ""
    prompt_yes_or_no "The next development version after release will be: $( bold $next_version_name ), looks good? (y/n)" explain_version_and_exit

    # Branch promote and create release builds for the Android/iOS SDKs
    prompt_next_step "Create a release branch for $( bold $release_version_name ) in the Android repo ${android_repo_url}:" "$( bold "
    git checkout ${android_repo_main_branch} && git pull origin ${android_repo_main_branch} && git checkout -b ${sdk_release_branch_name} && git push" )"

    prompt_next_step "Create a PR to ${android_repo_url} to edit the version to $( bold "${release_version_name}-rc1" ) for pre-release testing" "Edit the [versionName] section in $( blue "${android_repo_url}/edit/${sdk_release_branch_name}/snapchat/sdks/camerakit/core/ext.gradle#L47" )
    Example PR: $( blue "${android_repo_url}/pull/229236" )"

    prompt_yes_or_no "Get approval to merge the above PR, merge/cool them and wait to complete. Completed? (y/n)" echo "Please wait for the build to complete and/or fix build issues, if any."

    prompt_next_step "Trigger Android SDK build:" "$( blue "${android_sdk_build_url}/build" )
    The [branch] and [commit] parameters should be set to [${sdk_release_branch_name}] and [maven_group_id] to [com.snap.camerakit]"

    android_build_job_number=$( prompt_next_step_with_input "Enter the Android SDK build job number:" )
    echo ""

    prompt_next_step "Create a release branch for $( bold $release_version_name ) in the iOS repo ${ios_repo_url}:" "$( bold "
    git checkout ${ios_repo_main_branch} && git pull origin ${ios_repo_main_branch} && git checkout -b ${sdk_release_branch_name} && git push" )"

    prompt_next_step "Trigger iOS SDK build:" "$( blue "${ios_sdk_build_url}/build" )
    The [branch] parameter should be set to [${sdk_release_branch_name}] while [commit] should be set to the head of the branch, run: $ git rev-parse HEAD", and [pull_number] should be set to [force]

    ios_build_job_number=$( prompt_next_step_with_input "Enter the iOS SDK build job number:" )
    echo ""
    ios_sdk_commit=$( prompt_next_step_with_input "Enter the full iOS SDK commit of the above build job: " )
    echo ""

    prompt_next_step "Wait for the jobs to successfully finish:" "Android: $( blue "${android_sdk_build_url}/${android_build_job_number}" )
    iOS: $( blue "${ios_sdk_build_url}/${ios_build_job_number}" )"

    prompt_next_step "Find Android SDK version and commit from the build:" "$( blue "${android_sdk_build_url}/${android_build_job_number}/console" )
    ...
    To find the published version, scroll down the logs, you should find a message containing \"Published Snap SDKs\" with an artifact version below, for example:
    \"com.snap.camerakit:camerakit-api:1.8.0+27279679.146\" where [1.8.0+27279679.146] is the version you need.
    To find the commit, open the full build logs and copy the value from a COMMIT_SHA environment variable that gets logged."

    android_sdk_version_name=$( prompt_next_step_with_input "Enter the Android SDK version found above:" )
    echo ""
    android_sdk_commit=$( prompt_next_step_with_input "Enter the full Android SDK commit found above:" )
    echo ""

    # Create a new minor release branch and integrate pre-release builds of Android/iOS SDKs
    echo "In [$( bold $camerakit_distribution_repo_name )] repository, create a new release branch:"
    prompt_run_next_step "git checkout -b ${release_branch_name}"

    prompt_run_next_step ".buildscript/android/update.sh -v ${android_sdk_version_name} -r ${android_sdk_commit} -b ${android_build_job_number} --no-branch"
    prompt_run_next_step ".buildscript/ios/update.sh -r ${ios_sdk_commit} -b ${ios_build_job_number} --no-branch"
    prompt_run_next_step "git push"

    # Verify pre-release builds
    prompt_next_step "Open:" "$( blue $camerakit_distribution_repo_main_build_url ) 
    ...
    There should be a new build job started for the commit that you just pushed. 
    WARNING: If there is no build job or it failed, do not proceed further, investigate, try to resolve the issue yourself or ask for help.
    NOTE: a failure may be caused by a transient issue, you can try \"Rebuild\" it with the same parameters."

    main_build_job_number=$( prompt_next_step_with_input "Enter the build job number:" )
    echo ""
    main_build_job_url="${camerakit_distribution_repo_main_build_url}/${main_build_job_number}"
    main_build_job_artifact_url="${camerakit_distribution_repo_main_build_artifacts_url}/${main_build_job_number}"

    prompt_next_step "Wait for the build to finish:" "$( blue $main_build_job_url )
    ...
    When build is done, CameraKit distribution zip archive will be uploaded to: 
    $( blue $main_build_job_artifact_url )"

    prompt_next_step "Download and inspect contents:" "$( blue $main_build_job_artifact_url )
    ...
    While inspecting contents, post the above link to the #${camera_kit_internal_chat_name} Slack channel: $( blue  $camera_kit_internal_chat_url ).
    Other engineers $( bold "must" ) inspect contents of the release candidate and confirm if it is good to go."

    prompt_next_step "Wait for the sample apps to build and publish:" "Android build $( blue $camerakit_distribution_repo_android_publish ) publishes to: $( blue $camerakit_distribution_appcenter_android_sample )
    iOS build $( blue $camerakit_distribution_repo_ios_publish ) publishes to: $( blue $camerakit_distribution_appcenter_ios_sample )"

    release_verify_ticket=$( prompt_next_step_with_input "Create or find a JIRA ticket with links of the sample app builds above for QA to verify $( bold $release_version_name ). Paste a link to the ticket: " )
    echo ""

    # Bump Android/iOS SDK versions for the next development iteration and prepare SDK builds
    prompt_next_step "Create a PR to ${android_repo_url} to bump version to $( bold $next_version_name )" "Edit the [versionName] section in $( blue "${android_repo_url}/edit/${android_repo_main_branch}/snapchat/sdks/camerakit/core/ext.gradle#L47" )
    Example PR: $( blue "${android_repo_url}/pull/197699" )"

    prompt_next_step "Create a PR to ${ios_repo_url} to bump version to $( bold $next_version_name )" "Edit the [VERSION] file in $( blue "${ios_repo_url}/edit/${ios_repo_main_branch}/SDKs/CameraKit/CameraKit/VERSION" )
    Example PR: $( blue "${ios_repo_url}/pull/203124" )"

    prompt_yes_or_no "Get approval to merge the above PRs, merge/cool them and wait to complete. Completed? (y/n)" echo "Please wait for the build to complete and/or fix build issues, if any."

    prompt_next_step "Trigger Android SDK build:" "$( blue "${android_sdk_build_url}/build" )
    The [branch] and [commit] parameters should be set to [${android_repo_main_branch}] and [maven_group_id] to [com.snap.camerakit]"

    android_build_job_number=$( prompt_next_step_with_input "Enter the Android SDK build job number:" )
    echo ""

    prompt_next_step "Open:" "$( blue $ios_sdk_build_url )
    ...
    There should be a new build job started for the commit of the merged iOS version bump PR from above.
    WARNING: If there is no build job or it failed, do not proceed further, investigate, try to resolve the issue yourself or ask for help.
    NOTE: a failure may be caused by a transient issue, you can try \"Rebuild\" via ${ios_sdk_build_url}/build with [${ios_repo_main_branch}] for [branch] parameter and the merged PR commit for [commit] parameter"
    
    ios_build_job_number=$( prompt_next_step_with_input "Enter the iOS SDK build job number:" )
    echo ""
    ios_sdk_commit=$( prompt_next_step_with_input "Enter the full iOS SDK commit of the above build job: " )
    echo ""

    prompt_next_step "Wait for the jobs to successfully finish:" "Android: $( blue "${android_sdk_build_url}/${android_build_job_number}" )
    iOS: $( blue "${ios_sdk_build_url}/${ios_build_job_number}" )"

    prompt_next_step "Find Android SDK version and commit from the build:" "$( blue "${android_sdk_build_url}/${android_build_job_number}/console" )"

    android_sdk_version_name=$( prompt_next_step_with_input "Enter the Android SDK version found above:" )
    echo ""
    android_sdk_commit=$( prompt_next_step_with_input "Enter the full Android SDK commit found above:" )
    echo ""

    # Bump distribution version for the next development iteration and deliver SDK builds
    echo "In [$( bold $camerakit_distribution_repo_name )] repository,"
    prompt_run_next_step "$( bold "git checkout ${camerakit_distribution_repo_main_branch} && git reset --hard && git pull origin ${camerakit_distribution_repo_main_branch}" )"
    sdk_update_branch="bump/${camerakit_distribution_repo_main_branch}-${next_version_name}"
    prompt_run_next_step "git checkout -b ${sdk_update_branch}"
    prompt_run_next_step "echo \"${next_version_name}\" > ${version_file} && git add ${version_file} && git commit -m \"[Build] Bump version to ${next_version_name}\""

    prompt_run_next_step ".buildscript/android/update.sh -v ${android_sdk_version_name} -r ${android_sdk_commit} -b ${android_build_job_number} --no-branch"
    prompt_run_next_step ".buildscript/ios/update.sh -r ${ios_sdk_commit} -b ${ios_build_job_number} --no-branch"

    prompt_run_next_step "git push"

    prompt_next_step "Create a PR for the [$sdk_update_branch] by opening:" "$( blue "${camerakit_distribution_repo_url}/compare/${sdk_update_branch}?expand=1" )"
    prompt_next_step "Get approval to merge the above PR and then :cool: if all CI checks are green" ""

    # Confirm release verification and deliver Android SDK final release build
    prompt_yes_or_no "Wait for the $( bold $release_version_name ) release candidate verification in ${release_verify_ticket} JIRA ticket. Verified? (y/n)" echo "
    If any issues are discovered through verification, please prepare Android/iOS SDK builds that contain fixes and then use the ./buildscript/android|ios/update.sh scripts to deliver them to the [${camerakit_distribution_repo_name}] repository's ${release_branch_name} branch.
    "
    prompt_next_step "Update Android SDK version to $( bold "${release_version_name}" ) for the final release" "Edit the [versionName] section in $( blue "${android_repo_url}/edit/${sdk_release_branch_name}/snapchat/sdks/camerakit/core/ext.gradle#L47" ). Create a PR, get approval and :fire: it to merge."
    prompt_next_step "Trigger Android SDK build:" "$( blue "${android_sdk_build_url}/build" )
    The [branch] and [commit] parameters should be set to [${sdk_release_branch_name}] and [maven_group_id] to [com.snap.camerakit]"

    android_build_job_number=$( prompt_next_step_with_input "Enter the Android SDK build job number:" )
    echo ""
    android_sdk_commit=$( prompt_next_step_with_input "Enter the full Android SDK commit found in $( blue "${android_sdk_build_url}/${android_build_job_number}/console" ):" )

    prompt_next_step "Wait for the job to successfully finish:" "$( blue "${android_sdk_build_url}/${android_build_job_number}" )"

    echo "In [$( bold $camerakit_distribution_repo_name )] repository,"
    prompt_run_next_step "git checkout ${release_branch_name} && git pull origin ${release_branch_name}"
    prompt_run_next_step ".buildscript/android/update.sh -v ${release_version_name} -r ${android_sdk_commit} -b ${android_build_job_number} --create-pr"
    prompt_next_step "Open the PR created by running the above command. Get approval, :fire: and then wait for the PR to get merged" ""

    # Prepare CHANGELOG for release
    prompt_run_next_step "git checkout ${release_branch_name} && git pull origin ${release_branch_name}"
    changelog_update_branch="changelog/${release_branch_name}-update"
    prompt_run_next_step "git checkout -b ${changelog_update_branch}"
    prompt_next_step "Update the ${changelog_file}:" "Create a new section for the $( bold $release_version_name ) and move all applicable items from the [Unreleased] section to it"
    prompt_run_next_step "git add ${changelog_file} && git commit -m \"[Doc] Update CHANGELOG for ${release_version_name} release\" && git push"
    prompt_next_step "Create a new PR by opening:" "$( blue "${camerakit_distribution_repo_url}/compare/${release_branch_name}...${changelog_update_branch}?expand=1" )"
    prompt_next_step "Get approval to merge the above PR, :cool: it and the wait for the PR to get merged" ""

    # Download final release build, tag and publish it
    prompt_next_step "Open:" "$( blue $camerakit_distribution_repo_main_build_url )
    ...
    There should be a new build job started for the PR that you just merged."

    main_build_job_number=$( prompt_next_step_with_input "Enter the build job number:" )
    main_build_job_url="${camerakit_distribution_repo_main_build_url}/${main_build_job_number}"
    main_build_job_artifact_url="${camerakit_distribution_repo_main_build_artifacts_url}/${main_build_job_number}"

    prompt_next_step "Wait for the build to finish:" "$( blue $main_build_job_url )
    ...
    When build is done, CameraKit distribution zip archive will be uploaded to:
    $( blue $main_build_job_artifact_url )"

    prompt_next_step "Download:" "$( blue $main_build_job_artifact_url )"
    release_artifact_file_name="camera-kit-distribution-${release_version_name}.zip"
    prompt_next_step "Rename the downloaded artifact to ${release_artifact_file_name}" ""
    prompt_next_step "Create a new release by opening $( blue "${camerakit_distribution_repo_url}/releases/new" ):" "[Tag version]: $release_version_name
    [Target]: $release_branch_name
    [Release title]: $release_version_name
    [Description]: Copy exact items from the $release_version_name section in the CHANGELOG
    [Binaries]: Attach the ${release_artifact_file_name}"

    # TODO: replace with steps encoded in this script
    prompt_next_step "Complete the $( bold $release_version_name ) release publishing steps outlined in $( blue "${camerakit_distribution_public_release_doc}" )" ""

    prompt_next_step "Notify CameraKit partner engineers in the #${camera_kit_eng_guest_chat_name} channel about it" ""

    echo "Congratulations, release $( bold $release_version_name ) is done!"
}

new_release_major() {
    echo "
Sorry, we currently don't have a formalized process to create a major release. Please come back later ;]"

    present_entry_options
}

deliver_fix_or_feature() {
    echo " 
Are you targeting a specific release or the main branch (${camerakit_distribution_repo_main_branch})?
"
    option_main="Main"
    option_release="Release"
    option_nothing="Not sure"

    options=("$option_main" "$option_release" "$option_nothing")

    select option in "${options[@]}"; do
        case $option in
            $option_main)
                break
                ;;
            $option_release)
                new_release_patch
                exit
                ;;
            $option_nothing)
                echo "
Please reach out to engineers in the #${camera_kit_internal_chat_name} Slack channel for more info on branches used in the ${camerakit_distribution_repo_url} repo."
                exit
                ;;
            *) echo "Invalid option $REPLY";;
        esac
    done

    echo "In [$( bold $camerakit_distribution_repo_name )] repository,"
    prompt_run_next_step "git checkout ${camerakit_distribution_repo_main_branch} && git pull origin ${camerakit_distribution_repo_main_branch}"
    version_name=$( read_version_name )

    echo "
Which platform are you delivering a feature/fix for?
"
    option_android="Android"
    option_ios="iOS"

    options=("$option_android" "$option_ios")

    select option in "${options[@]}"; do
        case $option in
            $option_android)
                deliver_fix_or_feature_android $version_name
                break
                ;;
            $option_ios)
                deliver_fix_or_feature_ios $version_name
                break
                ;;
            *) echo "Invalid option $REPLY";;
        esac
    done

    echo "Congratulations, your change was delivered to appear in the next $( bold $version_name ) release!"
}

deliver_fix_or_feature_android() {
    version_name=$1

    android_sdk_version_name=$( prompt_next_step_with_input "Enter the Android SDK version name which contains the changes for the $( bold $version_name ):" )
    echo ""
    android_sdk_commit=$( prompt_next_step_with_input "Enter the full Android SDK commit which contains the changes for the $( bold $version_name ):" )
    echo ""
    android_build_job_number=$( prompt_next_step_with_input "Enter the Android SDK number of a build job which contains the changes for the $( bold $version_name ):" )
    echo ""

    prompt_run_next_step ".buildscript/android/update.sh -v ${android_sdk_version_name} -r ${android_sdk_commit} -b ${android_build_job_number} --create-pr"
    prompt_next_step "Get approval to merge the newly created PR, :cool: it and the wait for the PR to get merged" ""
}

deliver_fix_or_feature_ios() {
    version_name=$1

    ios_sdk_commit=$( prompt_next_step_with_input "Enter the iOS SDK commit which contains the changes for the $( bold $version_name ):" )
    echo ""
    ios_build_job_number=$( prompt_next_step_with_input "Enter the iOS SDK number of a build job which contains the changes for the $( bold $version_name ):" )
    echo ""

    prompt_run_next_step ".buildscript/ios/update.sh -r ${ios_sdk_commit} -b ${ios_build_job_number} --create-pr"
    prompt_next_step "Get approval to merge the newly created PR, :cool: it and the wait for the PR to get merged" ""
}

explain_branch_and_exit() {
    echo "Please restart the runbook on a different branch or ask for help in #${camera_kit_internal_chat_name} Slack channel." && exit
}

explain_version_and_exit() {
    echo "Please restart the runbook after checking if the current branch is correct/clean and a valid version is defined in the ${version_file}. If in doubt, ask for help in #${camera_kit_internal_chat_name} Slack channel." && exit
}

explain_patch_version_and_exit() {
    echo "If version should be different ask for a clarification in the #${camera_kit_internal_chat_name} Slack channel." && exit
}

present_entry_options() {
    echo " 
What would you like to do today? 
"
    option_new_release="I would like to create a new release"
    option_deliver_fix_or_feature="I have a feature or a fix that needs to be delivered to this repo"
    option_nothing="Nevermind, all good"

    options=("$option_new_release" "$option_deliver_fix_or_feature" "$option_nothing")

    select option in "${options[@]}"; do
        case $option in
            $option_new_release)
                new_release
                break
                ;;
            $option_deliver_fix_or_feature)
                deliver_fix_or_feature
                break
                ;;
            $option_nothing)
                echo "
np, bye!"
                exit
                ;;
            *) echo "Invalid option $REPLY";;
        esac
    done
}

trap_cancel() {
    echo "

Interrupt detected, please press CTRL-C again to exit or other key to continue.
"
    sleep 2 || exit 1
}


main() {
    echo "
Hey there, thanks for opening the runbook of CameraKit 📷 processes (some manual some automatic). 
Make sure you open another terminal tab or window to run commands when prompted."
    echo "
Certain steps in this runbook require CLI access to Github. You can create a personal access token via https://github.sc-corp.net/settings/tokens and then"
    prompt_run_next_step "export GITHUB_APIKEY=<your-generated-api-token-value>"

    present_entry_options
}

# https://askubuntu.com/questions/441744/pressing-enter-produces-m-instead-of-a-newline
stty sane
PS3="
    "
trap trap_cancel SIGINT SIGTERM
main
