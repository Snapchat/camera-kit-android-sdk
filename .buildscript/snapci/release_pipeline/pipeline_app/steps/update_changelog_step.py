from pipeline_app.base_step import PipelineStep, InProcessStepConfig
from pipeline_app.state import PipelineState
from pipeline_app import ci_git_helpers
from pipeline_app.steps.build_distribution_release_step import BuildDistributionReleaseStep

class UpdateChangeLogStep(PipelineStep):

    def execute(self, state: PipelineState):
        with state.read_state():
            ci_git_helpers.update_camera_kit_sdk_distribution_changelog_for_release(
                state.step1.releaseVersion, 
                state.step1.releaseCoordinationSlackChannel
            )
            
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                state.step5.releaseVerificationComplete and
                state.step6.releaseAndroidSdkBuild is not None and
                state.step6.releaseIosSdkBuild is not None and
                not bool(state.step8.releaseBinaryBuilds)
            )
    
    def get_next_step_config(self):
        return InProcessStepConfig(BuildDistributionReleaseStep)