"""Build the native universal macOS application and its portable ZIP."""
from pathlib import Path
import argparse
import hashlib
import json
import plistlib
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MAC = ROOT / "macos"
OUT = ROOT / "build" / "macos"
DIST = ROOT / "dist"
APP = DIST / "Tung-Tung-Client-Mac-1.30.4" / "Tung Tung Client.app"
VERSION = "1.30.4"
PAYLOAD_VERSION = "1.30.3"
VOICE_RUNTIME_VERSION = "1.12.0"
VOICE_RUNTIME_BUILD = "1120"
VOICE_FILE = "voicechat-fabric-2.6.24+26.3.jar"
VOICE_SHA512 = "414eb51967305fe7740d34016bc8e3e4e2fa1590e1278ad06bcdfa0687f65007b825d345dc09f79a992704a51ec93baf01fe727e0bbc32fdf506828b5a65d85a"

def run(args):
    subprocess.run([str(arg) for arg in args], check=True)

def build(voice_chat=False, binaries=None, runtime_binaries=None, make_archive=True):
    OUT.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(exist_ok=True)
    sources = sorted((MAC / "Sources").glob("*.swift"))
    if len(sources) < 3 or not (MAC / "ui" / "index.html").is_file():
        raise RuntimeError("Mac application sources/UI are incomplete")
    if APP.exists():
        shutil.rmtree(APP)
    contents = APP / "Contents"
    executable = contents / "MacOS" / "TungTungClient"
    resources = contents / "Resources"
    executable.parent.mkdir(parents=True)
    resources.mkdir()
    shutil.copytree(MAC / "ui", resources / "ui",
                    ignore=shutil.ignore_patterns("screenshots", "*-report.json", "__pycache__", "*.tmp"))
    (resources / "chess").mkdir()
    for name in ("engine.js", "native.js"):
        shutil.copy2(ROOT / "chess" / name, resources / "chess" / name)
    shutil.copytree(ROOT / "chess/extension", resources / "chess-extension")
    shutil.copy2(ROOT / "chess/engine.js", resources / "chess-extension/engine.js")
    shutil.copy2(ROOT / "macos/ui/assets/items/chesscom.png", resources / "chess-extension/icon.png")
    shutil.copytree(ROOT / "roblox", resources / "roblox",
                    ignore=shutil.ignore_patterns("tests", "luau-0.744", ".tools", "__pycache__", "*.tmp", "*.pyc"))
    # These are the frozen Windows source assets, copied without changing pixels/audio.
    assets = resources / "ui" / "assets"
    if not assets.exists():
        shutil.copytree(ROOT / "desktop" / "assets", assets,
                        ignore=shutil.ignore_patterns("tun-tun.png"))
    shutil.copytree(ROOT / ".tools/anti-leak-1.28.0/library", resources / "library")
    optional = json.loads((ROOT / ".tools/anti-leak-1.28.0/optional-mods.json").read_text())
    optional += json.loads((ROOT / "packaging/compatibility-mods.json").read_text())
    (resources / "optional-mods.json").write_text(json.dumps(optional, indent=2) + "\n")
    payload = resources / "payload"
    (payload / "mods").mkdir(parents=True)
    shutil.copy2(ROOT / "build" / "libs" / f"tung-tung-bridge-{PAYLOAD_VERSION}.jar", payload / "mods")
    shutil.copy2(ROOT / ".tools" / "fabric-api-0.162.0+26.3.jar", payload / "mods")
    shutil.copy2(ROOT / ".tools" / "map-art" / "client_maps-1.3.4.jar", payload / "mods")
    shutil.copy2(ROOT / ".tools" / "map-art" / "map-in-slot-3.5.0-tuntun.jar",
                 payload / "mods" / "map-in-slot-3.5.0.jar")
    if voice_chat:
        voice = ROOT / ".tools" / VOICE_FILE
        if voice.stat().st_size != 5612855 or hashlib.sha512(voice.read_bytes()).hexdigest() != VOICE_SHA512:
            raise RuntimeError("The Simple Voice Chat download does not match its official Modrinth hash")
        shutil.copy2(voice, payload / "mods")
    shutil.copy2(ROOT / ".tools" / "fabric-profile-26.3.json", payload / "fabric-profile.json")
    manifest = {"name": "Tung Tung Client", "version": PAYLOAD_VERSION,
                "desktopVersion": VERSION, "minecraft": "26.3", "loader": "0.19.5",
                "simpleVoiceChat": voice_chat, "files": []}
    for path in sorted(payload.rglob("*")):
        if path.is_file():
            manifest["files"].append({"path": path.relative_to(payload).as_posix(),
                                      "kind": "profile" if path.name == "fabric-profile.json" else "mod",
                                      "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    (payload / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for name, source in [("LICENSE.txt", ROOT / "LICENSE"),
                         ("FABRIC-API-LICENSE.txt", ROOT / ".tools" / "FABRIC-API-LICENSE.txt"),
                         ("CLIENT-MAPS-LICENSE.txt", ROOT / ".tools" / "map-art" / "CLIENT-MAPS-LICENSE.txt"),
                         ("MAP-IN-SLOT-LICENSE.txt", ROOT / ".tools" / "map-art" / "MAP-IN-SLOT-LICENSE.txt"),
                         ("COMPATIBILITY-MODS-NOTICE.txt", ROOT / "packaging" / "COMPATIBILITY-MODS-NOTICE.txt")]:
        shutil.copy2(source, resources / name)
    if (MAC / "README.txt").is_file():
        shutil.copy2(MAC / "README.txt", resources / "README.txt")
    shutil.copytree(ROOT / "docs", resources / "Instructions")
    if voice_chat:
        for name in ("VOICE-CHAT-SETUP.txt", "SIMPLE-VOICE-CHAT-NOTICE.txt"):
            shutil.copy2(MAC / name, resources / name)
        pack = DIST / f"Tung-Tung-Client-{PAYLOAD_VERSION}-Voice-Chat-Prism.zip"
        if not pack.is_file():
            raise RuntimeError("Build the combined Prism import profile before the Voice Chat app")
        shutil.copy2(pack, resources / pack.name)
        readme = resources / "README.txt"
        readme.write_text((MAC / "VOICE-CHAT-SETUP.txt").read_text() + "\n\n" + readme.read_text())
    if (MAC / "AppIcon.icns").is_file():
        shutil.copy2(MAC / "AppIcon.icns", resources / "AppIcon.icns")
    info = {
        "CFBundleName": "Tung Tung Client", "CFBundleDisplayName": "Tung Tung Client",
        "CFBundleIdentifier": "dev.tungtung.client", "CFBundleExecutable": "TungTungClient",
        "CFBundlePackageType": "APPL", "CFBundleShortVersionString": VERSION,
        "CFBundleVersion": "1304", "LSMinimumSystemVersion": "13.0",
        "NSHighResolutionCapable": True, "NSPrincipalClass": "NSApplication",
        "NSMicrophoneUsageDescription": "Simple Voice Chat in Tung Tung Minecraft needs your microphone so other players can hear you.",
        "NSAppTransportSecurity": {"NSAllowsLocalNetworking": True},
        "NSSupportsAutomaticGraphicsSwitching": True,
        "NSHumanReadableCopyright": "Tung Tung Client — desktop game companion",
        "TungTungSimpleVoiceChat": voice_chat,
    }
    if (resources / "AppIcon.icns").is_file():
        info["CFBundleIconFile"] = "AppIcon"
    (contents / "Info.plist").write_bytes(plistlib.dumps(info, fmt=plistlib.FMT_XML))
    sdk = subprocess.check_output(["xcrun", "--show-sdk-path"], text=True).strip()
    if binaries is None:
        binaries = []
        for architecture in ("arm64", "x86_64"):
            binary = OUT / f"TungTungClient-{architecture}"
            run(["xcrun", "swiftc", "-swift-version", "5", "-O", "-sdk", sdk,
                 "-target", f"{architecture}-apple-macos13.0", "-framework", "Cocoa", "-framework", "WebKit",
                 *sources, "-o", binary])
            binaries.append(binary)
    run(["xcrun", "lipo", "-create", *binaries, "-output", executable])
    executable.chmod(0o755)
    runtime_source = MAC / "RuntimeSources/VoiceJava.swift"
    host = resources / "Tung Tung Minecraft.app"
    host_contents = host / "Contents"
    (host_contents / "MacOS").mkdir(parents=True)
    host_info = {"CFBundleName": "Tung Tung Minecraft", "CFBundleDisplayName": "Tung Tung Minecraft",
                 "CFBundleIdentifier": "dev.tungtung.minecraft", "CFBundleExecutable": "TungTungVoiceHost",
                 "CFBundlePackageType": "APPL", "CFBundleShortVersionString": VOICE_RUNTIME_VERSION, "CFBundleVersion": VOICE_RUNTIME_BUILD,
                 "LSMinimumSystemVersion": "13.0", "LSUIElement": True,
                 "NSMicrophoneUsageDescription": "Simple Voice Chat in Tung Tung Minecraft needs your microphone so other players can hear you."}
    (host_contents / "Info.plist").write_bytes(plistlib.dumps(host_info))
    for kind in ("shim", "host"):
        parts = list(runtime_binaries[kind]) if runtime_binaries is not None else []
        if runtime_binaries is None:
            for architecture in ("arm64", "x86_64"):
                part = OUT / f"voice-{kind}-{architecture}"
                flags = ["-D", "VOICE_HOST"] if kind == "host" else []
                run(["xcrun", "swiftc", "-parse-as-library", "-swift-version", "5", "-O", "-sdk", sdk,
                     "-target", f"{architecture}-apple-macos13.0", "-framework", "Cocoa", "-framework", "AVFoundation",
                     *flags, runtime_source, "-o", part])
                parts.append(part)
        destination = host_contents / "MacOS/TungTungVoiceHost" if kind == "host" else contents / "MacOS/TungTungJava"
        run(["xcrun", "lipo", "-create", *parts, "-output", destination])
        destination.chmod(0o755)
    run(["codesign", "--force", "--deep", "--sign", "-", host])
    run(["codesign", "--force", "--deep", "--sign", "-", APP])
    run(["codesign", "--verify", "--deep", "--strict", APP])
    if not make_archive:
        return binaries
    suffix = "-Voice-Chat" if voice_chat else ""
    archive = DIST / f"Tung-Tung-Client-Mac-{VERSION}{suffix}.zip"
    if archive.exists():
        archive.unlink()
    run(["ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", APP, archive])
    sha = hashlib.sha256(archive.read_bytes()).hexdigest()
    (DIST / (archive.name + ".sha256")).write_text(sha + "  " + archive.name + "\n")
    print(f"{archive}\n{archive.stat().st_size / 1024**2:.2f} MiB\nSHA-256 {sha}")
    return binaries

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--voice-chat", action="store_true", help="Bundle Simple Voice Chat and a Prism import profile")
    group.add_argument("--all-variants", action="store_true", help="Build both ZIPs from the same universal native binary")
    args = parser.parse_args()
    if args.all_variants:
        compiled = build()
        build(voice_chat=True, binaries=compiled)
    else:
        build(voice_chat=args.voice_chat)
