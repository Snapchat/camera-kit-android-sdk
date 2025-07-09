import os
import json
from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState, ReleaseScope, Version
from pipeline_app.constants import *
from pipeline_app import ci_git_helpers, pipeline_helpers
from pipeline_app.steps.sdk_builds_step import SdkBuildsStep
from pipeline_app.pipeline_helpers import release_branch_prefix

class UpdateSdkVersionStep(PipelineStep):

    def execute(self, state: PipelineState):
        with state.update_state():
            next_version = state.step1.releaseVersion.bump_minor()
            release_update_branch = ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_SDK_REPO_MAIN)

            if state.step1.releaseScope == ReleaseScope.PATCH:
                next_version = state.step1.releaseVersion
                release_update_branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)

            current_android_version = ci_git_helpers.get_android_sdk_version(PATH_ANDROID_REPO, release_update_branch)
            if current_android_version != next_version:
                pipeline_helpers.update_camerakit_version_if_needed(
                    self,
                    PATH_ANDROID_REPO,
                    JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE,
                    release_update_branch,
                    'HEAD',
                    release_branch_prefix(),
                    next_version,
                )
            else:
                print(f"No need to update Android SDK version for {next_version}")

            current_ios_version = ci_git_helpers.get_ios_sdk_version(PATH_IOS_REPO, release_update_branch)
            next_ios_version = next_version.with_qualifier('-rc1') if state.step1.releaseScope == ReleaseScope.PATCH else next_version
           
            if current_ios_version != next_ios_version:
                pipeline_helpers.update_camerakit_version_if_needed(
                    self,
                    PATH_IOS_REPO,
                    JOB_CAMERAKIT_SDK_IOS_VERSION_UPDATE,
                    release_update_branch,
                    'HEAD',
                    release_branch_prefix(),
                    next_ios_version
                )
            else:
                print(f"No need to update iOS SDK version for {next_version}")

    def should_execute(self, state: PipelineState) -> bool:
        return True
        
    def get_next_step_config(self):
        return DynamicStepConfig(
            step_class=ProcessUpdateSdkVersionStep,
            display_name="2: Update SDK Version"
        )

class ProcessUpdateSdkVersionStep(PipelineStep):

    def execute(self, state: PipelineState): 
        with state.update_state():
            state.step2.developmentVersion = state.step1.releaseVersion if state.step1.releaseScope == ReleaseScope.PATCH else state.step1.releaseVersion.bump_minor()
            
            ios_job_id = JOB_CAMERAKIT_SDK_IOS_VERSION_UPDATE
            android_job_id = JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE

            # Determine the PR comment based on release scope
            pr_comment = COMMENT_PR_FIRE if state.step1.releaseScope == ReleaseScope.PATCH else COMMENT_PR_COOL

            for job_id in [ios_job_id, android_job_id]:    
                pr_json = pipeline_helpers.get_json_file_from_inputs(job_id, FILE_NAME_CI_RESULT_PR_RESPONSE)
                pipeline_helpers.process_pr_from_json(
                    pr_status_json=pr_json,
                    slack_channel=state.step1.releaseCoordinationSlackChannel,
                    pr_comment=pr_comment
                )

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return state.step2.developmentVersion is None
    
    def get_next_step_config(self):
        return InProcessStepConfig(SdkBuildsStep)