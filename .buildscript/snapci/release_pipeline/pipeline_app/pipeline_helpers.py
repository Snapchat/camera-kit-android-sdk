import os
import subprocess
import json
import time
from typing import Optional, Any, Dict
from concurrent.futures import ThreadPoolExecutor

from pipeline_app.state import ReleaseScope, Version, SdkBuild, BinaryBuild, is_test_mode
from pipeline_app.constants import *
from pipeline_app import ci_git_helpers, remote_services

# Use string type hints to avoid circular imports

def create_camera_kit_sdk_distribution_release_candidate_message(
        release_version: Version,
        builds_map: dict[str, BinaryBuild],
        build_job_url: str
) -> str:
    message = f"Release candidate builds for {release_version} are ready for testing:\n"
    
    for name, binary_build in builds_map.items():
        message += f"h3. {name}:\n"
        message += f"Version {binary_build.version} ({binary_build.build_number}):\n"
        message += f"{binary_build.htmlUrl}\n"
        message += f"built by {binary_build.get_build_url()}\n"
    
    message += f"\nh6. Generated in: {build_job_url}"
    return message

def notify_slack_and_process_pr(pr, slack_channel):
    """Process a single PR - notify and wait for approval"""
    remote_services.notify_on_slack(
        slack_channel,
        f"{pr['title']}: {pr['html_url']}"
    )
    ci_git_helpers.comment_on_pr_when_approved_and_wait_to_close(pr["repo"], pr["number"], pr["comment"])
    return pr["number"]  

def get_json_file_from_gcs(path: str, filename: str) -> dict:
    cmd = ["gsutil", "cp", f"gs://{GCS_BUCKET_SNAPENGINE_BUILDER}/{path}/{filename}", "."]
    subprocess.run(cmd, check=True)
    print(f"Downloaded {path} to {filename}")
    
    with open(filename, "r", encoding="utf-8") as f:
        return json.loads(f.read())

def publish_camerakit_android_sdk(pipeline_step: "PipelineStep", branch: str, internal: bool, step_name: str, test_mode: bool = False, job_id: Optional[str] = None):
    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=PATH_ANDROID_REPO,
        branch=branch,
        job_name=JOB_CAMERAKIT_SDK_ANDROID_PUBLISH,
        job_id=job_id,
        display_name=step_name,
        params={
        "maven_repository": "maven_snap_internal" if internal else "maven_sonatype_staging",
        "test_mode": test_mode
        },
        outputs=["publications.txt", "build_info.json"]
    )

def publish_camerakit_ios_sdk(pipeline_step: "PipelineStep", branch: str, step_name: str, test_mode: bool = False, job_id: Optional[str] = None):
    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=PATH_IOS_REPO,
        branch=branch,
        job_name=JOB_CAMERAKIT_SDK_IOS_PUBLISH,
        job_id=job_id,
        display_name=step_name,
        params={
            "test_mode": test_mode
        },
        outputs=["build_info.json"]
    )

def get_publish_job_id_for_branch(job_name: str, branch: str) -> str:
    return f"{job_name}-{branch}"

def get_ios_build(file_prefix: str, version: str):   
    build_info = get_json_file_from_inputs(file_prefix, FILE_NAME_CI_JOB_BUILD_INFO)

    build_commit = build_info['commit']
    build_number = build_info['build_number']
    pipeline_id = build_info['pipeline_id']
    branch = build_info['branch']        
    
    sdk_build = SdkBuild(
        version=version,
        branch=branch,
        commit=build_commit,
        build_number=build_number,
        build_job=JOB_CAMERAKIT_SDK_IOS_PUBLISH,
        build_host=HOST_SNAPCI_BUILDER,
        pipeline_id=pipeline_id
    )
    return sdk_build

def get_android_sdk_build(file_prefix: str): 
    build_info = get_json_file_from_inputs(file_prefix, FILE_NAME_CI_JOB_BUILD_INFO)
    
    print(f"🔍 [DEBUG] get_android_sdk_build called with file_prefix: {file_prefix}", flush=True)
    print(f"🔍 [DEBUG] build_info: {build_info}", flush=True)
    build_commit = build_info['commit']
    build_number = build_info['build_number']
    pipeline_id = build_info['pipeline_id']
    branch = build_info['branch']         

    ci_inputs_dir = os.environ.get("CI_INPUTS")
    if not ci_inputs_dir:
        raise EnvironmentError("CI_INPUTS environment variable is not set.")
    
    publications_file = f"{file_prefix}-{FILE_NAME_CI_JOB_PUBLICATIONS}"
    file_path = os.path.join(ci_inputs_dir, publications_file)    

    with open(file_path, 'r') as f:
        publications_content = f.read().strip()
        if not publications_content:
            raise ValueError(f"Publications file {publications_file} is empty")
        publications = publications_content.split('\n')
        if not publications:
            raise ValueError(f"No publications found in {publications_file}")
        first_publication = publications[0]
        version_str = first_publication.split(':')[-1]
        version = Version.from_string(version_str)
    
    sdk_build = SdkBuild(
        version=version,
        branch=branch,
        commit=build_commit,
        build_number=build_number,
        build_job=JOB_CAMERAKIT_SDK_ANDROID_PUBLISH,
        build_host=HOST_SNAPCI_BUILDER,
        pipeline_id=pipeline_id
    )
    return sdk_build

def get_android_sdk_from_pipeline_id(pipeline_id: str):
    path = f"{JOB_CAMERAKIT_SDK_ANDROID_PUBLISH}/{pipeline_id}"  
    print(f"🔍 [DEBUG] get_android_sdk_from_pipeline_id called with pipeline_id: {pipeline_id}", flush=True)
  
    try:
        build_info = get_json_file_from_gcs(path, FILE_NAME_CI_JOB_BUILD_INFO)
        print(f"✅ [DEBUG] Successfully loaded build_info: {build_info}", flush=True)
    except Exception as e:
        print(f"❌ [DEBUG] Failed to load build_info from GCS", flush=True)
        print(f"❌ [DEBUG] GCS path was: {path}", flush=True)
        print(f"❌ [DEBUG] Filename was: {FILE_NAME_CI_JOB_BUILD_INFO}", flush=True)
        raise

    build_commit = build_info['commit']
    build_number = build_info['build_number']
    pipeline_id = build_info['pipeline_id']
    branch = build_info['branch']         

    ci_inputs_dir = os.environ.get("CI_INPUTS")
    if not ci_inputs_dir:
        raise EnvironmentError("CI_INPUTS environment variable is not set.")
    
    cmd = ["gsutil", "cp", f"gs://{GCS_BUCKET_SNAPENGINE_BUILDER}/{path}/{FILE_NAME_CI_JOB_PUBLICATIONS}", "."]
    subprocess.run(cmd, check=True)
    
    with open(FILE_NAME_CI_JOB_PUBLICATIONS, "r") as f:
        publications = f.read().strip().split('\\n')
        if not publications:
            raise ValueError(f"No publications found in {FILE_NAME_CI_JOB_PUBLICATIONS}")
        
        first_publication = publications[0]
        version_str = first_publication.split(':')[-1]
        version = Version.from_string(version_str)
    
        sdk_build = SdkBuild(
            version=version,
            branch=branch,
            commit=build_commit,
            build_number=build_number,
            build_job=JOB_CAMERAKIT_SDK_ANDROID_PUBLISH,
            build_host=HOST_SNAPCI_BUILDER,
            pipeline_id=pipeline_id
        )
        return sdk_build

def get_ios_sdk_from_pipeline_id(pipeline_id: str, version: str):
    path = f"{JOB_CAMERAKIT_SDK_IOS_PUBLISH}/{pipeline_id}"
    print(f"🔍 [DEBUG] get_ios_sdk_from_pipeline_id called with pipeline_id: {pipeline_id}", flush=True)

    build_info = get_json_file_from_gcs(path, FILE_NAME_CI_JOB_BUILD_INFO)
    print(f"✅ [DEBUG] Successfully loaded iOS build_info: {build_info}", flush=True)

    build_commit = build_info['commit']
    build_number = build_info['build_number']
    pipeline_id = build_info['pipeline_id']
    branch = build_info['branch']

    sdk_build = SdkBuild(
        version=version,
        branch=branch,
        commit=build_commit,
        build_number=build_number,
        build_job=JOB_CAMERAKIT_SDK_IOS_PUBLISH,
        build_host=HOST_SNAPCI_BUILDER,
        pipeline_id=pipeline_id
    )
    return sdk_build

def get_file_from_inputs(filename: str) -> str:
    ci_inputs_dir = os.environ.get("CI_INPUTS")
    if not ci_inputs_dir:
        raise EnvironmentError("CI_INPUTS environment variable is not set.")
    
    file_path = os.path.join(ci_inputs_dir, filename)
    return read_file(file_path)

def get_json_file_from_inputs(output_job_id: str, filename: str) -> dict:
    file = get_file_from_inputs(f"{output_job_id}-{filename}")
    try:
        return json.loads(file)
    except json.JSONDecodeError as e:
            print(f"Error parsing JSON from {filename}: {e}")
            raise

def read_file(path: str) -> str:
    with open(path, "r", encoding="utf-8") as f:
        return f.read()
    
def update_build_name_for(release_scope: ReleaseScope, version: Version):
    build_number = os.environ.get("BUILD_NUMBER")
    
    if not build_number:
        raise EnvironmentError("BUILD_NUMBER environment variable is not set.")
    
    suffix = "_test" if is_test_mode() else ""
    build_name = f"{build_number}{suffix}_{release_scope.value.lower()}_{version}"

    ci_outputs_dir = os.environ.get("CI_OUTPUTS")
    if not ci_outputs_dir:
        raise EnvironmentError("CI_OUTPUTS environment variable is not set.")

    output_path = os.path.join(ci_outputs_dir, "build_number.txt")
    with open(output_path, "w") as f:
        f.write(build_name)

def camera_kit_release_branch_for(version: Version) -> str:
    return ci_git_helpers.add_test_branch_prefix_if_needed(f"release/{version.major}.{version.minor}.x")

def release_branch_prefix():
    return TEST_BRANCH_PREFIX if is_test_mode() else "N/A"

def watch_pipeline(job_name: str, pipeline_id: str, on_error_callback=None):
    print(f"Watching pipeline {job_name} https://ci-portal.mesh.sc-corp.net/cp/pipelines/p/{pipeline_id}", flush=True)
            
    try:
        result = subprocess.run(
            ["snapci", "pipeline", "watch", pipeline_id],
            check=False
        )
        
        if result.returncode != 0 and on_error_callback:
            on_error_callback()
    except Exception as e:
        if on_error_callback:
            on_error_callback()

def trigger_pipeline(pipeline_label: str, params: dict = None) -> str:
    label = f"{PATH_CAMERAKIT_DISTRIBUTION_REPO}@{BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN}//{JOB_CAMERAKIT_DISTRIBUTION_TRIGGER_JOB}"

    cmd = ["snapci", "pipeline", "trigger", label]

    dynamic_job_label = pipeline_label
    
    if params:
        for key, value in params.items():
            dynamic_job_label += f" --params {key}={value}"
    
    cmd.extend(["--params", f"label={dynamic_job_label}"])
    
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        check=False  # Don't raise exception automatically
    )
    if result.returncode != 0:
        print(f"❌ snapci command failed with return code {result.returncode}")
        print(f"STDOUT: {result.stdout}")
        print(f"STDERR: {result.stderr}")
        raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)

    pipeline_url = result.stdout.strip()
    pipeline_id = pipeline_url.split("/")[-1]
    return pipeline_id

def run_dynamic_job(job_label: str, job_id: str, name: str, params: dict = None):
    """Run a dynamic job with the given parameters"""
    cmd = ["snapci", "dynamic", "add", job_label, "--name", name]
    
    if params:
        for key, value in params.items():
            cmd.extend(["--params", f"{key}={value}"])
    
    cmd.extend(["--id", job_id])

    print(f"Running dynamic job with command: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)

def trigger_on_demand_release_builds():
    result = subprocess.run(
        ["snapci", "pipeline", "trigger", f"${PATH_CAMERAKIT_DISTRIBUTION_REPO}@{BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN}//job3"],
        capture_output=True,
        text=True,
        check=True
    )
    # Extract pipeline URL and clean it
    pipeline_url = result.stdout.strip()

    pipeline_id = pipeline_url.split("/")[-1]
    return pipeline_id

def cancel_pipeline(pipeline_id: str):
    subprocess.run(
        ["snapci", "pipeline", "cancel", pipeline_id],
        check=True
    )

def is_pipeline_running(pipeline_id: str) -> bool:
    result = subprocess.run(
        ["snapci", "pipeline", "get", pipeline_id],
        capture_output=True,
        text=True,
        check=True
    )

    print(f"Pipeline {pipeline_id} status: {result.stdout.strip()}")

def dynamic_build_distribution_release(pipeline_step: "PipelineStep", branch: str, commit: Optional[str] = None, test_mode: bool = False):
    jobs = [
        {
            "name": JOB_CAMERAKIT_DISTRIBUTION_BUILD,
            "step_name": "Build: CameraKit Distribution",
            "outputs": [FILE_NAME_CI_JOB_BUILD_INFO]
        },
        {
            "name": JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS,
            "step_name": "Publish: iOS Sample App",
            "outputs": [FILE_NAME_RELEASE_INFO, FILE_NAME_CI_JOB_BUILD_INFO]
        },
        {
            "name": JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID,
            "step_name": "Publish: Android Sample App",
            "outputs": [FILE_NAME_RELEASE_INFO, FILE_NAME_CI_JOB_BUILD_INFO]
        }
    ]

    for job in jobs:
        job_name = job["name"]
        step_name = job["step_name"]
        outputs = job["outputs"]

        pipeline_step.add_dynamic_job_to_wait_for(
            repo_name=PATH_CAMERAKIT_DISTRIBUTION_REPO,
            branch=branch,
            job_name=job_name,
            display_name=step_name,
            commit=commit,
            params={
                "pull_number": "1" if is_test_mode() else "N/A",
                "test_mode": test_mode
            },
            outputs=outputs
        )
        
def get_binary_builds_for_release(release_version: Version) -> dict[str, BinaryBuild]:

    def get_html_url_for_build(output_job_id: str, filename: str) -> str:
        return get_json_file_from_inputs(output_job_id, filename)["download_url"]

    release_branch = camera_kit_release_branch_for(release_version)
    distribution_build_job_bucket = f"{GCS_BUCKET_SNAPENGINE_BUILDER}/{JOB_CAMERAKIT_DISTRIBUTION_BUILD}/{os.environ.get('CI_PIPELINE_ID')}"
    jobs = [ 
        {
            "key": KEY_CAMERAKIT_DISTRIBUTION_BUILD,
            "job": JOB_CAMERAKIT_DISTRIBUTION_BUILD,
            "html_url": f"https://console.cloud.google.com/storage/browser/_details/{distribution_build_job_bucket}/camerakit-distribution.zip",
            "get_download_uri": f"gs://{distribution_build_job_bucket}/camerakit-distribution.zip"
        }, 
        {
            "key": KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID,
            "job": JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID,
            "html_url": get_html_url_for_build(JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID, FILE_NAME_RELEASE_INFO),
            "get_download_uri": None
        },
        {
            "key": KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS,
            "job": JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS,
            "html_url": get_html_url_for_build(JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS, FILE_NAME_RELEASE_INFO),
            "get_download_uri": None
        },
    ]

    binary_builds = dict()

    for job in jobs:
        job_key = job["key"]
        job_name = job["job"]
        
        html_url = job["html_url"]
        download_uri = job["get_download_uri"]
        
        build_info = get_json_file_from_inputs(job_name, FILE_NAME_CI_JOB_BUILD_INFO)

        binary_build = BinaryBuild(
            release_version,
            branch=release_branch,
            commit=build_info["commit"],
            build_number=build_info["build_number"],
            build_job=job_name,
            build_host=HOST_SNAPCI_BUILDER,
            htmlUrl=html_url,
            downloadUri=download_uri,
            pipeline_id=build_info["pipeline_id"]
        )
        
        binary_builds[job_key] = binary_build

    return binary_builds

def trigger_and_wait_for_distribution_build(
        release_version: Version,
        commit: str,
        slack_channel: str
):
    release_branch = camera_kit_release_branch_for(release_version)
    
    def get_html_url_for_build(job_path, file_name: str) -> str:
        return get_json_file_from_gcs(job_path, file_name)["download_url"]
    
    jobs = [ 
        {
            "name": KEY_CAMERAKIT_DISTRIBUTION_BUILD,
            "job": JOB_CAMERAKIT_DISTRIBUTION_BUILD,
            "html_url": lambda job_path: f"https://console.cloud.google.com/storage/browser/_details/{GCS_BUCKET_SNAPENGINE_BUILDER}/{job_path}/camerakit-distribution.zip",
            "get_download_uri": lambda job_path: f"gs://{GCS_BUCKET_SNAPENGINE_BUILDER}/{job_path}/camerakit-distribution.zip"
        }, 
        {
            "name": KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_ANDROID,
            "job": JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_ANDROID,
            "html_url": lambda job_path: get_html_url_for_build(job_path, FILE_NAME_RELEASE_INFO),
            "get_download_uri": lambda job_path: None
        },
        {
            "name": KEY_CAMERAKIT_DISTRIBUTION_SAMPLE_BUILD_IOS,
            "job": JOB_CAMERAKIT_DISTRIBUTION_SAMPLE_PUBLISH_IOS,
            "html_url": lambda job_path: get_html_url_for_build(job_path, FILE_NAME_RELEASE_INFO),
            "get_download_uri": lambda job_path: None
        },
     ]
    
    def process_job(job):
        job_name = job["job"]
        job_display_name = job["name"]
         
        job_label = f"{PATH_CAMERAKIT_DISTRIBUTION_REPO}@{release_branch}#{commit}//{job_name}"
        
        pipeline_id = trigger_pipeline(job_label)
        
        def on_error():
            remote_services.notify_on_slack(
                slack_channel,
                f"❌ Pipeline {job_display_name} failed https://ci-portal.mesh.sc-corp.net/cp/pipelines/p/{pipeline_id}"
            )
            raise Exception(f"Pipeline {job_display_name} failed")
        
        watch_pipeline(job_display_name, pipeline_id, on_error)
        
        job_path = f"{job_name}/{pipeline_id}"    
        download_uri = job["get_download_uri"](job_path)
        html_url = job["html_url"](job_path)

        build_info = get_json_file_from_gcs(job_path, FILE_NAME_CI_JOB_BUILD_INFO)
        
        binary_build = BinaryBuild(
            release_version,
            branch=release_branch,
            commit=build_info["commit"],
            build_number=build_info["build_number"],
            build_job=job_path,
            build_host=HOST_SNAPCI_BUILDER,
            htmlUrl=html_url,
            downloadUri=download_uri,
            pipeline_id=build_info["pipeline_id"]
        )
    
        return job_display_name, binary_build

    # Run all jobs in parallel
    with ThreadPoolExecutor(max_workers=3) as executor:
        futures = [executor.submit(process_job, job) for job in jobs]
        
        build_map_to_update = {}
        # Collect results
        for future in futures:
            job_name, binary_build = future.result()
            build_map_to_update[job_name] = binary_build
    
    return build_map_to_update

def trigger_and_wait_for_update_camerakit_version(
        repo: str,
        job: str,
        branch: str,
        commit: str,
        branch_prefix: str,
        next_version: Version,
        slack_channel: str,
):
    label = f"{repo}@{branch}//{job}"

    pipeline_id = trigger_pipeline(label, {
        "branch": branch,
        "commit": commit, 
        "next_version": str(next_version),
        "branch_prefix": branch_prefix
    })

    watch_pipeline(job, pipeline_id)

    try:
        pr_status_json = get_json_file_from_gcs(f"{JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE}/{pipeline_id}", FILE_NAME_CI_RESULT_PR_RESPONSE)    
        process_pr_from_json(pr_status_json, slack_channel, COMMENT_PR_FIRE)
    except Exception as e:
        print(f"Downloading {FILE_NAME_CI_RESULT_PR_RESPONSE} failed due to: {e}. "
                f"It is possible that the {JOB_CAMERAKIT_SDK_ANDROID_VERSION_UPDATE} exited early indicating no version update was necessary", flush=True)

def update_camerakit_version_if_needed(
    pipeline_step: "PipelineStep",
    repo: str,
    job: str,
    branch: str,
    commit: str, 
    branch_prefix: str, 
    next_version: Version
):
    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=repo,
        branch=branch,
        job_name=job,
        outputs=[FILE_NAME_CI_RESULT_PR_RESPONSE],
        params={
            "branch": branch,   
            "commit": commit,
            "next_version": str(next_version),
            "branch_prefix": branch_prefix
        }  
    )

def process_pr_from_json(pr_status_json: dict, slack_channel: str, pr_comment: str) -> bool: 
    
    print(f"PR status JSON: {pr_status_json}", flush=True)
    
    pr_number = pr_status_json['number']
    pr_title = pr_status_json['title']
    pr_html_url = pr_status_json['html_url']
    pr_repo = f"Snapchat/{pr_status_json['head']['repo']['name']}"
    
    print(f"Readying PR: {pr_html_url}", flush=True)
    
    # TODO: Implement retry logic
    ci_git_helpers.mark_pr_ready(pr_repo, pr_number)
    
    remote_services.notify_on_slack(slack_channel, f"{pr_title}: {pr_html_url}")
    
    ci_git_helpers.comment_on_pr_when_approved_and_wait_to_close(pr_repo, pr_number, pr_comment)
    
    return True

def wait_until_available(url: str) -> bool:
    while True:
        if remote_services.is_url_available(url):
            return True
        else:
            time.sleep(STATUS_CHECK_SLEEP_SECONDS)

def camera_kit_android_sdk_maven_central_url_for(version: Version) -> str:
    return f"https://{PATH_MAVEN_CENTRAL_REPO}/com/snap/camerakit/camerakit/{version}"

def camera_kit_ios_sdk_cocoapods_specs_url_for(version: Version) -> str:
    return f"https://{PATH_COCOAPODS_SPECS_REPO}/d/c/6/SCCameraKit/{version}/SCCameraKit.podspec.json"

def publish_camerakit_ios_sdk_to_cocoapods(pipeline_step: "PipelineStep", sdk_build: SdkBuild, distribution_branch: str, dry_run: bool):
    pipeline_step.add_dynamic_job_to_wait_for(
        repo_name=PATH_IOS_REPO,
        branch=sdk_build.branch,
        job_name=JOB_CAMERAKIT_SDK_IOS_COCOAPODS_PUBLISH_JOB,
        display_name="Publish iOS SDK to CocoaPods",
        commit=sdk_build.commit,
        outputs=["build_info.json"],
        params={
            "camkit_build": str(sdk_build.build_number),
            "camkit_commit": sdk_build.commit,
            "camkit_version": str(sdk_build.version),
            "distribution_branch": distribution_branch,
            "gcs_bucket": "gs://snap-kit-build/scsdk/camera-kit-ios/release",
            "dryrun": dry_run
        }
    )
