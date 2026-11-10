"""Install verified Night Vision 1.29.3 after normal game/launcher shutdown.

Stages both app copies before changing the active core. Keeps previous apps and
core JARs for rollback. Does not open account files or change game settings.
Defaults to a process check; pass --apply to install.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import plistlib
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.29.3"
EXPECTED = "1bfa7eada5942b36a036d693645eefcdbdcc706e12232df79ed40b09a8a773a3"
SOURCE = ROOT / f"dist/Night-Vision-{VERSION}/Tung Tung Client.app"
CORE = ROOT / f"build/libs/tung-tung-bridge-{VERSION}.jar"
PROFILE = Path.home() / "Library/Application Support/minecraft/tun-tun-26.3"
OUTPUT = ROOT / f"artifacts/night-vision-{VERSION}/installation.json"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def blockers():
    result = []
    for line in subprocess.check_output(["ps", "-axo", "pid=,comm="],text=True).splitlines():
        parts = line.strip().split(None,1)
        if len(parts)!=2: continue
        pid, executable = parts
        if executable.endswith(("/bin/java","/TungTungJava","/TungTungVoiceHost","/TungTungClient")) or "/minecraft.app/" in executable.lower() or "minecraft launcher.app/" in executable.lower() or "/minecraft/launcher/launcher.bundle/" in executable.lower():
            result.append({"pid":int(pid),"executable":executable})
    return result


def verify_app(app):
    info = plistlib.loads((app / "Contents/Info.plist").read_bytes())
    assert info["CFBundleShortVersionString"]==VERSION
    assert digest(app / "Contents/Resources/payload/mods" / CORE.name)==EXPECTED
    subprocess.run(["codesign","--verify","--deep","--strict",str(app)],check=True)


def apply():
    assert digest(CORE)==EXPECTED, "Core changed after validation."
    qa = json.loads((ROOT / f"artifacts/night-vision-{VERSION}/engine/summary.json").read_text())
    assert qa["ok"] and qa["coreSha256"]==EXPECTED, "Final core has not passed the real-engine checks."
    assert json.loads((ROOT / f"artifacts/night-vision-{VERSION}/ui/report.json").read_text())["passed"]
    verify_app(SOURCE)
    if blockers(): return {"installed":False,"reason":"Close Minecraft, Launcher and Tung Tung Client normally.","runningProcesses":blockers()}
    mods = PROFILE / "mods"
    assert mods.is_dir(), "Official Tung Tung profile not found."
    stamp = uuid.uuid4().hex
    backup = ROOT / f"build/install-backups/night-vision-{VERSION}" / stamp
    (backup / "mods").mkdir(parents=True)
    apps = [path for path in [Path("/Applications/Tung Tung Client.app"),Path.home() / "Applications/Tung Tung Client.app"] if path.is_dir()]
    assert apps, "Installed Tung Tung Client not found."
    staged, replaced, saved_mods = [], [], []
    pending = mods / (CORE.name+".pending")
    destination = mods / CORE.name
    try:
        for index, app in enumerate(apps):
            # /Applications itself may be protected while this user's existing
            # app bundle is writable. Stage on the same volume in the workspace.
            temporary = backup / "staging" / str(index) / app.name
            temporary.parent.mkdir(parents=True)
            staged.append(temporary)
            subprocess.run(["/bin/cp","-cR",str(SOURCE),str(temporary)],check=True)
            verify_app(temporary)
        shutil.copy2(CORE,pending)
        assert digest(pending)==EXPECTED
        if blockers(): raise RuntimeError("Game or desktop restarted before replacement; installation cancelled.")
        for index, (app, temporary) in enumerate(zip(apps,staged)):
            saved = backup / f"application-{index}" / app.name
            saved.mkdir(parents=True)
            (app / "Contents").rename(saved / "Contents")
            replaced.append((app,saved))
            (temporary / "Contents").rename(app / "Contents")
            verify_app(app)
        for old in mods.glob("tung-tung-bridge-*.jar"):
            saved = backup / "mods" / old.name
            old.rename(saved); saved_mods.append((old,saved))
        os.replace(pending,destination)
        assert digest(destination)==EXPECTED
        report = {"installed":True,"version":VERSION,"coreSha256":EXPECTED,
            "applications":list(map(str,apps)),"profile":str(PROFILE),"backup":str(backup),
            "gameConfigurationChanged":False,"otherModsChanged":False,"accountFilesAccessed":False,
            "resourcePacksChanged":False,"microphoneSettingsChanged":False}
        OUTPUT.parent.mkdir(parents=True,exist_ok=True)
        OUTPUT.write_text(json.dumps(report,indent=2)+"\n")
        return report
    except BaseException:
        # Only remove the new core if the old core was actually moved to backup.
        if saved_mods:
            destination.unlink(missing_ok=True)
            for old,saved in reversed(saved_mods): saved.rename(old)
        for app,saved in reversed(replaced):
            if (app / "Contents").exists(): shutil.rmtree(app / "Contents")
            (saved / "Contents").rename(app / "Contents")
        raise
    finally:
        pending.unlink(missing_ok=True)
        for path in staged:
            if path.exists(): shutil.rmtree(path)


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply",action="store_true")
    options=parser.parse_args()
    print(json.dumps(apply() if options.apply else {"checkOnly":True,"runningProcesses":blockers()},indent=2))
