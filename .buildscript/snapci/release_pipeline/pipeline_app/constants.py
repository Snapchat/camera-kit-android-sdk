STATUS_CHECK_SLEEP_SECONDS = 60
COMMAND_RETRY_MAX_COUNT = 3

TEST_BRANCH_PREFIX = "camkit-pipeline-test/"

URL_BASE_SNAP_JIRA_API = "https://to-jira-dot-sc-ats.appspot.com/rest/api/2"
URL_BASE_SNAP_SLACK_API = "https://to-slack-dot-sc-ats.appspot.com/api"
URL_GH_CLI_DOWNLOAD = "https://github.com/cli/cli/releases/download/v2.20.2/gh_2.20.2_linux_386.tar.gz"

URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_PUBLIC = "gs://snap-kit-reference-docs/CameraKit"
URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_STAGING = "gs://snap-kit-reference-docs-staging/CameraKit"
URI_GCS_SNAPENGINE_MAVEN_PUBLISH_RELEASES = "gcs://snapengine-maven-publish/releases"

FILE_NAME_CI_JOB_PUBLICATIONS= "publications.txt"
FILE_NAME_CI_JOB_BUILD_INFO = "build_info.json"
FILE_NAME_CI_RESULT_PR_RESPONSE = "pr_request_response.json"
FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG = "CHANGELOG.md"
FILE_NAME_STATE_JSON = "state.json"
FILE_NAME_RELEASE_INFO = "applivery_release_info.json"

STATE_JSON = "state.json"

GCS_BUCKET_SNAPENGINE_BUILDER = "snapengine-builder-artifacts"

LCA_AUDIENCE_ATS = "sc-ats.appspot.com"

CREDENTIALS_ID_GCS = "everybodysaydance-test"
CREDENTIALS_ID_SNAPENGINESC_GITHUB_SSH = "eb2750a7-56dc-4464-bb43-4109099a4623"
CREDENTIALS_ID_SNAPENGINESC_GITHUB_TOKEN = "2e9af316-971e-4cf3-be13-f23e7afcdc79"

HOST_SNAPCI_BUILDER = "ci-portal.mesh.sc-corp.net"
HOST_SNAP_GHE = "github.sc-corp.net"
HOST_SNAP_JIRA = "jira.sc-corp.net"

PATH_ANDROID_REPO = "Snapchat/camera-kit-android-sdk"
PATH_IOS_REPO = "Snapchat/camera-kit-ios-sdk"
PATH_SNAP_DOCS_REPO = "Snapchat/snap-docs"
PATH_CAMERAKIT_DISTRIBUTION_REPO = "Snapchat/camera-kit-distribution"
PATH_CAMERAKIT_REFERENCE_REPO_PUBLIC = "Snapchat/camera-kit-reference"
PATH_CAMERAKIT_REFERENCE_REPO_TEST = "Snap-Kit/camera-kit-reference-test"
PATH_COCOAPODS_SPECS_REPO = "raw.githubusercontent.com/CocoaPods/Specs/master/Specs"
PATH_MAVEN_CENTRAL_REPO = "repo1.maven.org/maven2"

BRANCH_SDK_REPO_MAIN = "main"
BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN = "master"
BRANCH_SNAP_DOCS_REPO_MAIN = "main"

KEY_CAMERAKIT_DISTRIBUTION_BUILD = "SDK distribution build"
KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID = "SDK distribution Android sample app build"
KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS = "SDK distribution iOS sample app build"
KEY_STASH_STATE = "state"

JOB_CAMERAKIT_DISTRIBUTION_TRIGGER_JOB = "trigger_job"
JOB_CAMERAKIT_DISTRIBUTION_PUBLISH_ANDROID_SDK = "publish_android_sdk"
JOB_CAMERAKIT_DISTRIBUTION_PUBLISH_IOS_SDK = "publish_ios_sdk"

JOB_CAMERAKIT_SDK_ANDROID_PUBLISH = "camkit_android_publish"
JOB_CAMERAKIT_SDK_IOS_PUBLISH= "publish-sdk"

JOB_CAMERAKIT_SDK_IOS_COCOAPODS_PUBLISH_JOB = "cocoapods-publish"
JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE = "camerakit-android-version-update"
JOB_CAMERAKIT_SDK_IOS_VERSION_UPDATE = "camerakit-ios-version-update"
JOB_CAMERAKIT_DISTRIBUTION_BUILD = "camkit_distribution_build"
JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID = "camkit_distribution_android_publish"
JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS = "camkit_distribution_ios_publish"
JOB_CAMERAKIT_DISTRIBUTION_GITHUB_PUBLISH = "camkit_distribution_publish_github"
JOB_CAMERAKIT_DISTRIBUTION_DOCS_API_REF_GCS_PUBLISH = "camkit_distribution_publsh_api_ref_docs_to_gcs"

CHANNEL_SLACK_CAMKIT_MOBILE_OPS_TEST = '#camkit-mobile-ops-pipeline-test'
CHANNEL_SLACK_CAMKIT_MOBILE_SDK_RELEASE_COORD = '#camkit-mobile-sdk-release-coordination'

COMMENT_PR_COOL = ":cool:"
COMMENT_PR_FIRE = ":fire:"

VERIFIED_OWNER_SLACK_IDS = {"U07LWTZCSAD", "U03JG3C3VNJ", "U02KMAKEAF4", "U0284PSL84U"}
