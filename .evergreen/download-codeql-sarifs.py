#!/usr/bin/env python3
"""Downloads the CodeQL SARIF reports for the checked out commit, one per analysed language.

Supported/used environment variables:
GITHUB_TOKEN -- needs code scanning alerts read access; the analyses API is 401 for
                anonymous callers even though this repository is public.
"""

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

GITHUB_REPO = "mongodb/mongo-hibernate"
ANALYSES_URL = "https://api.github.com/repos/{}/code-scanning/analyses".format(GITHUB_REPO)
# Every CodeQL run uploads one analysis per language, under these categories, so a commit has
# one analysis per entry and an export needs all of them.
CODEQL_CATEGORIES = ("/language:java-kotlin", "/language:actions")
POLL_TIMEOUT_SECS = 900
POLL_INTERVAL_SECS = 20
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "build" / "ssdlc" / "static-analysis-reports"


def github_get(url, accept, token):
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": "Bearer {}".format(token),
            "X-GitHub-Api-Version": "2022-11-28",
            "Accept": accept,
        },
    )
    try:
        with urllib.request.urlopen(request) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        sys.exit("GET {} returned {}: {}".format(url, error.code, error.read().decode("utf-8", "replace")))


def select_analysis_id(analyses, commit_sha, category):
    matching = [a for a in analyses if a["commit_sha"] == commit_sha and a["category"] == category]
    if not matching:
        return None
    return max(matching, key=lambda a: a["created_at"])["id"]


def wait_for_analyses(commit_sha, token):
    """Returns the newest analysis id per category, once every category has one."""
    print("\nWaiting for CodeQL analyses of {}".format(commit_sha))
    waited_secs = 0
    while True:
        analyses = json.loads(
            github_get(ANALYSES_URL + "?per_page=100", "application/vnd.github+json", token)
        )
        analysis_ids = {
            category: select_analysis_id(analyses, commit_sha, category)
            for category in CODEQL_CATEGORIES
        }
        for category, analysis_id in analysis_ids.items():
            print("{}: {}".format(category, analysis_id or "not yet uploaded"))
        if all(analysis_ids.values()):
            return analysis_ids
        if waited_secs >= POLL_TIMEOUT_SECS:
            sys.exit(
                "\nERROR: gave up after {}s waiting for the CodeQL analyses of {}".format(
                    waited_secs, commit_sha
                )
            )
        time.sleep(POLL_INTERVAL_SECS)
        waited_secs += POLL_INTERVAL_SECS


def assert_sarif(sarif, commit_sha, language):
    runs = sarif["runs"]
    automation_ids = [run.get("automationDetails", {}).get("id", "") for run in runs]
    if not any(language in automation_id for automation_id in automation_ids):
        sys.exit("no run for language {}, automationDetails ids are {}".format(language, automation_ids))
    revisions = [
        provenance.get("revisionId")
        for run in runs
        for provenance in run.get("versionControlProvenance", [])
    ]
    if revisions and commit_sha not in revisions:
        sys.exit("analysed revisions {} do not include {}".format(revisions, commit_sha))


def main():
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        sys.exit("GITHUB_TOKEN must be set to a non-empty string")
    commit_sha = subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip()

    # Evergreen and the CodeQL workflow react to the same push concurrently, so the analyses
    # of the commit being published do not exist yet when this task starts.
    analysis_ids = wait_for_analyses(commit_sha, token)

    print("\nDownloading CodeQL SARIF reports")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for category, analysis_id in analysis_ids.items():
        language = category[len("/language:"):]
        sarif = github_get(
            "{}/{}".format(ANALYSES_URL, analysis_id), "application/sarif+json", token
        )
        assert_sarif(json.loads(sarif), commit_sha, language)
        sarif_path = OUTPUT_DIR / "codeql_{}.sarif".format(language)
        sarif_path.write_bytes(sarif)
        print("{} ({} bytes)".format(sarif_path, sarif_path.stat().st_size))


if __name__ == "__main__":
    main()
