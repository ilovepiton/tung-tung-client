"""Install the verified 1.29.5 combat utilities / anchor fix release locally.
Uses the existing staged updater and its rollback behavior. Defaults to checking
running processes; --apply requires normal Minecraft/Launcher/client shutdown.
"""
from pathlib import Path
import argparse
import importlib.util
import json

ROOT=Path(__file__).resolve().parents[1]
VERSION="1.29.5"
EXPECTED="f1d1a4af8bef53147fa986a6f05b289e5d4f4b70f8e81a4dacdc153dc39d632a"
spec=importlib.util.spec_from_file_location("local_updater",ROOT/"scripts/install-loot.py")
updater=importlib.util.module_from_spec(spec);spec.loader.exec_module(updater)
updater.VERSION=VERSION;updater.EXPECTED=EXPECTED
updater.SOURCE=ROOT/f"dist/Loot-{VERSION}/Tung Tung Client.app"
updater.CORE=ROOT/f"build/libs/tung-tung-bridge-{VERSION}.jar"
updater.OUTPUT=ROOT/f"artifacts/loot-{VERSION}/installation.json"

def apply():
    folder=ROOT/f"artifacts/loot-{VERSION}"
    utilities=json.loads((folder/"utility-engine/summary.json").read_text())
    release=json.loads((folder/"release-report.json").read_text())
    assert utilities["ok"] and utilities["coreSha256"]==EXPECTED and utilities["checks"]==40
    assert release["ok"] and release["coreSha256"]==EXPECTED
    return updater.apply()

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--apply",action="store_true");args=parser.parse_args()
    print(json.dumps(apply() if args.apply else {"checkOnly":True,"runningProcesses":updater.blockers()},indent=2))
