package com.snap.camerakit.jenkins.pipeline

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

import java.text.SimpleDateFormat

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import java.util.regex.Pattern

//region constants
@Field final String TEST_BRANCH_PREFIX = "camkit-pipeline-test/"

@Field final String URL_GH_CLI_DOWNLOAD =
        'https://github.com/cli/cli/releases/download/v2.20.2/gh_2.20.2_linux_386.tar.gz'
@Field final String URL_BASE_SNAP_JIRA_API = 'https://to-jira-dot-sc-ats.appspot.com/rest/api/2'
@Field final String URL_BASE_SNAP_SLACK_API = 'https://to-slack-dot-sc-ats.appspot.com/api'

@Field final String URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_PUBLIC = 'gs://snap-kit-reference-docs/CameraKit'
@Field final String URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_STAGING = 'gs://snap-kit-reference-docs-staging/CameraKit'
@Field final String URI_GCS_SNAPENGINE_MAVEN_PUBLISH_RELEASES = 'gcs://snapengine-maven-publish/releases'

@Field final String FILE_NAME_GH_CLI_ARCHIVE = URL_GH_CLI_DOWNLOAD.tokenize('/').last()
@Field final String FILE_NAME_CI_RESULT_PR_RESPONSE = 'pr_request_response.json'
@Field final String FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG = "CHANGELOG.md"
@Field final String FILE_NAME_STATE_JSON = "state.json"

@Field final String GCS_BUCKET_SNAPENGINE_BUILDER = 'snapengine-builder-artifacts'

@Field final String LCA_AUDIENCE_ATS = 'sc-ats.appspot.com'

@Field final String CREDENTIALS_ID_GCS = 'everybodysaydance-test'
@Field final String CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH = 'eb2750a7-56dc-4464-bb43-4109099a4623'
@Field final String CREDENTIALS_ID_SNAPENGINESC_GITHUB_TOKEN = '2e9af316-971e-4cf3-be13-f23e7afcdc79'

@Field final String HOST_SNAPENGINE_BUILDER = 'snapengine-builder.sc-corp.net'
@Field final String HOST_SNAP_GHE = 'github.sc-corp.net'
@Field final String HOST_SNAP_JIRA = 'jira.sc-corp.net'

@Field final String PATH_ANDROID_REPO = 'Snapchat/Android'
@Field final String PATH_COCOAPODS_SPECS_REPO = "raw.githubusercontent.com/CocoaPods/Specs/master/Specs"

@Field final String PATH_CAMERAKIT_REFERENCE_REPO_PUBLIC = "Snapchat/camera-kit-reference"
@Field final String PATH_CAMERAKIT_REFERENCE_REPO_TEST = "Snap-Kit/camera-kit-reference-test"
@Field final String PATH_CAMERAKIT_DISTRIBUTION_REPO = 'Snapchat/camera-kit-distribution'
@Field final String PATH_MAVEN_CENTRAL_REPO = "repo1.maven.org/maven2"
@Field final String PATH_PHANTOM_REPO = 'Snapchat/phantom'
@Field final String PATH_SNAP_DOCS_REPO = 'Snapchat/snap-docs'

@Field final String BRANCH_LEGACY_MAIN = "master"
@Field final String BRANCH_ANDROID_REPO_MAIN = "master"
@Field final String BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN = "master"
@Field final String BRANCH_PHANTOM_REPO_MAIN = "master"
@Field final String BRANCH_SNAP_DOCS_REPO_MAIN = 'main'

@Field final String KEY_CAMERAKIT_DISTRIBUTION_BUILD = 'SDK distribution build'
@Field final String KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID = 'SDK distribution Android sample app build'
@Field final String KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS = 'SDK distribution iOS sample app build'
@Field final String KEY_STASH_STATE = "state"

@Field final String JOB_SNAP_SDK_ANDROID_PUBLISH = 'snap-sdk-android-publish'
@Field final String JOB_CAMERAKIT_SDK_IOS_BUILD_JOB = 'camera-kit-ios-sdk'
@Field final String JOB_CAMERAKIT_SDK_IOS_COCOAPODS_PUBLISH_JOB = 'camera-kit-ios-sdk-cocoapods-publish'
@Field final String JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE = 'camerakit-android-version-update'
@Field final String JOB_CAMERAKIT_SDK_IOS_VERSION_UPDATE = 'camerakit-ios-version-update'
@Field final String JOB_CAMERAKIT_DISTRIBUTION_BUILD = 'camerakit-distribution-build'
@Field final String JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID = 'camerakit-distribution-android-publish'
@Field final String JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS = 'camerakit-distribution-ios-publish'
@Field final String JOB_CAMERAKIT_DISTRIBUTION_GITHUB_PUBLISH = 'camerakit-distribution-publish-github'
@Field final String JOB_CAMERAKIT_DISTRIBUTION_DOCS_API_REF_GCS_PUBLISH =
        'camerakit-distribution-publish-api-ref-docs-to-gcs'

@Field final String CHANNEL_SLACK_CAMKIT_MOBILE_OPS_TEST = '#camkit-mobile-ops-pipeline-test'
@Field final String CHANNEL_SLACK_CAMKIT_MOBILE_SDK_RELEASE_COORD = '#camkit-mobile-sdk-release-coordination'

@Field final String COMMENT_PR_COOL = ":cool:"
@Field final String COMMENT_PR_FIRE = ":fire:"

@Field final long STATUS_CHECK_SLEEP_SECONDS = 60L
@Field final long STATUS_CHECK_INTERVAL_MILLIS = 15_000L // max is 15s, can only be lower
@Field final int COMMAND_RETRY_MAX_COUNT = 10
//endregion

//region pipeline
// WARNING: the pipeline is compiled into one giant method which might exceed the code size limit,
// see: https://docs.cloudbees.com/docs/cloudbees-ci-kb/latest/troubleshooting-guides/method-code-too-large-error.
// If this happens, the pipeline steps need to be split into separate methods defined outside the pipeline DSL.
pipeline {
    agent {
        label 'unifiedimagetd'
    }

    options {
        timestamps()
        preserveStashes()
    }

    parameters {
        booleanParam(
                defaultValue: true,
                name: 'TEST_MODE',
                description: 'Activates a mode where the release pipeline can be tested end to end using branch ' +
                        'names, notifications channels etc. that do not interfere with regular CameraKit release ' +
                        'process'
        )
        text(
                defaultValue: '',
                name: 'PREDEFINED_STATE_JSON',
                description: 'Optional, contents of a build\'s state json file to resume where previous build ' +
                        'stopped/failed'
        )
    }

    stages {
        //region stage #0
        stage('Prepare Environment') {
            steps {
                script {
                    if (params.PREDEFINED_STATE_JSON != null && !params.PREDEFINED_STATE_JSON.isEmpty()) {
                        writeStateInternal(State.fromJson(params.PREDEFINED_STATE_JSON))
                    }
                    prepareTools()
                }
            }
        }
        //endregion

        //region stage #1
        stage('Determine Release Scope') {
            when {
                expression {
                    readState { State state ->
                        state.stage1.releaseScope == null ||
                                state.stage1.releaseVersion == null ||
                                state.stage1.releaseVerificationIssueKey == null ||
                                state.stage1.releaseCoordinationSlackChannel == null
                    }
                }
            }
            steps {
                script {
                    updateState { State state ->
                        def releaseScopeInput = input(
                                id: 'releaseScope',
                                message: 'What is the next release scope?',
                                parameters: [choice(
                                        name: 'Choice:',
                                        choices: "${ReleaseScope.MINOR}" +
                                                "\n${ReleaseScope.PATCH}"
                                )]
                        )

                        state.stage1.releaseScope = ReleaseScope.from(releaseScopeInput)
                        println("Selected next release scope: ${state.stage1.releaseScope}")

                        if (state.stage1.releaseScope == null) {
                            error "Failed to parse a release scope from the choice: $releaseScopeInput"
                        } else if (state.stage1.releaseScope == ReleaseScope.MAJOR) {
                            error "The '${state.stage1.releaseScope}' release scope is not currently supported"
                        } else if (state.stage1.releaseScope == ReleaseScope.MINOR ||
                                state.stage1.releaseScope == ReleaseScope.PATCH) {
                            git branch: addTestBranchPrefixIfNeeded(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN),
                                    credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH,
                                    url: "git@${HOST_SNAP_GHE}:${PATH_CAMERAKIT_DISTRIBUTION_REPO}.git"

                            def currentVersion = Version.from(readFile('VERSION').readLines().first().trim())
                            println "Current development version: ${currentVersion.toString()}"

                            state.stage1.releaseVersion = currentVersion

                            if (state.stage1.releaseScope == ReleaseScope.PATCH) {
                                def patchReleaseVersionInput = input(
                                        id: 'patchVersion',
                                        message: 'What is the existing release version to patch?',
                                        parameters: [string(
                                                name: 'version',
                                                defaultValue: currentVersion.dropMinor().toString()
                                        )]
                                )

                                def releaseVersionToPatch = Version.from(patchReleaseVersionInput)
                                if (releaseVersionToPatch.compareTo(currentVersion) >= 0) {
                                    error "The release version to patch cannot be equal or greater " +
                                            "than the current development version: " +
                                            currentVersion.toString()
                                }

                                def patchReleaseVersion = releaseVersionToPatch.bumpPatch()
                                println "Next patch release version: ${patchReleaseVersion.toString()}"

                                state.stage1.releaseVersion = patchReleaseVersion
                            }
                        }

                        updateBuildNameFor(state.stage1.releaseScope, state.stage1.releaseVersion)

                        // To speed up testing process we automatically create or reset test branches after determining
                        // the release scope and version. Note that this will mess up any currently running pipeline
                        // that relies on those branches so make sure to execute this only a single job at a time!
                        createOrResetTestBranchesIfNeeded(state.stage1.releaseScope, state.stage1.releaseVersion)

                        if (state.stage1.releaseVerificationIssueKey == null) {
                            def summary = "${params.TEST_MODE ? '[TEST] ' : ''}" +
                                    "SDK ${state.stage1.releaseVersion.toString()} sign off"
                            def description = "This is the main ticket for the Camera Kit SDK " +
                                    "${state.stage1.releaseVersion.toString()} release verification.\n" +
                                    "Initial release candidate builds are pending, this issue will be updated with " +
                                    "more details in a bit.\n\nh6. Generated in: ${env.BUILD_URL}"

                            def issue = createJiraIssue("CAMKIT", "Task", summary, description)
                            def issueKey = issue['key']
                            def issueUrl = jiraIssueUrlFrom(issueKey)

                            println "Created Jira issue: $issueUrl"

                            state.stage1.releaseVerificationIssueKey = issueKey
                        }

                        if (state.stage1.releaseCoordinationSlackChannel == null) {
                            def channelName = "${state.stage1.releaseVerificationIssueKey.toLowerCase()}-release-" +
                                    "${state.stage1.releaseVersion.toString().replace('.', '-')}"

                            def result = createSlackChannel(channelName, false)
                            def channelId = result['channel']['id']

                            state.stage1.releaseCoordinationSlackChannel = "#$channelName"

                            notifyOnSlack(
                                    params.TEST_MODE ?
                                            CHANNEL_SLACK_CAMKIT_MOBILE_OPS_TEST :
                                            CHANNEL_SLACK_CAMKIT_MOBILE_SDK_RELEASE_COORD,
                                    "[Pipeline] Starting ${state.stage1.releaseVersion.toString()} release, " +
                                            "ticket: ${jiraIssueUrlFrom(state.stage1.releaseVerificationIssueKey)}, " +
                                            "co-ordination channel: <#${channelId}>"
                            )
                            notifyOnSlack(
                                    state.stage1.releaseCoordinationSlackChannel,
                                    "[Pipeline] Running release flow in: ${env.BUILD_URL}. " +
                                            "Tracking all updates in the verification ticket: " +
                                            "${jiraIssueUrlFrom(state.stage1.releaseVerificationIssueKey)} "
                            )
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #2
        stage('Update SDK Version') {
            when {
                expression {
                    readState().stage2.developmentVersion == null
                }
            }
            steps {
                script {
                    updateState { State state ->
                        state.stage2.developmentVersion = state.stage1.releaseVersion.bumpMinor()

                        def releaseUpdateBranch = addTestBranchPrefixIfNeeded(BRANCH_LEGACY_MAIN)
                        def prComment = COMMENT_PR_COOL

                        if (state.stage1.releaseScope == ReleaseScope.PATCH) {
                            state.stage2.developmentVersion = state.stage1.releaseVersion
                            releaseUpdateBranch = cameraKitSdkReleaseBranchFor(state.stage1.releaseVersion)
                            prComment = COMMENT_PR_FIRE
                        }

                        parallel([
                                [
                                        name   : 'Version update on Android',
                                        job    : JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE,
                                        repo   : PATH_ANDROID_REPO,
                                        branch : releaseUpdateBranch,
                                        commit : 'HEAD',
                                        version: state.stage1.releaseScope == ReleaseScope.PATCH ?
                                                state.stage2.developmentVersion.withQualifier('-rc1') :
                                                state.stage2.developmentVersion
                                ],
                                [
                                        name   : 'Version update on iOS',
                                        job    : JOB_CAMERAKIT_SDK_IOS_VERSION_UPDATE,
                                        repo   : PATH_PHANTOM_REPO,
                                        branch : releaseUpdateBranch,
                                        commit : 'HEAD',
                                        version: state.stage2.developmentVersion
                                ]
                        ].collectEntries { stageParameters ->
                            [(stageParameters.name): {
                                // this is important, must be a closure to be deferred to parallel work!
                                stage(stageParameters.name) {
                                    script {
                                        updateCameraKitSdkVersionIfNeeded(
                                                stageParameters.repo,
                                                stageParameters.job,
                                                stageParameters.branch,
                                                stageParameters.commit,
                                                stageParameters.version,
                                                prComment,
                                                state.stage1.releaseCoordinationSlackChannel
                                        )
                                    }
                                }
                            }]
                        })
                    }
                }
            }
        }
        //endregion

        //region stage #3
        stage('SDK Builds') {
            parallel {
                stage('Android SDK RC Build') {
                    when {
                        expression {
                            readState { State state ->
                                state.stage3.releaseCandidateAndroidSdkBuild == null
                            }
                        }
                    }
                    steps {
                        script {
                            def state = readState()
                            publishCameraKitAndroidSdk(
                                    cameraKitSdkReleaseBranchFor(state.stage1.releaseVersion),
                                    'HEAD',
                                    true,
                                    state.stage1.releaseCoordinationSlackChannel
                            ) { SdkBuild sdkBuild ->
                                updateState { State newState ->
                                    newState.stage3.releaseCandidateAndroidSdkBuild = sdkBuild
                                }
                            }
                        }
                    }
                }
                stage('Android SDK Dev Build') {
                    when {
                        expression {
                            readState { State state ->
                                state.stage3.developmentAndroidSdkBuild == null &&
                                        // this is a condition for a patch release
                                        state.stage2.developmentVersion.compareTo(state.stage1.releaseVersion) != 0
                            }
                        }
                    }
                    steps {
                        script {
                            publishCameraKitAndroidSdk(
                                    addTestBranchPrefixIfNeeded(BRANCH_ANDROID_REPO_MAIN),
                                    'HEAD',
                                    true,
                                    readState().stage1.releaseCoordinationSlackChannel
                            ) { SdkBuild sdkBuild ->
                                updateState { State state ->
                                    state.stage3.developmentAndroidSdkBuild = sdkBuild
                                }
                            }
                        }
                    }
                }
                stage('iOS SDK RC Build') {
                    when {
                        expression {
                            readState { State state ->
                                state.stage3.releaseCandidateIosSdkBuild == null
                            }
                        }
                    }
                    steps {
                        script {
                            def state = readState()
                            buildCameraKitIosSdk(
                                    state.stage1.releaseVersion,
                                    cameraKitSdkReleaseBranchFor(state.stage1.releaseVersion),
                                    'HEAD',
                                    state.stage1.releaseCoordinationSlackChannel
                            ) { SdkBuild sdkBuild ->
                                updateState { State newState ->
                                    newState.stage3.releaseCandidateIosSdkBuild = sdkBuild
                                }
                            }

                        }
                    }
                }
                stage('iOS SDK Dev Build') {
                    when {
                        expression {
                            readState { State state ->
                                state.stage3.developmentIosSdkBuild == null &&
                                        // this is a condition for a patch release
                                        state.stage2.developmentVersion.compareTo(state.stage1.releaseVersion) != 0
                            }
                        }
                    }
                    steps {
                        script {
                            def state = readState()
                            buildCameraKitIosSdk(
                                    state.stage2.developmentVersion,
                                    addTestBranchPrefixIfNeeded(BRANCH_PHANTOM_REPO_MAIN),
                                    'HEAD',
                                    state.stage1.releaseCoordinationSlackChannel
                            ) { SdkBuild sdkBuild ->
                                updateState { State newState ->
                                    newState.stage3.developmentIosSdkBuild = sdkBuild
                                }
                            }
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #4
        stage('Update SDK Distribution Version') {
            when {
                expression {
                    readState().stage4.releaseCandidateBinaryBuilds.isEmpty()
                }
            }
            steps {
                script {
                    updateState { State state ->
                        if (state.stage4.releaseCandidateBinaryBuildsCommitSha == null) {
                            def nextReleaseBranch = cameraKitSdkDistributionReleaseBranchFor(state.stage1.releaseVersion)
                            def jobs = [[
                                                baseBranch        : state.stage1.releaseScope == ReleaseScope.MINOR
                                                        ? addTestBranchPrefixIfNeeded(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN)
                                                        : nextReleaseBranch,
                                                newBranch         : state.stage1.releaseScope == ReleaseScope.MINOR
                                                        ? nextReleaseBranch
                                                        : null,
                                                newVersion        : state.stage1.releaseVersion,
                                                newAndroidSdkBuild: state.stage3.releaseCandidateAndroidSdkBuild,
                                                newIosSdkBuild    : state.stage3.releaseCandidateIosSdkBuild,
                                                newPrComment      : COMMENT_PR_FIRE
                                        ]]
                            if (state.stage1.releaseScope == ReleaseScope.MINOR) {
                                jobs.add([
                                        baseBranch        :
                                                addTestBranchPrefixIfNeeded(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN),
                                        newBranch         : null,
                                        newVersion        : state.stage2.developmentVersion,
                                        newAndroidSdkBuild: state.stage3.developmentAndroidSdkBuild,
                                        newIosSdkBuild    : state.stage3.developmentIosSdkBuild,
                                        newPrComment      : COMMENT_PR_COOL
                                ])
                            }

                            // NOTE: we need to create update PRs in serial due to the fact that we are working
                            // in the same workspace, git repo checkout is shared.
                            def prs = jobs.collect { parameters ->
                                def pr = updateCameraKitSdkDistributionWithNewSdkBuilds(
                                        parameters.baseBranch,
                                        parameters.newBranch,
                                        parameters.newVersion,
                                        parameters.newAndroidSdkBuild,
                                        parameters.newIosSdkBuild
                                )
                                pr['comment'] = parameters.newPrComment
                                pr
                            }

                            parallel(prs.collectEntries { pr ->
                                [(pr.title): {
                                    stage(pr.title) {
                                        script {
                                            notifyOnSlack(
                                                    state.stage1.releaseCoordinationSlackChannel,
                                                    "${pr.title}: ${pr.htmlUrl}"
                                            )
                                            commentOnPrWhenApprovedAndWaitToClose(pr.number, pr.repo, pr.comment)
                                        }
                                    }
                                }]
                            })

                            state.stage4.releaseCandidateBinaryBuildsCommitSha = getHeadCommitSha(
                                    PATH_CAMERAKIT_DISTRIBUTION_REPO,
                                    cameraKitSdkDistributionReleaseBranchFor(state.stage1.releaseVersion)
                            )
                        }
                    }

                    updateState { State state ->
                        buildCameraKitSdkDistributionRelease(
                                state.stage1.releaseVersion,
                                state.stage4.releaseCandidateBinaryBuildsCommitSha,
                                state.stage4.releaseCandidateBinaryBuilds,
                                state.stage1.releaseCoordinationSlackChannel
                        )
                    }

                    readState { State state ->
                        def message = createCameraKitSdkDistributionReleaseCandidateMessage(
                                state.stage1.releaseVersion,
                                state.stage4.releaseCandidateBinaryBuilds,
                                env.BUILD_URL
                        )
                        createJiraIssueComment(state.stage1.releaseVerificationIssueKey, message)
                    }
                }
            }
        }
        //endregion

        //region stage #5
        stage('Release Verification Cycle') {
            when {
                expression {
                    !readState().stage5.releaseVerificationComplete
                }
            }
            parallel {
                stage("Release Verification Status") {
                    steps {
                        script {
                            waitUntil(initialRecurrencePeriod: STATUS_CHECK_INTERVAL_MILLIS, quiet: true) {
                                try {
                                    State state = readState()
                                    def releaseVerificationIssueKey = state.stage1.releaseVerificationIssueKey
                                    def issue = lookUpJiraIssue(releaseVerificationIssueKey, 'status')
                                    def statusName = issue['fields']['status']['name']

                                    println "Issue [$releaseVerificationIssueKey] status: $statusName"

                                    if (statusName == 'Complete' || statusName == 'Done') {
                                        notifyOnSlack(
                                                state.stage1.releaseCoordinationSlackChannel,
                                                "[Pipeline] Input required: ${env.BUILD_URL}input"
                                        )
                                        input("Release candidate for ${state.stage1.releaseVersion.toString()} " +
                                                "appears to be " +
                                                "verified in ${jiraIssueUrlFrom(releaseVerificationIssueKey)}, " +
                                                "continue with the release?"
                                        )
                                        createJiraIssueComment(releaseVerificationIssueKey,
                                                "Release candidate for ${state.stage1.releaseVersion.toString()} " +
                                                        "appears to " +
                                                        "be verified, proceeding on to the final release steps in: " +
                                                        "${env.BUILD_URL}"
                                        )
                                        true
                                    } else {
                                        sleep STATUS_CHECK_SLEEP_SECONDS
                                        false
                                    }
                                } catch (error) {
                                    throwIfInterrupted(error)
                                    false
                                }
                            }
                            updateState { State state ->
                                // Signal complete
                                state.stage5.releaseVerificationComplete = true
                            }
                        }
                    }
                }
                stage('On-demand SDK RC Builds') {
                    steps {
                        script {
                            waitUntil(initialRecurrencePeriod: STATUS_CHECK_INTERVAL_MILLIS, quiet: true) {
                                State state = readState()
                                if (state.stage5.releaseVerificationComplete) {
                                    true
                                } else {
                                    String sdkReleaseBranch = cameraKitSdkReleaseBranchFor(state.stage1.releaseVersion)
                                    SdkBuild updatedReleaseCandidateAndroidSdkBuild = null
                                    SdkBuild updatedReleaseCandidateIosSdkBuild = null
                                    parallel(
                                            'On-demand Android SDK RC Build': {
                                                stage('On-demand Android SDK RC Build') {
                                                    script {
                                                        def headCommitSha =
                                                                getHeadCommitSha(PATH_ANDROID_REPO, sdkReleaseBranch)
                                                        def releaseCandidateAndroidSdkBuild =
                                                                state.stage5.releaseCandidateAndroidSdkBuild
                                                                        ?: state.stage3.releaseCandidateAndroidSdkBuild

                                                        def buildCommitSha = releaseCandidateAndroidSdkBuild.commit

                                                        println "HEAD commit: $headCommitSha, " +
                                                                "build commit: $buildCommitSha"

                                                        if (headCommitSha != buildCommitSha) {
                                                            println "It appears that $PATH_ANDROID_REPO repository " +
                                                                    "$sdkReleaseBranch branch has new commits, " +
                                                                    "preparing new build for release verification"

                                                            Version newReleaseCandidateVersion =
                                                                    releaseCandidateAndroidSdkBuild
                                                                            .version.bumpReleaseCandidate()

                                                            println "New Android SDK release candidate version: " +
                                                                    newReleaseCandidateVersion.toString()

                                                            updateCameraKitSdkVersionIfNeeded(
                                                                    PATH_ANDROID_REPO,
                                                                    JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE,
                                                                    sdkReleaseBranch,
                                                                    'HEAD',
                                                                    newReleaseCandidateVersion,
                                                                    COMMENT_PR_FIRE,
                                                                    state.stage1.releaseCoordinationSlackChannel
                                                            )

                                                            publishCameraKitAndroidSdk(
                                                                    sdkReleaseBranch,
                                                                    'HEAD',
                                                                    true,
                                                                    state.stage1.releaseCoordinationSlackChannel
                                                            ) { SdkBuild sdkBuild ->
                                                                updatedReleaseCandidateAndroidSdkBuild = sdkBuild
                                                                updateState { State newState ->
                                                                    newState.stage5.releaseCandidateAndroidSdkBuild =
                                                                            sdkBuild
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            'On-demand iOS SDK RC Build': {
                                                stage('On-demand iOS SDK RC Build') {
                                                    script {
                                                        def headCommitSha =
                                                                getHeadCommitSha(PATH_PHANTOM_REPO, sdkReleaseBranch)
                                                        def releaseCandidateIosSdkBuild =
                                                                state.stage5.releaseCandidateIosSdkBuild
                                                                        ?: state.stage3.releaseCandidateIosSdkBuild
                                                        def buildCommitSha = releaseCandidateIosSdkBuild.commit

                                                        println "HEAD commit: $headCommitSha, " +
                                                                "build commit: $buildCommitSha"

                                                        if (headCommitSha != buildCommitSha) {
                                                            println "It appears that $PATH_PHANTOM_REPO repository " +
                                                                    "$sdkReleaseBranch branch has new commits, " +
                                                                    "preparing new build for release verification"

                                                            buildCameraKitIosSdk(
                                                                    state.stage1.releaseVersion,
                                                                    sdkReleaseBranch,
                                                                    'HEAD',
                                                                    state.stage1.releaseCoordinationSlackChannel
                                                            ) { SdkBuild sdkBuild ->
                                                                updatedReleaseCandidateIosSdkBuild = sdkBuild
                                                                updateState { State newState ->
                                                                    newState.stage5.releaseCandidateIosSdkBuild =
                                                                            sdkBuild
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    )

                                    if (!readState().stage5.releaseVerificationComplete) {
                                        if (updatedReleaseCandidateAndroidSdkBuild != null
                                                || updatedReleaseCandidateIosSdkBuild != null) {
                                            def sdkDistributionReleaseBranch =
                                                    cameraKitSdkDistributionReleaseBranchFor(state.stage1.releaseVersion)

                                            def pr = updateCameraKitSdkDistributionWithNewSdkBuilds(
                                                    sdkDistributionReleaseBranch,
                                                    null,
                                                    state.stage1.releaseVersion,
                                                    updatedReleaseCandidateAndroidSdkBuild,
                                                    updatedReleaseCandidateIosSdkBuild
                                            )

                                            notifyOnSlack(
                                                    readState().stage1.releaseCoordinationSlackChannel,
                                                    "${pr.title}: ${pr.htmlUrl}"
                                            )
                                            commentOnPrWhenApprovedAndWaitToClose(pr.number, pr.repo, COMMENT_PR_FIRE)
                                        }
                                    }

                                    sleep STATUS_CHECK_SLEEP_SECONDS
                                    false
                                }
                            }
                        }
                    }
                }
                stage('On-demand SDK Distribution RC Build') {
                    steps {
                        script {
                            waitUntil(initialRecurrencePeriod: STATUS_CHECK_INTERVAL_MILLIS, quiet: true) {
                                State state = readState()
                                if (state.stage5.releaseVerificationComplete) {
                                    true
                                } else {
                                    def nextReleaseBranch =
                                            cameraKitSdkDistributionReleaseBranchFor(state.stage1.releaseVersion)
                                    def headCommitSha = getHeadCommitSha(
                                            PATH_CAMERAKIT_DISTRIBUTION_REPO,
                                            nextReleaseBranch
                                    )
                                    def releaseCandidateBinaryBuilds =
                                            state.stage5.releaseCandidateBinaryBuilds.isEmpty() ?
                                                    state.stage4.releaseCandidateBinaryBuilds :
                                                    state.stage5.releaseCandidateBinaryBuilds

                                    def cameraKitDistributionBuild =
                                            releaseCandidateBinaryBuilds[KEY_CAMERAKIT_DISTRIBUTION_BUILD]
                                    if (cameraKitDistributionBuild == null) {
                                        error("Missing Camera Kit distribution build in RC builds map: " +
                                                "${releaseCandidateBinaryBuilds.toString()}"
                                        )
                                    }
                                    def buildCommitSha = cameraKitDistributionBuild.commit

                                    println "HEAD commit: $headCommitSha, build commit: $buildCommitSha"

                                    if (headCommitSha != buildCommitSha) {
                                        println "It appears that $PATH_CAMERAKIT_DISTRIBUTION_REPO repository " +
                                                "$nextReleaseBranch branch has new commits, " +
                                                "preparing new builds for release verification"

                                        // NOTE: it would be great if there was a way to cancel jobs started below if
                                        // `releaseVerificationComplete == true`, however Jenkins pipeline does not
                                        // provide an easy way to do it as jobs started via the 'build' step block
                                        // until complete when `wait: true` but `wait: false` does not provide a way
                                        // to track scheduled build.
                                        buildCameraKitSdkDistributionRelease(
                                                state.stage1.releaseVersion,
                                                headCommitSha,
                                                releaseCandidateBinaryBuilds,
                                                state.stage1.releaseCoordinationSlackChannel
                                        )
                                        updateState { State newState ->
                                            newState.stage5.releaseCandidateBinaryBuilds = releaseCandidateBinaryBuilds
                                        }

                                        // Builds above might take a while to complete but ticket could be verified
                                        // already, we can exit early:
                                        if (readState().stage5.releaseVerificationComplete) {
                                            true
                                        } else {
                                            def message = createCameraKitSdkDistributionReleaseCandidateMessage(
                                                    readState().stage1.releaseVersion,
                                                    readState().stage5.releaseCandidateBinaryBuilds,
                                                    env.BUILD_URL
                                            )
                                            createJiraIssueComment(
                                                    readState().stage1.releaseVerificationIssueKey, message
                                            )
                                            false
                                        }
                                    } else {
                                        sleep STATUS_CHECK_SLEEP_SECONDS
                                        false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #6
        stage('SDK Release Builds') {
            when {
                expression {
                    readState { State state ->
                        state.stage5.releaseVerificationComplete &&
                                (state.stage6.releaseAndroidSdkBuild == null ||
                                        state.stage6.releaseIosSdkBuild == null)
                    }
                }
            }
            steps {
                script {
                    State state = readState()
                    String sdkReleaseBranch = cameraKitSdkReleaseBranchFor(state.stage1.releaseVersion)
                    SdkBuild releaseAndroidSdkBuild = state.stage6.releaseAndroidSdkBuild

                    if (releaseAndroidSdkBuild == null) {
                        updateCameraKitSdkVersionIfNeeded(
                                PATH_ANDROID_REPO,
                                JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE,
                                sdkReleaseBranch,
                                'HEAD',
                                state.stage1.releaseVersion,
                                COMMENT_PR_FIRE,
                                state.stage1.releaseCoordinationSlackChannel
                        )

                        publishCameraKitAndroidSdk(
                                sdkReleaseBranch, 'HEAD', true, state.stage1.releaseCoordinationSlackChannel
                        ) { SdkBuild sdkBuild ->
                            updateState { State newState ->
                                newState.stage6.releaseAndroidSdkBuild = sdkBuild
                            }

                        }

                        releaseAndroidSdkBuild = readState().stage6.releaseAndroidSdkBuild
                        String sdkDistributionReleaseBranch = cameraKitSdkDistributionReleaseBranchFor(
                                state.stage1.releaseVersion
                        )
                        def pr = updateCameraKitSdkDistributionWithNewSdkBuilds(
                                sdkDistributionReleaseBranch,
                                null,
                                state.stage1.releaseVersion,
                                releaseAndroidSdkBuild,
                                null
                        )

                        notifyOnSlack(state.stage1.releaseCoordinationSlackChannel, "${pr.title}: ${pr.htmlUrl}")
                        commentOnPrWhenApprovedAndWaitToClose(pr.number, pr.repo, COMMENT_PR_FIRE)
                    }

                    SdkBuild releaseIosSdkBuild = readState().stage6.releaseIosSdkBuild
                    // NOTE: iOS SDK does not need final builds so we can just use the latest RC
                    if (releaseIosSdkBuild == null) {
                        updateState { State newState ->
                            def releaseCandidateIosSdkBuild = newState.stage5.releaseCandidateIosSdkBuild
                                    ?: newState.stage3.releaseCandidateIosSdkBuild
                            if (releaseCandidateIosSdkBuild == null) {
                                error "Expected the [${newState.stage1.releaseVersion.toString()}]" +
                                        " iOS SDK release candidate build to not be null!"
                            } else {
                                newState.stage6.releaseIosSdkBuild = releaseCandidateIosSdkBuild
                            }
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #7
        stage('Update SDK Distribution CHANGELOG') {
            when {
                expression {
                    readState { State state ->
                        state.stage5.releaseVerificationComplete &&
                                state.stage6.releaseAndroidSdkBuild != null &&
                                state.stage6.releaseIosSdkBuild != null &&
                                state.stage8.releaseBinaryBuilds.isEmpty()
                    }
                }
            }
            steps {
                script {
                    readState { State state ->
                        updateCameraKitSdkDistributionChangelogForRelease(
                                state.stage1.releaseVersion, state.stage1.releaseCoordinationSlackChannel
                        )
                    }
                }
            }
        }
        //endregion

        //region stage #8
        stage('Create SDK Distribution Release') {
            when {
                expression {
                    readState().stage8.releaseGithubUrl == null
                }
            }
            steps {
                script {
                    State state = readState()
                    if (state.stage8.releaseBinaryBuilds.isEmpty()) {
                        buildCameraKitSdkDistributionRelease(
                                state.stage1.releaseVersion, 'HEAD',
                                state.stage8.releaseBinaryBuilds,
                                state.stage1.releaseCoordinationSlackChannel
                        )
                        updateState { State newState ->
                            newState.stage8.releaseBinaryBuilds = state.stage8.releaseBinaryBuilds
                        }
                    }

                    createCameraKitSdkDistributionRelease(
                            state.stage1.releaseVersion,
                            state.stage6.releaseAndroidSdkBuild,
                            state.stage6.releaseIosSdkBuild,
                            state.stage8.releaseBinaryBuilds,
                            state.stage1.releaseCoordinationSlackChannel
                    ) { releaseGithubUrl ->
                        updateState { State newState ->
                            createJiraIssueComment(
                                    newState.stage1.releaseVerificationIssueKey,
                                    "${newState.stage1.releaseVersion.toString()} release created, " +
                                            "details in: $releaseGithubUrl"
                            )
                            newState.stage8.releaseGithubUrl = releaseGithubUrl
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #9
        stage('Publish SDKs') {
            when {
                expression {
                    readState { State state ->
                        !params.TEST_MODE && state.stage8.releaseGithubUrl != null &&
                                (!state.stage9.androidSdkPublishedToMavenCentral ||
                                        !state.stage9.iosSdkPublishedToCocoapods)
                    }
                }
            }
            parallel {
                stage('Publish Android SDK to Maven Central') {
                    when {
                        expression {
                            State state = readState()
                            !isAvailable(cameraKitAndroidSdkMavenCentralUrlFor(state.stage1.releaseVersion)) &&
                                    state.stage6.releaseAndroidSdkBuild != null
                        }
                    }
                    steps {
                        script {
                            State state = readState()
                            publishCameraKitAndroidSdk(
                                    state.stage6.releaseAndroidSdkBuild.branch,
                                    state.stage6.releaseAndroidSdkBuild.commit,
                                    false,
                                    state.stage1.releaseCoordinationSlackChannel
                            ) {
                                notifyOnSlack(
                                        state.stage1.releaseCoordinationSlackChannel,
                                        "[Pipeline] Camera Kit Android SDK ${state.stage1.releaseVersion.toString()} " +
                                                "was published to the Sonatype Staging repository, please verify and " +
                                                "release it by signing in to: " +
                                                "https://oss.sonatype.org/#stagingRepositories"
                                )
                            }
                            waitUntilAvailable(cameraKitAndroidSdkMavenCentralUrlFor(state.stage1.releaseVersion))
                            updateState { State newState ->
                                newState.stage9.androidSdkPublishedToMavenCentral = true
                            }
                        }
                    }
                }
                stage('Publish iOS SDK to Cocoapods') {
                    when {
                        expression {
                            State state = readState()
                            !isAvailable(camerakitIosSdkCocoapodsSpecsUrlFor(state.stage1.releaseVersion)) &&
                                    state.stage6.releaseIosSdkBuild != null
                        }
                    }
                    steps {
                        script {
                            State state = readState()
                            publishCameraKitIosSdkToCocoapods(
                                    state.stage6.releaseIosSdkBuild,
                                    cameraKitSdkDistributionReleaseBranchFor(state.stage1.releaseVersion),
                                    false,
                                    state.stage1.releaseCoordinationSlackChannel
                            )
                            waitUntilAvailable(camerakitIosSdkCocoapodsSpecsUrlFor(state.stage1.releaseVersion))
                            updateState { State newState ->
                                newState.stage9.iosSdkPublishedToCocoapods = true
                            }
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #10
        stage('Sync SDK Public Resources') {
            parallel {
                stage('Sync SDK Reference to Github') {
                    when {
                        expression {
                            readState { State state ->
                                !state.stage10.sdkReferenceSyncedToPublicGithub &&
                                        state.stage8.releaseGithubUrl != null &&
                                        state.stage9.androidSdkPublishedToMavenCentral &&
                                        state.stage9.iosSdkPublishedToCocoapods
                            }
                        }
                    }
                    steps {
                        script {
                            State state = readState()
                            syncCameraKitSdkReferenceToPublicGithub(
                                    state.stage1.releaseVersion,
                                    params.TEST_MODE ?
                                            PATH_CAMERAKIT_REFERENCE_REPO_TEST :
                                            PATH_CAMERAKIT_REFERENCE_REPO_PUBLIC,
                                    params.TEST_MODE ?
                                            URI_GCS_SNAPENGINE_MAVEN_PUBLISH_RELEASES :
                                            null,
                                    state.stage1.releaseCoordinationSlackChannel
                            )
                            updateState { State newState ->
                                newState.stage10.sdkReferenceSyncedToPublicGithub = true
                            }
                        }
                    }
                }
                stage('Sync SDK API Docs to SnapDocs') {
                    when {
                        expression {
                            readState { State state ->
                                !state.stage10.sdkApiReferenceSyncedToSnapDocs &&
                                        state.stage8.releaseGithubUrl != null
                            }
                        }
                    }
                    steps {
                        script {
                            State state = readState()
                            syncCameraKitSdkApiReferenceToSnapDocs(
                                    state.stage1.releaseVersion,
                                    params.TEST_MODE ? URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_STAGING :
                                            URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_PUBLIC,
                                    state.stage1.releaseCoordinationSlackChannel
                            )
                            updateState { State newState ->
                                newState.stage10.sdkApiReferenceSyncedToSnapDocs = true
                            }
                        }
                    }
                }
            }
        }
        //endregion

        //region stage #11
        stage('Announce Release') {
            when {
                expression {
                    readState { State state ->
                        state.stage8.releaseGithubUrl != null
                    }
                }
            }
            steps {
                script {
                    readState { State state ->
                        notifyOnSlack(
                                state.stage1.releaseCoordinationSlackChannel,
                                "[Pipeline] CameraKit SDK ${state.stage1.releaseVersion.toString()} " +
                                        "release is complete, " +
                                        "details in: ${state.stage8.releaseGithubUrl}"
                        )
                    }
                }
            }
        }
        //endregion
    }
}
//endregion

//region common
def prepareTools() {
    waitUntil {
        try {
            sh "wget $URL_GH_CLI_DOWNLOAD -P /tmp"
            sh "mkdir -p /tmp/gh && tar -xvf /tmp/$FILE_NAME_GH_CLI_ARCHIVE -C /tmp/gh --strip-components 1"
            sh 'sudo mv /tmp/gh/bin/gh /usr/bin/gh && sudo chmod +x /usr/bin/gh'

            withCredentials(
                    [string(credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_TOKEN, variable: 'TOKEN')]
            ) {
                sh 'echo $TOKEN | ' +
                        "gh auth login --with-token --hostname $HOST_SNAP_GHE && gh auth status"
            }

            true
        } catch (error) {
            promptOrThrowIfInterrupted("Preparing environment failed due to: $error, retry?", error, null)
            false
        }
    }
}

def updateCameraKitSdkVersionIfNeeded(
        String repo,
        String job,
        String branch,
        String commit,
        Version nextVersion,
        String prComment,
        String slackChannel
) {
    def jobPath = null
    waitUntil {
        try {
            def jobResult = build job: job,
                    parameters: [
                            string(name: 'branch', value: branch),
                            string(name: 'commit', value: commit),
                            string(name: 'next_version', value: nextVersion.toString()),
                            string(name: 'branch_prefix', value: addTestBranchPrefixIfNeeded("camerakit"))
                    ],
                    wait: true
            jobPath = "$job/${jobResult.number}"

            try {
                step([
                        $class        : 'DownloadStep',
                        credentialsId : CREDENTIALS_ID_GCS,
                        bucketUri     :
                                "gs://${GCS_BUCKET_SNAPENGINE_BUILDER}/${jobPath}/$FILE_NAME_CI_RESULT_PR_RESPONSE",
                        localDirectory: '.'
                ])
            } catch (error) {
                println "Downloading $FILE_NAME_CI_RESULT_PR_RESPONSE failed due to: $error. " +
                        "It is possible that the $job exited early indicating no version update was necessary"
            }

            true
        } catch (error) {
            promptOrThrowIfInterrupted("$job failed due to ${error}, retry?", error, slackChannel)
            false
        }
    }

    def prStatusJsonFilePath = "${jobPath}/$FILE_NAME_CI_RESULT_PR_RESPONSE"
    if (fileExists(prStatusJsonFilePath)) {
        def prStatusJson = parseJsonTextAsMap(readFile(prStatusJsonFilePath))

        def prNumber = prStatusJson['number']
        def prTitle = prStatusJson['title']
        def prHtmlUrl = prStatusJson['html_url']

        println("Created PR: $prHtmlUrl")

        retry(COMMAND_RETRY_MAX_COUNT) {
            sh "gh pr ready $prNumber --repo ${repo}"
        }

        notifyOnSlack(slackChannel, "$prTitle: $prHtmlUrl")
        commentOnPrWhenApprovedAndWaitToClose(prNumber, repo, prComment)
    }
}

def publishCameraKitAndroidSdk(String branch, String commit, boolean internal, String slackChannel, Closure callback) {
    def job = JOB_SNAP_SDK_ANDROID_PUBLISH
    def jobResult = null
    def jobPath = null

    waitUntil {
        try {
            jobResult = build job: job,
                    parameters: [
                            string(name: 'branch', value: branch),
                            string(name: 'commit', value: commit),
                            string(name: 'maven_repository',
                                    value: internal ? 'maven_snap_internal' : 'maven_sonatype_staging'),
                            string(name: 'maven_group_id', value: 'com.snap.camerakit')
                    ],
                    wait: true
            jobPath = "${job}/${jobResult.number}"

            step([
                    $class        : 'DownloadStep',
                    credentialsId : CREDENTIALS_ID_GCS,
                    bucketUri     : "gs://${GCS_BUCKET_SNAPENGINE_BUILDER}/${jobPath}/publications.txt",
                    localDirectory: '.'
            ])

            def firstPublication = readFile("${jobPath}/publications.txt")
                    .readLines()
                    .first()
            def version = Version.from(firstPublication.tokenize(':').last())
            def buildCommit = jobResult.buildVariables['GIT_COMMIT']
            def buildNumber = jobResult.number
            def sdkBuild = new SdkBuild(version, branch, buildCommit, buildNumber as long, job, HOST_SNAPENGINE_BUILDER)

            println("Built CameraKit Android SDK: ${sdkBuild.toString()}")

            callback(sdkBuild)

            true
        } catch (error) {
            promptOrThrowIfInterrupted("$job failed due to ${error}, retry?", error, slackChannel)
            false
        }
    }
}

def buildCameraKitIosSdk(Version version, String branch, String commit, String slackChannel, Closure callback) {
    def job = JOB_CAMERAKIT_SDK_IOS_BUILD_JOB
    def jobResult = null
    def jobPath = null

    waitUntil {
        try {
            jobResult = build job: job,
                    parameters: [
                            string(name: 'branch', value: branch),
                            string(name: 'commit', value: commit)
                    ],
                    wait: true
            jobPath = "${job}/${jobResult.number}"
            true
        } catch (error) {
            promptOrThrowIfInterrupted("$job failed due to ${error}, retry?", error, slackChannel)
            false
        }
    }

    def buildCommit = jobResult.buildVariables['GIT_COMMIT']
    def buildNumber = jobResult.number
    def sdkBuild = new SdkBuild(version, branch, buildCommit, buildNumber as long, job, HOST_SNAPENGINE_BUILDER)

    println("Built CameraKit iOS SDK build: ${sdkBuild.toString()}")

    callback(sdkBuild)
}

def publishCameraKitIosSdkToCocoapods(SdkBuild sdkBuild, String distributionBranch, boolean dryRun, String slackChannel) {
    def job = JOB_CAMERAKIT_SDK_IOS_COCOAPODS_PUBLISH_JOB
    def jobResult = null
    def jobPath = null

    waitUntil {
        try {
            jobResult = build job: job,
                    parameters: [
                            string(name: 'branch', value: sdkBuild.branch),
                            string(name: 'commit', value: sdkBuild.commit),
                            string(name: 'camkit_build', value: sdkBuild.buildNumber.toString()),
                            string(name: 'camkit_commit', value: sdkBuild.commit),
                            string(name: 'camkit_version', value: sdkBuild.version.toString()),
                            string(name: 'distribution_branch', value: distributionBranch),
                            booleanParam(name: 'dryrun', value: dryRun)
                    ],
                    wait: true
            jobPath = "${job}/${jobResult.number}"
            true
        } catch (error) {
            promptOrThrowIfInterrupted("$job failed due to ${error}, retry?", error, slackChannel)
            false
        }
    }
}

def buildCameraKitSdkDistributionRelease(
        Version releaseVersion,
        String commit,
        Map<String, BinaryBuild> buildsMapToUpdate,
        String slackChannel
) {
    def releaseBranch = cameraKitSdkDistributionReleaseBranchFor(releaseVersion)

    def getHtmlUrlForAppCenterBuild = { jobPath ->
        step([
                $class        : 'DownloadStep',
                credentialsId : CREDENTIALS_ID_GCS,
                bucketUri     : "gs://${GCS_BUCKET_SNAPENGINE_BUILDER}/${jobPath}/app_center_release_info.json",
                localDirectory: '.'
        ])

        def filePath = "${jobPath}/app_center_release_info.json"
        if (fileExists(filePath)) {
            def fileContent = readFile(filePath)
            def appCenterReleaseInfoJson = parseJsonTextAsMap(fileContent)
            def downloadUrl = appCenterReleaseInfoJson['download_url']
            downloadUrl
        } else {
            null
        }
    }

    parallel([
            [
                    name          : KEY_CAMERAKIT_DISTRIBUTION_BUILD,
                    job           : JOB_CAMERAKIT_DISTRIBUTION_BUILD,
                    getHtmlUrl    : { jobPath ->
                        "https://console.cloud.google.com/storage/browser/_details/" +
                                "${GCS_BUCKET_SNAPENGINE_BUILDER}/$jobPath/camerakit-distribution.zip"
                    },
                    getDownloadUri: { jobPath ->
                        "gs://${GCS_BUCKET_SNAPENGINE_BUILDER}/$jobPath/camerakit-distribution.zip"
                    }
            ],
            [
                    name          : KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID,
                    job           : JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID,
                    getHtmlUrl    : getHtmlUrlForAppCenterBuild,
                    getDownloadUri: { null }
            ],
            [
                    name          : KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS,
                    job           : JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS,
                    getHtmlUrl    : getHtmlUrlForAppCenterBuild,
                    getDownloadUri: { null }
            ]
    ].collectEntries { parameters ->
        [(parameters.name): {
            stage(parameters.name) {
                script {
                    def jobResult = null
                    def jobPath = null

                    waitUntil {
                        try {
                            jobResult = build job: parameters.job,
                                    parameters: [
                                            string(name: 'branch', value: releaseBranch),
                                            string(name: 'commit', value: commit),
                                            // Jobs started on non main/release branch must have a pull request number
                                            // associated with them so we just use 1 that passes job script condition
                                            // as a workaround when running in test mode:
                                            string(name: 'pull_number', value: params.TEST_MODE ? "1" : "N/A")
                                    ]
                            wait: true
                            jobPath = "${parameters.job}/${jobResult.number}"
                            true
                        } catch (error) {
                            promptOrThrowIfInterrupted(
                                    "${parameters.job} failed due to ${error}, retry?", error, slackChannel
                            )
                            false
                        }
                    }

                    def htmlUrl = parameters.getHtmlUrl(jobPath)
                    def downloadUri = parameters.getDownloadUri(jobPath)
                    def buildCommit = jobResult.buildVariables['GIT_COMMIT']
                    def buildNumber = jobResult.number

                    def binaryBuild = new BinaryBuild(
                            releaseVersion,
                            releaseBranch,
                            buildCommit,
                            buildNumber as long,
                            parameters.job,
                            HOST_SNAPENGINE_BUILDER,
                            htmlUrl,
                            downloadUri
                    )

                    println "Got build: ${binaryBuild.toString()}"

                    buildsMapToUpdate.put(parameters.name, binaryBuild)
                }
            }
        }]
    })
}

@NonCPS
static String createCameraKitSdkDistributionReleaseCandidateMessage(
        Version releaseVersion,
        Map<String, BinaryBuild> buildsMap,
        String buildJobUrl
) {
    return "Release candidate builds for ${releaseVersion.toString()} " +
            "are ready for testing:\n" +
            buildsMap.collect { String name, BinaryBuild binaryBuild ->
                "h3. $name:" +
                        "\nVersion ${binaryBuild.version.toString()} (${binaryBuild.buildNumber}): " +
                        "${binaryBuild.htmlUrl} built by ${binaryBuild.getBuildUrl()}\n"

            }.join("\n") + "\nh6. Generated in: $buildJobUrl"
}

Map updateCameraKitSdkDistributionWithNewSdkBuilds(
        String baseBranch,
        String newBranch,
        Version newVersion,
        SdkBuild newAndroidSdkBuild,
        SdkBuild newIosSdkBuild
) {
    git branch: baseBranch,
            credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH,
            url: "git@${HOST_SNAP_GHE}:${PATH_CAMERAKIT_DISTRIBUTION_REPO}.git"
    if (newBranch != null) {
        sh "git checkout -B ${newBranch} && git push -f origin ${newBranch}"
    }

    def updateBranch = "update/${newVersion.toString()}/${System.currentTimeMillis()}"
    sh "git checkout -B $updateBranch"

    sh "echo \"${newVersion.toString()}\" > VERSION && " +
            "git add VERSION && " +
            "git commit -m \"[Build] Bump version to ${newVersion.toString()}\" " +
            "|| echo \"No changes to commit\""

    withCredentials(
            [string(credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_TOKEN, variable: 'GITHUB_APIKEY')]
    ) {
        if (newAndroidSdkBuild != null) {
            sh ".buildscript/android/update.sh " +
                    "-v ${newAndroidSdkBuild.version.toString()} " +
                    "-r ${newAndroidSdkBuild.commit} " +
                    "-b ${newAndroidSdkBuild.buildNumber} " +
                    "--no-branch"
        }
        if (newIosSdkBuild != null) {
            sh ".buildscript/ios/update.sh " +
                    "-r ${newIosSdkBuild.commit} " +
                    "-b ${newIosSdkBuild.buildNumber} " +
                    "--no-branch"
        }
    }

    sh "git push origin $updateBranch"

    def repo = "$HOST_SNAP_GHE/$PATH_CAMERAKIT_DISTRIBUTION_REPO"
    def prTitle = "[Build] Update SDKs for the ${newVersion.toString()} version"

    String prResult = null
    retry(COMMAND_RETRY_MAX_COUNT) {
        prResult = sh(
                returnStdout: true,
                script: "gh pr create " +
                        "--title \"$prTitle\" " +
                        "--body \"This PR updates the SDKs to the latest builds targeting the " +
                        "version: ${newVersion.toString()}. " +
                        "\n\nPlease refer to the individual commit messages to see a list of included " +
                        "changes in each SDK.\" " +
                        "--base ${newBranch ?: baseBranch} " +
                        "--head $updateBranch " +
                        "--repo $repo"
        ).trim()
    }
    def prNumber = prResult.tokenize('/').last()
    def prHtmlUrl = "https://$repo/pull/$prNumber"

    return [
            repo   : repo,
            number : prNumber,
            title  : prTitle,
            htmlUrl: prHtmlUrl
    ]
}

def updateCameraKitSdkDistributionChangelogForRelease(Version releaseVersion, String slackChannel) {
    def releaseBranch = cameraKitSdkDistributionReleaseBranchFor(releaseVersion)
    waitUntil {
        try {
            git branch: releaseBranch,
                    credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH,
                    url: "git@${HOST_SNAP_GHE}:${PATH_CAMERAKIT_DISTRIBUTION_REPO}.git"

            def updateBranch = "update/${releaseVersion.toString()}/changelog/${System.currentTimeMillis()}"
            sh "git checkout -B $updateBranch"

            String changelogContent = readFile(FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG)

            def dateFormat = new SimpleDateFormat("yyyy-MM-dd")
            String unreleasedSectionHeader = '<a name="unreleased"></a>\n## [Unreleased]'
            changelogContent = changelogContent.replace(
                    unreleasedSectionHeader,
                    unreleasedSectionHeader +
                            "\n\n<a name=\"${releaseVersion.toString()}\"></a>" +
                            "\n## [${releaseVersion.toString()}] - ${dateFormat.format(new Date())}"
            )

            writeFile(file: FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG, text: changelogContent)

            String updateTitle = "[Doc] Update CHANGELOG for ${releaseVersion.toString()} release"

            sh "git add ${FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG} && " +
                    "git commit -m \"$updateTitle\" && " +
                    "git push origin $updateBranch"

            def repo = "$HOST_SNAP_GHE/$PATH_CAMERAKIT_DISTRIBUTION_REPO"
            def prTitle = updateTitle
            def prResult = sh(
                    returnStdout: true,
                    script: "gh pr create " +
                            "--title \"$prTitle\" " +
                            "--body \"This PR updates the CHANGELOG targeting the " +
                            "${releaseVersion.toString()} release. Please double check if all " +
                            "items look good and add or remove any that might be needed for this " +
                            "release.\" " +
                            "--base $releaseBranch " +
                            "--head $updateBranch " +
                            "--repo $repo"
            ).trim()
            def prNumber = prResult.tokenize('/').last()
            def prHtmlUrl = "https://$repo/pull/$prNumber"

            notifyOnSlack(slackChannel, "$prTitle: $prHtmlUrl")
            // Cooling this PR as we want the CHANGELOG update to be cherry-picked downstream,
            // conflicts will need to be resolved manually.
            commentOnPrWhenApprovedAndWaitToClose(prNumber, repo, COMMENT_PR_COOL)
            true
        } catch (error) {
            promptOrThrowIfInterrupted(
                    "Failure while updating CHANGELOG for the ${releaseVersion.toString()} " +
                            "release due to: $error, retry?",
                    error,
                    slackChannel
            )
            false
        }
    }
}

def createCameraKitSdkDistributionRelease(
        Version releaseVersion,
        SdkBuild androidSdkBuild,
        SdkBuild iosSdkBuild,
        Map<String, BinaryBuild> binaryBuilds,
        String slackChannel,
        Closure callback
) {
    waitUntil {
        try {
            BinaryBuild sdkDistributionBuild = binaryBuilds[KEY_CAMERAKIT_DISTRIBUTION_BUILD]
            BinaryBuild androidSampleBuild = binaryBuilds[KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID]
            BinaryBuild iosSampleBuild = binaryBuilds[KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS]

            if (sdkDistributionBuild == null) {
                error "Expected the [${releaseVersion.toString()}]" +
                        " SDK distribution release build to not be null!"
            } else {
                String sdkDistributionZipFileName =
                        "camerakit-distribution-${releaseVersion.toString()}.zip"

                sh "gsutil cp '${sdkDistributionBuild.downloadUri}' $sdkDistributionZipFileName"

                String releaseTitle = releaseVersion.toString()
                String releaseTagName = releaseTitle
                String releaseTargetBranch = cameraKitSdkDistributionReleaseBranchFor(releaseVersion)

                git branch: releaseTargetBranch,
                        credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH,
                        url: "git@${HOST_SNAP_GHE}:${PATH_CAMERAKIT_DISTRIBUTION_REPO}.git"

                String changelogContent = readFile(FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG)
                String releaseTitleLink = "<a name=\"${releaseTitle}\"></a>"
                String changelogReleaseContent = null
                int releaseTitleLinkIndex = changelogContent.indexOf(releaseTitleLink)
                if (releaseTitleLinkIndex != -1) {
                    changelogReleaseContent = changelogContent
                            .substring(releaseTitleLinkIndex)
                            .replace(releaseTitleLink, "")
                            .stripLeading()
                            .split("<a") // up until previous release title link
                            .first()
                }
                if (changelogReleaseContent != null) {
                    List<String> releaseContentLines = changelogReleaseContent.readLines()
                    changelogReleaseContent = releaseContentLines
                            .takeRight(releaseContentLines.size() - 2)
                            .join("\n")
                } else {
                    changelogReleaseContent = "No notable changes recorded."
                }

                String releaseNotes = "## *Public*\n" + changelogReleaseContent

                releaseNotes += "\n\n## *Internal*"

                releaseNotes += "\n### SDKs"
                releaseNotes += appSizeInfoContentFor('Android', androidSdkBuild)
                releaseNotes += appSizeInfoContentFor('iOS', iosSdkBuild)

                releaseNotes += "\n### Samples"
                releaseNotes += sampleAppInfoContentFor('Android', androidSampleBuild)
                releaseNotes += sampleAppInfoContentFor('iOS', iosSampleBuild)

                String repo = "$HOST_SNAP_GHE/$PATH_CAMERAKIT_DISTRIBUTION_REPO"

                String releaseGithubUrl = sh(
                        returnStdout: true,
                        script: "gh release create $releaseTagName " +
                                "--target ${releaseTargetBranch} " +
                                "--title ${releaseTitle} " +
                                "--notes '${releaseNotes.replaceAll("'", "\\\\'")}' " +
                                "--repo $repo " +
                                (params.TEST_MODE ? "--draft " : " ") +
                                "./$sdkDistributionZipFileName"
                ).trim()

                println("Created ${releaseTitle} release: $releaseGithubUrl")

                callback(releaseGithubUrl)
            }
            true
        } catch (error) {
            promptOrThrowIfInterrupted(
                    "Failure while creating SDK distribution ${releaseVersion.toString()} " +
                            "release due to: $error, retry?",
                    error,
                    slackChannel
            )
            false
        }
    }
}

def appSizeInfoContentFor(String platform, SdkBuild sdkBuild) {
    String content = ""

    content += "\n- **${platform}**:"
    content += "\n\t- Build:"
    content += "\n\t\t- Branch: ${sdkBuild.branch}"
    content += "\n\t\t- Commit: ${sdkBuild.commit}"
    content += "\n\t\t- Job: ${sdkBuild.buildUrl}"

    String shortCommitSha = sdkBuild.commit.take(10)
    Map sizeInfo = queryCameraKitSdkSize(
            platform.toLowerCase(), sdkBuild.branch, shortCommitSha
    )

    if (sizeInfo != null) {
        def installSizeBytes = sizeInfo['install_size'] as long ?: 0L
        def downloadSizeBytes = sizeInfo['download_size'] as long ?: 0L
        def appSizeReportUrl = "https://looker.sc-corp.net/dashboards/3515" +
                "?App+Name=camerakit" +
                "&App+Platform=${platform.toLowerCase()}" +
                "&Variant=release" +
                "&Commit+Sha=${shortCommitSha}"
        content += "\n\t- Size:"
        content += "\n\t\t- Install: ${installSizeBytes} bytes"
        content += "\n\t\t- Downalod: ${downloadSizeBytes} bytes"
        content += "\n\t\t- Report: $appSizeReportUrl"
    }
    return content
}

static def sampleAppInfoContentFor(String platform, BinaryBuild binaryBuild) {
    String content = ""

    content += "\n- **${platform}**:"
    content += "\n\t- Download: ${binaryBuild.htmlUrl}"

    return content
}

def syncCameraKitSdkApiReferenceToSnapDocs(Version version, String gcsBucketUri, String slackChannel) {
    def job = JOB_CAMERAKIT_DISTRIBUTION_DOCS_API_REF_GCS_PUBLISH
    def jobResult = null
    def jobPath = null
    waitUntil {
        try {
            jobResult = build job: job,
                    parameters: [
                            string(
                                    name: 'branch',
                                    value: cameraKitSdkDistributionReleaseBranchFor(version)
                            ),
                            string(
                                    name: 'GCS_BUCKET_URI',
                                    value: gcsBucketUri
                            )
                    ],
                    wait: true
            jobPath = "${job}/${jobResult.number}"

            def baseBranch = BRANCH_SNAP_DOCS_REPO_MAIN

            git branch: baseBranch,
                    credentialsId: CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH,
                    url: "git@${HOST_SNAP_GHE}:${PATH_SNAP_DOCS_REPO}.git"

            def updateBranch =
                    "camerakit/update-api-ref" +
                            "/${version.toString()}" +
                            "/${System.currentTimeMillis()}"
            sh "git checkout -B $updateBranch"

            def cameraKitApiReferencePath = "reference/CameraKit"
            ['api-sidebar.js', 'docs/api/home.mdx'].each { fileToUpdate ->
                ['android', 'ios'].each { platform ->
                    sh "sed -i'.bak' " +
                            "\"s#$cameraKitApiReferencePath/$platform/" +
                            "[[:digit:]]\\+\\.[[:digit:]]\\+\\.[[:digit:]]/" +
                            "#$cameraKitApiReferencePath/$platform/" +
                            "${version.toString()}/#g\" " +
                            "\"${fileToUpdate}\""
                }
                sh "rm -rf ${fileToUpdate}.bak"
                sh "git add $fileToUpdate"
            }

            def commitMessage = "[CameraKit] " +
                    "Update API reference doc links to ${version.toString()}"
            def exitCode = sh(returnStatus: true, script: "git commit -m \"$commitMessage\"")
            if (exitCode != 0) {
                println "Attempting to commit resulted in exit code: $exitCode, " +
                        "most likely due to nothing to commit"
            } else {
                sh "git push origin $updateBranch"

                def repo = "$HOST_SNAP_GHE/$PATH_SNAP_DOCS_REPO"
                def prTitle = commitMessage
                def prResult = sh(
                        returnStdout: true,
                        script: "gh pr create " +
                                "--title \"$prTitle\" " +
                                "--body \"This PR updates the CameraKit API reference doc links to track the " +
                                "${version.toString()} version resources.\n" +
                                "API reference docs synced in: " +
                                "https://$HOST_SNAPENGINE_BUILDER/jenkins/job/$jobPath\" " +
                                "--base $baseBranch " +
                                "--head $updateBranch " +
                                "--repo $repo"
                ).trim()
                def prNumber = prResult.tokenize('/').last()
                def prHtmlUrl = "https://$repo/pull/$prNumber"

                notifyOnSlack(slackChannel, "$prTitle: $prHtmlUrl")
            }
            true
        } catch (error) {
            promptOrThrowIfInterrupted("$job failed due to ${error}, retry?", error, slackChannel)
            false
        }
    }
}

def syncCameraKitSdkReferenceToPublicGithub(
        Version version,
        String repo,
        String preReleaseMavenRepositoryUri,
        String slackChannel
) {
    def job = JOB_CAMERAKIT_DISTRIBUTION_GITHUB_PUBLISH
    def jobResult = null
    def jobPath = null
    waitUntil {
        try {
            jobResult = build job: job,
                    parameters: [
                            string(
                                    name: 'branch',
                                    value: cameraKitSdkDistributionReleaseBranchFor(version)
                            ),
                            string(
                                    name: 'GITHUB_REPO',
                                    value: repo
                            ),
                            string(
                                    name: 'PRE_RELEASE_MAVEN_REPOSITORY',
                                    value: preReleaseMavenRepositoryUri ?: ''
                            )
                    ],
                    wait: true
            jobPath = "${job}/${jobResult.number}"

            def prStatusJsonFilePath = "${jobPath}/$FILE_NAME_CI_RESULT_PR_RESPONSE"

            step([
                    $class        : 'DownloadStep',
                    credentialsId : CREDENTIALS_ID_GCS,
                    bucketUri     : "gs://${GCS_BUCKET_SNAPENGINE_BUILDER}" +
                            "/$prStatusJsonFilePath",
                    localDirectory: '.'
            ])

            if (fileExists(prStatusJsonFilePath)) {
                def prStatusJson = parseJsonTextAsMap(readFile(prStatusJsonFilePath))

                def prTitle = prStatusJson['title']
                def prHtmlUrl = prStatusJson['html_url']

                println("Created PR: $prHtmlUrl")

                notifyOnSlack(slackChannel, "$prTitle: $prHtmlUrl")
            }
            true
        } catch (error) {
            promptOrThrowIfInterrupted("$job failed due to ${error}, retry?", error, slackChannel)
            false
        }
    }
}

Map queryCameraKitSdkSize(String platform, String branch, String commit) {
    String bqResult = sh(returnStdout: true, script: "bq query --nouse_legacy_sql --format=prettyjson " +
            "--project_id=everybodysaydance " +
            "'SELECT app_size.download_size, app_size.install_size FROM `ci-metrics.app_size.app_size` as app_size " +
            "WHERE app_size.app_name=\"CameraKit\" " +
            "and app_size.platform=\"${platform}\" " +
            "and app_size.build_info.commit_sha=\"$commit\" " +
            "and app_size.build_info.commit_branch=\"$branch\"'"
    )
    try {
        List<Map> rows = parseJsonTextAsList(bqResult)
        if (!rows.isEmpty()) {
            return rows.first()
        } else {
            return null
        }
    } catch (error) {
        println("Failed to parse BQ result as a List: $error")
        return null
    }
}

Map createJiraIssue(String project, String type, String summary, String description) {
    String lcaToken = createLcaTokenFor(LCA_AUDIENCE_ATS)

    String data = """{
    "fields": {
        "project": { "key": "$project" },
        "summary": "$summary",
        "description": "${escapeNewLines(description)}",
        "issuetype": {
            "name": "$type"
        }
    }
}
    """

    Map jsonResult = null

    retry(COMMAND_RETRY_MAX_COUNT) {
        String result = sh(
                returnStdout: true,
                script: "curl " +
                        "--request POST '$URL_BASE_SNAP_JIRA_API/issue' " +
                        "--header \"SC-LCA-1: $lcaToken\" " +
                        "--header \"Accept: application/json\" " +
                        "--header \"Content-type: application/json\" " +
                        "--data '$data'"
        )
        jsonResult = parseJsonTextAsMap(result)
    }

    return jsonResult
}

def createJiraIssueComment(String issueKey, String body) {
    String lcaToken = createLcaTokenFor(LCA_AUDIENCE_ATS)

    String data = """{ "body": "${escapeNewLines(body)}" }"""

    retry(COMMAND_RETRY_MAX_COUNT) {
        sh(
                returnStdout: false,
                script: "curl " +
                        "--request POST '$URL_BASE_SNAP_JIRA_API/issue/$issueKey/comment' " +
                        "--header \"SC-LCA-1: $lcaToken\" " +
                        "--header \"Content-type: application/json\" " +
                        "--data '$data'"
        )
    }
}

Map lookUpJiraIssue(String issueKey, String... queryFields) {
    String lcaToken = createLcaTokenFor(LCA_AUDIENCE_ATS)

    Map jsonResult = null

    retry(COMMAND_RETRY_MAX_COUNT) {
        String result = sh(
                returnStdout: true,
                script: "curl " +
                        "-X GET " +
                        "-H \"SC-LCA-1: $lcaToken\" " +
                        "$URL_BASE_SNAP_JIRA_API/issue/$issueKey?fields=${queryFields.join(',')}"
        )
        jsonResult = parseJsonTextAsMap(result)
    }

    return jsonResult
}

String jiraIssueUrlFrom(String key) {
    return "https://$HOST_SNAP_JIRA/browse/$key"
}

String createLcaTokenFor(String audience) {
    String serviceAccount = sh(
            returnStdout: true,
            script: 'gcloud auth list --filter=status:ACTIVE --format=\'value(account)\''
    ).trim()

    sh(
            returnStdout: false,
            script: "type \$HOME/bin/lcaexec >/dev/null 2>&1 || gsutil cp gs://lca-binary-storage/latest/lcaexec " +
                    "\$HOME/bin/lcaexec && chmod +x \$HOME/bin/lcaexec"
    )

    String lcaToken = sh(
            returnStdout: true,
            script: "\$HOME/bin/lcaexec issue google ${serviceAccount} $audience --ttl 300"
    ).trim()

    return lcaToken
}

def notifyOnSlack(String channel, String message) {
    String lcaToken = createLcaTokenFor(LCA_AUDIENCE_ATS)

    String data = """{
    "channel": "$channel",
    "text": "$message",
    "username": "Release Bot"
}"""

    retry(COMMAND_RETRY_MAX_COUNT) {
        sh(
                returnStdout: false,
                script: "curl " +
                        "--request POST '$URL_BASE_SNAP_SLACK_API/chat.postMessage' " +
                        "--header \"SC-LCA-1: $lcaToken\" " +
                        "--header \"Content-type: application/json\" " +
                        "--data '$data'"
        )
    }
}

def createSlackChannel(String name, boolean isPrivate) {
    String lcaToken = createLcaTokenFor(LCA_AUDIENCE_ATS)

    String data = """{
    "name": "$name",
    "is_private": $isPrivate
}"""

    Map jsonResult = null

    retry(COMMAND_RETRY_MAX_COUNT) {
        def result = sh(
                returnStdout: true,
                script: "curl " +
                        "--request POST '$URL_BASE_SNAP_SLACK_API/conversations.create' " +
                        "--header \"SC-LCA-1: $lcaToken\" " +
                        "--header \"Content-type: application/json\" " +
                        "--data '$data'"
        )

        jsonResult = parseJsonTextAsMap(result)
    }

    return jsonResult
}

def waitForPrToClose(prNumber, repo) {
    waitUntil(initialRecurrencePeriod: STATUS_CHECK_INTERVAL_MILLIS, quiet: true) {
        try {
            def prStatus = sh(
                    returnStdout: true,
                    script: "gh pr view $prNumber --repo $repo --json state"
            )
            def prStatusJson = parseJsonTextAsMap(prStatus)
            def state = prStatusJson['state']
            if (state == 'CLOSED' || state == 'MERGED') {
                true
            } else {
                sleep STATUS_CHECK_SLEEP_SECONDS
                false
            }
        } catch (error) {
            println "Checking PR $prNumber status in repo $repo failed due to: ${error}"
            throwIfInterrupted(error)
            false
        }
    }
}

/**
 * @return True if PR was was approved.
 */
def waitForPrToBeApprovedOrClosed(prNumber, repo) {
    def approved = false

    waitUntil(initialRecurrencePeriod: STATUS_CHECK_INTERVAL_MILLIS, quiet: true) {
        try {
            def prStatus = sh(
                    returnStdout: true,
                    script: "gh pr view $prNumber --repo $repo --json reviewDecision,state"
            )
            def prStatusJson = parseJsonTextAsMap(prStatus)
            def state = prStatusJson['state']
            def reviewDecision = prStatusJson['reviewDecision']
            def closedOrMerged = state == 'CLOSED' || state == 'MERGED'
            approved = reviewDecision == 'APPROVED'
            if (closedOrMerged || approved) {
                true
            } else {
                sleep STATUS_CHECK_SLEEP_SECONDS
                false
            }
        } catch (error) {
            println "Checking PR $prNumber status in repo $repo failed due to: ${error}"
            throwIfInterrupted(error)
            false
        }
    }

    return approved
}

def commentOnPrWhenApprovedAndWaitToClose(prNumber, repo, comment) {
    if (waitForPrToBeApprovedOrClosed(prNumber, repo)) {
        retry(COMMAND_RETRY_MAX_COUNT) {
            sh "gh pr comment ${prNumber} --body '${comment}' --repo ${repo}"
        }
        waitForPrToClose(prNumber, repo)
    }
}

String getHeadCommitSha(String repo, String branch) {
    Map jsonResult = null

    retry(COMMAND_RETRY_MAX_COUNT) {
        def result = sh(
                returnStdout: true,
                script: "GH_REPO=$repo gh api /repos/{owner}/{repo}/commits/$branch"
        )
        jsonResult = parseJsonTextAsMap(result)
    }

    return jsonResult['sha']
}

def updateBuildNameFor(ReleaseScope releaseScope, Version version) {
    currentBuild.displayName = "${env.BUILD_NUMBER}${params.TEST_MODE ? "_test" : ""}" +
            "_${releaseScope.toString().toLowerCase()}_${version.toString()}"
}

def createOrResetTestBranchesIfNeeded(ReleaseScope releaseScope, Version releaseVersion) {
    createOrResetTestBranchIfNeeded(
            PATH_CAMERAKIT_DISTRIBUTION_REPO,
            releaseScope == ReleaseScope.PATCH ?
                    cameraKitSdkDistributionReleaseBranchFor(releaseVersion) :
                    addTestBranchPrefixIfNeeded(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN)
    )
    createOrResetTestBranchIfNeeded(
            PATH_ANDROID_REPO,
            releaseScope == ReleaseScope.PATCH ?
                    cameraKitSdkReleaseBranchFor(releaseVersion) :
                    addTestBranchPrefixIfNeeded(BRANCH_ANDROID_REPO_MAIN)
    )
    createOrResetTestBranchIfNeeded(
            PATH_PHANTOM_REPO,
            releaseScope == ReleaseScope.PATCH ?
                    cameraKitSdkReleaseBranchFor(releaseVersion) :
                    addTestBranchPrefixIfNeeded(BRANCH_PHANTOM_REPO_MAIN)
    )

}

def createOrResetTestBranchIfNeeded(String repo, String newBranch) {
    if (params.TEST_MODE) {
        def baseBranch = newBranch.replace(TEST_BRANCH_PREFIX, '')

        sh(
                returnStdout: false,
                script: "GH_REPO=$repo gh api --method DELETE /repos/{owner}/{repo}/git/refs/heads/$newBranch || true"
        )

        def baseBranchHeadSha = getHeadCommitSha(repo, baseBranch)

        sh(
                returnStdout: false,
                script: "GH_REPO=$repo gh api --method POST /repos/{owner}/{repo}/git/refs " +
                        "-f ref='refs/heads/$newBranch' " +
                        "-f sha='$baseBranchHeadSha'"
        )
    }
}

def waitUntilAvailable(String url) {
    waitUntil(initialRecurrencePeriod: STATUS_CHECK_INTERVAL_MILLIS, quiet: true) {
        if (isAvailable(url)) {
            true
        } else {
            sleep STATUS_CHECK_SLEEP_SECONDS
            false
        }
    }
}

boolean isAvailable(String url) {
    try {
        def statusCode = sh(
                returnStdout: true,
                script: "curl -I -s -o /dev/null -w \"%{http_code}\" -L $url"
        ) as int
        println "Status code for $url: $statusCode"
        return statusCode >= 200 && statusCode < 400
    } catch (error) {
        println("Failure while checking if $url is available: $error")
        throwIfInterrupted(error)
        return false
    }
}

String addTestBranchPrefixIfNeeded(String branch) {
    return params.TEST_MODE ? "${TEST_BRANCH_PREFIX}$branch" : branch
}

String cameraKitSdkReleaseBranchFor(Version version) {
    return addTestBranchPrefixIfNeeded("camerakit/release/${version.major}.${version.minor}.x")
}

String cameraKitSdkDistributionReleaseBranchFor(Version version) {
    return addTestBranchPrefixIfNeeded("release/${version.major}.${version.minor}.x")
}

String cameraKitAndroidSdkMavenCentralUrlFor(Version version) {
    return "https://$PATH_MAVEN_CENTRAL_REPO/com/snap/camerakit/camerakit/${version.toString()}"
}

String camerakitIosSdkCocoapodsSpecsUrlFor(Version version) {
    return "https://$PATH_COCOAPODS_SPECS_REPO/d/c/6/SCCameraKit/${version.toString()}/SCCameraKit.podspec.json"
}

static String escapeNewLines(String value) {
    return value.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n")
}

static Map parseJsonTextAsMap(String value) {
    return new JsonSlurper().parseText(value) as Map
}

static Map parseJsonTextAsList(String value) {
    return new JsonSlurper().parseText(value) as List
}

static void throwIfInterrupted(Throwable error) {
    if (error instanceof FlowInterruptedException && ((FlowInterruptedException) error).isActualInterruption()) {
        throw error
    }
}

def promptOrThrowIfInterrupted(String promptMessage, Throwable error, String slackChannel) {
    throwIfInterrupted(error)
    notifyOnSlack(
            slackChannel ?: (params.TEST_MODE ?
                    CHANNEL_SLACK_CAMKIT_MOBILE_OPS_TEST :
                    CHANNEL_SLACK_CAMKIT_MOBILE_SDK_RELEASE_COORD
            ),
            "[Pipeline] Failure${error != null ? " due to: " + (error.message ?: 'unknown reason') : ''}, " +
                    "see: ${env.BUILD_URL}"
    )
    input promptMessage
}

//region global state persistence
State readState() {
    return readState { State state -> state }
}

def readState(Closure stateConsumer) {
    return withState(false, stateConsumer)
}

def updateState(Closure stateConsumer) {
    return withState(true, stateConsumer)
}

def withState(boolean persist, Closure stateConsumer) {
    def result = null
    lock("stateResource_${env.BUILD_NUMBER}") {
        try {
            unstash(KEY_STASH_STATE)
        } catch (ignored) {
            // There might be no stash to unstash hence the error is ignored
        }
        def state = readStateInternal()
        // not using try...finally as we don't want to persist corrupt state
        result = stateConsumer(state)
        if (persist) {
            writeStateInternal(state)
            stash(includes: "**/$FILE_NAME_STATE_JSON", name: KEY_STASH_STATE)
            step([
                    $class: 'ClassicUploadStep',
                    credentialsId: CREDENTIALS_ID_GCS,
                    bucket: "gs://${GCS_BUCKET_SNAPENGINE_BUILDER}/${env.JOB_NAME}/${env.BUILD_NUMBER}",
                    pattern: "**/$FILE_NAME_STATE_JSON"
            ])
        }
    }
    return result
}

State readStateInternal() {
    if (fileExists(FILE_NAME_STATE_JSON)) {
        def stateJson = readFile(FILE_NAME_STATE_JSON)
        return State.fromJson(stateJson)
    } else {
        return new State()
    }
}

void writeStateInternal(State state) {
    writeFile(file: FILE_NAME_STATE_JSON, text: state.toJson())
}
//endregion

//endregion

//region types
/**
 * Defines all possible states that the pipeline goes through and can be used to restart from. Currently, this class
 * holds all stages state information as separate classes that correspond to the pipeline stages 1:1, there are no
 * additional states besides stages. If stages are updated, added to or removed from the pipeline then this class MUST
 * be updated to match, even if stage has no state to store - it makes it easier to reason about the inputs and outputs
 * of each stage.
 */
class State {

    static class Stage0 {

        private static final KEY = "stage0"

        @NonCPS
        @Override
        boolean equals(Object o) {
            return o instanceof Stage0
        }

        @NonCPS
        @Override
        int hashCode() {
            return Stage0.class.hashCode();
        }
    }
    
    static class Stage1 {

        private static final KEY = "stage1"

        ReleaseScope releaseScope = null
        Version releaseVersion = null
        String releaseVerificationIssueKey = null
        String releaseCoordinationSlackChannel = null

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage1 stage1 = (Stage1) o

            if (releaseCoordinationSlackChannel != stage1.releaseCoordinationSlackChannel) return false
            if (releaseScope != stage1.releaseScope) return false
            if (releaseVerificationIssueKey != stage1.releaseVerificationIssueKey) return false
            if (releaseVersion != stage1.releaseVersion) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (releaseScope != null ? releaseScope.hashCode() : 0)
            result = 31 * result + (releaseVersion != null ? releaseVersion.hashCode() : 0)
            result = 31 * result + (releaseVerificationIssueKey != null ? releaseVerificationIssueKey.hashCode() : 0)
            result = 31 * result + (releaseCoordinationSlackChannel != null ?
                    releaseCoordinationSlackChannel.hashCode() : 0)
            return result
        }
    }
    
    static class Stage2 {

        private static final KEY = "stage2"

        Version developmentVersion = null

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage2 stage2 = (Stage2) o

            if (developmentVersion != stage2.developmentVersion) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            return (developmentVersion != null ? developmentVersion.hashCode() : 0)
        }
    }

    static class Stage3 {

        private static final KEY = "stage3"

        SdkBuild developmentAndroidSdkBuild = null
        SdkBuild releaseCandidateAndroidSdkBuild = null
        SdkBuild developmentIosSdkBuild = null
        SdkBuild releaseCandidateIosSdkBuild = null

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage3 stage3 = (Stage3) o

            if (developmentAndroidSdkBuild != stage3.developmentAndroidSdkBuild) return false
            if (developmentIosSdkBuild != stage3.developmentIosSdkBuild) return false
            if (releaseCandidateAndroidSdkBuild != stage3.releaseCandidateAndroidSdkBuild) return false
            if (releaseCandidateIosSdkBuild != stage3.releaseCandidateIosSdkBuild) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (developmentAndroidSdkBuild != null ?
                    developmentAndroidSdkBuild.hashCode() : 0)
            result = 31 * result + (releaseCandidateAndroidSdkBuild != null ?
                    releaseCandidateAndroidSdkBuild.hashCode() : 0)
            result = 31 * result + (developmentIosSdkBuild != null ? developmentIosSdkBuild.hashCode() : 0)
            result = 31 * result + (releaseCandidateIosSdkBuild != null ? releaseCandidateIosSdkBuild.hashCode() : 0)
            return result
        }
    }
    
    static class Stage4 {

        private static final KEY = "stage4"

        String releaseCandidateBinaryBuildsCommitSha = null
        Map<String, BinaryBuild> releaseCandidateBinaryBuilds = [:]

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage4 stage4 = (Stage4) o

            if (releaseCandidateBinaryBuilds != stage4.releaseCandidateBinaryBuilds) return false
            if (releaseCandidateBinaryBuildsCommitSha != stage4.releaseCandidateBinaryBuildsCommitSha) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (releaseCandidateBinaryBuildsCommitSha != null ?
                    releaseCandidateBinaryBuildsCommitSha.hashCode() : 0)
            result = 31 * result + (releaseCandidateBinaryBuilds != null ?
                    releaseCandidateBinaryBuilds.hashCode() : 0)
            return result
        }
    }
    
    static class Stage5 {

        private static final KEY = "stage5"

        boolean releaseVerificationComplete = false
        SdkBuild releaseCandidateAndroidSdkBuild = null
        SdkBuild releaseCandidateIosSdkBuild = null
        Map<String, BinaryBuild> releaseCandidateBinaryBuilds = [:]

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage5 stage5 = (Stage5) o

            if (releaseVerificationComplete != stage5.releaseVerificationComplete) return false
            if (releaseCandidateAndroidSdkBuild != stage5.releaseCandidateAndroidSdkBuild) return false
            if (releaseCandidateBinaryBuilds != stage5.releaseCandidateBinaryBuilds) return false
            if (releaseCandidateIosSdkBuild != stage5.releaseCandidateIosSdkBuild) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (releaseVerificationComplete ? 1 : 0)
            result = 31 * result + (releaseCandidateAndroidSdkBuild != null ?
                    releaseCandidateAndroidSdkBuild.hashCode() : 0)
            result = 31 * result + (releaseCandidateIosSdkBuild != null ? releaseCandidateIosSdkBuild.hashCode() : 0)
            result = 31 * result + (releaseCandidateBinaryBuilds != null ? releaseCandidateBinaryBuilds.hashCode() : 0)
            return result
        }
    }
    
    static class Stage6 {

        private static final KEY = "stage6"

        SdkBuild releaseAndroidSdkBuild = null
        SdkBuild releaseIosSdkBuild = null

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage6 stage6 = (Stage6) o

            if (releaseAndroidSdkBuild != stage6.releaseAndroidSdkBuild) return false
            if (releaseIosSdkBuild != stage6.releaseIosSdkBuild) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (releaseAndroidSdkBuild != null ? releaseAndroidSdkBuild.hashCode() : 0)
            result = 31 * result + (releaseIosSdkBuild != null ? releaseIosSdkBuild.hashCode() : 0)
            return result
        }
    }
    
    static class Stage7 {

        private static final KEY = "stage7"

        @NonCPS
        @Override
        boolean equals(Object o) {
            return o instanceof Stage7
        }

        @NonCPS
        @Override
        int hashCode() {
            return Stage7.class.hashCode();
        }
    }
    
    static class Stage8 {

        private static final KEY = "stage8"

        Map<String, BinaryBuild> releaseBinaryBuilds = [:]
        String releaseGithubUrl = null

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage8 stage8 = (Stage8) o

            if (releaseBinaryBuilds != stage8.releaseBinaryBuilds) return false
            if (releaseGithubUrl != stage8.releaseGithubUrl) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (releaseBinaryBuilds != null ? releaseBinaryBuilds.hashCode() : 0)
            result = 31 * result + (releaseGithubUrl != null ? releaseGithubUrl.hashCode() : 0)
            return result
        }
    }

    static class Stage9 {

        private static final KEY = "stage9"

        boolean androidSdkPublishedToMavenCentral = false
        boolean iosSdkPublishedToCocoapods = false

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage9 stage9 = (Stage9) o

            if (androidSdkPublishedToMavenCentral != stage9.androidSdkPublishedToMavenCentral) return false
            if (iosSdkPublishedToCocoapods != stage9.iosSdkPublishedToCocoapods) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (androidSdkPublishedToMavenCentral ? 1 : 0)
            result = 31 * result + (iosSdkPublishedToCocoapods ? 1 : 0)
            return result
        }
    }
    
    static class Stage10 {

        private static final KEY = "stage10"

        boolean sdkReferenceSyncedToPublicGithub = false
        boolean sdkApiReferenceSyncedToSnapDocs = false

        @NonCPS
        @Override
        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Stage10 stage10 = (Stage10) o

            if (sdkApiReferenceSyncedToSnapDocs != stage10.sdkApiReferenceSyncedToSnapDocs) return false
            if (sdkReferenceSyncedToPublicGithub != stage10.sdkReferenceSyncedToPublicGithub) return false

            return true
        }

        @NonCPS
        @Override
        int hashCode() {
            int result
            result = (sdkReferenceSyncedToPublicGithub ? 1 : 0)
            result = 31 * result + (sdkApiReferenceSyncedToSnapDocs ? 1 : 0)
            return result
        }
    }
    
    static class Stage11 {

        private static final KEY = "stage11"

        @NonCPS
        @Override
        boolean equals(Object o) {
            return o instanceof Stage11
        }

        @NonCPS
        @Override
        int hashCode() {
            return Stage11.class.hashCode()
        }
    }

    Stage0 stage0 = new Stage0()
    Stage1 stage1 = new Stage1()
    Stage2 stage2 = new Stage2()
    Stage3 stage3 = new Stage3()
    Stage4 stage4 = new Stage4()
    Stage5 stage5 = new Stage5()
    Stage6 stage6 = new Stage6()
    Stage7 stage7 = new Stage7()
    Stage8 stage8 = new Stage8()
    Stage9 stage9 = new Stage9()
    Stage10 stage10 = new Stage10()
    Stage11 stage11 = new Stage11()

    @NonCPS
    @Override
    String toString() {
        return toJson()
    }

    @NonCPS
    //@EqualsAndHashCode cannot be used due to https://issues.jenkins.io/browse/JENKINS-40564
    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        State state = (State) o

        if (stage0 != state.stage0) return false
        if (stage1 != state.stage1) return false
        if (stage10 != state.stage10) return false
        if (stage11 != state.stage11) return false
        if (stage2 != state.stage2) return false
        if (stage3 != state.stage3) return false
        if (stage4 != state.stage4) return false
        if (stage5 != state.stage5) return false
        if (stage6 != state.stage6) return false
        if (stage7 != state.stage7) return false
        if (stage8 != state.stage8) return false
        if (stage9 != state.stage9) return false

        return true
    }

    @NonCPS
    @Override
    int hashCode() {
        int result
        result = stage0.hashCode()
        result = 31 * result + stage1.hashCode()
        result = 31 * result + stage2.hashCode()
        result = 31 * result + stage3.hashCode()
        result = 31 * result + stage4.hashCode()
        result = 31 * result + stage5.hashCode()
        result = 31 * result + stage6.hashCode()
        result = 31 * result + stage7.hashCode()
        result = 31 * result + stage8.hashCode()
        result = 31 * result + stage9.hashCode()
        result = 31 * result + stage10.hashCode()
        result = 31 * result + stage11.hashCode()
        return result
    }

    @NonCPS
    String toJson() {
        // To get an ordered map of keys, we need to use a custom map instead of passing this object to JsonOutput:
        return JsonOutput.prettyPrint(JsonOutput.toJson(
                [
                        (Stage0.KEY) : stage0,
                        (Stage1.KEY) : stage1,
                        (Stage2.KEY) : stage2,
                        (Stage3.KEY) : stage3,
                        (Stage4.KEY) : stage4,
                        (Stage5.KEY) : stage5,
                        (Stage6.KEY) : stage6,
                        (Stage7.KEY) : stage7,
                        (Stage8.KEY) : stage8,
                        (Stage9.KEY) : stage9,
                        (Stage10.KEY) : stage10,
                        (Stage11.KEY) : stage11,
                ]
        ))
    }

    @NonCPS
    static State fromJson(String value) {
        def json = new JsonSlurper().parseText(value)

        def state = new State()

        def stage0 = json[Stage0.KEY]
        if (stage0 instanceof Map) {
            // Nothing to do here
        }

        def stage1 = json[Stage1.KEY]
        if (stage1 instanceof Map) {
            state.stage1.releaseScope = ReleaseScope.from(stage1['releaseScope'])
            state.stage1.releaseVersion = Version.from(stage1['releaseVersion'])
            state.stage1.releaseVerificationIssueKey = stage1['releaseVerificationIssueKey']
            state.stage1.releaseCoordinationSlackChannel = stage1['releaseCoordinationSlackChannel']
        }

        def stage2 = json[Stage2.KEY]
        if (stage2 instanceof Map) {
            state.stage2.developmentVersion = Version.from(stage2['developmentVersion'])
        }

        def stage3 = json[Stage3.KEY]
        if (stage3 instanceof Map) {
            state.stage3.developmentAndroidSdkBuild = SdkBuild.from(stage3['developmentAndroidSdkBuild'])
            state.stage3.developmentIosSdkBuild = SdkBuild.from(stage3['developmentIosSdkBuild'])
            state.stage3.releaseCandidateAndroidSdkBuild = SdkBuild.from(stage3['releaseCandidateAndroidSdkBuild'])
            state.stage3.releaseCandidateIosSdkBuild = SdkBuild.from(stage3['releaseCandidateIosSdkBuild'])
        }

        def stage4 = json[Stage4.KEY]
        if (stage4 instanceof Map) {
            state.stage4.releaseCandidateBinaryBuildsCommitSha = stage4['releaseCandidateBinaryBuildsCommitSha']
            state.stage4.releaseCandidateBinaryBuilds = BinaryBuild.mapFrom(stage4['releaseCandidateBinaryBuilds'])
        }

        def stage5 = json[Stage5.KEY]
        if (stage5 instanceof Map) {
            state.stage5.releaseVerificationComplete = stage5['releaseVerificationComplete']
            state.stage5.releaseCandidateAndroidSdkBuild = SdkBuild.from(stage5['releaseCandidateAndroidSdkBuild'])
            state.stage5.releaseCandidateIosSdkBuild = SdkBuild.from(stage5['releaseCandidateIosSdkBuild'])
            state.stage5.releaseCandidateBinaryBuilds = BinaryBuild.mapFrom(stage5['releaseCandidateBinaryBuilds'])
        }

        def stage6 = json[Stage6.KEY]
        if (stage6 instanceof Map) {
            state.stage6.releaseAndroidSdkBuild = SdkBuild.from(stage6['releaseAndroidSdkBuild'])
            state.stage6.releaseIosSdkBuild = SdkBuild.from(stage6['releaseIosSdkBuild'])
        }

        def stage7 = json[Stage7.KEY]
        if (stage7 instanceof Map) {
            // Nothing to do here
        }

        def stage8 = json[Stage8.KEY]
        if (stage8 instanceof Map) {
            state.stage8.releaseBinaryBuilds = BinaryBuild.mapFrom(stage8['releaseBinaryBuilds'])
            state.stage8.releaseGithubUrl = stage8['releaseGithubUrl']
        }

        def stage9 = json[Stage9.KEY]
        if (stage9 instanceof Map) {
            state.stage9.androidSdkPublishedToMavenCentral = stage9['androidSdkPublishedToMavenCentral']
            state.stage9.iosSdkPublishedToCocoapods = stage9['iosSdkPublishedToCocoapods']
        }

        def stage10 = json[Stage10.KEY]
        if (stage10 instanceof Map) {
            state.stage10.sdkReferenceSyncedToPublicGithub = stage10['sdkReferenceSyncedToPublicGithub']
            state.stage10.sdkApiReferenceSyncedToSnapDocs = stage10['sdkApiReferenceSyncedToSnapDocs']

        }

        def stage11 = json[Stage11.KEY]
        if (stage11 instanceof Map) {
            // Nothing to do here
        }

        return state
    }
}

/**
 * Represents a build that ran on a CI machine, typically a Jenkins node.
 */
abstract class CiBuild {

    final Version version
    final String branch
    final String commit
    final long buildNumber
    final String buildJob
    final String buildHost

    CiBuild(Version version, String branch, String commit, long buildNumber, String buildJob, String buildHost) {
        this.version = version
        this.branch = branch
        this.commit = commit
        this.buildNumber = buildNumber
        this.buildJob = buildJob
        this.buildHost = buildHost
    }

    @NonCPS
    @Override
    String toString() {
        return "CiBuild{" +
                "version=" + version.toString() +
                ", branch='" + branch + '\'' +
                ", commit='" + commit + '\'' +
                ", buildNumber=" + buildNumber +
                ", buildJob='" + buildJob + '\'' +
                ", buildHost='" + buildHost + '\'' +
                '}';
    }

    @NonCPS
    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        CiBuild ciBuild = (CiBuild) o

        if (buildNumber != ciBuild.buildNumber) return false
        if (branch != ciBuild.branch) return false
        if (buildHost != ciBuild.buildHost) return false
        if (buildJob != ciBuild.buildJob) return false
        if (commit != ciBuild.commit) return false
        if (version != ciBuild.version) return false

        return true
    }

    @NonCPS
    @Override
    int hashCode() {
        int result
        result = version.hashCode()
        result = 31 * result + branch.hashCode()
        result = 31 * result + commit.hashCode()
        result = 31 * result + (int) (buildNumber ^ (buildNumber >>> 32))
        result = 31 * result + buildJob.hashCode()
        result = 31 * result + buildHost.hashCode()
        return result
    }

    @NonCPS
    String getBuildUrl() {
        return "https://$buildHost/jenkins/job/$buildJob/$buildNumber"
    }
}

/**
 * Represents CameraKit SDK build, currently either Android or iOS.
 */
final class SdkBuild extends CiBuild {

    SdkBuild(Version version, String branch, String commit, long buildNumber, String buildJob, String buildHost) {
        super(version, branch, commit, buildNumber, buildJob, buildHost)
    }

    @NonCPS
    static SdkBuild from(Object object) {
        if (object instanceof Map) {
            return new SdkBuild(
                    Version.from(object['version']),
                    object['branch'],
                    object['commit'],
                    object['buildNumber'],
                    object['buildJob'],
                    object['buildHost']
            )
        } else {
            return null
        }
    }
}

/**
 * Represents a build that produces a binary result such as an app or an archive that can be downloaded.
 */
final class BinaryBuild extends CiBuild {

    final String htmlUrl
    final String downloadUri

    BinaryBuild(
            Version version,
            String branch,
            String commit,
            long buildNumber,
            String buildJob,
            String buildHost,
            String htmlUrl,
            String downloadUri
    ) {
        super(version, branch, commit, buildNumber, buildJob, buildHost)

        this.htmlUrl = htmlUrl
        this.downloadUri = downloadUri
    }

    @NonCPS
    static Map<String, BinaryBuild> mapFrom(Object object) {
        if (object instanceof Map) {
            return object.collectEntries { entry ->
                BinaryBuild binaryBuild = null
                def value = entry.value
                if (value instanceof Map) {
                    binaryBuild = new BinaryBuild(
                            Version.from(value['version']),
                            value['branch'],
                            value['commit'],
                            value['buildNumber'],
                            value['buildJob'],
                            value['buildHost'],
                            value['htmlUrl'],
                            value['downloadUri']
                    )
                }
                [(entry.key): binaryBuild]
            }
        } else {
            return [:]
        }
    }

    @NonCPS
    @Override
    String toString() {
        return "BinaryBuild{" +
                "htmlUrl='" + htmlUrl + '\'' +
                ", downloadUri='" + downloadUri + '\'' +
                "} " + super.toString();
    }

    @NonCPS
    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        if (!super.equals(o)) return false

        BinaryBuild that = (BinaryBuild) o

        if (downloadUri != that.downloadUri) return false
        if (htmlUrl != that.htmlUrl) return false

        return true
    }

    @NonCPS
    @Override
    int hashCode() {
        int result = super.hashCode()
        result = 31 * result + (htmlUrl != null ? htmlUrl.hashCode() : 0)
        result = 31 * result + (downloadUri != null ? downloadUri.hashCode() : 0)
        return result
    }
}

/**
 * Represents the scope of a release in terms of semantic version transitions.
 */
enum ReleaseScope {

    MAJOR('major'),
    MINOR('minor'),
    PATCH('patch')

    ReleaseScope(String id) {
        this.id = id
    }

    private final String id

    @NonCPS
    static ReleaseScope from(String value) {
        for (ReleaseScope releaseScope : values()) {
            if (releaseScope.id.equalsIgnoreCase(value)) {
                return releaseScope;
            }
        }
        return null
    }
}

/**
 * Represents a semantic version with helper methods to parse and manipulate it.
 */
class Version implements Comparable<Version> {

    final int major
    final int minor
    final int patch
    final String qualifier

    private Version(int major, int minor, int patch, String qualifier) {
        this.major = major
        this.minor = minor
        this.patch = patch
        this.qualifier = qualifier
    }

    @NonCPS
    Version dropMinor() {
        return new Version(major, Math.max(0, minor - 1), patch, qualifier)
    }

    @NonCPS
    Version bumpMinor() {
        return new Version(major, Math.max(0, minor + 1), patch, qualifier)
    }

    @NonCPS
    Version bumpPatch() {
        return new Version(major, minor, Math.max(0, patch + 1), qualifier)
    }

    @NonCPS
    Version withQualifier(String value) {
        return new Version(major, minor, patch, value)
    }

    // NOTE: this loses build metadata part after '+'
    @NonCPS
    Version bumpReleaseCandidate() {
        if (qualifier != null) {
            def versionQualifierParts = qualifier.split('[+]')
            def maybeReleaseCandidate = versionQualifierParts[0].replace('-', '')
            if (maybeReleaseCandidate.startsWith('rc')) {
                def releaseCandidateNumber = Integer.parseInt(maybeReleaseCandidate.replaceAll("\\D", ""))
                def newReleaseCandidateNumber = releaseCandidateNumber + 1
                return new Version(major, minor, patch, "-rc$newReleaseCandidateNumber")
            } else {
                return this
            }
        } else {
            return this
        }
    }

    @NonCPS
    static Version from(Object object) {
        if (object instanceof String) {
            return from(object as String)
        } else if (object instanceof Map) {
            return from(object as Map)
        } else {
            return null
        }
    }

    @NonCPS
    static Version from(String versionName) {
        def versionParts = versionName.tokenize('.')
        def major = Integer.parseInt(versionParts[0])
        def minor = Integer.parseInt(versionParts[1])
        def patchParts = versionParts[2].split('[-+]')
        def patch = Integer.parseInt(patchParts[0])
        return new Version(
                major,
                minor,
                patch,
                patchParts.length > 1
                        ? versionParts[2].replaceFirst(patch.toString(), '') +
                        (versionParts.size() > 3 ? '.' + versionParts.takeRight(versionParts.size() - 3).join('.') : '')
                        : null
        )
    }

    @NonCPS
    static Version from(Map map) {
        return from(map['major'], map['minor'], map['patch'], map['qualifier'])
    }

    @NonCPS
    static Version from(int major, int minor, int patch, String qualifier) {
        return new Version(major, minor, patch, qualifier)
    }

    @NonCPS
    @Override
    String toString() {
        return "$major.$minor.$patch${qualifier != null ? qualifier : ''}"
    }

    @NonCPS
    @Override
    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        Version version = (Version) o

        if (major != version.major) {
            return false
        }
        if (minor != version.minor) {
            return false
        }
        if (patch != version.patch) {
            return false
        }
        if (qualifier != version.qualifier) {
            return false
        }

        return true
    }

    @NonCPS
    @Override
    int hashCode() {
        int result
        result = major
        result = 31 * result + minor
        result = 31 * result + patch
        return result
    }

    @NonCPS
    @Override
    int compareTo(Version other) {
        if (major != other.major) {
            return major - other.major;
        }
        if (minor != other.minor) {
            return minor - other.minor;
        }
        if (patch != other.patch) {
            return patch - other.patch;
        }
        if (qualifier == null && other.qualifier == null) {
            return 0
        }
        if (qualifier == null && other.qualifier != null) {
            return 1
        }
        if (qualifier != null && other.qualifier == null) {
            return -1
        }
        def hasPreRelease = qualifier.startsWith('-')
        def otherHasPreRelease = other.qualifier.startsWith('-')
        def hasBuildMetadata = qualifier.startsWith('+')
        def otherHasBuildMetadata = other.qualifier.startsWith('+')
        if (hasPreRelease && otherHasBuildMetadata) {
            return 1
        } else if (otherHasPreRelease && hasBuildMetadata) {
            return -1
        } else if ((hasPreRelease && otherHasPreRelease) || (hasBuildMetadata && otherHasBuildMetadata)) {
            return qualifier <=> qualifier
        } else if (!hasPreRelease && !hasBuildMetadata && (otherHasPreRelease || otherHasBuildMetadata)) {
            return 1
        } else if (!otherHasPreRelease && !otherHasBuildMetadata && (hasPreRelease || hasBuildMetadata)) {
            return -1
        } else {
            return 0
        }
    }
}
//endregion
