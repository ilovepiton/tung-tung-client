"""Install desktop 1.35.2 only; preserve the running Minecraft core and all profile data."""
from pathlib import Path
import subprocess, plistlib, shutil, uuid, json, hashlib
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'dist/Tung-Tung-Client-Mac-1.35.2/Tung Tung Client.app'
def running():
 return any(line.strip().endswith('/TungTungClient') for line in subprocess.check_output(['ps','-axo','comm='],text=True).splitlines())
def verify(app):
 assert plistlib.loads((app/'Contents/Info.plist').read_bytes())['CFBundleShortVersionString']=='1.35.2'
 assert 'auto_shield_breaker' in (app/'Contents/Resources/ui/mace.js').read_text()
 subprocess.run(['codesign','--verify','--deep','--strict',str(app)],check=True)
def main():
 assert not running(),'Quit Tung Tung Client before installation.'
 verify(SOURCE)
 apps=[p for p in [Path('/Applications/Tung Tung Client.app'),Path.home()/'Applications/Tung Tung Client.app'] if p.is_dir()]
 assert apps
 backup=ROOT/'build/install-backups/shield-ui-1.35.2'/uuid.uuid4().hex;backup.mkdir(parents=True)
 staged=[];replaced=[]
 try:
  for i,app in enumerate(apps):
   stage=backup/('stage-'+str(i))/app.name;stage.parent.mkdir()
   subprocess.run(['cp','-cR',str(SOURCE),str(stage)],check=True);verify(stage);staged.append(stage)
  assert not running(),'The app reopened; installation cancelled.'
  for i,(app,stage) in enumerate(zip(apps,staged)):
   saved=backup/('previous-'+str(i));saved.mkdir();(app/'Contents').rename(saved/'Contents');replaced.append((app,saved))
   (stage/'Contents').rename(app/'Contents');verify(app)
 except BaseException:
  for app,saved in reversed(replaced):
   if (app/'Contents').exists():shutil.rmtree(app/'Contents')
   (saved/'Contents').rename(app/'Contents')
  raise
 report={'installed':True,'desktopVersion':'1.35.2','coreVersion':'1.35.1','gameFilesChanged':False,'applications':[str(p) for p in apps],'backup':str(backup)}
 out=ROOT/'artifacts/shield-ui-1.35.2/installation.json';out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__': main()
