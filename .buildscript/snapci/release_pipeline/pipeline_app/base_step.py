# base_step.py
from abc import ABC, abstractmethod
import subprocess
from typing import Any, Optional, List, Dict, Union, Type
from pipeline_app.state import PipelineState, is_test_mode
from pipeline_app.constants import PATH_CAMERAKIT_DISTRIBUTION_REPO, BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN

class JobOutputsWrapper:
    def __init__(self, job_id: str, input_prefix: str, outputs: List[str] = []):
        """
        Wrapper for job outputs. Maps each output for a job to a unique name. to be used as input for the next step.
        """
        self.job_id = job_id
        self.renamed_inputs = []
        
        for output in outputs:
            renamed_input = f"{input_prefix}-{output}"
            self.renamed_inputs.append((output, renamed_input))

class InProcessStepConfig:
    """Configuration for in-process step execution"""
    def __init__(self, step_class: Type['PipelineStep']):
        self.step_class = step_class
    
    def create_step_instance(self) -> 'PipelineStep':
        """Create and return an instance of the step class"""
        return self.step_class()

class DynamicStepConfig:
    """Configuration for executing a step as a dynamic job """
    def __init__(self, 
                 step_class: Type['PipelineStep'],
                 display_name: str,
                 additional_params: Optional[Dict[str, Any]] = None,
                 ):
        self.step_class = step_class
        self.job_id = step_class.__name__
        self.display_name = display_name
        self.run_step = step_class.__name__
        self.additional_params = additional_params or {}
        
# Union type for next step configuration
NextStepConfig = Union[InProcessStepConfig, DynamicStepConfig, None]

class PipelineStep(ABC):
    """Base class for all pipeline steps"""
    
    name: str = None

    jobs_to_wait_for: List[JobOutputsWrapper] = []
    
    def __init__(self):
        if self.name is None:
            self.name = self.__class__.__name__
    
    @abstractmethod
    def execute(self, state: PipelineState):
        """Main step logic - must be implemented by subclasses"""
        pass
    
    @abstractmethod
    def should_execute(self, state: PipelineState) -> bool:
        """Check if step should be executed - must be implemented by subclasses"""
        pass
    
    def get_next_step_config(self) -> NextStepConfig:
        """
        Get configuration for the next step.
        Override this method to specify next step details and perform any post-execution setup.
        
        This method can:
        - Modify the state based on the execution result
        - Set up parameters for the next step
        - Perform any cleanup or side effects
        - Return the next step configuration
        
        Returns:
            InProcessStepConfig - For in-process execution
            DynamicStepConfig - For dynamic job execution 
            None - If no next step should be triggered
        """
        return None
    
    def add_dynamic_job_to_wait_for(
        self,
        repo_name: str,
        branch: str,
        job_name: str,
        display_name: Optional[str] = None,
        job_id: Optional[str] = None,
        commit: Optional[str] = None,
        params: Optional[dict] = None,
        outputs: Optional[List[str]] = []):
        """
        Triggers dynamic job and makes it a prereq for the next dynamic step.
        """
        commit_str = f"#{commit}" if commit else ""

        if display_name is None:
            display_name = job_name

        job_label = f"{repo_name}@{branch}{commit_str}//{job_name}"
    
        result_id = self.add_dynamic_job(job_id=job_id, job_label=job_label, display_name=display_name, params=params)
        input_prefix = result_id if job_id else job_name

        self.jobs_to_wait_for.append(JobOutputsWrapper(result_id, input_prefix, outputs))

    def add_dynamic_job(self, job_label: str, display_name: str, job_id: Optional[str] = None, params: Optional[Dict[str, Any]] = None):
        """
        Trigger a dynamic job - implement this to call your actual job system.
        Returns the job ID of the triggered job.
        """
        
        print(f"[DYNAMIC-STEP] Triggering: {display_name} ({job_label})")
        cmd = ["snapci", "dynamic", "add", job_label]
        
        if job_id:
            cmd.extend(["--id", job_id])

        if display_name:
            cmd.extend(["--name", display_name])
        
        if params:
            for key, value in params.items():
                cmd.extend(["--params", f"{key}={value}"])

        print(f"Running cmd: {cmd}")
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
        
        # The command echoes the job ID when complete, capture and return it
        result_id = result.stdout.strip()
        return result_id
        
    def wait_for_dynamic_job(self, job_id: str, prereq_id: str, renamed_inputs: List[tuple[str, str]]= []) -> None:
        """
        Wait for a dynamic job to complete - implement this to call your actual job system.
        """
        print(f"job {job_id} is waiting for {prereq_id}")
        cmd = ["snapci", "dynamic", "connect", job_id, "--prereq", prereq_id]
        
        if renamed_inputs:
            for input_name, output_name in renamed_inputs:
                cmd.extend(["--renamed-inputs", f"{input_name}={output_name}"])
        
        print(f"Wait for job cmd: {cmd}")       
        result = subprocess.run(cmd, check=True, capture_output=False, text=True)
        
        if result.stderr:
            print(f"Command stderr: {result.stderr}")
    
    def trigger_next_step(self, state: PipelineState) -> Any:
        """
        Trigger the next step - either in-process or as a dynamic job.
        Returns the result of the next step if run in-process, None if triggered as job.
        """
        next_config = self.get_next_step_config()
        
        if next_config is None:
            return None
        
        if isinstance(next_config, InProcessStepConfig):
            next_step = next_config.create_step_instance()
            print(f"[IN-PROCESS-NEXT] From '{self.name}' to '{next_step.name}'")
            return next_step.run(state)
        
        elif isinstance(next_config, DynamicStepConfig):
            # Prepare parameters for next step
            params = {
                "predefined_state_json_bucket_path": state._get_gcs_save_uri(),
                "test_mode": is_test_mode(),
                "run_step": next_config.run_step,
                **next_config.additional_params
            }

            next_step_job_label = f"{PATH_CAMERAKIT_DISTRIBUTION_REPO}@{BRANCH_CAMERAKIT_DISTRIBUTION_REPO_MAIN}//camkit_distribution_release_pipeline"

            # Capture the actual job ID returned by add_dynamic_job
            next_step_job_id = self.add_dynamic_job(
                job_label=next_step_job_label, 
                display_name=next_config.display_name, 
                params=params
            )

            for job in self.jobs_to_wait_for:
                self.wait_for_dynamic_job(next_step_job_id, job.job_id, job.renamed_inputs)
        
        return None
    
    def run(self, state: PipelineState) -> Any:
        """Execute the step with skip check and next step triggering"""
        print(f"run: {self.name}")
        
        if not self.should_execute(state):
            print(f"[SKIP] Step '{self.name}'")
            self.trigger_next_step(state)
            return 

        print(f"[EXECUTE] Step '{self.name}'")
        self.execute(state)        

        print(f"[TRIGGER-NEXT] Checking for next step from '{self.name}'")
        self.trigger_next_step(state)
            
