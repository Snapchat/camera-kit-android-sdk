from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState
from pipeline_app.constants import *
from pipeline_app import remote_services, ci_git_helpers, pipeline_helpers
from pipeline_app.ci_git_helpers import comment_on_pr_when_approved_and_wait_to_close
from pipeline_app.steps.update_changelog_step import UpdateChangeLogStep

# NOTE: iOS SDK does not need final builds so we can just use the latest RC
class FinalAndroidSDKBuildStep(PipelineStep):

    def execute(self, state: PipelineState):
        with state.read_state():
            sdk_release_branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
            slack_channel = state.step1.releaseCoordinationSlackChannel
            release_version = state.step1.releaseVersion

        pipeline_helpers.trigger_and_wait_for_update_camerakit_version(
            PATH_ANDROID_REPO,
            JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE,
            sdk_release_branch,
            'HEAD',
            pipeline_helpers.release_branch_prefix(),
            release_version,
            slack_channel
        )

        pipeline_helpers.publish_camerakit_android_sdk(
            self,
            branch=sdk_release_branch,
            internal=True,
            step_name="Upload SDK Builds: Android Release Candidate Build"
        )
            
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                state.step5.releaseVerificationComplete and 
                state.step6.releaseAndroidSdkBuild is None 
            )

    def get_next_step_config(self):
        return DynamicStepConfig(
            ProcessFinalSDKBuildStep,
            "6: SDK Release Builds",
        )

class ProcessFinalSDKBuildStep(PipelineStep):

    def execute(self, state: PipelineState):
        with state.update_state():
            branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)

            if state.step6.releaseAndroidSdkBuild is None:
                state.step6.releaseAndroidSdkBuild = pipeline_helpers.get_android_sdk_build(JOB_CAMERAKIT_SDK_ANDROID_PUBLISH)
                print(f"Release Android SDK build: {state.step6.releaseAndroidSdkBuild}")
    
        with state.read_state():
            release_version = state.step1.releaseVersion
            release_android_sdk_build = state.step6.releaseAndroidSdkBuild

        pr = ci_git_helpers.update_camera_kit_sdk_distribution_with_new_sdk_builds(
            branch,
            None,
            release_version,
            release_android_sdk_build,
            None
        )

        with state.read_state():
            slack_channel = state.step1.releaseCoordinationSlackChannel
            release_ios_sdk_build = state.step6.releaseIosSdkBuild
        
        remote_services.notify_on_slack(
            slack_channel,
            f"{pr['title']}: {pr['html_url']}"
        )

        ci_git_helpers.comment_on_pr_when_approved_and_wait_to_close(
            pr["repo"],
            pr["number"],
            COMMENT_PR_FIRE
        )

        if release_ios_sdk_build is None:
            with state.update_state():
                state.step6.releaseIosSdkBuild = (
                    state.step5.releaseCandidateIosSdkBuild or 
                    state.step3.iOSReleaseCandidateSdkBuild
                )
                if state.step6.releaseIosSdkBuild is None:
                    raise Exception(
                    f"Expected the {state.step1.releaseVersion} iOS SDK release candidate build to not be null!"
                )
                       
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                state.step5.releaseVerificationComplete and (
                    state.step6.releaseAndroidSdkBuild is None or
                    state.step6.releaseIosSdkBuild is None
                )
            )

    def get_next_step_config(self):
        return InProcessStepConfig(UpdateChangeLogStep)