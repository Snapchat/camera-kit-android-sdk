features(
  trigger_controller = "snapci",
)

image.vm(
    name = "camkit_distribution_android_image",
    base = "snap-ubuntu-2404",
    provision_with = ".buildscript/snapci/image/provision.sh",
)

_DEFAULT_EXEC_REQUIREMENTS = {
    "vm_image": "#b6be24987af8ba81556d4f5e21c14bdf9f2ff77d//camkit_distribution_android_image",
    "disk_type": "pd-ssd",
}

def _run_android(
        exec_requirements = {},
        secrets = [],
        **kwargs):
    run(
        exec_requirements = _DEFAULT_EXEC_REQUIREMENTS | exec_requirements,
        secrets = secrets,
        **kwargs
    )

_run_android(
    name = "camkit_distribution_android_publish",
    steps = [
        process(".buildscript/snapci/android/publish.sh"),
    ],
    secrets = [
        spookey(name = "AppliveryAppTokenCameraKitSamplePartnerAndroid", env = "APPLIVERY_APP_TOKEN"),
        spookey(name = "CI_GITHUB_APIKEY", env = "GITHUB_APIKEY"),
    ],
    params = {
        "test_mode": param.bool(
            default=False,
            doc="Enable test mode to skip script execution"
        )
    },
    notify = [
        slack("#camkit-mobile-ops", states = ["failed"]),
    ],
)

run(
    name="camkit_distribution_android_publish_github",
        steps = [
        process(".buildscript/snapci/android/publish_to_github_sdk_repo.sh"),
    ],
    exec_requirements = {
        "os": "macos",
        "arch": "arm64",
    },
    secrets = [
       spookey(name = "CameraKitPublicGithubUsername", env = "GITHUB_USERNAME"),
       spookey(name = "CameraKitPublicGithubAPIKey", env = "GITHUB_APIKEY"),
   ],
)

run(
    name="camkit_distribution_ios_publish_github",
        steps = [
        process(".buildscript/snapci/ios/publish_to_github_sdk_repo.sh"),
    ],
    exec_requirements = {
        "os": "macos",
        "arch": "arm64",
        "xcode_version": "16.0_16A242d"
    },
    secrets = [
       spookey(name = "CameraKitPublicGithubUsername", env = "GITHUB_USERNAME"),
       spookey(name = "CameraKitPublicGithubAPIKey", env = "GITHUB_APIKEY"),
   ],
)

run(
    name="camkit_distribution_publish_github",
        steps = [
        process(".buildscript/snapci/publish_to_github.sh"),
    ],
    exec_requirements = {
        "os": "macos",
        "arch": "arm64",
        "xcode_version": "16.0_16A242d"
    },
    secrets = [
       spookey(name = "CameraKitReferenceGithubUsername", env = "GITHUB_USERNAME"),
       spookey(name = "CameraKitReferenceGithubAPIKey", env = "GITHUB_APIKEY"),
   ],
)

run(
    name="camkit_distribution_ios_publish",
        steps = [
        process(".buildscript/snapci/ios/publish.sh"),
    ],
    exec_requirements = {
        "os": "macos",
        "arch": "arm64",
        "xcode_version": "16.0_16A242d"
    },
    secrets = [
        spookey(name = "AppliveryAppTokenCameraKitSamplePartnerIOS", env = "APPLIVERY_APP_TOKEN"),
        spookey(name = "CI_GITHUB_APIKEY", env = "GITHUB_APIKEY"),
    ],
    params = {
        "test_mode": param.bool(
            default=False,
            doc="Enable test mode to skip script execution"
        )
    },
    notify = [
        slack("#camkit-mobile-ops", states = ["failed"]),
    ],
)

run(
    name="camkit_distribution_build",
    steps= [
        process("bash", "-c", ".buildscript/snapci/build.sh")
    ],
    exec_requirements = {
        "os": "macos",
        "arch": "arm64",
        "xcode_version": "16.0_16A242d"
    },
    params = {
        "test_mode": param.bool(
            default=False,
            doc="Enable test mode to skip script execution"
        )
    },
)

run (
    name="camkit_distribution_publsh_api_ref_docs_to_gcs",
    steps=[
        process(".buildscript/snapci/publish_api_ref_docs_to_gcs.sh")
    ],
    params={
        "gcs_bucket_uri": param.string(
            default="gs://snap-kit-reference-docs-staging/CameraKit"
        )
    },
    exec_requirements = {
        "os": "macos",
        "arch": "arm64",
        "xcode_version": "16.0_16A242d"
    },
)

run(
    name="camkit_distribution_release_pipeline",
    steps = [
        process("bash", "-l", ".buildscript/snapci/release_pipeline/setup.sh"),
        process("bash", "-l", "-c", ".buildscript/snapci/release_pipeline/run_step.sh"),
    ],
    params = {
        "test_mode": param.bool(
            default=True
        ),       
        "predefined_state_json_bucket_path": param.string(
            default=""
        ),
        "release_scope": param.string(
            default = "MINOR",
	        choices = ["MINOR", "MAJOR", "PATCH"]
        ),
        "patch_version_to_release": param.string(
            default="",
            doc="What is the existing release version to patch? (This value must be set when release scope is PATCH)"
        ),
        "run_step": param.string(
                default="DetermineReleaseScopeStep",
                doc="Used by dynamic trigger to kickoff job at specfic step"
            ),
    },
    secrets = [
        spookey(name = "CameraKitDistributionGitToken", env = "GITHUB_APIKEY"),
    ],
    timeout_mins = 9999,
    dynamic=True,
)

run(
    name="trigger_job",
    steps=[
        process(".buildscript/snapci/trigger_job.sh")
    ],
    params= {
        "label": param.string() 
    },
    dynamic=True,
)

on_comment(
    name = "camkit_distribution_android_publish_comment",
    body = "/publish-android",
    execs = [
        exec("camkit_distribution_android_publish")
    ],
)

on_comment(
    name = "camkit_distribution_ios_publish_comment",
    body = "/publish-ios",
    execs = [
        exec("camkit_distribution_ios_publish")
    ],
)

on_cool(
    execs=[
        "camkit_distribution_build",
    ],
)
