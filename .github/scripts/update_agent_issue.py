"""Update a single GitHub issue with CI outcomes. No code edits are performed."""
import json
import os
import subprocess
from pathlib import Path

repo = os.environ["REPO"]
result = os.environ["RESULT"]
run_url = os.environ["RUN_URL"]
title = "ProofVault Build Agent | CI and Release Readiness"
log = Path("proofvault-build.log")
snippet = (log.read_text(errors="replace")[-2500:] if log.exists() else "No build output available")
body = (
    "Automated build agent status: **" + result + "**\n\n"
    "Run: " + run_url + "\n\n"
    "This job checks Gradle build, unit tests and lint. It does not auto-fix code, "
    "claim hardware test results, or release to production.\n\n"
    "Last lines of build log:\n\n```text\n" + snippet + "\n```\n"
)
query = subprocess.run(
    ["gh", "issue", "list", "--repo", repo, "--state", "open", "--search", title, "--json", "number,title"],
    check=True, capture_output=True, text=True,
)
issues = json.loads(query.stdout)
matching = next((item for item in issues if item["title"] == title), None)
if matching:
    subprocess.run(["gh", "issue", "edit", str(matching["number"]), "--repo", repo, "--body", body], check=True)
else:
    subprocess.run(["gh", "issue", "create", "--repo", repo, "--title", title, "--body", body], check=True)
