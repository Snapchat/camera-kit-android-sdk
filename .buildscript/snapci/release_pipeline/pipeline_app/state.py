import os
import json
import re
import dacite
import fcntl
import threading
import subprocess
import tempfile
from typing import Callable, Optional
from contextlib import contextmanager
from dataclasses import dataclass, asdict, field, fields
from enum import Enum
from functools import total_ordering
from pipeline_app.constants import GCS_BUCKET_SNAPENGINE_BUILDER

# -----------------------
# Step Data Classes
# -----------------------

class ReleaseScope(str, Enum):
    MINOR = "MINOR"
    PATCH = "PATCH"
    MAJOR = "MAJOR"

@total_ordering
@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int
    qualifier: Optional[str] = None

    @staticmethod
    def from_string(version_name: str) -> "Version":
        version_parts = version_name.split(".")

        major = int(version_parts[0])
        minor = int(version_parts[1])

        patch_and_qualifier = version_parts[2]
        patch_parts = re.split(r'[-+]', patch_and_qualifier, 1)
        patch = int(patch_parts[0])

        if len(patch_parts) > 1:
            # Remove patch prefix from version_parts[2]
            qualifier = version_parts[2][len(str(patch)):]
            if len(version_parts) > 3:
                qualifier += "." + ".".join(version_parts[3:])
        else:
            qualifier = None

        return Version(major, minor, patch, qualifier)

    def drop_minor(self) -> 'Version':
        return Version(self.major, max(0, self.minor - 1), self.patch, self.qualifier)

    def bump_minor(self) -> 'Version':
        return Version(self.major, self.minor + 1, self.patch, self.qualifier)

    def bump_patch(self) -> 'Version':
        return Version(self.major, self.minor, self.patch + 1, self.qualifier)

    def with_qualifier(self, value: str) -> 'Version':
        return Version(self.major, self.minor, self.patch, value)

    def bump_release_candidate(self) -> 'Version':
        if self.qualifier:
            parts = self.qualifier.split('+')
            maybe_rc = parts[0].replace('-', '')
            if maybe_rc.startswith('rc'):
                try:
                    rc_number = int(''.join(filter(str.isdigit, maybe_rc)))
                    return Version(self.major, self.minor, self.patch, f"-rc{rc_number + 1}")
                except ValueError:
                    pass
        return self

    def __str__(self):
        base = f"{self.major}.{self.minor}.{self.patch}"
        if self.qualifier:
            return f"{base}{self.qualifier}"
        return base

    def __eq__(self, other: "Version") -> bool:
        if isinstance(other, Version):
            return (
                self.major == other.major and
                self.minor == other.minor and
                self.patch == other.patch
            )
        return False

    def __lt__(self, other: "Version") -> bool:
        if self.major != other.major:
            return self.major < other.major
        if self.minor != other.minor:
            return self.minor < other.minor
        return self.patch < other.patch

@dataclass 
class CIBuild:
    version: Version
    branch: str
    commit: str
    pipeline_id: str
    build_number: str
    build_job: str
    build_host: str

    def get_build_url(self) -> str:
        return f"{self.build_host}/cp/pipelines/p/{self.pipeline_id}"

@dataclass
class SdkBuild(CIBuild):
    pass

@dataclass
class BinaryBuild(CIBuild):
    htmlUrl: str
    downloadUri: Optional[str] = None

@dataclass
class Step1:
    releaseScope: Optional[ReleaseScope] = None
    releaseVersion: Optional[Version] = None
    releaseVerificationIssueKey: Optional[str] = None
    releaseCoordinationSlackChannel: Optional[str] = None

@dataclass
class Step2:
    developmentVersion: Optional[Version] = None

@dataclass
class Step3:
    androidDevSdkBuild: Optional[SdkBuild] = None
    androidReleaseCandidateSdkBuild: Optional[SdkBuild] = None
    iOSDevSdkBuild: Optional[SdkBuild] = None
    iOSReleaseCandidateSdkBuild: Optional[SdkBuild] = None

@dataclass
class Step4:
    releaseCandidateBinaryBuilds: dict[str, BinaryBuild] = field(default_factory=dict)
    releaseCandidateSdkBuildsCommitSha: Optional[str] = None

@dataclass
class Step5:
    releaseVerificationPromptMessageTimestamp: Optional[str] = None
    releaseVerificationComplete: bool = False
    releaseCandidateAndroidSdkBuild: Optional[SdkBuild] = None
    releaseCandidateIosSdkBuild: Optional[SdkBuild] = None
    releaseCandidateBinaryBuilds: dict[str, BinaryBuild] = field(default_factory=dict)

@dataclass
class Step6:
    releaseAndroidSdkBuild: Optional[SdkBuild] = None
    releaseIosSdkBuild: Optional[SdkBuild] = None

@dataclass
class Step7:
    pass

@dataclass
class Step8:
    releaseBinaryBuilds: dict[str, BinaryBuild] = field(default_factory=dict)
    releaseGithubUrl: Optional[str] = None

@dataclass
class Step9:
    androidSdkPublishedToMavenCentral: bool = False
    iosSdkPublishedToCocoapods: bool = False

@dataclass
class Step10:
   sdkApiReferenceSyncedToPublicGithub: bool = False
   sdkApiReferenceSyncedToSnapDocs: bool = False
   
@dataclass
class Step11:
    pass


# -----------------------
# Top-Level Pipeline State
# -----------------------

@dataclass
class PipelineStateData:
    step1: Step1 = field(default_factory=Step1)
    step2: Step2 = field(default_factory=Step2)
    step3: Step3 = field(default_factory=Step3)
    step4: Step4 = field(default_factory=Step4)
    step5: Step5 = field(default_factory=Step5)
    step6: Step6 = field(default_factory=Step6)
    step7: Step7 = field(default_factory=Step7)
    step8: Step8 = field(default_factory=Step8)
    step9: Step9 = field(default_factory=Step9)
    step10: Step10 = field(default_factory=Step10)
    step11: Step11 = field(default_factory=Step11)

# -----------------------
# PipelineState Wrapper
# -----------------------

def _download_from_gcs(gcs_uri: str, local_path: str):
    """Downloads a file from GCS."""
    print(f"Downloading state from {gcs_uri} to {local_path}")
    subprocess.run(["gsutil", "cp", gcs_uri, local_path], check=True, capture_output=True)

def _upload_to_gcs(local_path: str, gcs_uri: str):
    """Uploads a file to GCS."""
    print(f"Uploading state from {local_path} to {gcs_uri}")
    subprocess.run(["gsutil", "cp", local_path, gcs_uri], check=True, capture_output=True)

class PipelineState:

    _filename = "release_state.json"

    def __init__(self, state_input: Optional[str] = None):
        self._lock = threading.RLock()  # Reentrant lock for thread safety

        if state_input and state_input.startswith("gs://"):
            # Create a temporary file that is not deleted on close.
            temp_f = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix=".json")
            temp_f.close() # Close it so gsutil can write to it
            
            _download_from_gcs(state_input, temp_f.name)
            print(f"State file downloaded to non-deleted file: {temp_f.name}")
            self._data = self._load_from_file(temp_f.name)

            # Immediately upload the loaded state to the new bucket for this run
            gcs_save_uri = self._get_gcs_save_uri()
            if gcs_save_uri:
                print(f"Immediately forwarding state to new location: {gcs_save_uri}")
                _upload_to_gcs(temp_f.name, gcs_save_uri)
                
        elif state_input:
            # Input is a JSON string
            self._data = self._load_from_json(state_input)
        else:
            # No input, start with a fresh state
            self._data = PipelineStateData()
    

    def _load_from_file(self, file_path: str) -> PipelineStateData:
        with open(file_path, 'r') as f:
            # Acquire shared lock for reading (with error handling)
            try:
                fcntl.flock(f.fileno(), fcntl.LOCK_SH)
                file_locked = True
            except (OSError, TypeError):
                # File locking not supported on this platform
                file_locked = False
            
            try:
                raw = json.load(f)
                return self._deserialize(raw)
            finally:
                if file_locked:
                    try:
                        fcntl.flock(f.fileno(), fcntl.LOCK_UN)
                    except (OSError, TypeError):
                        pass

    def _load_from_json(self, json_data: str) -> PipelineStateData:
        raw = json.loads(json_data)
        return self._deserialize(raw)

    def _deserialize(self, raw: dict) -> PipelineStateData:
        config = dacite.Config(type_hooks={ReleaseScope: ReleaseScope})
        return dacite.from_dict(PipelineStateData, raw, config=config)

    def to_json(self) -> str:
        return json.dumps(asdict(self._data), indent=4)

    @contextmanager
    def read_state(self):
        """
        Thread-safe context manager for reading state with direct attribute access.
        The state is loaded in __init__ and is immutable within this context.
        """
        with self._lock:
            self._bind_data(self._data)
            try:
                yield self
            finally:
                self._unbind_data()

    @contextmanager
    def update_state(self):
        """
        Thread-safe context manager for updating state.
        Upon exiting the context, the updated state is saved to GCS.
        """
        with self._lock:
            # Create a deep copy of the data for modification
            temp_data = self._deserialize(json.loads(self.to_json()))
            self._bind_data(temp_data)
            
            try:
                yield self
            finally:
                # Commit the changes and save to GCS
                self._unbind_data()
                self._data = temp_data

                gcs_uri = self._get_gcs_save_uri()
                if gcs_uri:
                    # Write to a temporary file that is not deleted on close
                    temp_f = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix=".json")
                    json.dump(asdict(self._data), temp_f, indent=4)
                    temp_f.flush()
                    
                    print(f"Updated state saved to non-deleted file: {temp_f.name}")
                    _upload_to_gcs(temp_f.name, gcs_uri)
                    temp_f.close()

    def _get_gcs_save_uri(self):
        """Constructs the GCS URI for saving the state from environment variables."""        
        ci_pipeline_name = os.environ.get("CI_PIPELINE_NAME")
        code_pipeline_id = os.environ.get("CODE_PIPELINE_ID")
       
        return f"gs://{GCS_BUCKET_SNAPENGINE_BUILDER}/{ci_pipeline_name}/{code_pipeline_id}/{self._filename}"

    def _bind_data(self, data: PipelineStateData):
        for name in data.__dataclass_fields__:
            setattr(self, name, getattr(data, name))

    def _unbind_data(self):
        for name in self._data.__dataclass_fields__:
            delattr(self, name)

def is_test_mode() -> bool: 
    return os.environ.get("test_mode", "true").lower() in ("1", "true", "yes")
