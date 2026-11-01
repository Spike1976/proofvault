#!/usr/bin/env python3
"""Issue-driven ProofVault coding agent. Opens a PR only after Gradle passes."""
import json
import os
import pathlib
import subprocess
import urllib.request

root = pathlib.Path.cwd()
task = os.environ["AGENT_ISSUE"][:6000]
paths = list(root.glob("app/src/main/java/**/*.kt"))[:30]
source = "\n".join("FILE: " + str(p.relative_to(root)) + "\n" + p.read_text()[:9000] for p in paths)
request_body = {
 "model": os.getenv("PROOFVAULT_AGENT_MODEL", "gpt-5.5"),
 "store": False,
 "instructions": "You are an Android software engineer. Solve the requested app issue. Return a JSON object with changes: an array of {path, content} where content is the complete Kotlin file. Modify at most 3 files under app/src/main/java. Do not change application ID, permissions, release signing, dependencies, user data handling, or GitHub workflows. Never implement hidden or unauthorized call recording. Include summary.",
 "input": task + "\n\nCURRENT SOURCE:\n" + source[:65000],
 "text": {"format": {"type": "json_object"}}
}
request = urllib.request.Request("https://api.openai.com/v1/responses",
 data=json.dumps(request_body).encode("utf-8"),
 headers={"Authorization": "Bearer " + os.environ["OPENAI_API_KEY"], "Content-Type": "application/json"})
with urllib.request.urlopen(request, timeout=180) as response:
 reply = json.load(response)
generated = "".join(piece.get("text", "") for item in reply.get("output", []) for piece in item.get("content", []) if piece.get("type") == "output_text")
edits = json.loads(generated)
changed = edits.get("changes", [])
if not 1 <= len(changed) <= 3:
 raise ValueError("Expected one to three file changes")
for item in changed:
 path = item["path"]
 file = (root / path).resolve()
 if not str(file).startswith(str((root / "app/src/main/java").resolve()) + "/") or not path.endswith(".kt"):
  raise ValueError("Edit outside allowed Kotlin source directory")
 if len(item["content"]) > 70000:
  raise ValueError("Oversized output")
 file.write_text(item["content"])
result = subprocess.run(["gradle", "--no-daemon", "testDebugUnitTest", "assembleDebug", "lintDebug"], text=True,
 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=900)
pathlib.Path("agent-build.log").write_text(result.stdout[-25000:])
if result.returncode:
 raise SystemExit("Tests/build/lint failed; no branch will be pushed.")
subprocess.run(["git", "add", "app/src/main/java"], check=True)
subprocess.run(["git", "commit", "-m", "agent: implement verified Android issue"], check=True)
pathlib.Path("agent-result.json").write_text(json.dumps({"status": "tests passed", "summary": edits.get("summary", "")}))
