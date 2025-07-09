import os
import json
from unittest.mock import patch, mock_open, MagicMock
from pipeline_app.state import PipelineState, ReleaseScope, Version
from pipeline_app.constants import *
from pipeline_app.steps.determine_release_scope_step import DetermineReleaseScopeStep
from pipeline_app.steps.update_sdk_version_step import ProcessUpdateSdkVersionStep
from pipeline_app.steps.sdk_builds_step import ProcessBuildSdksStep
from pipeline_app.steps.release_builds_step import ProcessReleaseBuildsStep
from pipeline_app.steps.final_sdk_build_step import ProcessFinalSDKBuildStep
from pipeline_app.steps.build_distribution_release_step import ProcessDistributionReleaseStep 
from pipeline_app.steps.publish_sdks_step import ProcessPublishSDKsStep
from pipeline_app.steps.sync_sdk_to_public_resources_step import ProcessSyncSDKToPublicResourcesStep  
from pipeline_app.steps.release_verification_step import ReleaseVerificationStep

def mock_json_file_inputs(output_job_id: str, filename: str = None) -> dict:
    # If only one argument is provided, it's the filename
    if filename is None:
        filename = output_job_id
        output_job_id = None

    print(f"MOCKING: get_json_file_from_inputs: {output_job_id}, {filename}")

    # If we have both arguments, combine them as the real function does
    if output_job_id is not None:
        filename = f"{output_job_id}-{filename}"

    if FILE_NAME_CI_RESULT_PR_RESPONSE in filename:
        return {
            "number": 123,
            "title": "Test PR",
            "html_url": "http://test.com",
            "head": {
                "repo": {
                    "name": "test-repo"
                }
            }
        }
    elif FILE_NAME_CI_JOB_BUILD_INFO in filename:
        return {
            "commit": "abc123",
            "build_number": "456",
            "branch": "main",
            "pipeline_id": "123"
        }
    elif FILE_NAME_RELEASE_INFO in filename: 
        return {
            "download_url" : "https://store.snap.applivery.io/camerakit-sample-partner-ios-release?os=ios&build=68529b284a7cce0643a9316e" 
        } 

def mock_open_side_effect(filename, mode='r', **kwargs):  # Add **kwargs
    if FILE_NAME_CI_JOB_PUBLICATIONS in filename:
        return mock_open(read_data="com.snap.camerakit:camerakit:1.41.0").return_value
    elif "ext.gradle" in filename: 
        return mock_open(read_data="def version = '1.41.0'").return_value
    elif "VERSION" in filename:
        return mock_open(read_data="1.41.0").return_value
    return mock_open(read_data="2").return_value

def mock_get_json_file_from_gcs(path: str, filename: str) -> dict:
    """
    Mocks the behavior of get_json_file_from_gcs.
    Given a GCS path, returns the corresponding mock data.
    """
    # Mocking build_info.json from a GCS path
    if FILE_NAME_CI_JOB_BUILD_INFO in filename:
        print(f"MOCK get_json_file_from_gcs: Returning build_info for path: {path}/{filename}")
        return {
            "commit": "abc123_from_gcs",
            "build_number": "789",
            "branch": "gcs_branch",
            "pipeline_id": "gcs_pipeline_456"
        }
    if FILE_NAME_RELEASE_INFO in filename:
        print(f"MOCK get_json_file_from_gcs: Returning release_info for path: {path}/{filename}")
        return {
            "download_url" : "https://store.snap.applivery.io/camerakit-sample-partner-ios-release?os=ios&build=68529b284a7cce0643a9316e" 
        } 

    # Add more conditions here for other GCS JSON files you need to mock
    
    # Default case if no file matches
    raise FileNotFoundError(f"Mock GCS JSON path not found: {path}/{filename}")

def mock_subprocess_run(*args, **kwargs):
    cmd_list = args[0] if args else kwargs.get('args', [])

    # --- Mock for 'gsutil cp' for non-JSON files ---
    if cmd_list and cmd_list[:2] == ["gsutil", "cp"]:
        gcs_source_path = cmd_list[2]
        local_destination_path = cmd_list[3]
        
        # We only mock publications.txt here now. build_info.json is handled by the get_json_file_from_gcs mock.
        if FILE_NAME_CI_JOB_PUBLICATIONS in gcs_source_path:
            mock_content = "com.snap.camerakit:camerakit:1.99.0"
            with open(local_destination_path, 'w') as f:
                f.write(mock_content)
            print(f"MOCKING: gsutil cp {gcs_source_path} -> {local_destination_path}")
            return MagicMock(stdout="", stderr="", returncode=0)
        else:
            print(f"ERROR: gsutil cp mock failed. Unhandled path: {gcs_source_path}")
            return MagicMock(stdout="", stderr=f"Unhandled GCS path: {gcs_source_path}", returncode=1)

    if cmd_list and cmd_list[:3] == ["snapci", "pipeline", "trigger"]:
        return MagicMock(stdout="", stderr="", returncode=0)

    elif cmd_list and cmd_list[:3] == ["snapci", "pipeline", "watch"]:
        return MagicMock(stdout="", stderr="", returncode=0)
    
    elif cmd_list and cmd_list[:3] == ["snapci", "dynamic", "add"]:
        # Mock snapci dynamic add command - return a mock job ID
        return MagicMock(stdout="mock_job_id_12345", stderr="", returncode=0)
    
    elif cmd_list and cmd_list[:3] == ["snapci", "dynamic", "connect"]:
        # Mock snapci dynamic connect command
        return MagicMock(stdout="", stderr="", returncode=0)
        
    if "gh" in cmd_list and "pr" in cmd_list and "view" in cmd_list:
        return MagicMock(
            stdout=json.dumps({
                "isDraft": True,
                "state": "CLOSED",
                "reviewDecision": "APPROVED"
            }),
            stderr="",
            returncode=0
        )
    elif "gh" in cmd_list and "pr" in cmd_list and "create" in cmd_list:
        print("MOCKING: gh pr create")
        return MagicMock(
            stdout="https://github.com/mock/repo/pull/456",
            stderr="",
            returncode=0
        )
    
    # Default fallback
    else:
        print(f"MOCKING: Unhandled command -> {' '.join(cmd_list)}") 
        return MagicMock(stdout="", stderr="", returncode=0)

@patch("pipeline_app.ci_git_helpers.git_checkout_branch", return_value="/tmp/fake_workspace")
@patch("pipeline_app.ci_git_helpers.create_or_reset_test_branch_if_needed", return_value=None)
@patch("pipeline_app.remote_services.create_jira_issue", return_value={"key": "CAMKIT-1234"})
@patch("pipeline_app.remote_services.jira_issue_url_from", return_value="http://jira/CAMKIT-1234")
@patch("pipeline_app.pipeline_helpers.read_file", side_effect=lambda path: "1.41.0" if path == "VERSION" else "1")
@patch("pipeline_app.pipeline_helpers.update_build_name_for", return_value=None)
@patch("pipeline_app.remote_services.notify_on_slack", return_value="1234567890.123456")
@patch("pipeline_app.remote_services.wait_for_slack_message_verification", return_value=None)
@patch("pipeline_app.remote_services.create_lca_token_for", return_value="fake_lca_token")
@patch("pipeline_app.remote_services.create_slack_channel", return_value={"channel": {"id": "C050HUQ9XV0", "name": "camkit-4226-release-1-22-0"}})
@patch("pipeline_app.pipeline_helpers.subprocess.run", side_effect=mock_subprocess_run)
@patch("builtins.open", side_effect=mock_open_side_effect)
@patch("pipeline_app.ci_git_helpers.comment_on_pr_when_approved_and_wait_to_close", return_value=None)
@patch("pipeline_app.pipeline_helpers.get_json_file_from_gcs", side_effect=mock_get_json_file_from_gcs)
@patch("pipeline_app.ci_git_helpers.update_camera_kit_sdk_distribution_with_new_sdk_builds", return_value={
    "number": 123,
    "title": "Update SDK versions", 
    "html_url": "https://github.com/test/repo/pull/123",
    "repo": "Snapchat/test-repo"
})
@patch("pipeline_app.ci_git_helpers.get_head_commit_sha", return_value="abc123def456")
@patch("os.path.exists", side_effect=lambda path: "VERSION" in path or "ext.gradle" in path)
@patch("pipeline_app.pipeline_helpers.get_json_file_from_inputs", side_effect=mock_json_file_inputs)
@patch("pipeline_app.remote_services.create_jira_issue_comment", return_value=None)
@patch("pipeline_app.remote_services.look_up_jira_issue", return_value={
    'fields': {
        'status': {
            'name': 'Complete'
        }
    }
})
@patch("time.sleep", return_value=None)
@patch("os.remove", return_value=None)
@patch("pipeline_app.remote_services.is_url_available", return_value=True)  
def test_determine_release_scope_patch_flow(
    mock_is_url_available,
    mock_os_remove,
    mock_sleep,
    mock_look_up_jira_issue,
    mock_create_jira_issue_comment,
    mock_get_json_file_from_inputs,
    mock_exists,
    mock_get_head_commit_sha,
    mock_update_camera_kit_sdk_distribution_with_new_sdk_builds,
    mock_get_json_file_from_gcs,
    mock_comment_on_pr,
    mock_file,
    mock_subprocess_run,
    mock_create_slack_channel,
    mock_create_lca_token,
    mock_wait_for_slack_message_verification,
    mock_notify_on_slack,
    mock_update_build_name_for,
    mock_read_file,
    mock_jira_issue_url_from,
    mock_create_jira_issue,
    mock_create_or_reset_test_branch_if_needed,
    mock_git_checkout_branch,
    tmp_path
):
    os.environ["release_scope"] = "PATCH"
    os.environ["patch_version_to_release"] = "1.38.2"
    os.environ["BUILD_NUMBER"] = "123"
    os.environ["CI_OUTPUTS"] = "/tmp/OUTPUTS"
    os.environ["CI_INPUTS"] = "/tmp/INPUTS"
    os.environ["TEST_MODE"] = "true"

    state_file = os.path.join("/tmp", "release_state.json")
    if os.path.exists(state_file):
        os.remove(state_file)

    os.makedirs("/tmp/fake_workspace", exist_ok=True)

    state = PipelineState()
    DetermineReleaseScopeStep().run(state)
    ProcessUpdateSdkVersionStep().run(state)
    ProcessBuildSdksStep().run(state)
    ProcessReleaseBuildsStep().run(state)
    ReleaseVerificationStep().run(state)
    ProcessFinalSDKBuildStep().run(state)
    ProcessDistributionReleaseStep().run(state)
    ProcessPublishSDKsStep().run(state)
    
    ProcessSyncSDKToPublicResourcesStep().run(state)
    
    assert state._data.step1.releaseScope == ReleaseScope.PATCH
    assert str(state._data.step1.releaseVersion) == str(Version.from_string("1.38.3"))
    assert state._data.step1.releaseVerificationIssueKey == "CAMKIT-1234"
   
