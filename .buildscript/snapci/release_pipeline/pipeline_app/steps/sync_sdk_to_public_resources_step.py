from pipeline_app.base_step import PipelineStep, InProcessStepConfig, DynamicStepConfig
from pipeline_app.state import PipelineState, Version, is_test_mode
from pipeline_app.constants import *
from pipeline_app import remote_services, pipeline_helpers, ci_git_helpers
from pipeline_app.steps.announce_release_step import AnnounceReleaseStep

def sync_camera_kit_reference_to_public_github(pipeline_step: PipelineStep, release_version: Version, repo: str, pre_release_maven_repository: str):
    job = JOB_CAMERAKIT_DISTRIBUTION_GITHUB_PUBLISH
    branch = ci_git_helpers.camera_kit_release_branch_for(release_version)

    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=PATH_CAMERAKIT_DISTRIBUTION_REPO,
        branch=branch,
        job_name=job,
        display_name="Sync CameraKit Reference to Public Github",
        params={
            "GITHUB_REPO": repo,
            "PRE_RELEASE_MAVEN_REPOSITORY": pre_release_maven_repository
        },
        outputs=[FILE_NAME_CI_RESULT_PR_RESPONSE]
    )

def sync_camera_kit_api_reference_to_snap_docs(pipeline_step: PipelineStep, release_version: Version, gcs_bucket_uri):
    job = JOB_CAMERAKIT_DISTRIBUTION_DOCS_API_REF_GCS_PUBLISH
    branch = ci_git_helpers.camera_kit_release_branch_for(release_version)

    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=PATH_CAMERAKIT_DISTRIBUTION_REPO,
        branch=branch,
        job_name=job,
        display_name="Sync CameraKit API Reference to Snap Docs",
        params={
            "GCS_BUCKET_URI": gcs_bucket_uri
        },
    )

def update_snapdocs_version(slack_channel, release_version):
    return ci_git_helpers.update_snapdocs_version(slack_channel, release_version)

def should_sync_to_public_github(state: PipelineState) -> bool:
    return (
        not state.step10.sdkApiReferenceSyncedToPublicGithub and
        state.step8.releaseGithubUrl is not None and 
        state.step9.androidSdkPublishedToMavenCentral and
        state.step9.iosSdkPublishedToCocoapods
    )

class SyncSDKToPublicResources(PipelineStep):

    def execute(self, state: PipelineState):
        
        with state.read_state():
            if should_sync_to_public_github(state):

                sync_camera_kit_reference_to_public_github(
                    self,
                    state.step1.releaseVersion,
                    PATH_CAMERAKIT_REFERENCE_REPO_TEST if is_test_mode() else PATH_CAMERAKIT_REFERENCE_REPO_PUBLIC,
                    URI_GCS_SNAPENGINE_MAVEN_PUBLISH_RELEASES if is_test_mode() else None
                )
            
            if (not state.step10.sdkApiReferenceSyncedToPublicGithub and 
                state.step8.releaseGithubUrl is not None):                
                
                sync_camera_kit_api_reference_to_snap_docs(
                    self,
                    state.step1.releaseVersion,
                    URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_STAGING if is_test_mode() else URI_GCS_SNAP_KIT_REF_DOCS_CAMERAKIT_PUBLIC,
                )
    
    def should_execute(self, state: PipelineState) -> bool:
        return True
    
    def get_next_step_config(self):
        return DynamicStepConfig(
            step_class=ProcessSyncSDKToPublicResourcesStep,
            display_name="9: Sync SDK to Public Resources"
        )

class ProcessSyncSDKToPublicResourcesStep(PipelineStep):
    def execute(self, state: PipelineState):
       
        with state.read_state():
            sync_to_public_github = should_sync_to_public_github(state)
            synced_to_snap_docs = state.step10.sdkApiReferenceSyncedToSnapDocs
            slack_channel = state.step1.releaseCoordinationSlackChannel
            release_version = state.step1.releaseVersion

        if sync_to_public_github:
            try:
                pr_result = pipeline_helpers.get_json_file_from_inputs(JOB_CAMERAKIT_DISTRIBUTION_GITHUB_PUBLISH, FILE_NAME_CI_RESULT_PR_RESPONSE)
                pr_title = pr_result["title"]
                pr_html_url = pr_result["html_url"]

                print(f"pr created {pr_html_url}")

                remote_services.notify_on_slack(
                    slack_channel,
                    f"{pr_title}: {pr_html_url}"
                )
            except Exception as e:
                print(f"Error processing PR result: {e} File may not exist, ensure PR was created in previous step")
                raise

            with state.update_state():
                state.step10.sdkApiReferenceSyncedToPublicGithub = True

        if not synced_to_snap_docs:
            update_snapdocs_version(
                slack_channel,
                release_version
            )
            with state.update_state():
                state.step10.sdkApiReferenceSyncedToSnapDocs = True

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return (
                not state.step10.sdkApiReferenceSyncedToPublicGithub or 
                not state.step10.sdkApiReferenceSyncedToSnapDocs
            )

    def get_next_step_config(self):
        return InProcessStepConfig(AnnounceReleaseStep)