from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState
from pipeline_app.constants import *
from pipeline_app import ci_git_helpers, pipeline_helpers
from pipeline_app.steps.update_sdk_distribution_version_step import UpdateSdkDistributionVersionStep

class SdkBuildsStep(PipelineStep):

    def execute(self, state: PipelineState):   
        with state.update_state():
            if state.step3.androidReleaseCandidateSdkBuild is None:
                branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_ANDROID_PUBLISH, branch)
                
                pipeline_helpers.publish_camerakit_android_sdk(
                    self,
                    job_id=job_id,
                    branch=branch,
                    internal=True,
                    step_name="Upload SDK Builds: Android Release Candidate Build"
                )
                
            if state.step3.androidDevSdkBuild is None and \
                state.step2.developmentVersion != state.step1.releaseVersion:
                branch = ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_SDK_REPO_MAIN)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_ANDROID_PUBLISH, branch)
                
                pipeline_helpers.publish_camerakit_android_sdk(
                    self,
                    job_id=job_id,
                    branch=branch,
                    internal=True,
                    step_name="Upload SDK Builds: Android Development Build"
                )
            
            if state.step3.iOSReleaseCandidateSdkBuild is None:
                branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_IOS_PUBLISH, branch)
                
                pipeline_helpers.publish_camerakit_ios_sdk(
                    self,
                    job_id=job_id,
                    branch=branch,
                    step_name="Upload SDK Builds: iOS Release Candidate Build"
                )

            if state.step3.iOSDevSdkBuild is None and \
                state.step2.developmentVersion != state.step1.releaseVersion:
                branch = ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_SDK_REPO_MAIN)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_IOS_PUBLISH, branch)
                
                pipeline_helpers.publish_camerakit_ios_sdk(
                    self,
                    job_id=job_id,
                    branch=branch,
                    step_name="Upload SDK Builds: iOS Development Build"
                )
    
    def should_execute(self, state: PipelineState) -> bool:
        return True
    
    def get_next_step_config(self):
        return DynamicStepConfig(
            step_class=ProcessBuildSdksStep,
            display_name="3: Build SDKs"
            )
 
class ProcessBuildSdksStep(PipelineStep):

    def execute(self, state: PipelineState):
        with state.update_state():
            if state.step3.androidReleaseCandidateSdkBuild is None:
                branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_ANDROID_PUBLISH, branch)
                state.step3.androidReleaseCandidateSdkBuild = pipeline_helpers.get_android_sdk_build(job_id)
                
                print(f"Release candidate Android SDK build: {state.step3.androidReleaseCandidateSdkBuild}")

            if state.step3.androidDevSdkBuild is None:
                branch = ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_SDK_REPO_MAIN)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_ANDROID_PUBLISH, branch)
                state.step3.androidDevSdkBuild = pipeline_helpers.get_android_sdk_build(job_id)
                
                print(f"Development Android SDK build: {state.step3.androidDevSdkBuild}")

            if state.step3.iOSReleaseCandidateSdkBuild is None:
                branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_IOS_PUBLISH, branch)
                state.step3.iOSReleaseCandidateSdkBuild = pipeline_helpers.get_ios_build(job_id, state.step1.releaseVersion)
                
                print(f"Release candidate iOS SDK build: {state.step3.iOSReleaseCandidateSdkBuild}")

            if state.step3.iOSDevSdkBuild is None:
                branch = ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_SDK_REPO_MAIN)
                job_id = pipeline_helpers.get_publish_job_id_for_branch(JOB_CAMERAKIT_SDK_IOS_PUBLISH, branch)
                state.step3.iOSDevSdkBuild = pipeline_helpers.get_ios_build(job_id, state.step1.releaseVersion)
                
                print(f"Development iOS SDK build: {state.step3.iOSDevSdkBuild}")
    
    def should_execute(self, state: PipelineState) -> bool:
        return True
    
    def get_next_step_config(self):
        return InProcessStepConfig(UpdateSdkDistributionVersionStep)