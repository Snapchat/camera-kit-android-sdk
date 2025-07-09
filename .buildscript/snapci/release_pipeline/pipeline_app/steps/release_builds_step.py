import os
from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState
from pipeline_app.constants import *
from pipeline_app import remote_services, ci_git_helpers, pipeline_helpers
from pipeline_app.pipeline_helpers import create_camera_kit_sdk_distribution_release_candidate_message
from pipeline_app.steps.release_verification_step import ReleaseVerificationStep

class ReleaseBuildsStep(PipelineStep):
    def execute(self, state: PipelineState):
        with state.update_state():
            release_branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
            commit = state.step4.releaseCandidateSdkBuildsCommitSha

            pipeline_helpers.dynamic_build_distribution_release(
                self,
                release_branch,
                commit,
                False
            )
                
    def should_execute(self, state: PipelineState) -> bool:
            with state.read_state():
                return not bool(state.step4.releaseCandidateBinaryBuilds)

    def get_next_step_config(self):
        return DynamicStepConfig(
            ProcessReleaseBuildsStep,
            "4: Release Builds",
        )

class ProcessReleaseBuildsStep(PipelineStep):
    def execute(self, state: PipelineState):
        with state.update_state():
            release_version = state.step1.releaseVersion
            
            binary_builds = pipeline_helpers.get_binary_builds_for_release(
                release_version
            )

            state.step4.releaseCandidateBinaryBuilds = binary_builds

        with state.read_state():
            message = pipeline_helpers.create_camera_kit_sdk_distribution_release_candidate_message(
                state.step1.releaseVersion,
                state.step4.releaseCandidateBinaryBuilds,
                os.environ.get("CI_PIPELINE_URL")
            )

            remote_services.create_jira_issue_comment(
                state.step1.releaseVerificationIssueKey,
                message
            )
            
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return not bool(state.step4.releaseCandidateBinaryBuilds)
        
    def get_next_step_config(self):
        return DynamicStepConfig(
            ReleaseVerificationStep,
            "5: Release Verification",
        )