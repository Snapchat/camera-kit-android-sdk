from concurrent.futures import ThreadPoolExecutor
from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState, SdkBuild, Version
from pipeline_app import remote_services, ci_git_helpers, pipeline_helpers
from pipeline_app.constants import *
from pipeline_app.pipeline_helpers import is_test_mode

from pipeline_app.steps.sync_sdk_to_public_resources_step import SyncSDKToPublicResources

class PublishSDKsStep(PipelineStep):
    def execute(self, state: PipelineState):
        with state.read_state():
            branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
            
            release_version = state.step1.releaseVersion
            maven_central_url = camera_kit_android_sdk_maven_central_url_for(release_version)

            if (not remote_services.is_url_available(maven_central_url) and 
                state.step6.releaseAndroidSdkBuild is not None):
                
                pipeline_helpers.publish_camerakit_android_sdk(
                    self,
                    branch=branch,
                    internal=False,
                    step_name="Publish SDKs: Android Release Candidate Build",
                    test_mode=False
                )
                
            if (not remote_services.is_url_available(pipeline_helpers.camera_kit_ios_sdk_cocoapods_specs_url_for(release_version)) and 
                state.step6.releaseIosSdkBuild is not None):
                
                publish_camerakit_ios_sdk_to_cocoapods(
                    self,
                    state.step6.releaseIosSdkBuild,
                    branch,
                    dry_run=False
                   )
                
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                not is_test_mode() and
                state.step8.releaseGithubUrl is not None and
                not state.step9.androidSdkPublishedToMavenCentral and
                not state.step9.iosSdkPublishedToCocoapods
            )
    
    def get_next_step_config(self):
        return DynamicStepConfig(
            step_class=ProcessPublishSDKsStep,
            display_name="8: Publish SDKs",
        )

class ProcessPublishSDKsStep(PipelineStep):
    def execute(self, state: PipelineState):
        with state.read_state():
            release_version = state.step1.releaseVersion
            slack_channel = state.step1.releaseCoordinationSlackChannel
            android_sdk_published_to_maven_central = state.step9.androidSdkPublishedToMavenCentral

        def wait_for_android_and_update_state():
            if not android_sdk_published_to_maven_central:
                remote_services.notify_on_slack(
                    slack_channel,
                    f"[Pipeline] Camera Kit Android SDK {release_version} " +
                    "was published to the Sonatype Staging repository, please verify and " +
                    "release it by signing in to: " +
                    "https://oss.sonatype.org/#stagingRepositories"
                )
                android_url = camera_kit_android_sdk_maven_central_url_for(release_version)
                pipeline_helpers.wait_until_available(android_url)
               
                with state.update_state():
                    state.step9.androidSdkPublishedToMavenCentral = True
                    print("✅ Android SDK is available on Maven Central")

        def wait_for_ios_and_update_state():
            ios_url = camera_kit_ios_sdk_cocoapods_specs_url_for(release_version)
            pipeline_helpers.wait_until_available(ios_url)
            with state.update_state():
                state.step9.iosSdkPublishedToCocoapods = True
                print("✅ iOS SDK is available on CocoaPods")

        with ThreadPoolExecutor(max_workers=2) as executor:
            android_future = executor.submit(wait_for_android_and_update_state)
            ios_future = executor.submit(wait_for_ios_and_update_state)
            
            # Wait for both to complete to ensure the step doesn't finish prematurely
            android_future.result()
            ios_future.result()

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                not is_test_mode() and
                state.step6.releaseAndroidSdkBuild is not None and
                state.step6.releaseIosSdkBuild is not None
            )
        
    def get_next_step_config(self):
        return InProcessStepConfig(SyncSDKToPublicResources)
    

def publish_camerakit_ios_sdk_to_cocoapods(pipeline_step: "PipelineStep", sdk_build: SdkBuild, distribution_branch: str, dry_run: bool):
    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=PATH_IOS_REPO,
        branch=sdk_build.branch,
        job_name=JOB_CAMERAKIT_SDK_IOS_COCOAPODS_PUBLISH_JOB,
        display_name="Publish SDKs: iOS Release Candidate Build",
        commit=sdk_build.commit,
        outputs=["build_info.json"],
        params={
            "camkit_build": str(sdk_build.build_number),
            "camkit_commit": sdk_build.commit,
            "camkit_version": str(sdk_build.version),
            "distribution_branch": distribution_branch,
            "gcs_bucket": "gs://snap-kit-build/scsdk/camera-kit-ios/release",
            "dryrun": dry_run
        }
    )


def camera_kit_android_sdk_maven_central_url_for(version: Version) -> str:
    return f"https://{PATH_MAVEN_CENTRAL_REPO}/com/snap/camerakit/camerakit/{version}"

def camera_kit_ios_sdk_cocoapods_specs_url_for(version: Version) -> str:
    return f"https://{PATH_COCOAPODS_SPECS_REPO}/d/c/6/SCCameraKit/{version}/SCCameraKit.podspec.json"