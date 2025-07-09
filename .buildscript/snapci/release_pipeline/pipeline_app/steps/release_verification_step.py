import os
import time
import threading
import traceback
from concurrent.futures import ThreadPoolExecutor
from pipeline_app.base_step import PipelineStep, InProcessStepConfig
from pipeline_app.state import PipelineState, is_test_mode
from pipeline_app.constants import *
from pipeline_app import remote_services, pipeline_helpers, ci_git_helpers
from pipeline_app.steps.final_sdk_build_step import FinalAndroidSDKBuildStep

class ReleaseVerificationStep(PipelineStep):

    def execute(self, state: PipelineState):     
        def release_verification_worker():
            try:
                self._wait_for_release_verification(state)
            except Exception as e:
                print(f"FATAL: Exception in verification thread: {e}", flush=True)
                traceback.print_exc()
                os._exit(1)
        
        def rc_builds_worker():
            try:
                self._trigger_on_demand_release_builds(state)
            except Exception as e:
                print(f"FATAL: Exception in rc builds thread: {e}", flush=True)
                traceback.print_exc()
                os._exit(1)
        
        def distribution_build_worker():
            try:
                self._trigger_distribution_build(state)
            except Exception as e:
                print(f"FATAL: Exception in distribution build thread: {e}", flush=True)
                traceback.print_exc()
                os._exit(1)
        
        # Start all three threads
        verification_thread = threading.Thread(target=release_verification_worker)
        rc_builds_thread = threading.Thread(target=rc_builds_worker)
        distribution_build_thread = threading.Thread(target=distribution_build_worker)
        
        verification_thread.start()
        rc_builds_thread.start()
        distribution_build_thread.start()
        
        # Wait for all three to complete
        verification_thread.join()
        rc_builds_thread.join()
        distribution_build_thread.join()

    def _trigger_on_demand_release_builds(self, state: PipelineState):
        while True:
            print("Triggering on demand release builds")
            with state.read_state():
                if state.step5.releaseVerificationComplete:
                    break
                
                release_version = state.step1.releaseVersion
                release_candidate_ios_sdk_build = state.step5.releaseCandidateIosSdkBuild or state.step3.iOSReleaseCandidateSdkBuild
                release_candidate_android_sdk_build = state.step5.releaseCandidateAndroidSdkBuild or state.step3.androidReleaseCandidateSdkBuild
                slack_channel = state.step1.releaseCoordinationSlackChannel
                
            sdk_release_branch = ci_git_helpers.camera_kit_release_branch_for(release_version)
            updated_android_sdk_build = None
            updated_ios_sdk_build = None

            def build_android_if_needed():
                android_head_commit_sha = ci_git_helpers.get_head_commit_sha(PATH_ANDROID_REPO, sdk_release_branch)
                android_build_commit_sha = release_candidate_android_sdk_build.commit
                
                print(f"Android SDK HEAD commit: {android_head_commit_sha}, build commit: {android_build_commit_sha}", flush=True)
                
                if android_head_commit_sha != android_build_commit_sha:
                    print(f"It appears that {PATH_ANDROID_REPO} repository {sdk_release_branch} branch has new commits, "
                          f"preparing new build for release verification", flush=True)
                    
                    new_release_candidate_version = release_candidate_android_sdk_build.version.bump_release_candidate()
                    print(f"New Android SDK release candidate version: {new_release_candidate_version}", flush=True)
                    
                    pipeline_helpers.trigger_and_wait_for_update_camerakit_version(
                        PATH_ANDROID_REPO,
                        JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE,
                        sdk_release_branch,
                        'HEAD',
                        pipeline_helpers.release_branch_prefix(),
                        new_release_candidate_version,
                        slack_channel
                    )
                    
                    android_publish_job_label = f"{PATH_ANDROID_REPO}@{sdk_release_branch}//{JOB_CAMERAKIT_SDK_ANDROID_PUBLISH}"
                    android_publish_pipeline_id = pipeline_helpers.trigger_pipeline(android_publish_job_label, {"test_mode": is_test_mode})
                    
                    pipeline_helpers.watch_pipeline(android_publish_job_label, android_publish_pipeline_id)
              
                    print(f"About to get Android SDK build from pipeline_id: {android_publish_pipeline_id}", flush=True)
                                       
                    with state.update_state():
                        state.step5.releaseCandidateAndroidSdkBuild = pipeline_helpers.get_android_sdk_from_pipeline_id(android_publish_pipeline_id)
                    
                    return True
                return False

            def build_ios_if_needed():
                ios_head_commit_sha = ci_git_helpers.get_head_commit_sha(PATH_IOS_REPO, sdk_release_branch)
                ios_build_commit_sha = release_candidate_ios_sdk_build.commit
                
                print(f"iOS SDK HEAD commit: {ios_head_commit_sha}, build commit: {ios_build_commit_sha}")
                
                if ios_head_commit_sha != ios_build_commit_sha:
                    print(f"It appears that {PATH_IOS_REPO} repository {sdk_release_branch} branch has new commits, "
                          f"preparing new build for release verification")
                    
                    ios_publish_job_label = f"{PATH_IOS_REPO}@{sdk_release_branch}//{JOB_CAMERAKIT_SDK_IOS_PUBLISH}"

                    pipeline_id = pipeline_helpers.trigger_pipeline(ios_publish_job_label, {"test_mode": is_test_mode})
                    pipeline_helpers.watch_pipeline(ios_publish_job_label, pipeline_id)
                    
                    with state.update_state():
                        state.step5.releaseCandidateIosSdkBuild = pipeline_helpers.get_ios_sdk_from_pipeline_id(pipeline_id, release_version)
                    
                    return True
                return False
         
            with ThreadPoolExecutor(max_workers=2) as executor:
                android_future = executor.submit(build_android_if_needed)
                ios_future = executor.submit(build_ios_if_needed)
                
                # Wait for both to complete
                android_built = android_future.result()
                ios_built = ios_future.result()
                
                with state.read_state():
                  release_verification_complete = state.step5.releaseVerificationComplete
                  updated_android_sdk_build = state.step5.releaseCandidateAndroidSdkBuild
                  updated_ios_sdk_build = state.step5.releaseCandidateIosSdkBuild
                  
                print(f"release_verification_complete: {release_verification_complete}, android_built: {android_built}, ios_built: {ios_built}", flush=True)

                if not release_verification_complete:
                    if android_built or ios_built:
                        print(f"Either Android ({android_built}) or iOS ({ios_built}) was built, creating SDK distribution PR", flush=True)
                        
                        sdk_distribution_release_branch = ci_git_helpers.camera_kit_release_branch_for(release_version)
                        print(f"Using SDK distribution release branch: {sdk_distribution_release_branch}", flush=True)

                        print(f"Updating camera kit SDK distribution with new builds - Android: {updated_android_sdk_build}, iOS: {updated_ios_sdk_build}", flush=True)
                        pr = ci_git_helpers.update_camera_kit_sdk_distribution_with_new_sdk_builds(
                            sdk_distribution_release_branch,
                            None,
                            release_version,
                            updated_android_sdk_build,
                            updated_ios_sdk_build
                        )

                        print(f"Created PR: {pr.get('url', 'No URL available')}", flush=True)
                        pr["comment"] = COMMENT_PR_FIRE

                        print(f"Notifying Slack and processing PR in channel: {slack_channel}", flush=True)
                        pipeline_helpers.notify_slack_and_process_pr(
                            pr,
                            slack_channel
                        )
                        print("✅ Successfully notified Slack and processed PR", flush=True)

            print(f"[Trigger On Demand Release Builds] Sleeping for {STATUS_CHECK_SLEEP_SECONDS} seconds")
            time.sleep(STATUS_CHECK_SLEEP_SECONDS)

    def _wait_for_release_verification(self, state: PipelineState):      
        while True:
            with state.read_state():
                release_verification_issue_key = state.step1.releaseVerificationIssueKey
                slack_channel = state.step1.releaseCoordinationSlackChannel
                release_version = state.step1.releaseVersion
                msg_timestamp = state.step5.releaseVerificationPromptMessageTimestamp
        
                release_verification_issue_key = state.step1.releaseVerificationIssueKey

            issue = remote_services.look_up_jira_issue(release_verification_issue_key, 'status')
            status_name = issue['fields']['status']['name']

            print(f"Issue [{release_verification_issue_key}] status: {status_name}", flush=True)

            if status_name == 'Complete' or status_name == 'Done':

                if msg_timestamp is None:
                    msg_timestamp = remote_services.notify_on_slack(
                        slack_channel,
                        f"Release candidate for {release_version} appears to be verified in "
                        f"{remote_services.jira_issue_url_from(release_verification_issue_key)}, react with :lgtm: to continue with the release"
                    )

                    with state.update_state():
                        state.step5.releaseVerificationPromptMessageTimestamp = msg_timestamp

                remote_services.wait_for_slack_message_verification(
                    slack_channel,
                    msg_timestamp
                )

                remote_services.create_jira_issue_comment(
                    release_verification_issue_key,
                    f"Release candidate for {release_version} appears to be verified, "
                    f"proceeding on to the final release steps in: {os.environ.get('CI_PIPELINE_URL')}"
                )

                with state.update_state():
                    state.step5.releaseVerificationComplete = True
                break
            else:
                print(f"Waiting for issue [{release_verification_issue_key}] to be marked as Complete or Done...", flush=True)
            print(f"[Wait For Release Verification] Sleeping for {STATUS_CHECK_SLEEP_SECONDS} seconds")
            time.sleep(STATUS_CHECK_SLEEP_SECONDS)

    def _trigger_distribution_build(self, state: PipelineState):
           while True:
                with state.read_state():
                    if state.step5.releaseVerificationComplete:
                        break

                    jira_issue_key = state.step1.releaseVerificationIssueKey
                    release_version = state.step1.releaseVersion
                    next_release_branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                    slack_channel = state.step1.releaseCoordinationSlackChannel
                    release_candidate_binary_builds = (
                        state.step5.releaseCandidateBinaryBuilds 
                        if state.step5.releaseCandidateBinaryBuilds 
                        else state.step4.releaseCandidateBinaryBuilds
                    )

                head_commit_sha = ci_git_helpers.get_head_commit_sha(PATH_CAMERAKIT_DISTRIBUTION_REPO, next_release_branch)
                camera_kit_distribution_build = release_candidate_binary_builds.get(KEY_CAMERAKIT_DISTRIBUTION_BUILD)

                if camera_kit_distribution_build is None:
                    raise Exception(f"Missing Camera Kit distribution build in RC builds map: {release_candidate_binary_builds}")

                build_commit_sha = camera_kit_distribution_build.commit
                print(f"Camera Kit distribution build commit: {build_commit_sha}, head commit: {head_commit_sha}", flush=True)

                if build_commit_sha != head_commit_sha:
                    print(
                        f"It appears that {PATH_CAMERAKIT_DISTRIBUTION_REPO} repository "
                        f"{next_release_branch} branch has new commits, "
                        f"preparing new build for release verification",
                        flush=True
                    )

                    new_rc_builds = pipeline_helpers.trigger_and_wait_for_distribution_build(
                        release_version,
                        head_commit_sha,
                        slack_channel
                    )   

                    with state.update_state():
                        state.step5.releaseCandidateBinaryBuilds = new_rc_builds

                    with state.read_state():
                        if state.step5.releaseVerificationComplete:
                            break
                        else:
                            message = ci_git_helpers.create_camera_kit_sdk_distribution_release_candidate_message(
                                release_version,
                                state.step5.releaseCandidateBinaryBuilds,
                                os.environ.get("CI_PIPELINE_URL")
                            )
                            remote_services.create_jira_issue_comment(jira_issue_key, message)
                else:                    
                    time.sleep(STATUS_CHECK_SLEEP_SECONDS)

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return not state.step5.releaseVerificationComplete

    def get_next_step_config(self):
        return InProcessStepConfig(FinalAndroidSDKBuildStep)