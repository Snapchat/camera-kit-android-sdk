from pipeline_app.base_step import PipelineStep
from pipeline_app.state import PipelineState
from pipeline_app import remote_services

class AnnounceReleaseStep(PipelineStep):
    def execute(self, state: PipelineState):
        with state.read_state():
            remote_services.notify_on_slack(
                state.step1.releaseCoordinationSlackChannel,
                f"[Pipeline] CameraKit SDK {state.step1.releaseVersion} "
                f"release is complete, "
                f"details in: {state.step8.releaseGithubUrl}"
            )
    
    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                state.step8.releaseGithubUrl is not None
            )