import requests
import re
import json
import time
import subprocess
import os
from urllib.parse import quote
from pipeline_app.constants import (
    URL_BASE_SNAP_JIRA_API,
    LCA_AUDIENCE_ATS, URL_BASE_SNAP_SLACK_API,
    URL_BASE_SNAP_JIRA_API, HOST_SNAP_JIRA,
    STATUS_CHECK_SLEEP_SECONDS,
    VERIFIED_OWNER_SLACK_IDS
)

COMMAND_RETRY_MAX_COUNT = 3

def escape_newlines(text: str) -> str:
    return re.sub(r'(\r\n|\r|\n)+', r'\\n', text)

def create_lca_token_for(audience: str) -> str:
    # Get the active service account
    service_account = subprocess.check_output(
        ["gcloud", "auth", "list", "--filter=status:ACTIVE", "--format=value(account)"],
        text=True
    ).strip()
  
    # Issue the LCA token
    lca_token = subprocess.check_output(
        ["lcaexec", "issue", "google", service_account, audience, "--ttl", "300"],
        text=True
    ).strip()

    return lca_token

def parse_json_text_as_map(text: str):
    return json.loads(text)

def create_jira_issue(project_key: str, issue_type: str, summary: str, description: str):
    lca_token = create_lca_token_for(LCA_AUDIENCE_ATS)
    url = f"{URL_BASE_SNAP_JIRA_API}/issue"
    data = {
        "fields": {
            "project": {"key": project_key},
            "summary": summary,
            "description": escape_newlines(description),
            "issuetype": {"name": issue_type}
        }
    }
    headers = {
        "SC-LCA-1": lca_token,
        "Accept": "application/json",
        "Content-type": "application/json"
    }
    for attempt in range(COMMAND_RETRY_MAX_COUNT):
        try:
            print("Jira request payload:", json.dumps(data, indent=2))
            response = requests.post(url, headers=headers, data=json.dumps(data))
            response.raise_for_status()
            return response.json()
        except Exception as e:
            if attempt == COMMAND_RETRY_MAX_COUNT - 1:
                raise
            time.sleep(2 ** attempt)  # Exponential backoff

def create_jira_issue_comment(issue_key: str, body: str):
    lca_token = create_lca_token_for(LCA_AUDIENCE_ATS)
    url = f"{URL_BASE_SNAP_JIRA_API}/issue/{issue_key}/comment"
    
    # Manually construct JSON string like the Groovy version
    escaped_body = escape_newlines(body)
    data_string = f'{{"body": "{escaped_body}"}}'
    
    headers = {
        "SC-LCA-1": lca_token,
        "Content-type": "application/json"
    }
    for attempt in range(COMMAND_RETRY_MAX_COUNT):
        try:
            response = requests.post(url, headers=headers, data=data_string)
            response.raise_for_status()
            return
        except Exception as e:
            if attempt == COMMAND_RETRY_MAX_COUNT - 1:
                raise
            time.sleep(2 ** attempt)

def look_up_jira_issue(issue_key: str, *query_fields: str):
    lca_token = create_lca_token_for(LCA_AUDIENCE_ATS)
    fields = ",".join(query_fields)
    url = f"{URL_BASE_SNAP_JIRA_API}/issue/{issue_key}?fields={fields}"
    headers = {
        "SC-LCA-1": lca_token
    }
    for attempt in range(COMMAND_RETRY_MAX_COUNT):
        try:
            response = requests.get(url, headers=headers)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            if attempt == COMMAND_RETRY_MAX_COUNT - 1:
                raise
            time.sleep(2 ** attempt)

def jira_issue_url_from(issue_key: str):
    return f"https://{HOST_SNAP_JIRA}/browse/{issue_key}"

def create_slack_channel(name: str, is_private: bool):
    lca_token = create_lca_token_for(LCA_AUDIENCE_ATS)
    url = f"{URL_BASE_SNAP_SLACK_API}/conversations.create"
    data = {
        "name": name,
        "is_private": is_private
    }
    headers = {
        "SC-LCA-1": lca_token,
        "Content-type": "application/json"
    }
    for attempt in range(COMMAND_RETRY_MAX_COUNT):
        try:
            print("Slack request payload:", json.dumps(data, indent=2))
            response = requests.post(url, headers=headers, data=json.dumps(data))
            response.raise_for_status()
            return response.json()
        except Exception as e:
            if attempt == COMMAND_RETRY_MAX_COUNT - 1:
                raise
            time.sleep(2 ** attempt)
    
def notify_on_slack(channel: str, message: str) -> str:

    lca_token = create_lca_token_for(LCA_AUDIENCE_ATS)
    url = f"{URL_BASE_SNAP_SLACK_API}/chat.postMessage"
    data = {
        "channel": channel,
        "text": message,
        "username": "Release Bot"
    }
    headers = {
        "SC-LCA-1": lca_token,
        "Content-type": "application/json"
    }
    for attempt in range(COMMAND_RETRY_MAX_COUNT):
        try:
            response = requests.post(url, headers=headers, data=json.dumps(data))
            response.raise_for_status()
            return response.json()["ts"]
            
        except Exception as e:
            if attempt == COMMAND_RETRY_MAX_COUNT - 1:
                raise
            time.sleep(2 ** attempt)
            
def wait_for_slack_message_verification(channel: str, msg_timestamp: str):
    print(f"Waiting for Slack message verification in channel: {channel}, timestamp: {msg_timestamp}")
    
    while True:
        lca_token = create_lca_token_for(LCA_AUDIENCE_ATS)
        url = f"{URL_BASE_SNAP_SLACK_API}/reactions.get?timestamp={msg_timestamp}&channel={quote(channel)}"

        headers = {
            "SC-LCA-1": lca_token,
            "Content-type": "application/x-www-form-urlencoded"
        }
        
        for attempt in range(COMMAND_RETRY_MAX_COUNT):
            try:
                response = requests.get(url, headers=headers)
                response.raise_for_status()
                response_data = response.json()

                message = response_data.get('message')            
                if not message or 'reactions' not in message:
                    break
                
                for reaction in message['reactions']:
                    if reaction.get('name') == 'lgtm':
                        # Check if any user who reacted with :lgtm: is in our verified list
                        users = reaction.get('users', [])
                        print(f"Found {len(users)} users with :lgtm: reaction: {users}", flush=True)
                        
                        for user_name in users:
                            if user_name in VERIFIED_OWNER_SLACK_IDS:
                                print(f"✔️ Verified user {user_name} found with :lgtm: reaction", flush=True)
                                return True
                
                print(f"No verified users found with :lgtm: reaction. Waiting...", flush=True)
                break
                
            except Exception as e:
                if attempt == COMMAND_RETRY_MAX_COUNT - 1:
                    print(f"Failed to check Slack message after {COMMAND_RETRY_MAX_COUNT} attempts: {e}", flush=True)
                    raise
                print(f"Attempt {attempt + 1} failed, retrying: {e}", flush=True)
                time.sleep(2 ** attempt)
        
        print("Waiting for Slack message verification...", flush=True)
        time.sleep(STATUS_CHECK_SLEEP_SECONDS)

def is_url_available(url: str) -> bool:
    try:
        print(f"Checking URL: {url}")
        response = requests.head(url, allow_redirects=True, timeout=10)
        http_code = response.status_code
        print(f"URL {url} returned HTTP status: {http_code}")
        return 200 <= http_code < 400
    except requests.RequestException as e:
        print(f"[Failed to check URL {url}: {e}")
        return False
