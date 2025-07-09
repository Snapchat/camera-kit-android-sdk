import datetime
import subprocess
import os
import tempfile
import time
from typing import Optional
from pipeline_app.state import ReleaseScope, Version, is_test_mode, SdkBuild, BinaryBuild
import json
from pipeline_app.constants import *
from pipeline_app import remote_services

workspace = os.environ.get("CI_HOME")

def run_shell(command: str, capture_output: bool = True):
    print(f"> {command}")
    try:
        result = subprocess.run(command, shell=True, check=True, capture_output=capture_output, text=True)
        if capture_output:
            print(f"Command output: {result.stdout}")
            if result.stderr:
                print(f"Command stderr: {result.stderr}")
    except subprocess.CalledProcessError as e:
        print(f"[WARN] Command failed (ignored): {e}")
        print(f"Command stdout: {e.stdout}")
        print(f"Command stderr: {e.stderr}")
        raise e

def run_git(cmd: str, cwd: Optional[str] = None):
    print(f"> {cmd}")
    subprocess.run(cmd, shell=True, cwd=cwd, check=True)

def git_checkout_branch(repo_url: str, branch: str) -> str:
    """
    Replicates Jenkins `git branch: ..., url: ...` behavior.    
    Returns the path to the workspace.
    """
    workspace = os.environ.get("CI_HOME") or tempfile.mkdtemp(prefix="ci_workspace_")
    workspace = tempfile.mkdtemp(prefix="ci_workspace_")
    print(f"[INFO] Using temporary workspace: {workspace}")

    # Clone repo without checkout
    run_git(f"git clone --no-checkout {repo_url} {workspace}")

    # Fetch the remote branch
    run_git(f"git fetch origin {branch}", cwd=workspace)

    # Resolve exact commit SHA
    result = subprocess.run(
        f"git rev-parse origin/{branch}",
        shell=True,
        cwd=workspace,
        text=True,
        capture_output=True,
        check=True
    )
    commit_sha = result.stdout.strip()

    # Checkout the resolved commit
    run_git(f"git checkout -f {commit_sha}", cwd=workspace)

    # Create or reset branch to this commit
    run_git(f"git checkout -B {branch} {commit_sha}", cwd=workspace)

    print(f"✔️ Checked out branch '{branch}' at commit {commit_sha} in workspace: {workspace}")
    return workspace

def add_test_branch_prefix_if_needed(branch: str) -> str:
    return f"{TEST_BRANCH_PREFIX}{branch}" if is_test_mode() else branch

def get_head_commit_sha(repo: str, branch: str) -> str:
    result = subprocess.run(
        f"gh api /repos/{repo}/git/ref/heads/{branch}",
        shell=True,
        capture_output=True,
        text=True,
        check=True
    )
    data = json.loads(result.stdout)
    return data["object"]["sha"]

def delete_branch_if_exists(repo: str, branch: str):
    try:
        subprocess.run(f"gh api --method DELETE /repos/{repo}/git/refs/heads/{branch}", shell=True, check=True)
        print(f"✔️ Successfully deleted branch '{branch}' in repo '{repo}'")
    except subprocess.CalledProcessError:
        # Check if the error is because branch doesn't exist (which is OK)
        # or if it's a different error that should be raised
        result = subprocess.run(f"gh api --method GET /repos/{repo}/git/refs/heads/{branch}", 
                       shell=True, capture_output=True)
        if result.returncode == 0:
            # Branch exists but delete failed for another reason - raise the error
            raise
        # Branch doesn't exist, which is OK
        print(f"[WARN] Branch '{branch}' does not exist in repo '{repo}', nothing to delete.")
        pass

def create_branch(repo: str, branch: str, sha: str):
    run_shell(
        f"GH_REPO={repo} gh api --method POST /repos/{repo}/git/refs "
        f"-f ref='refs/heads/{branch}' "
        f"-f sha='{sha}'"
    )

def  create_or_reset_test_branch(repo: str, new_branch: str):
    base_branch = new_branch.replace(TEST_BRANCH_PREFIX, "")
    delete_branch_if_exists(repo, new_branch)
    sha = get_head_commit_sha(repo, base_branch)
    create_branch(repo, new_branch, sha)

def create_or_reset_test_branch_if_needed(releaseScope: ReleaseScope, releaseVersion: Version):
        if is_test_mode():
            distribution_branch_name = (camera_kit_release_branch_for(releaseVersion)
                    if releaseScope == ReleaseScope.PATCH
                    else add_test_branch_prefix_if_needed(BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN))

            create_or_reset_test_branch(PATH_CAMERAKIT_DISTRIBUTION_REPO, distribution_branch_name)

            mobile_branch_name = (camera_kit_release_branch_for(releaseVersion)
                    if releaseScope == ReleaseScope.PATCH
                    else add_test_branch_prefix_if_needed(BRANCH_SDK_REPO_MAIN))

            create_or_reset_test_branch(PATH_ANDROID_REPO, mobile_branch_name)
            create_or_reset_test_branch(PATH_IOS_REPO, mobile_branch_name)

def camera_kit_release_branch_for(version: Version):
    return add_test_branch_prefix_if_needed(f"release/{version.major}.{version.minor}.x")

def mark_pr_ready(repo: str, pr_number: int):
    try:
        # Check if PR is already ready (not draft)
        result = subprocess.run(
            f"gh pr view {pr_number} --repo {repo} --json isDraft",
            shell=True,
            text=True,
            capture_output=True,
            check=True
        )
        
        pr_data = json.loads(result.stdout.strip())
        if not pr_data["isDraft"]:
            print(f"✔️ PR #{pr_number} in {repo} is already ready for review")
            return
        
        # Mark PR as ready using gh CLI
        subprocess.run(
            f"gh pr ready {pr_number} --repo {repo}",
            shell=True,
            check=True
        )
        
        print(f"✔️ PR #{pr_number} marked as ready for review in {repo}")
        
    except subprocess.CalledProcessError as e:
        raise Exception(f"Failed to mark PR #{pr_number} as ready for review in {repo}: {e}")
   
def wait_for_pr_to_be_approved_or_closed(repo: str, pr_number: int) -> bool:
    """
    Waits for a PR to be approved or closed/merged.
    Returns True if approved, False if closed/merged without approval.
    """
    approved = False
    
    while True:
        try:
            result = subprocess.run(
                f"gh pr view {pr_number} --repo {repo} --json reviewDecision,state",
                shell=True,
                text=True,
                capture_output=True,
                check=True
            )
            
            pr_data = json.loads(result.stdout.strip())
            
            state = pr_data["state"]
            review_decision = pr_data.get("reviewDecision")
            closed_or_merged = state in ["CLOSED", "MERGED"]
            approved = review_decision == "APPROVED"

            if closed_or_merged or approved:
                if approved:
                    print(f"✔️ PR #{pr_number} in {repo} has been approved")
                else:
                    print(f"✔️ PR #{pr_number} in {repo} has been closed/merged")
                break
            else:
                print(f"Waiting for PR #{pr_number} in {repo} to be approved or closed...", flush=True)
                time.sleep(STATUS_CHECK_SLEEP_SECONDS)
                
        except Exception as error:
            print(f"Checking PR #{pr_number} status in repo {repo} failed due to: {error}", flush=True)
            time.sleep(STATUS_CHECK_SLEEP_SECONDS)
    
    return approved

def comment_on_pr_when_approved_and_wait_to_close(repo: str, pr_number: int, comment: str):
    if wait_for_pr_to_be_approved_or_closed(repo, pr_number):
        try:
            subprocess.run(
                f"gh pr comment {pr_number} --repo {repo} --body \"{comment}\"",
                shell=True,
                text=True,
                capture_output=True,
                check=True
            )
            print(f"✔️ Comment posted on PR #{pr_number} in {repo}: {comment}", flush=True)
        except subprocess.CalledProcessError as e:
            raise Exception(f"Failed to post comment on PR #{pr_number} in {repo}: {e}")

        wait_for_pr_to_close(repo, pr_number)
    
def wait_for_pr_to_close(repo: str, pr_number: int):
    while True:
        result = subprocess.run(
            f"gh pr view {pr_number} --repo {repo} --json state",
            shell=True,
            text=True,
            capture_output=True,
            check=True
        )
        pr_data = json.loads(result.stdout.strip())
        print(f"PR #{pr_number} in {repo} state: {pr_data['state']}", flush=True)
        if pr_data["state"] in ["CLOSED", "MERGED"]:
            print(f"✔️ PR #{pr_number} in {repo} is now closed.", flush=True)
            break
        print("Waiting for PR to be closed...", flush=True)
        time.sleep(STATUS_CHECK_SLEEP_SECONDS)

def update_camera_kit_sdk_distribution_with_new_sdk_builds(
    base_branch: str,
    new_branch: Optional[str],
    new_version: Version,
    new_android_sdk_build: Optional[SdkBuild],
    new_ios_sdk_build: Optional[SdkBuild]
) -> dict:
    print("🚀 [START] update_camera_kit_sdk_distribution_with_new_sdk_builds")
    print(f"  - Base Branch: {base_branch}")
    print(f"  - New Branch: {new_branch}")
    print(f"  - New Version: {new_version}")
    print(f"  - Android Build: {new_android_sdk_build}")
    print(f"  - iOS Build: {new_ios_sdk_build}")

    # Clone and setup repo
    print("  - Checking out base branch...", flush=True)
    workspace = git_checkout_branch(
        f"git@{HOST_SNAP_GHE}:{PATH_CAMERAKIT_DISTRIBUTION_REPO}.git",
        base_branch
    )
    
    os.chdir(workspace)
    
    # Create new branch if specified
    if new_branch:
        run_git(f"git checkout -B {new_branch} && git push -f origin {new_branch}", cwd=workspace)

    # Create update branch
    update_branch = f"update/{new_version}/{int(time.time() * 1000)}"
    run_git(f"git checkout -B {update_branch}", cwd=workspace)

    # Update version file
    print(f"  - Updating VERSION file to: {new_version}", flush=True)
    run_shell(f"echo \"{new_version}\" > VERSION")
    run_git(
        f"git add VERSION && git commit -m \"[Build] Bump version to {new_version}\" || echo \"No changes to commit\"",
        cwd=workspace
    )

    result = subprocess.run(
        "git rev-parse HEAD",
        shell=True,
        capture_output=True,
        text=True,
        check=True
    )
    print(f"  - Current commit on branch '{update_branch}': {result.stdout.strip()}", flush=True)
    
    # Update SDKs
    if new_android_sdk_build:
        print("  - Updating Android SDK...", flush=True)
        run_shell(
            f".buildscript/android/update.sh "
            f"-v {new_android_sdk_build.version} "
            f"-r {new_android_sdk_build.commit} "
            f"-b {new_android_sdk_build.build_number} "
            f"--no-branch"
        )
        print("  - Android SDK update script finished.", flush=True)

    if new_ios_sdk_build:
        print("  - Updating iOS SDK...", flush=True)
        run_shell(
            f".buildscript/ios/update.sh "
            f"-r {new_ios_sdk_build.commit} "
            f"-b {new_ios_sdk_build.build_number} "
            f"--no-branch",
            capture_output=False 
        )
        print("  - iOS SDK update script finished.", flush=True)

    # Push changes
    print(f"  - Pushing changes to remote branch: {update_branch}", flush=True)
    run_git(f"git push origin {update_branch}", cwd=workspace)

    # Create PR
    repo = f"{HOST_SNAP_GHE}/{PATH_CAMERAKIT_DISTRIBUTION_REPO}"
    pr_title = f"[Build] Update SDKs for the {new_version} version"

    pr_result = None
    for i in range(COMMAND_RETRY_MAX_COUNT):
        try:
            print(f"  - Attempting to create PR (attempt {i+1}/{COMMAND_RETRY_MAX_COUNT})...", flush=True)
            pr_result = subprocess.run(
                f"gh pr create "
                f"--title \"{pr_title}\" "
                f"--body \"This PR updates the SDKs to the latest builds targeting the "
                f"version: {new_version}. "
                f"\\n\\nPlease refer to the individual commit messages to see a list of included "
                f"changes in each SDK.\" "
                f"--base {new_branch if new_branch else base_branch} "
                f"--head {update_branch} "
                f"--repo {repo}",
                shell=True,
                capture_output=True,
                text=True,
                check=True
            ).stdout.strip()
            print(f"  - Successfully created PR: {pr_result}", flush=True)
            break
        except subprocess.CalledProcessError as e:
            print(f"  - [WARN] PR creation attempt {i+1} failed. Retrying... Error: {e.stderr}", flush=True)
            if i == COMMAND_RETRY_MAX_COUNT - 1:
                print("  - [ERROR] All PR creation attempts failed.", flush=True)
                raise  # Re-raise the final exception
            continue

    if not pr_result:
        raise Exception("Failed to create pull request after multiple retries.")

    pr_number = pr_result.split('/')[-1]
    pr_html_url = f"https://{repo}/pull/{pr_number}"
    print(f"  - Parsed PR Number: {pr_number}", flush=True)
    print(f"  - Parsed PR URL: {pr_html_url}", flush=True)

    print("🏁 [END] update_camera_kit_sdk_distribution_with_new_sdk_builds", flush=True)
    return {
        "repo": repo,
        "number": pr_number,
        "title": pr_title,
        "html_url": pr_html_url
    }

def get_ios_sdk_version(repo_url: str, branch: str) -> Version:
    """
    Get the version from the iOS repo's VERSION file.
    Returns the version string.
    """
    workspace = git_checkout_branch(f"git@{HOST_SNAP_GHE}:{repo_url}.git", branch)
    
    version_file = os.path.join(workspace, "SDKs/CameraKit/CameraKit/VERSION")
    
    if not os.path.exists(version_file):
        raise FileNotFoundError(f"Version file not found at {version_file}")
        
    with open(version_file, 'r') as f:
        version = f.readline().strip()
        
    return Version.from_string(version)

def get_android_sdk_version(repo_url: str, branch: str) -> Version:
    """
    Get the version from the Android repo's VERSION file.
    Returns the version string.
    """
    workspace = git_checkout_branch(f"git@{HOST_SNAP_GHE}:{repo_url}.git", branch)
    
    version_file = os.path.join(workspace, "core/ext.gradle")
    
    if not os.path.exists(version_file):
        raise FileNotFoundError(f"Version file not found at {version_file}")
        
    with open(version_file, 'r') as f:
        content = f.read()
        import re
        match = re.search(r"def version = '([^']+)'", content)
        if not match:
            raise ValueError(f"Could not find version in {version_file}")
        version = match.group(1)
        
    return Version.from_string(version)

def update_camera_kit_sdk_distribution_changelog_for_release(release_version: Version, slack_channel: str):
    release_branch = camera_kit_release_branch_for(release_version)
    
    try:
        # Checkout the release branch
        workspace = git_checkout_branch(
            f"git@{HOST_SNAP_GHE}:{PATH_CAMERAKIT_DISTRIBUTION_REPO}.git", 
            release_branch
        )
        
        # Create update branch
        update_branch = f"update/{release_version}/changelog/{int(time.time() * 1000)}"
        
        # Switch to update branch
        subprocess.run([
            "git", "checkout", "-B", update_branch
        ], cwd=workspace, check=True)
        
        # Read changelog content
        changelog_path = os.path.join(workspace, FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG)
        
        with open(changelog_path, 'r') as f:
            changelog_content = f.read()
        
        # Update changelog content
        date_format = datetime.datetime.now().strftime("%Y-%m-%d")
        unreleased_section_header = '<a name="unreleased"></a>\n## [Unreleased]'
        new_content = changelog_content.replace(
            unreleased_section_header,
            unreleased_section_header +
            f"\n\n<a name=\"{release_version}\"></a>" +
            f"\n## [{release_version}] - {date_format}"
        )
        
        # Write updated changelog
        with open(changelog_path, 'w') as f:
            f.write(new_content)
        
        # Commit and push changes
        update_title = f"[Doc] Update CHANGELOG for {release_version} release"
        
        subprocess.run([
            "git", "add", FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG
        ], cwd=workspace, check=True)
        
        subprocess.run([
            "git", "commit", "-m", update_title
        ], cwd=workspace, check=True)
        
        subprocess.run([
            "git", "push", "origin", update_branch
        ], cwd=workspace, check=True)
        
        # Create PR
        repo = f"{HOST_SNAP_GHE}/{PATH_CAMERAKIT_DISTRIBUTION_REPO}"
        pr_title = update_title
        pr_body = (f"This PR updates the CHANGELOG targeting the "
                    f"{release_version} release. Please double check if all "
                    f"items look good and add or remove any that might be needed for this "
                    f"release.")
        
        pr_result = subprocess.run([
            "gh", "pr", "create",
            "--title", pr_title,
            "--body", pr_body,
            "--base", release_branch,
            "--head", update_branch,
            "--repo", repo
        ], cwd=workspace, capture_output=True, text=True, check=True)
        
        print(f"PR result: {pr_result}", flush=True)

        pr_output = pr_result.stdout.strip()
        pr_number = pr_output.split('/')[-1]
        pr_html_url = f"https://{repo}/pull/{pr_number}"
        
        # Notify Slack
        remote_services.notify_on_slack(slack_channel, f"{pr_title}: {pr_html_url}")
        
        # Comment on PR and wait for approval/close
        # Cooling this PR as we want the CHANGELOG update to be cherry-picked downstream,
        # conflicts will need to be resolved manually.
        comment_on_pr_when_approved_and_wait_to_close(
            PATH_CAMERAKIT_DISTRIBUTION_REPO, 
            int(pr_number), 
            COMMENT_PR_COOL
        )
        
        return True
        
    except Exception as error:
        error_msg = f"Failure while updating CHANGELOG for the {release_version} release due to: {error}"
        remote_services.notify_on_slack(slack_channel, error_msg)
        raise Exception(error_msg)

def create_camera_kit_sdk_distribution_release(
        release_version: Version,
        android_sdk_build: SdkBuild,
        ios_sdk_build: SdkBuild,
        binary_builds: dict[str, BinaryBuild],
        slack_channel: str,
) -> str:

    try:
        sdk_distribution_build = binary_builds.get(KEY_CAMERAKIT_DISTRIBUTION_BUILD)
        android_sample_build = binary_builds.get(KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID)
        ios_sample_build = binary_builds.get(KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS)

        if sdk_distribution_build is None:
            raise Exception(
                f"Expected the [{release_version}] "
                f"SDK distribution release build to not be null!"
            )
        
        sdk_distribution_zip_filename = f"camerakit-distribution-{release_version}.zip"

        print(f"[DEBUG] Downloading SDK distribution build from: {sdk_distribution_build.downloadUri}")
        print(f"[DEBUG] Target filename: {sdk_distribution_zip_filename}")
        print(f"[DEBUG] Current working directory: {os.getcwd()}")
        
        # List files before download
        print(f"[DEBUG] Files in current directory before download:")
        for file in os.listdir("."):
            print(f"  - {file}")

        run_shell(f"gsutil cp '{sdk_distribution_build.downloadUri}' {sdk_distribution_zip_filename}")

        # Get absolute path to the downloaded file
        absolute_zip_path = os.path.abspath(sdk_distribution_zip_filename)
        print(f"[DEBUG] Downloaded file absolute path: {absolute_zip_path}")

        release_title = str(release_version)
        release_tag_name = release_title
        release_target_branch = camera_kit_release_branch_for(release_version)

        workspace = git_checkout_branch(
            f"git@{HOST_SNAP_GHE}:{PATH_CAMERAKIT_DISTRIBUTION_REPO}.git",
            release_target_branch
        )

        changelog_content = ""
        
        changelog_path = os.path.join(workspace, FILE_NAME_CAMERAKIT_DISTRIBUTION_CHANGELOG)
        with open(changelog_path, "r", encoding="utf-8") as f:
            changelog_content = f.read()

        release_title_link = f"<a name=\"{release_title}\"></a>"
        changelog_release_content = None
        release_title_link_index = changelog_content.find(release_title_link)
        
        if release_title_link_index != -1:
            changelog_release_content = changelog_content[
                release_title_link_index:
            ].replace(release_title_link, "").lstrip()
            # Split up until previous release title link
            changelog_release_content = changelog_release_content.split("<a")[0]
        
        if changelog_release_content is not None:
            release_content_lines = changelog_release_content.splitlines()
            changelog_release_content = "\n".join(
                release_content_lines[2:]  # Skip first 2 lines
            )
        else:
            changelog_release_content = "No notable changes recorded."

        release_notes = f"## *Public*\n{changelog_release_content}"
        release_notes += "\n\n## *Internal*"
        release_notes += "\n### SDKs"
        release_notes += app_size_info_content_for('Android', android_sdk_build)
        release_notes += app_size_info_content_for('iOS', ios_sdk_build)
        release_notes += "\n### Samples"
        release_notes += sample_app_info_content_for('Android', android_sample_build)
        release_notes += sample_app_info_content_for('iOS', ios_sample_build)

        repo = f"{HOST_SNAP_GHE}/{PATH_CAMERAKIT_DISTRIBUTION_REPO}"

        # Escape single quotes in release notes
        escaped_release_notes = release_notes.replace("'", "\\'")
        
        release_command = [
            "gh", "release", "create", release_tag_name,
            "--target", release_target_branch,
            "--title", release_title,
            "--notes", escaped_release_notes,
            "--repo", repo,
        ]
        if is_test_mode():
            release_command.append("--draft")
        
        # Use absolute path since gh command runs in workspace directory
        release_command.append(absolute_zip_path)
        
        try:
            result = subprocess.run(
                release_command,
                cwd=workspace, capture_output=True, text=True, check=True
            )
            release_github_url = result.stdout.strip()


            print(f"Created {release_title} release: {release_github_url}")

            return release_github_url
        except subprocess.CalledProcessError as e:
            print(f"gh release create failed. Command: {' '.join(release_command)}")
            print(f"Return code: {e.returncode}")
            print(f"Stdout: {e.stdout}")
            print(f"Stderr: {e.stderr}")
            raise
        
    except Exception as error:
        error_msg = f"Failure while creating SDK distribution {release_version} release due to: {error}"
        remote_services.notify_on_slack(slack_channel, error_msg)
        raise Exception(error_msg)
    
def sample_app_info_content_for(platform: str, binary_build: BinaryBuild) -> str:
    content = ""
    
    content += f"\n- **{platform}**:"
    content += f"\n\t- Download: {binary_build.htmlUrl}"
    
    return content

def app_size_info_content_for(platform: str, sdk_build: SdkBuild) -> str:
    content = ""
    
    content += f"\n- **{platform}**:"
    content += f"\n\t- Build:"
    content += f"\n\t\t- Branch: {sdk_build.branch}"
    content += f"\n\t\t- Commit: {sdk_build.commit}"
    content += f"\n\t\t- Job: {sdk_build.get_build_url()}"
    
    short_commit_sha = sdk_build.commit[:10]
    size_info = query_camera_kit_sdk_size(
        platform.lower(), sdk_build.branch, short_commit_sha
    )
    
    if size_info is not None:
        install_size_bytes = size_info.get('install_size', 0) or 0
        download_size_bytes = size_info.get('download_size', 0) or 0
        app_size_report_url = (
            "https://looker.sc-corp.net/dashboards/3515"
            f"?App+Name=camerakit"
            f"&App+Platform={platform.lower()}"
            f"&Variant=release"
            f"&Commit+Sha={short_commit_sha}"
        )
        content += f"\n\t- Size:"
        content += f"\n\t\t- Install: {install_size_bytes} bytes"
        content += f"\n\t\t- Download: {download_size_bytes} bytes"
        content += f"\n\t\t- Report: {app_size_report_url}"
    
    return content


def query_camera_kit_sdk_size(platform: str, branch: str, commit: str) -> Optional[dict]:
    """
    Query BigQuery for CameraKit SDK size information.
    
    Args:
        platform: The platform (e.g., 'android', 'ios')
        branch: The git branch
        commit: The commit SHA
        
    Returns:
        Dictionary with download_size and install_size, or None if not found
    """
    bq_query = (
        f"SELECT app_size.download_size, app_size.install_size "
        f"FROM `ci-metrics.app_size.app_size` as app_size "
        f"WHERE app_size.app_name=\"CameraKit\" "
        f"and app_size.platform=\"{platform}\" "
        f"and app_size.build_info.commit_sha=\"{commit}\" "
        f"and app_size.build_info.commit_branch=\"{branch}\""
    )
    
    cmd = [
        "bq", "query", "--nouse_legacy_sql", "--format=prettyjson",
        "--project_id=everybodysaydance", bq_query
    ]

    print(f"Running BQ query: {cmd}")
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=True
        )
        
        bq_result = result.stdout.strip()
        rows = json.loads(bq_result)
        
        if rows and len(rows) > 0:
            return rows[0]
        else:
            return None
            
    except (subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"Failed to parse BQ result as a List: {error}")
        return None

def update_snapdocs_version(slack_channel: str, version: Version):
    """
    Update SnapDocs version by creating a branch, updating API reference links,
    committing changes, and creating a PR.
    
    Args:
        slack_channel: Slack channel for notifications
        version: Version to update to
    """
    base_branch = BRANCH_SNAP_DOCS_REPO_MAIN
    
    # Checkout the base branch
    workspace = git_checkout_branch(
        f"git@{HOST_SNAP_GHE}:{PATH_SNAP_DOCS_REPO}.git",
        base_branch
    )
    
    # Create update branch
    update_branch = f"camerakit/update-api-ref/{version}/{int(time.time() * 1000)}"
    
    # Switch to update branch
    subprocess.run([
        "git", "checkout", "-B", update_branch
    ], cwd=workspace, check=True)
    
    camera_kit_api_reference_path = "reference/CameraKit"
    files_to_update = ['sidebars/api-sidebar.js', 'docs/api/home.mdx']
    
    # Update each file for both platforms
    for file_to_update in files_to_update:
        file_path = os.path.join(workspace, file_to_update)
        
        for platform in ['android', 'ios']:
            # Create sed pattern to replace version numbers
            pattern = f"{camera_kit_api_reference_path}/{platform}/[0-9]+\\.[0-9]+\\.[0-9]+/"
            replacement = f"{camera_kit_api_reference_path}/{platform}/{version}/"
            
            # Run sed command to update the file
            subprocess.run([
                "sed", "-i.bak", 
                f"s#{pattern}#{replacement}#g",
                file_path
            ], cwd=workspace, check=True)
        
        # Remove backup file
        os.remove(f"{file_path}.bak")
        
        # Add file to git
        subprocess.run([
            "git", "add", file_to_update
        ], cwd=workspace, check=True)
    
    # Commit changes
    commit_message = f"[CameraKit] Update API reference doc links to {version}"
    
    try:
        subprocess.run([
            "git", "commit", "-m", commit_message
        ], cwd=workspace, check=True)
    except subprocess.CalledProcessError as e:
        if e.returncode != 0:
            print(f"Attempting to commit resulted in exit code: {e.returncode}, "
                  f"most likely due to nothing to commit")
            return
        else:
            raise
    
    # Push the branch
    subprocess.run([
        "git", "push", "origin", update_branch
    ], cwd=workspace, check=True)
    
    # Create PR
    repo = f"{HOST_SNAP_GHE}/{PATH_SNAP_DOCS_REPO}"
    pr_title = commit_message
    pr_body = (f"This PR updates the CameraKit API reference doc links to track the "
              f"{version} version resources.\n"
              f"API reference docs synced in: "
              f"{HOST_SNAPCI_BUILDER}/cp/pipelines/p/{os.environ.get('CI_PIPELINE_ID')}")
    
    pr_result = subprocess.run([
        "gh", "pr", "create",
        "--title", pr_title,
        "--body", pr_body,
        "--base", base_branch,
        "--head", update_branch,
        "--repo", repo
    ], cwd=workspace, capture_output=True, text=True, check=True)
    
    pr_result_text = pr_result.stdout.strip()
    pr_number = pr_result_text.split('/')[-1]
    pr_html_url = f"https://{repo}/pull/{pr_number}"
    
    # Notify on Slack
    remote_services.notify_on_slack(slack_channel, f"{pr_title}: {pr_html_url}")
