# Camera Kit Release Pipeline

This Python application manages the Camera Kit distribution release pipeline, handling build coordination, and release automation.

## 1. Pipeline Parameters

The following parameters can be used to control pipeline execution via the `run_step` parameter, as defined in `SNAPCI.star`:

- **`test_mode`**: Run in test mode to avoid actual releases
- **`predefined_state_json_bucket_path`**: Path to existing state JSON for resuming builds
- **`release_scope`**: Release type - choices: `MINOR`, `MAJOR`, `PATCH`
- **`patch_version_to_release`**: Existing version to patch (required when `release_scope` is `PATCH`)
- **`run_step`**: Specific pipeline step to start from. This is used internally. You dont need to provide this param. 

## 2. Restarting Build from Saved State

### Via SnapCI Dashboard
1. Navigate to the SnapCI dashboard for the failed pipeline
2. Click on the details of the most recently failed release pipeline step
3. Copy the `predefined_state_json_bucket_path` value
   - Format: `gs://snapengine-builder-artifacts/camkit_distribution_release_pipeline/{PIPELINE_ID}/release_state.json`

<img width="1070" alt="Screenshot 2025-07-02 at 11 12 33 AM" src="https://github.sc-corp.net/Snapchat/camera-kit-distribution/assets/9117/4b622378-d20e-458b-b691-02a9db8a963c">

4. Start a new execution with the copied path as the `predefined_state_json_bucket_path` parameter

<img width="1205" alt="Screenshot 2025-07-02 at 11 17 03 AM" src="https://github.sc-corp.net/Snapchat/camera-kit-distribution/assets/9117/60384435-b592-4e9d-a3d9-bd1b8a340986">

5. **Important**: Uncheck the `test_mode` parameter for production releases

### Manual State Modification
You can download and modify the state file directly:

1. Download: `https://storage.cloud.google.com/snapengine-builder-artifacts/camkit_distribution_release_pipeline/{PIPELINE_ID}/release_state.json`
2. Modify the state as needed
3. Re-upload using `gsutil` command (requires write permissions):
   ```bash
   gsutil cp modified_state.json gs://snapengine-builder-artifacts/camkit_distribution_release_pipeline/{PIPELINE_ID}/release_state.json
   ```

## 3. Development

For local development, use VSCode with the Python extension. While the application cannot be run locally due to CI environment dependencies, you can run tests using pytest:

```bash
cd .buildscript/snapci/release_pipeline/tests
pytest
```
