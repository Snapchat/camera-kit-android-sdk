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
    exec_requirements = {
        "ttl": "120"
    },
    secrets = [
       spookey(name = "AppliveryAppTokenCameraKitSamplePartnerAndroid", env = "APPLIVERY_APP_TOKEN"),
   ],
    notify = [
        slack("#camkit-mobile-ops", states = ["failed"]),
    ],
)

run(
    name="camerakit-distribution-android-publish-github",
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
    name="camerakit-distribution-ios-publish-github",
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

on_comment(
    name = "camkit_distribution_android_publish_comment",
    body = "/publish-android",
    execs = [
        exec("camkit_distribution_android_publish")
    ],
)
