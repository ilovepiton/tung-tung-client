"""Audit the frozen 1.29.4 Mac/core/Prism releases and their observed QA results."""
from pathlib import Path
import hashlib
import json
import plistlib
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
VERSION="1.29.4"
CORE_SHA="3c39d0fe51c324118d8342a73970ad24d64a7a85bf2e5e4d6d7a48c805befd6e"
OUT=ROOT/f"artifacts/loot-{VERSION}"
APP=ROOT/f"dist/Loot-{VERSION}/Tung Tung Client.app"
RES=APP/"Contents/Resources"


def main():
    checks=[]
    def check(value,label):
        assert value,label
        checks.append(label)
    engine=json.loads((OUT/"engine/summary.json").read_text())
    ui=json.loads((OUT/"ui/report.json").read_text())
    combat=json.loads((ROOT/f"artifacts/game-effects-{VERSION}/qa/summary.json").read_text())
    check(engine["ok"] and engine["coreSha256"]==CORE_SHA and engine["checks"]==85,"85 real-engine loot/guard checks")
    check(combat["ok"] and combat["coreSHA256"]==CORE_SHA and combat["checks"]==35 and combat["skipped"]==0,"35 combat regression checks, zero skips")
    check(ui["passed"] and len(ui["checks"])==17,"17 production UI checks")
    check(plistlib.loads((APP/"Contents/Info.plist").read_bytes())["CFBundleShortVersionString"]==VERSION,"Mac app version")
    subprocess.run(["codesign","--verify","--deep","--strict",str(APP)],check=True,capture_output=True)
    checks.append("Mac signature verification")
    check(set(subprocess.check_output(["xcrun","lipo","-archs",str(APP/"Contents/MacOS/TungTungClient")],text=True).split())=={"arm64","x86_64"},"Universal Mac executable")
    manifest=json.loads((RES/"payload/manifest.json").read_text())
    check(manifest["version"]==VERSION and manifest["desktopVersion"]==VERSION,"Payload versions match")
    for entry in manifest["files"]:
        check(hashlib.sha256((RES/"payload"/entry["path"]).read_bytes()).hexdigest()==entry["sha256"],"Payload checksum: "+entry["path"])
    artifacts=[]
    for name in [f"Tung-Tung-Client-Core-{VERSION}.zip",f"Tung-Tung-Client-Mac-{VERSION}-Voice-Chat.zip",f"Tung-Tung-Client-{VERSION}-Voice-Chat-Prism.zip"]:
        path=ROOT/"dist"/name
        sha=hashlib.sha256(path.read_bytes()).hexdigest()
        check(path.with_suffix(".zip.sha256").read_text().split()[0]==sha,"SHA256: "+name)
        with zipfile.ZipFile(path) as archive:
            check(archive.testzip() is None,"ZIP CRC: "+name)
            matching=[entry for entry in archive.namelist() if Path(entry).name==f"tung-tung-bridge-{VERSION}.jar"]
            check(len(matching)==1 and hashlib.sha256(archive.read(matching[0])).hexdigest()==CORE_SHA,"Tested core in "+name)
        artifacts.append({"path":str(path),"sha256":sha,"bytes":path.stat().st_size})
    previous=ROOT/"dist/Night-Vision-1.29.3/Tung Tung Client.app/Contents/Resources"
    for entry in json.loads((previous/"library/manifest.json").read_text())["files"]:
        check((RES/"library"/entry["path"]).read_bytes()==(previous/"library"/entry["path"]).read_bytes(),"Preserved cosmetic: "+entry["path"])
    for relative in ["src/Runtime.luau","src/Catalog.luau","dist/TungTungRobloxRuntime.luau"]:
        check((RES/"roblox"/relative).read_bytes()==(previous/"roblox"/relative).read_bytes(),"Preserved Roblox runtime: "+relative)
    with zipfile.ZipFile(ROOT/f"build/libs/tung-tung-bridge-{VERSION}.jar") as archive,zipfile.ZipFile(ROOT/"build/libs/tung-tung-bridge-1.29.3.jar") as old:
        check(not any("/qa/" in name or ".DS_Store" in name for name in archive.namelist()),"No QA classes or Finder metadata in core")
        for name in ["dev/amethyst/finder/modules/NightVisionModule.class","dev/amethyst/finder/modules/mixin/XrayLightmapMixin.class","dev/amethyst/finder/modules/pvp/ManualCombatAssist.class"]:
            check(archive.read(name)==old.read(name),"Preserved game implementation: "+name)
    report={"ok":True,"version":VERSION,"coreSha256":CORE_SHA,"checks":checks,"gameChecks":85,"combatChecks":35,"uiChecks":17,"artifacts":artifacts,"windows":"Native source compiled for .NET Framework 4.8; Windows gameplay not tested."}
    (OUT/"release-report.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps({"ok":True,"checks":len(checks),"report":str(OUT/"release-report.json")}))


if __name__=="__main__":main()
