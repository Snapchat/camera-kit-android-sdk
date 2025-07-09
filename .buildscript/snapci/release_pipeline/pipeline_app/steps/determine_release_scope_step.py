import os
from pipeline_app.base_step import PipelineStep, InProcessStepConfig
from pipeline_app.state import PipelineState, ReleaseScope, Version, is_test_mode
from pipeline_app.constants import BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN, CHANNEL_SLACK_CAMKIT_MOBILE_OPS_TEST, CHANNEL_SLACK_CAMKIT_MOBILE_SDK_RELEASE_COORD
from pipeline_app import remote_services, ci_git_helpers, pipeline_helpers
from pipeline_app.steps.update_sdk_version_step import UpdateSdkVersionStep

class DetermineReleaseScopeStep(PipelineStep):
    
    def execute(self, state: PipelineState):
        with state.update_state():
            state.step1.releaseScope = ReleaseScope[os.environ.get("release_scope")]
            print(f"Selected next release scope: {state.step1.releaseScope}")

            if state.step1.releaseScope == ReleaseScope.MAJOR:
                raise ValueError("The MAJOR release scope is not currently supported.")
            
            if state.step1.releaseScope == ReleaseScope.MINOR or ReleaseScope.PATCH:
                distribution_workspace = ci_git_helpers.git_checkout_branch(
                    repo_url="git@github.sc-corp.net:Snapchat/camera-kit-distribution.git",
                    branch=ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN) 
                )

                os.chdir(distribution_workspace)
   
                version_str = pipeline_helpers.read_file('VERSION').strip()
                current_version = Version.from_string(version_str)

                print(f"Current development version: {current_version}")

                state.step1.releaseVersion = current_version

                if (state.step1.releaseScope == ReleaseScope.PATCH):
                    patch_version_input = os.environ.get("patch_version_to_release")
                    if not patch_version_input:
                        raise ValueError("patch_version_to_release environment variable must be set for PATCH releases.")

                    release_version_to_patch = Version.from_string(patch_version_input)
                    if release_version_to_patch >= current_version:
                        raise ValueError(
                            f"The release version to patch cannot be equal or greater than the current development version: {current_version}"
                        )

                    patch_release_version = release_version_to_patch.bump_patch()
                    print(f"Next patch release version: {patch_release_version}")

                    state.step1.releaseVersion = patch_release_version

                # Do we still need to do this?
                pipeline_helpers.update_build_name_for(state.step1.releaseScope, state.step1.releaseVersion)

                # To speed up testing process we automatically create or reset test branches after determining
                # the release scope and version. Note that this will mess up any currently running pipeline
                # that relies on those branches so make sure to execute this only a single job at a time!

                ci_git_helpers.create_or_reset_test_branch_if_needed(state.step1.releaseScope, state.step1.releaseVersion)

                if (state.step1.releaseVerificationIssueKey is None):
                    prefix = "[TEST] " if is_test_mode() else ""
                    summary = f"{prefix}SDK {state.step1.releaseVersion} sign off"
                    description = (
                        f"This is the main ticket for the Camera Kit SDK {state.step1.releaseVersion} release verification.\n"
                        "Initial release candidate builds are pending, this issue will be updated with more details in a bit.\n\n"
                        f"h6. Generated in: {os.environ.get('CI_PIPELINE_URL')}"
                    )

                    issue = remote_services.create_jira_issue("CAMKIT", "Task", summary, description)
                    issue_key = issue['key']
                    issue_url = remote_services.jira_issue_url_from(issue_key)

                    print(f"Created Jira issue: {issue_url}")

                    state.step1.releaseVerificationIssueKey = issue_key

                if (state.step1.releaseCoordinationSlackChannel is None):
                    channel_name = f"{state.step1.releaseVerificationIssueKey.lower()}-release-{str(state.step1.releaseVersion).replace('.', '-')}"
                    result = remote_services.create_slack_channel(channel_name, False)
                    channel_id = result['channel']['id']

                    state.step1.releaseCoordinationSlackChannel = channel_id

                    remote_services.notify_on_slack(
                        CHANNEL_SLACK_CAMKIT_MOBILE_OPS_TEST if is_test_mode() else CHANNEL_SLACK_CAMKIT_MOBILE_SDK_RELEASE_COORD ,
                        f"[Pipeline] Starting {state.step1.releaseVersion} release, "
                        f"ticket: {remote_services.jira_issue_url_from(state.step1.releaseVerificationIssueKey)}, "
                        f"co-ordination channel: <#{channel_id}>"
                    )

                    remote_services.notify_on_slack(
                        state.step1.releaseCoordinationSlackChannel,
                        f"[Pipeline] Running release flow in: {os.environ.get('CI_PIPELINE_URL')}. "
                        f"Tracking all updates in the verification ticket: "
                        f"{remote_services.jira_issue_url_from(state.step1.releaseVerificationIssueKey)} "
                    )

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return not all([
                state.step1.releaseScope is not None,
                state.step1.releaseVersion is not None,
                state.step1.releaseVerificationIssueKey is not None,
                state.step1.releaseCoordinationSlackChannel is not None
            ])

    def get_next_step_config(self):
        return InProcessStepConfig(UpdateSdkVersionStep)