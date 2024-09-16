image.vm(
    name = "camkit_distribution_android_image",
    base = "snapci-linux",
    provision_with = ".buildscript/snapci/image/provision.sh",
)

_DEFAULT_EXEC_REQUIREMENTS = {
    "vm_image": "#a746c7f8731a7697fcd0d961b41ce2fbb3047ce7//camkit_distribution_android_image",
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
       spookey(name = "CameraKitAppCenterToken", env = "APPCENTER_TOKEN"),
   ],
    notify = [
        slack("#camkit-mobile-ops", states = ["failed"]),
    ],
)

on_comment(
    name = "camkit_distribution_android_publish_comment",
    body = "/publish-android",
    execs = [
        exec("camkit_distribution_android_publish")
    ],
)
