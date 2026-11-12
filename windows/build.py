"""Package the Windows companion with the shared, production desktop UI."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.31.0"
CORE = "1.31.0"
DEPS = ROOT / ".tools/windows-build"
STAGE = ROOT / "dist" / f"Tung-Tung-Client-Windows-{VERSION}"


def copy_tree(source, destination, ignore_patterns=()):
    """Synchronize a generated tree without needing a second full disk copy."""
    source, destination = Path(source), Path(destination)
    ignore = shutil.ignore_patterns(*ignore_patterns)
    if destination.exists():
        allowed = set()
        for path in source.rglob("*"):
            relative = path.relative_to(source)
            if any(part in ignore(source, [part]) for part in relative.parts):
                continue
            allowed.add(relative)
        for path in sorted(destination.rglob("*"), key=lambda item: len(item.parts), reverse=True):
            if path.relative_to(destination) not in allowed:
                if path.is_dir(): path.rmdir()
                else: path.unlink()
    shutil.copytree(source, destination, dirs_exist_ok=True, ignore=ignore)


def build(reuse_stage=None):
    subprocess.run([str(DEPS / "powershell/pwsh"), "-NoLogo", "-NoProfile", "-File", str(ROOT / "windows/compile.ps1"), "-OutputDirectory", str(ROOT / "build/windows")], check=True)
    if reuse_stage is not None:
        previous = Path(reuse_stage).resolve()
        retained_archive = previous.with_name(previous.name + "-Voice-Chat.zip")
        if previous.parent != (ROOT / "dist").resolve() or not previous.name.startswith("Tung-Tung-Client-Windows-") or not previous.is_dir() or not retained_archive.is_file() or STAGE.exists():
            raise ValueError("Reuse needs a previous generated Windows stage under dist, its retained ZIP, and no new stage")
        previous.rename(STAGE)
    elif STAGE.exists():
        shutil.rmtree(STAGE)
    STAGE.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "build/windows/Tung-Tung-Client.exe", STAGE)
    for name in ("START-TUNG-TUNG-CLIENT.cmd", "START-HERE.txt", "Tung-Tung-Client.exe.config"):
        shutil.copy2(ROOT / "windows" / name, STAGE)
    instructions = STAGE / "START-HERE.txt"
    copy_tree(ROOT / "docs", STAGE / "Instructions")
    lines = instructions.read_text().splitlines()
    lines[0] = f"TUNG TUNG CLIENT {VERSION} — Minecraft Java 26.3 / Fabric Core {CORE}"
    instructions.write_text("\n".join(lines) + "\n\nROBLOX WORKSPACE\nChoose Roblox in the game selector. The 24 controls require a compatible in-game runtime.\nOpen Connect & Setup > OPEN RUNTIME PACKAGE for the module and host contract.\nOPEN PAIRING FOLDER shows the private native connection directory. Keep its descriptor private.\nJoining a private server does not install a runtime. No injector or executor is bundled.\nFriend Fling requires target opt-in and a compatible game-side server adapter.\n")
    (STAGE / "desktop").mkdir(exist_ok=True)
    shutil.copy2(ROOT / "windows/Install-Core.ps1", STAGE / "desktop")
    copy_tree(ROOT / "macos/ui", STAGE / "ui", ("screenshots", "*-report.json", "*.tmp"))
    shutil.copy2(ROOT / "windows/microphone.js", STAGE / "ui/microphone.js")
    shutil.copy2(ROOT / "windows/windows-bridge.js", STAGE / "ui/windows-bridge.js")
    index = STAGE / "ui/index.html"
    index.write_text(index.read_text().replace('<script src="asset-bounds.js">', '<script src="windows-bridge.js"></script><script src="asset-bounds.js">'))
    client = STAGE / "ui/client.js"
    # A global chrome() function would overwrite WebView2's native chrome object.
    client.write_text(client.read_text().replace("function chrome(){", "function clientChrome(){").replace("sidebar()+chrome()+content", "sidebar()+clientChrome()+content"))
    client.write_text(client.read_text().replace(" / macOS", " / Windows").replace("One Mac client.", "One Windows client.").replace("Show folder in Finder", "Show folder in Explorer").replace("Open Tung Tung Client.app", "Open Tung-Tung-Client.exe").replace("its menu-bar icon", "its system-tray icon").replace("from the Dock", "from the taskbar").replace("MAC CLIENT", "WINDOWS CLIENT").replace("MAC MICROPHONE", "WINDOWS MICROPHONE").replace("Apply the microphone fix once. Minecraft Launcher keeps your account and profile. Allow Tung Tung Minecraft when macOS asks, then use V to select your microphone in Simple Voice Chat.", "Press V in Minecraft to select your input, unmute and test your microphone. Windows must allow microphone access for desktop apps. Multiplayer voice needs Simple Voice Chat on the server too."))
    for name in ("startup-meow.wav", "ui-click.wav"):
        shutil.copy2(ROOT / "desktop/assets" / name, STAGE / "ui/assets" / name)
    for name in ("Core", "WinForms"):
        shutil.copy2(DEPS / f"webview2/lib/net462/Microsoft.Web.WebView2.{name}.dll", STAGE)
    shutil.copy2(DEPS / "webview2/runtimes/win-x64/native/WebView2Loader.dll", STAGE)
    shutil.copy2(DEPS / "newtonsoft/lib/net45/Newtonsoft.Json.dll", STAGE)
    bootstrapper = DEPS / "MicrosoftEdgeWebview2Setup.exe"
    if bootstrapper.read_bytes()[:2] != b"MZ":
        raise ValueError("Microsoft WebView2 installer is not a Windows executable")
    shutil.copy2(bootstrapper, STAGE)
    (STAGE / "licenses").mkdir(exist_ok=True)
    shutil.copy2(DEPS / "webview2/NOTICE.txt", STAGE / "licenses/WEBVIEW2-NOTICE.txt")
    for name, source in [("LICENSE.txt", ROOT / "LICENSE"), ("FABRIC-API-LICENSE.txt", ROOT / ".tools/FABRIC-API-LICENSE.txt"), ("SIMPLE-VOICE-CHAT-NOTICE.txt", ROOT / "macos/SIMPLE-VOICE-CHAT-NOTICE.txt"), ("COMPATIBILITY-MODS-NOTICE.txt", ROOT / "packaging/COMPATIBILITY-MODS-NOTICE.txt"), ("CLIENT-MAPS-LICENSE.txt", ROOT / ".tools/map-art/CLIENT-MAPS-LICENSE.txt"), ("MAP-IN-SLOT-LICENSE.txt", ROOT / ".tools/map-art/MAP-IN-SLOT-LICENSE.txt"), ("WEBVIEW2-LICENSE.txt", DEPS / "webview2/LICENSE.txt"), ("NEWTONSOFT-LICENSE.txt", DEPS / "newtonsoft/LICENSE.md")]:
        shutil.copy2(source, STAGE / "licenses" / name)
    (STAGE / "source").mkdir(exist_ok=True)
    for name in ("Client.cs", "Bridge.cs", "RobloxBridge.cs", "compile.ps1", "build.py"):
        shutil.copy2(ROOT / "windows" / name, STAGE / "source" / name)
    copy_tree(ROOT / "roblox", STAGE / "roblox", ("tests", "tools", ".tools", "cache", ".cache", "__pycache__", "*.pyc", "*.tmp", "test-output.txt", ".DS_Store"))
    payload = STAGE / "payload"
    (payload / "mods").mkdir(parents=True, exist_ok=True)
    for old_core in (payload / "mods").glob("tung-tung-bridge-*.jar"):
        if old_core.name != f"tung-tung-bridge-{CORE}.jar": old_core.unlink()
    for source in (ROOT / f"build/libs/tung-tung-bridge-{CORE}.jar", ROOT / ".tools/fabric-api-0.162.0+26.3.jar", ROOT / ".tools/voicechat-fabric-2.6.27+26.3.jar", ROOT / ".tools/map-art/client_maps-1.3.4.jar"):
        shutil.copy2(source, payload / "mods" / source.name)
    shutil.copy2(ROOT / ".tools/map-art/map-in-slot-3.5.0-tuntun.jar", payload / "mods/map-in-slot-3.5.0.jar")
    copy_tree(ROOT / ".tools/anti-leak-1.28.0/library/resourcepacks", payload / "resourcepacks")
    shutil.copy2(ROOT / ".tools/anti-leak-1.28.0/library/THIRD-PARTY-NOTICES.txt", STAGE / "licenses/TEXTURE-PACKS.txt")
    shutil.copy2(ROOT / ".tools/fabric-profile-26.3.json", payload / "fabric-profile.json")
    manifest = {"name": "Tung Tung Client", "version": VERSION, "gameCoreVersion": CORE, "minecraft": "26.3", "simpleVoiceChat": True, "platform": "Windows x64", "files": []}
    for path in sorted(payload.rglob("*")):
        if path.is_file():
            manifest["files"].append({"path": path.relative_to(STAGE).as_posix(), "kind": "resourcepack" if path.parent.name == "resourcepacks" else "profile" if path.suffix == ".json" else "mod", "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest["downloads"] = json.loads((ROOT / ".tools/anti-leak-1.28.0/optional-mods.json").read_text()) + json.loads((ROOT / "packaging/compatibility-mods.json").read_text())
    (STAGE / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    archive = ROOT / f"dist/Tung-Tung-Client-Windows-{VERSION}-Voice-Chat.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for path in sorted(STAGE.rglob("*")):
            if path.is_file(): z.write(path, path.relative_to(STAGE.parent))
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    archive.with_name(archive.name + ".sha256").write_text(digest + "  " + archive.name + "\n")
    print(json.dumps({"archive": str(archive), "bytes": archive.stat().st_size, "sha256": digest}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reuse-stage", type=Path, help="Move and update a previous generated stage while retaining its existing ZIP")
    build(reuse_stage=parser.parse_args().reuse_stage)
