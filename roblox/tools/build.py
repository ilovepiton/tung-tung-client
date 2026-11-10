#!/usr/bin/env python3
"""Reproducible portable Luau source + deterministic mock checks, no game launch."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
DIST = ROOT / "dist"
TOOLS = ROOT / "tools" / "luau-0.744"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    DIST.mkdir(exist_ok=True)
    header = "-- Generated from roblox/src by tools/build.py. Standard Roblox APIs; compatible host required.\n"
    chunks = [header, "--!nonstrict\n"]
    for name in ("Catalog", "Policy", "PropertyLease"):
        chunks.append(f"local {name} = (function()\n")
        chunks.append((SRC / f"{name}.luau").read_text())
        chunks.append("\nend)()\n")
    runtime = (SRC / "Runtime.luau").read_text()
    runtime = "\n".join(line for line in runtime.splitlines() if not line.startswith("local Catalog =") and not line.startswith("local Policy =") and not line.startswith("local Lease ="))
    chunks.append("local Lease = PropertyLease\n")
    chunks.append(runtime)
    portable = DIST / "TungTungRobloxRuntime.luau"
    portable.write_text("".join(chunks))

    entries = []
    for line in (SRC / "Catalog.luau").read_text().splitlines():
        if not line.strip().startswith("{id="):
            continue
        entry = {}
        for match in re.finditer(r'(\w+)=("(?:[^"\\]|\\.)*"|[0-9]+(?:\.[0-9]+)?)', line):
            entry[match[1]] = json.loads(match[2])
        entries.append(entry)
    assert len(entries) == 24 and len({entry["id"] for entry in entries}) == 24
    (ROOT / "catalog.json").write_text(json.dumps({"protocol":1,"runtimeVersion":"1.0.0","modules":entries}, indent=2, ensure_ascii=False) + "\n")

    compiled = []
    for path in sorted(SRC.glob("*.luau")) + sorted((ROOT / "tests").glob("*.luau")) + [portable]:
        subprocess.run([str(TOOLS / "luau-compile"), str(path)], check=True, stdout=subprocess.DEVNULL)
        compiled.append(str(path.relative_to(ROOT)))
    completed = subprocess.run([str(TOOLS / "luau"), str(ROOT / "tests/runtime.spec.luau")], check=True, text=True, capture_output=True)
    (DIST / "test-output.txt").write_text(completed.stdout)
    passed = len(re.findall(r"^PASS ", completed.stdout, re.M))
    assert passed >= 33 and f"RESULT {passed} passed" in completed.stdout
    report = {
        "passed": True, "compiler": "Official Luau 0.744", "compiledFiles": compiled,
        "mockChecksPassed": passed, "liveEngineTested": False,
        "connectedStudiosAtDiscovery": 0,
        "limitations": "Mocks do not simulate engine physics, replication, rendering, streaming, or third-party game compatibility.",
        "runtimeSha256": digest(portable), "catalogSha256":digest(ROOT / "catalog.json"),
        "sourceSha256":{str(p.relative_to(ROOT)):digest(p) for p in sorted(SRC.glob("*.luau"))},
    }
    (DIST / "build-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"passed":True,"mockChecksPassed":passed,"compiledFiles":len(compiled),"runtimeSha256":report["runtimeSha256"],"liveEngineTested":False}))


if __name__ == "__main__":
    main()
