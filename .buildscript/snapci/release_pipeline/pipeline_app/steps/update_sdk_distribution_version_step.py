from concurrent.futures import ThreadPoolExecutor
from pipeline_app.base_step import PipelineStep, InProcessStepConfig
from pipeline_app.state import PipelineState, ReleaseScope
from pipeline_app.constants import *
from pipeline_app import ci_git_helpers, pipeline_helpers, remote_services
from pipeline_app.steps.release_builds_step import ReleaseBuildsStep

class UpdateSdkDistributionVersionStep(PipelineStep):
    def execute(self, state: PipelineState):
        with state.update_state():
            if state.step4.releaseCandidateSdkBuildsCommitSha is None:
                next_release_branch = ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                jobs = [{
                    "baseBranch": ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN) if state.step1.releaseScope == ReleaseScope.MINOR else next_release_branch,
                    "newBranch": next_release_branch if state.step1.releaseScope == ReleaseScope.MINOR else None,
                    "newVersion": state.step1.releaseVersion,
                    "newAndroidSdkBuild": state.step3.androidReleaseCandidateSdkBuild,
                    "newIosSdkBuild": state.step3.iOSReleaseCandidateSdkBuild,
                    "newPrComment": COMMENT_PR_FIRE
                }]

                if state.step1.releaseScope == ReleaseScope.MINOR:
                    jobs.append({
                        "baseBranch": ci_git_helpers.add_test_branch_prefix_if_needed(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN),
                        "newBranch": None,
                        "newVersion": state.step2.developmentVersion,
                        "newAndroidSdkBuild": state.step3.androidDevSdkBuild,
                        "newIosSdkBuild": state.step3.iOSDevSdkBuild,
                        "newPrComment": COMMENT_PR_COOL
                    })

                # Create update PRs in serial due to shared workspace
                prs = []
                for job in jobs:
                    pr = ci_git_helpers.update_camera_kit_sdk_distribution_with_new_sdk_builds(
                        job["baseBranch"],
                        job["newBranch"],
                        job["newVersion"],
                        job["newAndroidSdkBuild"],
                        job["newIosSdkBuild"]
                    )
                    pr["comment"] = job["newPrComment"]
                    prs.append(pr)

                # Process PRs in parallel
                with ThreadPoolExecutor(max_workers=len(prs)) as executor:
                    futures = [
                        executor.submit(pipeline_helpers.notify_slack_and_process_pr, pr, state.step1.releaseCoordinationSlackChannel)
                        for pr in prs
                    ]
                    
                    # Wait for all PRs to complete - crash on first exception
                    for future in futures:
                        pr_number = future.result()  # This will raise exception if the future failed
                        print(f"PR #{pr_number} completed")

                state.step4.releaseCandidateSdkBuildsCommitSha = ci_git_helpers.get_head_commit_sha(
                    PATH_CAMERAKIT_DISTRIBUTION_REPO,
                    ci_git_helpers.camera_kit_release_branch_for(state.step1.releaseVersion)
                )

    def should_execute(self, state: PipelineState) -> bool:
        with state.read_state():
            return state.step4.releaseCandidateSdkBuildsCommitSha is None

    def get_next_step_config(self):
        return InProcessStepConfig(ReleaseBuildsStep) 