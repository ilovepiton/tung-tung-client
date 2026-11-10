"""Run a disposable offline Minecraft 26.3 client and verify real Loot Protection / Item Finder rendering.

Requires the project's existing verified official dependency cache and Java 25.
Never reads account files or modifies the user's running game/profile.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
options=argparse.ArgumentParser(description=__doc__)
options.add_argument('--version',default='1.29.5')
options.add_argument('--baseline',action='store_true',help='Keep pre-fix regression evidence separately.')
options.add_argument('--timeout',type=int,default=600)
options.add_argument('--suite',choices=['loot','utilities'],default='loot')
options.add_argument('--prepare-only',action='store_true',help='Compile QA without starting Minecraft.')
options.add_argument('--delay-inventory-ms',type=int,default=0,help='Utilities only: delay server inventory echoes after the actual totem pop.')
arguments=options.parse_args()
VERSION = arguments.version
WORK = ROOT / f"build/loot-qa-{VERSION}-{arguments.suite}"
GAME = WORK / "game"
CLASSES = WORK / "classes"
OUTPUT = ROOT / f"artifacts/loot-{VERSION}" / (('anchor-placement-baseline' if arguments.suite=='loot' else 'totem-refill-baseline') if arguments.baseline else 'engine' if arguments.suite=='loot' else 'utility-engine')
JAVA = Path.home() / "Library/Application Support/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home/bin"


def main():
    for path in [GAME / "mods", GAME / "config", CLASSES, OUTPUT]:
        path.mkdir(parents=True, exist_ok=True)
    # This is the dedicated generated QA world, never a user save directory.
    world=GAME / 'saves' / ('LootQA' if arguments.suite=='loot' else 'UtilityQA')
    if world.exists() and not arguments.prepare_only:shutil.rmtree(world)
    for name in ["report.json", "summary.json", "01-cave-off.png", "02-cave-on.png", "03-cave-restored.png"]:
        if not arguments.prepare_only:(OUTPUT / name).unlink(missing_ok=True)
    core = ROOT / f"build/libs/tung-tung-bridge-{VERSION}.jar"
    classpath = (ROOT / ".tools/release-1.10.0/classpath.txt").read_text().strip().split(os.pathsep)
    compile_cp = classpath + [str(core)] + list(map(str,(ROOT / ".tools/release-1.10.0/compile-fabric").glob("*.jar")))
    subprocess.run([str(JAVA / "javac"), "--release", "25", "-proc:none", "-encoding", "UTF-8",
        "-cp", os.pathsep.join(compile_cp), "-d", str(CLASSES),
        *map(str,(ROOT / "src/qa/java").rglob("*.java"))],check=True)
    with zipfile.ZipFile(GAME / "mods/loot-qa.jar","w",zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json",json.dumps({"schemaVersion":1,"id":"night_vision_qa","version":"1",
            "environment":"client","entrypoints":{"client":["qa.LootQa" if arguments.suite=='loot' else "qa.CombatUtilityQa"]},"mixins":["qa.mixins.json"],
            "depends":{"amethyst_chunk_finder":"*"}}))
        archive.writestr("qa.mixins.json",json.dumps({"required":True,"package":"qa.mixin","compatibilityLevel":"JAVA_25",
            "client":["NightVisionMouseIsolationMixin","UtilityFocusMixin",*(["LootPacketProbe"] if arguments.suite=='loot' else ["UtilityPacketProbe","UtilityReceiptProbe","UtilityServerProbe","UtilityRenderProbe","UtilityLabelProbe","UtilityDelayMixin"])],"injectors":{"defaultRequire":1}}))
        for path in CLASSES.rglob("*.class"): archive.write(path,path.relative_to(CLASSES))
    if arguments.prepare_only:
        result={"prepared":True,"minecraftStarted":False,"userProfileTouched":False,"version":VERSION,"suite":arguments.suite,
            "coreSha256":hashlib.sha256(core.read_bytes()).hexdigest(),"delayInventoryMs":arguments.delay_inventory_ms,
            "qaJarSha256":hashlib.sha256((GAME / 'mods/loot-qa.jar').read_bytes()).hexdigest()}
        (OUTPUT / 'prepared.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result,indent=2));return
    for path in (GAME / "mods").glob("tung-tung-bridge-*.jar"): path.unlink()
    for path in [core,ROOT / ".tools/fabric-api-0.162.0+26.3.jar"]: shutil.copy2(path,GAME / "mods" / path.name)
    (GAME / "options.txt").write_text("resourcePacks:[]\nenableVsync:true\nmaxFps:60\nrenderDistance:2\nsimulationDistance:5\nfullscreen:false\n")
    natives = ROOT / ".tools/combat-1.13/natives"
    metadata = json.loads((ROOT / ".tools/minecraft-26.3.json").read_text())
    launch_arguments = [str(JAVA / "java"),"-XstartOnFirstThread","-Xmx1200M","-XX:ActiveProcessorCount=2",f"-Dtung.qa.output={OUTPUT}",
        f"-Dtung.qa.delayInventoryMs={arguments.delay_inventory_ms}","--enable-native-access=ALL-UNNAMED",f"-Djava.library.path={natives}",
        f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}","-cp",os.pathsep.join(classpath),
        "net.fabricmc.loader.impl.launch.knot.KnotClient","--username","LootQA",
        "--uuid","11223344556677889900112233445566","--accessToken","0","--userType","msa",
        "--version","26.3","--versionType","release","--gameDir",str(GAME),
        "--assetsDir",str(Path.home() / "Library/Application Support/minecraft/assets"),
        "--assetIndex",metadata["assetIndex"]["id"],"--width","960","--height","640"]
    with (OUTPUT / "launch.log").open("w") as log:
        child = subprocess.Popen(launch_arguments,cwd=GAME,stdout=log,stderr=subprocess.STDOUT)
        print(f"Disposable offline Loot Protection / Item Finder QA started (PID {child.pid}).",flush=True)
        try: code=child.wait(timeout=arguments.timeout)
        except subprocess.TimeoutExpired:
            child.terminate(); child.wait(timeout=20); code='timeout'
    report = json.loads((OUTPUT / "report.json").read_text()) if (OUTPUT / "report.json").exists() else {}
    passed = code==0 and report.get("stage")== (10 if arguments.suite=='loot' else 30) and not report.get("error") and len(report.get("checks",[]))>=(100 if arguments.suite=='loot' else 30) and all(item["passed"] for item in report["checks"])
    summary = {"ok":passed,"exit":code,"checks":len(report.get("checks",[])),"error":report.get("error"),
        "coreSha256":hashlib.sha256(core.read_bytes()).hexdigest(),"luminance":report.get("luminance",{}),
        "suite":arguments.suite,"syntheticAccount":True,"externalServer":False,"userProfileTouched":False}
    (OUTPUT / "summary.json").write_text(json.dumps(summary,indent=2)+"\n")
    print(json.dumps(summary,indent=2))
    raise SystemExit(0 if passed else 1)


if __name__=="__main__": main()
