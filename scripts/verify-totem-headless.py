"""Check delayed Auto Totem packet ordering without starting Minecraft or touching its profile."""
from pathlib import Path
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAVA=Path.home()/"Library/Application Support/minecraft/runtime/java-runtime-epsilon/mac-os-arm64/java-runtime-epsilon/jre.bundle/Contents/Home/bin"
OUT=ROOT/"build/totem-refill-headless"
REPORT=ROOT/"artifacts/totem-refill-1.29.6/headless.json"

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(JAVA/"javac"),"--release","25","-d",str(OUT),
        str(ROOT/"src/main/java/dev/amethyst/finder/modules/pvp/TotemRefillGate.java"),
        str(ROOT/"src/qa/headless/TotemRefillGateChecks.java")],check=True)
    result=subprocess.run([str(JAVA/"java"),"-cp",str(OUT),"TotemRefillGateChecks"],check=True,text=True,capture_output=True)
    report=json.loads(result.stdout)
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    REPORT.write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))

if __name__=="__main__":main()
