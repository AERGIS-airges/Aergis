"""Verify the retained CI709 build inputs; no network or historical checkout needed."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = json.loads((ROOT / "docs/recovery/ci709-source-manifest.json").read_text())
errors = []
for entry in manifest["files"]:
    path = ROOT / entry["path"]
    if not path.is_file():
        errors.append(f"Missing: {entry['path']}")
        continue
    data = path.read_bytes()
    actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if actual != entry["sha"]:
        errors.append(f"Changed: {entry['path']}")
    actual_mode = "100755" if path.stat().st_mode & 0o111 else "100644"
    if actual_mode != entry["mode"]:
        errors.append(f"Mode changed: {entry['path']}")
if errors:
    raise SystemExit("CI709 source fidelity failed:\n" + "\n".join(errors))
print(f"VERIFIED: {len(manifest['files'])} retained CI709 files match Git blob hashes and modes.")
