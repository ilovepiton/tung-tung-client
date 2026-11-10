"""Reversible desktop-only Chess update. No Minecraft core/profile writes. Default: check only."""
from pathlib import Path
import argparse
import hashlib
import json
import plistlib
import shutil
import subprocess
import uuid

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'dist/Chess-1.29.7/Tung Tung Client.app'
QA=ROOT/'artifacts/chess-auto-next'
PROFILE=Path.home()/'Library/Application Support/minecraft/tun-tun-26.3/mods'
VOICE=['Contents/MacOS/TungTungJava','Contents/Resources/Tung Tung Minecraft.app/Contents/Info.plist',
       'Contents/Resources/Tung Tung Minecraft.app/Contents/MacOS/TungTungVoiceHost']

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def blockers():
    result=[]
    for line in subprocess.check_output(['ps','-axo','pid=,comm='],text=True).splitlines():
        parts=line.strip().split(None,1)
        if len(parts)==2 and parts[1].endswith('/TungTungClient'): result.append({'pid':int(parts[0]),'executable':parts[1]})
    return result
def verify(app):
    assert plistlib.loads((app/'Contents/Info.plist').read_bytes())['CFBundleShortVersionString']=='1.29.7'
    audit=json.loads((QA/'release-report.json').read_text());assert audit['ok']
    assert sha(app/'Contents/MacOS/TungTungClient')==audit['executableSHA256']
    assert sha(app/'Contents/Resources/payload/mods/tung-tung-bridge-1.29.5.jar')==audit['coreSHA256']
    subprocess.run(['codesign','--verify','--deep','--strict',str(app)],check=True)
def cores(): return {p.name:sha(p) for p in PROFILE.glob('tung-tung-bridge-*.jar')}
def apply():
    verify(SOURCE)
    if blockers(): return {'installed':False,'reason':'Close Tung Tung Client normally. Minecraft may remain open.','runningProcesses':blockers()}
    before=cores(); assert before=={'tung-tung-bridge-1.29.5.jar':'f1d1a4af8bef53147fa986a6f05b289e5d4f4b70f8e81a4dacdc153dc39d632a'},'Unexpected installed core; preserve it for review.'
    apps=[p for p in [Path('/Applications/Tung Tung Client.app'),Path.home()/'Applications/Tung Tung Client.app'] if p.is_dir()]
    assert apps,'Installed client not found'
    for app in apps:
        for rel in VOICE: assert sha(app/rel)==sha(SOURCE/rel),'Voice runtime changed: '+rel
    backup=ROOT/'build/install-backups/chess-1.29.7'/uuid.uuid4().hex
    staged=[]; replaced=[]
    try:
        for index,app in enumerate(apps):
            temporary=backup/'staging'/str(index)/app.name;temporary.parent.mkdir(parents=True)
            staged.append(temporary)
            subprocess.run(['/bin/cp','-cR',str(SOURCE),str(temporary)],check=True);verify(temporary)
        if blockers(): raise RuntimeError('Desktop restarted; no replacement performed.')
        for index,(app,temporary) in enumerate(zip(apps,staged)):
            saved=backup/f'application-{index}'/app.name;saved.mkdir(parents=True)
            (app/'Contents').rename(saved/'Contents');replaced.append((app,saved))
            (temporary/'Contents').rename(app/'Contents');verify(app)
        assert before==cores(),'Minecraft core changed concurrently'
        report={'installed':True,'desktopVersion':'1.29.7','minecraftCoreVersion':'1.29.5',
            'applications':list(map(str,apps)),'backup':str(backup),'coreBefore':before,'coreAfter':cores(),
            'gameProfileModified':False,'voiceRuntimeChanged':False,'accountFilesAccessed':False,
            'nativeAccessibilityGranted':False,'chromeExtensionInstalled':False}
        (QA/'installation.json').write_text(json.dumps(report,indent=2)+'\n');return report
    except BaseException:
        for app,saved in reversed(replaced):
            if (app/'Contents').exists(): shutil.rmtree(app/'Contents')
            (saved/'Contents').rename(app/'Contents')
        raise
    finally:
        for path in staged:
            if path.exists(): shutil.rmtree(path)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    print(json.dumps(apply() if args.apply else {'checkOnly':True,'runningDesktopProcesses':blockers()},indent=2))
