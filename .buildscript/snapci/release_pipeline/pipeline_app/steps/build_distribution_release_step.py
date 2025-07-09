from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState
from pipeline_app.constants import *
from pipeline_app import remote_services, pipeline_helpers, ci_git_helpers

from pipeline_app.steps.publish_sdks_step import PublishSDKsStep


class BuildDistributionReleaseStep(PipelineStep):

    def execute(self, state: PipelineState):
        with state.read_state():
            pipeline_helpers.dynamic_build_distribution_release(
                self,
                branch=ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion),
                test_mode=False
            )               

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return not state.step8.releaseBinaryBuilds

    def get_next_step_config(self):
        return DynamicStepConfig(
            ProcessDistributionReleaseStep,
            "7: Create Distribution Release",
        )

class ProcessDistributionReleaseStep(PipelineStep):
    def execute(self, state: PipelineState):        
        with state.update_state():
            if not state.step8.releaseBinaryBuilds:
                state.step8.releaseBinaryBuilds = pipeline_helpers.get_binary_builds_for_release(state.step1.releaseVersion)

        with state.read_state():
            release_version = state.step1.releaseVersion
            android_sdk_build = state.step6.releaseAndroidSdkBuild
            ios_sdk_build = state.step6.releaseIosSdkBuild
            binary_builds = state.step8.releaseBinaryBuilds
            slack_channel = state.step1.releaseCoordinationSlackChannel
        
        github_url = ci_git_helpers.create_camera_kit_sdk_distribution_release(
            release_version,
            android_sdk_build,
            ios_sdk_build,
            binary_builds,
            slack_channel,
        )

        with state.update_state():
            remote_services.create_jira_issue_comment(
                state.step1.releaseVerificationIssueKey,
                f"{release_version} release created, details in: {github_url}"
            )
            state.step8.releaseGithubUrl = github_url
      
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return state.step8.releaseGithubUrl is None
    
    def get_next_step_config(self):
        return InProcessStepConfig(PublishSDKsStep)
    