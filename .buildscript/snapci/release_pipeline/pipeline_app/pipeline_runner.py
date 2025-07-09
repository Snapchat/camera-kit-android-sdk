# pipeline_runner.py

from typing import Dict, Optional, Type
import sys
from pipeline_app.state import PipelineState
from pipeline_app.base_step import PipelineStep

# Import all step classes directly from their individual files
from pipeline_app.steps.determine_release_scope_step import DetermineReleaseScopeStep
from pipeline_app.steps.update_sdk_version_step import UpdateSdkVersionStep, ProcessUpdateSdkVersionStep
from pipeline_app.steps.sdk_builds_step import SdkBuildsStep, ProcessBuildSdksStep
from pipeline_app.steps.update_sdk_distribution_version_step import UpdateSdkDistributionVersionStep
from pipeline_app.steps.release_builds_step import ReleaseBuildsStep, ProcessReleaseBuildsStep
from pipeline_app.steps.release_verification_step import ReleaseVerificationStep
from pipeline_app.steps.final_sdk_build_step import FinalAndroidSDKBuildStep, ProcessFinalSDKBuildStep
from pipeline_app.steps.update_changelog_step import UpdateChangeLogStep
from pipeline_app.steps.build_distribution_release_step import BuildDistributionReleaseStep, ProcessDistributionReleaseStep
from pipeline_app.steps.publish_sdks_step import PublishSDKsStep, ProcessPublishSDKsStep
from pipeline_app.steps.sync_sdk_to_public_resources_step import SyncSDKToPublicResources, ProcessSyncSDKToPublicResourcesStep
from pipeline_app.steps.announce_release_step import AnnounceReleaseStep

STEP_REGISTRY: Dict[str, Type[PipelineStep]] = {
    "DetermineReleaseScopeStep": DetermineReleaseScopeStep, # Step 1
    "UpdateSdkVersionStep": UpdateSdkVersionStep, # Step 2
    "ProcessUpdateSdkVersionStep": ProcessUpdateSdkVersionStep,
    "SdkBuildsStep": SdkBuildsStep, # Step 3
    "ProcessBuildSdksStep": ProcessBuildSdksStep,
    "UpdateSdkDistributionVersionStep": UpdateSdkDistributionVersionStep,
    "ReleaseBuildsStep": ReleaseBuildsStep,  # Step 4
    "ProcessReleaseBuildsStep": ProcessReleaseBuildsStep,
    "ReleaseVerificationStep": ReleaseVerificationStep, # Step 5
    "FinalAndroidSDKBuildStep": FinalAndroidSDKBuildStep,
    "ProcessFinalSDKBuildStep": ProcessFinalSDKBuildStep, # Step 6
    "UpdateChangeLogStep": UpdateChangeLogStep, # Step 7
    "BuildDistributionReleaseStep": BuildDistributionReleaseStep,  # Step 8
    "ProcessDistributionReleaseStep": ProcessDistributionReleaseStep, 
    "PublishSDKsStep": PublishSDKsStep, # Step 9
    "ProcessPublishSDKsStep": ProcessPublishSDKsStep,
    "SyncSDKToPublicResources": SyncSDKToPublicResources, # Step 10
    "ProcessSyncSDKToPublicResourcesStep": ProcessSyncSDKToPublicResourcesStep, 
    "AnnounceReleaseStep": AnnounceReleaseStep  # Step 11
}

def run_step(step_name: str, state_input: Optional[str]):
    if step_name not in STEP_REGISTRY:
        raise ValueError(f"Unknown step: {step_name}")

    state = PipelineState(state_input)
    step_instance = STEP_REGISTRY[step_name]()
    step_instance.run(state)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python pipeline_runner.py <step_name> [json_state]")
        print("Available steps:", ", ".join(STEP_REGISTRY.keys()))
        exit(1)

    step = sys.argv[1]
    input_json = sys.argv[2] if len(sys.argv) > 2 else None
    run_step(step, input_json)
    