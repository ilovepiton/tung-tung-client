"""Run a disposable offline Minecraft 26.3 client and verify real Night Vision rendering.

Requires the project's existing verified official dependency cache and Java 25.
Never reads account files or modifies the user's running game/profile.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.29.3"
WORK = ROOT / "build/night-vision-qa"
GAME = WORK / "game"
CLASSES = WORK / "classes"
OUTPUT = ROOT / f"artifacts/night-vision-{VERSION}/engine"
JAVA = Path.home() / "Library/Application Support/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home/bin"


def main():
    for path in [GAME / "mods", GAME / "config", CLASSES, OUTPUT]:
        path.mkdir(parents=True, exist_ok=True)
    # This is the dedicated generated QA world, never a user save directory.
    if (GAME / "saves/NightVisionQA").exists():
        shutil.rmtree(GAME / "saves/NightVisionQA")
    for name in ["report.json", "summary.json", "01-cave-off.png", "02-cave-on.png", "03-cave-restored.png"]:
        (OUTPUT / name).unlink(missing_ok=True)
    core = ROOT / f"build/libs/tung-tung-bridge-{VERSION}.jar"
    classpath = (ROOT / ".tools/release-1.10.0/classpath.txt").read_text().strip().split(os.pathsep)
    compile_cp = classpath + [str(core)] + list(map(str,(ROOT / ".tools/release-1.10.0/compile-fabric").glob("*.jar")))
    subprocess.run([str(JAVA / "javac"), "--release", "25", "-proc:none", "-encoding", "UTF-8",
        "-cp", os.pathsep.join(compile_cp), "-d", str(CLASSES),
        *map(str,(ROOT / "src/qa/java").rglob("*.java"))],check=True)
    with zipfile.ZipFile(GAME / "mods/night-vision-qa.jar","w",zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json",json.dumps({"schemaVersion":1,"id":"night_vision_qa","version":"1",
            "environment":"client","entrypoints":{"client":["qa.NightVisionQa"]},"mixins":["qa.mixins.json"],
            "depends":{"amethyst_chunk_finder":"*"}}))
        archive.writestr("qa.mixins.json",json.dumps({"required":True,"package":"qa.mixin","compatibilityLevel":"JAVA_25",
            "client":["NightVisionMouseIsolationMixin"],"injectors":{"defaultRequire":1}}))
        for path in CLASSES.rglob("*.class"): archive.write(path,path.relative_to(CLASSES))
    for path in (GAME / "mods").glob("tung-tung-bridge-*.jar"): path.unlink()
    for path in [core,ROOT / ".tools/fabric-api-0.162.0+26.3.jar"]: shutil.copy2(path,GAME / "mods" / path.name)
    (GAME / "options.txt").write_text("resourcePacks:[]\nenableVsync:true\nmaxFps:60\nrenderDistance:2\nsimulationDistance:5\nfullscreen:false\n")
    natives = ROOT / ".tools/combat-1.13/natives"
    metadata = json.loads((ROOT / ".tools/minecraft-26.3.json").read_text())
    arguments = [str(JAVA / "java"),"-XstartOnFirstThread","-Xmx1200M",f"-Dtung.qa.output={OUTPUT}",
        "--enable-native-access=ALL-UNNAMED",f"-Djava.library.path={natives}",
        f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}","-cp",os.pathsep.join(classpath),
        "net.fabricmc.loader.impl.launch.knot.KnotClient","--username","NightVisionQA",
        "--uuid","11223344556677889900112233445566","--accessToken","0","--userType","msa",
        "--version","26.3","--versionType","release","--gameDir",str(GAME),
        "--assetsDir",str(Path.home() / "Library/Application Support/minecraft/assets"),
        "--assetIndex",metadata["assetIndex"]["id"],"--width","960","--height","640"]
    with (OUTPUT / "launch.log").open("w") as log:
        child = subprocess.Popen(arguments,cwd=GAME,stdout=log,stderr=subprocess.STDOUT)
        print(f"Disposable offline Night Vision QA started (PID {child.pid}).",flush=True)
        try: code=child.wait(timeout=240)
        except subprocess.TimeoutExpired:
            child.terminate(); child.wait(timeout=20); raise
    report = json.loads((OUTPUT / "report.json").read_text()) if (OUTPUT / "report.json").exists() else {}
    passed = code==0 and report.get("stage")==10 and not report.get("error") and len(report.get("checks",[]))>=18 and all(item["passed"] for item in report["checks"])
    summary = {"ok":passed,"exit":code,"checks":len(report.get("checks",[])),"error":report.get("error"),
        "coreSha256":hashlib.sha256(core.read_bytes()).hexdigest(),"luminance":report.get("luminance",{}),
        "syntheticAccount":True,"externalServer":False,"userProfileTouched":False}
    (OUTPUT / "summary.json").write_text(json.dumps(summary,indent=2)+"\n")
    print(json.dumps(summary,indent=2))
    raise SystemExit(0 if passed else 1)


if __name__=="__main__": main()
